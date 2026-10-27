package io.github.surezzzzzz.sdk.ticket.server.test.cases;

import io.github.surezzzzzz.sdk.ticket.server.configuration.TicketFileConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证散形态不携带 S3 类型时，基础附件配置仍可被类加载。
 */
class TicketFileConfigurationTest {

    @Test
    void noopConfigurationLoadsWithoutOptionalS3Type() throws Exception {
        assertNotNull(Class.forName(TicketFileConfiguration.class.getName()));
    }
}
