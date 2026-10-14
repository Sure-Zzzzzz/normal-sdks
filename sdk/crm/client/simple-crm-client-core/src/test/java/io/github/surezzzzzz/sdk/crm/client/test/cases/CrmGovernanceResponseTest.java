package io.github.surezzzzzz.sdk.crm.client.test.cases;

import io.github.surezzzzzz.sdk.crm.client.CrmClientFieldSet;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import io.github.surezzzzzz.sdk.crm.client.model.CrmResourceView;
import io.github.surezzzzzz.sdk.crm.client.validation.CrmResponseValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 商品与客户治理响应契约：真实嵌套形状、严格字段边界与不可变结果。
 *
 * @author surezzzzzz
 */
@Slf4j
class CrmGovernanceResponseTest {

    @Test
    void offeringRequiresBusinessReferenceAndCapabilityFields() {
        Map<String, Object> payload = archive("offering-1", "published");
        payload.put("offeringReference", "catalog-reference-1");
        payload.put("capabilityType", "fixed_price_fulfillment_v1");
        payload.put("requiredConsumerCapability", "delivery-capability");
        CrmResourceView result = CrmResponseValidator.object(payload,
                CrmClientFieldSet.OFFERING_REQUIRED, CrmClientFieldSet.OFFERING);
        log.info("商品视图：reference={}，capability={}", result.getString("offeringReference"),
                result.getString("capabilityType"));
        assertEquals("catalog-reference-1", result.getString("offeringReference"));
        payload.remove("offeringReference");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(payload,
                CrmClientFieldSet.OFFERING_REQUIRED, CrmClientFieldSet.OFFERING));
        payload.put("offeringReference", "catalog-reference-1");
        payload.put("extraField", "unsupported");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(payload,
                CrmClientFieldSet.OFFERING_REQUIRED, CrmClientFieldSet.OFFERING));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeValidatesNestedArchivesAndRetainsEmptyRelocationSet() {
        Map<String, Object> source = archive("customer-source", "merged");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("source", source);
        payload.put("target", archive("customer-target", "active"));
        payload.put("relocatedContacts", Collections.emptyList());
        CrmResourceView result = CrmResponseValidator.customerMerge(payload);
        Map<String, Object> resultSource = (Map<String, Object>) result.get("source");
        log.info("客户合并：source={}，target={}，改挂集合为空仍合法", resultSource.get("id"),
                ((Map<String, Object>) result.get("target")).get("id"));
        assertEquals("customer-source", resultSource.get("id"));
        assertEquals(Collections.emptyList(), result.get("relocatedContacts"));
        source.put("id", "changed-after-validation");
        assertEquals("customer-source", resultSource.get("id"));
        assertThrows(UnsupportedOperationException.class, () -> resultSource.put("id", "changed"));
        source.remove("state");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
        payload.put("source", archive("customer-source", "merged"));
        payload.put("target", "not-an-object");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
    }

    @Test
    void mergeRejectsUnknownNestedFieldsAndNonArrayContacts() {
        Map<String, Object> contact = contact("contact-1", "示例职务");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("source", archive("customer-source", "merged"));
        payload.put("target", archive("customer-target", "active"));
        payload.put("relocatedContacts", Collections.singletonList(contact));
        log.info("客户合并响应校验：拒绝改挂联系人的未知字段和非数组集合");
        contact.put("extraField", "unsupported");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
        payload.put("relocatedContacts", 1);
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
        payload.put("relocatedContacts", Collections.emptyList());
        payload.put("extraField", "unsupported");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
    }

    @Test
    @SuppressWarnings("unchecked")
    void transferValidatesCustomerAndMinimalQuotationSummaries() {
        Map<String, Object> quotation = new LinkedHashMap<String, Object>();
        quotation.put("quotationId", "quotation-1");
        quotation.put("owner", "iam:target-owner");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("customer", archive("customer-1", "active"));
        payload.put("transferredQuotations", Collections.singletonList(quotation));
        CrmResourceView result = CrmResponseValidator.customerTransfer(payload);
        List<Map<String, Object>> quotations = (List<Map<String, Object>>) result.get("transferredQuotations");
        log.info("归属转移：customer={}，报价条数={}",
                ((Map<String, Object>) result.get("customer")).get("id"), quotations.size());
        assertEquals("quotation-1", quotations.get(0).get("quotationId"));
        assertThrows(UnsupportedOperationException.class, quotations::clear);
        assertThrows(UnsupportedOperationException.class, () -> quotations.get(0).put("owner", "changed"));
        quotation.remove("owner");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerTransfer(payload));
        quotation.put("owner", "iam:target-owner");
        quotation.put("extraField", "unsupported");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerTransfer(payload));
        payload.put("transferredQuotations", Collections.emptyList());
        assertEquals(Collections.emptyList(), CrmResponseValidator.customerTransfer(payload)
                .get("transferredQuotations"));
    }

    private Map<String, Object> archive(String id, String state) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("displayName", "示例档案");
        result.put("state", state);
        result.put("owner", "iam:actor-1");
        return result;
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergePreservesRelocatedContactCustomerAndNullableTitle() {
        Map<String, Object> contact = contact("contact-1", "示例职务");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("source", archive("customer-source", "merged"));
        payload.put("target", archive("customer-target", "active"));
        payload.put("relocatedContacts", Collections.singletonList(contact));
        CrmResourceView result = CrmResponseValidator.customerMerge(payload);
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) result.get("relocatedContacts");
        log.info("合并联系人使用客户标识并保留职务，职务空值也合法");
        assertEquals("customer-target", contacts.get(0).get("customerId"));
        assertEquals("示例职务", contacts.get(0).get("title"));
        assertFalse(contacts.get(0).containsKey("owner"));
        contact.put("customerId", "changed-after-validation");
        assertEquals("customer-target", contacts.get(0).get("customerId"));
        Map<String, Object> validatedContact = contacts.get(0);
        assertThrows(UnsupportedOperationException.class, () -> validatedContact.put("title", "changed"));
        payload.put("relocatedContacts", Collections.singletonList(contact("contact-2", null)));
        contacts = (List<Map<String, Object>>) CrmResponseValidator.customerMerge(payload)
                .get("relocatedContacts");
        assertTrue(contacts.get(0).containsKey("title"));
        assertNull(contacts.get(0).get("title"));
    }

    @Test
    void contactContractRejectsLegacyOwnerMissingCustomerAndInvalidStringFields() {
        Map<String, Object> payload = contact("contact-1", null);
        log.info("联系人校验：可空职务合法，客户归属必填且拒绝旧 owner 与非法类型");
        assertNull(CrmResponseValidator.object(payload,
                CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT).getNullable("title"));
        payload.remove("title");
        assertNull(CrmResponseValidator.object(payload,
                CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT).getNullable("title"));
        for (String field : CrmClientFieldSet.CONTACT) {
            Map<String, Object> invalid = contact("contact-1", "示例职务");
            invalid.put(field, 1);
            assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(invalid,
                    CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT), field);
        }
        payload.put("owner", "customer-target");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(payload,
                CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT));
        payload.remove("owner");
        payload.remove("customerId");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.object(payload,
                CrmClientFieldSet.CONTACT_REQUIRED, CrmClientFieldSet.CONTACT));
    }

    @Test
    void mergeRejectsInvalidContactCustomerAndTitleTypes() {
        Map<String, Object> contact = contact("contact-1", "示例职务");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("source", archive("customer-source", "merged"));
        payload.put("target", archive("customer-target", "active"));
        payload.put("relocatedContacts", Collections.singletonList(contact));
        log.info("合并联系人校验：客户归属和职务类型错误不能绕过嵌套校验");
        contact.put("customerId", Collections.singletonMap("id", "customer-target"));
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
        contact.put("customerId", "customer-target");
        contact.put("title", 1);
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
        contact.put("title", null);
        contact.put("owner", "customer-target");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
    }

    private Map<String, Object> contact(String id, String title) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("displayName", "示例联系人");
        result.put("state", "active");
        result.put("customerId", "customer-target");
        result.put("title", title);
        return result;
    }

    @Test
    void governanceRejectsNonStringArchiveAndQuotationIdentityFields() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        Map<String, Object> source = archive("customer-source", "merged");
        payload.put("source", source);
        payload.put("target", archive("customer-target", "active"));
        payload.put("relocatedContacts", Collections.emptyList());
        source.put("state", 1);
        log.info("治理结果拒绝非字符串状态、归属与报价标识");
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));
        source.put("state", "merged");
        source.put("owner", Collections.singletonMap("id", "actor-1"));
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerMerge(payload));

        Map<String, Object> quotation = new LinkedHashMap<String, Object>();
        quotation.put("quotationId", 1);
        quotation.put("owner", "iam:target-owner");
        payload.clear();
        payload.put("customer", archive("customer-1", "active"));
        payload.put("transferredQuotations", Collections.singletonList(quotation));
        assertThrows(CrmClientProtocolException.class, () -> CrmResponseValidator.customerTransfer(payload));
    }
}
