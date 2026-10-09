package io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client;

import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant.SimpleAkskOpenApiClientConstant;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AKSK Server 管理 OpenAPI 契约的 Feign 接口（认证由 AKSK 底座元注解自动挂接，20 端点）。
 *
 * <p>标注底座 {@code @AkskClientFeignClient}：宿主启用 {@code @EnableFeignClients} 扫描本包后，
 * Authorization 头由底座令牌链自动注入。目标地址直接引用底座
 * {@code io.github.surezzzzzz.sdk.auth.aksk.client.server-url}（管理 API 与 Token 端点同进程，
 * 无分址场景），API 基路径由 core 契约常量固定为 /api。</p>
 *
 * <p>方法面与 core 的 {@code AkskOpenApiClient} 一一对应（独立注解接口，不 implements——与 KMS
 * 的 KmsClient/KmsFeignClient 平行关系一致）。非 2xx 响应抛 FeignException 保留服务端状态码，
 * 本模块不包装异常、不自动重试。clientSecret 仅出现在创建/轮换响应中且只返回一次，调用方必须
 * 立即落受保护配置，禁止写入日志或异常消息。</p>
 *
 * @author surezzzzzz
 */
@AkskClientFeignClient(name = "aksk-openapi",
        url = SimpleAkskOpenApiClientConstant.SERVER_URL_PLACEHOLDER,
        path = SimpleAkskOpenApiClientConstant.API_BASE_PATH)
public interface AkskOpenApiFeignClient {

    // ==================== Client 管理（7） ====================

    /**
     * 创建 Client（平台级 AKP 或用户级 AKU；clientSecret 仅此一次返回）。
     *
     * @param request 创建请求
     * @return Client 标识与密钥
     */
    @PostMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT)
    CreateClientResponse createClient(@RequestBody CreateClientRequest request);

    /**
     * 分页查询 Client 列表（可选参数缺省时省略——传 null 即不发送）。
     *
     * @param clientIds   按 Client 标识批量过滤（上限 100）
     * @param ownerUserId 按归属用户过滤
     * @param type        按类型过滤（platform / user）
     * @param page        页码
     * @param size        每页数量
     * @return 分页结果
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT)
    PageResponse<ClientInfoResponse> listClients(
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_OWNER_USER_ID) String ownerUserId,
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_TYPE) String type,
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_PAGE) Integer page,
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_SIZE) Integer size);

    /**
     * 按 Client 标识批量查询（上限 100；返回 clients 键值对信封，非分页形态）。
     *
     * @param clientIds Client 标识列表（上限 100）
     * @return 批量结果（key = clientId）
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT)
    BatchClientResponse listClientsByClientIds(
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_IDS) List<String> clientIds);

    /**
     * 查询单个 Client 详情。
     *
     * @param clientId Client 标识
     * @return Client 详情
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT + "/{clientId}")
    ClientInfoResponse getClient(@PathVariable(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId);

    /**
     * 更新 Client（PATCH；body 含 name 即改名，enabled 即启停）。
     *
     * @param clientId Client 标识
     * @param request  更新请求（可选字段省略语义）
     * @return 操作结果
     */
    @PatchMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT + "/{clientId}")
    ApiResponse updateClient(@PathVariable(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId,
                             @RequestBody UpdateClientRequest request);

    /**
     * 轮换 Client Secret（新 clientSecret 仅此一次返回）。
     *
     * @param clientId              Client 标识
     * @param resetAccessTokenCache 是否同步重置访问令牌缓存
     * @return 新密钥
     */
    @PutMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT
            + "/{clientId}/secret")
    ResetSecretResponse rotateSecret(
            @PathVariable(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId,
            @RequestParam(value = SimpleAkskOpenApiClientConstant.PARAM_RESET_ACCESS_TOKEN_CACHE,
                    defaultValue = "true") boolean resetAccessTokenCache);

    /**
     * 删除 Client。
     *
     * @param clientId Client 标识
     */
    @DeleteMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT + "/{clientId}")
    void deleteClient(@PathVariable(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId);

    /**
     * 按归属用户批量同步 scopes。
     *
     * @param ownerUserId 归属用户标识
     * @return 同步结果
     */
    @PatchMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT)
    SyncScopesResponse syncUserScopes(
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_OWNER_USER_ID) String ownerUserId);

    // ==================== 应用授权（5，接入自动化核心） ====================

    /**
     * 为 Client 创建应用授权。
     *
     * @param clientId Client 标识（query 参数）
     * @param request  授权内容
     * @return 授权详情
     */
    @PostMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_AUTHORIZATION)
    ApplicationAuthorizationResponse createAuthorization(
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId,
            @RequestBody ApplicationAuthorizationRequest request);

    /**
     * 分页查询应用授权列表。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_AUTHORIZATION)
    PageResponse<ApplicationAuthorizationResponse> listAuthorizations(
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_PAGE) Integer page,
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_SIZE) Integer size);

    /**
     * 查询单个 Client 的应用授权。
     *
     * @param clientId Client 标识
     * @return 授权详情
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_AUTHORIZATION + "/{clientId}")
    ApplicationAuthorizationResponse getAuthorization(
            @PathVariable(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId);

    /**
     * 完整替换 Client 的应用授权（事务性撤销该 Client 全部活跃 Token）。
     *
     * @param clientId Client 标识
     * @param request  授权内容（admitted=true 即准入）
     * @return 授权详情
     */
    @PutMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_AUTHORIZATION + "/{clientId}")
    ApplicationAuthorizationResponse replaceAuthorization(
            @PathVariable(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId,
            @RequestBody ApplicationAuthorizationRequest request);

    /**
     * 撤销 Client 的应用授权（事务性撤销该 Client 全部活跃 Token）。
     *
     * @param clientId Client 标识
     */
    @PostMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_AUTHORIZATION + "/{clientId}/revoke")
    void revokeAuthorization(@PathVariable(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId);

    // ==================== Token 管理（8） ====================

    /**
     * 分页查询 Token 列表（MySQL 侧）。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN)
    PageResponse<TokenInfoResponse> listTokens(
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_PAGE) Integer page,
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_SIZE) Integer size);

    /**
     * 分页查询 Token 列表（Redis 侧）。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN + "/redis")
    PageResponse<TokenInfoResponse> listRedisTokens(
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_PAGE) Integer page,
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_SIZE) Integer size);

    /**
     * 查询单个 Token 详情。
     *
     * @param id Token 标识
     * @return Token 详情
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN + "/{id}")
    TokenInfoResponse getToken(@PathVariable("id") String id);

    /**
     * 撤销 Token（撤销后 introspect 立即 active=false）。
     *
     * @param id Token 标识
     */
    @PostMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN + "/{id}/revoke")
    void revokeToken(@PathVariable("id") String id);

    /**
     * 删除 Token 记录。
     *
     * @param id Token 标识
     */
    @DeleteMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN + "/{id}")
    void deleteToken(@PathVariable("id") String id);

    /**
     * 清理全部过期 Token。
     *
     * @return 清理计数
     */
    @DeleteMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN + "/expired")
    DeleteExpiredResponse deleteExpiredTokens();

    /**
     * Token 统计。
     *
     * @return 统计计数
     */
    @GetMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN + "/statistics")
    TokenStatisticsResponse getTokenStatistics();

    /**
     * 按 Client 批量撤销 Token。
     *
     * @param clientId Client 标识
     * @return 撤销计数
     */
    @DeleteMapping("/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN)
    BatchRevokeResponse revokeTokensByClientId(
            @RequestParam(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID) String clientId);
}
