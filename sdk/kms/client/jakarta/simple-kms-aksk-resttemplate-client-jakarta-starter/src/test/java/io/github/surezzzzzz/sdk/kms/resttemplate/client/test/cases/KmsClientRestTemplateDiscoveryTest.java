package io.github.surezzzzzz.sdk.kms.resttemplate.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.KmsRestTemplateClient;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.test.SimpleKmsClientRestTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发现路径测试：经 spring.factories / AutoConfiguration.imports 元数据真实启动并装配客户端。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleKmsClientRestTemplateTestApplication.class)
class KmsClientRestTemplateDiscoveryTest {

    @Autowired
    private KmsClient kmsClient;

    @Test
    void shouldDiscoverAutoConfigurationFromMetadata() {
        log.info("发现路径装配的客户端类型: {}", kmsClient.getClass().getName());
        assertNotNull(kmsClient, "经元数据发现必须装配 KmsClient");
        assertTrue(kmsClient instanceof KmsRestTemplateClient, "装配的必须是 KmsRestTemplateClient");
    }
}
