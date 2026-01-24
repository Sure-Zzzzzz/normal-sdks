package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 批量查询Client响应
 *
 * @author surezzzzzz
 */
@Data
public class BatchClientResponse {
    /**
     * Client信息Map (key=clientId, value=ClientInfo)
     */
    private Map<String, ClientInfoResponse> clients = new HashMap<>();
}
