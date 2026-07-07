package io.github.surezzzzzz.sdk.template.doc.handler.md;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.OutputFormat;
import io.github.surezzzzzz.sdk.template.doc.document.Document;
import io.github.surezzzzzz.sdk.template.doc.document.MdDocument;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.OutputHandler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Markdown Output Handler
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
public class MdOutputHandler implements OutputHandler {

    @Override
    public void writeToFile(Document document, String filePath) {
        byte[] bytes = toMdBytes(document);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(bytes);
        } catch (IOException e) {
            throw TemplateRenderException.writeFailed(e);
        }
    }

    @Override
    public void writeToStream(Document document, OutputStream outputStream) {
        byte[] bytes = toMdBytes(document);
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            throw TemplateRenderException.writeFailed(e);
        }
    }

    @Override
    public byte[] toBytes(Document document) {
        return toMdBytes(document);
    }

    @Override
    public OutputFormat supportedFormat() {
        return OutputFormat.MD;
    }

    private byte[] toMdBytes(Document document) {
        if (document == null) {
            throw TemplateRenderException.formatMismatch(OutputFormat.MD.getCode(), null);
        }
        if (!(document instanceof MdDocument)) {
            throw TemplateRenderException.formatMismatch(OutputFormat.MD.getCode(), document.getFormat());
        }
        return ((MdDocument) document).getMdBytes();
    }
}
