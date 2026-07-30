package io.github.surezzzzzz.sdk.license.core.repository;

import io.github.surezzzzzz.sdk.license.core.model.LicenseKeyMapping;

import java.util.Optional;

/**
 * License 不可变业务密钥映射仓储端口。
 *
 * @author surezzzzzz
 */
public interface LicenseKeyMappingRepository {

    /**
     * 查询 tenant 下的业务密钥映射。
     *
     * @param tenantId tenant 标识
     * @param kid      业务密钥标识
     * @return 映射；不存在时为空
     */
    Optional<LicenseKeyMapping> findByTenantIdAndKid(String tenantId, String kid);

    /**
     * 创建不可变业务密钥映射。
     *
     * <p>适配层必须在持久化事务中以 {@code (tenantId, kid)} 唯一约束拒绝并发重绑，
     * 不得将既有映射更新为新的 KMS 引用、版本、公钥或状态。</p>
     *
     * @param mapping 待创建映射
     */
    void create(LicenseKeyMapping mapping);
}
