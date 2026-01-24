package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;

import java.util.List;
import java.util.Map;

/**
 * Client Management Service
 *
 * @author surezzzzzz
 */
public interface ClientManagementService {

    /**
     * 创建平台级AKSK
     *
     * @param clientName 客户端名称
     * @return Client信息（包含明文SecretKey，仅在创建时返回）
     */
    ClientInfoResponse createPlatformClient(String clientName);

    /**
     * 创建平台级AKSK
     *
     * @param clientName 客户端名称
     * @param scopes 权限范围
     * @return Client信息（包含明文SecretKey，仅在创建时返回）
     */
    ClientInfoResponse createPlatformClient(String clientName, List<String> scopes);

    /**
     * 创建用户级AKSK
     *
     * @param ownerUserId 所属用户ID
     * @param ownerUsername 所属用户名
     * @param clientName  客户端名称
     * @return Client信息（包含明文SecretKey，仅在创建时返回）
     */
    ClientInfoResponse createUserClient(String ownerUserId, String ownerUsername, String clientName);

    /**
     * 创建用户级AKSK
     *
     * @param ownerUserId 所属用户ID
     * @param ownerUsername 所属用户名
     * @param clientName  客户端名称
     * @param scopes 权限范围
     * @return Client信息（包含明文SecretKey，仅在创建时返回）
     */
    ClientInfoResponse createUserClient(String ownerUserId, String ownerUsername, String clientName, List<String> scopes);

    /**
     * 删除Client
     *
     * @param clientId 客户端ID
     */
    void deleteClient(String clientId);

    /**
     * 根据clientId查询Client
     *
     * @param clientId 客户端ID
     * @return Client信息（包含AccessKey和SecretKey）
     */
    ClientInfoResponse getClientById(String clientId);

    /**
     * 根据ID列表批量查询Client
     * 使用SQL IN查询,避免N+1问题,性能优化版本
     *
     * @param clientIds 客户端ID列表
     * @return 客户端ID到客户端信息的映射
     */
    Map<String, ClientInfoResponse> batchGetClientsByIds(List<String> clientIds);

    /**
     * 分页查询Client列表（支持过滤条件）
     *
     * @param ownerUserId 所属用户ID（可选）
     * @param type 客户端类型（可选）
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页后的Client列表
     */
    PageResponse<ClientInfoResponse> listClients(String ownerUserId, String type, Integer page, Integer size);

    /**
     * 重新生成SecretKey
     *
     * @param clientId 客户端ID
     * @return 新的SecretKey
     */
    String regenerateSecretKey(String clientId);

    /**
     * 同步用户权限（批量更新用户的所有用户级AKSK的scope）
     *
     * @param ownerUserId 所属用户ID
     * @param scopes 新的权限列表
     * @return 更新的Client数量
     */
    int syncUserScopes(String ownerUserId, List<String> scopes);

    /**
     * 禁用客户端
     *
     * @param clientId 客户端ID
     */
    void disableClient(String clientId);

    /**
     * 启用客户端
     *
     * @param clientId 客户端ID
     */
    void enableClient(String clientId);
}
