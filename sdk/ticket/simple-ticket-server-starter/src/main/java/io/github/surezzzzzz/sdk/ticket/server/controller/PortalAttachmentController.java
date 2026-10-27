package io.github.surezzzzzz.sdk.ticket.server.controller;

import io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentNotFoundException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException;
import io.github.surezzzzzz.sdk.ticket.server.constant.SimpleTicketServerConstant;
import io.github.surezzzzzz.sdk.ticket.server.configuration.SimpleTicketServerProperties;
import io.github.surezzzzzz.sdk.ticket.server.dto.AttachmentDownloadResponse;
import io.github.surezzzzzz.sdk.ticket.server.dto.AttachmentPresignRequest;
import io.github.surezzzzzz.sdk.ticket.server.dto.AttachmentPresignResponse;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.ticket.server.model.TicketAttachment;
import io.github.surezzzzzz.sdk.ticket.server.repository.TicketAttachmentRepository;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketPrincipalResolver;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketQueryService;
import io.github.surezzzzzz.sdk.ticket.server.service.TicketRequestContext;
import io.github.surezzzzzz.sdk.ticket.server.spi.FileProvider;
import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;

/**
 * 页面 API 之附件面（DESIGN §7.1：预签名直传+下载签发；散形态 Noop 明确报错）。
 *
 * @author surezzzzzz
 */
@RestController
@RequestMapping("${" + "io.github.surezzzzzz.sdk.ticket.server.api-base-path" + ":/api/ticket}/portal")
@SimpleTicketServerComponent
public class PortalAttachmentController {

    private static final Duration UPLOAD_TTL = Duration.ofMinutes(10);

    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final TicketPrincipalResolver principalResolver;

    private final TicketQueryService queryService;

    private final TicketAttachmentRepository attachmentRepository;

    private final FileProvider fileProvider;

    private final SimpleTicketServerProperties properties;

    public PortalAttachmentController(TicketPrincipalResolver principalResolver,
                                      TicketQueryService queryService,
                                      TicketAttachmentRepository attachmentRepository,
                                      FileProvider fileProvider,
                                      SimpleTicketServerProperties properties) {
        this.principalResolver = principalResolver;
        this.queryService = queryService;
        this.attachmentRepository = attachmentRepository;
        this.fileProvider = fileProvider;
        this.properties = properties;
    }

    /**
     * 申请上传（服务端生成 objectKey；提交时回填校验归属=上传者令牌主体）。
     *
     * @param request 请求
     * @param body 上传申请（fileName/contentType）
     * @return objectKey + 预签名 URL
     */
    @PostMapping("/attachments/presign")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_CREATE)
    public AttachmentPresignResponse presign(HttpServletRequest request,
                                             @RequestBody AttachmentPresignRequest body) {
        TicketRequestContext context = requirePerson(request);
        String fileName = body == null ? null : body.getFileName();
        String contentType = body == null ? null : body.getContentType();
        Long size = body == null ? null : body.getSize();
        if (fileName == null || fileName.trim().isEmpty() || contentType == null
                || contentType.trim().isEmpty() || size == null || size <= 0
                || size > properties.getAttachment().getMaxSizeMb() * 1024L * 1024L
                || !allowedType(fileName)) {
            throw new TicketAttachmentRejectException();
        }
        String objectKey = fileProvider.issueUploadKey(null, context.getSubjectId(), fileName, contentType);
        String uploadUrl = fileProvider.presignUpload(objectKey, size, contentType, UPLOAD_TTL);
        return AttachmentPresignResponse.builder()
                .objectKey(objectKey)
                .uploadUrl(uploadUrl)
                .uploadedBy(context.getSubjectId())
                .build();
    }

    /**
     * 下载签发（工单可见性校验后短时效 URL——DESIGN §7.1）。
     *
     * @param request 请求
     * @param ticketNo 单号
     * @param attachmentId 附件 ID
     * @return 下载 URL
     */
    @GetMapping("/tickets/{ticketNo}/attachments/{attachmentId}/download-url")
    @RequireApiPermission(SimpleTicketServerConstant.PERMISSION_READ)
    public AttachmentDownloadResponse downloadUrl(HttpServletRequest request, @PathVariable String ticketNo,
                                                  @PathVariable Long attachmentId) {
        TicketRequestContext context = requirePerson(request);
        queryService.detail(context, ticketNo);
        TicketAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(TicketAttachmentNotFoundException::new);
        if (attachment.getTicketId() == null
                || !attachment.getTicketId().equals(resolveTicketId(context, ticketNo))) {
            throw new TicketAttachmentNotFoundException();
        }
        return AttachmentDownloadResponse.builder()
                .downloadUrl(fileProvider.presignDownload(attachment.getObjectKey(), DOWNLOAD_TTL))
                .build();
    }

    private Long resolveTicketId(TicketRequestContext context, String ticketNo) {
        return queryService.detail(context, ticketNo).getId();
    }

    private TicketRequestContext requirePerson(HttpServletRequest request) {
        TicketRequestContext context = principalResolver.resolve(request);
        if (context == null || context.isMachine()) {
            throw new TicketAttachmentRejectException();
        }
        return context;
    }

    private boolean allowedType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return properties.getAttachment().getAllowedTypes().contains(extension);
    }
}
