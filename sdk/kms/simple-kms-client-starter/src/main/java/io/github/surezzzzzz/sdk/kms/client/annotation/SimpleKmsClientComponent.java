package io.github.surezzzzzz.sdk.kms.client.annotation;

import org.springframework.stereotype.Indexed;

import java.lang.annotation.*;

/**
 * Simple KMS Client 组件标记。
 *
 * <p>仅用作精准扫描的 include filter 与组件索引标记；本身不声明 Spring {@code @Component} 语义。</p>
 *
 * @author surezzzzzz
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Indexed
public @interface SimpleKmsClientComponent {
}
