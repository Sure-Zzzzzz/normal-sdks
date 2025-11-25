# B2M SMS Client Starter

基于Spring Boot的B2M短信服务客户端启动器，提供简洁的短信发送功能。

## 功能特性

- ✅ 支持单条短信发送
- ✅ 支持模板短信发送
- ✅ 支持AES加密传输
- ✅ 支持GZIP压缩
- ✅ 支持自定义RestTemplate配置
- ✅ 支持Spring Boot自动配置

## 依赖配置

### Gradle

```gradle
dependencies {
    implementation 'io.github.surezzzzzz:b2m-sms-client-starter:1.0.0'
    // 注意：httpclient依赖由starter内部提供，其他依赖需要项目中显式引入
    implementation 'org.springframework:spring-web'
    implementation 'org.springframework.boot:spring-boot-configuration-processor'
    implementation 'org.springframework.boot:spring-boot-autoconfigure'
    implementation 'org.springframework:spring-context'
    implementation 'org.apache.commons:commons-lang3'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.surezzzzzz</groupId>
    <artifactId>b2m-sms-client-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<!-- 需要项目中显式引入以下依赖 -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## 快速开始

### 1. 配置application.yml

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        b2m:
          sms:
            app-id: your-app-id
            secret-key: your-secret-key

```

### 2. 使用SmsClient

```java
@Service
public class YourService {
    
    @Autowired
    private SmsClient smsClient;
    
    public void sendSms() {
        try {
            // 发送单条短信
            smsClient.sendSingleSms("13800138000", "您的验证码是123456");
            
            // 发送模板短信
            TemplateSmsIdAndMobile[] templateData = {
                TemplateSmsIdAndMobile.builder()
                    .mobile("13800138000")
                    .customSmsId("1234567890")
                    .build()
            };
            SendTemplateSmsResponse response = smsClient.sendTemplateSms(templateData, "template-id");
            
        } catch (Exception e) {
            // 处理异常
            log.error("短信发送失败", e);
        }
    }
}
```

## API说明

### SmsClient主要方法

| 方法 | 说明 | 参数 |
|-----|------|------|
| `sendSingleSms(String phoneNumber, String content)` | 发送单条短信 | phoneNumber: 手机号, content: 短信内容 |
| `sendTemplateSms(TemplateSmsIdAndMobile[] customSmsIdAndMobiles, String templateId)` | 发送模板短信 | customSmsIdAndMobiles: 模板数据数组, templateId: 模板ID |

### 配置属性

| 属性 | 说明 | 默认值 |
|-----|------|--------|
| `app-id` | B2M平台应用ID | 必填 |
| `secret-key` | B2M平台密钥 | 必填 |
| `template-url` | 模板短信接口地址 | http://bjksmtn.b2m.cn/inter/sendTemplateVariableSMS |
| `single-url` | 单条短信接口地址 | http://bjksmtn.b2m.cn/inter/sendSingleSMS |
| `algorithm` | 加密算法 | AES/ECB/PKCS5Padding |
| `encode` | 编码格式 | UTF-8 |
| `is-gzip` | 是否启用GZIP压缩 | true |
| `valid-period` | 请求有效期(秒) | 60 |


## 注意事项

1. **依赖配置**: 
   - `httpclient`依赖由starter内部提供（`implementation`）
   - 其他Spring相关依赖需要项目中显式引入，确保版本兼容性
   - 建议检查项目中的Spring Boot版本，确保依赖版本匹配

2. **安全配置**: 请妥善保管`secret-key`，建议使用环境变量或配置中心管理敏感信息

3. **异常处理**: 短信发送方法会抛出异常，请做好异常处理

4. **频率限制**: 注意B2M平台的短信发送频率限制

## 版本历史

### 1.0.0
- 初始版本发布
- 支持单条短信和模板短信发送
- 支持AES加密和GZIP压缩
- 支持Spring Boot自动配置

## 支持与反馈

如有问题或建议，请在GitHub Issues中反馈。