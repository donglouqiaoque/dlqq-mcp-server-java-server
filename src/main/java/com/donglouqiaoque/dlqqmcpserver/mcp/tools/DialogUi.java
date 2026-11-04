package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Dimension;
import java.awt.Image;

/**
 * 对话框通用外观工具：统一 LOGO 与按内容自适应窗口大小。
 */
final class DialogUi {

    private DialogUi() {
    }

    /**
     * 设置窗口标题栏 LOGO。
     */
    static void applyIcon(JFrame frame, String toolName) {
        LogoLoader.applyTo(frame, toolName);
    }

    /**
     * 返回内容区使用的 LOGO 标签（按边长缩放，失败时返回空标签）。
     */
    static JLabel logoLabel(int size) {
        Image logo = LogoLoader.load();
        if (logo == null) {
            return new JLabel();
        }
        Image scaled = logo.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new JLabel(new ImageIcon(scaled));
    }

    /**
     * 按内容自适应大小并居中；最小尺寸等于内容尺寸，避免出现滚动或需要手动放大。
     */
    static void fit(JFrame frame) {
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
    }

    /**
     * 根据消息文本估算显示区域大小，避免窗口被压成一小块。
     */
    static void sizeMessageArea(JTextArea area, JScrollPane scroll, String message) {
        int width = 560;
        int charsPerLine = Math.max(24, (width - 24) / 8);
        int lines = 0;
        String[] textLines = message == null ? new String[0] : message.split("\n", -1);
        for (String line : textLines) {
            lines += Math.max(1, (line.length() + charsPerLine - 1) / charsPerLine);
        }
        int height = Math.min(420, Math.max(120, lines * 22 + 16));
        area.setColumns(charsPerLine);
        area.setRows(Math.max(3, lines));
        scroll.setPreferredSize(new Dimension(width, height));
    }
}
