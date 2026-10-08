package io.github.surezzzzzz.sdk.auth.aksk.openapi.client;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.*;

/**
 * AKSK Server 管理 OpenAPI 编程式客户端（20 端点全量契约）。
 *
 * <p>面向接入自动化与运维脚本化：建 Client → 配应用授权 → 准入 → 换 Token 验证一条龙。
 * 所有返回类型均为 client 自有 wire 投影模型，不依赖 server 领域对象。本接口不重试、
 * 不生成幂等键、不管理令牌——出站认证由各传输形态 starter（Feign / RestTemplate）经
 * AKSK 底座挂接；持有管理凭据不等于拥有全部权限，实际可访问的接口与数据由 AKSK Server
 * 的应用授权决定。</p>
 *
 * <p>Secret 保密纪律：{@code createClient} / {@code rotateSecret} 返回的 clientSecret 仅出现一次，
 * 调用方必须立即落受保护配置，不得写入日志、异常消息或仓库。</p>
 *
 * @author surezzzzzz
 */
public interface AkskOpenApiClient {

    // ==================== Client 管理（7） ====================

    /**
     * 创建 Client（平台级 AKP 或用户级 AKU）。
     *
     * @param request 创建请求（type=platform 时 ownerUserId/ownerUsername 省略）
     * @return Client 标识与密钥（clientSecret 仅此一次返回）
     */
    CreateClientResponse createClient(CreateClientRequest request);

    /**
     * 分页查询 Client 列表。
     *
     * @param query 查询条件（可选字段对象；clientIds 上限 100）
     * @return 分页结果
     */
    PageResponse<ClientInfoResponse> listClients(ListClientsQuery query);

    /**
     * 查询单个 Client 详情。
     *
     * @param clientId Client 标识
     * @return Client 详情
     */
    ClientInfoResponse getClient(String clientId);

    /**
     * 更新 Client（PATCH；body 含 name 即改名，enabled 即启停）。
     *
     * @param clientId Client 标识
     * @param request  更新请求（可选字段省略语义）
     * @return 操作结果
     */
    ApiResponse updateClient(String clientId, UpdateClientRequest request);

    /**
     * 轮换 Client Secret。
     *
     * @param clientId              Client 标识
     * @param resetAccessTokenCache 是否同步重置访问令牌缓存
     * @return 新密钥（clientSecret 仅此一次返回）
     */
    ResetSecretResponse rotateSecret(String clientId, boolean resetAccessTokenCache);

    /**
     * 删除 Client。
     *
     * @param clientId Client 标识
     */
    void deleteClient(String clientId);

    /**
     * 按归属用户批量同步 scopes。
     *
     * @param ownerUserId 归属用户标识
     * @return 同步结果
     */
    SyncScopesResponse syncUserScopes(String ownerUserId);

    // ==================== 应用授权（5，接入自动化核心） ====================

    /**
     * 为 Client 创建应用授权。
     *
     * @param clientId Client 标识（query 参数）
     * @param request  授权内容（应用编码、admitted、角色、页面/API 权限码、DATA 文档、清单版本与摘要）
     * @return 授权详情
     */
    ApplicationAuthorizationResponse createAuthorization(String clientId, ApplicationAuthorizationRequest request);

    /**
     * 分页查询应用授权列表。
     *
     * @param page 页码（默认 1）
     * @param size 每页数量（默认 20）
     * @return 分页结果
     */
    PageResponse<ApplicationAuthorizationResponse> listAuthorizations(Integer page, Integer size);

    /**
     * 查询单个 Client 的应用授权。
     *
     * @param clientId Client 标识
     * @return 授权详情
     */
    ApplicationAuthorizationResponse getAuthorization(String clientId);

    /**
     * 完整替换 Client 的应用授权（事务性撤销该 Client 全部活跃 Token；历史 Token 不因替换获得新授权）。
     *
     * @param clientId Client 标识
     * @param request  授权内容（admitted=true 即准入）
     * @return 授权详情
     */
    ApplicationAuthorizationResponse replaceAuthorization(String clientId, ApplicationAuthorizationRequest request);

    /**
     * 撤销 Client 的应用授权（事务性撤销该 Client 全部活跃 Token）。
     *
     * @param clientId Client 标识
     */
    void revokeAuthorization(String clientId);

    // ==================== Token 管理（8） ====================

    /**
     * 分页查询 Token 列表（MySQL 侧）。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    PageResponse<TokenInfoResponse> listTokens(Integer page, Integer size);

    /**
     * 分页查询 Token 列表（Redis 侧）。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    PageResponse<TokenInfoResponse> listRedisTokens(Integer page, Integer size);

    /**
     * 查询单个 Token 详情。
     *
     * @param id Token 标识
     * @return Token 详情
     */
    TokenInfoResponse getToken(String id);

    /**
     * 撤销 Token（撤销后 introspect 立即 active=false）。
     *
     * @param id Token 标识
     */
    void revokeToken(String id);

    /**
     * 删除 Token 记录。
     *
     * @param id Token 标识
     */
    void deleteToken(String id);

    /**
     * 清理全部过期 Token。
     *
     * @return 清理计数
     */
    DeleteExpiredResponse deleteExpiredTokens();

    /**
     * Token 统计。
     *
     * @return 统计计数
     */
    TokenStatisticsResponse getTokenStatistics();

    /**
     * 按 Client 批量撤销 Token。
     *
     * @param clientId Client 标识
     * @return 撤销计数
     */
    BatchRevokeResponse revokeTokensByClientId(String clientId);
}
