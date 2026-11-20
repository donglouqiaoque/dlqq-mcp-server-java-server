package com.donglouqiaoque.dlqqmcpserver.mcp.policy;

import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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

    public enum Decision { ALLOW, APPROVE, DENY }

    private static final String RESOURCE_LEVEL_KEY = "permission.resource.level";
    private static final String OPERATION_LEVEL_KEY = "permission.operation.level";
    private static final String LEGACY_OPERATION_LEVEL_KEY = "permission.peripheral.level";
    private static final String LEGACY_WRITE_MODE_KEY = "workspace.write.mode";
    private static final String CUSTOM_PREFIX = "tool.policy.";

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
        // .dlqq 目录（配置、日志、缓存、脚本）一律禁止 AI 修改，与权限等级无关
        if (risk[0] != Risk.SAFE && touchesDlqq(arguments)) {
            return Result.deny("禁止修改 .dlqq 目录内的文件（配置、日志、缓存由服务端管理，任何权限等级均不可修改）");
        }
        boolean inside = isInsideWorkspace(arguments);

        var resource = decideResource(risk[0], inside);
        var operation = decideOperation(risk[1]);
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

    /** 工具风险摘要（用于审批窗口展示）。 */
    public static String riskSummary(String toolName) {
        Risk[] risk = RISKS.getOrDefault(toolName, new Risk[] {Risk.SAFE, Risk.SAFE});
        return "资源风险：" + riskLabel(risk[0]) + "；操作风险：" + riskLabel(risk[1]);
    }

    /** 参数中的路径是否位于当前工作区内。 */
    public static boolean argumentsInsideWorkspace(Map<String, Object> arguments) {
        return isInsideWorkspace(arguments);
    }

    /** 参数中是否包含 .dlqq 路径段。 */
    @SuppressWarnings("unchecked")
    private static boolean touchesDlqq(Map<String, Object> arguments) {
        if (arguments == null) {
            return false;
        }
        // dlqq_mcp_run_tools 等嵌套调用：继续检查内层 arguments
        Object nested = arguments.get("arguments");
        if (nested instanceof Map<?, ?> nestedMap && touchesDlqq((Map<String, Object>) nestedMap)) {
            return true;
        }
        for (String key : new String[] {"path", "file_path", "dir_path", "search_path",
                "source_path", "target_path", "new_path", "old_path"}) {
            Object value = arguments.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim().replace('\\', '/');
            if (text.isEmpty()) {
                continue;
            }
            for (String segment : text.split("/")) {
                if (".dlqq".equalsIgnoreCase(segment.trim())) {
                    return true;
                }
            }
        }
        return false;
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

    /** 资源权限等级：默认“工作区外需要审批”（approval），旧键 workspace.write.mode 已废弃。 */
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

        public Decision decision() {
            return decision;
        }

        public String message() {
            return message;
        }
    }
}
