# simple-iam-captcha-adapter-starter

IAM 登录验证码适配器 Starter。把通用验证码模块 `simple-captcha-starter`（默认图片码）桥接为 `simple-iam-core` 的 `CaptchaProvider` SPI 契约，登录链路的出题 / 验题即接入默认图片验证码。

`simple-iam-server-starter` 已传递引入本模块：业务方引入 server-starter 即得验证码能力，无需单独引入。仅当宿主自行拼装 IAM 登录链路（只引 `simple-iam-core`）且需要图片验证码时才单独引入本模块。

## 依赖

Gradle：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-captcha-adapter-starter:1.0.0'
}
```

Maven：

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>simple-iam-captcha-adapter-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

`simple-captcha-starter` 与 `simple-redis-route-starter` 由本模块传递引入，业务无需额外声明。宿主必须启用 redis-route（验证码挑战存储强制 Redis，多实例共享出题 / 验题；无 RedisRouteTemplate 时启动快速失败）。

## 装配与行为

引依赖即装配，无 `enable` 开关、无本模块专属配置项：宿主不引入即不装配，引入后装配即生效。仅当 `simple-iam-core` 的 `CaptchaProvider` SPI 在 classpath 时激活。

适配器只做契约桥接与模型转换（两模型同构：`captchaId` / `type` / `content`）：

- 出题 `generate()`：委托通用模块 `ImageCaptchaProvider` 生成图片挑战（data URI），经 Redis 暂存答案；
- 验题 `verify(captchaId, answer)`：转发通用模块，答案一次性消费（事务原子取删）。

验证码参数（挑战 TTL、图片字符长度等）经通用模块 `SimpleCaptchaProperties` 配置，见 `simple-captcha-starter` 的 README。

业务方需要滑块等其他形态时，直接实现 `simple-iam-core` 的 `CaptchaProvider` 接口并移除本模块依赖——不同实现不同引用，无让位场景。

## 测试

模块测试两层（真实 Redis fixture）：

- `SimpleIamCaptchaAdapterAutoConfigurationTest.shouldBridgeGenericProviderAsIamCoreContract`：iam-core 注入点是适配器、底层委托为通用默认图片实现；
- `SimpleIamCaptchaAdapterAutoConfigurationTest.shouldGenerateAndVerifyThroughBridge`：出题经模型转换可用，验题经转发一次性消费。

## Spring Boot 兼容性

基于 Spring Boot 2.7.9 / javax 基线开发；验证码核心与 `simple-captcha-starter` 一致，后者经 2.2.x ~ 2.7.x 四版本矩阵验证。
