package io.github.surezzzzzz.sdk.template.doc.html;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.model.MarkdownPdfContext;
import io.github.surezzzzzz.sdk.template.doc.model.MdImageReference;
import io.github.surezzzzzz.sdk.template.doc.support.ImageResourceResolver;
import lombok.RequiredArgsConstructor;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.util.*;

/**
 * Markdown XHTML Renderer
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class MarkdownXhtmlRenderer {

    private final SafeUrlSanitizer safeUrlSanitizer;
    private final ImageResourceResolver imageResourceResolver;

    /**
     * 转换 Markdown 为 XHTML。
     *
     * @param markdownText Markdown 文本
     * @param cssFontFamily CSS 字体族
     * @param context PDF 上下文
     * @return XHTML
     */
    public String toXhtml(String markdownText, String cssFontFamily, MarkdownPdfContext context) {
        try {
            List<Extension> extensions = Collections.singletonList(TablesExtension.create());
            Parser parser = Parser.builder().extensions(extensions).build();
            Node document = parser.parse(markdownText == null ? SimpleDocTemplateConstant.EMPTY : markdownText);
            XhtmlWriter writer = new XhtmlWriter();
            writeDocumentStart(writer, cssFontFamily);
            renderChildren(document, writer, context);
            writeDocumentEnd(writer);
            return writer.toString();
        } catch (TemplateRenderException e) {
            throw e;
        } catch (Exception e) {
            throw TemplateRenderException.markdownToHtmlFailed(e.getMessage(), e);
        }
    }

    private void writeDocumentStart(XhtmlWriter writer, String cssFontFamily) {
        String fontFamily = cssFontFamily == null || cssFontFamily.isEmpty() ? "sans-serif" : cssFontFamily;
        String codeFontFamily = fontFamily + ", monospace";
        writer.rawTrusted(SimpleDocTemplateConstant.HTML_DOCTYPE)
                .rawTrusted("<html><head><meta charset=\"UTF-8\" />")
                .rawTrusted("<style>")
                .rawTrusted("body{font-family:").rawTrusted(fontFamily).rawTrusted(";line-height:1.6;font-size:12pt;}")
                .rawTrusted("@page{size:A4;margin:20mm;}")
                .rawTrusted("table{border-collapse:collapse;width:100%;}")
                .rawTrusted("th,td{border:1px solid #999;padding:6px 8px;}")
                .rawTrusted("img{max-width:100%;}")
                .rawTrusted("pre{background:#f6f8fa;padding:8px;white-space:pre-wrap;font-family:").rawTrusted(codeFontFamily).rawTrusted(";}")
                .rawTrusted("code{font-family:").rawTrusted(codeFontFamily).rawTrusted(";}")
                .rawTrusted("</style></head><body>");
    }

    private void writeDocumentEnd(XhtmlWriter writer) {
        writer.rawTrusted("</body></html>");
    }

    private void renderChildren(Node parent, XhtmlWriter writer, MarkdownPdfContext context) {
        Node child = parent.getFirstChild();
        while (child != null) {
            renderNode(child, writer, context);
            child = child.getNext();
        }
    }

    private void renderNode(Node node, XhtmlWriter writer, MarkdownPdfContext context) {
        if (node instanceof Heading) {
            int level = Math.min(((Heading) node).getLevel(), 3);
            writer.tag("h" + level, null);
            renderChildren(node, writer, context);
            writer.endTag("h" + level);
        } else if (node instanceof Paragraph) {
            writer.tag("p", null);
            renderChildren(node, writer, context);
            writer.endTag("p");
        } else if (node instanceof Text) {
            writer.text(((Text) node).getLiteral());
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            writer.voidTag("br", null);
        } else if (node instanceof Emphasis) {
            writer.tag("em", null);
            renderChildren(node, writer, context);
            writer.endTag("em");
        } else if (node instanceof StrongEmphasis) {
            writer.tag("strong", null);
            renderChildren(node, writer, context);
            writer.endTag("strong");
        } else if (node instanceof Link) {
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("href", safeUrlSanitizer.sanitizeLinkHref(((Link) node).getDestination()));
            writer.tag("a", attrs);
            renderChildren(node, writer, context);
            writer.endTag("a");
        } else if (node instanceof Image) {
            renderImage((Image) node, writer, context);
        } else if (node instanceof BulletList) {
            writer.tag("ul", null);
            renderChildren(node, writer, context);
            writer.endTag("ul");
        } else if (node instanceof OrderedList) {
            writer.tag("ol", null);
            renderChildren(node, writer, context);
            writer.endTag("ol");
        } else if (node instanceof ListItem) {
            writer.tag("li", null);
            renderChildren(node, writer, context);
            writer.endTag("li");
        } else if (node instanceof FencedCodeBlock) {
            writer.tag("pre", null).tag("code", null).text(((FencedCodeBlock) node).getLiteral()).endTag("code").endTag("pre");
        } else if (node instanceof IndentedCodeBlock) {
            writer.tag("pre", null).tag("code", null).text(((IndentedCodeBlock) node).getLiteral()).endTag("code").endTag("pre");
        } else if (node instanceof Code) {
            writer.tag("code", null).text(((Code) node).getLiteral()).endTag("code");
        } else if (node instanceof HtmlInline) {
            writer.text(((HtmlInline) node).getLiteral());
        } else if (node instanceof HtmlBlock) {
            writer.text(((HtmlBlock) node).getLiteral());
        } else if (node.getClass().getName().contains("Table")) {
            renderTableNode(node, writer, context);
        } else if (node instanceof ThematicBreak) {
            writer.voidTag("hr", null);
        } else if (node instanceof BlockQuote) {
            throw TemplateRenderException.markdownUnsupportedFeature("blockquote");
        } else {
            renderChildren(node, writer, context);
        }
    }

    private void renderTableNode(Node node, XhtmlWriter writer, MarkdownPdfContext context) {
        String simpleName = node.getClass().getSimpleName();
        if ("TableBlock".equals(simpleName)) {
            writer.tag("table", null);
            renderChildren(node, writer, context);
            writer.endTag("table");
        } else if ("TableHead".equals(simpleName)) {
            writer.tag("thead", null);
            renderChildren(node, writer, context);
            writer.endTag("thead");
        } else if ("TableBody".equals(simpleName)) {
            writer.tag("tbody", null);
            renderChildren(node, writer, context);
            writer.endTag("tbody");
        } else if ("TableRow".equals(simpleName)) {
            writer.tag("tr", null);
            renderChildren(node, writer, context);
            writer.endTag("tr");
        } else if ("TableCell".equals(simpleName)) {
            String tag = isHeaderCell(node) ? "th" : "td";
            writer.tag(tag, null);
            renderChildren(node, writer, context);
            writer.endTag(tag);
        } else {
            renderChildren(node, writer, context);
        }
    }

    private boolean isHeaderCell(Node node) {
        try {
            java.lang.reflect.Method method = node.getClass().getMethod("isHeader");
            Object result = method.invoke(node);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private void renderImage(Image imageNode, XhtmlWriter writer, MarkdownPdfContext context) {
        String src = imageNode.getDestination();
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("alt", collectText(imageNode));
        MdImageReference reference = findReference(context, src);
        if (reference != null) {
            ImageResourceResolver.ResolvedImage resolved = imageResourceResolver.resolveToDataUri(reference.getImage(), context.getBaseUri());
            attrs.put("src", safeUrlSanitizer.sanitizeImageSrc(resolved.getDataUri()));
            if (reference.getWidth() > 0) {
                attrs.put("width", String.valueOf(reference.getWidth()));
            }
            if (reference.getHeight() > 0) {
                attrs.put("height", String.valueOf(reference.getHeight()));
            }
        } else {
            ImageResourceResolver.ResolvedImage resolved = imageResourceResolver.resolveToDataUri(src, null, context.getBaseUri());
            attrs.put("src", safeUrlSanitizer.sanitizeImageSrc(resolved.getDataUri()));
        }
        writer.voidTag("img", attrs);
    }

    private MdImageReference findReference(MarkdownPdfContext context, String src) {
        if (context == null || context.getImageReferences() == null) {
            return null;
        }
        for (MdImageReference reference : context.getImageReferences()) {
            if (reference.getToken().equals(src)) {
                return reference;
            }
        }
        return null;
    }

    private String collectText(Node node) {
        StringBuilder sb = new StringBuilder();
        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof Text) {
                sb.append(((Text) child).getLiteral());
            }
            child = child.getNext();
        }
        return sb.toString();
    }
}
