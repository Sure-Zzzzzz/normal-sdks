package io.github.surezzzzzz.sdk.http.xff.support;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 预读取前缀后仍可完整回放请求体的包装请求。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class ReplayableRequestBodyWrapper extends HttpServletRequestWrapper {

    private final InputStream replayStream;
    private final File replayFile;
    private final Map<String, String[]> parameters;

    /**
     * 创建请求体回放包装。
     *
     * @param request   原始请求
     * @param prefix    已读取的前缀
     * @param remainder 原始请求剩余流
     */
    public ReplayableRequestBodyWrapper(HttpServletRequest request, byte[] prefix,
                                        InputStream remainder,
                                        Map<String, List<String>> parameters) {
        super(request);
        this.replayStream = new SequenceInputStream(new ByteArrayInputStream(prefix), remainder);
        this.replayFile = null;
        this.parameters = immutableParameters(parameters);
    }

    public ReplayableRequestBodyWrapper(HttpServletRequest request, File replayFile,
                                        Map<String, List<String>> parameters) {
        super(request);
        this.replayStream = null;
        this.replayFile = replayFile;
        this.parameters = immutableParameters(parameters);
    }

    public void deleteReplayFile() {
        if (replayFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(replayFile.toPath());
        } catch (IOException e) {
            log.warn("删除请求体回放临时文件失败，path=[{}]", replayFile.getAbsolutePath(), e);
        }
    }

    private Map<String, String[]> immutableParameters(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().toArray(new String[entry.getValue().size()]));
        }
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public String getParameter(String name) {
        String[] values = parameters.get(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = parameters.get(name);
        return values == null ? null : values.clone();
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 返回完整的回放输入流。
     *
     * @return Servlet 输入流
     */
    @Override
    public ServletInputStream getInputStream() {
        try {
            InputStream source = replayFile == null
                    ? replayStream : new FileInputStream(replayFile);
            return new ReplayableServletInputStream(source);
        } catch (IOException e) {
            log.warn("打开请求体回放流失败", e);
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "请求体回放临时文件不可读取"));
        }
    }

    /**
     * 返回与输入流共享来源的 UTF-8 Reader。
     *
     * @return UTF-8 Reader
     */
    @Override
    public java.io.BufferedReader getReader() {
        return new java.io.BufferedReader(new java.io.InputStreamReader(
                getInputStream(), StandardCharsets.UTF_8));
    }

    private static final class ReplayableServletInputStream extends ServletInputStream {

        private final InputStream source;

        private ReplayableServletInputStream(InputStream source) {
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            return source.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return source.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            source.close();
        }

        @Override
        public boolean isFinished() {
            try {
                return source.available() == 0;
            } catch (IOException e) {
                log.warn("判断请求体回放流完成状态失败", e);
                return false;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                    String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID,
                            "请求体回放不支持异步读取"));
        }
    }
}
