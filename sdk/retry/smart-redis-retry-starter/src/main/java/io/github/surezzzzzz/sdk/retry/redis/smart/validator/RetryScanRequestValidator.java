package io.github.surezzzzzz.sdk.retry.redis.smart.validator;

import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryValidationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryScanRequest;
import lombok.RequiredArgsConstructor;

/**
 * 重试扫描请求校验器
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class RetryScanRequestValidator implements RetryRequestValidator<RetryScanRequest> {

    /**
     * Smart Redis Retry 配置
     */
    private final SmartRedisRetryProperties properties;

    /**
     * 判断是否支持扫描请求。
     *
     * @param requestType 请求类型
     * @return true 表示支持，false 表示不支持
     */
    @Override
    public boolean supports(Class<?> requestType) {
        return RetryScanRequest.class.isAssignableFrom(requestType);
    }

    /**
     * 校验扫描请求。
     *
     * @param request 扫描请求
     */
    @Override
    public void validate(RetryScanRequest request) {
        if (!hasText(request.getRouteKey())) {
            throw new RetryValidationException(ErrorCode.ROUTE_KEY_EMPTY, ErrorMessage.ROUTE_KEY_EMPTY);
        }
        if (!hasText(request.getRetryType())) {
            throw new RetryValidationException(ErrorCode.RETRY_TYPE_EMPTY, ErrorMessage.RETRY_TYPE_EMPTY);
        }
        if (request.getCount() != null
                && request.getCount() <= SmartRedisRetryConstant.ARRAY_INITIAL_INDEX) {
            throw new RetryValidationException(ErrorCode.SCAN_COUNT_INVALID, ErrorMessage.SCAN_COUNT_INVALID);
        }
        if (request.getCount() != null && request.getCount() > properties.getRedis().getScanCount()) {
            throw new RetryValidationException(ErrorCode.SCAN_COUNT_INVALID, ErrorMessage.SCAN_COUNT_INVALID);
        }
        if (request.getCursor() != null && !isValidCursor(request.getCursor())) {
            throw new RetryValidationException(ErrorCode.SCAN_CURSOR_INVALID, ErrorMessage.SCAN_CURSOR_INVALID);
        }
    }

    private boolean isValidCursor(String cursor) {
        String[] parts = cursor.split(SmartRedisRetryConstant.CLUSTER_CURSOR_SEPARATOR,
                SmartRedisRetryConstant.CLUSTER_CURSOR_PART_SIZE + SmartRedisRetryConstant.ARRAY_INITIAL_INDEX);
        if (parts.length == SmartRedisRetryConstant.CLUSTER_CURSOR_PART_SIZE) {
            return isNonNegativeNumber(parts[SmartRedisRetryConstant.CLUSTER_CURSOR_NODE_INDEX])
                    && isNonNegativeNumber(parts[SmartRedisRetryConstant.CLUSTER_CURSOR_NODE_SCAN_INDEX]);
        }
        return isNonNegativeNumber(cursor);
    }

    private boolean isNonNegativeNumber(String value) {
        if (!hasText(value)) {
            return false;
        }
        for (int index = SmartRedisRetryConstant.ARRAY_INITIAL_INDEX; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
