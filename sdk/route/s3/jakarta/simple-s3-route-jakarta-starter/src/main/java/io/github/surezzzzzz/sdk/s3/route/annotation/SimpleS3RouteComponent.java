package io.github.surezzzzzz.sdk.s3.route.annotation;

import java.lang.annotation.*;

/**
 * S3 Route 内部组件扫描标记。
 *
 * @author surezzzzzz
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SimpleS3RouteComponent {
}
