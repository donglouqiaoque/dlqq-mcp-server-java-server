package com.donglouqiaoque.dlqqmcpserver.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作区根目录与修改权限管理（会话级）。
 *
 * <p>每个会话（对话）拥有独立的工作区：路径<b>不持久化</b>，来自启动参数
 * {@code --workspace <dir>} 或运行期 {@code dlqq_workspace_set}；未指定时使用进程 CWD。
 * 会话标识由 {@link SessionScope} 维护，本类所有读写都作用于<b>当前请求所属会话</b>，
 * 因此同一个服务进程可以同时服务多个对话而互不影响。</p>
 *
 * <p>修改权限持久化在工作区的 {@code .dlqq/settings/settings.properties}
 * （键 {@code workspace.write.mode}）：</p>
 * <ul>
 *   <li>{@code readonly}：只读模式，禁止任何文件修改；</li>
 *   <li>{@code workspace}：工作区修改（默认），只允许修改工作区内的文件；</li>
 *   <li>{@code approval}：工作区外审批，工作区内直接修改、工作区外弹窗确认；</li>
 *   <li>{@code full}：完全权限，允许修改任意位置的文件。</li>
 * </ul>
 */
public final class Workspace {

    /** 工作区来源：启动参数 */
    public static final String SOURCE_CLI = "--workspace argument";
    /** 工作区来源：运行期设置 */
    public static final String SOURCE_RUNTIME = "runtime setting";
    /** 工作区来源：进程工作目录 */
    public static final String SOURCE_CWD = "process working directory";

    /** 只读模式：禁止修改任何文件 */
    public static final String PERMISSION_READONLY = "readonly";
    /** 工作区修改模式：只允许修改工作区内的文件 */
    public static final String PERMISSION_WORKSPACE = "workspace";
    /** 工作区外审批模式：工作区内直接修改，工作区外弹窗审批 */
    public static final String PERMISSION_APPROVAL = "approval";
    /** 完全权限：允许修改任意位置的文件 */
    public static final String PERMISSION_FULL = "full";

    /** 资源权限配置键（替代旧的 workspace.write.mode） */
    public static final String PERMISSION_KEY = "permission.resource.level";
    /** 操作权限配置键（鼠标/键盘/屏幕/串口/BLE/WiFi/USB 等外设操作） */
    public static final String OPERATION_PERMISSION_KEY = "permission.operation.level";
    /** 旧的工作区写权限键：只做兼容读取，设置时自动从配置文件中清理 */
    public static final String LEGACY_PERMISSION_KEY = "workspace.write.mode";
    /** 工作区内配置文件相对路径（与历史路径保持一致） */
    public static final String SETTINGS_RELATIVE_PATH = ".dlqq/settings/settings.properties";

    private Workspace() {
    }

    /**
     * 初始化默认基准工作区（服务启动时调用一次）：优先启动参数，否则进程 CWD。
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
     * @throws IllegalArgumentException 目录不存在或不是目录
     */
    public static void setRoot(String directory) {
        Path path = toExistingDirectory(directory);
        if (path == null) {
            throw new IllegalArgumentException("工作区必须是已存在的绝对目录: " + directory);
        }
        SessionScope.setCurrentRoot(path, SOURCE_RUNTIME);
    }

    /** 把当前会话回退到启动参数指定的工作区（若有效），否则回退到进程 CWD。 */
    public static void resetToDefault() {
        SessionScope.resetCurrentToDefault();
    }

    /** 当前会话的工作区根目录（绝对路径）。 */
    public static Path getRoot() {
        Path root = SessionScope.currentRoot();
        ensureDlqqMetadata(root);
        return root;
    }

    /**
     * 确保工作区下的 .dlqq 目录存在并带有 .gitignore（内容为 *，即忽略该目录全部内容）。
     *
     * <p>放在 getRoot() 里统一兜底：无论 .dlqq 是由日志初始化、缓存目录、
     * 设置文件还是后台程序目录首次创建，只要访问过工作区，就一定会补齐
     * .gitignore，避免日志/缓存被误提交到 git。检查结果按会话缓存。</p>
     */
    private static void ensureDlqqMetadata(Path base) {
        if (base == null) {
            return;
        }
        String key = base.toAbsolutePath().toString();
        if (key.equals(SessionScope.currentEnsuredRoot())) {
            return;
        }
        try {
            Path dlqqDir = base.resolve(".dlqq");
            if (!Files.isDirectory(dlqqDir)) {
                Files.createDirectories(dlqqDir);
            }
            Path gitignore = dlqqDir.resolve(".gitignore");
            if (!Files.exists(gitignore)) {
                Files.writeString(gitignore, "*\n", StandardCharsets.UTF_8);
            }
            // Windows 下把 .dlqq 设为隐藏目录（资源管理器默认不可见，避免误提交/误删）
            try {
                DosFileAttributeView dosView = Files.getFileAttributeView(dlqqDir, DosFileAttributeView.class);
                if (dosView != null) {
                    dosView.setHidden(true);
                }
            } catch (Exception ignored) {
                // 非 Windows 平台或文件系统不支持时忽略
            }
            SessionScope.setCurrentEnsuredRoot(key);
        } catch (Exception ignored) {
            // 只读工作区或权限不足时忽略，不影响主流程
        }
    }

    /** 当前会话的工作区来源说明。 */
    public static String getSource() {
        return SessionScope.currentSource();
    }

    /** 启动参数指定的工作区（未指定时返回 null）。 */
    public static String getCliPath() {
        return SessionScope.getBaseCliPath();
    }

    /** 当前会话的修改权限：readonly / workspace / approval / full。 */
    public static String getPermission() {
        return SessionScope.currentPermission();
    }

    /** 修改权限的中文说明。 */
    public static String getPermissionLabel() {
        switch (getPermission()) {
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

    /** 当前会话工作区的 settings.properties 路径（权限与其它配置都存这里）。 */
    public static Path getSettingsFile() {
        return SessionScope.currentRoot().resolve(SETTINGS_RELATIVE_PATH).normalize();
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
     * 检查是否允许修改指定路径（写操作前调用）。
     *
     * @param path 已解析的绝对路径
     * @return null 表示允许；否则返回错误消息
     */
    public static String checkWrite(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        switch (getPermission()) {
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
     * 设置当前会话工作区的修改权限，并持久化到该工作区的 settings.properties
     * （UTF-8，保留其它配置与注释）。
     *
     * @param mode readonly / workspace / approval / full
     * @throws IllegalArgumentException 模式非法或写入配置失败
     */
    public static void setPermission(String mode) {
        String normalized = normalizePermission(mode);
        if (normalized == null) {
            throw new IllegalArgumentException("权限模式必须是 readonly、workspace、approval 或 full: " + mode);
        }
        try {
            writeSettingsValue(getSettingsFile(), PERMISSION_KEY, normalized);
            removeLegacyPermissionKey(getSettingsFile());
        } catch (IOException e) {
            throw new IllegalArgumentException("写入资源权限配置失败: " + e.getMessage());
        }
        SessionScope.setCurrentPermission(normalized);
    }

    /** 当前会话的操作权限：readonly / approval / highrisk / full（缺省 approval）。 */
    public static String getOperationPermission() {
        String value = getSettingValue(OPERATION_PERMISSION_KEY);
        String normalized = normalizePermission(value);
        return normalized == null ? PERMISSION_APPROVAL : normalized;
    }

    /** 操作权限的中文说明。 */
    public static String getOperationPermissionLabel() {
        switch (getOperationPermission()) {
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
     * 读取指定工作区目录配置的资源权限（用于工作区切换时的提权判断）。
     *
     * @param root 目标工作区根目录
     * @return 资源权限；未配置时为默认值 approval
     */
    public static String getPermissionOf(Path root) {
        if (root == null) {
            return PERMISSION_APPROVAL;
        }
        Path settingsFile = root.resolve(SETTINGS_RELATIVE_PATH).normalize();
        String value = readSettingsValue(settingsFile, PERMISSION_KEY);
        String normalized = normalizePermission(value);
        return normalized == null ? PERMISSION_APPROVAL : normalized;
    }

    /**
     * 设置当前会话的操作权限并持久化。
     *
     * @param mode readonly / approval / highrisk / full
     * @throws IllegalArgumentException 模式非法或写入失败
     */
    public static void setOperationPermission(String mode) {
        String normalized = normalizeOperationPermission(mode);
        if (normalized == null) {
            throw new IllegalArgumentException("操作权限必须是 readonly、approval、highrisk 或 full: " + mode);
        }
        try {
            writeSettingsValue(getSettingsFile(), OPERATION_PERMISSION_KEY, normalized);
        } catch (IOException e) {
            throw new IllegalArgumentException("写入操作权限配置失败: " + e.getMessage());
        }
    }

    private static String normalizeOperationPermission(String mode) {
        if (mode == null) {
            return null;
        }
        switch (mode.trim().toLowerCase()) {
            case "readonly":
            case "approval":
            case "highrisk":
            case "full":
                return mode.trim().toLowerCase();
            default:
                return null;
        }
    }

    /** 从配置文件中移除旧键 workspace.write.mode（迁移用）。 */
    private static void removeLegacyPermissionKey(Path settingsFile) throws IOException {
        if (!Files.isRegularFile(settingsFile)) {
            return;
        }
        java.util.List<String> lines = Files.readAllLines(settingsFile, StandardCharsets.UTF_8);
        java.util.List<String> kept = new java.util.ArrayList<>(lines.size());
        boolean changed = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(LEGACY_PERMISSION_KEY + "=")) {
                changed = true;
                continue;
            }
            kept.add(line);
        }
        if (changed) {
            Files.write(settingsFile, kept, StandardCharsets.UTF_8);
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

    /** 读取当前会话工作区配置值；文件或键不存在时返回 null。 */
    public static String getSettingValue(String key) {
        return readSettingsValue(getSettingsFile(), key);
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
            case PERMISSION_READONLY:
            case PERMISSION_WORKSPACE:
            case PERMISSION_APPROVAL:
            case PERMISSION_FULL:
                return value;
            default:
                return null;
        }
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
