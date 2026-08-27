package io.github.surezzzzzz.sdk.s3.route.support;

import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.s3.route.constant.SimpleS3RouteConstant;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
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
 * S3 Route 私有 CA 信任辅助类，与仓库既有 S3 客户端先例同一套已验证方案：
 * 按文件内容解析 PEM / DER 编码的 X.509 CA（单文件可含多张），构建
 * "JRE 默认可信根 + 私有 CA" 的组合信任链，保留严格主机名校验。
 *
 * @author surezzzzzz
 */
public final class S3RouteTrustedCaHelper {

    private static final String TRUST_MANAGER_ALGORITHM = TrustManagerFactory.getDefaultAlgorithm();

    private S3RouteTrustedCaHelper() {
    }

    /**
     * 加载并校验私有 CA 文件。文件必须可读，每张证书均为处于有效期内的 CA；
     * 若声明 KeyUsage，必须包含 keyCertSign。
     *
     * @param trustedCaFile 私有 CA 文件路径
     * @return 已校验的 CA 证书
     */
    public static List<X509Certificate> loadCertificates(String trustedCaFile) {
        Path path;
        try {
            path = Paths.get(trustedCaFile);
        } catch (InvalidPathException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    String.format(ErrorMessage.TRUSTED_CA_FILE_NOT_READABLE, trustedCaFile), ex);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    String.format(ErrorMessage.TRUSTED_CA_FILE_NOT_READABLE, trustedCaFile));
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            CertificateFactory certificateFactory =
                    CertificateFactory.getInstance(SimpleS3RouteConstant.CERTIFICATE_TYPE_X509);
            Collection<? extends Certificate> parsedCertificates = certificateFactory.generateCertificates(inputStream);
            if (parsedCertificates.isEmpty()) {
                throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                        String.format(ErrorMessage.TRUSTED_CA_FILE_EMPTY, trustedCaFile));
            }
            List<X509Certificate> certificates = new ArrayList<X509Certificate>();
            for (Certificate certificate : parsedCertificates) {
                if (!(certificate instanceof X509Certificate)) {
                    throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                            String.format(ErrorMessage.TRUSTED_CA_FILE_INVALID, trustedCaFile));
                }
                X509Certificate x509Certificate = (X509Certificate) certificate;
                validateCertificate(x509Certificate, trustedCaFile);
                certificates.add(x509Certificate);
            }
            return certificates;
        } catch (S3RouteException ex) {
            throw ex;
        } catch (CertificateException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    String.format(ErrorMessage.TRUSTED_CA_FILE_INVALID, trustedCaFile), ex);
        } catch (java.io.IOException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    String.format(ErrorMessage.TRUSTED_CA_FILE_NOT_READABLE, trustedCaFile), ex);
        }
    }

    /**
     * 使用已校验的 CA 证书创建客户端级 TLS socket factory，组合
     * "JRE 默认可信根 + 私有 CA" 信任链并保留严格主机名校验。
     *
     * @param certificates 已校验的 CA 证书
     * @return TLS socket factory
     */
    public static SSLConnectionSocketFactory createSslSocketFactory(List<X509Certificate> certificates) {
        try {
            SSLContext sslContext = SSLContext.getInstance(SimpleS3RouteConstant.TLS_CONTEXT_PROTOCOL);
            sslContext.init(null, new TrustManager[]{new CompositeX509TrustManager(
                    getDefaultTrustManager(), getCustomTrustManager(certificates))}, null);
            return new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.STRICT_HOSTNAME_VERIFIER);
        } catch (NoSuchAlgorithmException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_INIT_FAILED, ex);
        } catch (java.security.KeyManagementException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_INIT_FAILED, ex);
        }
    }

    private static void validateCertificate(X509Certificate certificate, String trustedCaFile) {
        if (certificate.getBasicConstraints() < 0) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    String.format(ErrorMessage.TRUSTED_CA_NOT_CA, trustedCaFile));
        }
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage != null && (keyUsage.length <= SimpleS3RouteConstant.KEY_USAGE_CERT_SIGN_INDEX
                || !keyUsage[SimpleS3RouteConstant.KEY_USAGE_CERT_SIGN_INDEX])) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    String.format(ErrorMessage.TRUSTED_CA_KEY_USAGE_INVALID, trustedCaFile));
        }
        try {
            certificate.checkValidity();
        } catch (CertificateException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    String.format(ErrorMessage.TRUSTED_CA_NOT_VALID, trustedCaFile), ex);
        }
    }

    private static X509TrustManager getDefaultTrustManager() {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_ALGORITHM);
            trustManagerFactory.init((KeyStore) null);
            return getX509TrustManager(trustManagerFactory.getTrustManagers());
        } catch (NoSuchAlgorithmException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_INIT_FAILED, ex);
        } catch (KeyStoreException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_INIT_FAILED, ex);
        }
    }

    private static X509TrustManager getCustomTrustManager(List<X509Certificate> certificates) {
        try {
            KeyStore keyStore = KeyStore.getInstance(SimpleS3RouteConstant.TRUSTED_CA_KEY_STORE_TYPE);
            keyStore.load(null, null);
            for (int index = 0; index < certificates.size(); index++) {
                keyStore.setCertificateEntry(String.format(
                        SimpleS3RouteConstant.TRUSTED_CA_CERTIFICATE_ALIAS_TEMPLATE, index), certificates.get(index));
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TRUST_MANAGER_ALGORITHM);
            trustManagerFactory.init(keyStore);
            return getX509TrustManager(trustManagerFactory.getTrustManagers());
        } catch (KeyStoreException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_INIT_FAILED, ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_INIT_FAILED, ex);
        } catch (CertificateException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_INIT_FAILED, ex);
        } catch (java.io.IOException ex) {
            throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                    ErrorMessage.TRUSTED_CA_INIT_FAILED, ex);
        }
    }

    private static X509TrustManager getX509TrustManager(TrustManager[] trustManagers) {
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL, ErrorMessage.TRUSTED_CA_INIT_FAILED);
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
            Set<X509Certificate> certificates = new LinkedHashSet<X509Certificate>();
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
