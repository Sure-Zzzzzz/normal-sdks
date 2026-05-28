package io.github.surezzzzzz.sdk.template.doc.engine;

import io.github.surezzzzzz.sdk.template.doc.constant.OutputFormat;
import io.github.surezzzzzz.sdk.template.doc.document.Document;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import io.github.surezzzzzz.sdk.template.doc.handler.OutputHandler;
import lombok.Getter;

import java.io.File;
import java.io.OutputStream;

/**
 * Template Render Result - 渲染结果，持有渲染产物并提供链式输出 API
 *
 * @author surezzzzzz
 */
@Getter
public class TemplateRenderResult {

    private final Document document;
    private final OutputHandlerRegistry outputHandlerRegistry;
    private final String suffix;

    public TemplateRenderResult(Document document, OutputHandlerRegistry outputHandlerRegistry, String suffix) {
        this.document = document;
        this.outputHandlerRegistry = outputHandlerRegistry;
        this.suffix = suffix;
    }

    /**
     * 指定输出格式
     *
     * @param format 输出格式
     * @return OutputTarget，提供 toFile / toStream / toBytes
     */
    public OutputTarget output(OutputFormat format) {
        OutputHandler handler = outputHandlerRegistry.find(format);
        if (handler == null) {
            throw TemplateRenderException.outputHandlerNotFound(format.getCode());
        }
        return new OutputTarget(document, handler);
    }

    /**
     * 自动推断输出格式（根据模板文件后缀）
     *
     * @return OutputTarget，提供 toFile / toStream / toBytes
     */
    public OutputTarget output() {
        OutputHandler handler = outputHandlerRegistry.findBySuffix(suffix);
        if (handler == null) {
            throw TemplateRenderException.outputHandlerNotFound(suffix);
        }
        return new OutputTarget(document, handler);
    }

    /**
     * 渲染结果是否为空
     *
     * @return true 表示文档为空
     */
    public boolean isEmpty() {
        return document == null || document.isEmpty();
    }

    /**
     * Output Target - 输出目标持有器
     */
    @Getter
    public static class OutputTarget {

        private final Document document;
        private final OutputHandler handler;

        public OutputTarget(Document document, OutputHandler handler) {
            this.document = document;
            this.handler = handler;
        }

        /**
         * 输出到字节数组
         *
         * @return DOCX 字节数组
         */
        public byte[] toBytes() {
            return handler.toBytes(document);
        }

        /**
         * 输出到流
         *
         * @param outputStream 目标流
         */
        public void toStream(OutputStream outputStream) {
            handler.writeToStream(document, outputStream);
        }

        /**
         * 输出到文件（完整路径）
         *
         * @param filePath 完整文件路径，如 "/output/report.docx"
         */
        public void toFile(String filePath) {
            handler.writeToFile(document, filePath);
        }

        /**
         * 输出到文件（目录 + 文件名分开指定）
         *
         * @param dir      输出目录，如 "/output"
         * @param fileName 文件名，如 "report.docx"
         */
        public void toFile(String dir, String fileName) {
            if (dir == null || fileName == null) {
                throw new IllegalArgumentException("dir and fileName must not be null");
            }
            handler.writeToFile(document, dir + File.separator + fileName);
        }
    }
}