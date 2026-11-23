package com.donglouqiaoque.dlqqmcpserver.mcp;

import com.donglouqiaoque.dlqqmcpserver.mcp.tools.LogoLoader;
import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope.SessionInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * MCP 服务端控制台窗口。
 *
 * <p>启动时打开一个普通 {@link JFrame}：展示当前活跃会话（工作区、来源、权限、最后活跃时间），
 * 并可对选中会话设置工作区、资源权限与操作权限。</p>
 *
 * <p>点击标题栏关闭按钮不会退出进程，而是**隐藏到系统托盘**；可从托盘菜单或窗口"文件"菜单
 * 重新显示窗口，或选择"退出服务器"终止 MCP 服务器进程。</p>
 *
 * <p>无图形环境（headless）时不会创建窗口。</p>
 */
public final class ServerConsoleWindow {

    private static final String TITLE = "dlqq-mcp-server 控制台";
    private static final String[] COLUMNS = {"会话键", "工作区", "来源", "资源权限", "操作权限", "最后活跃"};
    private static final String[] RESOURCE_LEVELS = {"chat", "readonly", "workspace", "approval", "highrisk", "full"};
    private static final String[] OPERATION_LEVELS = {"chat", "readonly", "approval", "highrisk", "full"};
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int REFRESH_MILLIS = 2000;

    private static ServerConsoleWindow instance;

    private final JFrame frame = new JFrame(TITLE);
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JTextField workspaceField = new JTextField(46);
    private final JComboBox<String> resourceBox = new JComboBox<>(RESOURCE_LEVELS);
    private final JComboBox<String> operationBox = new JComboBox<>(OPERATION_LEVELS);
    private final JLabel statusLabel = new JLabel("就绪");
    private final JLabel sessionCountLabel = new JLabel("活跃会话: 0");
    private final JLabel workspaceSourceLabel = new JLabel("");

    private Timer refreshTimer;
    private TrayIcon trayIcon;
    private JDialog trayMenuDialog;
    private long trayMenuOpenedAt;

    private ServerConsoleWindow() {
        buildUi();
    }

    /** 启动控制台窗口（headless 时跳过；重复调用只显示已有窗口）。 */
    public static void start() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (instance == null) {
                instance = new ServerConsoleWindow();
                instance.show();
            } else {
                instance.showFromTray();
            }
        });
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(1250, 680);
        frame.setLocationRelativeTo(null);
        LogoLoader.applyTo(frame, "ServerConsole");

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        JMenuItem hideItem = new JMenuItem("隐藏到托盘");
        hideItem.addActionListener(event -> hideToTray());
        JMenuItem exitItem = new JMenuItem("退出服务器");
        exitItem.addActionListener(event -> stopAndExit());
        fileMenu.add(hideItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        top.add(new JLabel("部署目录: " + RuntimeDirs.getAppDir()), gbc);
        gbc.gridx = 1;
        top.add(new JLabel("运行时目录: " + RuntimeDirs.getRoot()), gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        top.add(new JLabel("日志文件: " + RuntimeDirs.getCurrentLogFile()), gbc);
        gbc.gridx = 1;
        top.add(sessionCountLabel, gbc);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                fillFieldsFromSelection();
            }
        });
        JScrollPane scrollPane = new JScrollPane(table);

        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        controls.add(new JLabel("工作区:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        controls.add(workspaceField, c);
        c.gridx = 4;
        c.gridwidth = 1;
        c.weightx = 0;
        JButton applyWorkspace = new JButton("设置选中会话工作区");
        applyWorkspace.addActionListener(event -> applyWorkspace());
        controls.add(applyWorkspace, c);
        c.gridx = 5;
        JButton refreshButton = new JButton("刷新");
        refreshButton.addActionListener(event -> refresh());
        controls.add(refreshButton, c);

        c.gridy = 1;
        c.gridx = 0;
        controls.add(new JLabel("资源权限:"), c);
        c.gridx = 1;
        controls.add(resourceBox, c);
        JButton applyResource = new JButton("设置资源权限");
        applyResource.addActionListener(event -> applyPermission(true));
        c.gridx = 2;
        controls.add(applyResource, c);
        c.gridx = 3;
        controls.add(new JLabel("操作权限:"), c);
        c.gridx = 4;
        controls.add(operationBox, c);
        JButton applyOperation = new JButton("设置操作权限");
        applyOperation.addActionListener(event -> applyPermission(false));
        c.gridx = 5;
        controls.add(applyOperation, c);

        c.gridy = 2;
        c.gridx = 0;
        c.gridwidth = 6;
        c.fill = GridBagConstraints.HORIZONTAL;
        controls.add(workspaceSourceLabel, c);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12F));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.add(statusLabel, BorderLayout.WEST);

        frame.add(top, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(controls, BorderLayout.NORTH);
        south.add(statusBar, BorderLayout.SOUTH);
        frame.add(south, BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                hideToTray();
            }
        });

        refreshTimer = new Timer(REFRESH_MILLIS, event -> refresh());
        refreshTimer.setInitialDelay(REFRESH_MILLIS);
    }

    private void show() {
        refresh();
        frame.setVisible(true);
        refreshTimer.start();
        installTrayIcon();
    }

    /** 隐藏到系统托盘；若托盘不可用则退化为退出进程，避免窗口无法找回。 */
    private void hideToTray() {
        if (trayIcon == null) {
            System.err.println("[ServerConsole] System tray unavailable, terminating MCP server process");
            stopAndExit();
            return;
        }
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        frame.setVisible(false);
        trayIcon.displayMessage(TITLE, "控制台已隐藏到托盘，双击托盘图标可重新打开", TrayIcon.MessageType.INFO);
    }

    /** 从托盘重新显示窗口。 */
    private void showFromTray() {
        frame.setVisible(true);
        frame.setExtendedState(JFrame.NORMAL);
        frame.toFront();
        refresh();
        if (refreshTimer != null) {
            refreshTimer.start();
        }
    }

    /** 安装系统托盘图标（双击显示窗口，右键弹出 Swing 菜单）。 */
    private void installTrayIcon() {
        if (trayIcon != null) {
            return;
        }
        if (!SystemTray.isSupported()) {
            System.err.println("[ServerConsole] System tray is not supported");
            return;
        }
        try {
            // 不使用 AWT PopupMenu：原生菜单用 Tahoma 字体（无中文字形）会显示方框，
            // 且菜单回调中直接显示窗口容易让菜单卡在屏幕上；统一改用 Swing 菜单。
            Image icon = LogoLoader.load();
            TrayIcon created = new TrayIcon(icon == null ? frame.getIconImage() : icon, TITLE);
            created.setImageAutoSize(true);
            created.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    handleTrayMouse(event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    handleTrayMouse(event);
                }
            });
            SystemTray.getSystemTray().add(created);
            trayIcon = created;
        } catch (Exception e) {
            System.err.println("[ServerConsole] Failed to install tray icon: " + e.getMessage());
        }
    }

    /** 托盘鼠标事件：右键显示 Swing 菜单，左键双击显示控制台。 */
    private void handleTrayMouse(MouseEvent event) {
        if (event.isPopupTrigger()) {
            showTrayMenu();
        } else if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
            showFromTray();
        }
    }

    /**
     * 弹出托盘菜单。
     *
     * <p>使用带焦点的无边框 {@link JDialog} 而不是 Swing 轻量弹出菜单：轻量菜单收不到"点击其他程序
     * 窗口/桌面"的鼠标事件，会一直留在屏幕上；对话框在失去焦点时（点击任意其他窗口）自动关闭。</p>
     */
    private void showTrayMenu() {
        if (trayMenuDialog != null && trayMenuDialog.isVisible()) {
            hideTrayMenu();
            return;
        }
        Point pointer = MouseInfo.getPointerInfo() == null
                ? new Point(0, 0) : MouseInfo.getPointerInfo().getLocation();

        Color background = uiColor("PopupMenu.background", new Color(0x2D2D30));
        Color foreground = uiColor("PopupMenu.foreground", new Color(0xE8E8E8));
        JDialog dialog = new JDialog(frame, false);
        dialog.setUndecorated(true);
        dialog.setAlwaysOnTop(true);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 1));
        panel.setBackground(background);
        panel.setBorder(BorderFactory.createLineBorder(new Color(0x585858)));
        panel.add(createMenuButton("显示控制台", foreground, background, this::showFromTray, dialog));
        panel.add(createMenuButton("退出服务器", foreground, background, this::stopAndExit, dialog));
        dialog.setContentPane(panel);
        dialog.pack();

        Dimension size = dialog.getSize();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = Math.max(0, Math.min(pointer.x, screen.width - size.width - 8));
        int y = pointer.y - size.height;
        if (y < 0) {
            y = pointer.y;
        }
        dialog.setLocation(x, y);
        dialog.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent event) {
                // 刚显示时可能先触发一次焦点变化，短暂延迟内忽略
                if (System.currentTimeMillis() - trayMenuOpenedAt > 200) {
                    hideTrayMenu();
                }
            }
        });
        dialog.getRootPane().registerKeyboardAction(event -> hideTrayMenu(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        trayMenuDialog = dialog;
        trayMenuOpenedAt = System.currentTimeMillis();
        dialog.setVisible(true);
        dialog.toFront();
        dialog.requestFocus();
    }

    /** 创建托盘菜单按钮（使用主题配色，鼠标悬停高亮）。 */
    private JButton createMenuButton(String text, Color foreground, Color background,
                                     Runnable action, JDialog dialog) {
        JButton button = new JButton(text);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 13F));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setBorder(BorderFactory.createEmptyBorder(7, 20, 7, 20));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                button.setBackground(new Color(0x3E3E42));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                button.setBackground(background);
            }
        });
        button.addActionListener(event -> {
            dialog.dispose();
            trayMenuDialog = null;
            SwingUtilities.invokeLater(action);
        });
        return button;
    }

    private Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private void hideTrayMenu() {
        if (trayMenuDialog != null) {
            JDialog dialog = trayMenuDialog;
            trayMenuDialog = null;
            dialog.dispose();
        }
    }

    /** 清理托盘菜单（与 hideTrayMenu 等价，供退出流程复用）。 */
    private void disposeTrayMenu() {
        hideTrayMenu();
    }

    /** 刷新会话列表（保留当前选中的会话键）。 */
    private void refresh() {
        String selected = selectedSessionId();
        List<SessionInfo> sessions = SessionScope.activeSessions();
        tableModel.setRowCount(0);
        for (SessionInfo session : sessions) {
            tableModel.addRow(new Object[] {
                    session.id(),
                    session.workspace(),
                    session.source(),
                    session.resourcePermission(),
                    session.operationPermission(),
                    CLOCK.format(Instant.ofEpochMilli(session.lastAccessMillis())
                            .atZone(ZoneId.systemDefault()))
            });
        }
        sessionCountLabel.setText("活跃会话: " + sessions.size());
        sessionCountLabel.setToolTipText("共 " + sessions.size() + " 个会话");
        if (selected != null) {
            selectSession(selected);
        } else if (sessions.isEmpty()) {
            statusLabel.setText("当前没有活跃会话");
        }
        if (sessions.isEmpty()) {
            workspaceField.setText("");
        }
    }

    private void fillFieldsFromSelection() {
        SessionInfo session = selectedSession();
        if (session == null) {
            return;
        }
        workspaceField.setText(session.workspace() == null ? "" : session.workspace().toString());
        resourceBox.setSelectedItem(session.resourcePermission());
        operationBox.setSelectedItem(session.operationPermission());
        workspaceSourceLabel.setText("来源: " + session.source());
    }

    private void applyWorkspace() {
        SessionInfo session = selectedSession();
        if (session == null) {
            statusLabel.setText("请先在列表中选择一个会话");
            return;
        }
        String text = workspaceField.getText().trim();
        if (text.isEmpty()) {
            statusLabel.setText("请填写工作区绝对路径");
            return;
        }
        Path workspace;
        try {
            workspace = Path.of(text).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            statusLabel.setText("路径不合法: " + e.getMessage());
            return;
        }
        if (!Files.isDirectory(workspace)) {
            statusLabel.setText("工作区不是已存在的目录: " + workspace);
            return;
        }
        if (!SessionScope.setSessionWorkspace(session.id(), workspace)) {
            statusLabel.setText("设置失败：会话不存在或工作区不可用（不能位于 .dlqq 下）");
            return;
        }
        statusLabel.setText("已设置会话工作区: " + workspace);
        refresh();
    }

    private void applyPermission(boolean resource) {
        SessionInfo session = selectedSession();
        if (session == null) {
            statusLabel.setText("请先在列表中选择一个会话");
            return;
        }
        try {
            if (resource) {
                String mode = String.valueOf(resourceBox.getSelectedItem());
                SessionScope.setSessionResourcePermission(session.id(), mode);
                statusLabel.setText("已设置资源权限: " + mode);
            } else {
                String mode = String.valueOf(operationBox.getSelectedItem());
                SessionScope.setSessionOperationPermission(session.id(), mode);
                statusLabel.setText("已设置操作权限: " + mode);
            }
        } catch (RuntimeException e) {
            statusLabel.setText("设置失败: " + e.getMessage());
            return;
        }
        refresh();
    }

    private SessionInfo selectedSession() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        String id = String.valueOf(tableModel.getValueAt(row, 0));
        for (SessionInfo session : SessionScope.activeSessions()) {
            if (session.id().equals(id)) {
                return session;
            }
        }
        return null;
    }

    private String selectedSessionId() {
        int row = table.getSelectedRow();
        return row < 0 ? null : String.valueOf(tableModel.getValueAt(row, 0));
    }

    private void selectSession(String sessionId) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (sessionId.equals(String.valueOf(tableModel.getValueAt(row, 0)))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    /** 退出服务器：移除托盘图标、停止刷新、释放窗口并终止 MCP 服务器进程。 */
    private void stopAndExit() {
        try {
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
            disposeTrayMenu();
            if (trayIcon != null) {
                SystemTray.getSystemTray().remove(trayIcon);
                trayIcon = null;
            }
            frame.dispose();
        } finally {
            System.err.println("[ServerConsole] Exit requested, terminating MCP server process");
            System.exit(0);
        }
    }
}
