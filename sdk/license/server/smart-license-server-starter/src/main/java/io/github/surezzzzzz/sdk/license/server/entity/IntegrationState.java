package io.github.surezzzzzz.sdk.license.server.entity;

/**
 * IAM 角色联动计划的命令状态机：PENDING_EXECUTION（待 IAM 管理员执行）→
 * MANUALLY_CONFIRMED（人工确认回执）/SUPERSEDED（新期望替代）。
 *
 * <p>确认守卫（修订匹配+未过期）与替代规则收敛在枚举行为里——实体只委托。
 * 回执是人员确认，不是 IAM 服务端签收。</p>
 *
 * @author surezzzzzz
 */
public enum IntegrationState {

    PENDING_EXECUTION("PENDING_EXECUTION") {
        @Override
        public IntegrationState confirm(long expectedRevision, long desiredRevision,
                                        long nowEpochSeconds, long validUntilEpochSeconds) {
            if (expectedRevision != desiredRevision) {
                throw new IllegalStateException("期望修订不匹配");
            }
            if (nowEpochSeconds >= validUntilEpochSeconds) {
                throw new IllegalStateException("清单已过期");
            }
            return MANUALLY_CONFIRMED;
        }

        @Override
        public IntegrationState supersede() {
            return SUPERSEDED;
        }
    },
    MANUALLY_CONFIRMED("MANUALLY_CONFIRMED") {
    },
    SUPERSEDED("SUPERSEDED") { },
    VERIFIED("VERIFIED") { },
    DRIFTED("DRIFTED") { },
    UNCERTAIN("UNCERTAIN") { },
    ENVIRONMENT_RECONFIRM_REQUIRED("ENVIRONMENT_RECONFIRM_REQUIRED") { };

    private final String code;

    IntegrationState(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 人工确认转移：仅待执行清单可确认；守卫失败抛状态异常。 */
    public IntegrationState confirm(long expectedRevision, long desiredRevision,
                                    long nowEpochSeconds, long validUntilEpochSeconds) {
        throw new IllegalStateException("清单状态不可确认：" + code);
    }

    /** 新期望替代：仅待执行清单可被替代。 */
    public IntegrationState supersede() {
        return this;
    }

    /** 从存储码解析；未知码即协议违例。 */
    public static IntegrationState fromCode(String code) {
        for (IntegrationState value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知联动状态：" + code);
    }
}
