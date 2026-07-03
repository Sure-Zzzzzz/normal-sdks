package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.exception.DocxToPdfFailedException;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.pdf.PdfOutputHandler;
import lombok.RequiredArgsConstructor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF Convert Helper
 *
 * <p>面向业务的 PDF 转换快捷入口，仅支持已有 DOCX 转 PDF。
 * 所有读取路径走 {@link LimitedInputStreamHelper} 受限读取，防止无上限读取。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class PdfConvertHelper {

    private final PdfOutputHandler pdfOutputHandler;
    private final LimitedInputStreamHelper inputStreamHelper;

    /**
     * 已有 DOCX 字节数组转 PDF 字节数组
     *
     * @param docxBytes DOCX 字节数组
     * @return PDF 字节数组
     */
    public byte[] fromDocx(byte[] docxBytes) {
        return pdfOutputHandler.convertToPdf(docxBytes);
    }

    /**
     * 已有 DOCX 输入流转 PDF 字节数组
     *
     * @param inputStream DOCX 输入流
     * @return PDF 字节数组
     */
    public byte[] fromDocx(InputStream inputStream) {
        return pdfOutputHandler.convertToPdf(toByteArray(inputStream, "读取 DOCX 输入流失败"));
    }

    /**
     * 已有 DOCX 文件转 PDF 字节数组
     *
     * @param file DOCX 文件
     * @return PDF 字节数组
     */
    public byte[] fromDocx(File file) {
        return fromDocx(toByteArray(file.toPath(), String.format("读取 DOCX 文件失败: %s", file.getAbsolutePath())));
    }

    /**
     * 已有 DOCX 路径转 PDF 字节数组
     *
     * @param path DOCX 文件路径
     * @return PDF 字节数组
     */
    public byte[] fromDocx(Path path) {
        return fromDocx(toByteArray(path, String.format("读取 DOCX 文件失败: %s", path)));
    }

    /**
     * 已有 DOCX 字节数组转 PDF，并写出到流
     *
     * @param docxBytes    DOCX 字节数组
     * @param outputStream PDF 输出流
     */
    public void fromDocx(byte[] docxBytes, OutputStream outputStream) {
        try {
            pdfOutputHandler.convertToPdf(docxBytes, outputStream);
        } catch (IOException e) {
            throw DocxToPdfFailedException.conversionFailed("写出 PDF 流失败", e);
        }
    }

    /**
     * 已有 DOCX 输入流转 PDF，并写出到流
     *
     * @param inputStream  DOCX 输入流
     * @param outputStream PDF 输出流
     */
    public void fromDocx(InputStream inputStream, OutputStream outputStream) {
        fromDocx(toByteArray(inputStream, "读取 DOCX 输入流失败"), outputStream);
    }

    /**
     * 已有 DOCX 文件转 PDF 文件
     *
     * @param docxFile DOCX 文件
     * @param pdfFile  PDF 输出文件
     */
    public void fromDocx(File docxFile, File pdfFile) {
        fromDocx(docxFile.toPath(), pdfFile.toPath());
    }

    /**
     * 已有 DOCX 路径转 PDF 路径
     *
     * @param docxPath DOCX 文件路径
     * @param pdfPath  PDF 输出路径
     */
    public void fromDocx(Path docxPath, Path pdfPath) {
        try {
            Files.write(pdfPath, fromDocx(docxPath));
        } catch (IOException e) {
            throw DocxToPdfFailedException.conversionFailed(
                    String.format("写出 PDF 文件失败: %s", pdfPath), e);
        }
    }

    private byte[] toByteArray(Path path, String failureMessage) {
        if (!Files.exists(path)) {
            throw DocxToPdfFailedException.conversionFailed(failureMessage, new java.nio.file.NoSuchFileException(path.toString()));
        }
        try (InputStream is = Files.newInputStream(path)) {
            return readLimited(is, failureMessage);
        } catch (IOException e) {
            throw DocxToPdfFailedException.conversionFailed(failureMessage, e);
        }
    }

    private byte[] toByteArray(InputStream inputStream, String failureMessage) {
        return readLimited(inputStream, failureMessage);
    }

    private byte[] readLimited(InputStream inputStream, String failureMessage) {
        try {
            return inputStreamHelper.readTemplateBytes(inputStream);
        } catch (TemplateRenderException e) {
            throw DocxToPdfFailedException.conversionFailed(failureMessage, e);
        }
    }
}
