package io.github.surezzzzzz.sdk.license.verifier.clock;

import io.github.surezzzzzz.sdk.license.core.spi.ClockProvider;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 默认时钟适配：系统 UTC 秒 + 单调时钟对照。
 *
 * <p>effectiveNow 取墙钟；isClockSuspect 在本实现中由墙钟与单调增量的显著倒挂判定
 * （首次调用建立基线，其后墙钟增量与单调增量差异超过阈值即疑似）。疑似在实例内保持，
 * 不通过下一次读取自行恢复；受控恢复须配合持久化可信时间核验。</p>
 *
 * @author surezzzzzz
 */
public class SystemClockProvider implements ClockProvider {

    private static final long SUSPECT_THRESHOLD_MILLIS = 5_000L;

    private final LongSupplier wallTimeMillis;
    private final LongSupplier monotonicTimeNanos;
    private Long baselineWallMillis;
    private long baselineMonotonicMillis;
    private boolean clockSuspect;

    /** 默认使用系统墙钟与进程单调时钟。 */
    public SystemClockProvider() {
        this(System::currentTimeMillis, System::nanoTime);
    }

    /** 提供确定的采样来源；来源可信性由宿主负责，不提供清除疑似状态的旁路。 */
    public SystemClockProvider(LongSupplier wallTimeMillis, LongSupplier monotonicTimeNanos) {
        this.wallTimeMillis = Objects.requireNonNull(wallTimeMillis);
        this.monotonicTimeNanos = Objects.requireNonNull(monotonicTimeNanos);
    }

    @Override
    public synchronized long currentEffectiveNow() {
        return wallTimeMillis.getAsLong() / 1000L;
    }

    @Override
    public synchronized boolean isClockSuspect() {
        if (clockSuspect) {
            return true;
        }
        long wallNow = wallTimeMillis.getAsLong();
        long monoNow = monotonicMillis();
        if (baselineWallMillis == null) {
            baselineWallMillis = wallNow;
            baselineMonotonicMillis = monoNow;
            return false;
        }
        long wallDelta = wallNow - baselineWallMillis;
        long monoDelta = monoNow - baselineMonotonicMillis;
        if (wallDelta < 0 || monoDelta < 0 || monoDelta - wallDelta > SUSPECT_THRESHOLD_MILLIS
                || wallDelta - monoDelta > SUSPECT_THRESHOLD_MILLIS) {
            // 回拨、单调倒挂或墙钟突跳后锁存，不能滚动重置基线重新放行。
            clockSuspect = true;
            return true;
        }
        // 健康时滚动重置基线：正常时钟漂移（ppm 级）不会累计越过阈值，避免基线过期永久误报
        baselineWallMillis = wallNow;
        baselineMonotonicMillis = monoNow;
        return false;
    }

    @Override
    public long monotonicMillis() {
        return monotonicTimeNanos.getAsLong() / 1_000_000L;
    }
}
