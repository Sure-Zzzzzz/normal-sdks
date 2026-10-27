package io.github.surezzzzzz.sdk.ticket.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.dto.AttachmentDownloadResponse;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentNotFoundException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketAttachment;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketAttachmentRepository;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketQueryService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.spi.FileProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;

/**
 * 管理侧附件下载签发。处理人查看他人工单时必须沿管理 DATA 链路校验可见性。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping("${" + "io.github.surezzzzzz.sdk.ticket.server.api-base-path" + ":/api/ticket}/manage")
@SimpleTicketServerComponent
public class ManageAttachmentController {

    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final TicketPrincipalResolver principalResolver;

    private final TicketQueryService queryService;

    private final TicketAttachmentRepository attachmentRepository;

    private final FileProvider fileProvider;

    public ManageAttachmentController(TicketPrincipalResolver principalResolver,
                                      TicketQueryService queryService,
                                      TicketAttachmentRepository attachmentRepository,
                                      FileProvider fileProvider) {
        this.principalResolver = principalResolver;
        this.queryService = queryService;
        this.attachmentRepository = attachmentRepository;
        this.fileProvider = fileProvider;
    }

    /**
     * 管理侧下载签发。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param attachmentId 附件标识
     * @return 短时效下载地址
     */
    @GetMapping("/tickets/{ticketNo}/attachments/{attachmentId}/download-url")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public AttachmentDownloadResponse downloadUrl(HttpServletRequest request, @PathVariable String ticketNo,
                                                  @PathVariable Long attachmentId) {
        TicketRequestContext context = requirePerson(request);
        Long ticketId = queryService.detail(context, ticketNo).getId();
        TicketAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(TicketAttachmentNotFoundException::new);
        if (attachment.getTicketId() == null || !attachment.getTicketId().equals(ticketId)) {
            throw new TicketAttachmentNotFoundException();
        }
        return AttachmentDownloadResponse.builder()
                .downloadUrl(fileProvider.presignDownload(attachment.getObjectKey(), DOWNLOAD_TTL))
                .build();
    }

    private TicketRequestContext requirePerson(HttpServletRequest request) {
        TicketRequestContext context = principalResolver.resolve(request);
        if (context == null || context.isMachine()) {
            throw new TicketForbiddenActionException();
        }
        return context;
    }
}
