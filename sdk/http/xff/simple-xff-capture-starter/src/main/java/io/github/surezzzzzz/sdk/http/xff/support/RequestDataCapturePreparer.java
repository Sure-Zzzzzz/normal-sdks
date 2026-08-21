package io.github.surezzzzzz.sdk.http.xff.support;

import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureProperties;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestBodyCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.constant.RequestDataCaptureStatus;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestBodySnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestDataSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestParameterSnapshot;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 请求参数与请求体采集准备器。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class RequestDataCapturePreparer {

    private final SimpleXffCaptureProperties.RequestData properties;
    private final RequestDataRuleMatcher ruleMatcher;

    /**
     * 创建请求数据准备器。
     *
     * @param properties Capture 配置
     */
    public RequestDataCapturePreparer(SimpleXffCaptureProperties properties) {
        if (properties == null || properties.getRequestData() == null) {
            throw validation("请求数据采集配置不能为空");
        }
        this.properties = properties.getRequestData();
        this.ruleMatcher = new RequestDataRuleMatcher(this.properties.getWhitelist(),
                this.properties.getBlacklist());
        validate();
    }

    /**
     * 准备请求数据快照与下游请求。
     *
     * @param request 原始请求
     * @return 请求数据准备结果
     */
    public RequestDataCaptureResult prepare(HttpServletRequest request) {
        if (request == null) {
            throw validation("请求不能为空");
        }
        boolean queryEnabled = properties.getQueryParameters().isEnabled();
        boolean formEnabled = properties.getFormParameters().isEnabled();
        boolean bodyEnabled = properties.getBody().isEnabled();
        boolean enabled = queryEnabled || formEnabled || bodyEnabled;
        boolean matched = enabled && ruleMatcher.matches(request);
        if (!enabled) {
            return new RequestDataCaptureResult(request, disabledSnapshot());
        }
        if (!matched) {
            return new RequestDataCaptureResult(request,
                    new RequestDataSnapshot(
                            parameterStatus(queryEnabled, RequestDataCaptureStatus.RULE_NOT_MATCHED),
                            parameterStatus(formEnabled, RequestDataCaptureStatus.RULE_NOT_MATCHED),
                            bodyStatus(bodyEnabled, RequestBodyCaptureStatus.RULE_NOT_MATCHED)));
        }

        Map<String, List<String>> requestQueryParameters = parseRequestQueryParameters(
                request.getQueryString());
        RequestParameterSnapshot query = queryEnabled
                ? captureQueryParameters(requestQueryParameters, request.getQueryString())
                : parameterStatus(false, RequestDataCaptureStatus.DISABLED);
        boolean formContentType = isFormContentType(request.getContentType());
        boolean needsBody = bodyEnabled && hasAllowedBodyContentType(request.getContentType());
        if (needsBody && formEnabled && formContentType) {
            return prepareFormAndBody(request, requestQueryParameters, query);
        }
        RequestParameterSnapshot form = formEnabled
                ? captureFormParameters(request, requestQueryParameters, BodyReadResult.empty(request), formContentType)
                : parameterStatus(false, RequestDataCaptureStatus.DISABLED);
        BodyReadResult bodyReadResult = needsBody ? readBody(request) : BodyReadResult.empty(request);
        RequestBodySnapshot body = bodyEnabled
                ? toBodySnapshot(request, bodyReadResult)
                : bodyStatus(false, RequestBodyCaptureStatus.DISABLED);
        return new RequestDataCaptureResult(
                bodyReadResult.createRequest(mergeParameters(requestQueryParameters, form)),
                new RequestDataSnapshot(query, form, body));
    }

    private RequestDataCaptureResult prepareFormAndBody(HttpServletRequest request,
                                                        Map<String, List<String>> queryParameters,
                                                        RequestParameterSnapshot query) {
        File replayFile = null;
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        Map<String, List<String>> formParameters = new LinkedHashMap<>();
        boolean bodyFullyWritten = false;
        try {
            replayFile = File.createTempFile("simple-xff-capture-", ".body");
            long bodyLength;
            try (InputStream source = request.getInputStream();
                 FileOutputStream fileOutput = new FileOutputStream(replayFile)) {
                bodyLength = parseFormAndWriteBody(source, fileOutput, prefix, formParameters);
            }
            bodyFullyWritten = true;
            RequestParameterSnapshot form = formParameters.isEmpty()
                    ? parameterStatus(true, RequestDataCaptureStatus.ABSENT)
                    : new RequestParameterSnapshot(RequestDataCaptureStatus.CAPTURED, formParameters);
            BodyReadResult bodyReadResult = BodyReadResult.file(request, prefix.toByteArray(),
                    bodyLength > properties.getBody().getMaxBytes());
            RequestBodySnapshot body = toBodySnapshot(request, bodyReadResult);
            return new RequestDataCaptureResult(
                    new ReplayableRequestBodyWrapper(request, replayFile,
                            mergeParameters(queryParameters, form)),
                    new RequestDataSnapshot(query, form, body));
        } catch (RuntimeException e) {
            log.warn("读取并回放表单请求体失败", e);
            return formAndBodyReadFailedResult(request, replayFile, bodyFullyWritten, query,
                    queryParameters, formParameters);
        } catch (IOException e) {
            log.warn("读取并回放表单请求体失败", e);
            return formAndBodyReadFailedResult(request, replayFile, bodyFullyWritten, query,
                    queryParameters, formParameters);
        }
    }

    private RequestDataCaptureResult formAndBodyReadFailedResult(HttpServletRequest request,
                                                                 File replayFile,
                                                                 boolean bodyFullyWritten,
                                                                 RequestParameterSnapshot query,
                                                                 Map<String, List<String>> queryParameters,
                                                                 Map<String, List<String>> formParameters) {
        RequestDataSnapshot snapshot = new RequestDataSnapshot(query,
                parameterStatus(true, RequestDataCaptureStatus.READ_FAILED),
                bodyStatus(true, RequestBodyCaptureStatus.READ_FAILED));
        if (bodyFullyWritten && replayFile != null) {
            Map<String, List<String>> parameters = new LinkedHashMap<>();
            appendParameters(parameters, queryParameters);
            appendParameters(parameters, formParameters);
            return new RequestDataCaptureResult(new ReplayableRequestBodyWrapper(request, replayFile,
                    parameters), snapshot);
        }
        deleteReplayFile(replayFile);
        return new RequestDataCaptureResult(request, snapshot);
    }

    private void deleteReplayFile(File replayFile) {
        if (replayFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(replayFile.toPath());
        } catch (IOException e) {
            log.warn("删除请求体回放临时文件失败，path=[{}]", replayFile.getAbsolutePath(), e);
        }
    }

    private long parseFormAndWriteBody(InputStream source, FileOutputStream fileOutput,
                                       ByteArrayOutputStream prefix,
                                       Map<String, List<String>> formParameters) throws IOException {
        ByteArrayOutputStream parameter = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        long bodyLength = 0L;
        boolean hasBody = false;
        while ((read = source.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            hasBody = true;
            bodyLength += read;
            fileOutput.write(buffer, 0, read);
            for (int index = 0; index < read; index++) {
                int value = buffer[index] & 0xff;
                if (value == '&') {
                    appendFormParameter(parameter, formParameters);
                    parameter.reset();
                } else {
                    parameter.write(value);
                }
                if (prefix.size() < properties.getBody().getMaxBytes()) {
                    prefix.write(value);
                }
            }
        }
        if (hasBody || parameter.size() > 0) {
            appendFormParameter(parameter, formParameters);
        }
        return bodyLength;
    }

    private void appendFormParameter(ByteArrayOutputStream parameter,
                                     Map<String, List<String>> formParameters) {
        if (parameter.size() == 0) {
            return;
        }
        Map<String, List<String>> parsed = parseParameters(
                new String(parameter.toByteArray(), StandardCharsets.UTF_8));
        appendParameters(formParameters, parsed);
    }

    private RequestParameterSnapshot captureQueryParameters(Map<String, List<String>> values,
                                                            String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return parameterStatus(true, RequestDataCaptureStatus.ABSENT);
        }
        if (values == null) {
            return parameterStatus(true, RequestDataCaptureStatus.READ_FAILED);
        }
        return new RequestParameterSnapshot(RequestDataCaptureStatus.CAPTURED, values);
    }

    private Map<String, List<String>> parseRequestQueryParameters(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return parseParameters(queryString);
        } catch (RuntimeException e) {
            log.warn("Query 参数读取失败，无法构造下游参数视图", e);
            return null;
        }
    }

    private RequestParameterSnapshot captureFormParameters(HttpServletRequest request,
                                                           Map<String, List<String>> queryParameters,
                                                           BodyReadResult bodyReadResult,
                                                           boolean formContentType) {
        if (!formContentType) {
            return parameterStatus(true, RequestDataCaptureStatus.ABSENT);
        }
        try {
            Map<String, String[]> servletParameters = request.getParameterMap();
            if (servletParameters != null && !servletParameters.isEmpty()) {
                Map<String, List<String>> formParameters = copyServletParameters(servletParameters,
                        queryParameters);
                if (!formParameters.isEmpty()) {
                    return new RequestParameterSnapshot(RequestDataCaptureStatus.CAPTURED,
                            formParameters);
                }
                return parameterStatus(true, RequestDataCaptureStatus.ABSENT);
            }
        } catch (RuntimeException e) {
            log.warn("Form 参数采集失败", e);
            return parameterStatus(true, RequestDataCaptureStatus.READ_FAILED);
        }
        if (bodyReadResult.status == BodyReadStatus.EMPTY) {
            return parameterStatus(true, RequestDataCaptureStatus.ABSENT);
        }
        if (bodyReadResult.status == BodyReadStatus.READ_FAILED) {
            return parameterStatus(true, RequestDataCaptureStatus.READ_FAILED);
        }
        if (bodyReadResult.status == BodyReadStatus.TRUNCATED) {
            return parameterStatus(true, RequestDataCaptureStatus.TRUNCATED);
        }
        try {
            return new RequestParameterSnapshot(RequestDataCaptureStatus.CAPTURED,
                    parseParameters(new String(bodyReadResult.prefix, StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            log.warn("Form 参数采集失败", e);
            return parameterStatus(true, RequestDataCaptureStatus.READ_FAILED);
        }
    }

    private Map<String, List<String>> copyServletParameters(Map<String, String[]> source,
                                                            Map<String, List<String>> queryParameters) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : source.entrySet()) {
            List<String> parameterValues = new ArrayList<>();
            if (entry.getValue() != null) {
                Collections.addAll(parameterValues, entry.getValue());
            }
            removeQueryPrefix(parameterValues, queryParameters == null
                    ? null : queryParameters.get(entry.getKey()));
            if (!parameterValues.isEmpty()) {
                values.put(entry.getKey(), parameterValues);
            }
        }
        return values;
    }

    private void removeQueryPrefix(List<String> servletValues, List<String> queryValues) {
        if (queryValues == null || servletValues.size() < queryValues.size()) {
            return;
        }
        for (int index = 0; index < queryValues.size(); index++) {
            if (!queryValues.get(index).equals(servletValues.get(index))) {
                return;
            }
        }
        servletValues.subList(0, queryValues.size()).clear();
    }

    private BodyReadResult readBody(HttpServletRequest request) {
        long maxBytes = properties.getBody().getMaxBytes();
        InputStream source;
        try {
            source = request.getInputStream();
        } catch (IOException e) {
            log.warn("读取请求体输入流失败", e);
            return BodyReadResult.failed(request);
        }
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        try {
            int first = source.read();
            if (first < 0) {
                return BodyReadResult.empty(request);
            }
            prefix.write(first);
            while (prefix.size() < maxBytes) {
                byte[] buffer = new byte[(int) Math.min(4096L, maxBytes - prefix.size())];
                int read = source.read(buffer);
                if (read < 0) {
                    return BodyReadResult.complete(request, prefix.toByteArray(), source);
                }
                if (read == 0) {
                    continue;
                }
                prefix.write(buffer, 0, read);
            }
            int extra = source.read();
            if (extra < 0) {
                return BodyReadResult.complete(request, prefix.toByteArray(), source);
            }
            return BodyReadResult.truncated(request, prefix.toByteArray(),
                    new java.io.SequenceInputStream(new ByteArrayInputStream(
                            new byte[]{(byte) extra}), source));
        } catch (IOException e) {
            log.warn("读取请求体失败，已读取字节数=[{}]", prefix.size(), e);
            return BodyReadResult.failed(request, prefix.toByteArray(), source);
        }
    }

    private RequestBodySnapshot toBodySnapshot(HttpServletRequest request, BodyReadResult result) {
        String contentType = request.getContentType();
        Long declaredLength = request.getContentLengthLong() < 0L
                ? null : request.getContentLengthLong();
        if (!hasAllowedBodyContentType(contentType)) {
            return bodyStatus(true, contentType == null
                            ? RequestBodyCaptureStatus.NO_BODY : RequestBodyCaptureStatus.CONTENT_TYPE_SKIPPED,
                    contentType, declaredLength);
        }
        if (result.status == BodyReadStatus.EMPTY) {
            return bodyStatus(true, RequestBodyCaptureStatus.NO_BODY, contentType, declaredLength);
        }
        if (result.status == BodyReadStatus.READ_FAILED) {
            return bodyStatus(true, RequestBodyCaptureStatus.READ_FAILED, contentType, declaredLength);
        }
        RequestBodyCaptureStatus status = result.status == BodyReadStatus.TRUNCATED
                ? RequestBodyCaptureStatus.TRUNCATED : RequestBodyCaptureStatus.CAPTURED;
        return new RequestBodySnapshot(status, contentType, declaredLength, result.prefix.length,
                new String(result.prefix, StandardCharsets.UTF_8));
    }

    private boolean hasAllowedBodyContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        for (String allowed : properties.getBody().getAllowedContentTypes()) {
            if (matchesContentType(mediaType, allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesContentType(String mediaType, String configured) {
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('/');
        String type = normalized.substring(0, separator);
        String subtype = normalized.substring(separator + 1);
        String typePrefix = type + "/";
        if ("*".equals(subtype)) {
            return mediaType.startsWith(typePrefix);
        }
        if (subtype.startsWith("*+")) {
            return mediaType.startsWith(typePrefix) && mediaType.endsWith(subtype.substring(1));
        }
        return normalized.equals(mediaType);
    }

    private Map<String, List<String>> mergeParameters(Map<String, List<String>> query,
                                                      RequestParameterSnapshot form) {
        if (query == null) {
            return null;
        }
        Map<String, List<String>> merged = new LinkedHashMap<>();
        appendParameters(merged, query);
        if (form.getStatus() == RequestDataCaptureStatus.CAPTURED) {
            appendParameters(merged, form.getValues());
        }
        return merged.isEmpty() ? null : merged;
    }

    private void appendParameters(Map<String, List<String>> target,
                                  Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            List<String> values = target.get(entry.getKey());
            if (values == null) {
                values = new ArrayList<>();
                target.put(entry.getKey(), values);
            }
            values.addAll(entry.getValue());
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw validation("UTF-8 不可用");
        }
    }

    private Map<String, List<String>> parseParameters(String encodedParameters) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        String[] pairs = encodedParameters.split("&", -1);
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            String encodedName = separator < 0 ? pair : pair.substring(0, separator);
            String encodedValue = separator < 0 ? "" : pair.substring(separator + 1);
            String name = decode(encodedName);
            String value = decode(encodedValue);
            List<String> valueList = values.get(name);
            if (valueList == null) {
                valueList = new ArrayList<>();
                values.put(name, valueList);
            }
            valueList.add(value);
        }
        return values;
    }

    private XffCaptureValidationException validation(String detail) {
        return new XffCaptureValidationException(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID,
                String.format(ErrorMessage.CAPTURE_SNAPSHOT_STATE_INVALID, detail));
    }

    private RequestDataSnapshot disabledSnapshot() {
        return RequestDataSnapshot.disabled();
    }

    private RequestParameterSnapshot parameterStatus(boolean enabled, RequestDataCaptureStatus status) {
        return new RequestParameterSnapshot(enabled ? status : RequestDataCaptureStatus.DISABLED,
                Collections.<String, List<String>>emptyMap());
    }

    private RequestBodySnapshot bodyStatus(boolean enabled, RequestBodyCaptureStatus status) {
        return bodyStatus(enabled, status, null, null);
    }

    private RequestBodySnapshot bodyStatus(boolean enabled, RequestBodyCaptureStatus status,
                                           String contentType, Long declaredLength) {
        return new RequestBodySnapshot(enabled ? status : RequestBodyCaptureStatus.DISABLED,
                contentType, declaredLength, 0L, null);
    }

    private void validate() {
        if (properties.getBody().getMaxBytes() <= 0L) {
            throw validation("请求体最大采集字节数必须大于 0");
        }
        List<String> allowedContentTypes = properties.getBody().getAllowedContentTypes();
        if (allowedContentTypes == null) {
            throw validation("请求体允许 Content-Type 不能为空");
        }
        for (String allowedContentType : allowedContentTypes) {
            validateAllowedContentType(allowedContentType);
        }
    }

    private void validateAllowedContentType(String allowedContentType) {
        if (allowedContentType == null || allowedContentType.trim().isEmpty()) {
            throw validation("请求体允许 Content-Type 不能为空白");
        }
        String normalized = allowedContentType.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('/');
        if (separator <= 0 || separator != normalized.lastIndexOf('/')
                || separator == normalized.length() - 1) {
            throw validation("请求体允许 Content-Type 格式非法：" + allowedContentType);
        }
        String type = normalized.substring(0, separator);
        String subtype = normalized.substring(separator + 1);
        if ("*".equals(type) || (subtype.indexOf('*') >= 0
                && !("*".equals(subtype) || (subtype.startsWith("*+")
                && subtype.length() > 2)))) {
            throw validation("请求体允许 Content-Type 格式非法：" + allowedContentType);
        }
    }

    private boolean isFormContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return "application/x-www-form-urlencoded".equals(
                contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT));
    }

    private enum BodyReadStatus {
        EMPTY, COMPLETE, TRUNCATED, READ_FAILED
    }

    private static class BodyReadResult {

        private final HttpServletRequest request;
        private final byte[] prefix;
        private final BodyReadStatus status;

        private BodyReadResult(HttpServletRequest request, byte[] prefix, BodyReadStatus status) {
            this.request = request;
            this.prefix = prefix;
            this.status = status;
        }

        private static BodyReadResult empty(HttpServletRequest request) {
            return new BodyReadResult(request, new byte[0], BodyReadStatus.EMPTY);
        }

        private static BodyReadResult complete(HttpServletRequest request, byte[] prefix,
                                               InputStream remainder) {
            return new BodyReadResult(request,
                    prefix,
                    BodyReadStatus.COMPLETE) {
                @Override
                protected HttpServletRequest createRequest(Map<String, List<String>> parameters) {
                    return new ReplayableRequestBodyWrapper(request, prefix, remainder, parameters);
                }
            };
        }

        private static BodyReadResult file(HttpServletRequest request, byte[] prefix,
                                           boolean truncated) {
            return new BodyReadResult(request, prefix,
                    truncated ? BodyReadStatus.TRUNCATED : BodyReadStatus.COMPLETE);
        }

        private static BodyReadResult truncated(HttpServletRequest request, byte[] prefix,
                                                InputStream remainder) {
            return new BodyReadResult(request,
                    prefix,
                    BodyReadStatus.TRUNCATED) {
                @Override
                protected HttpServletRequest createRequest(Map<String, List<String>> parameters) {
                    return new ReplayableRequestBodyWrapper(request, prefix, remainder, parameters);
                }
            };
        }

        private static BodyReadResult failed(HttpServletRequest request) {
            return new BodyReadResult(request, new byte[0], BodyReadStatus.READ_FAILED);
        }

        private static BodyReadResult failed(HttpServletRequest request, byte[] prefix,
                                             InputStream remainder) {
            return new BodyReadResult(request, prefix, BodyReadStatus.READ_FAILED) {
                @Override
                protected HttpServletRequest createRequest(Map<String, List<String>> parameters) {
                    return new ReplayableRequestBodyWrapper(request, prefix, remainder, parameters);
                }
            };
        }

        protected HttpServletRequest createRequest(Map<String, List<String>> parameters) {
            return request;
        }
    }
}
