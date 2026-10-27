package io.github.surezzzzzz.sdk.ticket.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketGroupRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketGroupResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketReportResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketTypeRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketTypeResponse;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketGroup;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketType;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.service.TypeGroupAdminService;
import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketResponseMapper;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;

/**
 * 管理 API 之 admin 面（类型/处理组 CRUD+统计报表——DESIGN §7.2；admin 码由资源层把守）。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping("${" + "io.github.surezzzzzz.sdk.ticket.server.api-base-path" + ":/api/ticket}/manage")
@SimpleTicketServerComponent
public class TicketAdminController {

    private final TicketPrincipalResolver principalResolver;

    private final TypeGroupAdminService adminService;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TicketAdminController(TicketPrincipalResolver principalResolver,
                                 TypeGroupAdminService adminService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.principalResolver = principalResolver;
        this.adminService = adminService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 类型列表。
     *
     * @param request 请求
     * @return 类型列表
     */
    @GetMapping("/types")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_TYPE_ADMIN)
    public List<TicketTypeResponse> types(HttpServletRequest request) {
        requirePermission(request, SimpleTicketServerConstant.PERMISSION_TYPE_ADMIN);
        List<TicketTypeResponse> result = new ArrayList<>();
        for (TicketType type : adminService.types()) {
            result.add(TicketResponseMapper.typeOf(type));
        }
        return result;
    }

    /**
     * 新建/更新类型。
     *
     * @param request 请求
     * @param body 类型体
     * @return 持久化后类型
     */
    @PostMapping("/types")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_TYPE_ADMIN)
    public TicketTypeResponse saveType(HttpServletRequest request, @RequestBody TicketTypeRequest body) {
        requirePermission(request, SimpleTicketServerConstant.PERMISSION_TYPE_ADMIN);
        return TicketResponseMapper.typeOf(adminService.saveType(TicketType.builder()
                .id(body.getId())
                .code(body.getCode())
                .name(body.getName())
                .formTemplate(body.getFormTemplate())
                .defaultGroup(body.getDefaultGroup())
                .slaResponseMinutes(body.getSlaResponseMinutes())
                .slaResolutionMinutes(body.getSlaResolutionMinutes())
                .enabled(body.isEnabled())
                .build()));
    }

    /**
     * 处理组列表。
     *
     * @param request 请求
     * @return 组列表
     */
    @GetMapping("/groups")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN)
    public List<TicketGroupResponse> groups(HttpServletRequest request) {
        requirePermission(request, SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN);
        List<TicketGroupResponse> result = new ArrayList<>();
        for (TicketGroup group : adminService.groups()) {
            result.add(TicketResponseMapper.groupOf(group));
        }
        return result;
    }

    /**
     * 新建/更新处理组。
     *
     * @param request 请求
     * @param body 组体
     * @return 持久化后组
     */
    @PostMapping("/groups")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN)
    public TicketGroupResponse saveGroup(HttpServletRequest request, @RequestBody TicketGroupRequest body) {
        requirePermission(request, SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN);
        return TicketResponseMapper.groupOf(adminService.saveGroup(TicketGroup.builder()
                .id(body.getId())
                .code(body.getCode())
                .name(body.getName())
                .memberSubjectIds(body.getMemberSubjectIds())
                .leaderSubjectId(body.getLeaderSubjectId())
                .enabled(body.isEnabled())
                .build()));
    }

    /**
     * 删除组（在途单拒删，实际停用——DESIGN §6.2）。
     *
     * @param request 请求
     * @param code 组编码
     */
    @DeleteMapping("/groups/{code}")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN)
    public void deleteGroup(HttpServletRequest request, @PathVariable String code) {
        requirePermission(request, SimpleTicketServerConstant.PERMISSION_GROUP_ADMIN);
        adminService.deleteGroup(code);
    }

    /**
     * 统计报表（状态分布+满意度；范围过滤后续按需扩维度——DESIGN §7.2 指标面首版聚焦两组）。
     *
     * @param request 请求
     * @return 报表
     */
    @GetMapping("/report")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_REPORT)
    public TicketReportResponse report(HttpServletRequest request,
                                       @org.springframework.web.bind.annotation.RequestParam(required = false)
                                       String from,
                                       @org.springframework.web.bind.annotation.RequestParam(required = false)
                                       String to) {
        requirePermission(request, SimpleTicketServerConstant.PERMISSION_REPORT);
        ReportRange range = reportRange(from, to);
        MapSqlParameterSource params = range.parameters();
        String where = range.whereClause();
        List<TicketReportResponse.CountItem> byStatus = countItems(
                jdbcTemplate.queryForList("SELECT status AS item_key, COUNT(*) AS item_count FROM ticket t"
                        + where + " GROUP BY status", params));
        List<TicketReportResponse.CountItem> byType = countItems(
                jdbcTemplate.queryForList("SELECT type_code AS item_key, COUNT(*) AS item_count FROM ticket t"
                        + where + " GROUP BY type_code", params));
        List<TicketReportResponse.CountItem> byGroup = countItems(
                jdbcTemplate.queryForList("SELECT COALESCE(assignee_group, '') AS item_key, COUNT(*) AS item_count"
                        + " FROM ticket t" + where + " GROUP BY assignee_group", params));
        Double avgStar = jdbcTemplate.queryForObject(
                "SELECT AVG(e.star) FROM ticket_evaluation e JOIN ticket t ON t.id = e.ticket_id" + where,
                params, Double.class);
        Long evaluated = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_evaluation e JOIN ticket t ON t.id = e.ticket_id" + where,
                params, Long.class);
        Long resolved = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket t" + where + " AND t.status IN ('CLOSED')",
                params, Long.class);
        Long responseTotal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket t" + where + " AND t.accepted_at IS NOT NULL",
                params, Long.class);
        Long responseOnTime = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket t" + where
                        + " AND t.accepted_at IS NOT NULL AND t.accepted_at <= t.response_deadline",
                params, Long.class);
        Long resolutionTotal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket t" + where + " AND t.resolved_at IS NOT NULL",
                params, Long.class);
        Long resolutionOnTime = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket t" + where
                        + " AND t.resolved_at IS NOT NULL"
                        + " AND t.resolved_at <= TIMESTAMPADD(MICROSECOND,"
                        + " t.pending_accumulated_ms, t.resolution_deadline)",
                params, Long.class);
        Long overdue = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket t" + where
                        + " AND (t.overdue_response = 1 OR t.overdue_resolution = 1)",
                params, Long.class);
        Double averageResponseMinutes = jdbcTemplate.queryForObject(
                "SELECT AVG(TIMESTAMPDIFF(MICROSECOND, t.created_at, t.accepted_at) / 60000000.0)"
                        + " FROM ticket t" + where + " AND t.accepted_at IS NOT NULL",
                params, Double.class);
        Double averageResolutionMinutes = jdbcTemplate.queryForObject(
                "SELECT AVG(TIMESTAMPDIFF(MICROSECOND, t.created_at, t.resolved_at) / 60000000.0)"
                        + " FROM ticket t" + where + " AND t.resolved_at IS NOT NULL",
                params, Double.class);
        long evaluatedCount = evaluated == null ? 0 : evaluated;
        long resolvedCount = resolved == null ? 0 : resolved;
        double participation = resolvedCount == 0 ? 0.0 : (double) evaluatedCount / resolvedCount;
        long responseDenominator = responseTotal == null ? 0 : responseTotal;
        long resolutionDenominator = resolutionTotal == null ? 0 : resolutionTotal;
        double responseSlaRate = responseDenominator == 0 ? 0.0
                : (double) (responseOnTime == null ? 0 : responseOnTime) / responseDenominator;
        double resolutionSlaRate = resolutionDenominator == 0 ? 0.0
                : (double) (resolutionOnTime == null ? 0 : resolutionOnTime) / resolutionDenominator;
        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket t" + where, params, Long.class);
        double overdueRate = totalCount == null || totalCount == 0 ? 0.0
                : (double) (overdue == null ? 0 : overdue) / totalCount;
        return TicketReportResponse.builder()
                .from(range.from)
                .to(range.to)
                .byStatus(byStatus)
                .byType(byType)
                .byGroup(byGroup)
                .responseSlaRate(responseSlaRate)
                .resolutionSlaRate(resolutionSlaRate)
                .overdueRate(overdueRate)
                .averageResponseMinutes(averageResponseMinutes == null ? 0.0 : averageResponseMinutes)
                .averageResolutionMinutes(averageResolutionMinutes == null ? 0.0 : averageResolutionMinutes)
                .satisfaction(TicketReportResponse.Satisfaction.builder()
                        .averageStar(avgStar == null ? 0.0 : avgStar)
                        .evaluated(evaluatedCount)
                        .resolved(resolvedCount)
                        .participationRate(participation)
                        .build())
                .build();
    }

    private List<TicketReportResponse.CountItem> countItems(List<Map<String, Object>> rows) {
        List<TicketReportResponse.CountItem> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get("item_key");
            Object count = row.get("item_count");
            result.add(TicketReportResponse.CountItem.builder()
                    .key(key == null ? null : String.valueOf(key))
                    .count(count == null ? 0L : ((Number) count).longValue())
                    .build());
        }
        return result;
    }

    private ReportRange reportRange(String from, String to) {
        if (from == null && to == null) {
            return new ReportRange(null, null, " WHERE 1 = 1", new MapSqlParameterSource());
        }
        try {
            LocalDate start = from == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(91)
                    : LocalDate.parse(from);
            LocalDate end = to == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : LocalDate.parse(to);
            if (end.isBefore(start) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 92) {
                throw new TicketValidationException("from/to", "报表范围必须为 0-92 天");
            }
            Instant startAt = start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
            Instant endAt = end.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
            return new ReportRange(from == null ? start.toString() : from, to == null ? end.toString() : to,
                    " WHERE 1 = 1 AND t.created_at >= :reportFrom AND t.created_at < :reportTo",
                    new MapSqlParameterSource().addValue("reportFrom", java.sql.Timestamp.from(startAt))
                            .addValue("reportTo", java.sql.Timestamp.from(endAt)));
        } catch (java.time.format.DateTimeParseException e) {
            throw new TicketValidationException("from/to", "必须为 yyyy-MM-dd");
        }
    }

    private static final class ReportRange {

        private final String from;
        private final String to;
        private final String where;
        private final MapSqlParameterSource params;

        private ReportRange(String from, String to, String where, MapSqlParameterSource params) {
            this.from = from;
            this.to = to;
            this.where = where;
            this.params = params;
        }

        private String whereClause() {
            return where;
        }

        private MapSqlParameterSource parameters() {
            return params;
        }
    }

    private void requirePermission(HttpServletRequest request, String permission) {
        TicketRequestContext context = principalResolver.resolve(request);
        if (context == null || context.isMachine() || !context.hasApiPermission(permission)) {
            throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException();
        }
    }
}
