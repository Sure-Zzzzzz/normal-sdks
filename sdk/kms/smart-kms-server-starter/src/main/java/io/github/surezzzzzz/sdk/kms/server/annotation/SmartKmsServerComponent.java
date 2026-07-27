package io.github.surezzzzzz.sdk.kms.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * KMS Server 精确组件扫描标记。
 *
 * @author surezzzzzz
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartKmsServerComponent {
}
