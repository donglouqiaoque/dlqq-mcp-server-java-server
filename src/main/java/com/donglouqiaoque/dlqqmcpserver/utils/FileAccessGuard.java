package com.donglouqiaoque.dlqqmcpserver.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件访问一致性守卫。
 *
 * <p>记录 AI 读取文件时的修改时间、大小与读取模式（文本 / 二进制），
 * 写入前校验：</p>
 * <ul>
 *   <li>文件必须先被读取（读取或搜索都算读取）；</li>
 *   <li>文本写入要求此前是文本读取，二进制写入要求此前是二进制读取；</li>
 *   <li>文件在读取后被外部修改（修改时间或大小变化）则要求重新读取。</li>
 * </ul>
 *
 * <p>校验可通过设置项 {@code file.access.check=false} 或系统属性
 * {@code -Ddlqq.file.access.check=false} 关闭，默认启用。</p>
 */
public final class FileAccessGuard {

    /** 读取 / 写入模式。 */
    public enum Mode { TEXT, BINARY }

    /** 设置项：是否启用文件访问校验（默认启用）。 */
    private static final String SETTINGS_KEY = "file.access.check";

    /** 系统属性：是否启用文件访问校验（优先级高于设置项）。 */
    private static final String SYSTEM_PROPERTY = "dlqq.file.access.check";

    /** key: 文件绝对路径，value: 最近一次读取/写入时的快照。 */
    private static final Map<String, Snapshot> RECORDS = new ConcurrentHashMap<>();

    private FileAccessGuard() {
    }

    /** 记录一次文本读取（read_file、文本搜索、读取 Office 等）。 */
    public static void recordRead(Path path) {
        record(path, Mode.TEXT);
    }

    /** 记录一次二进制读取（read_bin_file）。 */
    public static void recordReadBinary(Path path) {
        record(path, Mode.BINARY);
    }

    /**
     * 写入前校验。
     *
     * @param path      目标文件
     * @param mode      写入模式
     * @param operation 操作描述（用于错误提示）
     * @return null 表示允许；否则返回拒绝原因
     */
    public static String checkBeforeWrite(Path path, Mode mode, String operation) {
        if (path == null || !isEnabled()) {
            return null;
        }
        Snapshot current = snapshot(path);
        if (current == null) {
            // 文件不存在：允许新建
            return null;
        }
        String recordKey = key(path);
        Snapshot record = RECORDS.get(recordKey);
        if (record == null) {
            return "✗ 拒绝" + operation + "：文件尚未读取，请先读取后再修改（文本用 dlqq_read_file / dlqq_search_*，二进制用 dlqq_read_bin_file）：" + path;
        }
        if (record.mode() != mode) {
            String writeMode = mode == Mode.TEXT ? "文本" : "二进制";
            String readMode = record.mode() == Mode.TEXT ? "文本" : "二进制";
            return "✗ 拒绝" + operation + "：读取模式不匹配。本次以" + writeMode + "方式写入，但此前以"
                    + readMode + "方式读取，请用" + writeMode + "方式重新读取后再修改：" + path;
        }
        if (record.modifiedMillis() != current.modifiedMillis() || record.size() != current.size()) {
            return "✗ 拒绝" + operation + "：文件在读取后被修改（修改时间或大小已变化），请重新读取后再修改：" + path;
        }
        return null;
    }

    /** 写入成功后刷新记录（视为已读取最新内容）。 */
    public static void afterWrite(Path path, Mode mode) {
        record(path, mode);
    }

    /** 清除某个文件的记录。 */
    public static void clear(Path path) {
        if (path != null) {
            RECORDS.remove(key(path));
        }
    }

    /** 是否启用校验。 */
    public static boolean isEnabled() {
        String property = System.getProperty(SYSTEM_PROPERTY);
        if (property != null) {
            return !isFalse(property);
        }
        try {
            String value = Workspace.readSettingsValue(Workspace.getSettingsFile(), SETTINGS_KEY);
            return value == null || !isFalse(value);
        } catch (Exception e) {
            return true;
        }
    }

    private static void record(Path path, Mode mode) {
        if (path == null) {
            return;
        }
        Snapshot current = snapshot(path);
        if (current == null) {
            return;
        }
        RECORDS.put(key(path), new Snapshot(current.modifiedMillis(), current.size(), mode));
    }

    private static Snapshot snapshot(Path path) {
        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return null;
            }
            FileTime lastModified = Files.getLastModifiedTime(path);
            long size = Files.size(path);
            return new Snapshot(lastModified.toMillis(), size, Mode.TEXT);
        } catch (IOException e) {
            return null;
        }
    }

    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static boolean isFalse(String value) {
        String text = value.trim();
        return "false".equalsIgnoreCase(text) || "0".equals(text);
    }

    /** 文件快照：修改时间（毫秒）、大小、读取模式。 */
    private record Snapshot(long modifiedMillis, long size, Mode mode) {
    }
}
