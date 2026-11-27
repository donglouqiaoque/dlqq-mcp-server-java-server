package com.donglouqiaoque.dlqqmcpserver.mcp;

import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ToolPolicy;
import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ToolPolicy.PolicyEntry;
import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ToolPolicy.Risk;
import com.donglouqiaoque.dlqqmcpserver.mcp.policy.ToolPolicy.Visibility;
import com.donglouqiaoque.dlqqmcpserver.mcp.server.McpServer;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope;
import com.donglouqiaoque.dlqqmcpserver.utils.SessionScope.SessionInfo;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;
import com.donglouqiaoque.dlqqmcpserver.utils.WorkspaceRegistry;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 控制台「工具管理」选项卡：按会话/工作区管理所有工具的
 * 资源风险、外设风险与当前可见程度。
 *
 * <p>默认值来自内置 {@code resources/tool_policy.txt}；修改只写入当前工作区
 * settings 目录下的 {@code tool_policy.txt}，与 settings.properties 的管理方式一致。
 * 源码屏蔽工具也会展示，但其可见程度固定为“源码屏蔽”，只能修改风险等级。</p>
 */
public final class ToolManagementTabPanel extends JPanel implements RefreshableTab {

    private static final String[] COLUMNS = {"工具名称", "资源风险", "外设风险", "当前可见程度", "状态"};
    private static final int COLUMN_NAME = 0;
    private static final int COLUMN_RESOURCE = 1;
    private static final int COLUMN_PERIPHERAL = 2;
    private static final int COLUMN_VISIBILITY = 3;
    private static final int COLUMN_STATUS = 4;
    private static final String[] RISK_LABELS = {"无危 (S)", "风险 (R)", "高危 (H)"};
    private static final String[] VISIBILITY_LABELS = {"完全访问", "仅可查看", "已被隐藏"};
    private static final Color SUCCESS_COLOR = new Color(0x4C, 0xAF, 0x50);
    private static final Color ERROR_COLOR = new Color(0xE5, 0x73, 0x73);
    private static final String SOURCE_MASKED_LABEL = "源码屏蔽";

    private final JComboBox<SessionChoice> sessionBox = new JComboBox<>();
    private final JLabel workspaceLabel = new JLabel(" ");
    private final JLabel fileLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton restoreButton = new JButton("恢复默认");
    private final JButton restoreAllButton = new JButton("恢复全部");
    private final ToolTableModel tableModel = new ToolTableModel();
    private final JTable table = new JTable(tableModel);
    private Map<String, PolicyEntry> overrides = new LinkedHashMap<>();
    private boolean refreshing;

    public ToolManagementTabPanel() {
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
                loadPolicy();
            }
        });
        controls.add(sessionBox);

        restoreButton.setFocusable(false);
        restoreButton.setToolTipText("将选中工具恢复为内置默认策略");
        restoreButton.setEnabled(false);
        restoreButton.addActionListener(event -> restoreSelectedTool());
        controls.add(restoreButton);

        restoreAllButton.setFocusable(false);
        restoreAllButton.setToolTipText("将当前工作区所有工具策略恢复为内置默认值");
        restoreAllButton.setEnabled(true);
        restoreAllButton.addActionListener(event -> restoreAllTools());
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
        table.setToolTipText("双击“资源风险 / 外设风险 / 当前可见程度”单元格修改；修改只写入当前工作区的 tool_policy.txt");
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COLUMN_NAME).setPreferredWidth(360);
        table.getColumnModel().getColumn(COLUMN_RESOURCE).setPreferredWidth(120);
        table.getColumnModel().getColumn(COLUMN_PERIPHERAL).setPreferredWidth(120);
        table.getColumnModel().getColumn(COLUMN_VISIBILITY).setPreferredWidth(150);
        table.getColumnModel().getColumn(COLUMN_STATUS).setPreferredWidth(140);
        table.getColumnModel().getColumn(COLUMN_RESOURCE)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(RISK_LABELS)));
        table.getColumnModel().getColumn(COLUMN_PERIPHERAL)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(RISK_LABELS)));
        table.getColumnModel().getColumn(COLUMN_VISIBILITY)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(VISIBILITY_LABELS)));
        DefaultTableCellRenderer muted = new DefaultTableCellRenderer();
        muted.setForeground(new Color(0x9A, 0x9A, 0x9A));
        table.getColumnModel().getColumn(COLUMN_STATUS).setCellRenderer(muted);
        table.getSelectionModel().addListSelectionListener(event -> updateRestoreButton());
        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12F));
        add(statusLabel, BorderLayout.SOUTH);
        setStatus("提示：双击风险或可见程度单元格修改；源码屏蔽工具只能修改风险等级。", false);
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
            // 与会话管理/配置一致：workspace_config.json 中的工作区也展示
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
            } else {
                sessionBox.setSelectedIndex(sessionBox.getItemCount() > 0 ? 0 : -1);
            }
        } finally {
            refreshing = false;
        }
        loadPolicy();
    }

    private void loadPolicy() {
        SessionChoice choice = selectedChoice();
        if (choice == null) {
            overrides = new LinkedHashMap<>();
            tableModel.setRows(new ArrayList<>());
            workspaceLabel.setText("工作区：-");
            fileLabel.setText("工具策略覆盖文件：-");
            setStatus("当前没有可管理的会话或工作区。", false);
            updateRestoreButton();
            return;
        }

        Path policyFile = ToolPolicy.policyFileFor(choice.workspace());
        Map<String, PolicyEntry> defaults = ToolPolicy.defaultPolicy();
        overrides = new LinkedHashMap<>(ToolPolicy.readPolicyOverrides(choice.workspace()));

        Set<String> names = new HashSet<>(defaults.keySet());
        names.addAll(ToolCatalog.SOURCE_MASKED);
        List<String> sortedNames = new ArrayList<>(names);
        sortedNames.sort(Comparator.naturalOrder());

        String selectedTool = selectedToolName();
        List<ToolRow> rows = new ArrayList<>(sortedNames.size());
        for (String name : sortedNames) {
            boolean sourceMasked = ToolCatalog.isSourceMasked(name);
            PolicyEntry defaultEntry = defaults.getOrDefault(name,
                    new PolicyEntry(Risk.SAFE, Risk.SAFE, Visibility.FULL));
            PolicyEntry effective = overrides.getOrDefault(name, defaultEntry);
            Visibility visibility = sourceMasked ? Visibility.SOURCE_MASKED : effective.visibility();
            rows.add(new ToolRow(name, sourceMasked, effective.resourceRisk(),
                    effective.peripheralRisk(), visibility));
        }
        tableModel.setRows(rows);
        String sessionText = choice.sessionId() == null
                ? "无活跃会话（直接管理工作区配置）" : choice.sessionId();
        workspaceLabel.setText("工作区：" + choice.workspace() + "    会话：" + sessionText);
        fileLabel.setText("工具策略覆盖文件：" + (policyFile == null ? "-" : policyFile));
        fileLabel.setToolTipText(policyFile == null ? "" : policyFile.toString());
        selectTool(selectedTool);
        updateRestoreButton();
    }

    private void saveTool(ToolRow row, Risk resource, Risk peripheral, Visibility visibility) {
        SessionChoice choice = selectedChoice();
        if (choice == null) {
            showError("没有可保存的会话或工作区");
            return;
        }
        boolean visibilityChanged = visibility != null && visibility != row.visibility;
        try {
            Visibility newVisibility = visibility == null ? row.visibility : visibility;
            PolicyEntry edited = new PolicyEntry(resource, peripheral, newVisibility);
            Map<String, PolicyEntry> defaults = ToolPolicy.defaultPolicy();
            PolicyEntry defaultEntry = defaults.getOrDefault(row.name,
                    new PolicyEntry(Risk.SAFE, Risk.SAFE, Visibility.FULL));
            if (edited.equals(defaultEntry)) {
                overrides.remove(row.name);
            } else {
                overrides.put(row.name, edited);
            }
            ToolPolicy.writePolicyOverrides(choice.workspace(), overrides);
            if (visibilityChanged) {
                McpServer.notifyActiveToolsChanged();
            }
            SwingUtilities.invokeLater(() -> {
                loadPolicy();
                setStatus("✓ 已保存：" + row.name, false);
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(this::loadPolicy);
            showError("保存工具策略失败：" + e.getMessage());
        }
    }

    private void restoreSelectedTool() {
        ToolRow row = selectedTool();
        if (row == null) {
            showError("请先选择一个工具");
            return;
        }
        SessionChoice choice = selectedChoice();
        if (choice == null) {
            return;
        }
        try {
            PolicyEntry defaultEntry = ToolPolicy.defaultPolicy().get(row.name);
            if (defaultEntry == null) {
                defaultEntry = new PolicyEntry(Risk.SAFE, Risk.SAFE, Visibility.FULL);
            }
            overrides.remove(row.name);
            ToolPolicy.writePolicyOverrides(choice.workspace(), overrides);
            if (row.visibility != defaultEntry.visibility()) {
                McpServer.notifyActiveToolsChanged();
            }
            SwingUtilities.invokeLater(() -> {
                loadPolicy();
                setStatus("✓ 已恢复默认：" + row.name, false);
            });
        } catch (Exception e) {
            showError("恢复默认失败：" + e.getMessage());
        }
    }

    private void restoreAllTools() {
        SessionChoice choice = selectedChoice();
        if (choice == null) {
            showError("没有可恢复的会话或工作区");
            return;
        }
        if (overrides.isEmpty()) {
            setStatus("当前工作区没有工具策略覆盖，已是默认状态。", false);
            return;
        }
        int count = overrides.size();
        int answer = JOptionPane.showConfirmDialog(this,
                "将当前工作区的 " + count + " 个工具策略恢复为内置默认值，是否继续？",
                "恢复全部", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            boolean visibilityChanged = hasVisibilityOverride(overrides);
            overrides.clear();
            ToolPolicy.writePolicyOverrides(choice.workspace(), overrides);
            if (visibilityChanged) {
                McpServer.notifyActiveToolsChanged();
            }
            SwingUtilities.invokeLater(() -> {
                loadPolicy();
                setStatus("✓ 已恢复全部工具策略：" + count + " 项", false);
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(this::loadPolicy);
            showError("恢复全部工具策略失败：" + e.getMessage());
        }
    }

    private boolean hasVisibilityOverride(Map<String, PolicyEntry> entries) {
        Map<String, PolicyEntry> defaults = ToolPolicy.defaultPolicy();
        for (Map.Entry<String, PolicyEntry> override : entries.entrySet()) {
            PolicyEntry defaultEntry = defaults.get(override.getKey());
            if (defaultEntry == null) {
                defaultEntry = new PolicyEntry(Risk.SAFE, Risk.SAFE, Visibility.FULL);
            }
            if (override.getValue().visibility() != defaultEntry.visibility()) {
                return true;
            }
        }
        return false;
    }

    private void updateRestoreButton() {
        restoreAllButton.setEnabled(true);
        ToolRow row = selectedTool();
        if (row == null) {
            restoreButton.setEnabled(false);
            return;
        }
        PolicyEntry defaultEntry = ToolPolicy.defaultPolicy().get(row.name);
        if (defaultEntry == null) {
            defaultEntry = new PolicyEntry(Risk.SAFE, Risk.SAFE, Visibility.FULL);
        }
        restoreButton.setEnabled(row.resourceRisk != defaultEntry.resourceRisk()
                || row.peripheralRisk != defaultEntry.peripheralRisk()
                || (!row.sourceMasked && row.visibility != defaultEntry.visibility()));
    }

    private ToolRow selectedTool() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        return tableModel.rowAt(table.convertRowIndexToModel(viewRow));
    }

    private String selectedToolName() {
        ToolRow row = selectedTool();
        return row == null ? null : row.name;
    }

    private void selectTool(String name) {
        if (name == null) {
            return;
        }
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (name.equals(tableModel.rowAt(i).name)) {
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
        JOptionPane.showMessageDialog(this, message, "工具管理", JOptionPane.WARNING_MESSAGE);
    }

    private static String riskLabel(Risk risk) {
        if (risk == Risk.HIGH) {
            return "高危 (H)";
        }
        return risk == Risk.RISK ? "风险 (R)" : "无危 (S)";
    }

    private static Risk riskFromLabel(String label) {
        if (label == null) {
            return Risk.SAFE;
        }
        if (label.contains("H")) {
            return Risk.HIGH;
        }
        return label.contains("R") ? Risk.RISK : Risk.SAFE;
    }

    private static String visibilityLabel(Visibility visibility) {
        if (visibility == Visibility.SOURCE_MASKED) {
            return SOURCE_MASKED_LABEL;
        }
        if (visibility == Visibility.VIEW_ONLY) {
            return "仅可查看";
        }
        return visibility == Visibility.HIDDEN ? "已被隐藏" : "完全访问";
    }

    private static Visibility visibilityFromLabel(String label) {
        if ("仅可查看".equals(label)) {
            return Visibility.VIEW_ONLY;
        }
        if ("已被隐藏".equals(label)) {
            return Visibility.HIDDEN;
        }
        return Visibility.FULL;
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

    private static final class ToolRow {
        private final String name;
        private final boolean sourceMasked;
        private final Risk resourceRisk;
        private final Risk peripheralRisk;
        private final Visibility visibility;

        private ToolRow(String name, boolean sourceMasked, Risk resourceRisk, Risk peripheralRisk,
                        Visibility visibility) {
            this.name = name;
            this.sourceMasked = sourceMasked;
            this.resourceRisk = resourceRisk;
            this.peripheralRisk = peripheralRisk;
            this.visibility = visibility;
        }
    }

    private final class ToolTableModel extends AbstractTableModel {

        private final List<ToolRow> rows = new ArrayList<>();

        void setRows(List<ToolRow> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }

        ToolRow rowAt(int row) {
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
            ToolRow data = rowAt(row);
            if (data == null) {
                return false;
            }
            if (column == COLUMN_VISIBILITY) {
                return !data.sourceMasked;
            }
            return column == COLUMN_RESOURCE || column == COLUMN_PERIPHERAL;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ToolRow data = rowAt(row);
            if (data == null) {
                return "";
            }
            return switch (column) {
                case COLUMN_NAME -> data.name;
                case COLUMN_RESOURCE -> riskLabel(data.resourceRisk);
                case COLUMN_PERIPHERAL -> riskLabel(data.peripheralRisk);
                case COLUMN_VISIBILITY -> visibilityLabel(data.visibility);
                case COLUMN_STATUS -> data.sourceMasked ? "源码屏蔽" : "已注册";
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            ToolRow data = rowAt(row);
            if (data == null) {
                return;
            }
            String text = value == null ? "" : String.valueOf(value);
            if (column == COLUMN_RESOURCE) {
                saveTool(data, riskFromLabel(text), data.peripheralRisk, null);
            } else if (column == COLUMN_PERIPHERAL) {
                saveTool(data, data.resourceRisk, riskFromLabel(text), null);
            } else if (column == COLUMN_VISIBILITY && !data.sourceMasked) {
                saveTool(data, data.resourceRisk, data.peripheralRisk, visibilityFromLabel(text));
            }
        }
    }
}
