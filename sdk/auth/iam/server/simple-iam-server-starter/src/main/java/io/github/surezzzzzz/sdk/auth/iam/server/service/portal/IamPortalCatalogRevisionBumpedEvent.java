package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Portal 机器目录 revision 变更事件，仅供 starter 内部观测使用。
 */
public final class IamPortalCatalogRevisionBumpedEvent {

    private final String eventId;
    private final long beforeRevision;
    private final long afterRevision;
    private final String changeType;
    private final String applicationCodeDigest;
    private final String correlationId;

    public IamPortalCatalogRevisionBumpedEvent(long beforeRevision, long afterRevision,
                                               String changeType, String applicationCodeDigest,
                                               String correlationId) {
        this.eventId = UUID.randomUUID().toString();
        this.beforeRevision = beforeRevision;
        this.afterRevision = afterRevision;
        this.changeType = changeType;
        this.applicationCodeDigest = applicationCodeDigest;
        this.correlationId = correlationId;
    }

    static String digest(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            return null;
        }
    }

    public String getEventId() {
        return eventId;
    }

    public long getBeforeRevision() {
        return beforeRevision;
    }

    public long getAfterRevision() {
        return afterRevision;
    }

    public String getChangeType() {
        return changeType;
    }

    public String getApplicationCodeDigest() {
        return applicationCodeDigest;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
