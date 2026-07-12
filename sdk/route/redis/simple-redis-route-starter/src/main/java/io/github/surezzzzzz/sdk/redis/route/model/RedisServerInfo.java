package io.github.surezzzzzz.sdk.redis.route.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Redis Server 信息（由探测获得，不可变快照）
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class RedisServerInfo {

    /**
     * 对应的 datasource key
     */
    private final String datasourceKey;

    /**
     * 是否成功探测到版本号。
     * false 时 version / redisMode 均为 null。
     */
    private final boolean known;

    /**
     * Redis Server 版本号，known=true 时有值
     */
    private final RedisServerVersion version;

    /**
     * Redis 部署模式原始字符串（standalone / cluster），known=true 时有值
     */
    private final String redisMode;

    /**
     * 探测失败或被禁用时的说明，known=true 时为 null。
     * 只含短描述，不含密码/用户名/完整异常栈。
     */
    private final String errorMessage;
}
