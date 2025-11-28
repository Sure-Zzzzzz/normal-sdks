package io.github.surezzzzzz.sdk.prometheus.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.prometheus.PrometheusApplication;
import io.github.surezzzzzz.sdk.prometheus.api.model.request.QueryRangeRequest;
import io.github.surezzzzzz.sdk.prometheus.api.model.response.QueryInstantResponse;
import io.github.surezzzzzz.sdk.prometheus.api.model.response.QueryRangeResponse;
import io.github.surezzzzzz.sdk.prometheus.client.PrometheusClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import prometheus.Remote;
import prometheus.Types;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author: Sure.
 * @description Prometheus 客户端测试
 * @Date: 2024/1/19 14:13
 */
@Slf4j
@SpringBootTest(classes = PrometheusApplication.class)
public class PrometheusClientTest {

    @Autowired
    private PrometheusClient prometheusClient;

    @Autowired
    private ObjectMapper objectMapper;

    // 测试用的指标名称
    private static final String TEST_METRIC_NAME = "test_sdk_metric";
    private static final Random random = new Random();

    private static String generateRandomString() {
        int length = 8;
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder randomString = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            randomString.append(characters.charAt(index));
        }
        return randomString.toString();
    }

    @Test
    public void smokeTest() throws Exception {
        log.info("========== 开始完整测试流程 ==========");

        // 1. 写入多个时间点的数据
        String labelValue = writeMultipleDataPoints();

        // 等待数据写入生效（Prometheus 有写入延迟）
        log.info("等待2秒让数据写入生效...");
        Thread.sleep(2000);

        // 2. 即时查询刚写入的数据
        testInstantQuery(labelValue);

        // 3. 范围查询刚写入的数据
        testRangeQuery(labelValue);

        log.info("========== 测试完成 ==========");
    }

    /**
     * 写入多个时间点的数据
     *
     * @return 返回用于查询的 label 值
     */
    private String writeMultipleDataPoints() throws Exception {
        log.info("========== 写入测试数据 ==========");

        String labelValue = generateRandomString();
        Instant now = Instant.now();

        // 写入最近10分钟的数据，每分钟一个点
        for (int i = 10; i >= 0; i--) {
            Instant timestamp = now.minus(i, ChronoUnit.MINUTES);
            double value = 100 + random.nextDouble() * 50; // 100-150之间的随机值

            writeDataPoint(TEST_METRIC_NAME, labelValue, timestamp, value);
            log.info("✅ 写入数据点: time={}, value={}", timestamp, String.format("%.2f", value));
        }

        log.info("✅ 成功写入 11 个数据点，labelValue={}", labelValue);
        return labelValue;
    }

    /**
     * 写入单个数据点
     */
    private void writeDataPoint(String metricName, String labelValue, Instant timestamp, double value) throws Exception {
        // 元数据
        Types.MetricMetadata metricMetadata = Types.MetricMetadata.newBuilder()
                .setType(Types.MetricMetadata.MetricType.GAUGE)
                .setMetricFamilyName(metricName + "_family")
                .setHelp("SDK测试指标")
                .build();

        // 标签
        List<Types.Label> labels = new ArrayList<>();
        labels.add(Types.Label.newBuilder().setName("__name__").setValue(metricName).build());
        labels.add(Types.Label.newBuilder().setName("test_id").setValue(labelValue).build());
        labels.add(Types.Label.newBuilder().setName("env").setValue("test").build());

        // 样本数据
        Types.Sample sample = Types.Sample.newBuilder()
                .setTimestamp(timestamp.toEpochMilli())
                .setValue(value)
                .build();

        Types.TimeSeries timeSeries = Types.TimeSeries.newBuilder()
                .addAllLabels(labels)
                .addSamples(sample)
                .build();

        // 写入
        prometheusClient.write(Remote.WriteRequest.newBuilder()
                .addMetadata(metricMetadata)
                .addTimeseries(timeSeries)
                .build(), null);
    }

    /**
     * 即时查询
     */
    private void testInstantQuery(String labelValue) throws Exception {
        log.info("========== 测试即时查询 ==========");

        // 查询刚写入的数据
        String promql = String.format("%s{test_id=\"%s\"}", TEST_METRIC_NAME, labelValue);
        log.info("查询语句: {}", promql);

        QueryInstantResponse response = prometheusClient.query(promql, null);

        log.info("即时查询结果: {}", objectMapper.writeValueAsString(response));

        if (response.getData() != null && response.getData().getResult() != null) {
            log.info("✅ 查询到 {} 条结果", response.getData().getResult().size());
        } else {
            log.warn("⚠️ 未查询到数据");
        }
    }

    /**
     * 范围查询
     */
    private void testRangeQuery(String labelValue) throws Exception {
        log.info("========== 测试范围查询 ==========");

        Instant now = Instant.now();
        Instant fifteenMinutesAgo = now.minus(15, ChronoUnit.MINUTES);

        String promql = String.format("%s{test_id=\"%s\"}", TEST_METRIC_NAME, labelValue);
        log.info("查询语句: {}", promql);
        log.info("时间范围: {} 到 {}", fifteenMinutesAgo, now);

        // 方式1：使用 QueryRangeRequest
        log.info("--- 方式1：使用 QueryRangeRequest ---");
        QueryRangeResponse response1 = prometheusClient.queryRange(
                QueryRangeRequest.builder()
                        .query(promql)
                        .start(fifteenMinutesAgo)
                        .end(now)
                        .step(60)  // 每60秒一个点
                        .build()
        );
        log.info("范围查询结果1: {}", objectMapper.writeValueAsString(response1));

        // 方式2：使用 Double 时间戳
        log.info("--- 方式2：使用 Double 时间戳 ---");
        QueryRangeResponse response2 = prometheusClient.queryRange(
                promql,
                (double) fifteenMinutesAgo.getEpochSecond(),
                (double) now.getEpochSecond(),
                60
        );
        log.info("范围查询结果2: {}", objectMapper.writeValueAsString(response2));

        if (response1.getData() != null && response1.getData().getResult() != null) {
            log.info("✅ 查询到 {} 条时间序列", response1.getData().getResult().size());
        } else {
            log.warn("⚠️ 未查询到数据");
        }
    }

    /**
     * 测试不同的查询场景
     */
    @Test
    public void testVariousQueryScenarios() throws Exception {
        log.info("========== 测试多种查询场景 ==========");

        // 写入数据
        String labelValue = writeMultipleDataPoints();
        Thread.sleep(2000);

        String promql = String.format("%s{test_id=\"%s\"}", TEST_METRIC_NAME, labelValue);
        Instant now = Instant.now();

        // 场景1：最近5分钟，step=15秒
        testQueryScenario("最近5分钟", promql, now.minus(5, ChronoUnit.MINUTES), now, 15);

        // 场景2：最近10分钟，step=30秒
        testQueryScenario("最近10分钟", promql, now.minus(10, ChronoUnit.MINUTES), now, 30);

        // 场景3：最近15分钟，step=60秒
        testQueryScenario("最近15分钟", promql, now.minus(15, ChronoUnit.MINUTES), now, 60);
    }

    private void testQueryScenario(String description, String promql, Instant start, Instant end, Integer step) throws Exception {
        log.info("--- {} ---", description);

        QueryRangeResponse response = prometheusClient.queryRange(
                QueryRangeRequest.builder()
                        .query(promql)
                        .start(start)
                        .end(end)
                        .step(step)
                        .build()
        );

        if (response.getData() != null && response.getData().getResult() != null) {
            int resultCount = response.getData().getResult().size();
            log.info("✅ {} 查询成功: status={}, 结果数={}",
                    description,
                    response.getStatus(),
                    resultCount
            );

            // 打印每个时间序列的数据点数量
            response.getData().getResult().forEach(result -> {
                if (result.getValues() != null) {
                    log.info("   时间序列有 {} 个数据点", result.getValues().size());
                }
            });
        } else {
            log.warn("⚠️ {} 未查询到数据", description);
        }
    }

    /**
     * 测试聚合查询
     */
    @Test
    public void testAggregationQueries() throws Exception {
        log.info("========== 测试聚合查询 ==========");

        // 写入数据
        String labelValue = writeMultipleDataPoints();
        Thread.sleep(2000);

        Instant now = Instant.now();
        Instant tenMinutesAgo = now.minus(10, ChronoUnit.MINUTES);

        // 测试各种聚合函数
        String[] aggregations = {
                String.format("avg(%s{test_id=\"%s\"})", TEST_METRIC_NAME, labelValue),
                String.format("max(%s{test_id=\"%s\"})", TEST_METRIC_NAME, labelValue),
                String.format("min(%s{test_id=\"%s\"})", TEST_METRIC_NAME, labelValue),
                String.format("sum(%s{test_id=\"%s\"})", TEST_METRIC_NAME, labelValue),
                String.format("rate(%s{test_id=\"%s\"}[5m])", TEST_METRIC_NAME, labelValue)
        };

        for (String promql : aggregations) {
            log.info("--- 测试聚合: {} ---", promql);
            try {
                QueryRangeResponse response = prometheusClient.queryRange(
                        QueryRangeRequest.builder()
                                .query(promql)
                                .start(tenMinutesAgo)
                                .end(now)
                                .step(60)
                                .build()
                );
                log.info("✅ 聚合查询成功: {}", objectMapper.writeValueAsString(response));
            } catch (Exception e) {
                log.error("❌ 聚合查询失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 测试错误场景
     */
    @Test
    public void testErrorScenarios() {
        log.info("========== 测试错误场景 ==========");

        // 测试1：空查询
        try {
            prometheusClient.query("");
            log.error("❌ 空查询应该抛出异常");
        } catch (Exception e) {
            log.info("✅ 空查询正确抛出异常: {}", e.getMessage());
        }

        // 测试2：无效的 PromQL
        try {
            prometheusClient.query("invalid{{{promql");
            log.error("❌ 无效PromQL应该抛出异常");
        } catch (Exception e) {
            log.info("✅ 无效PromQL正确抛出异常: {}", e.getMessage());
        }

        // 测试3：时间范围错误（end < start）
        try {
            Instant now = Instant.now();
            prometheusClient.queryRange(
                    QueryRangeRequest.builder()
                            .query(TEST_METRIC_NAME)
                            .start(now)
                            .end(now.minus(1, ChronoUnit.HOURS))
                            .step(60)
                            .build()
            );
            log.error("❌ 时间范围错误应该抛出异常");
        } catch (Exception e) {
            log.info("✅ 时间范围错误正确抛出异常: {}", e.getMessage());
        }
    }
}
