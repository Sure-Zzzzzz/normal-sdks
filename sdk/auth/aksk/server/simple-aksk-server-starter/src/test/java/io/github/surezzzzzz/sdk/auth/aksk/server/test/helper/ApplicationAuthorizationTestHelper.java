package io.github.surezzzzzz.sdk.auth.aksk.server.test.helper;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

/**
 * 创建需要签发令牌的测试应用授权投影。
 *
 * @author surezzzzzz
 */
public final class ApplicationAuthorizationTestHelper {

    private static final String APPLICATION_CODE = "aksk-server";
    private static final String EMPTY_JSON_ARRAY = "[]";
    private static final String MANAGEMENT_PERMISSIONS =
            "[\"akskClient:create\",\"akskClient:read\",\"akskClient:update\",\"akskClient:delete\","
                    + "\"akskToken:read\",\"akskToken:update\",\"akskToken:delete\","
                    + "\"akskApplicationAuthorization:create\",\"akskApplicationAuthorization:read\","
                    + "\"akskApplicationAuthorization:update\",\"akskApplicationAuthorization:revoke\"]";
    private static final String MANIFEST_VERSION = "test-manifest";
    private static final String MANIFEST_DIGEST = "test-manifest-digest";

    private ApplicationAuthorizationTestHelper() {
    }

    public static void grantManagementAuthorization(AkskApplicationAuthorizationRepository repository,
                                                    ClientInfoResponse client) {
        grantManagementAuthorization(repository, client.getClientId());
    }

    public static void grantManagementAuthorization(AkskApplicationAuthorizationRepository repository,
                                                    String clientId) {
        if (repository.findByClientId(clientId).isPresent()) {
            return;
        }
        AkskApplicationAuthorizationEntity authorization = new AkskApplicationAuthorizationEntity();
        authorization.setClientId(clientId);
        authorization.setApplicationCode(APPLICATION_CODE);
        authorization.setAdmitted(Boolean.TRUE);
        authorization.setRolesJson(EMPTY_JSON_ARRAY);
        authorization.setPagePermissionsJson(EMPTY_JSON_ARRAY);
        authorization.setApiPermissionsJson(MANAGEMENT_PERMISSIONS);
        authorization.setDataGrantDocumentJson(AkskApplicationAuthorizationJsonCodec.writeDataGrantDocument(
                new DataGrantDocument(
                        SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION,
                        Arrays.asList(
                                new DataGrant("akskClient", Arrays.asList("create", "read", "update", "delete"),
                                        true, Collections.emptyList()),
                                new DataGrant("akskToken", Arrays.asList("read", "update", "delete"),
                                        true, Collections.emptyList()),
                                new DataGrant("akskApplicationAuthorization",
                                        Arrays.asList("create", "read", "update", "revoke"),
                                        true, Collections.emptyList())
                        ))));
        authorization.setAuthorizationVersion(1L);
        authorization.setManifestVersion(MANIFEST_VERSION);
        authorization.setManifestDigest(MANIFEST_DIGEST);
        authorization.setEnabled(Boolean.TRUE);
        Instant now = Instant.now();
        authorization.setCreatedAt(now);
        authorization.setUpdatedAt(now);
        repository.save(authorization);
    }
}
