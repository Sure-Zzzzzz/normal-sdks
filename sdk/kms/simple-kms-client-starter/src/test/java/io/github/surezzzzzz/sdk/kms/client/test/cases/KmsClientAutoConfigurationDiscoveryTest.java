package io.github.surezzzzzz.sdk.kms.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.configuration.SimpleKmsClientAutoConfiguration;
import io.github.surezzzzzz.sdk.kms.client.port.KeyEncryptionPort;
import io.github.surezzzzzz.sdk.kms.client.port.TenantPublicKeyPort;
import io.github.surezzzzzz.sdk.kms.client.port.TenantSignerPort;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple KMS Client 自动配置 元数据 发现测试。
 *
 * <p>真实启动自动配置，同时逐个枚举 classpath 同名 元数据，避免只读取首个依赖资源导致误判。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = KmsClientAutoConfigurationDiscoveryTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "io.github.surezzzzzz.sdk.kms.client.enabled=true",
                "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal"
        })
class KmsClientAutoConfigurationDiscoveryTest {

    private static final String ENABLE_AUTO_CONFIGURATION = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";
    private static final String AUTO_CONFIGURATION_CLASS = SimpleKmsClientAutoConfiguration.class.getName();
    private static final String FACTORIES_RESOURCE = "META-INF/spring.factories";
    private static final String IMPORTS_RESOURCE = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Autowired
    private KmsClient kmsClient;

    @Autowired
    private TenantSignerPort tenantSignerPort;

    @Autowired
    private TenantPublicKeyPort tenantPublicKeyPort;

    @Autowired
    private KeyEncryptionPort keyEncryptionPort;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldDiscoverExactlyOneAutoConfigurationAcrossBoot2Entrypoints() throws IOException {
        assertEquals(Integer.valueOf(1), Integer.valueOf(factoriesAutoConfigurationCount()),
                "spring.factories 必须只注册一次 Client 自动配置");
        assertEquals(Integer.valueOf(1), Integer.valueOf(importsAutoConfigurationCount()),
                "AutoConfiguration.imports 必须只注册一次 Client 自动配置");
        log.info("自动配置双入口已对齐: {}", AUTO_CONFIGURATION_CLASS);
    }

    @Test
    void shouldCreateClientAndMinimalPortsFromAutoConfigurationDiscovery() {
        assertNotNull(kmsClient, "自动发现必须创建默认 KmsClient");
        assertNotNull(tenantSignerPort, "自动发现必须创建默认签名端口");
        assertNotNull(tenantPublicKeyPort, "自动发现必须创建默认公钥端口");
        assertNotNull(keyEncryptionPort, "自动发现必须创建默认加解密端口");
        assertEquals(Integer.valueOf(1), Integer.valueOf(applicationContext
                .getBeansOfType(SimpleKmsClientAutoConfiguration.class).size()), "双入口不能重复导入自动配置");
        assertEquals(Integer.valueOf(1), Integer.valueOf(applicationContext.getBeansOfType(KmsClient.class).size()),
                "双入口不能重复创建默认 KmsClient");
        log.info("自动配置发现已创建一套默认 Client 与三个最小端口");
    }

    /**
     * 汇总全部 spring.factories 中本模块自动配置类的登记次数。
     */
    private int factoriesAutoConfigurationCount() throws IOException {
        Enumeration<URL> resources = getClass().getClassLoader().getResources(FACTORIES_RESOURCE);
        int count = 0;
        boolean found = false;
        while (resources.hasMoreElements()) {
            found = true;
            Properties properties = new Properties();
            try (InputStream input = resources.nextElement().openStream()) {
                properties.load(input);
            }
            count += countAutoConfiguration(properties.getProperty(ENABLE_AUTO_CONFIGURATION));
        }
        assertTrue(found, "必须包含 spring.factories 自动配置入口");
        return count;
    }

    /**
     * 汇总全部 AutoConfiguration.imports 中本模块自动配置类的登记次数。
     */
    private int importsAutoConfigurationCount() throws IOException {
        Enumeration<URL> resources = getClass().getClassLoader().getResources(IMPORTS_RESOURCE);
        int count = 0;
        boolean found = false;
        while (resources.hasMoreElements()) {
            found = true;
            try (InputStream input = resources.nextElement().openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (AUTO_CONFIGURATION_CLASS.equals(line.trim())) {
                        count++;
                    }
                }
            }
        }
        assertTrue(found, "必须包含 AutoConfiguration.imports 自动配置入口");
        return count;
    }

    private int countAutoConfiguration(String value) {
        if (value == null) {
            return 0;
        }
        int count = 0;
        for (String entry : value.split(",")) {
            if (AUTO_CONFIGURATION_CLASS.equals(entry.trim())) {
                count++;
            }
        }
        return count;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
