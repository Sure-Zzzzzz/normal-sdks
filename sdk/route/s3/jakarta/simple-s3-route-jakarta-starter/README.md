# Simple S3 Route Jakarta Starter

面向 Spring Boot 3.x 的 S3 兼容对象存储路由组件。调用方使用显式 target key 选择已登记的对象存储，组件返回标准 AWS SDK v1 `AmazonS3` 客户端，不二次封装上传、下载、列举或预签名等对象操作。

## 适用范围

- Spring Boot 3.x、Java 17 及以上。
- AWS S3、MinIO 或其他 S3 兼容对象存储的多 target 隔离访问。
- 每个 target 独立管理连接池、凭据、超时、签名版本和关闭生命周期。

本模块与 javax 线 `simple-s3-route-starter:1.0.0` 二选一。同一应用不要同时引入两者；两条线的包名、配置键和公开 API 保持一致，区别仅在 Spring Boot 自动配置机制和宿主兼容范围。

## 版本选型

| 宿主 | 选择的模块 | 当前版本 | 自动配置注册 |
|---|---|---|---|
| Spring Boot 2.x | `simple-s3-route-starter` | 1.0.0 | `spring.factories` |
| Spring Boot 3.x | `simple-s3-route-jakarta-starter` | 1.0.0 | `AutoConfiguration.imports` |

每个应用只能选择其中一条线。两条线使用相同的包名、配置键和 Route API，不能在同一宿主内同时引入。

## 依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-s3-route-jakarta-starter:1.0.0'
    implementation 'com.amazonaws:aws-java-sdk-s3:1.12.797'
}
```

Starter 对 AWS SDK v1 S3 使用 `compileOnly`，调用方应自行提供并治理 SDK 版本。AWS SDK v1 S3 客户端不依赖 javax Web API，可以在 Spring Boot 3 宿主中使用。

## 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        s3:
          route:
            enable: true
            targets:
              storage-primary:
                endpoint: https://storage.example.test
                region: us-east-1
                path-style-enabled: true
                authentication:
                  type: ACCESS_KEY
                  access-key: ${S3_ACCESS_KEY}
                  secret-key: ${S3_SECRET_KEY}
```

敏感凭据应由部署环境提供，不应写入版本库。每个 target 都必须使用明确的 key；没有默认 target，也不会回退到其他 target。

## 使用方式

```java
@Service
public class SampleStorageService {

    private final S3RouteTemplate s3RouteTemplate;

    public SampleStorageService(S3RouteTemplate s3RouteTemplate) {
        this.s3RouteTemplate = s3RouteTemplate;
    }

    public String readText(String bucket, String objectKey) {
        return s3RouteTemplate.execute("storage-primary", client ->
                client.getObjectAsString(bucket, objectKey));
    }
}
```

常规操作使用 `execute(targetKey, callback)`，让调用计入 Route 的关停排空窗口。需要与既有框架集成时可通过 `amazonS3(targetKey)` 获取由 Route 管理的长生命周期客户端；调用方不得调用该客户端的 `shutdown()`。

## 配置边界

配置前缀为 `io.github.surezzzzzz.sdk.s3.route`。每个 target 可独立配置 endpoint、region、`AWS_V4` 或 `S3_V2` 签名、Path Style、私有 CA、匿名或 access key 认证及客户端超时。私有 CA 只加入对应 target 的信任链，保留严格主机名校验，不修改 JVM 全局信任库。

路由和配置错误以 `S3RouteException` 返回稳定错误码；回调中的 AWS SDK 对象操作异常原样透传。DEBUG 日志只记录 target key、操作类别、耗时和故障类型，不记录 bucket、对象 key、对象内容、endpoint 或凭据。

## Spring Boot 3 自动配置

本模块使用 Spring Boot 3 的 `AutoConfiguration.imports` 注册自动配置，不包含 `spring.factories`。当前基线为 Spring Boot `3.4.2`、Java `17`；兼容性验证应以调用方实际使用的 Spring Boot 3 精确补丁版本为准。

## 从 Spring Boot 2.x 升级

1. 将依赖坐标替换为本模块，并移除 `simple-s3-route-starter`。
2. 将宿主升级至 Spring Boot 3.x 和 Java 17 及以上，并由调用方提供 `aws-java-sdk-s3`。
3. 保持原有 target 配置、`S3RouteTemplate` 和 `AmazonS3` 使用方式不变。
4. 在目标对象存储验证 target 隔离、读写、预签名和关闭生命周期；不要同时装载两条 Route 自动配置。

## 发布前验证

本版本使用两个独立 MinIO target，覆盖 target 隔离、上传、下载、列举、预签名、删除、自动配置、关停排空和私有 CA 配置校验。
