package com.donglouqiaoque.dlqqmcpserver.mcp;

import com.donglouqiaoque.dlqqmcpserver.mcp.tools.McpTools;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope.SessionInfo;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;
import com.donglouqiaoque.dlqqmcpserver.utils.WorkspaceRegistry;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 控制台「配置」选项卡：按会话选择工作区，管理该工作区 settings.properties 中的配置属性值。
 *
 * <p>配置按工作区保存，同一工作区的多个会话共用同一份配置文件；切换会话后
 * 会重新读取对应工作区的配置。双击「当前值」单元格即可修改并保存。</p>
 */
public final class ConfigTabPanel extends JPanel implements RefreshableTab {

    private static final String[] COLUMNS = {"属性", "当前值", "默认值"};
    private static final int COLUMN_KEY = 0;
    private static final int COLUMN_VALUE = 1;
    private static final int COLUMN_DEFAULT = 2;
    private static final Color SUCCESS_COLOR = new Color(0x4C, 0xAF, 0x50);
    private static final Color ERROR_COLOR = new Color(0xE5, 0x73, 0x73);

    private final JComboBox<SessionChoice> sessionBox = new JComboBox<>();
    private final JLabel workspaceLabel = new JLabel(" ");
    private final JLabel fileLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton restoreButton = new JButton("恢复默认");
    private final JButton restoreAllButton = new JButton("恢复全部");
    private final ConfigTableModel tableModel = new ConfigTableModel();
    private final JTable table = new JTable(tableModel);
    private boolean refreshing;

    public ConfigTabPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildUi();
        refresh();
    }

    private void buildUi() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JLabel("会话 / 工作区："));
        sessionBox.setPreferredSize(new Dimension(640, 28));
        sessionBox.addActionListener(event -> {
            if (!refreshing) {
                loadProperties();
            }
        });
        controls.add(sessionBox);


        restoreButton.setFocusable(false);
        restoreButton.setToolTipText("将选中属性恢复为默认值");
        restoreButton.setEnabled(false);
        restoreButton.addActionListener(event -> restoreSelectedProperty());
        controls.add(restoreButton);

        restoreAllButton.setFocusable(false);
        restoreAllButton.setToolTipText("将当前工作区所有配置属性恢复为默认值");
        restoreAllButton.setEnabled(true);
        restoreAllButton.addActionListener(event -> restoreAllProperties());
        controls.add(restoreAllButton);

        JPanel pathPanel = new JPanel(new GridLayout(0, 1, 0, 2));
        workspaceLabel.setFont(workspaceLabel.getFont().deriveFont(Font.PLAIN, 12F));
        fileLabel.setFont(fileLabel.getFont().deriveFont(Font.PLAIN, 12F));
        pathPanel.add(workspaceLabel);
        pathPanel.add(fileLabel);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(controls, BorderLayout.NORTH);
        top.add(pathPanel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setToolTipText("双击“当前值”单元格修改并保存；配置按工作区共享，同一工作区的会话使用同一份文件");
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COLUMN_KEY).setPreferredWidth(340);
        table.getColumnModel().getColumn(COLUMN_VALUE).setPreferredWidth(620);
        table.getColumnModel().getColumn(COLUMN_DEFAULT).setPreferredWidth(420);
        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer();
        defaultRenderer.setForeground(new Color(0x9A, 0x9A, 0x9A));
        table.getColumnModel().getColumn(COLUMN_DEFAULT).setCellRenderer(defaultRenderer);
        table.getSelectionModel().addListSelectionListener(event -> updateRestoreButton());
        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12F));
        add(statusLabel, BorderLayout.SOUTH);
        setStatus("提示：双击“当前值”单元格修改；配置按工作区保存，同一工作区的会话共用一份配置文件。", false);
    }

    @Override
    public void refresh() {
        String previousChoiceKey = selectedChoiceKey();
        List<SessionInfo> sessions = SessionScope.activeSessions();

        refreshing = true;
        try {
            sessionBox.removeAllItems();
            Set<String> activeWorkspaceKeys = new HashSet<>();
            for (SessionInfo session : sessions) {
                if (session.workspace() == null) {
                    continue;
                }
                activeWorkspaceKeys.add(WorkspaceRegistry.canonicalPathKey(session.workspace()));
                String label = (SessionScope.DEFAULT_SESSION_ID.equals(session.id())
                        ? "默认会话" : session.id())
                        + "  —  " + session.workspace();
                sessionBox.addItem(new SessionChoice(session.id(), session.workspace(), label));
            }
            // 与「会话管理」一致：workspace_config.json 中登记过、当前无活跃会话的工作区也展示
            for (Map.Entry<String, String> entry : WorkspaceRegistry.entries().entrySet()) {
                if (activeWorkspaceKeys.contains(entry.getKey())) {
                    continue;
                }
                Path workspace;
                try {
                    workspace = Path.of(entry.getKey());
                } catch (RuntimeException e) {
                    continue;
                }
                sessionBox.addItem(new SessionChoice(null, workspace,
                        "工作区（无活跃会话）  —  " + workspace));
            }
            int restoreIndex = -1;
            for (int i = 0; i < sessionBox.getItemCount(); i++) {
                if (sessionBox.getItemAt(i).key().equals(previousChoiceKey)) {
                    restoreIndex = i;
                    break;
                }
            }
            if (restoreIndex >= 0) {
                sessionBox.setSelectedIndex(restoreIndex);
            } else if (sessionBox.getItemCount() > 0) {
                sessionBox.setSelectedIndex(0);
            }
        } finally {
            refreshing = false;
        }
        loadProperties();
    }

    private void loadProperties() {
        SessionChoice choice = selectedChoice();
        if (choice == null) {
            tableModel.setRows(new ArrayList<>());
            workspaceLabel.setText("工作区：-");
            fileLabel.setText("配置文件：-");
            setStatus("当前没有可管理的会话或工作区配置。", false);
            updateRestoreButton();
            return;
        }

        Path settingsFile = Workspace.settingsFileOf(choice.workspace());
        Map<String, String> actual = Workspace.readAllSettingsFor(choice.workspace());
        Map<String, String> defaults = new LinkedHashMap<>(McpTools.getDefaultProperties());
        // 权限键不在 McpTools 的通用默认配置中，单独补充
        defaults.put(Workspace.PERMISSION_KEY, Workspace.DEFAULT_PERMISSION);
        defaults.put(Workspace.OPERATION_PERMISSION_KEY, Workspace.DEFAULT_OPERATION_PERMISSION);

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(defaults.keySet());
        keys.addAll(actual.keySet());
        List<String> orderedKeys = new ArrayList<>(keys);
        orderedKeys.sort(Comparator.comparingInt(ConfigTabPanel::groupRank)
                .thenComparing(Comparator.naturalOrder()));

        String selectedKey = selectedPropertyKey();
        List<PropertyRow> rows = new ArrayList<>(orderedKeys.size());
        for (String key : orderedKeys) {
            String current = actual.getOrDefault(key, "");
            String defaultValue = defaults.get(key);
            rows.add(new PropertyRow(key, current, defaultValue));
        }
        tableModel.setRows(rows);
        String sessionText = choice.sessionId() == null
                ? "无活跃会话（直接管理工作区配置）" : choice.sessionId();
        workspaceLabel.setText("工作区：" + choice.workspace()
                + "    会话：" + sessionText);
        fileLabel.setText("配置文件：" + settingsFile);
        fileLabel.setToolTipText(settingsFile.toString());
        selectProperty(selectedKey);
        updateRestoreButton();
    }

    private void saveProperty(PropertyRow row, String rawValue) {
        SessionChoice choice = selectedChoice();
        if (choice == null) {
            showError("没有可保存的会话或工作区");
            return;
        }
        String value = rawValue == null ? "" : rawValue;
        try {
            writePropertyValue(choice, row, value);
            SwingUtilities.invokeLater(() -> {
                loadProperties();
                setStatus("✓ 已保存：" + row.key() + " = " + value, false);
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(this::loadProperties);
            showError("保存配置失败：" + e.getMessage());
        }
    }

    private void writePropertyValue(SessionChoice choice, PropertyRow row, String value) throws Exception {
        if (Workspace.PERMISSION_KEY.equals(row.key())) {
            if (choice.sessionId() != null) {
                if (!SessionScope.setSessionResourcePermission(choice.sessionId(), value)) {
                    throw new IllegalArgumentException("会话不存在或资源权限无效");
                }
            } else {
                Workspace.setPermissionFor(choice.workspace(), value);
            }
        } else if (Workspace.OPERATION_PERMISSION_KEY.equals(row.key())) {
            if (choice.sessionId() != null) {
                if (!SessionScope.setSessionOperationPermission(choice.sessionId(), value)) {
                    throw new IllegalArgumentException("会话不存在或操作权限无效");
                }
            } else {
                Workspace.setOperationPermissionFor(choice.workspace(), value);
            }
        } else {
            Workspace.writeSettingFor(choice.workspace(), row.key(), value);
        }
    }

    private void restoreSelectedProperty() {
        PropertyRow row = selectedProperty();
        if (row == null) {
            showError("请先选择一个属性");
            return;
        }
        if (row.defaultValue() == null) {
            showError("该属性没有默认值: " + row.key());
            return;
        }
        saveProperty(row, row.defaultValue());
    }

    private void restoreAllProperties() {
        SessionChoice choice = selectedChoice();
        if (choice == null) {
            showError("没有可恢复的会话或工作区");
            return;
        }
        List<PropertyRow> pending = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            PropertyRow row = tableModel.rowAt(i);
            if (row != null && row.defaultValue() != null
                    && !row.defaultValue().equals(row.currentValue())) {
                pending.add(row);
            }
        }
        if (pending.isEmpty()) {
            setStatus("当前工作区所有可恢复属性已是默认值。", false);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "将当前工作区的 " + pending.size() + " 个配置属性恢复为默认值，是否继续？",
                "恢复全部", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            for (PropertyRow row : pending) {
                writePropertyValue(choice, row, row.defaultValue());
            }
            SwingUtilities.invokeLater(() -> {
                loadProperties();
                setStatus("✓ 已恢复全部配置：" + pending.size() + " 项", false);
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(this::loadProperties);
            showError("恢复全部配置失败：" + e.getMessage());
        }
    }

    private void updateRestoreButton() {
        PropertyRow row = selectedProperty();
        restoreButton.setEnabled(row != null && row.defaultValue() != null
                && !row.defaultValue().equals(row.currentValue()));
        restoreAllButton.setEnabled(true);
    }

    private PropertyRow selectedProperty() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return tableModel.rowAt(table.convertRowIndexToModel(viewRow));
    }

    private String selectedPropertyKey() {
        PropertyRow row = selectedProperty();
        return row == null ? null : row.key();
    }

    private void selectProperty(String key) {
        if (key == null) {
            return;
        }
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (key.equals(tableModel.rowAt(i).key())) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private SessionChoice selectedChoice() {
        Object item = sessionBox.getSelectedItem();
        return item instanceof SessionChoice choice ? choice : null;
    }

    private String selectedChoiceKey() {
        SessionChoice choice = selectedChoice();
        return choice == null ? null : choice.key();
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setText(text);
        statusLabel.setForeground(error ? ERROR_COLOR : SUCCESS_COLOR);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "配置", JOptionPane.WARNING_MESSAGE);
    }

    /** 属性分组排序：权限、响应、搜索、脚本、工具管理、其它。 */
    private static int groupRank(String key) {
        if (key.startsWith("permission.")) {
            return 0;
        }
        if (key.startsWith("response.")) {
            return 1;
        }
        if (key.startsWith("search.")) {
            return 2;
        }
        if (key.startsWith("script.")) {
            return 3;
        }
        if (key.startsWith("mcp.tools.")) {
            return 4;
        }
        return 5;
    }

    private record SessionChoice(String sessionId, Path workspace, String label) {
        String key() {
            return sessionId == null ? "w:" + workspace : "s:" + sessionId;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record PropertyRow(String key, String currentValue, String defaultValue) {
    }

    private final class ConfigTableModel extends AbstractTableModel {

        private final List<PropertyRow> rows = new ArrayList<>();

        void setRows(List<PropertyRow> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }

        PropertyRow rowAt(int row) {
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
            return column == COLUMN_VALUE;
        }

        @Override
        public Object getValueAt(int row, int column) {
            PropertyRow data = rowAt(row);
            if (data == null) {
                return "";
            }
            return switch (column) {
                case COLUMN_KEY -> data.key();
                case COLUMN_VALUE -> data.currentValue();
                case COLUMN_DEFAULT -> data.defaultValue() == null ? "" : data.defaultValue();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != COLUMN_VALUE) {
                return;
            }
            PropertyRow data = rowAt(row);
            if (data != null) {
                saveProperty(data, value == null ? "" : String.valueOf(value));
            }
        }
    }
}
