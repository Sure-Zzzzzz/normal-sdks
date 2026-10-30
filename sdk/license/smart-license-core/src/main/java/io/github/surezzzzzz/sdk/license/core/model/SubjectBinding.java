package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 签名快照绑定的具体授权对象（严格联合结构）。
 *
 * <p>部署绑定恰含 activationId/activationGeneration；人员/服务绑定恰含真实身份引用，
 * 适用固定资格时另含 assignmentId/assignmentGeneration，客户成员型禁止资格字段。
 * 不用 null 激活或自由 Map 表达联合；非法组合在构造期拒绝。</p>
 *
 * @author surezzzzzz
 */
public abstract class SubjectBinding {

    private final SubjectKind kind;

    private SubjectBinding(SubjectKind kind) {
        this.kind = kind;
    }

    public SubjectKind getKind() {
        return kind;
    }

    /** 部署绑定：激活编号与激活代数（正整数）。 */
    public static Deployment deployment(String activationId, long activationGeneration) {
        return new Deployment(activationId, activationGeneration);
    }

    /** 人员/服务绑定（无固定资格引用，客户成员型）。 */
    public static PersonService personService(SubjectRef subjectRef) {
        return new PersonService(subjectRef, null, 0L, false);
    }

    /** 人员/服务绑定（固定资格：assignmentId 与资格代数为必填对）。 */
    public static PersonService personService(SubjectRef subjectRef, String assignmentId, long assignmentGeneration) {
        return new PersonService(subjectRef, assignmentId, assignmentGeneration, true);
    }

    /** 部署绑定的激活编号。非部署绑定时抛出，不返回伪值。 */
    public abstract String activationId();

    /** 部署绑定的激活代数。 */
    public abstract long activationGeneration();

    /** 人员/服务绑定的真实身份引用。 */
    public abstract SubjectRef subjectRef();

    /** 固定资格 assignmentId；客户成员型为空。 */
    public abstract Optional<String> assignmentId();

    /** 固定资格代数；客户成员型为空。 */
    public abstract OptionalLong assignmentGeneration();


    /** 部署绑定。 */
    public static final class Deployment extends SubjectBinding {

        private final String activationId;
        private final long activationGeneration;

        private Deployment(String activationId, long activationGeneration) {
            super(SubjectKind.DEPLOYMENT);
            validateIdentifier(activationId);
            if (activationGeneration < 1) {
                throw new IllegalArgumentException(ErrorMessage.PROTOCOL_BINDING_INVALID);
            }
            this.activationId = activationId;
            this.activationGeneration = activationGeneration;
        }

        @Override
        public String activationId() {
            return activationId;
        }

        @Override
        public long activationGeneration() {
            return activationGeneration;
        }

        @Override
        public SubjectRef subjectRef() {
            throw new IllegalStateException(ErrorMessage.PROTOCOL_BINDING_INVALID);
        }

        @Override
        public Optional<String> assignmentId() {
            return Optional.empty();
        }

        @Override
        public OptionalLong assignmentGeneration() {
            return OptionalLong.empty();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Deployment)) {
                return false;
            }
            Deployment that = (Deployment) other;
            return activationGeneration == that.activationGeneration && activationId.equals(that.activationId);
        }

        @Override
        public int hashCode() {
            int result = activationId.hashCode();
            result = 31 * result + (int) (activationGeneration ^ (activationGeneration >>> 32));
            return result;
        }
    }

    /** 人员/服务绑定。 */
    public static final class PersonService extends SubjectBinding {

        private final SubjectRef subjectRef;
        private final String assignmentId;
        private final long assignmentGeneration;

        private PersonService(SubjectRef subjectRef, String assignmentId, long assignmentGeneration, boolean assigned) {
            super(subjectRef.getKind());
            if (assigned) {
                validateIdentifier(assignmentId);
                if (assignmentGeneration < 1) {
                    throw new IllegalArgumentException(ErrorMessage.PROTOCOL_BINDING_INVALID);
                }
            }
            this.subjectRef = subjectRef;
            this.assignmentId = assignmentId;
            this.assignmentGeneration = assignmentGeneration;
        }

        @Override
        public String activationId() {
            throw new IllegalStateException(ErrorMessage.PROTOCOL_BINDING_INVALID);
        }

        @Override
        public long activationGeneration() {
            throw new IllegalStateException(ErrorMessage.PROTOCOL_BINDING_INVALID);
        }

        @Override
        public SubjectRef subjectRef() {
            return subjectRef;
        }

        @Override
        public Optional<String> assignmentId() {
            return Optional.ofNullable(assignmentId);
        }

        @Override
        public OptionalLong assignmentGeneration() {
            return assignmentId == null ? OptionalLong.empty() : OptionalLong.of(assignmentGeneration);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PersonService)) {
                return false;
            }
            PersonService that = (PersonService) other;
            if (assignmentId == null ? that.assignmentId != null : !assignmentId.equals(that.assignmentId)) {
                return false;
            }
            if (assignmentId != null && assignmentGeneration != that.assignmentGeneration) {
                return false;
            }
            return subjectRef.equals(that.subjectRef);
        }

        @Override
        public int hashCode() {
            int result = subjectRef.hashCode();
            result = 31 * result + (assignmentId == null ? 0 : assignmentId.hashCode());
            if (assignmentId != null) {
                result = 31 * result + (int) (assignmentGeneration ^ (assignmentGeneration >>> 32));
            }
            return result;
        }
    }

    private static void validateIdentifier(String value) {
        if (value == null || value.isEmpty()
                || value.getBytes(StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.IDENTIFIER_MAX_BYTES
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(ErrorMessage.IDENTIFIER_INVALID);
        }
    }
}
