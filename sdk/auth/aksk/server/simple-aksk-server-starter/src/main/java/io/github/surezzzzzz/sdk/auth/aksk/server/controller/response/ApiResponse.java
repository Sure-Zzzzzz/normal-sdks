package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.Data;

/**
 * API Response
 * 统一的API响应格式,通过HTTP状态码表示成功/失败,message字段提供详细信息
 *
 * @author surezzzzzz
 */
@Data
public class ApiResponse {
    private String message;

    public ApiResponse(String message) {
        this.message = message;
    }
}
