package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant;

/**
 * Simple AKSK Resource Server Constants
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class SimpleAkskResourceServerConstant {

    /**
     * Configuration prefix
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.resource.server";

    // ==================== PEM Format Constants ====================

    /**
     * PEM public key header
     */
    public static final String PEM_PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----";

    /**
     * PEM public key footer
     */
    public static final String PEM_PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----";

    // ==================== Algorithm Constants ====================

    /**
     * RSA algorithm name
     */
    public static final String ALGORITHM_RSA = "RSA";

    // ==================== Error Message Templates ====================

    /**
     * Error message when public key is not configured
     */
    public static final String ERROR_PUBLIC_KEY_NOT_CONFIGURED = "Public key not configured. Please set either 'jwt.public-key' or 'jwt.public-key-location'";

    /**
     * Error message prefix when public key file is not found
     */
    public static final String ERROR_PUBLIC_KEY_FILE_NOT_FOUND = "Public key file not found: ";

    private SimpleAkskResourceServerConstant() {
        // Utility class, prevent instantiation
    }
}
