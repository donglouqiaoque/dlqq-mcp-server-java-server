package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;

/**
 * 命令执行结果窗口：显示命令、执行器、状态与实时输出。
 *
 * <p>命令结束前显示"执行中"，结束后显示退出码并默认 3 秒后自动关闭；
 * 用户手动关闭窗口时触发 onClose（用于终止仍在运行的进程）。</p>
 */
final class CommandWindow {

    /** 输出区最多保留的字符数，避免超长输出耗尽内存 */
    private static final int MAX_OUTPUT_CHARS = 400_000;

    private final JFrame frame;
    private final JTextArea outputArea;
    private final JLabel statusLabel;
    private final JLabel elapsedLabel;
    private final Timer timer;
    private final long startMillis = System.currentTimeMillis();
    private final Runnable onClose;
    private boolean finished;

    private CommandWindow(String shellLabel, String command, String workingDir, Runnable onClose) {
        this.onClose = onClose;
        frame = new JFrame("命令执行 - " + shellLabel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);
        // 使用项目 logo 作为窗口图标（与确认框保持一致）
        DialogUi.applyIcon(frame, "CommandWindow");
        // 弹出时不抢焦点，避免打断用户正在输入的内容
        frame.setAutoRequestFocus(false);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 顶部：命令信息（不指定背景色，跟随当前主题，避免暗色主题下看不清）
        JPanel head = new JPanel(new BorderLayout(6, 6));

        JTextArea cmdArea = new JTextArea(command);
        cmdArea.setEditable(false);
        cmdArea.setLineWrap(true);
        cmdArea.setWrapStyleWord(true);
        cmdArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        cmdArea.setRows(3);
        JScrollPane cmdScroll = new JScrollPane(cmdArea);
        cmdScroll.setBorder(BorderFactory.createTitledBorder("命令"));
        head.add(cmdScroll, BorderLayout.CENTER);

        JPanel meta = new JPanel(new GridLayout(0, 1, 2, 2));
        meta.add(new JLabel("执行器: " + shellLabel));
        meta.add(new JLabel("工作目录: " + (workingDir == null || workingDir.isEmpty() ? "(默认)" : workingDir)));
        meta.add(new JLabel("开始时间: " + LocalTime.now().withNano(0)));
        head.add(meta, BorderLayout.SOUTH);

        root.add(head, BorderLayout.NORTH);

        // 中部：输出
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane outScroll = new JScrollPane(outputArea);
        outScroll.setBorder(BorderFactory.createTitledBorder("输出"));
        root.add(outScroll, BorderLayout.CENTER);

        // 底部：状态 + 按钮
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        statusLabel = new JLabel("执行中…");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusLabel.setForeground(new Color(230, 150, 30));
        elapsedLabel = new JLabel("已运行 0 秒");
        statusPanel.add(statusLabel);
        statusPanel.add(elapsedLabel);
        bottom.add(statusPanel, BorderLayout.WEST);

        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> frame.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(closeBtn);
        bottom.add(buttons, BorderLayout.EAST);

        root.add(bottom, BorderLayout.SOUTH);

        frame.setContentPane(root);

        // 用户关闭窗口（按钮或标题栏）→ 终止仍在运行的进程
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                timer.stop();
                if (!finished && onClose != null) {
                    onClose.run();
                }
            }
        });

        timer = new Timer(500, e -> {
            long sec = (System.currentTimeMillis() - startMillis) / 1000;
            elapsedLabel.setText("已运行 " + sec + " 秒");
        });
        timer.start();

        frame.setVisible(true);
    }

    /**
     * 创建并显示窗口；无图形环境时返回 null。
     *
     * @param onClose 用户手动关闭窗口时的回调（用于终止仍在运行的命令进程），可为 null
     */
    static CommandWindow open(String shellLabel, String command, String workingDir, Runnable onClose) {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        try {
            return new CommandWindow(shellLabel, command, workingDir, onClose);
        } catch (Exception e) {
            return null;
        }
    }

    /** 追加输出（可在任意线程调用） */
    void append(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (outputArea.getDocument().getLength() > MAX_OUTPUT_CHARS) {
                outputArea.setText(outputArea.getText().substring(MAX_OUTPUT_CHARS / 2));
            }
            outputArea.append(text);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    /** 更新状态文本 */
    void setStatus(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
            statusLabel.setForeground(color);
        });
    }

    /** 命令结束：停止计时并显示最终状态；默认 3 秒后自动关闭窗口 */
    void finish(boolean timedOut, int exitCode) {
        finish(timedOut, exitCode, 3000);
    }

    /**
     * 命令结束：停止计时并显示最终状态。
     *
     * @param autoCloseMillis 大于 0 时在该毫秒数后自动关闭窗口；0 表示不自动关闭
     */
    void finish(boolean timedOut, int exitCode, long autoCloseMillis) {
        if (finished) {
            return;
        }
        finished = true;
        SwingUtilities.invokeLater(() -> {
            timer.stop();
            long sec = (System.currentTimeMillis() - startMillis) / 1000;
            elapsedLabel.setText("耗时 " + sec + " 秒");
            if (timedOut) {
                statusLabel.setText("已超时终止");
                statusLabel.setForeground(new Color(230, 80, 80));
            } else if (exitCode == 0) {
                statusLabel.setText("已完成（退出码 0）");
                statusLabel.setForeground(new Color(90, 200, 90));
            } else {
                statusLabel.setText("已失败（退出码 " + exitCode + "）");
                statusLabel.setForeground(new Color(230, 80, 80));
            }
            if (autoCloseMillis > 0) {
                // 读秒关闭：3、2、1 依次刷新，归零后关闭窗口
                final String baseText = statusLabel.getText();
                final int[] remain = { (int) Math.max(1, autoCloseMillis / 1000) };
                statusLabel.setText(baseText + "，" + remain[0] + " 秒后自动关闭");
                Timer closer = new Timer(1000, null);
                closer.addActionListener(e -> {
                    remain[0]--;
                    if (remain[0] <= 0) {
                        closer.stop();
                        frame.dispose();
                        return;
                    }
                    statusLabel.setText(baseText + "，" + remain[0] + " 秒后自动关闭");
                });
                closer.setRepeats(true);
                closer.start();
            }
        });
    }

    /** 后台启动模式：显示启动信息；窗口保持打开以实时展示输出，进程结束后自动收尾 */
    void showBackground(String pid) {
        append("命令已在后台启动\nPID: " + pid + "\n\n===== 实时输出 =====\n");
        setStatus("后台运行中（PID " + pid + "）", new Color(100, 160, 240));
    }

    void dispose() {
        SwingUtilities.invokeLater(frame::dispose);
    }
}
