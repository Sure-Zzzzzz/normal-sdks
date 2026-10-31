package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.RoleProjectionEntity;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 专用角色协调按目标持有数据库锁，跨 Server 也不会并行写同一角色。 */
public interface RoleProjectionRepository extends JpaRepository<RoleProjectionEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RoleProjectionEntity p where p.customerId=:customer and p.productId=:product and p.applicationId=:app")
    Optional<RoleProjectionEntity> lockTarget(@Param("customer") String customerId,
                                             @Param("product") String productId,
                                             @Param("app") long applicationId);
}
