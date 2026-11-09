package com.donglouqiaoque.dlqqmcpserver.mcp.tools;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 子进程看守：让命令进程随 MCP 进程一起结束。
 *
 * <p>Windows 上把子进程放进一个设置了 JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE 的
 * Job Object：只要 MCP 进程退出（句柄关闭，含被强杀），内核会连带结束作业内
 * 的所有进程。非 Windows 平台退化为 shutdown hook。</p>
 */
final class ChildProcessGuard {

    private static final boolean WINDOWS =
        System.getProperty("os.name", "").toLowerCase().contains("win");

    /** JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE */
    private static final int LIMIT_KILL_ON_JOB_CLOSE = 0x00002000;
    /** JobObjectExtendedLimitInformation */
    private static final int JOB_OBJECT_EXTENDED_LIMIT_INFORMATION = 9;
    private static final int PROCESS_TERMINATE = 0x0001;
    private static final int PROCESS_SET_QUOTA = 0x0100;
    /** JOBOBJECT_EXTENDED_LIMIT_INFORMATION 在 x64 上的字节数 */
    private static final int EXTENDED_LIMIT_INFO_SIZE = 144;
    /** LimitFlags 在 JOBOBJECT_BASIC_LIMIT_INFORMATION 中的偏移 */
    private static final int LIMIT_FLAGS_OFFSET = 16;

    private static final Object LOCK = new Object();
    private static final List<Process> ACTIVE = new CopyOnWriteArrayList<>();
    private static Pointer jobHandle;
    private static boolean jobFailed;
    private static boolean hookInstalled;

    private ChildProcessGuard() {
    }

    /** Windows API 子集（只声明用到的函数） */
    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer CreateJobObject(Pointer jobAttributes, String name);

        boolean SetInformationJobObject(Pointer job, int infoClass, Pointer info, int infoLength);

        boolean AssignProcessToJobObject(Pointer job, Pointer process);

        Pointer OpenProcess(int desiredAccess, boolean inheritHandle, int processId);

        boolean CloseHandle(Pointer handle);
    }

    /**
     * 登记子进程：加入 Job Object（Windows）并挂到 shutdown hook。
     */
    static void track(Process process) {
        if (process == null) {
            return;
        }
        ACTIVE.add(process);
        installShutdownHook();
        if (WINDOWS) {
            assignToJob(process);
        }
    }

    /** 进程结束后从活动列表移除 */
    static void release(Process process) {
        if (process != null) {
            ACTIVE.remove(process);
        }
    }

    private static void assignToJob(Process process) {
        synchronized (LOCK) {
            if (jobFailed) {
                return;
            }
            try {
                if (jobHandle == null) {
                    jobHandle = Kernel32.INSTANCE.CreateJobObject(null, "dlqq-mcp-commands");
                    if (jobHandle == null) {
                        jobFailed = true;
                        return;
                    }
                    Memory info = new Memory(EXTENDED_LIMIT_INFO_SIZE);
                    info.clear();
                    info.setInt(LIMIT_FLAGS_OFFSET, LIMIT_KILL_ON_JOB_CLOSE);
                    if (!Kernel32.INSTANCE.SetInformationJobObject(
                            jobHandle, JOB_OBJECT_EXTENDED_LIMIT_INFORMATION, info, EXTENDED_LIMIT_INFO_SIZE)) {
                        jobFailed = true;
                        return;
                    }
                }
                Pointer handle = Kernel32.INSTANCE.OpenProcess(
                        PROCESS_TERMINATE | PROCESS_SET_QUOTA, false, (int) process.pid());
                if (handle == null) {
                    return;
                }
                try {
                    Kernel32.INSTANCE.AssignProcessToJobObject(jobHandle, handle);
                } finally {
                    Kernel32.INSTANCE.CloseHandle(handle);
                }
            } catch (Throwable t) {
                jobFailed = true;
            }
        }
    }

    private static void installShutdownHook() {
        synchronized (LOCK) {
            if (hookInstalled) {
                return;
            }
            hookInstalled = true;
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(ChildProcessGuard::killAll, "dlqq-child-reaper"));
            } catch (IllegalStateException ignored) {
                // JVM 已在关闭中
            }
        }
    }

    /** 结束所有仍在运行的子进程（含其子树） */
    static void killAll() {
        for (Process process : ACTIVE) {
            try {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            } catch (Throwable ignored) {
                // 尽力而为
            }
        }
        ACTIVE.clear();
    }
}
