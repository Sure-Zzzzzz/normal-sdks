package io.github.surezzzzzz.sdk.auth.aksk.server.controller.request;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import lombok.Data;

/**
 * Token查询参数Request
 *
 * @author surezzzzzz
 */
@Data
public class TokenQueryRequest {

    /**
     * 客户端ID过滤
     */
    private String clientId;

    /**
     * 客户端类型过滤（1=平台级，2=用户级）
     */
    private Integer clientType;

    /**
     * Token状态过滤
     */
    private TokenInfo.TokenStatus status;

    /**
     * 搜索关键字（按clientId、clientName搜索）
     */
    private String search;

    /**
     * 页码（从1开始）
     */
    private int page = 1;

    /**
     * 每页大小
     */
    private int size = 10;
}
