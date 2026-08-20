package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientFactory;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteDiagnosticStatus;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.DefaultKafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail.KafkaConsumerGroupDetailRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.detail.KafkaConsumerGroupDetailResponse;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag.KafkaConsumerGroupLagListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.consumer.lag.KafkaConsumerGroupLagListResponse;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.datasource.KafkaDatasourceListResponse;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.config.KafkaTopicConfigRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.config.KafkaTopicConfigResponse;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.list.KafkaTopicListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime.KafkaTopicRuntimeRequest;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.runtime.KafkaTopicRuntimeResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Route callback 资源边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultKafkaOperationsViewAdapterTest {

    @Test
    void shouldMapKafkaDeadlineToGatewayTimeoutInsideRouteCallback() throws Exception {
        AdminClient adminClient = Mockito.mock(AdminClient.class);
        ListTopicsResult result = Mockito.mock(ListTopicsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<java.util.Set<String>> topicNames = Mockito.mock(KafkaFuture.class);
        Mockito.when(adminClient.listTopics()).thenReturn(result);
        Mockito.when(result.names()).thenReturn(topicNames);
        Mockito.when(topicNames.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new TimeoutException("sensitive endpoint detail"));

        AtomicBoolean callbackOpen = new AtomicBoolean();
        AtomicBoolean callbackCompleted = new AtomicBoolean();
        KafkaRouteAdminClientFactory factory = new KafkaRouteAdminClientFactory() {
            @Override
            public <T> T withAdminClient(String datasourceKey,
                                         io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientCallback<T> callback) {
                callbackOpen.set(true);
                try {
                    return callback.doWithAdminClient(adminClient);
                } finally {
                    callbackOpen.set(false);
                    callbackCompleted.set(true);
                }
            }
        };
        SimpleKafkaRouteRegistry registry = Mockito.mock(SimpleKafkaRouteRegistry.class);
        Mockito.when(registry.getDatasourceKeys()).thenReturn(Collections.singleton("default"));
        Mockito.when(registry.containsDatasource("default")).thenReturn(true);
        DefaultKafkaOperationsViewAdapter adapter = new DefaultKafkaOperationsViewAdapter(
                registry, Mockito.mock(KafkaRouteDiagnostics.class), factory, 100L, 10);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> adapter.listTopics(KafkaTopicListRequest.builder().datasourceKey("default").size(10).build()));
        log.info("Kafka deadline 映射结果：status={}，message={}", exception.getStatus(), exception.getMessage());

        assertEquals(504, exception.getStatus().value());
        assertEquals("Kafka 运维查询已超时", exception.getMessage());
        assertTrue(callbackCompleted.get());
        assertFalse(callbackOpen.get());
        Mockito.verify(topicNames).cancel(true);
        ArgumentCaptor<Long> timeoutNanos = ArgumentCaptor.forClass(Long.class);
        Mockito.verify(topicNames).get(timeoutNanos.capture(), Mockito.eq(TimeUnit.NANOSECONDS));
        assertTrue(timeoutNanos.getValue() > 0L && timeoutNanos.getValue() <= TimeUnit.MILLISECONDS.toNanos(100L));
    }

    @Test
    void shouldRetainKafkaExecutionFailureCause() throws Exception {
        AdminClient adminClient = Mockito.mock(AdminClient.class);
        ListTopicsResult result = Mockito.mock(ListTopicsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Set<String>> topicNames = Mockito.mock(KafkaFuture.class);
        IOException failure = new IOException("fixture failure");
        Mockito.when(adminClient.listTopics()).thenReturn(result);
        Mockito.when(result.names()).thenReturn(topicNames);
        Mockito.when(topicNames.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS)))
                .thenThrow(new ExecutionException(failure));

        DefaultKafkaOperationsViewAdapter adapter = adapter(adminClient, 10);
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> adapter.listTopics(KafkaTopicListRequest.builder().datasourceKey("default").size(10).build()));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Kafka 运维查询暂不可用", exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    @Test
    void shouldReturnSortedLimitedTopicWindow() throws Exception {
        AdminClient adminClient = Mockito.mock(AdminClient.class);
        ListTopicsResult result = Mockito.mock(ListTopicsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Set<String>> topicNames = Mockito.mock(KafkaFuture.class);
        Mockito.when(adminClient.listTopics()).thenReturn(result);
        Mockito.when(result.names()).thenReturn(topicNames);
        Mockito.when(topicNames.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS)))
                .thenReturn(Collections.unmodifiableSet(new java.util.LinkedHashSet<>(Arrays.asList(
                        "topic-c", "topic-a", "topic-b"))));

        KafkaRouteAdminClientFactory factory = new KafkaRouteAdminClientFactory() {
            @Override
            public <T> T withAdminClient(String datasourceKey,
                                         io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientCallback<T> callback) {
                return callback.doWithAdminClient(adminClient);
            }
        };
        SimpleKafkaRouteRegistry registry = Mockito.mock(SimpleKafkaRouteRegistry.class);
        Mockito.when(registry.getDatasourceKeys()).thenReturn(Collections.singleton("default"));
        Mockito.when(registry.containsDatasource("default")).thenReturn(true);
        DefaultKafkaOperationsViewAdapter adapter = new DefaultKafkaOperationsViewAdapter(
                registry, Mockito.mock(KafkaRouteDiagnostics.class), factory, 100L, 10);

        io.github.surezzzzzz.sdk.ops.middleware.kafka.topic.list.KafkaTopicListResponse response = adapter.listTopics(
                KafkaTopicListRequest.builder().datasourceKey("default").size(2).build());
        log.info("Kafka 首窗口结果：items={}", response.getItems());

        assertEquals(2, response.getItems().size());
        assertEquals("topic-a", response.getItems().get(0).getName());
        assertEquals("topic-b", response.getItems().get(1).getName());
    }

    @Test
    void shouldProjectRouteDiagnosticReasonWithoutInferringCapabilities() {
        SimpleKafkaRouteRegistry registry = Mockito.mock(SimpleKafkaRouteRegistry.class);
        Mockito.when(registry.getDatasourceKeys()).thenReturn(new java.util.LinkedHashSet<>(
                Arrays.asList("route-reason", "legacy-warn", "success")));
        KafkaRouteDiagnostics diagnostics = Mockito.mock(KafkaRouteDiagnostics.class);
        Mockito.when(diagnostics.getDiagnosticResult("route-reason")).thenReturn(KafkaRouteBrokerDiagnosticResult.builder()
                .status(KafkaRouteDiagnosticStatus.WARN)
                .diagnosticReason("已配置事务生产者，但 broker Feature API 未确认事务能力").build());
        Mockito.when(diagnostics.getDiagnosticResult("legacy-warn")).thenReturn(KafkaRouteBrokerDiagnosticResult.builder()
                .status(KafkaRouteDiagnosticStatus.WARN).build());
        Mockito.when(diagnostics.getDiagnosticResult("success")).thenReturn(KafkaRouteBrokerDiagnosticResult.builder()
                .status(KafkaRouteDiagnosticStatus.SUCCESS)
                .diagnosticReason("不应展示的原因").build());

        DefaultKafkaOperationsViewAdapter adapter = new DefaultKafkaOperationsViewAdapter(registry, diagnostics,
                Mockito.mock(KafkaRouteAdminClientFactory.class), 100L, 10);
        KafkaDatasourceListResponse response = adapter.listDatasources();

        assertEquals("已配置事务生产者，但 broker Feature API 未确认事务能力",
                response.getItems().get(0).getDiagnosticReason());
        assertEquals("Kafka Route 诊断存在待确认的生产者能力", response.getItems().get(1).getDiagnosticReason());
        assertNull(response.getItems().get(2).getDiagnosticReason());
    }

    @Test
    void shouldLimitTopicRuntimeBeforeRequestingOffsets() throws Exception {
        String topic = "orders";
        Node leader = new Node(1, "broker-1", 9092);
        TopicPartitionInfo partition0Info = new TopicPartitionInfo(0, leader, Collections.singletonList(leader),
                Collections.singletonList(leader));
        TopicPartitionInfo partition1Info = new TopicPartitionInfo(1, leader, Collections.singletonList(leader),
                Collections.singletonList(leader));
        TopicPartitionInfo partition2Info = new TopicPartitionInfo(2, leader, Collections.singletonList(leader),
                Collections.singletonList(leader));
        List<TopicPartitionInfo> partitionInfos = Arrays.asList(partition2Info, partition0Info, partition1Info);
        TopicDescription description = new TopicDescription(topic, false, partitionInfos);
        TopicDescription shortDescription = new TopicDescription(topic, false,
                Arrays.asList(partition1Info, partition0Info));

        AdminClient adminClient = Mockito.mock(AdminClient.class);
        DescribeTopicsResult describeTopicsResult = Mockito.mock(DescribeTopicsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, TopicDescription>> descriptions = Mockito.mock(KafkaFuture.class);
        Mockito.when(adminClient.describeTopics(Mockito.anyCollection())).thenReturn(describeTopicsResult);
        Mockito.when(describeTopicsResult.all()).thenReturn(descriptions);
        Mockito.when(descriptions.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS))).thenReturn(
                Collections.singletonMap(topic, description), Collections.singletonMap(topic, description),
                Collections.singletonMap(topic, shortDescription));

        ListOffsetsResult earliestResult = Mockito.mock(ListOffsetsResult.class);
        ListOffsetsResult latestResult = Mockito.mock(ListOffsetsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> earliestOffsets = Mockito.mock(KafkaFuture.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>> latestOffsets = Mockito.mock(KafkaFuture.class);
        Mockito.when(adminClient.listOffsets(Mockito.anyMap())).thenReturn(
                earliestResult, latestResult, earliestResult, latestResult, earliestResult, latestResult);
        Mockito.when(earliestResult.all()).thenReturn(earliestOffsets);
        Mockito.when(latestResult.all()).thenReturn(latestOffsets);
        TopicPartition partition0 = new TopicPartition(topic, 0);
        TopicPartition partition1 = new TopicPartition(topic, 1);
        TopicPartition partition2 = new TopicPartition(topic, 2);
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliest = new LinkedHashMap<>();
        earliest.put(partition0, new ListOffsetsResult.ListOffsetsResultInfo(10L, -1L, Optional.of(1)));
        earliest.put(partition1, new ListOffsetsResult.ListOffsetsResultInfo(20L, -1L, Optional.of(1)));
        earliest.put(partition2, new ListOffsetsResult.ListOffsetsResultInfo(30L, -1L, Optional.of(1)));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = new LinkedHashMap<>();
        latest.put(partition0, new ListOffsetsResult.ListOffsetsResultInfo(15L, -1L, Optional.of(1)));
        latest.put(partition1, new ListOffsetsResult.ListOffsetsResultInfo(28L, -1L, Optional.of(1)));
        latest.put(partition2, new ListOffsetsResult.ListOffsetsResultInfo(35L, -1L, Optional.of(1)));
        Mockito.when(earliestOffsets.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS))).thenReturn(earliest);
        Mockito.when(latestOffsets.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS))).thenReturn(latest);

        KafkaRouteAdminClientFactory factory = new KafkaRouteAdminClientFactory() {
            @Override
            public <T> T withAdminClient(String datasourceKey,
                                         io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientCallback<T> callback) {
                return callback.doWithAdminClient(adminClient);
            }
        };
        SimpleKafkaRouteRegistry registry = Mockito.mock(SimpleKafkaRouteRegistry.class);
        Mockito.when(registry.getDatasourceKeys()).thenReturn(Collections.singleton("default"));
        Mockito.when(registry.containsDatasource("default")).thenReturn(true);
        DefaultKafkaOperationsViewAdapter adapter = new DefaultKafkaOperationsViewAdapter(
                registry, Mockito.mock(KafkaRouteDiagnostics.class), factory, 100L, 2);

        KafkaTopicRuntimeResponse response = adapter.getTopicRuntime(KafkaTopicRuntimeRequest.builder()
                .datasourceKey("default").topic(topic).build());

        assertTrue(response.isTruncated());
        assertEquals(2, response.getPartitions().size());
        assertEquals(0, response.getPartitions().get(0).getPartition());
        assertEquals(1, response.getPartitions().get(1).getPartition());
        assertEquals(Integer.valueOf(1), response.getPartitions().get(0).getLeader());
        assertEquals(Collections.singletonList(1), response.getPartitions().get(0).getReplicas());
        assertEquals(Collections.singletonList(1), response.getPartitions().get(0).getInSyncReplicas());
        assertEquals(10L, response.getPartitions().get(0).getEarliestOffset());
        assertEquals(15L, response.getPartitions().get(0).getLatestOffset());

        KafkaTopicRuntimeResponse equalToLimit = new DefaultKafkaOperationsViewAdapter(
                registry, Mockito.mock(KafkaRouteDiagnostics.class), factory, 100L, 3).getTopicRuntime(
                KafkaTopicRuntimeRequest.builder().datasourceKey("default").topic(topic).build());
        assertFalse(equalToLimit.isTruncated());
        assertEquals(3, equalToLimit.getPartitions().size());

        KafkaTopicRuntimeResponse belowLimit = new DefaultKafkaOperationsViewAdapter(
                registry, Mockito.mock(KafkaRouteDiagnostics.class), factory, 100L, 3).getTopicRuntime(
                KafkaTopicRuntimeRequest.builder().datasourceKey("default").topic(topic).build());
        assertFalse(belowLimit.isTruncated());
        assertEquals(2, belowLimit.getPartitions().size());

        ArgumentCaptor<Map> specifications = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(adminClient, Mockito.times(6)).listOffsets(specifications.capture());
        List<Map> requests = specifications.getAllValues();
        assertEquals(Arrays.asList(partition0, partition1), new java.util.ArrayList<>(requests.get(0).keySet()));
        assertEquals(Arrays.asList(partition0, partition1), new java.util.ArrayList<>(requests.get(1).keySet()));
        assertFalse(requests.get(0).containsKey(partition2));
        assertFalse(requests.get(1).containsKey(partition2));
        assertEquals(Arrays.asList(partition0, partition1, partition2), new java.util.ArrayList<>(requests.get(2).keySet()));
        assertEquals(Arrays.asList(partition0, partition1, partition2), new java.util.ArrayList<>(requests.get(3).keySet()));
        assertEquals(Arrays.asList(partition0, partition1), new java.util.ArrayList<>(requests.get(4).keySet()));
        assertEquals(Arrays.asList(partition0, partition1), new java.util.ArrayList<>(requests.get(5).keySet()));
    }

    @Test
    void shouldReturnEmptyTopicRuntimeWithoutSubmittingEmptyOffsetRequest() throws Exception {
        AdminClient adminClient = Mockito.mock(AdminClient.class);
        DescribeTopicsResult describeTopicsResult = Mockito.mock(DescribeTopicsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, TopicDescription>> descriptions = Mockito.mock(KafkaFuture.class);
        TopicDescription description = new TopicDescription("empty-topic", false, Collections.emptyList());
        Mockito.when(adminClient.describeTopics(Mockito.anyCollection())).thenReturn(describeTopicsResult);
        Mockito.when(describeTopicsResult.all()).thenReturn(descriptions);
        Mockito.when(descriptions.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS)))
                .thenReturn(Collections.singletonMap("empty-topic", description));

        KafkaTopicRuntimeResponse response = adapter(adminClient, 10).getTopicRuntime(KafkaTopicRuntimeRequest.builder()
                .datasourceKey("default").topic("empty-topic").build());

        assertTrue(response.getPartitions().isEmpty());
        assertFalse(response.isTruncated());
        Mockito.verify(adminClient, Mockito.never()).listOffsets(Mockito.anyMap());
    }

    @Test
    void shouldReturnEmptyConsumerGroupLagWithoutSubmittingEmptyOffsetRequest() throws Exception {
        AdminClient adminClient = Mockito.mock(AdminClient.class);
        ListConsumerGroupOffsetsResult result = Mockito.mock(ListConsumerGroupOffsetsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>> offsets = Mockito.mock(KafkaFuture.class);
        Mockito.when(adminClient.listConsumerGroupOffsets("empty-group")).thenReturn(result);
        Mockito.when(result.partitionsToOffsetAndMetadata()).thenReturn(offsets);
        Mockito.when(offsets.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS)))
                .thenReturn(Collections.emptyMap());

        KafkaConsumerGroupLagListResponse response = adapter(adminClient, 10).getConsumerGroupLag(
                KafkaConsumerGroupLagListRequest.builder().datasourceKey("default").groupId("empty-group").size(10).build());

        assertTrue(response.getItems().isEmpty());
        assertFalse(response.getTruncated());
        Mockito.verify(adminClient, Mockito.never()).listOffsets(Mockito.anyMap());
    }

    @Test
    void shouldProjectOnlyAllowedNonSensitiveTopicConfiguration() throws Exception {
        AdminClient adminClient = Mockito.mock(AdminClient.class);
        DescribeConfigsResult result = Mockito.mock(DescribeConfigsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<ConfigResource, Config>> values = Mockito.mock(KafkaFuture.class);
        Config config = Mockito.mock(Config.class);
        ConfigEntry retention = Mockito.mock(ConfigEntry.class);
        ConfigEntry sensitive = Mockito.mock(ConfigEntry.class);
        ConfigEntry unsupported = Mockito.mock(ConfigEntry.class);
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, "orders");
        Mockito.when(adminClient.describeConfigs(Mockito.anyCollection())).thenReturn(result);
        Mockito.when(result.all()).thenReturn(values);
        Mockito.when(values.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS)))
                .thenReturn(Collections.singletonMap(resource, config));
        Mockito.when(config.entries()).thenReturn(Arrays.asList(retention, sensitive, unsupported));
        Mockito.when(retention.name()).thenReturn("retention.ms");
        Mockito.when(retention.value()).thenReturn("604800000");
        Mockito.when(retention.source()).thenReturn(ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG);
        Mockito.when(retention.isReadOnly()).thenReturn(false);
        Mockito.when(sensitive.name()).thenReturn("retention.bytes");
        Mockito.when(sensitive.isSensitive()).thenReturn(true);
        Mockito.when(unsupported.name()).thenReturn("unclean.leader.election.enable");
        Mockito.when(unsupported.isSensitive()).thenReturn(false);

        KafkaTopicConfigResponse response = adapter(adminClient, 10).getTopicConfig(KafkaTopicConfigRequest.builder()
                .datasourceKey("default").topic("orders").build());

        assertEquals("orders", response.getTopic());
        assertEquals(1, response.getItems().size());
        KafkaTopicConfigResponse.Item item = response.getItems().get(0);
        assertEquals("retention.ms", item.getName());
        assertEquals("604800000", item.getValue());
        assertEquals("DYNAMIC_TOPIC_CONFIG", item.getSource());
        assertFalse(item.getReadOnly());
        ArgumentCaptor<Collection> resources = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(adminClient).describeConfigs(resources.capture());
        assertEquals(Collections.singleton(resource), new LinkedHashSet<>(resources.getValue()));
    }

    @Test
    void shouldReportUnsupportedAndCompleteConsumerGroupAssignmentContracts() throws Exception {
        ConsumerGroupDescription unsupported = Mockito.mock(ConsumerGroupDescription.class);
        Mockito.when(unsupported.groupId()).thenReturn("legacy-group");
        Mockito.when(unsupported.isSimpleConsumerGroup()).thenReturn(true);
        Mockito.when(unsupported.members()).thenReturn(Collections.emptyList());
        KafkaConsumerGroupDetailResponse unsupportedResponse = getGroupDetail(unsupported, 10);
        assertEquals("simple", unsupportedResponse.getProtocolType());
        assertEquals("UNSUPPORTED_PROTOCOL", unsupportedResponse.getAssignmentStatus());
        assertTrue(unsupportedResponse.getAssignments().isEmpty());
        assertFalse(unsupportedResponse.getTruncated());

        ConsumerGroupDescription standard = Mockito.mock(ConsumerGroupDescription.class);
        Mockito.when(standard.groupId()).thenReturn("orders-group");
        Mockito.when(standard.isSimpleConsumerGroup()).thenReturn(false);
        Mockito.when(standard.partitionAssignor()).thenReturn("cooperative-sticky");
        Mockito.when(standard.members()).thenReturn(Collections.emptyList());
        KafkaConsumerGroupDetailResponse completeResponse = getGroupDetail(standard, 10);
        assertEquals("consumer", completeResponse.getProtocolType());
        assertEquals("COMPLETE", completeResponse.getAssignmentStatus());
        assertEquals(0, completeResponse.getMemberCount());
        assertTrue(completeResponse.getAssignments().isEmpty());
        assertFalse(completeResponse.getTruncated());
    }

    @Test
    void shouldMarkConsumerGroupAssignmentTruncatedAtServerBound() throws Exception {
        MemberDescription first = Mockito.mock(MemberDescription.class);
        MemberDescription second = Mockito.mock(MemberDescription.class);
        MemberAssignment firstAssignment = Mockito.mock(MemberAssignment.class);
        MemberAssignment secondAssignment = Mockito.mock(MemberAssignment.class);
        Mockito.when(first.consumerId()).thenReturn("member-a");
        Mockito.when(second.consumerId()).thenReturn("member-b");
        Mockito.when(first.assignment()).thenReturn(firstAssignment);
        Mockito.when(second.assignment()).thenReturn(secondAssignment);
        Mockito.when(firstAssignment.topicPartitions()).thenReturn(Collections.singleton(new TopicPartition("orders", 0)));
        Mockito.when(secondAssignment.topicPartitions()).thenReturn(Collections.singleton(new TopicPartition("orders", 1)));
        ConsumerGroupDescription description = Mockito.mock(ConsumerGroupDescription.class);
        Mockito.when(description.groupId()).thenReturn("orders-group");
        Mockito.when(description.isSimpleConsumerGroup()).thenReturn(false);
        Mockito.when(description.partitionAssignor()).thenReturn("range");
        Mockito.when(description.members()).thenReturn(Arrays.asList(second, first));

        KafkaConsumerGroupDetailResponse response = getGroupDetail(description, 1);

        assertEquals("TRUNCATED", response.getAssignmentStatus());
        assertTrue(response.getTruncated());
        assertEquals(2, response.getMemberCount());
        assertEquals(1, response.getAssignments().size());
        assertEquals("orders", response.getAssignments().get(0).getTopic());
        assertEquals(Collections.singletonList(0), response.getAssignments().get(0).getPartitions());
    }

    private KafkaConsumerGroupDetailResponse getGroupDetail(ConsumerGroupDescription description, int maxSize)
            throws Exception {
        AdminClient adminClient = Mockito.mock(AdminClient.class);
        DescribeConsumerGroupsResult result = Mockito.mock(DescribeConsumerGroupsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Map<String, ConsumerGroupDescription>> descriptions = Mockito.mock(KafkaFuture.class);
        Mockito.when(adminClient.describeConsumerGroups(Mockito.anyCollection())).thenReturn(result);
        Mockito.when(result.all()).thenReturn(descriptions);
        String groupId = description.groupId();
        Mockito.when(descriptions.get(Mockito.anyLong(), Mockito.eq(TimeUnit.NANOSECONDS)))
                .thenReturn(Collections.singletonMap(groupId, description));
        return adapter(adminClient, maxSize).getConsumerGroupDetail(KafkaConsumerGroupDetailRequest.builder()
                .datasourceKey("default").groupId(groupId).build());
    }

    private DefaultKafkaOperationsViewAdapter adapter(AdminClient adminClient, int maxSize) {
        KafkaRouteAdminClientFactory factory = new KafkaRouteAdminClientFactory() {
            @Override
            public <T> T withAdminClient(String datasourceKey,
                                         io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientCallback<T> callback) {
                return callback.doWithAdminClient(adminClient);
            }
        };
        SimpleKafkaRouteRegistry registry = Mockito.mock(SimpleKafkaRouteRegistry.class);
        Mockito.when(registry.getDatasourceKeys()).thenReturn(Collections.singleton("default"));
        Mockito.when(registry.containsDatasource("default")).thenReturn(true);
        return new DefaultKafkaOperationsViewAdapter(registry, Mockito.mock(KafkaRouteDiagnostics.class), factory, 100L,
                maxSize);
    }
}
