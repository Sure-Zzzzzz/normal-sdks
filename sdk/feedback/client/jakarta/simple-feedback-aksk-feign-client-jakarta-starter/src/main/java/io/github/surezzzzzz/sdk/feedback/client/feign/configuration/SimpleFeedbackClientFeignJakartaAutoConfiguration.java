package io.github.surezzzzzz.sdk.feedback.client.feign.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.FeignException;
import feign.RequestInterceptor;
import feign.RetryableException;
import feign.Retryer;
import io.github.surezzzzzz.sdk.feedback.client.FeedbackClient;
import io.github.surezzzzzz.sdk.feedback.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.client.constant.FeedbackClientConstant;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientProtocolException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientRetryableException;
import io.github.surezzzzzz.sdk.feedback.client.exception.FeedbackClientValidationException;
import io.github.surezzzzzz.sdk.feedback.client.feign.FeedbackFeignClient;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackAttachmentPart;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackDownload;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackPage;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmission;
import io.github.surezzzzzz.sdk.feedback.client.model.FeedbackSubmissionHint;
import io.github.surezzzzzz.sdk.feedback.client.model.SelectableApplication;
import io.github.surezzzzzz.sdk.feedback.client.support.FeedbackResponseValidator;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Jakarta Feign 传输装配；认证拦截器和连接实现由 AKSK 底座提供。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Feign.class, FeedbackClient.class})
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.feedback.client",
        name = "enable", havingValue = "true", matchIfMissing = true)
public class SimpleFeedbackClientFeignJakartaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FeedbackFeignClient.class)
    public FeedbackFeignClient feedbackFeignClient(
            @Qualifier("akskFeignRequestInterceptor") RequestInterceptor authentication,
            ObjectFactory<HttpMessageConverters> converters,
            @Value("${io.github.surezzzzzz.sdk.feedback.client.base-url}") String baseUrl) {
        converters.getObject();
        return Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(converters))
                .decoder(new SpringDecoder(converters))
                .requestInterceptor(authentication)
                .retryer(Retryer.NEVER_RETRY)
                .target(FeedbackFeignClient.class, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean(FeedbackClient.class)
    public FeedbackClient feedbackClient(FeedbackFeignClient contractFeign) {
        return new JakartaFeedbackFeignClient(contractFeign);
    }

    /** core 适配器：只在边界把 wire Map 转为已校验快照。 */
    public static final class JakartaFeedbackFeignClient implements FeedbackClient {
        private final FeedbackFeignClient contractFeign;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public JakartaFeedbackFeignClient(FeedbackFeignClient contractFeign) {
            if (contractFeign == null) {
                throw new FeedbackClientValidationException("contractFeign");
            }
            this.contractFeign = contractFeign;
        }

        @Override
        public FeedbackSubmission createSubmission(Map<String, Object> submission,
                                                   List<FeedbackAttachmentPart> attachments,
                                                   String idempotencyKey, String correlationId) {
            requireBody(submission, "submission");
            requireText(idempotencyKey, "idempotencyKey");
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<String, Object>();
            org.springframework.http.HttpHeaders submissionHeaders = new org.springframework.http.HttpHeaders();
            submissionHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            parts.add("submission", new org.springframework.http.HttpEntity<String>(write(submission), submissionHeaders));
            if (attachments != null) {
                for (FeedbackAttachmentPart attachment : attachments) {
                    if (attachment == null) {
                        throw new FeedbackClientValidationException("attachments");
                    }
                    ByteArrayResource resource = new ByteArrayResource(attachment.getContent()) {
                        @Override
                        public String getFilename() {
                            return attachment.getFilename();
                        }
                    };
                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    try {
                        headers.setContentType(org.springframework.http.MediaType.parseMediaType(
                                attachment.getContentType()));
                    } catch (org.springframework.http.InvalidMediaTypeException exception) {
                        throw new FeedbackClientValidationException("contentType");
                    }
                    parts.add("attachments", new org.springframework.http.HttpEntity<ByteArrayResource>(resource, headers));
                }
            }
            return call(new FeignCall<FeedbackSubmission>() {
                @Override
                public FeedbackSubmission invoke() {
                    return FeedbackResponseValidator.submission(contractFeign.createSubmission(
                            idempotencyKey, correlationId, parts));
                }
            });
        }

        @Override
        public FeedbackPage listSubmissions(final Map<String, ?> query) {
            return call(new FeignCall<FeedbackPage>() {
                @Override public FeedbackPage invoke() {
                    return FeedbackResponseValidator.page(contractFeign.listSubmissions(copyQuery(query)));
                }
            });
        }

        @Override
        public FeedbackSubmission getSubmission(final String id) {
            return call(new FeignCall<FeedbackSubmission>() {
                @Override public FeedbackSubmission invoke() {
                    return FeedbackResponseValidator.submission(contractFeign.getSubmission(path(id)));
                }
            });
        }

        @Override
        public FeedbackSubmission handleSubmission(final String id, Map<String, Object> handling,
                                                   final String correlationId) {
            requireBody(handling, "handling");
            return call(new FeignCall<FeedbackSubmission>() {
                @Override public FeedbackSubmission invoke() {
                    return FeedbackResponseValidator.submission(contractFeign.handleSubmission(
                            path(id), correlationId, handling));
                }
            });
        }

        @Override
        public FeedbackDownload downloadSubmissionAttachment(final String id, final String attachmentId) {
            return download(new FeignCall<ResponseEntity<byte[]>>() {
                @Override public ResponseEntity<byte[]> invoke() {
                    return contractFeign.downloadSubmissionAttachment(path(id), path(attachmentId));
                }
            });
        }

        @Override
        public FeedbackPage listMySubmissions(final Map<String, ?> query) {
            return call(new FeignCall<FeedbackPage>() {
                @Override public FeedbackPage invoke() {
                    return FeedbackResponseValidator.page(contractFeign.listMySubmissions(copyQuery(query)));
                }
            });
        }

        @Override
        public FeedbackSubmission getMySubmission(final String id) {
            return call(new FeignCall<FeedbackSubmission>() {
                @Override public FeedbackSubmission invoke() {
                    return FeedbackResponseValidator.submission(contractFeign.getMySubmission(path(id)));
                }
            });
        }

        @Override
        public FeedbackDownload downloadMySubmissionAttachment(final String id, final String attachmentId) {
            return download(new FeignCall<ResponseEntity<byte[]>>() {
                @Override public ResponseEntity<byte[]> invoke() {
                    return contractFeign.downloadMySubmissionAttachment(path(id), path(attachmentId));
                }
            });
        }

        @Override
        public FeedbackSubmissionHint getSubmissionHints() {
            return call(new FeignCall<FeedbackSubmissionHint>() {
                @Override public FeedbackSubmissionHint invoke() {
                    return FeedbackResponseValidator.hint(contractFeign.getSubmissionHints());
                }
            });
        }

        @Override
        public List<SelectableApplication> listSelectableApplications() {
            return call(new FeignCall<List<SelectableApplication>>() {
                @Override public List<SelectableApplication> invoke() {
                    return FeedbackResponseValidator.applications(contractFeign.listSelectableApplications());
                }
            });
        }

        @Override
        public List<Map<String, Object>> listApplications() {
            return call(new FeignCall<List<Map<String, Object>>>() {
                @Override public List<Map<String, Object>> invoke() {
                    return FeedbackResponseValidator.applicationList(contractFeign.listApplications());
                }
            });
        }

        @Override
        public Map<String, Object> createApplication(final Map<String, Object> application,
                                                     final String correlationId) {
            requireBody(application, "application");
            return call(new FeignCall<Map<String, Object>>() {
                @Override public Map<String, Object> invoke() {
                    return FeedbackResponseValidator.application(contractFeign.createApplication(
                            correlationId, application));
                }
            });
        }

        @Override
        public Map<String, Object> updateApplication(final String applicationCode,
                                                     final Map<String, Object> application,
                                                     final String correlationId) {
            requireBody(application, "application");
            return call(new FeignCall<Map<String, Object>>() {
                @Override public Map<String, Object> invoke() {
                    return FeedbackResponseValidator.application(contractFeign.updateApplication(
                            path(applicationCode), correlationId, application));
                }
            });
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

        private <T> T call(FeignCall<T> operation) {
            try {
                return operation.invoke();
            } catch (RetryableException exception) {
                throw new FeedbackClientRetryableException(FeedbackClientConstant.NETWORK_FAILURE_STATUS,
                        ErrorMessage.NETWORK_UNAVAILABLE, exception);
            } catch (FeignException exception) {
                int status = exception.status();
                String correlationId = null;
                if (exception.responseHeaders() != null && exception.responseHeaders().containsKey("X-Correlation-Id")) {
                    correlationId = exception.responseHeaders().get("X-Correlation-Id").stream().findFirst().orElse(null);
                }
                if (status >= FeedbackClientConstant.SERVER_ERROR_STATUS
                        || status == FeedbackClientConstant.TOO_MANY_REQUESTS_STATUS) {
                    throw new FeedbackClientRetryableException(status, ErrorMessage.SERVICE_UNAVAILABLE, exception);
                }
                throw new FeedbackClientProtocolException(status, ErrorMessage.REQUEST_REJECTED, correlationId);
            }
        }

        private String write(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception exception) {
                throw new FeedbackClientProtocolException(FeedbackClientConstant.CLIENT_ENCODING_STATUS,
                        ErrorMessage.REQUEST_ENCODING_FAILED, null);
            }
        }

        private static String path(String value) {
            if (value == null || value.trim().isEmpty()) {
                throw new FeedbackClientValidationException("path");
            }
            return value;
        }

        private static void requireText(String value, String parameter) {
            if (value == null || value.trim().isEmpty()) {
                throw new FeedbackClientValidationException(parameter);
            }
        }

        private static void requireBody(Object value, String parameter) {
            if (value == null) {
                throw new FeedbackClientValidationException(parameter);
            }
        }

        private static Map<String, Object> copyQuery(Map<String, ?> query) {
            if (query == null || query.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, Object> copy = new java.util.LinkedHashMap<String, Object>();
            for (Map.Entry<String, ?> entry : query.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    copy.put(entry.getKey(), entry.getValue());
                }
            }
            return copy;
        }

        private interface FeignCall<T> {
            T invoke();
        }
    }
}
