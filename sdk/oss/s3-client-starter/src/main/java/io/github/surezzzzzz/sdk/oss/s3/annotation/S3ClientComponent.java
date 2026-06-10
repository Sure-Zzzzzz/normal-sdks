package io.github.surezzzzzz.sdk.oss.s3.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义组件注解，用于标记 S3Client 模块的组件类，配合 ComponentScan 使用
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface S3ClientComponent {
}