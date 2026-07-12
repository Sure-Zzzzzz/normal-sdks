package io.github.surezzzzzz.sdk.redis.route.test;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * redis-route matrix profile 解析器。
 *
 * @author surezzzzzz
 */
public class RedisRouteMatrixProfilesResolver implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        String activeProfiles = System.getProperty("spring.profiles.active");
        if (activeProfiles == null) {
            return new String[]{"redis-route-version-matrix"};
        }
        String[] profiles = activeProfiles.split(",");
        for (String profile : profiles) {
            String versionProfile = profile.trim();
            if ("2.7.9".equals(versionProfile) || "2.4.5".equals(versionProfile)
                    || "2.3.12".equals(versionProfile) || "2.2.x".equals(versionProfile)) {
                return new String[]{"redis-route-version-matrix", versionProfile, "redis-route-version-matrix-" + versionProfile};
            }
        }
        return new String[]{"redis-route-version-matrix"};
    }
}
