package io.github.surezzzzzz.sdk.auth.data.permission.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限操作元数据。
 *
 * @author surezzzzzz
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DataPermissionOperation {

    /**
     * 获取资源标识。
     *
     * @return 资源标识
     */
    String resource();

    /**
     * 获取动作标识。
     *
     * @return 动作标识
     */
    String action();
}
