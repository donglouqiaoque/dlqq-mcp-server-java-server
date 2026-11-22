package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import javax.imageio.ImageIO;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具窗口 LOGO 加载器。
 *
 * <p>统一从 jar 资源 <code>/image/logo.png</code> 加载窗口图标。
 * 使用 PNG 而非 ICO 的原因：JDK 内置 ImageIO 不支持 ICO 格式，
 * 直接读取会导致图标加载失败（返回 null）。</p>
 *
 * <p>图片在服务启动时预加载并缓存：运行中替换 JAR 会让运行进程的
 * 资源查找间歇性失败，缓存可保证窗口图标始终可用；同时用多尺寸
 * {@link Frame#setIconImages(List)} 与系统任务栏图标，避免 Windows
 * 任务栏回退到默认 Java 图标。</p>
 */
public final class LogoLoader {

    /** 启动时预加载的 LOGO（JAR 被替换后仍可继续使用）。 */
    private static volatile Image cachedLogo;

    private LogoLoader() {
    }

    /**
     * 服务启动时预加载 LOGO，避免运行中替换 JAR 导致资源查找失败。
     */
    public static void preload() {
        load();
    }

    /**
     * 加载窗口 LOGO 图片（带进程内缓存）。
     *
     * @return Logo 图片；资源不存在或加载失败时返回 null（调用方应静默降级，不影响窗口功能）
     */
    static Image load() {
        Image cached = cachedLogo;
        if (cached != null) {
            return cached;
        }
        try (InputStream is = LogoLoader.class.getResourceAsStream("/image/logo.png")) {
            if (is == null) {
                return null;
            }
            Image logo = ImageIO.read(is);
            if (logo != null) {
                cachedLogo = logo;
                applyTaskbarIcon(logo);
            }
            return logo;
        } catch (Exception e) {
            System.err.println("[LogoLoader] 加载 logo.png 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 为窗口设置 LOGO 图标（失败时静默降级）。
     */
    static void applyTo(Window window, String toolName) {
        try {
            Image logo = load();
            if (logo != null) {
                window.setIconImage(logo);
                if (window instanceof Frame frame) {
                    frame.setIconImages(iconSizes(logo));
                }
                System.err.println("[" + toolName + "] 窗口图标已设置 (logo.png)");
            } else {
                System.err.println("[" + toolName + "] 警告: 未找到图标资源 /image/logo.png");
            }
        } catch (Exception e) {
            System.err.println("[" + toolName + "] 设置窗口图标失败: " + e.getMessage());
        }
    }

    /** 生成多尺寸图标列表，供 Windows 标题栏/任务栏按 DPI 选用。 */
    private static List<Image> iconSizes(Image logo) {
        int[] sizes = {16, 20, 24, 32, 40, 48, 64, 128, 256};
        List<Image> images = new ArrayList<>(sizes.length);
        for (int size : sizes) {
            images.add(logo.getScaledInstance(size, size, Image.SCALE_SMOOTH));
        }
        return images;
    }

    /** 设置系统任务栏图标（Windows 任务栏优先使用它；不支持时静默忽略）。 */
    private static void applyTaskbarIcon(Image logo) {
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(logo);
                }
            }
        } catch (Exception ignored) {
            // 无任务栏（headless / 非 Windows）时忽略
        }
    }
}
