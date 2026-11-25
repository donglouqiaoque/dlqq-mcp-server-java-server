package com.donglouqiaoque.dlqqmcpserver.mcp.policy;

import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具权限策略。
 *
 * <p>依据内置风险表（resources/tool_policy.txt，来自《MCP 工具风险与隐藏状态清单》）
 * 与 AI 权限等级设置，在每次 tools/call 前判定：允许 / 需要用户批准 / 直接拒绝。</p>
 *
 * <p>设置项（工作区 .dlqq/settings/settings.properties）：</p>
 * <ul>
 *   <li>{@code permission.resource.level}：对话模式 / 只读 / workspace / approval（默认） /
 *       highrisk / full / custom</li>
 *   <li>{@code permission.peripheral.level}：对话模式 / 只读 / approval（默认） / highrisk /
 *       full / custom</li>
 *   <li>{@code tool.policy.<工具名>=allow|approve|deny}：自定义权限时逐工具覆盖</li>
 * </ul>
 */
public final class ToolPolicy {

    public enum Risk { SAFE, RISK, HIGH }

    public enum Decision { ALLOW, APPROVE, DENY, ERROR }

    private static final String RESOURCE_LEVEL_KEY = "permission.resource.level";
    private static final String OPERATION_LEVEL_KEY = "permission.operation.level";
    private static final String LEGACY_OPERATION_LEVEL_KEY = "permission.peripheral.level";
    private static final String CUSTOM_PREFIX = "tool.policy.";
    /** .dlqq 目录拒绝消息（读与写都拒绝，cache 会话目录除外）。 */
    private static final String DLQQ_DENY_MESSAGE =
            "禁止访问 .dlqq 目录（配置/日志/缓存/数据由服务端管理，任何权限等级均不可读写；仅当前会话缓存目录内容除外）";
    /** 服务端内部执行的例外工具：不受 .dlqq 文件访问规则限制。 */
    private static final Set<String> MANAGEMENT_TOOLS = Set.of(
            "dlqq_clear_mcp_cache",
            "dlqq_clear_mcp_log",
            "dlqq_clear_mcp_data",
            "dlqq_get_mcp_properties",
            "dlqq_set_mcp_properties",
            "dlqq_reset_mcp_properties",
            "dlqq_workspace_set",
            "dlqq_workspace_get",
            "dlqq_workspace_reset",
            "dlqq_workspace_permission_set",
            "dlqq_get_mcp_version",
            "dlqq_get_cwd_path",
            "dlqq_get_tool_list",
            "dlqq_get_tools_env",
            "dlqq_clear_command_list",
            "dlqq_clear_powershell_list",
            "dlqq_clear_pwsh_list");

    private static final Map<String, Risk[]> RISKS = new HashMap<>();
    private static final Map<String, Boolean> HIDDEN = new HashMap<>();

    static {
        loadResource();
    }

    private ToolPolicy() {
    }

    /** 判定一次工具调用。 */
    public static Result decide(String toolName, Map<String, Object> arguments) {
        // 权限/工作区管理工具：任意权限等级下都可调用；
        // 降权直接执行、提权由工具自身弹带倒计时的审批窗口（与提权工具逻辑一致）
        if ("dlqq_workspace_permission_set".equals(toolName)
                || "dlqq_workspace_set".equals(toolName)
                || "dlqq_workspace_reset".equals(toolName)) {
            return Result.allow();
        }
        String custom = setting(CUSTOM_PREFIX + toolName);
        if (custom != null) {
            String value = custom.trim().toLowerCase();
            if ("allow".equals(value)) {
                return Result.allow();
            }
            if ("deny".equals(value)) {
                return Result.deny("自定义权限禁止执行该工具（tool.policy." + toolName + "=deny）");
            }
            if ("approve".equals(value)) {
                return Result.approve("自定义权限要求用户批准后执行该工具");
            }
        }
        Risk[] risk = RISKS.getOrDefault(toolName, new Risk[] {Risk.SAFE, Risk.SAFE});
        // .dlqq 目录（配置/日志/缓存/数据）对 AI 一律封闭；仅当前会话缓存目录内的内容允许读写
        if (!MANAGEMENT_TOOLS.contains(toolName)) {
            Result dlqq = decideDlqqAccess(arguments);
            if (dlqq != null) {
                return dlqq;
            }
        }

        // dlqq_overwrite_file：审批前评估覆盖幅度——
        // 文件不存在直接报错（提示 create_file）；小文件且相似度 >= 90% 时降级为风险工具；
        // 文件过大/无法读取/相似度不足时保持高危。
        if ("dlqq_overwrite_file".equals(toolName)) {
            OverwriteRiskAssessor.Result assessment = OverwriteRiskAssessor.assess(arguments);
            if (assessment.decision() == OverwriteRiskAssessor.Decision.ERROR) {
                return Result.error(assessment.message());
            }
            if (assessment.decision() == OverwriteRiskAssessor.Decision.DOWNGRADE) {
                risk[0] = Risk.RISK;
            }
        }

        // 通用属性工具禁止修改/重置权限、脚本、工具管理等受保护属性：直接拒绝，不进入审批。
        if ("dlqq_set_mcp_properties".equals(toolName)) {
            Object key = arguments == null ? null : arguments.get("key");
            Object value = arguments == null ? null : arguments.get("value");
            String text = key instanceof String valueText ? valueText : "";
            String newValue = value instanceof String text2 ? text2 : "";
            if (text.isBlank() || newValue.isEmpty()) {
                return Result.error("必须同时指定 key 和 value；查询属性请使用 dlqq_get_mcp_properties");
            }
            if (isProtectedPropertyKey(text)) {
                return Result.error("拒绝修改：" + text
                        + " 属于权限/脚本/工具管理保护属性，不能通过 dlqq_set_mcp_properties 修改");
            }
        }
        if ("dlqq_reset_mcp_properties".equals(toolName)) {
            Object key = arguments == null ? null : arguments.get("key");
            Object keys = arguments == null ? null : arguments.get("keys");
            String text = key instanceof String valueText ? valueText : "";
            boolean hasKey = !text.isBlank();
            boolean hasKeys = keys instanceof List<?> list && !list.isEmpty();
            if (hasKey && hasKeys) {
                return Result.error("不能同时指定 key 和 keys，请二选一");
            }
            if (hasKey && isProtectedPropertyKey(text)) {
                return Result.error("拒绝重置：" + text
                        + " 属于权限/脚本/工具管理保护属性");
            }
        }

        boolean inside = isInsideWorkspace(arguments);

        // 只在工具确实使用对应能力时才判断该维度权限：
        // 无资源读写（资源风险 SAFE）不判资源权限；无外设操作（操作风险 SAFE）不判操作权限；
        // 两者都使用的工具（如截图）才两个维度都判断。
        Result resource = risk[0] == Risk.SAFE ? Result.allow() : decideResource(risk[0], inside);
        Result operation = risk[1] == Risk.SAFE ? Result.allow() : decideOperation(risk[1]);
        if (resource.decision == Decision.DENY) {
            return resource;
        }
        if (operation.decision == Decision.DENY) {
            return operation;
        }
        if (resource.decision == Decision.APPROVE) {
            return resource;
        }
        if (operation.decision == Decision.APPROVE) {
            return operation;
        }
        return Result.allow();
    }

    /** 工具是否默认隐藏（源码屏蔽类工具）。 */
    public static boolean isHiddenByDefault(String toolName) {
        return Boolean.TRUE.equals(HIDDEN.get(toolName));
    }

    /**
     * 属性是否属于权限/脚本/工具管理等受保护配置。
     * 这类配置只能通过专用安全工具修改，不允许通过通用属性工具设置或重置。
     */
    public static boolean isProtectedPropertyKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return key.startsWith("permission.")
                || key.startsWith("script.")
                || key.startsWith("tool.policy.")
                || key.startsWith("mcp.tools.");
    }

    /** 工具风险摘要（用于审批窗口展示）。 */
    public static String riskSummary(String toolName) {
        Risk[] risk = RISKS.getOrDefault(toolName, new Risk[] {Risk.SAFE, Risk.SAFE});
        return "资源风险：" + riskLabel(risk[0]) + "；操作风险：" + riskLabel(risk[1]);
    }

    /** 参数中的路径是否位于当前工作区内。 */
    public static boolean argumentsInsideWorkspace(Map<String, Object> arguments) {
        return isInsideWorkspace(arguments);
    }

    /**
     * 判定本次调用涉及的 .dlqq 路径：没有任何 .dlqq 路径时返回 null（继续常规权限判定）；
     * 全部位于当前工作区缓存目录内容内时直接允许；其余情况拒绝。
     */
    private static Result decideDlqqAccess(Map<String, Object> arguments) {
        List<Path> touched = new ArrayList<>();
        collectTouchedPaths(arguments, touched);
        boolean anyDlqq = false;
        Path cacheDir = currentCacheDirOrNull();
        for (Path path : touched) {
            if (!Workspace.touchesDlqq(path)) {
                continue;
            }
            anyDlqq = true;
            if (cacheDir == null) {
                return Result.deny(DLQQ_DENY_MESSAGE);
            }
            Path normalized = path.toAbsolutePath().normalize();
            if (normalized.equals(cacheDir) || cacheDir.startsWith(normalized)) {
                return Result.deny("禁止修改缓存目录节点（只能读写当前工作区缓存目录内的内容）");
            }
            if (!normalized.startsWith(cacheDir)) {
                return Result.deny(DLQQ_DENY_MESSAGE);
            }
        }
        if (!anyDlqq) {
            return null;
        }
        for (Path path : touched) {
            if (!path.toAbsolutePath().normalize().startsWith(cacheDir)) {
                // 混合路径（如缓存目录内文件移动到工作区）：交由常规权限判定
                return null;
            }
        }
        return Result.allow();
    }

    /** 收集参数中出现的路径（含 dlqq_mcp_run_tools 的嵌套 arguments）。 */
    @SuppressWarnings("unchecked")
    private static void collectTouchedPaths(Map<String, Object> arguments, List<Path> out) {
        if (arguments == null) {
            return;
        }
        Object nested = arguments.get("arguments");
        if (nested instanceof Map<?, ?> nestedMap) {
            collectTouchedPaths((Map<String, Object>) nestedMap, out);
        }
        for (String key : new String[] {"path", "file_path", "dir_path", "search_path",
                "source_path", "target_path", "new_path", "old_path", "output_path", "output_dir"}) {
            Object value = arguments.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                out.add(Workspace.resolve(text));
            } catch (RuntimeException ignored) {
                // 非法路径交由工具自身校验
            }
        }
    }

    /** 当前工作区缓存目录；未初始化或异常时返回 null（按拒绝处理）。 */
    private static Path currentCacheDirOrNull() {
        try {
            return SessionScope.currentCacheDir().toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String riskLabel(Risk risk) {
        if (risk == Risk.HIGH) {
            return "高危";
        }
        if (risk == Risk.RISK) {
            return "风险";
        }
        return "无危";
    }

    private static Result decideResource(Risk risk, boolean inside) {
        String level = resourceLevel();
        if ("custom".equals(level)) {
            return Result.allow();
        }
        if ("chat".equals(level)) {
            return Result.deny("当前为对话模式：禁止任何资源操作");
        }
        if ("full".equals(level)) {
            return Result.allow();
        }
        if (risk == Risk.SAFE) {
            return Result.allow();
        }
        if ("readonly".equals(level)) {
            return Result.deny("资源权限为只读：禁止修改设备资源");
        }
        if (risk == Risk.HIGH) {
            if ("highrisk".equals(level) || "approval".equals(level)) {
                return Result.approve("高危资源工具需要用户批准后执行");
            }
            return Result.deny("当前资源权限等级禁止高危工具（" + level + "）");
        }
        // risk == RISK
        if ("workspace".equals(level)) {
            return inside ? Result.allow() : Result.deny("当前为工作区内修改模式：禁止修改工作区外内容");
        }
        if ("approval".equals(level)) {
            return inside ? Result.allow() : Result.approve("需要用户批准后修改工作区外内容");
        }
        if ("highrisk".equals(level)) {
            return Result.allow();
        }
        return Result.allow();
    }

    private static Result decideOperation(Risk risk) {
        String level = operationLevel();
        if ("custom".equals(level)) {
            return Result.allow();
        }
        if ("chat".equals(level)) {
            return Result.deny("当前为对话模式：禁止任何外设操作");
        }
        if ("full".equals(level)) {
            return Result.allow();
        }
        if (risk == Risk.SAFE) {
            return Result.allow();
        }
        if ("readonly".equals(level)) {
            return Result.deny("操作权限为只读：禁止操作设备外设");
        }
        if (risk == Risk.HIGH) {
            return Result.approve("高危外设操作需要用户批准后执行");
        }
        if ("approval".equals(level) || "highrisk".equals(level)) {
            return Result.approve("外设操作需要用户批准后执行");
        }
        return Result.allow();
    }

    /** 资源权限等级：默认“工作区外需要审批”（approval）。 */
    private static String resourceLevel() {
        String level = level(RESOURCE_LEVEL_KEY, null);
        return level == null ? "approval" : level;
    }

    /** 操作权限等级（新键优先，历史 peripheral 键兼容）。 */
    private static String operationLevel() {
        String level = level(OPERATION_LEVEL_KEY, null);
        if (level == null) {
            level = level(LEGACY_OPERATION_LEVEL_KEY, null);
        }
        return level == null ? "approval" : level;
    }

    /** 判断参数中的路径是否位于当前工作区内（无路径参数时按工作区内处理）。 */
    private static boolean isInsideWorkspace(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return true;
        }
        Path root = Workspace.getRoot().toAbsolutePath().normalize();
        String rootText = normalizeForCompare(root);
        for (String key : new String[] {"path", "file_path", "dir_path", "search_path",
                "source_path", "target_path", "new_path", "old_path"}) {
            Object value = arguments.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                Path resolved = Workspace.resolve(text).toAbsolutePath().normalize();
                String resolvedText = normalizeForCompare(resolved);
                // 先做规范化字符串前缀比较（大小写不敏感，Windows 盘符/路径分隔符差异可兼容），
                // 再进行 Path 前缀比较（处理符号链接/8.3 短路径等场景）
                boolean inside = resolvedText.equals(rootText)
                        || resolvedText.startsWith(rootText.endsWith("/") ? rootText : rootText + "/")
                        || resolved.startsWith(root);
                if (!inside) {
                    try {
                        Path realRoot = root.toRealPath();
                        Path realResolved = resolved.toRealPath();
                        if (realResolved.startsWith(realRoot)) {
                            inside = true;
                        }
                    } catch (Exception ignored) {
                        // 文件不存在时保持原判断
                    }
                }
                if (!inside) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        return true;
    }

    /** 统一路径比较格式：绝对路径、反斜杠、小写、去尾部分隔符。 */
    private static String normalizeForCompare(Path path) {
        String text = path.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase();
        while (text.endsWith("/") && text.length() > 2) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String level(String key, String fallback) {
        String value = setting(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String text = value.trim().toLowerCase();
        return text.isEmpty() ? fallback : text;
    }

    private static String setting(String key) {
        try {
            return Workspace.getSettingValue(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static void loadResource() {
        try (InputStream in = ToolPolicy.class.getResourceAsStream("/tool_policy.txt")) {
            if (in == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text = line.trim();
                    if (text.isEmpty() || text.startsWith("#")) {
                        continue;
                    }
                    String[] parts = text.split("\\|");
                    if (parts.length < 4) {
                        continue;
                    }
                    Risk resourceRisk = parseRisk(parts[1]);
                    Risk peripheralRisk = parseRisk(parts[2]);
                    RISKS.put(parts[0].trim(), new Risk[] {resourceRisk, peripheralRisk});
                    if ("1".equals(parts[3].trim())) {
                        HIDDEN.put(parts[0].trim(), true);
                    }
                }
            }
        } catch (Exception ignored) {
            // 策略表加载失败时保持空表，所有工具按安全级放行，不影响服务启动
        }
    }

    private static Risk parseRisk(String text) {
        String value = text.trim().toLowerCase();
        if ("h".equals(value)) {
            return Risk.HIGH;
        }
        if ("r".equals(value)) {
            return Risk.RISK;
        }
        return Risk.SAFE;
    }

    /** 判定结果。 */
    public static final class Result {
        private final Decision decision;
        private final String message;

        private Result(Decision decision, String message) {
            this.decision = decision;
            this.message = message;
        }

        private static Result allow() {
            return new Result(Decision.ALLOW, null);
        }

        private static Result approve(String message) {
            return new Result(Decision.APPROVE, message);
        }

        private static Result deny(String message) {
            return new Result(Decision.DENY, message);
        }

        /** 预检失败：直接返回工具错误（不弹审批，也不带“权限拒绝”前缀）。 */
        private static Result error(String message) {
            return new Result(Decision.ERROR, message);
        }

        public Decision decision() {
            return decision;
        }

        public String message() {
            return message;
        }
    }
}
