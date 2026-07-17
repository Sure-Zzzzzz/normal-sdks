package io.github.surezzzzzz.sdk.retry.redis.smart.script;

import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.smart.constant.SmartRedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.smart.exception.RetryOperationException;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.RetryContextSerializer;
import io.github.surezzzzzz.sdk.retry.redis.smart.support.RetryInfoConvertHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lua Redis 重试脚本执行器
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class LuaRedisRetryScriptExecutor implements RedisRetryScriptExecutor {

    /**
     * 重试上下文序列化器
     */
    private final RetryContextSerializer serializer;
    /**
     * 重试状态转换器
     */
    private final RetryInfoConvertHelper retryInfoConvertHelper;
    /**
     * 原子记录失败脚本
     */
    private final DefaultRedisScript<List> recordFailureScript = buildRecordFailureScript();
    /**
     * 原子清理记录脚本
     */
    private final DefaultRedisScript<List> clearScript = buildClearScript();

    /**
     * 原子记录失败信息。
     *
     * @param template  Redis 操作模板
     * @param redisKey  Redis 记录 Key
     * @param failure   失败信息
     * @param policy    重试策略
     * @param nowMillis 当前时间毫秒值
     * @param ttlMillis 记录存活时间毫秒值
     * @return 最新重试状态
     */
    @Override
    @SuppressWarnings("unchecked")
    public RetryInfo recordFailure(StringRedisTemplate template,
                                   String redisKey,
                                   RetryFailure failure,
                                   RetryPolicy policy,
                                   long nowMillis,
                                   long ttlMillis) {
        String contextJson = serializer.serialize(failure.getContext());
        List<Object> result;
        try {
            result = (List<Object>) template.execute(recordFailureScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(nowMillis),
                    String.valueOf(ttlMillis),
                    String.valueOf(policy.getMaxRetryTimes()),
                    String.valueOf(policy.getRetryIntervalMillis()),
                    String.valueOf(policy.getMaxIntervalMillis()),
                    String.valueOf(policy.getBackoffMultiplier()),
                    String.valueOf(policy.getJitterRatio()),
                    String.valueOf(failure.getRetryKey().hashCode()),
                    nullToEmpty(failure.getErrorCode()),
                    nullToEmpty(failure.getErrorMessage()),
                    nullToEmpty(contextJson));
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.LUA_SCRIPT_EXECUTION_FAILED,
                    ErrorMessage.LUA_SCRIPT_EXECUTION_FAILED, e);
        }
        try {
            String storedContextJson = retryInfoConvertHelper.contextJson(result);
            RetryInfo retryInfo = retryInfoConvertHelper.fromScriptResult(result,
                    serializer.deserialize(storedContextJson));
            if (retryInfo == null) {
                throw new RetryOperationException(ErrorCode.LUA_SCRIPT_RESULT_INVALID,
                        ErrorMessage.LUA_SCRIPT_RESULT_INVALID);
            }
            return retryInfo;
        } catch (RetryOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.LUA_SCRIPT_RESULT_CONVERT_FAILED,
                    ErrorMessage.LUA_SCRIPT_RESULT_CONVERT_FAILED, e);
        }
    }

    /**
     * 原子读取并删除重试状态。
     *
     * @param template      Redis 操作模板
     * @param redisKey      Redis 记录 Key
     * @param expectedCount 期望的失败次数，空值表示不校验
     * @return 被删除的重试状态；记录不存在或失败次数已变化时返回 null
     */
    @Override
    @SuppressWarnings("unchecked")
    public RetryInfo clear(StringRedisTemplate template, String redisKey, Integer expectedCount) {
        List<Object> values;
        try {
            values = (List<Object>) template.execute(clearScript,
                    Collections.singletonList(redisKey),
                    expectedCount == null ? SmartRedisRetryConstant.EMPTY : String.valueOf(expectedCount));
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.LUA_SCRIPT_EXECUTION_FAILED,
                    ErrorMessage.LUA_SCRIPT_EXECUTION_FAILED, e);
        }
        // clear.lua 在记录不存在或 expectedCount 不匹配时返回 nil，
        // Spring 可能反序列化为 null 或不足一对的单元素结果，均表示未命中，直接返回 null。
        if (values == null || values.size() < SmartRedisRetryConstant.HASH_ENTRY_WIDTH) {
            return null;
        }
        try {
            Map<Object, Object> hash = toHash(values);
            if (hash.isEmpty()) {
                return null;
            }
            return retryInfoConvertHelper.fromHash(hash,
                    serializer.deserialize(retryInfoConvertHelper.contextJson(hash)));
        } catch (Exception e) {
            throw new RetryOperationException(ErrorCode.LUA_SCRIPT_RESULT_CONVERT_FAILED,
                    ErrorMessage.LUA_SCRIPT_RESULT_CONVERT_FAILED, e);
        }
    }

    private DefaultRedisScript<List> buildRecordFailureScript() {
        return buildScript(SmartRedisRetryConstant.RECORD_FAILURE_SCRIPT_PATH);
    }

    private DefaultRedisScript<List> buildClearScript() {
        return buildScript(SmartRedisRetryConstant.CLEAR_RETRY_SCRIPT_PATH);
    }

    private DefaultRedisScript<List> buildScript(String scriptPath) {
        DefaultRedisScript<List> script = new DefaultRedisScript<List>();
        script.setLocation(new ClassPathResource(scriptPath));
        script.setResultType(List.class);
        return script;
    }

    private Map<Object, Object> toHash(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Object, Object> hash = new LinkedHashMap<Object, Object>();
        for (int index = SmartRedisRetryConstant.SCRIPT_RESULT_COUNT_INDEX;
             index < values.size();
             index += SmartRedisRetryConstant.HASH_ENTRY_WIDTH) {
            hash.put(values.get(index), values.get(index + SmartRedisRetryConstant.HASH_VALUE_INDEX_OFFSET));
        }
        return hash;
    }

    private String nullToEmpty(String value) {
        return value == null ? SmartRedisRetryConstant.EMPTY : value;
    }
}
