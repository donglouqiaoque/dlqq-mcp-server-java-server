package com.donglouqiaoque.dlqqmcpserver.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区根目录与权限管理（会话级）。
 *
 * <p>工作区路径<b>不持久化</b>：默认取进程 CWD（或启动参数 {@code --workspace}），
 * 会话首次请求可通过 {@code _meta} 下发，运行期可用 {@code dlqq_workspace_set} 切换。
 * 工作区不可用时统一回退 {@code <部署目录>/default_workspace}。</p>
 *
 * <p>每个工作区的配置持久化在全局运行时目录：
 * {@code <部署目录>/.dlqq/settings/<工作区hash>/settings.properties}。
 * 新工作区首次使用时写入默认权限 {@code chat}（对话模式：禁止任何资源与外设操作）。</p>
 */
public final class Workspace {

    /** 工作区来源：启动参数 */
    public static final String SOURCE_CLI = "--workspace argument";
    /** 工作区来源：运行期设置 */
    public static final String SOURCE_RUNTIME = "runtime setting";
    /** 工作区来源：进程工作目录 */
    public static final String SOURCE_CWD = "process working directory";
    /** 工作区来源：兜底工作区 */
    public static final String SOURCE_DEFAULT = "default workspace";

    /** 对话模式：禁止任何资源操作 */
    public static final String PERMISSION_CHAT = "chat";
    /** 只读模式：禁止修改任何文件 */
    public static final String PERMISSION_READONLY = "readonly";
    /** 工作区修改模式：只允许修改工作区内的文件 */
    public static final String PERMISSION_WORKSPACE = "workspace";
    /** 工作区外审批模式：工作区内直接修改，工作区外弹窗审批 */
    public static final String PERMISSION_APPROVAL = "approval";
    /** 完全权限：允许修改任意位置的文件 */
    public static final String PERMISSION_FULL = "full";

    /** 新工作区的默认资源权限（对话模式）。 */
    public static final String DEFAULT_PERMISSION = PERMISSION_CHAT;
    /** 新工作区的默认操作权限（对话模式）。 */
    public static final String DEFAULT_OPERATION_PERMISSION = PERMISSION_CHAT;

    /** 资源权限配置键 */
    public static final String PERMISSION_KEY = "permission.resource.level";
    /** 操作权限配置键（鼠标/键盘/屏幕/串口/BLE/WiFi/USB 等外设操作） */
    public static final String OPERATION_PERMISSION_KEY = "permission.operation.level";
    /**
     * 旧的工作区本地配置相对路径。
     *
     * @deprecated 配置已迁移到全局 {@code .dlqq/settings/<hash>/settings.properties}，不要再使用
     */
    @Deprecated
    public static final String SETTINGS_RELATIVE_PATH = ".dlqq/settings/settings.properties";

    private static final String SETTINGS_FILE_NAME = "settings.properties";

    private Workspace() {
    }

    /**
     * 初始化默认基准工作区（服务启动时调用一次）：优先启动参数，否则进程 CWD；
     * 两者都不可用时回退兜底工作区。
     *
     * @param cliWorkspace 启动参数 --workspace 的值，可为 null
     */
    public static void init(String cliWorkspace) {
        SessionScope.init(cliWorkspace);
    }

    /**
     * 运行期设置<b>当前会话</b>的工作区（不持久化；是否允许由调用方先做用户审批）。
     *
     * @param directory 已存在的绝对目录
     * @throws IllegalArgumentException 目录不存在、不是目录，或位于 .dlqq 下
     */
    public static void setRoot(String directory) {
        Path path = toExistingDirectory(directory);
        if (path == null) {
            throw new IllegalArgumentException("工作区必须是已存在的绝对目录: " + directory);
        }
        if (touchesDlqq(path)) {
            throw new IllegalArgumentException("工作区不能位于 .dlqq 目录下: " + directory);
        }
        SessionScope.setCurrentRoot(path, SOURCE_RUNTIME);
    }

    /** 把当前会话重置到启动默认工作区。 */
    public static void resetToDefault() {
        SessionScope.resetCurrentToDefault();
    }

    /** 当前会话的工作区根目录（绝对路径）。 */
    public static Path getRoot() {
        return SessionScope.currentRoot();
    }

    /** 当前会话的工作区来源说明。 */
    public static String getSource() {
        return SessionScope.currentSource();
    }

    /** 启动参数指定的工作区（未指定时返回 null）。 */
    public static String getCliPath() {
        return SessionScope.getBaseCliPath();
    }

    /** 当前会话的资源权限。 */
    public static String getPermission() {
        return SessionScope.currentPermission();
    }

    /** 资源权限的中文说明。 */
    public static String getPermissionLabel() {
        switch (getPermission()) {
            case PERMISSION_CHAT:
                return "对话模式（chat）";
            case PERMISSION_READONLY:
                return "只读模式（readonly）";
            case PERMISSION_FULL:
                return "完全权限（full）";
            case PERMISSION_APPROVAL:
                return "工作区外审批（approval）";
            default:
                return "工作区修改（workspace）";
        }
    }

    /** 当前会话工作区的 settings.properties 路径（权限与其它配置都存在这里）。 */
    public static Path getSettingsFile() {
        return settingsFileOf(SessionScope.currentRoot());
    }

    /**
     * 指定工作区的 settings.properties 路径（全局运行时目录下）。
     *
     * @param root 工作区根目录
     * @return {@code <部署目录>/.dlqq/settings/<hash>/settings.properties}
     */
    public static Path settingsFileOf(Path root) {
        String hash = WorkspaceRegistry.hashFor(root);
        return RuntimeDirs.getSettingsRoot().resolve(hash).resolve(SETTINGS_FILE_NAME);
    }

    /**
     * 确保指定工作区的 settings 文件存在；不存在时写入默认 chat 权限。
     *
     * @param root 工作区根目录
     */
    public static void ensureSettingsFileFor(Path root) {
        Path file = settingsFileOf(root);
        if (Files.isRegularFile(file)) {
            return;
        }
        try {
            writeSettingsValue(file, PERMISSION_KEY, DEFAULT_PERMISSION);
            writeSettingsValue(file, OPERATION_PERMISSION_KEY, DEFAULT_OPERATION_PERMISSION);
        } catch (IOException e) {
            System.err.println("[Workspace] 创建默认配置失败: " + file + " (" + e.getMessage() + ")");
        }
    }

    /** 当前工作区的权限配置文件路径（与 settings.properties 同一文件）。 */
    public static Path getPermissionFile() {
        return getSettingsFile();
    }

    /** 校验是否为已存在的绝对目录（不改变当前工作区）。 */
    public static boolean isValidDirectory(String directory) {
        return toExistingDirectory(directory) != null;
    }

    /**
     * 把用户路径解析为绝对路径：空值返回工作区根目录；绝对路径原样规范化；相对路径基于工作区解析。
     */
    public static Path resolve(String path) {
        Path root = SessionScope.currentRoot();
        if (isBlank(path)) {
            return root;
        }
        Path value = Paths.get(path.trim());
        return value.isAbsolute() ? value.normalize() : root.resolve(value).normalize();
    }

    /** 路径是否位于当前会话的工作区内。 */
    public static boolean isInside(Path path) {
        return path.toAbsolutePath().normalize().startsWith(SessionScope.currentRoot());
    }

    /** 该路径在当前权限模式下修改时是否需要弹窗审批（仅 approval 模式且在工作区外时为 true）。 */
    public static boolean requiresApproval(Path path) {
        return PERMISSION_APPROVAL.equals(getPermission()) && !isInside(path);
    }

    /**
     * 检查是否允许修改指定路径（写操作前调用，供工具层使用）。
     *
     * @param path 已解析的绝对路径
     * @return null 表示允许；否则返回错误消息
     */
    public static String checkWrite(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        // 当前工作区缓存目录内容：始终允许读写（不受权限等级限制；按工作区隔离，不区分会话）
        if (isCacheContent(absolute)) {
            return null;
        }
        // .dlqq 目录（除缓存目录内容外）一律禁止访问
        if (touchesDlqq(absolute)) {
            return "✗ 禁止访问 .dlqq 目录（仅当前工作区缓存目录内容允许读写）: " + absolute;
        }
        switch (getPermission()) {
            case PERMISSION_CHAT:
                return "✗ 当前为对话模式（chat），禁止任何资源操作: " + absolute
                        + "\n可用 dlqq_workspace_permission_set 提权后再试";
            case PERMISSION_READONLY:
                return "✗ 当前工作区为只读模式（readonly），禁止修改文件: " + absolute
                        + "\n可用 dlqq_workspace_permission_set 切换为 workspace、approval 或 full";
            case PERMISSION_FULL:
                return null;
            case PERMISSION_APPROVAL:
                return null; // 工作区外由调用方弹窗审批
            default:
                if (!isInside(absolute)) {
                    return "✗ 当前工作区权限为“工作区修改”（workspace），目标在工作区外: " + absolute
                            + "\n可用 dlqq_workspace_permission_set 切换为 approval 或 full";
                }
                return null;
        }
    }

    /**
     * 设置当前工作区的资源权限，并持久化到全局 settings。
     *
     * @param mode chat / readonly / workspace / approval / highrisk / full / custom
     * @throws IllegalArgumentException 模式非法或写入配置失败
     */
    public static void setPermission(String mode) {
        String normalized = normalizePermission(mode);
        if (normalized == null) {
            throw new IllegalArgumentException("资源权限必须是 chat、readonly、workspace、approval、highrisk 或 full: " + mode);
        }
        try {
            writeSettingsValue(getSettingsFile(), PERMISSION_KEY, normalized);
        } catch (IOException e) {
            throw new IllegalArgumentException("写入资源权限配置失败: " + e.getMessage());
        }
        SessionScope.setCurrentPermission(normalized);
    }

    /** 当前会话的操作权限：chat / readonly / approval / highrisk / full（缺省 chat）。 */
    public static String getOperationPermission() {
        String value = getSettingValue(OPERATION_PERMISSION_KEY);
        String normalized = normalizeOperationPermission(value);
        return normalized == null ? DEFAULT_OPERATION_PERMISSION : normalized;
    }

    /** 操作权限的中文说明。 */
    public static String getOperationPermissionLabel() {
        switch (getOperationPermission()) {
            case PERMISSION_CHAT:
                return "对话模式（chat）";
            case PERMISSION_READONLY:
                return "只读（readonly）";
            case PERMISSION_FULL:
                return "完全权限（full）";
            case "highrisk":
                return "高危需批准（highrisk）";
            default:
                return "需要批准（approval）";
        }
    }

    /**
     * 读取指定工作区配置的资源权限（用于工作区切换时的提权判断）。
     *
     * @param root 目标工作区根目录
     * @return 资源权限；未配置时为默认值 chat
     */
    public static String getPermissionOf(Path root) {
        if (root == null) {
            return DEFAULT_PERMISSION;
        }
        Path settingsFile = settingsFileOf(root);
        String value = readSettingsValue(settingsFile, PERMISSION_KEY);
        String normalized = normalizePermission(value);
        return normalized == null ? DEFAULT_PERMISSION : normalized;
    }

    /**
     * 读取指定工作区配置的操作权限（控制台展示用）。
     *
     * @param root 工作区根目录
     * @return 操作权限；未配置时为默认值 chat
     */
    public static String getOperationPermissionOf(Path root) {
        if (root == null) {
            return DEFAULT_OPERATION_PERMISSION;
        }
        Path settingsFile = settingsFileOf(root);
        String value = readSettingsValue(settingsFile, OPERATION_PERMISSION_KEY);
        String normalized = normalizeOperationPermission(value);
        return normalized == null ? DEFAULT_OPERATION_PERMISSION : normalized;
    }

    /**
     * 为指定工作区写入资源权限（不依赖当前线程会话，供控制台使用）。
     *
     * @param root 工作区根目录
     * @param mode chat / readonly / workspace / approval / highrisk / full
     * @return 实际写入的规范化权限
     * @throws IllegalArgumentException 模式非法或写入失败
     */
    public static String setPermissionFor(Path root, String mode) {
        String normalized = normalizePermission(mode);
        if (normalized == null) {
            throw new IllegalArgumentException("资源权限必须是 chat、readonly、workspace、approval、highrisk 或 full: " + mode);
        }
        Path settingsFile = settingsFileOf(root);
        try {
            writeSettingsValue(settingsFile, PERMISSION_KEY, normalized);
        } catch (IOException e) {
            throw new IllegalArgumentException("写入资源权限配置失败: " + e.getMessage());
        }
        return normalized;
    }

    /**
     * 为指定工作区写入操作权限（不依赖当前线程会话，供控制台使用）。
     *
     * @param root 工作区根目录
     * @param mode chat / readonly / approval / highrisk / full
     * @return 实际写入的规范化权限
     * @throws IllegalArgumentException 模式非法或写入失败
     */
    public static String setOperationPermissionFor(Path root, String mode) {
        String normalized = normalizeOperationPermission(mode);
        if (normalized == null) {
            throw new IllegalArgumentException("操作权限必须是 chat、readonly、approval、highrisk 或 full: " + mode);
        }
        try {
            writeSettingsValue(settingsFileOf(root), OPERATION_PERMISSION_KEY, normalized);
        } catch (IOException e) {
            throw new IllegalArgumentException("写入操作权限配置失败: " + e.getMessage());
        }
        return normalized;
    }

    /**
     * 设置当前会话的操作权限并持久化。
     *
     * @param mode chat / readonly / approval / highrisk / full
     * @throws IllegalArgumentException 模式非法或写入失败
     */
    public static void setOperationPermission(String mode) {
        String normalized = normalizeOperationPermission(mode);
        if (normalized == null) {
            throw new IllegalArgumentException("操作权限必须是 chat、readonly、approval、highrisk 或 full: " + mode);
        }
        try {
            writeSettingsValue(getSettingsFile(), OPERATION_PERMISSION_KEY, normalized);
        } catch (IOException e) {
            throw new IllegalArgumentException("写入操作权限配置失败: " + e.getMessage());
        }
    }

    static String normalizeOperationPermission(String mode) {
        if (mode == null) {
            return null;
        }
        switch (mode.trim().toLowerCase()) {
            case PERMISSION_CHAT:
            case PERMISSION_READONLY:
            case PERMISSION_APPROVAL:
            case "highrisk":
            case PERMISSION_FULL:
                return mode.trim().toLowerCase();
            default:
                return null;
        }
    }


    /** 当前会话标识（用于诊断输出）。 */
    public static String getSessionId() {
        return SessionScope.currentSessionId();
    }

    /** 当前活跃会话数（含默认会话）。 */
    public static int getSessionCount() {
        return SessionScope.sessionCount();
    }

    /** 会话 {@code _meta} 键名。 */
    public static String getSessionMetaKey() {
        return SessionScope.getMetaKey();
    }

    /** Agent {@code _meta} 键名。 */
    public static String getSessionAgentMetaKey() {
        return SessionScope.getAgentMetaKey();
    }

    /** 会话工作区 {@code _meta} 键名（客户端下发的会话 cwd 键）。 */
    public static String getSessionWorkspaceMetaKey() {
        return SessionScope.getWorkspaceMetaKey();
    }

    /** 读取当前工作区配置值；文件或键不存在时返回 null。 */
    public static String getSettingValue(String key) {
        return readSettingsValue(getSettingsFile(), key);
    }

    /**
     * 读取指定工作区的全部配置项（控制台配置选项卡使用）。
     *
     * @param root 工作区根目录
     * @return 键值对；文件不存在或读取失败时返回空 Map
     */
    public static Map<String, String> readAllSettingsFor(Path root) {
        Map<String, String> values = new LinkedHashMap<>();
        if (root == null) {
            return values;
        }
        Path file = settingsFileOf(root);
        if (!Files.isRegularFile(file)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int equalIndex = trimmed.indexOf('=');
                if (equalIndex > 0) {
                    values.put(trimmed.substring(0, equalIndex).trim(),
                            trimmed.substring(equalIndex + 1).trim());
                }
            }
        } catch (IOException ignored) {
            // 读取失败时返回已读取部分（通常为空）
        }
        return values;
    }

    /**
     * 写入指定工作区的配置项（控制台配置选项卡使用）。
     *
     * @param root  工作区根目录
     * @param key   配置键
     * @param value 配置值
     * @throws IOException 写入失败
     */
    public static void writeSettingFor(Path root, String key, String value) throws IOException {
        writeSettingsValue(settingsFileOf(root), key, value);
    }

    /**
     * 按 UTF-8 行格式读取配置值；文件或键不存在时返回 null。
     */
    static String readSettingsValue(Path settingsFile, String key) {
        if (!Files.isRegularFile(settingsFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(settingsFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key)) {
                    int equalIndex = trimmed.indexOf('=');
                    if (equalIndex > 0 && trimmed.substring(0, equalIndex).trim().equals(key)) {
                        return trimmed.substring(equalIndex + 1).trim();
                    }
                }
            }
        } catch (IOException ignored) {
            // 读取失败时按默认值处理
        }
        return null;
    }

    /**
     * 按 UTF-8 行格式写入/更新配置键，保留其它行与注释。
     */
    static void writeSettingsValue(Path settingsFile, String key, String value) throws IOException {
        Path parent = settingsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>();
        if (Files.isRegularFile(settingsFile)) {
            lines.addAll(Files.readAllLines(settingsFile, StandardCharsets.UTF_8));
        }
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            int equalIndex = trimmed.indexOf('=');
            if (equalIndex > 0 && trimmed.substring(0, equalIndex).trim().equals(key)) {
                lines.set(i, key + "=" + value);
                replaced = true;
            }
        }
        if (!replaced) {
            lines.add(key + "=" + value);
        }
        Files.write(settingsFile, lines, StandardCharsets.UTF_8);
    }

    static String normalizePermission(String mode) {
        if (mode == null) {
            return null;
        }
        String value = mode.trim().toLowerCase();
        switch (value) {
            case PERMISSION_CHAT:
            case PERMISSION_READONLY:
            case PERMISSION_WORKSPACE:
            case PERMISSION_APPROVAL:
            case "highrisk":
            case PERMISSION_FULL:
            case "custom":
                return value;
            default:
                return null;
        }
    }

    /** 路径是否包含名为 .dlqq 的路径段。 */
    public static boolean touchesDlqq(Path path) {
        if (path == null) {
            return false;
        }
        Path absolute = path.toAbsolutePath().normalize();
        for (Path segment : absolute) {
            if (RuntimeDirs.DLQQ_DIR_NAME.equalsIgnoreCase(segment.toString())) {
                return true;
            }
        }
        return false;
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
            if (!Files.isDirectory(path)) {
                return null;
            }
            return path;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 路径是否严格位于当前工作区缓存目录内部（缓存目录节点本身不算）。 */
    private static boolean isCacheContent(Path absolute) {
        try {
            Path cacheDir = SessionScope.currentCacheDir().toAbsolutePath().normalize();
            return absolute.startsWith(cacheDir) && !absolute.equals(cacheDir);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
