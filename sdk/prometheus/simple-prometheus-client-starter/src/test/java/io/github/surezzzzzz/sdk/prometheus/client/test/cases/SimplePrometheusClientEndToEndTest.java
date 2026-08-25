package io.github.surezzzzzz.sdk.prometheus.client.test.cases;

import io.github.surezzzzzz.sdk.prometheus.client.model.Metric;
import io.github.surezzzzzz.sdk.prometheus.client.model.QueryInstantResponse;
import io.github.surezzzzzz.sdk.prometheus.client.model.QueryRangeResponse;
import io.github.surezzzzzz.sdk.prometheus.client.template.PrometheusClientTemplate;
import io.github.surezzzzzz.sdk.prometheus.client.test.SimplePrometheusClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import prometheus.Remote;
import prometheus.Types;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple Prometheus Client 双版本 Docker 端到端测试。
 *
 * <p>测试自身通过 Client Remote Write 创建唯一样本，再经同一 Client 查询回读，避免外部造数脚本绕过协议链路。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimplePrometheusClientTestApplication.class)
class SimplePrometheusClientEndToEndTest {

    private static final List<String> TARGET_KEYS =
            Arrays.asList("prometheus-237", "prometheus-245");
    private static final String METRIC_NAME = "simple_prometheus_client_e2e_sample";
    private static final double SAMPLE_VALUE = 42.0D;
    private static final int STEP_SECONDS = 5;
    private static final long POLL_TIMEOUT_SECONDS = 30L;
    private static final long POLL_INTERVAL_MILLIS = 200L;

    @Autowired
    private PrometheusClientTemplate client;

    @Test
    void writeThenInstantAndRangeQueryReachBothPrometheusTargets() {
        String testId = "run_" + UUID.randomUUID().toString();
        Instant sampleTime = Instant.now().minusSeconds(2L);
        String promql = METRIC_NAME + "{client_test_id=\"" + testId + "\"}";

        for (String targetKey : TARGET_KEYS) {
            client.write(targetKey, writeRequest(testId, sampleTime));
            QueryInstantResponse instantResponse = awaitInstant(targetKey, promql, testId);
            QueryRangeResponse rangeResponse = awaitRange(targetKey, promql, sampleTime, testId);

            log.info("验证 target {} 的 write-then-query 结果", targetKey);
            assertInstantResponse(instantResponse, testId);
            assertRangeResponse(rangeResponse, testId);
        }
    }

    private Remote.WriteRequest writeRequest(String testId, Instant sampleTime) {
        Types.Label metricLabel = Types.Label.newBuilder()
                .setName("__name__")
                .setValue(METRIC_NAME)
                .build();
        Types.Label testLabel = Types.Label.newBuilder()
                .setName("client_test_id")
                .setValue(testId)
                .build();
        Types.Sample sample = Types.Sample.newBuilder()
                .setTimestamp(sampleTime.toEpochMilli())
                .setValue(SAMPLE_VALUE)
                .build();
        Types.TimeSeries timeSeries = Types.TimeSeries.newBuilder()
                .addLabels(metricLabel)
                .addLabels(testLabel)
                .addSamples(sample)
                .build();
        return Remote.WriteRequest.newBuilder()
                .addTimeseries(timeSeries)
                .build();
    }

    private QueryInstantResponse awaitInstant(String targetKey, String promql, String testId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            QueryInstantResponse response = client.query(targetKey, promql, Instant.now());
            if (hasInstantResult(response, testId)) {
                return response;
            }
            parkBetweenPolls();
        }
        throw new AssertionError("Prometheus 即时查询在截止时间内未读回本次写入样本");
    }

    private QueryRangeResponse awaitRange(
            String targetKey, String promql, Instant sampleTime, String testId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(POLL_TIMEOUT_SECONDS);
        Instant start = sampleTime.minusSeconds(5L);
        while (System.nanoTime() < deadline) {
            QueryRangeResponse response = client.queryRange(targetKey, promql, start,
                    Instant.now(), STEP_SECONDS);
            if (hasRangeResult(response, testId)) {
                return response;
            }
            parkBetweenPolls();
        }
        throw new AssertionError("Prometheus 范围查询在截止时间内未读回本次写入样本");
    }

    private boolean hasInstantResult(QueryInstantResponse response, String testId) {
        if (response == null || !"success".equals(response.getStatus())
                || response.getData() == null
                || !"vector".equals(response.getData().getResultType())) {
            return false;
        }
        Metric metric = findExpectedMetric(response.getData().getResult(), testId);
        return metric != null && hasExpectedValue(metric.getValue());
    }

    private boolean hasRangeResult(QueryRangeResponse response, String testId) {
        if (response == null || !"success".equals(response.getStatus())
                || response.getData() == null
                || !"matrix".equals(response.getData().getResultType())) {
            return false;
        }
        Metric metric = findExpectedMetric(response.getData().getResult(), testId);
        return metric != null && hasExpectedRangeValue(metric.getValues());
    }

    private Metric findExpectedMetric(List<Metric> metrics, String testId) {
        if (metrics == null) {
            return null;
        }
        for (Metric metric : metrics) {
            if (metric != null && metric.getMetric() != null
                    && METRIC_NAME.equals(metric.getMetric().get("__name__"))
                    && testId.equals(metric.getMetric().get("client_test_id"))) {
                return metric;
            }
        }
        return null;
    }

    private boolean hasExpectedValue(List<Double> sample) {
        return sample != null && sample.size() == 2
                && Double.valueOf(SAMPLE_VALUE).equals(sample.get(1));
    }

    private boolean hasExpectedRangeValue(List<List<Double>> samples) {
        if (samples == null) {
            return false;
        }
        for (List<Double> sample : samples) {
            if (hasExpectedValue(sample)) {
                return true;
            }
        }
        return false;
    }

    private void assertInstantResponse(QueryInstantResponse response, String testId) {
        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
        assertEquals("vector", response.getData().getResultType());
        Metric metric = findExpectedMetric(response.getData().getResult(), testId);
        assertNotNull(metric);
        assertNotNull(metric.getValue());
        assertEquals(2, metric.getValue().size());
        assertEquals(Double.valueOf(SAMPLE_VALUE), metric.getValue().get(1));
    }

    private void assertRangeResponse(QueryRangeResponse response, String testId) {
        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
        assertEquals("matrix", response.getData().getResultType());
        Metric metric = findExpectedMetric(response.getData().getResult(), testId);
        assertNotNull(metric);
        assertNotNull(metric.getValues());
        assertFalse(metric.getValues().isEmpty());
        assertTrue(hasExpectedRangeValue(metric.getValues()));
    }

    private void parkBetweenPolls() {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS));
    }
}
