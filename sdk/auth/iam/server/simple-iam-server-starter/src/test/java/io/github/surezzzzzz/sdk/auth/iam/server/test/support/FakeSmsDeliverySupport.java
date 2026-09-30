package io.github.surezzzzzz.sdk.auth.iam.server.test.support;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.SmsDeliveryProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 走查演示用假投递实现：deliver 只打日志不发真短信（挑战链路完整可演示，验证码输出在日志）。
 * 仅存在于 test 源集，随 runIamLocal 演示装配；生产装配走 simple-iam-sms-b2m-adapter-starter。装配真实 adaptor 时须以 fake-sms-delivery=false 关闭本实现（并存会被启动校验拒绝）。
 *
 * @author surezzzzzz
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "io.github.surezzzzzz.sdk.auth.iam.server.test.fake-sms-delivery",
        havingValue = "true", matchIfMissing = true)
public class FakeSmsDeliverySupport implements SmsDeliveryProvider {

    @Override
    public void deliver(String phone, String code, String purpose, int expiresInSeconds) {
        log.warn("[演示投递] phone=****{}, code={}, purpose={}, ttl={}s（假实现,不发真短信）",
                phone.substring(phone.length() - 4), code, purpose, expiresInSeconds);
    }

    @Override
    public List<String> supportedRegions() {
        return Collections.singletonList("+86");
    }
}
