package io.github.surezzzzzz.sdk.template.doc.document;

/**
 * Document Interface - 渲染产物抽象
 *
 * @author surezzzzzz
 */
public interface Document {

    /**
     * 获取文档格式标识，用于 OutputHandler 选择写出方式
     *
     * @return 格式标识，如 "docx", "md", "html"
     */
    String getFormat();

    /**
     * 判断是否为空文档（列表数据为空时）
     *
     * @return true 空文档，false 正常
     */
    boolean isEmpty();
}