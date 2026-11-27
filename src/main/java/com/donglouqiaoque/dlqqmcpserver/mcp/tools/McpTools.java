package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Stream;
import com.donglouqiaoque.dlqqmcpserver.BuildInfo;
import java.net.URLDecoder;
import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;
import com.donglouqiaoque.dlqqmcpserver.utils.WorkspaceRegistry;
import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ToolPolicy;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP工具类
 * 提供版本信息、构建时间等查询功能，以及MCP数据管理功能
 */
public class McpTools {

    /**
     * 脚本执行结果
     */
    private static class ScriptResult {
        final String output;
        final int exitCode;
        final boolean success;

        ScriptResult(String output, int exitCode) {
            this.output = output;
            this.exitCode = exitCode;
            this.success = exitCode == 0;
        }
    }

    /**
     * 获取MCP服务器版本和构建信息
     * @return 版本信息字符串
     */
    public static String getMcpVersion() {
        StringBuilder sb = new StringBuilder();
        sb.append("MCP服务器版本信息\n");
        sb.append("===================\n\n");

        try {
            // 获取当前类的JAR文件路径
            String classPath = McpTools.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            // URL解码处理中文路径
            classPath = URLDecoder.decode(classPath, "UTF-8");
            File jarFile = new File(classPath);

            sb.append("JAR文件路径: ").append(jarFile.getAbsolutePath()).append("\n");
            sb.append("JAR文件存在: ").append(jarFile.exists() ? "✓ 是" : "✗ 否").append("\n\n");

            if (jarFile.exists()) {
                // 获取JAR文件最后修改时间
                long lastModified = jarFile.lastModified();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String buildTime = sdf.format(new Date(lastModified));
                sb.append("JAR文件修改时间: ").append(buildTime).append("\n");
                sb.append("JAR文件大小: ").append(jarFile.length()).append(" bytes (").append(String.format("%.2f", jarFile.length() / 1024.0)).append(" KB)\n\n");

                // 真实构建时间：编译期写入 class 文件的常量（运行中替换 JAR 不会改变）
                String embeddedBuildTime = BuildInfo.BUILD_TIME;
                sb.append("构建时间（代码内，真实值）: ").append(utcToLocal(embeddedBuildTime))
                  .append(" (UTC: ").append(embeddedBuildTime).append(")\n\n");

                // 读取MANIFEST.MF
                try (JarFile jar = new JarFile(jarFile)) {
                    Manifest manifest = jar.getManifest();
                    if (manifest != null) {
                        Attributes mainAttrs = manifest.getMainAttributes();

                        sb.append("MANIFEST信息:\n");
                        sb.append("---------------\n");

                        String version = mainAttrs.getValue("Implementation-Version");
                        if (version != null) {
                            sb.append("版本: ").append(version).append("\n");
                        } else {
                            sb.append("版本: (未在MANIFEST中指定)\n");
                        }

                        String title = mainAttrs.getValue("Implementation-Title");
                        if (title != null) {
                            sb.append("名称: ").append(title).append("\n");
                        }

                        String vendor = mainAttrs.getValue("Implementation-Vendor");
                        if (vendor != null) {
                            sb.append("供应商: ").append(vendor).append("\n");
                        }

                        String buildJdk = mainAttrs.getValue("Build-Jdk");
                        if (buildJdk != null) {
                            sb.append("构建JDK: ").append(buildJdk).append("\n");
                        }

                        String createdBy = mainAttrs.getValue("Created-By");
                        if (createdBy != null) {
                            sb.append("创建者: ").append(createdBy).append("\n");
                        }

                        String buildTimeAttr = mainAttrs.getValue("Build-Time");
                        if (buildTimeAttr != null) {
                            try {
                                // 将UTC时间转换为本地时间
                                LocalDateTime utcTime = LocalDateTime.parse(buildTimeAttr,
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                ZonedDateTime utcZoned = utcTime.atZone(ZoneId.of("UTC"));
                                ZonedDateTime localZoned = utcZoned.withZoneSameInstant(ZoneId.systemDefault());
                                String localTimeStr = localZoned.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                sb.append("JAR清单构建时间（磁盘）: ").append(localTimeStr).append(" (UTC: ").append(buildTimeAttr).append(")\n");
                            } catch (Exception e) {
                                sb.append("JAR清单构建时间（磁盘）: ").append(buildTimeAttr).append("\n");
                            }
                        }
                    } else {
                        sb.append("MANIFEST信息: (未找到)\n");
                    }
                }
            }

            // 获取Java运行时信息
            sb.append("\n运行时信息:\n");
            sb.append("---------------\n");
            sb.append("Java版本: ").append(System.getProperty("java.version")).append("\n");
            sb.append("Java厂商: ").append(System.getProperty("java.vendor")).append("\n");
            sb.append("Java路径: ").append(System.getProperty("java.home")).append("\n");
            sb.append("操作系统: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
            sb.append("工作目录: ").append(System.getProperty("user.dir")).append("\n");
            sb.append("工作区: ").append(Workspace.getRoot()).append("（来源: ").append(Workspace.getSource()).append("）\n");

        } catch (Exception e) {
            sb.append("\n✗ 获取版本信息时出错: ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }

        return sb.toString();
    }

    /**
     * 把 UTC 时间字符串（yyyy-MM-dd HH:mm:ss）转换为本地时间字符串。
     */
    private static String utcToLocal(String utcTime) {
        try {
            LocalDateTime t = LocalDateTime.parse(utcTime,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return t.atZone(ZoneId.of("UTC"))
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return utcTime;
        }
    }

    /**
     * 热加载测试标记：编译期写死在代码里，修改后重新构建即可验证“替换 JAR 是否真的加载了新代码”。
     */
    public static final String HOT_RELOAD_TEST_MARK = "hot-reload-test-v4";

    /**
     * 热加载测试：返回代码中写死的标记字符串。
     * 该值编译在 class 文件中，替换 JAR 后不重启仍返回旧值；重启后返回新值。
     *
     * @return 热加载测试结果
     */
    public static String testHotReload() {
        return "热加载测试标记: " + HOT_RELOAD_TEST_MARK + "\n"
                + "返回内容已修改（v4），用于观察客户端是否自动更新工具列表。\n";
    }

    /**
     * 清除运行时数据（cache / data / log），保留 settings 与 workspace_config.json。
     * 权限配置属于关键设置，任何清理工具都不得删除。
     *
     * @return 删除结果描述
     */
    public static String clearDlqqData() {
        StringBuilder result = new StringBuilder();
        result.append("✓ 运行时数据清除完成\n");
        result.append("===================\n\n");
        result.append("cache: ").append(clearCacheRoot()).append("\n");
        result.append("data : ").append(clearDirectoryContents(RuntimeDirs.getDataRoot())).append("\n");
        result.append("log  : ").append(clearLog()).append("\n");
        result.append("\nsettings 与 workspace_config.json 已保留（权限配置受保护）。\n");
        return result.toString();
    }

    /** 删除目录下的全部内容（保留目录本身）。 */
    private static String clearDirectoryContents(Path dir) {
        if (!Files.isDirectory(dir)) {
            return "目录不存在，无需清除";
        }
        int deleted = 0;
        List<String> busy = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                try {
                    deleteDirectoryRecursive(entry);
                    deleted++;
                } catch (IOException e) {
                    busy.add(entry.getFileName().toString());
                }
            }
        } catch (IOException e) {
            return "读取失败：" + e.getMessage();
        }
        return "已删除 " + deleted + " 项" + (busy.isEmpty() ? "" : "；被占用：" + String.join(", ", busy));
    }

    /**
     * 递归删除目录并收集结果
     */
    private static void deleteDirectoryRecursiveCollect(Path path, String relativePath,
                                                        List<String> deletedItems,
                                                        List<String> busyFiles) {
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    String childRelative = relativePath + "/" + child.getFileName();
                    deleteDirectoryRecursiveCollect(child, childRelative, deletedItems, busyFiles);
                }
            } catch (IOException e) {
                // 忽略读取目录失败
            }
        }
        try {
            Files.delete(path);
            if (Files.isRegularFile(path)) {
                deletedItems.add(relativePath);
            } else {
                deletedItems.add(relativePath);
            }
        } catch (IOException e) {
            if (Files.exists(path)) {
                if (Files.isRegularFile(path)) {
                    busyFiles.add(relativePath);
                }
            }
        }
    }

    // ==================== 配置读取工具 ====================

    /**
     * 从settings.properties读取指定key的配置值
     * @param key 配置key
     * @return 配置值，如果不存在返回null
     */
    public static String getConfigFromSettings(String key) {
        Path settingsPath = Workspace.getSettingsFile();
        if (!Files.exists(settingsPath)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String configKey = line.substring(0, equalIndex).trim();
                    if (configKey.equals(key)) {
                        return line.substring(equalIndex + 1).trim();
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * 确保当前工作区的 settings.properties 存在；不存在时写入默认配置。
     * 配置位置：{@code <部署目录>/.dlqq/settings/<工作区hash>/settings.properties}
     */
    public static void ensureSettingsFile() {
        Path settingsPath = Workspace.getSettingsFile();
        if (Files.exists(settingsPath)) {
            fixSettingsEncodingIfNeeded(settingsPath);
            return;
        }
        try {
            Path settingsDir = settingsPath.getParent();
            if (settingsDir != null) {
                Files.createDirectories(settingsDir);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(settingsPath, StandardCharsets.UTF_8)) {
                writer.write("# MCP Server Settings\n");
                writer.write("# ==================\n\n");
                List<String> keys = new ArrayList<>(DEFAULT_PROPERTIES.keySet());
                Collections.sort(keys);
                for (String key : keys) {
                    writer.write(key + "=" + DEFAULT_PROPERTIES.get(key) + "\n");
                }
            }
        } catch (IOException e) {
            // 创建失败时忽略，读取端会回退到默认值
        }
    }

    /**
     * 兼容历史版本：旧文件可能是 GBK 编码（含中文注释），若检测到非法 UTF-8 则转成 UTF-8。
     */
    private static void fixSettingsEncodingIfNeeded(Path settingsPath) {
        try {
            byte[] bytes = Files.readAllBytes(settingsPath);
            CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                utf8Decoder.decode(ByteBuffer.wrap(bytes));
                return; // 已是合法 UTF-8
            } catch (CharacterCodingException ignored) {
                // 继续尝试 GBK
            }
            CharsetDecoder gbkDecoder = Charset.forName("GBK").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                String text = gbkDecoder.decode(ByteBuffer.wrap(bytes)).toString();
                Files.write(settingsPath, text.getBytes(StandardCharsets.UTF_8));
            } catch (CharacterCodingException ignored) {
                // 两种编码都无法解析时保持原样
            }
        } catch (IOException ignored) {
            // 读取失败时保持原样
        }
    }

    /** 动态工具管理：被移除工具列表在 settings.properties 中的键名 */
    public static final String EXCLUDED_TOOLS_KEY = "mcp.tools.excluded";

    /**
     * 读取被移除（排除）的工具名列表（逗号分隔，已去空）。
     *
     * @return 工具名列表；未配置时返回空列表
     */
    public static List<String> getExcludedToolNames() {
        List<String> names = new ArrayList<>();
        String value = getConfigFromSettings(EXCLUDED_TOOLS_KEY);
        if (value == null || value.trim().isEmpty()) {
            return names;
        }
        for (String part : value.split(",")) {
            String name = part.trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 保存被移除（排除）的工具名列表到 settings.properties。
     *
     * @param names 工具名集合；为空时写入空字符串（等同于清空）
     * @return 保存结果消息
     */
    public static String saveExcludedToolNames(Collection<String> names) {
        List<String> sorted = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.trim().isEmpty() && !sorted.contains(name.trim())) {
                    sorted.add(name.trim());
                }
            }
        }
        Collections.sort(sorted);
        return setProperty(EXCLUDED_TOOLS_KEY, String.join(",", sorted));
    }

    // 最大返回内容限制（API限制 258048 字节）
    public static final int MAX_RESPONSE_SIZE = 258048; // 约252KB，API上限
    public static final int DEFAULT_RESPONSE_SIZE = 25804; // 默认1/10 API上限（约25KB）

    /**
     * 获取最大回复文本大小配置
     * @return 最大字节数，默认25804（约25KB，API上限的1/10）
     */
    public static int getResponseMaxSize() {
        String value = getConfigFromSettings("response.max.size");
        if (value != null) {
            try {
                int size = Integer.parseInt(value);
                if (size > 0) {
                    // 限制不能超过API上限
                    return Math.min(size, MAX_RESPONSE_SIZE);
                }
            } catch (NumberFormatException e) {
                // 使用默认值
            }
        }
        return DEFAULT_RESPONSE_SIZE; // 默认258KB（API上限）
    }

    // ==================== 脚本执行工具 ====================

    /**
     * 从settings.properties读取指定key的脚本路径
     * @param key 配置key (如: script.build)
     * @return 脚本路径，如果不存在返回null
     */
    private static String getScriptFromSettings(String key) {
        Path settingsPath = Workspace.getSettingsFile();
        if (!Files.exists(settingsPath)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String configKey = line.substring(0, equalIndex).trim();
                    if (configKey.equals(key)) {
                        return line.substring(equalIndex + 1).trim();
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * 执行脚本文件（返回详细结果）
     * @param scriptPath 脚本路径
     * @param operationName 操作名称(用于日志)
     * @param configKey 配置项key(用于提示)
     * @return 脚本执行结果
     */
    private static ScriptResult executeScript(String scriptPath, String operationName, String configKey) {
        if (scriptPath == null || scriptPath.isEmpty()) {
            String output = "✗ 错误：未配置「" + operationName + "」脚本路径，需要先配置工具路径。\n\n" +
                   "请先使用以下工具设置脚本路径：\n" +
                   "  dlqq_set_" + configKey + "_script\n\n" +
                   "或在 .dlqq/settings/settings.properties 中手动设置：\n" +
                   "  script." + configKey + "=your_script_path.bat";
            return new ScriptResult(output, -1);
        }

        // 先加载结果类型：构建脚本可能会替换正在运行的 JAR，避免构建完成后加载该类失败
        ScriptResult.class.getName();

        File scriptFile = Workspace.resolve(scriptPath).toFile();
        if (!scriptFile.exists()) {
            String output = "✗ 错误：脚本文件不存在\n" +
                   "路径: " + scriptFile.getAbsolutePath() + "\n" +
                   "请检查 .dlqq/settings/settings.properties 中的配置";
            return new ScriptResult(output, -1);
        }

        StringBuilder result = new StringBuilder();
        result.append("执行 ").append(operationName).append("\n");
        result.append("===================\n\n");
        result.append("脚本路径: ").append(scriptFile.getAbsolutePath()).append("\n\n");

        int exitCode = -1;
        try {
            // AI 调用固定追加 -ai：脚本据此跳过 pause、自动确认并采用热构建/热加载等模式
            ProcessBuilder pb = new ProcessBuilder(scriptPath, "-ai");
            pb.directory(Workspace.getRoot().toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
            }

            exitCode = process.waitFor();
            result.append("\n===================\n");
            result.append("退出码: ").append(exitCode).append("\n");
            if (exitCode == 0) {
                result.append("✓ ").append(operationName).append(" 执行成功\n");
            } else {
                result.append("✗ ").append(operationName).append(" 执行失败\n");
            }

        } catch (IOException | InterruptedException e) {
            result.append("\n===================\n");
            result.append("✗ 执行异常: ").append(e.getMessage()).append("\n");
        }

        return new ScriptResult(result.toString(), exitCode);
    }

    /**
     * 执行脚本文件（返回字符串结果，兼容旧代码）
     * @param scriptPath 脚本路径
     * @param operationName 操作名称(用于日志)
     * @param configKey 配置项key(用于提示)
     * @return 执行结果字符串
     */
    private static String executeScriptString(String scriptPath, String operationName, String configKey) {
        return executeScript(scriptPath, operationName, configKey).output;
    }

    /**
     * 构建项目
     * 执行settings.properties中script.build定义的脚本
     * @return 构建结果
     */
    public static String buildProject() {
        String scriptPath = getScriptFromSettings("script.build");
        return executeScriptString(scriptPath, "构建", "build");
    }

    /**
     * 重建项目
     * 执行settings.properties中script.rebuild定义的脚本
     * @return 重建结果
     */
    public static String rebuildProject() {
        String scriptPath = getScriptFromSettings("script.rebuild");
        return executeScriptString(scriptPath, "重建", "rebuild");
    }

    /**
     * 擦除芯片
     * 执行settings.properties中script.chip.erase定义的脚本
     * @return 擦除结果
     */
    public static String chipErase() {
        String scriptPath = getScriptFromSettings("script.chip.erase");
        return executeScriptString(scriptPath, "芯片擦除", "chip_erase");
    }

    /**
     * 下载到芯片
     * 执行settings.properties中script.chip.download定义的脚本
     * @return 下载结果
     */
    public static String chipDownload() {
        String scriptPath = getScriptFromSettings("script.chip.download");
        return executeScriptString(scriptPath, "芯片下载", "chip_download");
    }

    // ==================== 设置脚本路径工具 ====================

    /**
     * 设置settings.properties中的指定key值
     * @param key 配置key
     * @param value 配置值
     * @param description 配置描述（用于显示）
     * @return 设置结果
     */
    private static String setScriptInSettings(String key, String value, String description) {
        Path settingsPath = Workspace.getSettingsFile();

        // 检查settings目录是否存在
        Path settingsDir = settingsPath.getParent();
        if (!Files.exists(settingsDir)) {
            try {
                Files.createDirectories(settingsDir);
            } catch (IOException e) {
                return "✗ 错误：无法创建settings目录: " + e.getMessage();
            }
        }

        // 读取现有配置
        Map<String, String> config = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();

        if (Files.exists(settingsPath)) {
            try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int equalIndex = line.indexOf('=');
                    if (equalIndex > 0) {
                        String configKey = line.substring(0, equalIndex).trim();
                        String configValue = line.substring(equalIndex + 1).trim();
                        config.put(configKey, configValue);
                    }
                }
            } catch (IOException e) {
                return "✗ 错误：无法读取settings.properties: " + e.getMessage();
            }
        }

        // 更新或添加配置
        String oldValue = config.get(key);
        config.put(key, value);

        // 如果没有脚本配置节，添加一个
        boolean hasScriptSection = lines.stream().anyMatch(l -> l.contains("Project build scripts"));

        // 重写文件
        try (BufferedWriter writer = Files.newBufferedWriter(settingsPath, StandardCharsets.UTF_8)) {
            // 如果文件为空，写入默认头部
            if (lines.isEmpty()) {
                writer.write("# MCP Server Settings\n");
                writer.write("# ==================\n\n");
                writer.write("# Response size limits (bytes)\n");
                writer.write("response.max.size=25804\n\n");
                writer.write("# Project build scripts\n");
                writer.write("# Scripts for build/rebuild/erase/download operations\n");
            } else {
                // 写入现有内容，但跳过旧值
                boolean skipNextValue = false;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith(key + "=")) {
                        // 跳过这个key的行，稍后写入新值
                        continue;
                    }
                    writer.write(line);
                    writer.write("\n");
                }

                // 如果没有脚本配置节，添加一个
                if (!hasScriptSection) {
                    writer.write("\n# Project build scripts\n");
                    writer.write("# Scripts for build/rebuild/erase/download operations\n");
                }
            }

            // 写入新的key-value
            writer.write(key + "=" + value + "\n");

        } catch (IOException e) {
            return "✗ 错误：无法写入settings.properties: " + e.getMessage();
        }

        StringBuilder result = new StringBuilder();
        result.append("✓ ").append(description).append("脚本路径已设置\n");
        result.append("===================\n\n");
        result.append("配置项: ").append(key).append("\n");
        if (oldValue != null) {
            result.append("旧值: ").append(oldValue).append("\n");
        }
        result.append("新值: ").append(value).append("\n\n");
        result.append("配置文件: ").append(settingsPath.toAbsolutePath()).append("\n");

        return result.toString();
    }

    /**
     * 设置构建脚本路径（资源高危，需要用户审批）
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setBuildScript(String scriptPath) {
        return setScriptInSettings("script.build", scriptPath, "构建项目");
    }

    /**
     * 设置重建脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setRebuildScript(String scriptPath) {
        return setScriptInSettings("script.rebuild", scriptPath, "重建项目");
    }

    /**
     * 设置芯片擦除脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setChipEraseScript(String scriptPath) {
        return setScriptInSettings("script.chip.erase", scriptPath, "芯片擦除");
    }

    /**
     * 设置芯片下载脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setChipDownloadScript(String scriptPath) {
        return setScriptInSettings("script.chip.download", scriptPath, "芯片下载");
    }

    // ==================== 搜索配置工具 ====================

    /**
     * 获取搜索配置值
     * @param key 配置key
     * @return 配置值，不存在返回null
     */
    public static String getSearchConfig(String key) {
        Path settingsPath = Workspace.getSettingsFile();
        if (!Files.exists(settingsPath)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String configKey = line.substring(0, equalIndex).trim();
                    String configValue = line.substring(equalIndex + 1).trim();
                    if (configKey.equals(key)) {
                        return configValue;
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * 设置搜索配置
     * @param key 配置key
     * @param value 配置值
     * @param description 配置描述
     * @return 设置结果
     */
    public static String setSearchConfig(String key, String value, String description) {
        // 复用 setScriptInSettings 的逻辑，但使用不同的描述前缀
        Path settingsPath = Workspace.getSettingsFile();

        // 检查settings目录是否存在
        Path settingsDir = settingsPath.getParent();
        if (!Files.exists(settingsDir)) {
            try {
                Files.createDirectories(settingsDir);
            } catch (IOException e) {
                return "✗ 错误：无法创建settings目录: " + e.getMessage();
            }
        }

        // 读取现有配置
        Map<String, String> config = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();

        if (Files.exists(settingsPath)) {
            try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int equalIndex = line.indexOf('=');
                    if (equalIndex > 0) {
                        String configKey = line.substring(0, equalIndex).trim();
                        String configValue = line.substring(equalIndex + 1).trim();
                        config.put(configKey, configValue);
                    }
                }
            } catch (IOException e) {
                return "✗ 错误：无法读取settings.properties: " + e.getMessage();
            }
        }

        // 更新配置
        String oldValue = config.get(key);
        config.put(key, value);

        // 检查是否有搜索配置节
        boolean hasSearchSection = lines.stream().anyMatch(l -> l.contains("Text search config"));

        // 重写文件
        try (BufferedWriter writer = Files.newBufferedWriter(settingsPath, StandardCharsets.UTF_8)) {
            if (lines.isEmpty()) {
                // 写入默认头部
                writer.write("# MCP Server Settings\n");
                writer.write("# ==================\n\n");
                writer.write("# Response size limits (bytes)\n");
                writer.write("response.max.size=25804\n\n");
                writer.write("# Project build scripts\n");
                writer.write("# Scripts for build/rebuild/erase/download operations\n");
            } else {
                // 写入现有内容，跳过旧的key
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith(key + "=")) {
                        continue;
                    }
                    writer.write(line);
                    writer.write("\n");
                }

                // 如果没有搜索配置节，添加一个
                if (!hasSearchSection) {
                    writer.write("\n# Text search config\n");
                    writer.write("# Configuration for dlqq_search_text tool\n");
                }
            }

            // 写入新的key-value
            writer.write(key + "=" + value + "\n");

        } catch (IOException e) {
            return "✗ 错误：无法写入settings.properties: " + e.getMessage();
        }

        StringBuilder result = new StringBuilder();
        result.append("✓ ").append(description).append("已设置\n");
        if (oldValue != null) {
            result.append("  旧值: ").append(oldValue).append("\n");
        }
        result.append("  新值: ").append(value).append("\n");

        return result.toString();
    }

    // ==================== 组合脚本执行工具 ====================

    /**
     * 擦除并下载
     * 优先执行script.erase.download组合脚本，如果不存在则按顺序执行script.chip.erase和script.chip.download
     * 如果擦除失败则不执行下载
     * @return 执行结果
     */
    public static String eraseDownload() {
        String compositeScript = getScriptFromSettings("script.erase.download");

        // 如果组合脚本存在，直接执行
        if (compositeScript != null && !compositeScript.isEmpty()) {
            return executeScriptString(compositeScript, "擦除并下载", "erase_download");
        }

        // 检查单独脚本是否存在
        String eraseScript = getScriptFromSettings("script.chip.erase");
        String downloadScript = getScriptFromSettings("script.chip.download");

        if (eraseScript == null || eraseScript.isEmpty()) {
            return "✗ 错误：未配置擦除脚本路径，需要先配置工具路径。\n\n" +
                   "组合脚本 script.erase.download 和单独脚本 script.chip.erase 都未配置\n" +
                   "请先使用 dlqq_set_chip_erase_script 设置擦除脚本\n" +
                   "或使用 dlqq_set_erase_download_script 设置组合脚本";
        }

        if (downloadScript == null || downloadScript.isEmpty()) {
            return "✗ 错误：未配置下载脚本路径，需要先配置工具路径。\n\n" +
                   "组合脚本 script.erase.download 和单独脚本 script.chip.download 都未配置\n" +
                   "请先使用 dlqq_set_chip_download_script 设置下载脚本\n" +
                   "或使用 dlqq_set_erase_download_script 设置组合脚本";
        }

        // 按顺序执行：先擦除，成功后下载
        StringBuilder result = new StringBuilder();
        result.append("执行擦除并下载（分步模式）\n");
        result.append("===================\n\n");
        result.append("【注意】组合脚本未配置，将按顺序执行擦除→下载\n\n");

        // 第一步：擦除
        result.append("【步骤1/2】擦除芯片\n");
        result.append("--------------------------------------------------\n");
        ScriptResult eraseResult = executeScript(eraseScript, "芯片擦除", "chip_erase");
        result.append(eraseResult.output);
        result.append("\n");

        if (!eraseResult.success) {
            result.append("【终止】擦除失败，跳过后续下载步骤\n");
            result.append("===================\n");
            result.append("✗ 擦除并下载 执行失败（擦除步骤失败）\n");
            return result.toString();
        }

        // 第二步：下载
        result.append("【步骤2/2】下载固件\n");
        result.append("--------------------------------------------------\n");
        ScriptResult downloadResult = executeScript(downloadScript, "芯片下载", "chip_download");
        result.append(downloadResult.output);
        result.append("\n");

        // 总结
        result.append("===================\n");
        if (downloadResult.success) {
            result.append("✓ 擦除并下载 执行成功\n");
        } else {
            result.append("✗ 擦除并下载 执行失败（下载步骤失败，但擦除已成功）\n");
        }

        return result.toString();
    }

    /**
     * 构建、擦除并下载
     * 优先执行组合脚本，如果不存在则按顺序执行build→erase→download
     * 如果某一步失败则不执行后续步骤
     * @return 执行结果
     */
    public static String buildEraseDownload() {
        String compositeScript = getScriptFromSettings("script.build.erase.download");

        // 如果组合脚本存在，直接执行
        if (compositeScript != null && !compositeScript.isEmpty()) {
            return executeScriptString(compositeScript, "构建、擦除并下载", "build_erase_download");
        }

        // 检查单独脚本是否存在
        String buildScript = getScriptFromSettings("script.build");
        String eraseScript = getScriptFromSettings("script.chip.erase");
        String downloadScript = getScriptFromSettings("script.chip.download");

        StringBuilder missingScripts = new StringBuilder();
        if (buildScript == null || buildScript.isEmpty()) {
            missingScripts.append("  - script.build (构建脚本)\n");
        }
        if (eraseScript == null || eraseScript.isEmpty()) {
            missingScripts.append("  - script.chip.erase (擦除脚本)\n");
        }
        if (downloadScript == null || downloadScript.isEmpty()) {
            missingScripts.append("  - script.chip.download (下载脚本)\n");
        }

        if (missingScripts.length() > 0) {
            return "✗ 错误：缺少必要的脚本配置，需要先配置工具路径。\n\n" +
                   "组合脚本 script.build.erase.download 未配置，且以下单独脚本也未配置：\n" +
                   missingScripts.toString() + "\n" +
                   "请先使用相应的 dlqq_set_*_script 工具设置脚本\n" +
                   "或使用 dlqq_set_build_erase_download_script 设置组合脚本";
        }

        // 按顺序执行
        StringBuilder result = new StringBuilder();
        result.append("执行构建、擦除并下载（分步模式）\n");
        result.append("===================\n\n");
        result.append("【注意】组合脚本未配置，将按顺序执行构建→擦除→下载\n\n");

        // 第一步：构建
        result.append("【步骤1/3】构建项目\n");
        result.append("--------------------------------------------------\n");
        ScriptResult buildResult = executeScript(buildScript, "构建", "build");
        result.append(buildResult.output);
        result.append("\n");

        if (!buildResult.success) {
            result.append("【终止】构建失败，跳过后续步骤\n");
            result.append("===================\n");
            result.append("✗ 构建、擦除并下载 执行失败（构建步骤失败）\n");
            return result.toString();
        }

        // 第二步：擦除
        result.append("【步骤2/3】擦除芯片\n");
        result.append("--------------------------------------------------\n");
        ScriptResult eraseResult = executeScript(eraseScript, "芯片擦除", "chip_erase");
        result.append(eraseResult.output);
        result.append("\n");

        if (!eraseResult.success) {
            result.append("【终止】擦除失败，跳过后续下载步骤\n");
            result.append("===================\n");
            result.append("✗ 构建、擦除并下载 执行失败（擦除步骤失败，但构建已成功）\n");
            return result.toString();
        }

        // 第三步：下载
        result.append("【步骤3/3】下载固件\n");
        result.append("--------------------------------------------------\n");
        ScriptResult downloadResult = executeScript(downloadScript, "芯片下载", "chip_download");
        result.append(downloadResult.output);
        result.append("\n");

        // 总结
        result.append("===================\n");
        if (downloadResult.success) {
            result.append("✓ 构建、擦除并下载 执行成功\n");
        } else {
            result.append("✗ 构建、擦除并下载 执行失败（下载步骤失败，但构建和擦除已成功）\n");
        }

        return result.toString();
    }

    /**
     * 重建、擦除并下载
     * 优先执行组合脚本，如果不存在则按顺序执行rebuild→erase→download
     * 如果某一步失败则不执行后续步骤
     * @return 执行结果
     */
    public static String rebuildEraseDownload() {
        String compositeScript = getScriptFromSettings("script.rebuild.erase.download");

        // 如果组合脚本存在，直接执行
        if (compositeScript != null && !compositeScript.isEmpty()) {
            return executeScriptString(compositeScript, "重建、擦除并下载", "rebuild_erase_download");
        }

        // 检查单独脚本是否存在
        String rebuildScript = getScriptFromSettings("script.rebuild");
        String eraseScript = getScriptFromSettings("script.chip.erase");
        String downloadScript = getScriptFromSettings("script.chip.download");

        StringBuilder missingScripts = new StringBuilder();
        if (rebuildScript == null || rebuildScript.isEmpty()) {
            missingScripts.append("  - script.rebuild (重建脚本)\n");
        }
        if (eraseScript == null || eraseScript.isEmpty()) {
            missingScripts.append("  - script.chip.erase (擦除脚本)\n");
        }
        if (downloadScript == null || downloadScript.isEmpty()) {
            missingScripts.append("  - script.chip.download (下载脚本)\n");
        }

        if (missingScripts.length() > 0) {
            return "✗ 错误：缺少必要的脚本配置，需要先配置工具路径。\n\n" +
                   "组合脚本 script.rebuild.erase.download 未配置，且以下单独脚本也未配置：\n" +
                   missingScripts.toString() + "\n" +
                   "请先使用相应的 dlqq_set_*_script 工具设置脚本\n" +
                   "或使用 dlqq_set_rebuild_erase_download_script 设置组合脚本";
        }

        // 按顺序执行
        StringBuilder result = new StringBuilder();
        result.append("执行重建、擦除并下载（分步模式）\n");
        result.append("===================\n\n");
        result.append("【注意】组合脚本未配置，将按顺序执行重建→擦除→下载\n\n");

        // 第一步：重建
        result.append("【步骤1/3】重建项目\n");
        result.append("--------------------------------------------------\n");
        ScriptResult rebuildResult = executeScript(rebuildScript, "重建", "rebuild");
        result.append(rebuildResult.output);
        result.append("\n");

        if (!rebuildResult.success) {
            result.append("【终止】重建失败，跳过后续步骤\n");
            result.append("===================\n");
            result.append("✗ 重建、擦除并下载 执行失败（重建步骤失败）\n");
            return result.toString();
        }

        // 第二步：擦除
        result.append("【步骤2/3】擦除芯片\n");
        result.append("--------------------------------------------------\n");
        ScriptResult eraseResult = executeScript(eraseScript, "芯片擦除", "chip_erase");
        result.append(eraseResult.output);
        result.append("\n");

        if (!eraseResult.success) {
            result.append("【终止】擦除失败，跳过后续下载步骤\n");
            result.append("===================\n");
            result.append("✗ 重建、擦除并下载 执行失败（擦除步骤失败，但重建已成功）\n");
            return result.toString();
        }

        // 第三步：下载
        result.append("【步骤3/3】下载固件\n");
        result.append("--------------------------------------------------\n");
        ScriptResult downloadResult = executeScript(downloadScript, "芯片下载", "chip_download");
        result.append(downloadResult.output);
        result.append("\n");

        // 总结
        result.append("===================\n");
        if (downloadResult.success) {
            result.append("✓ 重建、擦除并下载 执行成功\n");
        } else {
            result.append("✗ 重建、擦除并下载 执行失败（下载步骤失败，但重建和擦除已成功）\n");
        }

        return result.toString();
    }

    // ==================== 运行和调试工具 ====================

    /**
     * 运行项目
     * 执行settings.properties中script.run定义的脚本
     * @return 执行结果
     */
    public static String runProject() {
        String scriptPath = getScriptFromSettings("script.run");
        return executeScriptString(scriptPath, "运行项目", "run");
    }

    /**
     * 调试项目
     * 执行settings.properties中script.debug定义的脚本
     * @return 执行结果
     */
    public static String debugProject() {
        String scriptPath = getScriptFromSettings("script.debug");
        return executeScriptString(scriptPath, "调试项目", "debug");
    }

    /**
     * 构建并运行项目
     * 执行settings.properties中script.build.run定义的脚本
     * @return 执行结果
     */
    public static String buildRunProject() {
        String scriptPath = getScriptFromSettings("script.build.run");
        return executeScriptString(scriptPath, "构建并运行", "build_run");
    }

    /**
     * 构建并调试项目
     * 执行settings.properties中script.build.debug定义的脚本
     * @return 执行结果
     */
    public static String buildDebugProject() {
        String scriptPath = getScriptFromSettings("script.build.debug");
        return executeScriptString(scriptPath, "构建并调试", "build_debug");
    }

    /**
     * 重建并运行项目
     * 执行settings.properties中script.rebuild.run定义的脚本
     * @return 执行结果
     */
    public static String rebuildRunProject() {
        String scriptPath = getScriptFromSettings("script.rebuild.run");
        return executeScriptString(scriptPath, "重建并运行", "rebuild_run");
    }

    /**
     * 重建并调试项目
     * 执行settings.properties中script.rebuild.debug定义的脚本
     * @return 执行结果
     */
    public static String rebuildDebugProject() {
        String scriptPath = getScriptFromSettings("script.rebuild.debug");
        return executeScriptString(scriptPath, "重建并调试", "rebuild_debug");
    }

    // ==================== 组合脚本设置工具 ====================

    /**
     * 设置擦除下载组合脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setEraseDownloadScript(String scriptPath) {
        return setScriptInSettings("script.erase.download", scriptPath, "擦除并下载");
    }

    /**
     * 设置构建擦除下载组合脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setBuildEraseDownloadScript(String scriptPath) {
        return setScriptInSettings("script.build.erase.download", scriptPath, "构建、擦除并下载");
    }

    /**
     * 设置重建擦除下载组合脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setRebuildEraseDownloadScript(String scriptPath) {
        return setScriptInSettings("script.rebuild.erase.download", scriptPath, "重建、擦除并下载");
    }

    // ==================== 运行调试设置工具 ====================

    /**
     * 设置运行项目脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setRunScript(String scriptPath) {
        return setScriptInSettings("script.run", scriptPath, "运行项目");
    }

    /**
     * 设置调试项目脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setDebugScript(String scriptPath) {
        return setScriptInSettings("script.debug", scriptPath, "调试项目");
    }

    /**
     * 设置构建运行组合脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setBuildRunScript(String scriptPath) {
        return setScriptInSettings("script.build.run", scriptPath, "构建并运行");
    }

    /**
     * 设置构建调试组合脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setBuildDebugScript(String scriptPath) {
        return setScriptInSettings("script.build.debug", scriptPath, "构建并调试");
    }

    /**
     * 设置重建运行组合脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setRebuildRunScript(String scriptPath) {
        return setScriptInSettings("script.rebuild.run", scriptPath, "重建并运行");
    }

    /**
     * 设置重建调试组合脚本路径
     * @param scriptPath 脚本路径
     * @return 设置结果
     */
    public static String setRebuildDebugScript(String scriptPath) {
        return setScriptInSettings("script.rebuild.debug", scriptPath, "重建并调试");
    }

    /**
     * 设置最大回复文本大小
     * @param maxSize 最大字节数（必须为正整数，建议范围：1024-102400）
     * @return 设置结果
     */
    public static String setResponseMaxSize(String maxSize) {
        if (maxSize == null || maxSize.isEmpty()) {
            return "✗ 错误：max_size 参数不能为空";
        }

        int size;
        try {
            size = Integer.parseInt(maxSize.trim());
            if (size <= 0) {
                return "✗ 错误：max_size 必须是正整数";
            }
            if (size < 1024) {
                return "✗ 错误：max_size 不能小于1024字节（1KB），建议范围：1024-102400";
            }
            if (size > 102400) {
                return "✗ 警告：max_size 超过102400字节（100KB）可能导致传输问题，请谨慎使用";
            }
        } catch (NumberFormatException e) {
            return "✗ 错误：max_size 必须是有效的整数";
        }

        String result = setScriptInSettings("response.max.size", String.valueOf(size), "最大回复文本大小");

        // 添加使用说明
        if (result.startsWith("✓")) {
            result = result + "\n提示：新的限制将在下次文件操作时生效\n";
        }

        return result;
    }

    // ==================== 通用属性操作工具 ====================

    /**
     * 设置属性到settings.properties
     * @param key 属性键
     * @param value 属性值
     * @return 设置结果消息
     */
    public static String setProperty(String key, String value) {
        if (key == null || key.isEmpty()) {
            return "✗ 错误：属性键不能为空";
        }
        if (value == null) {
            return "✗ 错误：属性值不能为null";
        }

        Path settingsPath = Workspace.getSettingsFile();

        // 检查settings目录是否存在
        Path settingsDir = settingsPath.getParent();
        if (!Files.exists(settingsDir)) {
            try {
                Files.createDirectories(settingsDir);
            } catch (IOException e) {
                return "✗ 错误：无法创建settings目录: " + e.getMessage();
            }
        }

        // 读取现有配置
        Map<String, String> config = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();

        if (Files.exists(settingsPath)) {
            try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int equalIndex = line.indexOf('=');
                    if (equalIndex > 0) {
                        String configKey = line.substring(0, equalIndex).trim();
                        String configValue = line.substring(equalIndex + 1).trim();
                        config.put(configKey, configValue);
                    }
                }
            } catch (IOException e) {
                return "✗ 错误：无法读取settings.properties: " + e.getMessage();
            }
        }

        // 更新配置
        String oldValue = config.get(key);
        config.put(key, value);

        // 重写文件
        try (BufferedWriter writer = Files.newBufferedWriter(settingsPath, StandardCharsets.UTF_8)) {
            if (lines.isEmpty()) {
                // 写入默认头部
                writer.write("# MCP Server Settings\n");
                writer.write("# ==================\n\n");
            } else {
                // 过滤掉旧的key行
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        int equalIndex = line.indexOf('=');
                        if (equalIndex > 0) {
                            String configKey = line.substring(0, equalIndex).trim();
                            if (!configKey.equals(key)) {
                                writer.write(line + "\n");
                            }
                        } else {
                            writer.write(line + "\n");
                        }
                    } else {
                        writer.write(line + "\n");
                    }
                }
            }
            // 写入新值
            writer.write(key + "=" + value + "\n");
        } catch (IOException e) {
            return "✗ 错误：无法写入settings.properties: " + e.getMessage();
        }

        StringBuilder result = new StringBuilder();
        result.append("✓ 属性设置成功\n");
        result.append("===================\n\n");
        result.append("键: ").append(key).append("\n");
        if (oldValue != null) {
            result.append("旧值: ").append(oldValue).append("\n");
        }
        result.append("新值: ").append(value).append("\n");
        result.append("\n配置文件: ").append(settingsPath.toAbsolutePath().toString());

        return result.toString();
    }

    /**
     * 批量设置多个属性
     * @param properties Map<key, value>
     * @return 设置结果消息
     */
    public static String setProperties(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return "✗ 错误：至少指定一个属性";
        }

        StringBuilder result = new StringBuilder();
        result.append("✓ 批量设置属性\n");
        result.append("===================\n\n");

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String r = setProperty(entry.getKey(), entry.getValue());
            // 提取成功消息的核心部分
            if (r.startsWith("✓")) {
                result.append("✓ ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            } else {
                result.append("✗ ").append(entry.getKey()).append(": ").append(r).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * 获取所有属性
     * @return 所有属性的Map
     */
    public static Map<String, String> getAllProperties() {
        Path settingsPath = Workspace.getSettingsFile();
        Map<String, String> config = new LinkedHashMap<>();

        if (!Files.exists(settingsPath)) {
            return config;
        }

        try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String configKey = line.substring(0, equalIndex).trim();
                    String configValue = line.substring(equalIndex + 1).trim();
                    config.put(configKey, configValue);
                }
            }
        } catch (IOException e) {
            // 返回空map
        }

        return config;
    }

    /**
     * 格式化属性列表为字符串
     * @param properties 属性Map
     * @return 格式化后的字符串
     */
    public static String formatProperties(Map<String, String> properties) {
        if (properties.isEmpty()) {
            return "暂无配置属性";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("配置属性列表\n");
        sb.append("=".repeat(60)).append("\n\n");

        // 按类别分组
        Map<String, Map<String, String>> categories = new LinkedHashMap<>();
        categories.put("response", new LinkedHashMap<>());
        categories.put("search", new LinkedHashMap<>());
        categories.put("script", new LinkedHashMap<>());
        categories.put("other", new LinkedHashMap<>());

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String category = "other";
            if (key.startsWith("response.")) {
                category = "response";
            } else if (key.startsWith("search.")) {
                category = "search";
            } else if (key.startsWith("script.")) {
                category = "script";
            }
            categories.get(category).put(key, entry.getValue());
        }

        // 输出各分类
        if (!categories.get("response").isEmpty()) {
            sb.append("[响应大小限制]\n");
            for (Map.Entry<String, String> entry : categories.get("response").entrySet()) {
                sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        if (!categories.get("search").isEmpty()) {
            sb.append("[搜索配置]\n");
            for (Map.Entry<String, String> entry : categories.get("search").entrySet()) {
                sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        if (!categories.get("script").isEmpty()) {
            sb.append("[脚本配置]\n");
            for (Map.Entry<String, String> entry : categories.get("script").entrySet()) {
                sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        if (!categories.get("other").isEmpty()) {
            sb.append("[其他配置]\n");
            for (Map.Entry<String, String> entry : categories.get("other").entrySet()) {
                sb.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
            }
        }

        return sb.toString();
    }

    // ==================== 缓存目录工具 ====================

    /**
     * 获取当前工作区缓存目录路径：
     * {@code <部署目录>/.dlqq/cache/<工作区hash>}（按工作区隔离，不区分会话）。
     *
     * @return 缓存目录 Path
     */
    public static Path getCacheDir() {
        return SessionScope.currentCacheDir();
    }

    /**
     * 确保当前工作区缓存目录存在。
     *
     * @return 缓存目录 Path
     * @throws IOException 创建目录失败
     */
    public static Path ensureCacheDir() throws IOException {
        Path cacheDir = getCacheDir();
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        return cacheDir;
    }

    /**
     * 清除缓存目录：不传参数清当前工作区缓存内容；指定工作区清该工作区缓存；
     * {@code all=true} 清全部工作区缓存。
     *
     * @param workspace 工作区路径（可选）
     * @param all       是否清除全部工作区缓存
     * @return 清除结果描述
     */
    public static String clearCacheFor(String workspace, boolean all) {
        if (all) {
            return clearCacheRoot();
        }
        if (workspace != null && !workspace.trim().isEmpty()) {
            Path workspacePath = Workspace.resolve(workspace);
            if (!Files.isDirectory(workspacePath)) {
                return "✗ 清除失败：工作区不是已存在的绝对目录: " + workspace;
            }
            String hash = WorkspaceRegistry.hashFor(workspacePath);
            return clearCacheDirectory(RuntimeDirs.getCacheRoot().resolve(hash),
                    "工作区 " + workspacePath + "（hash=" + hash + "）");
        }
        return clearCache();
    }

    /** 清除全部工作区的缓存内容（保留 cache 根目录）。 */
    private static String clearCacheRoot() {
        Path root = RuntimeDirs.getCacheRoot();
        if (!Files.isDirectory(root)) {
            return "✓ 缓存目录不存在，无需清除";
        }
        int deletedWorkspaces = 0;
        List<String> busy = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                try {
                    deleteDirectoryRecursive(entry);
                    deletedWorkspaces++;
                } catch (IOException e) {
                    busy.add(entry.getFileName().toString());
                }
            }
        } catch (IOException e) {
            return "✗ 清除失败：无法读取缓存目录 - " + e.getMessage();
        }
        return "✓ 全部工作区缓存清除完成：已删除 " + deletedWorkspaces + " 个工作区缓存目录"
                + (busy.isEmpty() ? "" : "；被占用：" + String.join(", ", busy));
    }

    /** 清除指定工作区缓存目录并返回结果描述。 */
    private static String clearCacheDirectory(Path cacheDir, String label) {
        if (!Files.exists(cacheDir)) {
            return "✓ " + label + " 的缓存目录不存在，无需清除";
        }
        try {
            deleteDirectoryRecursive(cacheDir);
            return "✓ " + label + " 的缓存清除完成";
        } catch (IOException e) {
            return "✗ 清除失败：" + label + " - " + e.getMessage();
        }
    }

    /**
     * 清除当前工作区缓存目录内容。
     *
     * @return 清除结果描述
     */
    public static String clearCache() {
        Path cachePath = getCacheDir();

        if (!Files.exists(cachePath)) {
            return "✓ 缓存目录不存在，无需清除";
        }

        if (!Files.isDirectory(cachePath)) {
            return "✗ 清除失败：缓存路径不是目录";
        }

        int deletedCount = 0;
        int dirCount = 0;
        List<String> busyFiles = new ArrayList<>();

        // 遍历cache目录下的所有文件和子目录
        try (Stream<Path> entries = Files.list(cachePath)) {
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry)) {
                    // 递归删除子目录
                    deleteDirectoryRecursive(entry);
                    dirCount++;
                } else {
                    try {
                        Files.delete(entry);
                        deletedCount++;
                    } catch (IOException e) {
                        busyFiles.add(entry.getFileName().toString());
                    }
                }
            }
        } catch (IOException e) {
            return "✗ 清除失败：无法读取缓存目录 - " + e.getMessage();
        }

        // 构建返回结果
        StringBuilder result = new StringBuilder();
        result.append("✓ 缓存清除完成\n");
        result.append("===================\n\n");

        if (deletedCount > 0) {
            result.append("已删除的文件：").append(deletedCount).append("个\n");
        }
        if (dirCount > 0) {
            result.append("已删除的目录：").append(dirCount).append("个\n");
        }

        if (!busyFiles.isEmpty()) {
            result.append("\n以下文件当前被占用，无法删除（").append(busyFiles.size()).append("个）：\n");
            for (String file : busyFiles) {
                result.append("  ○ ").append(file).append("\n");
            }
        }

        if (deletedCount == 0 && dirCount == 0 && busyFiles.isEmpty()) {
            result.append("缓存目录为空，无需清除。\n");
        } else {
            result.append("\n缓存目录已清理。\n");
        }

        return result.toString();
    }

    /**
     * 递归删除目录
     */
    private static void deleteDirectoryRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteDirectoryRecursive(entry);
                }
            }
        }
        Files.delete(path);
    }

    // ==================== MCP日志清理工具 ====================

    /**
     * 清除MCP日志文件
     * 只删除.dlqq/log目录下的日志文件，保留目录结构
     * @return 删除结果描述
     */
    public static String clearLog() {
        Path logPath = RuntimeDirs.getLogRoot();

        if (!Files.exists(logPath)) {
            return "✓ 日志目录不存在，无需清除";
        }

        if (!Files.isDirectory(logPath)) {
            return "✗ 清除失败：日志路径不是目录";
        }

        int deletedCount = 0;
        List<String> busyFiles = new ArrayList<>();

        // 遍历log目录下的所有子目录
        try (Stream<Path> subDirs = Files.list(logPath)) {
            for (Path subDir : subDirs.toList()) {
                if (Files.isDirectory(subDir)) {
                    // 遍历子目录中的日志文件
                    try (Stream<Path> files = Files.list(subDir)) {
                        for (Path file : files.toList()) {
                            if (Files.isRegularFile(file)) {
                                try {
                                    Files.delete(file);
                                    deletedCount++;
                                } catch (IOException e) {
                                    busyFiles.add(subDir.getFileName() + "/" + file.getFileName());
                                }
                            }
                        }
                    } catch (IOException e) {
                        // 忽略读取子目录失败
                    }
                }
            }
        } catch (IOException e) {
            return "✗ 清除失败：无法读取日志目录 - " + e.getMessage();
        }

        // 构建返回结果
        StringBuilder result = new StringBuilder();
        result.append("✓ MCP日志清除完成\n");
        result.append("===================\n\n");

        if (deletedCount > 0) {
            result.append("已删除的日志文件：").append(deletedCount).append("个\n\n");
        }

        if (!busyFiles.isEmpty()) {
            result.append("以下日志文件当前被占用，无法删除（").append(busyFiles.size()).append("个）：\n");
            for (String file : busyFiles) {
                result.append("  ○ ").append(file).append("\n");
            }
            result.append("\n");
            result.append("注意：部分文件无法删除是正常现象，因为这些日志文件正在被MCP服务器使用。\n");
            result.append("已删除的日志文件不会受到影响，被占用的文件将在MCP服务器重启后可以被清除。\n");
        } else if (deletedCount == 0) {
            result.append("没有找到需要清除的日志文件。\n");
        } else {
            result.append("所有日志文件已成功删除。\n");
        }

        return result.toString();
    }

    // ==================== 配置重置工具 ====================

    /**
     * 默认配置值
     */
    private static final Map<String, String> DEFAULT_PROPERTIES = new HashMap<>() {{
        // 响应大小限制
        put("response.max.size", "25804");

        // 搜索配置
        put("search.max.files", "1000");
        put("search.max.file.size", String.valueOf(10 * 1024 * 1024)); // 10MB
        put("search.max.results", "50");
        put("search.text.extensions", ".java,.c,.cpp,.h,.hpp,.cs,.py,.js,.ts,.html,.css,.json,.xml,.yaml,.yml,.md,.txt,.sh,.bat,.properties,.gradle,.ini");

        // 动态工具管理（被移除的工具名，逗号分隔）
        put("mcp.tools.excluded", "");

        // 脚本配置（默认为空，需要用户自行设置）
        put("script.build", "");
        put("script.rebuild", "");
        put("script.chip.erase", "");
        put("script.chip.download", "");
        put("script.erase.download", "");
        put("script.build.erase.download", "");
        put("script.rebuild.erase.download", "");
        put("script.run", "");
        put("script.debug", "");
        put("script.build.run", "");
        put("script.build.debug", "");
        put("script.rebuild.run", "");
        put("script.rebuild.debug", "");
    }};

    /**
     * 获取默认配置值副本（控制台配置选项卡使用）。
     *
     * @return 默认配置键值对副本
     */
    public static Map<String, String> getDefaultProperties() {
        return new LinkedHashMap<>(DEFAULT_PROPERTIES);
    }

    /**
     * 重置配置属性为默认值。
     *
     * @param requestedKeys null 表示重置全部非保护属性；空集合表示没有可重置项；
     *                      非空集合表示按指定键重置，受保护属性会被过滤跳过
     * @return 重置结果描述；只要有任意一个属性重置失败，结果以 ✗ 开头
     */
    public static String resetProperties(Collection<String> requestedKeys) {
        List<String> targets = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        if (requestedKeys == null) {
            for (String key : DEFAULT_PROPERTIES.keySet()) {
                if (ToolPolicy.isProtectedPropertyKey(key)) {
                    skipped.add(key);
                } else {
                    targets.add(key);
                }
            }
        } else {
            for (String key : requestedKeys) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (ToolPolicy.isProtectedPropertyKey(key)) {
                    if (!skipped.contains(key)) {
                        skipped.add(key);
                    }
                } else if (!targets.contains(key)) {
                    targets.add(key);
                }
            }
        }

        StringBuilder result = new StringBuilder();
        if (targets.isEmpty()) {
            result.append("✓ 没有可重置的非保护属性\n");
            appendSkippedPropertyKeys(result, skipped);
            return result.toString();
        }

        int successCount = 0;
        int failCount = 0;
        StringBuilder details = new StringBuilder();
        for (String key : targets) {
            String one = resetSingleProperty(key);
            if (one.startsWith("✓")) {
                successCount++;
                details.append("✓ ").append(key).append('\n');
            } else {
                failCount++;
                details.append("✗ ").append(key).append(": ")
                        .append(one.replace("\n", " ")).append('\n');
            }
        }

        if (failCount == 0) {
            result.append("✓ 重置完成\n");
            result.append("===================\n\n");
            result.append("成功: ").append(successCount).append(" 个\n");
            appendSkippedPropertyKeys(result, skipped);
            result.append("\n").append(details);
        } else {
            result.append("✗ 重置失败：").append(failCount).append(" 个属性未能重置\n");
            result.append("===================\n\n");
            result.append("成功: ").append(successCount).append(" 个，失败: ").append(failCount).append(" 个\n");
            appendSkippedPropertyKeys(result, skipped);
            result.append("\n").append(details);
        }
        return result.toString();
    }

    private static void appendSkippedPropertyKeys(StringBuilder result, List<String> skipped) {
        if (!skipped.isEmpty()) {
            result.append("已跳过受保护属性: ").append(String.join(", ", skipped)).append('\n');
        }
    }

    /** 重置单个配置项为默认值（受保护属性由调用方提前过滤）。 */
    private static String resetSingleProperty(String key) {
        Path settingsPath = Workspace.getSettingsFile();

        // 检查settings目录是否存在
        Path settingsDir = settingsPath.getParent();
        if (!Files.exists(settingsDir)) {
            try {
                Files.createDirectories(settingsDir);
            } catch (IOException e) {
                return "✗ 错误：无法创建settings目录: " + e.getMessage();
            }
        }

        StringBuilder result = new StringBuilder();
        String defaultValue = DEFAULT_PROPERTIES.get(key);
        if (defaultValue == null) {
            // 非预定义属性：如果配置文件中存在，则删除该自定义属性
            if (getConfigFromSettings(key) != null) {
                String removeResult = removePropertyFromSettings(key);
                if (removeResult.startsWith("✓")) {
                    result.append("✓ 自定义配置项已删除\n");
                    result.append("===================\n\n");
                    result.append("键: ").append(key).append("\n");
                    result.append("（该键不是预定义配置项，已从配置文件中删除）\n");
                    result.append("\n").append(removeResult);
                    return result.toString();
                }
                return "✗ 错误：删除配置项失败\n" + removeResult;
            }
            return "✗ 错误：配置项 '" + key + "' 不存在";
        }

        // 设置默认值
        String setResult = setProperty(key, defaultValue);
        result.append("✓ 配置已重置为默认值\n");
        result.append("===================\n\n");
        result.append("键: ").append(key).append("\n");
        result.append("默认值: ").append(defaultValue).append("\n");
        result.append("\n").append(setResult);
        return result.toString();
    }

    /**
     * 从settings.properties中删除指定key的属性
     * @param key 属性键
     * @return 删除结果消息
     */
    private static String removePropertyFromSettings(String key) {
        if (key == null || key.isEmpty()) {
            return "✗ 错误：属性键不能为空";
        }

        Path settingsPath = Workspace.getSettingsFile();
        if (!Files.exists(settingsPath)) {
            return "✗ 错误：配置文件不存在: " + settingsPath.toAbsolutePath();
        }

        // 读取现有内容
        List<String> lines = new ArrayList<>();
        String oldValue = null;
        try (BufferedReader reader = Files.newBufferedReader(settingsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String configKey = line.substring(0, equalIndex).trim();
                    if (configKey.equals(key)) {
                        oldValue = line.substring(equalIndex + 1).trim();
                    }
                }
            }
        } catch (IOException e) {
            return "✗ 错误：无法读取settings.properties: " + e.getMessage();
        }

        if (oldValue == null) {
            return "✗ 错误：配置项 '" + key + "' 不存在";
        }

        // 重写文件，跳过该key的行
        try (BufferedWriter writer = Files.newBufferedWriter(settingsPath, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    int equalIndex = line.indexOf('=');
                    if (equalIndex > 0 && line.substring(0, equalIndex).trim().equals(key)) {
                        continue; // 跳过被删除的key
                    }
                }
                writer.write(line);
                writer.write("\n");
            }
        } catch (IOException e) {
            return "✗ 错误：无法写入settings.properties: " + e.getMessage();
        }

        StringBuilder result = new StringBuilder();
        result.append("✓ 属性已删除\n");
        result.append("===================\n\n");
        result.append("配置项: ").append(key).append("\n");
        result.append("旧值: ").append(oldValue).append("\n\n");
        result.append("配置文件: ").append(settingsPath.toAbsolutePath()).append("\n");

        return result.toString();
    }
}
