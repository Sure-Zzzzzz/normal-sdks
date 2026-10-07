package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.SmartRedisLimiterManagementClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model.SmartRedisLimiterPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model.SmartRedisLimiterTypedPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.annotation.SmartRedisLimiterManagementClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.configuration.SmartRedisLimiterManagementClientProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.support.SmartRedisLimiterManagementJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 基于 RestTemplate 的限流管理策略客户端
 * <p>传输与认证全部复用底座 {@code akskClientRestTemplate}（连接池、超时、AKSK 令牌头）；
 * 本类只补条件请求头与响应分类，不持有任何凭据或固定 token。底座 RestTemplate 缺失时
 * 启动即报缺 Bean（响亮失败，不静默）。200/304/字节上限/未知字段严格拒绝与既有内嵌客户端语义一致。</p>
 *
 * @author surezzzzzz
 */
@SmartRedisLimiterManagementClientRestTemplateComponent
public class RestTemplateSmartRedisLimiterManagementClient implements SmartRedisLimiterManagementClient {

    /**
     * HTTP 200 状态码
     */
    private static final int HTTP_STATUS_OK = 200;

    /**
     * HTTP 304 状态码
     */
    private static final int HTTP_STATUS_NOT_MODIFIED = 304;

    /**
     * 客户端配置
     */
    private final SmartRedisLimiterManagementClientProperties properties;

    /**
     * 策略快照编解码器
     */
    private final SmartRedisLimiterManagementJsonCodec jsonCodec;

    /**
     * 宿主注入的认证 RestTemplate
     */
    private final RestTemplate restTemplate;

    /**
     * 构造 RestTemplate 策略客户端
     *
     * @param properties             客户端配置
     * @param jsonCodec              策略快照编解码器
     * @param akskClientRestTemplate AKSK 底座预配置 RestTemplate（认证+连接池）
     */
    public RestTemplateSmartRedisLimiterManagementClient(
            SmartRedisLimiterManagementClientProperties properties,
            SmartRedisLimiterManagementJsonCodec jsonCodec,
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate) {
        this.properties = properties;
        this.jsonCodec = jsonCodec;
        this.restTemplate = akskClientRestTemplate;
        this.restTemplate.setErrorHandler(new PassthroughResponseErrorHandler());
    }

    /**
     * 拉取服务完整 v1 三元组策略快照
     *
     * @param serviceCode 服务编码
     * @param currentEtag 当前已接受 ETag，无快照时为 null
     * @return 拉取结果
     */
    @Override
    public SmartRedisLimiterPolicyFetchResult fetchPolicy(String serviceCode, String currentEtag) {
        return executeFetch(properties.getPolicySnapshotUrl(), serviceCode, currentEtag, false);
    }

    /**
     * 拉取服务完整 v2 类型化策略快照
     *
     * @param serviceCode 服务编码
     * @param currentEtag 当前已接受 ETag，无快照时为 null
     * @return 拉取结果
     */
    @Override
    public SmartRedisLimiterTypedPolicyFetchResult fetchTypedPolicy(String serviceCode, String currentEtag) {
        return executeFetch(properties.getTypedPolicySnapshotUrl(), serviceCode, currentEtag, true);
    }

    private <T> T executeFetch(String url, String serviceCode, String currentEtag, boolean typed) {
        if (url == null || url.trim().isEmpty()) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_SNAPSHOT_INVALID,
                    String.format(ErrorMessage.POLICY_SNAPSHOT_INVALID,
                            typed ? "typed-policy-snapshot-url 未配置" : "policy-snapshot-url 未配置"));
        }
        URI uri = UriComponentsBuilder
                .fromUriString(url.trim())
                .queryParam("serviceCode", serviceCode)
                .build()
                .encode()
                .toUri();
        try {
            Object result = restTemplate.execute(uri, HttpMethod.GET, request -> {
                if (currentEtag != null) {
                    request.getHeaders().set("If-None-Match",
                            new String(currentEtag.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                }
            }, response -> typed
                    ? readTypedResponse(response, serviceCode)
                    : readPolicyResponse(response, serviceCode));
            @SuppressWarnings("unchecked")
            T cast = (T) result;
            return cast;
        } catch (SmartRedisLimiterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_SNAPSHOT_INVALID,
                    String.format(ErrorMessage.POLICY_SNAPSHOT_INVALID, ex.getMessage()),
                    ex);
        }
    }

    private SmartRedisLimiterPolicyFetchResult readPolicyResponse(ClientHttpResponse response,
                                                                  String serviceCode) throws IOException {
        int statusCode = response.getRawStatusCode();
        String etag = response.getHeaders().getFirst("ETag");
        if (statusCode == HTTP_STATUS_NOT_MODIFIED) {
            return SmartRedisLimiterPolicyFetchResult.notModified();
        }
        if (statusCode != HTTP_STATUS_OK) {
            throw responseInvalid(statusCode);
        }
        requireEtag(etag);
        SmartRedisLimiterPolicyFetchResult fetched = SmartRedisLimiterPolicyFetchResult.fetched(
                etag, jsonCodec.decodePolicy(limited(response.getBody(), serviceCode)));
        return fetched;
    }

    private SmartRedisLimiterTypedPolicyFetchResult readTypedResponse(ClientHttpResponse response,
                                                                      String serviceCode) throws IOException {
        int statusCode = response.getRawStatusCode();
        String etag = response.getHeaders().getFirst("ETag");
        if (statusCode == HTTP_STATUS_NOT_MODIFIED) {
            return SmartRedisLimiterTypedPolicyFetchResult.notModified();
        }
        if (statusCode != HTTP_STATUS_OK) {
            throw responseInvalid(statusCode);
        }
        requireEtag(etag);
        return SmartRedisLimiterTypedPolicyFetchResult.fetched(
                etag, jsonCodec.decodeTypedPolicy(limited(response.getBody(), serviceCode)));
    }

    private void requireEtag(String etag) {
        if (etag == null || etag.trim().isEmpty()) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_SNAPSHOT_INVALID,
                    String.format(ErrorMessage.POLICY_SNAPSHOT_INVALID, "ETag 缺失"));
        }
    }

    private SmartRedisLimiterException responseInvalid(int statusCode) {
        return new SmartRedisLimiterException(
                ErrorCode.POLICY_SNAPSHOT_INVALID,
                String.format(ErrorMessage.POLICY_SNAPSHOT_INVALID,
                        "快照端点返回非预期状态码 " + statusCode));
    }

    private InputStream limited(InputStream body, String serviceCode) {
        return new LimitedInputStream(body, properties.getMaxResponseBytes());
    }

    /**
     * 将全部 HTTP 状态交给客户端统一分类
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
                throw new IOException("快照响应超过最大字节数上限 " + maxBytes);
            }
        }
    }
}
