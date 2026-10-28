package io.github.surezzzzzz.sdk.feedback.client.feign;

import feign.FeignException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.feedback.client.FeedbackClient;
import io.github.surezzzzzz.sdk.feedback.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.client.constant.FeedbackClientConstant;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientProtocolException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientRetryableException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackAttachmentPart;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackDownload;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackPage;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmissionHint;
import io.github.surezzzzzz.sdk.feedback.client.model.SelectableApplication;
import io.github.surezzzzzz.sdk.feedback.client.support.FeedbackResponseValidator;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 将 Feign wire 响应适配为 core 的校验快照，并统一异常分类。 */
public final class FeedbackFeignClientAdapter implements FeedbackClient {

    private final FeedbackFeignClient delegate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeedbackFeignClientAdapter(FeedbackFeignClient delegate) {
        if (delegate == null) {
            throw new FeedbackClientValidationException("delegate");
        }
        this.delegate = delegate;
    }

    @Override
    public FeedbackSubmission createSubmission(Map<String, Object> submission,
                                               List<FeedbackAttachmentPart> attachments,
                                               String idempotencyKey, String correlationId) {
        requireBody(submission, "submission");
        requireText(idempotencyKey, "idempotencyKey");
        List<MultipartFile> files = new ArrayList<MultipartFile>();
        if (attachments != null) {
            for (FeedbackAttachmentPart attachment : attachments) {
                if (attachment == null) {
                    throw new FeedbackClientValidationException("attachments");
                }
                files.add(new FeedbackMultipartFile(attachment));
            }
        }
        final MultipartFile submissionPart = jsonPart(submission);
        Map<String, Object> value = call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() {
                return delegate.createSubmission(idempotencyKey, correlationId, submissionPart,
                        files.isEmpty() ? null : files);
            }
        });
        return FeedbackResponseValidator.submission(value);
    }

    @Override
    public FeedbackPage listSubmissions(final Map<String, ?> query) {
        return FeedbackResponseValidator.page(call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() {
                return delegate.listSubmissions(query == null
                        ? Collections.<String, Object>emptyMap() : query);
            }
        }));
    }

    @Override
    public FeedbackSubmission getSubmission(String id) {
        requirePath(id);
        return FeedbackResponseValidator.submission(call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() { return delegate.getSubmission(id); }
        }));
    }

    @Override
    public FeedbackSubmission handleSubmission(String id, Map<String, Object> handling, String correlationId) {
        requirePath(id);
        requireBody(handling, "handling");
        return FeedbackResponseValidator.submission(call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() {
                return delegate.handleSubmission(id, correlationId, handling);
            }
        }));
    }

    @Override
    public FeedbackDownload downloadSubmissionAttachment(String id, String attachmentId) {
        requirePath(id);
        requirePath(attachmentId);
        return download(new FeignCall<ResponseEntity<byte[]>>() {
            @Override
            public ResponseEntity<byte[]> invoke() {
                return delegate.downloadSubmissionAttachment(id, attachmentId);
            }
        });
    }

    @Override
    public FeedbackPage listMySubmissions(final Map<String, ?> query) {
        return FeedbackResponseValidator.page(call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() {
                return delegate.listMySubmissions(query == null
                        ? Collections.<String, Object>emptyMap() : query);
            }
        }));
    }

    @Override
    public FeedbackSubmission getMySubmission(String id) {
        requirePath(id);
        return FeedbackResponseValidator.submission(call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() { return delegate.getMySubmission(id); }
        }));
    }

    @Override
    public FeedbackDownload downloadMySubmissionAttachment(String id, String attachmentId) {
        requirePath(id);
        requirePath(attachmentId);
        return download(new FeignCall<ResponseEntity<byte[]>>() {
            @Override
            public ResponseEntity<byte[]> invoke() {
                return delegate.downloadMySubmissionAttachment(id, attachmentId);
            }
        });
    }

    @Override
    public FeedbackSubmissionHint getSubmissionHints() {
        return FeedbackResponseValidator.hint(call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() { return delegate.getSubmissionHints(); }
        }));
    }

    @Override
    public List<SelectableApplication> listSelectableApplications() {
        return FeedbackResponseValidator.applications(call(new FeignCall<List<Map<String, Object>>>() {
            @Override
            public List<Map<String, Object>> invoke() { return delegate.listSelectableApplications(); }
        }));
    }

    @Override
    public List<Map<String, Object>> listApplications() {
        return FeedbackResponseValidator.applicationList(call(new FeignCall<List<Map<String, Object>>>() {
            @Override
            public List<Map<String, Object>> invoke() { return delegate.listApplications(); }
        }));
    }

    @Override
    public Map<String, Object> createApplication(final Map<String, Object> application,
                                                  final String correlationId) {
        requireBody(application, "application");
        return FeedbackResponseValidator.application(call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() {
                return delegate.createApplication(correlationId, application);
            }
        }));
    }

    @Override
    public Map<String, Object> updateApplication(String applicationCode,
                                                  final Map<String, Object> application,
                                                  final String correlationId) {
        requirePath(applicationCode);
        requireBody(application, "application");
        return FeedbackResponseValidator.application(call(new FeignCall<Map<String, Object>>() {
            @Override
            public Map<String, Object> invoke() {
                return delegate.updateApplication(applicationCode, correlationId, application);
            }
        }));
    }

    private FeedbackDownload download(FeignCall<ResponseEntity<byte[]>> operation) {
        ResponseEntity<byte[]> response = call(operation);
        if (response == null) {
            throw new FeedbackClientProtocolException(FeedbackClientConstant.SUCCESS_STATUS,
                    ErrorMessage.RESPONSE_ENCODING_INVALID, null);
        }
        return new FeedbackDownload(response.getBody(),
                response.getHeaders().getFirst("Content-Type"),
                response.getHeaders().getFirst("Content-Disposition"));
    }

    private static <T> T call(FeignCall<T> operation) {
        try {
            return operation.invoke();
        } catch (FeedbackClientException exception) {
            throw exception;
        } catch (FeignException exception) {
            int status = exception.status();
            if (status <= 0 || status >= FeedbackClientConstant.SERVER_ERROR_STATUS
                    || status == FeedbackClientConstant.TOO_MANY_REQUESTS_STATUS) {
                throw new FeedbackClientRetryableException(status,
                        ErrorMessage.SERVICE_UNAVAILABLE, exception);
            }
            throw new FeedbackClientProtocolException(status, ErrorMessage.REQUEST_REJECTED, null);
        }
    }

    private static void requireBody(Object value, String parameter) {
        if (value == null) {
            throw new FeedbackClientValidationException(parameter);
        }
    }

    private static void requireText(String value, String parameter) {
        if (value == null || value.trim().isEmpty()) {
            throw new FeedbackClientValidationException(parameter);
        }
    }

    private static void requirePath(String value) {
        requireText(value, "path");
    }

    private MultipartFile jsonPart(Map<String, Object> value) {
        try {
            return new FeedbackMultipartFile("submission.json", "application/json",
                    objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new FeedbackClientProtocolException(FeedbackClientConstant.CLIENT_ENCODING_STATUS,
                    ErrorMessage.REQUEST_ENCODING_FAILED, null);
        }
    }

    private interface FeignCall<T> {
        T invoke();
    }

    /** 保留原始文件名与媒体类型，供 Spring multipart encoder 生成正确的 part 头。 */
    private static final class FeedbackMultipartFile implements MultipartFile {
        private final String filename;
        private final String contentType;
        private final byte[] content;

        private FeedbackMultipartFile(FeedbackAttachmentPart attachment) {
            this(attachment.getFilename(), attachment.getContentType(), attachment.getContent());
        }

        private FeedbackMultipartFile(String filename, String contentType, byte[] content) {
            this.filename = filename;
            try {
                this.contentType = MediaType.parseMediaType(contentType).toString();
            } catch (InvalidMediaTypeException exception) {
                throw new FeedbackClientValidationException("contentType");
            }
            this.content = content.clone();
        }

        @Override public String getName() { return "attachments"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content.clone(); }
        @Override public InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), content); }
    }
}
