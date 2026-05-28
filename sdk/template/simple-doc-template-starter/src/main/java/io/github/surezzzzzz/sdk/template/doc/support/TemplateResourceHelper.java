package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateNotFoundException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Template Resource Helper - 模板资源加载工具
 *
 * <p>封装 Spring ResourceLoader，支持 classpath:、file: 等协议。
 * 提供 Resource 和 byte[] 两种加载方式：
 * - loadResource()：返回 Resource（Renderer 使用，支持 getInputStream）
 * - loadResourceBytes()：返回 byte[]（Processor 使用，需要完整字节进行 DOM 操作）
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
public class TemplateResourceHelper {

    private final ResourceLoader resourceLoader;

    public TemplateResourceHelper(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载模板资源
     *
     * @param location 模板路径（支持 classpath:、file: 等协议）
     * @return Resource 对象
     */
    public Resource loadResource(String location) {
        return resourceLoader.getResource(location);
    }

    /**
     * 加载模板原始字节
     *
     * <p>用于 SDK 预处理阶段（ConditionProcessor），
     * 需要完整的字节数据进行 ZIP 解析和 DOM 操作。
     *
     * @param location 模板路径
     * @return 模板文件的原始字节数组
     * @throws TemplateNotFoundException 模板文件不存在或读取失败
     */
    public byte[] loadResourceBytes(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw TemplateNotFoundException.notFound(location);
        }
        try (InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToByteArray(is);
        } catch (IOException e) {
            throw TemplateNotFoundException.notFound(location);
        }
    }
}