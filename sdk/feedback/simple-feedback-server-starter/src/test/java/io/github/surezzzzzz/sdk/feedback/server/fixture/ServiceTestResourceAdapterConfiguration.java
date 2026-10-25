package io.github.surezzzzzz.sdk.feedback.server.fixture;

import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 服务层测试不发起认证请求，但真实资源安全链仍要求存在认证适配器。
 *
 * @author surezzzzzz
 */
@TestConfiguration
public class ServiceTestResourceAdapterConfiguration {

    @Bean
    public ResourceAuthenticationAdapter feedbackServiceTestResourceAdapter() {
        return new ResourceAuthenticationAdapter() {
            @Override
            public ResourceAuthenticationSourceId sourceId() {
                return new ResourceAuthenticationSourceId("feedback-test");
            }

            @Override
            public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
                return ResourceAuthenticationResult.notApplicable();
            }
        };
    }
}
