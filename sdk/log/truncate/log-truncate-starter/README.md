# log-truncate-starter

对超长日志/对象进行安全截断的 Spring Boot Starter，避免控制台、日志文件或网络传输因大对象导致内存、磁盘或带宽暴涨。

## 功能
- 字符串、异常堆栈、任意 Java 对象统一截断
- UTF-8 多字节安全，不会截碎中文
- 深度与长度双重阈值，防止深层嵌套或超大字段
- Spring Boot 自动配置，零代码侵入，开箱即用
- 纯本地计算，无外部依赖

## 快速开始

### 1. 引入依赖（Gradle 示例）
本模块对以下依赖使用 `compileOnly`，**不会传递**，由引用者**自行决定版本**与**是否使用**：
```groovy
dependencies {
    // ① starter 本身
    implementation 'io.github.surezzzzzz:sdk-log-truncate-starter:1.0.0'

    // ② Jackson（示例，版本自理）
    // 如果你项目里已管理 Jackson，直接沿用即可；
    // 若未管理，可任选以下一种方式：
    // compileOnly 'com.fasterxml.jackson.core:jackson-databind:你的版本'
    // implementation 'com.fasterxml.jackson.core:jackson-databind:你的版本'
    
    // ③ 可选：Spring Boot 配置处理器（仅编译期，IDE 提示用）
    compileOnly 'org.springframework.boot:spring-boot-configuration-processor'
}
```
> 运行时若缺失 Jackson 会抛 `ClassNotFoundException`，请按项目已有版本补全即可。

### 2. 直接注入使用
```java
@Autowired
private LogTruncator logTruncator;

String shortLog = logTruncator.truncate(anyHugeObject);
log.info("{}", shortLog);
```

### 3. 配置项（`application.yml`）
```yaml
io:
  github:
    surezzzzzz:
      sdk:
        log:
          truncate:
            max-total-bytes: 8192          # 整体日志字符串最大字节数(UTF-8)，默认 8K
            max-field-chars: 1024          # 单字段字符截断阈值，默认 1K
            max-depth: 8                   # 对象展开最大深度，默认 8
            ellipsis: "..."                # 截断后缀，默认 "..."
            truncated-note-template: " [truncated {dropped}]"   # 提示模板
            depth-exceeded-placeholder: "__depth_exceeded__"  # 深度超限占位符
```

## 设计原则
- 安全：截断后字符串永远合法 UTF-8
- 轻量：不依赖第三方 RPC、存储或队列
- 无侵入：仅提供工具 Bean，不影响业务代码
- 可审计：截断结果带 `[truncated ...]` 标记，方便排查

