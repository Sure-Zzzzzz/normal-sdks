package io.github.surezzzzzz.sdk.retry.task.configuration;

import io.github.surezzzzzz.sdk.retry.task.constant.TaskRetryConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Task Retry 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(TaskRetryConstant.CONFIG_PREFIX)
public class TaskRetryProperties {

    private boolean enable = TaskRetryConstant.DEFAULT_ENABLE;
    private Policy defaultPolicy = Policy.defaultPolicy();
    private Policy fastPolicy = Policy.fastPolicy();
    private Policy slowPolicy = Policy.slowPolicy();

    @Data
    public static class Policy {
        private int retryTimes;
        private long initialDelayMillis;
        private double backoffMultiplier;
        private long maxDelayMillis;

        public static Policy defaultPolicy() {
            Policy policy = new Policy();
            policy.setRetryTimes(TaskRetryConstant.DEFAULT_RETRY_TIMES);
            policy.setInitialDelayMillis(TaskRetryConstant.DEFAULT_INITIAL_DELAY_MILLIS);
            policy.setBackoffMultiplier(TaskRetryConstant.DEFAULT_BACKOFF_MULTIPLIER);
            policy.setMaxDelayMillis(TaskRetryConstant.DEFAULT_MAX_DELAY_MILLIS);
            return policy;
        }

        public static Policy fastPolicy() {
            Policy policy = new Policy();
            policy.setRetryTimes(TaskRetryConstant.FAST_RETRY_TIMES);
            policy.setInitialDelayMillis(TaskRetryConstant.FAST_INITIAL_DELAY_MILLIS);
            policy.setBackoffMultiplier(TaskRetryConstant.FAST_BACKOFF_MULTIPLIER);
            policy.setMaxDelayMillis(TaskRetryConstant.FAST_MAX_DELAY_MILLIS);
            return policy;
        }

        public static Policy slowPolicy() {
            Policy policy = new Policy();
            policy.setRetryTimes(TaskRetryConstant.SLOW_RETRY_TIMES);
            policy.setInitialDelayMillis(TaskRetryConstant.SLOW_INITIAL_DELAY_MILLIS);
            policy.setBackoffMultiplier(TaskRetryConstant.SLOW_BACKOFF_MULTIPLIER);
            policy.setMaxDelayMillis(TaskRetryConstant.SLOW_MAX_DELAY_MILLIS);
            return policy;
        }
    }
}
