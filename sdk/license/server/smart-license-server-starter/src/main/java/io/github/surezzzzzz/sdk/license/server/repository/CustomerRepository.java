package io.github.surezzzzzz.sdk.license.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import io.github.surezzzzzz.sdk.license.server.entity.CustomerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** license_customer 仓储。 */
public interface CustomerRepository extends JpaRepository<CustomerEntity, String> {

    Optional<CustomerEntity> findByCode(String code);

    /** 受限 DATA 范围列表过滤（IN 集合）。 */
    Page<CustomerEntity> findByCustomerIdIn(Iterable<String> ids, Pageable pageable);
}
