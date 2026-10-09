package io.github.surezzzzzz.sdk.auth.iam.server.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 开放 API e2e 专用启动类（窄扫描）：排除 test 子包——该包内测试类的
 * 夹具（FixtureAdapter，sourceId=aksk）若被拾取会与真 aksk 资源层 adapter
 * 双注册（认证来源重复）。e2e 宿主只装生产组件+外插资源层。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "io.github.surezzzzzz.sdk.auth.iam.server",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "io\\.github\\.surezzzzzz\\.sdk\\.auth\\.iam\\.server\\.test\\..*"
        )
)
public class IamOpenApiE2eApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamOpenApiE2eApplication.class, args);
    }
}
