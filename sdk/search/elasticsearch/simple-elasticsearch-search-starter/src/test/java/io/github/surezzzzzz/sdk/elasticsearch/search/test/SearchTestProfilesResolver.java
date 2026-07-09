package io.github.surezzzzzz.sdk.elasticsearch.search.test;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * search-starter 多版本测试 profile 解析器。
 *
 * <p>主配置固定使用 application.yaml，版本差异只加载 application-test-{version}.yaml。</p>
 *
 * @author surezzzzzz
 */
public class SearchTestProfilesResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        String activeProfiles = System.getProperty("spring.profiles.active");
        if (activeProfiles == null) {
            return new String[]{"test-2.7.9"};
        }
        String[] profiles = activeProfiles.split(",");
        for (String profile : profiles) {
            String versionProfile = profile.trim();
            if ("2.7.9".equals(versionProfile) || "2.4.5".equals(versionProfile)
                    || "2.3.12".equals(versionProfile) || "2.2.x".equals(versionProfile)) {
                return new String[]{"test-" + versionProfile};
            }
        }
        return new String[]{"test-2.7.9"};
    }
}
