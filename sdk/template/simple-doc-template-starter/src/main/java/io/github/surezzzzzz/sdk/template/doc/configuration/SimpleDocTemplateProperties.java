package io.github.surezzzzzz.sdk.template.doc.configuration;

import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Doc Template Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleDocTemplateConstant.CONFIG_PREFIX)
public class SimpleDocTemplateProperties {

    /**
     * 是否启用（默认 false）
     */
    private boolean enable = false;

    /**
     * 模板根路径
     */
    private String templateLocation = SimpleDocTemplateConstant.DEFAULT_TEMPLATE_LOCATION;

    /**
     * 标签前缀，用于拼接模板占位符（如 [suredt.var:key]），可自定义但需与模板中占位符一致
     */
    private String tagPrefix = SimpleDocTemplateConstant.DEFAULT_TAG_PREFIX;

}