package com.donglouqiaoque.dlqqmcpserver.mcp.device;

import com.donglouqiaoque.dlqqmcpserver.mcp.RefreshableTab;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/**
 * 控制台 USB 选项卡（多设备）：左侧扫描到的设备/已打开设备列表，右侧为选中设备的收发面板。
 */
public final class UsbTabPanel extends JPanel implements RefreshableTab {

    private final DefaultListModel<String> scanModel = new DefaultListModel<>();
    private final DefaultListModel<String> openModel = new DefaultListModel<>();
    private final JList<String> scanList = new JList<>(scanModel);
    private final JList<String> openList = new JList<>(openModel);
    private final JTextArea logArea = new JTextArea();
    private final JTextField sendField = new JTextField();
    private final JCheckBox hexSend = new JCheckBox("HEX");
    private final JLabel deviceLabel = new JLabel("未选择设备");
    private final JLabel statusLabel = new JLabel(" ");

    private String currentKey;

    public UsbTabPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel line1 = new JPanel();
        line1.setLayout(new BoxLayout(line1, BoxLayout.X_AXIS));
        JButton openButton = new JButton("打开选中设备");
        openButton.addActionListener(e -> openSelected());
        line1.add(openButton);
        JButton closeButton = new JButton("关闭选中设备");
        closeButton.addActionListener(e -> closeSelected());
        line1.add(closeButton);
        line1.add(Box.createHorizontalGlue());
        line1.add(statusLabel);
        JPanel toolbar = new JPanel(new GridLayout(0, 1, 0, 4));
        toolbar.add(line1);
        add(toolbar, BorderLayout.NORTH);

        scanList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scanList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && scanList.getSelectedValue() != null) {
                openList.clearSelection();
                deviceLabel.setText("待打开: " + scanList.getSelectedValue());
            }
        });
        openList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        openList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String value = openList.getSelectedValue();
                if (value != null && !value.startsWith("（")) {
                    scanList.clearSelection();
                    currentKey = value;
                    deviceLabel.setText("设备: " + value);
                    refreshLog();
                }
            }
        });

        JPanel left = new JPanel(new GridLayout(0, 1, 0, 4));
        left.setBorder(BorderFactory.createTitledBorder("USB 设备"));
        JPanel scanBox = new JPanel(new BorderLayout(0, 2));
        scanBox.add(new JLabel("扫描到的设备:"), BorderLayout.NORTH);
        scanBox.add(new JScrollPane(scanList), BorderLayout.CENTER);
        JPanel openBox = new JPanel(new BorderLayout(0, 2));
        openBox.add(new JLabel("已打开设备:"), BorderLayout.NORTH);
        openBox.add(new JScrollPane(openList), BorderLayout.CENTER);
        left.add(scanBox);
        left.add(openBox);
        left.setPreferredSize(new Dimension(340, 0));

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(deviceLabel, BorderLayout.NORTH);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        right.add(new JScrollPane(logArea), BorderLayout.CENTER);
        JPanel sendPanel = new JPanel(new BorderLayout(4, 0));
        sendPanel.add(sendField, BorderLayout.CENTER);
        JPanel sendRight = new JPanel();
        sendRight.add(hexSend);
        JButton sendButton = new JButton("发送");
        sendButton.addActionListener(e -> send());
        sendRight.add(sendButton);
        sendPanel.add(sendRight, BorderLayout.EAST);
        sendField.addActionListener(e -> send());
        right.add(sendPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.35);
        add(split, BorderLayout.CENTER);

        refreshScan();
        refreshOpen();
        new javax.swing.Timer(1000, e -> refreshOpen()).start();
    }

    @Override
    public void refresh() {
        statusLabel.setText("扫描中…");
        new Thread(() -> {
            List<UsbDevices.DeviceInfo> devices = null;
            String error = null;
            try {
                devices = UsbDevices.scan();
            } catch (RuntimeException | LinkageError e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            List<UsbDevices.DeviceInfo> scanned = devices;
            String failure = error;
            SwingUtilities.invokeLater(() -> {
                String selected = scanList.getSelectedValue();
                scanModel.clear();
                if (scanned != null) {
                    for (UsbDevices.DeviceInfo info : scanned) {
                        scanModel.addElement(info.label());
                    }
                }
                if (scanModel.isEmpty()) {
                    scanModel.addElement(failure == null ? "（未发现 USB 设备）" : "（USB 扫描失败: " + failure + "）");
                }
                if (selected != null) {
                    scanList.setSelectedValue(selected, true);
                }
                statusLabel.setText(failure == null ? "扫描完成，共 " + scanModel.size() + " 个设备"
                        : "USB 扫描失败: " + failure);
                refreshOpen();
            });
        }, "usb-refresh-scan").start();
    }

    private void refreshScan() {
        String selected = scanList.getSelectedValue();
        scanModel.clear();
        try {
            for (UsbDevices.DeviceInfo info : UsbDevices.scan()) {
                scanModel.addElement(info.label());
            }
            statusLabel.setText("扫描完成，共 " + scanModel.size() + " 个设备");
        } catch (RuntimeException | LinkageError e) {
            scanModel.addElement("（USB 扫描失败: " + e.getMessage() + "）");
            statusLabel.setText("USB 扫描失败: " + e.getMessage());
        }
        if (scanModel.isEmpty()) {
            scanModel.addElement("（未发现 USB 设备）");
        }
        if (selected != null) {
            scanList.setSelectedValue(selected, true);
        }
    }

    private void refreshOpen() {
        String selected = openList.getSelectedValue();
        openModel.clear();
        for (UsbDevices.DeviceSession session : UsbDevices.openSessions()) {
            openModel.addElement(session.key());
        }
        if (openModel.isEmpty()) {
            openModel.addElement("（未打开设备）");
        } else if (currentKey != null && openModel.contains(currentKey)) {
            openList.setSelectedValue(currentKey, true);
        } else if (selected != null && openModel.contains(selected)) {
            openList.setSelectedValue(selected, true);
        }
        refreshLog();
    }

    private void openSelected() {
        String label = scanList.getSelectedValue();
        if (label == null || label.startsWith("（")) {
            statusLabel.setText("请先选择扫描到的设备");
            return;
        }
        String key = null;
        try {
            for (UsbDevices.DeviceInfo info : UsbDevices.scan()) {
                if (info.label().equals(label)) {
                    key = info.key();
                    break;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            statusLabel.setText("USB 扫描失败: " + e.getMessage());
            return;
        }
        if (key == null) {
            statusLabel.setText("设备已不存在，请刷新");
            return;
        }
        try {
            UsbDevices.DeviceSession session = UsbDevices.open(key);
            session.addListener(s -> SwingUtilities.invokeLater(this::refreshOpen));
            currentKey = session.key();
            statusLabel.setText("已打开 " + key);
            refreshOpen();
            openList.setSelectedValue(currentKey, true);
        } catch (RuntimeException e) {
            statusLabel.setText("打开失败: " + e.getMessage());
        }
    }

    private void closeSelected() {
        String key = openList.getSelectedValue();
        if (key == null || key.startsWith("（")) {
            key = currentKey;
        }
        if (key == null || key.startsWith("（")) {
            statusLabel.setText("请先选择已打开设备");
            return;
        }
        try {
            UsbDevices.close(key);
            if (key.equals(currentKey)) {
                currentKey = null;
                logArea.setText("");
                deviceLabel.setText("未选择设备");
            }
            statusLabel.setText("已关闭 " + key);
        } catch (RuntimeException e) {
            statusLabel.setText("关闭失败: " + e.getMessage());
        }
        refreshOpen();
    }

    private void send() {
        if (currentKey == null) {
            statusLabel.setText("请先在左侧选择已打开设备");
            return;
        }
        try {
            if (hexSend.isSelected()) {
                UsbDevices.sendHex(currentKey, sendField.getText());
            } else {
                UsbDevices.sendText(currentKey, sendField.getText());
            }
            sendField.setText("");
            statusLabel.setText("已发送");
            refreshLog();
        } catch (RuntimeException e) {
            statusLabel.setText("发送失败: " + e.getMessage());
        }
    }

    private void refreshLog() {
        if (currentKey == null) {
            return;
        }
        UsbDevices.DeviceSession session = UsbDevices.get(currentKey);
        if (session == null) {
            return;
        }
        String text = session.logText();
        logArea.setText(text);
        if (!text.isEmpty()) {
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }
}
