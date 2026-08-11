package io.github.surezzzzzz.sdk.ops.middleware.redis;

import lombok.Builder;
import lombok.Getter;

/**
 * Redis 数据源安全投影。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class RedisDatasourceResponse {

    /**
     * 数据源标识。
     */
    private final String datasourceKey;
    /**
     * 是否获得版本探测结果。
     */
    private final boolean versionKnown;
    /**
     * Redis 版本。
     */
    private final String version;
    /**
     * Redis 部署模式。
     */
    private final String deploymentMode;
}
