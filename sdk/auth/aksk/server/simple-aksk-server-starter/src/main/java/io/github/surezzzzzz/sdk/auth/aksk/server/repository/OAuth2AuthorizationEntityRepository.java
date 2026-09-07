package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2AuthorizationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * OAuth2 Authorization Entity Repository
 * 使用JPA Repository访问oauth2_authorization表
 *
 * @author surezzzzzz
 */
@Repository
public interface OAuth2AuthorizationEntityRepository extends JpaRepository<OAuth2AuthorizationEntity, String> {

    /**
     * 分页查询所有授权记录，按签发时间倒序
     *
     * @param pageable 分页参数
     * @return 授权实体分页结果
     */
    @Query("SELECT a FROM OAuth2AuthorizationEntity a WHERE a.accessTokenValue IS NOT NULL "
            + "AND a.accessTokenIssuedAt IS NOT NULL ORDER BY a.accessTokenIssuedAt DESC")
    Page<OAuth2AuthorizationEntity> findAllOrderByIssuedAtDesc(Pageable pageable);

    /**
     * 按注册客户端ID分页查询授权记录
     *
     * @param registeredClientId 注册客户端ID
     * @param pageable           分页参数
     * @return 授权实体分页结果
     */
    @Query("SELECT a FROM OAuth2AuthorizationEntity a WHERE a.registeredClientId = :registeredClientId "
            + "AND a.accessTokenValue IS NOT NULL AND a.accessTokenIssuedAt IS NOT NULL "
            + "ORDER BY a.accessTokenIssuedAt DESC")
    Page<OAuth2AuthorizationEntity> findByRegisteredClientIdOrderByAccessTokenIssuedAtDesc(
            @Param("registeredClientId") String registeredClientId, Pageable pageable);

    /**
     * 按注册客户端ID列表分页查询授权记录
     *
     * @param registeredClientIds 注册客户端ID列表
     * @param pageable            分页参数
     * @return 授权实体分页结果
     */
    @Query("SELECT a FROM OAuth2AuthorizationEntity a WHERE a.registeredClientId IN :registeredClientIds "
            + "AND a.accessTokenValue IS NOT NULL AND a.accessTokenIssuedAt IS NOT NULL "
            + "ORDER BY a.accessTokenIssuedAt DESC")
    Page<OAuth2AuthorizationEntity> findByRegisteredClientIdInOrderByAccessTokenIssuedAtDesc(List<String> registeredClientIds, Pageable pageable);

    /**
     * 查询过期的授权记录（分页）
     *
     * @param now      当前时间
     * @param pageable 分页参数
     * @return 授权实体分页结果
     */
    @Query("SELECT a FROM OAuth2AuthorizationEntity a WHERE a.accessTokenExpiresAt < :now ORDER BY a.accessTokenIssuedAt DESC")
    Page<OAuth2AuthorizationEntity> findExpiredTokens(Instant now, Pageable pageable);

    /**
     * 查询未过期的授权记录（分页）
     *
     * @param now      当前时间
     * @param pageable 分页参数
     * @return 授权实体分页结果
     */
    @Query("SELECT a FROM OAuth2AuthorizationEntity a WHERE a.accessTokenExpiresAt >= :now ORDER BY a.accessTokenIssuedAt DESC")
    Page<OAuth2AuthorizationEntity> findActiveTokens(Instant now, Pageable pageable);

    /**
     * 按注册客户端ID列表查询过期的授权记录（分页）
     *
     * @param registeredClientIds 注册客户端ID列表
     * @param now                 当前时间
     * @param pageable            分页参数
     * @return 授权实体分页结果
     */
    @Query("SELECT a FROM OAuth2AuthorizationEntity a WHERE a.registeredClientId IN :registeredClientIds AND a.accessTokenExpiresAt < :now ORDER BY a.accessTokenIssuedAt DESC")
    Page<OAuth2AuthorizationEntity> findExpiredTokensByClientIds(List<String> registeredClientIds, Instant now, Pageable pageable);

    /**
     * 按注册客户端ID列表查询未过期的授权记录（分页）
     *
     * @param registeredClientIds 注册客户端ID列表
     * @param now                 当前时间
     * @param pageable            分页参数
     * @return 授权实体分页结果
     */
    @Query("SELECT a FROM OAuth2AuthorizationEntity a WHERE a.registeredClientId IN :registeredClientIds AND a.accessTokenExpiresAt >= :now ORDER BY a.accessTokenIssuedAt DESC")
    Page<OAuth2AuthorizationEntity> findActiveTokensByClientIds(List<String> registeredClientIds, Instant now, Pageable pageable);

    /**
     * 分批删除过期的授权记录（MySQL 原生 DELETE ... LIMIT，避免大事务长锁表）
     * <p>
     * 显式声明 countQuery：Spring Data JPA 会为原生 @Query 方法预派生 count 查询，
     * 其派生器按 SELECT 语句结构解析，遇到 DELETE 语句会在仓库 bean 初始化阶段 NPE，
     * 显式提供后跳过自动派生（该 count 查询本身对 @Modifying 方法不会被执行）。
     *
     * @param now   当前时间
     * @param limit 单批删除行数上限
     * @return 本批删除的记录数
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM oauth2_authorization WHERE access_token_expires_at < :now LIMIT :limit",
            countQuery = "SELECT 1", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * 更新 access_token_metadata
     *
     * @param id       授权ID
     * @param metadata 新的 metadata 字节数组
     */
    @Modifying
    @Transactional
    @Query("UPDATE OAuth2AuthorizationEntity a SET a.accessTokenMetadata = :metadata WHERE a.id = :id")
    void updateAccessTokenMetadata(String id, byte[] metadata);

    /**
     * 统计授权记录总数
     *
     * @return 总数
     */
    @Query("SELECT COUNT(a) FROM OAuth2AuthorizationEntity a")
    long countAllAuthorizations();

    /**
     * 统计过期的授权记录数
     *
     * @param now 当前时间
     * @return 过期记录数
     */
    @Query("SELECT COUNT(a) FROM OAuth2AuthorizationEntity a WHERE a.accessTokenExpiresAt < :now")
    long countExpired(@Param("now") Instant now);

    /**
     * 统计未过期的授权记录数
     *
     * @param now 当前时间
     * @return 未过期记录数
     */
    @Query("SELECT COUNT(a) FROM OAuth2AuthorizationEntity a WHERE a.accessTokenExpiresAt >= :now")
    long countNotExpired(@Param("now") Instant now);
}
