package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.provider.JwtKeyProvider;
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

import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT Token验证测试（2.0.0 JWE 格式）
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

    @Autowired
    private SimpleAkskServerProperties properties;

    @Autowired
    private JwtKeyProvider jwtKeyProvider;

    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("清理Redis测试数据: {} 条", keys.size());
        }
        log.info("测试数据清理完成");
    }

    @Test
    void testJweTokenFormat() throws Exception {
        log.info("测试 JWE token 格式");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("JWT Format Test Client");
        String accessToken = getAccessToken(clientInfo);

        log.info("获取到 Access Token: {}...", accessToken.substring(0, 30));

        // JWE 格式：5段，用"."分隔
        String[] parts = accessToken.split("\\.");
        assertEquals(5, parts.length, "JWE token 应该有 5 段（用 . 分隔）");

        // 解析 JWE
        JWEObject jweObject = JWEObject.parse(accessToken);
        log.info("JWE 解析成功，Header: {}", jweObject.getHeader().toJSONObject());

        // 验证 JWE header
        assertEquals("A256GCMKW", jweObject.getHeader().getAlgorithm().toString());
        assertEquals("A256GCM", jweObject.getHeader().getEncryptionMethod().toString());
        assertEquals("JWT", jweObject.getHeader().getContentType());

        log.info("JWE token 格式验证通过");
    }

    @Test
    void testJweTokenDecrypt() throws Exception {
        log.info("测试 JWE token 解密");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("JWT Decrypt Test Client");
        String accessToken = getAccessToken(clientInfo);

        // 解密 JWE → JWS
        byte[] aesKeyBytes = Base64.getDecoder().decode(properties.getJwt().getEncryptionKey());
        OctetSequenceKey aesKey = new OctetSequenceKey.Builder(aesKeyBytes).build();

        JWEObject jweObject = JWEObject.parse(accessToken);
        jweObject.decrypt(new AESDecrypter(aesKey));

        String jwsCompact = jweObject.getPayload().toString();
        log.info("JWE 解密成功，JWS: {}...", jwsCompact.substring(0, 50));

        // 验证 JWS（用 RSA 公钥验签）
        SignedJWT signedJWT = SignedJWT.parse(jwsCompact);
        RSASSAVerifier verifier = new RSASSAVerifier(jwtKeyProvider.getPublicKey());
        boolean verified = signedJWT.verify(verifier);
        assertTrue(verified, "JWS 签名应该验证通过");

        log.info("JWE token 解密验证通过");
    }

    @Test
    void testJweTokenClaims() throws Exception {
        log.info("测试 JWE token claims");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("JWT Claims Test Client");
        String accessToken = getAccessToken(clientInfo);

        // 解密 JWE → JWS → claims
        byte[] aesKeyBytes = Base64.getDecoder().decode(properties.getJwt().getEncryptionKey());
        JWEObject jweObject = JWEObject.parse(accessToken);
        jweObject.decrypt(new AESDecrypter(new OctetSequenceKey.Builder(aesKeyBytes).build()));

        SignedJWT signedJWT = SignedJWT.parse(jweObject.getPayload().toString());
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        log.info("JWT Claims: {}", claims.toJSONObject());

        // 验证基础 claims
        assertNotNull(claims.getSubject());
        assertEquals(clientInfo.getClientId(), claims.getSubject());
        assertNotNull(claims.getIssuer());
        assertNotNull(claims.getExpirationTime());
        assertNotNull(claims.getIssueTime());
        assertTrue(claims.getExpirationTime().after(new Date()));

        // 验证 scope
        Object scopeClaim = claims.getClaim("scope");
        assertNotNull(scopeClaim, "JWT 应该包含 scope claim");

        // 验证 client_type
        String clientType = claims.getStringClaim("client_type");
        assertNotNull(clientType);
        assertEquals("platform", clientType);

        log.info("JWE token claims 验证通过");
    }

    @Test
    void testUserLevelAkskJweClaims() throws Exception {
        log.info("测试用户级 AKSK 的 JWE token claims");

        String userId = "10086";
        String username = "zhangsan";
        ClientInfoResponse clientInfo = clientManagementService.createUserClient(userId, username, "User JWT Test Client");
        String accessToken = getAccessToken(clientInfo);

        byte[] aesKeyBytes = Base64.getDecoder().decode(properties.getJwt().getEncryptionKey());
        JWEObject jweObject = JWEObject.parse(accessToken);
        jweObject.decrypt(new AESDecrypter(new OctetSequenceKey.Builder(aesKeyBytes).build()));

        SignedJWT signedJWT = SignedJWT.parse(jweObject.getPayload().toString());
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        log.info("用户级 AKSK JWT Claims: {}", claims.toJSONObject());

        // 验证 client_type = user
        String clientType = claims.getStringClaim("client_type");
        assertEquals("user", clientType);

        // 验证 user_id
        String userIdClaim = claims.getStringClaim("user_id");
        assertEquals(userId, userIdClaim);

        // 验证 username
        String usernameClaim = claims.getStringClaim("username");
        assertEquals(username, usernameClaim);

        log.info("用户级 AKSK 的 JWE token claims 验证通过");
    }

    @Test
    void testSecurityContextInJweClaims() throws Exception {
        log.info("测试 security_context 在 JWE token claims 中");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Security Context Test Client");

        String securityContext = "{\"user_id\":\"10086\",\"tenant_id\":\"tenant-123\"}";

        String tokenUrl = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");
        body.add("security_context", securityContext);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String accessToken = (String) response.getBody().get("access_token");

        // 解密 JWE
        byte[] aesKeyBytes = Base64.getDecoder().decode(properties.getJwt().getEncryptionKey());
        JWEObject jweObject = JWEObject.parse(accessToken);
        jweObject.decrypt(new AESDecrypter(new OctetSequenceKey.Builder(aesKeyBytes).build()));

        SignedJWT signedJWT = SignedJWT.parse(jweObject.getPayload().toString());
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        // 验证 security_context
        String scClaim = claims.getStringClaim("security_context");
        assertNotNull(scClaim);
        assertEquals(securityContext, scClaim);

        log.info("security_context 在 JWE token claims 中验证通过");
    }

    @Test
    void testTamperedJweTokenIsRejected() throws Exception {
        log.info("测试篡改 JWE token 后 /api 接口返回 401");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Tamper Test Client");
        // 先获取一个有效 token 用于创建 /api/client scope 的 token
        String validToken = getAccessToken(clientInfo);

        // 篡改：把 JWE 第三段（加密密钥）替换为随机字符串
        String[] parts = validToken.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".AAAAAAAAAAAAAAAA." + parts[3] + "." + parts[4];

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tampered);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/client?type=platform",
                HttpMethod.GET, request, String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "篡改的 JWE token 应返回 401");

        log.info("✓ 篡改 JWE token 被正确拒绝");
    }

    @Test
    void testSecurityContextTooLargeReturns400() {
        log.info("测试 security_context 超过大小限制时返回 400");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Security Context Size Test Client");

        // 构造超过 4096 字节的 security_context
        String largeContext = "{\"data\":\"" + "x".repeat(4200) + "\"}";

        String tokenUrl = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientInfo.getClientId(), clientInfo.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("security_context", largeContext);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                tokenUrl, new HttpEntity<>(body, headers), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "security_context 超限应返回 400");
        assertNotNull(response.getBody());
        assertEquals("security_context_too_large", response.getBody().get("error"),
                "error 字段应为 security_context_too_large");

        log.info("✓ security_context 超限正确返回 400");
    }

    @Test
    void testInvalidJweTokenReturns401OnApiAccess() {
        log.info("测试使用完全无效的 JWE token 访问 /api 返回 401");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("this.is.not.a.valid.jwe.token");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/client?type=platform",
                HttpMethod.GET, request, String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "无效 JWE token 应返回 401");

        log.info("✓ 无效 JWE token 正确返回 401");
    }

    /**
     * 辅助方法：获取 access token
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