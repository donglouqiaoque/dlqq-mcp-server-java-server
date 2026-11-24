package com.donglouqiaoque.dlqqmcpserver.mcp.device;

import org.usb4java.ConfigDescriptor;
import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.EndpointDescriptor;
import org.usb4java.Interface;
import org.usb4java.InterfaceDescriptor;
import org.usb4java.LibUsb;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * USB 设备管理器：按设备标识（VID:PID[:bus:address]）管理设备会话，不依赖窗口与实例端口。
 */
public final class UsbDevices {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Context CONTEXT = new Context();
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase().contains("win");
    private static final Pattern USB_ID_PATTERN =
            Pattern.compile("VID_([0-9A-Fa-f]{4})&PID_([0-9A-Fa-f]{4})");

    static {
        try {
            LibUsb.init(CONTEXT);
        } catch (Throwable ignored) {
            // 无 USB 原生库时仅禁用 USB 面板，不影响服务器启动
        }
    }

    /** 扫描结果。 */
    public record DeviceInfo(String key, String label, int vendorId, int productId, int busNumber, int address,
                             String manufacturer, String product, String serial) {
    }

    /** 已打开设备会话。 */
    public static final class DeviceSession {
        private final String key;
        private final Device device;
        private final DeviceHandle handle;
        private final Context context;
        private final int interfaceNumber;
        private final byte inEndpoint;
        private final byte outEndpoint;
        private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        private final List<Consumer<DeviceSession>> listeners = new CopyOnWriteArrayList<>();
        private final AtomicBoolean reading = new AtomicBoolean(true);
        private volatile boolean open = true;

        private DeviceSession(String key, Device device, DeviceHandle handle, Context context,
                              int interfaceNumber, byte inEndpoint, byte outEndpoint) {
            this.key = key;
            this.device = device;
            this.handle = handle;
            this.context = context;
            this.interfaceNumber = interfaceNumber;
            this.inEndpoint = inEndpoint;
            this.outEndpoint = outEndpoint;
        }

        public String key() {
            return key;
        }

        public boolean isOpen() {
            return open;
        }

        public boolean canRead() {
            return inEndpoint != 0;
        }

        public boolean canWrite() {
            return outEndpoint != 0;
        }

        public List<String> lines() {
            synchronized (lines) {
                return new ArrayList<>(lines);
            }
        }

        public String logText() {
            return String.join("\n", lines());
        }

        public void addListener(Consumer<DeviceSession> listener) {
            listeners.add(listener);
        }

        public void removeListener(Consumer<DeviceSession> listener) {
            listeners.remove(listener);
        }

        private void append(String direction, byte[] data) {
            String text = new String(data, StandardCharsets.UTF_8).replaceAll("[\\r\\n]+$", "");
            String line = "[" + TIME.format(LocalDateTime.now()) + "] " + direction + ": " + text;
            synchronized (lines) {
                lines.add(line);
                if (lines.size() > 20000) {
                    lines.subList(0, lines.size() - 20000).clear();
                }
            }
            notifyListeners();
        }

        private void appendSystem(String text) {
            synchronized (lines) {
                lines.add("[" + TIME.format(LocalDateTime.now()) + "] SYS: " + text);
            }
            notifyListeners();
        }

        private void notifyListeners() {
            for (Consumer<DeviceSession> listener : listeners) {
                try {
                    listener.accept(this);
                } catch (RuntimeException ignored) {
                    // 视图异常不影响设备读写
                }
            }
        }
    }

    private static final Map<String, DeviceSession> SESSIONS = new ConcurrentHashMap<>();

    private UsbDevices() {
    }

    /** 扫描当前 USB 设备（只返回有 VID/PID 的设备），并读取厂商/产品/序列号名称。 */
    public static List<DeviceInfo> scan() {
        List<DeviceInfo> result = new ArrayList<>();
        DeviceList list = new DeviceList();
        int rc = LibUsb.getDeviceList(CONTEXT, list);
        if (rc < 0) {
            return result;
        }
        Map<String, String> pnpNames = WINDOWS ? loadWindowsDeviceNames() : Map.of();
        try {
            for (Device device : list) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                if (LibUsb.getDeviceDescriptor(device, descriptor) != LibUsb.SUCCESS) {
                    continue;
                }
                int bus = LibUsb.getBusNumber(device);
                int address = LibUsb.getDeviceAddress(device);
                int vendorId = descriptor.idVendor() & 0xFFFF;
                int productId = descriptor.idProduct() & 0xFFFF;
                String key = String.format("%04X:%04X:%d:%d", vendorId, productId, bus, address);

                String manufacturer = "";
                String product = "";
                String serial = "";
                DeviceHandle handle = new DeviceHandle();
                if (LibUsb.open(device, handle) == LibUsb.SUCCESS) {
                    manufacturer = readStringDescriptor(handle, descriptor.iManufacturer());
                    product = readStringDescriptor(handle, descriptor.iProduct());
                    serial = readStringDescriptor(handle, descriptor.iSerialNumber());
                    LibUsb.close(handle);
                }
                if (product.isBlank() && manufacturer.isBlank()) {
                    String pnpName = pnpNames.get(String.format("%04X:%04X", vendorId, productId));
                    if (pnpName != null && !pnpName.isBlank()) {
                        product = pnpName;
                    }
                }

                String name = !product.isBlank() ? product
                        : (!manufacturer.isBlank() ? manufacturer : "(未知设备)");
                StringBuilder label = new StringBuilder(String.format("[%d-%d] %04X:%04X  %s",
                        bus, address, vendorId, productId, name));
                if (!manufacturer.isBlank() && !manufacturer.equals(name)) {
                    label.append(" (").append(manufacturer).append(')');
                }
                if (!serial.isBlank()) {
                    label.append("  SN:").append(serial);
                }
                result.add(new DeviceInfo(key, label.toString(), vendorId, productId, bus, address,
                        manufacturer, product, serial));
            }
        } finally {
            LibUsb.freeDeviceList(list, true);
        }
        result.sort((a, b) -> a.label().compareToIgnoreCase(b.label()));
        return result;
    }

    /**
     * Windows 上通过 PnP 设备名兜底：部分系统/HID 设备无法通过 libusb 打开读取字符串描述符，
     * 这里按 VID:PID 从 Win32_PnPEntity 中取友好名称。
     */
    private static Map<String, String> loadWindowsDeviceNames() {
        Map<String, String> names = new HashMap<>();
        try {
            String script = "Get-CimInstance Win32_PnPEntity | Where-Object { $_.DeviceID -like 'USB\\VID_*' }"
                    + " | ForEach-Object { $_.DeviceID + '|' + $_.Name }";
            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-Command", script);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            byte[] bytes = process.getInputStream().readAllBytes();
            if (!process.waitFor(12, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return names;
            }
            for (String line : decodeConsole(bytes).split("\\R")) {
                String[] parts = line.split("\\|", 2);
                if (parts.length != 2 || parts[1].isBlank()) {
                    continue;
                }
                Matcher matcher = USB_ID_PATTERN.matcher(parts[0]);
                if (matcher.find()) {
                    String vidPid = (matcher.group(1) + ":" + matcher.group(2)).toUpperCase();
                    names.putIfAbsent(vidPid, parts[1].trim());
                }
            }
        } catch (Exception ignored) {
            // PnP 名称只是增强显示，失败不影响扫描
        }
        return names;
    }

    private static String decodeConsole(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (Exception e) {
            try {
                return new String(bytes, Charset.forName("GBK"));
            } catch (RuntimeException ignored) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        }
    }

    private static String readStringDescriptor(DeviceHandle handle, byte index) {
        if (index == 0) {
            return "";
        }
        try {
            String value = LibUsb.getStringDescriptor(handle, index);
            return value == null ? "" : value.trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** 已打开的设备会话。 */
    public static List<DeviceSession> openSessions() {
        List<DeviceSession> result = new ArrayList<>(SESSIONS.values());
        result.sort((a, b) -> a.key().compareToIgnoreCase(b.key()));
        return result;
    }

    public static DeviceSession get(String key) {
        return key == null ? null : SESSIONS.get(key.trim());
    }

    /** 按扫描结果打开设备（需传入 scan() 返回的 key）。 */
    public static synchronized DeviceSession open(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("USB 设备标识不能为空");
        }
        if (SESSIONS.containsKey(key)) {
            throw new IllegalStateException("USB 设备已打开: " + key);
        }
        Device found = null;
        DeviceList list = new DeviceList();
        int rc = LibUsb.getDeviceList(CONTEXT, list);
        if (rc < 0) {
            throw new IllegalStateException("USB 设备列表获取失败: " + LibUsb.strError(rc));
        }
        try {
            for (Device device : list) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                if (LibUsb.getDeviceDescriptor(device, descriptor) != LibUsb.SUCCESS) {
                    continue;
                }
                String deviceKey = String.format("%04X:%04X:%d:%d",
                        descriptor.idVendor() & 0xFFFF, descriptor.idProduct() & 0xFFFF,
                        LibUsb.getBusNumber(device), LibUsb.getDeviceAddress(device));
                if (deviceKey.equalsIgnoreCase(key)) {
                    found = device;
                    LibUsb.refDevice(found);
                    break;
                }
            }
        } finally {
            LibUsb.freeDeviceList(list, true);
        }
        if (found == null) {
            throw new IllegalArgumentException("USB 设备不存在: " + key);
        }

        Context context = new Context();
        LibUsb.init(context);
        DeviceHandle handle = new DeviceHandle();
        rc = LibUsb.open(found, handle);
        if (rc != LibUsb.SUCCESS) {
            LibUsb.exit(context);
            throw new IllegalStateException("打开 USB 设备失败: " + LibUsb.strError(rc));
        }

        int[] location = findEndpoints(found);
        int interfaceNumber = location[0];
        byte inEndpoint = (byte) location[1];
        byte outEndpoint = (byte) location[2];
        if (interfaceNumber < 0) {
            LibUsb.close(handle);
            LibUsb.exit(context);
            throw new IllegalStateException("未找到可用的 USB 接口/端点: " + key);
        }
        rc = LibUsb.claimInterface(handle, interfaceNumber);
        if (rc != LibUsb.SUCCESS) {
            LibUsb.close(handle);
            LibUsb.exit(context);
            throw new IllegalStateException("占用 USB 接口失败: " + LibUsb.strError(rc));
        }

        DeviceSession session = new DeviceSession(key, found, handle, context,
                interfaceNumber, inEndpoint, outEndpoint);
        SESSIONS.put(key, session);
        session.appendSystem("已打开，接口=" + interfaceNumber
                + (inEndpoint != 0 ? " IN=0x" + String.format("%02X", inEndpoint & 0xFF) : "")
                + (outEndpoint != 0 ? " OUT=0x" + String.format("%02X", outEndpoint & 0xFF) : ""));
        if (inEndpoint != 0) {
            startReadThread(session);
        }
        return session;
    }

    /** 发送文本（UTF-8）。 */
    public static void sendText(String key, String text) {
        byte[] data = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        write(key, data);
    }

    /** 发送 HEX（允许空格）。 */
    public static void sendHex(String key, String hex) {
        String cleaned = hex == null ? "" : hex.replaceAll("\\s+", "");
        if (cleaned.isEmpty() || (cleaned.length() & 1) != 0) {
            throw new IllegalArgumentException("HEX 数据必须是非空偶数长度字符串");
        }
        byte[] data = new byte[cleaned.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        write(key, data);
    }

    public static synchronized void close(String key) {
        DeviceSession session = SESSIONS.remove(key);
        if (session == null) {
            return;
        }
        session.reading.set(false);
        try {
            LibUsb.releaseInterface(session.handle, session.interfaceNumber);
        } catch (RuntimeException ignored) {
            // ignore
        }
        try {
            LibUsb.close(session.handle);
        } catch (RuntimeException ignored) {
            // ignore
        }
        try {
            LibUsb.exit(session.context);
        } catch (RuntimeException ignored) {
            // ignore
        }
        LibUsb.unrefDevice(session.device);
        session.open = false;
    }

    public static synchronized void closeAll() {
        for (String key : new ArrayList<>(SESSIONS.keySet())) {
            close(key);
        }
    }

    private static void write(String key, byte[] data) {
        DeviceSession session = get(key);
        if (session == null) {
            throw new IllegalStateException("USB 设备未打开: " + key);
        }
        if (session.outEndpoint == 0) {
            throw new IllegalStateException("该 USB 设备没有可写端点: " + key);
        }
        if (!session.open) {
            throw new IllegalStateException("USB 设备已关闭: " + key);
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(Math.max(data.length, 1));
        buffer.put(data);
        buffer.flip();
        IntBuffer transferred = IntBuffer.allocate(1);
        int rc = LibUsb.bulkTransfer(session.handle, session.outEndpoint, buffer, transferred, 3000);
        if (rc != LibUsb.SUCCESS) {
            throw new IllegalStateException("USB 发送失败: " + LibUsb.strError(rc));
        }
        session.append("TX", data);
    }

    private static void startReadThread(DeviceSession session) {
        Thread thread = new Thread(() -> {
            while (session.reading.get() && session.open) {
                try {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
                    IntBuffer transferred = IntBuffer.allocate(1);
                    int rc = LibUsb.bulkTransfer(session.handle, session.inEndpoint,
                            buffer, transferred, 200);
                    if (rc == LibUsb.SUCCESS) {
                        int count = transferred.get();
                        if (count > 0) {
                            byte[] data = new byte[count];
                            buffer.position(0);
                            buffer.get(data);
                            session.append("RX", data);
                        }
                    } else if (rc != LibUsb.ERROR_TIMEOUT) {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (RuntimeException e) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "usb-read-" + session.key);
        thread.setDaemon(true);
        thread.start();
    }

    /** 返回 {interfaceNumber, inEndpoint, outEndpoint}；找不到返回 {-1,0,0}。 */
    private static int[] findEndpoints(Device device) {
        ConfigDescriptor config = new ConfigDescriptor();
        if (LibUsb.getActiveConfigDescriptor(device, config) != LibUsb.SUCCESS) {
            return new int[]{-1, 0, 0};
        }
        try {
            int interfaceNumber = -1;
            byte in = 0;
            byte out = 0;
            Interface[] interfaces = config.iface();
            if (interfaces == null) {
                return new int[]{-1, 0, 0};
            }
            for (int i = 0; i < interfaces.length && i < config.bNumInterfaces(); i++) {
                Interface iface = interfaces[i];
                if (iface == null || iface.altsetting() == null) {
                    continue;
                }
                for (int j = 0; j < iface.altsetting().length && j < iface.numAltsetting(); j++) {
                    InterfaceDescriptor altsetting = iface.altsetting()[j];
                    if (altsetting == null || altsetting.endpoint() == null) {
                        continue;
                    }
                    for (int k = 0; k < altsetting.endpoint().length && k < altsetting.bNumEndpoints(); k++) {
                        EndpointDescriptor endpoint = altsetting.endpoint()[k];
                        if (endpoint == null) {
                            continue;
                        }
                        int transferType = endpoint.bmAttributes() & 0x03;
                        if (transferType != 2 && transferType != 3) {
                            continue;
                        }
                        byte address = endpoint.bEndpointAddress();
                        if ((address & LibUsb.ENDPOINT_DIR_MASK) == LibUsb.ENDPOINT_IN) {
                            if (in == 0) {
                                in = address;
                            }
                        } else if (out == 0) {
                            out = address;
                        }
                        interfaceNumber = altsetting.bInterfaceNumber();
                    }
                }
            }
            return new int[]{interfaceNumber, in, out};
        } finally {
            LibUsb.freeConfigDescriptor(config);
        }
    }
}
