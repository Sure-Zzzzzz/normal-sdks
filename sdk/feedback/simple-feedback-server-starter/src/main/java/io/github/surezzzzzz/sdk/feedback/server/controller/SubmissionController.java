package io.github.surezzzzzz.sdk.feedback.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.*;
import io.github.surezzzzzz.sdk.feedback.server.controller.request.SubmissionCreateRequest;
import io.github.surezzzzzz.sdk.feedback.server.controller.request.SubmissionHandlingRequest;
import io.github.surezzzzzz.sdk.feedback.server.controller.response.SubmissionResponse;
import io.github.surezzzzzz.sdk.feedback.server.exception.AttachmentRejectException;
import io.github.surezzzzzz.sdk.feedback.server.exception.SimpleFeedbackServerException;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplicationAccessScope;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.server.repository.FeedbackSubmissionRepository;
import io.github.surezzzzzz.sdk.feedback.server.service.*;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import io.github.surezzzzzz.sdk.feedback.server.support.FeedbackRestExceptionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 反馈提交族 REST 端点（12 端点之 9；根路径可配，常量默认 /api/feedback）。
 *
 * <p>规范 §3.5/§3.6：资源名词路径、状态只由 HTTP status 表达、创建 201+Location、
 * 写请求必带 Idempotency-Key、X-Correlation-Id 缺头服务端生成并回显。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RestController
@RequestMapping("${" + SimpleFeedbackServerConstant.CONFIG_PREFIX + ".api-base-path:/api/feedback}")
@RequiredArgsConstructor
public class SubmissionController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SubmissionCreateService createService;
    private final SubmissionQueryService queryService;
    private final SubmissionHandlingService handlingService;
    private final HintService hintService;
    private final AttachmentStorageService attachmentStorageService;
    private final FeedbackPrincipalResolver principalResolver;
    private final io.github.surezzzzzz.sdk.feedback.server.service.ApplicationRegistryService applicationRegistryService;
    private final io.github.surezzzzzz.sdk.feedback.server.configuration.SimpleFeedbackServerProperties properties;
    private final ObjectProvider<SubmitterProfileProvider> submitterProfileProvider;

    /**
     * 附件规格校验（数量/单文件/总量/类型白名单，全部取运行配置值）。
     *
     * @param files  附件集合
     * @param config 附件运行配置
     */
    private static void validateAttachmentSpecs(List<MultipartFile> files,
                                                io.github.surezzzzzz.sdk.feedback.server.configuration.SimpleFeedbackServerProperties.AttachmentConfig config) {
        if (files.size() > config.getMaxCount()) {
            throw new AttachmentRejectException(ErrorCode.ATTACHMENT_COUNT_EXCEEDED,
                    String.format(ErrorMessage.ATTACHMENT_COUNT_EXCEEDED, config.getMaxCount()));
        }
        long total = 0L;
        for (MultipartFile file : files) {
            if (file.getSize() > config.getMaxSizeBytes()) {
                throw new AttachmentRejectException(ErrorCode.ATTACHMENT_SIZE_EXCEEDED,
                        String.format(ErrorMessage.ATTACHMENT_SIZE_EXCEEDED, config.getMaxSizeBytes()));
            }
            total += file.getSize();
            boolean allowed = false;
            for (String candidate : config.getAllowedContentTypes()) {
                if (candidate.equalsIgnoreCase(file.getContentType())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new AttachmentRejectException(ErrorCode.ATTACHMENT_MEDIA_TYPE_REJECTED,
                        String.format(ErrorMessage.ATTACHMENT_MEDIA_TYPE_REJECTED,
                                Arrays.toString(config.getAllowedContentTypes())));
            }
        }
        if (total > config.getMaxTotalSizeBytes()) {
            throw new AttachmentRejectException(ErrorCode.ATTACHMENT_TOTAL_SIZE_EXCEEDED,
                    String.format(ErrorMessage.ATTACHMENT_TOTAL_SIZE_EXCEEDED, config.getMaxTotalSizeBytes()));
        }
    }

    private static HttpHeaders correlationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", FeedbackRestExceptionHandler.currentCorrelationId());
        return headers;
    }

    private static byte[] readAll(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new SimpleFeedbackServerException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    ErrorMessage.DEPENDENCY_UNAVAILABLE, exception);
        }
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null) {
            return SimpleFeedbackServerConstant.DEFAULT_ATTACHMENT_FILENAME;
        }
        String name = filename.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name;
    }

    /**
     * 环境快照白名单裁剪（多余键服务端丢弃，DESIGN §5）。
     *
     * @param environment 原文 JSON
     * @return 裁剪后 JSON（原值为空或非法 JSON 时返回 null）
     */
    private static String sanitizeEnvironment(String environment) {
        if (environment == null || environment.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(environment);
            if (!root.isObject()) {
                return null;
            }
            java.util.LinkedHashMap<String, String> allowed = new java.util.LinkedHashMap<>();
            for (String key : SimpleFeedbackServerConstant.ENVIRONMENT_ALLOWED_KEYS) {
                JsonNode value = root.get(key);
                if (value != null && value.isValueNode()) {
                    allowed.put(key, value.asText());
                }
            }
            FeedbackSubmission.validateEnvironmentKeys(allowed.keySet());
            return OBJECT_MAPPER.writeValueAsString(allowed);
        } catch (IOException exception) {
            return null;
        }
    }

    /**
     * 幂等摘要（走查纠错：覆盖页面上下文与附件元数据清单——同键同内容判定必须含全部
     * 影响创建结果的输入，否则换附件重试会被误判为重放）。
     *
     * @param request 提交主体
     * @param files   附件集合（可空）
     * @return SHA-256 截断摘要
     */
    private static String digest(SubmissionCreateRequest request, List<MultipartFile> files) {
        StringBuilder raw = new StringBuilder(String.format("%s|%s|%s|%s|%s|%s|%s", request.getApplicationCode(),
                request.getKind(), request.getType(), request.getScore(), request.getContent(),
                request.getContactEmail(), request.getEnvironment()));
        raw.append("|ctx=").append(request.getContextPath());
        if (files != null) {
            for (MultipartFile file : files) {
                raw.append("|att=").append(sanitizeFilename(file.getOriginalFilename()))
                        .append(':').append(file.getContentType()).append(':').append(file.getSize());
            }
        }
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new SimpleFeedbackServerException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    ErrorMessage.DEPENDENCY_UNAVAILABLE, exception);
        }
    }

    /**
     * 提交反馈（multipart：JSON part + 附件 parts；Idempotency-Key 必带）。
     *
     * @param createRequest  JSON 主体 part
     * @param files          附件 parts（可空）
     * @param idempotencyKey 幂等键
     * @param request        HTTP 请求
     * @return 201 + Location
     */
    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_CREATE)
    public ResponseEntity<SubmissionResponse> create(@RequestPart("submission") SubmissionCreateRequest createRequest,
                                                     @RequestPart(value = "attachments", required = false)
                                                     List<MultipartFile> files,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     HttpServletRequest request) {
        FeedbackRequestContext context = requireContext(request);
        List<SubmissionCreateService.AttachmentInput> attachments = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            // 走查纠错：附件开关/数量/单文件/总量/类型全部走运行配置（Properties），
            // 此前只有 core 常量校验——配置键面形同虚设
            io.github.surezzzzzz.sdk.feedback.server.configuration.SimpleFeedbackServerProperties.AttachmentConfig config = properties.getAttachment();
            if (!config.isEnabled() || !attachmentStorageService.attachmentAvailable()) {
                throw new AttachmentRejectException(ErrorCode.ATTACHMENT_NOT_ENABLED,
                        ErrorMessage.ATTACHMENT_NOT_ENABLED);
            }
            validateAttachmentSpecs(files, config);
            for (MultipartFile file : files) {
                attachments.add(new SubmissionCreateService.AttachmentInput(
                        sanitizeFilename(file.getOriginalFilename()), file.getContentType(), file.getSize(),
                        new ByteArrayInputStream(readAll(file))));
            }
        }
        FeedbackSubmission submission = FeedbackSubmission.builder()
                .id(UUID.randomUUID().toString())
                .applicationCode(createRequest.getApplicationCode())
                .kind(createRequest.getKind())
                .type(createRequest.getType())
                .score(createRequest.getScore() == null ? 0 : createRequest.getScore())
                .content(createRequest.getContent())
                .contactEmail(createRequest.getContactEmail())
                .submitterSubjectId(context.getSubjectId())
                .submitterDisplayName(resolveSubmitterDisplayName(context.getSubjectId()))
                .owner(context.getOwner())
                .contextPath(createRequest.getContextPath())
                .environment(sanitizeEnvironment(createRequest.getEnvironment()))
                .status(FeedbackStatus.SUBMITTED.getCode())
                .rowVersion(0)
                .idempotencyKey(idempotencyKey)
                .idempotencyDigest(digest(createRequest, files))
                .createdTime(Instant.now())
                .updatedTime(Instant.now())
                .build();
        SubmissionCreateService.CreateResult result = createService.create(submission, attachments);
        HttpHeaders headers = correlationHeaders();
        headers.setLocation(URI.create("/submissions/" + result.getSubmission().getId()));
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers)
                .body(SubmissionResponse.fromMine(result.getSubmission(), null));
    }

    /**
     * 提交提示（交集后应用清单+邮箱预填，散形态降级）。
     *
     * @param request HTTP 请求
     * @return 200 提示
     */
    @GetMapping("/submission-hints")
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_HINT)
    public ResponseEntity<HintService.SubmissionHint> hint(HttpServletRequest request) {
        FeedbackRequestContext context = requireContext(request);
        HintService.SubmissionHint hint = hintService.hint(context.getSubjectId());
        return ResponseEntity.ok().headers(correlationHeaders()).body(hint);
    }

    /**
     * 应用选择列表（启用中注册项全量；交集过滤走 submission-hints）。
     *
     * @return 200 注册项精简列表
     */
    @GetMapping("/selectable-applications")
    @RequireApiPermission(FeedbackPermissionConstant.API_APPLICATION_LIST)
    public ResponseEntity<List<ApplicationController.SelectableApplication>> selectableApplications() {
        List<ApplicationController.SelectableApplication> items = new ArrayList<>();
        for (io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication application
                : queryApplications()) {
            items.add(ApplicationController.SelectableApplication.builder()
                    .applicationCode(application.getApplicationCode())
                    .displayName(application.getDisplayName())
                    .description(application.getDescription())
                    .build());
        }
        return ResponseEntity.ok().headers(correlationHeaders()).body(items);
    }

    /**
     * 管理列表（筛选+DATA 范围+分页）。
     *
     * @return 200 分页
     */
    @GetMapping("/submissions")
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_READ)
    @DataPermissionOperation(resource = FeedbackPermissionConstant.DATA_RESOURCE_SUBMISSION,
            action = FeedbackPermissionConstant.DATA_ACTION_READ)
    public ResponseEntity<SubmissionQueryService.PageResult<SubmissionResponse>> adminList(
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer score,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            @CurrentDataAccessPlan DataAccessPlan plan,
            HttpServletRequest request) {
        requireContext(request);
        int resolvedSize = resolvePageSize(size);
        FeedbackSubmissionRepository.Filter filter = new FeedbackSubmissionRepository.Filter(application, status,
                type, kind, score);
        SubmissionQueryService.PageResult<FeedbackSubmission> result = queryService.adminList(filter,
                FeedbackApplicationAccessScope.from(plan), page, resolvedSize);
        List<SubmissionResponse> items = new ArrayList<>();
        for (FeedbackSubmission submission : result.getItems()) {
            items.add(SubmissionResponse.fromAdmin(submission,
                    queryService.attachments(submission.getId())));
        }
        return ResponseEntity.ok().headers(correlationHeaders())
                .body(new SubmissionQueryService.PageResult<>(items, result.getTotal(), result.getPage(),
                        result.getSize()));
    }

    /**
     * 管理详情（DATA 不可见=安全 404）。
     *
     * @return 200 详情
     */
    @GetMapping("/submissions/{id}")
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_READ)
    @DataPermissionOperation(resource = FeedbackPermissionConstant.DATA_RESOURCE_SUBMISSION,
            action = FeedbackPermissionConstant.DATA_ACTION_READ)
    public ResponseEntity<SubmissionResponse> adminGet(@PathVariable String id,
                                                       @CurrentDataAccessPlan DataAccessPlan plan,
                                                       HttpServletRequest request) {
        requireContext(request);
        FeedbackSubmission submission = queryService.adminGet(id, FeedbackApplicationAccessScope.from(plan));
        return ResponseEntity.ok().headers(correlationHeaders())
                .body(SubmissionResponse.fromAdmin(submission, queryService.attachments(id)));
    }

    /**
     * 处理反馈（状态+备注+回复整体覆盖；expectedRowVersion CAS）。
     *
     * @return 200 处理后详情
     */
    @PutMapping("/submissions/{id}/handling")
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_MANAGE)
    @DataPermissionOperation(resource = FeedbackPermissionConstant.DATA_RESOURCE_SUBMISSION,
            action = FeedbackPermissionConstant.DATA_ACTION_MANAGE)
    public ResponseEntity<SubmissionResponse> handle(@PathVariable String id,
                                                     @RequestBody SubmissionHandlingRequest handlingRequest,
                                                     @CurrentDataAccessPlan DataAccessPlan plan,
                                                     HttpServletRequest request) {
        FeedbackRequestContext context = requireContext(request);
        FeedbackStatus target = FeedbackStatus.fromCode(handlingRequest.getTargetStatus());
        if (target == null) {
            throw new SimpleFeedbackServerException(ErrorCode.ENUM_VALUE_INVALID,
                    String.format(ErrorMessage.ENUM_VALUE_INVALID, "targetStatus",
                            java.util.Arrays.toString(FeedbackStatus.getAllCodes())));
        }
        FeedbackSubmission submission = handlingService.handle(id, target, context.getSubjectId(),
                handlingRequest.getHandlerNote(), handlingRequest.getReply(),
                handlingRequest.getExpectedRowVersion() == null ? -1 : handlingRequest.getExpectedRowVersion(),
                FeedbackApplicationAccessScope.from(plan));
        return ResponseEntity.ok().headers(correlationHeaders())
                .body(SubmissionResponse.fromAdmin(submission, queryService.attachments(id)));
    }

    /**
     * 我的反馈列表（owner 等值）。
     *
     * @return 200 分页
     */
    @GetMapping("/my-submissions")
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_MINE)
    public ResponseEntity<SubmissionQueryService.PageResult<SubmissionResponse>> myList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        FeedbackRequestContext context = requireContext(request);
        int resolvedSize = resolvePageSize(size);
        SubmissionQueryService.PageResult<FeedbackSubmission> result = queryService.mineList(context.getOwner(),
                page, resolvedSize);
        List<SubmissionResponse> items = new ArrayList<>();
        for (FeedbackSubmission submission : result.getItems()) {
            items.add(SubmissionResponse.fromMine(submission, queryService.attachments(submission.getId())));
        }
        return ResponseEntity.ok().headers(correlationHeaders())
                .body(new SubmissionQueryService.PageResult<>(items, result.getTotal(), result.getPage(),
                        result.getSize()));
    }

    /**
     * 我的反馈详情（非本人=安全 404）。
     *
     * @return 200 详情（含 reply）
     */
    @GetMapping("/my-submissions/{id}")
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_MINE)
    public ResponseEntity<SubmissionResponse> myGet(@PathVariable String id, HttpServletRequest request) {
        FeedbackRequestContext context = requireContext(request);
        FeedbackSubmission submission = queryService.mineGet(id, context.getOwner());
        return ResponseEntity.ok().headers(correlationHeaders())
                .body(SubmissionResponse.fromMine(submission, queryService.attachments(id)));
    }

    /**
     * 我的附件下载（服务端代理流式；owner 等值）。
     *
     * @return 200 附件流
     */
    @GetMapping("/my-submissions/{id}/attachments/{attachmentId}")
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_MINE)
    public ResponseEntity<org.springframework.core.io.InputStreamResource> myDownload(@PathVariable String id,
                                                                                      @PathVariable String attachmentId,
                                                                                      HttpServletRequest request) {
        FeedbackRequestContext context = requireContext(request);
        queryService.mineGet(id, context.getOwner());
        return streamAttachment(id, attachmentId);
    }

    /**
     * 管理附件下载（DATA 范围）。
     *
     * @return 200 附件流
     */
    @GetMapping("/submissions/{id}/attachments/{attachmentId}")
    @RequireApiPermission(FeedbackPermissionConstant.API_SUBMISSION_READ)
    @DataPermissionOperation(resource = FeedbackPermissionConstant.DATA_RESOURCE_SUBMISSION,
            action = FeedbackPermissionConstant.DATA_ACTION_READ)
    public ResponseEntity<org.springframework.core.io.InputStreamResource> adminDownload(
            @PathVariable String id, @PathVariable String attachmentId,
            @CurrentDataAccessPlan DataAccessPlan plan, HttpServletRequest request) {
        requireContext(request);
        queryService.adminGet(id, FeedbackApplicationAccessScope.from(plan));
        return streamAttachment(id, attachmentId);
    }

    private ResponseEntity<org.springframework.core.io.InputStreamResource> streamAttachment(String id,
                                                                                             String attachmentId) {
        io.github.surezzzzzz.sdk.feedback.server.model.FeedbackAttachment attachment =
                queryService.attachment(id, attachmentId);
        AttachmentStorageService.StoredObject stored = attachmentStorageService.download(
                attachment.getStorageKey(), properties.getAttachment().getBucketName());
        HttpHeaders headers = correlationHeaders();
        headers.setContentType(MediaType.parseMediaType(
                attachment.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : attachment.getContentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getId() + "\"");
        return ResponseEntity.ok().headers(headers)
                .body(new org.springframework.core.io.InputStreamResource(stored.getContent()));
    }

    private List<io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication> queryApplications() {
        return applicationRegistryService.listEnabled();
    }

    /**
     * 提交时快照提交人展示名（回访展示用；与 hint 邮箱预填同一取数点同一降级语义）：
     * 散形态无 provider、聚形态出站失败均返回 null（缺省裸 subjectId 展示），不拒提交。
     */
    private String resolveSubmitterDisplayName(String subjectId) {
        SubmitterProfileProvider profileProvider = submitterProfileProvider.getIfAvailable();
        if (profileProvider == null) {
            return null;
        }
        try {
            io.github.surezzzzzz.sdk.feedback.server.model.SubmissionContactHint contactHint =
                    profileProvider.contactHint(subjectId);
            return contactHint == null ? null : contactHint.getDisplayName();
        } catch (RuntimeException exception) {
            log.warn("提交人展示名取数出站失败，该项降级（不拒提交）来源=SubmitterProfileProvider");
            return null;
        }
    }

    private FeedbackRequestContext requireContext(HttpServletRequest request) {
        FeedbackRequestContext context = principalResolver.resolve(request);
        if (context == null) {
            throw new SimpleFeedbackServerException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    ErrorMessage.DEPENDENCY_UNAVAILABLE);
        }
        return context;
    }

    /**
     * 分页页大小解析（缺省取配置默认值；走查纠错：超上限 clamp 到上限、非法值回落默认——
     * 此前 page.maxSize 配置从未执行）。
     *
     * @param size 请求页大小（可空）
     * @return 生效页大小
     */
    private int resolvePageSize(Integer size) {
        io.github.surezzzzzz.sdk.feedback.server.configuration.SimpleFeedbackServerProperties.PageConfig config = properties.getPage();
        if (size == null || size < 1) {
            return config.getDefaultSize();
        }
        return Math.min(size, config.getMaxSize());
    }
}
