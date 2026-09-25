package io.github.surezzzzzz.sdk.b2m.sms.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.b2m.sms.annotation.SmsComponent;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.SendTemplateSmsRequest;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.SmsSingleRequest;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.entity.TemplateSmsIdAndMobile;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response.entity.DecompressData;
import io.github.surezzzzzz.sdk.b2m.sms.configuration.SmsProperties;
import io.github.surezzzzzz.sdk.b2m.sms.constant.ErrorCode;
import io.github.surezzzzzz.sdk.b2m.sms.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.b2m.sms.constant.SmsConstant;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsConfigurationException;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsException;
import io.github.surezzzzzz.sdk.b2m.sms.model.SmsSendResult;
import io.github.surezzzzzz.sdk.b2m.sms.support.SmsCryptoHelper;
import io.github.surezzzzzz.sdk.b2m.sms.support.SmsGzipHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * B2M 短信门面：单条/模板两个业务入口，组装差异在入口，传输管道（序列化→压缩→加密→POST→解密→解压→解析）统一一条。
 * 失败语义两分：SDK 故障（配置/加密/通信）抛 {@code SmsException}；平台业务结果进 {@code SmsSendResult}。
 * 日志不打手机号（跨层追踪用 customSmsId），自维护 ObjectMapper 禁注入容器实例。
 *
 * @author surezzzzzz
 */
@Slf4j
@SmsComponent
public class SmsClient {

    /**
     * SDK 自维护的 JSON 序列化器（隔离铁律：不注入、不复用应用容器 ObjectMapper）。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate smsRestTemplate;
    private final SmsProperties smsProperties;
    private final byte[] keyBytes;
    private final Charset charset;

    public SmsClient(@Qualifier(SmsConstant.BEAN_SMS_REST_TEMPLATE) RestTemplate smsRestTemplate,
                     SmsProperties smsProperties) {
        if (!StringUtils.hasText(smsProperties.getAppId()) || !StringUtils.hasText(smsProperties.getSecretKey())) {
            throw new SmsConfigurationException(ErrorCode.CONFIG_REQUIRED_MISSING,
                    ErrorMessage.CONFIG_REQUIRED_MISSING);
        }
        this.smsRestTemplate = smsRestTemplate;
        this.smsProperties = smsProperties;
        this.keyBytes = SmsCryptoHelper.requireValidKey(smsProperties.getSecretKey(), smsProperties.getEncode());
        this.charset = Charset.forName(smsProperties.getEncode());
        log.info("B2M短信客户端装配: templateUrl={}, gzip={}, algorithm={}",
                smsProperties.getTemplateUrl(), smsProperties.isGzip(), smsProperties.getAlgorithm());
    }

    /**
     * 发送单条短信（customSmsId 默认随机）。
     *
     * @param phone   手机号（E.164）
     * @param content 短信内容
     * @return 发送结果（平台业务结果不抛异常）
     */
    public SmsSendResult sendSingleSms(String phone, String content) {
        return sendSingleSms(phone, content, randomCustomSmsId());
    }

    /**
     * 发送单条短信（customSmsId 由调用方指定，跨层日志关联用）。
     *
     * @param phone       手机号（E.164）
     * @param content     短信内容
     * @param customSmsId 自定义追踪 ID
     * @return 发送结果
     */
    public SmsSendResult sendSingleSms(String phone, String content, String customSmsId) {
        SmsSingleRequest request = SmsSingleRequest.builder()
                .content(content)
                .customSmsId(customSmsId)
                .extendedCode(null)
                .mobile(phone)
                .timerTime(null)
                .build();
        return deliver(request, smsProperties.getSingleUrl(), SmsConstant.TEMPLATE_SINGLE, customSmsId, false);
    }

    /**
     * 发送模板变量短信（customSmsId 默认随机）。
     *
     * @param templateId B2M 平台模板编号
     * @param phone      手机号（E.164）
     * @param variables  模板变量
     * @return 发送结果（平台业务结果不抛异常）
     */
    public SmsSendResult sendTemplateSms(String templateId, String phone, Map<String, String> variables) {
        return sendTemplateSms(templateId, phone, variables, randomCustomSmsId());
    }

    /**
     * 发送模板变量短信（customSmsId 由调用方指定，跨层日志关联用）。
     *
     * @param templateId  B2M 平台模板编号
     * @param phone       手机号（E.164）
     * @param variables   模板变量
     * @param customSmsId 自定义追踪 ID
     * @return 发送结果
     */
    public SmsSendResult sendTemplateSms(String templateId, String phone, Map<String, String> variables,
                                         String customSmsId) {
        Map<String, String> safeVariables = variables == null ? new HashMap<String, String>() : variables;
        TemplateSmsIdAndMobile sms = TemplateSmsIdAndMobile.builder()
                .mobile(phone)
                .customSmsId(customSmsId)
                .content(safeVariables)
                .build();
        SendTemplateSmsRequest request = SendTemplateSmsRequest.builder()
                .templateId(templateId)
                .smses(new TemplateSmsIdAndMobile[]{sms})
                .extendedCode(null)
                .requestTime(System.currentTimeMillis())
                .requestValidPeriod(smsProperties.getValidPeriod())
                .timerTime(null)
                .build();
        return deliver(request, smsProperties.getTemplateUrl(), templateId, customSmsId, true);
    }

    /**
     * 统一传输管道：序列化→（压缩）→加密→POST→解密→（解压）→解析回执。
     *
     * @param request     平台报文
     * @param url         端点
     * @param templateId  模板标识（单条为 "single"，日志用）
     * @param customSmsId 追踪 ID（日志用）
     * @return 发送结果
     */
    private SmsSendResult deliver(Object request, String url, String templateId, String customSmsId,
                                  boolean templateMode) {
        long startAt = System.currentTimeMillis();
        byte[] plain;
        try {
            plain = OBJECT_MAPPER.writeValueAsBytes(request);
        } catch (Exception exception) {
            throw new SmsException(ErrorCode.COMM_REQUEST_SERIALIZE_FAILED,
                    ErrorMessage.COMM_REQUEST_SERIALIZE_FAILED, exception);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(SmsConstant.HEADER_APP_ID, smsProperties.getAppId());
        headers.set(SmsConstant.HEADER_ENCODE, smsProperties.getEncode());
        byte[] body = plain;
        if (smsProperties.isGzip()) {
            headers.set(SmsConstant.HEADER_GZIP, SmsConstant.HEADER_GZIP_ON);
            body = SmsGzipHelper.compress(plain);
        }
        body = SmsCryptoHelper.encrypt(body, keyBytes, smsProperties.getAlgorithm());
        log.debug("B2M发送请求: templateId={}, customSmsId={}", templateId, customSmsId);
        ResponseEntity<byte[]> response;
        try {
            response = smsRestTemplate.postForEntity(url, new HttpEntity<byte[]>(body, headers), byte[].class);
        } catch (RestClientException exception) {
            throw new SmsException(ErrorCode.COMM_REQUEST_FAILED, "B2M短信平台通信失败", exception);
        }
        log.debug("B2M收到响应: status={}, costMs={}, customSmsId={}",
                response.getStatusCodeValue(), System.currentTimeMillis() - startAt, customSmsId);
        SmsSendResult result = parseResponse(response, customSmsId, templateMode);
        log.debug("B2M发送完成: success={}, smsId={}, customSmsId={}",
                result.isSuccess(), result.getResultCode(), customSmsId);
        return result;
    }

    /**
     * 响应解析：非 2xx=通信异常；解密失败（密钥/算法故障）=SmsException；
     * 2xx 且回执解析成功=成功；解压或 JSON 解析失败（平台响应内容异常）=WARN+失败结果——三态可区分。
     * 回执形态按入口分（平台实测 2026-09-25）：模板=DecompressData 数组，单条=单个 DecompressData 对象。
     */
    private SmsSendResult parseResponse(ResponseEntity<byte[]> response, String customSmsId, boolean templateMode) {
        int status = response.getStatusCodeValue();
        if (status > SmsConstant.HTTP_OK_MAX) {
            throw new SmsException(ErrorCode.COMM_REQUEST_FAILED,
                    String.format(ErrorMessage.COMM_REQUEST_FAILED, status));
        }
        byte[] data = response.getBody();
        if (data == null || data.length == 0) {
            return SmsSendResult.builder().success(true).resultCode(null)
                    .message("B2M平台受理成功（空响应体，成功口径以平台文档定值为准）")
                    .customSmsId(customSmsId).build();
        }
        byte[] plain = SmsCryptoHelper.decrypt(data, keyBytes, smsProperties.getAlgorithm());
        try {
            if (smsProperties.isGzip()) {
                plain = SmsGzipHelper.decompress(plain);
            }
            String smsId;
            if (templateMode) {
                List<DecompressData> receipts = OBJECT_MAPPER.readValue(new String(plain, charset),
                        new TypeReference<List<DecompressData>>() {
                        });
                smsId = receipts.isEmpty() ? null : receipts.get(0).getSmsId();
            } else {
                DecompressData receipt = OBJECT_MAPPER.readValue(new String(plain, charset), DecompressData.class);
                smsId = receipt.getSmsId();
            }
            return SmsSendResult.builder().success(true).resultCode(smsId)
                    .message("B2M平台受理成功").customSmsId(customSmsId).build();
        } catch (Exception exception) {
            log.warn("B2M响应解析失败: customSmsId={}, reason={}", customSmsId, exception.getMessage());
            return SmsSendResult.builder().success(false).resultCode(String.valueOf(status))
                    .message(ErrorMessage.COMM_RESPONSE_PARSE_FAILED).customSmsId(customSmsId).build();
        }
    }

    private String randomCustomSmsId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
