package io.github.surezzzzzz.sdk.crm.server.test;

import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * CRM Server 测试应用：最小 Spring 上下文宿主（schema 由受控测试入口单次执行）。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleCrmServerTestApplication {

    /**
     * 启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SimpleCrmServerTestApplication.class, args);
    }

    /**
     * 测试用固定 owner 解析桥（无公共资源链时供装配测试注入）。
     *
     * @return 固定人员主体 iam:test-subject-1
     */
    @Bean
    public TestOwnerResolver testOwnerResolver() {
        return new TestOwnerResolver();
    }

    /**
     * 测试固定 owner 解析器（可切换主体模拟双轨）。
     *
     * @author surezzzzzz
     */
    public static class TestOwnerResolver {

        private volatile CrmOwner current = CrmOwner.human("test-subject-1");

        /**
         * 切换当前主体。
         *
         * @param owner 新主体
         */
        public void switchTo(CrmOwner owner) {
            this.current = owner;
        }

        /**
         * 解析当前主体。
         *
         * @return 当前主体
         */
        public CrmOwner resolve() {
            return current;
        }
    }
}
