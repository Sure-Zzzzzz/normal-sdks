package io.github.surezzzzzz.sdk.auth.aksk.resource.core.provider;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;

import java.util.Map;

/**
 * Simple AKSK Security Context Provider Interface
 * <p>
 * Different implementations can obtain context from different data sources:
 * - AkskUserContext: From HTTP Headers (via APISIX gateway)
 * - AkskJwtContext: From JWT Claims (direct JWT validation)
 * </p>
 *
 * @author surezzzzzz
 */
public interface SimpleAkskSecurityContextProvider {

    /**
     * Get all context data
     *
     * @return Map of all context fields (key: field name, value: field value)
     */
    Map<String, String> getAll();

    /**
     * Get specific field value
     *
     * @param key Field name
     * @return Field value, or null if not exists
     */
    String get(String key);

    /**
     * Get User ID
     *
     * @return User ID
     */
    default String getUserId() {
        return get(SimpleAkskResourceConstant.FIELD_USER_ID);
    }

    /**
     * Get Username
     *
     * @return Username
     */
    default String getUsername() {
        return get(SimpleAkskResourceConstant.FIELD_USERNAME);
    }

    /**
     * Get Client ID
     *
     * @return Client ID
     */
    default String getClientId() {
        return get(SimpleAkskResourceConstant.FIELD_CLIENT_ID);
    }

    /**
     * Get Client Type
     *
     * @return Client Type (e.g., "user", "service")
     */
    default String getClientType() {
        return get(SimpleAkskResourceConstant.FIELD_CLIENT_TYPE);
    }

    /**
     * Get Security Context
     * <p>
     * This is a special field that contains the original security context data
     * (e.g., JWT token, encrypted string, structured data, etc.)
     * </p>
     *
     * @return Security Context
     */
    default String getSecurityContext() {
        return get(SimpleAkskResourceConstant.FIELD_SECURITY_CONTEXT);
    }

    /**
     * Get Roles (comma-separated string)
     *
     * @return Roles string
     */
    default String getRoles() {
        return get(SimpleAkskResourceConstant.FIELD_ROLES);
    }

    /**
     * Get Scope (comma-separated string)
     *
     * @return Scope string
     */
    default String getScope() {
        return get(SimpleAkskResourceConstant.FIELD_SCOPE);
    }
}
