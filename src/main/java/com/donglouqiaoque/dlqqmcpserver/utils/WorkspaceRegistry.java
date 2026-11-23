package com.donglouqiaoque.dlqqmcpserver.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 工作区注册表：维护"工作区绝对路径 ↔ 目录 hash"的持久映射。
 *
 * <p>hash 规则：路径规范化（绝对路径 + normalize + 小写）后取 SHA-256 十六进制前缀；
 * 最短 {@value #MIN_HASH_LENGTH} 位，碰撞时逐位加长到 {@value #MAX_HASH_LENGTH} 位，
 * 仍冲突则追加 {@code -1}、{@code -2}。映射写入
 * {@code <部署目录>/.dlqq/workspace_config.json}，供 cache/settings 目录名与清理工具反查。</p>
 */
public final class WorkspaceRegistry {

    /** hash 最短长度（十六进制字符数）。 */
    public static final int MIN_HASH_LENGTH = 8;
    /** hash 最长长度（十六进制字符数）。 */
    public static final int MAX_HASH_LENGTH = 32;

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String CONFIG_ROOT_KEY = "workspaces";
    private static final String LOCK_FILE_NAME = "workspace_config.lock";
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, String> PATH_TO_HASH = new LinkedHashMap<>();
    private static final Map<String, String> HASH_TO_PATH = new LinkedHashMap<>();
    private static volatile boolean initialized;

    private WorkspaceRegistry() {
    }

    /**
     * 载入工作区映射（幂等，可重复调用）。
     *
     * @throws IllegalStateException 映射文件损坏（启动失败）
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        RuntimeDirs.init();
        load();
        initialized = true;
    }

    /**
     * 取工作区对应的目录 hash；不存在时计算并持久化。
     *
     * @param workspace 工作区根目录
     * @return 目录 hash（如 {@code 3f8a12cd}）
     */
    public static synchronized String hashFor(Path workspace) {
        init();
        String pathKey = canonicalPathKey(workspace);
        String existing = PATH_TO_HASH.get(pathKey);
        if (existing != null) {
            return existing;
        }
        String hex = sha256Hex(pathKey);
        String assigned = null;
        for (int length = MIN_HASH_LENGTH; length <= MAX_HASH_LENGTH; length++) {
            String candidate = hex.substring(0, length);
            if (!HASH_TO_PATH.containsKey(candidate)) {
                assigned = candidate;
                break;
            }
        }
        if (assigned == null) {
            String full = hex.substring(0, MAX_HASH_LENGTH);
            int suffix = 1;
            while (HASH_TO_PATH.containsKey(full + "-" + suffix)) {
                suffix++;
            }
            assigned = full + "-" + suffix;
        }
        PATH_TO_HASH.put(pathKey, assigned);
        HASH_TO_PATH.put(assigned, pathKey);
        persist();
        return assigned;
    }

    /**
     * 按 hash 反查工作区路径（小写规范路径）。
     *
     * @param hash 目录 hash
     * @return 工作区绝对路径；未注册时返回 null
     */
    public static synchronized Path workspaceFor(String hash) {
        init();
        if (hash == null) {
            return null;
        }
        String path = HASH_TO_PATH.get(hash.trim());
        return path == null ? null : Path.of(path);
    }

    /** 全部映射（只读副本，key 为小写规范路径）。 */
    public static synchronized Map<String, String> entries() {
        init();
        return Collections.unmodifiableMap(new LinkedHashMap<>(PATH_TO_HASH));
    }

    /** 工作区路径的规范化键：绝对路径 + normalize + 小写。 */
    public static String canonicalPathKey(Path workspace) {
        return workspace.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private static void load() {
        Path file = RuntimeDirs.getWorkspaceConfigFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("根节点不是 JSON 对象");
            }
            JsonElement workspaces = parsed.getAsJsonObject().get(CONFIG_ROOT_KEY);
            if (workspaces == null || !workspaces.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : workspaces.getAsJsonObject().entrySet()) {
                register(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            RuntimeDirs.failStartup("读取工作区映射失败：" + file, e);
        }
    }

    private static void register(String pathKey, String hash) {
        String existingPath = HASH_TO_PATH.get(hash);
        if (existingPath != null && !existingPath.equals(pathKey)) {
            throw new IllegalStateException("工作区 hash 冲突：" + hash + " 同时映射到 " + existingPath + " 与 " + pathKey);
        }
        PATH_TO_HASH.put(pathKey, hash);
        HASH_TO_PATH.put(hash, pathKey);
    }

    private static void persist() {
        Path file = RuntimeDirs.getWorkspaceConfigFile();
        Path lockFile = file.resolveSibling(LOCK_FILE_NAME);
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            writeConfig(file);
        } catch (IOException e) {
            RuntimeDirs.failStartup("写入工作区映射失败：" + file, e);
        }
    }

    private static void writeConfig(Path file) throws IOException {
        JsonObject workspaces = new JsonObject();
        for (Map.Entry<String, String> entry : PATH_TO_HASH.entrySet()) {
            workspaces.addProperty(entry.getKey(), entry.getValue());
        }
        JsonObject root = new JsonObject();
        root.add(CONFIG_ROOT_KEY, workspaces);
        Path temp = file.resolveSibling(file.getFileName() + TEMP_FILE_SUFFIX);
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 " + HASH_ALGORITHM, e);
        }
    }
}
