package com.donglouqiaoque.dlqqmcpserver.mcp.device;

import com.donglouqiaoque.dlqqmcpserver.mcp.RefreshableTab;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.swing.BorderFactory;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 控制台 BLE 选项卡（多设备）：左侧扫描/已连接设备列表，右侧为选中设备的服务、读写与通知面板。
 */
public final class BleTabPanel extends JPanel implements RefreshableTab {

    private final DefaultListModel<String> scanModel = new DefaultListModel<>();
    private final DefaultListModel<String> connectedModel = new DefaultListModel<>();
    private final JList<String> scanList = new JList<>(scanModel);
    private final JList<String> connectedList = new JList<>(connectedModel);
    private final JTextField characteristicField = new JTextField();
    private final JTextField dataField = new JTextField();
    private final JCheckBox hexWrite = new JCheckBox("HEX");
    private final JTextArea logArea = new JTextArea();
    private final JLabel deviceLabel = new JLabel("未选择设备");
    private final JLabel statusLabel = new JLabel(" ");
    private final Set<String> connected = new LinkedHashSet<>();

    private String currentAddress;

    private final Consumer<JsonObject> eventListener = event -> SwingUtilities.invokeLater(() -> {
        if ("notification".equals(event.get("event").getAsString())) {
            String address = event.has("address") ? event.get("address").getAsString() : "";
            String characteristic = event.has("characteristic")
                    ? event.get("characteristic").getAsString() : "";
            byte[] data = toBytes(event.getAsJsonArray("data"));
            appendLog("NOTIFY " + address + " " + characteristic + ": " + hex(data));
        }
    });

    public BleTabPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        BleDevices.addEventListener(eventListener);

        JPanel line1 = new JPanel();
        line1.add(new JLabel("扫描:"));
        JButton scanButton = new JButton("扫描设备");
        scanButton.addActionListener(e -> scan());
        line1.add(scanButton);
        JButton connectButton = new JButton("连接选中");
        connectButton.addActionListener(e -> connect());
        line1.add(connectButton);
        JButton disconnectButton = new JButton("断开选中");
        disconnectButton.addActionListener(e -> disconnect());
        line1.add(disconnectButton);
        line1.add(statusLabel);
        JPanel toolbar = new JPanel(new GridLayout(0, 1, 0, 4));
        toolbar.add(line1);
        add(toolbar, BorderLayout.NORTH);

        scanList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scanList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && scanList.getSelectedValue() != null) {
                connectedList.clearSelection();
                deviceLabel.setText("待连接: " + scanList.getSelectedValue());
            }
        });
        connectedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        connectedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String value = connectedList.getSelectedValue();
                if (value != null && !value.startsWith("（")) {
                    scanList.clearSelection();
                    currentAddress = value;
                    deviceLabel.setText("设备: " + value);
                }
            }
        });

        JPanel left = new JPanel(new GridLayout(0, 1, 0, 4));
        left.setBorder(BorderFactory.createTitledBorder("BLE 设备"));
        JPanel scanBox = new JPanel(new BorderLayout(0, 2));
        scanBox.add(new JLabel("扫描到的设备:"), BorderLayout.NORTH);
        scanBox.add(new JScrollPane(scanList), BorderLayout.CENTER);
        JPanel connectedBox = new JPanel(new BorderLayout(0, 2));
        connectedBox.add(new JLabel("已连接设备:"), BorderLayout.NORTH);
        connectedBox.add(new JScrollPane(connectedList), BorderLayout.CENTER);
        left.add(scanBox);
        left.add(connectedBox);
        left.setPreferredSize(new Dimension(320, 0));

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(deviceLabel, BorderLayout.NORTH);

        JPanel operation = new JPanel(new GridLayout(0, 1, 0, 3));
        JPanel line2 = new JPanel();
        line2.add(new JLabel("特征 UUID:"));
        characteristicField.setPreferredSize(new Dimension(320, 25));
        line2.add(characteristicField);
        JButton discoverButton = new JButton("发现服务");
        discoverButton.addActionListener(e -> discover());
        line2.add(discoverButton);
        operation.add(line2);
        JPanel line3 = new JPanel();
        line3.add(new JLabel("数据:"));
        dataField.setPreferredSize(new Dimension(260, 25));
        line3.add(dataField);
        line3.add(hexWrite);
        JButton readButton = new JButton("读");
        readButton.addActionListener(e -> read());
        line3.add(readButton);
        JButton writeButton = new JButton("写");
        writeButton.addActionListener(e -> write());
        line3.add(writeButton);
        JButton subscribeButton = new JButton("订阅");
        subscribeButton.addActionListener(e -> subscribe());
        line3.add(subscribeButton);
        JButton unsubscribeButton = new JButton("取消订阅");
        unsubscribeButton.addActionListener(e -> unsubscribe());
        line3.add(unsubscribeButton);
        operation.add(line3);
        right.add(operation, BorderLayout.SOUTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        right.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.3);
        add(split, BorderLayout.CENTER);

        new javax.swing.Timer(2000, e -> refreshStatus()).start();
        refreshStatus();
    }

    @Override
    public void refresh() {
        refreshStatus();
        refreshConnected();
        if (!BleDevices.isRunning()) {
            statusLabel.setText("BLE 后端未启动，点\"扫描设备\"启动");
            return;
        }
        new Thread(() -> {
            try {
                List<JsonObject> devices = BleDevices.devices();
                SwingUtilities.invokeLater(() -> {
                    String selected = scanList.getSelectedValue();
                    scanModel.clear();
                    for (JsonObject device : devices) {
                        String address = device.has("address") ? device.get("address").getAsString() : "";
                        String name = device.has("name") ? device.get("name").getAsString() : "";
                        String rssi = device.has("rssi") ? device.get("rssi").getAsString() : "";
                        scanModel.addElement(address + "  " + name + "  [" + rssi + "]");
                    }
                    if (scanModel.isEmpty()) {
                        scanModel.addElement("（未发现 BLE 设备，可点\"扫描设备\"重新扫描）");
                    }
                    if (selected != null) {
                        scanList.setSelectedValue(selected, true);
                    }
                    statusLabel.setText(BleDevices.backendInfo());
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("刷新 BLE 设备失败: " + e.getMessage()));
            }
        }, "ble-refresh-devices").start();
    }

    private void scan() {
        statusLabel.setText("扫描中…");
        new Thread(() -> {
            try {
                JsonObject response = BleDevices.scan(5);
                if (!response.has("success") || !response.get("success").getAsBoolean()) {
                    throw new IllegalStateException(response.toString());
                }
                var devices = BleDevices.devices();
                SwingUtilities.invokeLater(() -> {
                    scanModel.clear();
                    for (JsonObject device : devices) {
                        String address = device.has("address") ? device.get("address").getAsString() : "";
                        String name = device.has("name") ? device.get("name").getAsString() : "";
                        String rssi = device.has("rssi") ? device.get("rssi").getAsString() : "";
                        scanModel.addElement(address + "  " + name + "  [" + rssi + "]");
                    }
                    if (scanModel.isEmpty()) {
                        scanModel.addElement("（未发现 BLE 设备）");
                    }
                    statusLabel.setText("扫描完成，共 " + devices.size() + " 个设备");
                    appendLog("扫描完成，共 " + devices.size() + " 个设备");
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("扫描失败: " + e.getMessage());
                    appendLog("扫描失败: " + e.getMessage());
                });
            }
        }, "ble-scan").start();
    }

    private void connect() {
        String address = selectedAddress(scanList);
        if (address == null) {
            return;
        }
        statusLabel.setText("连接中…");
        new Thread(() -> {
            JsonObject response = BleDevices.connect(address);
            boolean success = response.has("success") && response.get("success").getAsBoolean();
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    connected.add(address);
                    refreshConnected();
                    connectedList.setSelectedValue(address, true);
                    currentAddress = address;
                    statusLabel.setText("已连接 " + address);
                    appendLog("已连接 " + address);
                } else {
                    statusLabel.setText("连接失败: " + response);
                    appendLog("连接失败: " + response);
                }
            });
        }, "ble-connect").start();
    }

    private void disconnect() {
        String address = connectedList.getSelectedValue();
        if (address == null || address.startsWith("（")) {
            address = currentAddress;
        }
        if (address == null || address.startsWith("（")) {
            statusLabel.setText("请先选择已连接设备");
            return;
        }
        JsonObject response = BleDevices.disconnect(address);
        connected.remove(address);
        if (address.equals(currentAddress)) {
            currentAddress = null;
            deviceLabel.setText("未选择设备");
        }
        refreshConnected();
        statusLabel.setText("已断开 " + address);
        appendLog("已断开 " + address + " (" + response + ")");
    }

    private void discover() {
        String address = requireConnectedAddress();
        if (address == null) {
            return;
        }
        statusLabel.setText("发现服务中…");
        new Thread(() -> {
            JsonArray services = BleDevices.discover(address);
            SwingUtilities.invokeLater(() -> {
                StringBuilder text = new StringBuilder();
                String firstCharacteristic = null;
                for (JsonElement element : services) {
                    JsonObject service = element.getAsJsonObject();
                    text.append("Service ").append(service.get("uuid").getAsString()).append('\n');
                    if (service.has("characteristics")) {
                        for (JsonElement characteristic : service.getAsJsonArray("characteristics")) {
                            JsonObject object = characteristic.getAsJsonObject();
                            String uuid = object.get("uuid").getAsString();
                            text.append("  ├─ ").append(uuid).append("  ")
                                    .append(object.has("properties") ? object.get("properties") : "").append('\n');
                            if (firstCharacteristic == null) {
                                firstCharacteristic = uuid;
                            }
                        }
                    }
                }
                appendLog(text.toString());
                if (firstCharacteristic != null) {
                    characteristicField.setText(firstCharacteristic);
                }
                statusLabel.setText("发现完成，共 " + services.size() + " 个服务");
            });
        }, "ble-discover").start();
    }

    private void read() {
        String address = requireConnectedAddress();
        String characteristic = characteristicField.getText().trim();
        if (address == null || characteristic.isEmpty()) {
            return;
        }
        new Thread(() -> {
            JsonObject response = BleDevices.read(address, characteristic);
            SwingUtilities.invokeLater(() -> {
                if (response.has("success") && response.get("success").getAsBoolean()) {
                    byte[] data = toBytes(response.getAsJsonArray("data"));
                    appendLog("READ " + characteristic + ": " + hex(data));
                    dataField.setText(hex(data));
                    statusLabel.setText("读取成功");
                } else {
                    statusLabel.setText("读取失败: " + response);
                }
            });
        }, "ble-read").start();
    }

    private void write() {
        String address = requireConnectedAddress();
        String characteristic = characteristicField.getText().trim();
        if (address == null || characteristic.isEmpty()) {
            return;
        }
        byte[] data;
        if (hexWrite.isSelected()) {
            data = fromHex(dataField.getText());
            if (data == null) {
                statusLabel.setText("数据不是合法 HEX");
                return;
            }
        } else {
            data = dataField.getText().getBytes(StandardCharsets.UTF_8);
        }
        new Thread(() -> {
            JsonObject response = BleDevices.write(address, characteristic, data);
            SwingUtilities.invokeLater(() -> {
                if (response.has("success") && response.get("success").getAsBoolean()) {
                    appendLog("WRITE " + characteristic + ": " + hex(data));
                    statusLabel.setText("写入成功");
                } else {
                    statusLabel.setText("写入失败: " + response);
                }
            });
        }, "ble-write").start();
    }

    private void subscribe() {
        String address = requireConnectedAddress();
        String characteristic = characteristicField.getText().trim();
        if (address == null || characteristic.isEmpty()) {
            return;
        }
        JsonObject response = BleDevices.subscribe(address, characteristic);
        statusLabel.setText(response.has("success") && response.get("success").getAsBoolean()
                ? "已订阅 " + characteristic : "订阅失败: " + response);
    }

    private void unsubscribe() {
        String address = requireConnectedAddress();
        String characteristic = characteristicField.getText().trim();
        if (address == null || characteristic.isEmpty()) {
            return;
        }
        JsonObject response = BleDevices.unsubscribe(address, characteristic);
        statusLabel.setText(response.has("success") && response.get("success").getAsBoolean()
                ? "已取消订阅 " + characteristic : "取消订阅失败: " + response);
    }

    private String requireConnectedAddress() {
        String address = connectedList.getSelectedValue();
        if (address == null || address.startsWith("（")) {
            address = currentAddress;
        }
        if (address == null || address.startsWith("（")) {
            statusLabel.setText("请先连接并选择设备");
            return null;
        }
        return address;
    }

    private void refreshConnected() {
        String selected = connectedList.getSelectedValue();
        connectedModel.clear();
        for (String address : connected) {
            connectedModel.addElement(address);
        }
        if (connectedModel.isEmpty()) {
            connectedModel.addElement("（未连接）");
        } else if (currentAddress != null && connectedModel.contains(currentAddress)) {
            connectedList.setSelectedValue(currentAddress, true);
        } else if (selected != null && connectedModel.contains(selected)) {
            connectedList.setSelectedValue(selected, true);
        }
    }

    private void refreshStatus() {
        statusLabel.setText(BleDevices.backendInfo());
    }

    private String selectedAddress(JList<String> list) {
        String value = list.getSelectedValue();
        if (value == null || value.startsWith("（")) {
            return null;
        }
        int index = value.indexOf("  ");
        return index > 0 ? value.substring(0, index) : value;
    }

    private void appendLog(String text) {
        logArea.append("[" + LocalTime.now().withNano(0) + "] " + text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static byte[] toBytes(JsonArray array) {
        if (array == null) {
            return new byte[0];
        }
        byte[] data = new byte[array.size()];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) array.get(i).getAsInt();
        }
        return data;
    }

    private static byte[] fromHex(String text) {
        String cleaned = text == null ? "" : text.replaceAll("\\s+", "");
        if ((cleaned.length() & 1) != 0) {
            return null;
        }
        byte[] data = new byte[cleaned.length() / 2];
        try {
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return data;
    }

    private static String hex(byte[] data) {
        StringBuilder builder = new StringBuilder();
        for (byte value : data) {
            builder.append(String.format("%02X ", value & 0xFF));
        }
        return builder.toString().trim();
    }
}
