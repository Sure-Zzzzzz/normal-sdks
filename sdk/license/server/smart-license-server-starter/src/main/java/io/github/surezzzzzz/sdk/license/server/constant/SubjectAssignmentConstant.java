package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 固定资格治理的版本、终态及指标边界。
 *
 * @author surezzzzzz
 */
public final class SubjectAssignmentConstant {

    public static final String INVALID_VERSION = "expectedRowVersion必须为非负整数";
    public static final String TERMINAL_ASSIGNMENT = "已替换或吊销的固定资格不能恢复、追加指标或再次替换";
    public static final String INVALID_GENERATION = "固定资格代数已达上限，禁止追加指标";
    public static final String INVALID_METRICS = "requestedNamedMetrics必须为非空数组";
    public static final String INVALID_GRANT_REVISION = "grantRevision必须为正整数";
    public static final String STALE_GRANT_REVISION = "授权修订已变化，请回读后重试";

    private SubjectAssignmentConstant() {
    }
}
