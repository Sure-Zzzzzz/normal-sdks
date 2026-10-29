package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
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
    private final String customerId;
    private final String opportunityId;
    private final String owner;
    private final long aggregateVersion;
    private final int currentVersion;
    private final Integer currentConfirmableVersion;
    private final String confirmedOrderId;
    /**
     * 重议来源报价标识（本报价确认时其自动 SUPERSEDED；可为 null）。
     */
    private final String supersedesQuotationId;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建Quotation。
     *
     * @param quotationId               报价唯一标识
     * @param customerId                客户唯一标识
     * @param owner                     报价归属操作者标识
     * @param aggregateVersion          聚合版本
     * @param currentVersion            当前报价版本号
     * @param currentConfirmableVersion 当前可确认版本号
     * @param confirmedOrderId          已确认订单标识
     * @param supersedesQuotationId     重议来源报价标识（可为 null）
     * @param createdAt                 创建时间
     * @param updatedAt                 变更后的更新时间
     *
     */
    public Quotation(String quotationId, String customerId, String owner,
                     long aggregateVersion, int currentVersion, Integer currentConfirmableVersion,
                     String confirmedOrderId, String supersedesQuotationId, Instant createdAt, Instant updatedAt) {
        this(quotationId, customerId, null, owner, aggregateVersion, currentVersion,
                currentConfirmableVersion, confirmedOrderId, supersedesQuotationId, createdAt, updatedAt);
    }

    /** 创建带商机归属的报价摘要。 */
    public Quotation(String quotationId, String customerId, String opportunityId, String owner,
                     long aggregateVersion, int currentVersion, Integer currentConfirmableVersion,
                     String confirmedOrderId, String supersedesQuotationId, Instant createdAt, Instant updatedAt) {
        this.quotationId = CrmValidationHelper.required(quotationId, "quotationId");
        this.customerId = CrmValidationHelper.required(customerId, "customerId");
        this.opportunityId = CrmValidationHelper.optional(opportunityId);
        this.owner = CrmValidationHelper.required(owner, "owner");
        this.aggregateVersion = CrmValidationHelper.positiveVersion(aggregateVersion, "aggregateVersion");
        this.currentVersion = CrmValidationHelper.positiveVersion(currentVersion, "currentVersion");
        if (currentConfirmableVersion != null && currentConfirmableVersion < 1) {
            throw CrmException.validation("currentConfirmableVersion");
        }
        this.currentConfirmableVersion = currentConfirmableVersion;
        this.confirmedOrderId = CrmValidationHelper.optional(confirmedOrderId);
        this.supersedesQuotationId = CrmValidationHelper.optional(supersedesQuotationId);
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
        return new Quotation(quotationId, customerId, opportunityId, owner, aggregateVersion + 1L,
                currentVersion, version, null, supersedesQuotationId, createdAt, updatedAt);
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
        return new Quotation(quotationId, customerId, opportunityId, owner, aggregateVersion + 1L,
                currentVersion, null, orderId, supersedesQuotationId, createdAt, updatedAt);
    }

    /**
     * 撤回已签发报价：仅存在可确认版本且未生成订单时可撤回，撤回后不再可确认。
     *
     * @param updatedAt 变更后的更新时间
     * @return 撤回后的报价聚合事实
     */
    public Quotation withdrawn(Instant updatedAt) {
        if (isConfirmed() || currentConfirmableVersion == null) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "quotation cannot be withdrawn");
        }
        return new Quotation(quotationId, customerId, opportunityId, owner, aggregateVersion + 1L,
                currentVersion, null, null, supersedesQuotationId, createdAt, updatedAt);
    }

    /**
     * 被重议新报价取代：仅已签发未确认时可被取代，取代后不再可确认。
     *
     * @param updatedAt 变更后的更新时间
     * @return 被取代后的报价聚合事实
     */
    public Quotation superseded(Instant updatedAt) {
        if (isConfirmed() || currentConfirmableVersion == null) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "quotation cannot be superseded");
        }
        return new Quotation(quotationId, customerId, opportunityId, owner, aggregateVersion + 1L,
                currentVersion, null, null, supersedesQuotationId, createdAt, updatedAt);
    }

    /**
     * 报价是否处于已签发待确认形态。
     *
     * @return 已签发且未确认、未撤回、未取代时返回 true
     */
    public boolean isIssued() {
        return !isConfirmed() && currentConfirmableVersion != null;
    }

    /**
     * 惰性过期判定：已签发且当前时间不早于版本有效截止时间即为过期（不落库、由读取侧判定）。
     *
     * @param version 当前可确认版本事实
     * @param now     当前权威业务时间
     * @return 已签发且已过期返回 true
     */
    public boolean isExpired(QuotationVersion version, Instant now) {
        return isIssued() && version != null && version.isExpired(now);
    }

    /**
     * 归属转移：仅未确认报价可转（确认后订单已生成，历史归属不追改）。
     *
     * @param newOwner  新归属主体（iam:/aksk: 前缀）
     * @param updatedAt 变更后的更新时间
     * @return 转移后的报价事实
     */
    public Quotation transferredTo(String newOwner, Instant updatedAt) {
        CrmOwner.of(newOwner);
        Instant requiredUpdatedAt = CrmValidationHelper.requiredObject(updatedAt, "updatedAt");
        if (isConfirmed()) {
            throw new CrmException(CrmErrorCode.INVALID_STATE_TRANSITION, "confirmed quotation cannot transfer");
        }
        return new Quotation(quotationId, customerId, opportunityId, newOwner, aggregateVersion + 1L, currentVersion,
                currentConfirmableVersion, confirmedOrderId, supersedesQuotationId, createdAt, requiredUpdatedAt);
    }
}
