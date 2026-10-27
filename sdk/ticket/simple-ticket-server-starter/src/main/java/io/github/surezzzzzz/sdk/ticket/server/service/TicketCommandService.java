package io.github.surezzzzzz.sdk.ticket.server.service;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.configuration.SimpleTicketServerProperties;
import io.github.surezzzzzz.sdk.ticket.server.constant.ActorRole;
import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketIdempotencyConflictException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketNotAccessibleException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketServiceException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketStateException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketComment;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketEvaluation;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketTransition;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketType;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketCommentRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketEvaluationRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketGroupRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketNoSequenceRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTransitionRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTypeRepository;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketNoHelper;
import io.github.surezzzzzz.sdk.ticket.server.support.OpenApiRequestFingerprint;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 工单命令编排（状态机动作全集：可见性→角色→领域方法→乐观锁落库→转移记录→通知）。
 *
 * <p>HTTP 语义映射：不可见=404 掩蔽；角色不符=403；状态冲突=409（乐观锁版本冲突同映射）。</p>
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
@Transactional
public class TicketCommandService {

    private static final Logger log = LoggerFactory.getLogger(TicketCommandService.class);

    private final TicketRepository ticketRepository;

    private final TicketTypeRepository typeRepository;

    private final TicketGroupRepository groupRepository;

    private final TicketTransitionRepository transitionRepository;

    private final TicketCommentRepository commentRepository;

    private final TicketEvaluationRepository evaluationRepository;

    private final io.github.surezzzzzz.sdk.ticket.server.repository.TicketAttachmentRepository attachmentRepository;

    private final io.github.surezzzzzz.sdk.ticket.server.spi.FileProvider fileProvider;

    private final TicketNoSequenceRepository sequenceRepository;

    private final TicketTemplateValidator templateValidator;

    private final NotificationDispatcher notificationDispatcher;

    private final TicketPrincipalResolver principalResolver;

    private final io.github.surezzzzzz.sdk.ticket.server.spi.SubmitterProfileProvider profileProvider;

    private final SimpleTicketServerProperties properties;

    private final io.github.surezzzzzz.sdk.ticket.server.repository.TicketPortalIdempotencyRepository
            portalIdempotencyRepository;

    /**
     * 兼容直接使用 core service 的测试/嵌入式调用；HTTP starter 使用带 Portal 幂等仓储的构造器。
     */
    public TicketCommandService(TicketRepository ticketRepository, TicketTypeRepository typeRepository,
                                TicketGroupRepository groupRepository,
                                TicketTransitionRepository transitionRepository,
                                TicketCommentRepository commentRepository,
                                TicketEvaluationRepository evaluationRepository,
                                io.github.surezzzzzz.sdk.ticket.server.repository.TicketAttachmentRepository
                                        attachmentRepository,
                                io.github.surezzzzzz.sdk.ticket.server.spi.FileProvider fileProvider,
                                TicketNoSequenceRepository sequenceRepository,
                                TicketTemplateValidator templateValidator,
                                NotificationDispatcher notificationDispatcher,
                                TicketPrincipalResolver principalResolver,
                                io.github.surezzzzzz.sdk.ticket.server.spi.SubmitterProfileProvider profileProvider,
                                SimpleTicketServerProperties properties) {
        this(ticketRepository, typeRepository, groupRepository, transitionRepository, commentRepository,
                evaluationRepository, attachmentRepository, fileProvider, sequenceRepository, templateValidator,
                notificationDispatcher, principalResolver, profileProvider, properties, null);
    }

    /**
     * Starter 运行构造器。
     */
    @Autowired
    public TicketCommandService(TicketRepository ticketRepository, TicketTypeRepository typeRepository,
                                TicketGroupRepository groupRepository,
                                TicketTransitionRepository transitionRepository,
                                TicketCommentRepository commentRepository,
                                TicketEvaluationRepository evaluationRepository,
                                io.github.surezzzzzz.sdk.ticket.server.repository.TicketAttachmentRepository
                                        attachmentRepository,
                                io.github.surezzzzzz.sdk.ticket.server.spi.FileProvider fileProvider,
                                TicketNoSequenceRepository sequenceRepository,
                                TicketTemplateValidator templateValidator,
                                NotificationDispatcher notificationDispatcher,
                                TicketPrincipalResolver principalResolver,
                                io.github.surezzzzzz.sdk.ticket.server.spi.SubmitterProfileProvider profileProvider,
                                SimpleTicketServerProperties properties,
                                io.github.surezzzzzz.sdk.ticket.server.repository.TicketPortalIdempotencyRepository
                                        portalIdempotencyRepository) {
        this.ticketRepository = ticketRepository;
        this.typeRepository = typeRepository;
        this.groupRepository = groupRepository;
        this.transitionRepository = transitionRepository;
        this.commentRepository = commentRepository;
        this.evaluationRepository = evaluationRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileProvider = fileProvider;
        this.sequenceRepository = sequenceRepository;
        this.templateValidator = templateValidator;
        this.notificationDispatcher = notificationDispatcher;
        this.principalResolver = principalResolver;
        this.profileProvider = profileProvider;
        this.properties = properties;
        this.portalIdempotencyRepository = portalIdempotencyRepository;
    }

    /**
     * 提交工单（portal 人员令牌；频控+模板校验+通知默认组）。
     *
     * @param context    请求上下文
     * @param typeCode   类型编码
     * @param subject    标题
     * @param description 描述
     * @param priority   优先级（可 null 默认 NORMAL）
     * @param answers    模板答案
     * @param relatedTicketNo 关联原单号（可 null）
     * @return 新工单
     */
    public Ticket createPortal(TicketRequestContext context, String typeCode, String subject,
                               String description, String priority, Map<String, Object> answers,
                               String relatedTicketNo, java.util.List<Map<String, Object>> attachments) {
        return createPortal(context, null, typeCode, subject, description, priority, answers,
                relatedTicketNo, attachments);
    }

    /**
     * 提交工单（带 Portal 幂等键的 HTTP 入口）。同一主体同一键只创建一次。
     *
     * @param context 请求上下文
     * @param idempotencyKey 幂等键；嵌入式调用可为空
     * @param typeCode 类型编码
     * @param subject 标题
     * @param description 描述
     * @param priority 优先级
     * @param answers 模板答案
     * @param relatedTicketNo 关联原单号
     * @param attachments 附件
     * @return 新工单或幂等重放工单
     */
    public Ticket createPortal(TicketRequestContext context, String idempotencyKey, String typeCode,
                               String subject, String description, String priority, Map<String, Object> answers,
                               String relatedTicketNo, java.util.List<Map<String, Object>> attachments) {
        return createPortalResult(context, idempotencyKey, typeCode, subject, description, priority, answers,
                relatedTicketNo, attachments).getTicket();
    }

    /**
     * Portal 创建结果，供 HTTP 层区分首次创建和幂等重放。
     */
    public PortalCreateResult createPortalResult(TicketRequestContext context, String idempotencyKey,
                                                 String typeCode, String subject, String description,
                                                 String priority, Map<String, Object> answers,
                                                 String relatedTicketNo,
                                                 java.util.List<Map<String, Object>> attachments) {
        if (idempotencyKey != null
                && (idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 128)) {
            throw new TicketValidationException("idempotencyKey", "为空或超过上限");
        }
        checkLengths(subject, description);
        TicketType type = requireEnabledType(typeCode);
        TicketPriority normalizedPriority = parsePriority(priority);
        if (normalizedPriority == null) {
            normalizedPriority = TicketPriority.NORMAL;
        }
        String formAnswers = templateValidator.validateAnswers(type.getFormTemplate(), answers);
        String fingerprint = idempotencyKey == null ? null : OpenApiRequestFingerprint.portal(
                typeCode, subject, description, normalizedPriority, context.getSubjectId(), answers,
                relatedTicketNo, attachments);
        if (idempotencyKey != null && portalIdempotencyRepository != null) {
            Optional<io.github.surezzzzzz.sdk.ticket.server.repository.TicketPortalIdempotencyRepository.Record>
                    existing = portalIdempotencyRepository.find(context.getSubjectId(), idempotencyKey);
            if (existing.isPresent()) {
                Ticket saved = ticketRepository.findById(existing.get().getTicketId())
                        .orElseThrow(() -> new TicketServiceException("幂等记录对应工单不存在"));
                if (!fingerprint.equals(existing.get().getRequestFingerprint())) {
                    throw new TicketIdempotencyConflictException();
                }
                return new PortalCreateResult(saved, false);
            }
        }
        String resolvedOwnerName = profileProvider.displayNameOf(context.getSubjectId());
        if (resolvedOwnerName == null || resolvedOwnerName.trim().isEmpty()) {
            resolvedOwnerName = context.getSubjectId();
        }
        Ticket ticket = newTicket(type, subject, description, formAnswers, priority,
                context.getSubjectId(), resolvedOwnerName.trim(), TicketSource.PORTAL, null, null,
                relatedTicketNo);
        ticket.setIdempotencyKey(idempotencyKey);
        ticket.setRequestFingerprint(fingerprint);
        insert(ticket);
        if (idempotencyKey != null && portalIdempotencyRepository != null) {
            try {
                portalIdempotencyRepository.insert(context.getSubjectId(), idempotencyKey, fingerprint,
                        ticket.getId());
            } catch (DuplicateKeyException race) {
                io.github.surezzzzzz.sdk.ticket.server.repository.TicketPortalIdempotencyRepository.Record
                        raced = portalIdempotencyRepository.find(context.getSubjectId(), idempotencyKey)
                        .orElseThrow(() -> new TicketServiceException(race));
                Ticket saved = ticketRepository.findById(raced.getTicketId())
                        .orElseThrow(() -> new TicketServiceException(race));
                if (!fingerprint.equals(raced.getRequestFingerprint())) {
                    throw new TicketIdempotencyConflictException();
                }
                return new PortalCreateResult(saved, false);
            }
        }
        bindAttachments(ticket, context.getSubjectId(), attachments);
        notifyGroup(type.getDefaultGroup(), ticket, "新工单");
        if (log.isDebugEnabled()) {
            log.debug("工单提交：ticketNo={}, source={}, created={}",
                    ticket.getTicketNo(), ticket.getSource(), true);
        }
        return new PortalCreateResult(ticket, true);
    }

    /**
     * 机器开单（openapi；Idempotency-Key 必填：同键同体返回原单、异体 409）。
     *
     * @param credentialId    凭据标识
     * @param idempotencyKey  幂等键
     * @param typeCode        类型编码
     * @param subject         标题
     * @param description     描述
     * @param priority        优先级
     * @param ownerSubjectId  提单人标识（必填字段——谁的问题就是谁的单；姓名服务端解析）
     * @param answers         模板答案
     * @return 工单（幂等命中时为已存单）
     */
    public Ticket createOpenApi(String credentialId, String idempotencyKey, String typeCode, String subject,
                                String description, String priority, String ownerSubjectId,
                                Map<String, Object> answers) {
        return createOpenApiResult(credentialId, idempotencyKey, typeCode, subject, description, priority,
                ownerSubjectId, answers).getTicket();
    }

    /**
     * 返回开单与重放的区别，供 HTTP 层准确区分 201 和 200。
     *
     * @param credentialId 凭据标识
     * @param idempotencyKey 幂等键
     * @param typeCode 类型编码
     * @param subject 标题
     * @param description 描述
     * @param priority 优先级
     * @param ownerSubjectId 提单人标识
     * @param answers 模板答案
     * @return 开单结果
     */
    public OpenApiCreateResult createOpenApiResult(String credentialId, String idempotencyKey, String typeCode,
                                                   String subject, String description, String priority,
                                                   String ownerSubjectId, Map<String, Object> answers) {
        if (credentialId == null || credentialId.trim().isEmpty() || credentialId.length() > 128) {
            throw new TicketValidationException("credentialId", "为空或超过上限");
        }
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 128) {
            throw new TicketValidationException("idempotencyKey", "为空或超过上限");
        }
        if (ownerSubjectId == null || ownerSubjectId.trim().isEmpty()) {
            throw new TicketValidationException("ownerSubjectId", "为必填项");
        }
        TicketPriority normalizedPriority = parsePriority(priority);
        if (normalizedPriority == null) {
            normalizedPriority = TicketPriority.NORMAL;
        }
        String fingerprint = OpenApiRequestFingerprint.of(typeCode, subject, description,
                normalizedPriority, ownerSubjectId, answers);
        Optional<Ticket> existing = ticketRepository.findByIdempotency(TicketSource.OPENAPI,
                credentialId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }
        checkLengths(subject, description);
        TicketType type = requireEnabledType(typeCode);
        String formAnswers = templateValidator.validateAnswers(type.getFormTemplate(), answers);
        String resolvedOwnerName = profileProvider.displayNameOf(ownerSubjectId);
        Ticket ticket = newTicket(type, subject, description, formAnswers, normalizedPriority.name(),
                ownerSubjectId, resolvedOwnerName, TicketSource.OPENAPI, credentialId,
                idempotencyKey, null);
        ticket.setRequestFingerprint(fingerprint);
        try {
            ticket.setId(ticketRepository.insert(ticket));
        } catch (DuplicateKeyException race) {
            Optional<Ticket> raced = ticketRepository.findByIdempotencyCurrent(TicketSource.OPENAPI,
                    credentialId, idempotencyKey);
            if (raced.isPresent()) {
                return replay(raced.get(), fingerprint);
            }
            throw new TicketServiceException(race);
        }
        notifyGroup(type.getDefaultGroup(), ticket, "新工单（机器）");
        if (log.isDebugEnabled()) {
            log.debug("工单提交：ticketNo={}, source={}, created={}",
                    ticket.getTicketNo(), ticket.getSource(), true);
        }
        return new OpenApiCreateResult(ticket, true);
    }

    private OpenApiCreateResult replay(Ticket saved, String fingerprint) {
        if (!fingerprint.equals(saved.getRequestFingerprint())) {
            throw new TicketIdempotencyConflictException();
        }
        if (log.isDebugEnabled()) {
            log.debug("工单提交：ticketNo={}, source={}, created={}",
                    saved.getTicketNo(), saved.getSource(), false);
        }
        return new OpenApiCreateResult(saved, false);
    }

    /**
     * 机器开单结果。
     */
    @Getter
    public static class PortalCreateResult {

        private final Ticket ticket;

        private final boolean created;

        public PortalCreateResult(Ticket ticket, boolean created) {
            this.ticket = ticket;
            this.created = created;
        }
    }

    /**
     * 机器开单结果。
     */
    @Getter
    public static class OpenApiCreateResult {

        private final Ticket ticket;

        private final boolean created;

        public OpenApiCreateResult(Ticket ticket, boolean created) {
            this.ticket = ticket;
            this.created = created;
        }

    }

    /**
     * 受理（组内成员含组长/admin）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @return 工单
     */
    public Ticket claim(TicketRequestContext context, String ticketNo) {
        return claim(context, ticketNo, null);
    }

    /**
     * 受理（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket claim(TicketRequestContext context, String ticketNo, Long expectedVersion) {
        Ticket ticket = visibleForManage(context, ticketNo);
        requireGroupMember(context, ticket, "受理");
        record(ticket, t -> t.claim(defaultGroup(ticket), context.getSubjectId(),
                context.getDisplayName(), now()), context, ActorRole.ASSIGNEE, "CLAIM", null, expectedVersion);
        notifyOwner(ticket, "工单已受理");
        return ticket;
    }

    /**
     * 分派（组长/admin；指定组与人，不问被派人意愿）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param groupCode 目标组
     * @param assigneeSubjectId 被派人
     * @return 工单
     */
    public Ticket assign(TicketRequestContext context, String ticketNo, String groupCode,
                         String assigneeSubjectId) {
        return assign(context, ticketNo, groupCode, assigneeSubjectId, null);
    }

    /**
     * 分派（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param groupCode 目标组
     * @param assigneeSubjectId 被派人
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket assign(TicketRequestContext context, String ticketNo, String groupCode,
                         String assigneeSubjectId, Long expectedVersion) {
        Ticket ticket = visibleForManage(context, ticketNo);
        requireLeaderOrAdmin(context, ticket, "分派");
        String resolvedName = profileProvider.displayNameOf(assigneeSubjectId);
        record(ticket, t -> t.assign(groupCode, assigneeSubjectId, resolvedName, now()),
                context, ActorRole.ADMIN, "ASSIGN", null, expectedVersion);
        notificationDispatcher.dispatch(assigneeSubjectId, "工单已分派给你",
                ticket.getTicketNo() + " " + ticket.getSubject());
        return ticket;
    }

    /**
     * 转派（组长/admin；换组/换人）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param groupCode 目标组
     * @param assigneeSubjectId 新处理人
     * @return 工单
     */
    public Ticket transfer(TicketRequestContext context, String ticketNo, String groupCode,
                           String assigneeSubjectId) {
        return transfer(context, ticketNo, groupCode, assigneeSubjectId, null);
    }

    /**
     * 转派（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param groupCode 目标组
     * @param assigneeSubjectId 新处理人
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket transfer(TicketRequestContext context, String ticketNo, String groupCode,
                           String assigneeSubjectId, Long expectedVersion) {
        Ticket ticket = visibleForManage(context, ticketNo);
        requireLeaderOrAdmin(context, ticket, "转派");
        String previous = ticket.getAssigneeSubjectId();
        String resolvedName = profileProvider.displayNameOf(assigneeSubjectId);
        record(ticket, t -> t.transfer(groupCode, assigneeSubjectId, resolvedName, now()),
                context, ActorRole.ADMIN, "TRANSFER", null, expectedVersion);
        notificationDispatcher.dispatch(assigneeSubjectId, "工单已转派给你",
                ticket.getTicketNo() + " " + ticket.getSubject());
        if (previous != null && !previous.equals(assigneeSubjectId)) {
            notificationDispatcher.dispatch(previous, "工单已转出",
                    ticket.getTicketNo() + " " + ticket.getSubject());
        }
        return ticket;
    }

    /**
     * 待补充（处理人）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param reason 原因码
     * @return 工单
     */
    public Ticket requestInfo(TicketRequestContext context, String ticketNo, RequestInfoReason reason) {
        return requestInfo(context, ticketNo, reason, null);
    }

    /**
     * 待补充（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param reason 原因
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket requestInfo(TicketRequestContext context, String ticketNo, RequestInfoReason reason,
                              Long expectedVersion) {
        Ticket ticket = visibleForManage(context, ticketNo);
        requireAssigneeOrAdmin(context, ticket, "待补充");
        record(ticket, t -> t.requestInfo(reason, now()), context, ActorRole.ASSIGNEE,
                "REQUEST_INFO", reason, expectedVersion);
        notifyOwner(ticket, "工单待你补充");
        return ticket;
    }

    /**
     * 补充（提单人；恢复计时）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param body 补充正文（落留言）
     * @return 工单
     */
    public Ticket supply(TicketRequestContext context, String ticketNo, String body) {
        return supply(context, ticketNo, body, null);
    }

    /**
     * 补充（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param body 补充正文
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket supply(TicketRequestContext context, String ticketNo, String body, Long expectedVersion) {
        Ticket ticket = visibleOwned(context, ticketNo);
        record(ticket, t -> t.supply(now()), context, ActorRole.OWNER, "SUPPLY", null, expectedVersion);
        addComment(ticket, context, ActorRole.OWNER, body, false, null);
        notifyAssignee(ticket, "工单已补充");
        return ticket;
    }

    /**
     * 留言（不改状态；internal 仅处理侧可 true）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param body 正文
     * @param internal 内部备注
     * @return 留言
     */
    public TicketComment comment(TicketRequestContext context, String ticketNo, String body, boolean internal) {
        return comment(context, ticketNo, body, internal, null);
    }

    /**
     * 留言（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param body 正文
     * @param internal 内部备注
     * @param expectedVersion 客户端详情版本，可为空
     * @return 留言
     */
    public TicketComment comment(TicketRequestContext context, String ticketNo, String body, boolean internal,
                                 Long expectedVersion) {
        Ticket ticket = visible(context, ticketNo);
        if (ticket.isTerminal()) {
            throw new TicketStateException("留言", ticket.getStatus());
        }
        ActorRole role = actorRoleFor(context, ticket);
        if (internal && role == ActorRole.OWNER) {
            throw new TicketForbiddenActionException();
        }
        return addComment(ticket, context, role, body, internal && role != ActorRole.OWNER, expectedVersion);
    }

    /**
     * 办结（处理人；生成确认期限）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @return 工单
     */
    public Ticket resolve(TicketRequestContext context, String ticketNo) {
        return resolve(context, ticketNo, null);
    }

    /**
     * 办结（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket resolve(TicketRequestContext context, String ticketNo, Long expectedVersion) {
        Ticket ticket = visibleForManage(context, ticketNo);
        requireAssigneeOrAdmin(context, ticket, "办结");
        record(ticket, t -> t.resolve(properties.getTicket().getConfirmTimeoutHours(), now()),
                context, ActorRole.ASSIGNEE, "RESOLVE", null, expectedVersion);
        notifyOwner(ticket, "工单已办结，请确认");
        return ticket;
    }

    /**
     * 确认关闭（提单人；邀请评价）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @return 工单
     */
    public Ticket confirm(TicketRequestContext context, String ticketNo) {
        return confirm(context, ticketNo, null);
    }

    /**
     * 确认关闭（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket confirm(TicketRequestContext context, String ticketNo, Long expectedVersion) {
        Ticket ticket = visibleOwned(context, ticketNo);
        ActorRole actorRole = context.isMachine() ? ActorRole.SYSTEM : ActorRole.OWNER;
        record(ticket, t -> t.confirm(now()), context, actorRole, "CONFIRM", null, expectedVersion);
        notifyOwner(ticket, "工单已关闭，欢迎评价");
        return ticket;
    }

    /**
     * 不认可重开（提单人；回处理中）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @return 工单
     */
    public Ticket reject(TicketRequestContext context, String ticketNo) {
        return reject(context, ticketNo, null);
    }

    /**
     * 重开（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket reject(TicketRequestContext context, String ticketNo, Long expectedVersion) {
        Ticket ticket = visibleOwned(context, ticketNo);
        record(ticket, t -> t.reject(now()), context, ActorRole.OWNER, "REJECT", null, expectedVersion);
        notifyAssignee(ticket, "工单被退回重开");
        return ticket;
    }

    /**
     * 撤销（提单人；通知处理组）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @return 工单
     */
    public Ticket withdraw(TicketRequestContext context, String ticketNo) {
        return withdraw(context, ticketNo, null);
    }

    /**
     * 撤销（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param expectedVersion 客户端详情版本，可为空
     * @return 工单
     */
    public Ticket withdraw(TicketRequestContext context, String ticketNo, Long expectedVersion) {
        Ticket ticket = visibleOwned(context, ticketNo);
        record(ticket, t -> t.withdraw(now()), context, ActorRole.OWNER, "WITHDRAW", null, expectedVersion);
        notifyGroup(ticket.getAssigneeGroup(), ticket, "工单已撤销");
        return ticket;
    }

    /**
     * 评价（提单人；CLOSED 后一次，confirm 关闭可评——超时关闭单在扫描侧不邀请且此处按终态关闭拒评）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param star 星级 1-5
     * @param commentText 评语
     * @return 评价
     */
    public TicketEvaluation evaluate(TicketRequestContext context, String ticketNo, int star, String commentText) {
        return evaluate(context, ticketNo, star, commentText, null);
    }

    /**
     * 评价（带 HTTP 乐观锁版本门槛）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @param star 星级
     * @param commentText 评语
     * @param expectedVersion 客户端详情版本，可为空
     * @return 评价
     */
    public TicketEvaluation evaluate(TicketRequestContext context, String ticketNo, int star, String commentText,
                                      Long expectedVersion) {
        Ticket ticket = visibleOwned(context, ticketNo);
        requireExpectedVersion(ticket, expectedVersion);
        if (ticket.getStatus() != io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus.CLOSED) {
            throw new TicketStateException("评价", ticket.getStatus(),
                    io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus.CLOSED);
        }
        if (transitionRepository.existsAction(ticket.getId(), "CONFIRM_TIMEOUT")) {
            throw new TicketStateException("评价", ticket.getStatus());
        }
        if (star < SimpleTicketServerConstant.STAR_MIN || star > SimpleTicketServerConstant.STAR_MAX) {
            throw new TicketValidationException("star", "须在 1-5 之间");
        }
        TicketEvaluation evaluation = TicketEvaluation.builder()
                .ticketId(ticket.getId())
                .evaluatorSubjectId(context.getSubjectId())
                .star(star)
                .comment(commentText)
                .evaluatedAt(now())
                .build();
        try {
            evaluationRepository.insert(evaluation);
        } catch (DuplicateKeyException once) {
            throw new TicketStateException("评价", ticket.getStatus());
        }
        notifyAssignee(ticket, "工单收到评价 " + star + " 星");
        return evaluation;
    }

    /**
     * 确认超时自动关闭（系统唯一自动转移，扫描调用；不邀请评价）。
     *
     * @param ticket 工单
     * @return 是否成功关闭
     */
    public boolean confirmTimeout(Ticket ticket) {
        try {
            record(ticket, t -> t.confirmTimeout(now()), null, ActorRole.SYSTEM, "CONFIRM_TIMEOUT", null);
            return true;
        } catch (TicketStateException skip) {
            return false;
        }
    }

    private void bindAttachments(Ticket ticket, String uploaderSubjectId,
                                 java.util.List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        long existingCount = attachmentRepository.countByTicket(ticket.getId());
        if (existingCount + attachments.size() > properties.getAttachment().getMaxCountPerTicket()) {
            throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
        }
        for (Map<String, Object> item : attachments) {
            String objectKey = item.get("objectKey") == null ? null : String.valueOf(item.get("objectKey"));
            String fileName = item.get("fileName") == null ? null : String.valueOf(item.get("fileName"));
            long declaredSize;
            try {
                declaredSize = item.get("size") == null ? -1L
                        : Long.parseLong(String.valueOf(item.get("size")));
            } catch (NumberFormatException invalidSize) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
            }
            if (objectKey == null || fileName == null || fileName.trim().isEmpty()
                    || declaredSize <= 0
                    || declaredSize > properties.getAttachment().getMaxSizeMb() * 1024L * 1024L
                    || !fileProvider.isUploadKeyOwned(objectKey, uploaderSubjectId)) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
            }
            String contentType = item.get("contentType") == null ? null : String.valueOf(item.get("contentType"));
            if (contentType == null || contentType.trim().isEmpty()) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
            }
            if (!allowedTypes().contains(extensionOf(fileName))) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
            }
            if (attachmentRepository.findByObjectKey(objectKey).isPresent()) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException();
            }
            if (!fileProvider.exists(objectKey)) {
                throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentNotFoundException();
            }
            attachmentRepository.insert(io.github.surezzzzzz.sdk.ticket.server.model.TicketAttachment.builder()
                    .ticketId(ticket.getId())
                    .commentId(null)
                    .objectKey(objectKey)
                    .fileName(fileName)
                    .size(declaredSize)
                    .contentType(contentType)
                    .uploadedBy(uploaderSubjectId)
                    .createdAt(now())
                    .build());
        }
    }

    private java.util.Set<String> allowedTypes() {
        return new java.util.HashSet<>(properties.getAttachment().getAllowedTypes());
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    // ==================== 可见性与角色 ====================

    /**
     * 可见性判定（owner=本人/组内/admin——DESIGN §10）。
     *
     * @param context 上下文
     * @param ticket 工单
     * @return 可见与否
     */
    public boolean canAccess(TicketRequestContext context, Ticket ticket) {
        if (context.isAllTicketRead()) {
            return true;
        }
        if (context.isMachine() && ticket.getSource() == TicketSource.OPENAPI
                && context.getSubjectId().equals(ticket.getCreatorCredentialId())) {
            return true;
        }
        if (ticket.getOwnerSubjectId().equals(context.getSubjectId())) {
            return true;
        }
        String effectiveGroup = ticket.getAssigneeGroup() == null
                ? defaultGroup(ticket) : ticket.getAssigneeGroup();
        return effectiveGroup != null
                && groupRepository.findEnabledGroupCodesOf(context.getSubjectId()).contains(effectiveGroup);
    }

    private Ticket visible(TicketRequestContext context, String ticketNo) {
        Ticket ticket = ticketRepository.findByNo(ticketNo).orElseThrow(TicketNotAccessibleException::new);
        if (!canAccess(context, ticket)) {
            throw new TicketNotAccessibleException();
        }
        return ticket;
    }

    private Ticket visibleOwned(TicketRequestContext context, String ticketNo) {
        Ticket ticket = visible(context, ticketNo);
        boolean machineOwner = context.isMachine()
                && ticket.getSource() == TicketSource.OPENAPI
                && context.getSubjectId().equals(ticket.getCreatorCredentialId());
        if (!machineOwner && !ticket.getOwnerSubjectId().equals(context.getSubjectId())) {
            throw new TicketForbiddenActionException();
        }
        return ticket;
    }

    private Ticket visibleForManage(TicketRequestContext context, String ticketNo) {
        return visible(context, ticketNo);
    }

    private void requireGroupMember(TicketRequestContext context, Ticket ticket, String action) {
        if (!groupRepository.findEnabledGroupCodesOf(context.getSubjectId())
                .contains(ticket.getAssigneeGroup() == null ? defaultGroup(ticket)
                        : ticket.getAssigneeGroup())) {
            throw new TicketForbiddenActionException();
        }
    }

    private void requireLeaderOrAdmin(TicketRequestContext context, Ticket ticket, String action) {
        if (context.isAllTicketRead()) {
            return;
        }
        if (!groupRepository.findByCode(ticket.getAssigneeGroup() == null
                ? defaultGroup(ticket) : ticket.getAssigneeGroup())
                .filter(group -> group.isEnabled() && group.isLeader(context.getSubjectId()))
                .isPresent()) {
            throw new TicketForbiddenActionException();
        }
    }

    private void requireAssigneeOrAdmin(TicketRequestContext context, Ticket ticket, String action) {
        if (ticket.getAssigneeSubjectId() == null
                || !ticket.getAssigneeSubjectId().equals(context.getSubjectId())) {
            throw new TicketForbiddenActionException();
        }
    }

    private ActorRole actorRoleFor(TicketRequestContext context, Ticket ticket) {
        if (context.isMachine()) {
            return ActorRole.SYSTEM;
        }
        if (context.isAllTicketRead()) {
            return ActorRole.ADMIN;
        }
        if (ticket.getOwnerSubjectId().equals(context.getSubjectId())) {
            return ActorRole.OWNER;
        }
        return ActorRole.ASSIGNEE;
    }

    // ==================== 内部 ====================

    private interface Mutation {

        void apply(Ticket ticket);
    }

    private void record(Ticket ticket, Mutation mutation, TicketRequestContext context, ActorRole role,
                        String action, RequestInfoReason reason) {
        record(ticket, mutation, context, role, action, reason, null);
    }

    private void record(Ticket ticket, Mutation mutation, TicketRequestContext context, ActorRole role,
                        String action, RequestInfoReason reason, Long expectedVersion) {
        io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus from = ticket.getStatus();
        requireExpectedVersion(ticket, expectedVersion);
        mutation.apply(ticket);
        int updated = ticketRepository.update(ticket);
        if (updated == 0) {
            throw new TicketStateException(action, from);
        }
        transitionRepository.insert(TicketTransition.builder()
                .ticketId(ticket.getId())
                .fromStatus(from)
                .toStatus(ticket.getStatus())
                .action(action)
                .actorSubjectId(context == null ? null : context.getSubjectId())
                .actorRole(role)
                .reasonCode(reason)
                .createdAt(now())
                .build());
        if (log.isDebugEnabled()) {
            log.debug("工单转移：ticketNo={}, action={}, from={}, to={}",
                    ticket.getTicketNo(), action, from, ticket.getStatus());
        }
    }

    private TicketComment addComment(Ticket ticket, TicketRequestContext context, ActorRole role,
                                     String body, boolean internal) {
        return addComment(ticket, context, role, body, internal, null);
    }

    private TicketComment addComment(Ticket ticket, TicketRequestContext context, ActorRole role,
                                     String body, boolean internal, Long expectedVersion) {
        if (body == null || body.trim().isEmpty()) {
            throw new TicketValidationException("body", "留言不能为空");
        }
        requireExpectedVersion(ticket, expectedVersion);
        TicketComment comment = TicketComment.builder()
                .ticketId(ticket.getId())
                .authorSubjectId(context.getSubjectId())
                .authorRole(role)
                .authorName(context.getDisplayName())
                .body(body)
                .internal(internal)
                .createdAt(now())
                .build();
        commentRepository.insert(comment);
        ticket.touchActivity(now());
        if (ticketRepository.update(ticket) == 0) {
            throw new TicketStateException("留言", ticket.getStatus());
        }
        return comment;
    }

    private void requireExpectedVersion(Ticket ticket, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion.longValue() != ticket.getVersion()) {
            throw new TicketStateException("版本校验", ticket.getStatus());
        }
    }

    private Ticket newTicket(TicketType type, String subject, String description, String formAnswers,
                             String priority, String ownerSubjectId, String ownerName, TicketSource source,
                             String credentialId, String idempotencyKey, String relatedTicketNo) {
        Instant now = now();
        LocalDate day = LocalDate.now(TicketNoHelper.beijingZone());
        String dateKey = day.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        long seq = sequenceRepository.next(dateKey);
        return Ticket.newTicket(TicketNoHelper.format(day.atStartOfDay(), seq), type.getCode(), subject,
                description, formAnswers, parsePriority(priority), ownerSubjectId, ownerName, source,
                credentialId, idempotencyKey, relatedTicketNo, type.getSlaResponseMinutes(),
                type.getSlaResolutionMinutes(), now);
    }

    private void insert(Ticket ticket) {
        ticket.setId(ticketRepository.insert(ticket));
    }

    private void checkLengths(String subject, String description) {
        if (subject == null || subject.trim().isEmpty()
                || subject.length() > properties.getTicket().getSubjectMaxLength()) {
            throw new TicketValidationException("subject", "为空或超过上限");
        }
        if (description != null && description.length() > properties.getTicket().getDescriptionMaxLength()) {
            throw new TicketValidationException("description", "超过上限");
        }
    }

    private TicketType requireEnabledType(String typeCode) {
        TicketType type = typeRepository.findByCode(typeCode).orElseThrow(
                io.github.surezzzzzz.sdk.ticket.server.exception.TicketTypeNotFoundException::new);
        if (!type.isEnabled()) {
            throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketTypeDisabledException();
        }
        return type;
    }

    private String defaultGroup(Ticket ticket) {
        return typeRepository.findByCode(ticket.getTypeCode())
                .map(TicketType::getDefaultGroup).orElse(null);
    }

    private void notifyOwner(Ticket ticket, String action) {
        notificationDispatcher.dispatch(ticket.getOwnerSubjectId(),
                action + "：" + ticket.getTicketNo(), ticket.getSubject());
    }

    private void notifyAssignee(Ticket ticket, String action) {
        if (ticket.getAssigneeSubjectId() != null) {
            notificationDispatcher.dispatch(ticket.getAssigneeSubjectId(),
                    action + "：" + ticket.getTicketNo(), ticket.getSubject());
        }
    }

    private void notifyGroup(String groupCode, Ticket ticket, String action) {
        if (groupCode == null) {
            return;
        }
        groupRepository.findByCode(groupCode).ifPresent(group ->
                notificationDispatcher.dispatch(group.getMemberSubjectIds(),
                        action + "：" + ticket.getTicketNo(), ticket.getSubject()));
    }

    private TicketPriority parsePriority(String priority) {
        if (priority == null || priority.trim().isEmpty()) {
            return null;
        }
        String normalized = priority.trim().toUpperCase(Locale.ROOT);
        for (TicketPriority candidate : TicketPriority.values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        throw new TicketValidationException("priority", "不合法");
    }

    private Instant now() {
        return Instant.now();
    }
}
