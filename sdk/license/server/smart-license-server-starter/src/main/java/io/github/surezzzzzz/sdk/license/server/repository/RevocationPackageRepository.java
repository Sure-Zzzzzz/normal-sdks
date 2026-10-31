package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.RevocationPackageEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 产品级吊销包元数据仓储。 */
public interface RevocationPackageRepository extends JpaRepository<RevocationPackageEntity, String> {
    Optional<RevocationPackageEntity> findByOperationId(String operationId);
    Optional<RevocationPackageEntity> findByIdempotencyDigest(String idempotencyDigest);
    Optional<RevocationPackageEntity> findByIssuerIdAndProductIdAndSequence(String issuerId, String productId,
                                                                              long sequence);
    Optional<RevocationPackageEntity> findTopByProductIdAndStateOrderBySequenceDesc(String productId, String state);
}
