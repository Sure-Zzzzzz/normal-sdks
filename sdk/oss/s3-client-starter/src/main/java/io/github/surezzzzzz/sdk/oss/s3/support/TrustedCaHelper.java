package io.github.surezzzzzz.sdk.oss.s3.support;

import io.github.surezzzzzz.sdk.oss.s3.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.oss.s3.constant.S3ClientConstant;
import io.github.surezzzzzz.sdk.oss.s3.exception.client.S3ClientPropertiesInvalidException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * S3 自定义 CA 信任辅助类
 *
 * @author surezzzzzz
 */
public final class TrustedCaHelper {

    private static final String TRUST_MANAGER_ALGORITHM = TrustManagerFactory.getDefaultAlgorithm();

    private TrustedCaHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析 S3 endpoint。
     *
     * @param endpoint S3 endpoint
     * @return endpoint URI
     */
    public static URI parseEndpoint(String endpoint) {
        if (StringUtils.isBlank(endpoint)) {
            throw invalidEndpoint(endpoint);
        }
        try {
            URI endpointUri = new URI(endpoint);
            if (!endpointUri.isAbsolute()
                    || StringUtils.isBlank(endpointUri.getHost())
                    || endpointUri.getUserInfo() != null
                    || endpointUri.getQuery() != null
                    || endpointUri.getFragment() != null
                    || (endpointUri.getPort() != -1
                    && (endpointUri.getPort() < S3ClientConstant.MIN_ENDPOINT_PORT
                    || endpointUri.getPort() > S3ClientConstant.MAX_ENDPOINT_PORT))
                    || (!S3ClientConstant.PROTOCOL_HTTP.equalsIgnoreCase(endpointUri.getScheme())
                    && !S3ClientConstant.PROTOCOL_HTTPS.equalsIgnoreCase(endpointUri.getScheme()))) {
                throw invalidEndpoint(endpoint);
            }
            return endpointUri;
        } catch (URISyntaxException ex) {
            throw invalidEndpoint(endpoint);
        }
    }

    /**
     * 判断 endpoint 是否使用 HTTPS。
     *
     * @param endpointUri endpoint URI
     * @return 是否使用 HTTPS
     */
    public static boolean isHttps(URI endpointUri) {
        return S3ClientConstant.PROTOCOL_HTTPS.equalsIgnoreCase(endpointUri.getScheme());
    }

    /**
     * 创建客户端级 TLS socket factory。
     *
     * @param trustedCaFile 自定义可信 CA 文件路径
     * @return TLS socket factory；未配置时返回 null
     */
    public static SSLConnectionSocketFactory createSslSocketFactory(String trustedCaFile) {
        if (StringUtils.isBlank(trustedCaFile)) {
            return null;
        }
        return createSslSocketFactory(loadCertificates(trustedCaFile), trustedCaFile);
    }

    /**
     * 使用已校验的 CA 证书创建客户端级 TLS socket factory。
     *
     * @param certificates  已校验的 CA 证书
     * @param trustedCaFile 自定义可信 CA 文件路径
     * @return TLS socket factory
     */
    public static SSLConnectionSocketFactory createSslSocketFactory(
            List<X509Certificate> certificates, String trustedCaFile) {
        try {
            SSLContext sslContext = SSLContext.getInstance(S3ClientConstant.TLS_CONTEXT_PROTOCOL);
            sslContext.init(null, new TrustManager[]{new CompositeX509TrustManager(
                    getDefaultTrustManager(), getCustomTrustManager(certificates))}, null);
            return new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.STRICT_HOSTNAME_VERIFIER);
        } catch (NoSuchAlgorithmException ex) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, trustedCaFile), ex);
        } catch (java.security.KeyManagementException ex) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, trustedCaFile), ex);
        }
    }

    /**
     * 加载并校验自定义 CA 文件。
     *
     * @param trustedCaFile 自定义可信 CA 文件路径
     * @return 已校验的 CA 证书
     */
    public static List<X509Certificate> loadCertificates(String trustedCaFile) {
        Path path;
        try {
            path = Paths.get(trustedCaFile);
        } catch (InvalidPathException ex) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_NOT_READABLE, trustedCaFile), ex);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_NOT_READABLE, trustedCaFile));
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance(S3ClientConstant.CERTIFICATE_TYPE_X509);
            Collection<? extends Certificate> parsedCertificates = certificateFactory.generateCertificates(inputStream);
            if (parsedCertificates.isEmpty()) {
                throw new S3ClientPropertiesInvalidException(
                        String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_EMPTY, trustedCaFile));
            }
            List<X509Certificate> certificates = new ArrayList<>();
            for (Certificate certificate : parsedCertificates) {
                if (!(certificate instanceof X509Certificate)) {
                    throw new S3ClientPropertiesInvalidException(
                            String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, trustedCaFile));
                }
                X509Certificate x509Certificate = (X509Certificate) certificate;
                validateCertificate(x509Certificate, trustedCaFile);
                certificates.add(x509Certificate);
            }
            return certificates;
        } catch (S3ClientPropertiesInvalidException ex) {
            throw ex;
        } catch (CertificateException ex) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, trustedCaFile), ex);
        } catch (java.io.IOException ex) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_NOT_READABLE, trustedCaFile), ex);
        }
    }

    private static void validateCertificate(X509Certificate certificate, String trustedCaFile) {
        if (certificate.getBasicConstraints() < 0) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_NOT_CA, trustedCaFile));
        }
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage != null && (keyUsage.length <= S3ClientConstant.KEY_USAGE_CERT_SIGN_INDEX || !keyUsage[S3ClientConstant.KEY_USAGE_CERT_SIGN_INDEX])) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_KEY_USAGE_INVALID, trustedCaFile));
        }
        try {
            certificate.checkValidity();
        } catch (CertificateException ex) {
            throw new S3ClientPropertiesInvalidException(
                    String.format(ErrorMessage.PROPERTIES_TRUSTED_CA_NOT_VALID, trustedCaFile), ex);
        }
    }

    private static X509TrustManager getDefaultTrustManager() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_ALGORITHM);
            trustManagerFactory.init((KeyStore) null);
            return getX509TrustManager(trustManagerFactory.getTrustManagers());
        } catch (NoSuchAlgorithmException ex) {
            throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, ex);
        } catch (KeyStoreException ex) {
            throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, ex);
        }
    }

    private static X509TrustManager getCustomTrustManager(List<X509Certificate> certificates) {
        try {
            KeyStore keyStore = KeyStore.getInstance(S3ClientConstant.TRUSTED_CA_KEY_STORE_TYPE);
            keyStore.load(null, null);
            for (int index = 0; index < certificates.size(); index++) {
                keyStore.setCertificateEntry(String.format(S3ClientConstant.TRUSTED_CA_CERTIFICATE_ALIAS_TEMPLATE, index), certificates.get(index));
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_ALGORITHM);
            trustManagerFactory.init(keyStore);
            return getX509TrustManager(trustManagerFactory.getTrustManagers());
        } catch (KeyStoreException ex) {
            throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, ex);
        } catch (CertificateException ex) {
            throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, ex);
        } catch (java.io.IOException ex) {
            throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID, ex);
        }
    }

    private static X509TrustManager getX509TrustManager(TrustManager[] trustManagers) {
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new S3ClientPropertiesInvalidException(ErrorMessage.PROPERTIES_TRUSTED_CA_FILE_INVALID);
    }

    private static S3ClientPropertiesInvalidException invalidEndpoint(String endpoint) {
        return new S3ClientPropertiesInvalidException(
                String.format(ErrorMessage.PROPERTIES_ENDPOINT_INVALID, endpoint));
    }

    static final class CompositeX509TrustManager implements X509TrustManager {

        private final X509TrustManager defaultTrustManager;
        private final X509TrustManager customTrustManager;

        CompositeX509TrustManager(X509TrustManager defaultTrustManager, X509TrustManager customTrustManager) {
            this.defaultTrustManager = defaultTrustManager;
            this.customTrustManager = customTrustManager;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            defaultTrustManager.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                defaultTrustManager.checkServerTrusted(chain, authType);
            } catch (CertificateException ex) {
                customTrustManager.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            Set<X509Certificate> certificates = new LinkedHashSet<>();
            addAcceptedIssuers(certificates, defaultTrustManager.getAcceptedIssuers());
            addAcceptedIssuers(certificates, customTrustManager.getAcceptedIssuers());
            return certificates.toArray(new X509Certificate[certificates.size()]);
        }

        private void addAcceptedIssuers(Set<X509Certificate> certificates, X509Certificate[] acceptedIssuers) {
            for (X509Certificate certificate : acceptedIssuers) {
                certificates.add(certificate);
            }
        }
    }
}
