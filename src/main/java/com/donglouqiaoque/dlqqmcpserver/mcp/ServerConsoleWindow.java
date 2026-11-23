package com.donglouqiaoque.dlqqmcpserver.mcp;

import com.donglouqiaoque.dlqqmcpserver.mcp.tools.LogoLoader;
import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope.SessionInfo;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 服务端控制台窗口。
 *
 * <p>服务启动时默认**隐藏到系统托盘**（不弹窗口）：**单击**托盘图标或托盘菜单"显示控制台"打开窗口。</p>
 *
 * <p>窗口内直接以表格展示活跃会话；会话的"工作区""资源权限""操作权限"可在表格中**双击单元格直接编辑**，
 * 提交后立即生效。窗口底部显示部署目录、运行时目录、日志文件与活跃会话数（长路径可横向滚动查看）。</p>
 *
 * <p>点击标题栏关闭按钮不会退出进程，而是隐藏到系统托盘；只有托盘菜单或窗口菜单中的
 * "退出服务器"才终止 MCP 服务器进程。</p>
 *
 * <p>无图形环境（headless）时不会创建窗口；系统托盘不可用时退化为启动即显示窗口。</p>
 */
public final class ServerConsoleWindow {

    /** 控制台中文名（菜单标题、托盘提示、对话框标题）。 */
    private static final String APP_NAME = "鸫楼桥鹊MCP服务器控制台";
    private static final String TITLE = APP_NAME;
    private static final String[] COLUMNS = {"会话键", "工作区", "来源", "资源权限", "操作权限", "最后活跃"};
    private static final int COLUMN_SESSION = 0;
    private static final int COLUMN_WORKSPACE = 1;
    private static final int COLUMN_SOURCE = 2;
    private static final int COLUMN_RESOURCE = 3;
    private static final int COLUMN_OPERATION = 4;
    private static final int COLUMN_LAST_ACTIVE = 5;
    private static final String[] RESOURCE_LEVELS = {"chat", "readonly", "workspace", "approval", "highrisk", "full"};
    private static final String[] OPERATION_LEVELS = {"chat", "readonly", "approval", "highrisk", "full"};
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int REFRESH_MILLIS = 2000;

    private static ServerConsoleWindow instance;

    private final JFrame frame = new JFrame();
    private final SessionTableModel tableModel = new SessionTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextArea infoArea = new JTextArea(4, 20);

    private Timer refreshTimer;
    private TrayIcon trayIcon;
    private JDialog trayMenuDialog;
    private long trayMenuOpenedAt;

    private ServerConsoleWindow() {
        buildUi();
    }

    /**
     * 启动控制台（headless 时跳过）。
     *
     * <p>默认隐藏到系统托盘、不弹出窗口；重复调用会显示已有窗口。</p>
     */
    public static void start() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (instance == null) {
                instance = new ServerConsoleWindow();
                instance.startHidden();
            } else {
                instance.showFromTray();
            }
        });
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        // 按要求删除系统标题栏中间的标题文字（窗口标题置空）
        frame.setTitle("");
        frame.setSize(1250, 680);
        frame.setLocationRelativeTo(null);
        LogoLoader.applyTo(frame, "ServerConsole");

        JMenuBar menuBar = new JMenuBar();
        JMenu appMenu = new JMenu(APP_NAME);
        appMenu.setFont(appMenu.getFont().deriveFont(Font.BOLD, 13F));
        JMenuItem hideItem = new JMenuItem("隐藏到托盘");
        hideItem.addActionListener(event -> hideToTray());
        JMenuItem exitItem = new JMenuItem("退出服务器");
        exitItem.addActionListener(event -> stopAndExit());
        appMenu.add(hideItem);
        appMenu.addSeparator();
        appMenu.add(exitItem);
        menuBar.add(appMenu);
        menuBar.add(Box.createHorizontalGlue());
        JButton refreshButton = new JButton("刷新");
        refreshButton.setFocusable(false);
        refreshButton.addActionListener(event -> refreshManually());
        menuBar.add(refreshButton);
        menuBar.add(Box.createHorizontalStrut(16));
        frame.setJMenuBar(menuBar);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.setToolTipText("双击“工作区”“资源权限”“操作权限”单元格可直接修改；工作区也可用单元格右侧“...”按钮选择目录");
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COLUMN_SESSION).setPreferredWidth(240);
        table.getColumnModel().getColumn(COLUMN_WORKSPACE).setPreferredWidth(340);
        table.getColumnModel().getColumn(COLUMN_SOURCE).setPreferredWidth(150);
        table.getColumnModel().getColumn(COLUMN_RESOURCE).setPreferredWidth(120);
        table.getColumnModel().getColumn(COLUMN_OPERATION).setPreferredWidth(120);
        table.getColumnModel().getColumn(COLUMN_LAST_ACTIVE).setPreferredWidth(90);
        table.getColumnModel().getColumn(COLUMN_WORKSPACE).setCellEditor(new WorkspaceCellEditor());
        table.getColumnModel().getColumn(COLUMN_RESOURCE)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(RESOURCE_LEVELS)));
        table.getColumnModel().getColumn(COLUMN_OPERATION)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(OPERATION_LEVELS)));
        JScrollPane tableScroll = new JScrollPane(table);

        infoArea.setEditable(false);
        infoArea.setLineWrap(false);
        infoArea.setWrapStyleWord(false);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        infoArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JScrollPane infoScroll = new JScrollPane(infoArea,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        infoScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x555555)));

        frame.add(tableScroll, BorderLayout.CENTER);
        frame.add(infoScroll, BorderLayout.SOUTH);

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

    /**
     * 默认启动方式：只安装托盘图标并隐藏窗口，不弹出控制台、不弹气泡提示。
     *
     * <p>系统托盘不可用时退化为直接显示窗口（否则控制台无法再打开）。</p>
     */
    private void startHidden() {
        installTrayIcon();
        if (trayIcon == null) {
            show();
            return;
        }
        refresh();
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

    /** 安装系统托盘图标（单击显示窗口，右键弹出菜单）。 */
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

    /** 托盘鼠标事件：右键显示菜单，左键单击（含双击）显示控制台。 */
    private void handleTrayMouse(MouseEvent event) {
        if (event.isPopupTrigger()) {
            showTrayMenu();
            return;
        }
        // 只在左键释放时处理：单击即可打开控制台，双击也会先触发一次单击
        if (event.getID() == MouseEvent.MOUSE_RELEASED
                && SwingUtilities.isLeftMouseButton(event)
                && event.getClickCount() >= 1) {
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

    /** 手动刷新：先结束单元格编辑，避免输入内容被刷新覆盖。 */
    private void refreshManually() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        refresh();
    }

    /** 刷新会话表格与底部信息（编辑进行中时跳过，避免打断输入）。 */
    private void refresh() {
        if (table.isEditing()) {
            return;
        }
        String selected = selectedSessionId();
        List<SessionInfo> sessions = SessionScope.activeSessions();
        tableModel.setSessions(sessions);
        if (selected != null) {
            selectSession(selected);
        }
        infoArea.setText(buildInfoText(sessions.size()));
        infoArea.setCaretPosition(0);
    }

    /** 底部信息区：部署目录 / 运行时目录 / 日志文件 / 活跃会话数（长路径可横向滚动）。 */
    private String buildInfoText(int sessionCount) {
        String logFile = RuntimeDirs.getCurrentLogFile() == null
                ? "-" : RuntimeDirs.getCurrentLogFile().toString();
        return "部署目录: " + RuntimeDirs.getAppDir()
                + "\n运行时目录: " + RuntimeDirs.getRoot()
                + "\n日志文件: " + logFile
                + "\n活跃会话: " + sessionCount;
    }

    private String selectedSessionId() {
        int row = table.getSelectedRow();
        return row < 0 ? null : String.valueOf(tableModel.getValueAt(row, COLUMN_SESSION));
    }

    private void selectSession(String sessionId) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (sessionId.equals(String.valueOf(tableModel.getValueAt(row, COLUMN_SESSION)))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, TITLE, JOptionPane.WARNING_MESSAGE);
    }

    /**
     * 工作区单元格编辑器：文本框 + 目录选择按钮（"..."），点击按钮弹出目录选择框。
     *
     * <p>按钮选完目录后直接通过表格模型应用新工作区；手工输入路径后回车则走表格默认提交逻辑。</p>
     */
    private final class WorkspaceCellEditor extends AbstractCellEditor implements TableCellEditor {

        private final JPanel panel = new JPanel(new BorderLayout(4, 0));
        private final JTextField input = new JTextField();
        private final JButton browseButton = new JButton("...");
        private int editingRow = -1;

        WorkspaceCellEditor() {
            browseButton.setFocusable(false);
            browseButton.setToolTipText("选择工作区目录");
            browseButton.setMargin(new Insets(0, 6, 0, 6));
            var folderIcon = UIManager.getIcon("FileView.directoryIcon");
            if (folderIcon != null) {
                browseButton.setIcon(folderIcon);
                browseButton.setText("");
            } else {
                browseButton.setText("...");
            }
            browseButton.addActionListener(event -> chooseDirectory());
            panel.add(input, BorderLayout.CENTER);
            panel.add(browseButton, BorderLayout.EAST);
        }

        private void chooseDirectory() {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("选择工作区目录");
            String current = input.getText().trim();
            if (!current.isEmpty()) {
                File currentDir = new File(current);
                if (currentDir.isDirectory()) {
                    chooser.setCurrentDirectory(currentDir);
                }
            }
            if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File selected = chooser.getSelectedFile();
            if (selected == null) {
                return;
            }
            int row = editingRow;
            cancelCellEditing();
            if (row >= 0) {
                tableModel.setValueAt(selected.getAbsolutePath(), row, COLUMN_WORKSPACE);
            }
        }

        @Override
        public Object getCellEditorValue() {
            return input.getText();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            editingRow = row;
            input.setText(value == null ? "" : value.toString());
            SwingUtilities.invokeLater(input::requestFocus);
            return panel;
        }
    }

    /** 会话表格模型：工作区、资源权限、操作权限三列可直接编辑并立即生效。 */
    private final class SessionTableModel extends AbstractTableModel {

        private final List<SessionInfo> sessions = new ArrayList<>();

        void setSessions(List<SessionInfo> newSessions) {
            sessions.clear();
            sessions.addAll(newSessions);
            fireTableDataChanged();
        }

        SessionInfo sessionAt(int row) {
            return row < 0 || row >= sessions.size() ? null : sessions.get(row);
        }

        @Override
        public int getRowCount() {
            return sessions.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == COLUMN_WORKSPACE || column == COLUMN_RESOURCE || column == COLUMN_OPERATION;
        }

        @Override
        public Object getValueAt(int row, int column) {
            SessionInfo session = sessionAt(row);
            if (session == null) {
                return "";
            }
            switch (column) {
                case COLUMN_SESSION:
                    return session.id();
                case COLUMN_WORKSPACE:
                    return session.workspace() == null ? "" : session.workspace().toString();
                case COLUMN_SOURCE:
                    return session.source();
                case COLUMN_RESOURCE:
                    return session.resourcePermission();
                case COLUMN_OPERATION:
                    return session.operationPermission();
                case COLUMN_LAST_ACTIVE:
                    return CLOCK.format(Instant.ofEpochMilli(session.lastAccessMillis())
                            .atZone(ZoneId.systemDefault()));
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            SessionInfo session = sessionAt(row);
            if (session == null) {
                return;
            }
            String text = value == null ? "" : value.toString().trim();
            try {
                if (column == COLUMN_WORKSPACE) {
                    if (text.isEmpty()) {
                        throw new IllegalArgumentException("工作区不能为空");
                    }
                    Path workspace = Path.of(text).toAbsolutePath().normalize();
                    if (!Files.isDirectory(workspace)) {
                        throw new IllegalArgumentException("工作区不是已存在的目录: " + workspace);
                    }
                    if (!SessionScope.setSessionWorkspace(session.id(), workspace)) {
                        throw new IllegalArgumentException("设置失败：工作区不可用（不能位于 .dlqq 下）");
                    }
                } else if (column == COLUMN_RESOURCE) {
                    SessionScope.setSessionResourcePermission(session.id(), text);
                } else if (column == COLUMN_OPERATION) {
                    SessionScope.setSessionOperationPermission(session.id(), text);
                }
            } catch (RuntimeException e) {
                showError("设置失败：" + e.getMessage());
            }
            SwingUtilities.invokeLater(ServerConsoleWindow.this::refresh);
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
