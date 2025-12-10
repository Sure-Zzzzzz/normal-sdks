# S3 Client Starter

一个基于AWS SDK的S3对象存储Spring Boot Starter，提供简洁易用的对象存储操作接口。

## 🚀 快速开始

### Maven Central引用

**Maven引用：**

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>s3-client-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 必须添加Spring Boot依赖（本starter使用compileOnly配置） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
```

**Gradle引用：**

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:s3-client-starter:1.0.0'
    
    // 必须添加Spring Boot依赖（本starter使用compileOnly配置）
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.boot:spring-boot-starter-web' // Web项目需要
}
```

**⚠️ 重要说明：**
本starter使用`compileOnly`配置了Spring相关依赖（`spring-boot-autoconfigure`、`spring-context`等），引用者必须显式添加Spring Boot相关依赖，否则会出现`ClassNotFoundException`。

### 基本配置

在`application.yml`中配置：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        s3:
          endpoint: "https://s3.example.com"
          access-key: "your-access-key"
          secret-key: "your-secret-key"
```

## 📋 功能特性

- ✅ **S3协议兼容** - 基于AWS SDK，支持标准S3协议
- ✅ **Spring Boot自动配置** - 零配置集成，开箱即用
- ✅ **STS临时凭证** - 支持安全的临时凭证获取
- ✅ **断点续传** - 支持文件下载断点续传
- ✅ **失败重试** - 内置智能重试机制
- ✅ **预签名URL** - 生成临时访问链接
- ✅ **存储桶管理** - 创建和管理存储桶
- ✅ **异常处理** - 统一异常体系，简化错误处理

## 🔧 完整配置示例

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        s3:
          # 基础连接配置
          endpoint: "https://s3.example.com"
          access-key: "${OSS_ACCESS_KEY}"
          secret-key: "${OSS_SECRET_KEY}"
          
          # STS配置
          role-arn: "arn:aws:iam::123456789012:role/OSSRole"
          sts-duration-seconds: 3600
          
          # URL配置
          presigned-url-expiration-seconds: 3600
          url-prefix: "https://cdn.example.com"
          
          # 下载配置
          download-directory: "/tmp/oss-downloads"
          max-download-retry-times: 3
          max-download-retry-seconds: 300
          
          # 上传配置
          max-upload-retry-times: 3
          max-upload-retry-seconds: 300
```

## 📚 相关链接

- [AWS S3文档](https://docs.aws.amazon.com/s3/)
- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
- [项目GitHub](https://github.com/surezzzzzz/normal-sdks)

## 🆘 问题反馈

如有问题，请在GitHub提交Issue或联系开发团队。

---

**Maven Central坐标**: `io.github.surezzzzzz:s3-client-starter:1.0.0`