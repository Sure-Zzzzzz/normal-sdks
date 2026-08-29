package io.github.surezzzzzz.sdk.s3.client.test.cases;

import io.github.surezzzzzz.sdk.s3.client.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.client.exception.EventParseFailedException;
import io.github.surezzzzzz.sdk.s3.client.model.S3Event;
import io.github.surezzzzzz.sdk.s3.client.support.S3EventParseHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3 事件解析测试：AWS 标准样例、MinIO 变体容错与非法 JSON 异常路径。
 *
 * @author surezzzzzz
 */
@Slf4j
class S3EventParseHelperTest {

    private static final String AWS_SAMPLE = "{"
            + "\"Records\":[{"
            + "\"eventVersion\":\"2.2\","
            + "\"eventSource\":\"aws:s3\","
            + "\"awsRegion\":\"us-west-2\","
            + "\"eventTime\":\"1970-01-01T00:00:00.000Z\","
            + "\"eventName\":\"ObjectCreated:Put\","
            + "\"userIdentity\":{\"principalId\":\"AIDAJDPLRKLG7UEXAMPLE\"},"
            + "\"requestParameters\":{\"sourceIPAddress\":\"127.0.0.1\"},"
            + "\"responseElements\":{"
            + "\"x-amz-request-id\":\"C3D13FE58DE4C810\","
            + "\"x-amz-id-2\":\"FMyUVURIY8EXAMPLE\""
            + "},"
            + "\"s3\":{"
            + "\"s3SchemaVersion\":\"1.0\","
            + "\"configurationId\":\"test-config-rule\","
            + "\"bucket\":{"
            + "\"name\":\"mybucket\","
            + "\"ownerIdentity\":{\"principalId\":\"A3NL1KOZZKExample\"},"
            + "\"arn\":\"arn:aws:s3:::mybucket\""
            + "},"
            + "\"object\":{"
            + "\"key\":\"happyface.jpg\","
            + "\"size\":1024,"
            + "\"eTag\":\"d41d8cd98f00b204e9800998ecf8427e\","
            + "\"versionId\":\"096fKKXTRTtl3on89fVO.nfljtsv6qko\","
            + "\"sequencer\":\"0055AED6DCD90281E5\""
            + "}"
            + "}"
            + "}"
            + "]}";

    @Test
    void parsesAwsSample() {
        S3Event event = S3EventParseHelper.parse(AWS_SAMPLE);

        assertThat(event.getRecords()).hasSize(1);
        S3Event.Record record = event.getRecords().get(0);
        assertThat(record.getEventVersion()).isEqualTo("2.2");
        assertThat(record.getEventSource()).isEqualTo("aws:s3");
        assertThat(record.getEventName()).isEqualTo("ObjectCreated:Put");
        assertThat(record.getUserIdentity().getPrincipalId()).isEqualTo("AIDAJDPLRKLG7UEXAMPLE");
        assertThat(record.getRequestParameters().getSourceIPAddress()).isEqualTo("127.0.0.1");
        assertThat(record.getResponseElements().getXAmzRequestId()).isEqualTo("C3D13FE58DE4C810");
        assertThat(record.getResponseElements().getXAmzId2()).isEqualTo("FMyUVURIY8EXAMPLE");
        assertThat(record.getS3().getBucket().getName()).isEqualTo("mybucket");
        assertThat(record.getS3().getBucket().getArn()).isEqualTo("arn:aws:s3:::mybucket");
        assertThat(record.getS3().getObject().getKey()).isEqualTo("happyface.jpg");
        assertThat(record.getS3().getObject().getSize()).isEqualTo(1024);
        assertThat(record.getS3().getObject().getETag()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
        assertThat(record.getS3().getObject().getVersionId()).isEqualTo("096fKKXTRTtl3on89fVO.nfljtsv6qko");
        log.info("AWS 标准样例解析通过: key={}, size={}",
                record.getS3().getObject().getKey(), record.getS3().getObject().getSize());
    }

    @Test
    void parsesMinioVariantWithUnknownFieldsTolerantly() {
        String minioSample = "{"
                + "\"EventName\":\"s3:ObjectCreated:Put\","
                + "\"Key\":\"mybucket/variant-face.jpg\","
                + "\"Records\":[{"
                + "\"eventVersion\":\"2.0\","
                + "\"eventSource\":\"minio:s3\","
                + "\"eventName\":\"s3:ObjectCreated:Put\","
                + "\"unknownExtensionField\":\"whatever\","
                + "\"s3\":{"
                + "\"bucket\":{\"name\":\"mybucket\",\"unknownBucketField\":1},"
                + "\"object\":{\"key\":\"variant-face.jpg\",\"size\":2048,\"eTag\":\"etag-variant\"}"
                + "}"
                + "}"
                + "]}";

        S3Event event = S3EventParseHelper.parse(minioSample);

        assertThat(event.getRecords()).hasSize(1);
        S3Event.Record record = event.getRecords().get(0);
        assertThat(record.getEventName()).isEqualTo("s3:ObjectCreated:Put");
        assertThat(record.getS3().getBucket().getName()).isEqualTo("mybucket");
        assertThat(record.getS3().getObject().getKey()).isEqualTo("variant-face.jpg");
        assertThat(record.getS3().getObject().getSize()).isEqualTo(2048);
        assertThat(record.getS3().getObject().getVersionId()).isNull();
        log.info("MinIO 变体未知字段容错解析通过: eventName={}", record.getEventName());
    }

    @Test
    void illegalJsonThrowsEventParseFailed() {
        assertThatThrownBy(() -> S3EventParseHelper.parse("{\"Records\":[broken"))
                .isInstanceOf(EventParseFailedException.class)
                .satisfies(exception -> assertThat(((EventParseFailedException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.EVENT_PARSE_FAILED))
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        log.info("非法 JSON 以 EVENT_PARSE_FAILED 拒绝且 cause 保留解析异常");
    }

    @Test
    void nullOrBlankJsonThrowsEventParseFailed() {
        assertThatThrownBy(() -> S3EventParseHelper.parse(null))
                .isInstanceOf(EventParseFailedException.class)
                .satisfies(exception -> assertThat(((EventParseFailedException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.EVENT_PARSE_FAILED));
        assertThatThrownBy(() -> S3EventParseHelper.parse("   "))
                .isInstanceOf(EventParseFailedException.class)
                .satisfies(exception -> assertThat(((EventParseFailedException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.EVENT_PARSE_FAILED));
        log.info("null 与空白 JSON 均以 EVENT_PARSE_FAILED 拒绝");
    }
}
