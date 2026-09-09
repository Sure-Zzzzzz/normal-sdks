package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamResourceVerificationClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * IAM 资源令牌验证客户端 Repository。
 *
 * @author surezzzzzz
 */
@Repository
public interface IamResourceVerificationClientRepository
        extends JpaRepository<IamResourceVerificationClientEntity, String> {

    /**
     * 按客户端ID查询资源验证客户端。
     */
    Optional<IamResourceVerificationClientEntity> findByClientId(String clientId);

    /**
     * 按应用ID查询其全部资源验证客户端。
     */
    List<IamResourceVerificationClientEntity> findByApplicationId(Long applicationId);
}
