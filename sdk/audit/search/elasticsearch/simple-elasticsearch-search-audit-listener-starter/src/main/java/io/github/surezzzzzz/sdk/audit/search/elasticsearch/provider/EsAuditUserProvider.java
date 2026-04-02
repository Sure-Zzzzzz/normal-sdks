package io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider;

/**
 * ES Audit User Provider
 *
 * @author surezzzzzz
 */
public interface EsAuditUserProvider {

    /**
     * Get client ID
     */
    String getClientId();

    /**
     * Get client type
     */
    String getClientType();

    /**
     * Get user ID
     */
    String getUserId();

    /**
     * Get username
     */
    String getUsername();
}
