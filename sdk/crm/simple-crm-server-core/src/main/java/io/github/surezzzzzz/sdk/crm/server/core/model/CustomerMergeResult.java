package io.github.surezzzzzz.sdk.crm.server.core.model;

import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 客户合并结果：源客户终态与改挂后的联系人/未完结报价集合（已确认事实改挂由实现同事务落库）。
 *
 * @author surezzzzzz
 */
@Getter
public final class CustomerMergeResult {

    private final Customer mergedSourceCustomer;
    private final Customer targetCustomer;
    private final List<Contact> relocatedContacts;

    /**
     * 创建CustomerMergeResult。
     *
     * @param mergedSourceCustomer 源客户终态（MERGED）
     * @param targetCustomer       目标客户事实
     * @param relocatedContacts    改挂后的联系人集合
     */
    public CustomerMergeResult(Customer mergedSourceCustomer, Customer targetCustomer,
                               List<Contact> relocatedContacts) {
        this.mergedSourceCustomer = CrmValidationHelper.requiredObject(mergedSourceCustomer,
                "mergedSourceCustomer");
        this.targetCustomer = CrmValidationHelper.requiredObject(targetCustomer, "targetCustomer");
        if (relocatedContacts == null || relocatedContacts.contains(null)) {
            throw new IllegalArgumentException("relocatedContacts");
        }
        this.relocatedContacts = Collections.unmodifiableList(relocatedContacts);
    }
}
