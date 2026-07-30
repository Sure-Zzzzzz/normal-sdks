package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.time.Instant;

/**
 * 报价聚合的当前可操作摘要。
 *
 * @author surezzzzzz
 */
@Getter
public final class Quotation {

    private final String quotationId;
    private final String tenantId;
    private final String customerId;
    private final String ownerActorId;
    private final long aggregateVersion;
    private final int currentVersion;
    private final Integer currentConfirmableVersion;
    private final String confirmedOrderId;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建Quotation。
     *
     * @param quotationId               报价唯一标识
     * @param tenantId                  租户唯一标识
     * @param customerId                客户唯一标识
     * @param ownerActorId              报价归属操作者标识
     * @param aggregateVersion          聚合版本
     * @param currentVersion            当前报价版本号
     * @param currentConfirmableVersion 当前可确认版本号
     * @param confirmedOrderId          已确认订单标识
     * @param createdAt                 创建时间
     * @param updatedAt                 变更后的更新时间
     *
     */
    public Quotation(String quotationId, String tenantId, String customerId, String ownerActorId,
                     long aggregateVersion, int currentVersion, Integer currentConfirmableVersion,
                     String confirmedOrderId, Instant createdAt, Instant updatedAt) {
        this.quotationId = CrmValidationHelper.required(quotationId, "quotationId");
        this.tenantId = CrmValidationHelper.required(tenantId, "tenantId");
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.ownerActorId = CrmValidationHelper.required(ownerActorId, "ownerActorId");
        this.aggregateVersion = CrmValidationHelper.positiveVersion(aggregateVersion, "aggregateVersion");
        this.currentVersion = CrmValidationHelper.positiveVersion(currentVersion, "currentVersion");
        if (currentConfirmableVersion != null && currentConfirmableVersion < 1) {
            throw CrmException.validation("currentConfirmableVersion");
        }
        this.currentConfirmableVersion = currentConfirmableVersion;
        this.confirmedOrderId = CrmValidationHelper.optional(confirmedOrderId);
        this.createdAt = CrmValidationHelper.requiredObject(createdAt, "createdAt");
        this.updatedAt = CrmValidationHelper.requiredObject(updatedAt, "updatedAt");
        if (this.confirmedOrderId != null && this.currentConfirmableVersion != null) {
            throw CrmException.validation("confirmedOrderId/currentConfirmableVersion");
        }
    }


    /**
     * 判断报价是否已经确认。
     *
     * @return 处理后的领域事实或校验结果。
     *
     */
    public boolean isConfirmed() {
        return confirmedOrderId != null;
    }

    /**
     * 判断报价版本当前是否允许确认。
     *
     * @param version 报价版本事实或版本号
     * @param now     当前权威业务时间
     * @return 处理后的领域事实或校验结果。
     *
     */
    public boolean canConfirm(QuotationVersion version, Instant now) {
        return version != null && now != null && quotationId.equals(version.getQuotationId()) && !isConfirmed()
                && currentConfirmableVersion != null && currentConfirmableVersion == version.getVersion()
                && version.getState() == QuotationState.ISSUED && now.isBefore(version.getValidUntil());
    }

    /**
     * 生成签发后的报价摘要。
     *
     * @param version   报价版本事实或版本号
     * @param updatedAt 变更后的更新时间
     * @return 处理后的领域事实或校验结果。
     *
     */
    public Quotation issued(int version, Instant updatedAt) {
        if (isConfirmed() || version != currentVersion || currentConfirmableVersion != null) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "quotation cannot be issued");
        }
        return new Quotation(quotationId, tenantId, customerId, ownerActorId, aggregateVersion + 1L,
                currentVersion, version, null, createdAt, updatedAt);
    }

    /**
     * 生成确认后的报价摘要。
     *
     * @param version   报价版本事实或版本号
     * @param orderId   订单唯一标识
     * @param updatedAt 变更后的更新时间
     * @return 处理后的领域事实或校验结果。
     *
     */
    public Quotation confirmed(int version, String orderId, Instant updatedAt) {
        if (isConfirmed() || currentConfirmableVersion == null || currentConfirmableVersion != version) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "quotation cannot be confirmed");
        }
        return new Quotation(quotationId, tenantId, customerId, ownerActorId, aggregateVersion + 1L,
                currentVersion, null, orderId, createdAt, updatedAt);
    }
}
