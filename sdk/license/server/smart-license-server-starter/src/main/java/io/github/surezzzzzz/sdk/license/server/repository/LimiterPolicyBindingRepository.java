package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.LimiterPolicyBindingEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** License 自有 limiter 策略投影台账仓储。 */
public interface LimiterPolicyBindingRepository extends JpaRepository<LimiterPolicyBindingEntity, String> {

    Optional<LimiterPolicyBindingEntity> findByGrantIdAndServiceCodeAndResourceCodeAndCustomerId(
            String grantId, String serviceCode, String resourceCode, String customerId);

    List<LimiterPolicyBindingEntity> findByGrantId(String grantId);

    List<LimiterPolicyBindingEntity> findByCustomerIdAndServiceCodeAndResourceCode(
            String customerId, String serviceCode, String resourceCode);

    List<LimiterPolicyBindingEntity> findByStateIn(java.util.Collection<
            io.github.surezzzzzz.sdk.license.server.constant.LimiterPolicyBindingState> states);
}
