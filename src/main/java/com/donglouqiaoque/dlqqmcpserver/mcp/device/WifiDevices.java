package com.donglouqiaoque.dlqqmcpserver.mcp.device;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WiFi 设备管理器：按网卡接口名管理，扫描/连接/断开均通过系统无线命令执行。
 */
public final class WifiDevices {

    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase().contains("win");

    private static final Pattern FIELD_PATTERN = Pattern.compile("^\\s*([^:\\r\\n]+?)\\s*[:：]\\s*(.*?)\\s*$");
    private static final Pattern SSID_PATTERN = Pattern.compile("(?i)^SSID(?:\\s+\\d+)?\\s*[:：]\\s*(.*)$");
    private static final Pattern SIGNAL_PATTERN = Pattern.compile("^(信号|Signal)\\s*[:：]\\s*(.*)$");

    /** 网卡接口信息。 */
    public record InterfaceInfo(String name, String state, String ssid) {
    }

    /** 扫描到的网络。 */
    public record Network(String ssid, String signal, String authentication) {
    }

    private WifiDevices() {
    }

    /** 最近一次 WiFi 扫描命令的原始输出（失败时通常是权限/位置服务提示）。 */
    private static volatile String lastScanOutput = "";

    /** 列出无线网卡接口。 */
    public static List<InterfaceInfo> interfaces() {
        String output = WINDOWS
                ? run("netsh", "wlan", "show", "interfaces")
                : run("nmcli", "-t", "-f", "DEVICE,TYPE,STATE,CONNECTION", "device");
        return WINDOWS ? parseInterfaces(output) : parseNmcliInterfaces(output);
    }

    /** 最近一次扫描的原始输出，供界面/工具在扫描不到网络时展示原因。 */
    public static String lastScanOutput() {
        return lastScanOutput;
    }

    /** 扫描网络（使用默认网卡）。 */
    public static List<Network> scan() {
        String output;
        if (WINDOWS) {
            output = run("netsh", "wlan", "show", "networks", "mode=bssid");
        } else {
            output = run("nmcli", "-t", "-f", "SSID,SIGNAL,SECURITY", "device", "wifi", "list");
        }
        lastScanOutput = output;
        return WINDOWS ? parseNetworks(output) : parseNmcliNetworks(output);
    }

    /** 当前连接状态（默认网卡），返回原始命令输出，便于界面直接显示诊断信息。 */
    public static String status() {
        if (WINDOWS) {
            return run("netsh", "wlan", "show", "interfaces");
        }
        return run("nmcli", "device", "status");
    }

    /** 连接指定 SSID（需要系统里已有对应配置文件）。 */
    public static String connect(String ssid) {
        if (ssid == null || ssid.isBlank()) {
            throw new IllegalArgumentException("SSID 不能为空");
        }
        String output = WINDOWS
                ? run("netsh", "wlan", "connect", "name=" + ssid)
                : run("nmcli", "device", "wifi", "connect", ssid);
        String lower = output.toLowerCase();
        if (lower.contains("失败") || lower.contains("failed") || lower.contains("错误")
                || lower.contains("error")) {
            throw new IllegalStateException(output.trim());
        }
        return output.trim();
    }

    /** 断开当前 WiFi。 */
    public static String disconnect() {
        return WINDOWS ? run("netsh", "wlan", "disconnect") : run("nmcli", "device", "disconnect", "wifi");
    }

    private static String run(String... command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            byte[] bytes = process.getInputStream().readAllBytes();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("无线命令执行超时: " + String.join(" ", command));
            }
            return decode(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("无线命令执行失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("无线命令被中断", e);
        }
    }

    /**
     * 控制台中文输出编码嗅探：先严格按 UTF-8 解码，失败再按 GBK（中文 Windows 控制台）、最后 ISO-8859-1。
     */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            try {
                return new String(bytes, Charset.forName("GBK"));
            } catch (RuntimeException ignored) {
                return new String(bytes, StandardCharsets.ISO_8859_1);
            }
        }
    }

    private static List<InterfaceInfo> parseInterfaces(String output) {
        List<InterfaceInfo> result = new ArrayList<>();
        String name = null;
        String state = "";
        String ssid = "";
        for (String raw : output.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                if (name != null) {
                    result.add(new InterfaceInfo(name, state, ssid));
                    name = null;
                    state = "";
                    ssid = "";
                }
                continue;
            }
            Matcher matcher = FIELD_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String field = matcher.group(1);
            String value = matcher.group(2).trim();
            if (field.equals("名称") || field.equalsIgnoreCase("Name")) {
                name = value;
                state = "";
                ssid = "";
            } else if (field.equals("状态") || field.equalsIgnoreCase("State")) {
                state = value;
            } else if (field.equalsIgnoreCase("SSID") || field.equals("SSID")) {
                ssid = value;
            }
        }
        if (name != null) {
            result.add(new InterfaceInfo(name, state, ssid));
        }
        if (result.isEmpty()) {
            // 有些系统只输出 BSSID/描述等字段；退化为按 "WLAN"/"无线" 关键字提取名称
            for (String raw : output.split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty()) {
                    continue;
                }
                Matcher matcher = FIELD_PATTERN.matcher(line);
                if (matcher.matches()) {
                    String field = matcher.group(1);
                    String value = matcher.group(2).trim();
                    if ((field.equals("描述") || field.equalsIgnoreCase("Description"))
                            && !value.isEmpty()) {
                        result.add(new InterfaceInfo(value, "", ""));
                    }
                }
            }
        }
        return result;
    }

    private static List<InterfaceInfo> parseNmcliInterfaces(String output) {
        List<InterfaceInfo> result = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String[] parts = line.split(":", -1);
            if (parts.length >= 4 && "wifi".equalsIgnoreCase(parts[1])) {
                result.add(new InterfaceInfo(parts[0], parts[2], parts[3]));
            }
        }
        return result;
    }

    private static List<Network> parseNetworks(String output) {
        List<Network> result = new ArrayList<>();
        String ssid = null;
        String authentication = "";
        for (String raw : output.split("\\R")) {
            String line = raw.trim();
            Matcher ssidMatcher = SSID_PATTERN.matcher(line);
            if (ssidMatcher.matches() && !line.contains("BSSID")) {
                ssid = ssidMatcher.group(1).trim();
                authentication = "";
                continue;
            }
            Matcher signalMatcher = SIGNAL_PATTERN.matcher(line);
            if (signalMatcher.matches() && ssid != null && !ssid.isEmpty()) {
                result.add(new Network(ssid, signalMatcher.group(2).replace("%", "").trim(), authentication));
                ssid = null;
                continue;
            }
            Matcher matcher = FIELD_PATTERN.matcher(line);
            if (matcher.matches()) {
                String field = matcher.group(1);
                if (field.equals("身份验证") || field.equalsIgnoreCase("Authentication")) {
                    authentication = matcher.group(2).trim();
                }
            }
        }
        return result;
    }

    private static List<Network> parseNmcliNetworks(String output) {
        List<Network> result = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String[] parts = line.split(":", -1);
            if (parts.length >= 3 && !parts[0].isEmpty()) {
                result.add(new Network(parts[0], parts[1], parts[2]));
            }
        }
        return result;
    }
}
