package io.github.surezzzzzz.sdk.ops.middleware.kafka;

import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientFactory;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteBrokerDiagnosticResult;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 仅通过 Kafka Route 诊断与 callback AdminClient 工厂获取安全视图的适配器。
 *
 * @author surezzzzzz
 */
public class DefaultKafkaOperationsViewAdapter implements KafkaOperationsViewAdapter {

    private final SimpleKafkaRouteRegistry registry;
    private final KafkaRouteDiagnostics diagnostics;
    private final KafkaRouteAdminClientFactory adminClientFactory;
    private final long deadlineMillis;
    private final int maxSize;

    /**
     * 创建 Kafka Route 适配器。
     */
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
    public KafkaTopicListResponse listTopics(final KafkaTopicListRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), adminClient -> {
            long deadlineNanos = operationDeadlineNanos();
            Set<String> topicNames = await(adminClient, deadlineNanos, new TopicReader<Set<String>>() {
                @Override
                public Set<String> read(AdminClient client, long timeoutMillis) throws Exception {
                    return client.listTopics().names().get(timeoutMillis, TimeUnit.MILLISECONDS);
                }
            });
            List<String> names = new ArrayList<>(topicNames);
            Collections.sort(names);
            return toTopicPage(names, request);
        });
    }

    @Override
    public KafkaConsumerGroupListResponse listConsumerGroups(final KafkaConsumerGroupListRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), adminClient -> {
            long deadlineNanos = operationDeadlineNanos();
            List<ConsumerGroupListing> listings = await(adminClient, deadlineNanos,
                    new TopicReader<List<ConsumerGroupListing>>() {
                        @Override
                        public List<ConsumerGroupListing> read(AdminClient client, long timeoutMillis) throws Exception {
                            return new ArrayList<>(client.listConsumerGroups().all().get(timeoutMillis, TimeUnit.MILLISECONDS));
                        }
                    });
            Collections.sort(listings, new Comparator<ConsumerGroupListing>() {
                @Override
                public int compare(ConsumerGroupListing left, ConsumerGroupListing right) {
                    return left.groupId().compareTo(right.groupId());
                }
            });
            return toGroupPage(listings, request);
        });
    }

    @Override
    public KafkaTopicRuntimeResponse getTopicRuntime(final KafkaTopicRuntimeRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), adminClient -> {
            long deadlineNanos = operationDeadlineNanos();
            TopicDescription description = await(adminClient, deadlineNanos, new TopicReader<TopicDescription>() {
                @Override
                public TopicDescription read(AdminClient client, long timeoutMillis) throws Exception {
                    return client.describeTopics(Collections.singleton(request.getTopic())).all().get(timeoutMillis,
                            TimeUnit.MILLISECONDS).get(request.getTopic());
                }
            });
            List<org.apache.kafka.common.TopicPartitionInfo> partitionInfos = new ArrayList<>(description.partitions());
            Collections.sort(partitionInfos, new Comparator<org.apache.kafka.common.TopicPartitionInfo>() {
                @Override
                public int compare(org.apache.kafka.common.TopicPartitionInfo left,
                                   org.apache.kafka.common.TopicPartitionInfo right) {
                    return Integer.compare(left.partition(), right.partition());
                }
            });
            int end = Math.min(maxSize, partitionInfos.size());
            List<org.apache.kafka.common.TopicPartitionInfo> selectedPartitions = partitionInfos.subList(0, end);
            Map<TopicPartition, OffsetSpec> earliest = new LinkedHashMap<>();
            Map<TopicPartition, OffsetSpec> latest = new LinkedHashMap<>();
            for (org.apache.kafka.common.TopicPartitionInfo partition : selectedPartitions) {
                TopicPartition topicPartition = new TopicPartition(request.getTopic(), partition.partition());
                earliest.put(topicPartition, OffsetSpec.earliest());
                latest.put(topicPartition, OffsetSpec.latest());
            }
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> earliestOffsets =
                    awaitOffsets(adminClient, deadlineNanos, earliest);
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    awaitOffsets(adminClient, deadlineNanos, latest);
            List<KafkaTopicRuntimeResponse.Partition> partitions = new ArrayList<>();
            for (org.apache.kafka.common.TopicPartitionInfo partition : selectedPartitions) {
                TopicPartition topicPartition = new TopicPartition(request.getTopic(), partition.partition());
                partitions.add(KafkaTopicRuntimeResponse.Partition.builder().partition(partition.partition())
                        .leader(partition.leader() == null ? null : partition.leader().id())
                        .replicas(nodeIds(partition.replicas())).inSyncReplicas(nodeIds(partition.isr()))
                        .earliestOffset(earliestOffsets.get(topicPartition).offset())
                        .latestOffset(latestOffsets.get(topicPartition).offset()).build());
            }
            return KafkaTopicRuntimeResponse.builder().topic(request.getTopic()).partitions(partitions)
                    .truncated(description.partitions().size() > end).build();
        });
    }

    @Override
    public KafkaConsumerGroupLagListResponse getConsumerGroupLag(final KafkaConsumerGroupLagListRequest request) {
        requireDatasource(request.getDatasourceKey());
        return adminClientFactory.withAdminClient(request.getDatasourceKey(), adminClient -> {
            long deadlineNanos = operationDeadlineNanos();
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets = await(adminClient,
                    deadlineNanos, new TopicReader<Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata>>() {
                        @Override
                        public Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> read(AdminClient client,
                                                                                                             long timeoutMillis)
                                throws Exception {
                            return client.listConsumerGroupOffsets(request.getGroupId()).partitionsToOffsetAndMetadata()
                                    .get(timeoutMillis, TimeUnit.MILLISECONDS);
                        }
                    });
            List<TopicPartition> partitions = new ArrayList<>(offsets.keySet());
            Collections.sort(partitions, new Comparator<TopicPartition>() {
                @Override
                public int compare(TopicPartition left, TopicPartition right) {
                    int topic = left.topic().compareTo(right.topic());
                    return topic == 0 ? Integer.compare(left.partition(), right.partition()) : topic;
                }
            });
            int end = Math.min(request.getSize(), partitions.size());
            Map<TopicPartition, OffsetSpec> latest = new LinkedHashMap<>();
            for (int index = 0; index < end; index++) {
                latest.put(partitions.get(index), OffsetSpec.latest());
            }
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    awaitOffsets(adminClient, deadlineNanos, latest);
            List<KafkaConsumerGroupLagResponse> items = new ArrayList<>();
            for (int index = 0; index < end; index++) {
                TopicPartition partition = partitions.get(index);
                long committed = offsets.get(partition).offset();
                long endOffset = endOffsets.get(partition).offset();
                items.add(KafkaConsumerGroupLagResponse.builder().topic(partition.topic()).partition(partition.partition())
                        .committedOffset(committed).endOffset(endOffset).lag(endOffset - committed).build());
            }
            return KafkaConsumerGroupLagListResponse.builder().items(items).build();
        });
    }

    private Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> awaitOffsets(
            final AdminClient adminClient, long deadlineNanos, final Map<TopicPartition, OffsetSpec> specifications) {
        return await(adminClient, deadlineNanos,
                new TopicReader<Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo>>() {
                    @Override
                    public Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> read(
                            AdminClient client, long timeoutMillis) throws Exception {
                        return client.listOffsets(specifications).all().get(timeoutMillis, TimeUnit.MILLISECONDS);
                    }
                });
    }

    private List<Integer> nodeIds(List<org.apache.kafka.common.Node> nodes) {
        List<Integer> nodeIds = new ArrayList<>();
        for (org.apache.kafka.common.Node node : nodes) {
            nodeIds.add(node.id());
        }
        return nodeIds;
    }

    private void requireDatasource(String datasourceKey) {
        if (!registry.getDatasourceKeys().contains(datasourceKey)) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
    }

    private KafkaDatasourceResponse toDatasourceResponse(String datasourceKey, KafkaRouteBrokerDiagnosticResult result) {
        if (result == null) {
            return KafkaDatasourceResponse.builder().datasourceKey(datasourceKey).diagnosticStatus("UNKNOWN").build();
        }
        return KafkaDatasourceResponse.builder().datasourceKey(datasourceKey)
                .diagnosticStatus(result.getStatus() == null ? "UNKNOWN" : result.getStatus().name())
                .diagnosticReason(diagnosticReason(result))
                .clusterId(result.getClusterId()).nodeCount(result.getNodeCount())
                .controllerVisible(result.isControllerVisible()).build();
    }

    private String diagnosticReason(KafkaRouteBrokerDiagnosticResult result) {
        if (result.getStatus() == null || !"WARN".equals(result.getStatus().name())) {
            return null;
        }
        String diagnosticReason = result.getDiagnosticReason();
        return diagnosticReason == null || diagnosticReason.trim().isEmpty()
                ? "Kafka Route 诊断存在待确认的生产者能力" : diagnosticReason;
    }

    private KafkaTopicListResponse toTopicPage(List<String> names, KafkaTopicListRequest request) {
        int end = Math.min(request.getSize(), names.size());
        List<KafkaTopicResponse> items = new ArrayList<>();
        for (int index = 0; index < end; index++) {
            items.add(KafkaTopicResponse.builder().name(names.get(index)).build());
        }
        return KafkaTopicListResponse.builder().items(items).build();
    }

    private KafkaConsumerGroupListResponse toGroupPage(List<ConsumerGroupListing> listings,
                                                       KafkaConsumerGroupListRequest request) {
        int end = Math.min(request.getSize(), listings.size());
        List<KafkaConsumerGroupResponse> items = new ArrayList<>();
        for (int index = 0; index < end; index++) {
            ConsumerGroupListing listing = listings.get(index);
            items.add(KafkaConsumerGroupResponse.builder().groupId(listing.groupId())
                    .protocolType(listing.isSimpleConsumerGroup() ? "simple" : "consumer").build());
        }
        return KafkaConsumerGroupListResponse.builder().items(items).build();
    }

    private <T> T await(AdminClient client, long deadlineNanos, TopicReader<T> reader) {
        try {
            long timeoutMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
            if (timeoutMillis <= 0L) {
                throw new TimeoutException();
            }
            return reader.read(client, timeoutMillis);
        } catch (TimeoutException e) {
            throw new MiddlewareOpsException(HttpStatus.GATEWAY_TIMEOUT, "Kafka 运维查询已超时");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new MiddlewareOpsException(HttpStatus.GATEWAY_TIMEOUT, "Kafka 运维查询已超时");
            }
            throw unavailable();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception e) {
            throw unavailable();
        }
    }

    private long operationDeadlineNanos() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMillis);
    }

    private MiddlewareOpsException unavailable() {
        return new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Kafka 运维查询暂不可用");
    }

    /**
     * callback 内同步读取 Kafka Admin 查询结果。
     *
     * @param <T> 结果类型
     */
    private interface TopicReader<T> {
        T read(AdminClient client, long timeoutMillis) throws Exception;
    }
}
