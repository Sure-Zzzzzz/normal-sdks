package io.github.surezzzzzz.sdk.ticket.server.service;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * SLA 扫描定时任务。
 *
 * @author surezzzzzz
 */
@SimpleTicketServerComponent
public class TicketSlaScanTask {

    private static final Logger log = LoggerFactory.getLogger(TicketSlaScanTask.class);

    private final SlaScanService scanService;

    public TicketSlaScanTask(SlaScanService scanService) {
        this.scanService = scanService;
    }

    /**
     * 周期扫描（异常吞掉记日志，下一轮继续；扫描幂等可重入）。
     */
    @Scheduled(fixedRateString = "#{${io.github.surezzzzzz.sdk.ticket.server.sla.scan-fixed-rate-seconds:60} * 1000}")
    public void scan() {
        try {
            scanService.scanOnce();
        } catch (Exception e) {
            log.warn("SLA 扫描轮失败（下一轮继续，异常类型={}）", e.getClass().getSimpleName());
        }
    }
}
