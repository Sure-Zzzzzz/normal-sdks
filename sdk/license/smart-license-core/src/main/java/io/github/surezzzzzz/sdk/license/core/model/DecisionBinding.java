package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import java.nio.charset.StandardCharsets;

/**
 * 在线判定回显的实际对象绑定。
 *
 * <p>这是判定响应的关联元数据，不是离线凭据中的签名绑定。部署绑定必须
 * 回显服务端核实过的激活代数；固定资格绑定必须把资格编号与代数成对回显。
 * 客户成员型资格没有固定资格编号，因此两者都为空。</p>
 *
 * @author surezzzzzz
 */
public final class DecisionBinding {

    private final SubjectKind kind;
    private final String activationId;
    private final Long activationGeneration;
    private final String sourceId;
    private final String subjectId;
    private final String assignmentId;
    private final Long assignmentGeneration;

    private DecisionBinding(SubjectKind kind, String activationId, Long activationGeneration,
                            String sourceId, String subjectId, String assignmentId,
                            Long assignmentGeneration) {
        if (kind == null) {
            throw new IllegalArgumentException(ErrorMessage.SUBJECT_REF_KIND_INVALID);
        }
        if (kind == SubjectKind.DEPLOYMENT) {
            requireText(activationId);
            if (sourceId != null || subjectId != null || assignmentId != null
                    || assignmentGeneration != null) {
                throw new IllegalArgumentException(ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
            requirePositive(activationGeneration);
        } else {
            if (activationId != null || activationGeneration != null) {
                throw new IllegalArgumentException(ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
            requireText(sourceId);
            requireText(subjectId);
            if ((assignmentId == null) != (assignmentGeneration == null)) {
                throw new IllegalArgumentException(ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
            requirePositiveOrNull(assignmentGeneration);
        }
        this.kind = kind;
        this.activationId = activationId;
        this.activationGeneration = activationGeneration;
        this.sourceId = sourceId;
        this.subjectId = subjectId;
        this.assignmentId = assignmentId;
        this.assignmentGeneration = assignmentGeneration;
    }

    /** 部署判定绑定；代数必须来自已核实的激活实体。 */
    public static DecisionBinding deployment(String activationId, Long activationGeneration) {
        return new DecisionBinding(SubjectKind.DEPLOYMENT, activationId, activationGeneration,
                null, null, null, null);
    }

    /** 人员/服务判定绑定；固定资格编号与代数必须成对，客户成员型两者均为空。 */
    public static DecisionBinding personService(SubjectKind kind, String sourceId, String subjectId,
                                               String assignmentId, Long assignmentGeneration) {
        if (kind == null || !kind.isSubjectRefKind()) {
            throw new IllegalArgumentException(ErrorMessage.SUBJECT_REF_KIND_INVALID);
        }
        return new DecisionBinding(kind, null, null, sourceId, subjectId, assignmentId,
                assignmentGeneration);
    }

    public SubjectKind getKind() {
        return kind;
    }

    public String getActivationId() {
        return activationId;
    }

    public Long getActivationGeneration() {
        return activationGeneration;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public Long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    private static void requireText(String value) {
        if (value == null || value.isEmpty()
                || value.getBytes(StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.IDENTIFIER_MAX_BYTES
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
    }

    private static void requirePositiveOrNull(Long value) {
        if (value != null && (value < 1L || value > 9007199254740991L)) {
            throw new IllegalArgumentException(ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
    }

    private static void requirePositive(Long value) {
        if (value == null || value < 1L || value > 9007199254740991L) {
            throw new IllegalArgumentException(ErrorMessage.PROTOCOL_BINDING_INVALID);
        }
    }
}
