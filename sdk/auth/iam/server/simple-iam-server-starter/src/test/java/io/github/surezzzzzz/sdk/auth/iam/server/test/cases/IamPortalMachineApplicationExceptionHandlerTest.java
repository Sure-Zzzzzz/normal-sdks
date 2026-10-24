package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.controller.rest.IamPortalMachineApplicationExceptionHandler;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalMachineDirectoryUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IamPortalMachineApplicationExceptionHandlerTest {

    @Test
    void mapsCorruptedStoredProjectionToInternalServerErrorInsteadOfUnavailable() {
        IamPortalMachineApplicationExceptionHandler handler =
                new IamPortalMachineApplicationExceptionHandler();

        ResponseEntity<?> response = handler.corruptedProjection(
                new IamPortalMachineDirectoryUnavailableException("内部测试原因"));

        assertEquals(500, response.getStatusCodeValue());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertNotNull(response.getBody());
    }

    @Test
    void keepsDependencyUnavailableAsServiceUnavailable() {
        IamPortalMachineApplicationExceptionHandler handler =
                new IamPortalMachineApplicationExceptionHandler();

        ResponseEntity<?> response = handler.business(new SimpleIamServerException(
                ErrorCode.OPEN_ROLE_UNAVAILABLE, "外部依赖暂不可用"));

        assertEquals(503, response.getStatusCodeValue());
    }
}
