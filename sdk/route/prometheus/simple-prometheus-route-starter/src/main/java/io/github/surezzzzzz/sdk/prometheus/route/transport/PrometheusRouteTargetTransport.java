package io.github.surezzzzzz.sdk.prometheus.route.transport;

import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.constant.PrometheusRouteHeaderName;
import io.github.surezzzzzz.sdk.prometheus.route.constant.SimplePrometheusRouteConstant;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 单个 target 私有 HTTP 资源。
 *
 * @author surezzzzzz
 */
final class PrometheusRouteTargetTransport implements PrometheusRouteTransport {

    private final URI baseUri;
    private final CloseableHttpClient httpClient;
    private final PrometheusRouteAuthenticationType authenticationType;
    private final String username;
    private final String password;
    private final String token;
    private final int maxResponseBodyBytes;

    PrometheusRouteTargetTransport(URI baseUri, CloseableHttpClient httpClient,
                                   PrometheusRouteAuthenticationType authenticationType, String username,
                                   String password, String token, int maxResponseBodyBytes) {
        this.baseUri = baseUri;
        this.httpClient = httpClient;
        this.authenticationType = authenticationType;
        this.username = username;
        this.password = password;
        this.token = token;
        this.maxResponseBodyBytes = maxResponseBodyBytes;
    }

    /**
     * 在固定 target 内执行受控请求并返回资源无关的响应快照。
     *
     * @param request Route 请求
     * @return 响应快照
     */
    public PrometheusRouteResponse exchange(PrometheusRouteRequest request) {
        validateHeaders(request.getHeaders());
        URI uri = PrometheusRouteUriFactory.create(baseUri, request.getRelativePath(), request.getQueryParameters());
        HttpUriRequest httpRequest = buildRequest(request, uri);
        injectHeaders(httpRequest, request.getHeaders());
        try {
            return execute(httpRequest);
        } catch (PrometheusRouteException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PrometheusRouteException(ErrorCode.REQUEST_EXECUTION_FAILED,
                    ErrorMessage.REQUEST_EXECUTION_FAILED);
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private HttpUriRequest buildRequest(PrometheusRouteRequest request, URI uri) {
        if (request.getMethod() == PrometheusRouteHttpMethod.GET) {
            return new HttpGet(uri);
        }
        HttpPost post = new HttpPost(uri);
        byte[] body = request.getBody();
        if (body != null) {
            post.setEntity(new ByteArrayEntity(body));
        }
        return post;
    }

    private void injectHeaders(HttpUriRequest httpRequest, List<PrometheusRouteHeader> headers) {
        for (PrometheusRouteHeader header : headers) {
            httpRequest.addHeader(header.getName(), header.getValue());
        }
        if (authenticationType == PrometheusRouteAuthenticationType.BASIC) {
            String basic = username + ":" + password;
            httpRequest.setHeader("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        } else if (authenticationType == PrometheusRouteAuthenticationType.BEARER) {
            httpRequest.setHeader("Authorization", "Bearer " + token);
        }
    }

    private PrometheusRouteResponse execute(HttpUriRequest request) throws IOException {
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            byte[] body = entity == null ? new byte[0] : readBody(entity);
            List<PrometheusRouteHeader> headers = new ArrayList<PrometheusRouteHeader>();
            for (org.apache.http.Header header : response.getAllHeaders()) {
                headers.add(new PrometheusRouteHeader(header.getName(), header.getValue()));
            }
            return PrometheusRouteResponse.of(response.getStatusLine().getStatusCode(), headers, body);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private byte[] readBody(HttpEntity entity) throws IOException {
        if (entity.getContentLength() > maxResponseBodyBytes) {
            throw new PrometheusRouteException(ErrorCode.RESPONSE_BODY_EXCEEDS_LIMIT,
                    ErrorMessage.RESPONSE_BODY_EXCEEDS_LIMIT);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(maxResponseBodyBytes, SimplePrometheusRouteConstant.RESPONSE_BUFFER_BYTES));
        java.io.InputStream input = entity.getContent();
        byte[] buffer = new byte[SimplePrometheusRouteConstant.RESPONSE_BUFFER_BYTES];
        int total = 0;
        try {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if ((long) total + count > maxResponseBodyBytes) {
                    throw new PrometheusRouteException(ErrorCode.RESPONSE_BODY_EXCEEDS_LIMIT,
                            ErrorMessage.RESPONSE_BODY_EXCEEDS_LIMIT);
                }
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private void validateHeaders(List<PrometheusRouteHeader> headers) {
        for (PrometheusRouteHeader header : headers) {
            if (PrometheusRouteHeaderName.FORBIDDEN_REQUEST_HEADERS.contains(header.getName())) {
                throw new PrometheusRouteException(ErrorCode.REQUEST_ILLEGAL,
                        ErrorMessage.REQUEST_ILLEGAL);
            }
        }
    }
}
