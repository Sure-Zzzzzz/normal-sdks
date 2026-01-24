package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2AuthorizationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
    @Query("SELECT a FROM OAuth2AuthorizationEntity a ORDER BY a.accessTokenIssuedAt DESC")
    Page<OAuth2AuthorizationEntity> findAllOrderByIssuedAtDesc(Pageable pageable);

    /**
     * 按注册客户端ID分页查询授权记录
     *
     * @param registeredClientId 注册客户端ID
     * @param pageable           分页参数
     * @return 授权实体分页结果
     */
    Page<OAuth2AuthorizationEntity> findByRegisteredClientIdOrderByAccessTokenIssuedAtDesc(String registeredClientId, Pageable pageable);

    /**
     * 按注册客户端ID列表分页查询授权记录
     *
     * @param registeredClientIds 注册客户端ID列表
     * @param pageable            分页参数
     * @return 授权实体分页结果
     */
    @Query("SELECT a FROM OAuth2AuthorizationEntity a WHERE a.registeredClientId IN :registeredClientIds ORDER BY a.accessTokenIssuedAt DESC")
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
     * 删除过期的授权记录
     *
     * @param now 当前时间
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM OAuth2AuthorizationEntity a WHERE a.accessTokenExpiresAt < :now")
    int deleteByAccessTokenExpiresAtBefore(Instant now);

    /**
     * 统计授权记录总数
     *
     * @return 总数
     */
    @Query("SELECT COUNT(a) FROM OAuth2AuthorizationEntity a")
    long countAllAuthorizations();
}
