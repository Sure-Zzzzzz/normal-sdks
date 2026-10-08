package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Web 控制器注册回归测试（1.3.4 修复防复发）。
 *
 * <p>1.3.0 起 IamWebAccountPhoneController 漏标 @SimpleIamServerComponent，精准扫描下从未注册，
 * /iam/web/account/** 全族 404（源码起服形态掩盖，容器化后暴露）。本测试对全部 /iam/web/**
 * 人员端点做注册断言，防止同类漏注解再次静默逃逸。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamWebControllerRegistrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldRegisterAllWebAccountPhoneEndpointsUnderPreciseScan() {
        RequestMappingHandlerMapping mapping = context.getBean(RequestMappingHandlerMapping.class);
        long registered = mapping.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPatternValues().stream()
                        .anyMatch(p -> p.startsWith("/iam/web/account")))
                .count();
        log.info("已注册 /iam/web/account 端点数: {}", registered);
        assertTrue(registered >= 4,
                "account 族四端点（GET/PUT/DELETE /phone + POST /phone-challenges）必须全部注册");
    }

    @Test
    void shouldRegisterEveryWebControllerFamily() {
        RequestMappingHandlerMapping mapping = context.getBean(RequestMappingHandlerMapping.class);
        String[] families = {"/iam/web/auth", "/iam/web/account", "/iam/web/branding",
                "/iam/web/portal", "/iam/web/theme-preference", "/iam/web/messages"};
        for (String family : families) {
            boolean present = mapping.getHandlerMethods().keySet().stream()
                    .flatMap(info -> info.getPatternValues().stream())
                    .anyMatch(p -> p.startsWith(family));
            assertTrue(present, "web 族 " + family + " 至少一个端点已注册");
        }
        log.info("六个 web 人员端点族注册齐全");
    }
}
