package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import com.donglouqiaoque.dlqqmcpserver.mcp.device.BleDevices;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.UartDevices;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.UsbDevices;
import com.donglouqiaoque.dlqqmcpserver.mcp.device.WifiDevices;
import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.McpSchema.CallToolResult;
import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.McpSchema.ToolContent;
import com.donglouqiaoque.dlqqmcpserver.mcp.protocol.McpSchema.ToolInputSchema;
import com.donglouqiaoque.dlqqmcpserver.mcp.server.McpServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外设工具注册中心：统一按设备地址/名称寻址，不再使用实例端口号。
 *
 * <p>UART 按 COM 口名、USB 按 VID:PID:bus:address、WiFi 按网卡接口/SSID、BLE 按 MAC 地址。</p>
 */
public final class DeviceTools {

    private DeviceTools() {
    }

    public static void registerTools(McpServer server) {
        registerUartList(server);
        registerUartOpen(server);
        registerUartClose(server);
        registerUartSend(server);
        registerUartGetLog(server);

        registerUsbList(server);
        registerUsbOpen(server);
        registerUsbClose(server);
        registerUsbSend(server);
        registerUsbGetLog(server);

        registerWifiInterfaces(server);
        registerWifiScan(server);
        registerWifiStatus(server);
        registerWifiConnect(server);
        registerWifiDisconnect(server);

        registerBleScan(server);
        registerBleGetDevices(server);
        registerBleConnect(server);
        registerBleDisconnect(server);
        registerBleDiscover(server);
        registerBleRead(server);
        registerBleWrite(server);
        registerBleSubscribe(server);
        registerBleUnsubscribe(server);
        registerBleGetLog(server);
    }

    // ==================== UART ====================

    private static void registerUartList(McpServer server) {
        server.addTool("dlqq_uart_list",
                "列出当前系统的串口（COM 口）以及已经打开的串口设备。UART 工具不再使用实例端口号。",
                schema(Collections.emptyMap()),
                args -> {
                    List<String> lines = new ArrayList<>();
                    lines.add("可用串口:");
                    try {
                        List<String> ports = UartDevices.listPorts();
                        if (ports.isEmpty()) {
                            lines.add("  未找到串口设备");
                        } else {
                            for (String port : ports) {
                                lines.add("  " + port);
                            }
                        }
                    } catch (RuntimeException e) {
                        lines.add("  串口枚举失败: " + e.getMessage());
                    }
                    lines.add("已打开设备:");
                    List<UartDevices.Device> devices = UartDevices.openDevices();
                    if (devices.isEmpty()) {
                        lines.add("  无");
                    } else {
                        for (UartDevices.Device device : devices) {
                            lines.add("  " + device.portName() + " - " + device.displayName());
                        }
                    }
                    return ok(String.join("\n", lines));
                });
    }

    private static void registerUartOpen(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("device", stringProperty("串口名称，例如 COM3；也允许 'COM3 - 描述' 形式"));
        properties.put("baud_rate", intProperty("波特率，默认 1000000"));
        server.addTool("dlqq_uart_open",
                "按串口名称（COM3/ttyUSB0）打开 UART 设备，可在控制台 UART 选项卡中查看与操作。",
                schema(properties, "device"),
                args -> {
                    String device = str(args, "device");
                    try {
                        int baud = args.get("baud_rate") == null ? 1000000 : intValue(args.get("baud_rate"), 1000000);
                        UartDevices.Device opened = UartDevices.open(device, baud);
                        return ok("已打开串口 " + opened.portName() + " @" + baud);
                    } catch (RuntimeException e) {
                        return err("打开串口失败: " + e.getMessage());
                    }
                });
    }

    private static void registerUartClose(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("device", stringProperty("串口名称，例如 COM3"));
        server.addTool("dlqq_uart_close", "关闭指定串口设备。",
                schema(properties, "device"),
                args -> {
                    String device = str(args, "device");
                    try {
                        UartDevices.close(device);
                        return ok("已关闭串口 " + device);
                    } catch (RuntimeException e) {
                        return err("关闭串口失败: " + e.getMessage());
                    }
                });
    }

    private static void registerUartSend(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("device", stringProperty("串口名称，例如 COM3"));
        properties.put("data", stringProperty("要发送的数据；format=hex 时填写 HEX 字符串"));
        properties.put("format", stringProperty("text 或 hex，默认 text"));
        server.addTool("dlqq_uart_send", "向指定串口发送文本或 HEX 数据。",
                schema(properties, "device", "data"),
                args -> {
                    String device = str(args, "device");
                    String data = str(args, "data");
                    String format = str(args, "format");
                    try {
                        if ("hex".equalsIgnoreCase(format)) {
                            UartDevices.sendHex(device, data);
                        } else {
                            UartDevices.sendText(device, data);
                        }
                        return ok("已发送 " + data.length() + " 字符到 " + device);
                    } catch (RuntimeException e) {
                        return err("发送失败: " + e.getMessage());
                    }
                });
    }

    private static void registerUartGetLog(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("device", stringProperty("串口名称，例如 COM3"));
        properties.put("lines", intProperty("返回最近多少行，默认 100"));
        server.addTool("dlqq_uart_get_log", "获取指定串口设备最近的数据收发日志。",
                schema(properties, "device"),
                args -> {
                    UartDevices.Device device = UartDevices.get(str(args, "device"));
                    if (device == null) {
                        return err("串口未打开: " + str(args, "device"));
                    }
                    int limit = args.get("lines") == null ? 100 : intValue(args.get("lines"), 100);
                    List<String> lines = device.lines();
                    int from = Math.max(0, lines.size() - Math.max(1, limit));
                    return ok(String.join("\n", lines.subList(from, lines.size())));
                });
    }

    // ==================== USB ====================

    private static void registerUsbList(McpServer server) {
        server.addTool("dlqq_usb_list",
                "扫描并列出 USB 设备（设备标识为 VID:PID:bus:address，可直接用于 dlqq_usb_open）。",
                schema(Collections.emptyMap()),
                args -> {
                    List<String> lines = new ArrayList<>();
                    lines.add("USB 设备:");
                    try {
                        List<UsbDevices.DeviceInfo> devices = UsbDevices.scan();
                        if (devices.isEmpty()) {
                            lines.add("  未发现 USB 设备");
                        } else {
                            for (UsbDevices.DeviceInfo info : devices) {
                                lines.add("  " + info.label() + "  key=" + info.key());
                            }
                        }
                    } catch (RuntimeException e) {
                        lines.add("  USB 扫描失败: " + e.getMessage());
                    }
                    lines.add("已打开设备:");
                    List<UsbDevices.DeviceSession> sessions = UsbDevices.openSessions();
                    if (sessions.isEmpty()) {
                        lines.add("  无");
                    } else {
                        for (UsbDevices.DeviceSession session : sessions) {
                            lines.add("  " + session.key());
                        }
                    }
                    return ok(String.join("\n", lines));
                });
    }

    private static void registerUsbOpen(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("device", stringProperty("USB 设备标识，来自 dlqq_usb_list 的 key，例如 0483:5740:1:2"));
        server.addTool("dlqq_usb_open", "按设备标识打开 USB 设备（占用第一个可用接口/端点）。",
                schema(properties, "device"),
                args -> {
                    try {
                        UsbDevices.DeviceSession session = UsbDevices.open(str(args, "device"));
                        return ok("已打开 USB 设备 " + session.key());
                    } catch (RuntimeException e) {
                        return err("打开 USB 设备失败: " + e.getMessage());
                    }
                });
    }

    private static void registerUsbClose(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("device", stringProperty("USB 设备标识"));
        server.addTool("dlqq_usb_close", "关闭指定 USB 设备。",
                schema(properties, "device"),
                args -> {
                    try {
                        UsbDevices.close(str(args, "device"));
                        return ok("已关闭 USB 设备 " + str(args, "device"));
                    } catch (RuntimeException e) {
                        return err("关闭 USB 设备失败: " + e.getMessage());
                    }
                });
    }

    private static void registerUsbSend(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("device", stringProperty("USB 设备标识"));
        properties.put("data", stringProperty("要发送的数据；format=hex 时填写 HEX 字符串"));
        properties.put("format", stringProperty("text 或 hex，默认 text"));
        server.addTool("dlqq_usb_send", "向指定 USB 设备批量端点发送数据。",
                schema(properties, "device", "data"),
                args -> {
                    try {
                        if ("hex".equalsIgnoreCase(str(args, "format"))) {
                            UsbDevices.sendHex(str(args, "device"), str(args, "data"));
                        } else {
                            UsbDevices.sendText(str(args, "device"), str(args, "data"));
                        }
                        return ok("已发送到 USB 设备 " + str(args, "device"));
                    } catch (RuntimeException e) {
                        return err("USB 发送失败: " + e.getMessage());
                    }
                });
    }

    private static void registerUsbGetLog(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("device", stringProperty("USB 设备标识"));
        properties.put("lines", intProperty("返回最近多少行，默认 100"));
        server.addTool("dlqq_usb_get_log", "获取指定 USB 设备的收发日志。",
                schema(properties, "device"),
                args -> {
                    UsbDevices.DeviceSession session = UsbDevices.get(str(args, "device"));
                    if (session == null) {
                        return err("USB 设备未打开: " + str(args, "device"));
                    }
                    int limit = args.get("lines") == null ? 100 : intValue(args.get("lines"), 100);
                    List<String> lines = session.lines();
                    int from = Math.max(0, lines.size() - Math.max(1, limit));
                    return ok(String.join("\n", lines.subList(from, lines.size())));
                });
    }

    // ==================== WiFi ====================

    private static void registerWifiInterfaces(McpServer server) {
        server.addTool("dlqq_wifi_interfaces", "列出无线网卡接口及其当前连接状态。",
                schema(Collections.emptyMap()),
                args -> {
                    try {
                        List<String> lines = new ArrayList<>();
                        for (WifiDevices.InterfaceInfo info : WifiDevices.interfaces()) {
                            lines.add(info.name() + "  状态=" + info.state() + "  SSID=" + info.ssid());
                        }
                        return ok(lines.isEmpty() ? "未发现无线网卡" : String.join("\n", lines));
                    } catch (RuntimeException e) {
                        return err("获取无线网卡失败: " + e.getMessage());
                    }
                });
    }

    private static void registerWifiScan(McpServer server) {
        server.addTool("dlqq_wifi_scan", "扫描周围的 WiFi 网络（返回 SSID/信号/认证，失败时返回系统原始提示）。",
                schema(Collections.emptyMap()),
                args -> {
                    try {
                        List<String> lines = new ArrayList<>();
                        for (WifiDevices.Network network : WifiDevices.scan()) {
                            lines.add(network.ssid() + "  信号=" + network.signal()
                                    + "  认证=" + network.authentication());
                        }
                        if (!lines.isEmpty()) {
                            return ok(String.join("\n", lines));
                        }
                        StringBuilder message = new StringBuilder("未扫描到 WiFi 网络");
                        String raw = WifiDevices.lastScanOutput();
                        if (raw != null && !raw.isBlank()) {
                            message.append("\n\n系统输出:\n").append(raw.trim());
                        }
                        message.append("\n\n提示：Windows 扫描 WLAN 需要开启“定位服务（位置权限）”，"
                                + "必要时以管理员身份运行服务端；无线网卡还需处于启用状态。");
                        return ok(message.toString());
                    } catch (RuntimeException e) {
                        return err("扫描 WiFi 失败: " + e.getMessage());
                    }
                });
    }

    private static void registerWifiStatus(McpServer server) {
        server.addTool("dlqq_wifi_status", "查询当前无线连接状态。",
                schema(Collections.emptyMap()),
                args -> {
                    try {
                        return ok(WifiDevices.status());
                    } catch (RuntimeException e) {
                        return err("获取 WiFi 状态失败: " + e.getMessage());
                    }
                });
    }

    private static void registerWifiConnect(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("ssid", stringProperty("要连接的 WiFi 名称（系统里需已有该网络的配置文件）"));
        server.addTool("dlqq_wifi_connect", "连接指定 WiFi 网络（使用系统已有配置文件）。",
                schema(properties, "ssid"),
                args -> {
                    try {
                        return ok(WifiDevices.connect(str(args, "ssid")));
                    } catch (RuntimeException e) {
                        return err("连接 WiFi 失败: " + e.getMessage());
                    }
                });
    }

    private static void registerWifiDisconnect(McpServer server) {
        server.addTool("dlqq_wifi_disconnect", "断开当前 WiFi 连接。",
                schema(Collections.emptyMap()),
                args -> {
                    try {
                        return ok(WifiDevices.disconnect());
                    } catch (RuntimeException e) {
                        return err("断开 WiFi 失败: " + e.getMessage());
                    }
                });
    }

    // ==================== BLE ====================

    private static void registerBleScan(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("timeout", intProperty("扫描时长（秒），默认 5"));
        server.addTool("dlqq_ble_scan", "扫描 BLE 设备（由无界面 Python bleak 后端执行）。",
                schema(properties),
                args -> {
                    int timeout = args.get("timeout") == null ? 5 : intValue(args.get("timeout"), 5);
                    JsonObject response = BleDevices.scan(timeout);
                    return response.has("success") && response.get("success").getAsBoolean()
                            ? ok("扫描完成: " + response) : err("BLE 扫描失败: " + response);
                });
    }

    private static void registerBleGetDevices(McpServer server) {
        server.addTool("dlqq_ble_get_devices", "获取最近一次 BLE 扫描到的设备列表（含 MAC 地址）。",
                schema(Collections.emptyMap()),
                args -> {
                    List<String> lines = new ArrayList<>();
                    for (JsonObject device : BleDevices.devices()) {
                        String address = device.has("address") ? device.get("address").getAsString() : "";
                        String name = device.has("name") ? device.get("name").getAsString() : "";
                        String rssi = device.has("rssi") ? device.get("rssi").getAsString() : "";
                        lines.add(address + "  " + name + "  RSSI=" + rssi);
                    }
                    return ok(lines.isEmpty() ? "尚未扫描到 BLE 设备，请先调用 dlqq_ble_scan" : String.join("\n", lines));
                });
    }

    private static void registerBleConnect(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("address", stringProperty("BLE 设备 MAC 地址"));
        server.addTool("dlqq_ble_connect", "按 MAC 地址连接 BLE 设备（可同时连接多个设备）。",
                schema(properties, "address"),
                args -> result(BleDevices.connect(str(args, "address")), "连接 BLE 设备失败"));
    }

    private static void registerBleDisconnect(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("address", stringProperty("BLE 设备 MAC 地址"));
        server.addTool("dlqq_ble_disconnect", "断开指定 BLE 设备。",
                schema(properties, "address"),
                args -> result(BleDevices.disconnect(str(args, "address")), "断开 BLE 设备失败"));
    }

    private static void registerBleDiscover(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("address", stringProperty("BLE 设备 MAC 地址"));
        server.addTool("dlqq_ble_discover", "发现指定 BLE 设备的服务与特征。",
                schema(properties, "address"),
                args -> {
                    JsonArray services = BleDevices.discover(str(args, "address"));
                    if (services.isEmpty()) {
                        return err("未发现服务或设备未连接: " + str(args, "address"));
                    }
                    StringBuilder builder = new StringBuilder();
                    for (JsonElement element : services) {
                        JsonObject service = element.getAsJsonObject();
                        builder.append("Service ").append(service.get("uuid").getAsString()).append('\n');
                        if (service.has("characteristics")) {
                            for (JsonElement characteristic : service.getAsJsonArray("characteristics")) {
                                JsonObject object = characteristic.getAsJsonObject();
                                builder.append("  ").append(object.get("uuid").getAsString())
                                        .append("  ").append(object.has("properties")
                                                ? object.get("properties") : "").append('\n');
                            }
                        }
                    }
                    return ok(builder.toString());
                });
    }

    private static void registerBleRead(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("address", stringProperty("BLE 设备 MAC 地址"));
        properties.put("characteristic", stringProperty("特征 UUID"));
        server.addTool("dlqq_ble_read", "读取 BLE 特征值（返回 HEX）。",
                schema(properties, "address", "characteristic"),
                args -> {
                    JsonObject response = BleDevices.read(str(args, "address"), str(args, "characteristic"));
                    if (!response.has("success") || !response.get("success").getAsBoolean()) {
                        return err("读取 BLE 特征失败: " + response);
                    }
                    byte[] data = new byte[0];
                    if (response.has("data") && response.get("data").isJsonArray()) {
                        JsonArray array = response.getAsJsonArray("data");
                        data = new byte[array.size()];
                        for (int i = 0; i < data.length; i++) {
                            data[i] = (byte) array.get(i).getAsInt();
                        }
                    }
                    return ok(hex(data));
                });
    }

    private static void registerBleWrite(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("address", stringProperty("BLE 设备 MAC 地址"));
        properties.put("characteristic", stringProperty("特征 UUID"));
        properties.put("data", stringProperty("要写入的数据；format=hex 时填写 HEX 字符串"));
        properties.put("format", stringProperty("text 或 hex，默认 text"));
        server.addTool("dlqq_ble_write", "写入 BLE 特征值（文本或 HEX）。",
                schema(properties, "address", "characteristic", "data"),
                args -> {
                    String text = str(args, "data");
                    byte[] data;
                    if ("hex".equalsIgnoreCase(str(args, "format"))) {
                        data = fromHex(text);
                        if (data == null) {
                            return err("HEX 数据格式错误");
                        }
                    } else {
                        data = text.getBytes(StandardCharsets.UTF_8);
                    }
                    return result(BleDevices.write(str(args, "address"), str(args, "characteristic"), data),
                            "写入 BLE 特征失败");
                });
    }

    private static void registerBleSubscribe(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("address", stringProperty("BLE 设备 MAC 地址"));
        properties.put("characteristic", stringProperty("特征 UUID"));
        server.addTool("dlqq_ble_subscribe", "订阅 BLE 特征通知（通知会显示在控制台 BLE 面板）。",
                schema(properties, "address", "characteristic"),
                args -> result(BleDevices.subscribe(str(args, "address"), str(args, "characteristic")),
                        "订阅 BLE 特征失败"));
    }

    private static void registerBleUnsubscribe(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("address", stringProperty("BLE 设备 MAC 地址"));
        properties.put("characteristic", stringProperty("特征 UUID"));
        server.addTool("dlqq_ble_unsubscribe", "取消订阅 BLE 特征通知。",
                schema(properties, "address", "characteristic"),
                args -> result(BleDevices.unsubscribe(str(args, "address"), str(args, "characteristic")),
                        "取消订阅 BLE 特征失败"));
    }

    private static void registerBleGetLog(McpServer server) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("lines", intProperty("返回最近多少行，默认 100"));
        server.addTool("dlqq_ble_get_log", "获取 BLE 后端日志。",
                schema(properties),
                args -> {
                    int lines = args.get("lines") == null ? 100 : intValue(args.get("lines"), 100);
                    JsonObject response = BleDevices.logs(lines);
                    if (!response.has("success") || !response.get("success").getAsBoolean()) {
                        return err("获取 BLE 日志失败: " + response);
                    }
                    List<String> result = new ArrayList<>();
                    if (response.has("logs") && response.get("logs").isJsonArray()) {
                        for (JsonElement element : response.getAsJsonArray("logs")) {
                            result.add(element.getAsString());
                        }
                    }
                    return ok(String.join("\n", result));
                });
    }

    // ==================== helpers ====================

    private static CallToolResult result(JsonObject response, String errorPrefix) {
        if (response != null && response.has("success") && response.get("success").getAsBoolean()) {
            return ok(response.toString());
        }
        return err(errorPrefix + ": " + (response == null ? "无响应" : response.toString()));
    }

    private static Map<String, Object> schemaMap() {
        return new LinkedHashMap<>();
    }

    private static ToolInputSchema schema(Map<String, Object> properties, String... required) {
        List<String> requiredList = required.length == 0
                ? Collections.emptyList() : List.of(required);
        return new ToolInputSchema(properties, requiredList);
    }

    private static Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> intProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }

    private static String str(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value, int defaultValue) {
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
            return data;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String hex(byte[] data) {
        StringBuilder builder = new StringBuilder();
        for (byte value : data) {
            builder.append(String.format("%02X ", value & 0xFF));
        }
        return builder.toString().trim();
    }

    private static CallToolResult ok(String text) {
        return new CallToolResult(Collections.singletonList(ToolContent.text(text)), false);
    }

    private static CallToolResult err(String text) {
        return new CallToolResult(Collections.singletonList(ToolContent.text(text)), true);
    }
}
