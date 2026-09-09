package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserGroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * IAM 协作组成员 Repository
 *
 * @author surezzzzzz
 */
@Repository
public interface IamUserGroupMemberRepository extends JpaRepository<IamUserGroupMemberEntity, Long> {

    List<IamUserGroupMemberEntity> findByGroupId(Long groupId);

    List<IamUserGroupMemberEntity> findByUserId(Long userId);

    List<IamUserGroupMemberEntity> findByGroupIdIn(Collection<Long> groupIds);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    @Modifying
    @Query("DELETE FROM IamUserGroupMemberEntity m WHERE m.groupId = :groupId")
    void deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying
    @Query("DELETE FROM IamUserGroupMemberEntity m WHERE m.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM IamUserGroupMemberEntity m WHERE m.groupId = :groupId AND m.userId = :userId")
    void deleteByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
