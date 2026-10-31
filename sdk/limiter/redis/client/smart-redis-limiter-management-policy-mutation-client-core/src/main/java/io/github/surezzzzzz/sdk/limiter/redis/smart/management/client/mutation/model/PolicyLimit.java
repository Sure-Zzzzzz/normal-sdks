package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model;

/**
 * 单档时间窗口（与规则窗口 wire 契约同构：count/window/unit）
 *
 * @author surezzzzzz
 */
public final class PolicyLimit {

    private final long count;
    private final long window;
    private final String unit;

    public PolicyLimit(long count, long window, String unit) {
        this.count = count;
        this.window = window;
        this.unit = unit;
    }

    public long getCount() {
        return count;
    }

    public long getWindow() {
        return window;
    }

    public String getUnit() {
        return unit;
    }
}
