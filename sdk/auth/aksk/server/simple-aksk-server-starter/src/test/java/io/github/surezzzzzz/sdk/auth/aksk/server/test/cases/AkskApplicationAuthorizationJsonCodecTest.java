package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AKSK应用授权JSON编解码器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class AkskApplicationAuthorizationJsonCodecTest {

    /**
     * 验证合法文档必须经过结构化 Claim 规范化后持久化并可还原。
     */
    @Test
    void shouldWriteAndReadNormalizedDataGrantDocument() {
        DataGrantDocument document = document();

        String value = AkskApplicationAuthorizationJsonCodec.writeDataGrantDocument(document);
        DataGrantDocument restored = AkskApplicationAuthorizationJsonCodec.readDataGrantDocument(value);

        log.info("AKSK授权文档已通过规范化JSON持久化并还原");
        assertEquals(document, restored, "持久化数据授权文档必须完整还原");
    }

    /**
     * 验证无 DATA 授权必须保留为实体字段的 null。
     */
    @Test
    void shouldKeepMissingDataGrantDocumentAsNull() {
        log.info("AKSK未配置DATA授权时保留空文档语义");
        assertNull(AkskApplicationAuthorizationJsonCodec.readDataGrantDocument(null),
                "空持久化字段必须表示未授予DATA权限");
        assertThrows(IllegalArgumentException.class,
                () -> AkskApplicationAuthorizationJsonCodec.writeDataGrantDocument(null),
                "不得把空文档编码为有效DATA授权");
    }

    /**
     * 验证非严格 JSON 不能作为授权快照读取。
     */
    @Test
    void shouldRejectInvalidDataGrantDocumentJson() {
        log.info("AKSK必须拒绝非严格数据授权JSON");
        assertThrows(IllegalArgumentException.class,
                () -> AkskApplicationAuthorizationJsonCodec.readDataGrantDocument("{\"protocol\":\"invalid\"}"),
                "缺失必要字段或协议错误的文档必须拒绝");
    }

    private DataGrantDocument document() {
        DataConstraint tenant = new DataConstraint("tenantId", DataConstraintOperator.IN,
                Collections.singletonList("tenant-a"));
        DataConstraint department = new DataConstraint("departmentId", DataConstraintOperator.IN,
                Collections.singletonList("department-a"));
        DataGrant grant = new DataGrant("order", Collections.singletonList("read"), false,
                Arrays.asList(tenant, department));
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(grant));
    }
}
