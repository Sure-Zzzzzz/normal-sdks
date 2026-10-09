package io.github.surezzzzzz.sdk.iam.resttemplate.client.test.demo;

import io.github.surezzzzzz.sdk.iam.client.IamDepartmentClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamDepartment;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * RT-SB2 端到端联调宿主：真实 aksk 底座令牌链（AKP client_credentials）→ 已部署 IAM openapi
 * 六步验证（users 读链 + departments 读链 + 双分页形态实证）。
 *
 * <p>与 KMS e2e 同形：链路与凭据全部来自配置（application.yml 结构 + gitignored
 * application-local.yml 真值，模板见 .example），宿主零自定义 Bean，六步断言失败即抛。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public final class IamClientE2eRunner {
    /**
     * admin 的公开主体（联调库既有账号，作 e2e 定位输入；env 可覆盖）。
     */
    private static final String adminSubjectId =
            System.getenv().getOrDefault("IAM_E2E_ADMIN_SUBJECT_ID", "4590442652332852");

    private IamClientE2eRunner() {
    }

    /**
     * 联调入口。
     *
     * @param args 未使用
     * @throws Exception HTTP 失败时抛出
     */
    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(IamClientE2eRunner.RunnerConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Collections.singletonMap("spring.main.banner-mode", "off"));
        ConfigurableApplicationContext context = app.run();
        IamUserClient users = context.getBean(IamUserClient.class);
        IamDepartmentClient departments = context.getBean(IamDepartmentClient.class);

        // [1] 分页查询用户（Spring Page wire：content/totalElements，页码 0 起）
        IamSpringPage<IamUser> page = users.listUsers(null, null, null, 0, 20);
        log.info("[1] listUsers: totalElements={} number={} size={}",
                page.getTotalElements(), page.getNumber(), page.getSize());
        if (page.getNumber() != 0) {
            throw new IllegalStateException("[1] Spring Page 页码必须 0 起");
        }
        if (page.getContent().isEmpty()) {
            throw new IllegalStateException("[1] 联调库必有用户，空页=契约解析失败");
        }

        // [2] 单用户详情 + 角色编码裸列表（按公开主体定位；server wire 不回 subjectId，
        //     以 admin 的已知主体作定位输入，username 字段回读校验）
        IamUser detail = users.getUser(adminSubjectId);
        log.info("[2] getUser: username={} roles={}", detail.getUsername(), detail.getRoles());
        if (!"admin".equals(detail.getUsername())) {
            throw new IllegalStateException("[2] admin 主体定位必须回读 username=admin");
        }
        List<String> roles = users.getUserRoles(adminSubjectId);
        log.info("[2b] getUserRoles: {}", roles);

        // [3] DATA 范围求交验证：keyword 过滤收敛
        IamSpringPage<IamUser> filtered = users.listUsers(null, null, "admin", 0, 20);
        log.info("[3] keyword 过滤: {} 条（应≥1 且全部匹配）", filtered.getTotalElements());
        if (filtered.getTotalElements() < 1) {
            throw new IllegalStateException("[3] keyword=自身用户名必须命中自己");
        }

        // [4] 部门列表（数组 wire）
        List<IamDepartment> departmentList = departments.listDepartments();
        log.info("[4] listDepartments: {} 个", departmentList.size());
        if (departmentList.isEmpty()) {
            throw new IllegalStateException("[4] 联调库必有 root 部门");
        }

        // [5] 部门详情
        IamDepartment root = departmentList.get(0);
        IamDepartment byId = departments.getDepartment(root.getId());
        log.info("[5] getDepartment({}): code={}", root.getId(), byId.getCode());

        // [6] 非 2xx 透传：查不存在的用户须 404（HttpStatusCodeException 保留状态码）
        try {
            users.getUser("no-such-subject-e2e-1008");
            throw new IllegalStateException("[6] 不存在的用户必须 404，未抛=透传语义破坏");
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound exception) {
            log.info("[6] 404 透传验证: status={} ", exception.getRawStatusCode());
        }

        log.info("[IAM-CLIENT-E2E] ALL 6 STEPS PASSED");
        context.close();
    }

    /**
     * 宿主配置：零自定义 Bean，令牌链与地址全部来自配置文件与自动配置。
     */
    @Configuration
    @EnableAutoConfiguration
    static class RunnerConfiguration {
    }
}
