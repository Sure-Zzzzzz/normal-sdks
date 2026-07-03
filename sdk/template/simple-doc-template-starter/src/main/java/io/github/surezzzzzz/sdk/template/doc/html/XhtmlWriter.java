package io.github.surezzzzzz.sdk.template.doc.html;

import java.util.Map;

/**
 * XHTML Writer
 *
 * @author surezzzzzz
 */
public class XhtmlWriter {

    private final StringBuilder out = new StringBuilder();

    /**
     * 写可信原文。
     *
     * @param raw 原文
     * @return writer
     */
    public XhtmlWriter rawTrusted(String raw) {
        out.append(raw);
        return this;
    }

    /**
     * 写文本。
     *
     * @param text 文本
     * @return writer
     */
    public XhtmlWriter text(String text) {
        out.append(escapeText(text));
        return this;
    }

    /**
     * 写开始标签。
     *
     * @param name 标签名
     * @param attributes 属性
     * @return writer
     */
    public XhtmlWriter tag(String name, Map<String, String> attributes) {
        out.append('<').append(name);
        appendAttributes(attributes);
        out.append('>');
        return this;
    }

    /**
     * 写结束标签。
     *
     * @param name 标签名
     * @return writer
     */
    public XhtmlWriter endTag(String name) {
        out.append("</").append(name).append('>');
        return this;
    }

    /**
     * 写 void 标签。
     *
     * @param name 标签名
     * @param attributes 属性
     * @return writer
     */
    public XhtmlWriter voidTag(String name, Map<String, String> attributes) {
        out.append('<').append(name);
        appendAttributes(attributes);
        out.append(" />");
        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }

    private void appendAttributes(Map<String, String> attributes) {
        if (attributes == null) {
            return;
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            out.append(' ').append(entry.getKey()).append("=\"").append(escapeAttribute(entry.getValue())).append('"');
        }
    }

    private String escapeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeAttribute(String text) {
        return escapeText(text);
    }
}
