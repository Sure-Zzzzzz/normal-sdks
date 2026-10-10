package io.github.surezzzzzz.sdk.auth.data.permission.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入当前请求已评估的数据访问计划。
 *
 * <p>契约注解归 core（1.2.0 起）：控制器签名只依赖 core，Spring MVC 装配件（javax / jakarta 双线）
 * 的参数解析器负责识别注入；{@code spring.mvc.annotation.CurrentDataAccessPlan} 为兼容旧签名保留，
 * 新代码一律使用本类型。</p>
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentDataAccessPlan {
}
