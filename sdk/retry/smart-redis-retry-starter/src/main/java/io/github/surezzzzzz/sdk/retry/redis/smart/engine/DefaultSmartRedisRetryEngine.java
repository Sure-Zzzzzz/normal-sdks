package io.github.surezzzzzz.sdk.retry.redis.smart.engine;

import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.retry.redis.smart.clock.RetryClock;
import io.github.surezzzzzz.sdk.retry.redis.smart.configuration.SmartRedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.*;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryOperationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.facade.DefaultRetryScene;
import io.github.surezzzzzz.sdk.retry.redis.smart.facade.RetryScene;
import io.github.surezzzzzz.sdk.retry.redis.smart.listener.SmartRedisRetryListener;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.*;
import io.github.surezzzzzz.sdk.retry.redis.smart.policy.RetryPolicyResolver;
import io.github.surezzzzzz.sdk.retry.redis.smart.script.RedisRetryScriptExecutor;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.RetryContextSerializer;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryInfoConvertHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryKeyHelper;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryPolicyValidator;
import io.github.surezzzzzz.sdk.retry.redis.smart.validator.RetryRequestValidatorChain;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.async.RedisKeyAsyncCommands;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 默认 Smart Redis Retry 引擎
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultSmartRedisRetryEngine implements SmartRedisRetryEngine {

    /**
     * Redis 路由模板
     */
    private final RedisRouteTemplate redisRouteTemplate;
    /**
     * Smart Redis Retry 配置
     */
    private final SmartRedisRetryProperties properties;
    /**
     * 重试策略解析器
     */
    private final RetryPolicyResolver retryPolicyResolver;
    /**
     * 重试事件监听器
     */
    private final SmartRedisRetryListener listener;
    /**
     * 重试时钟
     */
    private final RetryClock retryClock;
    /**
     * 重试 Key 构建辅助器
     */
    private final RetryKeyHelper retryKeyHelper;
    /**
     * 重试信息转换辅助器
     */
    private final RetryInfoConvertHelper retryInfoConvertHelper;
    /**
     * 重试上下文序列化器
     */
    private final RetryContextSerializer retryContextSerializer;
    /**
     * 请求校验器链
     */
    private final RetryRequestValidatorChain validatorChain;
    /**
     * 重试策略校验器
     */
    private final RetryPolicyValidator retryPolicyValidator;
    /**
     * Redis 脚本执行器
     */
    private final RedisRetryScriptExecutor scriptExecutor;

    /**
     * 快速判断当前是否可以重试。
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @return true 表示允许重试，false 表示不允许
     */
    @Override
    public boolean canRetry(String retryType, String retryKey) {
        return decide(retryType, retryKey).isAllowed();
    }

    /**
     * 获取当前重试决策。
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @return 重试决策
     */
    @Override
    public RetryDecision decide(String retryType, String retryKey) {
        RetryFailure failure = RetryFailure.builder().retryType(retryType).retryKey(retryKey).build();
        validatorChain.validate(failure);
        try {
            String redisKey = retryKeyHelper.buildRedisKey(retryType, retryKey);
            RetryInfo retryInfo = redisRouteTemplate.execute(redisKey, template -> readInfo(template, redisKey));
            RetryDecision decision = buildDecision(retryInfo, retryClock.currentTimeMillis());
            safeOnDecision(retryType, retryKey, decision);
            if (RetryDecisionType.EXHAUSTED == decision.getType()) {
                clearExhaustedRecord(retryType, retryKey, retryInfo);
            }
            return decision;
        } catch (RuntimeException e) {
            return handleRedisFailure(e);
        }
    }

    /**
     * 使用默认类型和 Key 记录一次失败。
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @return 更新后的重试信息
     */
    @Override
    public RetryInfo recordFailure(String retryType, String retryKey) {
        return recordFailure(RetryFailure.builder().retryType(retryType).retryKey(retryKey).build());
    }

    /**
     * 使用完整失败请求记录一次失败。
     *
     * @param failure 失败请求
     * @return 更新后的重试信息
     */
    @Override
    public RetryInfo recordFailure(RetryFailure failure) {
        validatorChain.validate(failure);
        RetryPolicy policy = retryPolicyResolver.resolve(failure.getRetryType(), failure);
        retryPolicyValidator.validate(policy);
        try {
            String redisKey = retryKeyHelper.buildRedisKey(failure.getRetryType(), failure.getRetryKey());
            long nowMillis = retryClock.currentTimeMillis();
            long ttlMillis = toTtlMillis(properties.getRedis().getRecordTtlSeconds());
            RetryInfo retryInfo = redisRouteTemplate.execute(redisKey, template ->
                    scriptExecutor.recordFailure(template, redisKey, failure, policy, nowMillis, ttlMillis));
            safeOnRecord(failure, retryInfo);
            if (isFirstExhausted(retryInfo)) {
                safeOnExhausted(failure.getRetryType(), failure.getRetryKey(), retryInfo);
            }
            return retryInfo;
        } catch (RuntimeException e) {
            log.warn("Smart Redis Retry recordFailure 失败，retryType={}, retryKey={}", failure.getRetryType(), failure.getRetryKey(), e);
            handleRedisFailure(e);
            return null;
        }
    }

    /**
     * 原子清理指定重试记录并返回清理前的状态。
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @return 清理前的重试信息，记录不存在时返回 null
     */
    @Override
    public RetryInfo clear(String retryType, String retryKey) {
        RetryFailure failure = RetryFailure.builder().retryType(retryType).retryKey(retryKey).build();
        validatorChain.validate(failure);
        try {
            String redisKey = retryKeyHelper.buildRedisKey(retryType, retryKey);
            RetryInfo cleared = redisRouteTemplate.execute(redisKey,
                    template -> scriptExecutor.clear(template, redisKey, null));
            if (cleared != null) {
                safeOnClear(retryType, retryKey, cleared);
            }
            return cleared;
        } catch (RuntimeException e) {
            handleRedisFailure(e);
            return null;
        }
    }

    /**
     * 查询指定重试记录的当前状态。
     *
     * @param retryType 重试类型
     * @param retryKey  重试标识
     * @return 重试信息，记录不存在时返回 null
     */
    @Override
    public RetryInfo getInfo(String retryType, String retryKey) {
        RetryFailure failure = RetryFailure.builder().retryType(retryType).retryKey(retryKey).build();
        validatorChain.validate(failure);
        try {
            String redisKey = retryKeyHelper.buildRedisKey(retryType, retryKey);
            return redisRouteTemplate.execute(redisKey, template -> readInfo(template, redisKey));
        } catch (RuntimeException e) {
            handleRedisFailure(e);
            return null;
        }
    }

    /**
     * 分页扫描指定重试类型的 Key。
     * <p>Standalone 使用 Redis 原生游标；Cluster 使用引擎生成的不透明游标，
     * 每次仅扫描一个 master 节点的一页，调用方必须原样传回 nextCursor。</p>
     *
     * @param routeKey  路由 Key
     * @param retryType 重试类型
     * @param cursor    分页游标，传 null 或空字符串从头开始
     * @return 单页扫描结果
     */
    @Override
    public RetryScanResult scan(String routeKey, String retryType, String cursor) {
        return scan(RetryScanRequest.builder().routeKey(routeKey).retryType(retryType).cursor(cursor).build());
    }

    /**
     * 使用完整请求参数分页扫描。
     * <p>Cluster 模式的 nextCursor 为不透明值，调用方必须原样传回，
     * 直到 finished 为 true。</p>
     *
     * @param request 扫描请求
     * @return 单页扫描结果
     */
    @Override
    public RetryScanResult scan(RetryScanRequest request) {
        if (request.getCursor() == null || request.getCursor().trim().isEmpty()) {
            request.setCursor(SmartRedisRetryConstant.CURSOR_INITIAL);
        }
        if (request.getCount() == null) {
            request.setCount(properties.getRedis().getScanCount());
        }
        validatorChain.validate(request);
        try {
            String pattern = retryKeyHelper.buildScanPattern(request.getRetryType());
            StringRedisTemplate template = redisRouteTemplate.stringTemplateByKey(request.getRouteKey());
            return scanTemplate(template, pattern, request);
        } catch (RuntimeException e) {
            log.warn("Smart Redis Retry scan 失败，routeKey={}，retryType={}，cursor={}",
                    request.getRouteKey(), request.getRetryType(), request.getCursor(), e);
            handleRedisFailure(e);
            return RetryScanResult.builder()
                    .nextCursor(SmartRedisRetryConstant.CURSOR_INITIAL)
                    .finished(true)
                    .keys(Collections.<String>emptyList())
                    .infos(Collections.<String, RetryInfo>emptyMap())
                    .build();
        }
    }

    /**
     * 创建指定重试类型的场景门面。
     *
     * @param retryType 重试类型
     * @return 场景门面
     */
    @Override
    public RetryScene scene(String retryType) {
        return new DefaultRetryScene(retryType, this);
    }

    private RetryInfo readInfo(StringRedisTemplate template, String redisKey) {
        Map<Object, Object> hash = template.opsForHash().entries(redisKey);
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        return retryInfoConvertHelper.fromHash(hash, retryContextSerializer.deserialize(retryInfoConvertHelper.contextJson(hash)));
    }

    private RetryDecision buildDecision(RetryInfo retryInfo, long nowMillis) {
        if (retryInfo == null) {
            return RetryDecision.builder().type(RetryDecisionType.ALLOW).allowed(true).build();
        }
        if (retryInfo.getCount() != null && retryInfo.getMaxRetryTimes() != null
                && retryInfo.getCount() >= retryInfo.getMaxRetryTimes()) {
            return RetryDecision.builder()
                    .type(RetryDecisionType.EXHAUSTED)
                    .allowed(false)
                    .currentCount(retryInfo.getCount())
                    .maxRetryTimes(retryInfo.getMaxRetryTimes())
                    .nextRetryTime(retryInfo.getNextRetryTime())
                    .retryInfo(retryInfo)
                    .build();
        }
        if (retryInfo.getNextRetryTime() != null && nowMillis < retryInfo.getNextRetryTime()) {
            return RetryDecision.builder()
                    .type(RetryDecisionType.WAITING)
                    .allowed(false)
                    .currentCount(retryInfo.getCount())
                    .maxRetryTimes(retryInfo.getMaxRetryTimes())
                    .nextRetryTime(retryInfo.getNextRetryTime())
                    .waitMillis(retryInfo.getNextRetryTime() - nowMillis)
                    .retryInfo(retryInfo)
                    .build();
        }
        return RetryDecision.builder()
                .type(RetryDecisionType.ALLOW)
                .allowed(true)
                .currentCount(retryInfo.getCount())
                .maxRetryTimes(retryInfo.getMaxRetryTimes())
                .nextRetryTime(retryInfo.getNextRetryTime())
                .retryInfo(retryInfo)
                .build();
    }

    @SuppressWarnings("unchecked")
    private RetryScanResult scanTemplate(StringRedisTemplate template, String pattern, RetryScanRequest request) {
        RedisConnection connection = template.getConnectionFactory().getConnection();
        try {
            Object nativeConnection = connection.getNativeConnection();
            ScanArgs args = new ScanArgs().match(pattern).limit(request.getCount());
            if (nativeConnection instanceof RedisAdvancedClusterAsyncCommands) {
                return scanClusterTemplate(template, request, args,
                        (RedisAdvancedClusterAsyncCommands<byte[], byte[]>) nativeConnection);
            }
            RedisKeyAsyncCommands<byte[], byte[]> commands = (RedisKeyAsyncCommands<byte[], byte[]>) nativeConnection;
            KeyScanCursor<byte[]> scanCursor = awaitScan(commands.scan(ScanCursor.of(request.getCursor()), args));
            return buildScanResult(template, request, scanCursor.getCursor(), scanCursor.isFinished(), scanCursor.getKeys());
        } finally {
            connection.close();
        }
    }

    private RetryScanResult scanClusterTemplate(StringRedisTemplate template,
                                                RetryScanRequest request,
                                                ScanArgs args,
                                                RedisAdvancedClusterAsyncCommands<byte[], byte[]> commands) {
        List<io.lettuce.core.api.async.RedisAsyncCommands<byte[], byte[]>> masterCommands =
                new ArrayList<io.lettuce.core.api.async.RedisAsyncCommands<byte[], byte[]>>(
                        commands.masters().asMap().values());
        ClusterScanCursor clusterCursor = parseClusterCursor(request.getCursor());
        if (clusterCursor.getNodeIndex() >= masterCommands.size()) {
            return buildScanResult(template, request, SmartRedisRetryConstant.CURSOR_INITIAL, true,
                    Collections.<byte[]>emptyList());
        }
        try {
            KeyScanCursor<byte[]> scanCursor = awaitScan(masterCommands.get(clusterCursor.getNodeIndex())
                    .scan(ScanCursor.of(clusterCursor.getNodeCursor()), args));
            return buildScanResult(template, request,
                    nextClusterCursor(clusterCursor, scanCursor, masterCommands.size()),
                    isClusterScanFinished(clusterCursor, scanCursor, masterCommands.size()), scanCursor.getKeys());
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.REDIS_OPERATION_FAILED, ErrorMessage.REDIS_OPERATION_FAILED, e);
        }
    }

    private ClusterScanCursor parseClusterCursor(String cursor) {
        if (!cursor.contains(SmartRedisRetryConstant.CLUSTER_CURSOR_SEPARATOR)) {
            return new ClusterScanCursor(SmartRedisRetryConstant.ARRAY_INITIAL_INDEX, cursor);
        }
        String[] parts = cursor.split(SmartRedisRetryConstant.CLUSTER_CURSOR_SEPARATOR,
                SmartRedisRetryConstant.CLUSTER_CURSOR_PART_SIZE);
        return new ClusterScanCursor(Integer.parseInt(parts[SmartRedisRetryConstant.CLUSTER_CURSOR_NODE_INDEX]),
                parts[SmartRedisRetryConstant.CLUSTER_CURSOR_NODE_SCAN_INDEX]);
    }

    private String nextClusterCursor(ClusterScanCursor clusterCursor, KeyScanCursor<byte[]> scanCursor,
                                     int masterNodeCount) {
        if (!scanCursor.isFinished()) {
            return String.format(SmartRedisRetryConstant.CLUSTER_CURSOR_TEMPLATE,
                    clusterCursor.getNodeIndex(), scanCursor.getCursor());
        }
        int nextNodeIndex = clusterCursor.getNodeIndex() + SmartRedisRetryConstant.RETRY_COUNT_INITIAL;
        if (nextNodeIndex >= masterNodeCount) {
            return SmartRedisRetryConstant.CURSOR_INITIAL;
        }
        return String.format(SmartRedisRetryConstant.CLUSTER_CURSOR_TEMPLATE,
                nextNodeIndex, SmartRedisRetryConstant.CURSOR_INITIAL);
    }

    private boolean isClusterScanFinished(ClusterScanCursor clusterCursor, KeyScanCursor<byte[]> scanCursor,
                                          int masterNodeCount) {
        return scanCursor.isFinished()
                && clusterCursor.getNodeIndex() + SmartRedisRetryConstant.RETRY_COUNT_INITIAL >= masterNodeCount;
    }

    @lombok.Value
    private static class ClusterScanCursor {
        /**
         * 当前 master 节点下标
         */
        int nodeIndex;
        /**
         * 当前 master 节点内的 SCAN 游标
         */
        String nodeCursor;
    }

    private KeyScanCursor<byte[]> awaitScan(java.util.concurrent.Future<KeyScanCursor<byte[]>> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryOperationException(ErrorCode.REDIS_OPERATION_FAILED, ErrorMessage.REDIS_OPERATION_FAILED, e);
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.REDIS_OPERATION_FAILED, ErrorMessage.REDIS_OPERATION_FAILED, e);
        }
    }

    private RetryScanResult buildScanResult(StringRedisTemplate template,
                                            RetryScanRequest request,
                                            String nextCursor,
                                            boolean finished,
                                            List<byte[]> keyBytesList) {
        List<String> keys = new ArrayList<String>();
        for (byte[] keyBytes : keyBytesList) {
            keys.add(new String(keyBytes, StandardCharsets.UTF_8));
        }
        Map<String, RetryInfo> infos = new LinkedHashMap<String, RetryInfo>();
        if (request.isIncludeInfo()) {
            for (String key : keys) {
                RetryInfo info = readInfo(template, key);
                if (info != null) {
                    infos.put(key, info);
                }
            }
        }
        return RetryScanResult.builder()
                .nextCursor(nextCursor)
                .finished(finished)
                .keys(keys)
                .infos(infos)
                .build();
    }

    private RetryDecision handleRedisFailure(RuntimeException e) {
        RedisFailureStrategy strategy = RedisFailureStrategy.fromCode(properties.getGuard().getRedisFailureStrategy());
        if (RedisFailureStrategy.FAIL_OPEN == strategy) {
            return RetryDecision.builder().type(RetryDecisionType.ALLOW).allowed(true).build();
        }
        if (RedisFailureStrategy.THROW == strategy) {
            if (e instanceof RetryOperationException) {
                throw e;
            }
            throw new RetryOperationException(ErrorCode.REDIS_OPERATION_FAILED, ErrorMessage.REDIS_OPERATION_FAILED, e);
        }
        return RetryDecision.builder().type(RetryDecisionType.WAITING).allowed(false).build();
    }

    private boolean isFirstExhausted(RetryInfo retryInfo) {
        return retryInfo != null && retryInfo.getCount() != null && retryInfo.getMaxRetryTimes() != null
                && retryInfo.getCount().equals(retryInfo.getMaxRetryTimes());
    }

    private void clearExhaustedRecord(String retryType, String retryKey, RetryInfo retryInfo) {
        if (properties.getRedis().isRetainExhausted()) {
            return;
        }
        try {
            String redisKey = retryKeyHelper.buildRedisKey(retryType, retryKey);
            redisRouteTemplate.execute(redisKey,
                    template -> scriptExecutor.clear(template, redisKey, retryInfo.getCount()));
        } catch (RuntimeException e) {
            log.warn("Smart Redis Retry 自动清理耗尽记录失败，retryType={}, retryKey={}", retryType, retryKey, e);
        }
    }

    private long toTtlMillis(long recordTtlSeconds) {
        if (recordTtlSeconds > SmartRedisRetryConstant.MAX_RECORD_TTL_SECONDS) {
            throw new RetryOperationException(ErrorCode.RECORD_TTL_TOO_LARGE,
                    ErrorMessage.RECORD_TTL_TOO_LARGE);
        }
        return Math.multiplyExact(recordTtlSeconds, SmartRedisRetryConstant.MILLIS_PER_SECOND);
    }

    private void safeOnDecision(String retryType, String retryKey, RetryDecision decision) {
        try {
            listener.onDecision(retryType, retryKey, decision);
        } catch (Exception e) {
            log.debug("Smart Redis Retry 决策监听异常", e);
        }
    }

    private void safeOnRecord(RetryFailure failure, RetryInfo retryInfo) {
        try {
            listener.onRecord(failure, retryInfo);
        } catch (Exception e) {
            log.debug("Smart Redis Retry 记录监听异常", e);
        }
    }

    private void safeOnClear(String retryType, String retryKey, RetryInfo retryInfo) {
        try {
            listener.onClear(retryType, retryKey, retryInfo);
        } catch (Exception e) {
            log.debug("Smart Redis Retry 清理监听异常", e);
        }
    }

    private void safeOnExhausted(String retryType, String retryKey, RetryInfo retryInfo) {
        try {
            listener.onExhausted(retryType, retryKey, retryInfo);
        } catch (Exception e) {
            log.debug("Smart Redis Retry 耗尽监听异常", e);
        }
    }
}
