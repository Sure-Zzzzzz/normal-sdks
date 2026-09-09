package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.request.CreateMessageRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageBatchDetailResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageBatchRecipientResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageBatchSummaryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageSendResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.response.AdminPageResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamMessageEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.MessageRecipientsResolvedEvent;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamMessageRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 站内信服务
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class MessageService {

    /**
     * 批次聚合列：send_batch_id 仅普通索引，MySQL8 默认 ONLY_FULL_GROUP_BY 下
     * 非聚合列必须用 MAX() 包裹（批次内同值，结果确定）
     */
    private static final String SQL_LIST_MESSAGE_BATCHES =
            "SELECT send_batch_id, MAX(title) AS title, MAX(sender_username) AS sender_username, "
                    + "MAX(target_summary) AS target_summary, COUNT(*) AS recipient_count, "
                    + "COUNT(read_at) AS read_count, MIN(created_at) AS created_at "
                    + "FROM iam_message WHERE send_batch_id IS NOT NULL "
                    + "GROUP BY send_batch_id ORDER BY MIN(created_at) DESC, send_batch_id DESC LIMIT ? OFFSET ?";
    private static final String SQL_COUNT_MESSAGE_BATCHES =
            "SELECT COUNT(DISTINCT send_batch_id) FROM iam_message WHERE send_batch_id IS NOT NULL";
    private static final String SQL_GET_MESSAGE_BATCH_ROW =
            "SELECT title, content, sender_username, target_summary, created_at "
                    + "FROM iam_message WHERE send_batch_id = ? LIMIT 1";
    private static final String SQL_COUNT_MESSAGE_BATCH =
            "SELECT COUNT(*), COUNT(read_at) FROM iam_message WHERE send_batch_id = ?";
    private static final String SQL_LIST_BATCH_RECIPIENTS =
            "SELECT recipient_user_id, read_at FROM iam_message WHERE send_batch_id = ? "
                    + "ORDER BY id ASC LIMIT ? OFFSET ?";
    private static final String SQL_COUNT_BATCH_RECIPIENTS =
            "SELECT COUNT(*) FROM iam_message WHERE send_batch_id = ?";

    private final IamMessageRepository messageRepository;
    private final IamUserRepository userRepository;
    private final DepartmentService departmentService;
    private final UserGroupService userGroupService;
    private final ApplicationEventPublisher eventPublisher;
    private final JdbcTemplate jdbcTemplate;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 创建站内信，兼容单收件人调用
     */
    @Transactional
    public IamMessageEntity createMessage(CreateMessageRequest request, Long senderUserId, String senderUsername) {
        List<IamMessageEntity> messages = createMessages(request, senderUserId, senderUsername);
        return messages.get(0);
    }

    /**
     * 发送站内信
     */
    @Transactional
    public MessageSendResponse sendMessage(CreateMessageRequest request, Long senderUserId, String senderUsername) {
        List<IamMessageEntity> messages = createMessages(request, senderUserId, senderUsername);
        MessageSendResponse response = new MessageSendResponse(messages.get(0).getSendBatchId(), messages.size());
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.MESSAGE,
                response.getSendBatchId(), messages.get(0).getTitle(),
                String.format("收件人 %d 人", response.getRecipientCount()));
        return response;
    }

    /**
     * 按收件人全量拉取（时间倒序）
     */
    public List<IamMessageEntity> listMessages(Long recipientUserId) {
        return messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId);
    }

    /**
     * 按收件人分页（页码从 1 起，时间倒序）
     */
    public Page<IamMessageEntity> listMessages(Long recipientUserId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(normalizedPage - 1, normalizedSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return messageRepository.findByRecipientUserId(recipientUserId, pageable);
    }

    /**
     * 统计未读数
     */
    public long countUnreadMessages(Long recipientUserId) {
        return messageRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    /**
     * 管理台分页查询发送批次（按 send_batch_id 聚合，时间倒序）
     */
    public AdminPageResponse<MessageBatchSummaryResponse> listMessageBatches(int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_MESSAGE_BATCHES, Long.class);
        List<MessageBatchSummaryResponse> content = jdbcTemplate.query(SQL_LIST_MESSAGE_BATCHES,
                (rs, rowNum) -> toBatchSummary(rs), normalizedSize, (normalizedPage - 1) * normalizedSize);
        Page<MessageBatchSummaryResponse> result = new PageImpl<>(content,
                PageRequest.of(normalizedPage - 1, normalizedSize), total != null ? total : 0L);
        return AdminPageResponse.from(result);
    }

    /**
     * 管理台查询批次详情；批次不存在抛 MESSAGE_NOT_FOUND
     */
    public MessageBatchDetailResponse getMessageBatch(String sendBatchId) {
        List<Object[]> batchRows = new ArrayList<>();
        jdbcTemplate.query(SQL_GET_MESSAGE_BATCH_ROW, rs -> {
            batchRows.add(new Object[]{rs.getString("title"), rs.getString("content"),
                    rs.getString("sender_username"), rs.getString("target_summary"),
                    rs.getTimestamp("created_at").toInstant()});
        }, sendBatchId);
        if (batchRows.isEmpty()) {
            throw new SimpleIamServerException(ErrorCode.MESSAGE_NOT_FOUND,
                    String.format(ServerErrorMessage.MESSAGE_NOT_FOUND, sendBatchId));
        }
        Object[] row = batchRows.get(0);
        int[] target = parseTargetSummary((String) row[3]);
        long[] counts = countBatchRecipientsAndRead(sendBatchId);
        return MessageBatchDetailResponse.builder()
                .sendBatchId(sendBatchId)
                .title((String) row[0])
                .content((String) row[1])
                .senderUsername((String) row[2])
                .targetUserCount(target[0])
                .targetDepartmentCount(target[1])
                .targetUserGroupCount(target[2])
                .targetIncludeChildDepartments(target[3] == 1)
                .recipientCount(counts[0])
                .readCount(counts[1])
                .createdAt((Instant) row[4])
                .build();
    }

    /**
     * 管理台分页查询批次收件人（含已读状态；用户已注销时展示兜底文案）
     */
    public AdminPageResponse<MessageBatchRecipientResponse> listMessageBatchRecipients(String sendBatchId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_BATCH_RECIPIENTS, Long.class, sendBatchId);
        if (total == null || total == 0L) {
            throw new SimpleIamServerException(ErrorCode.MESSAGE_NOT_FOUND,
                    String.format(ServerErrorMessage.MESSAGE_NOT_FOUND, sendBatchId));
        }
        List<Long> recipientUserIds = new ArrayList<>();
        List<java.sql.Timestamp> readAts = new ArrayList<>();
        jdbcTemplate.query(SQL_LIST_BATCH_RECIPIENTS, rs -> {
            recipientUserIds.add(rs.getLong("recipient_user_id"));
            readAts.add(rs.getTimestamp("read_at"));
        }, sendBatchId, normalizedSize, (normalizedPage - 1) * normalizedSize);

        Map<Long, IamUserEntity> usersById = indexUsersById(userRepository.findAllById(recipientUserIds));
        List<MessageBatchRecipientResponse> content = new ArrayList<>();
        for (int i = 0; i < recipientUserIds.size(); i++) {
            Long userId = recipientUserIds.get(i);
            IamUserEntity user = usersById.get(userId);
            java.sql.Timestamp readAt = readAts.get(i);
            content.add(MessageBatchRecipientResponse.builder()
                    .userId(userId)
                    .username(user != null ? user.getUsername() : "#" + userId)
                    .displayName(user != null && user.getDisplayName() != null
                            ? user.getDisplayName() : "已注销用户 #" + userId)
                    .readAt(readAt != null ? readAt.toInstant() : null)
                    .build());
        }
        Page<MessageBatchRecipientResponse> result = new PageImpl<>(content,
                PageRequest.of(normalizedPage - 1, normalizedSize), total);
        return AdminPageResponse.from(result);
    }

    private long[] countBatchRecipientsAndRead(String sendBatchId) {
        return jdbcTemplate.queryForObject(SQL_COUNT_MESSAGE_BATCH, (rs, rowNum) ->
                new long[]{rs.getLong(1), rs.getLong(2)}, sendBatchId);
    }

    private MessageBatchSummaryResponse toBatchSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        int[] target = parseTargetSummary(rs.getString("target_summary"));
        return MessageBatchSummaryResponse.builder()
                .sendBatchId(rs.getString("send_batch_id"))
                .title(rs.getString("title"))
                .senderUsername(rs.getString("sender_username"))
                .targetUserCount(target[0])
                .targetDepartmentCount(target[1])
                .targetUserGroupCount(target[2])
                .targetIncludeChildDepartments(target[3] == 1)
                .recipientCount(rs.getLong("recipient_count"))
                .readCount(rs.getLong("read_count"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .build();
    }

    /**
     * 解析 target_summary 快照回结构化目标数；null 或格式不符时兜底全 0。
     * 格式由 {@link SimpleIamServerConstant#TEMPLATE_MESSAGE_TARGET_SUMMARY} 唯一产出。
     */
    private int[] parseTargetSummary(String targetSummary) {
        int[] target = new int[]{0, 0, 0, 0};
        if (targetSummary == null || targetSummary.isEmpty()) {
            return target;
        }
        String[] parts = targetSummary.split(",");
        for (String part : parts) {
            String[] keyValue = part.split(":", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = keyValue[0].trim();
            String value = keyValue[1].trim();
            try {
                if ("用户".equals(key)) {
                    target[0] = Integer.parseInt(value);
                } else if ("部门".equals(key)) {
                    target[1] = Integer.parseInt(value);
                } else if ("协作组".equals(key)) {
                    target[2] = Integer.parseInt(value);
                } else if ("包含子部门".equals(key)) {
                    target[3] = "true".equals(value) ? 1 : 0;
                }
            } catch (NumberFormatException ignored) {
                // 历史数据格式异常时保持 0 兜底
            }
        }
        return target;
    }

    /**
     * 全部已读
     */
    @Transactional
    public int markAllRead(Long recipientUserId) {
        int updatedCount = messageRepository.markAllRead(recipientUserId);
        if (updatedCount > 0) {
            publishRecipientsResolved(Collections.singleton(recipientUserId));
        }
        return updatedCount;
    }

    /**
     * 单条已读
     */
    @Transactional
    public IamMessageEntity markRead(Long recipientUserId, Long messageId) {
        IamMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.MESSAGE_NOT_FOUND,
                        String.format(ServerErrorMessage.MESSAGE_NOT_FOUND, messageId)));
        if (!recipientUserId.equals(message.getRecipientUserId())) {
            throw new SimpleIamServerException(ErrorCode.MESSAGE_FORBIDDEN, ServerErrorMessage.MESSAGE_FORBIDDEN);
        }
        if (message.getReadAt() == null) {
            message.setReadAt(Instant.now());
            message = messageRepository.save(message);
            publishRecipientsResolved(Collections.singleton(recipientUserId));
        }
        return message;
    }

    private List<IamMessageEntity> createMessages(CreateMessageRequest request, Long senderUserId, String senderUsername) {
        validateMessageContent(request);
        Set<Long> recipientUserIds = resolveRecipientUserIds(request);
        if (recipientUserIds.isEmpty()) {
            throw new SimpleIamServerException(ErrorCode.MESSAGE_RECIPIENT_EMPTY, ServerErrorMessage.MESSAGE_RECIPIENT_EMPTY);
        }
        String sendBatchId = UUID.randomUUID().toString();
        String targetSummary = buildTargetSummary(request);
        Instant now = Instant.now();
        List<IamMessageEntity> messages = new ArrayList<IamMessageEntity>();
        for (Long recipientUserId : recipientUserIds) {
            IamMessageEntity message = new IamMessageEntity();
            message.setRecipientUserId(recipientUserId);
            message.setSenderUserId(senderUserId);
            message.setSenderUsername(senderUsername);
            message.setTitle(request.getTitle());
            message.setContent(request.getContent());
            message.setSendBatchId(sendBatchId);
            message.setTargetSummary(targetSummary);
            message.setCreatedAt(now);
            messages.add(message);
        }
        List<IamMessageEntity> savedMessages = messageRepository.saveAll(messages);
        publishRecipientsResolved(recipientUserIds);
        return savedMessages;
    }

    private Set<Long> resolveRecipientUserIds(CreateMessageRequest request) {
        Set<Long> recipientUserIds = new LinkedHashSet<Long>();
        Set<Long> explicitUserIds = new LinkedHashSet<Long>(normalizeIds(request.getRecipientUserIds()));
        if (request.getRecipientUserId() != null) {
            explicitUserIds.add(request.getRecipientUserId());
        }
        addActiveExplicitRecipients(recipientUserIds, explicitUserIds);

        List<Long> departmentIds = departmentService.resolveActiveDepartmentIds(
                normalizeIds(request.getDepartmentIds()), Boolean.TRUE.equals(request.getIncludeChildDepartments()));
        if (!departmentIds.isEmpty()) {
            for (IamUserEntity user : userRepository.findByDepartmentIdInAndStatus(
                    departmentIds, SimpleIamServerConstant.STATUS_ACTIVE)) {
                recipientUserIds.add(user.getId());
            }
        }

        addActiveExistingRecipients(recipientUserIds, userGroupService.resolveActiveMemberUserIds(request.getUserGroupIds()));
        return recipientUserIds;
    }

    private void addActiveExplicitRecipients(Set<Long> recipientUserIds, Set<Long> explicitUserIds) {
        if (explicitUserIds.isEmpty()) {
            return;
        }
        Map<Long, IamUserEntity> usersById = indexUsersById(userRepository.findAllById(explicitUserIds));
        for (Long userId : explicitUserIds) {
            IamUserEntity user = usersById.get(userId);
            if (user == null) {
                throw new SimpleIamServerException(ErrorCode.USER_NOT_FOUND,
                        String.format(ServerErrorMessage.USER_NOT_FOUND, userId));
            }
            if (SimpleIamServerConstant.STATUS_ACTIVE == user.getStatus()) {
                recipientUserIds.add(userId);
            }
        }
    }

    private void addActiveExistingRecipients(Set<Long> recipientUserIds, List<Long> userIds) {
        for (IamUserEntity user : userRepository.findAllById(userIds)) {
            if (SimpleIamServerConstant.STATUS_ACTIVE == user.getStatus()) {
                recipientUserIds.add(user.getId());
            }
        }
    }

    private Map<Long, IamUserEntity> indexUsersById(Iterable<IamUserEntity> users) {
        Map<Long, IamUserEntity> usersById = new LinkedHashMap<Long, IamUserEntity>();
        for (IamUserEntity user : users) {
            usersById.put(user.getId(), user);
        }
        return usersById;
    }

    private void validateMessageContent(CreateMessageRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()
                || request.getTitle().length() > SimpleIamServerConstant.MESSAGE_TITLE_MAX_LENGTH) {
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED,
                    String.format(ServerErrorMessage.MESSAGE_TITLE_INVALID,
                            SimpleIamServerConstant.MESSAGE_TITLE_MAX_LENGTH));
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()
                || request.getContent().length() > SimpleIamServerConstant.MESSAGE_CONTENT_MAX_LENGTH) {
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED,
                    String.format(ServerErrorMessage.MESSAGE_CONTENT_INVALID,
                            SimpleIamServerConstant.MESSAGE_CONTENT_MAX_LENGTH));
        }
    }

    private void publishRecipientsResolved(Set<Long> recipientUserIds) {
        eventPublisher.publishEvent(new MessageRecipientsResolvedEvent(new LinkedHashSet<Long>(recipientUserIds)));
    }

    private String buildTargetSummary(CreateMessageRequest request) {
        Set<Long> explicitUserIds = new LinkedHashSet<Long>(normalizeIds(request.getRecipientUserIds()));
        if (request.getRecipientUserId() != null) {
            explicitUserIds.add(request.getRecipientUserId());
        }
        return String.format(SimpleIamServerConstant.TEMPLATE_MESSAGE_TARGET_SUMMARY,
                explicitUserIds.size(),
                normalizeIds(request.getDepartmentIds()).size(),
                normalizeIds(request.getUserGroupIds()).size(),
                Boolean.TRUE.equals(request.getIncludeChildDepartments()));
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> normalizedIds = new LinkedHashSet<Long>();
        for (Long id : ids) {
            if (id != null) {
                normalizedIds.add(id);
            }
        }
        return new ArrayList<Long>(normalizedIds);
    }

}
