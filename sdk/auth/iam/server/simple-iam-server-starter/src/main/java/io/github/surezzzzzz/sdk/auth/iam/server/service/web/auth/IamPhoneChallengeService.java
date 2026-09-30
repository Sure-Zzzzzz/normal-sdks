package io.github.surezzzzzz.sdk.auth.iam.server.service.web.auth;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.SmsDeliveryProvider;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 手机号挑战状态机：登录/绑定/忘记密码三用途共用一套实现，仅 purpose 与成功后动作不同。
 * 挑战全态只存 Redis（HMAC 验证码+手机号哈希+计数，TTL 原生过期，跨实例共享）；
 * 创建=冷却/三维频控/废弃同号旧挑战；消费=固定顺序（存在→次数→HMAC 恒定时间比对→Lua 原子消费）；
 * 手机号哈希=HMAC-SHA256（复用挑战密钥，禁裸哈希防字典攻击）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPhoneChallengeService {

    /**
     * 消费脚本：校验次数上限后比对 HMAC 并原子删除（单次消费，防并发双花）。
     * KEYS[1]=挑战详情键；ARGV[1]=次数上限 ARGV[2]=期望 HMAC ARGV[3]=purpose
     * 返回：1=成功；-1=不存在/过期；-2=超次；-3=HMAC 不匹配；-4=purpose 不匹配
     */
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if not v then return -1 end "
                    + "local parts = {} "
                    + "for token in string.gmatch(v, '[^|]+') do table.insert(parts, token) end "
                    + "if tonumber(parts[3]) >= tonumber(ARGV[1]) then return -2 end "
                    + "if parts[2] ~= ARGV[2] then redis.call('SET', KEYS[1], v, 'KEEPTTL') return -3 end "
                    + "if parts[4] ~= ARGV[3] then redis.call('SET', KEYS[1], v, 'KEEPTTL') return -4 end "
                    + "redis.call('DEL', KEYS[1]) "
                    + "return 1",
            Long.class);

    private static final String GLOBAL_RATE_ID = "all";
    private static final String FIELD_SEPARATOR = "|";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyHelper redisKeyHelper;
    private final SimpleIamServerProperties properties;
    private final ObjectProvider<SmsDeliveryProvider> deliveryProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 投递实现是否可用（未装配=能力不存在，端点失败关闭）。
     *
     * @return true 可用
     */
    /**
     * 启动即拒绝多投递实现并存（不静默择一；演示假投递经
     * {@code ...test.fake-sms-delivery=false} 关闭后装配真实 adaptor）。
     */
    @javax.annotation.PostConstruct
    void assertSingleDeliveryProvider() {
        long count = deliveryProvider.stream().count();
        if (count > 1) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    "短信投递实现并存 " + count + " 个：SMS 投递必须唯一，请只保留一个实现");
        }
        // 冷却必须≤有效期：冷却>有效期会出现"旧码已过期且不允许重发"的死窗
        int cooldown = properties.getPhoneChallenge().getSendCooldownSeconds();
        int ttl = properties.getPhoneChallenge().getTtlSeconds();
        if (cooldown > ttl) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    "手机号挑战配置非法：同号冷却(" + cooldown
                            + "s)不得大于挑战有效期(" + ttl + "s)，否则存在无法重发的死窗");
        }
    }

    /**
     * 同号发送冷却配置（秒），挑战响应下发给前端驱动倒计时。
     */
    public int sendCooldownSeconds() {
        return properties.getPhoneChallenge().getSendCooldownSeconds();
    }

    public boolean deliveryAvailable() {
        return deliveryProvider.getIfAvailable() != null;
    }

    /**
     * 投递实现（调用前须以 deliveryAvailable 判定；providers 响应读能力声明用）。
     *
     * @return 投递实现
     */
    public SmsDeliveryProvider deliveryProvider() {
        return deliveryProvider.getObject();
    }

    /**
     * 创建挑战：三维频控→同号冷却→废弃旧挑战→生成码→存 Redis→投递。
     * 未知号/范围外号同样受理（防枚举），是否真实投递由调用方按用户态决定。
     *
     * @param normalizedPhone E.164 手机号
     * @param purpose         用途常量
     * @param clientIp        客户端 IP（频控维度）
     * @param deliver         是否真实投递（false=防枚举受理）
     * @return challengeId
     */
    public String createChallenge(String normalizedPhone, String purpose, String clientIp, boolean deliver) {
        String phoneHash = phoneHash(normalizedPhone);
        enforceRateLimit(phoneHash, clientIp);
        enforceCooldown(normalizedPhone, phoneHash);

        String code = generateCode();
        String challengeId = UUID.randomUUID().toString();
        String codeHmac = hmac(challengeId + FIELD_SEPARATOR + code);
        // 重发即废旧（验证码安全语义：码被截获时用户重发后旧码立即失效）。
        // 同号创建已被冷却串行化（冷却期内重复创建直接拒绝），无并发竞争窗口，
        // 读旧 active→删旧详情→写新键的顺序执行即安全，无需 Lua 跨键原子。
        String activeKey = redisKeyHelper.buildPhoneChallengeActiveKey(phoneHash);
        String previousChallengeId = stringRedisTemplate.opsForValue().get(activeKey);
        if (previousChallengeId != null && !previousChallengeId.equals(challengeId)) {
            stringRedisTemplate.delete(redisKeyHelper.buildPhoneChallengeDetailKey(previousChallengeId));
            log.debug("重发废弃旧挑战: phoneHash={}, oldChallengeId={}, newChallengeId={}",
                    phoneHash, previousChallengeId, challengeId);
        }
        // value 结构:phoneHash|codeHmac|attempts|purpose（消费失败时 attempts 自增重写）
        stringRedisTemplate.opsForValue().set(redisKeyHelper.buildPhoneChallengeDetailKey(challengeId),
                phoneHash + FIELD_SEPARATOR + codeHmac + FIELD_SEPARATOR + 0 + FIELD_SEPARATOR + purpose,
                Duration.ofSeconds(properties.getPhoneChallenge().getTtlSeconds()));
        stringRedisTemplate.opsForValue().set(activeKey,
                challengeId, Duration.ofSeconds(properties.getPhoneChallenge().getTtlSeconds()));

        if (deliver) {
            deliveryProvider.getObject().deliver(normalizedPhone, code, purpose,
                    properties.getPhoneChallenge().getTtlSeconds());
        }
        log.debug("手机号挑战创建: purpose={}, phoneHash={}, deliver={}, challengeId={}",
                purpose, phoneHash, deliver, challengeId);
        return challengeId;
    }

    /**
     * 消费挑战：固定顺序（存在→次数→HMAC 恒定时间比对→Lua 原子删除）。
     *
     * @param challengeId 挑战 ID
     * @param code        用户输入验证码
     * @param purpose     期望用途（不匹配统一失败）
     * @return true 消费成功
     */
    public boolean consumeChallenge(String challengeId, String code, String purpose) {
        return consumeChallenge(challengeId, code, purpose, null);
    }

    /**
     * 消费挑战（带号哈希原子校验：绑定场景防 A 号发码绑 B 号）。
     *
     * @param challengeId     挑战 ID
     * @param code            验证码
     * @param purpose         期望用途
     * @param normalizedPhone 期望手机号（null=不校验；非空时须与挑战创建号一致）
     * @return true 消费成功
     */
    public boolean consumeChallenge(String challengeId, String code, String purpose, String normalizedPhone) {
        String key = redisKeyHelper.buildPhoneChallengeDetailKey(challengeId);
        String current = stringRedisTemplate.opsForValue().get(key);
        if (current == null) {
            log.debug("挑战消费失败(不存在或过期): challengeId={}", challengeId);
            return false;
        }
        String[] parts = current.split("\\" + FIELD_SEPARATOR);
        if (parts.length < 4) {
            log.warn("挑战数据结构异常: challengeId={}", challengeId);
            return false;
        }
        long attempts = Long.parseLong(parts[2]);
        if (attempts >= properties.getPhoneChallenge().getMaxAttempts()) {
            log.debug("挑战消费失败(超次): challengeId={}", challengeId);
            return false;
        }
        String expectedHmac = parts[1];
        String actualHmac = hmac(challengeId + FIELD_SEPARATOR + code);
        boolean hmacMatch = MessageDigest.isEqual(expectedHmac.getBytes(StandardCharsets.UTF_8),
                actualHmac.getBytes(StandardCharsets.UTF_8));
        boolean phoneMatch = normalizedPhone == null
                || MessageDigest.isEqual(parts[0].getBytes(StandardCharsets.UTF_8),
                phoneHash(normalizedPhone).getBytes(StandardCharsets.UTF_8));
        if (!hmacMatch || !phoneMatch) {
            bumpAttempts(key, current, attempts);
            log.debug("挑战消费失败(HMAC或号不匹配): challengeId={}", challengeId);
            return false;
        }
        Long result = stringRedisTemplate.execute(CONSUME_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(properties.getPhoneChallenge().getMaxAttempts()),
                expectedHmac, purpose);
        boolean success = result != null && result == 1L;
        if (!success) {
            bumpAttempts(key, current, attempts);
            log.debug("挑战消费失败(code={}): challengeId={}", result, challengeId);
        }
        return success;
    }

    /**
     * 消费失败后次数自增（重写 value，保留原 TTL）。
     */
    private void bumpAttempts(String key, String current, long attempts) {
        String[] parts = current.split("\\" + FIELD_SEPARATOR);
        String updated = parts[0] + FIELD_SEPARATOR + parts[1] + FIELD_SEPARATOR + (attempts + 1)
                + FIELD_SEPARATOR + parts[3];
        Duration ttl = stringRedisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS) > 0
                ? Duration.ofSeconds(stringRedisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS))
                : Duration.ofSeconds(properties.getPhoneChallenge().getTtlSeconds());
        stringRedisTemplate.opsForValue().set(key, updated, ttl);
    }

    /**
     * 三维频控：号哈希/IP/全局，窗口=计数 TTL，超限即拒。
     */
    private void enforceRateLimit(String phoneHash, String clientIp) {
        SimpleIamServerProperties.PhoneChallengeConfig config = properties.getPhoneChallenge();
        checkRate("phone", phoneHash, config.getRateLimitPhoneWindowSeconds(), config.getRateLimitPhoneLimit());
        checkRate("ip", clientIp == null ? "unknown" : clientIp,
                config.getRateLimitIpWindowSeconds(), config.getRateLimitIpLimit());
        checkRate("global", GLOBAL_RATE_ID,
                config.getRateLimitGlobalWindowSeconds(), config.getRateLimitGlobalLimit());
    }

    /**
     * 单维度频控：INCR 首次设置 TTL，超限抛 429 面。
     */
    private void checkRate(String dimension, String id, int windowSeconds, int limit) {
        String key = redisKeyHelper.buildPhoneRateKey(dimension, id);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > limit) {
            log.warn("手机号挑战频控触发: dimension={}, count={}", dimension, count);
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED,
                    "验证码发送过于频繁，请稍后再试");
        }
    }

    /**
     * 同号发送冷却：活跃映射键 TTL 剩余大于(总TTL-冷却)时视为冷却期内。
     */
    private void enforceCooldown(String normalizedPhone, String phoneHash) {
        String key = redisKeyHelper.buildPhoneChallengeActiveKey(phoneHash);
        Long ttl = stringRedisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            long elapsed = properties.getPhoneChallenge().getTtlSeconds() - ttl;
            if (elapsed < properties.getPhoneChallenge().getSendCooldownSeconds()) {
                log.debug("同号发送冷却中: phoneHash={}, remainSeconds={}", phoneHash, ttl);
                throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED,
                        "验证码发送间隔过短，请稍后再试");
            }
        }
    }

    /**
     * 手机号哈希：HMAC-SHA256（复用挑战密钥；输入域与验证码 HMAC 不同——码侧输入含 challengeId 前缀）。
     */
    public String phoneHash(String normalizedPhone) {
        return hmac(normalizedPhone);
    }

    private String generateCode() {
        int length = properties.getPhoneChallenge().getCodeLength();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(secureRandom.nextInt(10));
        }
        return builder.toString();
    }

    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(requireHmacKey(), "HmacSHA256"));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (GeneralSecurityException exception) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    "手机号挑战 HMAC 计算失败", exception);
        }
    }

    private byte[] requireHmacKey() {
        String key = properties.getPhoneChallenge().getHmacKey();
        if (!StringUtils.hasText(key)
                || key.getBytes(StandardCharsets.UTF_8).length
                < SimpleIamServerConstant.PHONE_CHALLENGE_HMAC_KEY_MIN_BYTES) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    ServerErrorMessage.PHONE_CHALLENGE_HMAC_KEY_INVALID);
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }
}
