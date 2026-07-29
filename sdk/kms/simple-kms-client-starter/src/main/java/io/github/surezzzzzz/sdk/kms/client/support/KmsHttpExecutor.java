package io.github.surezzzzzz.sdk.kms.client.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.client.exception.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * KMS HTTP 请求统一执行器。
 *
 * <p>集中控制请求与响应体大小、成功响应媒体类型、状态映射和资源关闭。异常仅保留安全诊断元数据，
 * 不保留或传播原始请求、响应载荷及认证信息。</p>
 *
 * @author surezzzzzz
 */
public class KmsHttpExecutor {

    private final RestTemplate restTemplate;
    private final KmsJsonCodec codec;
    private final KmsHttpErrorMapper errorMapper;
    private final int maxRequestBytes;
    private final int maxResponseBytes;

    /**
     * 创建执行器。
     *
     * @param restTemplate     专属 HTTP 客户端
     * @param codec            专属 JSON 编解码器
     * @param errorMapper      HTTP 错误映射器
     * @param maxRequestBytes  最大请求字节数
     * @param maxResponseBytes 最大响应字节数
     */
    public KmsHttpExecutor(RestTemplate restTemplate, KmsJsonCodec codec, KmsHttpErrorMapper errorMapper,
                           int maxRequestBytes, int maxResponseBytes) {
        this.restTemplate = restTemplate;
        this.codec = codec;
        this.errorMapper = errorMapper;
        this.maxRequestBytes = maxRequestBytes;
        this.maxResponseBytes = maxResponseBytes;
    }

    /**
     * 按无填充 Base64url 规则预估二进制字段在线上占用，避免序列化大载荷后才超过限制。
     */
    private static long base64UrlLength(int length) {
        long groups = length / SimpleKmsClientConstant.BASE64_GROUP_BYTES;
        int remainder = length % SimpleKmsClientConstant.BASE64_GROUP_BYTES;
        return groups * SimpleKmsClientConstant.BASE64_GROUP_CHARACTERS
                + (remainder == 0 ? 0 : remainder + 1);
    }

    /**
     * 校验请求中二进制字段的 Base64url 编码长度。
     *
     * @param values 二进制字段
     * @throws KmsBadRequestException      二进制字段为空时抛出
     * @throws KmsPayloadTooLargeException 编码预估长度超过请求上限时抛出
     */
    public void validateBinaryValues(byte[]... values) {
        long total = 0;
        for (byte[] value : values) {
            KmsValidationHelper.requireValue(value);
            total += base64UrlLength(value.length);
            if (total > maxRequestBytes) {
                throw new KmsPayloadTooLargeException(SimpleKmsClientConstant.MESSAGE_INVALID_REQUEST, null,
                        null, null, null, null);
            }
        }
    }

    /**
     * 执行单次 JSON 请求。
     *
     * @param uri     目标接口地址
     * @param method  HTTP 方法
     * @param headers 可选请求头
     * @param body    可选 JSON 请求体
     * @return 非空 JSON 成功响应，204 响应返回 {@code null}
     * @throws SimpleKmsClientException 请求、响应或通信不符合 Client 契约时抛出
     */
    public JsonNode execute(URI uri, HttpMethod method, Map<String, String> headers, Object body) {
        byte[] requestBody = body == null ? null : codec.write(body);
        if (requestBody != null && requestBody.length > maxRequestBytes) {
            throw new KmsPayloadTooLargeException(SimpleKmsClientConstant.MESSAGE_INVALID_REQUEST,
                    SimpleKmsClientConstant.HTTP_STATUS_PAYLOAD_TOO_LARGE, method.name(), uri.getPath(), null, null);
        }
        try {
            return restTemplate.execute(uri, method, request -> {
                request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                request.getHeaders().setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
                if (headers != null) {
                    for (Map.Entry<String, String> item : headers.entrySet()) {
                        request.getHeaders().set(item.getKey(), item.getValue());
                    }
                }
                if (requestBody != null) {
                    StreamUtils.copy(requestBody, request.getBody());
                }
            }, response -> extract(response, method, uri));
        } catch (SimpleKmsClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KmsTransportException(SimpleKmsClientConstant.MESSAGE_TRANSPORT_ERROR, null);
        }
    }

    /**
     * 统一解析成功和失败响应；无论解析、限长或映射在哪一步失败，均关闭底层响应。
     */
    private JsonNode extract(ClientHttpResponse response, HttpMethod method, URI uri) throws IOException {
        try {
            int status = response.getRawStatusCode();
            byte[] bytes = read(response);
            if (status >= SimpleKmsClientConstant.HTTP_STATUS_SUCCESS_MIN
                    && status < SimpleKmsClientConstant.HTTP_STATUS_SUCCESS_MAX_EXCLUSIVE) {
                if (status == SimpleKmsClientConstant.HTTP_STATUS_NO_CONTENT) {
                    return null;
                }
                if (response.getHeaders().getContentType() == null
                        || !MediaType.APPLICATION_JSON.includes(response.getHeaders().getContentType())) {
                    throw new KmsProtocolException(SimpleKmsClientConstant.MESSAGE_PROTOCOL_ERROR);
                }
                JsonNode node = codec.read(bytes);
                if (node == null || node.isNull()) {
                    throw new KmsProtocolException(SimpleKmsClientConstant.MESSAGE_PROTOCOL_ERROR);
                }
                return node;
            }
            throw error(status, method, uri, bytes);
        } finally {
            response.close();
        }
    }

    /**
     * 同时检查声明长度和实际流式累计长度，防止缺失或伪造 Content-Length 绕过响应体限制。
     */
    private byte[] read(ClientHttpResponse response) throws IOException {
        long contentLength = response.getHeaders().getContentLength();
        if (contentLength > maxResponseBytes) {
            throw new KmsResponseTooLargeException(SimpleKmsClientConstant.MESSAGE_RESPONSE_TOO_LARGE);
        }
        try (InputStream input = response.getBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[SimpleKmsClientConstant.RESPONSE_BUFFER_BYTES];
            int count;
            long total = 0;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > maxResponseBytes) {
                    throw new KmsResponseTooLargeException(SimpleKmsClientConstant.MESSAGE_RESPONSE_TOO_LARGE);
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    /**
     * 只从错误对象提取约定的安全诊断字段，绝不将原始响应载荷附加到异常。
     */
    private SimpleKmsClientException error(int status, HttpMethod method, URI uri, byte[] bytes) {
        String message = SimpleKmsClientConstant.MESSAGE_SERVICE_ERROR;
        String requestId = null;
        Instant timestamp = null;
        try {
            JsonNode node = codec.read(bytes);
            if (node != null && node.isObject()) {
                if (node.path(SimpleKmsClientConstant.FIELD_MESSAGE).isTextual()) {
                    message = node.path(SimpleKmsClientConstant.FIELD_MESSAGE).textValue();
                }
                if (node.path(SimpleKmsClientConstant.FIELD_REQUEST_ID).isTextual()) {
                    requestId = node.path(SimpleKmsClientConstant.FIELD_REQUEST_ID).textValue();
                }
                if (node.path(SimpleKmsClientConstant.FIELD_TIMESTAMP).isTextual()) {
                    timestamp = Instant.parse(node.path(SimpleKmsClientConstant.FIELD_TIMESTAMP).textValue());
                }
            }
        } catch (RuntimeException ignored) {
            message = SimpleKmsClientConstant.MESSAGE_ERROR_RESPONSE;
        }
        return errorMapper.map(status, method.name(), uri.getPath(), message, requestId, timestamp);
    }
}
