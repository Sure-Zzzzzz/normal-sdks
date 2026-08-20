package io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Redis 字面量前缀 key 发现请求校验器。
 *
 * @author surezzzzzz
 */
public class RedisKeyDiscoveryRequestValidator extends DefaultMiddlewareOpsRequestValidator<RedisKeyDiscoveryRequest> {

    private static final char WILDCARD_ANY = '*';
    private static final char WILDCARD_SINGLE = '?';
    private static final char WILDCARD_SET_BEGIN = '[';
    private static final char WILDCARD_SET_END = ']';
    private static final char ESCAPE = '\\';

    private final int maxPrefixLength;
    private final int maxSize;

    /**
     * 创建 Redis key 发现请求校验器。
     *
     * @param maxPrefixLength 前缀最大长度
     * @param maxSize         结果数量上限
     */
    public RedisKeyDiscoveryRequestValidator(int maxPrefixLength, int maxSize) {
        super(RedisKeyDiscoveryRequest.class);
        this.maxPrefixLength = maxPrefixLength;
        this.maxSize = maxSize;
    }

    @Override
    public void validate(RedisKeyDiscoveryRequest request) {
        requireDatasource(request.getDatasourceKey());
        if (!isLiteralPrefix(request.getPrefix())) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Redis key 前缀不符合查询规范");
        }
        if (request.getSize() <= 0 || request.getSize() > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
    }

    private boolean isLiteralPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty() || prefix.length() > maxPrefixLength) {
            return false;
        }
        for (int index = 0; index < prefix.length(); index++) {
            char character = prefix.charAt(index);
            if (Character.isISOControl(character) || character == WILDCARD_ANY || character == WILDCARD_SINGLE
                    || character == WILDCARD_SET_BEGIN || character == WILDCARD_SET_END || character == ESCAPE) {
                return false;
            }
        }
        return true;
    }
}
