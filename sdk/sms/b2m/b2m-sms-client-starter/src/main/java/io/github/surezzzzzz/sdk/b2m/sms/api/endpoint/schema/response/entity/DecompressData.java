package io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * B2M 平台回执条目（smsId/mobile/customSmsId；含手机号明文，禁整体入日志）。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DecompressData {
    private String smsId;
    private String mobile;
    private String customSmsId;
}
