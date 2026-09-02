package io.github.surezzzzzz.sdk.auth.captcha.test.cases;

import io.github.surezzzzzz.sdk.auth.captcha.configuration.SimpleCaptchaAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.captcha.spi.CaptchaProvider;
import io.github.surezzzzzz.sdk.auth.captcha.spi.ImageCaptchaProvider;
import io.github.surezzzzzz.sdk.auth.captcha.storage.ChallengeStore;
import io.github.surezzzzzz.sdk.auth.captcha.storage.RedisRouteChallengeStore;
import io.github.surezzzzzz.sdk.auth.captcha.test.SimpleCaptchaTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple Captcha Auto Configuration Test
 *
 * <p>默认装配场景走主测试上下文（真实 Redis，redis-route 接管）；
 * 缺 route 快速失败场景使用 ApplicationContextRunner 独立上下文验证
 * （不依赖 Redis）。默认实现即唯一内置实现（组件装配）；自定义实现的接入方
 * 不引本模块运行时，无让位场景。被动功能模块无 enable 开关
 * （不想用不引包），挑战存储强制 Redis 无内存模式。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleCaptchaTestApplication.class)
class SimpleCaptchaAutoConfigurationTest {

    @Autowired
    private CaptchaProvider captchaProvider;

    @Autowired
    private ChallengeStore challengeStore;

    /**
     * 默认装配：宿主存在 redis-route 时注册图片实现 + Redis 共享存储
     */
    @Test
    void shouldRegisterDefaultProviderWithRedisRoute() {
        log.info("默认装配的 Provider 类型：{}", captchaProvider.getClass().getName());
        log.info("默认装配的挑战存储类型：{}", challengeStore.getClass().getName());
        assertThat(captchaProvider).isInstanceOf(ImageCaptchaProvider.class);
        assertThat(challengeStore).isInstanceOf(RedisRouteChallengeStore.class);
    }

    /**
     * 宿主无 redis-route bean 时启动快速失败（强制 Redis，无内存模式）
     */
    @Test
    void noRouteFailsStartupFast() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleCaptchaAutoConfiguration.class))
                .run(context -> {
                    log.info("验证缺 redis-route 时应用启动快速失败");
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
                });
    }
}
