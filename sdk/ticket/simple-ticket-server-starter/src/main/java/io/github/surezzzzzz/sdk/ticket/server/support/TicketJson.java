package io.github.surezzzzzz.sdk.ticket.server.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 工单 JSON 编解码（SDK 自维护 ObjectMapper，禁止注入/复用应用容器 Bean——规范）。
 *
 * @author surezzzzzz
 */
public final class TicketJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private TicketJson() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 取编解码器。
     *
     * @return ObjectMapper
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
