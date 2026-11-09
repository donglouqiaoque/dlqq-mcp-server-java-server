package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台程序登记表：记录 no_wait 启动的后台命令（cmd / powershell / pwsh）。
 *
 * <p>记录与日志放在 {@code .dlqq/cache/background_programs/} 下，每次 MCP 服务启动
 * 生成一份新的索引文件（{@code programs_<启动时间>.jsonl}），历史文件保留。</p>
 */
final class BackgroundPrograms {

    /** 单个日志最多返回的字节数，避免一次返回过大 */
    private static final int MAX_LOG_BYTES = 200_000;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger SEQ = new AtomicInteger(0);
    private static final String SESSION_STAMP =
        new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

    private BackgroundPrograms() {
    }

    /** 一条后台程序记录 */
    static final class Entry {
        String id;
        long pid;
        String shell;
        String command;
        String workingDir;
        String startTime;
        volatile String status = "running";
        volatile Integer exitCode;
        File logFile;
        transient Process process;
        transient CommandWindow window;

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("pid", pid);
            map.put("shell", shell);
            map.put("command", command);
            map.put("workingDir", workingDir);
            map.put("startTime", startTime);
            map.put("status", status);
            map.put("exitCode", exitCode);
            map.put("logFile", logFile == null ? null : logFile.getAbsolutePath());
            return map;
        }
    }

    private static File baseDir() {
        File dir = new File(Workspace.getRoot().toFile(),
            ".dlqq" + File.separator + "cache" + File.separator + "background_programs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File indexFile() {
        return new File(baseDir(), "programs_" + SESSION_STAMP + ".jsonl");
    }

    /** 登记一个刚启动的后台进程 */
    static Entry register(Process process, String shell, String command, String workingDir) {
        Entry entry = new Entry();
        entry.id = shellPrefix(shell) + "-" + SEQ.incrementAndGet();
        entry.pid = process.pid();
        entry.shell = shell;
        entry.command = command;
        entry.workingDir = workingDir;
        entry.startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        entry.process = process;
        entry.logFile = new File(baseDir(), entry.id + ".log");
        ENTRIES.add(entry);
        persist();
        return entry;
    }

    /** 关联执行窗口 */
    static void attach(Entry entry, CommandWindow window) {
        entry.window = window;
    }

    /** 启动输出泵：输出同时写入窗口与日志文件，进程结束后更新状态 */
    static void startPump(Entry entry) {
        Thread pump = new Thread(() -> {
            Charset charset = Charset.forName(consoleCharset());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(entry.process.getInputStream(), charset));
                 Writer writer = new OutputStreamWriter(
                     new FileOutputStream(entry.logFile, true), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.write("\n");
                    writer.flush();
                    if (entry.window != null) {
                        entry.window.append(line + "\n");
                    }
                }
            } catch (IOException ignored) {
                // 进程被终止或窗口关闭时读取会中断，属正常情况
            }
            int exitCode;
            try {
                exitCode = entry.process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exitCode = -1;
            }
            if ("running".equals(entry.status)) {
                markFinished(entry, exitCode);
            }
        }, "dlqq-bg-pump-" + entry.id);
        pump.setDaemon(true);
        pump.start();
    }

    /** 进程正常结束 */
    static void markFinished(Entry entry, int exitCode) {
        entry.exitCode = exitCode;
        entry.status = exitCode == 0 ? "completed" : "failed";
        if (entry.window != null) {
            entry.window.finish(false, exitCode);
        }
        persist();
    }

    /** 用户关闭窗口导致进程被终止 */
    static void markKilled(Entry entry) {
        if (!"running".equals(entry.status)) {
            return;
        }
        entry.status = "killed";
        persist();
    }

    // ==================== 工具方法 ====================

    /** 列出指定 shell 的后台程序（含已结束的） */
    static String list(String shell) {
        StringBuilder sb = new StringBuilder();
        sb.append("后台程序列表（本会话缓存: ").append(indexFile().getName()).append("）\n");
        sb.append("====================\n\n");
        List<Entry> matched = filter(shell);
        if (matched.isEmpty()) {
            sb.append("（无记录）");
            return sb.toString();
        }
        sb.append(String.format("%-12s %-8s %-10s %-19s %-10s %s\n",
            "ID", "PID", "状态", "开始时间", "退出码", "命令"));
        for (Entry e : matched) {
            sb.append(String.format("%-12s %-8d %-10s %-19s %-10s %s\n",
                e.id, e.pid, statusLabel(e), e.startTime,
                e.exitCode == null ? "-" : String.valueOf(e.exitCode), shorten(e.command, 60)));
        }
        long running = matched.stream().filter(e -> "running".equals(e.status)).count();
        sb.append("\n合计: ").append(matched.size()).append(" 个，其中运行中 ").append(running).append(" 个");
        return sb.toString();
    }

    /** 读取指定 ID 的日志 */
    static String log(String shell, String id) {
        Entry entry = find(shell, id);
        if (entry == null) {
            return "未找到后台程序: " + id;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("后台程序日志\n");
        sb.append("ID: ").append(entry.id).append("\n");
        sb.append("PID: ").append(entry.pid).append("\n");
        sb.append("状态: ").append(statusLabel(entry)).append("\n");
        sb.append("命令: ").append(entry.command).append("\n");
        sb.append("日志: ").append(entry.logFile == null ? "(无)" : entry.logFile.getAbsolutePath()).append("\n");
        sb.append("\n===== 输出 =====\n");
        if (entry.logFile == null || !entry.logFile.isFile()) {
            sb.append("(暂无输出)");
            return sb.toString();
        }
        try {
            String content = readTail(entry.logFile);
            sb.append(content.isEmpty() ? "(暂无输出)" : content);
        } catch (IOException e) {
            sb.append("读取日志失败: ").append(e.getMessage());
        }
        return sb.toString();
    }

    /** 终止指定后台程序 */
    static String kill(String shell, String id) {
        Entry entry = find(shell, id);
        if (entry == null) {
            return "未找到后台程序: " + id;
        }
        if (!"running".equals(entry.status) || entry.process == null || !entry.process.isAlive()) {
            return "该后台程序已结束，无需终止: " + id + "（状态 " + statusLabel(entry) + "）";
        }
        try {
            entry.process.descendants().forEach(ProcessHandle::destroyForcibly);
            entry.process.destroyForcibly();
            entry.process.waitFor();
        } catch (Exception ignored) {
            // 尽力而为
        }
        markKilled(entry);
        if (entry.window != null) {
            entry.window.dispose();
        }
        return "已终止后台程序 " + id + "（PID " + entry.pid + "）";
    }

    /** 清除已结束的记录与日志；运行中的保持不变 */
    static String clear(String shell) {
        List<Entry> matched = filter(shell);
        List<Entry> removable = new ArrayList<>();
        for (Entry e : matched) {
            if (!"running".equals(e.status)) {
                removable.add(e);
            }
        }
        for (Entry e : removable) {
            ENTRIES.remove(e);
            if (e.logFile != null && e.logFile.isFile()) {
                e.logFile.delete();
            }
        }
        persist();
        long stillRunning = filter(shell).stream().filter(e -> "running".equals(e.status)).count();
        return "已清除 " + removable.size() + " 条已结束记录及其日志；"
            + "运行中的 " + stillRunning + " 个后台程序保留。";
    }

    /** 刷新状态：检测被外部关闭/结束的进程，同步为最新状态 */
    static String refresh(String shell) {
        int changed = 0;
        for (Entry e : filter(shell)) {
            if (!"running".equals(e.status) || e.process == null) {
                continue;
            }
            if (!e.process.isAlive()) {
                int code;
                try {
                    code = e.process.exitValue();
                } catch (IllegalThreadStateException ex) {
                    continue;
                }
                e.exitCode = code;
                e.status = code == 0 ? "completed" : "failed";
                changed++;
            }
        }
        if (changed > 0) {
            persist();
        }
        return "状态已刷新：更新 " + changed + " 个后台程序。\n\n" + list(shell);
    }

    // ==================== 内部工具 ====================

    private static List<Entry> filter(String shell) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (shell == null || shell.equals(e.shell)) {
                result.add(e);
            }
        }
        return result;
    }

    private static Entry find(String shell, String id) {
        for (Entry e : ENTRIES) {
            if (e.id.equals(id) && (shell == null || shell.equals(e.shell))) {
                return e;
            }
        }
        return null;
    }

    private static String shellPrefix(String shell) {
        if (RunCommandTools.SHELL_POWERSHELL.equals(shell)) {
            return "powershell";
        }
        if (RunCommandTools.SHELL_PWSH.equals(shell)) {
            return "pwsh";
        }
        return "command";
    }

    private static String statusLabel(Entry e) {
        switch (e.status) {
            case "completed":
                return "已完成";
            case "failed":
                return "已失败";
            case "killed":
                return "已终止";
            default:
                return "运行中";
        }
    }

    private static String shorten(String text, int limit) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() <= limit ? oneLine : oneLine.substring(0, limit) + "…";
    }

    private static String readTail(File file) throws IOException {
        byte[] all = Files.readAllBytes(file.toPath());
        int start = Math.max(0, all.length - MAX_LOG_BYTES);
        String text = new String(all, start, all.length - start, StandardCharsets.UTF_8);
        if (start > 0) {
            text = "…（仅显示最后 " + MAX_LOG_BYTES / 1000 + " KB）\n" + text;
        }
        return text;
    }

    private static String consoleCharset() {
        String nativeEncoding = System.getProperty("native.encoding");
        return nativeEncoding != null && !nativeEncoding.isEmpty() ? nativeEncoding : "UTF-8";
    }

    /** 把当前登记表写入本会话索引文件 */
    private static synchronized void persist() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : ENTRIES) {
            sb.append(GSON.toJson(e.toMap())).append("\n");
        }
        try {
            Files.write(indexFile().toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 索引写入失败不影响主流程
        }
    }
}
