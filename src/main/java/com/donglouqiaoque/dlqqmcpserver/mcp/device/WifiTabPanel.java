package com.donglouqiaoque.dlqqmcpserver.mcp.device;

import com.donglouqiaoque.dlqqmcpserver.mcp.RefreshableTab;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/**
 * 控制台 WiFi 选项卡（多网卡/多网络）：左侧网卡与网络列表，右侧为连接状态/诊断输出。
 *
 * <p>系统无线命令（netsh/nmcli）是阻塞子进程，界面线程只负责展示，命令统一放到后台线程执行。</p>
 */
public final class WifiTabPanel extends JPanel implements RefreshableTab {

    private final DefaultListModel<String> interfaceModel = new DefaultListModel<>();
    private final DefaultListModel<String> networkModel = new DefaultListModel<>();
    private final JList<String> interfaceList = new JList<>(interfaceModel);
    private final JList<String> networkList = new JList<>(networkModel);
    private final JTextArea statusArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");

    private volatile boolean scanning;

    public WifiTabPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel toolbar = new JPanel(new GridLayout(0, 1, 0, 4));
        JPanel line1 = new JPanel();
        line1.add(new JLabel("无线网卡:"));
        line1.add(new JLabel("（netsh/nmcli 使用系统默认无线接口）"));
        JButton scanButton = new JButton("扫描网络");
        scanButton.addActionListener(e -> scanAsync());
        line1.add(scanButton);
        JButton connectButton = new JButton("连接选中网络");
        connectButton.addActionListener(e -> connectAsync());
        line1.add(connectButton);
        JButton disconnectButton = new JButton("断开");
        disconnectButton.addActionListener(e -> disconnectAsync());
        line1.add(disconnectButton);
        line1.add(statusLabel);
        toolbar.add(line1);
        add(toolbar, BorderLayout.NORTH);

        interfaceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        interfaceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && interfaceList.getSelectedValue() != null) {
                statusLabel.setText("选中网卡: " + interfaceList.getSelectedValue());
            }
        });
        networkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel left = new JPanel(new GridLayout(0, 1, 0, 4));
        left.setBorder(BorderFactory.createTitledBorder("设备/网络"));
        JPanel interfacesBox = new JPanel(new BorderLayout(0, 2));
        interfacesBox.add(new JLabel("无线网卡:"), BorderLayout.NORTH);
        interfacesBox.add(new JScrollPane(interfaceList), BorderLayout.CENTER);
        JPanel networksBox = new JPanel(new BorderLayout(0, 2));
        networksBox.add(new JLabel("可用网络:"), BorderLayout.NORTH);
        networksBox.add(new JScrollPane(networkList), BorderLayout.CENTER);
        left.add(interfacesBox);
        left.add(networksBox);
        left.setPreferredSize(new Dimension(280, 0));

        statusArea.setEditable(false);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        statusArea.setText("正在读取无线状态…");
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(new JScrollPane(statusArea), BorderLayout.CENTER);
        JLabel hint = new JLabel("说明：一块网卡同一时间只能连接一个网络；连接需要系统里已有该 WiFi 的配置文件。");
        right.add(hint, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.3);
        add(split, BorderLayout.CENTER);

        refreshInterfacesAsync();
        showStatusAsync();
    }

    @Override
    public void refresh() {
        refreshInterfacesAsync();
        showStatusAsync();
    }

    private void refreshInterfacesAsync() {
        statusLabel.setText("正在读取网卡…");
        new Thread(() -> {
            try {
                List<WifiDevices.InterfaceInfo> interfaces = WifiDevices.interfaces();
                SwingUtilities.invokeLater(() -> {
                    String selected = interfaceList.getSelectedValue();
                    interfaceModel.clear();
                    for (WifiDevices.InterfaceInfo info : interfaces) {
                        String text = info.name() + "  [" + info.state() + "]"
                                + (info.ssid().isBlank() ? "" : "  SSID=" + info.ssid());
                        interfaceModel.addElement(text);
                    }
                    if (interfaceModel.isEmpty()) {
                        interfaceModel.addElement("（未发现无线网卡）");
                    }
                    if (selected != null) {
                        interfaceList.setSelectedValue(selected, true);
                    }
                    statusLabel.setText("网卡数量: " + interfaces.size());
                });
            } catch (RuntimeException | LinkageError e) {
                SwingUtilities.invokeLater(() -> {
                    interfaceModel.clear();
                    interfaceModel.addElement("（枚举失败）");
                    statusLabel.setText("网卡枚举失败: " + e.getMessage());
                    statusArea.setText("网卡枚举失败: " + e.getMessage());
                });
            }
        }, "wifi-interfaces").start();
    }

    private void scanAsync() {
        if (scanning) {
            statusLabel.setText("正在扫描，请稍候…");
            return;
        }
        scanning = true;
        statusLabel.setText("扫描中…（约 3~6 秒）");
        new Thread(() -> {
            try {
                List<WifiDevices.Network> networks = WifiDevices.scan();
                String rawStatus = networks.isEmpty() ? WifiDevices.lastScanOutput() : null;
                final String raw = rawStatus;
                SwingUtilities.invokeLater(() -> {
                    networkModel.clear();
                    for (WifiDevices.Network network : networks) {
                        networkModel.addElement(network.ssid() + "  信号:" + network.signal()
                                + "  认证:" + network.authentication());
                    }
                    if (networkModel.isEmpty()) {
                        networkModel.addElement("（未扫描到网络）");
                    }
                    statusLabel.setText("扫描完成: " + networks.size() + " 个网络");
                    if (raw != null && !raw.isBlank()) {
                        statusArea.setText(raw);
                        if (networks.isEmpty()) {
                            statusArea.append("\n\n提示：Windows 扫描 WLAN 通常还要求开启“定位服务（位置权限）”，"
                                    + "必要时以管理员身份运行服务端。");
                        }
                    }
                });
            } catch (RuntimeException | LinkageError e) {
                String message = e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("扫描失败: " + message);
                    statusArea.setText("扫描失败: " + message + "\n\n"
                            + "常见原因：无线网卡被禁用、飞行模式开启、WLAN AutoConfig 服务未运行。");
                });
            } finally {
                scanning = false;
            }
        }, "wifi-scan").start();
    }

    private void connectAsync() {
        String value = networkList.getSelectedValue();
        if (value == null || value.startsWith("（")) {
            statusLabel.setText("请先扫描并选择网络");
            return;
        }
        String ssid = value.contains("  信号:") ? value.substring(0, value.indexOf("  信号:")) : value;
        statusLabel.setText("连接中: " + ssid + " …");
        new Thread(() -> {
            try {
                String output = WifiDevices.connect(ssid);
                SwingUtilities.invokeLater(() -> {
                    statusArea.setText(output);
                    statusLabel.setText("已请求连接 " + ssid);
                });
            } catch (RuntimeException | LinkageError e) {
                String message = e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    statusArea.setText("连接失败: " + message);
                    statusLabel.setText("连接失败");
                    JOptionPane.showMessageDialog(this, "连接失败: " + message, "WiFi",
                            JOptionPane.WARNING_MESSAGE);
                });
            }
        }, "wifi-connect").start();
    }

    private void disconnectAsync() {
        statusLabel.setText("断开中…");
        new Thread(() -> {
            try {
                String output = WifiDevices.disconnect();
                SwingUtilities.invokeLater(() -> {
                    statusArea.setText(output);
                    statusLabel.setText("已断开");
                });
            } catch (RuntimeException | LinkageError e) {
                String message = e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    statusArea.setText("断开失败: " + message);
                    statusLabel.setText("断开失败");
                });
            }
        }, "wifi-disconnect").start();
    }

    private void showStatusAsync() {
        statusLabel.setText("正在读取状态…");
        new Thread(() -> {
            try {
                String output = WifiDevices.status();
                SwingUtilities.invokeLater(() -> {
                    statusArea.setText(output);
                    statusArea.setCaretPosition(0);
                    statusLabel.setText("状态已刷新");
                });
            } catch (RuntimeException | LinkageError e) {
                String message = e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    statusArea.setText("获取 WiFi 状态失败: " + message);
                    statusLabel.setText("状态获取失败");
                });
            }
        }, "wifi-status").start();
    }
}
