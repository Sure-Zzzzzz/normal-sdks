package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Web 登录方式响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebLoginProviderResponse {

    private String code;

    private String name;

    private String type;

    private boolean enabled;

    private String authorizeUrl;

    private String description;

    /**
     * 投递能力声明的区号列表（type=phone 时有值，逗号分隔；其余类型为 null）
     */
    private String supportedRegions;
}
