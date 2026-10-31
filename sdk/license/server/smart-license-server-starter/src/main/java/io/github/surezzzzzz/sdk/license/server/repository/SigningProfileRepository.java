package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import javax.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

/**
 * license_signing_profile 仓储。
 *
 * <p>档案为不可变历史记录（轮换留 RETIRED 行），单行语义的查找一律限定 ACTIVE。</p>
 */
public interface SigningProfileRepository extends JpaRepository<SigningProfileEntity, String> {

    /** 在用档案（历史行存在后不带 status 的同键查找是多行歧义，禁止使用）。 */
    Optional<SigningProfileEntity> findByProductIdAndPurposeAndStatus(String productId, String purpose,
                                                                      String status);

    List<SigningProfileEntity> findByProductIdAndPurpose(String productId, String purpose);

    /**
     * 登记事务的在用档案悲观锁：轮换/首建的并发串行化界（持有行锁期间完成 retire+插入，
     * 与 schema 的 active_marker 生成列唯一键共同保证至多一个 ACTIVE）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
    @org.springframework.data.jpa.repository.Query("select p from SigningProfileEntity p"
            + " where p.productId = ?1 and p.purpose = ?2 and p.status = 'ACTIVE'")
    Optional<SigningProfileEntity> lockActive(String productId, String purpose);

    /** 受限 DATA 范围列表过滤（IN 集合）。 */
    List<SigningProfileEntity> findByProfileIdIn(Iterable<String> profileIds);

    /** 协议密钥吊销以 kid 为目标；退役档案仍是历史凭据的可信归属事实。 */
    boolean existsByKid(String kid);
}
