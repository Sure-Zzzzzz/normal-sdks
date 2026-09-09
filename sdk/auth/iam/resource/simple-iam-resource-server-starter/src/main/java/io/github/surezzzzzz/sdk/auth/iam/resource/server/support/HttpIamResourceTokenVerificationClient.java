package io.github.surezzzzzz.sdk.auth.iam.resource.server.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.configuration.SimpleIamResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.constant.SimpleIamResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.IamResourceVerificationProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.IamResourceVerificationUnavailableException;
import io.github.surezzzzzz.sdk.auth.iam.resource.server.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IAM受控令牌验证HTTP客户端。
 *
 * @author surezzzzzz
 */
@Slf4j
public class HttpIamResourceTokenVerificationClient {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private final RestTemplate restTemplate;
    private final String verificationEndpoint;
    private final String basicAuthorization;

    public HttpIamResourceTokenVerificationClient(SimpleIamResourceServerProperties properties) {
        this(properties, createRestTemplate(properties));
    }

    /**
     * 注入自定义 RestTemplate（测试或定制连接层使用；接管错误处理策略）。
     */
    public HttpIamResourceTokenVerificationClient(SimpleIamResourceServerProperties properties, RestTemplate restTemplate) {
        validate(properties);
        if (restTemplate == null) {
            throw new ValidationException("IAM资源验证RestTemplate不能为null");
        }
        this.restTemplate = restTemplate;
        this.restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            /**
             * 关闭 RestTemplate 默认 4xx / 5xx 抛错：401 是业务语义（token 无效），需按状态码分流
             */
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        this.verificationEndpoint = properties.getVerificationEndpoint();
        this.basicAuthorization = createBasicAuthorization(properties.getClientId(), properties.getClientSecret());
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper().enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    private static RestTemplate createRestTemplate(SimpleIamResourceServerProperties properties) {
        validate(properties);
        return new RestTemplate(createRequestFactory(properties));
    }

    private static SimpleClientHttpRequestFactory createRequestFactory(SimpleIamResourceServerProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return factory;
    }

    private static String createBasicAuthorization(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return SimpleIamResourceServerConstant.BASIC_AUTHENTICATION_SCHEME + " "
                + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static void validate(SimpleIamResourceServerProperties properties) {
        if (properties == null || isBlank(properties.getVerificationEndpoint()) || isBlank(properties.getClientId())
                || isBlank(properties.getClientSecret()) || properties.getConnectTimeoutMillis() <= 0
                || properties.getReadTimeoutMillis() <= 0) {
            throw new ConfigurationException("IAM资源验证客户端配置无效");
        }
    }

    private static Map<String, Object> readVerifiedClaims(String responseBody) {
        try {
            Map<String, Object> response = OBJECT_MAPPER.readValue(responseBody,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            if (response.size() != 2 || !response.containsKey(SimpleIamCoreConstant.CLAIM_SUBJECT)
                    || !response.containsKey(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION)) {
                throw new IamResourceVerificationProtocolException();
            }
            Map<String, Object> claims = new LinkedHashMap<String, Object>();
            claims.put(SimpleIamCoreConstant.CLAIM_SUBJECT, response.get(SimpleIamCoreConstant.CLAIM_SUBJECT));
            claims.put(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION,
                    response.get(SimpleIamCoreConstant.CLAIM_APPLICATION_AUTHORIZATION));
            return Collections.unmodifiableMap(claims);
        } catch (IamResourceVerificationProtocolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IamResourceVerificationProtocolException(exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    RestTemplate getRestTemplate() {
        return restTemplate;
    }

    /**
     * 受控验证端点（已验证配置形态，供子类扩展时使用）。
     */
    protected String verificationEndpoint() {
        return verificationEndpoint;
    }

    /**
     * 远程调用验证端点：401 返 null（token 无效），非 200 抛协议异常。
     */
    public Map<String, Object> verify(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.debug("IAM受控验证跳过：令牌为空");
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, basicAuthorization);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        Map<String, Object> request = Collections.<String, Object>singletonMap("token", token);
        try {
            ResponseEntity<String> response = restTemplate.exchange(verificationEndpoint, HttpMethod.POST,
                    new HttpEntity<String>(OBJECT_MAPPER.writeValueAsString(request), headers), String.class);
            if (response.getStatusCodeValue() == SimpleIamResourceServerConstant.HTTP_STATUS_UNAUTHORIZED) {
                log.debug("IAM受控验证返回401，令牌不活跃");
                return null;
            }
            if (response.getStatusCodeValue() != SimpleIamResourceServerConstant.HTTP_STATUS_OK
                    || response.getBody() == null) {
                log.warn("IAM受控验证返回非预期状态，状态码={}", response.getStatusCodeValue());
                throw new IamResourceVerificationProtocolException();
            }
            return readVerifiedClaims(response.getBody());
        } catch (IamResourceVerificationProtocolException exception) {
            throw exception;
        } catch (IamResourceVerificationUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("IAM受控验证调用失败，异常类型={}", exception.getClass().getName());
            throw new IamResourceVerificationUnavailableException(exception);
        } catch (Exception exception) {
            log.warn("IAM受控验证序列化失败，异常类型={}", exception.getClass().getName());
            throw new IamResourceVerificationUnavailableException(exception);
        }
    }
}
