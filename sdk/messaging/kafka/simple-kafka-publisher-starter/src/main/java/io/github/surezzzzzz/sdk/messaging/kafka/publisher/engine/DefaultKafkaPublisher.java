package io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine;

import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.SimpleKafkaPublisherConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishEnvelopeCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishHeaderCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.*;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.KafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.KafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.support.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 默认 Kafka 发布门面
 *
 * @author surezzzzzz
 */
public class DefaultKafkaPublisher implements KafkaPublisher {

    private final KafkaRouteTemplate kafkaRouteTemplate;
    private final SimpleKafkaPublisherProperties properties;
    private final KafkaPublishSerializer serializer;
    private final KafkaPublishTopicResolver topicResolver;
    private final KafkaPublishKeyResolver keyResolver;
    private final KafkaPublishRouteKeyResolver routeKeyResolver;
    private final KafkaPublishMessageIdGenerator messageIdGenerator;
    private final KafkaPublishTraceResolver traceResolver;
    private final KafkaPublishClock clock;
    private final List<KafkaPublishHeaderCustomizer> headerCustomizers;
    private final List<KafkaPublishEnvelopeCustomizer> envelopeCustomizers;

    /**
     * 创建默认 Kafka 发布门面
     *
     * @param kafkaRouteTemplate  route 模板
     * @param properties          Publisher 配置
     * @param serializer          序列化器
     * @param topicResolver       topic 解析器
     * @param keyResolver         key 解析器
     * @param routeKeyResolver    routeKey 解析器
     * @param messageIdGenerator  messageId 生成器
     * @param traceResolver       traceId 解析器
     * @param clock               发布时钟
     * @param headerCustomizers   header 自定义器
     * @param envelopeCustomizers envelope 自定义器
     */
    public DefaultKafkaPublisher(KafkaRouteTemplate kafkaRouteTemplate,
                                 SimpleKafkaPublisherProperties properties,
                                 KafkaPublishSerializer serializer,
                                 KafkaPublishTopicResolver topicResolver,
                                 KafkaPublishKeyResolver keyResolver,
                                 KafkaPublishRouteKeyResolver routeKeyResolver,
                                 KafkaPublishMessageIdGenerator messageIdGenerator,
                                 KafkaPublishTraceResolver traceResolver,
                                 KafkaPublishClock clock,
                                 List<KafkaPublishHeaderCustomizer> headerCustomizers,
                                 List<KafkaPublishEnvelopeCustomizer> envelopeCustomizers) {
        this.kafkaRouteTemplate = kafkaRouteTemplate;
        this.properties = properties;
        this.serializer = serializer;
        this.topicResolver = topicResolver;
        this.keyResolver = keyResolver;
        this.routeKeyResolver = routeKeyResolver;
        this.messageIdGenerator = messageIdGenerator;
        this.traceResolver = traceResolver;
        this.clock = clock;
        this.headerCustomizers = sortedCopy(headerCustomizers);
        this.envelopeCustomizers = sortedCopy(envelopeCustomizers);
    }

    private <T> List<T> sortedCopy(List<T> source) {
        List<T> copy = source == null ? new ArrayList<>() : new ArrayList<>(source);
        AnnotationAwareOrderComparator.sort(copy);
        return Collections.unmodifiableList(copy);
    }

    /**
     * 指定最终消息 topic 异步发布，由 route 按 topic 规则选择 datasource
     *
     * @param topic   最终消息 topic
     * @param payload payload
     * @param <T>     payload 类型
     * @return 发布结果 Future
     */
    @Override
    public <T> ListenableFuture<KafkaPublishResult> publish(String topic, T payload) {
        KafkaPublishMessage<T> message = KafkaPublishMessage.<T>builder()
                .payload(payload)
                .build();
        return doPublish(PublishMode.TOPIC, topic, null, null, null, message);
    }

    /**
     * 指定最终消息 topic 和 record key 异步发布，由 route 按 topic 规则选择 datasource
     *
     * @param topic   最终消息 topic
     * @param key     Kafka record key
     * @param payload payload
     * @param <T>     payload 类型
     * @return 发布结果 Future
     */
    @Override
    public <T> ListenableFuture<KafkaPublishResult> publish(String topic, String key, T payload) {
        KafkaPublishMessage<T> message = KafkaPublishMessage.<T>builder()
                .payload(payload)
                .build();
        return doPublish(PublishMode.TOPIC, topic, key, null, null, message);
    }

    /**
     * 按消息字段异步发布，依次按 datasourceKey、routeKey、topic 选择路由模式
     *
     * @param message 发布消息
     * @param <T>     payload 类型
     * @return 发布结果 Future
     */
    @Override
    public <T> ListenableFuture<KafkaPublishResult> publish(KafkaPublishMessage<T> message) {
        return doPublish(PublishMode.AUTO, null, null, null, null, message);
    }

    /**
     * 指定 routeKey 异步发布，由 route 按 routeKey 规则选择 datasource
     *
     * <p>routeKey 只用于选择 datasource，不改变最终消息 topic；本方法忽略 message.datasourceKey，
     * 且不调用 routeKey resolver。
     *
     * @param routeKey 用于选择 datasource 的 routeKey
     * @param message  发布消息
     * @param <T>      payload 类型
     * @return 发布结果 Future
     */
    @Override
    public <T> ListenableFuture<KafkaPublishResult> publishByRouteKey(String routeKey, KafkaPublishMessage<T> message) {
        return doPublish(PublishMode.ROUTE_KEY, null, null, routeKey, null, message);
    }

    /**
     * 指定 datasource 异步发布，绕过 route 的 topic/routeKey 规则
     *
     * <p>本方法忽略 message.routeKey，且不调用 routeKey resolver；message 中的 topic 仍是最终消息 topic。
     *
     * @param datasourceKey 目标 datasource key
     * @param message       发布消息
     * @param <T>           payload 类型
     * @return 发布结果 Future
     */
    @Override
    public <T> ListenableFuture<KafkaPublishResult> publishOn(String datasourceKey, KafkaPublishMessage<T> message) {
        return doPublish(PublishMode.DATASOURCE, null, null, null, datasourceKey, message);
    }

    /**
     * 按消息字段同步发布并等待 broker 发送结果
     *
     * <p>路由语义与 {@link #publish(KafkaPublishMessage)} 一致，仅本方法使用 send.timeout-ms。
     * 等待超时或被中断都表示发送状态未知，调用方不应盲目重试，以免重复投递。
     *
     * @param message 发布消息
     * @param <T>     payload 类型
     * @return broker 确认后的发布结果
     */
    @Override
    public <T> KafkaPublishResult publishAndWait(KafkaPublishMessage<T> message) {
        PublishContext<T> context = prepareContext(PublishMode.AUTO, null, null, null, null, message);
        try {
            return send(context).get(properties.getSend().getTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_008,
                    String.format(ErrorMessage.SEND_TIMEOUT, context.topic, context.messageType,
                            context.messageId, properties.getSend().getTimeoutMs()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_011,
                    String.format(ErrorMessage.SEND_INTERRUPTED, context.topic, context.messageType,
                            context.messageId, SimpleKafkaPublisherConstant.REASON_SEND_INTERRUPTED), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof KafkaPublishException) {
                throw (KafkaPublishException) cause;
            }
            throw sendFailed(context, cause);
        }
    }

    private <T> ListenableFuture<KafkaPublishResult> doPublish(PublishMode publishMode, String topic, String key,
                                                               String routeKey, String datasourceKey,
                                                               KafkaPublishMessage<T> message) {
        PublishContext<T> context = prepareContext(publishMode, topic, key, routeKey, datasourceKey, message);
        return send(context);
    }

    private <T> PublishContext<T> prepareContext(PublishMode publishMode, String topic, String key,
                                                 String routeKey, String datasourceKey,
                                                 KafkaPublishMessage<T> message) {
        validateMessage(message);
        PublishContext<T> context = new PublishContext<>();
        context.message = message;
        context.mode = publishMode;
        context.topic = resolveTopic(topic, message);
        context.key = resolveKey(key, message);
        context.messageId = resolveMessageId(message);
        context.messageType = message.getMessageType();
        context.traceId = traceResolver.resolveTraceId();
        context.publishedAt = clock.currentTimeMillis();
        context.envelopeEnabled = resolveEnvelopeEnabled(message);
        validateAppName(context.envelopeEnabled);
        context.datasourceKey = resolveDatasourceKey(publishMode, datasourceKey, message);
        context.routeKey = resolveRouteKey(publishMode, routeKey, message, context.datasourceKey);
        validateTopic(context.topic);
        validatePayload(message, context.messageId);
        validatePartition(message.getPartition());
        validateTimestamp(message.getTimestamp());
        context.value = serialize(context);
        context.headers = buildRecordHeaders(context);
        context.record = new ProducerRecord<>(context.topic, message.getPartition(), message.getTimestamp(),
                context.key, context.value, context.headers);
        return context;
    }

    private <T> ListenableFuture<KafkaPublishResult> send(PublishContext<T> context) {
        final ListenableFuture<SendResult<String, String>> sendFuture;
        try {
            if (context.mode == PublishMode.DATASOURCE || context.datasourceKey != null) {
                sendFuture = kafkaRouteTemplate.sendOn(context.datasourceKey, context.record);
            } else if (context.routeKey != null) {
                sendFuture = kafkaRouteTemplate.sendByRouteKey(context.routeKey, context.record);
            } else {
                sendFuture = kafkaRouteTemplate.send(context.record);
            }
        } catch (RuntimeException e) {
            SettableListenableFuture<KafkaPublishResult> failedFuture = new SettableListenableFuture<>();
            failedFuture.setException(sendFailed(context, e));
            return failedFuture;
        }
        if (sendFuture == null) {
            SettableListenableFuture<KafkaPublishResult> failedFuture = new SettableListenableFuture<>();
            failedFuture.setException(sendFailed(context,
                    new IllegalStateException(SimpleKafkaPublisherConstant.REASON_SEND_FUTURE_EMPTY)));
            return failedFuture;
        }
        final SettableListenableFuture<KafkaPublishResult> resultFuture =
                new SettableListenableFuture<KafkaPublishResult>() {
                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        boolean cancelled = super.cancel(mayInterruptIfRunning);
                        if (cancelled) {
                            sendFuture.cancel(mayInterruptIfRunning);
                        }
                        return cancelled;
                    }
                };
        sendFuture.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onFailure(Throwable ex) {
                resultFuture.setException(sendFailed(context, ex));
            }

            @Override
            public void onSuccess(SendResult<String, String> result) {
                if (result == null || result.getRecordMetadata() == null) {
                    resultFuture.setException(sendFailed(context,
                            new IllegalStateException(SimpleKafkaPublisherConstant.REASON_SEND_RESULT_EMPTY)));
                    return;
                }
                resultFuture.set(buildResult(context, result));
            }
        });
        return resultFuture;
    }

    private <T> KafkaPublishResult buildResult(PublishContext<T> context, SendResult<String, String> result) {
        return KafkaPublishResult.builder()
                .messageId(context.messageId)
                .topic(result.getRecordMetadata().topic())
                .key(context.key)
                .partition(result.getRecordMetadata().partition())
                .offset(result.getRecordMetadata().offset())
                .timestamp(result.getRecordMetadata().timestamp())
                .datasourceKey(context.mode == PublishMode.DATASOURCE ? context.datasourceKey : null)
                .build();
    }

    private <T> String serialize(PublishContext<T> context) {
        KafkaPublishEnvelope<T> envelope = null;
        if (context.envelopeEnabled) {
            Map<String, Object> attributes = context.message.getAttributes() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(context.message.getAttributes());
            envelope = KafkaPublishEnvelope.<T>builder()
                    .messageId(context.messageId)
                    .messageType(context.messageType)
                    .source(properties.getAppName())
                    .timestamp(context.publishedAt)
                    .traceId(context.traceId)
                    .payload(context.message.getPayload())
                    .attributes(attributes)
                    .build();
            customizeEnvelope(envelope);
        }
        return serializer.serialize(KafkaPublishSerializeContext.builder()
                .topic(context.topic)
                .messageId(context.messageId)
                .messageType(context.messageType)
                .payload(context.message.getPayload())
                .envelope(envelope)
                .envelopeEnabled(context.envelopeEnabled)
                .build());
    }

    private void customizeEnvelope(KafkaPublishEnvelope<?> envelope) {
        Map<String, Object> attributes = new LinkedHashMap<>(envelope.getAttributes());
        KafkaPublishEnvelopeContext context = KafkaPublishEnvelopeContext.builder()
                .messageId(envelope.getMessageId())
                .messageType(envelope.getMessageType())
                .source(envelope.getSource())
                .timestamp(envelope.getTimestamp())
                .traceId(envelope.getTraceId())
                .attributes(attributes)
                .build();
        for (KafkaPublishEnvelopeCustomizer customizer : envelopeCustomizers) {
            customizer.customize(context);
        }
        // customizer 只能操作 context 持有的 attributes 副本，执行完毕后显式回写到 envelope，
        // 避免依赖"envelope 与 context 共享同一 map 引用"的隐式副作用。
        envelope.getAttributes().clear();
        envelope.getAttributes().putAll(context.getAttributes());
    }

    private <T> List<Header> buildRecordHeaders(PublishContext<T> context) {
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, String> defaultHeaders = new LinkedHashMap<>();
        if (properties.getHeaders().isEnableDefaultHeaders()) {
            putDefaultHeader(defaultHeaders, properties.getHeaders().getMessageIdHeader(), context.messageId);
            putDefaultHeader(defaultHeaders, properties.getHeaders().getMessageTypeHeader(), context.messageType);
            putDefaultHeader(defaultHeaders, properties.getHeaders().getTraceIdHeader(), context.traceId);
            putDefaultHeader(defaultHeaders, properties.getHeaders().getSourceHeader(), properties.getAppName());
            putDefaultHeader(defaultHeaders, properties.getHeaders().getPublishedAtHeader(), String.valueOf(context.publishedAt));
            headers.putAll(defaultHeaders);
        }
        mergeMessageHeaders(headers, context.message.getHeaders(), defaultHeaders);
        customizeHeaders(context, headers);
        Map<String, String> normalizedHeaders = normalizeAndValidateHeaders(headers, defaultHeaders);
        List<Header> recordHeaders = new ArrayList<>();
        for (Map.Entry<String, String> entry : normalizedHeaders.entrySet()) {
            recordHeaders.add(new RecordHeader(entry.getKey(),
                    entry.getValue().getBytes(SimpleKafkaPublisherConstant.CHARSET_UTF_8)));
        }
        return recordHeaders;
    }

    private void putDefaultHeader(Map<String, String> headers, String headerName, String headerValue) {
        String normalized = KafkaPublishHeaderHelper.normalizeHeaderName(headerName);
        if (normalized != null && headerValue != null) {
            headers.put(normalized, headerValue);
        }
    }

    private void mergeMessageHeaders(Map<String, String> headers, Map<String, String> messageHeaders,
                                     Map<String, String> defaultHeaders) {
        if (messageHeaders == null || messageHeaders.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : messageHeaders.entrySet()) {
            String headerName = KafkaPublishHeaderHelper.normalizeHeaderName(entry.getKey());
            validateHeaderName(headerName);
            validateHeaderValue(headerName, entry.getValue());
            if (!properties.getHeaders().isAllowHeaderOverride()
                    && KafkaPublishHeaderHelper.isReservedHeader(headerName, properties)
                    && containsNormalized(defaultHeaders, headerName)) {
                throw headerInvalid(headerName, SimpleKafkaPublisherConstant.REASON_HEADER_RESERVED);
            }
            headers.put(headerName, entry.getValue());
        }
    }

    private <T> void customizeHeaders(PublishContext<T> context, Map<String, String> headers) {
        KafkaPublishHeaderContext headerContext = KafkaPublishHeaderContext.builder()
                .topic(context.topic)
                .key(context.key)
                .messageId(context.messageId)
                .messageType(context.messageType)
                .traceId(context.traceId)
                .source(properties.getAppName())
                .publishedAt(context.publishedAt)
                .headers(headers)
                .build();
        for (KafkaPublishHeaderCustomizer customizer : headerCustomizers) {
            customizer.customize(headerContext);
        }
    }

    private Map<String, String> normalizeAndValidateHeaders(Map<String, String> headers,
                                                            Map<String, String> defaultHeaders) {
        Map<String, String> normalizedHeaders = new LinkedHashMap<>();
        Map<String, String> normalizedNames = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String headerName = KafkaPublishHeaderHelper.normalizeHeaderName(entry.getKey());
            validateHeaderName(headerName);
            validateHeaderValue(headerName, entry.getValue());
            String normalizedKey = headerName.toLowerCase(java.util.Locale.ROOT);
            String previousName = normalizedNames.get(normalizedKey);
            if (previousName != null) {
                if (!properties.getHeaders().isAllowHeaderOverride()) {
                    throw headerInvalid(headerName, SimpleKafkaPublisherConstant.REASON_HEADER_DUPLICATE);
                }
                normalizedHeaders.remove(previousName);
            }
            normalizedNames.put(normalizedKey, headerName);
            if (!properties.getHeaders().isAllowHeaderOverride()
                    && KafkaPublishHeaderHelper.isReservedHeader(headerName, properties)
                    && defaultHeaderChanged(defaultHeaders, headerName, entry.getValue())) {
                throw headerInvalid(headerName, SimpleKafkaPublisherConstant.REASON_HEADER_RESERVED);
            }
            normalizedHeaders.put(headerName, entry.getValue());
        }
        if (!properties.getHeaders().isAllowHeaderOverride()) {
            validateDefaultHeadersPresent(normalizedHeaders, defaultHeaders);
        }
        return normalizedHeaders;
    }

    private void validateDefaultHeadersPresent(Map<String, String> headers, Map<String, String> defaultHeaders) {
        for (Map.Entry<String, String> defaultEntry : defaultHeaders.entrySet()) {
            String currentValue = getNormalizedValue(headers, defaultEntry.getKey());
            if (!defaultEntry.getValue().equals(currentValue)) {
                throw headerInvalid(defaultEntry.getKey(), SimpleKafkaPublisherConstant.REASON_HEADER_RESERVED);
            }
        }
    }

    private String getNormalizedValue(Map<String, String> headers, String headerName) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean defaultHeaderChanged(Map<String, String> defaultHeaders, String headerName, String value) {
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                return !entry.getValue().equals(value);
            }
        }
        return KafkaPublishHeaderHelper.isReservedHeader(headerName, properties);
    }

    private boolean containsNormalized(Map<String, String> headers, String headerName) {
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    private void validateHeaderName(String headerName) {
        if (!KafkaPublishStringHelper.hasText(headerName)) {
            throw headerInvalid(headerName, SimpleKafkaPublisherConstant.REASON_HEADER_KEY_EMPTY);
        }
        if (KafkaPublishStringHelper.containsControlCharacter(headerName)) {
            throw headerInvalid(headerName, SimpleKafkaPublisherConstant.REASON_HEADER_KEY_CONTROL);
        }
    }

    private void validateHeaderValue(String headerName, String headerValue) {
        if (headerValue == null) {
            throw headerInvalid(headerName, SimpleKafkaPublisherConstant.REASON_HEADER_VALUE_NULL);
        }
    }

    private KafkaPublishException headerInvalid(String headerName, String reason) {
        return new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_009,
                String.format(ErrorMessage.HEADER_INVALID, headerName, reason));
    }

    private <T> String resolveTopic(String apiTopic, KafkaPublishMessage<T> message) {
        if (apiTopic != null) {
            return apiTopic;
        }
        if (KafkaPublishStringHelper.hasText(message.getTopic())) {
            return message.getTopic();
        }
        String resolvedTopic = topicResolver.resolveTopic(message);
        if (KafkaPublishStringHelper.hasText(resolvedTopic)) {
            return resolvedTopic;
        }
        return properties.getDefaultTopic();
    }

    private <T> String resolveKey(String apiKey, KafkaPublishMessage<T> message) {
        if (apiKey != null) {
            return apiKey;
        }
        if (message.getKey() != null) {
            return message.getKey();
        }
        return keyResolver.resolveKey(message);
    }

    private <T> String resolveDatasourceKey(PublishMode publishMode, String apiDatasourceKey,
                                            KafkaPublishMessage<T> message) {
        if (publishMode == PublishMode.DATASOURCE) {
            validateDatasourceKey(apiDatasourceKey);
            return apiDatasourceKey;
        }
        if (publishMode == PublishMode.AUTO && message.getDatasourceKey() != null) {
            validateDatasourceKey(message.getDatasourceKey());
            return message.getDatasourceKey();
        }
        return null;
    }

    private <T> String resolveRouteKey(PublishMode publishMode, String apiRouteKey,
                                       KafkaPublishMessage<T> message, String datasourceKey) {
        if (publishMode == PublishMode.ROUTE_KEY) {
            validateRouteKey(apiRouteKey);
            return apiRouteKey;
        }
        if (publishMode == PublishMode.AUTO && datasourceKey == null) {
            String resolvedRouteKey = routeKeyResolver.resolveRouteKey(message);
            if (resolvedRouteKey != null) {
                validateRouteKey(resolvedRouteKey);
            }
            return resolvedRouteKey;
        }
        return null;
    }

    private <T> String resolveMessageId(KafkaPublishMessage<T> message) {
        String messageId = KafkaPublishStringHelper.hasText(message.getMessageId())
                ? message.getMessageId() : messageIdGenerator.generateMessageId();
        if (!KafkaPublishStringHelper.hasText(messageId)) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_002,
                    String.format(ErrorMessage.MESSAGE_INVALID,
                            SimpleKafkaPublisherConstant.REASON_MESSAGE_ID_EMPTY));
        }
        return messageId;
    }

    private <T> boolean resolveEnvelopeEnabled(KafkaPublishMessage<T> message) {
        if (message.getEnvelopeEnabled() != null) {
            return message.getEnvelopeEnabled();
        }
        return properties.getEnvelope().isEnable();
    }

    private void validateAppName(boolean envelopeEnabled) {
        if ((envelopeEnabled || properties.getHeaders().isEnableDefaultHeaders())
                && !KafkaPublishStringHelper.hasText(properties.getAppName())) {
            throw new KafkaPublishConfigurationException(ErrorCode.KAFKA_PUBLISHER_001,
                    String.format(ErrorMessage.CONFIG_INVALID, SimpleKafkaPublisherConstant.REASON_APP_NAME_EMPTY));
        }
    }

    private void validateMessage(KafkaPublishMessage<?> message) {
        if (message == null) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_002,
                    String.format(ErrorMessage.MESSAGE_INVALID, SimpleKafkaPublisherConstant.REASON_MESSAGE_EMPTY));
        }
    }

    private void validateTopic(String topic) {
        if (!KafkaPublishStringHelper.hasText(topic)) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_004, ErrorMessage.TOPIC_EMPTY);
        }
    }

    private void validateRouteKey(String routeKey) {
        if (!KafkaPublishStringHelper.hasText(routeKey)) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_010,
                    String.format(ErrorMessage.ROUTE_INPUT_INVALID, SimpleKafkaPublisherConstant.REASON_ROUTE_KEY_EMPTY));
        }
    }

    private void validateDatasourceKey(String datasourceKey) {
        if (!KafkaPublishStringHelper.hasText(datasourceKey)) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_010,
                    String.format(ErrorMessage.ROUTE_INPUT_INVALID, SimpleKafkaPublisherConstant.REASON_DATASOURCE_KEY_EMPTY));
        }
    }

    private void validatePayload(KafkaPublishMessage<?> message, String messageId) {
        if (message.getPayload() == null && !properties.getEnvelope().isIncludeNullPayload()) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_003,
                    String.format(ErrorMessage.PAYLOAD_INVALID, message.getMessageType(), messageId,
                            SimpleKafkaPublisherConstant.REASON_PAYLOAD_EMPTY));
        }
    }

    private void validatePartition(Integer partition) {
        if (partition != null && partition < SimpleKafkaPublisherConstant.ZERO) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_005,
                    String.format(ErrorMessage.RECORD_INVALID,
                            SimpleKafkaPublisherConstant.REASON_PARTITION_NEGATIVE));
        }
    }

    private void validateTimestamp(Long timestamp) {
        if (timestamp != null && timestamp < SimpleKafkaPublisherConstant.ZERO) {
            throw new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_005,
                    String.format(ErrorMessage.RECORD_INVALID,
                            SimpleKafkaPublisherConstant.REASON_TIMESTAMP_NEGATIVE));
        }
    }

    private KafkaPublishException sendFailed(PublishContext<?> context, Throwable cause) {
        return new KafkaPublishException(ErrorCode.KAFKA_PUBLISHER_007,
                String.format(ErrorMessage.SEND_FAILED, context.topic, context.messageType, context.messageId), cause);
    }

    private enum PublishMode {
        AUTO,
        TOPIC,
        ROUTE_KEY,
        DATASOURCE
    }

    private static class PublishContext<T> {
        private PublishMode mode;
        private KafkaPublishMessage<T> message;
        private String topic;
        private String key;
        private String routeKey;
        private String datasourceKey;
        private String messageId;
        private String messageType;
        private String traceId;
        private Long publishedAt;
        private boolean envelopeEnabled;
        private String value;
        private List<Header> headers;
        private ProducerRecord<String, String> record;
    }
}
