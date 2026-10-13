package io.github.surezzzzzz.sdk.feedback.server.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Feedback Server 测试应用。
 *
 * <p>联调形态说明：不排除 SecurityAutoConfiguration、不提供宿主固定桥——资源层
 * （resource-server+iam-resource introspect 三件套）挂真实认证链，验证客户端凭据经
 * systemProperty 注入（本地 init 脚本），PKCE 人员令牌经 IAM verify 换取授权投影，
 * FeedbackResourceServerPrincipalResolver 桥自动接管真实身份。starter 单测的 service
 * 层调用不经 MVC，不受认证链影响。</p>
 *
 * <p>非直观约束：组件扫描排除 cases 与 walkthrough 包——测试类内嵌 @TestConfiguration
 * （如 hint/协作链的 stub provider）是静态内部类，会被类路径扫描独立扫为组件；不排除时
 * 桩 bean 会带进其他测试类乃至本地联调环境（本类复用 test classpath），同名桩即
 * BeanDefinitionOverrideException。显式 classes 引用的测试不受影响。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication
@ComponentScan(basePackages = "io.github.surezzzzzz.sdk.feedback.server.test",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = ".*\\.test\\.(cases|walkthrough)\\..*"))
public class SimpleFeedbackServerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleFeedbackServerTestApplication.class, args);
    }
}
