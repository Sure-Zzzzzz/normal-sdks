package io.github.surezzzzzz.sdk.license.verifier.test.cases;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.surezzzzzz.sdk.license.core.verify.AtomicRevocationStore;
import io.github.surezzzzzz.sdk.license.core.verify.DefaultLicenseVerifier;
import io.github.surezzzzzz.sdk.license.verifier.configuration.LicenseVerifierAutoConfiguration;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.FileRevocationStore;
import io.github.surezzzzzz.sdk.license.verifier.service.LicenseTrustMaterialInstaller;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 验证默认自动配置的条件顺序：文件吊销仓和信任资料安装器必须同时可用。
 *
 * @author surezzzzzz
 */
class LicenseVerifierAutoConfigurationTest {

    @Test
    void defaultBeansAreRegisteredTogether() throws Exception {
        Path stateDirectory = Files.createTempDirectory("license-verifier-context-");
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                    "io.github.surezzzzzz.sdk.license.verifier.enabled=true",
                    "io.github.surezzzzzz.sdk.license.verifier.state-dir="
                            + stateDirectory.toAbsolutePath())
                    .applyTo(context);
            context.register(LicenseVerifierAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBean(AtomicRevocationStore.class))
                    .isInstanceOf(FileRevocationStore.class);
            assertThat(context.getBean(LicenseTrustMaterialInstaller.class)).isNotNull();
            assertThat(context.getBean(DefaultLicenseVerifier.class)).isNotNull();
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(stateDirectory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.io.IOException failure) {
                        throw new IllegalStateException("测试目录清理失败", failure);
                    }
                });
            }
        }
    }
}
