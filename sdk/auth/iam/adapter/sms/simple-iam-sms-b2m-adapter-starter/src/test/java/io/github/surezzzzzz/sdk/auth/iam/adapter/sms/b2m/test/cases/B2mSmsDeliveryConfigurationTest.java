package io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.configuration.B2mSmsDeliveryConfiguration;
import io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.delivery.B2mSmsDeliveryProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.SmsDeliveryProvider;
import io.github.surezzzzzz.sdk.b2m.sms.client.SmsClient;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsException;
import io.github.surezzzzzz.sdk.b2m.sms.model.SmsSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * B2M 投递装配与投递行为测试（常规运行，零外部依赖）：
 * enable 开关/话术校验/能力声明/三套话术正文与 {code} 替换/平台拒绝不吞/通信异常透传。
 *
 * @author surezzzzzz
 */
class B2mSmsDeliveryConfigurationTest {

    private static final String PHONE = "+8617710290000";
    private static final String PREFIX = "io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m";

    private final List<List<String>> captured = new ArrayList<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(B2mSmsDeliveryConfiguration.class))
            .withBean(SmsClient.class, () -> stubWithCapture(captured));

    /**
     * 替身：Mockito mock 的 SmsClient（默认成功受理并捕获三参入参供正文断言；失败场景由用例二次打桩覆盖）。
     */
    private static SmsClient stubWithCapture(List<List<String>> sink) {
        SmsClient stub = mock(SmsClient.class);
        given(stub.sendSingleSms(anyString(), anyString(), anyString())).willAnswer(invocation -> {
            sink.add(java.util.Arrays.asList(
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            return SmsSendResult.builder().success(true).resultCode("0")
                    .message("ok").customSmsId(invocation.getArgument(2)).build();
        });
        return stub;
    }

    @Test
    @DisplayName("enable=false：能力整体消失，无 SmsDeliveryProvider 装配")
    void shouldNotAssembleWhenDisabled() {
        runner.withPropertyValues(PREFIX + ".enable=false")
                .run(context -> assertThat(context.getBeansOfType(SmsDeliveryProvider.class)).isEmpty());
    }

    @Test
    @DisplayName("话术含【】签名形态：启动失败（签名由独立配置拼接，不可覆盖）")
    void shouldFailFastWhenTextContainsSignatureBracket() {
        runner.withPropertyValues(
                        PREFIX + ".enable=true",
                        PREFIX + ".login-text=【签名】您的验证码{code}")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("B2M投递话术非法");
                });
    }

    @Test
    @DisplayName("enable=true：装配唯一投递实现，能力声明 +86")
    void shouldAssembleWithSupportedRegions() {
        runner.withPropertyValues(PREFIX + ".enable=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsDeliveryProvider.class);
                    assertThat(context.getBean(SmsDeliveryProvider.class)).isInstanceOf(B2mSmsDeliveryProvider.class);
                    assertThat(context.getBean(SmsDeliveryProvider.class).supportedRegions())
                            .isEqualTo(java.util.Arrays.asList("+86"));
                });
    }

    @Test
    @DisplayName("三套话术正文：签名前缀+{code} 替换+customSmsId=purpose")
    void shouldComposeTextPerPurpose() {
        runner.withPropertyValues(
                        PREFIX + ".enable=true",
                        PREFIX + ".signature=【统一认证】")
                .run(context -> {
                    SmsDeliveryProvider provider = context.getBean(SmsDeliveryProvider.class);
                    provider.deliver(PHONE, "654321", SimpleIamCoreConstant.SMS_PURPOSE_LOGIN, 300);
                    provider.deliver(PHONE, "234567", SimpleIamCoreConstant.SMS_PURPOSE_BIND, 300);
                    provider.deliver(PHONE, "345678", SimpleIamCoreConstant.SMS_PURPOSE_FORGOT_PASSWORD, 300);
                    assertThat(captured).containsExactly(
                            java.util.Arrays.asList(PHONE, "【统一认证】您的登录验证码：654321，5分钟内有效，请注意身份保密。",
                                    SimpleIamCoreConstant.SMS_PURPOSE_LOGIN),
                            java.util.Arrays.asList(PHONE, "【统一认证】您正在绑定手机号，验证码：234567，5分钟内有效，请注意身份保密。",
                                    SimpleIamCoreConstant.SMS_PURPOSE_BIND),
                            java.util.Arrays.asList(PHONE, "【统一认证】您正在重置密码，验证码：345678，5分钟内有效，请注意身份保密。",
                                    SimpleIamCoreConstant.SMS_PURPOSE_FORGOT_PASSWORD));
                });
    }

    @Test
    @DisplayName("未知用途：失败关闭，不静默落登录话术（避免发出语义错误的短信）")
    void shouldRejectUnknownPurpose() {
        runner.withPropertyValues(PREFIX + ".enable=true")
                .run(context -> {
                    SmsDeliveryProvider provider = context.getBean(SmsDeliveryProvider.class);
                    assertThatThrownBy(() -> provider.deliver(PHONE, "123456", "unknown-purpose", 300))
                            .isInstanceOf(io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m.exception.B2mSmsDeliveryException.class)
                            .hasMessageContaining("未知短信用途");
                    assertThat(captured).isEmpty();
                });
    }

    @Test
    @DisplayName("平台业务拒绝（success=false 不抛异常）：投递层必须抛出，不吞失败")
    void shouldThrowOnPlatformRejection() {
        runner.withPropertyValues(PREFIX + ".enable=true")
                .run(context -> {
                    SmsClient stub = context.getBean(SmsClient.class);
                    given(stub.sendSingleSms(anyString(), anyString(), anyString()))
                            .willReturn(SmsSendResult.builder()
                                    .success(false).resultCode("EUCP-REJECT")
                                    .message("余额不足").customSmsId("login").build());
                    SmsDeliveryProvider provider = context.getBean(SmsDeliveryProvider.class);
                    assertThatThrownBy(() -> provider.deliver(PHONE, "111111",
                            SimpleIamCoreConstant.SMS_PURPOSE_LOGIN, 300))
                            .isInstanceOf(SmsException.class)
                            .hasMessageContaining("B2M平台拒绝投递")
                            .hasMessageContaining("余额不足");
                });
    }

    @Test
    @DisplayName("通信异常：原样透传，不改写不吞")
    void shouldPropagateCommunicationFailure() {
        runner.withPropertyValues(PREFIX + ".enable=true")
                .run(context -> {
                    SmsClient stub = context.getBean(SmsClient.class);
                    given(stub.sendSingleSms(anyString(), anyString(), anyString()))
                            .willThrow(new SmsException("COMM", "通信失败"));
                    SmsDeliveryProvider provider = context.getBean(SmsDeliveryProvider.class);
                    assertThatThrownBy(() -> provider.deliver(PHONE, "111111",
                            SimpleIamCoreConstant.SMS_PURPOSE_LOGIN, 300))
                            .isInstanceOf(SmsException.class)
                            .hasMessage("通信失败");
                });
    }
}
