package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;

/**
 * 资格政策：部署分配/客户成员/固定资格（严格联合）。
 *
 * <p>部署恰用 ALLOCATED_DEPLOYMENTS；人员可用 CUSTOMER_MEMBERS（必须组织 required=true，
 * 禁止 OFFLINE）或 ASSIGNED_SUBJECTS（可另要求成员资格）；服务恰用 ASSIGNED_SUBJECTS。
 * 人员/服务的 OFFLINE/HYBRID 另含 offlineProofProfile，ONLINE 禁止该字段。</p>
 *
 * @author surezzzzzz
 */
public abstract class EligibilityPolicy {

    /** 资格种类。 */
    public enum Kind {
        ALLOCATED_DEPLOYMENTS("ALLOCATED_DEPLOYMENTS"),
        CUSTOMER_MEMBERS("CUSTOMER_MEMBERS"),
        ASSIGNED_SUBJECTS("ASSIGNED_SUBJECTS");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /** 校验模式。 */
    public enum Mode {
        OFFLINE("OFFLINE"), ONLINE("ONLINE"), HYBRID("HYBRID");

        private final String code;

        Mode(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final Kind kind;

    private EligibilityPolicy(Kind kind) {
        this.kind = kind;
    }

    public static AllocatedDeployments allocatedDeployments() {
        return new AllocatedDeployments();
    }

    public static CustomerMembers customerMembers() {
        return new CustomerMembers(null);
    }

    public static AssignedSubjects assignedSubjects(String offlineProofProfile) {
        return new AssignedSubjects(offlineProofProfile);
    }

    public Kind getKind() {
        return kind;
    }

    /** 固定资格 offlineProofProfile；ONLINE 或非固定资格为 null。 */
    public String getOfflineProofProfile() {
        return null;
    }

    /** 部署分配资格。 */
    public static final class AllocatedDeployments extends EligibilityPolicy {
        private AllocatedDeployments() {
            super(Kind.ALLOCATED_DEPLOYMENTS);
        }
    }

    /** 客户当前成员资格。 */
    public static final class CustomerMembers extends EligibilityPolicy {
        private CustomerMembers(String ignored) {
            super(Kind.CUSTOMER_MEMBERS);
        }
    }

    /** 固定资格名单。 */
    public static final class AssignedSubjects extends EligibilityPolicy {

        private final String offlineProofProfile;

        private AssignedSubjects(String offlineProofProfile) {
            super(Kind.ASSIGNED_SUBJECTS);
            this.offlineProofProfile = offlineProofProfile;
        }

        @Override
        public String getOfflineProofProfile() {
            return offlineProofProfile;
        }
    }

    /** 发布级组合校验：subjectKind×eligibilityKind×mode×offlineProof。 */
    public static void validateCombination(SubjectKind subjectKind, EligibilityPolicy policy, Mode mode) {
        if (subjectKind == null || policy == null || mode == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        // 部署恰用部署分配
        if (subjectKind == SubjectKind.DEPLOYMENT) {
            if (policy.getKind() != Kind.ALLOCATED_DEPLOYMENTS) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            if (policy.getOfflineProofProfile() != null) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
            }
            return;
        }
        // 人员/服务禁止部署分配
        if (policy.getKind() == Kind.ALLOCATED_DEPLOYMENTS) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        // 服务恰用固定资格
        if (subjectKind == SubjectKind.SERVICE && policy.getKind() == Kind.CUSTOMER_MEMBERS) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        // 客户成员禁止 OFFLINE
        if (policy.getKind() == Kind.CUSTOMER_MEMBERS && mode == Mode.OFFLINE) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        // 人员/服务 OFFLINE/HYBRID 必须有 offlineProofProfile，ONLINE 禁止
        String profile = policy.getOfflineProofProfile();
        if (mode == Mode.ONLINE && profile != null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (mode != Mode.ONLINE && profile == null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
    }
}
