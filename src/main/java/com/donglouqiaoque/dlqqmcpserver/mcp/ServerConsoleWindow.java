package com.donglouqiaoque.dlqqmcpserver.mcp;

import com.donglouqiaoque.dlqqmcpserver.mcp.device.BleDevices;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.BleTabPanel;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.UartDevices;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.UartTabPanel;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.UsbDevices;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.UsbTabPanel;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.WifiTabPanel;
import com.donglouqiaoque.dlqqmcpserver.mcp.env.EnvTabPanel;
import com.donglouqiaoque.dlqqmcpserver.mcp.tools.LogoLoader;
import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope.SessionInfo;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;
import com.donglouqiaoque.dlqqmcpserver.utils.WorkspaceRegistry;

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
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
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
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int REFRESH_MILLIS = 2000;

    private static ServerConsoleWindow instance;

    private final JFrame frame = new JFrame();
    private final SessionTableModel tableModel = new SessionTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTextArea infoArea = new JTextArea(4, 20);

    private JTabbedPane tabs;
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
        JLabel contactLabel = new JLabel("联系作者：https://www.donglouqiaoque.com");
        contactLabel.setFont(contactLabel.getFont().deriveFont(Font.PLAIN, 12F));
        contactLabel.setForeground(new Color(0xD6, 0xD6, 0xD6));
        contactLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        contactLabel.setToolTipText("点击打开默认浏览器访问作者网站");
        contactLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                openAuthorWebsite();
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                contactLabel.setForeground(new Color(0xFFFFFF));
            }

            @Override
            public void mouseExited(MouseEvent event) {
                contactLabel.setForeground(new Color(0xD6, 0xD6, 0xD6));
            }
        });
        menuBar.add(Box.createHorizontalGlue());
        menuBar.add(contactLabel);
        menuBar.add(Box.createHorizontalGlue());
        JButton refreshButton = new JButton("刷新");
        refreshButton.setFocusable(false);
        refreshButton.addActionListener(event -> refreshManually());
        menuBar.add(refreshButton);
        menuBar.add(Box.createHorizontalStrut(16));
        frame.setJMenuBar(menuBar);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.setToolTipText("双击“工作区”“资源权限”“操作权限”单元格可直接修改；工作区也可用单元格右侧文件夹图标选择目录");
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COLUMN_SESSION).setPreferredWidth(240);
        table.getColumnModel().getColumn(COLUMN_WORKSPACE).setPreferredWidth(340);
        table.getColumnModel().getColumn(COLUMN_SOURCE).setPreferredWidth(150);
        table.getColumnModel().getColumn(COLUMN_RESOURCE).setPreferredWidth(120);
        table.getColumnModel().getColumn(COLUMN_OPERATION).setPreferredWidth(120);
        table.getColumnModel().getColumn(COLUMN_LAST_ACTIVE).setPreferredWidth(150);
        table.getColumnModel().getColumn(COLUMN_WORKSPACE).setCellEditor(new WorkspaceCellEditor());
        table.getColumnModel().getColumn(COLUMN_RESOURCE)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(RESOURCE_LEVELS)));
        table.getColumnModel().getColumn(COLUMN_OPERATION)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(OPERATION_LEVELS)));
        // 资源权限 / 操作权限 / 最后活跃：表头与数据单元格都居中对齐
        DefaultTableCellRenderer centeredRenderer = new DefaultTableCellRenderer();
        centeredRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        int[] centeredColumns = {COLUMN_RESOURCE, COLUMN_OPERATION, COLUMN_LAST_ACTIVE};
        for (int column : centeredColumns) {
            table.getColumnModel().getColumn(column).setCellRenderer(centeredRenderer);
            table.getColumnModel().getColumn(column).setHeaderRenderer(centeredHeaderRenderer());
        }
        // 点击表格空白处（无行区域）取消选中；表格未填满视口时点击视口空白区域也取消选中
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!event.isPopupTrigger() && table.rowAtPoint(event.getPoint()) < 0) {
                    clearTableSelection();
                }
            }
        });
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getViewport().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!event.isPopupTrigger()) {
                    clearTableSelection();
                }
            }
        });

        infoArea.setEditable(false);
        infoArea.setLineWrap(false);
        infoArea.setWrapStyleWord(false);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        infoArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JScrollPane infoScroll = new JScrollPane(infoArea,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        infoScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x555555)));

        JPanel sessionsPanel = new JPanel(new BorderLayout());
        sessionsPanel.add(tableScroll, BorderLayout.CENTER);
        sessionsPanel.add(infoScroll, BorderLayout.SOUTH);

        tabs = new JTabbedPane();
        tabs.addTab("会话管理", sessionsPanel);
        tabs.addTab("BLE", new BleTabPanel());
        tabs.addTab("UART", new UartTabPanel());
        tabs.addTab("USB", new UsbTabPanel());
        tabs.addTab("WiFi", new WifiTabPanel());
        tabs.addTab("环境", new EnvTabPanel());
        frame.add(tabs, BorderLayout.CENTER);

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

    /** 手动刷新：刷新当前选中选项卡；会话管理页刷新表格，其它页调用各自的 RefreshableTab。 */
    private void refreshManually() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        int selectedIndex = tabs == null ? -1 : tabs.getSelectedIndex();
        if (selectedIndex > 0) {
            Component component = tabs.getComponentAt(selectedIndex);
            if (component instanceof RefreshableTab refreshable) {
                refreshable.refresh();
                return;
            }
        }
        refresh();
    }

    /** 刷新表格（活跃会话 + workspace_config.json 中登记过的全部工作区）与底部信息。 */
    private void refresh() {
        if (table.isEditing()) {
            return;
        }
        String selectedKey = selectedRowKey();
        List<SessionInfo> sessions = SessionScope.activeSessions();
        List<Row> rows = buildRows(sessions);
        tableModel.setRows(rows);
        if (selectedKey != null) {
            selectByKey(selectedKey);
        }
        infoArea.setText(buildInfoText(sessions.size(), rows.size()));
        infoArea.setCaretPosition(0);
    }

    /** 组装表格行：活跃会话行 + 仅存在于 workspace_config.json 的工作区行，按最后活跃时间倒序。 */
    private List<Row> buildRows(List<SessionInfo> sessions) {
        List<Row> rows = new ArrayList<>();
        Set<String> activeWorkspaces = new HashSet<>();
        for (SessionInfo session : sessions) {
            rows.add(Row.forSession(session));
            if (session.workspace() != null) {
                activeWorkspaces.add(WorkspaceRegistry.canonicalPathKey(session.workspace()));
            }
        }
        for (Map.Entry<String, String> entry : WorkspaceRegistry.entries().entrySet()) {
            if (activeWorkspaces.contains(entry.getKey())) {
                continue;
            }
            Path workspace;
            try {
                workspace = Path.of(entry.getKey());
            } catch (RuntimeException e) {
                continue;
            }
            rows.add(Row.forWorkspace(workspace,
                    Workspace.getPermissionOf(workspace),
                    Workspace.getOperationPermissionOf(workspace),
                    settingsLastModified(workspace)));
        }
        rows.sort(Comparator.comparingLong(Row::lastActiveMillis).reversed());
        return rows;
    }

    /** 工作区 settings.properties 的最后修改时间（无活跃会话工作区的"最后活跃"近似值）。 */
    private long settingsLastModified(Path workspace) {
        try {
            return Files.getLastModifiedTime(Workspace.settingsFileOf(workspace)).toMillis();
        } catch (IOException | RuntimeException e) {
            return 0L;
        }
    }

    /** 底部信息区：部署目录 / 运行时目录 / 日志文件 / 会话与表格行数（长路径可横向滚动）。 */
    private String buildInfoText(int sessionCount, int rowCount) {
        String logFile = RuntimeDirs.getCurrentLogFile() == null
                ? "-" : RuntimeDirs.getCurrentLogFile().toString();
        return "部署目录: " + RuntimeDirs.getAppDir()
                + "\n运行时目录: " + RuntimeDirs.getRoot()
                + "\n日志文件: " + logFile
                + "\n活跃会话: " + sessionCount + "    表格行（会话+工作区）: " + rowCount;
    }

    private String selectedRowKey() {
        int row = table.getSelectedRow();
        return row < 0 ? null : tableModel.rowAt(row).key();
    }

    private void selectByKey(String key) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (key.equals(tableModel.rowAt(row).key())) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    /** 生成与默认表头渲染器一致、仅把文字居中的表头渲染器。 */
    private TableCellRenderer centeredHeaderRenderer() {
        TableCellRenderer base = table.getTableHeader().getDefaultRenderer();
        return (table, value, isSelected, hasFocus, row, column) -> {
            Component component = base.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel label) {
                label.setHorizontalAlignment(SwingConstants.CENTER);
            }
            return component;
        };
    }

    /** 取消表格选中行；若正在编辑单元格，先结束编辑再取消选中。 */
    private void clearTableSelection() {
        if (table.isEditing()) {
            TableCellEditor editor = table.getCellEditor();
            if (editor != null) {
                editor.stopCellEditing();
            }
        }
        table.clearSelection();
    }

    /** "最后活跃"格式化：带年月日；无时间（工作区从未产生配置）显示 "-"。 */
    private String formatLastActive(long millis) {
        if (millis <= 0L) {
            return "-";
        }
        return CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, TITLE, JOptionPane.WARNING_MESSAGE);
    }

    /** 打开作者网站（默认浏览器）；失败时弹窗提示。 */
    private void openAuthorWebsite() {
        final String website = "https://www.donglouqiaoque.com";
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("当前系统不支持打开默认浏览器");
            }
            Desktop.getDesktop().browse(new URI(website));
        } catch (Exception e) {
            showError("无法打开浏览器：" + e.getMessage());
        }
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

    /** 表格行：活跃会话行，或仅存在于 workspace_config.json 的工作区行。 */
    private static final class Row {

        private final SessionInfo session;
        private final Path workspace;
        private final String source;
        private final String resourcePermission;
        private final String operationPermission;
        private final long lastActiveMillis;

        private Row(SessionInfo session, Path workspace, String source,
                    String resourcePermission, String operationPermission, long lastActiveMillis) {
            this.session = session;
            this.workspace = workspace;
            this.source = source;
            this.resourcePermission = resourcePermission;
            this.operationPermission = operationPermission;
            this.lastActiveMillis = lastActiveMillis;
        }

        static Row forSession(SessionInfo session) {
            return new Row(session, session.workspace(), session.source(),
                    session.resourcePermission(), session.operationPermission(), session.lastAccessMillis());
        }

        static Row forWorkspace(Path workspace, String resourcePermission,
                                String operationPermission, long lastActiveMillis) {
            return new Row(null, workspace, "workspace_config.json",
                    resourcePermission, operationPermission, lastActiveMillis);
        }

        boolean isSession() {
            return session != null;
        }

        String sessionLabel() {
            return session == null ? "（工作区）" : session.id();
        }

        Path workspace() {
            return workspace;
        }

        String source() {
            return source;
        }

        String resourcePermission() {
            return resourcePermission;
        }

        String operationPermission() {
            return operationPermission;
        }

        long lastActiveMillis() {
            return lastActiveMillis;
        }

        String key() {
            if (session != null) {
                return "s:" + session.id();
            }
            return "w:" + (workspace == null ? "" : workspace.toString());
        }
    }

    /** 表格模型：工作区（仅会话行）与资源/操作权限列可直接编辑并立即生效。 */
    private final class SessionTableModel extends AbstractTableModel {

        private final List<Row> rows = new ArrayList<>();

        void setRows(List<Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }

        Row rowAt(int row) {
            return row < 0 || row >= rows.size() ? null : rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
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
            Row data = rowAt(row);
            if (data == null) {
                return false;
            }
            if (column == COLUMN_RESOURCE || column == COLUMN_OPERATION) {
                return true;
            }
            return column == COLUMN_WORKSPACE && data.isSession();
        }

        @Override
        public Object getValueAt(int row, int column) {
            Row data = rowAt(row);
            if (data == null) {
                return "";
            }
            switch (column) {
                case COLUMN_SESSION:
                    return data.sessionLabel();
                case COLUMN_WORKSPACE:
                    return data.workspace() == null ? "" : data.workspace().toString();
                case COLUMN_SOURCE:
                    return data.source();
                case COLUMN_RESOURCE:
                    return data.resourcePermission();
                case COLUMN_OPERATION:
                    return data.operationPermission();
                case COLUMN_LAST_ACTIVE:
                    return formatLastActive(data.lastActiveMillis());
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            Row data = rowAt(row);
            if (data == null) {
                return;
            }
            String text = value == null ? "" : value.toString().trim();
            try {
                if (column == COLUMN_WORKSPACE) {
                    if (!data.isSession()) {
                        throw new IllegalArgumentException("工作区行不能修改工作区路径");
                    }
                    if (text.isEmpty()) {
                        throw new IllegalArgumentException("工作区不能为空");
                    }
                    Path workspace = Path.of(text).toAbsolutePath().normalize();
                    Path current = data.workspace() == null ? null : data.workspace().toAbsolutePath().normalize();
                    if (workspace.equals(current)) {
                        return; // 值未变化（只是点了一下单元格/目录选择框）：不计为一次操作
                    }
                    if (!Files.isDirectory(workspace)) {
                        throw new IllegalArgumentException("工作区不是已存在的目录: " + workspace);
                    }
                    if (!SessionScope.setSessionWorkspace(data.session.id(), workspace)) {
                        throw new IllegalArgumentException("设置失败：工作区不可用（不能位于 .dlqq 下）");
                    }
                } else if (column == COLUMN_RESOURCE) {
                    if (text.equalsIgnoreCase(data.resourcePermission())) {
                        return; // 只打开了权限下拉框、没有切换选项：不计为一次操作
                    }
                    if (data.isSession()) {
                        SessionScope.setSessionResourcePermission(data.session.id(), text);
                    } else {
                        Workspace.setPermissionFor(data.workspace(), text);
                    }
                } else if (column == COLUMN_OPERATION) {
                    if (text.equalsIgnoreCase(data.operationPermission())) {
                        return; // 只打开了权限下拉框、没有切换选项：不计为一次操作
                    }
                    if (data.isSession()) {
                        SessionScope.setSessionOperationPermission(data.session.id(), text);
                    } else {
                        Workspace.setOperationPermissionFor(data.workspace(), text);
                    }
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
            UartDevices.closeAll();
            UsbDevices.closeAll();
            BleDevices.stop();
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
