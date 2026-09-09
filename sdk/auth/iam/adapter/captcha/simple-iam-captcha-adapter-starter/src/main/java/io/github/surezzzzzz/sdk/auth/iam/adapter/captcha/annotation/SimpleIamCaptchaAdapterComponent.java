package io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple IAM Captcha Adapter Component Annotation
 *
 * <p>标记验证码适配器组件，由
 * {@code SimpleIamCaptchaAdapterAutoConfiguration} 的精准扫描装配。
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleIamCaptchaAdapterComponent {
}
