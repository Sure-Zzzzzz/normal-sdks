package io.github.surezzzzzz.sdk.sensitive.keyword.constant;

/**
 * 敏感机构类型
 *
 * @author surezzzzzz
 */
public enum SensitiveOrgType {
    NONE(0.80),        // 普通机构：保留率阈值80%
    FINANCIAL(0.60),   // 金融机构：保留率阈值60%（更严格）
    GOVERNMENT(0.60),  // 政府机构：保留率阈值60%（更严格）
    EDUCATION(0.70);   // 教育机构：保留率阈值70%

    private final double retentionThreshold;

    SensitiveOrgType(double retentionThreshold) {
        this.retentionThreshold = retentionThreshold;
    }

    public double getRetentionThreshold() {
        return retentionThreshold;
    }
}
