package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT Token验证测试
 * <p>
 * 测试JWT token的生成、格式和内容是否正确
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class JwtTokenValidationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");

        // 清理客户端数据
        clientRepository.findAll().forEach(client -> {
            log.info("删除客户端: id={}, clientId={}", client.getId(), client.getClientId());
        });
        clientRepository.deleteAll();

        // 清理Redis中的测试数据
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            keys.forEach(key -> {
                log.info("删除Redis key: {}", key);
            });
            redisTemplate.delete(keys);
            log.info("清理Redis测试数据: {} 条", keys.size());
        }

        log.info("测试数据清理完成");
    }

    @Test
    void testJwtTokenFormat() throws Exception {
        log.info("测试JWT token格式");

        // Step 1: 创建AKSK并获取token
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("JWT Format Test Client");
        String accessToken = getAccessToken(clientInfo);

        log.info("获取到Access Token: {}", accessToken.substring(0, Math.min(50, accessToken.length())) + "...");

        // Step 2: 解析JWT token
        JWT jwt = JWTParser.parse(accessToken);
        log.info("JWT解析结果: jwt={}, type={}", jwt != null ? "not null" : "null", jwt != null ? jwt.getClass().getSimpleName() : "N/A");
        assertNotNull(jwt, "JWT token应该能够被解析");

        // Step 3: 验证JWT是SignedJWT
        boolean isSignedJWT = jwt instanceof SignedJWT;
        log.info("JWT是否为SignedJWT: {}", isSignedJWT);
        assertTrue(isSignedJWT, "JWT token应该是SignedJWT");
        SignedJWT signedJWT = (SignedJWT) jwt;

        // Step 4: 验证JWT header
        log.info("JWT Header - Algorithm: {}, KeyID: {}",
                signedJWT.getHeader().getAlgorithm(),
                signedJWT.getHeader().getKeyID());
        assertEquals(JWSAlgorithm.RS256, signedJWT.getHeader().getAlgorithm(), "JWT应该使用RS256算法");
        assertNotNull(signedJWT.getHeader().getKeyID(), "JWT header应该包含kid");

        log.info("JWT token格式验证通过");
    }

    @Test
    void testJwtTokenClaims() throws Exception {
        log.info("测试JWT token claims");

        // Step 1: 创建AKSK并获取token
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("JWT Claims Test Client");
        String accessToken = getAccessToken(clientInfo);

        // Step 2: 解析JWT token
        SignedJWT signedJWT = SignedJWT.parse(accessToken);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        log.info("JWT Claims完整内容: {}", claims.toJSONObject());

        // Step 3: 验证必需的claims
        log.info("JWT Claims - sub: {}, expected: {}", claims.getSubject(), clientInfo.getClientId());
        assertNotNull(claims.getSubject(), "JWT应该包含sub claim");
        assertEquals(clientInfo.getClientId(), claims.getSubject(), "sub claim应该是clientId");

        log.info("JWT Claims - iss: {}", claims.getIssuer());
        assertNotNull(claims.getIssuer(), "JWT应该包含iss claim");

        log.info("JWT Claims - exp: {}", claims.getExpirationTime());
        assertNotNull(claims.getExpirationTime(), "JWT应该包含exp claim");

        log.info("JWT Claims - iat: {}", claims.getIssueTime());
        assertNotNull(claims.getIssueTime(), "JWT应该包含iat claim");

        // Step 4: 验证exp时间在未来
        Date now = new Date();
        log.info("当前时间: {}, exp时间: {}, exp是否在未来: {}",
                now, claims.getExpirationTime(), claims.getExpirationTime().after(now));
        assertTrue(claims.getExpirationTime().after(now), "JWT的exp时间应该在未来");

        // Step 5: 验证scope
        Object scopeClaim = claims.getClaim("scope");
        log.info("JWT Claims - scope: {}", scopeClaim);
        assertNotNull(scopeClaim, "JWT应该包含scope claim");

        // Step 6: 验证client_type
        String clientType = claims.getStringClaim("client_type");
        log.info("JWT Claims - client_type: {}", clientType);
        assertNotNull(clientType, "JWT应该包含client_type claim");
        assertEquals("platform", clientType, "平台级AKSK的client_type应该是platform");

        log.info("JWT token claims验证通过");
    }

    @Test
    void testUserLevelAkskJwtClaims() throws Exception {
        log.info("测试用户级AKSK的JWT token claims");

        // Step 1: 创建用户级AKSK并获取token
        String userId = "10086";
        String username = "zhangsan";
        ClientInfoResponse clientInfo = clientManagementService.createUserClient(userId, username, "User JWT Test Client");
        String accessToken = getAccessToken(clientInfo);

        // Step 2: 解析JWT token
        SignedJWT signedJWT = SignedJWT.parse(accessToken);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        log.info("用户级AKSK JWT Claims完整内容: {}", claims.toJSONObject());

        // Step 3: 验证client_type
        String clientType = claims.getStringClaim("client_type");
        log.info("JWT Claims - client_type: {}", clientType);
        assertNotNull(clientType, "JWT应该包含client_type claim");
        assertEquals("user", clientType, "用户级AKSK的client_type应该是user");

        // Step 4: 验证user_id
        String userIdClaim = claims.getStringClaim("user_id");
        log.info("JWT Claims - user_id: {}", userIdClaim);
        assertNotNull(userIdClaim, "用户级AKSK的JWT应该包含user_id claim");
        assertEquals(userId, userIdClaim, "user_id应该匹配创建时的userId");

        // Step 5: 验证username
        String usernameClaim = claims.getStringClaim("username");
        log.info("JWT Claims - username: {}", usernameClaim);
        assertNotNull(usernameClaim, "用户级AKSK的JWT应该包含username claim");
        assertEquals(username, usernameClaim, "username应该匹配创建时的username");

        log.info("用户级AKSK的JWT token claims验证通过");
    }

    @Test
    void testSecurityContextInJwtClaims() throws Exception {
        log.info("测试security_context在JWT token claims中");

        // Step 1: 创建平台级AKSK
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Security Context Test Client");
        log.info("创建AKSK成功 - ClientId: {}", clientInfo.getClientId());

        // Step 2: 构建security_context JSON
        String securityContext = "{\"user_id\":\"10086\",\"tenant_id\":\"tenant-123\"}";
        log.info("构建security_context: {}", securityContext);

        // Step 3: 在token请求中传递security_context参数
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");
        body.add("security_context", securityContext); // 添加security_context参数

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        log.info("请求Token - URL: {}, ClientId: {}, security_context: {}",
                tokenUrl, clientInfo.getClientId(), securityContext);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        log.info("Token响应 - Status: {}, Body: {}", response.getStatusCode(), response.getBody());

        // Step 4: 验证Token响应成功
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        String accessToken = (String) response.getBody().get("access_token");
        assertNotNull(accessToken, "access_token不应为空");

        // Step 5: 解析JWT并验证security_context claim
        SignedJWT signedJWT = SignedJWT.parse(accessToken);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        log.info("JWT Claims完整内容: {}", claims.toJSONObject());

        // Step 6: 验证security_context claim存在且值正确
        String securityContextClaim = claims.getStringClaim("security_context");
        log.info("JWT Claims - security_context: {}", securityContextClaim);

        assertNotNull(securityContextClaim, "JWT应该包含security_context claim");
        assertEquals(securityContext, securityContextClaim, "security_context值应该匹配");

        log.info("security_context在JWT token claims中验证通过");
    }

    /**
     * 辅助方法：获取access token
     */
    private String getAccessToken(ClientInfoResponse clientInfo) {
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        return (String) response.getBody().get("access_token");
    }
}
