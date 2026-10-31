package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseSigningProfileController;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LicenseSigningProfileControllerTest {

    private SigningProfileRepository profiles;
    private SigningOperationRepository operations;
    private LicenseSigningProfileController controller;

    @BeforeEach
    void setUp() {
        profiles = mock(SigningProfileRepository.class);
        operations = mock(SigningOperationRepository.class);
        controller = new LicenseSigningProfileController(profiles, operations, null);
    }

    @Test
    void unsupportedStatusFilterIsRejectedBeforeRepositoryRead() {
        assertThatThrownBy(() -> controller.list(
                LicenseIdentityHttpTest.TestRequests.authenticated(),
                TestPlans.allFor("license-signing-profile", "read"), "BOGUS"))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus())
                .isEqualTo(422);
        verifyNoInteractions(profiles, operations);
    }

    @Test
    void detailAndDisposalReadsOutsideCurrentDataScopeAreHiddenAsNotFound() {
        assertNotFound(() -> controller.get("profile-hidden",
                TestPlans.restrictedFor("license-signing-profile", "read", "profileId", "profile-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()));
        assertNotFound(() -> controller.disposalAdvice("profile-hidden",
                TestPlans.restrictedFor("license-signing-profile", "read", "profileId", "profile-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()));
        verifyNoInteractions(profiles, operations);
    }

    private static void assertNotFound(Runnable request) {
        assertThatThrownBy(request::run).isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus()).isEqualTo(404);
    }
}
