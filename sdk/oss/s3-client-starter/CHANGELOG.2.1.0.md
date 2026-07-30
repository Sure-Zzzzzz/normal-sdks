# CHANGELOG

## 2.1.0

### 私有 CA 信任

- 新增可选 `trusted-ca-file` 配置，为 starter 创建的 `AmazonS3` 和 `AWSSecurityTokenService` 客户端加载私有 X.509 CA
- 支持 PEM 或 DER 编码的 `.crt`、`.cer`、`.pem` 文件，按证书内容解析而非扩展名；单个文件可包含多张 CA 证书
- 自定义 CA 与 JRE 默认可信根组合：默认信任链优先校验，失败后再尝试自定义 CA，避免配置私有 CA 后丢失公共 CA 信任
- 保留严格 hostname verification；证书 SAN / CN 与 endpoint 主机名不匹配时仍会失败
- 不修改 JRE `cacerts`，不设置 JVM 全局 TLS 系统属性或默认 `SSLContext`，不提供 trust-all 能力
- `trusted-ca-file` 非空时 endpoint 必须为 HTTPS；非法 endpoint、不可读或非法 CA 文件、非 CA 证书、缺少 `keyCertSign` 的 KeyUsage、过期或尚未生效的 CA 均在启动期以 `S3ClientPropertiesInvalidException(OSS_301)` 拒绝
- 检测到 AWS SDK 全局 `com.amazonaws.sdk.disableCertChecking=true` 时拒绝启动，防止全局 trust-all 覆盖客户端级信任边界

### 兼容性与使用边界

- 未配置 `trusted-ca-file` 时，保持既有 HTTP / HTTPS 客户端构建路径与 JRE 默认信任行为
- 预签名 URL 由外部浏览器、移动端或调用方 HTTP 客户端访问；外部客户端需要自行信任私有 CA
- 未升级 AWS SDK、Spring Boot 或既有 S3 操作 API

### 测试

- 新增进程内 HTTPS S3 / STS 夹具，验证正确私有 CA 可完成真实 TLS 握手与最小协议请求
- 验证未配置 CA 的私有证书链失败、主机名不匹配失败以及 HTTP endpoint 未配置 CA 的回归路径
- 覆盖 PEM、DER、多证书、不可读、损坏、空文件、叶证书、KeyUsage、有效期、非法 endpoint 与 AWS SDK 全局关闭证书校验
- 覆盖 Spring 启动期失败：配置 Bean 已进入初始化阶段时，两个 AWS 客户端 Bean 均未创建
- 覆盖复合 TrustManager 的默认信任优先、默认失败后自定义 CA 校验，以及相同 Subject 不同证书的 issuer 保留语义
