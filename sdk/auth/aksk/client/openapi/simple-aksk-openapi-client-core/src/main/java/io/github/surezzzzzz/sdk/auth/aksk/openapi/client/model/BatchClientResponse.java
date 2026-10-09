package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.Map;

/**
 * Client 批量查询响应（listClients 携带 clientIds 时的信封形态；key 为 clientId）
 *
 * @author surezzzzzz
 */
public class BatchClientResponse {

    /**
     * Client 明细映射（key = clientId；未命中或无权限的标识不出现在映射中）
     */
    private Map<String, ClientInfoResponse> clients;

    public Map<String, ClientInfoResponse> getClients() {
        return clients;
    }

    public void setClients(Map<String, ClientInfoResponse> clients) {
        this.clients = clients;
    }
}
