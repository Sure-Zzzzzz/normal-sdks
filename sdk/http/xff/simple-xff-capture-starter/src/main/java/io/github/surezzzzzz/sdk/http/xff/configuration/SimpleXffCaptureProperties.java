package io.github.surezzzzzz.sdk.http.xff.configuration;

import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureWebConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple XFF Capture 配置属性。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleXffCaptureConstant.CONFIG_PREFIX)
public class SimpleXffCaptureProperties {

    /**
     * 是否启用 XFF 自动采集。
     */
    private boolean enable = SimpleXffCaptureConstant.DEFAULT_ENABLE;

    /**
     * XFF Filter 注册顺序，未配置时使用稳定默认值。
     */
    private int order = SimpleXffCaptureWebConstant.FILTER_ORDER;
}
