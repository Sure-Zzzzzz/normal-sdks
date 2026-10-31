package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.FulfillmentGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 履约授权关联仓储：主键与唯一键承担多实例去重，不通过 JVM 锁协调。
 *
 * @author surezzzzzz
 */
public interface FulfillmentGrantRepository extends JpaRepository<FulfillmentGrantEntity, String> {
}
