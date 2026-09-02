package io.github.surezzzzzz.sdk.auth.captcha.test.cases;

import io.github.surezzzzzz.sdk.auth.captcha.configuration.SimpleCaptchaProperties;
import io.github.surezzzzzz.sdk.auth.captcha.constant.SimpleCaptchaConstant;
import io.github.surezzzzzz.sdk.auth.captcha.model.CaptchaChallenge;
import io.github.surezzzzzz.sdk.auth.captcha.spi.CaptchaProvider;
import io.github.surezzzzzz.sdk.auth.captcha.test.SimpleCaptchaTestApplication;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Image Captcha Provider Test
 *
 * <p>挑战存储走本机真实 Redis（连接由 redis-route 接管）；测试经 route 门面
 * 读取答案构造正向用例（Provider 本身是被测对象，答案可预期性由存储侧提供）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleCaptchaTestApplication.class)
class ImageCaptchaProviderTest {

    @Autowired
    private CaptchaProvider captchaProvider;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private SimpleCaptchaProperties properties;

    /**
     * 按模块 key 规范构建挑战 key（与 ImageCaptchaProvider 一致）
     */
    private String buildChallengeKey(String captchaId) {
        return SimpleCaptchaConstant.KEY_PREFIX
                + SimpleCaptchaConstant.SEPARATOR_COLON + SimpleCaptchaConstant.BUSINESS_CHALLENGE
                + SimpleCaptchaConstant.SEPARATOR_COLON + SimpleCaptchaConstant.BRACE_PREFIX
                + properties.getRedis().getMe() + SimpleCaptchaConstant.BRACE_SUFFIX
                + SimpleCaptchaConstant.SEPARATOR_DOUBLE_COLON + captchaId;
    }

    /**
     * 经 route 门面读取挑战答案（测试正向用例构造）
     */
    private String readAnswer(String captchaId) {
        String key = buildChallengeKey(captchaId);
        return redisRouteTemplate.stringTemplateByKey(key).opsForValue().get(key);
    }

    @Test
    void shouldGenerateImageChallenge() {
        CaptchaChallenge challenge = captchaProvider.generate();

        log.info("生成的挑战：captchaId={}, type={}, contentLength={}",
                challenge.getCaptchaId(), challenge.getType(), challenge.getContent().length());
        assertNotNull(challenge.getCaptchaId(), "captchaId 不应为空");
        assertEquals(SimpleCaptchaConstant.CHALLENGE_TYPE_IMAGE, challenge.getType(), "默认实现类型应为 image");
        assertTrue(challenge.getContent().startsWith("data:image/png;base64,"), "content 应为 PNG data URI");

        byte[] pngBytes = Base64.getDecoder()
                .decode(challenge.getContent().substring(challenge.getContent().indexOf(',') + 1));
        log.info("解码后图片字节数：{}", pngBytes.length);
        assertTrue(pngBytes.length > 0, "解码后图片内容不应为空");
        assertEquals(0x89, pngBytes[0] & 0xFF, "应为 PNG 魔数开头");
    }

    @Test
    void shouldHonorConfigurableCharLength() {
        CaptchaChallenge challenge = captchaProvider.generate();
        String answer = readAnswer(challenge.getCaptchaId());

        log.info("配置字符个数={}，存储中答案={}（长度 {}）",
                properties.getImageCharLength(), answer, answer.length());
        assertEquals(properties.getImageCharLength(), answer.length(), "答案字符个数应与配置一致");
    }

    @Test
    void shouldHonorConfigurableTtl() {
        CaptchaChallenge challenge = captchaProvider.generate();
        String key = buildChallengeKey(challenge.getCaptchaId());
        Long expireSeconds = redisRouteTemplate.stringTemplateByKey(key)
                .getExpire(key, TimeUnit.SECONDS);

        log.info("配置 TTL={} 秒，Redis 实际剩余过期时间={} 秒",
                properties.getChallengeTtlSeconds(), expireSeconds);
        assertNotNull(expireSeconds, "挑战应设置过期时间");
        assertTrue(expireSeconds > 0 && expireSeconds <= properties.getChallengeTtlSeconds(),
                "剩余过期时间应在配置 TTL 范围内");
    }

    @Test
    void shouldVerifyWithCorrectAnswer() {
        CaptchaChallenge challenge = captchaProvider.generate();
        String answer = readAnswer(challenge.getCaptchaId());

        boolean passed = captchaProvider.verify(challenge.getCaptchaId(), answer);
        log.info("正确答案校验：captchaId={}, answer={}, passed={}", challenge.getCaptchaId(), answer, passed);
        assertTrue(passed, "正确答案应校验通过");
    }

    @Test
    void shouldVerifyCaseInsensitive() {
        CaptchaChallenge challenge = captchaProvider.generate();
        String answer = readAnswer(challenge.getCaptchaId());

        boolean passed = captchaProvider.verify(challenge.getCaptchaId(), answer.toUpperCase());
        log.info("大写答案校验：original={}, upper={}, passed={}", answer, answer.toUpperCase(), passed);
        assertTrue(passed, "校验应忽略大小写");
    }

    @Test
    void shouldRejectWrongAnswer() {
        CaptchaChallenge challenge = captchaProvider.generate();
        String answer = readAnswer(challenge.getCaptchaId());
        String wrongAnswer = answer + "x";

        boolean passed = captchaProvider.verify(challenge.getCaptchaId(), wrongAnswer);
        log.info("错误答案校验：answer={}, wrongAnswer={}, passed={}", answer, wrongAnswer, passed);
        assertFalse(passed, "错误答案应校验失败");
    }

    @Test
    void shouldConsumeChallengeOnce() {
        CaptchaChallenge challenge = captchaProvider.generate();
        String answer = readAnswer(challenge.getCaptchaId());

        boolean first = captchaProvider.verify(challenge.getCaptchaId(), answer);
        boolean second = captchaProvider.verify(challenge.getCaptchaId(), answer);
        log.info("一次性消费：first={}, second={}", first, second);
        assertTrue(first, "首次校验应通过");
        assertFalse(second, "同一挑战二次校验应失败（一次性消费）");
    }

    @Test
    void shouldFailWhenChallengeExpired() {
        CaptchaChallenge challenge = captchaProvider.generate();
        String answer = readAnswer(challenge.getCaptchaId());
        // 直接删除 key 模拟过期
        String key = buildChallengeKey(challenge.getCaptchaId());
        redisRouteTemplate.stringTemplateByKey(key).delete(key);

        boolean passed = captchaProvider.verify(challenge.getCaptchaId(), answer);
        log.info("过期挑战校验：captchaId={}, passed={}", challenge.getCaptchaId(), passed);
        assertFalse(passed, "过期（不存在）挑战应校验失败");
    }

    @Test
    void shouldFailWhenAnswerBlank() {
        CaptchaChallenge challenge = captchaProvider.generate();

        boolean blankPassed = captchaProvider.verify(challenge.getCaptchaId(), "  ");
        boolean nullPassed = captchaProvider.verify(challenge.getCaptchaId(), null);
        log.info("空白答案校验：blankPassed={}, nullPassed={}", blankPassed, nullPassed);
        assertFalse(blankPassed, "空白答案应校验失败");
        assertFalse(nullPassed, "null 答案应校验失败");
    }
}
