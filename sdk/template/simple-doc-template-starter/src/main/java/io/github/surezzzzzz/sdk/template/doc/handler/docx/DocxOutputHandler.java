package io.github.surezzzzzz.sdk.template.doc.handler.docx;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.constant.OutputFormat;
import io.github.surezzzzzz.sdk.template.doc.document.Document;
import io.github.surezzzzzz.sdk.template.doc.document.WordDocument;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.OutputHandler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * DOCX Output Handler
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
public class DocxOutputHandler implements OutputHandler {

    @Override
    public void writeToFile(Document document, String filePath) {
        byte[] bytes = toDocxBytes(document);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(bytes);
        } catch (IOException e) {
            throw TemplateRenderException.writeFailed(e);
        }
    }

    @Override
    public void writeToStream(Document document, OutputStream outputStream) {
        byte[] bytes = toDocxBytes(document);
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            throw TemplateRenderException.writeFailed(e);
        }
    }

    @Override
    public byte[] toBytes(Document document) {
        return toDocxBytes(document);
    }

    @Override
    public OutputFormat supportedFormat() {
        return OutputFormat.DOCX;
    }

    private byte[] toDocxBytes(Document document) {
        if (!(document instanceof WordDocument)) {
            throw TemplateRenderException.formatMismatch(OutputFormat.DOCX.getCode(), document.getFormat());
        }
        return ((WordDocument) document).getDocxBytes();
    }
}