package io.github.surezzzzzz.sdk.license.verifier.configuration;

import io.github.surezzzzzz.sdk.license.core.spi.ClockProvider;
import io.github.surezzzzzz.sdk.license.core.spi.StateStore;
import io.github.surezzzzzz.sdk.license.core.spi.TrustStore;
import io.github.surezzzzzz.sdk.license.core.spi.TrustedSubjectProvider;
import io.github.surezzzzzz.sdk.license.core.verify.DefaultLicenseVerifier;
import io.github.surezzzzzz.sdk.license.core.verify.RevocationStore;
import io.github.surezzzzzz.sdk.license.core.verify.AtomicRevocationStore;
import io.github.surezzzzzz.sdk.license.verifier.clock.SystemClockProvider;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.FileStateStore;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.FileRevocationStore;
import io.github.surezzzzzz.sdk.license.verifier.filesystem.InMemoryTrustStore;
import io.github.surezzzzzz.sdk.license.verifier.service.LicenseTrustMaterialInstaller;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * License Verifier Starter 装配：Core 流程 + 默认端口实现。
 *
 * <p>全部端口可被宿主 Bean 覆盖（@ConditionalOnMissingBean）；技术 Properties 仅状态目录，
 * 不存在 deviceEnabled/graceDays 等业务开关。装配开关只决定是否提供能力，
 * 不让缺失能力被认为已满足。</p>
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnProperty(name = "io.github.surezzzzzz.sdk.license.verifier.enabled", havingValue = "true")
public class LicenseVerifierAutoConfiguration {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(LicenseVerifierAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ClockProvider.class)
    public ClockProvider licenseClockProvider() {
        return new SystemClockProvider();
    }

    @Bean
    @ConditionalOnMissingBean(TrustStore.class)
    public TrustStore licenseTrustStore() {
        return new InMemoryTrustStore();
    }

    @Bean
    @ConditionalOnMissingBean(RevocationStore.class)
    public AtomicRevocationStore licenseRevocationStore(
            @Value("${io.github.surezzzzzz.sdk.license.verifier.state-dir}") String stateDir) {
        // 默认使用与 StateStore 同一目录；文件损坏时启动失败，不能以空仓伪装成“无吊销”。
        return new FileRevocationStore(Paths.get(stateDir));
    }

    @Bean
    @ConditionalOnMissingBean(LicenseTrustMaterialInstaller.class)
    @ConditionalOnBean(AtomicRevocationStore.class)
    public LicenseTrustMaterialInstaller licenseTrustMaterialInstaller(
            TrustStore trustStore, AtomicRevocationStore revocationStore) {
        return new LicenseTrustMaterialInstaller(trustStore, revocationStore);
    }

    @Bean
    @ConditionalOnMissingBean(StateStore.class)
    public StateStore licenseStateStore(
            @Value("${io.github.surezzzzzz.sdk.license.verifier.state-dir}") String stateDir) {
        return new FileStateStore(Paths.get(stateDir), "state.properties");
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultLicenseVerifier licenseVerifier(TrustStore trustStore, RevocationStore revocationStore,
                                                   ClockProvider clockProvider, StateStore stateStore,
                                                   ObjectProvider<TrustedSubjectProvider> trustedSubjectProviders,
                                                   @Value("${io.github.surezzzzzz.sdk.license.verifier.supported-offline-proof-profiles:}")
                                                   String supportedProfiles) {
        return new DefaultLicenseVerifier(trustStore, revocationStore, clockProvider, stateStore,
                parseProfiles(supportedProfiles), trustedSubjectProviders.getIfAvailable());
    }

    /** 仅登记宿主已安装的可信身份档案；凭据声明不会自动扩展此集合。 */
    private static Set<String> parseProfiles(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> profiles = new LinkedHashSet<String>();
        for (String item : value.split(",")) {
            String profile = item.trim();
            if (profile.isEmpty()) {
                throw new IllegalArgumentException("supported-offline-proof-profiles 不能包含空档案");
            }
            profiles.add(profile);
        }
        return profiles;
    }
}
