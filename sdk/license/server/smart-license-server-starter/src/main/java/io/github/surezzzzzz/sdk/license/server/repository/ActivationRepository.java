package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** license_activation 仓储。 */
public interface ActivationRepository extends JpaRepository<ActivationEntity, String> {

    List<ActivationEntity> findByGrantId(String grantId);
}
