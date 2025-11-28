package io.github.surezzzzzz.sdk.prometheus.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.prometheus.api.model.request.QueryInstantRequest;
import io.github.surezzzzzz.sdk.prometheus.api.model.request.QueryRangeRequest;
import io.github.surezzzzzz.sdk.prometheus.api.model.response.QueryInstantResponse;
import io.github.surezzzzzz.sdk.prometheus.api.model.response.QueryRangeResponse;
import io.github.surezzzzzz.sdk.prometheus.configuration.PrometheusComponent;
import io.github.surezzzzzz.sdk.prometheus.configuration.PrometheusProperties;
import io.github.surezzzzzz.sdk.retry.task.executor.TaskRetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.xerial.snappy.Snappy;
import prometheus.Remote;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Base64;

/**
 * Prometheus 客户端 - 支持写入和查询，带重试机制
 *
 * @author Sure
 */
@Slf4j
@PrometheusComponent
public class PrometheusClient {

    private CloseableHttpClient writeHttpClient;
    private CloseableHttpClient readHttpClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PrometheusProperties.WriteServer writeServer;

    @Autowired
    private PrometheusProperties.ReadServer readServer;

    @Autowired
    private TaskRetryExecutor retryExecutor;

    @PostConstruct
    public void init() {
        writeHttpClient = buildClient(writeServer.getConnectionMaxTotal(), writeServer.getConnectionMaxPerRoute());
        readHttpClient = buildClient(readServer.getConnectionMaxTotal(), readServer.getConnectionMaxPerRoute());
    }

    private CloseableHttpClient buildClient(int maxTotal, int maxPerRoute) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(maxPerRoute);
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();
    }

    // ==================== 写入 API ====================

    /**
     * 远程写入 Prometheus（带重试）
     */
    public CloseableHttpResponse write(Remote.WriteRequest writeRequest) throws Exception {
        return write(writeRequest, null);
    }

    public CloseableHttpResponse write(Remote.WriteRequest writeRequest, String host) throws Exception {
        return retryExecutor.executeWithRetry(() -> {
            CloseableHttpResponse response = null;
            try {
                byte[] compressed = Snappy.compress(writeRequest.toByteArray());
                HttpPost httpPost = buildWriteRequest(compressed, host);
                response = writeHttpClient.execute(httpPost);

                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 400) {
                    String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                    throw new IOException("Prometheus write failed with status: " + statusCode + ", body: " + body);
                }

                log.debug("Prometheus write success: {}", response.getStatusLine());
                return response;
            } catch (IOException e) {
                if (response != null) {
                    try {
                        response.close();
                    } catch (IOException ex) {
                        log.warn("Failed to close response", ex);
                    }
                }
                log.error("Prometheus write failed", e);
                throw new RuntimeException("Prometheus write failed", e);
            }
        }, 3, 2); // 3次重试，间隔2秒
    }

    private HttpPost buildWriteRequest(byte[] compressed, String host) {
        String url = (StringUtils.isEmpty(host) ? writeServer.getHost() : host) + writeServer.getWriteUri();
        HttpPost httpPost = new HttpPost(url);

        // 设置请求头
        httpPost.setHeader("Content-Type", writeServer.getContentType());
        httpPost.setHeader("Content-Encoding", writeServer.getContentEncoding());
        httpPost.setHeader("X-Prometheus-Remote-Write-Version", writeServer.getXPrometheusRemoteWriteVersion());

        // 添加认证
        addAuthHeader(httpPost, writeServer.getUsername(), writeServer.getPassword());

        // 设置请求体
        httpPost.setEntity(new ByteArrayEntity(compressed));
        return httpPost;
    }

    // ==================== 即时查询 API ====================

    /**
     * 即时查询（带重试）
     */
    public QueryInstantResponse query(String promql) throws Exception {
        return query(promql, null, null);
    }

    public QueryInstantResponse query(QueryInstantRequest request) throws Exception {
        return query(
                request.getQuery(),
                request.getTime() == null ? null : instantToDouble(request.getTime()),
                null
        );
    }

    public QueryInstantResponse query(String promql, String host) throws Exception {
        return query(promql, null, host);
    }

    public QueryInstantResponse query(String promql, Double time, String host) throws Exception {
        return retryExecutor.executeWithRetry(() -> {
            CloseableHttpResponse response = null;
            try {
                HttpGet httpGet = buildQueryRequest(promql, time, host);
                response = readHttpClient.execute(httpGet);

                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");

                if (statusCode >= 400) {
                    throw new IOException("Prometheus query failed with status: " + statusCode + ", body: " + body);
                }

                QueryInstantResponse result = objectMapper.readValue(body, QueryInstantResponse.class);
                log.debug("PrometheusResponse: {}", objectMapper.writeValueAsString(result));
                return result;
            } catch (IOException | URISyntaxException e) {
                log.error("Prometheus query failed: {}", promql, e);
                throw new RuntimeException("Prometheus query failed", e);
            } finally {
                if (response != null) {
                    try {
                        response.close();
                    } catch (IOException e) {
                        log.warn("Failed to close response", e);
                    }
                }
            }
        }, 3, 2);
    }

    private HttpGet buildQueryRequest(String promql, Double time, String host) throws URISyntaxException {
        String url = (StringUtils.isEmpty(host) ? readServer.getHost() : host) + readServer.getQueryUri();

        URIBuilder uriBuilder = new URIBuilder(url)
                .setParameter("query", promql);

        if (time != null) {
            uriBuilder.setParameter("time", String.format("%.3f", time));
        }

        HttpGet httpGet = new HttpGet(uriBuilder.build());
        addAuthHeader(httpGet, readServer.getUsername(), readServer.getPassword());
        return httpGet;
    }

    // ==================== 范围查询 API ====================

    /**
     * 范围查询（带重试）
     */
    public QueryRangeResponse queryRange(String promql, Double start, Double end, Integer step) throws Exception {
        return queryRange(promql, start, end, step, null);
    }

    public QueryRangeResponse queryRange(QueryRangeRequest request) throws Exception {
        // ✅ 添加空值检查
        if (request.getStart() == null || request.getEnd() == null) {
            throw new IllegalArgumentException("start 和 end 不能为 null");
        }

        return queryRange(
                request.getQuery(),
                instantToDouble(request.getStart()),
                instantToDouble(request.getEnd()),
                request.getStep() == null ? 14 : request.getStep(),
                null
        );
    }

    public QueryRangeResponse queryRange(String promql, Double start, Double end, Integer step, String host) throws Exception {
        return retryExecutor.executeWithRetry(() -> {
            CloseableHttpResponse response = null;
            try {
                HttpGet httpGet = buildQueryRangeRequest(promql, start, end, step, host);
                response = readHttpClient.execute(httpGet);

                int statusCode = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");

                if (statusCode >= 400) {
                    throw new IOException("Prometheus range query failed with status: " + statusCode + ", body: " + body);
                }

                QueryRangeResponse result = objectMapper.readValue(body, QueryRangeResponse.class);
                log.debug("PrometheusQueryRangeResponse: {}", objectMapper.writeValueAsString(result));
                return result;
            } catch (IOException | URISyntaxException e) {
                log.error("Prometheus range query failed: {}", promql, e);
                throw new RuntimeException("Prometheus range query failed", e);
            } finally {
                if (response != null) {
                    try {
                        response.close();
                    } catch (IOException e) {
                        log.warn("Failed to close response", e);
                    }
                }
            }
        }, 3, 2);
    }

    private HttpGet buildQueryRangeRequest(String promql, Double start, Double end, Integer step, String host) throws URISyntaxException {
        String url = (StringUtils.isEmpty(host) ? readServer.getHost() : host) + readServer.getQueryRangeUri();

        URIBuilder uriBuilder = new URIBuilder(url)
                .setParameter("query", promql)
                .setParameter("start", String.format("%.3f", start))
                .setParameter("end", String.format("%.3f", end))
                .setParameter("step", step.toString());

        HttpGet httpGet = new HttpGet(uriBuilder.build());
        addAuthHeader(httpGet, readServer.getUsername(), readServer.getPassword());
        return httpGet;
    }

    // ==================== 工具方法 ====================

    /**
     * 添加 Basic Auth 认证头
     */
    private void addAuthHeader(HttpGet httpGet, String username, String password) {
        if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            httpGet.setHeader("Authorization", "Basic " + encodedAuth);
        }
    }

    private void addAuthHeader(HttpPost httpPost, String username, String password) {
        if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            httpPost.setHeader("Authorization", "Basic " + encodedAuth);
        }
    }

    /**
     * Instant 转 Double（Prometheus 时间格式）
     */
    private Double instantToDouble(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant 不能为 null");
        }
        return (double) instant.getEpochSecond() + (double) instant.getNano() / 1_000_000_000.0;
    }
}
