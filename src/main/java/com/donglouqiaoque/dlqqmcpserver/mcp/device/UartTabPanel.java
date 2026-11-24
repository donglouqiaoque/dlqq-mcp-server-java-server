package com.donglouqiaoque.dlqqmcpserver.mcp.device;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import com.donglouqiaoque.dlqqmcpserver.mcp.RefreshableTab;
import java.util.List;

/**
 * 控制台 UART 选项卡（多设备）：左侧为可用串口/已打开设备列表，右侧为选中设备的收发面板。
 */
public final class UartTabPanel extends JPanel implements RefreshableTab {

    private final DefaultListModel<String> portModel = new DefaultListModel<>();
    private final DefaultListModel<String> openModel = new DefaultListModel<>();
    private final JList<String> portList = new JList<>(portModel);
    private final JList<String> openList = new JList<>(openModel);
    private final JComboBox<Integer> baudCombo = new JComboBox<>(new Integer[]{
            9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600, 1000000, 2000000});
    private final JTextArea logArea = new JTextArea();
    private final JTextField sendField = new JTextField();
    private final JCheckBox hexSend = new JCheckBox("HEX");
    private final JLabel deviceLabel = new JLabel("未选择设备");
    private final JLabel statusLabel = new JLabel(" ");

    private String currentDevice;

    public UartTabPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel line1 = new JPanel();
        line1.setLayout(new BoxLayout(line1, BoxLayout.X_AXIS));
        line1.add(new JLabel("波特率:"));
        baudCombo.setEditable(true);
        baudCombo.setSelectedItem(1000000);
        baudCombo.setMaximumSize(new Dimension(120, 25));
        line1.add(baudCombo);
        JButton openButton = new JButton("打开选中串口");
        openButton.addActionListener(e -> openSelectedPort());
        line1.add(openButton);
        JButton closeButton = new JButton("关闭选中设备");
        closeButton.addActionListener(e -> closeSelectedDevice());
        line1.add(closeButton);
        line1.add(Box.createHorizontalGlue());
        line1.add(statusLabel);
        JPanel toolbar = new JPanel(new GridLayout(0, 1, 0, 4));
        toolbar.add(line1);
        add(toolbar, BorderLayout.NORTH);

        portList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        portList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && portList.getSelectedValue() != null) {
                openList.clearSelection();
                deviceLabel.setText("待打开: " + portList.getSelectedValue());
            }
        });
        openList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        openList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String value = openList.getSelectedValue();
                if (value != null && !value.startsWith("（")) {
                    portList.clearSelection();
                    currentDevice = value;
                    deviceLabel.setText("设备: " + value);
                    refreshLog();
                }
            }
        });

        JPanel left = new JPanel(new GridLayout(0, 1, 0, 4));
        left.setBorder(BorderFactory.createTitledBorder("设备"));
        JPanel portsBox = new JPanel(new BorderLayout(0, 2));
        portsBox.add(new JLabel("可用串口:"), BorderLayout.NORTH);
        portsBox.add(new JScrollPane(portList), BorderLayout.CENTER);
        JPanel openBox = new JPanel(new BorderLayout(0, 2));
        openBox.add(new JLabel("已打开设备:"), BorderLayout.NORTH);
        openBox.add(new JScrollPane(openList), BorderLayout.CENTER);
        left.add(portsBox);
        left.add(openBox);
        left.setPreferredSize(new Dimension(260, 0));

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
        split.setResizeWeight(0.25);
        add(split, BorderLayout.CENTER);

        refreshPorts();
        refreshOpenDevices();
        new javax.swing.Timer(1000, e -> refreshOpenDevices()).start();
    }

    private void refreshPorts() {
        String selected = portList.getSelectedValue();
        portModel.clear();
        try {
            List<String> ports = UartDevices.listPorts();
            for (String port : ports) {
                portModel.addElement(port);
            }
        } catch (RuntimeException e) {
            statusLabel.setText("串口枚举失败: " + e.getMessage());
        }
        if (portModel.isEmpty()) {
            portModel.addElement("（未找到串口）");
        }
        if (selected != null) {
            portList.setSelectedValue(selected, true);
        }
    }

    private void refreshOpenDevices() {
        String selected = openList.getSelectedValue();
        openModel.clear();
        for (UartDevices.Device device : UartDevices.openDevices()) {
            openModel.addElement(device.portName());
        }
        if (openModel.isEmpty()) {
            openModel.addElement("（未打开设备）");
        } else if (currentDevice != null && openModel.contains(currentDevice)) {
            openList.setSelectedValue(currentDevice, true);
        } else if (selected != null && openModel.contains(selected)) {
            openList.setSelectedValue(selected, true);
        }
        refreshLog();
    }

    private void openSelectedPort() {
        Object value = portList.getSelectedValue();
        if (value == null || value.toString().startsWith("（")) {
            statusLabel.setText("请先在左侧选择串口");
            return;
        }
        String port = value.toString();
        int baud = parseBaud();
        try {
            UartDevices.Device device = UartDevices.open(port, baud);
            device.addListener(d -> SwingUtilities.invokeLater(this::refreshOpenDevices));
            currentDevice = device.portName();
            statusLabel.setText("已打开 " + device.portName());
            refreshOpenDevices();
            openList.setSelectedValue(currentDevice, true);
        } catch (RuntimeException e) {
            statusLabel.setText("打开失败: " + e.getMessage());
        }
    }

    private void closeSelectedDevice() {
        String device = openList.getSelectedValue();
        if (device == null || device.startsWith("（")) {
            device = currentDevice;
        }
        if (device == null || device.startsWith("（")) {
            statusLabel.setText("请先选择已打开设备");
            return;
        }
        try {
            UartDevices.close(device);
            if (device.equals(currentDevice)) {
                currentDevice = null;
                logArea.setText("");
                deviceLabel.setText("未选择设备");
            }
            statusLabel.setText("已关闭 " + device);
        } catch (RuntimeException e) {
            statusLabel.setText("关闭失败: " + e.getMessage());
        }
        refreshOpenDevices();
    }

    private void send() {
        if (currentDevice == null) {
            statusLabel.setText("请先在左侧选择已打开设备");
            return;
        }
        try {
            if (hexSend.isSelected()) {
                UartDevices.sendHex(currentDevice, sendField.getText());
            } else {
                UartDevices.sendText(currentDevice, sendField.getText());
            }
            sendField.setText("");
            statusLabel.setText("已发送");
            refreshLog();
        } catch (RuntimeException e) {
            statusLabel.setText("发送失败: " + e.getMessage());
        }
    }

    private void refreshLog() {
        if (currentDevice == null) {
            return;
        }
        UartDevices.Device device = UartDevices.get(currentDevice);
        if (device == null) {
            return;
        }
        String text = device.logText();
        logArea.setText(text);
        if (!text.isEmpty()) {
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    @Override
    public void refresh() {
        statusLabel.setText("刷新中…");
        refreshPorts();
        refreshOpenDevices();
        statusLabel.setText("已刷新");
    }

    private int parseBaud() {
        Object value = baudCombo.getSelectedItem();
        try {
            return value == null ? 1000000 : Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 1000000;
        }
    }
}
