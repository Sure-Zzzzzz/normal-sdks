package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.DefaultBulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration.SimpleElasticsearchPersistenceProperties;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 受控协议响应只用于契约断言，不代替真实 ES 验收。
 */
public class PersistenceProtocolFixture {
    public final PersistenceTargetResolver targets = mock(PersistenceTargetResolver.class);
    public final PersistenceRestHelper rest = mock(PersistenceRestHelper.class);
    public final PersistencePayloadCodec codec = new JacksonPersistencePayloadCodec();
    public final SimpleElasticsearchPersistenceProperties properties = new SimpleElasticsearchPersistenceProperties();
    public final List<Call> calls = new ArrayList<>();
    public final List<Object> events = new ArrayList<>();
    public final Deque<Object> responses = new ArrayDeque<>();
    public ElasticsearchWriteApiHelper helper;

    /**
     * 默认使用无处理器链；路由结果固定，专门观察协议而不模拟路由算法。
     */
    public PersistenceProtocolFixture() {
        when(targets.writeIndex(anyString())).thenAnswer(call -> call.getArgument(0));
        when(targets.physicalIndex(anyString())).thenAnswer(call -> call.getArgument(0));
        when(targets.datasource(any(String[].class))).thenReturn("sample");
        when(targets.hasDatasource("sample")).thenReturn(true);
        when(rest.perform(anyString(), anyString(), anyString(), anyMap(), nullable(String.class))).thenAnswer(call -> {
            calls.add(new Call(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                    new LinkedHashMap<>(call.getArgument(3)), call.getArgument(4)));
            if (responses.isEmpty()) throw new AssertionError("存在未声明的外部调用");
            Object response = responses.removeFirst();
            if (response instanceof RuntimeException) throw (RuntimeException) response;
            return response;
        });
        configure(new DocumentPreProcessorChain(List.of()), events::add);
    }

    /**
     * 构造单写协议响应，便于故意破坏某一个字段。
     */
    public static Map<String, Object> single(String result) {
        return Map.of("_index", "sample-record", "_id", "row1", "result", result);
    }

    /**
     * 构造 bulk 逐项状态；失败原因标记不得进入公开结果或事件。
     */
    public static Map<String, Object> bulk(String operation, int... statuses) {
        List<Object> items = new ArrayList<>();
        for (int status : statuses) {
            Map<String, Object> state = new LinkedHashMap<>(Map.of("status", status));
            if (status >= 300) state.put("error", Map.of("type", "sample_error", "reason", "private-detail"));
            items.add(Map.of(operation, state));
        }
        return Map.of("items", items);
    }

    /**
     * 替换实际处理链和事件发布者，仍执行生产协议编译器。
     */
    public void configure(DocumentPreProcessorChain chain, ApplicationEventPublisher publisher) {
        helper = new ElasticsearchWriteApiHelper(targets, codec, rest, chain,
                new DefaultBulkFailureClassifier(), properties, publisher);
    }

    /**
     * 执行完整准备与协议结果转换。
     */
    public Object execute(PersistenceRequest request) {
        return helper.prepare(request, false).get();
    }

    /**
     * 保留不可变调用快照，断言基于实际编译结果。
     */
    @Getter
    @RequiredArgsConstructor
    public static class Call {
        private final String source, method, path;
        private final Map<String, String> parameters;
        private final String body;
    }
}
