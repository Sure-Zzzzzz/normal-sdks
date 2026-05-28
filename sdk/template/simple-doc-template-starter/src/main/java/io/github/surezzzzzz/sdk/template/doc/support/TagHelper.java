package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.configuration.SimpleDocTemplateProperties;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

/**
 * Tag Helper - 标签工具类
 *
 * <p>封装标签前缀和标签的动态拼接，前缀由运行时配置注入。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleDocTemplateComponent
public class TagHelper {

    /** 运行时前缀，由 PostConstruct 注入 */
    private String prefix = SimpleDocTemplateConstant.DEFAULT_TAG_PREFIX;

    @Autowired
    private SimpleDocTemplateProperties properties;

    @PostConstruct
    void init() {
        prefix = properties.getTagPrefix();
        log.info("Tag prefix initialized: [{}]", prefix);
    }

    public String getPrefix() {
        return prefix;
    }

    // ==================== 前缀 ====================

    /**
     * 标签前缀：文本变量
     */
    public String varPrefix() {
        return String.format(SimpleDocTemplateConstant.TAG_VAR_PREFIX_TEMPLATE, prefix);
    }

    /**
     * 标签前缀：图片
     */
    public String imgPrefix() {
        return String.format(SimpleDocTemplateConstant.TAG_IMG_PREFIX_TEMPLATE, prefix);
    }

    /**
     * 标签前缀：图表
     */
    public String chartPrefix() {
        return String.format(SimpleDocTemplateConstant.TAG_CHART_PREFIX_TEMPLATE, prefix);
    }

    /**
     * 标签前缀：条件块开始
     */
    public String startPrefix() {
        return String.format(SimpleDocTemplateConstant.TAG_START_PREFIX_TEMPLATE, prefix);
    }

    /**
     * 标签前缀：条件块结束
     */
    public String endPrefix() {
        return String.format(SimpleDocTemplateConstant.TAG_END_PREFIX_TEMPLATE, prefix);
    }

    /**
     * 标签前缀：循环开始
     */
    public String forPrefix() {
        return String.format(SimpleDocTemplateConstant.TAG_FOR_PREFIX_TEMPLATE, prefix);
    }

    /**
     * 标签前缀：循环结束
     */
    public String endforPrefix() {
        return String.format(SimpleDocTemplateConstant.TAG_ENDFOR_PREFIX_TEMPLATE, prefix);
    }

    // ==================== 完整标签 ====================

    /**
     * 完整标签：条件块开始
     */
    public String startTag(String key) {
        return String.format(SimpleDocTemplateConstant.TAG_START_TAG_TEMPLATE, prefix, key);
    }

    /**
     * 完整标签：条件块结束
     */
    public String endTag(String key) {
        return String.format(SimpleDocTemplateConstant.TAG_END_TAG_TEMPLATE, prefix, key);
    }

    /**
     * 完整标签：循环开始
     */
    public String forTag(String key) {
        return String.format(SimpleDocTemplateConstant.TAG_FOR_TAG_TEMPLATE, prefix, key);
    }

    /**
     * 完整标签：循环结束
     */
    public String endforTag(String key) {
        return String.format(SimpleDocTemplateConstant.TAG_ENDFOR_TAG_TEMPLATE, prefix, key);
    }

    // ==================== 工具方法 ====================

    /**
     * 判断文本是否以指定前缀开头
     */
    public boolean matches(String text, String prefix) {
        return text != null && text.startsWith(prefix);
    }

    /**
     * 从标签中提取 key
     */
    public String extractKey(String tag, String prefix) {
        if (tag == null || !tag.startsWith(prefix)) {
            return "";
        }
        int suffix = tag.indexOf(']');
        if (suffix < 0) {
            return "";
        }
        return tag.substring(prefix.length(), suffix);
    }
}
