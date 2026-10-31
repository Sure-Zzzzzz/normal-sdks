package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import io.github.surezzzzzz.sdk.license.core.spi.CrmFulfillmentPort;
import org.junit.jupiter.api.Test;

/**
 * CRM 履约端口 SPI 埋点断言：值类型不可变与游标语义（后续 AUTO 协调器的消费面契约）。
 *
 * @author surezzzzzz
 */
class CrmFulfillmentPortTest {

    @Test
    void outcomeCodesMatchCrmStableContract() {
        assertThat(CrmFulfillmentPort.Outcome.IN_PROGRESS.getCode()).isEqualTo("in_progress");
        assertThat(CrmFulfillmentPort.Outcome.COMPLETED.getCode()).isEqualTo("completed");
        assertThat(CrmFulfillmentPort.Outcome.FAILED.getCode()).isEqualTo("failed");
    }

    @Test
    void pullPageIsImmutableAndCarriesCursor() {
        List<CrmFulfillmentPort.FulfillmentItem> source = new ArrayList<CrmFulfillmentPort.FulfillmentItem>();
        source.add(new CrmFulfillmentPort.FulfillmentItem(
                "ff-1", "cu-bank", "order-9", "offering-1", "LICENSE", 5L, "license-consumer", 42L));
        CrmFulfillmentPort.FulfillmentPull pull =
                new CrmFulfillmentPort.FulfillmentPull(source, "cursor-next");
        assertThat(pull.getNextCursor()).isEqualTo("cursor-next");
        assertThat(pull.getItems()).hasSize(1);
        source.clear();
        assertThat(pull.getItems()).hasSize(1); // 防御性复制：外部清空不影响页
        assertThatThrownBy(() -> pull.getItems().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fulfillmentItemCarriesOnlyLicenseRelevantFields() {
        CrmFulfillmentPort.FulfillmentItem item = new CrmFulfillmentPort.FulfillmentItem(
                "ff-1", "cu-bank", "order-9", "offering-1", "LICENSE", 5L, "license-consumer", 42L);
        assertThat(item.getFulfillmentId()).isEqualTo("ff-1");
        assertThat(item.getOrderRef()).isEqualTo("order-9");
        assertThat(item.getOfferingRef()).isEqualTo("offering-1");
        assertThat(item.getCapabilityType()).isEqualTo("LICENSE");
        assertThat(item.getQuantity()).isEqualTo(5L);
        assertThat(item.getAssignedConsumerId()).isEqualTo("license-consumer");
        assertThat(item.getSequenceWatermark()).isEqualTo(42L);
    }
}
