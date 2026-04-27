package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.BatchRevokeResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;

/**
 * Token Management Service
 *
 * @author surezzzzzz
 */
public interface TokenManagementService {

    /**
     * 查询Token列表（支持过滤、搜索、分页）- 只查询MySQL
     *
     * @param request 查询参数
     * @return Token信息分页响应
     */
    PageResponse<TokenInfoResponse> queryTokens(TokenQueryRequest request);

    /**
     * 查询Redis中的Token列表（支持状态过滤和分页）
     *
     * @param status Token状态过滤（可选）
     * @param page   页码（从1开始）
     * @param size   每页大小
     * @return Token信息分页响应
     */
    PageResponse<TokenInfoResponse> queryRedisTokens(TokenInfo.TokenStatus status, int page, int size);

    /**
     * 获取Token详情 - 只从MySQL查询
     *
     * @param id 授权ID
     * @return Token信息
     */
    TokenInfoResponse getTokenById(String id);

    /**
     * 撤销 Token（调用 /oauth2/revoke，token 立即失效但记录保留）
     *
     * @param id 授权ID
     */
    void revokeToken(String id);

    /**
     * 删除Token（先撤销，再同时删除MySQL和Redis中的数据）
     *
     * @param id 授权ID
     */
    void deleteToken(String id);

    /**
     * 清理过期Token（同时清理MySQL和Redis）
     *
     * @return 删除的Token数量
     */
    int deleteExpiredTokens();

    /**
     * 获取Token统计信息
     *
     * @return 统计信息
     */
    TokenStatisticsResponse getStatistics();

    /**
     * 批量撤销指定 Client 下所有活跃 Token
     *
     * @param clientId 客户端 ID（AKSK 格式，如 AKP...）
     * @return 撤销结果，含本次实际撤销数量
     * @throws ClientException clientId 为空时抛 TOKEN_001；client 不存在时抛 CLIENT_002
     */
    BatchRevokeResponse revokeAllByClientId(String clientId);
}
