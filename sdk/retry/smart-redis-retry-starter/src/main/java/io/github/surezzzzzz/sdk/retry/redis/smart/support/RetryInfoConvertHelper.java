package io.github.surezzzzzz.sdk.retry.redis.smart.support;

import io.github.surezzzzzz.sdk.retry.redis.smart.annotation.SmartRedisRetryComponent;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;

import java.util.List;
import java.util.Map;

/**
 * 重试状态转换 Helper
 *
 * @author surezzzzzz
 */
@SmartRedisRetryComponent
public class RetryInfoConvertHelper {

    /**
     * 将 Redis Hash 转换为重试状态
     *
     * @param hash    Redis Hash 数据
     * @param context 重试上下文
     * @return 重试状态
     */
    public RetryInfo fromHash(Map<Object, Object> hash, Map<String, Object> context) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        return RetryInfo.builder()
                .count(toInteger(hash.get(SmartRedisRetryConstant.FIELD_COUNT)))
                .maxRetryTimes(toInteger(hash.get(SmartRedisRetryConstant.FIELD_MAX_RETRY_TIMES)))
                .retryIntervalMillis(toLong(hash.get(SmartRedisRetryConstant.FIELD_RETRY_INTERVAL_MILLIS)))
                .maxIntervalMillis(toLong(hash.get(SmartRedisRetryConstant.FIELD_MAX_INTERVAL_MILLIS)))
                .backoffMultiplier(toDouble(hash.get(SmartRedisRetryConstant.FIELD_BACKOFF_MULTIPLIER)))
                .firstFailTime(toLong(hash.get(SmartRedisRetryConstant.FIELD_FIRST_FAIL_TIME)))
                .lastFailTime(toLong(hash.get(SmartRedisRetryConstant.FIELD_LAST_FAIL_TIME)))
                .nextRetryTime(toLong(hash.get(SmartRedisRetryConstant.FIELD_NEXT_RETRY_TIME)))
                .lastErrorCode(toString(hash.get(SmartRedisRetryConstant.FIELD_LAST_ERROR_CODE)))
                .lastErrorMessage(toString(hash.get(SmartRedisRetryConstant.FIELD_LAST_ERROR_MESSAGE)))
                .context(context)
                .build();
    }

    /**
     * 将 Lua 返回值转换为重试状态
     *
     * @param values  Lua 返回值
     * @param context 重试上下文
     * @return 重试状态
     */
    public RetryInfo fromScriptResult(List<Object> values, Map<String, Object> context) {
        if (values == null || values.size() < SmartRedisRetryConstant.SCRIPT_RESULT_SIZE) {
            return null;
        }
        return RetryInfo.builder()
                .count(toInteger(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_COUNT_INDEX)))
                .maxRetryTimes(toInteger(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_MAX_RETRY_TIMES_INDEX)))
                .retryIntervalMillis(toLong(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_RETRY_INTERVAL_MILLIS_INDEX)))
                .maxIntervalMillis(toLong(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_MAX_INTERVAL_MILLIS_INDEX)))
                .backoffMultiplier(toDouble(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_BACKOFF_MULTIPLIER_INDEX)))
                .firstFailTime(toLong(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_FIRST_FAIL_TIME_INDEX)))
                .lastFailTime(toLong(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_LAST_FAIL_TIME_INDEX)))
                .nextRetryTime(toLong(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_NEXT_RETRY_TIME_INDEX)))
                .lastErrorCode(toString(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_LAST_ERROR_CODE_INDEX)))
                .lastErrorMessage(toString(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_LAST_ERROR_MESSAGE_INDEX)))
                .context(context)
                .build();
    }

    /**
     * 从 Redis Hash 读取上下文 JSON
     *
     * @param hash Redis Hash 数据
     * @return 上下文 JSON
     */
    public String contextJson(Map<Object, Object> hash) {
        if (hash == null) {
            return null;
        }
        return toString(hash.get(SmartRedisRetryConstant.FIELD_CONTEXT));
    }

    /**
     * 从 Lua 返回值读取上下文 JSON
     *
     * @param values Lua 返回值
     * @return 上下文 JSON
     */
    public String contextJson(List<Object> values) {
        if (values == null || values.size() < SmartRedisRetryConstant.SCRIPT_RESULT_SIZE) {
            return null;
        }
        return toString(values.get(SmartRedisRetryConstant.SCRIPT_RESULT_CONTEXT_INDEX));
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(toString(value));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return Long.valueOf(toString(value));
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        return Double.valueOf(toString(value));
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
