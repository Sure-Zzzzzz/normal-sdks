package io.github.surezzzzzz.sdk.ticket.server.adaptor.configuration;

import io.github.surezzzzzz.sdk.ticket.server.adaptor.SimpleTicketIamAdaptorPackage;
import io.github.surezzzzzz.sdk.ticket.server.adaptor.annotation.SimpleTicketIamAdaptorComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Ticket IAM Adaptor Auto Configuration（adaptor 纪律 §3.7：引用即装配）。
 *
 * <p>仅扫描本模块自定义注解标记的组件（站内信/姓名解析两个 SPI 实现件）；无开关、无让位——
 * 引本件即聚形态，不引即散形态（server-starter 降级实现兜底）。IAM 客户端 Bean 缺席时
 * 扫描件构造注入在启动期响亮失败（feedback adaptor 同构，缺件不静默）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ComponentScan(
        basePackageClasses = SimpleTicketIamAdaptorPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleTicketIamAdaptorComponent.class),
        useDefaultFilters = false
)
public class SimpleTicketIamAdaptorAutoConfiguration implements InitializingBean {

    /**
     * 自动配置加载成功的最小埋点（与家族件形态一致）。
     */
    @Override
    public void afterPropertiesSet() {
        log.info("===== SimpleTicket IAM Adaptor 自动配置加载成功 =====");
    }
}
