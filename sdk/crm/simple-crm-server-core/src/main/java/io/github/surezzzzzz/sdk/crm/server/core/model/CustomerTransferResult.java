package io.github.surezzzzzz.sdk.crm.server.core.model;

import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 客户归属转移结果：转移后的客户与连带转移的未完结报价集合。
 *
 * @author surezzzzzz
 */
@Getter
public final class CustomerTransferResult {

    private final Customer customer;
    private final List<Quotation> transferredQuotations;

    /**
     * 创建CustomerTransferResult。
     *
     * @param customer              转移后的客户事实
     * @param transferredQuotations 连带转移的未完结报价集合
     */
    public CustomerTransferResult(Customer customer, List<Quotation> transferredQuotations) {
        this.customer = CrmValidationHelper.requiredObject(customer, "customer");
        if (transferredQuotations == null || transferredQuotations.contains(null)) {
            throw new IllegalArgumentException("transferredQuotations");
        }
        this.transferredQuotations = Collections.unmodifiableList(transferredQuotations);
    }
}
