package com.donglouqiaoque.dlqqmcpserver.mcp;

import java.util.Set;

/**
 * 工具目录状态：源码屏蔽工具清单。
 *
 * <p>这些工具在 {@code AITools} 中按源码屏蔽方式保留注册代码（注册调用被注释），
 * 因此不会出现在运行时工具列表中，但仍需要在工具管理选项卡中展示并允许配置其风险等级。</p>
 */
public final class ToolCatalog {

    /** 当前源码屏蔽、不注册到运行时的工具。 */
    public static final Set<String> SOURCE_MASKED = Set.of(
            // 项目构建 / 芯片 / 运行 / 调试（dlqq_build_project、dlqq_set_build_script 已恢复）
            "dlqq_rebuild_project",
            "dlqq_chip_erase",
            "dlqq_chip_download",
            "dlqq_erase_download",
            "dlqq_build_erase_download",
            "dlqq_rebuild_erase_download",
            "dlqq_run_project",
            "dlqq_debug_project",
            "dlqq_build_run_project",
            "dlqq_build_debug_project",
            "dlqq_rebuild_run_project",
            "dlqq_rebuild_debug_project",
            // 脚本路径设置（dlqq_set_build_script 已恢复）
            "dlqq_set_rebuild_script",
            "dlqq_set_chip_erase_script",
            "dlqq_set_chip_download_script",
            "dlqq_set_erase_download_script",
            "dlqq_set_build_erase_download_script",
            "dlqq_set_rebuild_erase_download_script",
            "dlqq_set_run_script",
            "dlqq_set_debug_script",
            "dlqq_set_build_run_script",
            "dlqq_set_build_debug_script",
            "dlqq_set_rebuild_run_script",
            "dlqq_set_rebuild_debug_script",
            // 动态工具管理（仅保留 dlqq_mcp_refresh_tools）
            "dlqq_mcp_hide_tools",
            "dlqq_mcp_show_tools",
            "dlqq_mcp_reset_tools",
            "dlqq_mcp_run_tools",
            // 热加载测试工具
            "dlqq_test_hot_reload");

    private ToolCatalog() {
    }

    /** 工具是否为源码屏蔽状态。 */
    public static boolean isSourceMasked(String name) {
        return name != null && SOURCE_MASKED.contains(name);
    }
}
