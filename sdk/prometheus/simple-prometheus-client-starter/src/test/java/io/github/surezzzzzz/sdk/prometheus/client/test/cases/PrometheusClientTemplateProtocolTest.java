package io.github.surezzzzzz.sdk.prometheus.client.test.cases;

import io.github.surezzzzzz.sdk.prometheus.client.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.client.exception.PrometheusClientException;
import io.github.surezzzzzz.sdk.prometheus.client.model.QueryInstantResponse;
import io.github.surezzzzzz.sdk.prometheus.client.model.QueryRangeResponse;
import io.github.surezzzzzz.sdk.prometheus.client.template.PrometheusClientTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.*;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.xerial.snappy.Snappy;
import prometheus.Remote;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prometheus Client 协议边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class PrometheusClientTemplateProtocolTest {

    private static final String TARGET_KEY = "protocol-fixture";
    private static final String PROMQL = "up";
    private static final int SUCCESS_STATUS = 200;
    private static final int WRITE_SUCCESS_STATUS = 204;

    @Test
    void writeBuildsRemoteWriteRequestWithoutClientAuthentication() throws Exception {
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(WRITE_SUCCESS_STATUS, ""));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);
        Remote.WriteRequest writeRequest = Remote.WriteRequest.newBuilder().build();

        client.write(TARGET_KEY, writeRequest);

        PrometheusRouteRequest request = route.getLastRequest();
        log.info("验证 Remote Write 请求路径、协议 header 和压缩正文");
        assertEquals(TARGET_KEY, route.getLastTargetKey());
        assertEquals(PrometheusRouteHttpMethod.POST, request.getMethod());
        assertEquals("/api/v1/write", request.getRelativePath());
        assertTrue(request.getQueryParameters().isEmpty());
        assertEquals(3, request.getHeaders().size());
        assertEquals("content-type", request.getHeaders().get(0).getName());
        assertEquals("application/x-protobuf", request.getHeaders().get(0).getValue());
        assertEquals("content-encoding", request.getHeaders().get(1).getName());
        assertEquals("snappy", request.getHeaders().get(1).getValue());
        assertEquals("x-prometheus-remote-write-version", request.getHeaders().get(2).getName());
        assertEquals("0.1.0", request.getHeaders().get(2).getValue());
        assertFalse(request.getHeaders().stream()
                .anyMatch(header -> "authorization".equals(header.getName())));
        assertArrayEquals(writeRequest.toByteArray(), Snappy.uncompress(request.getBody()));
        assertEquals(1, route.getCallCount());
    }

    @Test
    void queryAndRangeQueryPreserveParameterOrderAndUseLocaleIndependentSeconds() {
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(SUCCESS_STATUS, vectorResponse()));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);

        synchronized (Locale.class) {
            Locale originalLocale = Locale.getDefault();
            try {
                Locale.setDefault(Locale.GERMANY);
                QueryInstantResponse instant = client.query(TARGET_KEY, PROMQL,
                        Instant.ofEpochSecond(1L, 123456789L));
                PrometheusRouteRequest instantRequest = route.getLastRequest();
                log.info("验证即时查询参数顺序和固定小数格式");
                assertEquals("success", instant.getStatus());
                assertEquals("/api/v1/query", instantRequest.getRelativePath());
                assertEquals(2, instantRequest.getQueryParameters().size());
                assertParameter(instantRequest.getQueryParameters().get(0), "query", PROMQL);
                assertParameter(instantRequest.getQueryParameters().get(1), "time", "1.123");

                route.setResponse(response(SUCCESS_STATUS, matrixResponse()));
                QueryRangeResponse range = client.queryRange(TARGET_KEY, PROMQL,
                        Instant.ofEpochSecond(2L, 125999999L),
                        Instant.ofEpochSecond(3L, 999999999L), 15);
                PrometheusRouteRequest rangeRequest = route.getLastRequest();
                log.info("验证范围查询参数顺序和固定小数格式");
                assertEquals("success", range.getStatus());
                assertEquals("/api/v1/query_range", rangeRequest.getRelativePath());
                assertEquals(4, rangeRequest.getQueryParameters().size());
                assertParameter(rangeRequest.getQueryParameters().get(0), "query", PROMQL);
                assertParameter(rangeRequest.getQueryParameters().get(1), "start", "2.125");
                assertParameter(rangeRequest.getQueryParameters().get(2), "end", "3.999");
                assertParameter(rangeRequest.getQueryParameters().get(3), "step", "15");

                route.setResponse(response(SUCCESS_STATUS, vectorResponse()));
                client.query(TARGET_KEY, PROMQL, Instant.ofEpochSecond(-2L, 876543211L));
                assertParameter(route.getLastRequest().getQueryParameters().get(1), "time", "-1.123");
            } finally {
                Locale.setDefault(originalLocale);
            }
        }
    }

    @Test
    void queryWithoutTimeOnlySendsQueryParameter() {
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(SUCCESS_STATUS, vectorResponse()));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);

        client.query(TARGET_KEY, PROMQL, null);

        List<PrometheusRouteParameter> parameters = route.getLastRequest().getQueryParameters();
        log.info("验证未指定时间时只发送 query 参数");
        assertEquals(1, parameters.size());
        assertParameter(parameters.get(0), "query", PROMQL);
    }

    @Test
    void queryParsesVectorAndMatrixButRejectsUnsupportedResultShapes() {
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(SUCCESS_STATUS, vectorResponse()));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);

        QueryInstantResponse vector = client.query(TARGET_KEY, PROMQL, null);
        assertEquals("vector", vector.getData().getResultType());
        assertEquals(1, vector.getData().getResult().size());
        assertEquals("up", vector.getData().getResult().get(0).getMetric().get("__name__"));
        assertEquals(2, vector.getData().getResult().get(0).getValue().size());
        assertEquals(Double.valueOf(42.5D), vector.getData().getResult().get(0).getValue().get(1));

        route.setResponse(response(SUCCESS_STATUS, matrixResponse()));
        QueryRangeResponse matrix = client.queryRange(TARGET_KEY, PROMQL,
                Instant.ofEpochSecond(1L), Instant.ofEpochSecond(2L), 1);
        assertEquals("matrix", matrix.getData().getResultType());
        assertEquals(1, matrix.getData().getResult().size());
        assertEquals("up", matrix.getData().getResult().get(0).getMetric().get("__name__"));
        assertEquals(2, matrix.getData().getResult().get(0).getValues().size());
        assertEquals(Double.valueOf(43.0D), matrix.getData().getResult().get(0).getValues().get(1).get(1));

        route.setResponse(response(SUCCESS_STATUS,
                "{\"status\":\"success\",\"data\":{\"resultType\":\"scalar\",\"result\":1}}"));
        PrometheusClientException scalar = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        route.setResponse(response(SUCCESS_STATUS,
                "{\"status\":\"success\",\"data\":{\"resultType\":\"string\",\"result\":\"text\"}}"));
        PrometheusClientException string = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        log.info("验证 1.0.0 仅承诺 vector/matrix 响应模型");
        assertEquals(ErrorCode.RESPONSE_PARSE_FAILED, scalar.getErrorCode());
        assertEquals(ErrorCode.RESPONSE_PARSE_FAILED, string.getErrorCode());
    }

    @Test
    void emptySuccessfulResultsAreAccepted() {
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(SUCCESS_STATUS, emptyVectorResponse()));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);

        assertTrue(client.query(TARGET_KEY, PROMQL, null).getData().getResult().isEmpty());
        route.setResponse(response(SUCCESS_STATUS, emptyMatrixResponse()));
        assertTrue(client.queryRange(TARGET_KEY, PROMQL,
                        Instant.ofEpochSecond(1L), Instant.ofEpochSecond(2L), 1)
                .getData().getResult().isEmpty());
    }

    @Test
    void statusAndResponseStructureFailuresUseStableClientErrors() {
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(SUCCESS_STATUS, vectorResponse()));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);

        route.setResponse(response(SUCCESS_STATUS,
                "{\"status\":\"fixture-status-secret\",\"errorType\":\"bad_data\",\"error\":\"invalid\"}"));
        PrometheusClientException statusFailure = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        route.setResponse(response(SUCCESS_STATUS, ""));
        PrometheusClientException emptyBody = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        route.setResponse(response(SUCCESS_STATUS, "not-json"));
        PrometheusClientException malformed = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        route.setResponse(response(SUCCESS_STATUS, "{\"status\":\"success\"}"));
        PrometheusClientException missingData = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        route.setResponse(response(SUCCESS_STATUS,
                "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":{}}}"));
        PrometheusClientException invalidResult = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        route.setResponse(response(SUCCESS_STATUS,
                "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{},\"value\":[1]}]}}"));
        PrometheusClientException invalidSample = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        log.info("验证 Prometheus 业务失败和响应结构失败的错误分层");
        assertEquals(ErrorCode.QUERY_FAILED, statusFailure.getErrorCode());
        assertFalse(statusFailure.getMessage().contains("fixture-status-secret"));
        assertEquals(ErrorCode.RESPONSE_PARSE_FAILED, emptyBody.getErrorCode());
        assertEquals(ErrorCode.RESPONSE_PARSE_FAILED, malformed.getErrorCode());
        assertFalse(malformed.getMessage().contains("not-json"));
        assertNull(malformed.getCause());
        assertEquals(ErrorCode.RESPONSE_PARSE_FAILED, missingData.getErrorCode());
        assertEquals(ErrorCode.RESPONSE_PARSE_FAILED, invalidResult.getErrorCode());
        assertEquals(ErrorCode.RESPONSE_PARSE_FAILED, invalidSample.getErrorCode());
    }

    @Test
    void httpStatusesMapToOperationErrorsAndRedirectIsRejected() {
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(302, "redirect"));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);

        PrometheusClientException redirect = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        route.setResponse(response(400, "query-rejected"));
        PrometheusClientException queryFailure = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        route.setResponse(response(500, "write-rejected"));
        PrometheusClientException writeFailure = assertThrows(PrometheusClientException.class,
                () -> client.write(TARGET_KEY, Remote.WriteRequest.getDefaultInstance()));
        log.info("验证 3xx、查询 4xx 和写入 5xx 的错误码映射");
        assertEquals(ErrorCode.UNEXPECTED_REDIRECT, redirect.getErrorCode());
        assertEquals(ErrorCode.QUERY_FAILED, queryFailure.getErrorCode());
        assertEquals(ErrorCode.WRITE_REJECTED, writeFailure.getErrorCode());
    }

    @Test
    void errorResponseDoesNotExposeBodyAndRouteExceptionPassesThroughWithoutRetry() {
        String body = "fixture-response-" + repeat("x", 600);
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(503, body));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);

        PrometheusClientException failure = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        log.info("验证错误响应仅暴露受控元数据");
        assertTrue(failure.getMessage().contains(
                "responseBodyBytes=" + body.getBytes(StandardCharsets.UTF_8).length));
        assertFalse(failure.getMessage().contains("fixture-response-"));
        assertNull(failure.getCause());

        PrometheusRouteException routeException =
                new PrometheusRouteException("PROMETHEUS_ROUTE_TEST", "受控 Route 异常");
        route.setException(routeException);
        PrometheusRouteException actual = assertThrows(PrometheusRouteException.class,
                () -> client.query(TARGET_KEY, PROMQL, null));
        log.info("验证 Route 异常原样透传且 Client 只调用一次");
        assertSame(routeException, actual);
        assertEquals(2, route.getCallCount());
    }

    @Test
    void invalidArgumentsAreRejectedBeforeRouteExchange() {
        CapturingRouteTemplate route = new CapturingRouteTemplate(response(SUCCESS_STATUS, vectorResponse()));
        PrometheusClientTemplate client = new PrometheusClientTemplate(route);

        PrometheusClientException nullWrite = assertThrows(PrometheusClientException.class,
                () -> client.write(TARGET_KEY, null));
        PrometheusClientException blankQuery = assertThrows(PrometheusClientException.class,
                () -> client.query(TARGET_KEY, "  ", null));
        PrometheusClientException nullTime = assertThrows(PrometheusClientException.class,
                () -> client.queryRange(TARGET_KEY, PROMQL, null, Instant.now(), 1));
        PrometheusClientException invalidStep = assertThrows(PrometheusClientException.class,
                () -> client.queryRange(TARGET_KEY, PROMQL, Instant.EPOCH, Instant.EPOCH, 0));
        PrometheusClientException invertedTime = assertThrows(PrometheusClientException.class,
                () -> client.queryRange(TARGET_KEY, PROMQL, Instant.ofEpochSecond(2), Instant.ofEpochSecond(1), 1));
        log.info("验证非法输入在 Route 调用前被拒绝");
        assertEquals(ErrorCode.REQUEST_ILLEGAL, nullWrite.getErrorCode());
        assertEquals(ErrorCode.REQUEST_ILLEGAL, blankQuery.getErrorCode());
        assertEquals(ErrorCode.REQUEST_ILLEGAL, nullTime.getErrorCode());
        assertEquals(ErrorCode.REQUEST_ILLEGAL, invalidStep.getErrorCode());
        assertEquals(ErrorCode.REQUEST_ILLEGAL, invertedTime.getErrorCode());
        assertEquals(0, route.getCallCount());
    }

    private void assertParameter(PrometheusRouteParameter parameter, String name, String value) {
        assertEquals(name, parameter.getName());
        assertEquals(value, parameter.getValue());
    }

    private PrometheusRouteResponse response(int statusCode, String body) {
        return PrometheusRouteResponse.of(statusCode, Collections.<PrometheusRouteHeader>emptyList(),
                body.getBytes(StandardCharsets.UTF_8));
    }

    private String vectorResponse() {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{\"__name__\":\"up\",\"job\":\"fixture\"},\"value\":[1.123,\"42.5\"]}]},\"ignored\":true}";
    }

    private String matrixResponse() {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[{\"metric\":{\"__name__\":\"up\",\"job\":\"fixture\"},\"values\":[[2.125,\"42.5\"],[3.999,\"43\"]]}]}}";
    }

    private String emptyVectorResponse() {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";
    }

    private String emptyMatrixResponse() {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}";
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static final class CapturingRouteTemplate extends PrometheusRouteTemplate {

        private PrometheusRouteResponse response;
        private RuntimeException exception;
        private String lastTargetKey;
        private PrometheusRouteRequest lastRequest;
        private int callCount;

        private CapturingRouteTemplate(PrometheusRouteResponse response) {
            super(null, null);
            this.response = response;
        }

        @Override
        public PrometheusRouteResponse exchange(String targetKey, PrometheusRouteRequest request) {
            callCount++;
            lastTargetKey = targetKey;
            lastRequest = request;
            if (exception != null) {
                throw exception;
            }
            return response;
        }

        private void setResponse(PrometheusRouteResponse response) {
            this.response = response;
            this.exception = null;
        }

        private void setException(RuntimeException exception) {
            this.exception = exception;
        }

        private String getLastTargetKey() {
            return lastTargetKey;
        }

        private PrometheusRouteRequest getLastRequest() {
            return lastRequest;
        }

        private int getCallCount() {
            return callCount;
        }
    }
}
