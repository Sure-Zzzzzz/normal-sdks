package io.github.surezzzzzz.sdk.feedback.client.resttemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.feedback.client.FeedbackClient;
import io.github.surezzzzzz.sdk.feedback.client.constant.FeedbackClientConstant;
import io.github.surezzzzzz.sdk.feedback.client.constant.ErrorMessage;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Feedback RestTemplate 客户端；认证由 AKSK {@code akskClientRestTemplate} 注入。 */
public class FeedbackRestTemplateClient implements FeedbackClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FeedbackRestTemplateClient(RestTemplate restTemplate, String baseUrl) {
        if (restTemplate == null) {
            throw new FeedbackClientValidationException("restTemplate");
        }
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new FeedbackClientValidationException("baseUrl");
        }
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
    }

    @Override
    public FeedbackSubmission createSubmission(Map<String, Object> submission,
                                               List<FeedbackAttachmentPart> attachments,
                                               String idempotencyKey, String correlationId) {
        requireBody(submission, "submission");
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new FeedbackClientValidationException("idempotencyKey");
        }
        HttpHeaders headers = jsonHeaders(idempotencyKey, correlationId);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<String, Object>();
        HttpHeaders jsonPartHeaders = new HttpHeaders();
        jsonPartHeaders.setContentType(MediaType.APPLICATION_JSON);
        parts.add("submission", new HttpEntity<String>(write(submission), jsonPartHeaders));
        if (attachments != null) {
            for (final FeedbackAttachmentPart attachment : attachments) {
                if (attachment == null) {
                    throw new FeedbackClientValidationException("attachments");
                }
                ByteArrayResource resource = new ByteArrayResource(attachment.getContent()) {
                    @Override
                    public String getFilename() { return attachment.getFilename(); }
                };
                HttpHeaders fileHeaders = new HttpHeaders();
                try {
                    fileHeaders.setContentType(MediaType.parseMediaType(attachment.getContentType()));
                } catch (InvalidMediaTypeException exception) {
                    throw new FeedbackClientValidationException("contentType");
                }
                parts.add("attachments", new HttpEntity<ByteArrayResource>(resource, fileHeaders));
            }
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        Map<String, Object> value = exchange(HttpMethod.POST, "/submissions", headers, parts,
                new TypeReference<Map<String, Object>>() { });
        return FeedbackResponseValidator.submission(value);
    }

    @Override
    public FeedbackPage listSubmissions(Map<String, ?> query) {
        return FeedbackResponseValidator.page(exchange(HttpMethod.GET, "/submissions", null, null,
                mapType(), query));
    }

    @Override
    public FeedbackSubmission getSubmission(String id) {
        return FeedbackResponseValidator.submission(exchange(HttpMethod.GET, "/submissions/" + path(id), null,
                null, mapType(), null));
    }

    @Override
    public FeedbackSubmission handleSubmission(String id, Map<String, Object> handling, String correlationId) {
        requireBody(handling, "handling");
        return FeedbackResponseValidator.submission(exchange(HttpMethod.PUT, "/submissions/" + path(id)
                        + "/handling", jsonHeaders(null, correlationId), write(handling), mapType()));
    }

    @Override
    public FeedbackDownload downloadSubmissionAttachment(String id, String attachmentId) {
        return download("/submissions/" + path(id) + "/attachments/" + path(attachmentId));
    }

    @Override
    public FeedbackPage listMySubmissions(Map<String, ?> query) {
        return FeedbackResponseValidator.page(exchange(HttpMethod.GET, "/me/submissions", null, null,
                mapType(), query));
    }

    @Override
    public FeedbackSubmission getMySubmission(String id) {
        return FeedbackResponseValidator.submission(exchange(HttpMethod.GET, "/me/submissions/" + path(id), null,
                null, mapType(), null));
    }

    @Override
    public FeedbackDownload downloadMySubmissionAttachment(String id, String attachmentId) {
        return download("/me/submissions/" + path(id) + "/attachments/" + path(attachmentId));
    }

    @Override
    public FeedbackSubmissionHint getSubmissionHints() {
        return FeedbackResponseValidator.hint(exchange(HttpMethod.GET, "/submission-hints", null, null,
                mapType(), null));
    }

    @Override
    public List<SelectableApplication> listSelectableApplications() {
        return FeedbackResponseValidator.applications(exchange(HttpMethod.GET, "/selectable-applications",
                null, null, listType(), null));
    }

    @Override
    public List<Map<String, Object>> listApplications() {
        return FeedbackResponseValidator.applicationList(
                exchange(HttpMethod.GET, "/applications", null, null, listType(), null));
    }

    @Override
    public Map<String, Object> createApplication(Map<String, Object> application, String correlationId) {
        requireBody(application, "application");
        return FeedbackResponseValidator.application(exchange(HttpMethod.POST, "/applications",
                jsonHeaders(null, correlationId), write(application), mapType()));
    }

    @Override
    public Map<String, Object> updateApplication(String applicationCode, Map<String, Object> application,
                                                 String correlationId) {
        requireBody(application, "application");
        return FeedbackResponseValidator.application(exchange(HttpMethod.PUT, "/applications/" + path(applicationCode),
                jsonHeaders(null, correlationId), write(application), mapType()));
    }

    private FeedbackDownload download(String path) {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(uri(path, null), HttpMethod.GET,
                    new HttpEntity<byte[]>(new HttpHeaders()), byte[].class);
            return new FeedbackDownload(response.getBody(), response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                    response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        } catch (HttpStatusCodeException exception) {
            throw mapHttp(exception);
        } catch (ResourceAccessException exception) {
            throw new FeedbackClientRetryableException(FeedbackClientConstant.NETWORK_FAILURE_STATUS,
                    ErrorMessage.NETWORK_UNAVAILABLE, exception);
        }
    }

    private <T> T exchange(HttpMethod method, String path, HttpHeaders headers, Object body,
                           TypeReference<T> type) {
        return exchange(method, path, headers, body, type, null);
    }

    private <T> T exchange(HttpMethod method, String path, HttpHeaders headers, Object body,
                           TypeReference<T> type, Map<String, ?> query) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(uri(path, query), method,
                    new HttpEntity<Object>(body, headers == null ? new HttpHeaders() : headers), String.class);
            return read(response.getBody(), type);
        } catch (HttpStatusCodeException exception) {
            throw mapHttp(exception);
        } catch (ResourceAccessException exception) {
            throw new FeedbackClientRetryableException(FeedbackClientConstant.NETWORK_FAILURE_STATUS,
                    ErrorMessage.NETWORK_UNAVAILABLE, exception);
        }
    }

    private <T> T exchange(HttpMethod method, String path, HttpHeaders headers, String body,
                           TypeReference<T> type) {
        return exchange(method, path, headers, (Object) body, type);
    }

    private URI uri(String path, Map<String, ?> query) {
        String target = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(FeedbackClientConstant.API_BASE_PATH).build().toUriString() + path;
        if (query != null) {
            UriComponentsBuilder queryBuilder = UriComponentsBuilder.newInstance();
            for (Map.Entry<String, ?> entry : query.entrySet()) {
                if (entry.getValue() != null) {
                    queryBuilder.queryParam(entry.getKey(), entry.getValue());
                }
            }
            String queryString = queryBuilder.build().encode().getQuery();
            if (queryString != null && !queryString.isEmpty()) {
                target += "?" + queryString;
            }
        }
        return URI.create(target);
    }

    private HttpHeaders jsonHeaders(String idempotencyKey, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            headers.set("X-Correlation-Id", correlationId);
        }
        return headers;
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) {
            throw new FeedbackClientProtocolException(FeedbackClientConstant.CLIENT_ENCODING_STATUS,
                    ErrorMessage.REQUEST_ENCODING_FAILED, null);
        }
    }

    private static void requireBody(Object value, String parameter) {
        if (value == null) {
            throw new FeedbackClientValidationException(parameter);
        }
    }

    private <T> T read(String body, TypeReference<T> type) {
        try { return objectMapper.readValue(body, type); }
        catch (Exception exception) {
            throw new FeedbackClientProtocolException(FeedbackClientConstant.SUCCESS_STATUS,
                    ErrorMessage.RESPONSE_ENCODING_INVALID, null);
        }
    }

    private FeedbackClientException mapHttp(HttpStatusCodeException exception) {
        String correlationId = exception.getResponseHeaders() == null ? null
                : exception.getResponseHeaders().getFirst("X-Correlation-Id");
        int status = exception.getRawStatusCode();
        if (status >= FeedbackClientConstant.SERVER_ERROR_STATUS
                || status == FeedbackClientConstant.TOO_MANY_REQUESTS_STATUS) {
            return new FeedbackClientRetryableException(status, ErrorMessage.SERVICE_UNAVAILABLE, exception);
        }
        return new FeedbackClientProtocolException(status, ErrorMessage.REQUEST_REJECTED, correlationId);
    }

    private static String path(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new FeedbackClientValidationException("path");
        }
        String encoded = UriComponentsBuilder.newInstance().pathSegment(value).build().encode()
                .toUriString();
        return encoded.startsWith("/") ? encoded.substring(1) : encoded;
    }

    private static TypeReference<Map<String, Object>> mapType() {
        return new TypeReference<Map<String, Object>>() { };
    }

    private static TypeReference<List<Map<String, Object>>> listType() {
        return new TypeReference<List<Map<String, Object>>>() { };
    }
}
