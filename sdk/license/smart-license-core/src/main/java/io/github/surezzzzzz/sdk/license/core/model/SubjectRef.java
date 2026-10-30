package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import java.nio.charset.StandardCharsets;

/**
 * 真实身份引用：受信来源命名空间下的稳定人员/服务编号。
 *
 * <p>kind 仅允许 PERSON/SERVICE；sourceId 是受控登记的来源命名空间，不由请求临时指定获得信任；同编号跨来源不混淆。</p>
 *
 * @author surezzzzzz
 */
public final class SubjectRef {

    private final SubjectKind kind;
    private final String sourceId;
    private final String subjectId;

    private SubjectRef(SubjectKind kind, String sourceId, String subjectId) {
        this.kind = kind;
        this.sourceId = sourceId;
        this.subjectId = subjectId;
    }

    /** 构造人员或服务身份引用；非法种类或空/超限编号在构造期拒绝。 */
    public static SubjectRef of(SubjectKind kind, String sourceId, String subjectId) {
        if (kind == null || !kind.isSubjectRefKind()) {
            throw new IllegalArgumentException(ErrorMessage.SUBJECT_REF_KIND_INVALID);
        }
        validateIdentifier(sourceId);
        validateIdentifier(subjectId);
        return new SubjectRef(kind, sourceId, subjectId);
    }

    public SubjectKind getKind() {
        return kind;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    private static void validateIdentifier(String value) {
        if (value == null || value.isEmpty()
                || value.getBytes(StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.IDENTIFIER_MAX_BYTES
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(ErrorMessage.IDENTIFIER_INVALID);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SubjectRef)) {
            return false;
        }
        SubjectRef that = (SubjectRef) other;
        return kind == that.kind && sourceId.equals(that.sourceId) && subjectId.equals(that.subjectId);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + sourceId.hashCode();
        result = 31 * result + subjectId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SubjectRef{kind=" + kind.getCode() + ", sourceId='" + sourceId + "', subjectId='" + subjectId + "'}";
    }
}
