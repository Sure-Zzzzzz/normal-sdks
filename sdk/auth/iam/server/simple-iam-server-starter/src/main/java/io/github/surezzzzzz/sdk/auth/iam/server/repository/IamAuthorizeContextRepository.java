package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamAuthorizeContextStatus;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamAuthorizeContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * IAM 授权交易上下文 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamAuthorizeContextRepository extends JpaRepository<IamAuthorizeContextEntity, String> {

    /**
     * 根据 Servlet 会话哈希查询未完成交易
     */
    List<IamAuthorizeContextEntity> findByServletSessionIdHashAndStatusIn(String servletSessionIdHash,
                                                                          List<IamAuthorizeContextStatus> statuses);

    /**
     * 根据客户端和状态参数哈希查询交易
     */
    Optional<IamAuthorizeContextEntity> findByClientIdAndStateHash(String clientId, String stateHash);

    /**
     * 根据用户和状态查询交易
     */
    List<IamAuthorizeContextEntity> findByUserIdAndStatusIn(Long userId,
                                                            List<IamAuthorizeContextStatus> statuses);

    /**
     * 查询到期但未终止的授权交易
     */
    List<IamAuthorizeContextEntity> findByExpiresAtBeforeAndStatusIn(Instant expiresAt,
                                                                     List<IamAuthorizeContextStatus> statuses);
}
