package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AKSK 服务主体应用授权投影仓储。
 *
 * @author surezzzzzz
 */
@Repository
public interface AkskApplicationAuthorizationRepository
        extends JpaRepository<AkskApplicationAuthorizationEntity, Long> {

    /**
     * 按服务主体查询授权投影。
     *
     * @param clientId AKSK客户端标识
     * @return 授权投影
     */
    Optional<AkskApplicationAuthorizationEntity> findByClientId(String clientId);

    /**
     * 批量查询服务主体授权投影。
     *
     * @param clientIds AKSK客户端标识列表
     * @return 授权投影列表
     */
    @Query("SELECT authorization FROM AkskApplicationAuthorizationEntity authorization "
            + "WHERE authorization.clientId IN :clientIds")
    List<AkskApplicationAuthorizationEntity> findAllByClientIdIn(@Param("clientIds") List<String> clientIds);
}
