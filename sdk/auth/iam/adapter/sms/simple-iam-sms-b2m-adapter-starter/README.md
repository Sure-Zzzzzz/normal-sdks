# Simple IAM SMS B2M Adapter Starter

B2M 短信投递适配器:实现 IAM `SmsDeliveryProvider` SPI,装配自动开启、未装配自动取消(adaptor=能力,与 SSO/LDAP/captcha 同范式)。

## 依赖与配置

```gradle
implementation 'io.github.sure-zzzzzz:simple-iam-sms-b2m-adapter-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:b2m-sms-client-starter:2.0.1'
```

```yaml
io.github.surezzzzzz.sdk.auth.iam.adapter.sms.b2m:
  enable: true
  signature: 你的签名          # 单条通道直发:签名+话术拼正文,adaptor 固定拼接
  # login-text/bind-text/forgot-password-text 默认内置({code} 占位),含【】签名形态启动失败
```

B2M 凭据(app-id/secret-key)在 `io.github.surezzzzzz.sdk.b2m.sms` 前缀(b2m-sms-client-starter 层,敏感走 application-local.yml);其 enable=false 时 SmsClient 缺失,本 adaptor 装配失败——能力整体消失,无静默态。

## 能力声明

`supportedRegions()=["+86"]`(B2M 平台国内通道;出入口统一消费 list,未来国际通道换/加 adaptor)。

## 测试

- `B2mSmsDeliveryConfigurationTest`（常规运行，零外部依赖）：enable 开关/话术【】启动失败/唯一装配与能力声明/三套话术正文（签名前缀+{code} 替换+customSmsId=purpose）/平台业务拒绝抛出不吞/通信异常透传。
- `B2mSmsDeliveryManualSendTest`（手跑 E2E，双闸门）：环境变量 `IAM_SMS_E2E=true` 且 `src/test/resources/application-local.yml`（gitignored）填有 b2m 凭据与 `iam-sms-e2e.phone`；任一不满足整类自动跳过，全量测试永远安全。真实送达由目标手机人工确认。
