package io.github.surezzzzzz.sdk.auth.aksk.server.controller.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 自助修改 AKU 显示名称的浏览器输入。
 */
@Data
public class UpdateMyAkskClientRequest {

    @NotBlank
    private String clientName;
}
