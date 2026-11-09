package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.McpSchema.CallToolResult;
import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.McpSchema.ToolContent;
import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.McpSchema.ToolInputSchema;
import com.donglouqiaoque.dlqqmcpserver.mcp.server.McpServer;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * 命令执行工具 - 执行命令行指令（需要用户审批）
 */
public class RunCommandTools {

    // 默认超时时间（秒）
    private static final int DEFAULT_TIMEOUT = 60;
    // 最大超时时间（秒）
    private static final int MAX_TIMEOUT = 600;
    // 确认框等待用户审批的超时时间（秒）：到点自动取消，不执行命令
    private static final int APPROVAL_TIMEOUT_SECONDS = 60;

    /** 用 cmd.exe /c 执行（包内可见：BackgroundPrograms 也需要用来区分 shell） */
    static final String SHELL_CMD = "cmd";
    /** 用 Windows PowerShell 5.1（powershell.exe）执行 */
    static final String SHELL_POWERSHELL = "powershell";
    /** 用 PowerShell 7+（pwsh.exe）执行 */
    static final String SHELL_PWSH = "pwsh";

    /**
     * 注册命令执行工具
     */
    public static void registerTools(McpServer server) {
        registerRunCommandTool(server);
        registerShellTool(server, "dlqq_run_powershell", SHELL_POWERSHELL,
                "Windows PowerShell 5.1",
                "执行 PowerShell 脚本（powershell.exe，Windows PowerShell 5.1，需要用户审批）。执行前会弹出确认对话框，用户可以选择执行或取消。支持设置工作目录、超时时间，以及 no_wait（后台启动、立即返回，不等待命令结束）。",
                "要执行的 PowerShell 脚本，如: \"Get-ChildItem C:\\\"、\"Get-Process | Select-Object -First 5\"");
        registerShellTool(server, "dlqq_run_pwsh", SHELL_PWSH,
                "PowerShell 7",
                "执行 PowerShell 脚本（pwsh.exe，PowerShell 7+，需要用户审批）。执行前会弹出确认对话框，用户可以选择执行或取消。支持设置工作目录、超时时间，以及 no_wait（后台启动、立即返回，不等待命令结束）。",
                "要执行的 PowerShell 脚本，如: \"Get-ChildItem C:\\\"、\"$PSVersionTable.PSVersion\"");
        registerBackgroundTools(server);
    }

    /** 后台程序管理工具的动作 */
    private enum BackgroundAction {
        LIST, LOG, KILL, CLEAR, REFRESH
    }

    /**
     * 注册后台程序管理工具（cmd / powershell / pwsh 各 5 个）
     */
    private static void registerBackgroundTools(McpServer server) {
        registerBackgroundTool(server, "dlqq_get_command_list", SHELL_CMD, "cmd", BackgroundAction.LIST);
        registerBackgroundTool(server, "dlqq_get_command_log", SHELL_CMD, "cmd", BackgroundAction.LOG);
        registerBackgroundTool(server, "dlqq_get_command_kill", SHELL_CMD, "cmd", BackgroundAction.KILL);
        registerBackgroundTool(server, "dlqq_get_command_clear", SHELL_CMD, "cmd", BackgroundAction.CLEAR);
        registerBackgroundTool(server, "dlqq_get_command_refresh", SHELL_CMD, "cmd", BackgroundAction.REFRESH);

        registerBackgroundTool(server, "dlqq_get_powershell_list", SHELL_POWERSHELL, "PowerShell 5.1", BackgroundAction.LIST);
        registerBackgroundTool(server, "dlqq_get_powershell_log", SHELL_POWERSHELL, "PowerShell 5.1", BackgroundAction.LOG);
        registerBackgroundTool(server, "dlqq_get_powershell_kill", SHELL_POWERSHELL, "PowerShell 5.1", BackgroundAction.KILL);
        registerBackgroundTool(server, "dlqq_get_powershell_clear", SHELL_POWERSHELL, "PowerShell 5.1", BackgroundAction.CLEAR);
        registerBackgroundTool(server, "dlqq_get_powershell_refresh", SHELL_POWERSHELL, "PowerShell 5.1", BackgroundAction.REFRESH);

        registerBackgroundTool(server, "dlqq_get_pwsh_list", SHELL_PWSH, "PowerShell 7", BackgroundAction.LIST);
        registerBackgroundTool(server, "dlqq_get_pwsh_log", SHELL_PWSH, "PowerShell 7", BackgroundAction.LOG);
        registerBackgroundTool(server, "dlqq_get_pwsh_kill", SHELL_PWSH, "PowerShell 7", BackgroundAction.KILL);
        registerBackgroundTool(server, "dlqq_get_pwsh_clear", SHELL_PWSH, "PowerShell 7", BackgroundAction.CLEAR);
        registerBackgroundTool(server, "dlqq_get_pwsh_refresh", SHELL_PWSH, "PowerShell 7", BackgroundAction.REFRESH);
    }

    /**
     * 注册一个后台程序管理工具
     */
    private static void registerBackgroundTool(McpServer server, String toolName, String shell,
                                               String shellLabel, BackgroundAction action) {
        boolean needId = action == BackgroundAction.LOG || action == BackgroundAction.KILL;
        Map<String, Object> properties = new HashMap<>();
        if (needId) {
            Map<String, Object> idProp = new HashMap<>();
            idProp.put("type", "string");
            idProp.put("description", "后台程序 ID（从列表工具获取，如 command-1、pwsh-2）");
            properties.put("id", idProp);
        }
        ToolInputSchema inputSchema = new ToolInputSchema(properties,
            needId ? Collections.singletonList("id") : Collections.emptyList());

        String description;
        switch (action) {
            case LIST:
                description = "列出由 " + shellLabel + " 启动的后台程序（运行中 + 已结束）及其状态";
                break;
            case LOG:
                description = "获取 " + shellLabel + " 后台程序的输出日志（需提供 id）";
                break;
            case KILL:
                description = "终止指定的 " + shellLabel + " 后台程序（需提供 id）";
                break;
            case CLEAR:
                description = "清除已结束的 " + shellLabel + " 后台程序记录与日志（运行中的不会被清除）";
                break;
            default:
                description = "刷新 " + shellLabel + " 后台程序状态（同步用户手动关闭窗口等外部变化）";
                break;
        }

        server.addTool(toolName, description, inputSchema, arguments -> {
            String id = null;
            if (arguments != null && arguments.get("id") != null) {
                id = String.valueOf(arguments.get("id")).trim();
            }
            switch (action) {
                case LIST:
                    return createSuccessResult(BackgroundPrograms.list(shell));
                case CLEAR:
                    return createSuccessResult(BackgroundPrograms.clear(shell));
                case REFRESH:
                    return createSuccessResult(BackgroundPrograms.refresh(shell));
                case LOG:
                case KILL:
                    if (id == null || id.isEmpty()) {
                        return createErrorResult("缺少必需参数: id");
                    }
                    String result = action == BackgroundAction.LOG
                        ? BackgroundPrograms.log(shell, id)
                        : BackgroundPrograms.kill(shell, id);
                    return result.startsWith("未找到") ? createErrorResult(result) : createSuccessResult(result);
                default:
                    return createErrorResult("不支持的操作");
            }
        });
    }

    /**
     * 注册 dlqq_run_command 工具
     */
    private static void registerRunCommandTool(McpServer server) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> commandProp = new HashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", "要执行的命令行指令，如: \"echo Hello World\", \"dir\", \"java -version\"");
        properties.put("command", commandProp);

        Map<String, Object> workingDirProp = new HashMap<>();
        workingDirProp.put("type", "string");
        workingDirProp.put("description", "工作目录（可选），命令将在该目录下执行");
        properties.put("working_dir", workingDirProp);

        Map<String, Object> timeoutProp = new HashMap<>();
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "超时时间（秒），默认60秒，最大600秒；no_wait=true 时该参数无效");
        properties.put("timeout", timeoutProp);

        Map<String, Object> noWaitProp = new HashMap<>();
        noWaitProp.put("type", "boolean");
        noWaitProp.put("description", "是否不等待命令执行结束（默认 false）。true = 命令在后台启动并立即返回 PID 与日志路径，适用于启动服务等常驻命令");
        properties.put("no_wait", noWaitProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.singletonList("command"));

        server.addTool("dlqq_run_command",
            "执行命令行指令（需要用户审批）。执行前会弹出确认对话框，用户可以选择执行或取消。支持设置工作目录、超时时间，以及 no_wait（后台启动、立即返回，不等待命令结束）。",
            inputSchema,
            params -> {
                Object commandObj = params.get("command");
                if (commandObj == null) {
                    return createErrorResult("缺少必需参数: command");
                }
                String command = String.valueOf(commandObj).trim();
                if (command.isEmpty()) {
                    return createErrorResult("命令不能为空");
                }

                String workingDir = null;
                Object workingDirObj = params.get("working_dir");
                if (workingDirObj != null) {
                    workingDir = String.valueOf(workingDirObj).trim();
                    if (workingDir.isEmpty()) {
                        workingDir = null;
                    }
                }
                // 统一解析为绝对路径：弹窗显示和实际执行都用同一路径
                if (workingDir != null) {
                    workingDir = Workspace.resolve(workingDir).toString();
                }

                int timeout = DEFAULT_TIMEOUT;
                Object timeoutObj = params.get("timeout");
                if (timeoutObj != null) {
                    try {
                        timeout = Integer.parseInt(String.valueOf(timeoutObj));
                        if (timeout < 1) {
                            timeout = DEFAULT_TIMEOUT;
                        } else if (timeout > MAX_TIMEOUT) {
                            timeout = MAX_TIMEOUT;
                        }
                    } catch (NumberFormatException e) {
                        // 使用默认值
                    }
                }

                boolean noWait = false;
                Object noWaitObj = params.get("no_wait");
                if (noWaitObj != null) {
                    noWait = Boolean.parseBoolean(String.valueOf(noWaitObj).trim());
                }

                // 显示确认对话框
                boolean approved = showApprovalDialog(command, workingDir, timeout, noWait);

                if (!approved) {
                    return createErrorResult("用户取消了命令执行");
                }

                // 执行命令（no_wait=true 时后台启动并立即返回）
                return executeCommand(command, workingDir, timeout, noWait);
            }
        );
    }
    /**
     * 注册使用指定 shell 的命令执行工具（powershell / pwsh）。
     * 与 dlqq_run_command 逻辑一致，仅执行器不同。
     */
    private static void registerShellTool(McpServer server, String toolName, String shell,
                                          String shellLabel, String toolDescription,
                                          String commandDescription) {
        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> commandProp = new HashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", commandDescription);
        properties.put("command", commandProp);

        Map<String, Object> workingDirProp = new HashMap<>();
        workingDirProp.put("type", "string");
        workingDirProp.put("description", "工作目录（可选），命令将在该目录下执行");
        properties.put("working_dir", workingDirProp);

        Map<String, Object> timeoutProp = new HashMap<>();
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "超时时间（秒），默认60秒，最大600秒；no_wait=true 时该参数无效");
        properties.put("timeout", timeoutProp);

        Map<String, Object> noWaitProp = new HashMap<>();
        noWaitProp.put("type", "boolean");
        noWaitProp.put("description", "是否不等待命令执行结束（默认 false）。true = 命令在后台启动并立即返回 PID 与日志路径");
        properties.put("no_wait", noWaitProp);

        ToolInputSchema inputSchema = new ToolInputSchema(properties, Collections.singletonList("command"));

        server.addTool(toolName, toolDescription, inputSchema, params -> {
            Object commandObj = params.get("command");
            if (commandObj == null) {
                return createErrorResult("缺少必需参数: command");
            }
            String command = String.valueOf(commandObj).trim();
            if (command.isEmpty()) {
                return createErrorResult("命令不能为空");
            }

            String workingDir = null;
            Object workingDirObj = params.get("working_dir");
            if (workingDirObj != null) {
                workingDir = String.valueOf(workingDirObj).trim();
                if (workingDir.isEmpty()) {
                    workingDir = null;
                }
            }
            if (workingDir != null) {
                workingDir = Workspace.resolve(workingDir).toString();
            }

            int timeout = DEFAULT_TIMEOUT;
            Object timeoutObj = params.get("timeout");
            if (timeoutObj != null) {
                try {
                    timeout = Integer.parseInt(String.valueOf(timeoutObj));
                    if (timeout < 1) {
                        timeout = DEFAULT_TIMEOUT;
                    } else if (timeout > MAX_TIMEOUT) {
                        timeout = MAX_TIMEOUT;
                    }
                } catch (NumberFormatException e) {
                    // 使用默认值
                }
            }

            boolean noWait = false;
            Object noWaitObj = params.get("no_wait");
            if (noWaitObj != null) {
                noWait = Boolean.parseBoolean(String.valueOf(noWaitObj).trim());
            }

            boolean approved = showApprovalDialog(command, workingDir, timeout, noWait, shellLabel);
            if (!approved) {
                return createErrorResult("用户取消了命令执行");
            }
            return executeCommand(command, workingDir, timeout, noWait, shell);
        });
    }

    /**
     * 显示用户确认对话框
     */
    private static boolean showApprovalDialog(String command, String workingDir, int timeout, boolean noWait) {
        return showApprovalDialog(command, workingDir, timeout, noWait, null);
    }

    private static boolean showApprovalDialog(String command, String workingDir, int timeout, boolean noWait,
                                              String shellLabel) {
        // 使用 CountDownLatch 等待用户点击按钮（invokeAndWait 在 setVisible 后立即返回，无法等待用户操作）
        final boolean[] result = { false };
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            SwingUtilities.invokeLater(() -> {
                // 创建 JFrame 作为主窗口
                JFrame frame = new JFrame(shellLabel == null ? "命令执行确认" : "命令执行确认 - " + shellLabel);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setResizable(true);
                DialogUi.applyIcon(frame, "RunCommandTools");

                // 主面板
                JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
                mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

                // 警告图标和标题
                JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                JLabel warningLabel = new JLabel("⚠️ AI 请求执行系统命令");
                warningLabel.setFont(new Font("Dialog", Font.BOLD, 16));
                warningLabel.setForeground(new Color(220, 140, 0));
                headerPanel.add(warningLabel);
                mainPanel.add(headerPanel, BorderLayout.NORTH);

                // 命令信息面板
                JPanel infoPanel = new JPanel(new GridLayout(0, 1, 5, 5));
                infoPanel.setBorder(BorderFactory.createTitledBorder("命令信息"));

                // 命令
                JPanel commandPanel = new JPanel(new BorderLayout());
                commandPanel.add(new JLabel("命令: "), BorderLayout.WEST);
                JTextArea commandArea = new JTextArea(command);
                commandArea.setEditable(false);
                commandArea.setLineWrap(true);
                commandArea.setWrapStyleWord(true);
                commandArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
                commandArea.setRows(6);
                commandArea.setColumns(60);
                JScrollPane commandScroll = new JScrollPane(commandArea);
                commandScroll.setPreferredSize(new Dimension(640, 160));
                commandPanel.add(commandScroll, BorderLayout.CENTER);
                infoPanel.add(commandPanel);

                // 工作目录
                if (workingDir != null && !workingDir.isEmpty()) {
                    JPanel dirPanel = new JPanel(new BorderLayout());
                    dirPanel.add(new JLabel("工作目录: "), BorderLayout.WEST);
                    JTextField dirField = new JTextField(workingDir);
                    dirField.setEditable(false);
                    dirPanel.add(dirField, BorderLayout.CENTER);
                    infoPanel.add(dirPanel);
                }

                // 命令执行超时（批准后才开始计时）
                JPanel timeoutPanel = new JPanel(new BorderLayout());
                timeoutPanel.add(new JLabel("命令超时: "), BorderLayout.WEST);
                timeoutPanel.add(new JLabel(noWait
                        ? "不适用（后台启动，不等待结束）"
                        : timeout + " 秒（点『执行命令』后开始计时）"), BorderLayout.CENTER);
                infoPanel.add(timeoutPanel);

                // 执行方式
                JPanel modePanel = new JPanel(new BorderLayout());
                modePanel.add(new JLabel("执行方式: "), BorderLayout.WEST);
                modePanel.add(new JLabel(noWait ? "后台启动（立即返回，不等待结束）" : "前台执行（等待命令结束）"), BorderLayout.CENTER);
                infoPanel.add(modePanel);

                // 让各行保持自身高度，避免窗口拉高时字段被均匀撑开、行间出现大片空白
                JPanel infoWrapper = new JPanel(new BorderLayout());
                infoWrapper.add(infoPanel, BorderLayout.NORTH);
                mainPanel.add(infoWrapper, BorderLayout.CENTER);

                // 警告信息
                JPanel warningPanel = new JPanel(new BorderLayout());
                warningPanel.setBackground(new Color(255, 248, 220));
                warningPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 200, 100)),
                    BorderFactory.createEmptyBorder(10, 10, 10, 10)
                ));
                JLabel warningText = new JLabel("⚠️ 警告：执行未知命令可能对系统造成风险，请确认该命令是安全的。");
                warningText.setForeground(new Color(180, 100, 0));
                warningPanel.add(warningText, BorderLayout.CENTER);

                // 按钮面板 + 审批倒计时（超时自动取消，不执行命令）
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
                JButton cancelButton = new JButton("取消");
                cancelButton.setPreferredSize(new Dimension(100, 30));
                JButton executeButton = new JButton("执行命令");
                executeButton.setPreferredSize(new Dimension(100, 30));
                executeButton.setBackground(new Color(0, 120, 215));
                executeButton.setForeground(Color.WHITE);
                executeButton.setFocusPainted(false);

                JLabel countdownLabel = new JLabel("审批倒计时: 剩余 " + APPROVAL_TIMEOUT_SECONDS
                        + " 秒（到点自动取消，不执行）");
                countdownLabel.setForeground(new Color(220, 120, 0));
                final int[] remaining = { APPROVAL_TIMEOUT_SECONDS };
                final javax.swing.Timer countdownTimer = new javax.swing.Timer(1000, null);
                countdownTimer.addActionListener(e -> {
                    remaining[0]--;
                    if (remaining[0] <= 0) {
                        countdownTimer.stop();
                        result[0] = false;
                        frame.dispose();
                        latch.countDown();
                        return;
                    }
                    countdownLabel.setText("审批倒计时: 剩余 " + remaining[0] + " 秒（到点自动取消，不执行）");
                });

                cancelButton.addActionListener(e -> {
                    countdownTimer.stop();
                    result[0] = false;
                    frame.dispose();
                    latch.countDown();
                });

                executeButton.addActionListener(e -> {
                    countdownTimer.stop();
                    result[0] = true;
                    frame.dispose();
                    latch.countDown();
                });

                // 窗口被关闭（点X）时按取消处理
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        countdownTimer.stop();
                        latch.countDown();
                    }
                });

                buttonPanel.add(cancelButton);
                buttonPanel.add(executeButton);

                JPanel buttonRow = new JPanel(new BorderLayout(8, 8));
                buttonRow.add(countdownLabel, BorderLayout.WEST);
                buttonRow.add(buttonPanel, BorderLayout.EAST);

                JPanel southPanel = new JPanel(new BorderLayout(8, 8));
                southPanel.add(warningPanel, BorderLayout.NORTH);
                southPanel.add(buttonRow, BorderLayout.SOUTH);
                mainPanel.add(southPanel, BorderLayout.SOUTH);

                // 倒计时的启动时机在窗口显示之后（见下方 setVisible 之后）

                frame.getContentPane().add(mainPanel);

                // 设置默认按钮
                frame.getRootPane().setDefaultButton(cancelButton);

                // 按内容自适应大小并居中
                DialogUi.fit(frame);

                // 命令可能很长，保证窗口不小于一个可读的尺寸
                frame.setMinimumSize(new Dimension(760, 460));
                if (frame.getWidth() < 760 || frame.getHeight() < 460) {
                    frame.setSize(Math.max(frame.getWidth(), 760), Math.max(frame.getHeight(), 460));
                    frame.setLocationRelativeTo(null);
                }

                // 显示窗口（不抢焦点：不打断用户正在输入的内容）
                frame.setAutoRequestFocus(false);
                frame.setVisible(true);

                // 窗口显示后再开始读秒，避免构造期间就开始倒计时
                countdownTimer.start();
            });

            // 阻塞等待用户点击；最多等 APPROVAL_TIMEOUT_SECONDS + 5 秒兜底
            latch.await(APPROVAL_TIMEOUT_SECONDS + 5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            // 如果对话框显示失败，默认拒绝执行
            return false;
        }

        return result[0];
    }

    /**
     * 执行命令
     */
    private static CallToolResult executeCommand(String command, String workingDir, int timeout, boolean noWait) {
        return executeCommand(command, workingDir, timeout, noWait, SHELL_CMD);
    }

    private static CallToolResult executeCommand(String command, String workingDir, int timeout, boolean noWait,
                                                 String shell) {
        try {
            // 构建 ProcessBuilder
            ProcessBuilder pb;

            // 检测操作系统
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                if (SHELL_POWERSHELL.equals(shell)) {
                    // Windows PowerShell 5.1
                    pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                            "-Command", command);
                } else if (SHELL_PWSH.equals(shell)) {
                    // PowerShell 7+
                    pb = new ProcessBuilder("pwsh.exe", "-NoProfile", "-Command", command);
                } else {
                    // 默认 cmd
                    pb = new ProcessBuilder("cmd", "/c", command);
                }
            } else {
                if (SHELL_POWERSHELL.equals(shell) || SHELL_PWSH.equals(shell)) {
                    pb = new ProcessBuilder("pwsh", "-NoProfile", "-Command", command);
                } else {
                    // Linux/Mac 使用 sh -c
                    pb = new ProcessBuilder("sh", "-c", command);
                }
            }

            // 设置工作目录
            if (workingDir != null && !workingDir.isEmpty()) {
                File dir = Workspace.resolve(workingDir).toFile();
                if (dir.exists() && dir.isDirectory()) {
                    pb.directory(dir);
                } else {
                    return createErrorResult("工作目录不存在: " + workingDir);
                }
            }

            // no_wait：后台启动，输出同时写入执行窗口与后台日志文件，立即返回
            if (noWait) {
                pb.redirectErrorStream(true);
                Process process = pb.start();
                ChildProcessGuard.track(process);
                final BackgroundPrograms.Entry entry =
                    BackgroundPrograms.register(process, shell, command, workingDir);
                CommandWindow bgWin = CommandWindow.open(shellLabel(shell), command, workingDir, () -> {
                    if (process.isAlive()) {
                        process.descendants().forEach(ProcessHandle::destroyForcibly);
                        process.destroyForcibly();
                    }
                    BackgroundPrograms.markKilled(entry);
                });
                BackgroundPrograms.attach(entry, bgWin);
                if (bgWin != null) {
                    bgWin.showBackground(String.valueOf(process.pid()));
                }
                BackgroundPrograms.startPump(entry);
                StringBuilder r = new StringBuilder();
                r.append("【命令已在后台启动】\n");
                r.append("ID: ").append(entry.id).append("\n");
                r.append("命令: ").append(command).append("\n");
                if (workingDir != null && !workingDir.isEmpty()) {
                    r.append("工作目录: ").append(workingDir).append("\n");
                }
                r.append("进程号(PID): ").append(process.pid()).append("\n");
                r.append("日志文件: ").append(entry.logFile.getAbsolutePath()).append("\n");
                r.append("说明: 已立即返回；关闭窗口会终止该进程；可用 ").append(backgroundToolPrefix(shell))
                    .append("_list / _log / _kill 查看与管理。");
                return createSuccessResult(r.toString());
            }

            // 合并错误流
            pb.redirectErrorStream(true);

            // 启动进程
            Process process = pb.start();
            ChildProcessGuard.track(process);

            // 执行窗口：实时显示命令输出与运行状态；用户关闭窗口时终止命令
            final CommandWindow window = CommandWindow.open(shellLabel(shell), command, workingDir, () -> {
                if (process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            });
            // 控制台输出编码：Windows 下通常是本地编码（如 GBK），不能按 UTF-8 硬解
            final String consoleCharset = consoleCharset();

            // 使用 Future 来超时控制
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<StringBuilder> future = executor.submit(() -> {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), consoleCharset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        if (window != null) {
                            window.append(line + "\n");
                        }
                    }
                }
                return output;
            });

            StringBuilder output;
            boolean timedOut = false;

            try {
                output = future.get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                process.destroyForcibly();
                timedOut = true;
                output = new StringBuilder("命令执行超时（" + timeout + "秒）\n");
                // 尝试获取已输出的内容
                try {
                    output.append(future.get(2, TimeUnit.SECONDS));
                } catch (Exception ex) {
                    // 忽略
                }
            } finally {
                executor.shutdown();
            }

            // 等待进程结束（如果还未结束）
            int exitCode = -1;
            try {
                if (!timedOut) {
                    exitCode = process.waitFor();
                }
            } catch (InterruptedException e) {
                // 忽略
            }

            // 收尾：更新窗口状态、注销子进程
            if (window != null) {
                window.finish(timedOut, exitCode);
            }
            ChildProcessGuard.release(process);

            // 构建结果
            StringBuilder result = new StringBuilder();
            result.append("【命令执行结果】\n");
            result.append("命令: ").append(command).append("\n");
            if (workingDir != null && !workingDir.isEmpty()) {
                result.append("工作目录: ").append(workingDir).append("\n");
            }
            result.append("退出码: ").append(exitCode).append("\n");
            result.append(timedOut ? "状态: 超时\n" : "状态: 完成\n");
            result.append("\n===== 输出 =====\n");
            result.append(output.length() > 0 ? output.toString() : "(无输出)");

            // 非零退出码或超时都视为失败，必须返回错误结果（isError=true），
            // 否则调用方会把它当成正常返回，进而忽略失败。
            if (timedOut || exitCode != 0) {
                return createErrorResult(result.toString());
            }
            return createSuccessResult(result.toString());

        } catch (IOException e) {
            return createErrorResult("执行命令失败: " + e.getMessage());
        } catch (Exception e) {
            return createErrorResult("未知错误: " + e.getMessage());
        }
    }

    /**
     * 执行器显示名（用于窗口标题与结果说明）
     */
    private static String shellLabel(String shell) {
        if (SHELL_POWERSHELL.equals(shell)) {
            return "Windows PowerShell 5.1";
        }
        if (SHELL_PWSH.equals(shell)) {
            return "PowerShell 7";
        }
        return "cmd.exe";
    }

    /**
     * 控制台输出编码：Windows 下命令输出一般是本地编码（如 GBK），
     * 不能按 UTF-8 硬解，否则中文全是乱码。
     */
    private static String consoleCharset() {
        String nativeEncoding = System.getProperty("native.encoding");
        if (nativeEncoding != null && !nativeEncoding.isEmpty()) {
            return nativeEncoding;
        }
        return "UTF-8";
    }

    /**
     * 后台管理工具前缀（dlqq_get_command / dlqq_get_powershell / dlqq_get_pwsh）
     */
    private static String backgroundToolPrefix(String shell) {
        if (SHELL_POWERSHELL.equals(shell)) {
            return "dlqq_get_powershell";
        }
        if (SHELL_PWSH.equals(shell)) {
            return "dlqq_get_pwsh";
        }
        return "dlqq_get_command";
    }

    /**
     * 创建成功结果
     */
    private static CallToolResult createSuccessResult(String content) {
        return new CallToolResult(
            Collections.singletonList(ToolContent.text(content)),
            false
        );
    }

    /**
     * 创建错误结果
     */
    private static CallToolResult createErrorResult(String message) {
        return new CallToolResult(
            Collections.singletonList(ToolContent.text(message)),
            true
        );
    }
}
