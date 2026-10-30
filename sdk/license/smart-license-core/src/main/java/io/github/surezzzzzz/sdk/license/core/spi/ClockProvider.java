package io.github.surezzzzzz.sdk.license.core.spi;

/**
 * 可信时钟端口：提供当前 UTC 秒与单调时钟约束。
 *
 * <p>UTC 小幅回退不降低 effectiveNow；回退超容忍进入 CLOCK_SUSPECT。
 * 进程运行中墙钟与单调时间明显不符停止许可。实施由 Verifier Starter 提供默认实现。</p>
 *
 * @author surezzzzzz
 */
public interface ClockProvider {

    /** 当前可信 effectiveNow（UTC 秒）。 */
    long currentEffectiveNow();

    /** 是否进入 CLOCK_SUSPECT（明显回退或墙钟与单调不符）。 */
    boolean isClockSuspect();

    /** 单调时钟当前值（毫秒，用于进程内一致性判断）。 */
    long monotonicMillis();
}
