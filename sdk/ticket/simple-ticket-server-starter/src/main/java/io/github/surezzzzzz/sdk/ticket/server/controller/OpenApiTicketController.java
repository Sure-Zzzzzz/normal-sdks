package io.github.surezzzzzz.sdk.ticket.server.controller;

import io.github.surezzzzzz.sdk.ticket.server.constant.TicketSource;
import io.github.surezzzzzz.sdk.ticket.server.dto.OpenApiCreateTicketRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.PageResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketDetailResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketSummaryResponse;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketCommandService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketQueryService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketResponseMapper;
import io.github.surezzzzzz.sdk.ticket.server.support.TicketRequestParameterParser;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * openapi（AKSK Bearer，/api/ticket/openapi/**——DESIGN §7.3；凭据边界=本凭据创建的单）。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping("${" + "io.github.surezzzzzz.sdk.ticket.server.api-base-path" + ":/api/ticket}/openapi")
@SimpleTicketServerComponent
public class OpenApiTicketController {

    private final TicketPrincipalResolver principalResolver;

    private final TicketCommandService commandService;

    private final TicketQueryService queryService;

    public OpenApiTicketController(TicketPrincipalResolver principalResolver,
                                   TicketCommandService commandService, TicketQueryService queryService) {
        this.principalResolver = principalResolver;
        this.commandService = commandService;
        this.queryService = queryService;
    }

    /**
     * 机器开单（Idempotency-Key 必填）。
     *
     * @param request 请求
     * @param idempotencyKey 幂等键
     * @param body 开单体
     * @return 工单摘要（201；幂等命中 200 返回原单）
     */
    @PostMapping("/tickets")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_CREATE)
    public ResponseEntity<TicketSummaryResponse> create(HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody OpenApiCreateTicketRequest body) {
        TicketRequestContext context = requireMachine(request);
        TicketCommandService.OpenApiCreateResult result = commandService.createOpenApiResult(
                context.getSubjectId(), idempotencyKey,
                body.getTypeCode(), body.getSubject(), body.getDescription(), body.getPriority(),
                body.getOwnerSubjectId(), body.getFormAnswers());
        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TicketResponseMapper.summaryOf(result.getTicket()));
    }

    /**
     * 本凭据的工单列表（消费方轮询）。
     *
     * @param request 请求
     * @param page 页码
     * @param size 页大小
     * @param status 状态过滤
     * @return 分页摘要
     */
    @GetMapping("/tickets")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public PageResponse<TicketSummaryResponse> list(HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> status) {
        TicketRequestContext context = requireMachine(request);
        int safePage = TicketRequestParameterParser.safePage(page);
        int safeSize = TicketRequestParameterParser.safeSize(size);
        List<TicketSummaryResponse> content = new ArrayList<>();
        for (Ticket ticket : queryService.byCredential(context.getSubjectId(),
                TicketRequestParameterParser.statuses(status),
                safePage, safeSize)) {
            content.add(TicketResponseMapper.summaryOf(ticket));
        }
        return PageResponse.<TicketSummaryResponse>builder().content(content).page(safePage).size(safeSize).build();
    }

    /**
     * 查单（限本凭据）。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 详情
     */
    @GetMapping("/tickets/{ticketNo}")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public TicketDetailResponse get(HttpServletRequest request, @PathVariable String ticketNo) {
        TicketRequestContext context = requireMachine(request);
        return TicketResponseMapper.detailOf(requireOwn(context, ticketNo));
    }

    /**
     * 机器追加信息（纯文本落 SYSTEM 留言，限本凭据且 source=OPENAPI）。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param body 追加体
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/append")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_COMMENT)
    public TicketSummaryResponse append(HttpServletRequest request, @PathVariable String ticketNo,
                                        @RequestBody io.github.surezzzzzz.sdk.ticket.server.dto.CommentRequest body) {
        TicketRequestContext context = requireMachine(request);
        Ticket ticket = requireOwn(context, ticketNo);
        commandService.comment(context, ticketNo, body.getBody(), false, expectedVersion(request));
        return TicketResponseMapper.summaryOf(queryService.detail(context, ticketNo));
    }

    /**
     * 自动单办结确认（限本凭据且 RESOLVED 态）。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/close")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_CLOSE)
    public TicketSummaryResponse close(HttpServletRequest request, @PathVariable String ticketNo) {
        TicketRequestContext context = requireMachine(request);
        Ticket ticket = requireOwn(context, ticketNo);
        return TicketResponseMapper.summaryOf(commandService.confirm(context, ticketNo, expectedVersion(request)));
    }

    private Ticket requireOwn(TicketRequestContext context, String ticketNo) {
        Ticket ticket = queryService.detail(context, ticketNo);
        if (ticket.getCreatorCredentialId() == null
                || !ticket.getCreatorCredentialId().equals(context.getSubjectId())) {
            throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketNotAccessibleException();
        }
        return ticket;
    }

    private TicketRequestContext requireMachine(HttpServletRequest request) {
        TicketRequestContext context = principalResolver.resolve(request);
        if (context == null || !context.isMachine()) {
            throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException();
        }
        return context;
    }

    private Long expectedVersion(HttpServletRequest request) {
        return TicketRequestParameterParser.expectedVersion(request == null ? null : request.getHeader("If-Match"));
    }

}
