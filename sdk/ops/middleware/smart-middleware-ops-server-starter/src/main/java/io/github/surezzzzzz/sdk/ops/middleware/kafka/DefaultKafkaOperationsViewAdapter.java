package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientFactory;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 通过 Kafka Route 受控 AdminClient 作用域完成只读运维查询的适配器。
 *
 * @author surezzzzzz
 */
public class DefaultKafkaOperationsViewAdapter implements KafkaOperationsViewAdapter {

    private static final Set<String> TOPIC_CONFIG_ALLOWLIST = new LinkedHashSet<>(Arrays.asList(
            "cleanup.policy", "retention.ms", "retention.bytes", "segment.ms", "segment.bytes",
            "compression.type", "min.insync.replicas", "max.message.bytes", "message.timestamp.type"));
    private static final Set<String> SUPPORTED_ASSIGNORS = new LinkedHashSet<>(Arrays.asList(
            "range", "roundrobin", "sticky", "cooperative-sticky"));

    private final SimpleKafkaRouteRegistry registry;
    private final KafkaRouteDiagnostics diagnostics;
    private final KafkaRouteAdminClientFactory adminClientFactory;
    private final long deadlineMillis;
    private final int maxSize;

    public DefaultKafkaOperationsViewAdapter(SimpleKafkaRouteRegistry registry, KafkaRouteDiagnostics diagnostics,
                                             KafkaRouteAdminClientFactory adminClientFactory, long deadlineMillis,
                                             int maxSize) {
        this.registry = registry;
        this.diagnostics = diagnostics;
        this.adminClientFactory = adminClientFactory;
        this.deadlineMillis = deadlineMillis;
        this.maxSize = maxSize;
    }

    @Override
    public KafkaDatasourceListResponse listDatasources() {
        List<KafkaDatasourceResponse> items = new ArrayList<>();
        for (String datasourceKey : registry.getDatasourceKeys()) {
            items.add(toDatasourceResponse(datasourceKey, diagnostics.getDiagnosticResult(datasourceKey)));
        }
        return KafkaDatasourceListResponse.builder().items(items).build();
    }

    @Override
    public KafkaTopicListResponse listTopics(KafkaTopicListRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), client -> {
            long deadlineNanos = operationDeadlineNanos();
            List<String> names = new ArrayList<>(await(deadlineNanos, client.listTopics().names()));
            Collections.sort(names);
            List<KafkaTopicResponse> items = new ArrayList<>();
            for (String name : names) {
                if (hasPrefix(name, request.getPrefix())) {
                    items.add(KafkaTopicResponse.builder().name(name).build());
                    if (items.size() > request.getSize()) {
                        break;
                    }
                }
            }
            boolean truncated = items.size() > request.getSize();
            if (truncated) {
                items.remove(items.size() - 1);
            }
            return KafkaTopicListResponse.builder().items(items).limit(request.getSize()).returned(items.size())
                    .truncated(truncated).traversalComplete(true)
                    .stopReason(truncated ? "RESULT_LIMIT" : "COMPLETED").build();
        });
    }

    @Override
    public KafkaConsumerGroupListResponse listConsumerGroups(KafkaConsumerGroupListRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), client -> {
            long deadlineNanos = operationDeadlineNanos();
            List<ConsumerGroupListing> listings = new ArrayList<>(await(deadlineNanos, client.listConsumerGroups().all()));
            Collections.sort(listings, Comparator.comparing(ConsumerGroupListing::groupId));
            List<KafkaConsumerGroupResponse> items = new ArrayList<>();
            for (ConsumerGroupListing listing : listings) {
                if (hasPrefix(listing.groupId(), request.getPrefix())) {
                    items.add(KafkaConsumerGroupResponse.builder().groupId(listing.groupId())
                            .protocolType(listing.isSimpleConsumerGroup() ? "simple" : "consumer").build());
                    if (items.size() > request.getSize()) {
                        break;
                    }
                }
            }
            boolean truncated = items.size() > request.getSize();
            if (truncated) {
                items.remove(items.size() - 1);
            }
            return KafkaConsumerGroupListResponse.builder().items(items).limit(request.getSize()).returned(items.size())
                    .truncated(truncated).traversalComplete(true)
                    .stopReason(truncated ? "RESULT_LIMIT" : "COMPLETED").build();
        });
    }

    @Override
    public KafkaTopicConfigResponse getTopicConfig(KafkaTopicConfigRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), client -> {
            long deadlineNanos = operationDeadlineNanos();
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, request.getTopic());
            Config config = await(deadlineNanos, client.describeConfigs(Collections.singleton(resource)).all()).get(resource);
            Map<String, ConfigEntry> entries = new LinkedHashMap<>();
            for (ConfigEntry entry : config.entries()) {
                if (TOPIC_CONFIG_ALLOWLIST.contains(entry.name()) && !entry.isSensitive()) {
                    entries.put(entry.name(), entry);
                }
            }
            List<KafkaTopicConfigResponse.Item> items = new ArrayList<>();
            for (String name : TOPIC_CONFIG_ALLOWLIST) {
                ConfigEntry entry = entries.get(name);
                if (entry != null) {
                    items.add(KafkaTopicConfigResponse.Item.builder().name(name).value(entry.value())
                            .source(entry.source() == null ? null : entry.source().name())
                            .readOnly(entry.isReadOnly()).build());
                }
            }
            return KafkaTopicConfigResponse.builder().topic(request.getTopic()).items(items).build();
        });
    }

    @Override
    public KafkaConsumerGroupDetailResponse getConsumerGroupDetail(KafkaConsumerGroupDetailRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), client -> {
            long deadlineNanos = operationDeadlineNanos();
            ConsumerGroupDescription description = await(deadlineNanos, client.describeConsumerGroups(
                    Collections.singleton(request.getGroupId())).all()).get(request.getGroupId());
            return toConsumerGroupDetail(description);
        });
    }

    @Override
    public KafkaTopicRuntimeResponse getTopicRuntime(KafkaTopicRuntimeRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), client -> {
            long deadlineNanos = operationDeadlineNanos();
            TopicDescription description = await(deadlineNanos, client.describeTopics(Collections.singleton(request.getTopic()))
                    .all()).get(request.getTopic());
            List<org.apache.kafka.common.TopicPartitionInfo> allPartitions = new ArrayList<>(description.partitions());
            Collections.sort(allPartitions, Comparator.comparingInt(org.apache.kafka.common.TopicPartitionInfo::partition));
            int end = Math.min(maxSize, allPartitions.size());
            Map<TopicPartition, OffsetSpec> earliest = new LinkedHashMap<>();
            Map<TopicPartition, OffsetSpec> latest = new LinkedHashMap<>();
            for (int index = 0; index < end; index++) {
                TopicPartition key = new TopicPartition(request.getTopic(), allPartitions.get(index).partition());
                earliest.put(key, OffsetSpec.earliest());
                latest.put(key, OffsetSpec.latest());
            }
            if (earliest.isEmpty()) {
                return KafkaTopicRuntimeResponse.builder().topic(request.getTopic()).partitions(Collections.emptyList())
                        .truncated(false).build();
            }
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> first =
                    await(deadlineNanos, client.listOffsets(earliest).all());
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> last =
                    await(deadlineNanos, client.listOffsets(latest).all());
            List<KafkaTopicRuntimeResponse.Partition> partitions = new ArrayList<>();
            for (int index = 0; index < end; index++) {
                org.apache.kafka.common.TopicPartitionInfo partition = allPartitions.get(index);
                TopicPartition key = new TopicPartition(request.getTopic(), partition.partition());
                partitions.add(KafkaTopicRuntimeResponse.Partition.builder().partition(partition.partition())
                        .leader(partition.leader() == null ? null : partition.leader().id())
                        .replicas(nodeIds(partition.replicas())).inSyncReplicas(nodeIds(partition.isr()))
                        .earliestOffset(first.get(key).offset()).latestOffset(last.get(key).offset()).build());
            }
            return KafkaTopicRuntimeResponse.builder().topic(request.getTopic()).partitions(partitions)
                    .truncated(allPartitions.size() > end).build();
        });
    }

    @Override
    public KafkaConsumerGroupLagListResponse getConsumerGroupLag(KafkaConsumerGroupLagListRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), client -> {
            long deadlineNanos = operationDeadlineNanos();
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets = await(deadlineNanos,
                    client.listConsumerGroupOffsets(request.getGroupId()).partitionsToOffsetAndMetadata());
            List<TopicPartition> partitions = new ArrayList<>(offsets.keySet());
            Collections.sort(partitions, Comparator.comparing(TopicPartition::topic)
                    .thenComparingInt(TopicPartition::partition));
            int end = Math.min(request.getSize(), partitions.size());
            if (end == 0) {
                return KafkaConsumerGroupLagListResponse.builder().items(Collections.emptyList()).truncated(false).build();
            }
            Map<TopicPartition, OffsetSpec> latest = new LinkedHashMap<>();
            for (int index = 0; index < end; index++) {
                latest.put(partitions.get(index), OffsetSpec.latest());
            }
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    await(deadlineNanos, client.listOffsets(latest).all());
            List<KafkaConsumerGroupLagResponse> items = new ArrayList<>();
            for (int index = 0; index < end; index++) {
                TopicPartition partition = partitions.get(index);
                long committed = offsets.get(partition).offset();
                long endOffset = endOffsets.get(partition).offset();
                items.add(KafkaConsumerGroupLagResponse.builder().topic(partition.topic()).partition(partition.partition())
                        .committedOffset(committed).endOffset(endOffset).lag(endOffset - committed).build());
            }
            return KafkaConsumerGroupLagListResponse.builder().items(items)
                    .truncated(partitions.size() > end).build();
        });
    }

    private KafkaConsumerGroupDetailResponse toConsumerGroupDetail(ConsumerGroupDescription description) {
        String state = description.state() == null ? "UNKNOWN" : description.state().name();
        String protocolType = description.isSimpleConsumerGroup() ? "simple" : "consumer";
        if (description.isSimpleConsumerGroup() || !SUPPORTED_ASSIGNORS.contains(description.partitionAssignor())) {
            return KafkaConsumerGroupDetailResponse.builder().groupId(description.groupId()).state(state)
                    .protocolType(protocolType).assignmentStatus("UNSUPPORTED_PROTOCOL")
                    .memberCount(description.members().size()).assignments(Collections.emptyList()).truncated(false).build();
        }
        List<MemberDescription> members = new ArrayList<>(description.members());
        Collections.sort(members, Comparator.comparing(MemberDescription::consumerId));
        Map<String, List<Integer>> grouped = new TreeMap<>();
        boolean truncated = members.size() > maxSize;
        int projectedPartitions = 0;
        for (int memberIndex = 0; memberIndex < members.size() && memberIndex < maxSize; memberIndex++) {
            for (TopicPartition partition : members.get(memberIndex).assignment().topicPartitions()) {
                if (projectedPartitions >= maxSize) {
                    truncated = true;
                    break;
                }
                List<Integer> values = grouped.get(partition.topic());
                if (values == null) {
                    if (grouped.size() >= maxSize) {
                        truncated = true;
                        continue;
                    }
                    values = new ArrayList<>();
                    grouped.put(partition.topic(), values);
                }
                if (values.size() >= maxSize) {
                    truncated = true;
                    continue;
                }
                values.add(partition.partition());
                projectedPartitions++;
            }
            if (projectedPartitions >= maxSize) {
                break;
            }
        }
        List<KafkaConsumerGroupDetailResponse.Assignment> assignments = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            Collections.sort(entry.getValue());
            assignments.add(KafkaConsumerGroupDetailResponse.Assignment.builder().topic(entry.getKey())
                    .partitions(entry.getValue()).build());
        }
        return KafkaConsumerGroupDetailResponse.builder().groupId(description.groupId()).state(state)
                .protocolType(protocolType).assignmentStatus(truncated ? "TRUNCATED" : "COMPLETE")
                .memberCount(members.size()).assignments(assignments).truncated(truncated).build();
    }

    private List<Integer> nodeIds(List<org.apache.kafka.common.Node> nodes) {
        List<Integer> result = new ArrayList<>();
        for (org.apache.kafka.common.Node node : nodes) {
            result.add(node.id());
        }
        return result;
    }

    private void requireDatasource(String datasourceKey) {
        if (!registry.containsDatasource(datasourceKey)) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
    }

    private boolean hasPrefix(String value, String prefix) {
        return prefix == null || prefix.isEmpty() || value.startsWith(prefix);
    }

    private long operationDeadlineNanos() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMillis);
    }

    private <T> T await(long deadlineNanos, org.apache.kafka.common.KafkaFuture<T> future) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            future.cancel(true);
            throw new MiddlewareOpsException(HttpStatus.GATEWAY_TIMEOUT, "Kafka 运维查询已超时");
        }
        try {
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new MiddlewareOpsException(HttpStatus.GATEWAY_TIMEOUT, "Kafka 运维查询已超时");
        } catch (ExecutionException e) {
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (RuntimeException e) {
            throw unavailable();
        }
    }

    private MiddlewareOpsException unavailable() {
        return new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Kafka 运维查询暂不可用");
    }

    private KafkaDatasourceResponse toDatasourceResponse(String datasourceKey, KafkaRouteBrokerDiagnosticResult result) {
        if (result == null) {
            return KafkaDatasourceResponse.builder().datasourceKey(datasourceKey).diagnosticStatus("UNKNOWN").build();
        }
        return KafkaDatasourceResponse.builder().datasourceKey(datasourceKey)
                .diagnosticStatus(result.getStatus() == null ? "UNKNOWN" : result.getStatus().name())
                .diagnosticReason(diagnosticReason(result)).clusterId(result.getClusterId()).nodeCount(result.getNodeCount())
                .controllerVisible(result.isControllerVisible()).build();
    }

    private String diagnosticReason(KafkaRouteBrokerDiagnosticResult result) {
        if (result.getStatus() == null || !"WARN".equals(result.getStatus().name())) {
            return null;
        }
        return result.getDiagnosticReason() == null || result.getDiagnosticReason().trim().isEmpty()
                ? "Kafka Route 诊断存在待确认的生产者能力" : result.getDiagnosticReason();
    }
}
