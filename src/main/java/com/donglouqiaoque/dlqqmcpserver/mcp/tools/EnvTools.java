package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 开发环境检测：C / Java / Python / Node.js 以及各环境已安装的库。
 */
public final class EnvTools {

    private EnvTools() {
    }

    private static final int CMD_TIMEOUT_SECONDS = 6;

    /**
     * 生成环境检测报告。
     */
    public static String getToolsEnv() {
        StringBuilder sb = new StringBuilder();
        sb.append("开发环境检测\n");
        sb.append("============\n\n");

        sb.append("[C 编译器]\n").append(detectC()).append("\n");
        sb.append("[Java]\n").append(detectJava()).append("\n");
        sb.append("[Python]\n").append(detectPython()).append("\n");
        sb.append("[Node.js]\n").append(detectNode()).append("\n");
        sb.append("[已安装库]\n").append(detectLibraries());
        return sb.toString();
    }

    // ==================== 各环境检测 ====================

    private static String detectC() {
        for (String cmd : new String[]{"gcc", "clang", "cc"}) {
            String out = run(cmd, "--version");
            if (out != null && !out.isEmpty()) {
                return "  " + cmd + ": " + firstLine(out) + "\n";
            }
        }
        return "  未检测到（gcc / clang / cc 均不可用）\n";
    }

    private static String detectJava() {
        StringBuilder sb = new StringBuilder();
        sb.append("  版本: ").append(System.getProperty("java.version"))
                .append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("  运行时: ").append(System.getProperty("java.home")).append("\n");
        String javac = run("javac", "-version");
        sb.append("  javac: ").append(javac == null || javac.isEmpty() ? "未检测到" : firstLine(javac)).append("\n");
        return sb.toString();
    }

    private static String detectPython() {
        StringBuilder sb = new StringBuilder();
        File exe = findBundledPython();
        if (exe != null) {
            String version = run(exe.getAbsolutePath(), "--version");
            sb.append("  项目运行时: ").append(version == null ? "未知" : version.trim()).append("\n");
            sb.append("  路径: ").append(exe.getAbsolutePath()).append("\n");
        } else {
            sb.append("  项目运行时: 未找到 python\n");
        }
        String system = run("python", "--version");
        sb.append("  系统 Python: ")
                .append(system == null || system.isEmpty() ? "未检测到" : firstLine(system))
                .append("\n");
        return sb.toString();
    }

    private static String detectNode() {
        String node = run("node", "-v");
        String npm = run("cmd", "/c", "npm", "-v");
        StringBuilder sb = new StringBuilder();
        sb.append("  node: ").append(node == null || node.isEmpty() ? "未检测到" : node.trim()).append("\n");
        sb.append("  npm:  ").append(npm == null || npm.isEmpty() ? "未检测到" : npm.trim()).append("\n");
        return sb.toString();
    }

    // ==================== 库列表 ====================

    private static String detectLibraries() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n  Python（.runtime/python）:\n");
        File python = findBundledPython();
        String pipList = python == null ? null
                : run(python.getAbsolutePath(), "-m", "pip", "list", "--format=freeze");
        appendItems(sb, splitLines(pipList), 60, "    未检测到");

        sb.append("\n  Java（JAR 内置）:\n");
        List<String> javaLibs = readMavenLibs();
        sb.append("    共 ").append(javaLibs.size()).append(" 个依赖\n");
        appendItems(sb, javaLibs, 40, "    未检测到");

        sb.append("\n  Node.js（全局包）:\n");
        String npmGlobal = run("cmd", "/c", "npm", "ls", "-g", "--depth=0");
        appendItems(sb, parseNpmGlobal(npmGlobal), 20, "    未检测到");

        return sb.toString();
    }

    private static List<String> parseNpmGlobal(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("+--") || trimmed.startsWith("`--")) {
                result.add(trimmed.substring(3).trim());
            }
        }
        return result;
    }

    private static void appendItems(StringBuilder sb, List<String> items, int limit, String emptyText) {
        if (items.isEmpty()) {
            sb.append(emptyText).append("\n");
            return;
        }
        int count = Math.min(items.size(), limit);
        for (int i = 0; i < count; i++) {
            sb.append("    ").append(items.get(i)).append("\n");
        }
        if (items.size() > count) {
            sb.append("    ...（其余 ").append(items.size() - count).append(" 个已省略）\n");
        }
    }

    /**
     * 读取 JAR 内 META-INF/maven 下的依赖坐标。
     */
    private static List<String> readMavenLibs() {
        List<String> result = new ArrayList<>();
        try {
            File jar = new File(EnvTools.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jar.isFile()) {
                return result;
            }
            try (JarFile jarFile = new JarFile(jar)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith("META-INF/maven/") || !name.endsWith("/pom.properties")) {
                        continue;
                    }
                    Properties props = new Properties();
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        props.load(is);
                    }
                    String groupId = props.getProperty("groupId");
                    String artifactId = props.getProperty("artifactId");
                    String version = props.getProperty("version");
                    if (artifactId != null) {
                        result.add(groupId + ":" + artifactId + " " + version);
                    }
                }
            }
        } catch (Exception e) {
            // 依赖信息不可用时返回空列表
        }
        Collections.sort(result);
        return result;
    }

    // ==================== 通用工具 ====================

    private static File findBundledPython() {
        for (File base : pythonBaseDirs()) {
            File exe = new File(base, "python" + File.separator + "python.exe");
            if (exe.isFile()) {
                return exe;
            }
        }
        return null;
    }

    private static List<File> pythonBaseDirs() {
        List<File> dirs = new ArrayList<>();
        try {
            File codeSource = new File(EnvTools.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File appDir = codeSource.isFile() ? codeSource.getParentFile() : codeSource;
            if (appDir != null) {
                dirs.add(appDir);
            }
        } catch (Exception ignored) {
            // 忽略，继续使用其它候选目录
        }
        dirs.add(new File(System.getProperty("user.dir")));
        try {
            dirs.add(Workspace.getRoot().toFile());
        } catch (Exception ignored) {
            // 某些场景下工作区不可用
        }
        return dirs;
    }

    private static String run(String... command) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!process.waitFor(CMD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String text = out.toString().trim();
            return process.exitValue() == 0 ? text : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int index = text.indexOf('\n');
        return index < 0 ? text.trim() : text.substring(0, index).trim();
    }

    private static List<String> splitLines(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
