package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response.OwnerAuthorizationChangePullResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response.OwnerAuthorizationChangeResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamAkskAuthorizationChangeEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamAkskAuthorizationChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IAM 授权变更日志读取服务。
 *
 * <p>拉取仅按 sourceSequence 连续前进。发现保留缺口时不返回残缺增量，
 * 调用方必须先按 resolve 执行全量修复。</p>
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamAkskAuthorizationChangePullService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final ObjectMapper PAYLOAD_OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final IamAkskAuthorizationChangeRepository changeRepository;

    /**
     * 读取 afterSequence 之后的一页连续最终态快照。
     */
    @Transactional(readOnly = true)
    public OwnerAuthorizationChangePullResponse pull(Long afterSequence, Integer pageSize) {
        long cursor = afterSequence == null ? 0L : afterSequence.longValue();
        int size = pageSize == null ? MAX_PAGE_SIZE : Math.min(pageSize.intValue(), MAX_PAGE_SIZE);
        IamAkskAuthorizationChangeEntity oldest = changeRepository.findFirstByOrderBySourceSequenceAsc();
        IamAkskAuthorizationChangeEntity newest = changeRepository.findFirstByOrderBySourceSequenceDesc();
        long oldestSequence = oldest == null ? 0L : oldest.getSourceSequence().longValue();
        long highWaterSequence = newest == null ? 0L : newest.getSourceSequence().longValue();
        if (oldest != null && cursor > 0L && cursor < oldestSequence - 1L) {
            return OwnerAuthorizationChangePullResponse.builder()
                    .resyncRequired(true)
                    .oldestAvailableSequence(oldestSequence)
                    .highWaterSequence(highWaterSequence)
                    .changes(Collections.<OwnerAuthorizationChangeResponse>emptyList())
                    .build();
        }
        List<OwnerAuthorizationChangeResponse> changes = changeRepository
                .findBySourceSequenceGreaterThanOrderBySourceSequenceAsc(Long.valueOf(cursor),
                        PageRequest.of(0, size))
                .stream().map(this::toResponse).collect(Collectors.toList());
        return OwnerAuthorizationChangePullResponse.builder()
                .resyncRequired(false)
                .oldestAvailableSequence(oldestSequence)
                .highWaterSequence(highWaterSequence)
                .changes(changes)
                .build();
    }

    private OwnerAuthorizationChangeResponse toResponse(IamAkskAuthorizationChangeEntity entity) {
        try {
            return OwnerAuthorizationChangeResponse.builder()
                    .sourceSequence(entity.getSourceSequence())
                    .eventId(entity.getEventId())
                    .changeType(entity.getChangeType())
                    .aggregateKey(entity.getAggregateKey())
                    .reasonCode(entity.getReasonCode())
                    .schemaVersion(entity.getSchemaVersion())
                    .payload(PAYLOAD_OBJECT_MAPPER.readValue(entity.getPayloadJson(), PAYLOAD_TYPE))
                    .occurredAt(entity.getOccurredAt() == null ? null : entity.getOccurredAt().toEpochMilli())
                    .build();
        } catch (Exception exception) {
            // 载荷由同一 SDK 的写侧生成；损坏数据不能被静默降级为旧授权。
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CHANGE_PAYLOAD_INVALID,
                    ServerErrorMessage.APPLICATION_AUTHORIZATION_CHANGE_PAYLOAD_INVALID, exception);
        }
    }
}
