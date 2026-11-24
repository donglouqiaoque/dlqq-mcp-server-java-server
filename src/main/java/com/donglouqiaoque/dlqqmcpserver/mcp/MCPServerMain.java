package com.donglouqiaoque.dlqqmcpserver.mcp;

import com.donglouqiaoque.dlqqmcpserver.BuildInfo;
import com.donglouqiaoque.dlqqmcpserver.mcp.server.McpServer;
import com.donglouqiaoque.dlqqmcpserver.mcp.tools.AITools;
import com.donglouqiaoque.dlqqmcpserver.mcp.tools.LogoLoader;
import com.donglouqiaoque.dlqqmcpserver.mcp.tools.McpTools;
import com.donglouqiaoque.dlqqmcpserver.mcp.transport.HttpTransport;
import com.donglouqiaoque.dlqqmcpserver.mcp.transport.McpTransport;
import com.donglouqiaoque.dlqqmcpserver.mcp.transport.StdioTransport;
import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;
import com.donglouqiaoque.dlqqmcpserver.utils.WorkspaceRegistry;
import com.formdev.flatlaf.FlatDarkLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * MCP服务器入口
 */
public class MCPServerMain {

    // Logger 在工作区确定后、日志目录创建完成后初始化
    private static Logger logger;

    /**
     * 初始化日志文件与 stderr 重定向：统一写到
     * {@code <部署目录>/.dlqq/log/<transport>/<启动时间>.log}。
     * 同一秒多次启动时由 {@link RuntimeDirs#createLogFile(String)} 追加 -2、-3 后缀。
     */
    private static void initLogging() {
        try {
            String transportName = httpMode ? "http" : "stdio";
            Path logFile = RuntimeDirs.createLogFile(transportName);
            System.setProperty("org.slf4j.simpleLogger.logFile", logFile.toAbsolutePath().toString());
            // 设置为追加模式
            System.setProperty("org.slf4j.simpleLogger.append", "true");

            PrintStream stderrStream = new PrintStream(
                    new FileOutputStream(logFile.toFile(), true), true, "UTF-8");
            System.setErr(stderrStream);
        } catch (Exception e) {
            // 日志文件创建失败由 RuntimeDirs 处理为启动失败；这里只兜底保留默认 stderr
            System.err.println("[MCPServerMain] initLogging failed: " + e.getMessage());
        }
    }

    // 全局变量用于健康检查
    private static volatile boolean isRunning = true;
    private static McpTransport transport;
    private static McpServer server;

    /** 启动时记录的服务器 JAR 修改时间、大小与清单构建时间（运行中被替换会导致类懒加载失败，必须主动退出）。 */
    private static long jarInitialModified = -1L;
    private static long jarInitialSize = -1L;
    private static String jarBuildTimeOnDisk = null;

    /** 重启前等待前台工具调用结束的最长时间（毫秒），超时强制退出。 */
    private static final long TOOL_DRAIN_TIMEOUT_MILLIS = 5_000L;
    /** 重启提示窗至少保留时间（毫秒），确保用户能看到弹窗。 */
    private static final long MIN_RESTART_NOTICE_MILLIS = 2_000L;

    // 传输方式配置（默认 stdio）
    private static boolean httpMode = false;
    private static int httpPort = 49129;
    private static String httpPath = "/mcp";
    /** 启动参数指定的工作区目录（--workspace） */
    private static String workspacePath = null;
    /** 启动参数指定的会话标识键（--session-meta-key，默认 dsh/sessionId） */
    private static String sessionMetaKey = null;
    /** 启动参数指定的 Agent 标识键（--session-agent-meta-key，默认 dsh/agentId） */
    private static String sessionAgentMetaKey = null;
    /** 启动参数指定的会话工作区标识键（--session-workspace-meta-key，默认 dsh/workspace） */
    private static String sessionWorkspaceMetaKey = null;
    /** 启动参数指定的会话空闲回收分钟数（--session-idle-minutes，默认 30，0 = 不回收） */
    private static int sessionIdleMinutes = 30;

    /**
     * 初始化 Swing 外观（FlatLaf 深色主题），作用于所有工具窗口与对话框。
     * 无图形环境时跳过；失败只记录日志，不影响服务启动。
     */
    private static void initLookAndFeel() {
        if (GraphicsEnvironment.isHeadless()) {
            logger.info("Headless environment detected, skip look and feel initialization");
            return;
        }
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            logger.info("Look and feel: FlatDarkLaf");
        } catch (Exception e) {
            logger.warn("FlatLaf initialization failed, keep default look and feel: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        // 解析命令行参数（--transport http|stdio, --port N, --path /mcp, --workspace <dir>,
        // --session-meta-key <key>, --session-agent-meta-key <key>, --session-workspace-meta-key <key>,
        // --session-idle-minutes <n>）
        parseArgs(args);

        // 初始化全局运行时目录：<jar 目录>/.dlqq 与 default_workspace（失败则启动失败并弹窗）
        RuntimeDirs.init();
        WorkspaceRegistry.init();

        // 初始化默认工作区：--workspace > 进程 CWD；不可用时回退 default_workspace
        Workspace.init(workspacePath);

        // 配置会话隔离参数（单进程多会话 × 多 Agent，含客户端下发的会话工作区）
        SessionScope.configure(sessionMetaKey, sessionAgentMetaKey, sessionWorkspaceMetaKey, sessionIdleMinutes);
        SessionScope.startSweeper();

        // 设置UTF-8编码
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.stdout.encoding", "UTF-8");
        System.setProperty("sun.stderr.encoding", "UTF-8");

        // 初始化全局日志文件（.dlqq/log/<transport>/<启动时间>.log）并创建 Logger
        initLogging();
        logger = LoggerFactory.getLogger(MCPServerMain.class);

        // 统一 Swing 外观：FlatLaf 深色主题，作用于所有工具窗口与对话框
        initLookAndFeel();

        // 预加载窗口 LOGO：运行中替换 JAR 后资源查找会失败，缓存可保证图标始终可用
        LogoLoader.preload();

        // 确保当前工作区的 settings.properties 存在（全局 .dlqq/settings/<hash>/）
        Workspace.ensureSettingsFileFor(Workspace.getRoot());

        // 重新初始化 stdout 的编码
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (Exception ignored) {
            // stdout 编码设置失败不影响协议（JSON-RPC 按行写入）
        }

        logger.info("MCP Server starting...");
        logger.info("Working directory: {}", System.getProperty("user.dir"));
        logger.info("Deploy directory: {}", RuntimeDirs.getAppDir());
        logger.info("Runtime directory: {}", RuntimeDirs.getRoot());
        logger.info("Workspace: {} (source: {})", Workspace.getRoot(), Workspace.getSource());
        logger.info("Default charset: {}", Charset.defaultCharset().name());
        logger.info("Log file: {}", RuntimeDirs.getCurrentLogFile());

        // 记录 JAR 基线：运行中若 JAR 被替换（构建/覆盖），健康检查会主动退出，避免客户端请求超时
        recordJarBaseline();
        
        try {
            startServer();

            // 服务端控制台窗口：查看活跃会话、设置会话工作区与权限；关闭窗口即终止服务器进程
            ServerConsoleWindow.start();

            // 启动健康检查线程
            startHealthCheckThread();

            // 启动 JAR 替换监视：每 2 秒比对磁盘 JAR 清单构建时间与内存 BuildInfo.BUILD_TIME；
            // 不一致（或文件被覆盖）说明运行代码已落后，立即退出等待客户端重新拉起进程
            startJarVersionWatcher();
            // 主线程保持运行
            while (isRunning) {
                Thread.sleep(1000);
            }
            
        } catch (IOException e) {
            logger.error("MCP server failed to start: {}", e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.warn("MCP server interrupted");
            isRunning = false;
        } finally {
            cleanup();
        }
    }
    
    /**
     * 解析命令行参数
     * 支持: --transport http|stdio, --port N, --path /mcp, --workspace <dir>,
     *       --session-meta-key <key>, --session-agent-meta-key <key>,
     *       --session-workspace-meta-key <key>, --session-idle-minutes <n>
     */
    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--transport":
                    if (i + 1 < args.length) {
                        String t = args[++i].toLowerCase();
                        httpMode = "http".equals(t);
                        if (!"http".equals(t) && !"stdio".equals(t)) {
                            logger.warn("[MCPServerMain] Unknown transport: {}, using default stdio", t);
                            httpMode = false;
                        }
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            httpPort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            logger.warn("[MCPServerMain] Invalid port: {}, using default 49129", args[i]);
                        }
                    }
                    break;
                case "--path":
                    if (i + 1 < args.length) {
                        httpPath = args[++i];
                        if (!httpPath.startsWith("/")) {
                            httpPath = "/" + httpPath;
                        }
                    }
                    break;
                case "--workspace":
                    if (i + 1 < args.length) {
                        workspacePath = args[++i];
                    }
                    break;
                case "--session-meta-key":
                    if (i + 1 < args.length) {
                        sessionMetaKey = args[++i];
                    }
                    break;
                case "--session-agent-meta-key":
                    if (i + 1 < args.length) {
                        sessionAgentMetaKey = args[++i];
                    }
                    break;
                case "--session-workspace-meta-key":
                    if (i + 1 < args.length) {
                        sessionWorkspaceMetaKey = args[++i];
                    }
                    break;
                case "--session-idle-minutes":
                    if (i + 1 < args.length) {
                        try {
                            sessionIdleMinutes = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            logger.warn("[MCPServerMain] Invalid session idle minutes: {}, using default 30", args[i]);
                        }
                    }
                    break;
                default:
                    // 忽略未知参数
                    break;
            }
        }
    }

    /**
     * 启动MCP服务器
     */
    private static void startServer() throws IOException {
        // 创建传输层（stdio 或 http）
        HttpTransport httpTransport = null;
        if (httpMode) {
            httpTransport = new HttpTransport(httpPort, httpPath);
            transport = httpTransport;
            logger.info("Transport created: HTTP (port={}, path={})", httpPort, httpPath);
        } else {
            transport = new StdioTransport();
            logger.info("Transport created: STDIO");
        }

        // 创建MCP服务器
        String version = MCPServerMain.class.getPackage().getImplementationVersion();
        if (version == null || version.isEmpty()) {
            version = "255.0.0";
        }
        server = new McpServer(
            "dlqq-mcp-server",
            version,
            transport
        );
        logger.debug("Server created");

        // 注册工具
        AITools aiTools = new AITools();
        aiTools.registerTools(server);
        logger.debug("Tools registered");

        // 应用持久化的“被移除工具”列表（.dlqq/settings/settings.properties 的 mcp.tools.excluded）
        int excluded = server.applyExcludedTools(
                McpTools.getExcludedToolNames());
        logger.info("Persisted removed tools: {}, enabled tools: {}", excluded, server.getEnabledToolCount());

        // 启动服务器
        server.start();
        logger.info("========================================");
        logger.info("MCP Server started successfully!");
        logger.info("Server name: dlqq-mcp-server");
        logger.info("Server version: {}", version);
        if (httpMode) {
            int actualPort = httpTransport != null ? httpTransport.getPort() : httpPort;
            logger.info("Transport: HTTP (Streamable HTTP)");
            logger.info("Endpoint: http://localhost:{}{}", actualPort, httpPath);
            // 双击 bat / 控制台启动时给出可见提示（端口可能因冲突被用户改过）
            System.out.println("[HTTP] MCP server is running: http://localhost:" + actualPort + httpPath);
            System.out.println("[HTTP] Press Ctrl+C to stop.");
            System.out.flush();
        } else {
            logger.info("Transport: STDIO");
        }
        logger.info("Waiting for requests...");
        logger.info("========================================");
    }
    
    /**
     * 清理资源
     */
    private static void cleanup() {
        logger.info("MCP Server cleaning up...");
        isRunning = false;
        if (server != null) {
            try {
                server.stop();
            } catch (IOException e) {
                logger.error("Error stopping server: {}", e.getMessage(), e);
            }
        }
        logger.info("MCP Server exited");
    }
    
    /**
     * 记录启动时服务器 JAR 的修改时间、大小与清单构建时间，用于运行期替换检测。
     */
    private static void recordJarBaseline() {
        try {
            Path jar = RuntimeDirs.getServerJar();
            jarInitialModified = Files.getLastModifiedTime(jar).toMillis();
            jarInitialSize = Files.size(jar);
            jarBuildTimeOnDisk = readBuildTimeFromJar(jar);
            logger.info("Server JAR baseline: modified={}, size={}, manifest build time={}",
                    jarInitialModified, jarInitialSize, jarBuildTimeOnDisk);
        } catch (IOException e) {
            logger.warn("Cannot record server JAR baseline: {}", e.getMessage());
        }
    }

    /** 读取 JAR 清单中的 Build-Time 属性（UTC，{@code yyyy-MM-dd HH:mm:ss}）；读取失败返回 null。 */
    private static String readBuildTimeFromJar(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return null;
            }
            Attributes attributes = manifest.getMainAttributes();
            return attributes.getValue("Build-Time");
        } catch (IOException | RuntimeException e) {
            logger.warn("[JarWatcher] Cannot read manifest build time: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 服务器 JAR 是否已在运行期被替换。
     *
     * <p>优先比较内存中编译期常量 {@link BuildInfo#BUILD_TIME} 与磁盘 JAR 清单的 Build-Time：
     * 两者不一致说明磁盘上已是新构建；清单不可读时退回比较修改时间/大小。</p>
     */
    private static boolean serverJarChanged() {
        if (jarInitialModified < 0) {
            return false;
        }
        Path jar = RuntimeDirs.getServerJar();
        String diskBuildTime = readBuildTimeFromJar(jar);
        if (diskBuildTime != null && !diskBuildTime.equals(BuildInfo.BUILD_TIME)) {
            logger.warn("[JarWatcher] In-memory build {} differs from JAR manifest build {}",
                    BuildInfo.BUILD_TIME, diskBuildTime);
            return true;
        }
        try {
            return Files.getLastModifiedTime(jar).toMillis() != jarInitialModified
                    || Files.size(jar) != jarInitialSize;
        } catch (IOException e) {
            logger.warn("[JarWatcher] Cannot check server JAR: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 运行中 JAR 被替换：弹窗提示后等待前台工具调用全部结束，再退出进程；客户端自动重连并拉起新进程。
     *
     * <p>规则：期间拒绝新的工具调用；后台（no_wait）调用不等待，直接随进程结束；
     * 前台调用最多等待 {@link #TOOL_DRAIN_TIMEOUT_MILLIS}（5 秒），超时强制退出；
     * 提示窗至少保留 {@link #MIN_RESTART_NOTICE_MILLIS}（2 秒），保证用户能看到弹窗。</p>
     */
    private static void exitBecauseServerJarReplaced() {
        long noticedAt = System.currentTimeMillis();
        logger.error("[JarWatcher] Server JAR was replaced or rebuilt; draining foreground tool calls before exit");
        System.err.println("[dlqq-mcp-server] JAR version changed at runtime; waiting for foreground tool calls to finish.");
        showRestartNotice("检测到 MCP 服务器 JAR 已被重新构建/覆盖。\n正在等待进行中的工具调用结束（最多 5 秒），随后自动重启并重新连接。");
        isRunning = false;
        if (server != null) {
            server.beginDrain();
        }
        long deadline = noticedAt + TOOL_DRAIN_TIMEOUT_MILLIS;
        int lastReported = -1;
        while (System.currentTimeMillis() < deadline) {
            int active = server == null ? 0 : server.activeToolCallCount();
            if (active == 0) {
                break;
            }
            if (active != lastReported) {
                logger.info("[JarWatcher] Waiting for {} foreground tool call(s) to finish before restart", active);
                lastReported = active;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        int remaining = server == null ? 0 : server.activeToolCallCount();
        if (remaining > 0) {
            logger.warn("[JarWatcher] Foreground tool calls still running after {}ms ({} call(s)); forcing exit",
                    TOOL_DRAIN_TIMEOUT_MILLIS, remaining);
        } else {
            logger.info("[JarWatcher] All foreground tool calls finished; exiting after minimum notice time");
        }
        // 提示窗至少保留 2 秒：工具在 2 秒内结束则补足到 2 秒；超过 2 秒则按工具实际结束时间退出
        long elapsed = System.currentTimeMillis() - noticedAt;
        long holdMillis = MIN_RESTART_NOTICE_MILLIS - elapsed;
        if (holdMillis > 0) {
            logger.info("[JarWatcher] Holding restart notice for another {}ms", holdMillis);
            try {
                Thread.sleep(holdMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.exit(0);
    }

    /**
     * 显示"服务即将自动重启"提示窗（非模态、置顶）。
     *
     * <p>无图形环境时直接跳过；弹窗失败不影响重启流程。</p>
     */
    private static void showRestartNotice(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                JOptionPane pane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE);
                JDialog dialog = pane.createDialog(null, "dlqq-mcp-server");
                dialog.setModal(false);
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
            } catch (RuntimeException e) {
                logger.warn("[JarWatcher] Cannot show restart notice: {}", e.getMessage());
            }
        });
    }

    /**
     * JAR 版本监视线程：每 2 秒检查磁盘 JAR 是否被构建/覆盖，变化则退出等待客户端重新连接。
     */
    private static void startJarVersionWatcher() {
        Thread watcher = new Thread(() -> {
            final long interval = 2000L;
            logger.info("[JarWatcher] JAR change watcher started, interval={}ms, build={}",
                    interval, BuildInfo.BUILD_TIME);
            while (isRunning) {
                try {
                    Thread.sleep(interval);
                    if (!isRunning) {
                        break;
                    }
                    if (serverJarChanged()) {
                        exitBecauseServerJarReplaced();
                    }
                } catch (InterruptedException e) {
                    logger.warn("[JarWatcher] JAR watcher interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (RuntimeException e) {
                    logger.warn("[JarWatcher] Watcher check failed: {}", e.getMessage());
                }
            }
            logger.info("[JarWatcher] JAR watcher exited");
        });
        watcher.setDaemon(true);
        watcher.setName("MCP-JarWatcher");
        watcher.start();
    }

    /**
     * 启动健康检查线程
     * 监控服务器状态，如果传输层断开则尝试恢复
     */
    private static void startHealthCheckThread() {
        Thread healthCheck = new Thread(() -> {
            int checkInterval = 5000;  // 每5秒检查一次
            int errorCount = 0;
            final int MAX_ERROR_COUNT = 3;
            int checkCount = 0;
            long lastMemoryLog = System.currentTimeMillis();

            logger.info("[HealthCheck] Health check thread started, interval={}ms", checkInterval);

            while (isRunning) {
                try {
                    Thread.sleep(checkInterval);
                    checkCount++;

                    // 每30秒输出一次内存状态
                    if (System.currentTimeMillis() - lastMemoryLog > 30000) {
                        Runtime runtime = Runtime.getRuntime();
                        long totalMemory = runtime.totalMemory() / 1024 / 1024;
                        long freeMemory = runtime.freeMemory() / 1024 / 1024;
                        long maxMemory = runtime.maxMemory() / 1024 / 1024;
                        logger.info("[HealthCheck] JVM memory - total:{}MB, free:{}MB, max:{}MB, used:{}MB",
                                totalMemory, freeMemory, maxMemory, totalMemory - freeMemory);
                        lastMemoryLog = System.currentTimeMillis();
                    }

                    // 检查传输层是否还在运行
                    if (transport != null) {
                        boolean transportRunning = transport.isRunning();
                        if (checkCount % 12 == 0) {  // 每分钟输出一次状态
                            logger.info("[HealthCheck] Status OK - checks={}, transport={}", checkCount, transportRunning ? "running" : "stopped");
                        }

                        if (!transportRunning) {
                            logger.warn("[HealthCheck] Transport stopped!");
                            errorCount++;

                            if (errorCount >= MAX_ERROR_COUNT) {
                                logger.error("[HealthCheck] {} consecutive check failures, restarting...", MAX_ERROR_COUNT);
                                try {
                                    // 停止旧服务器
                                    if (server != null) {
                                        logger.info("[HealthCheck] Stopping old server...");
                                        server.stop();
                                    }
                                    // 重新启动
                                    logger.info("[HealthCheck] Restarting server...");
                                    startServer();
                                    errorCount = 0;
                                    logger.info("[HealthCheck] Server restarted successfully");
                                } catch (IOException e) {
                                    logger.error("[HealthCheck] Restart failed: {}", e.getMessage(), e);
                                }
                            }
                        } else {
                            if (errorCount > 0) {
                                logger.info("[HealthCheck] Transport recovered, resetting error count");
                            }
                            errorCount = 0;  // 重置错误计数
                        }
                    } else {
                        logger.warn("[HealthCheck] transport is null");
                    }
                } catch (InterruptedException e) {
                    logger.warn("[HealthCheck] Health check thread interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            logger.info("[HealthCheck] Health check thread exited, total checks={}", checkCount);
        });
        healthCheck.setDaemon(true);
        healthCheck.setName("MCP-HealthCheck");
        healthCheck.start();
        logger.info("[HealthCheck] Health check thread started");
    }
}
