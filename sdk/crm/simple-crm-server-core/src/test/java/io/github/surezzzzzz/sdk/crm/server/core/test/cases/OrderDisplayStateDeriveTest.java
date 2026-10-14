package io.github.surezzzzzz.sdk.crm.server.core.test.cases;

import io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderDisplayState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 订单展示态派生测试：按交付项完成数三档派生（无完成=待履约、部分=部分已履约、全部=已履约）。
 *
 * @author surezzzzzz
 */
class OrderDisplayStateDeriveTest {

    @Test
    void emptyOrZeroCompletedYieldsPendingFulfillment() {
        assertEquals(OrderDisplayState.PENDING_FULFILLMENT, OrderDisplayState.derive(0, 0),
                "空交付项集合视为待履约");
        assertEquals(OrderDisplayState.PENDING_FULFILLMENT, OrderDisplayState.derive(3, 0),
                "无完成项保持待履约");
    }

    @Test
    void partialCompletionYieldsPartiallyFulfilled() {
        assertEquals(OrderDisplayState.PARTIALLY_FULFILLED, OrderDisplayState.derive(3, 1));
        assertEquals(OrderDisplayState.PARTIALLY_FULFILLED, OrderDisplayState.derive(3, 2),
                "部分完成不得提前判为已履约");
    }

    @Test
    void allCompletedYieldsFulfilled() {
        assertEquals(OrderDisplayState.FULFILLED, OrderDisplayState.derive(1, 1),
                "单项订单完成即已履约");
        assertEquals(OrderDisplayState.FULFILLED, OrderDisplayState.derive(5, 5));
        assertEquals(OrderDisplayState.FULFILLED, OrderDisplayState.derive(2, 7),
                "完成数超出总数按饱和处理（防御输入，正常链路不出现）");
    }
}
