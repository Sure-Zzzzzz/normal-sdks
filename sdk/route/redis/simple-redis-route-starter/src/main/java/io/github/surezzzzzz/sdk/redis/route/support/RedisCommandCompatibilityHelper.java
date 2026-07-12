package io.github.surezzzzzz.sdk.redis.route.support;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 命令安全语义兼容 Helper
 *
 * <p>只封装有明确等价语义的兼容逻辑（如 UNLINK 降级 DEL），
 * 不提供命令不等价的透明降级。
 *
 * @author surezzzzzz
 */
public final class RedisCommandCompatibilityHelper {

    private RedisCommandCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 删除 key：优先使用 UNLINK（4.0+ 异步删除），不支持则降级到 DEL。
     * 两者语义完全等价，仅性能不同。
     *
     * @param template Redis template
     * @param info     Redis Server 信息
     * @param key      Redis key
     */
    public static void deletePreferUnlink(StringRedisTemplate template, RedisServerInfo info, String key) {
        if (RedisCommandCapabilityHelper.supportsUnlink(info)) {
            template.unlink(key);
        } else {
            template.delete(key);
        }
    }

    /**
     * 断言 Server 满足指定能力要求，不满足时抛 RouteException。
     * 用于调用方在路由执行前做能力前置校验。
     *
     * @param info           Redis Server 信息
     * @param capabilityName 能力名称常量
     * @throws RouteException 能力不满足要求时抛出
     */
    public static void requireCapability(RedisServerInfo info, String capabilityName) {
        if (!RedisCommandCapabilityHelper.supports(info, capabilityName)) {
            String datasourceKey = info != null ? info.getDatasourceKey() : "unknown";
            String versionStr = (info != null && info.isKnown() && info.getVersion() != null)
                    ? info.getVersion().getRaw()
                    : "unknown";
            throw new RouteException(ErrorCode.REDIS_ROUTE_013,
                    String.format(ErrorMessage.CAPABILITY_NOT_SATISFIED, datasourceKey, capabilityName, versionStr));
        }
    }
}
