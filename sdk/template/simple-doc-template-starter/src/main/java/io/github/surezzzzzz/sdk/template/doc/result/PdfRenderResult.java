package io.github.surezzzzzz.sdk.template.doc.result;

import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfOutputHandler;
import lombok.Getter;

import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/**
 * PDF 渲染结果 - PDF 专用结果类，不实现 Document 接口
 * <p>
 * 通过 renderForPdf() 获取，只暴露 PDF 输出方法，无法误输出为 DOCX。
 *
 * @author surezzzzzz
 */
@Getter
public class PdfRenderResult {

    /**
     * 渲染后的 DOCX 字节（含 chart marker 或含 OOXML chart）
     */
    private final byte[] docxBytes;

    /**
     * Chart 路径专用：按 chart 出现顺序存储 PNG，无 chart 时为空 List
     */
    private final List<byte[]> chartPngList;

    /**
     * Spring 管理的 PdfOutputHandler，由 TemplateEngine 注入
     */
    private final PdfOutputHandler handler;

    public PdfRenderResult(byte[] docxBytes, List<byte[]> chartPngList, PdfOutputHandler handler) {
        this.docxBytes = docxBytes;
        this.chartPngList = chartPngList != null ? chartPngList : Collections.emptyList();
        this.handler = handler;
    }

    /**
     * 输出为 PDF 字节数组
     */
    public byte[] toBytes() {
        return handler.toPdfBytes(this);
    }

    /**
     * 输出到文件
     */
    public void toFile(String filePath) {
        handler.writeToFile(this, filePath);
    }

    /**
     * 输出到流（适合 HTTP 响应等场景）
     */
    public void toStream(OutputStream outputStream) {
        handler.writeToStream(this, outputStream);
    }
}
