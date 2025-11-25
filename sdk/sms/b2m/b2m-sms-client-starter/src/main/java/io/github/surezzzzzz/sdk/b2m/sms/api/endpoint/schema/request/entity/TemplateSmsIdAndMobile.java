package io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSmsIdAndMobile {

    private String mobile;
    private String customSmsId;
    private Map<String, String> content;
}
