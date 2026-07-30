package io.github.surezzzzzz.sdk.oss.s3.support;

import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自定义 CA 信任辅助类测试
 *
 * @author surezzzzzz
 */
class TrustedCaHelperTest {

    private static final String TRUSTED_CA_RESOURCE = "test-ca.txt";
    private static final String DUPLICATE_SUBJECT_CA_RESOURCE = "test-ca-duplicate-subject.txt";
    private static final String AUTH_TYPE = "RSA";

    @Test
    @DisplayName("默认可信根校验通过时不再调用自定义 CA")
    void testDefaultTrustManagerTakesPrecedence() {
        RecordingTrustManager defaultTrustManager = new RecordingTrustManager(false, new X509Certificate[0]);
        RecordingTrustManager customTrustManager = new RecordingTrustManager(true, new X509Certificate[0]);
        TrustedCaHelper.CompositeX509TrustManager trustManager =
                new TrustedCaHelper.CompositeX509TrustManager(defaultTrustManager, customTrustManager);

        assertDoesNotThrow(() -> trustManager.checkServerTrusted(new X509Certificate[0], AUTH_TYPE));
        assertEquals(1, defaultTrustManager.getServerTrustCheckCount(), "默认可信根必须先校验");
        assertEquals(0, customTrustManager.getServerTrustCheckCount(), "默认可信根通过时不得调用自定义 CA");
    }

    @Test
    @DisplayName("默认可信根拒绝时允许自定义 CA 继续校验")
    void testCustomTrustManagerRunsAfterDefaultFailure() {
        RecordingTrustManager defaultTrustManager = new RecordingTrustManager(true, new X509Certificate[0]);
        RecordingTrustManager customTrustManager = new RecordingTrustManager(false, new X509Certificate[0]);
        TrustedCaHelper.CompositeX509TrustManager trustManager =
                new TrustedCaHelper.CompositeX509TrustManager(defaultTrustManager, customTrustManager);

        assertDoesNotThrow(() -> trustManager.checkServerTrusted(new X509Certificate[0], AUTH_TYPE));
        assertEquals(1, defaultTrustManager.getServerTrustCheckCount(), "默认可信根必须先校验");
        assertEquals(1, customTrustManager.getServerTrustCheckCount(), "默认可信根拒绝后必须校验自定义 CA");
    }

    @Test
    @DisplayName("相同 Subject 的不同 CA 不会在 issuer 列表中丢失")
    void testAcceptedIssuersRetainDifferentCertificatesWithSameSubject() throws Exception {
        X509Certificate firstCertificate = loadCertificate(TRUSTED_CA_RESOURCE);
        X509Certificate secondCertificate = loadCertificate(DUPLICATE_SUBJECT_CA_RESOURCE);
        assertEquals(firstCertificate.getSubjectX500Principal(), secondCertificate.getSubjectX500Principal(),
                "测试夹具必须使用相同 Subject");

        TrustedCaHelper.CompositeX509TrustManager trustManager = new TrustedCaHelper.CompositeX509TrustManager(
                new RecordingTrustManager(false, new X509Certificate[]{firstCertificate}),
                new RecordingTrustManager(false, new X509Certificate[]{secondCertificate})
        );

        X509Certificate[] acceptedIssuers = trustManager.getAcceptedIssuers();
        assertEquals(2, acceptedIssuers.length, "相同 Subject 的不同 CA 都必须保留");
        assertNotNull(acceptedIssuers[0], "第一个 issuer 不得为空");
        assertNotNull(acceptedIssuers[1], "第二个 issuer 不得为空");
    }

    private X509Certificate loadCertificate(String resourceName) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(inputStream, "测试证书资源必须存在");
            return (X509Certificate) CertificateFactory
                    .getInstance(S3ClientConstant.CERTIFICATE_TYPE_X509)
                    .generateCertificate(inputStream);
        }
    }

    private static final class RecordingTrustManager implements X509TrustManager {

        private final boolean rejectServerTrust;
        private final X509Certificate[] acceptedIssuers;
        private int serverTrustCheckCount;

        private RecordingTrustManager(boolean rejectServerTrust, X509Certificate[] acceptedIssuers) {
            this.rejectServerTrust = rejectServerTrust;
            this.acceptedIssuers = acceptedIssuers;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            serverTrustCheckCount++;
            if (rejectServerTrust) {
                throw new CertificateException("拒绝服务端证书");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return acceptedIssuers;
        }

        private int getServerTrustCheckCount() {
            return serverTrustCheckCount;
        }
    }
}
