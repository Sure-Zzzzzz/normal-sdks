package io.github.surezzzzzz.sdk.license.server.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 版本化资产（模型/方案）的发布状态机：草稿可编辑，发布即固化（不可逆），
 * 已发布可退役（禁止新引用，历史不受影响）。
 *
 * <p>转移守卫与目标状态收敛在枚举行为里——实体方法只委托，不写状态分支。</p>
 *
 * @author surezzzzzz
 */
public enum PublicationState {

    DRAFT("DRAFT") {
        @Override
        public PublicationState publish() {
            return PUBLISHED;
        }

        @Override
        public boolean editable() {
            return true;
        }
    },
    PUBLISHED("PUBLISHED") {
        @Override
        public PublicationState retire() {
            return RETIRED;
        }
    },
    RETIRED("RETIRED") {
    };

    private final String code;

    PublicationState(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 发布转移：仅草稿允许；非法转移抛状态异常。 */
    public PublicationState publish() {
        throw new IllegalStateException("仅草稿可发布");
    }

    /** 退役转移：仅已发布允许。 */
    public PublicationState retire() {
        throw new IllegalStateException("仅已发布可退役");
    }

    /** 可编辑（草稿专属）：已发布快照不可变。 */
    public boolean editable() {
        return false;
    }

    /** 从存储码解析；未知码即协议违例（fail-closed）。 */
    public static PublicationState fromCode(String code) {
        for (PublicationState value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知发布状态：" + code);
    }
}
