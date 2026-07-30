package io.github.surezzzzzz.sdk.messaging.kafka.consumer.error;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 默认退避策略：指数退避 + 抖动。
 * 第 attempt 次失败后的基准间隔为 initial * multiplier^(attempt-1)，封顶 maxInterval，
 * 再在 [base*(1-jitter), base*(1+jitter)] 区间内随机取值。
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultKafkaConsumerBackoffPolicy implements KafkaConsumerBackoffPolicy {

    private final SimpleKafkaConsumerProperties properties;

    @Override
    public long computeBackoffMs(int attempt) {
        SimpleKafkaConsumerProperties.ErrorConfig error = properties.getError();
        long initial = error.getInitialIntervalMs();
        double multiplier = error.getMultiplier();
        long maxInterval = error.getMaxIntervalMs();
        double jitter = error.getJitterFactor();

        int exponent = attempt <= 1 ? 0 : attempt - 1;
        double calculated = initial * Math.pow(multiplier, exponent);
        long base = (long) Math.min(maxInterval, calculated);
        return jitterInterval(base, jitter);
    }

    private long jitterInterval(long base, double jitter) {
        if (jitter <= 0) {
            return base;
        }
        double low = base * (1.0 - jitter);
        double high = base * (1.0 + jitter);
        if (high <= low) {
            return base;
        }
        double value = ThreadLocalRandom.current().nextDouble(low, high);
        return value < 0 ? 0 : (long) value;
    }
}
