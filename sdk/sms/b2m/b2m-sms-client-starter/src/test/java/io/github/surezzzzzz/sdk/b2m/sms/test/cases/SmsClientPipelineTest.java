package io.github.surezzzzzz.sdk.b2m.sms.test.cases;

import io.github.surezzzzzz.sdk.b2m.sms.client.SmsClient;
import io.github.surezzzzzz.sdk.b2m.sms.configuration.SmsProperties;
import io.github.surezzzzzz.sdk.b2m.sms.constant.SmsConstant;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsConfigurationException;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsException;
import io.github.surezzzzzz.sdk.b2m.sms.model.SmsSendResult;
import io.github.surezzzzzz.sdk.b2m.sms.support.SmsCryptoHelper;
import io.github.surezzzzzz.sdk.b2m.sms.support.SmsGzipHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 短信门面管道测试：mock RestTemplate + 真实加解密——全链路不出网。
 * 预制平台响应（加密压缩后的回执 JSON）注入 mock，验证成功/失败/异常三态与构造期校验。
 *
 * @author surezzzzzz
 */
@Slf4j
class SmsClientPipelineTest {

    private static final String KEY = "0123456789abcdef";
    private static final String TRACE_ID = "trace-challenge-0001";

    private RestTemplate restTemplate;
    private SmsClient smsClient;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        smsClient = new SmsClient(restTemplate, properties());
    }

    private SmsProperties properties() {
        SmsProperties properties = new SmsProperties();
        properties.setAppId("test-app");
        properties.setSecretKey(KEY);
        properties.setGzip(true);
        return properties;
    }

    /**
     * 用与 SDK 相同的管道预制平台响应密文（压缩+加密的回执 JSON）。
     */
    private byte[] platformBody(String receiptJson) {
        byte[] compressed = SmsGzipHelper.compress(receiptJson.getBytes(StandardCharsets.UTF_8));
        return SmsCryptoHelper.encrypt(compressed, KEY.getBytes(StandardCharsets.UTF_8),
                SmsConstant.DEFAULT_ALGORITHM);
    }

    @Test
    @DisplayName("模板短信成功：回执解出 smsId，customSmsId 回显")
    void shouldSendTemplateSmsSuccessfully() {
        String receipt = "[{\"smsId\":\"sms-7749\",\"mobile\":\"masked\",\"customSmsId\":\"" + TRACE_ID + "\"}]";
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(platformBody(receipt)));

        SmsSendResult result = smsClient.sendTemplateSms("tpl-001", "+8613800000000",
                Collections.singletonMap("code", "834621"), TRACE_ID);
        log.info("发送结果: success={}, smsId={}, customSmsId={}", result.isSuccess(), result.getResultCode(),
                result.getCustomSmsId());
        assertTrue(result.isSuccess(), "平台 2xx 且回执解析成功应判定成功");
        assertEquals("sms-7749", result.getResultCode(), "成功结果码应为回执 smsId");
        assertEquals(TRACE_ID, result.getCustomSmsId(), "customSmsId 回显供跨层日志关联");
    }

    @Test
    @DisplayName("请求组装断言：捕获请求体解密解压，templateId/手机号/变量/customSmsId/有效期进报文")
    void shouldAssembleTemplateRequestCorrectly() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(platformBody("[]")));

        smsClient.sendTemplateSms("tpl-009", "+8613800000000",
                Collections.singletonMap("code", "834621"), TRACE_ID);

        org.mockito.ArgumentCaptor<org.springframework.http.HttpEntity<byte[]>> captor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.http.HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate).postForEntity(
                eq("http://bjksmtn.b2m.cn/inter/sendTemplateVariableSMS"), captor.capture(),
                eq(byte[].class));
        byte[] body = captor.getValue().getBody();
        byte[] plain = SmsCryptoHelper.decrypt(body, KEY.getBytes(StandardCharsets.UTF_8),
                SmsConstant.DEFAULT_ALGORITHM);
        plain = SmsGzipHelper.decompress(plain);
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(new String(plain, StandardCharsets.UTF_8));
        log.info("组装的请求报文: {}", node.toString());
        assertEquals("tpl-009", node.get("templateId").asText());
        assertEquals("+8613800000000", node.get("smses").get(0).get("mobile").asText());
        assertEquals(TRACE_ID, node.get("smses").get(0).get("customSmsId").asText());
        assertEquals("834621", node.get("smses").get(0).get("content").get("code").asText());
        org.junit.jupiter.api.Assertions.assertTrue(node.get("requestValidPeriod").asInt() > 0,
                "有效期应随配置进报文");
    }

    @Test
    @DisplayName("gzip=false 分支：响应仅加密，成功路径同样解出回执")
    void shouldSendSuccessfullyWithoutGzip() {
        SmsProperties properties = properties();
        properties.setGzip(false);
        SmsClient noGzipClient = new SmsClient(restTemplate, properties);
        byte[] encrypted = SmsCryptoHelper.encrypt("{\"smsId\":\"sms-2002\"}".getBytes(StandardCharsets.UTF_8),
                KEY.getBytes(StandardCharsets.UTF_8), SmsConstant.DEFAULT_ALGORITHM);
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(encrypted));

        SmsSendResult result = noGzipClient.sendSingleSms("+8613800000000", "内容", TRACE_ID);
        log.info("gzip=false 结果: success={}, smsId={}", result.isSuccess(), result.getResultCode());
        assertTrue(result.isSuccess());
        assertEquals("sms-2002", result.getResultCode());
    }

    @Test
    @DisplayName("单条短信成功：默认随机 customSmsId")
    void shouldSendSingleSmsWithRandomTraceId() {
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(platformBody("{\"smsId\":\"sms-1001\"}")));

        SmsSendResult result = smsClient.sendSingleSms("+8613800000000", "内容");
        log.info("单条结果: success={}, customSmsId={}", result.isSuccess(), result.getCustomSmsId());
        assertTrue(result.isSuccess());
        assertEquals("sms-1001", result.getResultCode());
        assertFalse(result.getCustomSmsId().isEmpty(), "默认 customSmsId 非空");
    }

    @Test
    @DisplayName("平台 HTTP 500 抛通信异常（SDK 故障），非业务结果")
    void shouldThrowOnHttpError() {
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenReturn(new ResponseEntity<byte[]>(new byte[0], HttpStatus.INTERNAL_SERVER_ERROR));
        SmsException exception = assertThrows(SmsException.class,
                () -> smsClient.sendSingleSms("+8613800000000", "内容", TRACE_ID));
        log.info("HTTP 异常: errorCode={}, message={}", exception.getErrorCode(), exception.getMessage());
        assertEquals("SMS_COMM_001", exception.getErrorCode());
    }

    @Test
    @DisplayName("网络不可达抛通信异常")
    void shouldThrowOnNetworkFailure() {
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenThrow(new RestClientException("connect refused"));
        SmsException exception = assertThrows(SmsException.class,
                () -> smsClient.sendSingleSms("+8613800000000", "内容", TRACE_ID));
        log.info("网络异常: errorCode={}", exception.getErrorCode());
        assertEquals("SMS_COMM_001", exception.getErrorCode());
    }

    @Test
    @DisplayName("2xx 但响应体非法：失败结果对象（三态区分），不抛异常")
    void shouldReturnFailedResultOnGarbageBody() {
        byte[] garbage = SmsCryptoHelper.encrypt("not-json".getBytes(StandardCharsets.UTF_8),
                KEY.getBytes(StandardCharsets.UTF_8), SmsConstant.DEFAULT_ALGORITHM);
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(garbage));

        SmsSendResult result = smsClient.sendTemplateSms("tpl-001", "+8613800000000",
                Collections.singletonMap("code", "834621"), TRACE_ID);
        log.info("解析失败结果: success={}, message={}", result.isSuccess(), result.getMessage());
        assertFalse(result.isSuccess(), "非法响应体应判定失败");
        assertEquals("200", result.getResultCode());
    }

    @Test
    @DisplayName("空响应体：保守按受理成功（口径以平台文档定值）")
    void shouldTreatEmptyBodyAsAccepted() {
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[0]));
        SmsSendResult result = smsClient.sendSingleSms("+8613800000000", "内容", TRACE_ID);
        log.info("空响应体结果: success={}, message={}", result.isSuccess(), result.getMessage());
        assertTrue(result.isSuccess());
        assertEquals(TRACE_ID, result.getCustomSmsId());
    }

    @Test
    @DisplayName("解密失败（错误密钥的响应）抛 SmsException，不进结果对象")
    void shouldThrowWhenDecryptFails() {
        byte[] wrongKeyBody = SmsCryptoHelper.encrypt("[{\"smsId\":\"x\"}]".getBytes(StandardCharsets.UTF_8),
                "0000000000000000".getBytes(StandardCharsets.UTF_8), SmsConstant.DEFAULT_ALGORITHM);
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(wrongKeyBody));
        SmsException exception = assertThrows(SmsException.class,
                () -> smsClient.sendSingleSms("+8613800000000", "内容", TRACE_ID));
        log.info("解密失败: errorCode={}", exception.getErrorCode());
        assertEquals("SMS_CRYPTO_002", exception.getErrorCode(), "解密失败属 SDK 故障应抛");
    }

    @Test
    @DisplayName("构造期校验：appId 缺失启动失败")
    void shouldFailFastWhenAppIdMissing() {
        SmsProperties properties = properties();
        properties.setAppId(null);
        SmsConfigurationException exception = assertThrows(SmsConfigurationException.class,
                () -> new SmsClient(restTemplate, properties));
        log.info("必填缺失: errorCode={}", exception.getErrorCode());
        assertEquals("SMS_CONFIG_001", exception.getErrorCode());
    }

    @Test
    @DisplayName("构造期校验：密钥长度非法启动失败")
    void shouldFailFastWhenKeyLengthInvalid() {
        SmsProperties properties = properties();
        properties.setSecretKey("short");
        SmsConfigurationException exception = assertThrows(SmsConfigurationException.class,
                () -> new SmsClient(restTemplate, properties));
        log.info("密钥长度: errorCode={}", exception.getErrorCode());
        assertEquals("SMS_CONFIG_002", exception.getErrorCode());
    }
}
