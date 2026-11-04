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

    /**
     * 注册命令执行工具
     */
    public static void registerTools(McpServer server) {
        registerRunCommandTool(server);
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
     * 显示用户确认对话框
     */
    private static boolean showApprovalDialog(String command, String workingDir, int timeout, boolean noWait) {
        // 使用 CountDownLatch 等待用户点击按钮（invokeAndWait 在 setVisible 后立即返回，无法等待用户操作）
        final boolean[] result = { false };
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            SwingUtilities.invokeLater(() -> {
                // 创建 JFrame 作为主窗口
                JFrame frame = new JFrame("命令执行确认");
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

                // 超时时间
                JPanel timeoutPanel = new JPanel(new BorderLayout());
                timeoutPanel.add(new JLabel("超时时间: "), BorderLayout.WEST);
                timeoutPanel.add(new JLabel(noWait ? "不适用（后台启动）" : timeout + " 秒"), BorderLayout.CENTER);
                infoPanel.add(timeoutPanel);

                // 执行方式
                JPanel modePanel = new JPanel(new BorderLayout());
                modePanel.add(new JLabel("执行方式: "), BorderLayout.WEST);
                modePanel.add(new JLabel(noWait ? "后台启动（立即返回，不等待结束）" : "前台执行（等待命令结束）"), BorderLayout.CENTER);
                infoPanel.add(modePanel);

                mainPanel.add(infoPanel, BorderLayout.CENTER);

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
                mainPanel.add(warningPanel, BorderLayout.SOUTH);

                // 按钮面板
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
                JButton cancelButton = new JButton("取消");
                cancelButton.setPreferredSize(new Dimension(100, 30));
                JButton executeButton = new JButton("执行命令");
                executeButton.setPreferredSize(new Dimension(100, 30));
                executeButton.setBackground(new Color(0, 120, 215));
                executeButton.setForeground(Color.WHITE);
                executeButton.setFocusPainted(false);

                cancelButton.addActionListener(e -> {
                    result[0] = false;
                    frame.dispose();
                    latch.countDown();
                });

                executeButton.addActionListener(e -> {
                    result[0] = true;
                    frame.dispose();
                    latch.countDown();
                });

                // 窗口被关闭（点X）时按取消处理
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        latch.countDown();
                    }
                });

                buttonPanel.add(cancelButton);
                buttonPanel.add(executeButton);
                mainPanel.add(buttonPanel, BorderLayout.SOUTH);

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

                // 显示窗口
                frame.setVisible(true);
            });

            // 阻塞等待用户点击（最多等10分钟，避免永久阻塞）
            latch.await(10, TimeUnit.MINUTES);
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
        try {
            // 构建 ProcessBuilder
            ProcessBuilder pb;

            // 检测操作系统
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows 使用 cmd /c
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                // Linux/Mac 使用 sh -c
                pb = new ProcessBuilder("sh", "-c", command);
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

            // no_wait：后台启动，输出写入日志文件，立即返回
            if (noWait) {
                File logDir = new File(Workspace.getRoot().toFile(), ".dlqq" + File.separator + "cache");
                if (!logDir.exists() && !logDir.mkdirs()) {
                    return createErrorResult("无法创建日志目录: " + logDir.getAbsolutePath());
                }
                File logFile = new File(logDir, "run_command_" + System.currentTimeMillis() + ".log");
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.to(logFile));
                Process process = pb.start();
                StringBuilder r = new StringBuilder();
                r.append("【命令已在后台启动】\n");
                r.append("命令: ").append(command).append("\n");
                if (workingDir != null && !workingDir.isEmpty()) {
                    r.append("工作目录: ").append(workingDir).append("\n");
                }
                r.append("进程号(PID): ").append(process.pid()).append("\n");
                r.append("日志文件: ").append(logFile.getAbsolutePath()).append("\n");
                r.append("说明: 已立即返回，未等待命令结束；停止可用 taskkill /PID ").append(process.pid()).append(" /T /F。");
                return createSuccessResult(r.toString());
            }

            // 合并错误流
            pb.redirectErrorStream(true);

            // 启动进程
            Process process = pb.start();

            // 使用 Future 来超时控制
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<StringBuilder> future = executor.submit(() -> {
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
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

            return createSuccessResult(result.toString());

        } catch (IOException e) {
            return createErrorResult("执行命令失败: " + e.getMessage());
        } catch (Exception e) {
            return createErrorResult("未知错误: " + e.getMessage());
        }
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
