package io.github.surezzzzzz.sdk.prometheus.route.test.cases;

import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.*;
import io.github.surezzzzzz.sdk.prometheus.route.validator.DefaultPrometheusRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class PrometheusRouteModelTest {

    @Test
    void requestAndResponseDoNotExposeMutableBody() {
        byte[] requestBody = new byte[]{1};
        PrometheusRouteRequest request = new PrometheusRouteRequest(PrometheusRouteHttpMethod.POST, "/api/v1/write",
                Collections.<PrometheusRouteParameter>emptyList(), Collections.<PrometheusRouteHeader>emptyList(), requestBody);
        requestBody[0] = 2;
        log.info("请求正文防御性复制长度: {}", request.getBody().length);
        assertArrayEquals(new byte[]{1}, request.getBody());
        byte[] returnedRequestBody = request.getBody();
        returnedRequestBody[0] = 3;
        log.info("读取请求正文副本后原始长度: {}", request.getBody().length);
        assertArrayEquals(new byte[]{1}, request.getBody());

        byte[] responseBody = new byte[]{4};
        PrometheusRouteResponse response = PrometheusRouteResponse.of(200,
                new ArrayList<PrometheusRouteHeader>(Arrays.asList(new PrometheusRouteHeader("X-Test", "ok"))), responseBody);
        responseBody[0] = 5;
        log.info("响应快照状态码: {}，响应正文长度: {}", response.getStatusCode(), response.getBody().length);
        assertArrayEquals(new byte[]{4}, response.getBody());
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> response.getHeaders().add(new PrometheusRouteHeader("x", "y")));
        log.info("响应 header 集合不可变异常类型: {}", exception.getClass().getSimpleName());
    }

    @Test
    void getRejectsBody() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET,
                        "/api/v1/query", null, null, new byte[]{1}));
        log.info("GET 正文拒绝错误消息: {}", exception.getMessage());
        assertEquals(ErrorMessage.REQUEST_ILLEGAL, exception.getMessage());
    }

    @Test
    void protocolEnumsProvideStableCodes() {
        log.info("HTTP 方法代码数量: {}，认证类型代码数量: {}",
                PrometheusRouteHttpMethod.getAllCodes().length,
                PrometheusRouteAuthenticationType.getAllCodes().length);
        assertEquals(PrometheusRouteHttpMethod.GET, PrometheusRouteHttpMethod.fromCode("get"));
        assertFalse(PrometheusRouteHttpMethod.isValid("PUT"));
        assertEquals("BASIC", PrometheusRouteAuthenticationType.BASIC.toString());
        assertFalse(PrometheusRouteAuthenticationType.isValid("DIGEST"));
    }

    @Test
    void rejectsNullRequestListElement() {
        IllegalArgumentException parameterException = assertThrows(IllegalArgumentException.class,
                () -> new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/api/v1/query",
                        Arrays.asList(new PrometheusRouteParameter("query", "up"), null),
                        Collections.<PrometheusRouteHeader>emptyList(), null));
        log.info("空 query 参数拒绝错误消息: {}", parameterException.getMessage());
        assertEquals(ErrorMessage.REQUEST_ILLEGAL, parameterException.getMessage());
        IllegalArgumentException headerException = assertThrows(IllegalArgumentException.class,
                () -> new PrometheusRouteRequest(PrometheusRouteHttpMethod.GET, "/api/v1/query",
                        Collections.<PrometheusRouteParameter>emptyList(),
                        Arrays.asList(new PrometheusRouteHeader("Accept", "application/json"), null), null));
        log.info("空 header 拒绝错误消息: {}", headerException.getMessage());
        assertEquals(ErrorMessage.REQUEST_ILLEGAL, headerException.getMessage());
    }

    @Test
    void rejectsNonPositiveValidateAfterInactivity() {
        SimplePrometheusRouteProperties properties = new SimplePrometheusRouteProperties();
        properties.setEnable(true);
        SimplePrometheusRouteProperties.TargetConfig target =
                new SimplePrometheusRouteProperties.TargetConfig();
        target.setUrl("http://127.0.0.1:9090");
        target.getHttp().setValidateAfterInactivityMs(0);
        properties.getTargets().put("test-main", target);

        PrometheusRouteException exception = assertThrows(PrometheusRouteException.class,
                () -> new DefaultPrometheusRoutePropertiesValidator().validate(properties));

        log.info("空闲连接校验配置错误码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.TARGET_CONFIGURATION_ILLEGAL, exception.getErrorCode());
        assertEquals(ErrorMessage.TARGET_CONFIGURATION_ILLEGAL, exception.getMessage());
    }

    @Test
    void targetConfigurationToStringDoesNotExposeEndpointOrCredentials() {
        String endpoint = "http://sensitive-endpoint.example.com";
        SimplePrometheusRouteProperties.TargetConfig target =
                new SimplePrometheusRouteProperties.TargetConfig();
        target.setUrl(endpoint);
        target.getAuthentication().setType(PrometheusRouteAuthenticationType.BEARER);
        target.getAuthentication().setToken("sensitive-token");

        String targetText = target.toString();
        log.info("脱敏 target 配置文本长度: {}", targetText.length());
        assertFalse(targetText.contains(endpoint));
        assertFalse(targetText.contains("sensitive-token"));
    }

    @Test
    void configurationExceptionDoesNotExposeTargetOrEndpoint() {
        String targetKey = "internal-target";
        String endpoint = "http://sensitive-endpoint.example.com";
        SimplePrometheusRouteProperties properties = new SimplePrometheusRouteProperties();
        properties.setEnable(true);
        SimplePrometheusRouteProperties.TargetConfig target =
                new SimplePrometheusRouteProperties.TargetConfig();
        target.setUrl(endpoint + "?secret=value");
        properties.getTargets().put(targetKey, target);

        PrometheusRouteException exception = assertThrows(PrometheusRouteException.class,
                () -> new DefaultPrometheusRoutePropertiesValidator().validate(properties));

        log.info("脱敏配置错误码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.TARGET_CONFIGURATION_ILLEGAL, exception.getErrorCode());
        assertEquals(ErrorMessage.TARGET_CONFIGURATION_ILLEGAL, exception.getMessage());
        assertFalse(exception.getMessage().contains(targetKey));
        assertFalse(exception.getMessage().contains(endpoint));
    }
}
