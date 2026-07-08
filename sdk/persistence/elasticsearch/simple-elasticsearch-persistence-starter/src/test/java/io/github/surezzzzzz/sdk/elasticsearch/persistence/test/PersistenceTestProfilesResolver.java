package io.github.surezzzzzz.sdk.elasticsearch.persistence.test;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * persistence-test profile 解析器。
 *
 * <p>读取 {@code -Dspring.profiles.active}，若为 2.7.9/2.4.5/2.3.12/2.2.x 之一，
 * 激活三层 profile：{@code persistence-test}（写测试 base）、{@code <version>}（版本）、
 * {@code persistence-test-<version>}（版本对应的 ES 端口覆盖）。
 *
 * @author surezzzzzz
 */
public class PersistenceTestProfilesResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        String activeProfiles = System.getProperty("spring.profiles.active");
        if (activeProfiles == null) {
            return new String[]{"persistence-test"};
        }
        String[] profiles = activeProfiles.split(",");
        for (String profile : profiles) {
            String versionProfile = profile.trim();
            if ("2.7.9".equals(versionProfile) || "2.4.5".equals(versionProfile)
                    || "2.3.12".equals(versionProfile) || "2.2.x".equals(versionProfile)) {
                return new String[]{"persistence-test", versionProfile, "persistence-test-" + versionProfile};
            }
        }
        return new String[]{"persistence-test"};
    }
}
