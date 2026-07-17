package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTraceResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 默认 Kafka 发布 traceId 解析器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaPublishTraceResolverTest {

    private final DefaultKafkaPublishTraceResolver resolver = new DefaultKafkaPublishTraceResolver();

    @AfterEach
    public void clearMdc() {
        MDC.clear();
    }

    @Test
    public void testReturnsTrimmedHighestPriorityTraceId() {
        MDC.put(SimpleKafkaPublisherConstant.MDC_TRACE_ID, "  mock-primary-trace  ");
        MDC.put(SimpleKafkaPublisherConstant.MDC_TRACE_ID_WITH_HYPHEN, "mock-secondary-trace");

        String traceId = resolver.resolveTraceId();

        log.info("最高优先级 traceId 解析结果: {}", traceId);
        assertEquals("mock-primary-trace", traceId, "应返回 trim 后的最高优先级 traceId");
    }

    @Test
    public void testBlankCandidatesFallBackToXTraceId() {
        MDC.put(SimpleKafkaPublisherConstant.MDC_TRACE_ID, " ");
        MDC.put(SimpleKafkaPublisherConstant.MDC_TRACE_ID_WITH_HYPHEN, "\t");
        MDC.put(SimpleKafkaPublisherConstant.MDC_X_TRACE_ID, "  mock-x-trace  ");

        String traceId = resolver.resolveTraceId();

        log.info("blank fallback traceId 解析结果: {}", traceId);
        assertEquals("mock-x-trace", traceId, "blank 高优先级候选应继续回退并返回 trim 后结果");
    }

    @Test
    public void testAllBlankReturnsNull() {
        MDC.put(SimpleKafkaPublisherConstant.MDC_TRACE_ID, " ");
        MDC.put(SimpleKafkaPublisherConstant.MDC_TRACE_ID_WITH_HYPHEN, "\t");
        MDC.put(SimpleKafkaPublisherConstant.MDC_X_TRACE_ID, "  ");

        String traceId = resolver.resolveTraceId();

        log.info("全部 blank traceId 解析结果: {}", traceId);
        assertNull(traceId, "全部 MDC 候选为 blank 时应返回 null");
    }
}
