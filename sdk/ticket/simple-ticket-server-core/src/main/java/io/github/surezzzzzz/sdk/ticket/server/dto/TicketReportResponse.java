package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理面报表响应。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReportResponse {

    /** 报表起始日期（含）。 */
    private String from;

    /** 报表结束日期（含）。 */
    private String to;

    /** 按状态统计。 */
    @Builder.Default
    private List<CountItem> byStatus = new ArrayList<>();

    /** 按类型统计。 */
    @Builder.Default
    private List<CountItem> byType = new ArrayList<>();

    /** 按处理组统计。 */
    @Builder.Default
    private List<CountItem> byGroup = new ArrayList<>();

    /** 响应达标率。 */
    private double responseSlaRate;

    /** 办结达标率。 */
    private double resolutionSlaRate;

    /** 超时率。 */
    private double overdueRate;

    /** 平均响应耗时（分钟）。 */
    private double averageResponseMinutes;

    /** 平均办结耗时（分钟）。 */
    private double averageResolutionMinutes;

    /** 满意度。 */
    private Satisfaction satisfaction;

    /** 计数项。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountItem {

        /** 维度值。 */
        private String key;

        /** 数量。 */
        private long count;
    }

    /** 满意度统计。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Satisfaction {

        /** 平均星级。 */
        private double averageStar;

        /** 评价数量。 */
        private long evaluated;

        /** 已办结数量。 */
        private long resolved;

        /** 参评率。 */
        private double participationRate;
    }
}
