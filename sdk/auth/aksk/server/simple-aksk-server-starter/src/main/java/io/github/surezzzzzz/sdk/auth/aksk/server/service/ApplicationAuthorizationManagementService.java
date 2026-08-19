package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;

import java.util.List;
import java.util.Map;

/**
 * AKSK 服务主体应用授权管理服务。
 *
 * @author surezzzzzz
 */
public interface ApplicationAuthorizationManagementService {

    ApplicationAuthorizationResponse createLocal(String clientId, ApplicationAuthorizationRequest request);

    ApplicationAuthorizationResponse getLocal(String clientId);

    /**
     * 批量查询本地应用授权投影，用于管理页面展示最小状态。
     *
     * @param clientIds AKSK客户端标识列表
     * @return 以客户端标识为键的授权投影
     */
    Map<String, ApplicationAuthorizationResponse> getLocalByClientIds(List<String> clientIds);

    ApplicationAuthorizationResponse replaceLocal(String clientId, ApplicationAuthorizationRequest request);

    void revokeLocal(String clientId);

    ApplicationAuthorizationResponse create(String clientId, ApplicationAuthorizationRequest request,
                                            DataAccessPlan plan);

    ApplicationAuthorizationResponse get(String clientId, DataAccessPlan plan);

    PageResponse<ApplicationAuthorizationResponse> list(Integer page, Integer size, DataAccessPlan plan);

    ApplicationAuthorizationResponse replace(String clientId, ApplicationAuthorizationRequest request,
                                             DataAccessPlan plan, DataAccessPlan tokenPlan);

    void revoke(String clientId, DataAccessPlan plan, DataAccessPlan tokenPlan);
}
