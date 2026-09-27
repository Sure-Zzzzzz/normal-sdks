package io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * B2M 短信投递组件注解。
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface B2mSmsDeliveryComponent {
}
