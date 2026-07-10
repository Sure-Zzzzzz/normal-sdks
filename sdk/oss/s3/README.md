# S3 Callback Event Model

`s3` 是对象存储回调事件实体模块，当前只提供 `S3Event`，用于反序列化 S3 / MinIO / 阿里云 OSS 等兼容 S3 事件通知的回调 JSON。

本模块不是 `s3-client-starter` 的基础依赖；`s3-client-starter` 的对象存储客户端能力独立提供。

## 版本

| 模块 | 版本 |
|---|---|
| s3 | 1.0.0 |

## 定位

- 提供对象存储事件回调实体类
- 适用于接收 Bucket 事件通知后的 JSON 反序列化
- 不提供上传、下载、桶管理、预签名 URL 等客户端能力
- 不作为 `s3-client-starter` 的依赖前置

## 核心实体

### S3Event

路径：`io.github.surezzzzzz.sdk.oss.s3.data.model.entity.S3Event`

对应兼容 S3 事件通知的顶层结构：

```json
{
  "Records": [
    {
      "eventVersion": "2.1",
      "eventSource": "aws:s3",
      "eventTime": "2026-07-10T00:00:00.000Z",
      "eventName": "ObjectCreated:Put",
      "userIdentity": {
        "principalId": "principal"
      },
      "requestParameters": {
        "sourceIPAddress": "127.0.0.1"
      },
      "responseElements": {
        "xAmzRequestId": "request-id",
        "xAmzId2": "host-id"
      },
      "s3": {
        "s3SchemaVersion": "1.0",
        "configurationId": "config-id",
        "bucket": {
          "name": "test-bucket",
          "arn": "arn:aws:s3:::test-bucket",
          "ownerIdentity": {
            "principalId": "owner"
          }
        },
        "object": {
          "key": "demo/object.txt",
          "size": 1024,
          "etag": "etag",
          "versionId": "version-id",
          "sequencer": "sequencer"
        }
      }
    }
  ]
}
```

## 使用示例

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.oss.s3.data.model.entity.S3Event;

public class S3CallbackController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void handleCallback(String body) throws Exception {
        S3Event event = objectMapper.readValue(body, S3Event.class);
        for (S3Event.Record record : event.getRecords()) {
            String bucketName = record.getS3().getBucket().getName();
            String objectKey = record.getS3().getObject().getKey();
            String eventName = record.getEventName();
            // 按业务需要处理回调事件
        }
    }
}
```

## 依赖说明

```gradle
dependencies {
    compileOnly "com.fasterxml.jackson.core:jackson-databind"
}
```

`S3Event` 使用 Jackson 的 `@JsonProperty("Records")` 适配回调 JSON 顶层字段。
