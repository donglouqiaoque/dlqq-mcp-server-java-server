package com.donglouqiaoque.dlqqmcpserver.mcp.policy;

import java.util.Map;

/**
 * 按工具定制的审批窗口内容。
 *
 * <p>原则：</p>
 * <ul>
 *   <li>每个需要审批的工具都有贴合自身操作的标题与说明，不允许出现"空信息"审批框；</li>
 *   <li>已经自带确认窗口的工具（命令执行、删除文件/目录、修改/重置工作区、权限提权）
 *       由工具自身窗口负责，不走本类，避免重复审批。</li>
 * </ul>
 */
public final class ApprovalPrompt {

    private ApprovalPrompt() {
    }

    /** 已自带审批窗口的工具（策略层不再弹窗）。 */
    public static boolean hasBuiltinApproval(String toolName) {
        return switch (toolName) {
            case "dlqq_run_command", "dlqq_run_powershell", "dlqq_run_pwsh",
                    "dlqq_delete_file", "dlqq_delete_dir",
                    "dlqq_workspace_set", "dlqq_workspace_reset",
                    "dlqq_workspace_permission_set" -> true;
            default -> false;
        };
    }

    /** 审批窗口标题（按工具定制）。 */
    public static String title(String toolName) {
        return switch (toolName) {
            case "dlqq_overwrite_file" -> "覆盖文件确认";
            case "dlqq_clear_file" -> "清空文件确认";
            case "dlqq_replace_text_in_dir" -> "批量替换确认";
            case "dlqq_move_file" -> "移动文件确认";
            case "dlqq_move_dir" -> "移动目录确认";
            case "dlqq_zip" -> "压缩确认";
            case "dlqq_unzip" -> "解压确认";
            case "dlqq_kill_command" -> "终止 cmd 后台程序确认";
            case "dlqq_kill_powershell" -> "终止 PowerShell 5.1 后台程序确认";
            case "dlqq_kill_pwsh" -> "终止 PowerShell 7 后台程序确认";
            case "dlqq_mcp_hide_tools" -> "隐藏工具确认";
            case "dlqq_mcp_show_tools" -> "恢复工具确认";
            case "dlqq_mcp_reset_tools" -> "重置工具列表确认";
            case "dlqq_mcp_run_tools" -> "绕过限制调用工具确认";
            case "dlqq_clear_mcp_data" -> "清除 MCP 数据确认";
            case "dlqq_capture_screen" -> "屏幕截图确认";
            default -> "工具执行确认";
        };
    }

    /** 审批窗口正文：包含工具、风险、原因、影响范围与关键参数。 */
    public static String message(String toolName, Map<String, Object> arguments, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append(operationSummary(toolName, arguments)).append("\n\n");
        sb.append("工具: ").append(toolName).append("\n");
        sb.append(ToolPolicy.riskSummary(toolName)).append("\n");
        if (reason != null && !reason.isBlank()) {
            sb.append("原因: ").append(reason).append("\n");
        }
        sb.append("影响范围: ")
                .append(ToolPolicy.argumentsInsideWorkspace(arguments) ? "工作区内" : "工作区外")
                .append("\n");
        String params = parameterSummary(arguments);
        if (!params.isEmpty()) {
            sb.append("\n关键参数:\n").append(params);
        }
        sb.append("\n\n是否允许本次执行？");
        return sb.toString();
    }

    /** 各工具的操作说明（含目标对象）。 */
    private static String operationSummary(String toolName, Map<String, Object> arguments) {
        return switch (toolName) {
            case "dlqq_overwrite_file" -> "将完整覆盖以下文件，原内容不可恢复：\n" + value(arguments, "file_path");
            case "dlqq_clear_file" -> "将清空以下文件（保留文件本身）：\n" + value(arguments, "file_path");
            case "dlqq_replace_text_in_dir" -> "将在以下目录内批量替换文本：\n" + value(arguments, "dir_path");
            case "dlqq_move_file" -> "将移动文件：\n" + value(arguments, "source_path")
                    + "\n→ " + value(arguments, "target_path");
            case "dlqq_move_dir" -> "将移动目录（目标已存在会先删除）：\n" + value(arguments, "source_path")
                    + "\n→ " + value(arguments, "target_path");
            case "dlqq_zip" -> "将压缩到：\n" + value(arguments, "output_path");
            case "dlqq_unzip" -> "将解压到：\n" + value(arguments, "output_dir");
            case "dlqq_kill_command", "dlqq_kill_powershell", "dlqq_kill_pwsh" ->
                    "将强制终止后台程序：" + value(arguments, "id");
            case "dlqq_mcp_hide_tools", "dlqq_mcp_show_tools" ->
                    "将调整以下工具对 AI 的可见性：" + value(arguments, "tool_names");
            case "dlqq_mcp_reset_tools" -> "将恢复所有被隐藏的工具，并清空排除列表。";
            case "dlqq_mcp_run_tools" -> "将绕过可见性限制，直接调用工具：" + value(arguments, "name");
            case "dlqq_clear_mcp_data" -> "将删除整个 .dlqq 数据目录（配置、日志、缓存、脚本全部清除，不可恢复）。";
            case "dlqq_capture_screen" -> "将截取当前屏幕内容并保存为图片文件。";
            default -> "AI 请求执行操作：" + toolName;
        };
    }

    private static String parameterSummary(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String key = entry.getKey();
            if ("_meta".equals(key)) {
                continue;
            }
            if (count++ >= 6) {
                sb.append("  ...\n");
                break;
            }
            sb.append("  ").append(key).append(" = ").append(truncate(String.valueOf(entry.getValue())))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String value(Map<String, Object> arguments, String key) {
        if (arguments == null || arguments.get(key) == null) {
            return "（未提供）";
        }
        return truncate(String.valueOf(arguments.get(key)));
    }

    private static String truncate(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() <= 300 ? value : value.substring(0, 300) + "…（已截断）";
    }
}
