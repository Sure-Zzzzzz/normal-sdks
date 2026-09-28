package io.github.surezzzzzz.sdk.auth.aksk.server.controller.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 自助创建 inherited AKU 的浏览器输入，不接收 owner、scope 或三权字段。
 */
@Data
public class CreateMyAkskClientRequest {

    @NotNull
    private Long targetApplicationId;

    @NotBlank
    private String clientName;
}
