package io.github.surezzzzzz.sdk.prometheus.route.test.cases;

import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteHeader;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteHttpMethod;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteRequest;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteResponse;
import io.github.surezzzzzz.sdk.prometheus.route.template.PrometheusRouteTemplate;
import io.github.surezzzzzz.sdk.prometheus.route.test.SimplePrometheusRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prometheus Route 双版本端到端验证。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimplePrometheusRouteTestApplication.class)
class PrometheusRouteEndToEndTest {

    @Autowired
    private PrometheusRouteTemplate template;

    @Test
    void reachesBothPinnedPrometheusVersions() {
        PrometheusRouteResponse response237 = template.exchange("prometheus-237", buildInfoRequest());
        log.info("Prometheus 2.37.0 buildinfo status: {}", response237.getStatusCode());
        assertBuildInfo(response237, "2.37.0");

        PrometheusRouteResponse response245 = template.exchange("prometheus-245", buildInfoRequest());
        log.info("Prometheus 2.45.2 buildinfo status: {}", response245.getStatusCode());
        assertBuildInfo(response245, "2.45.2");

        PrometheusRouteResponse query237 = template.exchange("prometheus-237", queryRequest());
        log.info("Prometheus 2.37.0 query status: {}", query237.getStatusCode());
        assertPostQuery(query237);

        PrometheusRouteResponse query245 = template.exchange("prometheus-245", queryRequest());
        log.info("Prometheus 2.45.2 query status: {}", query245.getStatusCode());
        assertPostQuery(query245);
    }

    private PrometheusRouteRequest buildInfoRequest() {
        return new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/api/v1/status/buildinfo",
                Collections.emptyList(), Collections.emptyList(), null);
    }

    private PrometheusRouteRequest queryRequest() {
        return new PrometheusRouteRequest(PrometheusRouteHttpMethod.POST, "/api/v1/query",
                Collections.emptyList(), Collections.singletonList(
                new PrometheusRouteHeader("Content-Type", "application/x-www-form-urlencoded")),
                "query=up".getBytes(StandardCharsets.UTF_8));
    }

    private void assertPostQuery(PrometheusRouteResponse response) {
        assertTrue(response.getStatusCode() == 200,
                "Prometheus query 应返回 HTTP 200，实际为 " + response.getStatusCode());
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"status\":\"success\""), "Prometheus query 应返回 success 状态");
    }

    private void assertBuildInfo(PrometheusRouteResponse response, String expectedVersion) {
        assertEquals(200, response.getStatusCode(), "Prometheus buildinfo 应返回 HTTP 200");
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"version\":\"" + expectedVersion + "\""),
                "Prometheus buildinfo 版本不匹配");
    }
}
