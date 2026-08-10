package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.constant;

/**
 * Spring MVC数据权限Starter常量。
 *
 * @author surezzzzzz
 */
public final class SimpleDataPermissionSpringMvcConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc";
    /**
     * 启用配置名称。
     */
    public static final String CONFIG_NAME_ENABLED = "enabled";
    /**
     * 默认启用状态。
     */
    public static final boolean DEFAULT_ENABLED = true;
    /**
     * 常量类实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "数据权限Spring MVC Starter常量类不能实例化";

    private SimpleDataPermissionSpringMvcConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
