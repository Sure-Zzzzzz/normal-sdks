package io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.test.cases;

import io.github.surezzzzzz.sdk.auth.captcha.spi.ImageCaptchaProvider;
import io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.service.CaptchaProviderAdapter;
import io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.test.SimpleIamCaptchaAdapterTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaChallenge;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple IAM Captcha Adapter 自动装配测试
 *
 * <p>验证适配器把通用 captcha 模块桥接为 iam-core 契约：
 * iam-core 注入点拿到适配器、底层委托为通用图片实现、
 * 出题经模型转换可用、验题经转发一次性消费（真 Redis fixture）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamCaptchaAdapterTestApplication.class)
class SimpleIamCaptchaAdapterAutoConfigurationTest {

    private static final String CHALLENGE_KEY_TEMPLATE = "sure-auth-captcha:challenge:{default}::%s";

    @Autowired
    private io.github.surezzzzzz.sdk.auth.iam.core.spi.CaptchaProvider iamCaptchaProvider;

    @Autowired
    private io.github.surezzzzzz.sdk.auth.captcha.spi.CaptchaProvider genericCaptchaProvider;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    /**
     * iam-core 契约注入点是适配器，底层委托为通用模块默认图片实现
     */
    @Test
    void shouldBridgeGenericProviderAsIamCoreContract() {
        assertEquals(CaptchaProviderAdapter.class, iamCaptchaProvider.getClass(),
                "iam-core 注入点必须是适配器");
        assertEquals(ImageCaptchaProvider.class, genericCaptchaProvider.getClass(),
                "通用模块注入点必须是默认图片实现");
        log.info("桥接形态：iam-core={} 委托 generic={}",
                iamCaptchaProvider.getClass().getSimpleName(),
                genericCaptchaProvider.getClass().getSimpleName());
    }

    /**
     * 出题经模型转换可用（captchaId / image 类型 / data URI），验题经转发一次性消费
     */
    @Test
    void shouldGenerateAndVerifyThroughBridge() {
        CaptchaChallenge challenge = iamCaptchaProvider.generate();
        assertEquals("image", challenge.getType(), "默认实现挑战类型为 image");
        assertTrue(challenge.getContent().startsWith("data:image/png;base64,"), "图片内容为 data URI");

        String key = String.format(CHALLENGE_KEY_TEMPLATE, challenge.getCaptchaId());
        String answer = redisRouteTemplate.stringTemplateByKey(key).opsForValue().get(key);
        assertTrue(answer != null && !answer.isEmpty(), "挑战答案应已写入 Redis");
        assertTrue(iamCaptchaProvider.verify(challenge.getCaptchaId(), answer), "正确答案应通过（经转发）");
        assertFalse(iamCaptchaProvider.verify(challenge.getCaptchaId(), answer), "二次消费应失败（一次性）");
        log.info("桥接闭环：captchaId={}, 答案经 Redis 读回校验通过", challenge.getCaptchaId());
    }
}
