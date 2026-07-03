package io.github.surezzzzzz.sdk.template.doc.model;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Markdown PDF Context
 *
 * <p>Markdown 转 PDF 渲染过程中的上下文数据。
 *
 * @author surezzzzzz
 */
@Getter
public class MarkdownPdfContext {

    private final String templateLocation;
    private final String resolvedLocation;
    private final String baseUri;
    private final List<MdImageReference> imageReferences;
    private final boolean allowRemoteResource;

    public MarkdownPdfContext(String templateLocation,
                              String resolvedLocation,
                              String baseUri,
                              List<MdImageReference> imageReferences,
                              boolean allowRemoteResource) {
        this.templateLocation = templateLocation;
        this.resolvedLocation = resolvedLocation;
        this.baseUri = baseUri;
        this.imageReferences = imageReferences == null ? Collections.emptyList() : Collections.unmodifiableList(imageReferences);
        this.allowRemoteResource = allowRemoteResource;
    }
}
