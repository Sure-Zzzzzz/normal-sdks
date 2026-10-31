package io.github.surezzzzzz.sdk.license.server.repository;

import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** license_subject_assignment 仓储。 */
public interface SubjectAssignmentRepository extends JpaRepository<SubjectAssignmentEntity, String> {

    List<SubjectAssignmentEntity> findByGrantId(String grantId);
}
