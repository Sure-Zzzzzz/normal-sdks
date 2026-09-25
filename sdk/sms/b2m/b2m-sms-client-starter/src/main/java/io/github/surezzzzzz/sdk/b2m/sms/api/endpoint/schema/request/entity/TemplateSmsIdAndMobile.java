package io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * B2M 模板短信收件条目：手机号+模板变量+自定义追踪 ID（平台协议映射）。
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSmsIdAndMobile {

    private String mobile;
    private String customSmsId;
    private Map<String, String> content;
}
