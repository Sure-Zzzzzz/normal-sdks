package io.github.surezzzzzz.sdk.feedback.server.test.support;

import io.github.surezzzzzz.sdk.iam.client.IamApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 测试级 IAM 客户端兜底装配（仅测试 classpath 的 spring.factories 注册，不入模块产物）。
 *
 * <p>非直观约束：starter 的 testImplementation 以 project() 引 adaptor（共存验收需要），
 * adaptor 扫描件在所有测试上下文请求 IamUserClient/IamApplicationClient 构造注入——
 * 不出站的日常/单测上下文本无客户端。本配置缺席补 mock（@ConditionalOnMissingBean 让位），
 * 显式桩（协作/共存测试的真实形态）优先；mock 从不被调用，SPI 消费方由各测试的
 *
 * @author surezzzzzz
 * @Primary 桩占住。</p>
 */
@Configuration
public class IamClientTestAutoConfiguration {

    /**
     * IAM 用户客户端测试兜底（不出站 mock）。
     *
     * @return mock 客户端
     */
    @Bean
    @ConditionalOnMissingBean(IamUserClient.class)
    public IamUserClient testFallbackIamUserClient() {
        return Mockito.mock(IamUserClient.class);
    }

    /**
     * IAM 应用目录客户端测试兜底（不出站 mock）。
     *
     * @return mock 客户端
     */
    @Bean
    @ConditionalOnMissingBean(IamApplicationClient.class)
    public IamApplicationClient testFallbackIamApplicationClient() {
        return Mockito.mock(IamApplicationClient.class);
    }
}
