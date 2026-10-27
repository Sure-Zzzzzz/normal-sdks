package io.github.surezzzzzz.sdk.ticket.server.configuration;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 工单服务配置（前缀见 core 常量；DESIGN §5.4，默认值与 core 常量同源）。
 *
 * <p>凭据与外部地址不在此面（env 注入，机密永不入库）。</p>
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.ticket.server")
public class SimpleTicketServerProperties {

    /**
     * 总开关（false=不装配任何 HTTP 面与扫描任务）。
     */
    private boolean enable = true;

    /**
     * REST 根路径。
     */
    private String apiBasePath = "/api/ticket";

    /**
     * 附件子配置。
     */
    private Attachment attachment = new Attachment();

    /**
     * 通知子配置。
     */
    private Notification notification = new Notification();

    /**
     * SLA 子配置。
     */
    private Sla sla = new Sla();

    /**
     * 业务阈值子配置。
     */
    private Ticket ticket = new Ticket();

    /**
     * 附件配置。
     */
    @Data
    public static class Attachment {

        /**
         * 附件能力开关（false=提交面隐藏附件入口，Noop FileProvider）。
         */
        private boolean enabled = false;

        /**
         * 附件桶名（S3 适配必填）。
         */
        private String bucket;

        /**
         * 单文件大小上限（MB）。
         */
        private int maxSizeMb = 20;

        /**
         * 单工单数量上限。
         */
        private int maxCountPerTicket = 10;

        /**
         * 类型白名单（扩展名小写）。
         */
        private List<String> allowedTypes = new ArrayList<>(Arrays.asList(
                "png", "jpg", "jpeg", "pdf", "zip", "log", "txt"));










    }

    /**
     * 通知配置。
     */
    @Data
    public static class Notification {

        /**
         * 站内信出口开关（false=Noop 日志降级，流转不受影响）。
         */
        private boolean enabled = false;

        /**
         * 投递失败重试次数（超过后落日志告警，不阻塞主流程）。
         */
        private int retryTimes = 3;




    }

    /**
     * SLA 配置。
     */
    @Data
    public static class Sla {

        /**
         * 扫描周期（秒）。
         */
        private long scanFixedRateSeconds = 60;


    }

    /**
     * 业务阈值配置。
     */
    @Data
    public static class Ticket {

        /**
         * 确认超时（小时，全局统一不分类型）。
         */
        private long confirmTimeoutHours = 168;

        /**
         * 标题长度上限。
         */
        private int subjectMaxLength = 120;

        /**
         * 描述长度上限。
         */
        private int descriptionMaxLength = 8000;






    }












}