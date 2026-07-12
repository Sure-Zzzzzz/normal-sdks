package io.github.surezzzzzz.sdk.redis.route.support;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis Server 信息探测，内部使用，不作为 Spring Bean 注册
 *
 * <p>探测时机：registry.afterPropertiesSet() 后，对每个 datasource 执行一次。
 * 探测失败不阻断启动，known=false 并记录 warn。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class RedisServerInfoProbeHelper {

    private RedisServerInfoProbeHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 探测指定 datasource 的 Redis Server 信息。
     * probeEnabled=false 时直接返回 known=false（附说明消息），不发起任何命令。
     *
     * @param datasourceKey     datasource key
     * @param connectionFactory Redis 连接工厂
     * @param probeEnabled      是否启用探测
     * @return Redis Server 信息快照，失败时返回 known=false 的对象
     */
    public static RedisServerInfo probe(String datasourceKey, RedisConnectionFactory connectionFactory,
                                        boolean probeEnabled) {
        if (!probeEnabled) {
            log.info("Redis Server info 探测已禁用，datasource=[{}]，原因: probe.server-info=false", datasourceKey);
            return unknownInfo(datasourceKey, ErrorMessage.PROBE_DISABLED);
        }
        try {
            String rawInfo = RedisSpringDataCompatibilityHelper.infoServer(connectionFactory);
            if (rawInfo == null || rawInfo.trim().isEmpty()) {
                log.warn("Redis Server info 探测返回空，datasource=[{}]", datasourceKey);
                return unknownInfo(datasourceKey, ErrorMessage.PROBE_FAILED);
            }
            return parseInfoOutput(datasourceKey, rawInfo);
        } catch (Exception e) {
            // 不暴露原始异常消息，避免密码/主机信息泄露，只记录异常类型
            log.warn("Redis Server info 探测失败，datasource=[{}]，异常类型=[{}]",
                    datasourceKey, e.getClass().getSimpleName());
            return unknownInfo(datasourceKey, ErrorMessage.PROBE_FAILED);
        }
    }

    /**
     * 解析 INFO server 输出，提取 redis_version 和 redis_mode
     */
    private static RedisServerInfo parseInfoOutput(String datasourceKey, String rawInfo) {
        String versionLine = null;
        String modeLine = null;
        for (String line : rawInfo.split("\n")) {
            String trimmed = line.trim();
            int colonIndex = trimmed.lastIndexOf(':');
            if (colonIndex <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colonIndex);
            String value = trimmed.substring(colonIndex + 1).trim();
            if (isInfoKey(key, "redis_version")) {
                versionLine = value;
            } else if (isInfoKey(key, "redis_mode")) {
                modeLine = value;
            }
            if (versionLine != null && modeLine != null) {
                break;
            }
        }
        if (versionLine == null || versionLine.isEmpty()) {
            log.warn("Redis Server info 未包含 redis_version，datasource=[{}]", datasourceKey);
            return unknownInfo(datasourceKey, ErrorMessage.PROBE_FAILED);
        }
        try {
            RedisServerVersion version = RedisServerVersion.parse(versionLine);
            log.info("Redis Server info 探测完成，datasource=[{}]，version=[{}]，mode=[{}]",
                    datasourceKey, version.getRaw(), modeLine);
            return RedisServerInfo.builder()
                    .datasourceKey(datasourceKey)
                    .known(true)
                    .version(version)
                    .redisMode(modeLine)
                    .build();
        } catch (Exception e) {
            log.warn("Redis Server 版本号解析失败，datasource=[{}]，raw=[{}]", datasourceKey, versionLine);
            return unknownInfo(datasourceKey, ErrorMessage.PROBE_FAILED);
        }
    }

    private static boolean isInfoKey(String actualKey, String expectedKey) {
        return expectedKey.equals(actualKey) || actualKey.endsWith("." + expectedKey);
    }

    /**
     * 构造 known=false 的未知 Server 信息快照
     */
    private static RedisServerInfo unknownInfo(String datasourceKey, String errorMessage) {
        return RedisServerInfo.builder()
                .datasourceKey(datasourceKey)
                .known(false)
                .errorMessage(errorMessage)
                .build();
    }
}
