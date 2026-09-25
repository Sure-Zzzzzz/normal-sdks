# B2M SMS Client Starter

> **1.x 已封版**：1.x 文档冻结快照见 [README.1.x.md](README.1.x.md)；本文档对应 **2.0.0**。

基于 Spring Boot 的 B2M 短信客户端：单条/模板短信发送，AES 加密 + GZIP 压缩传输，失败语义两分（SDK 故障抛异常，平台业务结果进结果对象）。

**兼容范围**：Spring Boot 2.x（javax 线，基线 2.7.9）；SB3/jakarta 宿主需另行评估后再引入。

## 版本选型

| 版本 | 状态 | 说明 |
| --- | --- | --- |
| 2.0.0 | 当前 | 重构版：异常体系/结果对象/enable 开关/构造注入/自维护 ObjectMapper/日志不打手机号 |
| 1.0.0 | 终版封存 | 文档快照见 README.1.x.md；升级指南见 CHANGELOG.2.0.0.md |

## 快速开始（三步）

**第一步：引入依赖**

```gradle
dependencies {
    implementation 'io.github.surezzzzzz:b2m-sms-client-starter:2.0.0'
    // 宿主需自行提供 Spring Boot 环境（spring-web/spring-context 由宿主传递）
}
```

**第二步：最小化配置（只需三项）**

```yaml
# application.yml（git 受管模板，真实凭据放 application-local.yml）
io:
  github:
    surezzzzzz:
      sdk:
        b2m:
          sms:
            enable: true                       # 装配开关，不配默认 false（SmsClient 不装配）
            app-id: your-app-id         # B2M 平台分配的应用 ID
            secret-key: 0123456789abcdef        # AES 密钥（16/24/32 字节，启动校验）
```

敏感凭据与受管文件分离（仓库既有惯例：`spring.profiles.active: local` 激活 gitignored 的 `application-local.yml`）：

```yaml
# application-local.yml（gitignored，真实凭据只在这里）
io:
  github:
    surezzzzzz:
      sdk:
        b2m:
          sms:
            enable: true
            app-id: your-app-id
            secret-key: 0123456789abcdef
```

**第三步：注入并使用**

```java
@Autowired
private SmsClient smsClient;

// 单条短信
SmsSendResult single = smsClient.sendSingleSms("+8613800000000", "你好");

// 模板变量短信（customSmsId 可选重载，传业务追踪 ID 做跨层日志关联）
SmsSendResult result = smsClient.sendTemplateSms("your-template-id", "+8613800000000",
        Collections.singletonMap("code", "834621"), "challenge-123");

if (result.isSuccess()) {
    String smsId = result.getResultCode();   // 平台回执 smsId
} else {
    log.warn("平台未受理: resultCode={}, message={}", result.getResultCode(), result.getMessage());
}
```

## 最全配置（11 项，除前三项外全部有默认值）

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        b2m:
          sms:
            # ===== 必填三项 =====
            enable: true                                      # 装配开关（默认 false：SmsClient 不装配，上游 adaptor 随之消失）
            app-id: your-app-id                       # 平台应用 ID
            secret-key: 0123456789abcdef                      # AES 密钥（16/24/32 字节，启动校验，敏感只进 local 文件）

            # ===== 平台协议参数（以下全部可省略，走默认） =====
            template-url: http://bjksmtn.b2m.cn/inter/sendTemplateVariableSMS
                                                            # 模板变量短信端点（默认平台公有端点，私有化部署时覆盖）
            single-url: http://bjksmtn.b2m.cn/inter/sendSingleSMS
                                                            # 单条短信端点（默认平台公有端点）
            algorithm: AES/ECB/PKCS5Padding                   # 加密算法（平台协议约定；换 PKCS7Padding 见下方说明）
            encode: UTF-8                                     # 报文编码
            gzip: true                                        # 请求体 GZIP 压缩开关
            valid-period: 60                                  # 请求有效期（秒）
            connect-timeout-ms: 5000                          # HTTP 连接超时（毫秒）
            read-timeout-ms: 10000                            # HTTP 读取超时（毫秒）
```

**PKCS7Padding**：可选算法，需要时使用方自行引入 BouncyCastle（如 `org.bouncycastle:bcprov-jdk15on`），缺失时启动显式失败（错误码 `SMS_CRYPTO_003`）；默认 `AES/ECB/PKCS5Padding` 无额外依赖。

## 启动行为

- `enable=false`（默认）或未配置：SmsClient 不装配，注入点为空——按"bean 不存在"判定能力缺失；
- `enable=true` 但 app-id/secret-key 缺失、或密钥长度非 16/24/32 字节：**启动直接失败**（`SmsConfigurationException`），不留"装配了但发不出"的中间态。

## 失败语义（两分）

| 场景 | 行为 | 错误码 |
| --- | --- | --- |
| 配置缺失/密钥非法 | 启动失败 | SMS_CONFIG_001 / SMS_CONFIG_002 |
| 加密/解密失败（密钥或算法故障） | 抛 `SmsException` | SMS_CRYPTO_001 / SMS_CRYPTO_002 |
| PKCS7 缺 BouncyCastle | 启动失败 | SMS_CRYPTO_003 |
| HTTP 通信失败/非 2xx | 抛 `SmsException` | SMS_COMM_001 |
| 请求体序列化失败 | 抛 `SmsException` | SMS_COMM_003 |
| 平台 2xx 但响应内容解析失败 | 进 `SmsSendResult`（success=false） | SMS_COMM_002 |

`SmsException.getErrorCode()` 对照上表定位；`SmsSendResult` 的 `resultCode` 成功时=平台回执 smsId、失败时=HTTP 状态码文本。回执形态：模板接口返回回执数组、单条接口返回单个回执对象（平台实测），SDK 已分别解析。

## 日志

DEBUG（默认关闭）五点贯穿：装配（INFO）/发送请求（模板 ID+customSmsId）/HTTP 返回（状态码+耗时）/发送完成（success+smsId）/解析失败（WARN）。**不打手机号**，跨层追踪用 customSmsId。排障开启：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.b2m.sms: debug
```

## 测试与联调

- `SmsCryptoGzipHelperTest` / `SmsClientPipelineTest`：mock RestTemplate + 真实加解密，全链路不出网；
- `B2mManualSendTest`（默认跳过）：平台真实发送联调——复制 `application-local.yml.example` 为 `application-local.yml` 填真实凭据与 `b2m.e2e.phone` 目标号，设环境变量 `B2M_SMS_E2E=true` 后运行。

## 变更记录

见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)；1.x 封版快照见 [README.1.x.md](README.1.x.md)。
