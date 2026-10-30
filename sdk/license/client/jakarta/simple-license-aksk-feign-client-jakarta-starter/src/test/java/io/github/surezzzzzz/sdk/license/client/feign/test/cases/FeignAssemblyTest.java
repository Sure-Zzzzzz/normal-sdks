package io.github.surezzzzzz.sdk.license.client.feign.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import feign.RequestInterceptor;
import io.github.surezzzzzz.sdk.license.client.feign.LicenseFeignJakartaAutoConfiguration;
import io.github.surezzzzzz.sdk.license.client.spi.LicenseServerClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 装配缺件响亮失败，宿主提供整体客户端时不再强制无用的认证与转换器底座。
 *
 * @author surezzzzzz
 */
@Slf4j
class FeignAssemblyTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(LicenseFeignJakartaAutoConfiguration.class)
                .withPropertyValues("io.github.surezzzzzz.sdk.license.client.feign.enabled=true",
                        "io.github.surezzzzzz.sdk.license.client.base-url=http://127.0.0.1:1");
    }

    @Test
    void officialNamedInterceptorAndConvertersRegisterOneRealContract() {
        log.info("验证完整底座注册唯一客户端及真实Feign契约");
        runner().withBean("akskFeignRequestInterceptor", RequestInterceptor.class, () -> template -> {
        }).withBean(HttpMessageConverters.class, HttpMessageConverters::new).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LicenseServerClient.class);
            assertThat(context).hasSingleBean(LicenseFeignJakartaAutoConfiguration.LicenseContractFeign.class);
        });
    }

    @Test
    void missingAuthenticationOrConvertersFailsAtStartup() {
        log.info("验证缺认证或缺转换器在启动期失败");
        runner().withBean(HttpMessageConverters.class, HttpMessageConverters::new)
                .run(context -> assertThat(context).hasFailed());
        runner().withBean("akskFeignRequestInterceptor", RequestInterceptor.class, () -> template -> {
        }).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void customClientMakesDefaultContractAndDependenciesYield() {
        log.info("验证宿主整体覆盖时默认契约让位，不要求无用底座");
        runner().withBean(LicenseServerClient.class, () -> mock(LicenseServerClient.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LicenseServerClient.class);
            assertThat(context).doesNotHaveBean(LicenseFeignJakartaAutoConfiguration.LicenseContractFeign.class);
        });
    }
}
