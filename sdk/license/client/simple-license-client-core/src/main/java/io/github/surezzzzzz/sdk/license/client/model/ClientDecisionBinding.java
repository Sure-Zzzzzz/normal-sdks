package io.github.surezzzzzz.sdk.license.client.model;

/**
 * 在线判定响应中的对象绑定；与离线凭据的签名 SubjectBinding 分开。
 *
 * @author surezzzzzz
 */
public final class ClientDecisionBinding {

    private final String kind;
    private final String activationId;
    private final Long activationGeneration;
    private final String sourceId;
    private final String subjectId;
    private final String assignmentId;
    private final Long assignmentGeneration;

    private ClientDecisionBinding(String kind, String activationId, Long activationGeneration,
                                  String sourceId, String subjectId, String assignmentId,
                                  Long assignmentGeneration) {
        if (kind == null || kind.isEmpty()) {
            throw new IllegalArgumentException("判定绑定种类不能为空");
        }
        if ("DEPLOYMENT".equals(kind)) {
            requireText(activationId, "activationId");
            requirePositive(activationGeneration, "activationGeneration");
            if (sourceId != null || subjectId != null || assignmentId != null || assignmentGeneration != null) {
                throw new IllegalArgumentException("部署绑定不得包含人员/服务字段");
            }
        } else if ("PERSON".equals(kind) || "SERVICE".equals(kind)) {
            requireText(sourceId, "sourceId");
            requireText(subjectId, "subjectId");
            if ((assignmentId == null) != (assignmentGeneration == null)) {
                throw new IllegalArgumentException("固定资格编号与代数必须成对");
            }
            if (assignmentGeneration != null) {
                requirePositive(assignmentGeneration, "assignmentGeneration");
            }
            if (activationId != null || activationGeneration != null) {
                throw new IllegalArgumentException("人员/服务绑定不得包含激活字段");
            }
        } else {
            throw new IllegalArgumentException("判定绑定种类无效");
        }
        this.kind = kind;
        this.activationId = activationId;
        this.activationGeneration = activationGeneration;
        this.sourceId = sourceId;
        this.subjectId = subjectId;
        this.assignmentId = assignmentId;
        this.assignmentGeneration = assignmentGeneration;
    }

    public static ClientDecisionBinding deployment(String activationId, Long activationGeneration) {
        return new ClientDecisionBinding("DEPLOYMENT", activationId, activationGeneration,
                null, null, null, null);
    }

    public static ClientDecisionBinding personService(String kind, String sourceId, String subjectId,
                                                      String assignmentId, Long assignmentGeneration) {
        return new ClientDecisionBinding(kind, null, null, sourceId, subjectId,
                assignmentId, assignmentGeneration);
    }

    public String getKind() {
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

    private static void requireText(String value, String field) {
        if (value == null || value.isEmpty() || value.length() > 128
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value < 1L || value > 9007199254740991L) {
            throw new IllegalArgumentException(field + "必须为JSON安全正整数");
        }
    }
}
