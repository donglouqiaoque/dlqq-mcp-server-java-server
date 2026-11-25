package com.donglouqiaoque.dlqqmcpserver.mcp.policy;

import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * dlqq_overwrite_file 的审批前风险评估。
 *
 * <p>目标：AI 用 overwrite 做“大改/整文件替换”时按高危审批；
 * 只做局部小改（相似度 >= 90%）时降级为风险工具直接执行，减少审批骚扰。</p>
 *
 * <p>限制：目标文件不存在直接报错；文件过大（1 MiB / 20000 行）不做相似度计算，
 * 保持高危；无法读取/解码时同样保持高危。</p>
 */
final class OverwriteRiskAssessor {

    /** 参与相似度计算的最大文件大小（字节）。 */
    static final long MAX_FILE_BYTES = 1024L * 1024L;
    /** 参与相似度计算的最大行数（新旧内容任一超过即放弃降级）。 */
    static final int MAX_LINES = 20_000;
    /** 行级相似度达到该值才考虑降级。 */
    static final double MIN_SIMILARITY = 0.90;
    /** 允许的最大改动行比例（第二道护栏）。 */
    static final double MAX_CHANGED_RATIO = 0.10;
    /** 小文件至少允许改动的行数。 */
    static final int MIN_CHANGED_LINES = 3;

    enum Decision {
        /** 审批前直接报错（如文件不存在）。 */
        ERROR,
        /** 保持工具风险表中的高危等级。 */
        KEEP_HIGH,
        /** 降级为风险（RISK）工具。 */
        DOWNGRADE
    }

    record Result(Decision decision, String message, double similarity) {

        static Result error(String message) {
            return new Result(Decision.ERROR, message, 0.0);
        }

        static Result keepHigh() {
            return new Result(Decision.KEEP_HIGH, null, 0.0);
        }

        static Result downgrade(double similarity) {
            return new Result(Decision.DOWNGRADE, null, similarity);
        }
    }

    private OverwriteRiskAssessor() {
    }

    /**
     * 评估一次 dlqq_overwrite_file 调用。
     *
     * @param arguments 工具参数（file_path / content / charset）
     */
    static Result assess(Map<String, Object> arguments) {
        Object rawPath = arguments == null ? null : arguments.get("file_path");
        if (!(rawPath instanceof String filePath) || filePath.isBlank()) {
            // 交给工具参数校验，保持原风险等级
            return Result.keepHigh();
        }

        Path path;
        try {
            path = Workspace.resolve(filePath).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return Result.error("文件路径无效，无法覆盖：" + filePath);
        }

        if (!Files.exists(path)) {
            return Result.error("文件不存在，" + filePath
                    + "；dlqq_overwrite_file 只能覆盖已有文件，新建文件请使用 dlqq_create_file。");
        }
        if (!Files.isRegularFile(path)) {
            return Result.error("目标路径不是普通文件，无法覆盖：" + path);
        }

        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                return Result.keepHigh();
            }
        } catch (IOException e) {
            return Result.keepHigh();
        }

        Object rawContent = arguments.get("content");
        if (!(rawContent instanceof String newText) || newText.isEmpty()) {
            return Result.keepHigh();
        }
        if (newText.length() > MAX_FILE_BYTES) {
            // 新内容本身过大：diff 成本不可控，保持高危
            return Result.keepHigh();
        }

        Charset charset = resolveCharset(arguments.get("charset"));
        if (charset == null) {
            return Result.keepHigh();
        }

        String oldText;
        try {
            oldText = new String(Files.readAllBytes(path), charset);
        } catch (Exception e) {
            return Result.keepHigh();
        }
        oldText = stripBom(oldText);
        newText = stripBom(newText);

        if (countLines(oldText) > MAX_LINES || countLines(newText) > MAX_LINES) {
            return Result.keepHigh();
        }
        List<String> oldLines = splitLines(oldText);
        List<String> newLines = splitLines(newText);

        int oldCount = oldLines.size();
        int newCount = newLines.size();
        if (oldCount == newCount && oldLines.equals(newLines)) {
            return Result.downgrade(1.0);
        }

        try {
            Patch<String> patch = DiffUtils.diff(oldLines, newLines);
            int deleted = 0;
            int inserted = 0;
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                deleted += delta.getSource().size();
                inserted += delta.getTarget().size();
            }
            int total = oldCount + newCount;
            if (total <= 0) {
                return Result.keepHigh();
            }
            int matched = oldCount - deleted;
            double similarity = 2.0 * matched / total;
            int changedLines = deleted + inserted;
            int allowedChanged = Math.max(MIN_CHANGED_LINES,
                    (int) Math.ceil(oldCount * MAX_CHANGED_RATIO));
            if (similarity >= MIN_SIMILARITY && changedLines <= allowedChanged) {
                return Result.downgrade(similarity);
            }
        } catch (Exception e) {
            // diff 失败/内存不足等情况：保守保持高危
            return Result.keepHigh();
        }
        return Result.keepHigh();
    }

    private static Charset resolveCharset(Object rawCharset) {
        String name = rawCharset == null ? "" : String.valueOf(rawCharset).trim();
        if (name.isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        String upper = name.toUpperCase();
        try {
            return switch (upper) {
                case "UTF-8", "UTF8" -> StandardCharsets.UTF_8;
                case "UTF-16", "UTF16" -> StandardCharsets.UTF_16;
                case "UTF-16LE", "UTF16LE" -> StandardCharsets.UTF_16LE;
                case "UTF-16BE", "UTF16BE" -> StandardCharsets.UTF_16BE;
                case "ISO-8859-1", "ISO8859-1", "LATIN1" -> StandardCharsets.ISO_8859_1;
                case "US-ASCII", "ASCII" -> StandardCharsets.US_ASCII;
                default -> Charset.forName(name);
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripBom(String text) {
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

    /** 粗略统计行数（用于在 split 前快速判断是否超过行数上限）。 */
    private static int countLines(String text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
                if (count > MAX_LINES) {
                    return count;
                }
            }
        }
        return count;
    }

    private static List<String> splitLines(String text) {
        return List.of(text.split("\\R", -1));
    }
}
