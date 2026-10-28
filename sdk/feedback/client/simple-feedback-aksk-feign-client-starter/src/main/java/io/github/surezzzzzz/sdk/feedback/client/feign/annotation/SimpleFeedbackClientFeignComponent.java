package io.github.surezzzzzz.sdk.feedback.client.feign.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Feedback Feign 客户端组件标记，供宿主按包扫描时复用。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SimpleFeedbackClientFeignComponent {
}
