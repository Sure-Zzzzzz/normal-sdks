package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * 固定资格（license_subject_assignment）：授权下人员/服务的实名资格记录。
 *
 * <p>资格代数（assignmentGeneration）仅在同 assignmentId 内单调，接收者不可改绑；
 * 撤回只能经不可撤回吊销产生 REVOKED 终态。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_subject_assignment")
public class SubjectAssignmentEntity {

    @Id
    @Column(name = "assignment_id", length = 64, nullable = false)
    private String assignmentId;

    @Column(name = "grant_id", length = 64, nullable = false)
    private String grantId;

    /** 授权修订（创建时的 grantRevision 快照）。 */
    @Column(name = "grant_revision", nullable = false)
    private long grantRevision;

    /** 对象种类：PERSON/SERVICE（部署走 activation，不建资格）。 */
    @Column(name = "subject_kind", length = 16, nullable = false)
    private String subjectKind;

    @Column(name = "source_id", length = 128, nullable = false)
    private String sourceId;

    @Column(name = "subject_id", length = 128, nullable = false)
    private String subjectId;

    /** 资格代数（同 assignmentId 内单调；追加实名指标时递增）。 */
    @Column(name = "assignment_generation", nullable = false)
    private long assignmentGeneration;

    /** ACTIVE/SUSPENDED/REVOKED。 */
    @Column(name = "state", length = 16, nullable = false)
    private String state;

    /** 实名指标集合（JSON 数组文本，NAMED_SEAT 台账关联；无实名指标为空数组）。 */
    @Column(name = "metrics", nullable = false, length = 2048)
    private String metrics;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SubjectAssignmentEntity() {
    }

    public SubjectAssignmentEntity(String assignmentId, String grantId, long grantRevision,
                                    String subjectKind, String sourceId, String subjectId,
                                    String metrics) {
        this.assignmentId = assignmentId;
        this.grantId = grantId;
        this.grantRevision = grantRevision;
        this.subjectKind = subjectKind;
        this.sourceId = sourceId;
        this.subjectId = subjectId;
        this.assignmentGeneration = 1L;
        this.state = "ACTIVE";
        this.metrics = metrics;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public String getGrantId() {
        return grantId;
    }

    public long getGrantRevision() {
        return grantRevision;
    }

    public String getSubjectKind() {
        return subjectKind;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public String getMetrics() {
        return metrics;
    }

    /** 追加实名指标：代数递增（资格本身不变更接收者）。 */
    public void appendMetrics(String metrics, long generation) {
        this.metrics = metrics;
        this.assignmentGeneration = generation;
        this.updatedAt = Instant.now();
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
