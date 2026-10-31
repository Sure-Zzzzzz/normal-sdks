package io.github.surezzzzzz.sdk.license.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * License Server 组件标记：仅被本模块自动配置的包含过滤器扫描装配。
 *
 * <p>adaptor 装配规范（对齐 IAM/KMS adaptor 先例）：引用即代表使用——引依赖即装配，
 * 无 enable 开关、无 ConditionalOnMissingBean 让位；实现替换即替换依赖。
 * 标记类经构造注入装配，缺必需依赖时启动即报缺 Bean（响亮失败，不静默）。</p>
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleLicenseServerComponent {
}
