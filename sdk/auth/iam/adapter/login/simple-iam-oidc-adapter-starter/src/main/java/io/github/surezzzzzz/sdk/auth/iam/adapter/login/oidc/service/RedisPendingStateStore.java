package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.constant.SimpleIamOidcAdapterConstant;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.exception.SimpleIamOidcAdapterException;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.model.PendingAuthorization;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 授权暂存上下文的 Redis 实现（多实例部署：authorize 与 callback 可落在不同实例）。
 *
 * <p>key 形态 {@code sure-auth-iam:oidc-pending:{state}}，经 redis-route 按前缀
 * 落 default 数据源；TTL 即过期；consume 用 getAndDelete 原子取删，保证 state 一次性。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisPendingStateStore implements PendingStateStore {

    private final RedisRouteTemplate redisRouteTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisPendingStateStore(RedisRouteTemplate redisRouteTemplate) {
        this.redisRouteTemplate = redisRouteTemplate;
    }

    private static String shortState(String state) {
        return state == null ? "null" : state.substring(0, Math.min(8, state.length()));
    }

    /**
     * 暂存 state → 授权上下文（Redis，多实例共享）
     */
    @Override
    public void save(String state, PendingAuthorization pending, Duration ttl) {
        String key = keyOf(state);
        redisRouteTemplate.stringTemplateByKey(key).opsForValue().set(key, toJson(pending), ttl);
        log.debug("OIDC 授权上下文已暂存：state={}, ttl={}s", shortState(state), ttl.getSeconds());
    }

    /**
     * 一次性消费 state（多实例防重放）
     */
    @Override
    public PendingAuthorization consume(String state) {
        String key = keyOf(state);
        String json = redisRouteTemplate.stringTemplateByKey(key).opsForValue().getAndDelete(key);
        if (!StringUtils.hasText(json)) {
            log.debug("OIDC 授权上下文不存在或已消费：state={}", shortState(state));
            return null;
        }
        log.debug("OIDC 授权上下文已消费（原子取删）：state={}", shortState(state));
        return fromJson(json);
    }

    private String keyOf(String state) {
        return SimpleIamOidcAdapterConstant.PENDING_KEY_PREFIX + state;
    }

    private String toJson(PendingAuthorization pending) {
        try {
            return objectMapper.writeValueAsString(pending);
        } catch (JsonProcessingException exception) {
            throw new SimpleIamOidcAdapterException(SimpleIamOidcAdapterConstant.ERROR_PENDING_STATE_IO,
                    "OIDC 授权上下文序列化失败", exception);
        }
    }

    private PendingAuthorization fromJson(String json) {
        try {
            return objectMapper.readValue(json, PendingAuthorization.class);
        } catch (JsonProcessingException exception) {
            throw new SimpleIamOidcAdapterException(SimpleIamOidcAdapterConstant.ERROR_PENDING_STATE_IO,
                    "OIDC 授权上下文反序列化失败", exception);
        }
    }
}
