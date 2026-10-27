package io.github.surezzzzzz.sdk.ticket.server.service;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.configuration.SimpleTicketServerProperties;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketGroup;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketGroupRepository;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SLA 扫描（原子置位+提醒不自动改状态；确认超时是唯一系统转移——DESIGN §9）。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
public class SlaScanService {

    private static final Logger log = LoggerFactory.getLogger(SlaScanService.class);

    private static final int SCAN_BATCH = 200;

    private final TicketRepository ticketRepository;

    private final TicketGroupRepository groupRepository;

    private final TicketCommandService commandService;

    private final NotificationDispatcher notificationDispatcher;

    private final SimpleTicketServerProperties properties;

    public SlaScanService(TicketRepository ticketRepository, TicketGroupRepository groupRepository,
                          TicketCommandService commandService, NotificationDispatcher notificationDispatcher,
                          SimpleTicketServerProperties properties) {
        this.ticketRepository = ticketRepository;
        this.groupRepository = groupRepository;
        this.commandService = commandService;
        this.notificationDispatcher = notificationDispatcher;
        this.properties = properties;
    }

    /**
     * 扫描一轮（定时任务入口；幂等可重入）。
     *
     * @return 命中处理数
     */
    public int scanOnce() {
        Instant now = Instant.now();
        List<Ticket> candidates = ticketRepository.findScanCandidates(now, SCAN_BATCH);
        int handled = 0;
        for (Ticket ticket : candidates) {
            if (ticket.isConfirmOverdue(now)) {
                if (commandService.confirmTimeout(ticket)) {
                    handled++;
                }
                continue;
            }
            if (ticket.isResponseOverdue(now) && !ticket.isOverdueResponse()) {
                ticket.setOverdueResponse(true);
                ticket.touchActivity(now);
                int updated = ticketRepository.update(ticket);
                if (updated > 0) {
                    notifyGroupOverdue(ticket, "响应超时");
                    handled++;
                }
            } else if (ticket.isResolutionOverdue(now) && !ticket.isOverdueResolution()) {
                ticket.setOverdueResolution(true);
                ticket.touchActivity(now);
                int updated = ticketRepository.update(ticket);
                if (updated > 0) {
                    notifyGroupOverdue(ticket, "办结超时");
                    handled++;
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("SLA 扫描：candidates={}, handled={}", candidates.size(), handled);
        }
        return handled;
    }

    /**
     * 扫描周期（秒，装配定时任务用）。
     *
     * @return 周期
     */
    public long fixedRateSeconds() {
        return properties.getSla().getScanFixedRateSeconds();
    }

    private void notifyGroupOverdue(Ticket ticket, String what) {
        if (ticket.getAssigneeGroup() == null) {
            return;
        }
        Optional<TicketGroup> group = groupRepository.findByCode(ticket.getAssigneeGroup());
        if (group.isPresent()) {
            notificationDispatcher.dispatch(group.get().getMemberSubjectIds(),
                    "工单" + what + "：" + ticket.getTicketNo(), ticket.getSubject());
        }
    }
}
