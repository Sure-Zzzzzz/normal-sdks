package io.github.surezzzzzz.sdk.license.server.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import io.github.surezzzzzz.sdk.license.server.entity.IssuerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** license_issuer 仓储。 */
public interface IssuerRepository extends JpaRepository<IssuerEntity, String> {

    Optional<IssuerEntity> findByCode(String code);

    /** 受限 DATA 范围列表过滤（IN 集合）。 */
    Page<IssuerEntity> findByIssuerIdIn(Iterable<String> ids, Pageable pageable);
}
