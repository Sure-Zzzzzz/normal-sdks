package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * 装配链诊断：列出底座链关键 Bean 的在场情况。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = OpenApiE2eTestApplication.class)
class AssemblyDiagTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void dumpBaseBeans() {
        String[] names = {"akskClientRestTemplate", "tokenManager", "smartCacheManager", "redisRouteTemplate"};
        for (String name : names) {
            System.out.println("[DIAG] " + name + " present=" + context.containsBean(name)
                    + (context.containsBean(name) ? " type=" + context.getType(name).getSimpleName() : ""));
        }
        System.out.println("[DIAG] RestTemplate beans: " + String.join(",",
                context.getBeanNamesForType(org.springframework.web.client.RestTemplate.class)));
    }
}
