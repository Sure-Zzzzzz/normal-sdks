package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.BatchRevokeResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;

/**
 * Token 管理服务。
 *
 * <p>无 {@link DataAccessPlan} 的方法仅供本地受控调用；管理端入口必须使用带数据权限计划的重载，
 * 防止查询或变更越过资源范围约束。
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

    /**
     * 按指定业务原因撤销 Client 下所有活跃 Token。
     *
     * @param clientId 客户端 ID
     * @param cause    撤销原因
     * @return 撤销结果
     */
    BatchRevokeResponse revokeAllByClientId(String clientId, TokenEventCause cause);

    /**
     * 在数据权限计划约束下查询 MySQL Token 列表。
     *
     * @param request 查询条件
     * @param plan    Token 数据权限计划
     * @return 可访问范围内的 Token 分页结果
     */
    PageResponse<TokenInfoResponse> queryTokens(TokenQueryRequest request, DataAccessPlan plan);

    /**
     * 在数据权限计划约束下查询 Redis Token 列表。
     *
     * @param status Token 状态，可为空
     * @param page   页码，从 1 开始
     * @param size   每页大小
     * @param plan   Token 数据权限计划
     * @return 可访问范围内的 Token 分页结果
     */
    PageResponse<TokenInfoResponse> queryRedisTokens(TokenInfo.TokenStatus status, int page, int size,
                                                     DataAccessPlan plan);

    /**
     * 在数据权限计划约束下获取单个 Token 详情。
     *
     * @param id   授权 ID
     * @param plan Token 数据权限计划
     * @return Token 信息
     */
    TokenInfoResponse getTokenById(String id, DataAccessPlan plan);

    /**
     * 在数据权限计划约束下撤销单个 Token。
     *
     * @param id   授权 ID
     * @param plan Token 数据权限计划
     */
    void revokeToken(String id, DataAccessPlan plan);

    /**
     * 在数据权限计划约束下删除单个 Token。
     *
     * @param id   授权 ID
     * @param plan Token 数据权限计划
     */
    void deleteToken(String id, DataAccessPlan plan);

    /**
     * 在数据权限计划约束下清理过期 Token。
     *
     * @param plan Token 数据权限计划
     * @return 删除数量
     */
    int deleteExpiredTokens(DataAccessPlan plan);

    /**
     * 在数据权限计划约束下获取 Token 统计。
     *
     * @param plan Token 数据权限计划
     * @return 可访问范围内的统计信息
     */
    TokenStatisticsResponse getStatistics(DataAccessPlan plan);

    /**
     * 在数据权限计划约束下撤销指定 Client 的全部活跃 Token。
     *
     * @param clientId 客户端 ID
     * @param plan     Token 数据权限计划
     * @return 撤销结果
     */
    BatchRevokeResponse revokeAllByClientId(String clientId, DataAccessPlan plan);

    /**
     * 在数据权限预检通过后，按指定业务原因撤销 Client 下所有活跃 Token。
     *
     * @param clientId 客户端 ID
     * @param plan     Token 数据权限计划
     * @param cause    撤销原因
     * @return 撤销结果
     */
    BatchRevokeResponse revokeAllByClientId(String clientId, DataAccessPlan plan, TokenEventCause cause);

    /**
     * 预检数据权限计划是否覆盖指定 Client 的全部活跃 Token。
     *
     * <p>批量变更前先执行预检，避免部分 Token 已变更后才发现剩余资源无权处理。
     *
     * @param clientId 客户端 ID
     * @param plan     Token 数据权限计划
     */
    void requireAllByClientIdAllowed(String clientId, DataAccessPlan plan);
}
