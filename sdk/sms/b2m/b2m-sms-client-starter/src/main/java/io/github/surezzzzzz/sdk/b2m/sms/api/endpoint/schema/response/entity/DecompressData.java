package io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.response.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/12 8:16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DecompressData {
    private String smsId;
    private String mobile;
    private String customSmsId;
}
