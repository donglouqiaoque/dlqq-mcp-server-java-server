package com.donglouqiaoque.dlqqmcpserver.mcp.env;

import com.donglouqiaoque.dlqqmcpserver.mcp.RefreshableTab;
import com.donglouqiaoque.dlqqmcpserver.utils.RuntimeDirs;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 控制台「环境」选项卡：管理 Java 与 Python 运行环境。
 *
 * <p>参考 dlqq-llm 的环境页：展示解释器/版本/依赖版本，提供检测、安装依赖、重建环境入口。
 * 所有外部命令都在后台线程执行，输出实时追加到日志区。</p>
 */
public final class EnvTabPanel extends JPanel implements RefreshableTab {

    private final JLabel javaVersionValue = new JLabel();
    private final JLabel javaVendorValue = new JLabel();
    private final JLabel javaHomeValue = new JLabel();
    private final JLabel jarPathValue = new JLabel();
    private final JLabel memoryValue = new JLabel();

    private final JLabel pythonExeValue = new JLabel();
    private final JLabel pythonVersionValue = new JLabel();
    private final JLabel pipVersionValue = new JLabel();
    private final JLabel bleakVersionValue = new JLabel();

    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");

    private volatile boolean busy;

    public EnvTabPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel javaPanel = createJavaPanel();
        JPanel pythonPanel = createPythonPanel();

        JSplitPane sections = new JSplitPane(JSplitPane.VERTICAL_SPLIT, javaPanel, pythonPanel);
        sections.setResizeWeight(0.35);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel logPanel = new JPanel(new BorderLayout(0, 2));
        logPanel.setBorder(BorderFactory.createTitledBorder("输出日志"));
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        JPanel logToolbar = new JPanel();
        logToolbar.setLayout(new BoxLayout(logToolbar, BoxLayout.X_AXIS));
        JButton clearLogButton = new JButton("清空日志");
        clearLogButton.addActionListener(e -> logArea.setText(""));
        logToolbar.add(clearLogButton);
        logToolbar.add(Box.createHorizontalGlue());
        logToolbar.add(statusLabel);
        logPanel.add(logToolbar, BorderLayout.SOUTH);

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sections, logPanel);
        main.setResizeWeight(0.72);
        add(main, BorderLayout.CENTER);

        refreshAsync();
    }

    private JPanel createJavaPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Java 环境"));

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 3));
        grid.add(new JLabel("Java 版本:"));
        grid.add(javaVersionValue);
        grid.add(new JLabel("Java 供应商:"));
        grid.add(javaVendorValue);
        grid.add(new JLabel("Java 安装目录:"));
        grid.add(javaHomeValue);
        grid.add(new JLabel("服务器 JAR:"));
        grid.add(jarPathValue);
        grid.add(new JLabel("JVM 堆内存:"));
        grid.add(memoryValue);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton openJavaButton = new JButton("打开 Java 目录");
        openJavaButton.addActionListener(e -> openPath(System.getProperty("java.home")));
        buttons.add(openJavaButton);
        buttons.add(Box.createHorizontalStrut(8));
        JButton openAppButton = new JButton("打开部署目录");
        openAppButton.addActionListener(e -> openPath(RuntimeDirs.getAppDir().toString()));
        buttons.add(openAppButton);
        buttons.add(Box.createHorizontalGlue());

        panel.add(grid, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createPythonPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Python 环境（BLE 后端）"));

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 3));
        grid.add(new JLabel("解释器:"));
        grid.add(pythonExeValue);
        grid.add(new JLabel("Python 版本:"));
        grid.add(pythonVersionValue);
        grid.add(new JLabel("pip 版本:"));
        grid.add(pipVersionValue);
        grid.add(new JLabel("bleak 版本:"));
        grid.add(bleakVersionValue);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton openPythonButton = new JButton("打开 Python 目录");
        openPythonButton.addActionListener(e -> openPath(new File(runtimeDir(), "python").getAbsolutePath()));
        buttons.add(openPythonButton);
        buttons.add(Box.createHorizontalStrut(8));
        JButton installBleDepsButton = new JButton("安装/更新 BLE 依赖");
        installBleDepsButton.addActionListener(e -> installBleDependenciesAsync());
        buttons.add(installBleDepsButton);
        buttons.add(Box.createHorizontalStrut(8));
        JButton rebuildButton = new JButton("重建 Python 环境");
        rebuildButton.addActionListener(e -> rebuildPythonEnvAsync());
        buttons.add(rebuildButton);
        buttons.add(Box.createHorizontalGlue());

        panel.add(grid, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private File runtimeDir() {
        return RuntimeDirs.getAppDir().toFile();
    }

    @Override
    public void refresh() {
        refreshAsync();
    }

    private void refreshAsync() {
        javaVersionValue.setText(System.getProperty("java.version", "未知"));
        javaVendorValue.setText(System.getProperty("java.vendor", "未知"));
        javaHomeValue.setText(System.getProperty("java.home", "未知"));
        jarPathValue.setText(RuntimeDirs.getServerJar().toString());
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        memoryValue.setText(usedMb + " MB / 上限 " + maxMb + " MB");

        statusLabel.setText("正在检测 Python 环境…");
        new Thread(() -> {
            try {
                File pythonExe = new File(runtimeDir(), "python" + File.separator + "python.exe");
                if (!pythonExe.isFile()) {
                    SwingUtilities.invokeLater(() -> {
                        pythonExeValue.setText("未找到: " + pythonExe.getAbsolutePath());
                        pythonVersionValue.setText("不可用");
                        pipVersionValue.setText("不可用");
                        bleakVersionValue.setText("不可用");
                        statusLabel.setText("未找到内置 Python，可点“重建 Python 环境”");
                    });
                    return;
                }
                String version = firstLine(runAndCapture("python", pythonExe, "-s", "-P", "-c",
                        "import sys;print(sys.version.split()[0])"), "未知");
                String pipVersion = firstLine(runAndCapture("pip", pythonExe, "-s", "-P", "-m", "pip",
                        "--version"), "不可用");
                String bleakCode = "import importlib.metadata as m\n"
                        + "try:\n"
                        + "    print(m.version('bleak'))\n"
                        + "except Exception:\n"
                        + "    print('未安装')\n";
                String bleak = firstLine(runAndCapture("bleak", pythonExe, "-s", "-P", "-c", bleakCode), "未安装");
                SwingUtilities.invokeLater(() -> {
                    pythonExeValue.setText(pythonExe.getAbsolutePath());
                    pythonVersionValue.setText(version);
                    pipVersionValue.setText(pipVersion);
                    bleakVersionValue.setText(bleak);
                    statusLabel.setText("检测完成");
                    appendLog("[环境] Python " + version + "，pip: " + pipVersion + "，bleak: " + bleak);
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Python 检测失败: " + e.getMessage());
                    appendLog("[环境] Python 检测失败: " + e.getMessage());
                });
            }
        }, "env-python-detect").start();
    }

    private void installBleDependenciesAsync() {
        int choice = JOptionPane.showConfirmDialog(this,
                "将使用项目内置 Python 执行:\n"
                        + "python -m pip install --upgrade bleak ttkbootstrap pillow\n\n"
                        + "需要联网，是否继续？",
                "安装/更新 BLE 依赖", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        runPythonCommandAsync("安装 BLE 依赖",
                new String[]{"-m", "pip", "install", "--upgrade", "bleak", "ttkbootstrap", "pillow"});
    }

    private void rebuildPythonEnvAsync() {
        String baseDir = JOptionPane.showInputDialog(this,
                "输入用于复制标准库的完整 CPython 安装目录（包含 python.exe），例如 C:\\Python314:\n"
                        + "将重建 " + runtimeDir() + "\\python",
                "重建 Python 环境", JOptionPane.QUESTION_MESSAGE);
        if (baseDir == null || baseDir.isBlank()) {
            return;
        }
        File pythonExe = new File(baseDir.trim(), "python.exe");
        if (!pythonExe.isFile()) {
            JOptionPane.showMessageDialog(this, "目录下没有 python.exe:\n" + pythonExe.getAbsolutePath(),
                    "重建 Python 环境", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "将执行:\n.runtime\\make_python_env.bat \"" + pythonExe.getParentFile() + "\" /force nopause\n\n"
                        + "这会重新复制 Python 标准库并安装依赖，是否继续？",
                "重建 Python 环境", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        File script = new File(runtimeDir(), "make_python_env.bat");
        if (!script.isFile()) {
            JOptionPane.showMessageDialog(this, "找不到脚本: " + script.getAbsolutePath(),
                    "重建 Python 环境", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runScriptAsync("重建 Python 环境",
                new String[]{"cmd.exe", "/c", script.getAbsolutePath(),
                        pythonExe.getParentFile().getAbsolutePath(), "/force", "nopause"});
    }

    private void runPythonCommandAsync(String title, String[] pythonArgs) {
        File pythonExe = new File(runtimeDir(), "python" + File.separator + "python.exe");
        if (!pythonExe.isFile()) {
            appendLog("[环境] 未找到内置 Python: " + pythonExe.getAbsolutePath());
            return;
        }
        List<String> command = new ArrayList<>();
        command.add(pythonExe.getAbsolutePath());
        command.add("-s");
        command.add("-P");
        for (String arg : pythonArgs) {
            command.add(arg);
        }
        runScriptAsync(title, command.toArray(new String[0]));
    }

    private void runScriptAsync(String title, String[] command) {
        if (busy) {
            statusLabel.setText("已有环境任务在执行，请稍候…");
            return;
        }
        busy = true;
        statusLabel.setText(title + "：执行中…");
        appendLog("[环境] === " + title + " ===");
        appendLog("[环境] " + String.join(" ", command));
        new Thread(() -> {
            int exitCode = -1;
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(runtimeDir());
                builder.redirectErrorStream(true);
                Process process = builder.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                for (String line : output.split("\\R")) {
                    appendLog(line);
                }
                if (!process.waitFor(5, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    appendLog("[环境] 命令超时，已强制结束");
                }
                exitCode = process.exitValue();
                appendLog("[环境] " + title + " 结束，退出码: " + exitCode);
            } catch (Exception e) {
                appendLog("[环境] " + title + " 失败: " + e.getMessage());
            } finally {
                int code = exitCode;
                busy = false;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(title + (code == 0 ? "：完成" : "：失败 (退出码 " + code + ")"));
                    refreshAsync();
                });
            }
        }, "env-task").start();
    }

    private String runAndCapture(String name, File executable, String... args) {
        List<String> command = new ArrayList<>();
        command.add(executable.getAbsolutePath());
        for (String arg : args) {
            command.add(arg);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(runtimeDir());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            byte[] bytes = process.getInputStream().readAllBytes();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "超时";
            }
            return decode(bytes).trim();
        } catch (Exception e) {
            return "检测失败: " + e.getMessage();
        }
    }

    private static String firstLine(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return fallback;
    }

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

    private void openPath(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            File file = new File(path);
            if (!file.exists()) {
                statusLabel.setText("路径不存在: " + path);
                return;
            }
            Desktop.getDesktop().open(file);
        } catch (Exception e) {
            statusLabel.setText("打开目录失败: " + e.getMessage());
        }
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
