package io.github.surezzzzzz.sdk.crm.server.core.command;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

/**
 * 上架商品或服务命令。
 *
 * @author surezzzzzz
 */
@Getter
public final class PublishOfferingCommand {

    private final String offeringId;

    /**
     * 创建PublishOfferingCommand。
     *
     * @param offeringId 目标资源唯一标识
     */
    public PublishOfferingCommand(String offeringId) {
        this.offeringId = CrmValidationHelper.required(offeringId, "offeringId");
    }
}
