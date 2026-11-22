package com.donglouqiaoque.dlqqmcpserver.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话作用域：让同一个 MCP 服务进程同时服务多个对话会话。
 *
 * <p>会话标识由客户端通过 {@code params._meta[<metaKey>]} 提供（stdio 与 HTTP 通用），
 * HTTP 模式在缺少 {@code _meta} 时回退到 {@code Mcp-Session-Id} 请求头。
 * 没有会话标识的请求落到默认会话（进程级），保持与旧客户端兼容。</p>
 *
 * <p>每个会话持有独立的工作区根目录、来源与修改权限；请求线程通过 ThreadLocal
 * 绑定当前会话，工作区与配置访问自动路由到对应会话。</p>
 */
public final class SessionScope {

    private static final Logger logger = LoggerFactory.getLogger(SessionScope.class);

    /** 无会话标识时使用的默认会话（进程级，向后兼容）。 */
    public static final String DEFAULT_SESSION_ID = "__default__";

    /** 默认的 {@code _meta} 键名，与 DSH 侧插件约定一致。 */
    public static final String DEFAULT_META_KEY = "dsh/sessionId";

    /** 会话 ID 最大长度，防止超长键污染内存。 */
    private static final int MAX_SESSION_ID_LENGTH = 256;

    /** 默认空闲回收时间：30 分钟。 */
    private static final long DEFAULT_IDLE_MILLIS = 30L * 60 * 1000L;

    /** 回收扫描间隔：60 秒。 */
    private static final long SWEEP_INTERVAL_MILLIS = 60L * 1000L;

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    /** 工作区来源常量（与 Workspace 的公开常量保持一致）。 */
    private static final String SOURCE_CWD = "process working directory";
    private static final String SOURCE_CLI = "--workspace argument";

    private static final String DEFAULT_AGENT_META_KEY = "dsh/agentId";
    /** 会话工作区目录的 _meta 键（客户端携带会话 cwd，用于会话初始化工作区）。 */
    private static final String DEFAULT_WORKSPACE_META_KEY = "dsh/workspace";
    /** 复合会话键最大长度（会话键 + Agent 键）。 */
    private static final int MAX_COMPOSITE_KEY_LENGTH = 512;
    /** 工作区来源：由客户端 _meta 下发的会话 cwd 初始化。 */
    private static final String SOURCE_HINT = "session cwd (params._meta)";

    private static volatile String metaKey = DEFAULT_META_KEY;
    private static volatile String agentMetaKey = DEFAULT_AGENT_META_KEY;
    private static volatile String workspaceMetaKey = DEFAULT_WORKSPACE_META_KEY;
    private static volatile long idleMillis = DEFAULT_IDLE_MILLIS;
    private static volatile Path baseRoot = normalizeCwd();
    private static volatile String baseSource = SOURCE_CWD;
    private static volatile String baseCliPath;
    private static volatile boolean sweeperStarted;

    private SessionScope() {
    }

    /**
     * 初始化默认基准工作区（启动时调用一次）：优先 --workspace，否则进程 CWD。
     *
     * @param cliWorkspace 启动参数 --workspace 的值，可为 null
     */
    public static synchronized void init(String cliWorkspace) {
        baseCliPath = isBlank(cliWorkspace) ? null : cliWorkspace.trim();
        Path cliDir = toExistingDirectory(baseCliPath);
        if (cliDir != null) {
            baseRoot = cliDir;
            baseSource = SOURCE_CLI;
        } else {
            baseRoot = normalizeCwd();
            baseSource = SOURCE_CWD;
        }
        SESSIONS.clear();
        getSession(DEFAULT_SESSION_ID);
        logger.info("[SessionScope] Base workspace initialized: {} (source: {})", baseRoot, baseSource);
    }

    /**
     * 配置会话参数（启动参数解析后调用）。
     *
     * @param metaKeyOverride          会话键名，空值使用默认值
     * @param agentMetaKeyOverride     Agent 键名，空值使用默认值
     * @param workspaceMetaKeyOverride 会话工作区 _meta 键名，空值使用默认值
     * @param idleMinutes              空闲回收分钟数，0 表示不回收
     */
    public static void configure(String metaKeyOverride, String agentMetaKeyOverride,
                                 String workspaceMetaKeyOverride, int idleMinutes) {
        if (!isBlank(metaKeyOverride)) {
            metaKey = metaKeyOverride.trim();
        }
        if (!isBlank(agentMetaKeyOverride)) {
            agentMetaKey = agentMetaKeyOverride.trim();
        }
        if (!isBlank(workspaceMetaKeyOverride)) {
            workspaceMetaKey = workspaceMetaKeyOverride.trim();
        }
        if (idleMinutes > 0) {
            idleMillis = idleMinutes * 60_000L;
        } else if (idleMinutes == 0) {
            idleMillis = Long.MAX_VALUE;
        }
        logger.info("[SessionScope] Session meta key: {}, agent meta key: {}, workspace meta key: {}, idle timeout: {}",
                metaKey, agentMetaKey, workspaceMetaKey,
                idleMillis == Long.MAX_VALUE ? "disabled" : (idleMillis / 60000L) + "min");
    }

    /** 启动后台线程回收空闲会话（只回收非默认会话）。 */
    public static synchronized void startSweeper() {
        if (sweeperStarted) {
            return;
        }
        sweeperStarted = true;
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(SWEEP_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                sweepIdleSessions();
            }
        }, "dlqq-session-sweeper");
        thread.setDaemon(true);
        thread.start();
        logger.info("[SessionScope] Idle session sweeper started");
    }

    /**
     * 绑定当前线程到指定会话。
     *
     * @param sessionId 会话标识，空值使用默认会话
     * @return 绑定前的会话标识（用于退出时恢复），没有则为 null
     */
    public static String enter(String sessionId) {
        String previous = CURRENT.get();
        String id = normalizeSessionId(sessionId);
        Session session = getSession(id);
        session.lastAccess = System.currentTimeMillis();
        CURRENT.set(id);
        return previous;
    }

    /**
     * 绑定当前线程到指定会话，并在会话首次出现时应用客户端下发的会话工作区。
     *
     * @param sessionId 会话标识，空值使用默认会话
     * @param params    请求参数（用于读取 {@code _meta} 中的会话工作区）
     * @return 绑定前的会话标识（用于退出时恢复），没有则为 null
     */
    public static String enter(String sessionId, Map<String, Object> params) {
        String previous = enter(sessionId);
        applyWorkspaceHint(params);
        return previous;
    }

    /**
     * 会话首次被访问时，用客户端 {@code _meta[workspaceMetaKey]} 下发的绝对目录初始化工作区。
     * 每个会话只尝试一次；路径非法/不存在时静默回退到基准工作区。
     */
    @SuppressWarnings("unchecked")
    private static void applyWorkspaceHint(Map<String, Object> params) {
        Session session = currentSession();
        if (session.workspaceHintApplied) {
            return;
        }
        session.workspaceHintApplied = true;
        Path hint = resolveWorkspaceHint(params);
        if (hint == null || hint.equals(session.root)) {
            return;
        }
        session.root = hint;
        session.source = SOURCE_HINT;
        session.ensuredRoot = null;
        session.permission = loadPermission(hint);
        logger.info("[SessionScope] Session workspace initialized from _meta: {} (source: {}, session: {})",
                hint, SOURCE_HINT, session.id);
    }

    /** 从 params._meta 中解析并校验会话工作区目录；非法时返回 null。 */
    @SuppressWarnings("unchecked")
    private static Path resolveWorkspaceHint(Map<String, Object> params) {
        if (params == null || isBlank(workspaceMetaKey)) {
            return null;
        }
        Object metaRaw = params.get("_meta");
        if (!(metaRaw instanceof Map<?, ?> meta)) {
            return null;
        }
        Object value = ((Map<String, Object>) meta).get(workspaceMetaKey);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        // .dlqq 目录（配置/日志/缓存/脚本）不允许作为工作区
        for (String segment : text.replace('\\', '/').split("/")) {
            if (".dlqq".equalsIgnoreCase(segment.trim())) {
                logger.warn("[SessionScope] Reject session workspace hint under .dlqq: {}", text);
                return null;
            }
        }
        try {
            Path path = Paths.get(text);
            if (!path.isAbsolute()) {
                logger.warn("[SessionScope] Reject non-absolute session workspace hint: {}", text);
                return null;
            }
            path = path.normalize();
            if (!java.nio.file.Files.isDirectory(path)) {
                logger.warn("[SessionScope] Session workspace hint is not a directory: {}", text);
                return null;
            }
            return path;
        } catch (RuntimeException e) {
            logger.warn("[SessionScope] Invalid session workspace hint: {} ({})", text, e.getMessage());
            return null;
        }
    }

    /** 当前会话工作区 {@code _meta} 键名。 */
    public static String getWorkspaceMetaKey() {
        return workspaceMetaKey;
    }

    /**
     * 退出当前会话绑定。
     *
     * @param previous {@link #enter(String)} 返回的旧值
     */
    public static void exit(String previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /**
     * 从 JSON-RPC params 中解析会话键：读取 {@code params._meta} 中的会话键与 Agent 键。
     *
     * <p>两个键都提供时组成复合键，实现「多个对话 × 多个 Agent」的二维隔离；
     * 只提供会话键时按会话隔离；都没有时返回 null（落到默认会话，兼容不支持多会话的客户端）。</p>
     *
     * @param params 请求参数（可为 null）
     * @return 会话键，未提供任何标识时返回 null
     */
    @SuppressWarnings("unchecked")
    public static String resolveSessionId(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        Object metaRaw = params.get("_meta");
        if (!(metaRaw instanceof Map<?, ?> meta)) {
            return null;
        }
        String sessionId = readMetaString((Map<String, Object>) meta, metaKey);
        String agentId = readMetaString((Map<String, Object>) meta, agentMetaKey);
        if (sessionId == null && agentId == null) {
            return null;
        }
        String composite;
        if (sessionId == null) {
            composite = "agent:" + agentId;
        } else if (agentId == null) {
            composite = "session:" + sessionId;
        } else {
            composite = "session:" + sessionId + "|agent:" + agentId;
        }
        return composite.length() > MAX_COMPOSITE_KEY_LENGTH ? null : composite;
    }

    /** 读取一个 {@code _meta} 字符串值并做长度校验；空值或超长返回 null。 */
    private static String readMetaString(Map<String, Object> meta, String key) {
        if (meta == null || key == null) {
            return null;
        }
        Object value = meta.get(key);
        if (value == null) {
            return null;
        }
        String id = String.valueOf(value).trim();
        if (id.isEmpty() || id.length() > MAX_SESSION_ID_LENGTH) {
            return null;
        }
        return id;
    }

    /** 当前线程绑定的会话标识。 */
    public static String currentSessionId() {
        String id = CURRENT.get();
        return id == null ? DEFAULT_SESSION_ID : id;
    }

    /** 当前活跃会话数量（含默认会话）。 */
    public static int sessionCount() {
        return SESSIONS.size();
    }

    /** 当前会话键名。 */
    public static String getMetaKey() {
        return metaKey;
    }

    /** 当前 Agent 键名。 */
    public static String getAgentMetaKey() {
        return agentMetaKey;
    }

    /** 当前会话的工作区根目录。 */
    public static Path currentRoot() {
        return currentSession().root;
    }

    /** 当前会话的工作区来源。 */
    public static String currentSource() {
        return currentSession().source;
    }

    /** 启动参数指定的工作区（未指定或无效时返回 null）。 */
    public static String getBaseCliPath() {
        return baseCliPath;
    }

    /** 当前会话的修改权限。 */
    public static String currentPermission() {
        return currentSession().permission;
    }

    /** 修改当前会话的工作区根目录，并按新工作区重新加载权限。 */
    public static void setCurrentRoot(Path root, String source) {
        Session session = currentSession();
        session.root = root;
        session.source = source;
        session.ensuredRoot = null;
        session.permission = loadPermission(root);
    }

    /** 把当前会话回退到启动基准工作区。 */
    public static void resetCurrentToDefault() {
        Session session = currentSession();
        session.root = baseRoot;
        session.source = baseSource;
        session.ensuredRoot = null;
        session.permission = loadPermission(baseRoot);
    }

    /** 设置当前会话的修改权限（调用方负责持久化）。 */
    public static void setCurrentPermission(String permission) {
        currentSession().permission = permission;
    }

    /** 当前会话已确保过 .dlqq 元数据的工作区路径（避免重复磁盘检查）。 */
    public static String currentEnsuredRoot() {
        return currentSession().ensuredRoot;
    }

    /** 记录当前会话已确保 .dlqq 元数据的工作区路径。 */
    public static void setCurrentEnsuredRoot(String path) {
        currentSession().ensuredRoot = path;
    }

    /**
     * 释放指定会话（HTTP DELETE 或空闲回收）。
     *
     * @param sessionId 会话标识，默认会话不会被释放
     */
    public static void disposeSession(String sessionId) {
        if (isBlank(sessionId) || DEFAULT_SESSION_ID.equals(sessionId)) {
            return;
        }
        if (SESSIONS.remove(sessionId) != null) {
            logger.info("[SessionScope] Session disposed: {}", sessionId);
        }
    }

    private static void sweepIdleSessions() {
        if (idleMillis == Long.MAX_VALUE) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Session> entry : SESSIONS.entrySet()) {
            Session session = entry.getValue();
            if (DEFAULT_SESSION_ID.equals(entry.getKey())) {
                continue;
            }
            if (now - session.lastAccess > idleMillis) {
                SESSIONS.remove(entry.getKey(), session);
                logger.info("[SessionScope] Session expired after idle timeout: {}", entry.getKey());
            }
        }
    }

    private static Session currentSession() {
        return getSession(normalizeSessionId(CURRENT.get()));
    }

    private static Session getSession(String sessionId) {
        return SESSIONS.computeIfAbsent(sessionId, SessionScope::createSession);
    }

    private static Session createSession(String sessionId) {
        Session session = new Session(sessionId);
        session.root = baseRoot;
        session.source = baseSource;
        session.permission = loadPermission(baseRoot);
        session.lastAccess = System.currentTimeMillis();
        logger.info("[SessionScope] Session created: {} (workspace: {})", sessionId, session.root);
        return session;
    }

    private static String loadPermission(Path root) {
        // 默认权限：工作区外需要审批（approval）
        String fallback = Workspace.PERMISSION_APPROVAL;
        if (root == null) {
            return fallback;
        }
        Path settingsFile = root.resolve(Workspace.SETTINGS_RELATIVE_PATH).normalize();
        String value = Workspace.readSettingsValue(settingsFile, Workspace.PERMISSION_KEY);
        String normalized = Workspace.normalizePermission(value);
        return normalized == null ? fallback : normalized;
    }

    private static String normalizeSessionId(String sessionId) {
        if (isBlank(sessionId)) {
            return DEFAULT_SESSION_ID;
        }
        String id = sessionId.trim();
        if (id.length() > MAX_SESSION_ID_LENGTH) {
            return DEFAULT_SESSION_ID;
        }
        return id;
    }

    private static Path normalizeCwd() {
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static Path toExistingDirectory(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            Path path = Paths.get(value.trim());
            if (!path.isAbsolute()) {
                return null;
            }
            path = path.normalize();
            return java.nio.file.Files.isDirectory(path) ? path : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 单个会话的独立状态。 */
    public static final class Session {
        private final String id;
        private volatile Path root;
        private volatile String source;
        private volatile String permission;
        private volatile String ensuredRoot;
        private volatile long lastAccess;
        /** 是否已尝试应用客户端下发的会话工作区（每个会话只尝试一次）。 */
        private volatile boolean workspaceHintApplied;

        private Session(String id) {
            this.id = id;
        }

        /** 会话标识。 */
        public String getId() {
            return id;
        }
    }
}
