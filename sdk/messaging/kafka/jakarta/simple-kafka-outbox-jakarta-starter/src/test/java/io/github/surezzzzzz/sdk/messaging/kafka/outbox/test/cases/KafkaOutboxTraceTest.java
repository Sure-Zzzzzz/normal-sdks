package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.DefaultKafkaOutboxTraceSnapshotResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceScope;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.MdcKafkaOutboxTraceScope;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Kafka Outbox Trace 快照和作用域测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaOutboxTraceTest {

    private final DefaultKafkaOutboxTraceSnapshotResolver resolver =
            new DefaultKafkaOutboxTraceSnapshotResolver();
    private final MdcKafkaOutboxTraceScope traceScope = new MdcKafkaOutboxTraceScope();

    @AfterEach
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void testThreeKeyCapturePriorityAndTrimming() {
        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID, "  primary-trace  ");
        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN, "secondary-trace");
        MDC.put(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID, "third-trace");
        String primary = resolver.resolveTraceId();

        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID, " ");
        String secondary = resolver.resolveTraceId();

        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN, "\t");
        String third = resolver.resolveTraceId();

        log.info("三 key 捕获结果，一级: {}, 二级回退: {}, 三级回退: {}", primary, secondary, third);
        assertEquals("primary-trace", primary, "traceId 应具有最高捕获优先级并被 trim");
        assertEquals("secondary-trace", secondary, "traceId 空白时应回退到 trace-id");
        assertEquals("third-trace", third, "前两级空白时应回退到 X-B3-TraceId");
    }

    @Test
    public void testAllMissingOrBlankReturnsNull() {
        String missing = resolver.resolveTraceId();
        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID, " ");
        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN, "\t");
        MDC.put(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID, "  ");
        String blank = resolver.resolveTraceId();

        log.info("Trace 全部缺失结果: {}, 全部空白结果: {}", missing, blank);
        assertNull(missing, "三个 MDC key 全部缺失时应返回 null");
        assertNull(blank, "三个 MDC key 全部空白时应返回 null");
    }

    @Test
    public void testScopeReplacesAndExactlyRestoresThreeKeys() {
        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID, "previous-primary");
        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN, "previous-secondary");
        MDC.put(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID, "previous-third");

        KafkaOutboxTraceScope.Scope scope = traceScope.open("snapshot-trace");
        log.info("作用域内三个 key: traceId={}, trace-id={}, X-B3-TraceId={}",
                MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN),
                MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID));
        assertEquals("snapshot-trace", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                "作用域内应安装快照 traceId");
        assertNull(MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN),
                "作用域内应移除次优先级 trace-id");
        assertNull(MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID),
                "作用域内应移除最低优先级 X-B3-TraceId");

        scope.close();
        log.info("作用域关闭后三个 key: traceId={}, trace-id={}, X-B3-TraceId={}",
                MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN),
                MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID));
        assertEquals("previous-primary", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                "关闭作用域应精确恢复原 traceId");
        assertEquals("previous-secondary", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN),
                "关闭作用域应精确恢复原 trace-id");
        assertEquals("previous-third", MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID),
                "关闭作用域应精确恢复原 X-B3-TraceId");
    }

    @Test
    public void testNullScopeClearsAndRestoresMixedPresence() {
        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN, "previous-secondary");

        try (KafkaOutboxTraceScope.Scope ignored = traceScope.open(null)) {
            log.info("null trace 作用域内三个 key: traceId={}, trace-id={}, X-B3-TraceId={}",
                    MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                    MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN),
                    MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID));
            assertNull(MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID), "null 快照应移除 traceId");
            assertNull(MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN),
                    "null 快照作用域应移除 trace-id");
            assertNull(MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID),
                    "null 快照作用域应移除 X-B3-TraceId");
        }

        log.info("null trace 作用域关闭后三个 key: traceId={}, trace-id={}, X-B3-TraceId={}",
                MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN),
                MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID));
        assertNull(MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID), "原本缺失的 traceId 应保持缺失");
        assertEquals("previous-secondary", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID_WITH_HYPHEN),
                "原本存在的 trace-id 应恢复");
        assertNull(MDC.get(SimpleKafkaOutboxConstant.MDC_X_TRACE_ID),
                "原本缺失的 X-B3-TraceId 应保持缺失");
    }

    @Test
    public void testNestedScopesRestoreInStackOrder() {
        MDC.put(SimpleKafkaOutboxConstant.MDC_TRACE_ID, "root-trace");
        KafkaOutboxTraceScope.Scope outer = traceScope.open("outer-trace");
        KafkaOutboxTraceScope.Scope inner = traceScope.open("inner-trace");

        log.info("嵌套作用域最内层 traceId: {}", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID));
        assertEquals("inner-trace", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                "内层作用域应覆盖外层 traceId");
        inner.close();
        log.info("关闭内层后的 traceId: {}", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID));
        assertEquals("outer-trace", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                "关闭内层后应恢复外层 traceId");
        outer.close();
        log.info("关闭外层后的 traceId: {}", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID));
        assertEquals("root-trace", MDC.get(SimpleKafkaOutboxConstant.MDC_TRACE_ID),
                "关闭外层后应恢复根 traceId");
    }
}
