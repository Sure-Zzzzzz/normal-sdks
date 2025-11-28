# prometheus-core

Prometheus 通用协议定义模块，基于 Protobuf 生成，提供读写远程存储的 Java 绑定。

## 功能
- `Types.java`：指标元数据、时间序列、标签、样本等基础结构
- `Remote.java`：远程读写请求/响应封装（WriteRequest / ReadRequest / Query / QueryResult 等）
- 零业务语义，纯协议层，可直接嵌入任何监控或网关组件

## 快速开始
```gradle
dependencies {
    implementation 'io.github.surezzzzzz.sdk:prometheus-core:1.0.0'
}
```

## 使用示例
```java
import prometheus.Remote.*;
import prometheus.Types.*;

// 构造写请求
WriteRequest request = WriteRequest.newBuilder()
    .addTimeseries(TimeSeries.newBuilder()
        .addLabels(Label.newBuilder().setName("__name__").setValue("cpu_usage").build())
        .addSamples(Sample.newBuilder().setValue(0.85).setTimestamp(System.currentTimeMillis()).build())
    )
    .build();

// 序列化后通过 HTTP/GRPC 发送给远程存储
byte[] data = request.toByteArray();
```

## 生成说明
源码由 `prometheus.proto` 经 `protoc` 插件生成，如需修改请：
1. 更新 `.proto` 文件
2. 重新执行 `generateProto` 任务
3. 仅提交生成后的 Java 文件，无需额外依赖插件

## 依赖
- `com.google.protobuf:protobuf-java:3.25.1`（api 传递，无需额外声明）
- `com.google.protobuf:protobuf-java-util:3.25.1`（提供 JSON 打印支持）

## 版本
当前版本：`1.0.0`（见 version.properties）

## 注意事项
- 本模块仅包含协议对象，**不包含**客户端、序列化或网络实现
- 所有字段均使用 Protobuf 基本类型，无自定义枚举或业务常量
- 建议与 `prometheus-client-starter` 或其他远程存储客户端搭配使用