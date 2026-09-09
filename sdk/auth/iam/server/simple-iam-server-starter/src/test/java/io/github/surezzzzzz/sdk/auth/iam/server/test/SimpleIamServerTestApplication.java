package io.github.surezzzzzz.sdk.auth.iam.server.test;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.MenuItemRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.Collections;

/**
 * Simple IAM Server Test Application
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "io.github.surezzzzzz.sdk.auth.iam.server")
public class SimpleIamServerTestApplication {

    private static final String LOCAL_APPLICATION_CODE = "iam";

    public static void main(String[] args) {
        SpringApplication.run(SimpleIamServerTestApplication.class, args);
    }

    @Bean
    @Profile("local")
    @ConditionalOnProperty(name = "simple-iam.local-fixture.enabled", havingValue = "true")
    ApplicationRunner localIamAdminApplicationFixture(IamTrustedApplicationRepository trustedApplicationRepository,
                                                      TrustedApplicationService trustedApplicationService) {
        return args -> {
            if (trustedApplicationRepository.existsByApplicationCode(LOCAL_APPLICATION_CODE)) {
                log.info("本地统一身份与访问管理应用已存在，跳过创建：code={}", LOCAL_APPLICATION_CODE);
                return;
            }
            trustedApplicationService.createApplication(createLocalApplicationRequest());
            log.info("本地统一身份与访问管理应用创建完成：code={}", LOCAL_APPLICATION_CODE);
        };
    }

    private CreateTrustedApplicationRequest createLocalApplicationRequest() {
        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(LOCAL_APPLICATION_CODE);
        request.setApplicationName("统一身份与访问管理");
        request.setDescription("本地统一身份与访问管理联调应用");
        request.setIcon("access-control");
        request.setPortal(createLocalPortal());
        request.setInitialClient(createLocalClient());
        return request;
    }

    private PortalIntegrationRequest createLocalPortal() {
        PortalIntegrationRequest portal = new PortalIntegrationRequest();
        portal.setEnabled(true);
        portal.setEntry("http://localhost:5175");
        portal.setMenus(Arrays.asList(
                menu("dashboard", "工作台", "/", 0),
                menu("organizations", "组织与成员", "/organizations", 1),
                menu("user-groups", "协作组", "/user-groups", 2),
                menu("roles", "角色", "/roles", 3),
                menu("permissions", "权限", "/permissions", 4),
                menu("trusted-applications", "可信应用", "/trusted-applications", 5),
                menu("messages", "站内信", "/messages", 6)
        ));
        return portal;
    }

    private MenuItemRequest menu(String code, String name, String route, int sortOrder) {
        MenuItemRequest menu = new MenuItemRequest();
        menu.setCode(code);
        menu.setName(name);
        menu.setRoute(route);
        menu.setSortOrder(sortOrder);
        return menu;
    }

    private CreateTrustedApplicationClientRequest createLocalClient() {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId("iam-local-web");
        client.setClientName("本地 IAM 管理 Web");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("http://localhost:5174/login"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList("authorization_code"));
        client.setAuthenticationMethods(Collections.singletonList("none"));
        return client;
    }
}
