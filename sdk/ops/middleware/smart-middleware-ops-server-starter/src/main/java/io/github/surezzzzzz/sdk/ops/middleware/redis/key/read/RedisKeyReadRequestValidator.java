package io.github.surezzzzzz.sdk.ops.middleware.redis.key.read;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Redis 精确 key 类型化读取请求校验器。
 *
 * @author surezzzzzz
 */
public class RedisKeyReadRequestValidator extends DefaultMiddlewareOpsRequestValidator<RedisKeyReadRequest> {

    private final int maxKeyLength;
    private final int maxSize;

    /**
     * 创建 Redis key 读取请求校验器。
     *
     * @param maxKeyLength key 最大长度
     * @param maxSize      结果数量上限
     */
    public RedisKeyReadRequestValidator(int maxKeyLength, int maxSize) {
        super(RedisKeyReadRequest.class);
        this.maxKeyLength = maxKeyLength;
        this.maxSize = maxSize;
    }

    @Override
    public void validate(RedisKeyReadRequest request) {
        requireDatasource(request.getDatasourceKey());
        if (!isExactKey(request.getKey())) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Redis key 不符合查询规范");
        }
        if (request.getOffset() < 0 || request.getSize() <= 0 || request.getSize() > maxSize) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
    }

    private boolean isExactKey(String key) {
        if (key == null || key.trim().isEmpty() || key.length() > maxKeyLength) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (Character.isISOControl(character) || character == '*' || character == '?' || character == '[') {
                return false;
            }
        }
        return true;
    }
}
