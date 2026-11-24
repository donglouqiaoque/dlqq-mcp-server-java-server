package com.donglouqiaoque.dlqqmcpserver.mcp.device;

import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * BLE 后端客户端：管理无界面 Python bleak 服务进程（stdin/stdout JSON），
 * BLE UI 由 Java Swing 渲染，设备按 MAC 地址寻址。
 */
public final class BleDevices {

    private static final Gson GSON = new Gson();
    private static final AtomicInteger REQUEST_ID = new AtomicInteger();
    private static final Map<Integer, CompletableFuture<JsonObject>> PENDING = new ConcurrentHashMap<>();
    private static final List<Consumer<JsonObject>> EVENT_LISTENERS = new CopyOnWriteArrayList<>();

    private static Process process;
    private static BufferedWriter stdin;
    private static volatile boolean running;
    private static volatile boolean ready;
    private static boolean hookInstalled;

    private BleDevices() {
    }

    public static boolean isRunning() {
        return running && process != null && process.isAlive();
    }

    public static String backendInfo() {
        if (!isRunning()) {
            return "BLE 后端未启动";
        }
        return "BLE 后端进程 PID=" + process.pid();
    }

    /** 确保 Python 后端已启动（幂等）。 */
    public static synchronized void ensureStarted() {
        if (isRunning()) {
            return;
        }
        try {
            String pythonDir = releaseScript();
            if (pythonDir == null) {
                throw new IllegalStateException("无法释放 BLE 后端脚本");
            }
            String python = findPython();
            if (python == null) {
                throw new IllegalStateException("未找到项目自带的 python 运行时（python/python.exe）");
            }
            ProcessBuilder builder = new ProcessBuilder(python, "-s", "-P",
                    new File(pythonDir, "ble_service.py").getAbsolutePath());
            builder.directory(new File(pythonDir));
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            process = builder.start();
            stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            running = true;
            ready = false;
            startReader(process);
            installShutdownHook();
            JsonObject ping = request("ping", null, 8000);
            if (!ping.has("success") || !ping.get("success").getAsBoolean()) {
                throw new IllegalStateException("BLE 后端启动失败: " + ping);
            }
        } catch (RuntimeException e) {
            stop();
            throw e;
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("BLE 后端启动失败: " + e.getMessage(), e);
        }
    }

    /** 发送请求并等待响应。 */
    public static JsonObject request(String type, JsonObject params, int timeoutMillis) {
        if (!running) {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "BLE 后端未启动");
            return error;
        }
        int id = REQUEST_ID.incrementAndGet();
        JsonObject request = new JsonObject();
        request.addProperty("id", id);
        request.addProperty("type", type);
        if (params != null) {
            request.add("params", params);
        }
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        PENDING.put(id, future);
        try {
            synchronized (BleDevices.class) {
                stdin.write(request.toString());
                stdin.newLine();
                stdin.flush();
            }
            return future.get(Math.max(1000, timeoutMillis), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "BLE 通信失败: " + e.getMessage());
            return error;
        } finally {
            PENDING.remove(id);
        }
    }

    public static void addEventListener(Consumer<JsonObject> listener) {
        EVENT_LISTENERS.add(listener);
    }

    public static void removeEventListener(Consumer<JsonObject> listener) {
        EVENT_LISTENERS.remove(listener);
    }

    public static JsonObject scan(int timeoutSeconds) {
        ensureStarted();
        JsonObject params = new JsonObject();
        params.addProperty("timeout", Math.max(1, timeoutSeconds));
        return request("scan", params, (timeoutSeconds + 30) * 1000);
    }

    public static List<JsonObject> devices() {
        ensureStarted();
        List<JsonObject> result = new ArrayList<>();
        JsonObject response = request("get_devices", null, 10000);
        if (response.has("devices") && response.get("devices").isJsonArray()) {
            for (JsonElement element : response.getAsJsonArray("devices")) {
                if (element.isJsonObject()) {
                    result.add(element.getAsJsonObject());
                }
            }
        }
        return result;
    }

    public static JsonObject connect(String address) {
        ensureStarted();
        return request("connect", params("address", address), 45000);
    }

    public static JsonObject disconnect(String address) {
        ensureStarted();
        return request("disconnect", params("address", address), 25000);
    }

    public static JsonArray discover(String address) {
        ensureStarted();
        JsonObject response = request("discover", params("address", address), 35000);
        if (response.has("success") && response.get("success").getAsBoolean()
                && response.has("services") && response.get("services").isJsonArray()) {
            return response.getAsJsonArray("services");
        }
        return new JsonArray();
    }

    public static JsonObject read(String address, String characteristic) {
        ensureStarted();
        JsonObject params = params("address", address);
        params.addProperty("characteristic", characteristic);
        return request("read", params, 35000);
    }

    public static JsonObject write(String address, String characteristic, byte[] data) {
        ensureStarted();
        JsonObject params = params("address", address);
        params.addProperty("characteristic", characteristic);
        JsonArray bytes = new JsonArray();
        for (byte value : data) {
            bytes.add(value & 0xFF);
        }
        params.add("data", bytes);
        return request("write", params, 35000);
    }

    public static JsonObject subscribe(String address, String characteristic) {
        ensureStarted();
        JsonObject params = params("address", address);
        params.addProperty("characteristic", characteristic);
        return request("subscribe", params, 35000);
    }

    public static JsonObject unsubscribe(String address, String characteristic) {
        ensureStarted();
        JsonObject params = params("address", address);
        params.addProperty("characteristic", characteristic);
        return request("unsubscribe", params, 35000);
    }

    public static JsonObject logs(int lines) {
        ensureStarted();
        JsonObject params = new JsonObject();
        params.addProperty("lines", Math.max(1, lines));
        return request("get_log", params, 10000);
    }

    /** 停止 Python 后端。 */
    public static synchronized void stop() {
        running = false;
        if (stdin != null) {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("type", "exit");
                stdin.write(request.toString());
                stdin.newLine();
                stdin.flush();
            } catch (Exception ignored) {
                // ignore
            }
            stdin = null;
        }
        if (process != null) {
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
        for (CompletableFuture<JsonObject> future : PENDING.values()) {
            future.completeExceptionally(new IllegalStateException("BLE 后端已停止"));
        }
        PENDING.clear();
        ready = false;
    }

    private static JsonObject params(String key, String value) {
        JsonObject params = new JsonObject();
        params.addProperty(key, value);
        return params;
    }

    private static void startReader(Process owner) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(owner.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonObject message;
                    try {
                        message = GSON.fromJson(line, JsonObject.class);
                    } catch (RuntimeException e) {
                        System.err.println("[BleDevices] Invalid backend output: " + line);
                        continue;
                    }
                    if (message == null) {
                        continue;
                    }
                    if (message.has("id")) {
                        int id = message.get("id").getAsInt();
                        CompletableFuture<JsonObject> future = PENDING.remove(id);
                        if (future != null) {
                            future.complete(message);
                        }
                    } else if (message.has("event")) {
                        String event = message.get("event").getAsString();
                        if ("ready".equals(event)) {
                            ready = true;
                        }
                        for (Consumer<JsonObject> listener : EVENT_LISTENERS) {
                            try {
                                listener.accept(message);
                            } catch (RuntimeException ignored) {
                                // ignore listener errors
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[BleDevices] Backend reader stopped: " + e.getMessage());
                }
            } finally {
                ready = false;
            }
        }, "ble-backend-reader");
        thread.setDaemon(true);
        thread.start();
    }

    private static void installShutdownHook() {
        synchronized (BleDevices.class) {
            if (hookInstalled) {
                return;
            }
            hookInstalled = true;
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(BleDevices::stop, "ble-backend-reaper"));
            } catch (IllegalStateException ignored) {
                // JVM already shutting down
            }
        }
    }

    private static String releaseScript() {
        try {
            File dir = RuntimeDirs.getDataRoot().resolve("script").resolve("python").toFile();
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File target = new File(dir, "ble_service.py");
            try (InputStream input = BleDevices.class.getResourceAsStream("/py/ble_service.py");
                 FileOutputStream output = new FileOutputStream(target)) {
                if (input == null) {
                    return null;
                }
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            }
            return dir.getAbsolutePath();
        } catch (Exception e) {
            System.err.println("[BleDevices] Failed to release backend script: " + e.getMessage());
            return null;
        }
    }

    private static String findPython() {
        List<File> bases = new ArrayList<>();
        try {
            File codeSource = new File(BleDevices.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            File appDir = codeSource.isFile() ? codeSource.getParentFile() : codeSource;
            if (appDir != null) {
                bases.add(appDir);
            }
        } catch (Exception ignored) {
            // fall back below
        }
        bases.add(new File(System.getProperty("user.dir")));
        for (File base : bases) {
            File exe = new File(base, "python" + File.separator + "python.exe");
            if (exe.isFile()) {
                return exe.getAbsolutePath();
            }
        }
        return null;
    }
}
