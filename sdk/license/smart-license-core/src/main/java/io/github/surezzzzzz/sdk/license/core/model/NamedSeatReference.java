package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.util.OptionalLong;

/**
 * 实名席位指标分配引用：固定资格上的单个 NAMED_SEAT 占位。
 *
 * <p>quantity 固定为 1；retention 为 FIXED+retainUntil 或 PERPETUAL；与 subjectBinding 的
 * 固定资格及 quotaPolicy 对应。重复指标拒绝。</p>
 *
 * @author surezzzzzz
 */
public final class NamedSeatReference {

    private final String assignmentId;
    private final long assignmentGeneration;
    private final String metric;
    private final Retention retention;
    private final Long retainUntil;

    /** 保留策略。 */
    public enum Retention {
        FIXED("FIXED"), PERPETUAL("PERPETUAL");

        private final String code;

        Retention(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private NamedSeatReference(String assignmentId, long assignmentGeneration, String metric,
                               Retention retention, Long retainUntil) {
        if (assignmentId == null || assignmentId.isEmpty() || metric == null || metric.isEmpty()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (assignmentGeneration < 1) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (retention == Retention.FIXED && (retainUntil == null || retainUntil < 0)) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        if (retention == Retention.PERPETUAL && retainUntil != null) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        this.assignmentId = assignmentId;
        this.assignmentGeneration = assignmentGeneration;
        this.metric = metric;
        this.retention = retention;
        this.retainUntil = retainUntil;
    }

    public static NamedSeatReference fixed(String assignmentId, long assignmentGeneration, String metric,
                                           long retainUntil) {
        return new NamedSeatReference(assignmentId, assignmentGeneration, metric, Retention.FIXED, retainUntil);
    }

    public static NamedSeatReference perpetual(String assignmentId, long assignmentGeneration, String metric) {
        return new NamedSeatReference(assignmentId, assignmentGeneration, metric, Retention.PERPETUAL, null);
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public long getAssignmentGeneration() {
        return assignmentGeneration;
    }

    public String getMetric() {
        return metric;
    }

    public Retention getRetention() {
        return retention;
    }

    /** FIXED 保留截止；PERPETUAL 为空。 */
    public OptionalLong getRetainUntil() {
        return retention == Retention.PERPETUAL
                ? OptionalLong.empty()
                : OptionalLong.of(retainUntil);
    }
}
