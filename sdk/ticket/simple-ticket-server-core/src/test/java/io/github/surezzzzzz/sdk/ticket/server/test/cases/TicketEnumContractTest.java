package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.constant.ActorRole;
import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketAction;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单枚举代码契约：新增查找能力不改变原有枚举名称、持久化值和响应值。
 *
 * @author surezzzzzz
 */
@Slf4j
class TicketEnumContractTest {

    @Test
    void allEnumCodesPreserveExistingNames() {
        assertContract(ActorRole.values(), ActorRole::getCode, ActorRole::getDescription,
                ActorRole::fromCode, ActorRole::isValid, ActorRole::getAllCodes);
        assertContract(RequestInfoReason.values(), RequestInfoReason::getCode, RequestInfoReason::getDescription,
                RequestInfoReason::fromCode, RequestInfoReason::isValid, RequestInfoReason::getAllCodes);
        assertContract(TicketAction.values(), TicketAction::getCode, TicketAction::getDescription,
                TicketAction::fromCode, TicketAction::isValid, TicketAction::getAllCodes);
        assertContract(TicketPriority.values(), TicketPriority::getCode, TicketPriority::getDescription,
                TicketPriority::fromCode, TicketPriority::isValid, TicketPriority::getAllCodes);
        assertContract(TicketSource.values(), TicketSource::getCode, TicketSource::getDescription,
                TicketSource::fromCode, TicketSource::isValid, TicketSource::getAllCodes);
        assertContract(TicketStatus.values(), TicketStatus::getCode, TicketStatus::getDescription,
                TicketStatus::fromCode, TicketStatus::isValid, TicketStatus::getAllCodes);
    }

    private <E extends Enum<E>> void assertContract(E[] values, Function<E, String> codeOf,
            Function<E, String> descriptionOf, Function<String, E> fromCode, Predicate<String> isValid,
            Supplier<String[]> allCodes) {
        log.debug("验证工单枚举代码契约：type={}", values[0].getDeclaringClass().getSimpleName());
        String[] names = Arrays.stream(values).map(Enum::name).toArray(String[]::new);
        assertArrayEquals(names, allCodes.get(), "代码列表应保持枚举声明顺序与原值");
        for (E value : values) {
            String code = value.name();
            assertEquals(code, codeOf.apply(value), "代码须等于旧枚举名称");
            assertEquals(code, value.toString(), "字符串表示须保持旧值");
            assertNotNull(descriptionOf.apply(value), "展示说明不可为空");
            assertFalse(descriptionOf.apply(value).trim().isEmpty(), "展示说明不可为空白");
            assertSame(value, fromCode.apply(code), "代码须解析回原枚举");
            assertSame(value, fromCode.apply(code.toLowerCase(Locale.ROOT)), "代码解析应忽略大小写");
            assertTrue(isValid.test(code), "现有代码须有效");
        }
        assertNull(fromCode.apply(null), "空代码不应匹配枚举");
        assertNull(fromCode.apply("UNKNOWN"), "未知代码不应匹配枚举");
        assertFalse(isValid.test(null), "空代码无效");
        assertFalse(isValid.test("UNKNOWN"), "未知代码无效");
    }
}
