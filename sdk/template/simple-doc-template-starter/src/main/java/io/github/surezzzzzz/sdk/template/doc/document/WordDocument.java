package io.github.surezzzzzz.sdk.template.doc.document;

import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import lombok.Getter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;

/**
 * Word Document - Word 文档产物，持有最终渲染字节（已含 chart XML）
 *
 * @author surezzzzzz
 */
@Getter
public class WordDocument implements Document {

    /**
     * -- GETTER --
     * 获取最终 DOCX 字节（直接输出，不经 POI 二次序列化）
     */
    private final byte[] docxBytes;

    public WordDocument(byte[] docxBytes) {
        this.docxBytes = docxBytes;
    }

    /**
     * 仅供需要解析文档内容的场景（如测试断言）使用
     */
    public XWPFDocument toXWPFDocument() {
        try {
            return new XWPFDocument(new ByteArrayInputStream(docxBytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse WordDocument bytes", e);
        }
    }

    @Override
    public String getFormat() {
        return SimpleDocTemplateConstant.FORMAT_DOCX;
    }

    @Override
    public boolean isEmpty() {
        return docxBytes == null || docxBytes.length == 0;
    }
}