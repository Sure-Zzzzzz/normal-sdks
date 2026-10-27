package io.github.surezzzzzz.sdk.ticket.server.controller;

import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.dto.AssignRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.CommentRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.CommentResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.PageResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.RequestInfoRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketDetailResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketSummaryResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TimelineResponse;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketCommandService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketQueryService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketResponseMapper;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketRequestParameterParser;
import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理 API（人员令牌+处理组，/api/ticket/manage/**——DESIGN §7.2，类型化契约）。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping("${" + "io.github.surezzzzzz.sdk.ticket.server.api-base-path" + ":/api/ticket}/manage")
@SimpleTicketServerComponent
public class ManageTicketController {

    private final TicketPrincipalResolver principalResolver;

    private final TicketCommandService commandService;

    private final TicketQueryService queryService;

    public ManageTicketController(TicketPrincipalResolver principalResolver,
                                  TicketCommandService commandService, TicketQueryService queryService) {
        this.principalResolver = principalResolver;
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /**
     * 组队列。
     *
     * @param request 请求
     * @param page 页码
     * @param size 页大小
     * @param status 状态过滤
     * @return 分页摘要
     */
    @GetMapping("/queue")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public PageResponse<TicketSummaryResponse> queue(HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String q) {
        TicketRequestContext context = requireContext(request);
        int safePage = TicketRequestParameterParser.safePage(page);
        int safeSize = TicketRequestParameterParser.safeSize(size);
        List<TicketSummaryResponse> content = new ArrayList<>();
        for (Ticket ticket : queryService.queue(context.getSubjectId(),
                TicketRequestParameterParser.statuses(status), TicketRequestParameterParser.priority(priority),
                overdue, q, safePage, safeSize)) {
            content.add(TicketResponseMapper.summaryOf(ticket));
        }
        return PageResponse.<TicketSummaryResponse>builder().content(content).page(safePage).size(safeSize).build();
    }

    /** 兼容嵌入式调用的旧签名；HTTP 请求走带筛选项的重载。 */
    public PageResponse<TicketSummaryResponse> queue(HttpServletRequest request, int page, int size,
                                                     List<String> status) {
        return queue(request, page, size, status, null, null, null);
    }

    /**
     * 全部工单分页（admin 面：全量 DATA；admin 位由桥从授权上下文解析）。
     *
     * @param request 请求
     * @param page 页码
     * @param size 页大小
     * @param status 状态过滤
     * @return 分页摘要
     */
    @GetMapping("/all-tickets")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public PageResponse<TicketSummaryResponse> allTickets(HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> status) {
        TicketRequestContext context = requireContext(request);
        if (!context.isAllTicketRead()) {
            throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException();
        }
        int safePage = TicketRequestParameterParser.safePage(page);
        int safeSize = TicketRequestParameterParser.safeSize(size);
        List<TicketSummaryResponse> content = new ArrayList<>();
        for (Ticket ticket : queryService.allTickets(TicketRequestParameterParser.statuses(status),
                safePage, safeSize)) {
            content.add(TicketResponseMapper.summaryOf(ticket));
        }
        return PageResponse.<TicketSummaryResponse>builder().content(content).page(safePage).size(safeSize).build();
    }

    /**
     * 管理侧工单详情。不能复用 portal 详情，否则处理组成员查看他人工单会被本人 DATA 掩蔽。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 工单详情
     */
    @GetMapping("/tickets/{ticketNo}")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public TicketDetailResponse detail(HttpServletRequest request, @PathVariable String ticketNo) {
        return TicketResponseMapper.detailOf(queryService.detail(requireContext(request), ticketNo));
    }

    /**
     * 管理侧时间线，处理组成员可见内部留言；可见性仍由组内或全量 DATA 判定。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param before 游标
     * @param size 页大小
     * @return 时间线
     */
    @GetMapping("/tickets/{ticketNo}/timeline")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public TimelineResponse timeline(HttpServletRequest request, @PathVariable String ticketNo,
                                     @RequestParam(required = false) String before,
                                     @RequestParam(defaultValue = "50") int size) {
        TicketRequestContext context = requireContext(request);
        TicketRequestParameterParser.validateBefore(before);
        TicketQueryService.Timeline timeline = queryService.timeline(context, ticketNo, before, size);
        return TimelineResponse.builder()
                .transitions(timeline.transitions)
                .comments(timeline.comments)
                .attachments(timeline.attachments)
                .nextCursor(timeline.nextCursor)
                .build();
    }

    /**
     * 受理。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/claim")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_CLAIM)
    public TicketSummaryResponse claim(HttpServletRequest request, @PathVariable String ticketNo) {
        return TicketResponseMapper.summaryOf(commandService.claim(requireContext(request), ticketNo,
                expectedVersion(request)));
    }

    /**
     * 分派。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param body 分派体
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/assign")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_ASSIGN)
    public TicketSummaryResponse assign(HttpServletRequest request, @PathVariable String ticketNo,
                                        @RequestBody AssignRequest body) {
        return TicketResponseMapper.summaryOf(commandService.assign(requireContext(request), ticketNo,
                body.getGroupCode(), body.getAssigneeSubjectId(), expectedVersion(request)));
    }

    /**
     * 转派。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param body 转派体
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/transfer")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_TRANSFER)
    public TicketSummaryResponse transfer(HttpServletRequest request, @PathVariable String ticketNo,
                                          @RequestBody AssignRequest body) {
        return TicketResponseMapper.summaryOf(commandService.transfer(requireContext(request), ticketNo,
                body.getGroupCode(), body.getAssigneeSubjectId(), expectedVersion(request)));
    }

    /**
     * 待补充。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param body 原因体
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/request-info")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_RESOLVE)
    public TicketSummaryResponse requestInfo(HttpServletRequest request, @PathVariable String ticketNo,
                                             @RequestBody RequestInfoRequest body) {
        return TicketResponseMapper.summaryOf(commandService.requestInfo(requireContext(request), ticketNo,
                TicketRequestParameterParser.reason(body.getReason()), expectedVersion(request)));
    }

    /**
     * 办结。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/resolve")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_RESOLVE)
    public TicketSummaryResponse resolve(HttpServletRequest request, @PathVariable String ticketNo) {
        return TicketResponseMapper.summaryOf(commandService.resolve(requireContext(request), ticketNo,
                expectedVersion(request)));
    }

    /**
     * 处理侧留言（internal 可 true）。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param body 留言体
     * @return 留言
     */
    @PostMapping("/tickets/{ticketNo}/comments")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_COMMENT)
    public CommentResponse comment(HttpServletRequest request, @PathVariable String ticketNo,
                                   @RequestBody CommentRequest body) {
        return TicketResponseMapper.commentOf(commandService.comment(requireContext(request), ticketNo,
                body.getBody(), Boolean.TRUE.equals(body.getInternal()), expectedVersion(request)));
    }

    private TicketRequestContext requireContext(HttpServletRequest request) {
        TicketRequestContext context = principalResolver.resolve(request);
        if (context == null || context.isMachine()) {
            throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException();
        }
        return context;
    }

    private Long expectedVersion(HttpServletRequest request) {
        return TicketRequestParameterParser.expectedVersion(request == null ? null : request.getHeader("If-Match"));
    }

}
