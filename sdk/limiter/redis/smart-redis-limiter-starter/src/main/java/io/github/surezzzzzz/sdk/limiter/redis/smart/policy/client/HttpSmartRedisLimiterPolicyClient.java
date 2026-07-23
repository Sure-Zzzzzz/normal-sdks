package io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.json.SmartRedisLimiterPolicyJsonCodec;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * 基于 RestTemplate 的远程策略 HTTP 客户端
 *
 * @author surezzzzzz
 */
public class HttpSmartRedisLimiterPolicyClient implements SmartRedisLimiterPolicyClient {

    /**
     * 限流器配置
     */
    private final SmartRedisLimiterProperties properties;

    /**
     * 策略 JSON 编解码器
     */
    private final SmartRedisLimiterPolicyJsonCodec jsonCodec;

    /**
     * 私有 HTTP 客户端
     */
    private final RestTemplate restTemplate;

    /**
     * 构造远程策略 HTTP 客户端
     *
     * @param properties 限流器配置
     * @param jsonCodec  策略 JSON 编解码器
     */
    public HttpSmartRedisLimiterPolicyClient(SmartRedisLimiterProperties properties,
                                             SmartRedisLimiterPolicyJsonCodec jsonCodec) {
        this.properties = properties;
        this.jsonCodec = jsonCodec;
        this.restTemplate = new RestTemplate(createRequestFactory(properties));
        this.restTemplate.setErrorHandler(new PassthroughResponseErrorHandler());
    }

    /**
     * 拉取服务完整策略快照
     *
     * @param serviceCode 服务编码
     * @param currentEtag 当前已接受 ETag，无快照时为 null
     * @return 拉取结果
     */
    @Override
    public SmartRedisLimiterPolicyFetchResult fetch(String serviceCode, String currentEtag) {
        URI uri = UriComponentsBuilder
                .fromUriString(properties.getRemotePolicy().getSnapshotUrl())
                .queryParam(SmartRedisLimiterStarterConstant.HTTP_QUERY_PARAM_SERVICE_CODE, serviceCode)
                .build()
                .encode()
                .toUri();
        try {
            return restTemplate.execute(uri, HttpMethod.GET, request -> {
                if (currentEtag != null) {
                    request.getHeaders().set(
                            SmartRedisLimiterStarterConstant.HTTP_HEADER_IF_NONE_MATCH,
                            currentEtag);
                }
                String policyToken = properties.getRemotePolicy().getPolicyToken();
                if (policyToken != null && !policyToken.trim().isEmpty()) {
                    request.getHeaders().set(
                            SmartRedisLimiterStarterConstant.HTTP_HEADER_POLICY_TOKEN,
                            policyToken);
                }
            }, response -> readResponse(response.getRawStatusCode(),
                    response.getHeaders().getFirst(SmartRedisLimiterStarterConstant.HTTP_HEADER_ETAG),
                    response.getHeaders().getContentLength(),
                    response.getBody()));
        } catch (SmartRedisLimiterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_FETCH_FAILED,
                    String.format(ErrorMessage.POLICY_FETCH_FAILED, ex.getMessage()),
                    ex);
        }
    }

    private SmartRedisLimiterPolicyFetchResult readResponse(int statusCode,
                                                            String etag,
                                                            long contentLength,
                                                            InputStream body) {
        if (statusCode == SmartRedisLimiterStarterConstant.HTTP_STATUS_NOT_MODIFIED) {
            return SmartRedisLimiterPolicyFetchResult.notModified();
        }
        if (statusCode != SmartRedisLimiterStarterConstant.HTTP_STATUS_OK) {
            throw responseInvalid(String.format(ErrorMessage.POLICY_HTTP_STATUS_INVALID, statusCode));
        }
        if (etag == null || etag.trim().isEmpty()) {
            throw responseInvalid(ErrorMessage.POLICY_ETAG_REQUIRED);
        }
        long maxResponseBytes = properties.getRemotePolicy().getMaxResponseBytes();
        if (contentLength > maxResponseBytes) {
            throw responseInvalid(String.format(
                    ErrorMessage.POLICY_RESPONSE_TOO_LARGE,
                    maxResponseBytes));
        }
        SmartRedisLimiterPolicySnapshot snapshot = jsonCodec.decode(
                new LimitedInputStream(body, maxResponseBytes));
        return SmartRedisLimiterPolicyFetchResult.fetched(etag, snapshot);
    }

    private ClientHttpRequestFactory createRequestFactory(SmartRedisLimiterProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getRemotePolicy().getConnectTimeoutMillis().intValue());
        factory.setReadTimeout(properties.getRemotePolicy().getReadTimeoutMillis().intValue());
        return factory;
    }

    private SmartRedisLimiterException responseInvalid(String message) {
        return new SmartRedisLimiterException(
                ErrorCode.POLICY_RESPONSE_INVALID,
                message);
    }

    /**
     * 将全部 HTTP 状态交给策略客户端统一分类
     */
    private static final class PassthroughResponseErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) {
            return false;
        }

        @Override
        public void handleError(ClientHttpResponse response) {
            // 不提前抛异常，由响应提取器统一处理状态码。
        }
    }

    /**
     * 读取过程中强制限制字节数的输入流
     */
    private static final class LimitedInputStream extends FilterInputStream {

        private final long maxBytes;
        private long consumed;

        private LimitedInputStream(InputStream inputStream, long maxBytes) {
            super(inputStream);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                increaseConsumed(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                increaseConsumed(read);
            }
            return read;
        }

        private void increaseConsumed(long count) throws IOException {
            consumed += count;
            if (consumed > maxBytes) {
                throw new IOException(String.format(
                        ErrorMessage.POLICY_RESPONSE_TOO_LARGE,
                        maxBytes));
            }
        }
    }
}
