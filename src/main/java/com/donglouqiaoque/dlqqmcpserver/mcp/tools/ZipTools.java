package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import com.donglouqiaoque.dlqqmcpserver.utils.FileUtils;
import com.donglouqiaoque.dlqqmcpserver.utils.Workspace;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 压缩/解压缩工具 - 支持ZIP(带密码)、7Z、TAR、GZ、BZ2、XZ、RAR、CPIO、AR等格式
 */
public class ZipTools {

    /** Gson 实例（用于 asar 索引的解析与序列化） */
    private static final Gson GSON = new Gson();

    /**
     * 支持的压缩格式（解压）
     */
    public static final List<String> SUPPORTED_FORMATS = Arrays.asList(
        "zip", "jar", "war", "ear", "7z", "tar", "tar.gz", "tgz", "tar.bz2", "tbz2",
        "tar.xz", "txz", "gz", "bz2", "xz", "zstd", "zst", "tar.zst", "tzst",
        "asar", "rar", "cpio", "ar"
    );

    /**
     * 压缩文件或目录（目前仅支持zip格式）
     * @param sourcePaths 源文件/目录路径列表（支持多个文件/目录）
     * @param outputPath 输出压缩文件路径（可选，默认为源目录下的压缩包）
     * @param format 压缩格式（可选，默认为zip）
     * @param password 压缩密码（可选，为空表示不加密）
     * @param compressionLevel 压缩级别（1-9，默认为5）
     * @return 包含操作结果的信息
     */
    public static Map<String, Object> zipFiles(List<String> sourcePaths, String outputPath, 
                                                String format, String password, Integer compressionLevel) throws IOException {
        if (sourcePaths == null || sourcePaths.isEmpty()) {
            throw new IOException("源文件路径不能为空");
        }

        // 验证并规范化源路径
        List<File> sourceFiles = new ArrayList<>();
        for (String sourcePath : sourcePaths) {
            String normalizedPath = FileUtils.normalizePath(sourcePath);
            File sourceFile = Workspace.resolve(normalizedPath).toFile();
            if (!sourceFile.exists()) {
                throw new IOException("源文件/目录不存在: " + sourcePath);
            }
            sourceFiles.add(sourceFile);
        }

        // 确定压缩格式
        if (format == null || format.trim().isEmpty()) {
            format = "zip";
        }
        format = format.toLowerCase().trim();
        if ("zstd".equals(format) || "zst".equals(format)
                || "tar.zst".equals(format) || "tzst".equals(format)) {
            return zipToZstd(sourceFiles, outputPath, format, compressionLevel);
        }
        if ("asar".equals(format)) {
            return zipToAsar(sourceFiles, outputPath);
        }
        if (!"zip".equals(format)) {
            throw new IOException("压缩支持 zip 与 zstd（单文件生成 .zst，多文件/目录生成 .tar.zst）");
        }

        // 确定输出路径
        Path outputFilePath;
        if (outputPath != null && !outputPath.trim().isEmpty()) {
            String normalizedOutput = FileUtils.normalizePath(outputPath);
            outputFilePath = Workspace.resolve(normalizedOutput);
            String permissionError = FileTools.checkWritePermission(outputFilePath.toString(), "ZIP 压缩输出");
            if (permissionError != null) {
                throw new IOException(permissionError);
            }
        } else {
            // 默认输出到 .dlqq/cache 目录
            File firstSource = sourceFiles.get(0);
            String baseName = firstSource.getName();
            if (!firstSource.isDirectory()) {
                int lastDot = baseName.lastIndexOf('.');
                if (lastDot > 0) {
                    baseName = baseName.substring(0, lastDot);
                }
            }
            Path cacheDir = McpTools.ensureCacheDir();
            outputFilePath = cacheDir.resolve(baseName + ".zip");
        }

        // 确保输出目录存在
        Files.createDirectories(outputFilePath.getParent());

        // 如果输出文件已存在，删除它
        if (Files.exists(outputFilePath)) {
            Files.delete(outputFilePath);
        }

        // 设置压缩参数
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        
        // 设置压缩级别
        int level = (compressionLevel != null && compressionLevel >= 1 && compressionLevel <= 9) ? compressionLevel : 5;
        CompressionLevel[] levels = {
            CompressionLevel.FASTEST, CompressionLevel.FASTEST, CompressionLevel.FAST,
            CompressionLevel.FAST, CompressionLevel.NORMAL, CompressionLevel.NORMAL,
            CompressionLevel.HIGHER, CompressionLevel.HIGHER, CompressionLevel.MAXIMUM
        };
        zipParameters.setCompressionLevel(levels[level - 1]);

        // 设置加密
        boolean usePassword = password != null && !password.trim().isEmpty();
        if (usePassword) {
            zipParameters.setEncryptFiles(true);
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
            zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        }

        // 创建ZIP文件
        ZipFile zipFile = usePassword ? new ZipFile(outputFilePath.toFile(), password.toCharArray())
                                       : new ZipFile(outputFilePath.toFile());

        // 添加文件到压缩包
        for (File sourceFile : sourceFiles) {
            if (sourceFile.isDirectory()) {
                zipFile.addFolder(sourceFile, zipParameters);
            } else {
                zipFile.addFile(sourceFile, zipParameters);
            }
        }

        // 获取压缩结果信息
        long originalSize = calculateTotalSize(sourceFiles);
        long compressedSize = Files.size(outputFilePath);
        double compressionRatio = originalSize > 0 ? (1.0 - (double) compressedSize / originalSize) * 100 : 0;

        return Map.of(
            "outputPath", outputFilePath.toString(),
            "format", format,
            "originalSize", originalSize,
            "compressedSize", compressedSize,
            "compressionRatio", String.format("%.2f%%", compressionRatio),
            "fileCount", sourceFiles.size(),
            "passwordProtected", usePassword
        );
    }

    /**
     * 压缩为 zstd：单个普通文件生成 .zst；目录或多个源先打 tar 再压成 .tar.zst。
     */
    private static Map<String, Object> zipToZstd(List<File> sourceFiles, String outputPath,
                                                 String format, Integer compressionLevel) throws IOException {
        int level = compressionLevel == null ? 5 : Math.max(1, Math.min(9, compressionLevel));
        boolean plainFile = sourceFiles.size() == 1 && sourceFiles.get(0).isFile();
        boolean wantTar = "tar.zst".equals(format) || "tzst".equals(format) || !plainFile;

        Path target;
        if (outputPath != null && !outputPath.trim().isEmpty()) {
            target = Workspace.resolve(FileUtils.normalizePath(outputPath));
            String permissionError = FileTools.checkWritePermission(target.toString(), "zstd 压缩输出");
            if (permissionError != null) {
                throw new IOException(permissionError);
            }
        } else {
            Path cacheDir = McpTools.ensureCacheDir();
            File first = sourceFiles.get(0);
            target = cacheDir.resolve(first.getName() + (wantTar ? ".tar.zst" : ".zst"));
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        try (OutputStream fileOut = Files.newOutputStream(target)) {
            if (wantTar) {
                try (ZstdOutputStream zout = new ZstdOutputStream(fileOut, level);
                     TarArchiveOutputStream tarOut = new TarArchiveOutputStream(zout)) {
                    tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                    for (File source : sourceFiles) {
                        addToTar(tarOut, source, source.getName());
                    }
                    tarOut.finish();
                }
            } else {
                try (ZstdOutputStream zout = new ZstdOutputStream(fileOut, level);
                     InputStream in = Files.newInputStream(sourceFiles.get(0).toPath())) {
                    IOUtils.copy(in, zout);
                }
            }
        }

        return Map.of(
            "format", wantTar ? "tar.zst" : "zstd",
            "outputPath", target.toString(),
            "sourceCount", sourceFiles.size(),
            "compressedSize", Files.size(target)
        );
    }

    /** 把文件或目录递归写入 tar 流 */
    private static void addToTar(TarArchiveOutputStream tarOut, File file, String entryName) throws IOException {
        if (file.isDirectory()) {
            String dirName = entryName.endsWith("/") ? entryName : entryName + "/";
            tarOut.putArchiveEntry(new TarArchiveEntry(file, dirName));
            tarOut.closeArchiveEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToTar(tarOut, child, dirName + child.getName());
                }
            }
        } else {
            tarOut.putArchiveEntry(new TarArchiveEntry(file, entryName));
            try (InputStream in = Files.newInputStream(file.toPath())) {
                IOUtils.copy(in, tarOut);
            }
            tarOut.closeArchiveEntry();
        }
    }
    /**
     * 解压缩文件（支持多种格式：zip/7z/tar/gz/bz2/xz/rar等）
     * @param zipPath 压缩文件路径
     * @param outputDir 输出目录（可选，默认为压缩文件所在目录下的同名文件夹）
     * @param password 解压密码（可选，仅zip格式支持）
     * @return 包含操作结果的信息
     */
    public static Map<String, Object> unzipFile(String zipPath, String outputDir, String password) throws IOException {
        return unzipFile(zipPath, outputDir, password, 0);
    }

    /**
     * 解压压缩文件。
     *
     * @param detectMode 格式判定模式：
     *                   0 = 严格按后缀名（后缀不支持直接报错；实际格式与后缀不符则报错）
     *                   1 = 先按后缀名，不符或无法识别时探测真实格式
     *                   2 = 忽略后缀名，只按文件内容探测，探测不出则报错
     */
    public static Map<String, Object> unzipFile(String zipPath, String outputDir, String password,
                                                int detectMode) throws IOException {
        if (zipPath == null || zipPath.trim().isEmpty()) {
            throw new IOException("压缩文件路径不能为空");
        }

        // 规范化路径
        String normalizedZipPath = FileUtils.normalizePath(zipPath);
        Path zipFilePath = Workspace.resolve(normalizedZipPath);

        if (!Files.exists(zipFilePath)) {
            throw new IOException("压缩文件不存在: " + zipPath);
        }

        if (!Files.isRegularFile(zipFilePath)) {
            throw new IOException("路径不是文件: " + zipPath);
        }

        // 按 detectMode 解析格式
        String fileName = zipFilePath.getFileName().toString().toLowerCase();
        String ext = resolveArchiveFormat(zipFilePath, fileName, detectMode);

        // 确定输出目录
        Path outputPath;
        if (outputDir != null && !outputDir.trim().isEmpty()) {
            String normalizedOutput = FileUtils.normalizePath(outputDir);
            outputPath = Workspace.resolve(normalizedOutput);
            String permissionError = FileTools.checkWritePermission(outputPath.toString(), "ZIP 解压输出");
            if (permissionError != null) {
                throw new IOException(permissionError);
            }
        } else {
            // 默认输出到 .dlqq/cache 目录下的同名文件夹
            String baseName = stripArchiveExt(fileName);
            Path cacheDir = McpTools.ensureCacheDir();
            outputPath = cacheDir.resolve(baseName);
        }

        // 确保输出目录存在
        Files.createDirectories(outputPath);

        List<String> fileList;

        if ("zip".equals(ext)) {
            // zip格式使用zip4j（支持密码）
            fileList = extractZip(zipFilePath, outputPath, password);
        } else {
            // 其他格式使用 commons-compress
            if (password != null && !password.trim().isEmpty()) {
                throw new IOException("当前格式 (" + ext + ") 不支持密码解压，密码仅适用于ZIP格式");
            }
            fileList = extractWithCommonsCompress(zipFilePath, outputPath, ext);
        }

        // 计算解压后的大小
        long extractedSize = calculateDirectorySize(outputPath.toFile());

        return Map.of(
            "zipPath", zipPath,
            "outputDir", outputPath.toString(),
            "format", ext,
            "fileCount", fileList.size(),
            "extractedSize", extractedSize,
            "files", fileList.subList(0, Math.min(fileList.size(), 20)), // 最多显示20个文件
            "hasMoreFiles", fileList.size() > 20
        );
    }
    /**
     * 按 detectMode 解析压缩格式。
     */
    private static String resolveArchiveFormat(Path file, String fileName, int detectMode) throws IOException {
        String ext = getArchiveExt(fileName);
        String detected = detectFormatByMagic(file);
        if (detectMode == 0) {
            if (ext == null) {
                throw new IOException("不支持的文件格式: " + fileName + "，支持的格式: " + getSupportedFormats());
            }
            if (detected != null && !familyMatches(ext, detected)) {
                throw new IOException("文件实际格式为 " + detected + "，与后缀不匹配: " + fileName
                        + "（确认无误时可改用 detect_mode=1 或 2）");
            }
            return ext;
        }
        if (detectMode == 2) {
            if (detected == null) {
                throw new IOException("无法根据文件内容判断压缩格式: " + fileName);
            }
            return mapDetectedToExt(detected);
        }
        // detectMode == 1：先信后缀，不符或识别不出时用探测结果
        if (ext != null && (detected == null || familyMatches(ext, detected))) {
            return ext;
        }
        if (detected != null) {
            return mapDetectedToExt(detected);
        }
        if (ext != null) {
            return ext;
        }
        throw new IOException("无法判断压缩格式: " + fileName);
    }

    /** 探测出的裸格式映射为解压用的格式名 */
    private static String mapDetectedToExt(String detected) {
        return detected;
    }

    /** 后缀格式与探测格式是否属于同一族（如 tar.gz 与 gz） */
    private static boolean familyMatches(String ext, String detected) {
        String base = ext.startsWith("tar.") ? ext.substring(4) : ext;
        if ("tgz".equals(base)) base = "gz";
        if ("tbz2".equals(base) || "tbz".equals(base)) base = "bz2";
        if ("txz".equals(base)) base = "xz";
        if ("tzst".equals(base) || "zst".equals(base)) base = "zstd";
        return base.equals(detected);
    }

    /**
     * 通过文件头魔数判断压缩格式，无法判断时返回 null。
     */
    private static String detectFormatByMagic(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = new byte[512];
            int read = in.readNBytes(head, 0, head.length);
            if (read < 4) {
                return null;
            }
            if ((head[0] & 0xFF) == 0x50 && (head[1] & 0xFF) == 0x4B
                    && ((head[2] & 0xFF) == 0x03 || (head[2] & 0xFF) == 0x05 || (head[2] & 0xFF) == 0x07)) {
                return "zip";
            }
            if ((head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B) {
                return "gz";
            }
            if (head[0] == 'B' && head[1] == 'Z' && head[2] == 'h') {
                return "bz2";
            }
            if (read >= 6 && (head[0] & 0xFF) == 0xFD && head[1] == '7' && head[2] == 'z'
                    && head[3] == 'X' && head[4] == 'Z' && head[5] == 0x00) {
                return "xz";
            }
            if ((head[0] & 0xFF) == 0x28 && (head[1] & 0xFF) == 0xB5
                    && (head[2] & 0xFF) == 0x2F && (head[3] & 0xFF) == 0xFD) {
                return "zstd";
            }
            if (read >= 6 && (head[0] & 0xFF) == 0x37 && head[1] == 'z' && (head[2] & 0xFF) == 0xBC
                    && (head[3] & 0xFF) == 0xAF && head[4] == 0x27 && head[5] == 0x1C) {
                return "7z";
            }
            if (read >= 7 && head[0] == 'R' && head[1] == 'a' && head[2] == 'r'
                    && head[3] == '!' && head[4] == 0x1A && head[5] == 0x07) {
                return "rar";
            }
            if (read >= 262 && head[257] == 'u' && head[258] == 's' && head[259] == 't'
                    && head[260] == 'a' && head[261] == 'r') {
                return "tar";
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 列出压缩文件内容（支持多种格式）
     * @param zipPath 压缩文件路径
     * @param password 密码（可选，仅zip格式支持）
     * @return 文件内容列表
     */
    public static Map<String, Object> listZipContents(String zipPath, String password) throws IOException {
        if (zipPath == null || zipPath.trim().isEmpty()) {
            throw new IOException("压缩文件路径不能为空");
        }

        String normalizedZipPath = FileUtils.normalizePath(zipPath);
        Path zipFilePath = Workspace.resolve(normalizedZipPath);

        if (!Files.exists(zipFilePath)) {
            throw new IOException("压缩文件不存在: " + zipPath);
        }

        String fileName = zipFilePath.getFileName().toString().toLowerCase();
        String ext = getArchiveExt(fileName);
        if (ext == null) {
            throw new IOException("不支持的文件格式: " + fileName + "，支持的格式: " + getSupportedFormats());
        }

        if ("zip".equals(ext)) {
            // zip格式使用zip4j（支持密码）
            boolean usePassword = password != null && !password.trim().isEmpty();
            ZipFile zipFile = usePassword ? new ZipFile(zipFilePath.toFile(), password.toCharArray())
                                           : new ZipFile(zipFilePath.toFile());

            if (!zipFile.isValidZipFile()) {
                throw new IOException("无效的ZIP文件: " + zipPath);
            }

            List<Map<String, Object>> fileList = zipFile.getFileHeaders().stream()
                .map(header -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", header.getFileName());
                    m.put("size", header.getUncompressedSize());
                    m.put("compressedSize", header.getCompressedSize());
                    m.put("isDirectory", header.isDirectory());
                    m.put("crc", header.getCrc());
                    m.put("lastModified", header.getLastModifiedTime());
                    return m;
                })
                .collect(Collectors.toList());

            long totalSize = fileList.stream().mapToLong(f -> ((Number) f.get("size")).longValue()).sum();
            long totalCompressed = fileList.stream().mapToLong(f -> ((Number) f.get("compressedSize")).longValue()).sum();

            return Map.of(
                "zipPath", zipPath,
                "format", ext,
                "fileCount", fileList.size(),
                "totalSize", totalSize,
                "totalCompressedSize", totalCompressed,
                "encrypted", zipFile.isEncrypted(),
                "files", fileList
            );
        } else {
            if (password != null && !password.trim().isEmpty()) {
                throw new IOException("当前格式 (" + ext + ") 不支持密码，密码仅适用于ZIP格式");
            }
            return listWithCommonsCompress(zipFilePath, ext);
        }
    }

    /**
     * 使用zip4j解压zip文件
     */
    private static List<String> extractZip(Path zipFilePath, Path outputPath, String password) throws IOException {
        boolean usePassword = password != null && !password.trim().isEmpty();
        ZipFile zipFile = usePassword ? new ZipFile(zipFilePath.toFile(), password.toCharArray())
                                       : new ZipFile(zipFilePath.toFile());

        // 检查是否是有效的ZIP文件
        if (!zipFile.isValidZipFile()) {
            throw new IOException("无效的ZIP文件: " + zipFilePath);
        }

        // 检查是否需要密码
        if (zipFile.isEncrypted() && !usePassword) {
            throw new IOException("ZIP文件需要密码，请提供password参数");
        }

        // 获取文件列表信息
        List<String> fileList = zipFile.getFileHeaders().stream()
            .map(header -> header.getFileName())
            .collect(Collectors.toList());

        // 解压
        zipFile.extractAll(outputPath.toString());

        return fileList;
    }

    /**
     * 使用 commons-compress 解压（支持 7z/tar/gz/bz2/xz/rar/cpio/ar 等）
     * 按扩展名明确分发，避免自动检测流的 mark/reset 问题
     */
    private static List<String> extractWithCommonsCompress(Path archivePath, Path outputPath, String ext) throws IOException {
        List<String> fileList = new ArrayList<>();
        try (InputStream rawIn = new BufferedInputStream(Files.newInputStream(archivePath))) {
            switch (ext) {
                case "gz":
                case "bz2":
                case "xz":
                case "zstd":
                case "zst": {
                    // 单文件压缩（无归档结构）
                    InputStream comp = createCompressorStream(rawIn, ext);
                    String outName = stripLastExtension(archivePath.getFileName().toString());
                    Path target = outputPath.resolve(outName).normalize();
                    try (OutputStream out = Files.newOutputStream(target)) {
                        IOUtils.copy(comp, out);
                    }
                    fileList.add(outName);
                    break;
                }
                case "tar.gz":
                case "tgz":
                case "tar.bz2":
                case "tbz2":
                case "tbz":
                case "tar.xz":
                case "txz":
                case "tar.zst":
                case "tzst": {
                    // tar + 压缩
                    InputStream comp = createCompressorStream(rawIn, ext);
                    extractTarEntries(comp, outputPath, fileList);
                    break;
                }
                case "tar": {
                    extractTarEntries(rawIn, outputPath, fileList);
                    break;
                }
                case "asar": {
                    // asar 是 Electron 归档（非压缩），需按索引偏移随机读取
                    extractAsar(archivePath, outputPath, fileList);
                    break;
                }
                case "7z": {
                    // 7z不支持流式读取，需使用 SevenZFile 随机访问文件
                    extractSevenZ(archivePath, outputPath, fileList);
                    break;
                }
                default: {
                    // 7z/rar/cpio/ar等：自动检测归档格式
                    try (ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(
                            new BufferedInputStream(rawIn, 8192))) {
                        extractArchiveEntries(ais, outputPath, fileList);
                    } catch (ArchiveException e) {
                        throw new IOException("无法识别的归档格式: " + archivePath.getFileName() + "（" + e.getMessage() + "）");
                    }
                }
            }
        }
        return fileList;
    }

    /**
     * 创建解压缩流（gzip/bzip2/xz）
     */
    private static InputStream createCompressorStream(InputStream in, String ext) throws IOException {
        switch (ext) {
            case "gz":
            case "tar.gz":
            case "tgz":
                return new GzipCompressorInputStream(in);
            case "bz2":
            case "tar.bz2":
            case "tbz2":
            case "tbz":
                return new BZip2CompressorInputStream(in);
            case "xz":
            case "tar.xz":
            case "txz":
                return new XZCompressorInputStream(in);
            case "zstd":
            case "zst":
            case "tar.zst":
            case "tzst":
                return new ZstdInputStream(in);
            default:
                return in;
        }
    }

    /**
     * 解压7z文件（SevenZFile 需要随机访问文件，不支持流式）
     */
    private static void extractSevenZ(Path archivePath, Path outputPath, List<String> fileList) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(archivePath.toFile())) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path target = outputPath.resolve(entry.getName()).normalize();
                if (!target.startsWith(outputPath)) {
                    throw new IOException("检测到非法路径（路径穿越）: " + entry.getName());
                }
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    // SevenZFile 不是 InputStream，需手动循环读取
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = sevenZFile.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
                fileList.add(entry.getName());
            }
        }
    }

    /**
     * 解压tar条目
     */
    private static void extractTarEntries(InputStream in, Path outputPath, List<String> fileList) throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path target = outputPath.resolve(entry.getName()).normalize();
                if (!target.startsWith(outputPath)) {
                    throw new IOException("检测到非法路径（路径穿越）: " + entry.getName());
                }
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    IOUtils.copy(tis, out);
                }
                fileList.add(entry.getName());
            }
        }
    }

    /**
     * 解压通用归档条目（7z/rar/cpio/ar/zip等）
     */
    private static void extractArchiveEntries(ArchiveInputStream ais, Path outputPath, List<String> fileList) throws IOException {
        ArchiveEntry entry;
        while ((entry = ais.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            Path target = outputPath.resolve(entry.getName()).normalize();
            if (!target.startsWith(outputPath)) {
                throw new IOException("检测到非法路径（路径穿越）: " + entry.getName());
            }
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target)) {
                IOUtils.copy(ais, out);
            }
            fileList.add(entry.getName());
        }
    }

    /**
     * 使用 commons-compress 列出压缩文件内容
     */
    private static Map<String, Object> listWithCommonsCompress(Path archivePath, String ext) throws IOException {
        List<Map<String, Object>> fileList = new ArrayList<>();
        long totalSize = 0;
        long totalCompressed = 0;

        try (InputStream rawIn = new BufferedInputStream(Files.newInputStream(archivePath))) {
            switch (ext) {
                case "gz":
                case "bz2":
                case "xz": {
                    // 单文件压缩：无法列出内部条目，显示解压后的文件名
                    Map<String, Object> m = new LinkedHashMap<>();
                    String outName = stripLastExtension(archivePath.getFileName().toString());
                    m.put("name", outName);
                    m.put("size", -1L);
                    m.put("compressedSize", Files.size(archivePath));
                    m.put("isDirectory", false);
                    m.put("crc", 0L);
                    m.put("lastModified", Files.getLastModifiedTime(archivePath).toMillis());
                    fileList.add(m);
                    break;
                }
                case "tar.gz":
                case "tgz":
                case "tar.bz2":
                case "tbz2":
                case "tbz":
                case "tar.xz":
                case "txz": {
                    InputStream comp = createCompressorStream(rawIn, ext);
                    totalSize += listTarEntries(comp, fileList);
                    break;
                }
                case "tar": {
                    totalSize += listTarEntries(rawIn, fileList);
                    break;
                }
                case "7z": {
                    // 7z不支持流式读取，使用 SevenZFile 列出
                    try (SevenZFile sevenZFile = new SevenZFile(archivePath.toFile())) {
                        SevenZArchiveEntry entry;
                        while ((entry = sevenZFile.getNextEntry()) != null) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("name", entry.getName());
                            m.put("size", entry.getSize());
                            m.put("compressedSize", entry.getSize());
                            m.put("isDirectory", entry.isDirectory());
                            m.put("crc", 0L);
                            m.put("lastModified", entry.getLastModifiedDate());
                            fileList.add(m);
                            if (!entry.isDirectory()) {
                                totalSize += entry.getSize();
                            }
                        }
                    }
                    break;
                }
                default: {
                    try (ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(
                            new BufferedInputStream(rawIn, 8192))) {
                        ArchiveEntry entry;
                        while ((entry = ais.getNextEntry()) != null) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("name", entry.getName());
                            m.put("size", entry.getSize());
                            m.put("compressedSize", entry.getSize());
                            m.put("isDirectory", entry.isDirectory());
                            m.put("crc", 0L);
                            m.put("lastModified", entry.getLastModifiedDate());
                            fileList.add(m);
                            if (!entry.isDirectory()) {
                                totalSize += entry.getSize();
                                totalCompressed += entry.getSize();
                            }
                        }
                    } catch (ArchiveException e) {
                        throw new IOException("无法识别的归档格式: " + archivePath.getFileName() + "（" + e.getMessage() + "）");
                    }
                }
            }
        }

        totalCompressed = Math.max(totalCompressed, totalSize);
        return Map.of(
            "zipPath", archivePath.toString(),
            "format", ext,
            "fileCount", fileList.size(),
            "totalSize", totalSize,
            "totalCompressedSize", totalCompressed,
            "encrypted", false,
            "files", fileList
        );
    }

    /**
     * 列出tar条目，返回非目录条目的总大小
     */
    private static long listTarEntries(InputStream in, List<Map<String, Object>> fileList) throws IOException {
        long totalSize = 0;
        try (TarArchiveInputStream tis = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", entry.getName());
                m.put("size", entry.getSize());
                m.put("compressedSize", entry.getSize());
                m.put("isDirectory", entry.isDirectory());
                m.put("crc", 0L);
                m.put("lastModified", entry.getLastModifiedDate());
                fileList.add(m);
                if (!entry.isDirectory()) {
                    totalSize += entry.getSize();
                }
            }
        }
        return totalSize;
    }

    /**
     * 去掉最后一个扩展名（如 file1.txt.gz → file1.txt）
     */
    private static String stripLastExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName + ".out";
    }

    /**
     * 根据文件名判断归档格式
     */
    private static String getArchiveExt(String fileName) {
        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) return "tar.gz";
        if (fileName.endsWith(".tar.bz2") || fileName.endsWith(".tbz2") || fileName.endsWith(".tbz")) return "tar.bz2";
        if (fileName.endsWith(".tar.xz") || fileName.endsWith(".txz")) return "tar.xz";
        if (fileName.endsWith(".tar.zst") || fileName.endsWith(".tzst")) return "tar.zst";
        if (fileName.endsWith(".tar")) return "tar";
        if (fileName.endsWith(".zip")) return "zip";
        if (fileName.endsWith(".jar") || fileName.endsWith(".war") || fileName.endsWith(".ear")) return "zip";
        if (fileName.endsWith(".7z")) return "7z";
        if (fileName.endsWith(".gz")) return "gz";
        if (fileName.endsWith(".bz2")) return "bz2";
        if (fileName.endsWith(".xz")) return "xz";
        if (fileName.endsWith(".zstd") || fileName.endsWith(".zst")) return "zstd";
        if (fileName.endsWith(".asar")) return "asar";
        if (fileName.endsWith(".rar")) return "rar";
        if (fileName.endsWith(".cpio")) return "cpio";
        if (fileName.endsWith(".ar")) return "ar";
        return null;
    }

    /**
     * 去掉归档扩展名得到默认输出目录名
     */
    private static String stripArchiveExt(String fileName) {
        String[] suffixes = {".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst", ".tar", ".zip", ".jar", ".war", ".ear",
                             ".7z", ".gz", ".bz2", ".xz", ".zstd", ".zst", ".asar", ".rar", ".cpio", ".ar",
                             ".tgz", ".tbz2", ".tbz", ".txz", ".tzst"};
        for (String suffix : suffixes) {
            if (fileName.endsWith(suffix)) {
                return fileName.substring(0, fileName.length() - suffix.length());
            }
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * 计算多个文件/目录的总大小
     */
    private static long calculateTotalSize(List<File> files) {
        long total = 0;
        for (File file : files) {
            total += calculateDirectorySize(file);
        }
        return total;
    }

    /**
     * 计算文件或目录的大小
     */
    private static long calculateDirectorySize(File file) {
        if (!file.exists()) {
            return 0;
        }
        if (file.isFile()) {
            return file.length();
        }
        long size = 0;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                size += calculateDirectorySize(f);
            }
        }
        return size;
    }

    /**
     * 检查是否是支持的压缩格式
     */
    public static boolean isSupportedFormat(String format) {
        if (format == null) {
            return false;
        }
        return SUPPORTED_FORMATS.contains(format.toLowerCase().trim());
    }

    /**
     * 获取支持的格式列表
     */
    public static String getSupportedFormats() {
        return String.join(", ", SUPPORTED_FORMATS);
    }

    // ==================== ASAR（Electron 归档） ====================

    /** asar 数据区起始偏移 = 8 + headerSize */
    private static long asarDataOffset(int headerSize) {
        return 8L + headerSize;
    }

    /**
     * 解压 asar 归档（Electron 打包格式，非压缩容器）。
     */
    private static List<String> extractAsar(Path archivePath, Path outputPath, List<String> fileList) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(archivePath.toFile(), "r")) {
            // 头部布局：[uint32 4][uint32 headerSize][...][json...]
            // 不依赖固定偏移：直接在头部查找 JSON 的起始 '{'
            byte[] head = new byte[64];
            raf.readFully(head);
            int headLen = head.length;
            ByteBuffer buf = ByteBuffer.wrap(head, 0, headLen).order(ByteOrder.LITTLE_ENDIAN);
            buf.getInt();
            int headerSize = buf.getInt();
            if (headerSize <= 0 || headerSize > 256 * 1024 * 1024) {
                throw new IOException("asar 头部异常: headerSize=" + headerSize);
            }
            int jsonStart = -1;
            for (int i = 8; i < headLen; i++) {
                if (head[i] == '{') {
                    jsonStart = i;
                    break;
                }
            }
            if (jsonStart < 0) {
                throw new IOException("asar 索引起始位置未找到");
            }
            long dataOffset = asarDataOffset(headerSize);
            long jsonLength = dataOffset - jsonStart;
            if (jsonLength <= 0 || jsonLength > 256L * 1024 * 1024) {
                throw new IOException("asar 索引长度异常: " + jsonLength);
            }
            raf.seek(jsonStart);
            byte[] jsonBytes = new byte[(int) jsonLength];
            raf.readFully(jsonBytes);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            JsonObject root;
            try {
                JsonReader reader = new JsonReader(new StringReader(json));
                reader.setLenient(true);
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                String preview = json.length() > 64 ? json.substring(0, 64) : json;
                throw new IOException("asar 索引解析失败: " + e.getMessage() + "，前 64 字符: " + preview);
            }
            JsonObject files = root.getAsJsonObject("files");
            if (files != null) {
                asarWalk(raf, files, "", outputPath, dataOffset, fileList);
            }
        }
        return fileList;
    }

    /** 递归遍历 asar 索引树并抽取文件 */
    private static void asarWalk(RandomAccessFile raf, JsonObject node, String prefix, Path outputPath,
                                 long dataOffset, List<String> fileList) throws IOException {
        for (Map.Entry<String, JsonElement> entry : node.entrySet()) {
            String name = entry.getKey();
            JsonObject item = entry.getValue().getAsJsonObject();
            String entryPath = prefix.isEmpty() ? name : prefix + "/" + name;
            if (item.has("files")) {
                asarWalk(raf, item.getAsJsonObject("files"), entryPath, outputPath, dataOffset, fileList);
                continue;
            }
            if (!item.has("offset")) {
                continue;
            }
            long offset = Long.parseLong(item.get("offset").getAsString());
            long size = item.get("size").getAsLong();
            Path target = outputPath.resolve(entryPath).normalize();
            if (!target.startsWith(outputPath)) {
                continue;
            }
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            raf.seek(dataOffset + offset);
            try (OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                long remain = size;
                while (remain > 0) {
                    int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remain));
                    if (read < 0) {
                        break;
                    }
                    out.write(buffer, 0, read);
                    remain -= read;
                }
            }
            fileList.add(entryPath);
        }
    }

    /**
     * 打包为 asar：目录或多个源合并为一棵树，单文件作为根条目。
     */
    private static Map<String, Object> zipToAsar(List<File> sources, String outputPath) throws IOException {
        Path target;
        if (outputPath != null && !outputPath.trim().isEmpty()) {
            target = Workspace.resolve(FileUtils.normalizePath(outputPath));
            String permissionError = FileTools.checkWritePermission(target.toString(), "asar 打包输出");
            if (permissionError != null) {
                throw new IOException(permissionError);
            }
        } else {
            Path cacheDir = McpTools.ensureCacheDir();
            target = cacheDir.resolve(sources.get(0).getName() + ".asar");
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        LinkedHashMap<String, File> entries = new LinkedHashMap<>();
        for (File source : sources) {
            collectAsarEntries(source, source.getName(), entries);
        }

        // 先算偏移（累计大小），再序列化索引，最后写头 + 数据
        LinkedHashMap<String, Long> offsets = new LinkedHashMap<>();
        long cursor = 0;
        for (Map.Entry<String, File> entry : entries.entrySet()) {
            offsets.put(entry.getKey(), cursor);
            cursor += entry.getValue().length();
        }

        JsonObject rootFiles = new JsonObject();
        for (Map.Entry<String, File> entry : entries.entrySet()) {
            String[] parts = entry.getKey().split("/");
            JsonObject parent = rootFiles;
            for (int i = 0; i < parts.length - 1; i++) {
                JsonObject child = parent.getAsJsonObject(parts[i]);
                if (child == null) {
                    child = new JsonObject();
                    child.add("files", new JsonObject());
                    parent.add(parts[i], child);
                }
                parent = child.getAsJsonObject("files");
            }
            JsonObject fileNode = new JsonObject();
            fileNode.addProperty("size", entry.getValue().length());
            fileNode.addProperty("offset", String.valueOf(offsets.get(entry.getKey())));
            parent.add(parts[parts.length - 1], fileNode);
        }
        JsonObject root = new JsonObject();
        root.add("files", rootFiles);

        byte[] jsonBytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        int headerSize = jsonBytes.length + 4;
        ByteBuffer header = ByteBuffer.allocate(8 + headerSize).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(4);
        header.putInt(headerSize);
        header.putInt(jsonBytes.length);
        header.put(jsonBytes);

        try (OutputStream out = Files.newOutputStream(target)) {
            out.write(header.array());
            byte[] buffer = new byte[64 * 1024];
            for (File file : entries.values()) {
                try (InputStream in = Files.newInputStream(file.toPath())) {
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
            }
        }

        return Map.of(
            "format", "asar",
            "outputPath", target.toString(),
            "sourceCount", sources.size(),
            "fileCount", entries.size(),
            "compressedSize", Files.size(target)
        );
    }

    /** 递归收集 asar 条目（路径 → 文件） */
    private static void collectAsarEntries(File file, String entryPath, Map<String, File> out) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectAsarEntries(child, entryPath + "/" + child.getName(), out);
                }
            }
        } else {
            out.put(entryPath, file);
        }
    }
}
