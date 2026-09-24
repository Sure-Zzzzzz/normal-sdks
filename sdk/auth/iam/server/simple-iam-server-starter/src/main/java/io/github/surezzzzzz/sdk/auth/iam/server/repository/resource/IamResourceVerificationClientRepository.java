package io.github.surezzzzzz.sdk.auth.iam.server.repository.resource;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.resource.IamResourceVerificationClientEntity;
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

    /**
     * 可信应用物理删除时一并删除其资源验证客户端，避免留下无法归属的 Basic 凭据记录。
     */
    void deleteByApplicationId(Long applicationId);
}
