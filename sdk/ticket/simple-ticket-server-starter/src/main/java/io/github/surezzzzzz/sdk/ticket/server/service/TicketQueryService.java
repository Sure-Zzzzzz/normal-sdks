package io.github.surezzzzzz.sdk.ticket.server.service;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.constant.ActorRole;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketNotAccessibleException;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketComment;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketTransition;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketAttachmentRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketCommentRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketGroupRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTransitionRepository;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketRequestParameterParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.time.Instant;

/**
 * 工单查询（DATA 三层：owner/组内/凭据；时间线 internal 按角色过滤——DESIGN §10/§7）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
public class TicketQueryService {

    private final TicketRepository ticketRepository;

    private final io.github.surezzzzzz.sdk.ticket.server.repository.TicketTypeRepository typeRepository;

    private final TicketCommentRepository commentRepository;

    private final TicketTransitionRepository transitionRepository;

    private final TicketAttachmentRepository attachmentRepository;

    private final TicketGroupRepository groupRepository;

    public TicketQueryService(TicketRepository ticketRepository,
                              io.github.surezzzzzz.sdk.ticket.server.repository.TicketTypeRepository typeRepository,
                              TicketCommentRepository commentRepository,
                              TicketTransitionRepository transitionRepository,
                              TicketAttachmentRepository attachmentRepository,
                              TicketGroupRepository groupRepository) {
        this.ticketRepository = ticketRepository;
        this.typeRepository = typeRepository;
        this.commentRepository = commentRepository;
        this.transitionRepository = transitionRepository;
        this.attachmentRepository = attachmentRepository;
        this.groupRepository = groupRepository;
    }

    /**
     * 我的工单分页。
     *
     * @param subjectId 提单人
     * @param statuses 状态过滤
     * @param page 页码（1 起）
     * @param size 页大小
     * @return 列表
     */
    public List<Ticket> myTickets(String subjectId,
                                  Collection<io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus> statuses,
                                  int page, int size) {
        return ticketRepository.findMine(subjectId, statuses, offset(page, size),
                TicketRequestParameterParser.safeSize(size));
    }

    /** 带类型、日期和关键词过滤的我的工单查询。 */
    public List<Ticket> myTickets(String subjectId,
                                  Collection<io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus> statuses,
                                  String typeCode, java.time.Instant from, java.time.Instant to, String query,
                                  int page, int size) {
        return ticketRepository.findMine(subjectId, statuses, typeCode, from, to, query,
                offset(page, size), TicketRequestParameterParser.safeSize(size));
    }

    /**
     * 组队列分页（DATA=请求者的启用组）。
     *
     * @param subjectId 请求者
     * @param statuses 状态过滤
     * @param page 页码
     * @param size 页大小
     * @return 列表
     */
    public List<Ticket> queue(String subjectId,
                              Collection<io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus> statuses,
                              int page, int size) {
        List<String> groups = groupRepository.findEnabledGroupCodesOf(subjectId);
        if (groups.isEmpty()) {
            return new ArrayList<>();
        }
        return ticketRepository.findQueue(groups, statuses, offset(page, size),
                TicketRequestParameterParser.safeSize(size));
    }

    /** 带优先级、超时和关键词过滤的组队列查询。 */
    public List<Ticket> queue(String subjectId,
                              Collection<io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus> statuses,
                              io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority priority,
                              Boolean overdue, String query, int page, int size) {
        List<String> groups = groupRepository.findEnabledGroupCodesOf(subjectId);
        if (groups.isEmpty()) {
            return new ArrayList<>();
        }
        return ticketRepository.findQueue(groups, statuses, priority, overdue, query,
                offset(page, size), TicketRequestParameterParser.safeSize(size));
    }

    /**
     * 全部工单分页（admin 面）。
     *
     * @param statuses 状态过滤
     * @param page 页码
     * @param size 页大小
     * @return 列表
     */
    public List<Ticket> allTickets(Collection<io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus> statuses,
                                   int page, int size) {
        return ticketRepository.findAll(statuses, offset(page, size), TicketRequestParameterParser.safeSize(size));
    }

    /**
     * openapi 凭据的工单分页（消费方轮询）。
     *
     * @param credentialId 凭据标识
     * @param statuses 状态过滤
     * @param page 页码
     * @param size 页大小
     * @return 列表
     */
    public List<Ticket> byCredential(String credentialId,
                                     Collection<io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus> statuses,
                                     int page, int size) {
        return ticketRepository.findByCredential(credentialId, statuses, offset(page, size),
                TicketRequestParameterParser.safeSize(size));
    }

    /**
     * 详情（可见性校验；不可见 404 掩蔽）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @return 工单
     */
    public Ticket detail(TicketRequestContext context, String ticketNo) {
        Ticket ticket = ticketRepository.findByNo(ticketNo).orElseThrow(TicketNotAccessibleException::new);
        boolean visible = context.isAllTicketRead()
                || ticket.getOwnerSubjectId().equals(context.getSubjectId())
                || (ticket.getAssigneeGroup() != null && groupRepository
                        .findEnabledGroupCodesOf(context.getSubjectId()).contains(ticket.getAssigneeGroup()))
                || (ticket.getAssigneeGroup() == null && typeDefaultGroupOf(ticket) != null
                        && groupRepository.findEnabledGroupCodesOf(context.getSubjectId())
                        .contains(typeDefaultGroupOf(ticket)))
                || (context.isMachine() && ticket.getSource() == TicketSource.OPENAPI
                        && context.getSubjectId().equals(ticket.getCreatorCredentialId()));
        if (!visible) {
            throw new TicketNotAccessibleException();
        }
        return ticket;
    }

    /**
     * 时间线（转移+留言按同一倒序游标分页；提单人视角过滤 internal）。
     *
     * @param context 上下文
     * @param ticketNo 单号
     * @return 转移与留言交错列表（按时间归并由调用端合并）
     */
    public Timeline timeline(TicketRequestContext context, String ticketNo, String before, int size) {
        Ticket ticket = detail(context, ticketNo);
        boolean ownerView = ticket.getOwnerSubjectId().equals(context.getSubjectId())
                && !context.isAllTicketRead() && !context.isMachine();
        List<TicketComment> comments = commentRepository.findByTicketBefore(ticket.getId(), before,
                Math.min(100, Math.max(1, size)));
        if (ownerView) {
            List<TicketComment> visible = new ArrayList<>();
            for (TicketComment comment : comments) {
                if (!comment.isInternal()) {
                    visible.add(comment);
                }
            }
            comments = visible;
        }
        List<TicketTransition> transitions = transitionRepository.findByTicketBefore(ticket.getId(), before,
                Math.min(100, Math.max(1, size)));
        Instant next = null;
        for (TicketComment comment : comments) {
            if (comment.getCreatedAt() != null && (next == null || comment.getCreatedAt().isBefore(next))) {
                next = comment.getCreatedAt();
            }
        }
        for (TicketTransition transition : transitions) {
            if (transition.getCreatedAt() != null
                    && (next == null || transition.getCreatedAt().isBefore(next))) {
                next = transition.getCreatedAt();
            }
        }
        return new Timeline(transitions, comments, attachmentRepository.findByTicket(ticket.getId()),
                next == null ? null : next.toString());
    }

    /**
     * 时间线聚合。
     */
    public static class Timeline {

        /**
         * 转移记录
         */
        public final List<TicketTransition> transitions;

        /**
         * 留言（已按角色过滤 internal）
         */
        public final List<TicketComment> comments;

        /**
         * 附件
         */
        public final List<io.github.surezzzzzz.sdk.ticket.server.model.TicketAttachment> attachments;

        /** 下一页游标；没有更早记录时为 null。 */
        public final String nextCursor;

        public Timeline(List<TicketTransition> transitions, List<TicketComment> comments,
                        List<io.github.surezzzzzz.sdk.ticket.server.model.TicketAttachment> attachments) {
            this(transitions, comments, attachments, null);
        }

        public Timeline(List<TicketTransition> transitions, List<TicketComment> comments,
                        List<io.github.surezzzzzz.sdk.ticket.server.model.TicketAttachment> attachments,
                        String nextCursor) {
            this.transitions = transitions;
            this.comments = comments;
            this.attachments = attachments;
            this.nextCursor = nextCursor;
        }
    }

    private String typeDefaultGroupOf(Ticket ticket) {
        return lookupDefaultGroup(ticket);
    }

    private String lookupDefaultGroup(Ticket ticket) {
        return typeRepository.findByCode(ticket.getTypeCode())
                .map(io.github.surezzzzzz.sdk.ticket.server.model.TicketType::getDefaultGroup).orElse(null);
    }

    private int offset(int page, int size) {
        long safeOffset = ((long) TicketRequestParameterParser.safePage(page) - 1L)
                * TicketRequestParameterParser.safeSize(size);
        return safeOffset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) safeOffset;
    }
}
