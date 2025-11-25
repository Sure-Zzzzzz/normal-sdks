package io.github.surezzzzzz.sdk.b2m.sms.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.SendTemplateSmsRequest;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.SmsSingleRequest;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.entity.TemplateSmsIdAndMobile;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response.SendTemplateSmsResponse;
import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response.entity.DecompressData;
import io.github.surezzzzzz.sdk.b2m.sms.configuration.SmsComponent;
import io.github.surezzzzzz.sdk.b2m.sms.configuration.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/11 16:46
 */
@SmsComponent
@Slf4j
public class SmsClient {

    @Autowired
    @Qualifier("smsRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private SmsProperties smsProperties;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发送单条短信
     *
     * @param phoneNumber
     * @param content     短信内容
     * @throws Exception
     */
    public void sendSingleSms(String phoneNumber, String content) throws Exception {
        SmsSingleRequest smsSingleRequest = SmsSingleRequest.builder()
                .content(content)
                .customSmsId(System.currentTimeMillis() + "")
                .extendedCode(null)
                .mobile(phoneNumber)
                .timerTime(null).build();
        sendSms(smsSingleRequest, smsProperties.getSingleUrl());
    }

    public SendTemplateSmsResponse sendTemplateSms(TemplateSmsIdAndMobile[] customSmsIdAndMobiles, String templateId) throws Exception {
        SendTemplateSmsRequest sendTemplateSmsRequest = SendTemplateSmsRequest.builder()
                .templateId(templateId)
                .smses(customSmsIdAndMobiles)
                .extendedCode(null)
                .requestTime(System.currentTimeMillis())
                .requestValidPeriod(smsProperties.getValidPeriod())
                .timerTime(null).build();
        return sendSms(sendTemplateSmsRequest, smsProperties.getTemplateUrl());
    }

    public SendTemplateSmsResponse sendSms(Object content, String smsUrl) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        String requestJson = objectMapper.writeValueAsString(content);
        log.info("sendSms requestJson:{}", requestJson);
        byte[] bytes = requestJson.getBytes(smsProperties.getEncode());
        if (smsProperties.isGzip()) {
            headers.set("gzip", "on");
            bytes = compress(bytes);
        }
        byte[] paramBytes = encrypt(bytes, smsProperties.getSecretKey().getBytes(), smsProperties.getAlgorithm());
        headers.set("appId", smsProperties.getAppId());
        headers.set("encode", smsProperties.getEncode());
        HttpEntity<byte[]> requestEntity = new HttpEntity<>(paramBytes, headers);
        log.debug("requestEntity:{}", objectMapper.writeValueAsString(requestEntity));
        // 发送POST请求并获取响应
        ResponseEntity<byte[]> responseEntity = restTemplate.postForEntity(smsUrl, requestEntity, byte[].class);
        log.debug("responseEntity:{}", objectMapper.writeValueAsString(responseEntity));
        String decompressDataString = null;
        List<DecompressData> decompressData = new ArrayList<>();
        try {
            if (responseEntity.getStatusCodeValue() == 200) {
                byte[] data = responseEntity.getBody();
                data = decrypt(data, smsProperties.getSecretKey().getBytes(), smsProperties.getAlgorithm());
                if (smsProperties.isGzip()) {
                    data = decompress(data);
                }
                if (data != null) {
                    decompressDataString = new String(data, smsProperties.getEncode());
                    if (smsUrl.equals(smsProperties.getTemplateUrl())) {
                        decompressData = objectMapper.readValue(decompressDataString, new TypeReference<List<DecompressData>>() {
                        });
                    }
                }
            }
        } catch (Exception e) {
            log.debug("decompress data error:{}", e.getMessage());
            e.printStackTrace();
            //do nothing，返回体暂时没用上
        }
        log.info("sendSms response:{}, decompressData:{}", objectMapper.writeValueAsString(responseEntity), decompressDataString);
        return SendTemplateSmsResponse.builder().responseEntity(responseEntity).decompressData(decompressData).build();
    }

    public static byte[] compress(byte[] bytes) throws IOException {
        ByteArrayOutputStream out = null;
        GZIPOutputStream gos = null;
        try {
            out = new ByteArrayOutputStream();
            gos = new GZIPOutputStream(out);
            gos.write(bytes);
            gos.finish();
            gos.flush();
        } finally {
            if (gos != null) {
                gos.close();
            }
            if (out != null) {
                out.close();
            }
        }
        return out.toByteArray();
    }

    public static byte[] decompress(byte[] bytes) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        GZIPInputStream gin = new GZIPInputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count;
        byte[] data = new byte[1024];
        while ((count = gin.read(data, 0, 1024)) != -1) {
            out.write(data, 0, count);
        }
        out.flush();
        out.close();
        gin.close();
        return out.toByteArray();
    }

    public static byte[] encrypt(byte[] content, byte[] password, String algorithm) {
        if (content == null || password == null) return null;
        try {
            Cipher cipher;
            if (algorithm.endsWith("PKCS7Padding")) {
                cipher = Cipher.getInstance(algorithm, "BC");
            } else {
                cipher = Cipher.getInstance(algorithm);
            }
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(password, "AES"));
            return cipher.doFinal(content);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] decrypt(byte[] content, byte[] password, String algorithm) {
        if (content == null || password == null)
            return null;
        try {
            Cipher cipher;
            if (algorithm.endsWith("PKCS7Padding")) {
                cipher = Cipher.getInstance(algorithm, "BC");
            } else {
                cipher = Cipher.getInstance(algorithm);
            }
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(password, "AES"));
            return cipher.doFinal(content);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
