package io.github.surezzzzzz.sdk.ops.middleware.redis.datasource;

import io.github.surezzzzzz.sdk.ops.middleware.redis.summary.RedisDatasourceResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Redis 数据源清单响应。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisDatasourceListResponse {

    /**
     * 数据源安全投影。
     */
    private final List<RedisDatasourceResponse> items;
}
