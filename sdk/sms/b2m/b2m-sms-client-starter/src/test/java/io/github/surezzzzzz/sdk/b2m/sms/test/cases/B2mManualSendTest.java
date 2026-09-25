package io.github.surezzzzzz.sdk.b2m.sms.test.cases;

import io.github.surezzzzzz.sdk.b2m.sms.client.SmsClient;
import io.github.surezzzzzz.sdk.b2m.sms.configuration.SmsProperties;
import io.github.surezzzzzz.sdk.b2m.sms.model.SmsSendResult;
import io.github.surezzzzzz.sdk.b2m.sms.test.SmsTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * B2M 平台真实发送联调（手跑用例）：验证加密/压缩/报文与平台真实互通。
 * 运行条件（三闸门）：环境变量 B2M_SMS_E2E=true（类级注解，env 不存在即整类跳过）；
 * application-local.yml（gitignored）填有真实 appId/secretKey 与测试键 b2m.e2e.phone（目标手机号）；
 * 任一不满足自动跳过，全量测试永远安全。测试类自身不含手机号与凭据，可安全入库。
 *
 * @author surezzzzzz
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "B2M_SMS_E2E", matches = "true")
@SpringBootTest(classes = SmsTestApplication.class)
class B2mManualSendTest {

    @Autowired
    private ObjectProvider<SmsClient> smsClientProvider;

    @Autowired
    private ObjectProvider<SmsProperties> smsPropertiesProvider;

    /**
     * 目标手机号（只存于 gitignored 的 application-local.yml，测试类不含真实号码）。
     */
    @org.springframework.beans.factory.annotation.Value("${b2m.e2e.phone:}")
    private String e2ePhone;

    /**
     * 凭据就绪判定：配置 bean 存在（enable=true 才装配）、必填已填且非占位文案。
     */
    private boolean credentialsReady() {
        SmsProperties properties = smsPropertiesProvider.getIfAvailable();
        return properties != null && properties.isEnable()
                && properties.getAppId() != null && !properties.getAppId().startsWith("在此")
                && properties.getSecretKey() != null && !properties.getSecretKey().startsWith("在此");
    }

    @Test
    @DisplayName("真实发送单条短信（目标号与内容走 local 配置）")
    void shouldSendRealSingleSms() {
        assumeTrue(credentialsReady(), "请在 application-local.yml 填入真实 app-id/secret-key 后手跑");
        SmsClient smsClient = smsClientProvider.getIfAvailable();
        assumeTrue(smsClient != null, "SmsClient 未装配：检查 application-local.yml enable=true");
        log.info("闸门诊断: credentialsReady={}, clientPresent={}, e2ePhoneTail={}", credentialsReady(),
                smsClientProvider.getIfAvailable() != null,
                e2ePhone == null ? "null" : (e2ePhone.trim().isEmpty() ? "empty" : e2ePhone.trim().substring(e2ePhone.trim().length() - 4)));
        assumeTrue(e2ePhone != null && !e2ePhone.trim().isEmpty(), "请在 application-local.yml 配置 b2m.e2e.phone");

        SmsSendResult result = smsClient.sendSingleSms(e2ePhone.trim(), "你好", "manual-single-0001");
        log.info("真实单条发送结果: success={}, smsId={}, message={}, customSmsId={}",
                result.isSuccess(), result.getResultCode(), result.getMessage(), result.getCustomSmsId());
        assertNotNull(result);
        assertTrue(result.isSuccess(), "真实平台应受理成功（失败时先核对凭据与网络）");
    }
}
