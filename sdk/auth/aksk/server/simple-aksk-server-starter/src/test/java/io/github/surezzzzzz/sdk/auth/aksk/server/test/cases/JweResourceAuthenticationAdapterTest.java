package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import com.nimbusds.jwt.JWTClaimsSet;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.resourceserver.JweResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskEffectiveAuthorizationResult;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.AkskEffectiveAuthorizationService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2RegisteredClientEntityService;
import io.github.surezzzzzz.sdk.auth.aksk.server.token.JweJwtDecoder;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationOutcome;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.BearerResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AKSK Server 自身资源认证的令牌状态和继承授权测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class JweResourceAuthenticationAdapterTest {

    private static final ResourceAuthenticationSourceId SOURCE_ID = new ResourceAuthenticationSourceId(
            AkskConstant.RESOURCE_AUTHENTICATION_SOURCE_ID);
    private static final String TOKEN_VALUE = "jwe-token-value";
    private static final String CLIENT_ID = "AKU-owner";
    private static final String OWNER_SUBJECT_ID = "human-1001";

    @Test
    void shouldRejectRevokedTokenBeforeResolvingAuthorization() {
        Instant issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(300L);
        ApplicationAuthorizationContext authorization = authorization(ApplicationAuthorizationSubjectType.SERVICE, CLIENT_ID);
        JweJwtDecoder decoder = mock(JweJwtDecoder.class);
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        CachedOAuth2RegisteredClientEntityService cachedClientEntityService = activeClientEntityService();
        AkskEffectiveAuthorizationService effectiveService = mock(AkskEffectiveAuthorizationService.class);
        when(decoder.decode(TOKEN_VALUE)).thenReturn(claims(authorization, issuedAt, expiresAt,
                AkskAuthorizationMode.STATIC_LEGACY, null, null, null, null));
        OAuth2Authorization revokedAuthorization = storedAuthorization(issuedAt, expiresAt, true);
        when(authorizationService.findByToken(eq(TOKEN_VALUE), any())).thenReturn(revokedAuthorization);
        JweResourceAuthenticationAdapter adapter = new JweResourceAuthenticationAdapter(
                decoder, authorizationService, cachedClientEntityService, effectiveService);

        io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult result =
                adapter.authenticate(new BearerResourceCredential(SOURCE_ID, TOKEN_VALUE));

        assertEquals(ResourceAuthenticationOutcome.REJECTED, result.getOutcome());
        assertEquals(ResourceAuthenticationFailureCategory.TOKEN_INACTIVE, result.getFailureCategory());
        verify(effectiveService, never()).resolve(any(), any(), any());
    }

    @Test
    void shouldAuthenticateOwnerInheritedTokenAsHuman() {
        Instant issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(300L);
        ApplicationAuthorizationContext authorization = authorization(ApplicationAuthorizationSubjectType.HUMAN, OWNER_SUBJECT_ID);
        JweJwtDecoder decoder = mock(JweJwtDecoder.class);
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        CachedOAuth2RegisteredClientEntityService cachedClientEntityService = activeClientEntityService();
        AkskEffectiveAuthorizationService effectiveService = mock(AkskEffectiveAuthorizationService.class);
        when(decoder.decode(TOKEN_VALUE)).thenReturn(claims(authorization, issuedAt, expiresAt,
                AkskAuthorizationMode.OWNER_INHERITED, Long.valueOf(21L), Long.valueOf(31L),
                Long.valueOf(41L), "iam"));
        when(authorizationService.findByToken(eq(TOKEN_VALUE), any())).thenReturn(
                storedAuthorization(issuedAt, expiresAt, false));
        when(effectiveService.resolve(CLIENT_ID, issuedAt, expiresAt)).thenReturn(
                new AkskEffectiveAuthorizationResult(authorization, AkskOwnerAuthorizationMode.OWNER_INHERITED,
                        Long.valueOf(21L), Long.valueOf(31L), Long.valueOf(41L), "iam", OWNER_SUBJECT_ID,
                        Long.valueOf(51L), Long.valueOf(61L)));
        JweResourceAuthenticationAdapter adapter = new JweResourceAuthenticationAdapter(
                decoder, authorizationService, cachedClientEntityService, effectiveService);

        io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult result =
                adapter.authenticate(new BearerResourceCredential(SOURCE_ID, TOKEN_VALUE));

        assertEquals(ResourceAuthenticationOutcome.AUTHENTICATED, result.getOutcome());
        assertEquals(ResourceSubjectType.HUMAN, result.getPrincipal().getSubjectType());
        assertEquals(OWNER_SUBJECT_ID, result.getPrincipal().getSubjectId());
    }

    @Test
    void shouldAuthenticateWhenSignedClaimsAndStoredRecordUseDifferentTimestampPrecision() {
        Instant issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(300L);
        ApplicationAuthorizationContext authorization = authorization(ApplicationAuthorizationSubjectType.SERVICE, CLIENT_ID);
        JweJwtDecoder decoder = mock(JweJwtDecoder.class);
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        CachedOAuth2RegisteredClientEntityService cachedClientEntityService = activeClientEntityService();
        AkskEffectiveAuthorizationService effectiveService = mock(AkskEffectiveAuthorizationService.class);
        when(decoder.decode(TOKEN_VALUE)).thenReturn(claims(authorization, issuedAt, expiresAt,
                AkskAuthorizationMode.STATIC_LEGACY, null, null, null, null));
        when(authorizationService.findByToken(eq(TOKEN_VALUE), any())).thenReturn(
                storedAuthorization(issuedAt.plusSeconds(1L), expiresAt.plusSeconds(1L), false));
        when(effectiveService.resolve(CLIENT_ID, issuedAt, expiresAt)).thenReturn(
                new AkskEffectiveAuthorizationResult(authorization, AkskOwnerAuthorizationMode.STATIC_LEGACY,
                        null, null, null, null, null));
        JweResourceAuthenticationAdapter adapter = new JweResourceAuthenticationAdapter(
                decoder, authorizationService, cachedClientEntityService, effectiveService);

        io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult result =
                adapter.authenticate(new BearerResourceCredential(SOURCE_ID, TOKEN_VALUE));

        assertEquals(ResourceAuthenticationOutcome.AUTHENTICATED, result.getOutcome());
        assertEquals(ResourceSubjectType.SERVICE, result.getPrincipal().getSubjectType());
    }

    @Test
    void shouldRejectOwnerInheritedTokenWhenEpochChanged() {
        Instant issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(300L);
        ApplicationAuthorizationContext authorization = authorization(ApplicationAuthorizationSubjectType.HUMAN, OWNER_SUBJECT_ID);
        JweJwtDecoder decoder = mock(JweJwtDecoder.class);
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        CachedOAuth2RegisteredClientEntityService cachedClientEntityService = activeClientEntityService();
        AkskEffectiveAuthorizationService effectiveService = mock(AkskEffectiveAuthorizationService.class);
        when(decoder.decode(TOKEN_VALUE)).thenReturn(claims(authorization, issuedAt, expiresAt,
                AkskAuthorizationMode.OWNER_INHERITED, Long.valueOf(21L), Long.valueOf(30L),
                Long.valueOf(41L), "iam"));
        when(authorizationService.findByToken(eq(TOKEN_VALUE), any())).thenReturn(
                storedAuthorization(issuedAt, expiresAt, false));
        when(effectiveService.resolve(CLIENT_ID, issuedAt, expiresAt)).thenReturn(
                new AkskEffectiveAuthorizationResult(authorization, AkskOwnerAuthorizationMode.OWNER_INHERITED,
                        Long.valueOf(21L), Long.valueOf(31L), Long.valueOf(41L), "iam", OWNER_SUBJECT_ID,
                        Long.valueOf(51L), Long.valueOf(61L)));
        JweResourceAuthenticationAdapter adapter = new JweResourceAuthenticationAdapter(
                decoder, authorizationService, cachedClientEntityService, effectiveService);

        io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult result =
                adapter.authenticate(new BearerResourceCredential(SOURCE_ID, TOKEN_VALUE));

        assertEquals(ResourceAuthenticationOutcome.REJECTED, result.getOutcome());
        assertEquals(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID, result.getFailureCategory());
    }

    @Test
    void shouldRejectDeletedClientBeforeReadingAuthorization() {
        Instant issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(300L);
        ApplicationAuthorizationContext authorization = authorization(ApplicationAuthorizationSubjectType.SERVICE, CLIENT_ID);
        JweJwtDecoder decoder = mock(JweJwtDecoder.class);
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        CachedOAuth2RegisteredClientEntityService cachedClientEntityService =
                mock(CachedOAuth2RegisteredClientEntityService.class);
        AkskEffectiveAuthorizationService effectiveService = mock(AkskEffectiveAuthorizationService.class);
        when(decoder.decode(TOKEN_VALUE)).thenReturn(claims(authorization, issuedAt, expiresAt,
                AkskAuthorizationMode.STATIC_LEGACY, null, null, null, null));
        when(cachedClientEntityService.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());
        JweResourceAuthenticationAdapter adapter = new JweResourceAuthenticationAdapter(
                decoder, authorizationService, cachedClientEntityService, effectiveService);

        io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult result =
                adapter.authenticate(new BearerResourceCredential(SOURCE_ID, TOKEN_VALUE));

        assertEquals(ResourceAuthenticationOutcome.REJECTED, result.getOutcome());
        assertEquals(ResourceAuthenticationFailureCategory.TOKEN_INACTIVE, result.getFailureCategory());
        verify(authorizationService, never()).findByToken(any(), any());
        verify(effectiveService, never()).resolve(any(), any(), any());
    }

    private CachedOAuth2RegisteredClientEntityService activeClientEntityService() {
        CachedOAuth2RegisteredClientEntityService service = mock(CachedOAuth2RegisteredClientEntityService.class);
        OAuth2RegisteredClientEntity client = new OAuth2RegisteredClientEntity();
        client.setClientId(CLIENT_ID);
        client.setEnabled(true);
        when(service.findByClientId(CLIENT_ID)).thenReturn(Optional.of(client));
        return service;
    }

    private OAuth2Authorization storedAuthorization(Instant issuedAt, Instant expiresAt, boolean revoked) {
        RegisteredClient client = RegisteredClient.withId("registered-client-id")
                .clientId(CLIENT_ID)
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                TOKEN_VALUE, issuedAt, expiresAt, Collections.singleton("read"));
        if (!revoked) {
            return OAuth2Authorization.withRegisteredClient(client)
                    .principalName(CLIENT_ID)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .token(accessToken)
                    .build();
        }
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName(CLIENT_ID)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .token(accessToken, metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME,
                        Boolean.TRUE))
                .build();
    }

    private JWTClaimsSet claims(ApplicationAuthorizationContext authorization, Instant issuedAt, Instant expiresAt,
                                AkskAuthorizationMode mode, Long targetApplicationId, Long ownerEpoch,
                                Long applicationEpoch, String ownerSourceId) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .claim(JwtClaimConstant.CLIENT_ID, CLIENT_ID)
                .claim(JwtClaimConstant.APPLICATION_AUTHORIZATION,
                        ApplicationAuthorizationContextClaimMapper.toClaim(authorization))
                .claim(SimpleAkskServerConstant.JWT_CLAIM_AUTHORIZATION_MODE, mode.name())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
        if (targetApplicationId != null) {
            builder.claim(SimpleAkskServerConstant.JWT_CLAIM_TARGET_APPLICATION_ID, targetApplicationId)
                    .claim(SimpleAkskServerConstant.JWT_CLAIM_OWNER_SECURITY_EPOCH, ownerEpoch)
                    .claim(SimpleAkskServerConstant.JWT_CLAIM_APPLICATION_AUTHORIZATION_EPOCH, applicationEpoch)
                    .claim(SimpleAkskServerConstant.JWT_CLAIM_OWNER_INHERITED_ACCESS_EPOCH, Long.valueOf(51L))
                    .claim(SimpleAkskServerConstant.JWT_CLAIM_PROJECTION_ACCESS_EPOCH, Long.valueOf(61L))
                    .claim(SimpleAkskServerConstant.JWT_CLAIM_OWNER_SOURCE_ID, ownerSourceId);
        }
        return builder.build();
    }

    private ApplicationAuthorizationContext authorization(ApplicationAuthorizationSubjectType subjectType,
                                                          String subjectId) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return new ApplicationAuthorizationContext(SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION, subjectType, subjectId, "aksk-server", true,
                Collections.singletonList("operator"), Collections.singletonList("clients"),
                Collections.singletonList("akskClient:read"), null, 1L, "manifest-1", "digest-1",
                now.minusSeconds(1L), now.plusSeconds(600L));
    }
}
