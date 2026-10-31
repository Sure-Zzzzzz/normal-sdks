package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** license_product 仓储。 */
public interface ProductRepository extends JpaRepository<ProductEntity, String> {

    /** 先锁定已存在的产品，串行化该产品吊销范围的首次创建，避免空范围行的间隙锁竞争。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.productId = ?1")
    Optional<ProductEntity> lockByProductId(String productId);

    Optional<ProductEntity> findByIssuerIdAndCode(String issuerId, String code);

    Page<ProductEntity> findByIssuerIdOrderByCreatedAtAsc(Pageable pageable, String issuerId);

    /** 受限 DATA 范围列表过滤（IN 集合）。 */
    Page<ProductEntity> findByProductIdIn(Iterable<String> ids, Pageable pageable);
}
