package com.donglouqiaoque.dlqqmcpserver.mcp.policy;

/**
 * 工具调用超时上下文。
 *
 * <p>语义：服务端单次工具调用的总超时 = 审批时间 + 运行时间，默认 50 秒；
 * 后台进程类调用（立即返回）不受此限制。超时时间可由调用方通过
 * {@code params._meta["dlqq/toolTimeoutSeconds"]} 指定。</p>
 *
 * <p>审批窗口展示的"审批剩余时间"与工具总超时一致，并且必须在超时时
 * 立即关闭窗口、中断工具执行、返回明确错误。</p>
 */
public final class ToolTimeoutContext {

    /** 默认工具总超时（秒）。 */
    public static final int DEFAULT_TIMEOUT_SECONDS = 50;

    /** 允许的最大超时（秒），防止滥用。 */
    public static final int MAX_TIMEOUT_SECONDS = 3600;

    /** 系统属性：覆盖默认超时（秒）。 */
    private static final String SYSTEM_PROPERTY = "dlqq.tool.timeout.seconds";

    /** 当前线程的工具调用截止时间。 */
    private static final ThreadLocal<Long> DEADLINE_MILLIS = new ThreadLocal<>();

    /**
     * 最近一次 begin 的截止时间：工具可能在子线程执行，
     * 子线程拿不到 ThreadLocal 时用它作为同一截止时间。
     */
    private static volatile long sharedDeadlineMillis;

    private ToolTimeoutContext() {
    }

    /** 默认超时秒数（系统属性可覆盖）。 */
    public static int defaultTimeoutSeconds() {
        String prop = System.getProperty(SYSTEM_PROPERTY);
        if (prop != null) {
            try {
                int value = Integer.parseInt(prop.trim());
                if (value > 0) {
                    return Math.min(value, MAX_TIMEOUT_SECONDS);
                }
            } catch (NumberFormatException ignored) {
                // 使用默认值
            }
        }
        return DEFAULT_TIMEOUT_SECONDS;
    }

    /** 开始计时（本次工具调用）。 */
    public static void begin(int timeoutSeconds) {
        int seconds = timeoutSeconds > 0
                ? Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS)
                : defaultTimeoutSeconds();
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        DEADLINE_MILLIS.set(deadline);
        sharedDeadlineMillis = deadline;
    }

    /** 结束计时。 */
    public static void clear() {
        DEADLINE_MILLIS.remove();
    }

    /** 剩余毫秒数；未开始计时时返回默认超时剩余值。 */
    public static long remainingMillis() {
        Long deadline = DEADLINE_MILLIS.get();
        long value = deadline != null ? deadline : sharedDeadlineMillis;
        if (value <= 0L) {
            return defaultTimeoutSeconds() * 1000L;
        }
        long remaining = value - System.currentTimeMillis();
        return Math.max(remaining, 0L);
    }

    /** 剩余秒数（不足 1 秒按 1 秒计，已超时为 0）。 */
    public static int remainingSeconds() {
        long remaining = remainingMillis();
        return (int) Math.max(0L, (remaining + 999L) / 1000L);
    }

    /** 是否已超时。 */
    public static boolean isExpired() {
        return remainingMillis() <= 0L;
    }
}
