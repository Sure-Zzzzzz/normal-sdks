package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * OAuth2 Registered Client Entity Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface OAuth2RegisteredClientEntityRepository extends JpaRepository<OAuth2RegisteredClientEntity, String> {

    /**
     * 根据clientId查询
     *
     * @param clientId 客户端ID
     * @return 客户端实体
     */
    Optional<OAuth2RegisteredClientEntity> findByClientId(String clientId);

    /**
     * 根据所属用户ID查询
     *
     * @param ownerUserId 所属用户ID
     * @return 客户端实体列表
     */
    @Query("SELECT c FROM OAuth2RegisteredClientEntity c WHERE c.ownerUserId = :ownerUserId ORDER BY c.clientIdIssuedAt DESC")
    List<OAuth2RegisteredClientEntity> findByOwnerUserId(@Param("ownerUserId") String ownerUserId);

    /**
     * 根据所属用户ID分页查询
     *
     * @param ownerUserId 所属用户ID
     * @param pageable    分页参数
     * @return 客户端实体分页结果
     */
    Page<OAuth2RegisteredClientEntity> findByOwnerUserId(String ownerUserId, Pageable pageable);

    /**
     * 根据客户端类型查询
     *
     * @param clientType 客户端类型
     * @return 客户端实体列表
     */
    @Query("SELECT c FROM OAuth2RegisteredClientEntity c WHERE c.clientType = :clientType ORDER BY c.clientIdIssuedAt DESC")
    List<OAuth2RegisteredClientEntity> findByClientType(@Param("clientType") Integer clientType);

    /**
     * 根据客户端类型分页查询
     *
     * @param clientType 客户端类型
     * @param pageable   分页参数
     * @return 客户端实体分页结果
     */
    Page<OAuth2RegisteredClientEntity> findByClientType(Integer clientType, Pageable pageable);

    /**
     * 根据所属用户ID和客户端类型查询
     *
     * @param ownerUserId 所属用户ID
     * @param clientType  客户端类型
     * @return 客户端实体列表
     */
    @Query("SELECT c FROM OAuth2RegisteredClientEntity c WHERE c.ownerUserId = :ownerUserId AND c.clientType = :clientType ORDER BY c.clientIdIssuedAt DESC")
    List<OAuth2RegisteredClientEntity> findByOwnerUserIdAndClientType(@Param("ownerUserId") String ownerUserId, @Param("clientType") Integer clientType);

    /**
     * 判断clientId是否存在
     *
     * @param clientId 客户端ID
     * @return true存在，false不存在
     */
    boolean existsByClientId(String clientId);

    /**
     * 根据搜索关键字查询（搜索clientId或clientName）
     *
     * @param search 搜索关键字
     * @return 客户端实体列表
     */
    @Query("SELECT c FROM OAuth2RegisteredClientEntity c WHERE LOWER(c.clientId) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.clientName) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY c.clientIdIssuedAt DESC")
    List<OAuth2RegisteredClientEntity> findBySearchKeyword(@Param("search") String search);

    /**
     * 根据clientType和搜索关键字查询
     *
     * @param clientType 客户端类型
     * @param search     搜索关键字
     * @return 客户端实体列表
     */
    @Query("SELECT c FROM OAuth2RegisteredClientEntity c WHERE c.clientType = :clientType AND (LOWER(c.clientId) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.clientName) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY c.clientIdIssuedAt DESC")
    List<OAuth2RegisteredClientEntity> findByClientTypeAndSearchKeyword(@Param("clientType") Integer clientType, @Param("search") String search);

    /**
     * 根据clientId列表批量查询
     * 使用IN查询,一次SQL查询所有记录,避免N+1问题
     *
     * @param clientIds clientId列表
     * @return 客户端实体列表
     */
    @Query("SELECT c FROM OAuth2RegisteredClientEntity c WHERE c.clientId IN :clientIds ORDER BY c.clientIdIssuedAt DESC")
    List<OAuth2RegisteredClientEntity> findAllByClientIdIn(@Param("clientIds") List<String> clientIds);
}
