package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.provider;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.provider.SimpleAkskSecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.support.SimpleAkskSecurityContextHelper;

import java.util.Collections;
import java.util.Map;

/**
 * SimpleAkskSecurityContextHelper 的 SimpleAkskSecurityContextProvider 适配器
 *
 * <p>将 SimpleAkskSecurityContextHelper 的静态方法适配为 SimpleAkskSecurityContextProvider 接口。
 *
 * <p>用于 SimpleAkskSecurityAspect 的依赖注入。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class AkskJwtContextProvider implements SimpleAkskSecurityContextProvider {

    @Override
    public Map<String, String> getAll() {
        Map<String, String> context = SimpleAkskSecurityContextHelper.getAll();
        return context != null ? context : Collections.emptyMap();
    }

    @Override
    public String get(String key) {
        return SimpleAkskSecurityContextHelper.get(key);
    }
}
