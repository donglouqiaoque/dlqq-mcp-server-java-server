package com.donglouqiaoque.dlqqmcpserver.mcp.device;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * UART 设备管理器：按串口名称（COMx / ttyUSBx）管理设备会话，不依赖窗口与实例端口。
 */
public final class UartDevices {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int DEFAULT_MAX_LINES = 20000;

    /** 串口设备会话：一个对象对应一个已打开的串口。 */
    public static final class Device {
        private final String portName;
        private final String displayName;
        private final SerialPort serialPort;
        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        private final List<Consumer<Device>> listeners = new CopyOnWriteArrayList<>();
        private volatile boolean open = true;

        private Device(String portName, String displayName, SerialPort serialPort) {
            this.portName = portName;
            this.displayName = displayName;
            this.serialPort = serialPort;
        }

        public String portName() {
            return portName;
        }

        public String displayName() {
            return displayName;
        }

        public boolean isOpen() {
            return open;
        }

        public List<String> lines() {
            synchronized (lines) {
                return new ArrayList<>(lines);
            }
        }

        public String logText() {
            return String.join("\n", lines());
        }

        public void addListener(Consumer<Device> listener) {
            listeners.add(listener);
        }

        public void removeListener(Consumer<Device> listener) {
            listeners.remove(listener);
        }

        private void append(String direction, byte[] data, int length) {
            String text;
            try {
                text = new String(data, 0, length, StandardCharsets.UTF_8);
            } catch (RuntimeException e) {
                text = new String(data, 0, length, StandardCharsets.ISO_8859_1);
            }
            text = text.replaceAll("[\\r\\n]+$", "");
            String line = "[" + TIME.format(LocalDateTime.now()) + "] " + direction + ": " + text;
            synchronized (lines) {
                lines.add(line);
                if (lines.size() > DEFAULT_MAX_LINES) {
                    lines.subList(0, lines.size() - DEFAULT_MAX_LINES).clear();
                }
            }
            for (Consumer<Device> listener : listeners) {
                try {
                    listener.accept(this);
                } catch (RuntimeException ignored) {
                    // 视图异常不影响串口读写
                }
            }
        }

        private void markClosed() {
            open = false;
        }
    }

    private static final Map<String, Device> DEVICES = new ConcurrentHashMap<>();

    private UartDevices() {
    }

    /** 当前系统可用串口列表（名称 + 描述）。 */
    public static List<String> listPorts() {
        List<String> result = new ArrayList<>();
        for (SerialPort port : SerialPort.getCommPorts()) {
            result.add(port.getSystemPortName() + " - " + port.getDescriptivePortName());
        }
        return result;
    }

    /** 已打开的 UART 设备（按串口名排序）。 */
    public static List<Device> openDevices() {
        List<Device> result = new ArrayList<>(DEVICES.values());
        result.sort((a, b) -> a.portName().compareToIgnoreCase(b.portName()));
        return result;
    }

    /** 获取已打开的设备；不存在返回 null。 */
    public static Device get(String portName) {
        return portName == null ? null : DEVICES.get(portName.trim());
    }

    /**
     * 打开串口设备；同一串口重复打开会抛错。
     *
     * @param portName 串口名（如 COM3），也允许 "COM3 - 描述" 形式
     * @param baudRate 波特率，&lt;=0 时使用 1000000
     */
    public static synchronized Device open(String portName, int baudRate) {
        String name = normalizePortName(portName);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("串口名称不能为空");
        }
        if (DEVICES.containsKey(name)) {
            throw new IllegalStateException("串口已被打开: " + name);
        }
        SerialPort serialPort = findPort(name);
        if (serialPort == null) {
            throw new IllegalArgumentException("串口不存在: " + name);
        }
        int baud = baudRate > 0 ? baudRate : 1000000;
        serialPort.setBaudRate(baud);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        serialPort.setParity(SerialPort.NO_PARITY);
        if (!serialPort.openPort()) {
            throw new IllegalStateException("打开串口失败: " + name);
        }
        Device device = new Device(name, serialPort.getDescriptivePortName(), serialPort);
        serialPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
                    return;
                }
                byte[] data = event.getReceivedData();
                if (data != null && data.length > 0) {
                    device.append("RX", data, data.length);
                }
            }
        });
        DEVICES.put(name, device);
        device.append("SYS", ("已打开 " + name + " @" + baud).getBytes(StandardCharsets.UTF_8),
                ("已打开 " + name + " @" + baud).getBytes(StandardCharsets.UTF_8).length);
        return device;
    }

    /** 发送文本（UTF-8）。 */
    public static void sendText(String portName, String text) {
        Device device = require(portName);
        byte[] data = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        write(device, data);
        device.append("TX", data, data.length);
    }

    /** 发送 HEX 字符串（允许空格分隔）。 */
    public static void sendHex(String portName, String hex) {
        Device device = require(portName);
        String cleaned = hex == null ? "" : hex.replaceAll("\\s+", "");
        if (cleaned.isEmpty() || (cleaned.length() & 1) != 0) {
            throw new IllegalArgumentException("HEX 数据必须是非空偶数长度字符串");
        }
        byte[] data = new byte[cleaned.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        write(device, data);
        device.append("TX", data, data.length);
    }

    /** 关闭并移除设备。 */
    public static synchronized void close(String portName) {
        Device device = DEVICES.remove(normalizePortName(portName));
        if (device != null) {
            try {
                device.serialPort.removeDataListener();
            } catch (RuntimeException ignored) {
                // ignore
            }
            try {
                device.serialPort.closePort();
            } catch (RuntimeException ignored) {
                // ignore
            }
            device.markClosed();
        }
    }

    /** 关闭所有设备（MCP 退出/重启时调用）。 */
    public static synchronized void closeAll() {
        for (String name : new ArrayList<>(DEVICES.keySet())) {
            close(name);
        }
    }

    private static Device require(String portName) {
        Device device = get(portName);
        if (device == null) {
            throw new IllegalStateException("串口未打开: " + portName);
        }
        return device;
    }

    private static void write(Device device, byte[] data) {
        int written = device.serialPort.writeBytes(data, data.length);
        if (written < 0) {
            throw new IllegalStateException("串口写入失败: " + device.portName());
        }
    }

    private static String normalizePortName(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        int separator = text.indexOf(" - ");
        return separator > 0 ? text.substring(0, separator).trim() : text;
    }

    private static SerialPort findPort(String name) {
        for (SerialPort port : SerialPort.getCommPorts()) {
            if (port.getSystemPortName().equalsIgnoreCase(name)) {
                return port;
            }
        }
        return null;
    }
}
