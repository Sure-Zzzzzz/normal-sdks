package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.configuration;

import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.constant.SimpleDataPermissionSpringMvcConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring MVC数据权限配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleDataPermissionSpringMvcConstant.CONFIG_PREFIX)
public class DataPermissionSpringMvcProperties {

    /**
     * 是否启用数据权限MVC适配。
     */
    private boolean enabled = SimpleDataPermissionSpringMvcConstant.DEFAULT_ENABLED;
}
