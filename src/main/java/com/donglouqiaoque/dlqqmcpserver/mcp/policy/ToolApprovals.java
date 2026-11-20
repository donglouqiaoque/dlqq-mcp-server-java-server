package com.donglouqiaoque.dlqqmcpserver.mcp.policy;

import com.donglouqiaoque.dlqqmcpserver.mcp.tools.DialogTools;

import java.util.Map;

/**
 * 工具级审批入口。
 *
 * <p>每个高危工具在执行前调用本类，弹出<b>该工具专属</b>的审批窗口
 * （标题与说明由 {@link ApprovalPrompt} 按工具定制），倒计时与工具总超时一致。</p>
 *
 * <p>已经自带审批窗口的工具（命令执行、删除文件/目录、修改/重置工作区、
 * 权限提权）不在此重复弹窗。</p>
 */
public final class ToolApprovals {

    private ToolApprovals() {
    }

    /**
     * 高危工具执行前审批。
     *
     * @param toolName  工具名
     * @param arguments 工具参数
     * @return true=可以执行（用户批准或该工具自带窗口）；false=用户拒绝/超时未批准
     */
    public static boolean confirm(String toolName, Map<String, Object> arguments) {
        if (ApprovalPrompt.hasBuiltinApproval(toolName)) {
            // 工具自带审批窗口，避免重复批准
            return true;
        }
        return DialogTools.showApprovalDialog(
                ApprovalPrompt.title(toolName),
                ApprovalPrompt.message(toolName, arguments, null));
    }

    /** 取消时的返回文本（区分超时与拒绝）。 */
    public static String cancelledMessage(String toolName) {
        return DialogTools.lastApprovalTimedOut()
                ? "【操作已取消】审批超时（工具总时限已到），操作未执行：" + toolName
                : "【操作已取消】用户拒绝了本次操作：" + toolName;
    }
}
