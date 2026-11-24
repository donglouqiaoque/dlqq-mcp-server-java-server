package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import com.donglouqiaoque.dlqqmcpserver.mcp.device.BleDevices;
import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.McpSchema.*;
import com.donglouqiaoque.dlqqmcpserver.mcp.server.McpServer;
import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;
import java.util.List;

/**
 * 工具注册中心 - 将各分组工具注册到 MCP 服务器（dlqq-mcp-server）
 */
public class AITools {

    /**
     * 注册所有 MCP 工具到服务器
     */
    public void registerTools(McpServer server) {
        // ========== 文件操作工具 (1-11) ==========
        registerReadFileTool(server);           // dlqq_read_file
        registerCreateFileTool(server);           // dlqq_create_file
        registerOverwriteFileTool(server);        // dlqq_overwrite_file
        registerClearFileTool(server); // dlqq_clear_file
        registerAppendFileTool(server);           // dlqq_append_file
        registerRemoveLinesTool(server);          // dlqq_remove_lines_in_file
        registerReplaceLinesTool(server);         // dlqq_replace_lines_in_file
        registerInsertTextTool(server);           // dlqq_insert_text_in_file
        registerReplaceTextTool(server);          // dlqq_replace_text_in_file
        registerRemoveTextInFileTool(server);   // dlqq_remove_text_in_file
        registerReplaceTextInDirTool(server);   // dlqq_replace_text_in_dir
        registerDeleteFileTool(server);           // dlqq_delete_file
        registerCopyFileTool(server);             // dlqq_copy_file
        registerMoveFileTool(server);             // dlqq_move_file
        registerRenameFileTool(server);           // dlqq_rename_file

        // ========== 目录操作工具 (12-19) ==========
        registerCreateDirTool(server);            // dlqq_mdir
        registerCreateDirsTool(server);           // dlqq_mdirs
        registerDeleteDirTool(server);            // dlqq_delete_dir
        registerCopyDirTool(server);              // dlqq_copy_dir
        registerMoveDirTool(server);              // dlqq_move_dir
        registerRenameDirTool(server);              // dlqq_rename_dir
        registerListDirTreeTool(server);            // dlqq_list_dir_tree
        registerListDirFilesTool(server);         // dlqq_list_dir_files

        // ========== 二进制文件操作工具 (20-21) ==========
        registerReadBinFileTool(server);          // dlqq_read_bin_file
        registerWriteBinFileTool(server);         // dlqq_write_bin_file

        // ========== BOM 控制工具 (20-21) ==========
        registerAddUnicodeBomTool(server);        // dlqq_add_unicode_bom
        registerRemoveUnicodeBomTool(server);     // dlqq_remove_unicode_bom

        // ========== 搜索工具 (25-28) ==========
        registerSearchFileTool(server);           // dlqq_search_file
        registerSearchDirTool(server);            // dlqq_search_dir
        registerSearchTextInDirTool(server);      // dlqq_search_text_in_dir
        registerSearchTextInFileTool(server);     // dlqq_search_text_in_file

        // ========== 对话框工具 (22-24) ==========
        registerDialogTextTool(server);           // dlqq_dialog_text
        registerDialogCheckTool(server);          // dlqq_dialog_check
        registerDialogFilePathTool(server);       // dlqq_dialog_file_path

        // ========== 外设设备工具（新架构：按设备地址/名称寻址，无实例端口号） ==========
        DeviceTools.registerTools(server);          // dlqq_uart_* / dlqq_usb_* / dlqq_wifi_* / dlqq_ble_*

        // ========== 系统信息工具 ==========
        registerGetMcpVersionTool(server);          // dlqq_get_mcp_version
        // 【源码屏蔽】dlqq_test_hot_reload：仅用于验证热加载，常规使用不需要
        // registerTestHotReloadTool(server);          // dlqq_test_hot_reload
        registerMcpHideToolsTool(server);           // dlqq_mcp_hide_tools
        registerMcpShowToolsTool(server);           // dlqq_mcp_show_tools
        registerMcpRefreshToolsTool(server);        // dlqq_mcp_refresh_tools
        registerMcpResetToolsTool(server);          // dlqq_mcp_reset_tools
        registerMcpRunToolsTool(server);            // dlqq_mcp_run_tools
        registerWorkspaceSetTool(server);           // dlqq_workspace_set
        registerWorkspaceGetTool(server);           // dlqq_workspace_get
        registerGetCwdPathTool(server);             // dlqq_get_cwd_path
        registerWorkspaceResetTool(server);         // dlqq_workspace_reset
        registerWorkspacePermissionTool(server);    // dlqq_workspace_permission_set
        registerCheckBluetoothTool(server);          // dlqq_check_bluetooth_open
        registerGetToolsEnvTool(server);             // dlqq_get_tools_env

        // ========== MCP数据管理工具 ==========
        registerClearMcpDataTool(server);           // dlqq_clear_mcp_data
        registerClearMcpCacheTool(server);          // dlqq_clear_mcp_cache
        registerClearMcpLogTool(server);            // dlqq_clear_mcp_log
        registerResetMcpPropertiesTool(server);       // dlqq_reset_mcp_properties
        registerGetMcpPropertiesTool(server);       // dlqq_get_mcp_properties
        registerSetMcpPropertiesTool(server);       // dlqq_set_mcp_properties

        // ========== Office文件工具 ==========
        registerReadOfficeFileTool(server);         // dlqq_read_office_file
        registerConvertOfficeFileTool(server);      // dlqq_convert_office_file

        // ========== 压缩/解压工具 ==========
        registerZipTool(server);                    // dlqq_zip
        registerUnzipTool(server);                  // dlqq_unzip

        // ========== 计算器工具 ==========
        CalculatorTools.registerTools(server);      // dlqq_calculator_math, dlqq_calculator_programmer, dlqq_calculator_function

        // ========== 命令执行工具 ==========
        RunCommandTools.registerTools(server);      // dlqq_run_command


        // ========== 项目构建和芯片操作工具 ==========
        // 【源码屏蔽】根据《MCP 工具权限架构》：项目构建/芯片操作/脚本路径设置类工具不暴露给 AI
        // registerBuildProjectTool(server);           // dlqq_build_project
        // registerRebuildProjectTool(server);         // dlqq_rebuild_project
        // registerChipEraseTool(server);              // dlqq_chip_erase
        // registerChipDownloadTool(server);           // dlqq_chip_download

        // ========== 组合脚本执行工具 ==========
        // registerEraseDownloadTool(server);          // dlqq_erase_download
        // registerBuildEraseDownloadTool(server);   // dlqq_build_erase_download
        // registerRebuildEraseDownloadTool(server); // dlqq_rebuild_erase_download

        // ========== 运行调试工具 ==========
        // registerRunProjectTool(server);             // dlqq_run_project
        // registerDebugProjectTool(server);           // dlqq_debug_project
        // registerBuildRunProjectTool(server);        // dlqq_build_run_project
        // registerBuildDebugProjectTool(server);      // dlqq_build_debug_project
        // registerRebuildRunProjectTool(server);      // dlqq_rebuild_run_project
        // registerRebuildDebugProjectTool(server);    // dlqq_rebuild_debug_project

        // ========== 脚本路径设置工具 ==========
        // 【源码屏蔽】全部脚本路径设置工具：AI 不得通过设置脚本来绕过人工审批
        // registerSetBuildScriptTool(server);         // dlqq_set_build_script
        // registerSetRebuildScriptTool(server);       // dlqq_set_rebuild_script
        // registerSetChipEraseScriptTool(server);     // dlqq_set_chip_erase_script
        // registerSetChipDownloadScriptTool(server);  // dlqq_set_chip_download_script
        // registerSetEraseDownloadScriptTool(server); // dlqq_set_erase_download_script
        // registerSetBuildEraseDownloadScriptTool(server);  // dlqq_set_build_erase_download_script
        // registerSetRebuildEraseDownloadScriptTool(server); // dlqq_set_rebuild_erase_download_script
        // registerSetRunScriptTool(server);           // dlqq_set_run_script
        // registerSetDebugScriptTool(server);         // dlqq_set_debug_script
        // registerSetBuildRunScriptTool(server);      // dlqq_set_build_run_script
        // registerSetBuildDebugScriptTool(server);    // dlqq_set_build_debug_script
        // registerSetRebuildRunScriptTool(server);    // dlqq_set_rebuild_run_script
        // registerSetRebuildDebugScriptTool(server);  // dlqq_set_rebuild_debug_script


        // ========== 鼠标工具 ==========
        MouseTools.registerTools(server);           // dlqq_mouse_get_position 等7个

        // ========== 键盘工具 ==========
        KeyboardTools.registerTools(server);        // dlqq_keyboard_type 等6个

        // ========== 时间工具 ==========
        TimeTools.registerTools(server);            // dlqq_get_current_time

        // ========== 屏幕截图工具 ==========
        ScreenTools.registerTools(server);          // dlqq_capture_screen

        // ========== 图片工具 ==========
        ImageTools.registerTools(server);           // dlqq_convert_image

        // ========== 工具列表查询工具 ==========
        registerGetToolListTool(server);            // dlqq_get_tool_list
    }

    // ==================== 文件操作工具 ====================

    /**
     * 1. dlqq_read_file - 读取文件（支持行范围）
     */
    private void registerReadFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径（支持绝对路径和相对路径）");
        properties.put("file_path", pathProp);

        Map<String, Object> startProp = new HashMap<>();
        startProp.put("type", Arrays.asList("integer", "string"));
        startProp.put("description", "起始行数（1-based，默认为1）");
        properties.put("start_line", startProp);

        Map<String, Object> endProp = new HashMap<>();
        endProp.put("type", Arrays.asList("integer", "string"));
        endProp.put("description", "结束行数（1-based，包含，-1表示到文件末尾）");
        properties.put("end_line", endProp);

        Map<String, Object> linesProp = new HashMap<>();
        linesProp.put("type", Arrays.asList("integer", "string"));
        linesProp.put("description", "读取行数（与end_line互斥，end_line优先）");
        properties.put("read_lines", linesProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        Map<String, Object> rawProp = new HashMap<>();
        rawProp.put("type", "boolean");
        rawProp.put("description", "原文模式（可选，默认 false）：true 时返回不带行号的纯文本，"
            + "可直接整段粘贴给 dlqq_replace_lines_in_file 的 expected_text");
        properties.put("raw", rawProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path"));

        server.addTool("dlqq_read_file",
            "读取指定文件的指定行范围的内容。支持通过start_line和end_line指定范围，或通过start_line和read_lines指定起始行和读取行数。支持指定字符集编码（默认UTF-8）。"
            + "raw=true 时返回无行号原文，便于直接作为 expected_text。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                Integer startLine = parseInteger(arguments.get("start_line"), 1);
                Integer endLine = parseInteger(arguments.get("end_line"), -1);
                Integer readLines = parseInteger(arguments.get("read_lines"), 0);
                String charset = (String) arguments.get("charset");
                boolean raw = parseBoolean(arguments.get("raw"), false);

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }

                try {
                    String content = FileTools.readFile(filePath, startLine, endLine, readLines, charset);
                    if (raw) {
                        StringBuilder sb = new StringBuilder();
                        String[] lines = content.split("\n", -1);
                        for (int i = 0; i < lines.length; i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            sb.append(lines[i].replaceFirst("^\\s*\\d+\\s*\\|\\s?", ""));
                        }
                        content = sb.toString();
                    }
                    return textResult(content);
                } catch (IOException e) {
                    return errorResult("读取文件失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 2. dlqq_create_file - 创建文件
     */
    private void registerCreateFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径（支持绝对路径和相对路径）");
        properties.put("file_path", pathProp);

        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "文件内容（为空则创建空文件）");
        properties.put("content", contentProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path"));

        server.addTool("dlqq_create_file",
            "创建新文件，可指定文件内容。如果文件已存在，会覆盖原有内容。自动创建父目录。支持指定字符集编码（默认UTF-8）。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                String content = (String) arguments.get("content");
                String charset = (String) arguments.get("charset");

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }

                try {
                    String report = FileTools.createFile(filePath, content, charset);
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("创建文件失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 2.5 dlqq_overwrite_file - 覆盖文件
     */
    private void registerOverwriteFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径（支持绝对路径和相对路径）");
        properties.put("file_path", pathProp);

        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "文件内容（会完全覆盖原文件内容）");
        properties.put("content", contentProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path"));

        server.addTool("dlqq_overwrite_file",
            "覆盖指定文件的内容，不管文件是否存在都会直接写入。自动创建父目录。支持指定字符集编码（默认UTF-8）。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                String content = (String) arguments.get("content");
                String charset = (String) arguments.get("charset");

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }

                try {
                    String report = FileTools.overwriteFile(filePath, content, charset);
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("覆盖文件失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 2.5b dlqq_clear_file - 清空文件内容
     */
    private void registerClearFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径（支持绝对路径和相对路径）");
        properties.put("file_path", pathProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path"));

        server.addTool("dlqq_clear_file",
            "清空文件内容（保留文件本身）。将文件内容清空为空白，但保留文件。如需删除文件本身，请使用 dlqq_delete_file。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                String charset = (String) arguments.get("charset");

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }

                try {
                    String report = FileTools.clearFile(filePath, charset);
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("清空文件失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 2.6 dlqq_append_file - 追加文件内容
     */
    private void registerAppendFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径（支持绝对路径和相对路径）");
        properties.put("file_path", pathProp);

        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "要追加的内容");
        properties.put("content", contentProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path", "content"));

        server.addTool("dlqq_append_file",
            "在文件末尾追加内容。如果文件不存在会自动创建。自动创建父目录。支持指定字符集编码（默认UTF-8）。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                String content = (String) arguments.get("content");
                String charset = (String) arguments.get("charset");

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }
                if (content == null || content.isEmpty()) {
                    return errorResult("追加内容不能为空");
                }

                try {
                    String report = FileTools.appendFile(filePath, content, charset);
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("追加内容失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 3. dlqq_remove_lines_in_file - 删除指定行
     */
    private void registerRemoveLinesTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径");
        properties.put("file_path", pathProp);

        Map<String, Object> startProp = new HashMap<>();
        startProp.put("type", Arrays.asList("integer", "string"));
        startProp.put("description", "起始行数（1-based，包含）");
        properties.put("start_line", startProp);

        Map<String, Object> endProp = new HashMap<>();
        endProp.put("type", Arrays.asList("integer", "string"));
        endProp.put("description", "结束行数（1-based，包含，-1表示到文件末尾）");
        properties.put("end_line", endProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path", "start_line", "end_line"));

        server.addTool("dlqq_remove_lines_in_file",
            "删除文件中指定行范围的内容（包含起始行和结束行）。支持指定字符集编码（默认UTF-8）。注意：end_line为-1表示到文件末尾，行号超出范围会自动修正。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                Integer startLine = parseInteger(arguments.get("start_line"), null);
                Integer endLine = parseInteger(arguments.get("end_line"), null);
                String charset = (String) arguments.get("charset");

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }
                if (startLine == null || endLine == null) {
                    return errorResult("起始行和结束行必须指定");
                }

                try {
                    String report = FileTools.removeLinesInFile(filePath, startLine, endLine, charset);
                    // 检查是否被拒绝修改
                    if (report.startsWith("【拒绝修改】")) {
                        return errorResult(report + "\n\n【读取示例】\n请使用以下命令读取目标内容：\n" +
                            "{\"file_path\": \"" + filePath + "\", \"start_line\": " + startLine + ", \"end_line\": " + endLine + "}");
                    }
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("操作失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 3.5 dlqq_replace_lines_in_file - 替换指定行内容
     */
    private void registerReplaceLinesTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径");
        properties.put("file_path", pathProp);

        Map<String, Object> startProp = new HashMap<>();
        startProp.put("type", Arrays.asList("integer", "string"));
        startProp.put("description", "起始行数（1-based，包含）");
        properties.put("start_line", startProp);

        Map<String, Object> endProp = new HashMap<>();
        endProp.put("type", Arrays.asList("integer", "string"));
        endProp.put("description", "结束行数（1-based，包含，-1表示到文件末尾）");
        properties.put("end_line", endProp);

        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "替换的新内容（支持多行）");
        properties.put("content", contentProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        Map<String, Object> expectedProp = new HashMap<>();
        expectedProp.put("type", "string");
        expectedProp.put("description", "校验用（可选，强烈建议）：目标行当前应有的内容。"
            + "与实际内容不一致时拒绝修改，并在错误里回显实际内容，可避免按行号改错位置");
        properties.put("expected_text", expectedProp);

        Map<String, Object> dryRunProp = new HashMap<>();
        dryRunProp.put("type", "boolean");
        dryRunProp.put("description", "预览模式（可选，默认 false）：true 时只返回替换前后的对比，不写入文件");
        properties.put("dry_run", dryRunProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path", "start_line", "end_line", "content"));

        server.addTool("dlqq_replace_lines_in_file",
            "将文件中指定行范围的内容替换为新内容（包含起始行和结束行）。支持多行替换。支持指定字符集编码（默认UTF-8）。注意：end_line为-1表示到文件末尾，行号超出范围会自动修正。"
            + "建议同时传 expected_text 做保护，dry_run=true 可先预览。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                Integer startLine = parseInteger(arguments.get("start_line"), null);
                Integer endLine = parseInteger(arguments.get("end_line"), null);
                String content = (String) arguments.get("content");
                String charset = (String) arguments.get("charset");

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }
                if (startLine == null || endLine == null) {
                    return errorResult("起始行和结束行必须指定");
                }
                if (content == null) {
                    return errorResult("错误：content 参数为空。可能原因：\n" +
                        "1. JSON 字符串中的换行符 \\n 被当作实际换行，导致解析失败\n" +
                        "2. content 内容过长或包含特殊字符\n" +
                        "建议：将 \\n 替换为 \\\n 或使用 dlqq_insert_text_in_file 工具逐行插入");
                }

                boolean dryRun = parseBoolean(arguments.get("dry_run"), false);
                // expected_text 校验：确认目标行当前内容与预期一致，避免按行号改错位置
                Object expectedObj = arguments.get("expected_text");
                if (expectedObj != null) {
                    // 容忍从 dlqq_read_file 输出里直接复制：去掉 "123 | " 行号前缀
                    String rawExpected = String.valueOf(expectedObj).replace("\r\n", "\n");
                    StringBuilder expectedBuilder = new StringBuilder();
                    String[] expectedLines = rawExpected.split("\n", -1);
                    for (int i = 0; i < expectedLines.length; i++) {
                        if (i > 0) {
                            expectedBuilder.append('\n');
                        }
                        expectedBuilder.append(expectedLines[i].replaceFirst("^\\s*\\d+\\s*\\|\\s?", ""));
                    }
                    String expected = expectedBuilder.toString();
                    String actual = "";
                    try {
                        Charset cs = Charset.forName(
                                charset == null || charset.isEmpty() ? "UTF-8" : charset);
                        List<String> fileLines = Files.readAllLines(Workspace.resolve(filePath), cs);
                        int from = Math.max(1, startLine);
                        int to = endLine == -1 ? fileLines.size() : Math.min(fileLines.size(), endLine);
                        actual = from > to ? "" : String.join("\n", fileLines.subList(from - 1, to));
                    } catch (Exception ignored) {
                        // 读取失败时不阻断，交由后续逻辑处理
                    }
                    if (!expected.equals(actual)) {
                        return errorResult("【拒绝修改】第 " + startLine + "-" + endLine
                                + " 行与 expected_text 不一致，未做任何改动。\n\n实际内容:\n" + actual);
                    }
                }

                // dry_run：只预览替换结果，不写入文件
                if (dryRun) {
                    try {
                        Charset cs = Charset.forName(
                                charset == null || charset.isEmpty() ? "UTF-8" : charset);
                        List<String> all = Files.readAllLines(Workspace.resolve(filePath), cs);
                        int from = Math.max(1, startLine);
                        int to = endLine == -1 ? all.size() : Math.min(all.size(), endLine);
                        String[] newLines = content.split("\n", -1);
                        StringBuilder sb = new StringBuilder();
                        sb.append("【预览 dry_run】未写入任何内容\n");
                        sb.append("===================\n\n");
                        sb.append("文件: ").append(filePath).append('\n');
                        sb.append("替换 ").append(from).append('-').append(to)
                          .append(" 行：原 ").append(Math.max(0, to - from + 1))
                          .append(" 行 → 新 ").append(newLines.length).append(" 行\n\n");
                        int ctxFrom = Math.max(1, from - 3);
                        int ctxTo = Math.min(all.size(), to + 3);
                        for (int i = ctxFrom; i < from; i++) {
                            sb.append("    ").append(i).append(" | ").append(all.get(i - 1)).append('\n');
                        }
                        for (int i = from; i <= to && i <= all.size(); i++) {
                            sb.append("-   ").append(i).append(" | ").append(all.get(i - 1)).append('\n');
                        }
                        for (int i = 0; i < newLines.length; i++) {
                            sb.append("+   ").append(from + i).append(" | ").append(newLines[i]).append('\n');
                        }
                        for (int i = to + 1; i <= ctxTo; i++) {
                            sb.append("    ").append(i).append(" | ").append(all.get(i - 1)).append('\n');
                        }
                        return textResult(sb.toString());
                    } catch (Exception e) {
                        return errorResult("预览失败: " + e.getMessage());
                    }
                }

                try {
                    String report = FileTools.replaceLinesInFile(filePath, startLine, endLine, content, charset);
                    // 检查是否被拒绝修改
                    if (report.startsWith("【拒绝修改】")) {
                        return errorResult(report + "\n\n【读取示例】\n请使用以下命令读取目标内容：\n" +
                            "{\"file_path\": \"" + filePath + "\", \"start_line\": " + startLine + ", \"end_line\": " + endLine + "}");
                    }
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("操作失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 4. dlqq_insert_text_in_file - 在指定行插入内容
     */
    private void registerInsertTextTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径");
        properties.put("file_path", pathProp);

        Map<String, Object> lineProp = new HashMap<>();
        lineProp.put("type", Arrays.asList("integer", "string"));
        lineProp.put("description", "插入位置（1-based，插入到该行之前）");
        properties.put("line", lineProp);

        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "要插入的内容（支持多行）");
        properties.put("content", contentProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path", "line", "content"));

        server.addTool("dlqq_insert_text_in_file",
            "在文件的指定行之前插入内容。支持插入多行文本。支持指定字符集编码（默认UTF-8）。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                Integer line = parseInteger(arguments.get("line"), null);
                String content = (String) arguments.get("content");
                String charset = (String) arguments.get("charset");

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }
                if (line == null) {
                    return errorResult("插入行必须指定");
                }
                if (content == null) {
                    content = "";
                }

                try {
                    String report = FileTools.insertTextInFile(filePath, line, content, charset);
                    // 检查是否被拒绝修改
                    if (report.startsWith("【拒绝修改】")) {
                        // 计算建议读取的范围（插入点前后5行）
                        int readStart = Math.max(1, line - 5);
                        int readEnd = line + 5;
                        return errorResult(report + "\n\n【读取示例】\n请使用以下命令读取插入位置附近的上下文：\n" +
                            "{\"file_path\": \"" + filePath + "\", \"start_line\": " + readStart + ", \"end_line\": " + readEnd + "}");
                    }
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("操作失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 5. dlqq_replace_text_in_file - 替换文本（支持正则）
     */
    private void registerReplaceTextTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径");
        properties.put("file_path", pathProp);

        Map<String, Object> searchProp = new HashMap<>();
        searchProp.put("type", "string");
        searchProp.put("description", "要搜索的文本或正则表达式");
        properties.put("search_text", searchProp);

        Map<String, Object> replaceProp = new HashMap<>();
        replaceProp.put("type", "string");
        replaceProp.put("description", "替换后的文本");
        properties.put("replace_text", replaceProp);

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "模式：'text'（普通文本）或 'regex'（正则表达式）");
        modeProp.put("enum", Arrays.asList("text", "regex"));
        properties.put("mode", modeProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        Map<String, Object> startLineProp = new HashMap<>();
        startLineProp.put("type", "integer");
        startLineProp.put("description", "起始行号（1-based，可选）。传入时可只读取指定行范围进行验证");
        properties.put("start_line", startLineProp);

        Map<String, Object> endLineProp = new HashMap<>();
        endLineProp.put("type", "integer");
        endLineProp.put("description", "结束行号（1-based，可选，-1表示到末尾）。传入时可只读取指定行范围进行验证");
        properties.put("end_line", endLineProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path", "search_text", "replace_text"));

        server.addTool("dlqq_replace_text_in_file",
            "替换文件中的指定文本。支持普通文本模式和正则表达式模式。支持指定字符集编码（默认UTF-8）。" +
            "传入start_line和end_line参数时，只需读取指定行范围即可修改；未传入时需读取全部文件内容。" +
            "返回结果仅展示修改部分和上下5行代码的对比。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                String searchText = (String) arguments.get("search_text");
                String replaceText = (String) arguments.get("replace_text");
                String mode = (String) arguments.getOrDefault("mode", "text");
                String charset = (String) arguments.get("charset");
                Integer startLine = parseInteger(arguments.get("start_line"), null);
                Integer endLine = parseInteger(arguments.get("end_line"), null);

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }
                if (searchText == null) {
                    return errorResult("搜索文本不能为空");
                }
                if (replaceText == null) {
                    replaceText = "";
                }

                try {
                    boolean useRegex = "regex".equalsIgnoreCase(mode);
                    String report = FileTools.replaceTextInFile(filePath, searchText, replaceText, useRegex, charset, startLine, endLine);
                    // 检查是否被拒绝修改或未找到匹配
                    if (report.startsWith("【拒绝修改】")) {
                        return errorResult(report + "\n\n【读取示例】\n请使用以下命令读取文件全部内容：\n" +
                            "{\"file_path\": \"" + filePath + "\", \"start_line\": 1, \"end_line\": -1}");
                    }
                    if (report.startsWith("【未替换】")) {
                        return errorResult(report);
                    }
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("操作失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 5.5 dlqq_remove_text_in_file - 删除匹配的文本
     */
    private void registerRemoveTextInFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径");
        properties.put("file_path", pathProp);

        Map<String, Object> searchProp = new HashMap<>();
        searchProp.put("type", "string");
        searchProp.put("description", "要搜索并删除的文本或正则表达式");
        properties.put("search_text", searchProp);

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "模式：'text'（普通文本）或 'regex'（正则表达式）");
        modeProp.put("enum", Arrays.asList("text", "regex"));
        properties.put("mode", modeProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        Map<String, Object> startLineProp = new HashMap<>();
        startLineProp.put("type", "integer");
        startLineProp.put("description", "起始行号（1-based，可选）。传入时可只读取指定行范围进行验证");
        properties.put("start_line", startLineProp);

        Map<String, Object> endLineProp = new HashMap<>();
        endLineProp.put("type", "integer");
        endLineProp.put("description", "结束行号（1-based，可选，-1表示到末尾）。传入时可只读取指定行范围进行验证");
        properties.put("end_line", endLineProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path", "search_text"));

        server.addTool("dlqq_remove_text_in_file",
            "删除文件中匹配的文本。相当于将匹配文本替换为空字符串。支持普通文本模式和正则表达式模式。" +
            "传入start_line和end_line参数时，只需读取指定行范围即可修改；未传入时需读取全部文件内容。" +
            "返回结果仅展示修改部分和上下5行代码的对比。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                String searchText = (String) arguments.get("search_text");
                String mode = (String) arguments.getOrDefault("mode", "text");
                String charset = (String) arguments.get("charset");
                Integer startLine = parseInteger(arguments.get("start_line"), null);
                Integer endLine = parseInteger(arguments.get("end_line"), null);

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }
                if (searchText == null || searchText.isEmpty()) {
                    return errorResult("搜索文本不能为空");
                }

                try {
                    boolean useRegex = "regex".equalsIgnoreCase(mode);
                    String report = FileTools.removeTextInFile(filePath, searchText, useRegex, charset, startLine, endLine);
                    // 检查是否被拒绝修改或未找到匹配
                    if (report.startsWith("【拒绝修改】")) {
                        return errorResult(report + "\n\n【读取示例】\n请使用以下命令读取文件全部内容：\n" +
                            "{\"file_path\": \"" + filePath + "\", \"start_line\": 1, \"end_line\": -1}");
                    }
                    if (report.startsWith("【未替换】")) {
                        return errorResult(report);
                    }
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("操作失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 5.6 dlqq_replace_text_in_dir - 在目录下批量替换文本
     */
    private void registerReplaceTextInDirTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> dirProp = new HashMap<>();
        dirProp.put("type", "string");
        dirProp.put("description", "目录路径，将在该目录及其子目录中查找文本文件进行替换");
        properties.put("dir_path", dirProp);

        Map<String, Object> searchProp = new HashMap<>();
        searchProp.put("type", "string");
        searchProp.put("description", "要搜索的文本或正则表达式");
        properties.put("search_text", searchProp);

        Map<String, Object> replaceProp = new HashMap<>();
        replaceProp.put("type", "string");
        replaceProp.put("description", "替换后的文本（为空则删除匹配内容）");
        properties.put("replace_text", replaceProp);

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "模式：'text'（普通文本）或 'regex'（正则表达式）");
        modeProp.put("enum", Arrays.asList("text", "regex"));
        properties.put("mode", modeProp);

        Map<String, Object> charsetProp = new HashMap<>();
        charsetProp.put("type", "string");
        charsetProp.put("description", "字符集编码（如 UTF-8、GBK、ISO-8859-1 等，默认为 UTF-8）");
        properties.put("charset", charsetProp);

        Map<String, Object> fileExtProp = new HashMap<>();
        fileExtProp.put("type", "array");
        fileExtProp.put("description", "文件扩展名列表（如 [\"java\", \"txt\", \"md\"]），为空则使用默认文本文件类型");
        properties.put("file_extensions", fileExtProp);

        Map<String, Object> maxFilesProp = new HashMap<>();
        maxFilesProp.put("type", "integer");
        maxFilesProp.put("description", "最大处理文件数（默认100，防止处理过多文件）");
        properties.put("max_files", maxFilesProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("dir_path", "search_text", "replace_text"));

        server.addTool("dlqq_replace_text_in_dir",
            "在指定目录及其子目录下的所有文本文件中批量替换指定文本。支持普通文本模式和正则表达式模式。" +
            "支持指定文件类型过滤，支持设置最大处理文件数限制。返回详细的替换报告，包含每个文件的替换结果汇总。",
            inputSchema,
            arguments -> {
                String dirPath = (String) arguments.get("dir_path");
                String searchText = (String) arguments.get("search_text");
                String replaceText = (String) arguments.get("replace_text");
                String mode = (String) arguments.getOrDefault("mode", "text");
                String charset = (String) arguments.get("charset");
                Object fileExtensionsObj = arguments.get("file_extensions");
                Integer maxFiles = parseInteger(arguments.get("max_files"), null);

                if (dirPath == null || dirPath.trim().isEmpty()) {
                    return errorResult("目录路径不能为空");
                }
                if (searchText == null || searchText.isEmpty()) {
                    return errorResult("搜索文本不能为空");
                }
                if (replaceText == null) {
                    replaceText = "";
                }

                // 处理文件扩展名列表
                List<String> fileExtensions = null;
                if (fileExtensionsObj instanceof List) {
                    fileExtensions = new ArrayList<>();
                    for (Object ext : (List<?>) fileExtensionsObj) {
                        if (ext instanceof String) {
                            fileExtensions.add((String) ext);
                        }
                    }
                }

                try {
                    boolean useRegex = "regex".equalsIgnoreCase(mode);
                    String report = FileTools.replaceTextInDir(dirPath, searchText, replaceText, useRegex, charset, fileExtensions, maxFiles);
                    // 检查是否被取消
                    if (report.startsWith("【操作已取消】")) {
                        return errorResult(report);
                    }
                    return textResult(report);
                } catch (IOException e) {
                    return errorResult("操作失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 6. dlqq_delete_file - 删除文件（带确认对话框）
     */
    private void registerDeleteFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "要删除的文件路径");
        properties.put("file_path", pathProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path"));

        server.addTool("dlqq_delete_file",
            "删除指定文件。敏感操作，会弹出确认对话框要求用户手动确认。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }

                try {
                    boolean deleted = FileTools.deleteFile(filePath);
                    if (deleted) {
                        return textResult("文件已删除: " + filePath);
                    } else {
                        return textResult("用户取消了删除操作");
                    }
                } catch (IOException e) {
                    return errorResult("删除失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 7. dlqq_copy_file - 复制文件
     */
    private void registerCopyFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sourceProp = new HashMap<>();
        sourceProp.put("type", "string");
        sourceProp.put("description", "源文件路径");
        properties.put("source_path", sourceProp);

        Map<String, Object> targetProp = new HashMap<>();
        targetProp.put("type", "string");
        targetProp.put("description", "目标文件路径");
        properties.put("target_path", targetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("source_path", "target_path"));

        server.addTool("dlqq_copy_file",
            "复制文件到指定位置。如果目标文件已存在，会覆盖。自动创建目标目录。",
            inputSchema,
            arguments -> {
                String sourcePath = (String) arguments.get("source_path");
                String targetPath = (String) arguments.get("target_path");

                if (sourcePath == null || sourcePath.trim().isEmpty()) {
                    return errorResult("源文件路径不能为空");
                }
                if (targetPath == null || targetPath.trim().isEmpty()) {
                    return errorResult("目标文件路径不能为空");
                }

                try {
                    FileTools.copyFile(sourcePath, targetPath);
                    return textResult("文件复制成功");
                } catch (IOException e) {
                    return errorResult("复制失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 8. dlqq_move_file - 移动文件
     */
    private void registerMoveFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sourceProp = new HashMap<>();
        sourceProp.put("type", "string");
        sourceProp.put("description", "源文件路径");
        properties.put("source_path", sourceProp);

        Map<String, Object> targetProp = new HashMap<>();
        targetProp.put("type", "string");
        targetProp.put("description", "目标文件路径");
        properties.put("target_path", targetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("source_path", "target_path"));

        server.addTool("dlqq_move_file",
            "移动文件到指定位置。如果目标文件已存在，会覆盖。自动创建目标目录。",
            inputSchema,
            arguments -> {
                String sourcePath = (String) arguments.get("source_path");
                String targetPath = (String) arguments.get("target_path");

                if (sourcePath == null || sourcePath.trim().isEmpty()) {
                    return errorResult("源文件路径不能为空");
                }
                if (targetPath == null || targetPath.trim().isEmpty()) {
                    return errorResult("目标文件路径不能为空");
                }

                try {
                    FileTools.moveFile(sourcePath, targetPath);
                    return textResult("文件移动成功");
                } catch (IOException e) {
                    return errorResult("移动失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 8.5 dlqq_rename_file - 重命名文件
     */
    private void registerRenameFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sourceProp = new HashMap<>();
        sourceProp.put("type", "string");
        sourceProp.put("description", "源文件路径");
        properties.put("source_path", sourceProp);

        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "新文件名（仅文件名，不包含路径）");
        properties.put("new_name", nameProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("source_path", "new_name"));

        server.addTool("dlqq_rename_file",
            "重命名文件，在同一目录下修改文件名。",
            inputSchema,
            arguments -> {
                String sourcePath = (String) arguments.get("source_path");
                String newName = (String) arguments.get("new_name");

                if (sourcePath == null || sourcePath.trim().isEmpty()) {
                    return errorResult("源文件路径不能为空");
                }
                if (newName == null || newName.trim().isEmpty()) {
                    return errorResult("新文件名不能为空");
                }

                try {
                    FileTools.renameFile(sourcePath, newName);
                    return textResult("文件重命名成功");
                } catch (IOException e) {
                    return errorResult("重命名失败: " + e.getMessage());
                }
            }
        );
    }

    // ==================== 目录操作工具 ====================

    /**
     * 9. dlqq_mdir - 创建文件夹
     */
    private void registerCreateDirTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件夹路径（只创建最后一级，父目录必须已存在）");
        properties.put("dir_path", pathProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("dir_path"));

        server.addTool("dlqq_mdir",
            "创建指定文件夹。如果父目录不存在会报错，请使用 dlqq_mdirs 创建多级目录。",
            inputSchema,
            arguments -> {
                String dirPath = (String) arguments.get("dir_path");

                if (dirPath == null || dirPath.trim().isEmpty()) {
                    return errorResult("文件夹路径不能为空");
                }

                try {
                    FileTools.createDirectory(dirPath);
                    return textResult("文件夹创建成功: " + dirPath);
                } catch (IOException e) {
                    return errorResult("创建文件夹失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 10. dlqq_mdirs - 创建文件夹（包括父文件夹）
     */
    private void registerCreateDirsTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件夹路径（包括所有父文件夹一起创建）");
        properties.put("dir_path", pathProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("dir_path"));

        server.addTool("dlqq_mdirs",
            "创建指定文件夹，如果不存在会自动创建所有父文件夹。",
            inputSchema,
            arguments -> {
                String dirPath = (String) arguments.get("dir_path");

                if (dirPath == null || dirPath.trim().isEmpty()) {
                    return errorResult("文件夹路径不能为空");
                }

                try {
                    FileTools.createDirectories(dirPath);
                    return textResult("文件夹创建成功: " + dirPath);
                } catch (IOException e) {
                    return errorResult("创建文件夹失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 11. dlqq_delete_dir - 删除文件夹（带确认对话框）
     */
    private void registerDeleteDirTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "要删除的文件夹路径（会递归删除所有内容）");
        properties.put("dir_path", pathProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("dir_path"));

        server.addTool("dlqq_delete_dir",
            "删除指定文件夹及其所有内容。敏感操作，会弹出确认对话框显示目录包含的文件和子目录数量。",
            inputSchema,
            arguments -> {
                String dirPath = (String) arguments.get("dir_path");

                if (dirPath == null || dirPath.trim().isEmpty()) {
                    return errorResult("文件夹路径不能为空");
                }

                try {
                    boolean deleted = FileTools.deleteDirectory(dirPath);
                    if (deleted) {
                        return textResult("文件夹已删除: " + dirPath);
                    } else {
                        return textResult("用户取消了删除操作");
                    }
                } catch (IOException e) {
                    return errorResult("删除失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 12. dlqq_copy_dir - 复制文件夹
     */
    private void registerCopyDirTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sourceProp = new HashMap<>();
        sourceProp.put("type", "string");
        sourceProp.put("description", "源文件夹路径");
        properties.put("source_path", sourceProp);

        Map<String, Object> targetProp = new HashMap<>();
        targetProp.put("type", "string");
        targetProp.put("description", "目标文件夹路径");
        properties.put("target_path", targetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("source_path", "target_path"));

        server.addTool("dlqq_copy_dir",
            "复制文件夹到指定位置，递归复制所有子目录和文件。",
            inputSchema,
            arguments -> {
                String sourcePath = (String) arguments.get("source_path");
                String targetPath = (String) arguments.get("target_path");

                if (sourcePath == null || sourcePath.trim().isEmpty()) {
                    return errorResult("源文件夹路径不能为空");
                }
                if (targetPath == null || targetPath.trim().isEmpty()) {
                    return errorResult("目标文件夹路径不能为空");
                }

                try {
                    FileTools.copyDirectory(sourcePath, targetPath);
                    return textResult("文件夹复制成功");
                } catch (IOException e) {
                    return errorResult("复制失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 13. dlqq_move_dir - 移动文件夹
     */
    private void registerMoveDirTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sourceProp = new HashMap<>();
        sourceProp.put("type", "string");
        sourceProp.put("description", "源文件夹路径");
        properties.put("source_path", sourceProp);

        Map<String, Object> targetProp = new HashMap<>();
        targetProp.put("type", "string");
        targetProp.put("description", "目标文件夹路径");
        properties.put("target_path", targetProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("source_path", "target_path"));

        server.addTool("dlqq_move_dir",
            "移动文件夹到指定位置。如果目标文件夹已存在，会先删除再移动。",
            inputSchema,
            arguments -> {
                String sourcePath = (String) arguments.get("source_path");
                String targetPath = (String) arguments.get("target_path");

                if (sourcePath == null || sourcePath.trim().isEmpty()) {
                    return errorResult("源文件夹路径不能为空");
                }
                if (targetPath == null || targetPath.trim().isEmpty()) {
                    return errorResult("目标文件夹路径不能为空");
                }

                try {
                    FileTools.moveDirectory(sourcePath, targetPath);
                    return textResult("文件夹移动成功");
                } catch (IOException e) {
                    return errorResult("移动失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 13.5 dlqq_rename_dir - 重命名文件夹
     */
    private void registerRenameDirTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sourceProp = new HashMap<>();
        sourceProp.put("type", "string");
        sourceProp.put("description", "源文件夹路径");
        properties.put("source_path", sourceProp);

        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "新文件夹名（仅文件夹名，不包含路径）");
        properties.put("new_name", nameProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("source_path", "new_name"));

        server.addTool("dlqq_rename_dir",
            "重命名文件夹，在同一目录下修改文件夹名。",
            inputSchema,
            arguments -> {
                String sourcePath = (String) arguments.get("source_path");
                String newName = (String) arguments.get("new_name");

                if (sourcePath == null || sourcePath.trim().isEmpty()) {
                    return errorResult("源文件夹路径不能为空");
                }
                if (newName == null || newName.trim().isEmpty()) {
                    return errorResult("新文件夹名不能为空");
                }

                try {
                    FileTools.renameDirectory(sourcePath, newName);
                    return textResult("文件夹重命名成功");
                } catch (IOException e) {
                    return errorResult("重命名失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 17. dlqq_list_dir_tree - 列出目录树（递归）
     */
    private void registerListDirTreeTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "要列出的目录路径");
        properties.put("dir_path", pathProp);

        Map<String, Object> depthProp = new HashMap<>();
        depthProp.put("type", Arrays.asList("integer", "string"));
        depthProp.put("description", "最大递归深度（-1表示无限制，默认为-1）");
        properties.put("max_depth", depthProp);

        Map<String, Object> entriesProp = new HashMap<>();
        entriesProp.put("type", Arrays.asList("integer", "string"));
        entriesProp.put("description", "最大返回条目数（-1表示无限制，默认为-1）");
        properties.put("max_entries", entriesProp);

        Map<String, Object> hiddenProp = new HashMap<>();
        hiddenProp.put("type", "string");
        hiddenProp.put("description", "是否显示隐藏文件/目录（'true'或'false'，默认为'false'）");
        hiddenProp.put("enum", Arrays.asList("true", "false"));
        properties.put("show_hidden", hiddenProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("dir_path"));

        server.addTool("dlqq_list_dir_tree",
            "列出指定文件夹的完整目录结构，递归显示所有子目录和文件。支持指定递归深度和最大条目数限制。",
            inputSchema,
            arguments -> {
                String dirPath = (String) arguments.get("dir_path");
                Integer maxDepth = parseInteger(arguments.get("max_depth"), -1);
                Integer maxEntries = parseInteger(arguments.get("max_entries"), -1);
                boolean showHidden = parseBoolean(arguments.get("show_hidden"), false);

                if (dirPath == null || dirPath.trim().isEmpty()) {
                    return errorResult("目录路径不能为空");
                }

                try {
                    String result = FileTools.listDirectoryTree(dirPath, maxDepth, maxEntries, showHidden);
                    return textResult(result);
                } catch (IOException e) {
                    return errorResult("列出目录失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 18. dlqq_list_dir_files - 列出单层目录内容
     */
    private void registerListDirFilesTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "要列出的目录路径");
        properties.put("dir_path", pathProp);

        Map<String, Object> hiddenProp = new HashMap<>();
        hiddenProp.put("type", "string");
        hiddenProp.put("description", "是否显示隐藏文件/目录（'true'或'false'，默认为'false'）");
        hiddenProp.put("enum", Arrays.asList("true", "false"));
        properties.put("show_hidden", hiddenProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("dir_path"));

        server.addTool("dlqq_list_dir_files",
            "列出指定文件夹下的文件和文件夹，只列出一层，不递归进入子目录。",
            inputSchema,
            arguments -> {
                String dirPath = (String) arguments.get("dir_path");
                boolean showHidden = parseBoolean(arguments.get("show_hidden"), false);

                if (dirPath == null || dirPath.trim().isEmpty()) {
                    return errorResult("目录路径不能为空");
                }

                try {
                    String result = FileTools.listDirectoryFiles(dirPath, showHidden);
                    return textResult(result);
                } catch (IOException e) {
                    return errorResult("列出目录失败: " + e.getMessage());
                }
            }
        );
    }

    // ==================== 二进制文件操作工具 ====================

    /**
     * 14. dlqq_read_bin_file - 读取二进制文件为十六进制
     */
    private void registerReadBinFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "二进制文件路径");
        properties.put("file_path", pathProp);

        Map<String, Object> startProp = new HashMap<>();
        startProp.put("type", Arrays.asList("integer", "string"));
        startProp.put("description", "起始字节（0-based，默认为0）");
        properties.put("start_byte", startProp);

        Map<String, Object> endProp = new HashMap<>();
        endProp.put("type", Arrays.asList("integer", "string"));
        endProp.put("description", "结束字节（0-based，包含，-1表示到文件末尾）");
        properties.put("end_byte", endProp);

        Map<String, Object> bytesProp = new HashMap<>();
        bytesProp.put("type", Arrays.asList("integer", "string"));
        bytesProp.put("description", "读取字节数（与end_byte互斥，end_byte优先）");
        properties.put("read_bytes", bytesProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path"));

        server.addTool("dlqq_read_bin_file",
            "读取二进制文件并将字节转换为十六进制文本返回。支持通过start_byte和end_byte指定范围，或通过start_byte和read_bytes指定起始字节和读取字节数。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                Long startByte = parseLong(arguments.get("start_byte"), 0L);
                Long endByte = parseLong(arguments.get("end_byte"), -1L);
                Long readBytes = parseLong(arguments.get("read_bytes"), 0L);

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }

                try {
                    String hexContent = FileTools.readBinaryFileAsHex(filePath, startByte, endByte, readBytes);
                    return textResult(hexContent);
                } catch (IOException e) {
                    return errorResult("读取文件失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 15. dlqq_write_bin_file - 将十六进制写入二进制文件
     */
    private void registerWriteBinFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "目标文件路径");
        properties.put("file_path", pathProp);

        Map<String, Object> hexProp = new HashMap<>();
        hexProp.put("type", "string");
        hexProp.put("description", "十六进制文本（可包含空格、换行，会自动去除）");
        properties.put("hex_content", hexProp);

        Map<String, Object> appendProp = new HashMap<>();
        appendProp.put("type", "boolean");
        appendProp.put("description", "是否追加写入（默认false，即覆盖写入）");
        properties.put("append", appendProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path", "hex_content"));

        server.addTool("dlqq_write_bin_file",
            "将十六进制文本转换为二进制并写入文件。支持包含空格和换行的十六进制文本。支持覆盖或追加写入。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                String hexContent = (String) arguments.get("hex_content");
                Boolean append = parseBoolean(arguments.get("append"), false);

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }
                if (hexContent == null || hexContent.trim().isEmpty()) {
                    return errorResult("十六进制内容不能为空");
                }

                try {
                    FileTools.writeBinaryFileFromHex(filePath, hexContent, append);
                    String mode = append ? "追加" : "覆盖";
                    return textResult(mode + "写入二进制文件成功: " + filePath);
                } catch (IOException e) {
                    return errorResult("写入文件失败: " + e.getMessage());
                }
            }
        );
    }

    // ==================== 搜索工具 ====================

    /**
     * 16. dlqq_search_file - 搜索文件
     */
    private void registerSearchFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "搜索目录路径");
        properties.put("search_path", pathProp);

        Map<String, Object> patternProp = new HashMap<>();
        patternProp.put("type", "string");
        patternProp.put("description", "搜索模式（文件名或正则表达式）");
        properties.put("pattern", patternProp);

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "模式：'text'（部分匹配）或 'regex'（正则表达式）");
        modeProp.put("enum", Arrays.asList("text", "regex"));
        properties.put("mode", modeProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("search_path", "pattern"));

        server.addTool("dlqq_search_file",
            "在指定目录下查找文件。支持部分匹配（文件名包含指定文本）或正则表达式匹配。",
            inputSchema,
            arguments -> {
                String searchPath = (String) arguments.get("search_path");
                String pattern = (String) arguments.get("pattern");
                String mode = (String) arguments.getOrDefault("mode", "text");

                if (searchPath == null || searchPath.trim().isEmpty()) {
                    return errorResult("搜索路径不能为空");
                }
                if (pattern == null || pattern.trim().isEmpty()) {
                    return errorResult("搜索模式不能为空");
                }

                try {
                    boolean useRegex = "regex".equalsIgnoreCase(mode);
                    List<String> results = FileTools.searchFiles(searchPath, pattern, useRegex);

                    if (results.isEmpty()) {
                        return textResult("未找到匹配的文件");
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("找到 ").append(results.size()).append(" 个文件:\n");
                    for (int i = 0; i < results.size(); i++) {
                        sb.append(i + 1).append(". ").append(results.get(i)).append("\n");
                    }
                    return textResult(sb.toString());
                } catch (IOException e) {
                    return errorResult("搜索失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 17. dlqq_search_dir - 搜索文件夹
     */
    private void registerSearchDirTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "搜索目录路径");
        properties.put("search_path", pathProp);

        Map<String, Object> patternProp = new HashMap<>();
        patternProp.put("type", "string");
        patternProp.put("description", "搜索模式（文件夹名或正则表达式）");
        properties.put("pattern", patternProp);

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "模式：'text'（部分匹配）或 'regex'（正则表达式）");
        modeProp.put("enum", Arrays.asList("text", "regex"));
        properties.put("mode", modeProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("search_path", "pattern"));

        server.addTool("dlqq_search_dir",
            "在指定目录下查找文件夹。支持部分匹配（文件夹名包含指定文本）或正则表达式匹配。",
            inputSchema,
            arguments -> {
                String searchPath = (String) arguments.get("search_path");
                String pattern = (String) arguments.get("pattern");
                String mode = (String) arguments.getOrDefault("mode", "text");

                if (searchPath == null || searchPath.trim().isEmpty()) {
                    return errorResult("搜索路径不能为空");
                }
                if (pattern == null || pattern.trim().isEmpty()) {
                    return errorResult("搜索模式不能为空");
                }

                try {
                    boolean useRegex = "regex".equalsIgnoreCase(mode);
                    List<String> results = FileTools.searchDirectories(searchPath, pattern, useRegex);

                    if (results.isEmpty()) {
                        return textResult("未找到匹配的文件夹");
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("找到 ").append(results.size()).append(" 个文件夹:\n");
                    for (int i = 0; i < results.size(); i++) {
                        sb.append(i + 1).append(". ").append(results.get(i)).append("\n");
                    }
                    return textResult(sb.toString());
                } catch (IOException e) {
                    return errorResult("搜索失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 17b. dlqq_search_text_in_dir - 在目录下的文本文件中搜索内容
     */
    private void registerSearchTextInDirTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "搜索目录路径");
        properties.put("search_path", pathProp);

        Map<String, Object> patternProp = new HashMap<>();
        patternProp.put("type", "string");
        patternProp.put("description", "搜索文本模式");
        properties.put("pattern", patternProp);

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "模式：'text'（部分匹配）或 'regex'（正则表达式）");
        modeProp.put("enum", Arrays.asList("text", "regex"));
        properties.put("mode", modeProp);

        Map<String, Object> ignoreCaseProp = new HashMap<>();
        ignoreCaseProp.put("type", "boolean");
        ignoreCaseProp.put("description", "是否忽略大小写（默认true）");
        properties.put("ignore_case", ignoreCaseProp);

        Map<String, Object> extensionsProp = new HashMap<>();
        extensionsProp.put("type", "array");
        extensionsProp.put("description", "文件后缀名列表（如 ['.java', '.c', '.h']），为空时使用配置默认值");
        properties.put("file_extensions", extensionsProp);

        Map<String, Object> maxResultsProp = new HashMap<>();
        maxResultsProp.put("type", Arrays.asList("integer", "string"));
        maxResultsProp.put("description", "最大返回结果数（默认使用配置search.max.results，若无配置则为50）");
        properties.put("max_results", maxResultsProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("search_path", "pattern"));

        server.addTool("dlqq_search_text_in_dir",
            "在指定目录下的所有文本文件中搜索指定内容。支持正则表达式或部分匹配，支持忽略大小写，" +
            "返回匹配的文件路径、行号、行内容以及3行上下文。" +
            "可通过 file_extensions 指定文件类型，为空时使用 settings.properties 中的默认配置。" +
            "搜索受 search.max.files、search.max.file.size、search.max.results 配置限制。",
            inputSchema,
            arguments -> {
                String searchPath = (String) arguments.get("search_path");
                String pattern = (String) arguments.get("pattern");
                String mode = (String) arguments.getOrDefault("mode", "text");
                Boolean ignoreCase = parseBoolean(arguments.get("ignore_case"), true);
                @SuppressWarnings("unchecked")
                List<String> fileExtensions = (List<String>) arguments.get("file_extensions");

                // 从配置读取默认最大结果数
                int defaultMaxResults = getSearchConfigInt("search.max.results", 50);
                Integer maxResults = parseInteger(arguments.get("max_results"), defaultMaxResults);

                if (searchPath == null || searchPath.trim().isEmpty()) {
                    return errorResult("搜索路径不能为空");
                }
                if (pattern == null || pattern.trim().isEmpty()) {
                    return errorResult("搜索模式不能为空");
                }

                try {
                    // 读取配置
                    int maxFiles = getSearchConfigInt("search.max.files", 1000);
                    long maxFileSize = getSearchConfigLong("search.max.file.size", 10 * 1024 * 1024); // 默认10MB
                    String defaultExtensions = getSearchConfigString("search.text.extensions", 
                        ".java,.c,.cpp,.h,.hpp,.cs,.py,.js,.ts,.html,.css,.json,.xml,.yaml,.yml,.md,.txt,.sh,.bat,.properties,.gradle,.ini");

                    // 如果未指定扩展名，使用默认配置
                    if (fileExtensions == null || fileExtensions.isEmpty()) {
                        fileExtensions = Arrays.asList(defaultExtensions.split(","));
                    }

                    boolean useRegex = "regex".equalsIgnoreCase(mode);

                    List<FileTools.TextSearchResult> results = FileTools.searchTextInFiles(
                        searchPath, pattern, useRegex, ignoreCase, 
                        fileExtensions, maxFiles, maxFileSize
                    );

                    String formatted = FileTools.formatSearchResults(results, maxResults);
                    return textResult(formatted);

                } catch (IOException e) {
                    return errorResult("搜索失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 17c. dlqq_search_text_in_file - 在单个文本文件中搜索内容
     */
    private void registerSearchTextInFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件路径");
        properties.put("file_path", pathProp);

        Map<String, Object> patternProp = new HashMap<>();
        patternProp.put("type", "string");
        patternProp.put("description", "搜索文本模式");
        properties.put("pattern", patternProp);

        Map<String, Object> modeProp = new HashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "模式：'text'（部分匹配）或 'regex'（正则表达式）");
        modeProp.put("enum", Arrays.asList("text", "regex"));
        properties.put("mode", modeProp);

        Map<String, Object> ignoreCaseProp = new HashMap<>();
        ignoreCaseProp.put("type", "boolean");
        ignoreCaseProp.put("description", "是否忽略大小写（默认true）");
        properties.put("ignore_case", ignoreCaseProp);

        Map<String, Object> maxResultsProp = new HashMap<>();
        maxResultsProp.put("type", Arrays.asList("integer", "string"));
        maxResultsProp.put("description", "最大返回结果数（默认使用配置search.max.results，若无配置则为50，-1表示无限制）");
        properties.put("max_results", maxResultsProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("file_path", "pattern"));

        server.addTool("dlqq_search_text_in_file",
            "在单个文本文件中搜索指定内容。支持正则表达式或部分匹配，支持忽略大小写，" +
            "返回匹配的行号、行内容以及3行上下文。" +
            "可通过 max_results 参数限制返回结果数，或使用 search.max.results 配置设置默认值。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                String pattern = (String) arguments.get("pattern");
                String mode = (String) arguments.getOrDefault("mode", "text");
                Boolean ignoreCase = parseBoolean(arguments.get("ignore_case"), true);

                // 从配置读取默认最大结果数
                int defaultMaxResults = getSearchConfigInt("search.max.results", 50);
                Integer maxResults = parseInteger(arguments.get("max_results"), defaultMaxResults);

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }
                if (pattern == null || pattern.trim().isEmpty()) {
                    return errorResult("搜索模式不能为空");
                }

                try {
                    boolean useRegex = "regex".equalsIgnoreCase(mode);

                    List<FileTools.TextSearchResult> results = FileTools.searchTextInSingleFile(
                        filePath, pattern, useRegex, ignoreCase
                    );

                    // 限制结果数量
                    List<FileTools.TextSearchResult> limitedResults = maxResults > 0 && results.size() > maxResults
                        ? results.subList(0, maxResults)
                        : results;

                    String formatted = FileTools.formatSearchResults(limitedResults, -1);
                    return textResult(formatted);

                } catch (IOException e) {
                    return errorResult("搜索失败: " + e.getMessage());
                }
            }
        );
    }

    // ==================== 对话框工具 ====================

    /**
     * 18. dlqq_dialog_text - 文本输入对话框
     */
    private void registerDialogTextTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> titleProp = new HashMap<>();
        titleProp.put("type", "string");
        titleProp.put("description", "对话框标题");
        properties.put("title", titleProp);

        Map<String, Object> messageProp = new HashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "提示消息");
        properties.put("message", messageProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("title", "message"));

        server.addTool("dlqq_dialog_text",
            "向用户提问并等待用户输入文本。展示独立窗口供用户输入任意文本内容。",
            inputSchema,
            arguments -> {
                String title = (String) arguments.get("title");
                String message = (String) arguments.get("message");

                if (title == null || title.trim().isEmpty()) {
                    return errorResult("标题不能为空");
                }

                try {
                    DialogTools.DialogResult result = DialogTools.showTextInputDialog(title, message != null ? message : "");
                    if (result == null || !result.isConfirmed()) {
                        return textResult("用户取消了输入");
                    }
                    return textResult(result.getTextInput() != null ? result.getTextInput() : "");
                } catch (Exception e) {
                    return errorResult("对话框执行失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 19. dlqq_dialog_check - 确认对话框（是/否）
     */
    private void registerDialogCheckTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> titleProp = new HashMap<>();
        titleProp.put("type", "string");
        titleProp.put("description", "对话框标题");
        properties.put("title", titleProp);

        Map<String, Object> messageProp = new HashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "确认消息内容");
        properties.put("message", messageProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("title", "message"));

        server.addTool("dlqq_dialog_check",
            "向用户提问并要求用户选择是或否。展示独立确认窗口。",
            inputSchema,
            arguments -> {
                String title = (String) arguments.get("title");
                String message = (String) arguments.get("message");

                if (title == null || title.trim().isEmpty()) {
                    return errorResult("标题不能为空");
                }

                try {
                    boolean confirmed = DialogTools.showConfirmDialog(title, message != null ? message : "");
                    return textResult(confirmed ? "是" : "否");
                } catch (Exception e) {
                    return errorResult("对话框执行失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 20. dlqq_dialog_file_path - 文件选择对话框
     */
    private void registerDialogFilePathTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> titleProp = new HashMap<>();
        titleProp.put("type", "string");
        titleProp.put("description", "对话框标题");
        properties.put("title", titleProp);

        Map<String, Object> extensionsProp = new HashMap<>();
        extensionsProp.put("type", "array");
        extensionsProp.put("description", "允许的文件扩展名列表（如 [\"txt\", \"java\"]）");
        properties.put("extensions", extensionsProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("title"));

        server.addTool("dlqq_dialog_file_path",
            "向用户提问并要求用户选择文件。展示独立文件选择窗口，返回选中的文件路径。",
            inputSchema,
            arguments -> {
                String title = (String) arguments.get("title");
                @SuppressWarnings("unchecked")
                List<String> extensions = (List<String>) arguments.get("extensions");

                if (title == null || title.trim().isEmpty()) {
                    title = "选择文件";
                }

                try {
                    String[] extArray = extensions != null ? extensions.toArray(new String[0]) : null;
                    String[] paths = DialogTools.showFileChooser(title, extArray, false);

                    if (paths.length == 0) {
                        return textResult("用户未选择文件");
                    }

                    return textResult(paths[0]);
                } catch (Exception e) {
                    return errorResult("文件选择失败: " + e.getMessage());
                }
            }
        );
    }

    // ==================== 工具方法 ====================

    /**
     * 创建文本结果。
     * 若文本属于拒绝/取消/失败类（无权限、用户拒绝、规则拦截等），
     * 自动转为错误结果（isError=true），避免被当成正常返回。
     */
    private CallToolResult textResult(String text) {
        if (isRefusal(text)) {
            return errorResult(text);
        }
        CallToolResult result = new CallToolResult();
        result.setError(false);
        List<ToolContent> content = new ArrayList<>();
        content.add(ToolContent.text(text));
        result.setContent(content);
        return result;
    }

    /**
     * 判断工具输出是否属于拒绝/取消/失败类，需要以错误返回。
     */
    private boolean isRefusal(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String[] prefixes = {"【拒绝", "【操作已取消】", "【失败", "✗", "错误：", "错误:", "[错误]"};
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建错误结果
     */
    private CallToolResult errorResult(String error) {
        CallToolResult result = new CallToolResult();
        result.setError(true);
        List<ToolContent> content = new ArrayList<>();
        content.add(ToolContent.error(error));
        result.setContent(content);
        return result;
    }

    /**
     * 解析布尔参数（兼容 Boolean、数字和字符串）：
     * true/false、1/0、yes/no、on/off，无法识别时返回默认值。
     */
    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String s = String.valueOf(value).trim().toLowerCase();
        if (s.isEmpty()) {
            return defaultValue;
        }
        switch (s) {
            case "true":
            case "1":
            case "yes":
            case "on":
                return true;
            case "false":
            case "0":
            case "no":
            case "off":
                return false;
            default:
                return defaultValue;
        }
    }
    /**
     * 解析整数参数（兼容 Long、Integer 和 String）
     * 当传入非空值但无法解析时抛出IllegalArgumentException，确保AI传错参数时报错而不是使用默认值
     */
    private Integer parseInteger(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("参数格式错误：'" + str + "' 不是有效的整数");
            }
        }
        throw new IllegalArgumentException("参数类型错误：期望整数类型，但得到 " + value.getClass().getSimpleName());
    }

    /**
     * 解析长整数参数（兼容 Long、Integer 和 String）
     * 当传入非空值但无法解析时抛出IllegalArgumentException，确保AI传错参数时报错而不是使用默认值
     */
    private Long parseLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) {
                return defaultValue;
            }
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("参数格式错误：'" + str + "' 不是有效的整数");
            }
        }
        throw new IllegalArgumentException("参数类型错误：期望整数类型，但得到 " + value.getClass().getSimpleName());
    }

    // ==================== 搜索配置读取方法 ====================

    /**
     * 获取搜索配置值（字符串）
     */
    private String getSearchConfigString(String key, String defaultValue) {
        String value = McpTools.getSearchConfig(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * 获取搜索配置值（整数）
     */
    private int getSearchConfigInt(String key, int defaultValue) {
        String value = McpTools.getSearchConfig(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取搜索配置值（长整数）
     */
    private long getSearchConfigLong(String key, long defaultValue) {
        String value = McpTools.getSearchConfig(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ==================== 系统信息工具 ====================

    /**
     * dlqq_get_mcp_version - 获取MCP服务器版本和构建信息
     */
    private void registerGetMcpVersionTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_get_mcp_version",
            "获取MCP服务器的版本信息、真实构建时间（编译进代码）、JAR文件信息与运行时信息，用于确认代码是否已更新",
            inputSchema,
            arguments -> {
                String result = McpTools.getMcpVersion();
                return textResult(result);
            }
        );
    }

    /**
     * dlqq_get_tools_env - 获取当前可用的开发环境与已安装库
     */
    private void registerGetToolsEnvTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_get_tools_env",
            "获取当前可用的 C、Java、Python、Node.js 开发环境（版本与路径）以及各环境已安装的库。",
            inputSchema,
            arguments -> {
                String result = EnvTools.getToolsEnv();
                return textResult(result);
            }
        );
    }

    /**
     * dlqq_test_hot_reload - 热加载测试：返回代码中写死的标记字符串
     */
    private void registerTestHotReloadTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_test_hot_reload",
            "热加载测试：返回代码中写死的标记字符串。替换 JAR 后不重启仍返回旧值，重启后返回新值，用于验证新代码是否已加载",
            inputSchema,
            arguments -> {
                String result = McpTools.testHotReload();
                return textResult(result);
            }
        );
    }

    /**
     * dlqq_mcp_hide_tools - 动态隐藏（禁用）指定 MCP 工具，并持久化到 settings.properties
     */
    private void registerMcpHideToolsTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> toolNamesProp = new HashMap<>();
        toolNamesProp.put("type", "array");
        toolNamesProp.put("items", Collections.singletonMap("type", "string"));
        toolNamesProp.put("description", "要隐藏的工具名列表，如 [\"dlqq_capture_screen\",\"dlqq_convert_image\"]；也支持逗号分隔字符串");
        properties.put("tool_names", toolNamesProp);
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.singletonList("tool_names"));

        server.addTool("dlqq_mcp_hide_tools",
            "动态隐藏（禁用）指定的 MCP 工具：立即对客户端不可见，并写入 .dlqq/settings/settings.properties 的 mcp.tools.excluded，下次启动仍然保持隐藏状态；可用 dlqq_mcp_show_tools 恢复",
            inputSchema,
            arguments -> {
                List<String> names = parseToolNames(arguments);
                if (names.isEmpty()) {
                    return textResult("✗ 错误：请提供要隐藏的工具名（tool_names）");
                }
                McpServer.ToolChangeResult result = server.disableTools(names);
                McpTools.saveExcludedToolNames(server.getDisabledTools());
                boolean notified = server.notifyToolsListChanged();

                StringBuilder sb = new StringBuilder();
                sb.append("动态隐藏工具结果\n");
                sb.append("===================\n\n");
                sb.append("本次隐藏: ").append(result.applied.isEmpty() ? "（无）" : String.join(", ", result.applied)).append("\n");
                if (!result.unknown.isEmpty()) {
                    sb.append("不存在的工具: ").append(String.join(", ", result.unknown)).append("\n");
                }
                if (!result.protectedNames.isEmpty()) {
                    sb.append("受保护不可隐藏: ").append(String.join(", ", result.protectedNames)).append("\n");
                }
                sb.append("当前启用工具: ").append(result.enabledCount).append(" 个\n");
                sb.append("当前已隐藏工具: ").append(result.disabledCount).append(" 个");
                if (result.disabledCount > 0) {
                    sb.append("（").append(String.join(", ", server.getDisabledTools())).append("）");
                }
                sb.append("\n");
                sb.append("已持久化到 settings.properties: ").append(McpTools.EXCLUDED_TOOLS_KEY).append("\n");
                sb.append("客户端刷新通知: ").append(notified ? "已发送" : "未发送（无 SSE 连接，客户端下次重连后生效）");
                return textResult(sb.toString());
            }
        );
    }

    /**
     * dlqq_mcp_show_tools - 恢复（显示）指定被隐藏的 MCP 工具，并持久化
     */
    private void registerMcpShowToolsTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> toolNamesProp = new HashMap<>();
        toolNamesProp.put("type", "array");
        toolNamesProp.put("items", Collections.singletonMap("type", "string"));
        toolNamesProp.put("description", "要恢复显示的工具名列表；也支持逗号分隔字符串");
        properties.put("tool_names", toolNamesProp);
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.singletonList("tool_names"));

        server.addTool("dlqq_mcp_show_tools",
            "恢复（显示）指定被隐藏的 MCP 工具：从 mcp.tools.excluded 中移除并立即生效，下次启动仍然保持显示",
            inputSchema,
            arguments -> {
                List<String> names = parseToolNames(arguments);
                if (names.isEmpty()) {
                    return textResult("✗ 错误：请提供要恢复显示的工具名（tool_names）");
                }
                List<String> restored = server.enableTools(names);
                McpTools.saveExcludedToolNames(server.getDisabledTools());
                boolean notified = server.notifyToolsListChanged();
                StringBuilder sb = new StringBuilder();
                sb.append("动态显示工具结果\n");
                sb.append("===================\n\n");
                sb.append("本次恢复: ").append(restored.isEmpty() ? "（无，工具本就未隐藏）" : String.join(", ", restored)).append("\n");
                sb.append("当前启用工具: ").append(server.getEnabledToolCount()).append(" 个\n");
                sb.append("当前已隐藏工具: ").append(server.getDisabledTools().size()).append(" 个\n");
                sb.append("已持久化到 settings.properties: ").append(McpTools.EXCLUDED_TOOLS_KEY).append("\n");
                sb.append("客户端刷新通知: ").append(notified ? "已发送" : "未发送（无 SSE 连接）");
                return textResult(sb.toString());
            }
        );
    }

    /**
     * dlqq_mcp_refresh_tools - 仅刷新（发送 notifications/tools/list_changed）
     */
    private void registerMcpRefreshToolsTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_mcp_refresh_tools",
            "仅向客户端发送 notifications/tools/list_changed，让客户端重新拉取工具列表（不改变工具集，HTTP 模式需要客户端已打开 SSE）",
            inputSchema,
            arguments -> {
                boolean notified = server.notifyToolsListChanged();
                return textResult("刷新通知: " + (notified ? "已发送" : "未发送（无 SSE 连接）")
                        + "\n当前启用工具: " + server.getEnabledToolCount() + " 个"
                        + "\n当前已移除工具: " + server.getDisabledTools().size() + " 个");
            }
        );
    }

    /**
     * dlqq_mcp_reset_tools - 恢复所有被移除的工具并刷新
     */
    private void registerMcpResetToolsTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_mcp_reset_tools",
            "恢复所有被移除（禁用）的工具，清空 settings.properties 中的 mcp.tools.excluded，并发送 notifications/tools/list_changed",
            inputSchema,
            arguments -> {
                List<String> restored = server.enableAllTools();
                McpTools.saveExcludedToolNames(Collections.emptyList());
                boolean notified = server.notifyToolsListChanged();

                StringBuilder sb = new StringBuilder();
                sb.append("恢复被移除工具结果\n");
                sb.append("===================\n\n");
                sb.append("本次恢复: ").append(restored.isEmpty() ? "（无，所有工具本来就是启用状态）" : String.join(", ", restored)).append("\n");
                sb.append("当前启用工具: ").append(server.getEnabledToolCount()).append(" 个\n");
                sb.append("已清空 settings.properties: ").append(McpTools.EXCLUDED_TOOLS_KEY).append("\n");
                sb.append("客户端刷新通知: ").append(notified ? "已发送" : "未发送（无 SSE 连接，客户端下次重连后生效）");
                return textResult(sb.toString());
            }
        );
    }

    /**
     * dlqq_mcp_run_tools - 按名称直接调用任意已注册工具（绕过运行时移除/客户端未加载限制）
     */
    private void registerMcpRunToolsTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "要调用的工具名，例如 dlqq_get_current_time");
        properties.put("name", nameProp);

        Map<String, Object> argumentsProp = new HashMap<>();
        argumentsProp.put("type", "object");
        argumentsProp.put("description", "工具参数对象（可选），例如 {\"expression\":\"1+1\"}");
        properties.put("arguments", argumentsProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.singletonList("name"));

        server.addTool("dlqq_mcp_run_tools",
            "按名称直接调用任意已注册的 MCP 工具：不受“已移除（dlqq_mcp_remove_tools）/客户端未加载”限制，可用于调用被移除或客户端看不到的工具；只登记了名称但服务端未注册的工具无法调用",
            inputSchema,
            arguments -> {
                Object nameValue = arguments == null ? null : arguments.get("name");
                String toolName = nameValue == null ? "" : String.valueOf(nameValue).trim();
                if (toolName.isEmpty()) {
                    return textResult("✗ 错误：请提供要调用的工具名（name）");
                }
                if ("dlqq_mcp_run_tools".equals(toolName)) {
                    return textResult("✗ 错误：dlqq_mcp_run_tools 不能调用自身");
                }

                Map<String, Object> toolArguments = new LinkedHashMap<>();
                Object rawArgs = arguments == null ? null : arguments.get("arguments");
                if (rawArgs instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        toolArguments.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }

                try {
                    CallToolResult result = server.invokeToolDirect(toolName, toolArguments);
                    if (result == null) {
                        return textResult("✗ 工具不存在或未注册: " + toolName + "（服务端没有该工具，无法调用）");
                    }
                    return result;
                } catch (Exception e) {
                    return textResult("✗ 调用工具失败: " + toolName + " -> " + e.getMessage());
                }
            }
        );
    }

    /**
     * dlqq_workspace_set - 设置工作区目录（相对路径的基准目录）
     */
    private void registerWorkspaceSetTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "工作区目录（必须是已存在的绝对路径）");
        properties.put("path", pathProp);
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.singletonList("path"));

        server.addTool("dlqq_workspace_set",
            "设置 MCP 服务器的工作区目录（必须是已存在的绝对目录，且不能位于 .dlqq 下）：之后所有工具的相对路径都基于该目录解析。工作区路径不持久化，仅本次运行有效；设置失败时保持当前工作区不变",
            inputSchema,
            arguments -> {
                Object raw = arguments == null ? null : arguments.get("path");
                String path = raw == null ? "" : String.valueOf(raw).trim();
                if (path.isEmpty()) {
                    return textResult("✗ 错误：请提供工作区目录（path）");
                }
                if (!Workspace.isValidDirectory(path)) {
                    return textResult("✗ 设置失败：工作区必须是已存在的绝对目录: " + path + "\n当前工作区保持不变: " + Workspace.getRoot());
                }
                Path target = Workspace.resolve(path);
                if (Workspace.touchesDlqq(target)) {
                    return textResult("✗ 设置失败：工作区不能位于 .dlqq 目录下: " + target
                            + "\n当前工作区保持不变: " + Workspace.getRoot());
                }
                // 目标工作区与当前一致：直接返回，不弹任何窗口
                if (target.toAbsolutePath().normalize()
                        .equals(Workspace.getRoot().toAbsolutePath().normalize())) {
                    return textResult("工作区未变化，无需修改\n===================\n\n"
                            + "当前工作区: " + Workspace.getRoot() + "\n"
                            + "来源: " + Workspace.getSource() + "\n"
                            + "资源权限: " + Workspace.getPermissionLabel());
                }
                // 切换工作区始终需要审批（带倒计时，倒计时与工具总超时一致）
                boolean confirmed = DialogTools.showApprovalDialog(
                        "切换工作区审批",
                        "AI 请求切换 MCP 服务器的工作区：\n\n"
                                + "当前工作区: " + Workspace.getRoot() + "\n"
                                + "新工作区: " + target + "\n\n"
                                + "工作区路径不持久化，仅本次运行有效。\n"
                                + "是否允许切换？"
                );
                if (!confirmed) {
                    return textResult("【操作已取消】用户拒绝了工作区切换\n当前工作区: " + Workspace.getRoot());
                }
                try {
                    Workspace.setRoot(path);
                } catch (IllegalArgumentException e) {
                    return textResult("✗ 设置失败：" + e.getMessage());
                }
                return textResult("工作区已设置（仅本次运行有效，不持久化）\n===================\n\n"
                        + "当前工作区: " + Workspace.getRoot() + "\n"
                        + "来源: " + Workspace.getSource() + "\n"
                        + "资源权限: " + Workspace.getPermissionLabel() + "\n"
                        + "操作权限: " + Workspace.getOperationPermissionLabel() + "\n"
                        + "权限配置: " + Workspace.getPermissionFile());
            }
        );
    }

    /**
     * dlqq_workspace_get - 查看当前工作区
     */
    private void registerWorkspaceGetTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_workspace_get",
            "查看当前工作区目录、来源（--workspace / 运行期设置 / 进程工作目录 / 会话 cwd / default_workspace）、资源权限（chat/readonly/workspace/approval/highrisk/full）、操作权限（chat/readonly/approval/highrisk/full）、权限配置文件、全局运行时目录与缓存目录；并显示当前会话标识、会话键名、Agent 键名、工作区键名、活跃会话数",
            inputSchema,
            arguments -> textResult("工作区信息\n===================\n\n"
                    + "当前会话: " + Workspace.getSessionId() + "\n"
                    + "会话标识键: params._meta[" + Workspace.getSessionMetaKey() + "]\n"
                    + "Agent 标识键: params._meta[" + Workspace.getSessionAgentMetaKey() + "]\n"
                    + "工作区标识键: params._meta[" + Workspace.getSessionWorkspaceMetaKey() + "]\n"
                    + "活跃会话数: " + Workspace.getSessionCount() + "\n"
                    + "当前工作区: " + Workspace.getRoot() + "\n"
                    + "来源: " + Workspace.getSource() + "\n"
                    + "启动参数: " + (Workspace.getCliPath() == null ? "（未指定）" : Workspace.getCliPath()) + "\n"
                    + "资源权限: " + Workspace.getPermissionLabel() + "\n"
                    + "操作权限: " + Workspace.getOperationPermissionLabel() + "\n"
                    + "权限配置: " + Workspace.getPermissionFile() + "\n"
                    + "运行时目录: " + RuntimeDirs.getRoot() + "\n"
                    + "缓存目录: " + SessionScope.currentCacheDir() + "\n"
                    + "进程工作目录: " + System.getProperty("user.dir"))
        );
    }

    /**
     * dlqq_get_cwd_path - 获取进程当前工作目录（CWD）与工作区路径
     */
    private void registerGetCwdPathTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());
        server.addTool("dlqq_get_cwd_path",
            "获取 MCP 服务进程的当前工作目录（CWD）、当前工作区、全局运行时目录与缓存目录",
            inputSchema,
            arguments -> textResult("路径信息\n===================\n\n"
                    + "进程工作目录: " + System.getProperty("user.dir") + "\n"
                    + "当前工作区: " + Workspace.getRoot() + "\n"
                    + "工作区来源: " + Workspace.getSource() + "\n"
                    + "当前会话: " + Workspace.getSessionId() + "\n"
                    + "运行时目录: " + RuntimeDirs.getRoot() + "\n"
                    + "缓存目录: " + SessionScope.currentCacheDir() + "\n"
                    + "兜底工作区: " + RuntimeDirs.getDefaultWorkspace())
        );
    }
    /**
     * dlqq_workspace_reset - 清除持久化工作区并回退
     */
    private void registerWorkspaceResetTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_workspace_reset",
            "回退到启动参数 --workspace（若指定）或进程工作目录；不修改工作区内的权限配置",
            inputSchema,
            arguments -> {
                String target = Workspace.getCliPath() != null
                        ? Workspace.getCliPath()
                        : System.getProperty("user.dir");
                Path targetPath = Workspace.resolve(target);
                // 回退目标与当前一致：直接返回，不弹窗口
                if (targetPath.toAbsolutePath().normalize()
                        .equals(Workspace.getRoot().toAbsolutePath().normalize())) {
                    return textResult("工作区已是默认值，无需重置\n===================\n\n"
                            + "当前工作区: " + Workspace.getRoot()
                            + "\n来源: " + Workspace.getSource());
                }
                // 重置工作区始终需要审批（带倒计时）
                boolean confirmed = DialogTools.showApprovalDialog(
                        "重置工作区审批",
                        "AI 请求重置 MCP 服务器的工作区：\n\n"
                                + "当前工作区: " + Workspace.getRoot() + "\n\n"
                                + "回退到: " + targetPath + "\n\n"
                                + "是否允许重置？"
                );
                if (!confirmed) {
                    return textResult("【操作已取消】用户拒绝了重置工作区\n当前工作区: " + Workspace.getRoot());
                }
                Workspace.resetToDefault();
                return textResult("工作区已重置\n===================\n\n"
                        + "当前工作区: " + Workspace.getRoot() + "\n"
                        + "来源: " + Workspace.getSource() + "\n"
                        + "资源权限: " + Workspace.getPermissionLabel());
            }
        );
    }

    /**
     * dlqq_workspace_permission_set - 设置资源权限 / 操作权限（持久化到工作区配置）
     */
    private void registerWorkspacePermissionTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> resourceProp = new HashMap<>();
        resourceProp.put("type", "string");
        resourceProp.put("description", "资源权限：chat（对话）、readonly（只读）、workspace（仅工作区内修改）、approval（工作区外审批）、highrisk（高危需批准）、full（完全权限）");
        properties.put("resource_mode", resourceProp);
        Map<String, Object> operationProp = new HashMap<>();
        operationProp.put("type", "string");
        operationProp.put("description", "操作权限：chat（对话）、readonly（只读）、approval（需要批准）、highrisk（高危需批准）、full（完全权限）");
        properties.put("operation_mode", operationProp);
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_workspace_permission_set",
            "设置当前工作区的资源权限（文件/目录/命令等）与操作权限（鼠标/键盘/屏幕/串口/BLE/WiFi/USB 等外设）。"
                    + "该工具在任意权限等级下都可调用；降权/同级直接执行，提权必须用户批准。"
                    + "配置写入 <部署目录>/.dlqq/settings/<工作区hash>/settings.properties 的 "
                    + Workspace.PERMISSION_KEY + " 与 " + Workspace.OPERATION_PERMISSION_KEY,
            inputSchema,
            arguments -> {
                String resourceMode = arguments == null || arguments.get("resource_mode") == null ? null
                        : String.valueOf(arguments.get("resource_mode")).trim();
                String operationMode = arguments == null || arguments.get("operation_mode") == null ? null
                        : String.valueOf(arguments.get("operation_mode")).trim();
                if ((resourceMode == null || resourceMode.isEmpty())
                        && (operationMode == null || operationMode.isEmpty())) {
                    return textResult("✗ 请至少提供 resource_mode（资源权限）或 operation_mode（操作权限）之一");
                }
                // 参数校验必须在审批之前：非法权限值直接报错，不弹审批窗口
                if (resourceMode != null && !resourceMode.isEmpty() && resourceRank(resourceMode) < 0) {
                    return textResult("✗ 参数错误：resource_mode 非法（" + resourceMode
                            + "），可选值：chat / readonly / workspace / approval / highrisk / full");
                }
                if (operationMode != null && !operationMode.isEmpty() && operationRank(operationMode) < 0) {
                    return textResult("✗ 参数错误：operation_mode 非法（" + operationMode
                            + "），可选值：readonly / approval / highrisk / full");
                }
                // 特殊规则：降权/同级直接执行，提权必须用户批准
                StringBuilder escalation = new StringBuilder();
                if (resourceMode != null && !resourceMode.isEmpty()
                        && resourceRank(resourceMode) > resourceRank(Workspace.getPermission())) {
                    escalation.append("资源权限: ").append(Workspace.getPermissionLabel())
                            .append(" → ").append(resourceMode).append("\n");
                }
                if (operationMode != null && !operationMode.isEmpty()
                        && operationRank(operationMode) > operationRank(Workspace.getOperationPermission())) {
                    escalation.append("操作权限: ").append(Workspace.getOperationPermissionLabel())
                            .append(" → ").append(operationMode).append("\n");
                }
                if (escalation.length() > 0) {
                    boolean approved = DialogTools.showApprovalDialog(
                            "权限提权审批",
                            "AI 请求提升 MCP 服务器权限：\n\n" + escalation
                                    + "\n当前工作区: " + Workspace.getRoot() + "\n"
                                    + "权限配置文件: " + Workspace.getPermissionFile() + "\n\n"
                                    + "提权后 AI 可执行的操作范围会扩大，是否允许？");
                    if (!approved) {
                        return textResult("【操作已取消】用户拒绝提升权限\n资源权限: " + Workspace.getPermissionLabel()
                                + "\n操作权限: " + Workspace.getOperationPermissionLabel());
                    }
                }
                try {
                    if (resourceMode != null && !resourceMode.isEmpty()) {
                        Workspace.setPermission(resourceMode);
                    }
                    if (operationMode != null && !operationMode.isEmpty()) {
                        Workspace.setOperationPermission(operationMode);
                    }
                } catch (IllegalArgumentException e) {
                    return textResult("✗ 设置失败：" + e.getMessage());
                }
                return textResult("工作区权限已设置\n===================\n\n"
                        + "当前工作区: " + Workspace.getRoot() + "\n"
                        + "资源权限: " + Workspace.getPermissionLabel() + "\n"
                        + "操作权限: " + Workspace.getOperationPermissionLabel() + "\n"
                        + "权限配置: " + Workspace.getPermissionFile());
            }
        );
    }

    /** 资源权限等级数值：越大权限越高；未知模式返回 -1。 */
    private static int resourceRank(String mode) {
        if (mode == null) {
            return -1;
        }
        switch (mode.trim().toLowerCase()) {
            case "chat":
                return 0;
            case "readonly":
                return 1;
            case "workspace":
                return 2;
            case "approval":
                return 3;
            case "highrisk":
                return 4;
            case "full":
                return 5;
            default:
                return -1;
        }
    }

    /** 操作权限等级数值：越大权限越高；未知模式返回 -1。 */
    private static int operationRank(String mode) {
        if (mode == null) {
            return -1;
        }
        switch (mode.trim().toLowerCase()) {
            case "chat":
                return 0;
            case "readonly":
                return 1;
            case "approval":
                return 2;
            case "highrisk":
                return 3;
            case "full":
                return 4;
            default:
                return -1;
        }
    }

    /**
     * 解析工具名参数：支持 JSON 数组或逗号分隔字符串。
     */
    private List<String> parseToolNames(Map<String, Object> arguments) {
        List<String> names = new ArrayList<>();
        if (arguments == null) {
            return names;
        }
        Object raw = arguments.get("tool_names");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    names.add(String.valueOf(item).trim());
                }
            }
        } else if (raw != null) {
            for (String part : String.valueOf(raw).split(",")) {
                if (!part.trim().isEmpty()) {
                    names.add(part.trim());
                }
            }
        }
        return names;
    }

    /**
     * 48. dlqq_check_bluetooth_open - 检查电脑蓝牙是否开启
     */
    private void registerCheckBluetoothTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_check_bluetooth_open",
            "检查Windows电脑的蓝牙适配器是否已开启",
            inputSchema,
            arguments -> {
                String result = BleDevices.checkBluetoothOpen();
                return textResult(result);
            }
        );
    }

    // ==================== MCP数据管理工具 ====================

    /**
     * dlqq_clear_mcp_data - 清除MCP数据目录
     */
    private void registerClearMcpDataTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_clear_mcp_data",
            "清除运行时数据（.dlqq 下的 cache、data、log），保留 settings 与 workspace_config.json（权限配置受保护）。即使部分文件被占用也会返回成功结果，并告知哪些文件被占用。",
            inputSchema,
            arguments -> {
                String result = McpTools.clearDlqqData();
                return textResult(result);
            }
        );
    }

    /**
     * dlqq_clear_mcp_cache - 清除MCP缓存目录
     */
    private void registerClearMcpCacheTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> workspaceProp = new HashMap<>();
        workspaceProp.put("type", "string");
        workspaceProp.put("description", "要清理的工作区绝对路径（可选）；不传且 all=false 时清理当前工作区的缓存");
        properties.put("workspace", workspaceProp);
        Map<String, Object> allProp = new HashMap<>();
        allProp.put("type", "boolean");
        allProp.put("description", "是否清理全部工作区的缓存（可选，默认 false）");
        properties.put("all", allProp);
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_clear_mcp_cache",
            "清除缓存目录（.dlqq/cache/<工作区hash>，按工作区隔离、不区分会话）。可传 workspace 清理指定工作区，或 all=true 清理全部工作区；都不传则清理当前工作区缓存内容。",
            inputSchema,
            arguments -> {
                String workspace = arguments != null && arguments.get("workspace") != null
                        ? String.valueOf(arguments.get("workspace")) : null;
                boolean all = arguments != null && Boolean.TRUE.equals(arguments.get("all"));
                String result = McpTools.clearCacheFor(workspace, all);
                return textResult(result);
            }
        );
    }

    // ==================== 项目构建和芯片操作工具 ====================

    private void registerBuildProjectTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_build_project",
            "构建项目。执行settings.properties中script.build定义的脚本；未配置时返回错误并提示先设置脚本路径。",
            inputSchema,
            arguments -> {
                String result = McpTools.buildProject();
                return textResult(result);
            }
        );
    }

    private void registerRebuildProjectTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_rebuild_project",
            "重建项目（clean + build）。执行settings.properties中script.rebuild定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.rebuildProject();
                return textResult(result);
            }
        );
    }

    private void registerChipEraseTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_chip_erase",
            "擦除芯片。执行settings.properties中script.chip.erase定义的脚本；未配置时返回错误并提示先设置脚本路径。",
            inputSchema,
            arguments -> {
                String result = McpTools.chipErase();
                return textResult(result);
            }
        );
    }

    private void registerChipDownloadTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_chip_download",
            "下载固件到芯片。执行settings.properties中script.chip.download定义的脚本；未配置时返回错误并提示先设置脚本路径。",
            inputSchema,
            arguments -> {
                String result = McpTools.chipDownload();
                return textResult(result);
            }
        );
    }

    // ==================== 组合脚本执行工具 ====================

    private void registerEraseDownloadTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_erase_download",
            "擦除芯片并下载固件。执行settings.properties中script.erase.download定义的脚本；未配置时返回错误并提示先设置脚本路径。",
            inputSchema,
            arguments -> {
                String result = McpTools.eraseDownload();
                return textResult(result);
            }
        );
    }

    private void registerBuildEraseDownloadTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_build_erase_download",
            "构建项目、擦除芯片并下载固件。执行settings.properties中script.build.erase.download定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.buildEraseDownload();
                return textResult(result);
            }
        );
    }

    private void registerRebuildEraseDownloadTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_rebuild_erase_download",
            "重建项目、擦除芯片并下载固件。执行settings.properties中script.rebuild.erase.download定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.rebuildEraseDownload();
                return textResult(result);
            }
        );
    }

    // ==================== 运行调试工具 ====================

    private void registerRunProjectTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_run_project",
            "运行项目。执行settings.properties中script.run定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.runProject();
                return textResult(result);
            }
        );
    }

    private void registerDebugProjectTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_debug_project",
            "调试项目。执行settings.properties中script.debug定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.debugProject();
                return textResult(result);
            }
        );
    }

    private void registerBuildRunProjectTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_build_run_project",
            "构建并运行项目。执行settings.properties中script.build.run定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.buildRunProject();
                return textResult(result);
            }
        );
    }

    private void registerBuildDebugProjectTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_build_debug_project",
            "构建并调试项目。执行settings.properties中script.build.debug定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.buildDebugProject();
                return textResult(result);
            }
        );
    }

    private void registerRebuildRunProjectTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_rebuild_run_project",
            "重建并运行项目。执行settings.properties中script.rebuild.run定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.rebuildRunProject();
                return textResult(result);
            }
        );
    }

    private void registerRebuildDebugProjectTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_rebuild_debug_project",
            "重建并调试项目。执行settings.properties中script.rebuild.debug定义的脚本。",
            inputSchema,
            arguments -> {
                String result = McpTools.rebuildDebugProject();
                return textResult(result);
            }
        );
    }

    // ==================== MCP数据管理工具 ====================

    /**
     * 获取MCP配置属性
     */
    private void registerGetMcpPropertiesTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> keyProp = new HashMap<>();
        keyProp.put("type", "string");
        keyProp.put("description", "属性键名（可选）。如果指定，只返回该属性的值；如果不指定，返回所有属性列表");
        properties.put("key", keyProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_get_mcp_properties",
            "获取MCP配置属性。可以获取所有属性列表，或指定属性键获取单个属性值。配置存储在 .dlqq/settings/settings.properties 文件中。",
            inputSchema,
            arguments -> {
                String key = (String) arguments.get("key");

                if (key != null && !key.isEmpty()) {
                    // 获取单个属性
                    String value = McpTools.getConfigFromSettings(key);
                    if (value == null) {
                        return textResult("属性 '" + key + "' 不存在");
                    }
                    return textResult(key + " = " + value);
                } else {
                    // 获取所有属性
                    Map<String, String> props = McpTools.getAllProperties();
                    String formatted = McpTools.formatProperties(props);
                    return textResult(formatted);
                }
            }
        );
    }

    /**
     * 设置MCP配置属性
     */
    private void registerSetMcpPropertiesTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> keyProp = new HashMap<>();
        keyProp.put("type", "string");
        keyProp.put("description", "属性键名，例如：response.max.size、search.max.files、script.build");
        properties.put("key", keyProp);

        Map<String, Object> valueProp = new HashMap<>();
        valueProp.put("type", "string");
        valueProp.put("description", "属性值");
        properties.put("value", valueProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_set_mcp_properties",
            "设置MCP配置属性。至少指定 key 和 value 其中一个参数。配置保存到 .dlqq/settings/settings.properties 文件中。常用属性包括：response.max.size（响应大小限制，默认25804字节约25KB，最大258048）、search.max.files（最大搜索文件数）、search.max.file.size（最大文件大小）、search.text.extensions（文本文件后缀）、script.build（构建脚本路径）等。",
            inputSchema,
            arguments -> {
                String key = (String) arguments.get("key");
                String value = (String) arguments.get("value");

                if ((key == null || key.isEmpty()) && (value == null || value.isEmpty())) {
                    return errorResult("至少指定一个参数：key 或 value");
                }

                // 如果只指定了key，显示该属性的当前值
                if (key != null && !key.isEmpty() && (value == null || value.isEmpty())) {
                    String currentValue = McpTools.getConfigFromSettings(key);
                    if (currentValue == null) {
                        return textResult("属性 '" + key + "' 不存在");
                    }
                    return textResult(key + " = " + currentValue + "\n\n使用 dlqq_set_mcp_properties 并指定 value 参数来修改该属性");
                }

                // 设置属性
                // 权限相关属性禁止通过本工具修改：权限只能由 dlqq_workspace_permission_set 管理
                if (key != null && (key.startsWith("permission.") || key.startsWith("tool.policy.")
                        || "workspace.write.mode".equals(key))) {
                    return errorResult("✗ 拒绝设置：'" + key + "' 属于权限相关属性，"
                            + "请使用 dlqq_workspace_permission_set 修改权限，本工具不接受权限类键名");
                }
                // 对 response.max.size 进行验证，不能超过API上限
                if ("response.max.size".equals(key)) {
                    try {
                        int size = Integer.parseInt(value);
                        if (size > McpTools.MAX_RESPONSE_SIZE) {
                            return errorResult(String.format("response.max.size 不能超过 %d (API上限)", McpTools.MAX_RESPONSE_SIZE));
                        }
                        if (size < 1) {
                            return errorResult("response.max.size 必须大于0");
                        }
                    } catch (NumberFormatException e) {
                        return errorResult("response.max.size 必须是有效的整数");
                    }
                }
                String result = McpTools.setProperty(key, value);
                return textResult(result);
            }
        );
    }

    /**
     * dlqq_clear_mcp_log - 清除MCP日志文件
     */
    private void registerClearMcpLogTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();
        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_clear_mcp_log",
            "清除MCP日志文件（.dlqq/log目录），保留目录结构。即使部分文件被占用也会返回成功结果，并告知哪些文件被占用。",
            inputSchema,
            arguments -> {
                String result = McpTools.clearLog();
                return textResult(result);
            }
        );
    }

    // ==================== 脚本路径设置工具 ====================

    /**
     * 通用注册脚本路径设置工具
     */
    private void registerSetScriptTool(McpServer server, String toolName, String description,
                                       Function<String, String> setter) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "脚本路径（.bat或.sh脚本的绝对路径）");
        properties.put("script_path", pathProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.singletonList("script_path"));

        server.addTool(toolName, description, inputSchema, arguments -> {
            String scriptPath = (String) arguments.get("script_path");
            if (scriptPath == null || scriptPath.trim().isEmpty()) {
                return errorResult("脚本路径不能为空");
            }
            String result = setter.apply(scriptPath.trim());
            return textResult(result);
        });
    }

    /*
     * 【已屏蔽】dlqq_set_build_script - 设置构建脚本路径
     * 屏蔽原因：AI 不应通过设置脚本路径绕过人工审批（构建必须走 dlqq_run_command 并人工确认）。
     * 保留代码以备将来恢复；如需启用，取消下一段注释并把上面的注册行一并放开。
     *
    private void registerSetBuildScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_build_script",
            "设置构建脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.build 项。",
            McpTools::setBuildScript);
    }
     */

    /**
     * dlqq_get_tool_list - 获取工具列表
     */
    private void registerGetToolListTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> filterProp = new HashMap<>();
        filterProp.put("type", "string");
        filterProp.put("description", "名称过滤关键字（可选，如 wifi、keyboard、mouse、set_build，为空返回全部工具）");
        properties.put("filter", filterProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_get_tool_list",
            "获取MCP服务器上所有已注册工具的列表（工具名称和描述）。支持按名称关键字过滤。",
            inputSchema,
            arguments -> {
                String filter = (String) arguments.get("filter");
                return textResult(server.getToolsInfo(filter));
            }
        );
    }

    /**
     * dlqq_set_rebuild_script - 设置重建脚本路径
     */
    private void registerSetRebuildScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_rebuild_script",
            "设置重建脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.rebuild 项。",
            McpTools::setRebuildScript);
    }

    /**
     * dlqq_set_chip_erase_script - 设置芯片擦除脚本路径
     */
    private void registerSetChipEraseScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_chip_erase_script",
            "设置芯片擦除脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.chip.erase 项。",
            McpTools::setChipEraseScript);
    }

    /**
     * dlqq_set_chip_download_script - 设置芯片下载脚本路径
     */
    private void registerSetChipDownloadScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_chip_download_script",
            "设置芯片下载脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.chip.download 项。",
            McpTools::setChipDownloadScript);
    }

    /**
     * dlqq_set_erase_download_script - 设置擦除并下载组合脚本路径
     */
    private void registerSetEraseDownloadScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_erase_download_script",
            "设置擦除并下载组合脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.erase.download 项。",
            McpTools::setEraseDownloadScript);
    }

    /**
     * dlqq_set_build_erase_download_script - 设置构建擦除下载组合脚本路径
     */
    private void registerSetBuildEraseDownloadScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_build_erase_download_script",
            "设置构建、擦除并下载组合脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.build.erase.download 项。",
            McpTools::setBuildEraseDownloadScript);
    }

    /**
     * dlqq_set_rebuild_erase_download_script - 设置重建擦除下载组合脚本路径
     */
    private void registerSetRebuildEraseDownloadScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_rebuild_erase_download_script",
            "设置重建、擦除并下载组合脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.rebuild.erase.download 项。",
            McpTools::setRebuildEraseDownloadScript);
    }

    /**
     * dlqq_set_run_script - 设置运行脚本路径
     */
    private void registerSetRunScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_run_script",
            "设置运行项目脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.run 项。",
            McpTools::setRunScript);
    }

    /**
     * dlqq_set_debug_script - 设置调试脚本路径
     */
    private void registerSetDebugScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_debug_script",
            "设置调试项目脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.debug 项。",
            McpTools::setDebugScript);
    }

    /**
     * dlqq_set_build_run_script - 设置构建并运行脚本路径
     */
    private void registerSetBuildRunScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_build_run_script",
            "设置构建并运行组合脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.build.run 项。",
            McpTools::setBuildRunScript);
    }

    /**
     * dlqq_set_build_debug_script - 设置构建并调试脚本路径
     */
    private void registerSetBuildDebugScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_build_debug_script",
            "设置构建并调试组合脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.build.debug 项。",
            McpTools::setBuildDebugScript);
    }

    /**
     * dlqq_set_rebuild_run_script - 设置重建并运行脚本路径
     */
    private void registerSetRebuildRunScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_rebuild_run_script",
            "设置重建并运行组合脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.rebuild.run 项。",
            McpTools::setRebuildRunScript);
    }

    /**
     * dlqq_set_rebuild_debug_script - 设置重建并调试脚本路径
     */
    private void registerSetRebuildDebugScriptTool(McpServer server) {
        registerSetScriptTool(server, "dlqq_set_rebuild_debug_script",
            "设置重建并调试组合脚本路径。配置保存到 .dlqq/settings/settings.properties 的 script.rebuild.debug 项。",
            McpTools::setRebuildDebugScript);
    }

    /**
     * dlqq_reset_mcp_properties - 重置MCP配置为默认值
     */
    private void registerResetMcpPropertiesTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> keyProp = new HashMap<>();
        keyProp.put("type", "string");
        keyProp.put("description", "要重置的属性键名（可选）。如果为空，则重置所有配置为默认值");
        properties.put("key", keyProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.emptyList());

        server.addTool("dlqq_reset_mcp_properties",
            "重置MCP配置属性为默认值。可以重置单个属性或所有属性。配置保存到 .dlqq/settings/settings.properties 文件中。",
            inputSchema,
            arguments -> {
                String key = (String) arguments.get("key");
                String result = McpTools.resetProperties(key);
                return textResult(result);
            }
        );
    }

    /**
     * dlqq_read_office_file - 读取Office文件并转换为Markdown
     */
    private void registerReadOfficeFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "Office文件路径（支持PDF、DOC、DOCX、XLS、XLSX、PPT、PPTX）");
        properties.put("file_path", pathProp);

        Map<String, Object> startPageProp = new HashMap<>();
        startPageProp.put("type", "integer");
        startPageProp.put("description", "起始页码/段落/Sheet/幻灯片索引（可选，从1开始。PDF:页码；Word:段落；Excel:Sheet；PPT:幻灯片）");
        properties.put("start_page", startPageProp);

        Map<String, Object> endPageProp = new HashMap<>();
        endPageProp.put("type", "integer");
        endPageProp.put("description", "结束页码/段落/Sheet/幻灯片索引（可选，包含该页。不指定则处理到末尾，最大100页/段/Sheet/幻灯片）");
        properties.put("end_page", endPageProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.singletonList("file_path"));

        server.addTool("dlqq_read_office_file",
            "读取Office文件（PDF、DOC、DOCX、XLS、XLSX、PPT、PPTX）并转换为Markdown格式。支持分页/分段处理：PDF按页码、Word按段落、Excel按Sheet、PPT按幻灯片。转换后的文件保存在当前工作区缓存目录（.dlqq/cache/<工作区>），返回Markdown文件路径。",
            inputSchema,
            arguments -> {
                String filePath = (String) arguments.get("file_path");
                Integer startPage = null;
                Integer endPage = null;
                
                if (arguments.get("start_page") != null) {
                    startPage = ((Number) arguments.get("start_page")).intValue();
                }
                if (arguments.get("end_page") != null) {
                    endPage = ((Number) arguments.get("end_page")).intValue();
                }

                if (filePath == null || filePath.trim().isEmpty()) {
                    return errorResult("文件路径不能为空");
                }

                try {
                    Map<String, Object> result = OfficeTools.readOfficeFile(filePath, startPage, endPage);

                    StringBuilder sb = new StringBuilder();
                    sb.append("✓ Office文件转换成功\n");
                    sb.append("===================\n\n");
                    sb.append("原始文件: ").append(result.get("originalPath")).append("\n");
                    sb.append("标题: ").append(result.get("title")).append("\n");
                    if (startPage != null || endPage != null) {
                        sb.append("处理范围: ").append(startPage != null ? startPage : "起始").append("-").append(endPage != null ? endPage : "末尾").append("\n");
                    }
                    sb.append("\n");
                    sb.append("Markdown文件: ").append(result.get("markdownPath")).append("\n");
                    sb.append("文件大小: ").append(result.get("size")).append(" 字节\n\n");
                    sb.append("提示: 请使用 dlqq_read_file 工具读取上述Markdown文件内容。");

                    return textResult(sb.toString());
                } catch (IOException e) {
                    return errorResult("文件转换失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * dlqq_convert_office_file - 将Office文件从一种格式转换为另一种格式
     */
    private void registerConvertOfficeFileTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sourceProp = new HashMap<>();
        sourceProp.put("type", "string");
        sourceProp.put("description", "源文件路径（支持PDF、DOC、DOCX、XLS、XLSX、PPT、PPTX、MD、TXT）");
        properties.put("source_path", sourceProp);

        Map<String, Object> formatProp = new HashMap<>();
        formatProp.put("type", "string");
        formatProp.put("description", "目标格式（docx/xlsx/pptx/md/txt，pdf暂不支持）");
        properties.put("target_format", formatProp);

        Map<String, Object> outputProp = new HashMap<>();
        outputProp.put("type", "string");
        outputProp.put("description", "输出路径（可选，默认保存到.dlqq/cache目录）");
        properties.put("output_path", outputProp);

        Map<String, Object> startPageProp = new HashMap<>();
        startPageProp.put("type", "integer");
        startPageProp.put("description", "起始页码/段落/Sheet/幻灯片索引（可选，从1开始）");
        properties.put("start_page", startPageProp);

        Map<String, Object> endPageProp = new HashMap<>();
        endPageProp.put("type", "integer");
        endPageProp.put("description", "结束页码/段落/Sheet/幻灯片索引（可选，包含该页）");
        properties.put("end_page", endPageProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("source_path", "target_format"));

        server.addTool("dlqq_convert_office_file",
            "将Office文件从一种格式转换为另一种格式。支持: PDF/DOC/DOCX/XLS/XLSX/PPT/PPTX/MD/TXT → DOCX/XLSX/PPTX/MD/TXT。支持分页/分段处理。输出路径可选，默认为.dlqq/cache目录。",
            inputSchema,
            arguments -> {
                String sourcePath = (String) arguments.get("source_path");
                String targetFormat = (String) arguments.get("target_format");
                String outputPath = (String) arguments.get("output_path");
                Integer startPage = null;
                Integer endPage = null;
                
                if (arguments.get("start_page") != null) {
                    startPage = ((Number) arguments.get("start_page")).intValue();
                }
                if (arguments.get("end_page") != null) {
                    endPage = ((Number) arguments.get("end_page")).intValue();
                }

                if (sourcePath == null || sourcePath.trim().isEmpty()) {
                    return errorResult("源文件路径不能为空");
                }

                if (targetFormat == null || targetFormat.trim().isEmpty()) {
                    return errorResult("目标格式不能为空");
                }

                try {
                    Map<String, Object> result = OfficeTools.convertOfficeFile(sourcePath, targetFormat, outputPath, startPage, endPage);

                    StringBuilder sb = new StringBuilder();
                    sb.append("✓ Office文件格式转换成功\n");
                    sb.append("===================\n\n");
                    sb.append("源文件: ").append(result.get("sourcePath")).append("\n");
                    sb.append("源格式: ").append(result.get("sourceFormat")).append("\n");
                    sb.append("目标格式: ").append(result.get("targetFormat")).append("\n");
                    if (startPage != null || endPage != null) {
                        sb.append("处理范围: ").append(startPage != null ? startPage : "起始").append("-").append(endPage != null ? endPage : "末尾").append("\n");
                    }
                    sb.append("\n输出文件: ").append(result.get("outputPath")).append("\n");
                    sb.append("文件大小: ").append(result.get("size")).append(" 字节\n");

                    return textResult(sb.toString());
                } catch (IOException e) {
                    return errorResult("文件格式转换失败: " + e.getMessage());
                }
            }
        );
    }

    // ==================== 压缩/解压工具 ====================

    /**
     * dlqq_zip - 压缩文件或目录
     */
    private void registerZipTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> sourcesProp = new HashMap<>();
        sourcesProp.put("type", "array");
        sourcesProp.put("description", "源文件/目录路径列表，支持多个文件或目录");
        properties.put("source_paths", sourcesProp);

        Map<String, Object> outputProp = new HashMap<>();
        outputProp.put("type", "string");
        outputProp.put("description", "输出压缩文件路径（可选，默认保存到 .dlqq/cache 目录）");
        properties.put("output_path", outputProp);

        Map<String, Object> formatProp = new HashMap<>();
        formatProp.put("type", "string");
        formatProp.put("description", "压缩格式（可选，默认 zip）：zip 为标准压缩包；zstd 为单文件 .zst，"
            + "目录或多个源会自动打包为 .tar.zst");
        formatProp.put("enum", Arrays.asList("zip", "zstd", "zst", "tar.zst", "tzst"));
        properties.put("format", formatProp);

        Map<String, Object> passwordProp = new HashMap<>();
        passwordProp.put("type", "string");
        passwordProp.put("description", "压缩密码（可选，为空表示不加密）");
        properties.put("password", passwordProp);

        Map<String, Object> levelProp = new HashMap<>();
        levelProp.put("type", Arrays.asList("integer", "string"));
        levelProp.put("description", "压缩级别（1-9，默认5，1最快压缩率最低，9最慢压缩率最高）");
        properties.put("compression_level", levelProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("source_paths"));

        server.addTool("dlqq_zip",
            "压缩文件或目录。支持 zip 与 zstd 两种格式。" 
            + "zip：多文件/目录压缩、密码保护、压缩级别 1-9。"
            + "zstd：单个文件生成 .zst；目录或多个源自动打包为 .tar.zst（密码不适用）。"
            + "默认输出到 .dlqq/cache 目录。",
            inputSchema,
            arguments -> {
                @SuppressWarnings("unchecked")
                List<String> sourcePaths = (List<String>) arguments.get("source_paths");
                String outputPath = (String) arguments.get("output_path");
                String format = (String) arguments.get("format");
                String password = (String) arguments.get("password");
                Integer compressionLevel = parseInteger(arguments.get("compression_level"), 5);

                if (sourcePaths == null || sourcePaths.isEmpty()) {
                    return errorResult("源文件路径不能为空");
                }

                try {
                    Map<String, Object> result = ZipTools.zipFiles(sourcePaths, outputPath, format, password, compressionLevel);

                    StringBuilder sb = new StringBuilder();
                    sb.append("✓ 压缩成功\n");
                    sb.append("===================\n\n");
                    sb.append("输出文件: ").append(result.get("outputPath")).append("\n");
                    sb.append("格式: ").append(result.get("format")).append("\n");
                    Object originalSize = result.get("originalSize");
                    if (originalSize instanceof Long) {
                        sb.append("原始大小: ").append(formatFileSize((Long) originalSize)).append("\n");
                    }
                    Object compressedSize = result.get("compressedSize");
                    if (compressedSize instanceof Long) {
                        sb.append("压缩后大小: ").append(formatFileSize((Long) compressedSize)).append("\n");
                    }
                    Object ratio = result.get("compressionRatio");
                    if (ratio != null) {
                        sb.append("压缩率: ").append(ratio).append("\n");
                    }
                    Object fileCount = result.get("fileCount");
                    if (fileCount != null) {
                        sb.append("文件数: ").append(fileCount).append("\n");
                    }
                    Object sourceCount = result.get("sourceCount");
                    if (sourceCount != null) {
                        sb.append("源数量: ").append(sourceCount).append("\n");
                    }
                    Object passwordProtected = result.get("passwordProtected");
                    if (passwordProtected != null) {
                        sb.append("密码保护: ").append(((Boolean) passwordProtected) ? "是" : "否").append("\n");
                    }

                    return textResult(sb.toString());
                } catch (IOException e) {
                    return errorResult("压缩失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * dlqq_unzip - 解压ZIP文件
     */
    private void registerUnzipTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> zipPathProp = new HashMap<>();
        zipPathProp.put("type", "string");
        zipPathProp.put("description", "ZIP文件路径");
        properties.put("zip_path", zipPathProp);

        Map<String, Object> outputDirProp = new HashMap<>();
        outputDirProp.put("type", "string");
        outputDirProp.put("description", "解压输出目录（可选，默认解压到 .dlqq/cache 目录下的同名文件夹）");
        properties.put("output_dir", outputDirProp);

        Map<String, Object> passwordProp = new HashMap<>();
        passwordProp.put("type", "string");
        passwordProp.put("description", "解压密码（可选，如果ZIP有密码则需要提供）");
        properties.put("password", passwordProp);

        Map<String, Object> detectModeProp = new HashMap<>();
        detectModeProp.put("type", "integer");
        detectModeProp.put("description", "格式判定模式（可选，默认 0）："
            + "0=严格按后缀名，实际格式与后缀不符或后缀不支持则报错；"
            + "1=先按后缀名，不符或识别不出时自动探测真实格式；"
            + "2=忽略后缀名，只按文件内容探测，探测不出则报错");
        properties.put("detect_mode", detectModeProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("zip_path"));

        server.addTool("dlqq_unzip",
            "解压压缩文件（支持 zip/7z/tar/gz/bz2/xz/zstd 等）。"
            + "默认解压到 .dlqq/cache 目录下的同名文件夹中。"
            + "detect_mode 控制格式判定：0 严格按后缀、1 后缀失败再探测、2 只看文件内容。",
            inputSchema,
            arguments -> {
                String zipPath = (String) arguments.get("zip_path");
                String outputDir = (String) arguments.get("output_dir");
                String password = (String) arguments.get("password");
                Integer detectMode = parseInteger(arguments.get("detect_mode"), 0);

                if (zipPath == null || zipPath.trim().isEmpty()) {
                    return errorResult("ZIP文件路径不能为空");
                }

                try {
                    Map<String, Object> result = ZipTools.unzipFile(zipPath, outputDir, password, detectMode);

                    StringBuilder sb = new StringBuilder();
                    sb.append("✓ 解压成功\n");
                    sb.append("===================\n\n");
                    sb.append("ZIP文件: ").append(result.get("zipPath")).append("\n");
                    sb.append("输出目录: ").append(result.get("outputDir")).append("\n");
                    sb.append("文件数: ").append(result.get("fileCount")).append("\n");
                    sb.append("解压后大小: ").append(formatFileSize((Long) result.get("extractedSize"))).append("\n\n");

                    @SuppressWarnings("unchecked")
                    List<String> files = (List<String>) result.get("files");
                    sb.append("文件列表（前20个）:\n");
                    for (String file : files) {
                        sb.append("  - ").append(file).append("\n");
                    }
                    if ((Boolean) result.get("hasMoreFiles")) {
                        sb.append("  ... 还有更多文件\n");
                    }

                    return textResult(sb.toString());
                } catch (IOException e) {
                    return errorResult("解压失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 格式化文件大小显示
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    // ==================== BOM 控制工具 ====================

    /**
     * 注册 dlqq_add_unicode_bom - 为文件或目录添加 UTF-8 BOM
     */
    private void registerAddUnicodeBomTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件或目录路径（支持绝对路径和相对路径）");
        properties.put("path", pathProp);

        Map<String, Object> isDirProp = new HashMap<>();
        isDirProp.put("type", "boolean");
        isDirProp.put("description", "是否为目录模式（默认为false，表示处理单个文件）");
        properties.put("is_directory", isDirProp);

        Map<String, Object> extensionsProp = new HashMap<>();
        extensionsProp.put("type", "array");
        extensionsProp.put("description", "文件扩展名列表（如 [\"java\", \"txt\"]），仅在目录模式下有效，为空则处理所有文件");
        properties.put("file_extensions", extensionsProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("path"));

        server.addTool("dlqq_add_unicode_bom",
            "为指定文件或目录添加 UTF-8 BOM（字节顺序标记）。如果文件已有 BOM 则跳过，如果文件不存在则创建带 BOM 的新文件。目录模式下会递归处理所有文件，遵循 AI 规则（.aiignore/.aiwriterules/.aireadrules）过滤文件。",
            inputSchema,
            arguments -> {
                String path = (String) arguments.get("path");
                Boolean isDirectory = parseBoolean(arguments.get("is_directory"), false);
                @SuppressWarnings("unchecked")
                List<String> extensions = (List<String>) arguments.get("file_extensions");

                if (path == null || path.trim().isEmpty()) {
                    return errorResult("路径不能为空");
                }

                try {
                    if (isDirectory != null && isDirectory) {
                        // 目录模式
                        String result = FileTools.addUnicodeBomInDir(path, extensions);
                        return textResult(result);
                    } else {
                        // 单文件模式
                        String result = FileTools.addUnicodeBom(path);
                        return textResult(result);
                    }
                } catch (Exception e) {
                    return errorResult("添加 BOM 失败: " + e.getMessage());
                }
            }
        );
    }

    /**
     * 注册 dlqq_remove_unicode_bom - 移除文件或目录的 UTF-8 BOM
     */
    private void registerRemoveUnicodeBomTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "文件或目录路径（支持绝对路径和相对路径）");
        properties.put("path", pathProp);

        Map<String, Object> isDirProp = new HashMap<>();
        isDirProp.put("type", "boolean");
        isDirProp.put("description", "是否为目录模式（默认为false，表示处理单个文件）");
        properties.put("is_directory", isDirProp);

        Map<String, Object> extensionsProp = new HashMap<>();
        extensionsProp.put("type", "array");
        extensionsProp.put("description", "文件扩展名列表（如 [\"java\", \"txt\"]），仅在目录模式下有效，为空则处理所有文件");
        properties.put("file_extensions", extensionsProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Arrays.asList("path"));

        server.addTool("dlqq_remove_unicode_bom",
            "移除指定文件或目录的 UTF-8 BOM（字节顺序标记）。如果文件没有 BOM 则跳过。目录模式下会递归处理所有文件，遵循 AI 规则（.aiignore/.aiwriterules/.aireadrules）过滤文件。",
            inputSchema,
            arguments -> {
                String path = (String) arguments.get("path");
                Boolean isDirectory = parseBoolean(arguments.get("is_directory"), false);
                @SuppressWarnings("unchecked")
                List<String> extensions = (List<String>) arguments.get("file_extensions");

                if (path == null || path.trim().isEmpty()) {
                    return errorResult("路径不能为空");
                }

                try {
                    if (isDirectory != null && isDirectory) {
                        // 目录模式
                        String result = FileTools.removeUnicodeBomInDir(path, extensions);
                        return textResult(result);
                    } else {
                        // 单文件模式
                        String result = FileTools.removeUnicodeBom(path);
                        return textResult(result);
                    }
                } catch (Exception e) {
                    return errorResult("移除 BOM 失败: " + e.getMessage());
                }
            }
        );
    }

}
