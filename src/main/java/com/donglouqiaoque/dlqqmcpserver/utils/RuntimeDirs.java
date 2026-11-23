package com.donglouqiaoque.dlqqmcpserver.utils;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 运行时目录：与 jar 同级的唯一数据根目录。
 *
 * <p>部署目录下固定包含：
 * <ul>
 *   <li>{@code .dlqq/}：全局运行时目录（cache/data/log/settings/work区映射）；</li>
 *   <li>{@code default_workspace/}：兜底工作区，仅在工作区不可用时使用。</li>
 * </ul>
 *
 * <p>这些目录无法创建或不可写时视为启动失败：弹窗提示、stderr 输出完整原因、
 * 并以 {@link IllegalStateException} 终止启动（不回退用户目录）。</p>
 */
public final class RuntimeDirs {

    /** 运行时目录名。 */
    public static final String DLQQ_DIR_NAME = ".dlqq";
    /** 兜底工作区目录名。 */
    public static final String DEFAULT_WORKSPACE_NAME = "default_workspace";

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String GIT_IGNORE_CONTENT = "*\n";
    private static final String WRITE_PROBE_PREFIX = ".write-probe-";

    private static volatile boolean initialized;
    private static volatile Path appDir;
    private static volatile Path dlqqRoot;
    private static volatile Path defaultWorkspace;
    private static volatile Path currentLogFile;

    private RuntimeDirs() {
    }

    /**
     * 初始化运行时目录（幂等，可重复调用）。
     *
     * @throws IllegalStateException 目录无法创建或不可写（启动失败）
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        Path base = resolveAppDir();
        Path root = base.resolve(DLQQ_DIR_NAME).toAbsolutePath().normalize();
        Path workspace = base.resolve(DEFAULT_WORKSPACE_NAME).toAbsolutePath().normalize();
        try {
            createDirectories(root);
            ensureWritable(root);
            createDirectories(root.resolve("cache"));
            createDirectories(root.resolve("data"));
            createDirectories(root.resolve("log"));
            createDirectories(root.resolve("settings"));
            ensureGitIgnore(root);
            createDirectories(workspace);
            if (!Files.isDirectory(workspace)) {
                throw new IOException("默认工作区不是目录");
            }
            appDir = base;
            dlqqRoot = root;
            defaultWorkspace = workspace;
            initialized = true;
        } catch (Exception e) {
            failStartup("初始化运行时目录失败：" + root, e);
        }
    }

    /**
     * 为本次进程创建日志文件：{@code <root>/log/<transport>/<启动时间>.log}。
     * 同一秒多次启动时依次追加 {@code -2}、{@code -3} 后缀。
     *
     * @param transport 传输方式（stdio / http），空值按 stdio 处理
     * @return 新建的日志文件绝对路径
     */
    public static synchronized Path createLogFile(String transport) {
        init();
        String dirName = isBlank(transport) ? "stdio" : transport.trim().toLowerCase(Locale.ROOT);
        Path dir = dlqqRoot.resolve("log").resolve(dirName);
        try {
            createDirectories(dir);
        } catch (IOException e) {
            failStartup("创建日志目录失败：" + dir, e);
        }
        String baseName = LocalDateTime.now().format(LOG_TIME_FORMAT);
        Path file = dir.resolve(baseName + ".log");
        int suffix = 2;
        while (Files.exists(file)) {
            file = dir.resolve(baseName + "-" + suffix + ".log");
            suffix++;
        }
        try {
            Files.createFile(file);
        } catch (IOException e) {
            failStartup("创建日志文件失败：" + file, e);
        }
        currentLogFile = file;
        return file;
    }

    /** 启动失败：输出 stderr、弹窗提示，并抛出终止启动的异常。 */
    public static void failStartup(String summary, Throwable cause) {
        String detail = cause == null || cause.getMessage() == null ? "" : "：" + cause.getMessage();
        String message = summary + detail;
        System.err.println("[RuntimeDirs] 启动失败: " + message);
        if (cause != null) {
            cause.printStackTrace(System.err);
        }
        showErrorPopup(message);
        throw new IllegalStateException(message, cause);
    }

    /** 部署目录（jar 所在目录；开发态回退进程 CWD）。 */
    public static Path getAppDir() {
        ensureInitialized();
        return appDir;
    }

    /** 服务器 JAR 文件（{@code <部署目录>/dlqq_mcp_server.jar}），用于运行期替换检测。 */
    public static Path getServerJar() {
        ensureInitialized();
        return appDir.resolve("dlqq_mcp_server.jar");
    }

    /** 全局运行时目录 {@code <部署目录>/.dlqq}。 */
    public static Path getRoot() {
        ensureInitialized();
        return dlqqRoot;
    }

    /** 兜底工作区 {@code <部署目录>/default_workspace}。 */
    public static Path getDefaultWorkspace() {
        ensureInitialized();
        return defaultWorkspace;
    }

    /** cache 根目录。 */
    public static Path getCacheRoot() {
        return getRoot().resolve("cache");
    }

    /** data 根目录（全局共享）。 */
    public static Path getDataRoot() {
        return getRoot().resolve("data");
    }

    /** log 根目录。 */
    public static Path getLogRoot() {
        return getRoot().resolve("log");
    }

    /** settings 根目录。 */
    public static Path getSettingsRoot() {
        return getRoot().resolve("settings");
    }

    /** 工作区映射文件。 */
    public static Path getWorkspaceConfigFile() {
        return getRoot().resolve("workspace_config.json");
    }

    /** 本次进程的日志文件（未创建日志时为 null）。 */
    public static Path getCurrentLogFile() {
        return currentLogFile;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }

    /** 从 CodeSource 定位部署目录；开发态（类目录）回退进程 CWD。 */
    private static Path resolveAppDir() {
        try {
            URL location = RuntimeDirs.class.getProtectionDomain().getCodeSource().getLocation();
            Path path = Paths.get(location.toURI()).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path.getParent();
            }
        } catch (Exception e) {
            System.err.println("[RuntimeDirs] 无法从 CodeSource 定位部署目录: " + e.getMessage());
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static void createDirectories(Path dir) throws IOException {
        Files.createDirectories(dir);
        if (!Files.isDirectory(dir)) {
            throw new IOException("不是目录");
        }
    }

    /** 真实写一个探针文件再删除，确认目录可写（Windows ACL 下 Files.isWritable 不可靠）。 */
    private static void ensureWritable(Path dir) throws IOException {
        Path probe = dir.resolve(WRITE_PROBE_PREFIX + System.nanoTime());
        try {
            Files.writeString(probe, "probe", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    private static void ensureGitIgnore(Path root) {
        Path file = root.resolve(".gitignore");
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, GIT_IGNORE_CONTENT,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            // 仅用于忽略运行时数据，失败不影响启动
            System.err.println("[RuntimeDirs] 创建 .gitignore 失败: " + e.getMessage());
        }
    }

    private static void showErrorPopup(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null,
                    "dlqq-mcp-server 启动失败：\n\n" + message
                            + "\n\n请检查部署目录（jar 所在目录）的写权限后重试。",
                    "dlqq-mcp-server 启动失败", JOptionPane.ERROR_MESSAGE));
        } catch (Exception e) {
            System.err.println("[RuntimeDirs] 无法显示启动失败弹窗: " + e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
