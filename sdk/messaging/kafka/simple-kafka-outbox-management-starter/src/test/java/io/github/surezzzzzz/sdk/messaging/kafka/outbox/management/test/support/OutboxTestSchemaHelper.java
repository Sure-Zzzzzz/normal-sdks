package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Outbox 测试表结构辅助类。
 *
 * @author surezzzzzz
 */
public final class OutboxTestSchemaHelper {
    private static final String DDL_PATH = "sdk/messaging/kafka/simple-kafka-outbox-starter/docs/01_schema.sql";

    private OutboxTestSchemaHelper() {
    }

    /**
     * 按 Runtime DDL 重建测试表。
     */
    public static void recreateTable(JdbcTemplate jdbcTemplate) throws IOException {
        String ddl = new String(Files.readAllBytes(resolveDdlPath()), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (!statement.trim().isEmpty()) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    private static Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "..", "simple-kafka-outbox-starter", "docs", "01_schema.sql");
    }
}
