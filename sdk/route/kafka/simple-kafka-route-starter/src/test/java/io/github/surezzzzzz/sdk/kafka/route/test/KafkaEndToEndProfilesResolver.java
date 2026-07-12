package io.github.surezzzzzz.sdk.kafka.route.test;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Kafka 端到端测试 profile 解析器
 *
 * @author surezzzzzz
 */
public class KafkaEndToEndProfilesResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        String activeProfiles = System.getProperty("spring.profiles.active");
        if (activeProfiles == null) {
            return new String[]{"kafka-e2e"};
        }
        String[] profiles = activeProfiles.split(",");
        for (String profile : profiles) {
            String versionProfile = profile.trim();
            if ("2.7.9".equals(versionProfile) || "2.4.5".equals(versionProfile)
                    || "2.3.12".equals(versionProfile) || "2.2.x".equals(versionProfile)) {
                return new String[]{"kafka-e2e", versionProfile, "kafka-e2e-" + versionProfile};
            }
        }
        return new String[]{"kafka-e2e"};
    }
}
