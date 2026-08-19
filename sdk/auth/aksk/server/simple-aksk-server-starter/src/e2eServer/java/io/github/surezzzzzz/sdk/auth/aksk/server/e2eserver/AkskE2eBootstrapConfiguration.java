package io.github.surezzzzzz.sdk.auth.aksk.server.e2eserver;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

/**
 * 初始化 IAM 与 AKSK 协作 E2E 的真实服务主体。
 *
 * @author surezzzzzz
 */
@Configuration
public class AkskE2eBootstrapConfiguration {

    private static final String ALLOWED_CLIENT_NAME = "IAM AKSK E2E Allowed Service";
    private static final String DENIED_CLIENT_NAME = "IAM AKSK E2E Denied Service";
    private static final String INTROSPECTION_CLIENT_NAME = "IAM AKSK E2E Resource Introspection Client";
    private static final String APPLICATION_CODE = "iam-aksk-e2e-resource";
    private static final String RESOURCE_READ_API_PERMISSION = "resource.read";
    private static final String EMPTY_JSON_ARRAY = "[]";
    private static final String RESOURCE_READ_API_PERMISSION_JSON = "[\"resource.read\"]";
    private static final String MANIFEST_VERSION = "iam-aksk-e2e-v1";
    private static final String MANIFEST_DIGEST = "iam-aksk-e2e";
    private static final String ORDER_RESOURCE = "order";
    private static final String READ_ACTION = "read";
    private static final String TENANT_ID_DIMENSION = "tenantId";
    private static final String DEPARTMENT_ID_DIMENSION = "departmentId";
    private static final String ALLOWED_TENANT_ID = "tenant-a";
    private static final String ALLOWED_DEPARTMENT_ID = "department-a";
    private static final String SECOND_TENANT_ID = "tenant-b";
    private static final String SECOND_DEPARTMENT_ID = "department-b";
    private static final String ALLOWED_CLIENT_ID_PROPERTY = "aksk.allowed.client-id";
    private static final String ALLOWED_CLIENT_SECRET_PROPERTY = "aksk.allowed.client-secret";
    private static final String DENIED_CLIENT_ID_PROPERTY = "aksk.denied.client-id";
    private static final String DENIED_CLIENT_SECRET_PROPERTY = "aksk.denied.client-secret";
    private static final String INTROSPECTION_CLIENT_ID_PROPERTY = "aksk.introspection.client-id";
    private static final String INTROSPECTION_CLIENT_SECRET_PROPERTY = "aksk.introspection.client-secret";

    @Bean
    ApplicationRunner akskE2eBootstrap(ClientManagementService clientManagementService,
                                       AkskApplicationAuthorizationRepository authorizationRepository,
                                       @Value("${iam.aksk.e2e.aksk.credentials-file}") String credentialsFile) {
        return arguments -> {
            ClientInfoResponse allowedClient = clientManagementService.createPlatformClient(
                    ALLOWED_CLIENT_NAME, Collections.singletonList(RESOURCE_READ_API_PERMISSION));
            ClientInfoResponse deniedClient = clientManagementService.createPlatformClient(
                    DENIED_CLIENT_NAME, Collections.singletonList(RESOURCE_READ_API_PERMISSION));
            ClientInfoResponse introspectionClient = clientManagementService.createPlatformClient(
                    INTROSPECTION_CLIENT_NAME, Collections.singletonList(RESOURCE_READ_API_PERMISSION));
            authorizationRepository.save(createAuthorization(allowedClient.getClientId(), RESOURCE_READ_API_PERMISSION_JSON,
                    AkskApplicationAuthorizationJsonCodec.writeDataGrantDocument(dataGrantDocument())));
            authorizationRepository.save(createAuthorization(deniedClient.getClientId(), EMPTY_JSON_ARRAY, null));
            authorizationRepository.save(createAuthorization(introspectionClient.getClientId(),
                    RESOURCE_READ_API_PERMISSION_JSON, null));
            writeCredentials(credentialsFile, allowedClient, deniedClient, introspectionClient);
        };
    }

    private static AkskApplicationAuthorizationEntity createAuthorization(String clientId, String apiPermissionsJson,
                                                                         String dataGrantDocumentJson) {
        Instant now = Instant.now();
        AkskApplicationAuthorizationEntity authorization = new AkskApplicationAuthorizationEntity();
        authorization.setClientId(clientId);
        authorization.setApplicationCode(APPLICATION_CODE);
        authorization.setAdmitted(Boolean.TRUE);
        authorization.setRolesJson(EMPTY_JSON_ARRAY);
        authorization.setPagePermissionsJson(EMPTY_JSON_ARRAY);
        authorization.setApiPermissionsJson(apiPermissionsJson);
        authorization.setDataGrantDocumentJson(dataGrantDocumentJson);
        authorization.setAuthorizationVersion(1L);
        authorization.setManifestVersion(MANIFEST_VERSION);
        authorization.setManifestDigest(MANIFEST_DIGEST);
        authorization.setEnabled(Boolean.TRUE);
        authorization.setCreatedAt(now);
        authorization.setUpdatedAt(now);
        return authorization;
    }

    private static DataGrantDocument dataGrantDocument() {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Arrays.asList(restrictedGrant(ALLOWED_TENANT_ID, ALLOWED_DEPARTMENT_ID),
                        restrictedGrant(SECOND_TENANT_ID, SECOND_DEPARTMENT_ID)));
    }

    private static DataGrant restrictedGrant(String tenantId, String departmentId) {
        return new DataGrant(ORDER_RESOURCE, Collections.singletonList(READ_ACTION), false, Arrays.asList(
                new DataConstraint(TENANT_ID_DIMENSION, DataConstraintOperator.IN, Collections.singletonList(tenantId)),
                new DataConstraint(DEPARTMENT_ID_DIMENSION, DataConstraintOperator.IN,
                        Collections.singletonList(departmentId))));
    }

    private static void writeCredentials(String credentialsFile, ClientInfoResponse allowedClient,
                                         ClientInfoResponse deniedClient, ClientInfoResponse introspectionClient)
            throws IOException {
        File file = new File(credentialsFile);
        Properties credentials = loadCredentials(file);
        credentials.setProperty(ALLOWED_CLIENT_ID_PROPERTY, allowedClient.getClientId());
        credentials.setProperty(ALLOWED_CLIENT_SECRET_PROPERTY, allowedClient.getClientSecret());
        credentials.setProperty(DENIED_CLIENT_ID_PROPERTY, deniedClient.getClientId());
        credentials.setProperty(DENIED_CLIENT_SECRET_PROPERTY, deniedClient.getClientSecret());
        credentials.setProperty(INTROSPECTION_CLIENT_ID_PROPERTY, introspectionClient.getClientId());
        credentials.setProperty(INTROSPECTION_CLIENT_SECRET_PROPERTY, introspectionClient.getClientSecret());
        try (FileOutputStream output = new FileOutputStream(file)) {
            credentials.store(output, null);
        }
    }

    private static Properties loadCredentials(File file) throws IOException {
        Properties credentials = new Properties();
        if (!file.exists()) {
            return credentials;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            credentials.load(input);
        }
        return credentials;
    }
}
