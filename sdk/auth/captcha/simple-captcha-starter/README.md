# simple-captcha-starter

通用验证码 Starter：出题 / 验题 / 挑战存储。被动组件——只提供验证码能力本身，"何时要求验证码"（登录失败几次后弹出、按 IP 风控等）由消费方决定。默认内置图片验证码实现（Java2D 自绘，零第三方绘图依赖），并提供 `CaptchaProvider` SPI 供滑块 / 行为码 / 云验证码等方案接入。

## 依赖

Gradle：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-captcha-starter:1.0.0'

    // 挑战存储强依赖 redis-route（implementation 已传递，无需单独引入）。
    // 宿主侧需按自身依赖治理提供 Spring Data Redis 运行时
    // （route 对 data-redis 是 compileOnly，不传递）。
    implementation 'org.springframework.data:spring-data-redis'
}
```

Maven：

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>simple-captcha-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

无 enable 开关：被动功能模块，不引包即关闭；引入即装配默认实现。

## 接入

挑战存储强制走 Redis 共享存储（无内存模式）——出题与验题可落在不同实例。宿主必须已启用 redis-route，容器中无 `RedisRouteTemplate` bean 时应用启动快速失败。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          captcha:
            challenge-ttl-seconds: 120    # 挑战有效期（秒）
            image-char-length: 4          # 图片码字符个数
            redis:
              me: default                 # 应用实例标识（多应用共用 Redis 时区分）
```

引入依赖后即可注入 `CaptchaProvider` 使用：

```java
@Autowired
private CaptchaProvider captchaProvider;

/** 出题：前端拿 captchaId + 图片展示 */
@GetMapping("/captcha")
public CaptchaChallenge challenge() {
    return captchaProvider.generate();
}

/** 验题：随业务请求一并提交 captchaId + 用户输入 */
@PostMapping("/login")
public String login(LoginRequest request) {
    if (!captchaProvider.verify(request.getCaptchaId(), request.getCaptcha())) {
        return "验证码错误或已过期";
    }
    // ...业务校验
}
```

`CaptchaChallenge` 含三个字段：`captchaId`（验题时回传）、`type`（`image` 等，由实现方约定）、`content`。默认图片实现的 `content` 为 PNG data URI，前端直接 `<img :src="content">` 展示。

## 行为契约

- `generate()` 返回一次挑战；默认实现 `captchaId` 为 UUID，答案存入挑战存储并按 TTL 过期。
- `verify(captchaId, answer)` 一次性消费：同一挑战无论校验成败，取出即删——重放、并发双花、过期 / 不存在的挑战一律返回 `false`，不抛异常。
- 默认图片实现答案统一小写存储，校验忽略大小写并 trim 首尾空白。
- 字符池已去混淆（剔除 `0/O`、`1/I/l` 等易混字符），逐字符随机旋转 + 干扰线 + 噪点，单字符占宽 30px、图高 42px。
- 存储职责归实现：图片码经本模块 `ChallengeStore`（Redis）；远程校验型实现（滑块 / 云验证码）用厂商 token，无需本地存储。

## SPI 扩展

自定义实现（滑块 / 行为码 / 云验证码）实现 `CaptchaProvider` 并注册为 Spring Bean：

```java
@Bean
public CaptchaProvider customCaptchaProvider() {
    return new CaptchaProvider() {
        @Override
        public CaptchaChallenge generate() {
            // 对接厂商出题
        }

        @Override
        public boolean verify(String captchaId, String answer) {
            // 厂商服务端校验；一次性消费语义
        }
    };
}
```

自定义实现的接入方不引本模块运行时——`compileOnly` 取 `CaptchaProvider` 接口即可，不同实现不同引用，无让位场景。

## Redis Key 结构

```
sure-auth-captcha:challenge:{me}::{captchaId}
```

`{me}` HashTag 包裹支持 Redis Cluster，经 redis-route `stringTemplateByKey` 按 key 路由落数据源；消费使用 MULTI/EXEC 事务原子取删（不依赖 spring-data-redis 2.6+ 的 `getAndDelete`，全版本矩阵兼容）。

## 异常与错误码

- 启动期：宿主无 redis-route bean 时快速失败（缺 `RedisRouteTemplate`），无静默降级。
- 运行期：`SimpleCaptchaException`，错误码 `CAPTCHA_001`（验证码图片编码失败）。
- 验证失败不是异常：`verify` 一律返回 `false`，由消费方决定提示语义。

## 部署前提

默认图片实现依赖 JRE 字体栈渲染字符：

- 常规 JDK 镜像、debian-slim：开箱可用
- alpine / distroless 瘦身容器：需自带 fontconfig + 基本字体（如 `font-dejavu`）；缺字体时可接入自定义 Provider 完全绕开该依赖

## Spring Boot 兼容性

1.0.0 已完成以下完整测试矩阵。每一档均使用真实 Redis（route 接管）执行模块全部测试（11 项，零跳过、零失败、零错误）。

| Spring Boot | JDK | Gradle | 状态 |
| --- | --- | --- | --- |
| 2.2.13.RELEASE | 8 | 7.6 | 已验证 |
| 2.3.12.RELEASE | 8 | 7.6 | 已验证 |
| 2.4.5 | 8 | 7.6 | 已验证 |
| 2.7.9 | 11 | 8.5 | 已验证 |
