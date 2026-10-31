package io.github.surezzzzzz.sdk.license.server.configuration;

import io.github.surezzzzzz.sdk.license.server.constant.SimpleLicenseServerConstant;
import javax.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * License 业务执行政策，不参与认证或其他应用的配置。
 *
 * <p>失联签名接管必须由宿主显式声明等待窗口；未配置时禁用 SIGNING 接管。
 * 配置窗口不触发后台重签，管理员仍须携带原行版本、原执行代数和接管理由请求恢复。</p>
 *
 * @author surezzzzzz
 */
@Data
@Validated
@ConfigurationProperties(prefix = SimpleLicenseServerConstant.PROPERTY_PREFIX)
public class LicenseServerProperties {

    /** 距最近一次 KMS 调用达到此秒数后，才允许人工接管失联 SIGNING；null 表示禁用。 */
    @Min(1)
    private Long staleSigningAfterSeconds;
}
