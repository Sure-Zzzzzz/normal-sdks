package io.github.surezzzzzz.sdk.feedback.server.adaptor.configuration;

import io.github.surezzzzzz.sdk.feedback.server.adaptor.SimpleFeedbackIamAdaptorPackage;
import io.github.surezzzzzz.sdk.feedback.server.adaptor.annotation.SimpleFeedbackIamAdaptorComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Simple Feedback IAM Adaptor Auto Configuration（adaptor 纪律 §3.7：引用即装配）。
 *
 * <p>仅扫描本模块自定义注解标记的组件（两个 SPI 实现件）；无开关、无让位——
 * 引本件即聚形态，不引即散形态（starter 降级实现兜底）；宿主自定义 SPI 实现经依赖
 * 替换表达（排除本件或自备实现链），不经 MissingBean 让位。IAM 客户端 Bean 缺席时
 * 扫描件构造注入在启动期响亮失败（aksk openapi 客户端参照，缺件不静默）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@ComponentScan(
        basePackageClasses = SimpleFeedbackIamAdaptorPackage.class,
        includeFilters = @ComponentScan.Filter(SimpleFeedbackIamAdaptorComponent.class),
        useDefaultFilters = false
)
public class SimpleFeedbackIamAdaptorAutoConfiguration {

    /**
     * 自动配置加载成功的最小埋点（与家族件形态一致）。
     */
    @PostConstruct
    public void init() {
        log.info("===== SimpleFeedback IAM Adaptor 自动配置加载成功 =====");
    }
}
