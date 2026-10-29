package io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity;

import lombok.Getter;

/**
 * 商机销售阶段。
 *
 * @author surezzzzzz
 */
@Getter
public enum OpportunityStage {
    NEW("new", "新建"),
    QUALIFIED("qualified", "已确认"),
    PROPOSAL("proposal", "方案/报价"),
    NEGOTIATION("negotiation", "谈判"),
    WON("won", "赢单"),
    LOST("lost", "输单");

    private final String code;
    private final String description;

    OpportunityStage(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static OpportunityStage fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OpportunityStage stage : values()) {
            if (stage.code.equalsIgnoreCase(code)) {
                return stage;
            }
        }
        return null;
    }
}
