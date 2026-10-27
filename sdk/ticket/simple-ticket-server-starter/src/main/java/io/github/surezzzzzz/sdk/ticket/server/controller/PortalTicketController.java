package io.github.surezzzzzz.sdk.ticket.server.controller;

import io.github.surezzzzzz.sdk.ticket.server.dto.CommentRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.CommentResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.CreateTicketRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.EvaluateRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.EvaluationResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.PageResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.SupplyRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketDetailResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketSummaryResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TimelineResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketTypeResponse;
import io.github.surezzzzzz.sdk.ticket.server.model.Ticket;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketCommandService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketQueryService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketTypeRepository;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面 API（人员令牌，/api/ticket/portal/**——DESIGN §7.1，类型化契约）。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping("${" + "io.github.surezzzzzz.sdk.ticket.server.api-base-path" + ":/api/ticket}/portal")
@SimpleTicketServerComponent
public class PortalTicketController {

    private final TicketPrincipalResolver principalResolver;

    private final TicketCommandService commandService;

    private final TicketQueryService queryService;

    private final TicketTypeRepository typeRepository;

    public PortalTicketController(TicketPrincipalResolver principalResolver,
                                  TicketCommandService commandService, TicketQueryService queryService,
                                  TicketTypeRepository typeRepository) {
        this.principalResolver = principalResolver;
        this.commandService = commandService;
        this.queryService = queryService;
        this.typeRepository = typeRepository;
    }

    /**
     * 启用类型+模板（提交表单动态渲染用；提单人面只读）。
     *
     * @param request 请求
     * @return 启用类型列表
     */
    @GetMapping("/types")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public List<TicketTypeResponse> types(HttpServletRequest request) {
        requireContext(request);
        List<TicketTypeResponse> enabled = new ArrayList<>();
        for (io.github.surezzzzzz.sdk.ticket.server.model.TicketType type : typeRepository.findAll()) {
            if (type.isEnabled()) {
                enabled.add(TicketResponseMapper.typeOf(type));
            }
        }
        return enabled;
    }

    /**
     * 我的工单分页。
     *
     * @param request 请求
     * @param page 页码
     * @param size 页大小
     * @param status 状态（可重复）
     * @return 分页摘要
     */
    @GetMapping("/my-tickets")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public PageResponse<TicketSummaryResponse> myTickets(HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q) {
        TicketRequestContext context = requireContext(request);
        java.time.Instant[] range = TicketRequestParameterParser.dateRange(from, to);
        int safePage = TicketRequestParameterParser.safePage(page);
        int safeSize = TicketRequestParameterParser.safeSize(size);
        return pageOf(queryService.myTickets(context.getSubjectId(),
                TicketRequestParameterParser.statuses(status), typeCode, range[0], range[1], q,
                safePage, safeSize), safePage, safeSize);
    }

    /** 兼容嵌入式调用的旧签名；HTTP 请求走带筛选项的重载。 */
    public PageResponse<TicketSummaryResponse> myTickets(HttpServletRequest request, int page, int size,
                                                         List<String> status) {
        return myTickets(request, page, size, status, null, null, null, null);
    }

    /**
     * 提交工单。
     *
     * @param request 请求
     * @param body 提交体
     * @param idempotencyKey 幂等键（同主体同键重放返回原单）
     * @return 工单摘要（201；幂等重放 200）
     */
    @PostMapping("/tickets")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_CREATE)
    public ResponseEntity<TicketSummaryResponse> create(HttpServletRequest request,
                                                        @RequestHeader(value = "Idempotency-Key", required = false)
                                                        String idempotencyKey,
                                                        @RequestBody CreateTicketRequest body) {
        TicketRequestContext context = requireContext(request);
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException(
                    "idempotencyKey", "为必填项");
        }
        TicketCommandService.PortalCreateResult result = commandService.createPortalResult(context, idempotencyKey,
                body.getTypeCode(), body.getSubject(),
                body.getDescription(), body.getPriority(), body.getFormAnswers(),
                body.getRelatedTicketNo(), attachmentMaps(body));
        return ResponseEntity.status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(TicketResponseMapper.summaryOf(result.getTicket()));
    }

    /**
     * 工单详情。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 详情
     */
    @GetMapping("/tickets/{ticketNo}")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public TicketDetailResponse detail(HttpServletRequest request, @PathVariable String ticketNo) {
        return TicketResponseMapper.detailOf(queryService.detail(requireContext(request), ticketNo));
    }

    /**
     * 时间线。
     *
     * @param request 请求
     * @param ticketNo 单号
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
     * 留言。
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
                body.getBody(), false, expectedVersion(request)));
    }

    /**
     * 补充。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param body 补充体
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/supply")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_COMMENT)
    public TicketSummaryResponse supply(HttpServletRequest request, @PathVariable String ticketNo,
                                        @RequestBody SupplyRequest body) {
        return TicketResponseMapper.summaryOf(
                commandService.supply(requireContext(request), ticketNo, body.getBody(), expectedVersion(request)));
    }

    /**
     * 确认关闭。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/confirm")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_CLOSE)
    public TicketSummaryResponse confirm(HttpServletRequest request, @PathVariable String ticketNo) {
        return TicketResponseMapper.summaryOf(commandService.confirm(requireContext(request), ticketNo,
                expectedVersion(request)));
    }

    /**
     * 不认可重开。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/reject")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_CLOSE)
    public TicketSummaryResponse reject(HttpServletRequest request, @PathVariable String ticketNo) {
        return TicketResponseMapper.summaryOf(commandService.reject(requireContext(request), ticketNo,
                expectedVersion(request)));
    }

    /**
     * 撤销。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @return 工单摘要
     */
    @PostMapping("/tickets/{ticketNo}/withdraw")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_WITHDRAW)
    public TicketSummaryResponse withdraw(HttpServletRequest request, @PathVariable String ticketNo) {
        return TicketResponseMapper.summaryOf(commandService.withdraw(requireContext(request), ticketNo,
                expectedVersion(request)));
    }

    /**
     * 评价。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param body 评价体
     * @return 评价结果
     */
    @PostMapping("/tickets/{ticketNo}/evaluation")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_EVALUATE)
    public EvaluationResponse evaluate(HttpServletRequest request, @PathVariable String ticketNo,
                                       @RequestBody EvaluateRequest body) {
        commandService.evaluate(requireContext(request), ticketNo, body.getStar(), body.getComment(),
                expectedVersion(request));
        return EvaluationResponse.builder().evaluated(true).star(body.getStar()).build();
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

    private PageResponse<TicketSummaryResponse> pageOf(List<Ticket> tickets, int page, int size) {
        List<TicketSummaryResponse> content = new ArrayList<>();
        for (Ticket ticket : tickets) {
            content.add(TicketResponseMapper.summaryOf(ticket));
        }
        return PageResponse.<TicketSummaryResponse>builder().content(content).page(page).size(size).build();
    }

    private List<Map<String, Object>> attachmentMaps(CreateTicketRequest body) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (body.getAttachments() != null) {
            for (CreateTicketRequest.AttachmentItem item : body.getAttachments()) {
                Map<String, Object> map = new HashMap<>();
                map.put("objectKey", item.getObjectKey());
                map.put("fileName", item.getFileName());
                map.put("contentType", item.getContentType());
                map.put("size", item.getSize());
                maps.add(map);
            }
        }
        return maps;
    }

}
