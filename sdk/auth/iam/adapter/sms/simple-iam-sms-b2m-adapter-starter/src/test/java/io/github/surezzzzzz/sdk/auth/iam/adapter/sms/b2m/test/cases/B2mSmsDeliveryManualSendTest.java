package io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.delivery.B2mSmsDeliveryProvider;
import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.test.B2mSmsDeliveryTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.SmsDeliveryProvider;
import io.github.surezzzzzz.sdk.b2m.sms.configuration.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * B2M 投递 adaptor 真实发送联调（手跑用例）：验证 adaptor 装配→单条直发→平台受理全链路。
 * 运行条件（双闸门）：环境变量 IAM_SMS_E2E=true（类级注解控制，env 不存在即整类跳过）
 * 且 application-local.yml（gitignored，受仓库级忽略规则保护，切勿提交）填有真实
 * appId/secretKey 与目标手机号；两闸门任一不满足自动跳过，全量测试永远安全。
 * 真实送达由目标手机人工确认（短信正文=签名+登录话术，含 6 位验证码）。
 *
 * @author surezzzzzz
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "IAM_SMS_E2E", matches = "true")
@SpringBootTest(classes = B2mSmsDeliveryTestApplication.class)
class B2mSmsDeliveryManualSendTest {

    @Autowired
    private ObjectProvider<SmsDeliveryProvider> deliveryProvider;

    @Autowired
    private ObjectProvider<SmsProperties> smsPropertiesProvider;

    @Autowired
    private Environment environment;

    /**
     * 凭据与目标号就绪判定：b2m 客户端已装配（enable+凭据齐全）且配置了手机号。
     */
    private boolean ready() {
        return smsPropertiesProvider.getIfAvailable() != null
                && environment.getProperty("iam-sms-e2e.phone") != null;
    }

    @Test
    @DisplayName("真实单条直发：adaptor 装配+能力声明+登录验证码投递（送达由手机确认）")
    void shouldDeliverRealLoginCode() {
        assumeTrue(ready(), "请在 application-local.yml 填入 b2m 凭据与 iam-sms-e2e.phone 后手跑");
        SmsDeliveryProvider provider = deliveryProvider.getObject();
        assertTrue(provider instanceof B2mSmsDeliveryProvider, "应装配 B2M 投递实现");
        assertEquals(java.util.Arrays.asList("+86"), provider.supportedRegions(), "能力声明 +86");

        String phone = environment.getProperty("iam-sms-e2e.phone");
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        provider.deliver(phone, code, SimpleIamCoreConstant.SMS_PURPOSE_LOGIN, 300);
        log.info("真实投递已受理: phone=****{}, code 已发送（正文=签名+登录话术）",
                phone.substring(phone.length() - 4));
    }
}
