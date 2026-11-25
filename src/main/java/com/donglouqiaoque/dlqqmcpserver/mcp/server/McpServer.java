package com.donglouqiaoque.dlqqmcpserver.mcp.server;

import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ApprovalPrompt;
import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ToolApprovals;
import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ToolPolicy;
import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ToolTimeoutContext;
import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.JsonRpcMessage;
import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.McpSchema.*;
import com.donglouqiaoque.dlqqmcpserver.mcp.tools.DialogTools;
import com.donglouqiaoque.dlqqmcpserver.mcp.transport.McpTransport;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP服务器核心类
 */
public class McpServer {

    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);

    private final String serverName;
    private final String serverVersion;
    private final McpTransport transport;
    private final Map<String, ToolHandler> toolHandlers;
    private final Map<String, ResourceHandler> resourceHandlers;
    private final Map<String, PromptHandler> promptHandlers;
    private final Capabilities capabilities;

    private boolean initialized = false;
    private String clientProtocolVersion;
    private int toolsListRequestCount = 0;  // 记录tools/list请求次数

    /** 兜底通知默认延时（毫秒），可用 -Dmcp.listChanged.delay.ms 覆盖 */
    private static final long DEFAULT_TOOLS_NOTIFY_DELAY_MS = 10_000L;
    /** 兜底通知调度器（守护线程，避免阻止进程退出） */
    private final ScheduledExecutorService notificationScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "tools-list-changed-notifier");
                thread.setDaemon(true);
                return thread;
            });
    /** 当前待执行的兜底通知任务 */
    private volatile ScheduledFuture<?> pendingToolsNotification;
    /** 客户端是否已请求过 tools/list（请求过后不再推送兜底通知） */
    private volatile boolean toolsListRequested = false;
    /** 兜底通知是否已经发送过（只发一次） */
    private volatile boolean fallbackToolsNotificationSent = false;

    /** 正在执行的工具调用数量（JAR 更新时等待最后一个工具调用结束）。 */
    private final AtomicInteger activeToolCalls = new AtomicInteger();
    /** 是否已进入重启排水状态：不再接受新的工具调用。 */
    private volatile boolean draining = false;

    public McpServer(String serverName, String serverVersion, McpTransport transport) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.transport = transport;
        this.toolHandlers = new ConcurrentHashMap<>();
        this.resourceHandlers = new ConcurrentHashMap<>();
        this.promptHandlers = new ConcurrentHashMap<>();
        this.capabilities = new Capabilities();

        // 注册默认的处理程序
        registerDefaultHandlers();
    }

    /**
     * 通知客户端工具列表已变化（notifications/tools/list_changed）。
     *
     * @return true 表示通知已通过至少一个通道发送
     */
    public boolean notifyToolsListChanged() {
        boolean sent = transport.notifyToolsListChanged();
        logger.info("[McpServer] Sent notifications/tools/list_changed, result={}", sent);
        return sent;
    }

    /**
     * 动态工具管理：被移除（禁用）的工具集合。
     * 工具本身仍保留在 toolHandlers 中，便于 reset 时恢复。
     */
    private final Set<String> disabledTools = ConcurrentHashMap.newKeySet();

    /** 不允许被移除的管理工具（否则无法恢复） */
    private static final Set<String> PROTECTED_TOOLS = Set.of(
            "dlqq_mcp_remove_tools",
            "dlqq_mcp_refresh_tools",
            "dlqq_mcp_reset_tools",
            "dlqq_mcp_run_tools");

    /**
     * 动态工具变更结果。
     */
    public static class ToolChangeResult {
        /** 本次实际移除/恢复的工具 */
        public final List<String> applied = new ArrayList<>();
        /** 不存在或未启用的工具 */
        public final List<String> unknown = new ArrayList<>();
        /** 受保护、不允许移除的管理工具 */
        public final List<String> protectedNames = new ArrayList<>();
        /** 变更后处于启用状态的工具数量 */
        public int enabledCount;
        /** 变更后处于移除状态的工具数量 */
        public int disabledCount;
    }

    /**
     * 是否存在指定工具（含已被移除的工具）。
     */
    public boolean hasTool(String name) {
        return toolHandlers.containsKey(name);
    }

    /**
     * 当前被移除（禁用）的工具列表。
     */
    public Set<String> getDisabledTools() {
        return new LinkedHashSet<>(disabledTools);
    }

    /**
     * 当前启用（对客户端可见）的工具数量。
     */
    public int getEnabledToolCount() {
        return toolHandlers.size() - disabledTools.size();
    }

    /**
     * 进入重启排水状态：拒绝新的工具调用，等待进行中的调用结束。
     */
    public void beginDrain() {
        draining = true;
        logger.info("[McpServer] Drain requested, active tool calls: {}", activeToolCalls.get());
    }

    /** 当前进行中的工具调用数量。 */
    public int activeToolCallCount() {
        return activeToolCalls.get();
    }

    /** 是否已进入排水状态。 */
    public boolean isDraining() {
        return draining;
    }

    /**
     * 启动时应用持久化的排除列表（.dlqq/settings/settings.properties 的 mcp.tools.excluded）。
     *
     * @param names 要移除的工具名
     * @return 实际生效的数量
     */
    public int applyExcludedTools(Collection<String> names) {
        int applied = 0;
        if (names == null) {
            return 0;
        }
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            String toolName = name.trim();
            if (PROTECTED_TOOLS.contains(toolName) || !toolHandlers.containsKey(toolName)) {
                logger.warn("[McpServer] Ignored invalid excluded tool at startup: {}", toolName);
                continue;
            }
            if (disabledTools.add(toolName)) {
                applied++;
            }
        }
        logger.info("[McpServer] Applied {} excluded tools at startup, enabled tools={}", applied, getEnabledToolCount());
        return applied;
    }

    /**
     * 运行时移除工具（仅禁用，不删除注册信息，便于 reset 恢复）。
     */
    public ToolChangeResult disableTools(Collection<String> names) {
        ToolChangeResult result = new ToolChangeResult();
        if (names != null) {
            for (String name : names) {
                if (name == null || name.trim().isEmpty()) {
                    continue;
                }
                String toolName = name.trim();
                if (PROTECTED_TOOLS.contains(toolName)) {
                    result.protectedNames.add(toolName);
                } else if (!toolHandlers.containsKey(toolName)) {
                    result.unknown.add(toolName);
                } else if (disabledTools.add(toolName)) {
                    result.applied.add(toolName);
                }
            }
        }
        result.enabledCount = getEnabledToolCount();
        result.disabledCount = disabledTools.size();
        logger.info("[McpServer] Removed tools: applied={}, unknown={}, protected={}, enabled={}",
                result.applied, result.unknown, result.protectedNames, result.enabledCount);
        return result;
    }

    /**
     * 恢复所有被移除的工具。
     *
     * @return 实际恢复的工具名
     */
    public List<String> enableAllTools() {
        List<String> restored = new ArrayList<>(disabledTools);
        disabledTools.clear();
        logger.info("[McpServer] Restored all removed tools: {}", restored);
        return restored;
    }

    /**
     * 恢复指定被移除的工具（dlqq_mcp_show_tools 使用）。
     *
     * @param names 要恢复的工具名
     * @return 实际恢复的工具名
     */
    public List<String> enableTools(List<String> names) {
        List<String> restored = new ArrayList<>();
        for (String name : names) {
            if (disabledTools.remove(name)) {
                restored.add(name);
            }
        }
        logger.info("[McpServer] Restored tools: {}", restored);
        return restored;
    }

    /**
     * 指定工具当前是否被移除。
     */
    public boolean isToolDisabled(String name) {
        return disabledTools.contains(name);
    }

    /**
     * 直接调用已注册工具，忽略“运行时移除/客户端可见性”限制。
     *
     * <p>供 {@code dlqq_mcp_run_tools} 使用：即使工具已被 {@code dlqq_mcp_remove_tools}
     * 移除、或客户端尚未加载，也按名称直接执行服务端已注册的处理器。</p>
     *
     * @param name      工具名
     * @param arguments 工具参数（可为 null）
     * @return 工具执行结果；工具未注册时返回 null
     * @throws Exception 工具执行过程中的异常
     */
    public CallToolResult invokeToolDirect(String name, Map<String, Object> arguments) throws Exception {
        ToolHandler handler = toolHandlers.get(name);
        if (handler == null) {
            return null;
        }
        logger.info("[McpServer] dlqq_mcp_run_tools direct call: {} (disabled={})", name, disabledTools.contains(name));
        return handler.call(arguments);
    }

    /**
     * 注册默认的处理程序
     */
    private void registerDefaultHandlers() {
        // 初始化
        this.transport.setMessageHandler(this::handleMessage);
    }

    /**
     * 启动服务器
     */
    public void start() throws IOException {
        this.transport.start();
    }

    /**
     * 客户端发来任意消息时调用：取消兜底通知；收到 tools/list 后永久取消。
     *
     * @param method JSON-RPC 方法名
     */
    private void onClientActivity(String method) {
        cancelPendingToolsNotification();
        if ("tools/list".equals(method)) {
            toolsListRequested = true;
            return;
        }
        if ("initialize".equals(method) || "initialized".equals(method)
                || "notifications/initialized".equals(method)) {
            scheduleFallbackToolsNotification();
        }
    }

    /**
     * 初始化后启动兜底计时：若客户端在延时内没有请求 tools/list，则推送一次列表变更通知。
     */
    private void scheduleFallbackToolsNotification() {
        if (toolsListRequested || fallbackToolsNotificationSent) {
            return;
        }
        cancelPendingToolsNotification();
        long delayMs = Long.getLong("mcp.listChanged.delay.ms", DEFAULT_TOOLS_NOTIFY_DELAY_MS);
        pendingToolsNotification = notificationScheduler.schedule(() -> {
            if (!toolsListRequested && !fallbackToolsNotificationSent) {
                fallbackToolsNotificationSent = true;
                logger.info("[McpServer] No tools/list within {}ms after initialize; sending notifications/tools/list_changed", delayMs);
                notifyToolsListChanged();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /** 取消尚未触发的兜底通知。 */
    private void cancelPendingToolsNotification() {
        ScheduledFuture<?> pending = pendingToolsNotification;
        if (pending != null) {
            pending.cancel(false);
            pendingToolsNotification = null;
        }
    }

    /**
     * 停止服务器
     */
    public void stop() throws IOException {
        cancelPendingToolsNotification();
        notificationScheduler.shutdownNow();
        this.transport.stop();
    }

    /**
     * 处理传入的消息
     */
    private void handleMessage(JsonRpcMessage.Request request) {
        String method = request.getMethod();
        // 会话绑定：单进程多会话隔离的统一入口（stdio / http 由传输层解析出 sessionId）；
        // 会话首次出现时同时应用客户端在 _meta 中下发的会话工作区目录
        String previousSession = SessionScope.enter(request.getSessionId(), request.getParams());
        try {
            onClientActivity(method);
            logger.info("[McpServer.handleMessage] Received request: method={}, id={}, session={}, isNotification={}",
                    method, request.getId(), SessionScope.currentSessionId(), request.isNotification());

            try {
                // 检查是否是通知（notification）- id为null
                boolean isNotification = request.isNotification();

                switch (method) {
                    case "server/discover":
                        logger.info("[McpServer.handleMessage] Handling server/discover probe");
                        handleDiscover(request);
                        break;
                    case "initialize":
                        logger.info("[McpServer.handleMessage] Handling initialize request");
                        handleInitialize(request);
                        break;
                    case "initialized":
                    case "notifications/initialized":
                        // 客户端确认初始化完成 - 这是通知，不返回响应
                        logger.info("[McpServer.handleMessage] Handling initialized notification");
                        handleInitialized(request);
                        break;
                    case "tools/list":
                        logger.info("[McpServer.handleMessage] Handling tools/list request");
                        handleToolsList(request);
                        break;
                    case "tools/call":
                        logger.info("[McpServer.handleMessage] Handling tools/call request");
                        handleToolsCall(request);
                        break;
                    case "resources/list":
                        logger.info("[McpServer.handleMessage] Handling resources/list request");
                        handleResourcesList(request);
                        break;
                    case "prompts/list":
                        logger.info("[McpServer.handleMessage] Handling prompts/list request");
                        handlePromptsList(request);
                        break;
                    case "prompts/get":
                        logger.info("[McpServer.handleMessage] Handling prompts/get request");
                        handlePromptsGet(request);
                        break;
                    default:
                        // 对于通知，不发送错误响应
                        if (!isNotification) {
                            logger.warn("[McpServer.handleMessage] Unknown method: {}", method);
                            sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_METHOD_NOT_FOUND,
                                    "Unknown method: " + method);
                        } else {
                            logger.warn("[McpServer.handleMessage] Ignoring unknown notification: {}", method);
                        }
                }
            } catch (Throwable e) {
                logger.error("[McpServer.handleMessage] Error handling request: method={}, error={}", method, e.getMessage(), e);
                try {
                    // 对于通知，不发送错误响应
                    if (request.getId() != null) {
                        sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_INTERNAL_ERROR,
                                "Internal error: " + e.getMessage());
                    } else {
                        logger.error("[McpServer.handleMessage] Error handling notification: {}", e.getMessage(), e);
                    }
                } catch (IOException ioException) {
                    logger.error("[McpServer.handleMessage] Failed to send error response: {}", ioException.getMessage(), ioException);
                }
            }
        } finally {
            SessionScope.exit(previousSession);
        }
    }

    /**
     * 处理初始化请求
     */
    @SuppressWarnings("unchecked")
    private void handleInitialize(JsonRpcMessage.Request request) throws IOException {
        Map<String, Object> params = request.getParams();
        logger.info("[McpServer.handleInitialize] Starting, params={}", params);

        if (params == null) {
            logger.error("[McpServer.handleInitialize] Missing params");
            sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_INVALID_PARAMS, "Missing params");
            return;
        }

        this.clientProtocolVersion = (String) params.get("protocolVersion");
        this.initialized = true;

        logger.info("[McpServer.handleInitialize] Client protocol version: {}", this.clientProtocolVersion);
        logger.info("[McpServer.handleInitialize] Registered tools: {}", getEnabledToolCount());
        logger.info("[McpServer.handleInitialize] Registered resources: {}", resourceHandlers.size());
        logger.info("[McpServer.handleInitialize] Registered prompts: {}", promptHandlers.size());

        // 构建初始化结果 - 使用客户端请求的协议版本
        InitializeResult result = new InitializeResult();
        result.setProtocolVersion(this.clientProtocolVersion);
        result.setCapabilities(this.capabilities);
        result.setServerInfo(new ServerInfo(serverName, serverVersion));

        logger.info("[McpServer.handleInitialize] Sending initialize response...");
        logger.info("[McpServer.handleInitialize] Response: protocolVersion={}, serverName={}, serverVersion={}",
                this.clientProtocolVersion, serverName, serverVersion);
        sendResponse(request.getId(), result);
        logger.info("[McpServer.handleInitialize] Initialize response sent");
    }

    /**
     * 处理初始化确认
     */
    private void handleInitialized(JsonRpcMessage.Request request) {
        // 初始化确认是通知，不需要响应
        // 但需要确认服务器已正确初始化
        logger.info("MCP server initialized, ready for requests");
    }

    /**
     * 处理 {@code server/discover} 探测（MCP 2026-07-28+ 客户端在连接时先发送该请求）。
     *
     * <p>返回服务器支持的协议版本与能力；只声明当前实现实际兼容的 2025 系协议版本，
     * 客户端据此回落到传统 initialize 握手，避免探测失败后杀进程重连。</p>
     */
    private void handleDiscover(JsonRpcMessage.Request request) throws IOException {
        logger.info("[McpServer.handleDiscover] ========== Handling server/discover probe ==========");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supportedVersions", List.of("2025-11-25", "2025-06-18", "2025-03-26"));
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("prompts", Map.of());
        capabilities.put("resources", Map.of());
        result.put("capabilities", capabilities);
        result.put("instructions", "dlqq-mcp-server: MCP tools with dlqq_ prefix");
        sendResponse(request.getId(), result);
        logger.info("[McpServer.handleDiscover] Response sent, supportedVersions={}", result.get("supportedVersions"));
    }

    /**
     * 处理工具列表请求
     */
    private void handleToolsList(JsonRpcMessage.Request request) throws IOException {
        logger.info("[McpServer.handleToolsList] ========== Handling tools/list request ==========");
        long startTime = System.currentTimeMillis();

        List<Tool> tools = new ArrayList<>();
        logger.info("[McpServer.handleToolsList] Building tool list, handler count: {}", toolHandlers.size());

        for (Map.Entry<String, ToolHandler> entry : toolHandlers.entrySet()) {
            if (disabledTools.contains(entry.getKey())) {
                continue;
            }
            ToolHandler handler = entry.getValue();
            Tool tool = new Tool();
            tool.setName(entry.getKey());
            tool.setDescription(handler.getDescription());
            tool.setInputSchema(handler.getInputSchema());
            tools.add(tool);
        }

        logger.info("[McpServer.handleToolsList] Tool list built: {} tools ({} removed)",
                tools.size(), disabledTools.size());

        ListToolsResult result = new ListToolsResult(tools);
        logger.info("[McpServer.handleToolsList] ListToolsResult created");

        long buildTime = System.currentTimeMillis() - startTime;
        logger.info("[McpServer.handleToolsList] Build took {}ms, sending response...", buildTime);

        sendResponse(request.getId(), result);
        logger.info("[McpServer.handleToolsList] ========== tools/list response sent, total {}ms ==========", System.currentTimeMillis() - startTime);
    }

    /**
     * 处理工具调用请求：登记进行中数量；重启排水期间拒绝新调用并返回错误。
     *
     * <p>后台（no_wait）调用不登记、不阻塞重启排水：随进程一起结束。</p>
     */
    private void handleToolsCall(JsonRpcMessage.Request request) throws IOException {
        if (draining) {
            CallToolResult rejected = new CallToolResult();
            rejected.setError(true);
            List<ToolContent> content = new ArrayList<>();
            content.add(ToolContent.error("服务端正在重启，当前请求未执行，请稍后重试"));
            rejected.setContent(content);
            sendResponse(request.getId(), rejected);
            return;
        }
        boolean background = isBackgroundToolCall(request);
        if (!background) {
            activeToolCalls.incrementAndGet();
        }
        try {
            doHandleToolsCall(request);
        } finally {
            if (!background) {
                activeToolCalls.decrementAndGet();
            }
        }
    }

    /** 请求是否为后台（no_wait）工具调用：后台调用不阻塞重启排水。 */
    @SuppressWarnings("unchecked")
    private boolean isBackgroundToolCall(JsonRpcMessage.Request request) {
        Map<String, Object> params = request.getParams();
        if (params == null) {
            return false;
        }
        Object raw = params.get("arguments");
        if (!(raw instanceof Map<?, ?>)) {
            return false;
        }
        return isBackgroundCall((Map<String, Object>) raw);
    }

    /**
     * 工具调用的实际处理。
     */
    @SuppressWarnings("unchecked")
    private void doHandleToolsCall(JsonRpcMessage.Request request) throws IOException {
        Map<String, Object> params = request.getParams();
        if (params == null) {
            sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_INVALID_PARAMS, "Missing params");
            return;
        }

        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        ToolHandler handler = toolHandlers.get(toolName);
        if (handler == null) {
            sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_INVALID_PARAMS,
                    "Tool not found: " + toolName);
            return;
        }
        if (disabledTools.contains(toolName)) {
            sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_INVALID_PARAMS,
                    "工具已被移除: " + toolName + "（可用 dlqq_mcp_reset_tools 恢复）");
            return;
        }

        // 权限策略校验：按风险等级与 AI 权限等级决定 允许 / 需要批准 / 拒绝
        ToolPolicy.Result policyResult = ToolPolicy.decide(toolName, arguments);
        if (policyResult.decision() == ToolPolicy.Decision.ERROR) {
            CallToolResult preflight = new CallToolResult();
            preflight.setError(true);
            List<ToolContent> content = new ArrayList<>();
            content.add(ToolContent.error("✗ " + policyResult.message()));
            preflight.setContent(content);
            sendResponse(request.getId(), preflight);
            return;
        }
        if (policyResult.decision() == ToolPolicy.Decision.DENY) {
            CallToolResult denied = new CallToolResult();
            denied.setError(true);
            List<ToolContent> content = new ArrayList<>();
            content.add(ToolContent.error("✗ 权限拒绝：" + policyResult.message()));
            denied.setContent(content);
            sendResponse(request.getId(), denied);
            return;
        }
        // 参数先校验：未知参数直接报错，不进入审批（避免“批准后才发现参数错误”）
        String argumentError = validateUnknownArguments(handler, arguments);
        if (argumentError != null) {
            CallToolResult invalid = new CallToolResult();
            invalid.setError(true);
            List<ToolContent> content = new ArrayList<>();
            content.add(ToolContent.error(argumentError));
            invalid.setContent(content);
            sendResponse(request.getId(), invalid);
            return;
        }

        // 工具总超时 = 审批时间 + 运行时间，默认 50 秒；后台进程（no_wait）立即返回，不受限。
        int timeoutSeconds = resolveToolTimeoutSeconds(params, arguments);
        boolean background = isBackgroundCall(arguments);
        ToolTimeoutContext.begin(timeoutSeconds);
        try {
            // 需要用户批准：弹出该工具专属审批窗口（内置窗口工具跳过，避免重复批准）
            if (policyResult.decision() == ToolPolicy.Decision.APPROVE
                    && !ToolApprovals.confirm(toolName, arguments)) {
                CallToolResult cancelled = new CallToolResult();
                cancelled.setError(true);
                List<ToolContent> content = new ArrayList<>();
                content.add(ToolContent.error(ToolApprovals.cancelledMessage(toolName)));
                cancelled.setContent(content);
                sendResponse(request.getId(), cancelled);
                return;
            }
            int remainingSeconds = background ? timeoutSeconds : ToolTimeoutContext.remainingSeconds();
            if (!background && remainingSeconds <= 0) {
                DialogTools.closeApprovalWindow();
                CallToolResult timeoutResult = new CallToolResult();
                timeoutResult.setError(true);
                List<ToolContent> content = new ArrayList<>();
                content.add(ToolContent.error("✗ 工具执行超时（总时限 " + timeoutSeconds
                        + " 秒，含审批与运行时间），审批窗口已关闭，操作未执行"));
                timeoutResult.setContent(content);
                sendResponse(request.getId(), timeoutResult);
                return;
            }
            CallToolResult result;
            if (background) {
                result = handler.call(arguments);
            } else {
                final int timeout = remainingSeconds;
                // 关键：工具在子线程执行，必须把当前会话绑定带到子线程，
                // 否则子线程里 SessionScope 为空 → 工作区回落到默认会话（进程 CWD），
                // 导致工作区内路径被误判为工作区外、workspace_get 显示 __default__。
                final String workerSessionId = request.getSessionId();
                CompletableFuture<CallToolResult> future = CompletableFuture.supplyAsync(() -> {
                    String previousSession = SessionScope.enter(workerSessionId);
                    ToolTimeoutContext.begin(timeout);
                    try {
                        return handler.call(arguments);
                    } finally {
                        ToolTimeoutContext.clear();
                        SessionScope.exit(previousSession);
                    }
                });
                try {
                    result = future.get(remainingSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException te) {
                    future.cancel(true);
                    DialogTools.closeApprovalWindow();
                    CallToolResult timeoutResult = new CallToolResult();
                    timeoutResult.setError(true);
                    List<ToolContent> content = new ArrayList<>();
                    content.add(ToolContent.error("✗ 工具执行超时（总时限 " + timeoutSeconds
                            + " 秒，含审批与运行时间），已立即终止；审批窗口已关闭"));
                    timeoutResult.setContent(content);
                    sendResponse(request.getId(), timeoutResult);
                    return;
                }
            }
            sendResponse(request.getId(), result);
        } catch (Exception e) {
            CallToolResult errorResult = new CallToolResult();
            errorResult.setError(true);
            List<ToolContent> content = new ArrayList<>();
            content.add(ToolContent.error("Error: " + e.getMessage()));
            errorResult.setContent(content);
            sendResponse(request.getId(), errorResult);
        } finally {
            ToolTimeoutContext.clear();
        }
    }

    /** 解析本次工具调用超时秒数：params._meta["dlqq/toolTimeoutSeconds"] 优先，默认 50 秒。 */
    @SuppressWarnings("unchecked")
    private int resolveToolTimeoutSeconds(Map<String, Object> params, Map<String, Object> arguments) {
        Object metaRaw = params.get("_meta");
        if (metaRaw instanceof Map<?, ?> meta) {
            Object value = ((Map<String, Object>) meta).get("dlqq/toolTimeoutSeconds");
            Integer seconds = parsePositiveInt(value);
            if (seconds != null) {
                return seconds;
            }
        }
        if (arguments != null) {
            Integer seconds = parsePositiveInt(arguments.get("tool_timeout_seconds"));
            if (seconds != null) {
                return seconds;
            }
        }
        return ToolTimeoutContext.defaultTimeoutSeconds();
    }

    private Integer parsePositiveInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 后台进程类调用（no_wait=true）立即返回，不设总超时。 */
    private boolean isBackgroundCall(Map<String, Object> arguments) {
        if (arguments == null) {
            return false;
        }
        Object noWait = arguments.get("no_wait");
        if (noWait instanceof Boolean b) {
            return b;
        }
        return noWait != null && "true".equalsIgnoreCase(String.valueOf(noWait).trim());
    }

    /** 校验工具参数：未知参数直接报错（在审批之前执行）。 */
    private String validateUnknownArguments(ToolHandler handler, Map<String, Object> arguments) {
        if (handler == null || arguments == null || arguments.isEmpty()) {
            return null;
        }
        ToolInputSchema inputSchema = handler.getInputSchema();
        if (inputSchema == null || inputSchema.getProperties() == null) {
            return null;
        }
        Set<String> validParams = inputSchema.getProperties().keySet();
        for (String key : arguments.keySet()) {
            if (!validParams.contains(key) && !"tool_timeout_seconds".equals(key)) {
                return "✗ 参数错误：未知参数 \"" + key + "\"（该工具支持的参数: "
                        + String.join(", ", validParams) + "）";
            }
        }
        return null;
    }
    /**
     * 处理资源列表请求
     */
    private void handleResourcesList(JsonRpcMessage.Request request) throws IOException {
        List<Resource> resources = new ArrayList<>();
        
        for (Map.Entry<String, ResourceHandler> entry : resourceHandlers.entrySet()) {
            resources.add(entry.getValue().getResource());
        }

        ListResourcesResult result = new ListResourcesResult(resources);
        sendResponse(request.getId(), result);
    }

    /**
     * 处理提示列表请求
     */
    private void handlePromptsList(JsonRpcMessage.Request request) throws IOException {
        List<Prompt> prompts = new ArrayList<>();
        
        for (Map.Entry<String, PromptHandler> entry : promptHandlers.entrySet()) {
            prompts.add(entry.getValue().getPrompt());
        }

        ListPromptsResult result = new ListPromptsResult(prompts);
        sendResponse(request.getId(), result);
    }

    /**
     * 处理获取提示请求
     */
    @SuppressWarnings("unchecked")
    private void handlePromptsGet(JsonRpcMessage.Request request) throws IOException {
        Map<String, Object> params = request.getParams();
        if (params == null) {
            sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_INVALID_PARAMS, "Missing params");
            return;
        }

        String promptName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        PromptHandler handler = promptHandlers.get(promptName);
        if (handler == null) {
            sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_INVALID_PARAMS, 
                    "Prompt not found: " + promptName);
            return;
        }

        try {
            GetPromptResult result = handler.get(arguments);
            sendResponse(request.getId(), result);
        } catch (Exception e) {
            sendErrorResponse(request.getId(), JsonRpcMessage.ERROR_INTERNAL_ERROR, 
                    "Error getting prompt: " + e.getMessage());
        }
    }

    /**
     * 发送响应
     */
    private void sendResponse(Object id, Object result) throws IOException {
        logger.info("[McpServer.sendResponse] ========== Sending response ==========");
        logger.info("[McpServer.sendResponse] Response ID: {}", id);
        logger.info("[McpServer.sendResponse] Result type: {}", result != null ? result.getClass().getSimpleName() : "null");

        // 记录结果内容摘要
        if (result != null) {
            String resultStr = result.toString();
            String preview = resultStr.length() > 500 ? resultStr.substring(0, 500) + "..." : resultStr;
            logger.info("[McpServer.sendResponse] Result summary: {}", preview);
        }

        try {
            JsonRpcMessage.Response response = JsonRpcMessage.Response.success(id, result);
            logger.info("[McpServer.sendResponse] Response created, isError: {}", response.isError());

            transport.sendResponse(response);
            logger.info("[McpServer.sendResponse] ========== Response sent ==========");
        } catch (IOException e) {
            logger.error("[McpServer.sendResponse] IO error while sending response: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("[McpServer.sendResponse] Error while sending response: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(Object id, int code, String message) throws IOException {
        logger.error("[McpServer.sendErrorResponse] ========== Sending error response ==========");
        logger.error("[McpServer.sendErrorResponse] Response ID: {}, code: {}, message: {}", id, code, message);

        try {
            JsonRpcMessage.Response response = JsonRpcMessage.Response.error(id, code, message);
            transport.sendResponse(response);
            logger.info("[McpServer.sendErrorResponse] ========== Error response sent ==========");
        } catch (IOException e) {
            logger.error("[McpServer.sendErrorResponse] IO error while sending error response: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("[McpServer.sendErrorResponse] Error while sending error response: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 文件编辑类工具：对能力较弱的模型统一附加"优先用 replace_text_in_file"的提示，
     * 降低按行号/整段覆盖导致文件损坏的概率。
     */
    private static final Set<String> EDITING_TOOLS = Set.of(
        "dlqq_create_file", "dlqq_overwrite_file", "dlqq_append_file", "dlqq_clear_file",
        "dlqq_insert_text_in_file", "dlqq_replace_lines_in_file",
        "dlqq_remove_lines_in_file", "dlqq_remove_text_in_file", "dlqq_write_bin_file",
        "dlqq_add_unicode_bom", "dlqq_remove_unicode_bom");

    /** 追加到编辑类工具描述末尾的提示语 */
    private static final String EDITING_HINT =
        "\n\n【弱模型提示】如果你的模型能力有限，建议只使用 dlqq_replace_text_in_file 修改文件内容，"
        + "不要使用本工具或其他编辑工具，以免损坏用户文件。";

    /**
     * 注册工具
     */
    public void addTool(String name, String description, ToolInputSchema inputSchema, 
                        Function<Map<String, Object>, CallToolResult> handler) {
        String finalDescription = EDITING_TOOLS.contains(name) && description != null
            ? description + EDITING_HINT
            : description;
        toolHandlers.put(name, new ToolHandler(finalDescription, inputSchema, handler));
        capabilities.setTools(true);
    }

    /**
     * 获取所有已注册工具的信息列表
     * @param filter 名称过滤关键字（可为null，返回全部工具）
     * @return 工具列表文本
     */
    public String getToolsInfo(String filter) {
        StringBuilder sb = new StringBuilder();
        List<String> names = new ArrayList<>(toolHandlers.keySet());
        Collections.sort(names);

        String keyword = (filter == null || filter.trim().isEmpty()) ? null : filter.trim().toLowerCase();
        int count = 0;
        for (String name : names) {
            if (disabledTools.contains(name)) {
                continue;
            }
            if (keyword != null && !name.toLowerCase().contains(keyword)) {
                continue;
            }
            ToolHandler handler = toolHandlers.get(name);
            sb.append("- `").append(name).append("`\n  ").append(handler.getDescription()).append("\n");
            count++;
        }

        StringBuilder result = new StringBuilder();
        result.append("MCP服务器已注册工具");
        if (keyword != null) {
            result.append("（过滤: ").append(filter.trim()).append("）");
        }
        result.append(": ").append(count).append(" 个");
        if (keyword == null) {
            result.append(" / 总计 ").append(getEnabledToolCount()).append(" 个");
        }
        if (!disabledTools.isEmpty()) {
            List<String> disabled = new ArrayList<>(disabledTools);
            Collections.sort(disabled);
            result.append("（已移除 ").append(disabled.size()).append(" 个: ")
                  .append(String.join(", ", disabled)).append("）");
        }
        result.append("\n");
        result.append("===================\n\n");
        result.append(sb);
        return result.toString();
    }

    /**
     * 注册资源
     */
    public void addResource(Resource resource, ResourceHandler handler) {
        resourceHandlers.put(resource.getUri(), handler);
        capabilities.setResources(true);
    }

    /**
     * 注册提示
     */
    public void addPrompt(Prompt prompt, PromptHandler handler) {
        promptHandlers.put(prompt.getName(), handler);
        capabilities.setPrompts(true);
    }

    /**
     * 工具处理器
     */
    private static class ToolHandler {
        private final String description;
        private final ToolInputSchema inputSchema;
        private final Function<Map<String, Object>, CallToolResult> handler;

        ToolHandler(String description, ToolInputSchema inputSchema, 
                   Function<Map<String, Object>, CallToolResult> handler) {
            this.description = description;
            this.inputSchema = inputSchema;
            this.handler = handler;
        }

        String getDescription() {
            return description;
        }

        ToolInputSchema getInputSchema() {
            return inputSchema;
        }

        CallToolResult call(Map<String, Object> arguments) {
            // 校验参数：检测未知参数
            if (inputSchema != null && inputSchema.getProperties() != null) {
                Set<String> validParams = inputSchema.getProperties().keySet();
                for (String key : arguments.keySet()) {
                    if (!validParams.contains(key)) {
                        CallToolResult result = new CallToolResult();
                        result.setError(true);
                        List<ToolContent> content = new ArrayList<>();
                        content.add(ToolContent.error("未知参数: " + key + "，有效参数为: " + String.join(", ", validParams)));
                        result.setContent(content);
                        return result;
                    }
                }
            }
            return handler.apply(arguments);
        }
    }

    /**
     * 资源处理器接口
     */
    public interface ResourceHandler {
        Resource getResource();
        String getContent();
    }

    /**
     * 提示处理器接口
     */
    public interface PromptHandler {
        Prompt getPrompt();
        GetPromptResult get(Map<String, Object> arguments);
    }
}
