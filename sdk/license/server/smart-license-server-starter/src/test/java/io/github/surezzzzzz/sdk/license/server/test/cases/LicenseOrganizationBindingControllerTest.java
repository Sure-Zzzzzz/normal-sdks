package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.license.server.controller.LicenseOrganizationBindingController;
import io.github.surezzzzzz.sdk.license.server.repository.CustomerRepository;
import io.github.surezzzzzz.sdk.license.server.repository.OrganizationBindingRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LicenseOrganizationBindingControllerTest {

    @Test
    void readOutsideCurrentDataScopeIsHiddenAsNotFound() {
        CustomerRepository customers = mock(CustomerRepository.class);
        OrganizationBindingRepository bindings = mock(OrganizationBindingRepository.class);
        LicenseOrganizationBindingController controller =
                new LicenseOrganizationBindingController(bindings, customers);

        assertThatThrownBy(() -> controller.getBinding("customer-hidden",
                TestPlans.restrictedFor("license-customer", "read", "customerId", "customer-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus()).isEqualTo(404);
        verifyNoInteractions(customers, bindings);
    }
}
