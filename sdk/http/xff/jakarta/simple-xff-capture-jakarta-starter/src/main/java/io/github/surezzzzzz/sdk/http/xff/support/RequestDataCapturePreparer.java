package io.github.surezzzzzz.sdk.http.xff.support;

import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureProperties;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.core.constant.*;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestBodySnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestDataSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestParameterSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

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
            throw validation(SimpleXffCaptureConstant.DETAIL_REQUEST_DATA_REQUIRED);
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
            throw validation(SimpleXffCaptureConstant.DETAIL_REQUEST_REQUIRED);
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
            replayFile = File.createTempFile(SimpleXffCaptureConstant.REPLAY_FILE_PREFIX,
                    SimpleXffCaptureConstant.REPLAY_FILE_SUFFIX);
            FormBodyReadResult readResult;
            try (InputStream source = request.getInputStream();
                 FileOutputStream fileOutput = new FileOutputStream(replayFile)) {
                readResult = parseFormAndWriteBody(source, fileOutput, prefix, formParameters);
            }
            bodyFullyWritten = true;
            RequestParameterSnapshot form;
            if (readResult.isFormReadFailed()) {
                form = parameterStatus(true, RequestDataCaptureStatus.READ_FAILED);
            } else if (formParameters.isEmpty()) {
                form = parameterStatus(true, RequestDataCaptureStatus.ABSENT);
            } else {
                form = new RequestParameterSnapshot(RequestDataCaptureStatus.CAPTURED, formParameters);
            }
            BodyReadResult bodyReadResult = BodyReadResult.file(request, prefix.toByteArray(),
                    readResult.getBodyLength() > properties.getBody().getMaxBytes());
            RequestBodySnapshot body = toBodySnapshot(request, bodyReadResult);
            return new RequestDataCaptureResult(
                    new ReplayableRequestBodyWrapper(request, replayFile,
                            mergeParameters(queryParameters, form)),
                    new RequestDataSnapshot(query, form, body));
        } catch (RuntimeException e) {
            log.warn("读取并回放表单请求体失败，异常类型=[{}]", e.getClass().getName());
            return formAndBodyReadFailedResult(request, replayFile, bodyFullyWritten, query,
                    queryParameters, formParameters);
        } catch (IOException e) {
            log.warn("读取并回放表单请求体失败，异常类型=[{}]", e.getClass().getName());
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
            log.warn("删除请求体回放临时文件失败，异常类型=[{}]", e.getClass().getName());
        }
    }

    private FormBodyReadResult parseFormAndWriteBody(InputStream source, FileOutputStream fileOutput,
                                                     ByteArrayOutputStream prefix,
                                                     Map<String, List<String>> formParameters) throws IOException {
        ByteArrayOutputStream parameter = new ByteArrayOutputStream();
        byte[] buffer = new byte[SimpleXffCaptureConstant.BODY_READ_BUFFER_BYTES];
        int read;
        long bodyLength = 0L;
        boolean hasBody = false;
        boolean formReadFailed = false;
        while ((read = source.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            hasBody = true;
            bodyLength += read;
            fileOutput.write(buffer, 0, read);
            for (int index = 0; index < read; index++) {
                int value = buffer[index] & 0xff;
                if (value == SimpleXffCaptureConstant.FORM_PARAMETER_SEPARATOR) {
                    if (!appendFormParameter(parameter, formParameters)) {
                        formReadFailed = true;
                    }
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
            if (!appendFormParameter(parameter, formParameters)) {
                formReadFailed = true;
            }
        }
        return new FormBodyReadResult(bodyLength, formReadFailed);
    }

    private boolean appendFormParameter(ByteArrayOutputStream parameter,
                                        Map<String, List<String>> formParameters) {
        if (parameter.size() == 0) {
            return true;
        }
        try {
            Map<String, List<String>> parsed = parseParameters(
                    new String(parameter.toByteArray(), StandardCharsets.UTF_8));
            appendParameters(formParameters, parsed);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("Form 参数解码失败，保留完整请求体回放，异常类型=[{}]", e.getClass().getName());
            return false;
        }
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
            log.warn("Query 参数读取失败，无法构造下游参数视图，异常类型=[{}]", e.getClass().getName());
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
            log.warn("Form 参数采集失败，异常类型=[{}]", e.getClass().getName());
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
            log.warn("Form 参数采集失败，异常类型=[{}]", e.getClass().getName());
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
            log.warn("读取请求体输入流失败，异常类型=[{}]", e.getClass().getName());
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
                byte[] buffer = new byte[(int) Math.min(SimpleXffCaptureConstant.BODY_READ_BUFFER_BYTES,
                        maxBytes - prefix.size())];
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
            log.warn("读取请求体失败，已读取字节数=[{}]，异常类型=[{}]",
                    prefix.size(), e.getClass().getName());
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
        String mediaType = contentType.split(SimpleXffCaptureConstant.MEDIA_TYPE_PARAMETER_SEPARATOR, 2)[0]
                .trim().toLowerCase(Locale.ROOT);
        for (String allowed : properties.getBody().getAllowedContentTypes()) {
            if (matchesContentType(mediaType, allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesContentType(String mediaType, String configured) {
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(SimpleXffCaptureConstant.MEDIA_TYPE_SEPARATOR);
        String type = normalized.substring(0, separator);
        String subtype = normalized.substring(separator + 1);
        String typePrefix = type + SimpleXffCaptureConstant.MEDIA_TYPE_SEPARATOR;
        if (SimpleXffCaptureConstant.MEDIA_TYPE_WILDCARD.equals(subtype)) {
            return mediaType.startsWith(typePrefix);
        }
        if (subtype.startsWith(SimpleXffCaptureConstant.MEDIA_TYPE_SUFFIX_WILDCARD)) {
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
            throw validation(SimpleXffCaptureConstant.DETAIL_UTF8_UNAVAILABLE);
        }
    }

    private Map<String, List<String>> parseParameters(String encodedParameters) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        String[] pairs = encodedParameters.split(
                String.valueOf(SimpleXffCaptureConstant.FORM_PARAMETER_SEPARATOR), -1);
        for (String pair : pairs) {
            int separator = pair.indexOf(SimpleXffCaptureConstant.FORM_VALUE_SEPARATOR);
            String encodedName = separator < 0 ? pair : pair.substring(0, separator);
            String encodedValue = separator < 0
                    ? SimpleXffCaptureCoreConstant.EMPTY_VALUE : pair.substring(separator + 1);
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
            throw validation(SimpleXffCaptureConstant.DETAIL_BODY_MAX_BYTES_POSITIVE);
        }
        List<String> allowedContentTypes = properties.getBody().getAllowedContentTypes();
        if (allowedContentTypes == null) {
            throw validation(SimpleXffCaptureConstant.DETAIL_ALLOWED_CONTENT_TYPES_REQUIRED);
        }
        for (String allowedContentType : allowedContentTypes) {
            validateAllowedContentType(allowedContentType);
        }
    }

    private void validateAllowedContentType(String allowedContentType) {
        if (allowedContentType == null || allowedContentType.trim().isEmpty()) {
            throw validation(SimpleXffCaptureConstant.DETAIL_ALLOWED_CONTENT_TYPE_NOT_BLANK);
        }
        String normalized = allowedContentType.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(SimpleXffCaptureConstant.MEDIA_TYPE_SEPARATOR);
        if (separator <= 0 || separator != normalized.lastIndexOf(SimpleXffCaptureConstant.MEDIA_TYPE_SEPARATOR)
                || separator == normalized.length() - 1) {
            throw validation(String.format(SimpleXffCaptureConstant.DETAIL_ALLOWED_CONTENT_TYPE_INVALID,
                    allowedContentType));
        }
        String type = normalized.substring(0, separator);
        String subtype = normalized.substring(separator + 1);
        if (SimpleXffCaptureConstant.MEDIA_TYPE_WILDCARD.equals(type)
                || (subtype.indexOf(SimpleXffCaptureConstant.MEDIA_TYPE_WILDCARD) >= 0
                && !(SimpleXffCaptureConstant.MEDIA_TYPE_WILDCARD.equals(subtype)
                || (subtype.startsWith(SimpleXffCaptureConstant.MEDIA_TYPE_SUFFIX_WILDCARD)
                && subtype.length() > SimpleXffCaptureConstant.MEDIA_TYPE_SUFFIX_WILDCARD.length())))) {
            throw validation(String.format(SimpleXffCaptureConstant.DETAIL_ALLOWED_CONTENT_TYPE_INVALID,
                    allowedContentType));
        }
    }

    private boolean isFormContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return SimpleXffCaptureConstant.FORM_CONTENT_TYPE.equals(
                contentType.split(SimpleXffCaptureConstant.MEDIA_TYPE_PARAMETER_SEPARATOR, 2)[0]
                        .trim().toLowerCase(Locale.ROOT));
    }

    private enum BodyReadStatus {
        EMPTY, COMPLETE, TRUNCATED, READ_FAILED
    }

    @Value
    private static class FormBodyReadResult {
        long bodyLength;
        boolean formReadFailed;
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
