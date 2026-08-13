package io.github.surezzzzzz.sdk.prometheus.route.test.cases;

import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteParameter;
import io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteUriFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class PrometheusRouteUriFactoryTest {

    @Test
    void preservesConfiguredBasePathAndRepeatedParameters() {
        URI base = PrometheusRouteUriFactory.normalizeBaseUri("http://127.0.0.1:9090/prometheus/");

        URI uri = PrometheusRouteUriFactory.create(base, "/api/v1/series", Arrays.asList(
                new PrometheusRouteParameter("match[]", "up"),
                new PrometheusRouteParameter("match[]", "{job=\"test\"}")
        ));

        log.info("合并后的 URI path 长度: {}，query 参数数量: {}", uri.getPath().length(), 2);
        assertEquals("/prometheus/api/v1/series", uri.getPath());
        assertEquals("match%5B%5D=up&match%5B%5D=%7Bjob%3D%22test%22%7D", uri.getRawQuery());
    }

    @Test
    void rejectsTraversalAndAbsolutePath() {
        URI base = PrometheusRouteUriFactory.normalizeBaseUri("http://127.0.0.1:9090");

        PrometheusRouteException traversal = assertThrows(PrometheusRouteException.class,
                () -> PrometheusRouteUriFactory.create(base, "/api/../v1/query", null));
        log.info("路径穿越拒绝错误码: {}", traversal.getErrorCode());
        assertEquals(ErrorCode.REQUEST_ILLEGAL, traversal.getErrorCode());

        PrometheusRouteException absolute = assertThrows(PrometheusRouteException.class,
                () -> PrometheusRouteUriFactory.create(base, "//outside.example.com/api", null));
        log.info("绝对路径拒绝错误码: {}", absolute.getErrorCode());
        assertEquals(ErrorCode.REQUEST_ILLEGAL, absolute.getErrorCode());
    }

    @Test
    void requestExceptionDoesNotExposeTargetKey() {
        String targetKey = "internal-target";
        URI base = PrometheusRouteUriFactory.normalizeBaseUri("http://127.0.0.1:9090");

        PrometheusRouteException exception = assertThrows(PrometheusRouteException.class,
                () -> PrometheusRouteUriFactory.create(base, "//outside.example.com/api", null));

        log.info("脱敏请求拒绝错误码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.REQUEST_ILLEGAL, exception.getErrorCode());
        assertEquals(ErrorMessage.REQUEST_ILLEGAL, exception.getMessage());
        assertFalse(exception.getMessage().contains(targetKey));
    }
}
