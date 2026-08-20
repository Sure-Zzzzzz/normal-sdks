package io.github.surezzzzzz.sdk.ops.middleware.redis.key.metadata;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsRequestValidator;
import org.springframework.http.HttpStatus;

/**
 * Redis 精确 key 元数据请求校验器。
 *
 * @author surezzzzzz
 */
public class RedisKeyMetadataRequestValidator extends DefaultMiddlewareOpsRequestValidator<RedisKeyMetadataRequest> {

    private static final int MAX_KEY_LENGTH = 256;

    public RedisKeyMetadataRequestValidator() {
        super(RedisKeyMetadataRequest.class);
    }

    @Override
    public void validate(RedisKeyMetadataRequest request) {
        requireDatasource(request.getDatasourceKey());
        String key = request.getKey();
        if (key == null || key.trim().isEmpty() || key.length() > MAX_KEY_LENGTH || containsForbiddenCharacter(key)) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Redis key 不符合查询规范");
        }
    }

    private boolean containsForbiddenCharacter(String key) {
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (Character.isISOControl(character) || character == '*' || character == '?' || character == '[') {
                return true;
            }
        }
        return false;
    }
}
