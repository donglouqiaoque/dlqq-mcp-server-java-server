package com.donglouqiaoque.dlqqmcpserver.mcp;

import com.donglouqiaoque.dlqqmcpserver.BuildInfo;
import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 控制台「版权鸣谢」选项卡：展示作者信息、运行时/第三方组件许可与完整许可文件入口。
 */
public final class AboutTabPanel extends JPanel implements RefreshableTab {

    private static final String AUTHOR = "鸫楼桥鹊";
    private static final String WEBSITE = "https://www.donglouqiaoque.com";
    private static final String NOTICES_FILE = "THIRD-PARTY-NOTICES.txt";

    private final JLabel versionValue = new JLabel();
    private final JLabel buildTimeValue = new JLabel();
    private final JLabel noticesPathValue = new JLabel();
    private final JLabel actionStatus = new JLabel(" ");
    private final JTextArea noticesArea = new JTextArea();

    private static final DateTimeFormatter BUILD_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public AboutTabPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 8, 3));
        infoPanel.setBorder(BorderFactory.createTitledBorder("程序信息"));
        infoPanel.add(new JLabel("程序名称:"));
        infoPanel.add(new JLabel("鸫楼桥鹊 MCP 服务器"));
        infoPanel.add(new JLabel("作者:"));
        infoPanel.add(new JLabel(AUTHOR));
        infoPanel.add(new JLabel("联系作者:"));
        JLabel websiteLabel = new JLabel("<html><a href=\"" + WEBSITE + "\">" + WEBSITE + "</a></html>");
        websiteLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        websiteLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openUri(WEBSITE);
            }
        });
        infoPanel.add(websiteLabel);
        infoPanel.add(new JLabel("版本:"));
        infoPanel.add(versionValue);
        infoPanel.add(new JLabel("构建时间:"));
        infoPanel.add(buildTimeValue);
        infoPanel.add(new JLabel("许可文件:"));
        noticesPathValue.setToolTipText("动态读取 THIRD-PARTY-NOTICES.txt");
        infoPanel.add(noticesPathValue);

        noticesArea.setEditable(false);
        noticesArea.setLineWrap(true);
        noticesArea.setWrapStyleWord(true);
        noticesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane noticesScroll = new JScrollPane(noticesArea);
        noticesScroll.setBorder(BorderFactory.createTitledBorder("版权鸣谢与第三方许可"));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton openNoticesButton = new JButton("打开第三方许可文件");
        openNoticesButton.addActionListener(e -> openNoticesFile());
        buttons.add(openNoticesButton);
        buttons.add(Box.createHorizontalStrut(8));
        JButton openDeployButton = new JButton("打开部署目录");
        openDeployButton.addActionListener(e -> openDirectory(RuntimeDirs.getAppDir().toFile()));
        buttons.add(openDeployButton);
        buttons.add(Box.createHorizontalStrut(8));
        JButton websiteButton = new JButton("访问作者网站");
        websiteButton.addActionListener(e -> openUri(WEBSITE));
        buttons.add(websiteButton);
        buttons.add(Box.createHorizontalGlue());
        actionStatus.setForeground(new Color(0x888888));
        buttons.add(actionStatus);

        add(infoPanel, BorderLayout.NORTH);
        add(noticesScroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        refresh();
    }

    @Override
    public void refresh() {
        versionValue.setText(versionText());
        buildTimeValue.setText(formatBuildTime());
        File notices = findNoticesFile();
        noticesPathValue.setText(notices == null ? "未找到" : notices.getAbsolutePath());
        noticesArea.setText(loadNoticesText());
        noticesArea.setCaretPosition(0);
    }

    private static String versionText() {
        Package pkg = AboutTabPanel.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.isBlank() ? "255.0.0" : version;
    }

    /** 构建时间内部按 UTC 存储，仅显示时转换为系统时区。 */
    private static String formatBuildTime() {
        try {
            ZonedDateTime local = LocalDateTime.parse(BuildInfo.BUILD_TIME, BUILD_TIME_FORMAT)
                    .atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(ZoneId.systemDefault());
            return local.format(BUILD_TIME_FORMAT) + "（" + ZoneId.systemDefault().getId() + "）";
        } catch (Exception e) {
            return BuildInfo.BUILD_TIME + "（UTC）";
        }
    }

    private static String loadNoticesText() {
        File file = findNoticesFile();
        if (file == null) {
            return missingNoticesText();
        }
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "读取许可文件失败：" + file.getAbsolutePath() + "\n\n" + e.getMessage();
        }
    }

    private static String missingNoticesText() {
        File expected = new File(RuntimeDirs.getAppDir().toFile(), NOTICES_FILE);
        return "未找到 " + NOTICES_FILE + "。\n\n期望位置：\n  " + expected.getAbsolutePath()
                + "\n\n该文件应部署在 jar 同级目录（.runtime）。请放好后点右上角「刷新」重新读取。";
    }

    private static File findNoticesFile() {
        File file = new File(RuntimeDirs.getAppDir().toFile(), NOTICES_FILE);
        return file.isFile() ? file : null;
    }

    private void openNoticesFile() {
        File file = findNoticesFile();
        if (file == null) {
            actionStatus.setText("未找到许可文件");
            JOptionPane.showMessageDialog(this, missingNoticesText(),
                    "第三方许可文件不存在", JOptionPane.ERROR_MESSAGE);
            return;
        }
        actionStatus.setText("正在打开: " + file.getName());
        if (openTextFile(file)) {
            return;
        }
        showPath(file);
    }

    /** 优先用记事本打开文本许可文件（Windows 下比默认文件关联更可靠）。 */
    private boolean openTextFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        if (isWindows()) {
            try {
                new ProcessBuilder("notepad.exe", file.getAbsolutePath()).start();
                actionStatus.setText("已用记事本打开: " + file.getName());
                return true;
            } catch (Exception ignored) {
                // fall back to Desktop
            }
        }
        return openWithDesktop(file);
    }

    private void openDirectory(File dir) {
        actionStatus.setText("正在打开目录: " + dir.getAbsolutePath());
        if (openWithDesktop(dir)) {
            return;
        }
        if (isWindows()) {
            try {
                new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
                actionStatus.setText("已打开目录: " + dir.getAbsolutePath());
                return;
            } catch (Exception ignored) {
                // fall through
            }
        }
        showPath(dir);
    }

    private boolean openWithDesktop(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
                actionStatus.setText("已打开: " + file.getName());
                return true;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return false;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void openUri(String uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(uri));
                return;
            }
        } catch (Exception ignored) {
            // fall through
        }
        JOptionPane.showMessageDialog(this, uri, "联系作者", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showPath(File file) {
        JOptionPane.showMessageDialog(this, file.getAbsolutePath(), "路径", JOptionPane.INFORMATION_MESSAGE);
    }
}
