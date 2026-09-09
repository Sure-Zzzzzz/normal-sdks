package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * IAM 站内信 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamMessageRepository extends JpaRepository<IamMessageEntity, Long> {

    List<IamMessageEntity> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    Page<IamMessageEntity> findByRecipientUserId(Long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    @Modifying
    @Query("UPDATE IamMessageEntity m SET m.readAt = CURRENT_TIMESTAMP "
            + "WHERE m.recipientUserId = :recipientUserId AND m.readAt IS NULL")
    int markAllRead(@Param("recipientUserId") Long recipientUserId);
}
