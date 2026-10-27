package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.service.SlaScanService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketSlaScanTask;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * SLA 扫描周期必须按秒配置并换算成定时器毫秒值。
 */
@Slf4j
class TicketSlaScheduleTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test
    void customSecondsConfigureActualFixedRate() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.ticket.server.sla.scan-fixed-rate-seconds=7")
                .run(context -> assertEquals(7000L, fixedRateOf(context.getBean(
                        ScheduledAnnotationBeanPostProcessor.class))));
    }

    @Test
    void defaultFixedRateRemainsSixtySeconds() {
        contextRunner.run(context -> assertEquals(60000L, fixedRateOf(context.getBean(
                ScheduledAnnotationBeanPostProcessor.class))));
    }

    private static long fixedRateOf(ScheduledAnnotationBeanPostProcessor processor) {
        assertEquals(1, processor.getScheduledTasks().size());
        ScheduledTask scheduled = processor.getScheduledTasks().iterator().next();
        FixedRateTask task = (FixedRateTask) scheduled.getTask();
        log.debug("核对 SLA 扫描调度：intervalMs={}", task.getInterval());
        return task.getInterval();
    }

    @Configuration
    @EnableScheduling
    static class SchedulingConfiguration {

        @Bean
        TicketSlaScanTask ticketSlaScanTask() {
            return new TicketSlaScanTask(mock(SlaScanService.class));
        }
    }
}
