package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.TrustedApplicationBuiltInResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TrustedApplicationBuiltInResolverTest {

    @Test
    void defaultManifestClassifiesAkskAsBuiltInWithoutDatabaseFlag() {
        IamTrustedApplicationEntity application = new IamTrustedApplicationEntity();
        application.setApplicationCode("aksk");
        application.setBuiltIn(Boolean.FALSE);

        TrustedApplicationBuiltInResolver resolver = new TrustedApplicationBuiltInResolver(
                mock(IamTrustedApplicationRepository.class), new SimpleIamServerProperties());

        assertTrue(resolver.isBuiltIn(application));
    }
}
