package io.github.surezzzzzz.sdk.ticket.server.model;

import io.github.surezzzzzz.sdk.ticket.server.constant.ActorRole;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.ticket.server.constant.RequestInfoReason;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketPriority;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketStatus;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketStateException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketNoHelper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * 工单聚合（ticket 表）。领域时间统一 Instant（UTC），展示层转北京墙钟（DESIGN §10）。
 *
 * <p>状态机以富领域方法承载（DESIGN §6.4 动作全枚举）：每个动作方法自校验前置状态，
 * 非法转移抛 {@link TicketStateException}（409）；暂停账语义（DESIGN §9 唯一口径）：
 * {@code 办结到期判定 = now > resolutionDeadline + 暂停累计}，PENDING_INFO 进行中该式恒暂缓。
 * 人员快照（ownerName/assigneeName）在提交/受理时固化，离职不追改。</p>
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    private Long id;

    /**
     * 单号（TK+yyMMdd+6 位日序列）
     */
    private String ticketNo;

    /**
     * 类型编码
     */
    private String typeCode;

    /**
     * 标题（≤120）
     */
    private String subject;

    /**
     * 描述（Markdown，≤8000）
     */
    private String description;

    /**
     * 模板答案 JSON 快照（提交时固化，模板后续变更不重校验）
     */
    private String formAnswers;

    /**
     * 优先级（仅排序与展示，不影响 SLA）
     */
    private TicketPriority priority;

    /**
     * 状态
     */
    private TicketStatus status;

    /**
     * 提单人标识（鉴权锚：DATA owner 维度）
     */
    private String ownerSubjectId;

    /**
     * 提单人姓名快照
     */
    private String ownerName;

    /**
     * 当前处理组编码
     */
    private String assigneeGroup;

    /**
     * 当前处理人标识
     */
    private String assigneeSubjectId;

    /**
     * 处理人姓名快照
     */
    private String assigneeName;

    /**
     * 响应时限（提交时固化）
     */
    private Instant responseDeadline;

    /**
     * 办结时限基线（提交时固化；到期判定加暂停累计）
     */
    private Instant resolutionDeadline;

    /**
     * 确认期限（RESOLVED 时生成，REJECT 清除）
     */
    private Instant confirmDeadline;

    /**
     * 暂停累计毫秒（待补充期间不计办结时限）
     */
    private long pendingAccumulatedMs;

    /**
     * 本次暂停起点（null=未暂停）
     */
    private Instant pauseStartedAt;

    /**
     * 响应超时标记（扫描原子置位）
     */
    private boolean overdueResponse;

    /**
     * 办结超时标记（扫描原子置位）
     */
    private boolean overdueResolution;

    /**
     * 来源（PORTAL/OPENAPI）
     */
    private TicketSource source;

    /**
     * 创建凭据标识（openapi 来源审计定位）
     */
    private String creatorCredentialId;

    /**
     * 幂等键（openapi 提交）
     */
    private String idempotencyKey;

    /**
     * 机器开单请求摘要（仅用于同凭据幂等判定，不保存请求原文）
     */
    private String requestFingerprint;

    /**
     * 关联原单号（重提场景）
     */
    private String relatedTicketNo;

    /**
     * 最近活跃时刻（任何留言/转移/补充刷新，我的工单排序锚）
     */
    private Instant lastActivityAt;

    /**
     * 受理时刻（响应时效实际值，报表用）
     */
    private Instant acceptedAt;

    /**
     * 办结时刻（办结时效实际值，报表用）
     */
    private Instant resolvedAt;

    /**
     * 关闭时刻
     */
    private Instant closedAt;

    /**
     * 创建时刻
     */
    private Instant createdAt;

    /**
     * 乐观锁版本（每次领域变更递增）
     */
    private long version;

    // ==================== 工厂 ====================

    /**
     * 新建工单（SUBMITTED，SLA 时限按类型配置固化）。
     *
     * @param ticketNo        单号（TicketNoHelper 生成）
     * @param typeCode        类型编码
     * @param subject         标题
     * @param description     描述
     * @param formAnswers     模板答案 JSON 快照
     * @param priority        优先级
     * @param ownerSubjectId  提单人标识
     * @param ownerName       提单人姓名快照
     * @param source          来源
     * @param creatorCredentialId 创建凭据标识（PORTAL 为 null）
     * @param idempotencyKey  幂等键（PORTAL 为 null）
     * @param relatedTicketNo 关联原单号（可 null）
     * @param responseMinutes 类型响应时限分钟
     * @param resolutionMinutes 类型办结时限分钟
     * @param now             创建时刻
     * @return 新工单
     */
    public static Ticket newTicket(String ticketNo, String typeCode, String subject, String description,
                                   String formAnswers, TicketPriority priority, String ownerSubjectId,
                                   String ownerName, TicketSource source, String creatorCredentialId,
                                   String idempotencyKey, String relatedTicketNo, long responseMinutes,
                                   long resolutionMinutes, Instant now) {
        if (subject == null || subject.trim().isEmpty()) {
            throw new TicketValidationException("subject", "标题不能为空");
        }
        return Ticket.builder()
                .ticketNo(ticketNo)
                .typeCode(typeCode)
                .subject(subject)
                .description(description)
                .formAnswers(formAnswers)
                .priority(priority == null ? TicketPriority.NORMAL : priority)
                .status(TicketStatus.SUBMITTED)
                .ownerSubjectId(ownerSubjectId)
                .ownerName(ownerName)
                .source(source)
                .creatorCredentialId(creatorCredentialId)
                .idempotencyKey(idempotencyKey)
                .relatedTicketNo(relatedTicketNo)
                .responseDeadline(now.plus(Duration.ofMinutes(responseMinutes)))
                .resolutionDeadline(now.plus(Duration.ofMinutes(resolutionMinutes)))
                .lastActivityAt(now)
                .createdAt(now)
                .version(0L)
                .build();
    }

    // ==================== SLA 判定（唯一口径） ====================

    /**
     * 办结有效期限（基线 + 已结算暂停 + 进行中暂停随 now 同速延伸，暂停期恒不触发）。
     *
     * @param now 判定时刻
     * @return 有效办结期限
     */
    public Instant effectiveResolutionDeadline(Instant now) {
        Instant effective = resolutionDeadline.plus(Duration.ofMillis(pendingAccumulatedMs));
        if (pauseStartedAt != null && now.isAfter(pauseStartedAt)) {
            effective = effective.plus(Duration.between(pauseStartedAt, now));
        }
        return effective;
    }

    /**
     * 是否办结超时（未终态才有意义）。
     *
     * @param now 判定时刻
     * @return 超时与否
     */
    public boolean isResolutionOverdue(Instant now) {
        return now.isAfter(effectiveResolutionDeadline(now));
    }

    /**
     * 是否响应超时（不暂停；仅未受理时有意义）。
     *
     * @param now 判定时刻
     * @return 超时与否
     */
    public boolean isResponseOverdue(Instant now) {
        return acceptedAt == null && now.isAfter(responseDeadline);
    }

    /**
     * 是否确认超时（扫描自动关闭依据）。
     *
     * @param now 判定时刻
     * @return 超时与否
     */
    public boolean isConfirmOverdue(Instant now) {
        return status == TicketStatus.RESOLVED && confirmDeadline != null && now.isAfter(confirmDeadline);
    }

    // ==================== 状态机动作（DESIGN §6.4 全枚举） ====================

    /**
     * 受理（SUBMITTED→PROCESSING；响应 SLA 以此时刻达标判定）。
     *
     * @param groupCode         处理组编码
     * @param assigneeSubjectId 处理人标识
     * @param assigneeName      处理人姓名快照
     * @param now               动作时刻
     */
    public void claim(String groupCode, String assigneeSubjectId, String assigneeName, Instant now) {
        requireStatus(TicketStatus.SUBMITTED, "受理");
        this.assigneeGroup = groupCode;
        this.assigneeSubjectId = assigneeSubjectId;
        this.assigneeName = assigneeName;
        this.acceptedAt = now;
        touch(TicketStatus.PROCESSING, now);
    }

    /**
     * 分派（组长/admin 指定组与人；SUBMITTED 起始同受理语义进 PROCESSING，其余状态保持）。
     *
     * @param groupCode         目标组编码
     * @param assigneeSubjectId 被派人标识
     * @param assigneeName      被派人姓名快照
     * @param now               动作时刻
     */
    public void assign(String groupCode, String assigneeSubjectId, String assigneeName, Instant now) {
        requireStatusIn("分派", TicketStatus.SUBMITTED, TicketStatus.PROCESSING, TicketStatus.PENDING_INFO);
        this.assigneeGroup = groupCode;
        this.assigneeSubjectId = assigneeSubjectId;
        this.assigneeName = assigneeName;
        if (this.acceptedAt == null) {
            this.acceptedAt = now;
        }
        TicketStatus next = this.status == TicketStatus.SUBMITTED ? TicketStatus.PROCESSING : this.status;
        touch(next, now);
    }

    /**
     * 转派（换组/换人；状态与暂停账随单保持）。
     *
     * @param groupCode         目标组编码
     * @param assigneeSubjectId 新处理人标识
     * @param assigneeName      新处理人姓名快照
     * @param now               动作时刻
     */
    public void transfer(String groupCode, String assigneeSubjectId, String assigneeName, Instant now) {
        requireStatusIn("转派", TicketStatus.PROCESSING, TicketStatus.PENDING_INFO);
        this.assigneeGroup = groupCode;
        this.assigneeSubjectId = assigneeSubjectId;
        this.assigneeName = assigneeName;
        touch(this.status, now);
    }

    /**
     * 待补充（PROCESSING→PENDING_INFO；办结计时暂停）。
     *
     * @param reason 原因码
     * @param now    动作时刻
     */
    public void requestInfo(RequestInfoReason reason, Instant now) {
        requireStatus(TicketStatus.PROCESSING, "待补充");
        if (reason == null) {
            throw new TicketValidationException("reason", "待补充原因不能为空");
        }
        this.pauseStartedAt = now;
        touch(TicketStatus.PENDING_INFO, now);
    }

    /**
     * 补充（PENDING_INFO→PROCESSING；结算暂停账并恢复计时）。
     *
     * @param now 动作时刻
     */
    public void supply(Instant now) {
        requireStatus(TicketStatus.PENDING_INFO, "补充");
        settlePause(now);
        touch(TicketStatus.PROCESSING, now);
    }

    /**
     * 办结（PROCESSING/PENDING_INFO→RESOLVED；生成确认期限；暂停中先结账）。
     *
     * @param confirmTimeoutHours 确认超时小时数（全局配置）
     * @param now                 动作时刻
     */
    public void resolve(long confirmTimeoutHours, Instant now) {
        requireStatusIn("办结", TicketStatus.PROCESSING, TicketStatus.PENDING_INFO);
        settlePause(now);
        this.resolvedAt = now;
        this.confirmDeadline = now.plus(Duration.ofHours(confirmTimeoutHours));
        touch(TicketStatus.RESOLVED, now);
    }

    /**
     * 确认关闭（RESOLVED→CLOSED；可评价）。
     *
     * @param now 动作时刻
     */
    public void confirm(Instant now) {
        requireStatus(TicketStatus.RESOLVED, "确认关闭");
        this.closedAt = now;
        touch(TicketStatus.CLOSED, now);
    }

    /**
     * 不认可重开（RESOLVED→PROCESSING；保留处理人；清除确认期限）。
     *
     * @param now 动作时刻
     */
    public void reject(Instant now) {
        requireStatus(TicketStatus.RESOLVED, "重开");
        this.confirmDeadline = null;
        this.resolvedAt = null;
        touch(TicketStatus.PROCESSING, now);
    }

    /**
     * 撤销（SUBMITTED/PROCESSING/PENDING_INFO→WITHDRAWN 终态；暂停中先结账）。
     *
     * @param now 动作时刻
     */
    public void withdraw(Instant now) {
        requireStatusIn("撤销", TicketStatus.SUBMITTED, TicketStatus.PROCESSING, TicketStatus.PENDING_INFO);
        settlePause(now);
        touch(TicketStatus.WITHDRAWN, now);
    }

    /**
     * 确认超时自动关闭（系统唯一自动转移；不邀请评价）。
     *
     * @param now 动作时刻
     */
    public void confirmTimeout(Instant now) {
        requireStatus(TicketStatus.RESOLVED, "确认超时关闭");
        this.closedAt = now;
        touch(TicketStatus.CLOSED, now);
    }

    /**
     * 刷新活跃时刻（留言等不改状态的动作调用）。
     *
     * @param now 动作时刻
     */
    public void touchActivity(Instant now) {
        this.lastActivityAt = now;
        this.version++;
    }

    /**
     * 是否终态。
     *
     * @return 终态与否
     */
    public boolean isTerminal() {
        return status == TicketStatus.CLOSED || status == TicketStatus.WITHDRAWN;
    }

    // ==================== 内部 ====================

    private void settlePause(Instant now) {
        if (pauseStartedAt != null) {
            if (now.isAfter(pauseStartedAt)) {
                this.pendingAccumulatedMs += Duration.between(pauseStartedAt, now).toMillis();
            }
            this.pauseStartedAt = null;
        }
    }

    private void requireStatus(TicketStatus expected, String action) {
        if (this.status != expected) {
            throw new TicketStateException(action, this.status, expected);
        }
    }

    private void requireStatusIn(String action, TicketStatus... expected) {
        for (TicketStatus status : expected) {
            if (this.status == status) {
                return;
            }
        }
        throw new TicketStateException(action, this.status, expected);
    }

    private void touch(TicketStatus next, Instant now) {
        this.status = next;
        this.lastActivityAt = now;
        this.version++;
    }
}
