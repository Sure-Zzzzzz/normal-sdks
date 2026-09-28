package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientOwnerBindingEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskClientLifecycleCommandRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskClientOwnerBindingRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.*;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * OWNER_INHERITED AKU 自助生命周期的事务集成测试。
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
class AkskSelfServiceLifecycleIntegrationTest {

    private static final String OWNER_SOURCE_ID = "local-iam";
    private static final String OWNER_SUBJECT_ID = "1001";

    @Autowired
    private AkskSelfServiceLifecycleService lifecycleService;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private AkskClientOwnerBindingRepository bindingRepository;

    @Autowired
    private AkskClientLifecycleCommandRepository commandRepository;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private TokenManagementService tokenManagementService;

    private AkskSelfServicePrincipal owner;
    private String clientId;

    @BeforeEach
    void setUp() {
        owner = new AkskSelfServicePrincipal(OWNER_SOURCE_ID, OWNER_SUBJECT_ID, "request-test");
        ClientInfoResponse client = clientManagementService.createUserClient(OWNER_SUBJECT_ID, null, "My AKU");
        clientId = client.getClientId();
        AkskClientOwnerBindingEntity binding = new AkskClientOwnerBindingEntity();
        binding.setClientId(clientId);
        binding.setOwnerSourceId(OWNER_SOURCE_ID);
        binding.setOwnerSubjectId(OWNER_SUBJECT_ID);
        binding.setTargetApplicationId(Long.valueOf(9L));
        binding.setAuthorizationMode(AkskOwnerAuthorizationMode.OWNER_INHERITED);
        binding.setOwnerState(Integer.valueOf(1));
        binding.setLifecycleVersion(Long.valueOf(1L));
        binding.setBindingOrigin("TEST");
        binding.setBoundAt(Instant.now());
        binding.setUpdatedAt(Instant.now());
        bindingRepository.saveAndFlush(binding);
    }

    @AfterEach
    void tearDown() {
        reset(tokenManagementService);
        commandRepository.deleteAll();
        bindingRepository.deleteAll();
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldApplyLifecycleVersionAndRetainTombstoneOnTermination() {
        AkskSelfServiceLifecycleResult renamed = lifecycleService.rename(owner, clientId,
                Long.valueOf(1L), "Renamed AKU");

        assertEquals(AkskSelfServiceLifecycleResult.State.SUCCESS, renamed.getState());
        assertEquals("Renamed AKU", renamed.getClient().getClientName());
        assertEquals(Long.valueOf(2L), renamed.getClient().getLifecycleVersion());

        AkskSelfServiceLifecycleResult stale = lifecycleService.rotateSecret(owner, clientId, Long.valueOf(1L));
        assertEquals(AkskSelfServiceLifecycleResult.State.PRECONDITION_FAILED, stale.getState());

        AkskSelfServiceLifecycleResult rotated = lifecycleService.rotateSecret(owner, clientId, Long.valueOf(2L));
        assertEquals(AkskSelfServiceLifecycleResult.State.SUCCESS, rotated.getState());
        assertNotNull(rotated.getClient().getClientSecret());
        assertEquals(Long.valueOf(3L), rotated.getClient().getLifecycleVersion());
        verify(tokenManagementService).revokeAllByClientId(clientId, TokenEventCause.CLIENT_SECRET_RESET);

        AkskSelfServiceLifecycleResult terminated = lifecycleService.terminate(owner, clientId, Long.valueOf(3L));
        assertEquals(AkskSelfServiceLifecycleResult.State.SUCCESS, terminated.getState());
        assertFalse(clientRepository.findByClientId(clientId).isPresent());
        AkskClientOwnerBindingEntity tombstone = bindingRepository.findByClientId(clientId).orElse(null);
        assertNotNull(tombstone);
        assertEquals(Integer.valueOf(0), tombstone.getOwnerState());
        assertEquals(Long.valueOf(4L), tombstone.getLifecycleVersion());
        verify(tokenManagementService).revokeAllByClientId(eq(clientId), eq(TokenEventCause.CLIENT_DELETED));

        log.info("AKU生命周期已验证：clientId={}，tombstoneVersion={}", clientId, tombstone.getLifecycleVersion());
    }
}
