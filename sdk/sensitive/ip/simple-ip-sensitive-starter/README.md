# Simple IP Sensitive Starter

一个轻量级的 Spring Boot Starter，用于 IPv4 和 IPv6 地址的脱敏处理。

## 特性

- ✅ 支持 IPv4 和 IPv6 地址脱敏
- ✅ 支持 CIDR 格式脱敏
- ✅ 自动识别 IP 类型
- ✅ 支持自定义脱敏位置（1-based 索引）
- ✅ 支持自定义掩码字符
- ✅ 提供 Jackson 序列化器注解支持
- ✅ 线程安全
- ✅ 零依赖（除 Spring Boot 和 Jackson）

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-ip-sensitive-starter:1.0.0'
}
```

### 2. 配置（可选）

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        sensitive:
          ip:
            enable: true  # 默认 true
            default-mask-positions:
              ipv4: [ 3, 4 ]  # 默认脱敏 IPv4 的第 3、4 段
              ipv6: [ 5, 6, 7, 8 ]  # 默认脱敏 IPv6 的后 4 组
            ipv4-mask-char: "*"  # 默认 "*"
            ipv6-mask-char: "****"  # 默认 "****"
```

### 3. 使用方式

#### 方式一：编程式调用

```java

@Autowired
private SimpleIpSensitiveHelper helper;

// 自动识别 IP 类型并脱敏
String masked = helper.mask("192.168.1.1");
// 输出: 192.168.*.*

// 自定义脱敏位置
String masked = helper.mask("192.168.1.1", new int[]{1, 2});
// 输出: *.*.1.1

// 自定义掩码字符
String masked = helper.mask("192.168.1.1", new int[]{3, 4}, "X");
// 输出: 192.168.X.X

// CIDR 脱敏
String masked = helper.maskCidr("192.168.0.0/16");
// 输出: 192.*.0.0/16
```

#### 方式二：注解式（Jackson 序列化）

```java

@Data
public class AccessLog {
    @SimpleIpSensitize  // 使用默认策略
    private String clientIp;

    @SimpleIpSensitize(mask = {1, 2})  // 自定义位置
    private String serverIp;

    @SimpleIpSensitize(mask = {3, 4}, maskChar = "X")  // 自定义位置和字符
    private String proxyIp;
}

// 序列化时自动脱敏
AccessLog log = new AccessLog("192.168.1.1", "10.0.0.1", "172.16.0.1");
String json = objectMapper.writeValueAsString(log);
// {"clientIp":"192.168.*.*","serverIp":"*.*.0.1","proxyIp":"172.16.X.X"}
```

## 详细说明

### IPv4 脱敏

#### 默认策略

默认脱敏第 3、4 段：

```java
helper.mask("192.168.1.1");
// 输出: 192.168.*.*
```

#### 自定义位置

位置索引从 1 开始（符合人类认知）：

```java
helper.mask("192.168.1.1",new int[] {
    1, 2
});  // 脱敏第 1、2 段
// 输出: *.*.1.1

        helper.

mask("192.168.1.1",new int[] {
    2, 3
});  // 脱敏第 2、3 段
// 输出: 192.*.*.1
```

#### CIDR 脱敏

算法：`networkBytes = (prefixLength + 7) / 8`，脱敏网络位的最后一个字节，主机位补 0。

```java
helper.maskCidr("10.0.0.0/8");
// 输出: *.0.0.0/8

helper.

maskCidr("192.168.0.0/16");
// 输出: 192.*.0.0/16

helper.

maskCidr("192.168.1.0/24");
// 输出: 192.168.*.0/24

helper.

maskCidr("192.168.1.1/32");
// 输出: 192.168.*.* (使用默认策略，不带 /32)
```

### IPv6 脱敏

#### 默认策略

默认脱敏后 4 组：

```java
helper.mask("2001:db8::1");
// 输出: 2001:0db8:0000:0000:****:****:****:****
```

#### 自定义位置

位置索引从 1 开始，支持压缩格式：

```java
helper.mask("2001:db8::1",new int[] {
    1, 2
});
// 输出: ****:****:0000:0000:0000:0000:0000:0001

        helper.

mask("::1",new int[] {
    8
});
// 输出: 0000:0000:0000:0000:0000:0000:0000:****
```

#### CIDR 脱敏

算法：`networkGroups = (prefixLength + 15) / 16`，脱敏网络位的最后一个组，主机位补 0000。

```java
helper.maskCidr("2001:db8::/32");
// 输出: 2001:db8:****:0000:0000:0000:0000:0000/32

helper.

maskCidr("2001:db8:85a3::/48");
// 输出: 2001:db8:85a3:****:0000:0000:0000:0000/48

helper.

maskCidr("2001:db8:85a3:8d3::/64");
// 输出: 2001:db8:85a3:8d3:****:0000:0000:0000/64

helper.

maskCidr("2001:db8::1/128");
// 输出: 2001:0db8:****:****:****:****:****:**** (使用默认策略，不带 /128)
```

### 自动识别

`SimpleIpSensitiveHelper` 会自动识别 IPv4 和 IPv6：

```java
helper.mask("192.168.1.1");  // 自动识别为 IPv4
helper.

mask("2001:db8::1");  // 自动识别为 IPv6
helper.

maskCidr("192.168.0.0/16");  // 自动识别为 IPv4 CIDR
helper.

maskCidr("2001:db8::/32");  // 自动识别为 IPv6 CIDR
```

### 专用 Helper

如果明确知道 IP 类型，可以直接使用专用 Helper：

```java

@Autowired
private SimpleIpv4SensitiveHelper ipv4Helper;

@Autowired
private SimpleIpv6SensitiveHelper ipv6Helper;

// IPv4 专用
ipv4Helper.

mask("192.168.1.1");
ipv4Helper.

maskCidr("192.168.0.0/16");

// IPv6 专用
ipv6Helper.

mask("2001:db8::1");
ipv6Helper.

maskCidr("2001:db8::/32");
```

## 异常处理

### 异常类型

- `InvalidIpFormatException` - IP 格式不合法
- `EmptyMaskPositionsException` - 位置数组为空
- `MaskPositionOutOfBoundsException` - 位置越界
- `InvalidCidrFormatException` - CIDR 格式不合法
- `InvalidCidrPrefixException` - CIDR 前缀长度不合法

### 异常示例

```java
// 无效 IP 格式
helper.mask("256.1.1.1");  // 抛出 InvalidIpFormatException

// 空位置数组
helper.

mask("192.168.1.1",new int[] {
});  // 抛出 EmptyMaskPositionsException

// 位置越界
        helper.

mask("192.168.1.1",new int[] {
    5
});  // 抛出 MaskPositionOutOfBoundsException

// 无效 CIDR
        helper.

maskCidr("192.168.1.1");  // 抛出 InvalidCidrFormatException
helper.

maskCidr("192.168.1.1/33");  // 抛出 InvalidCidrPrefixException
```

### Jackson 序列化器异常处理

序列化器内部捕获所有异常，脱敏失败时返回原值，避免影响业务：

```java

@SimpleIpSensitize
private String ip;  // 如果脱敏失败，序列化时返回原值
```

## 性能优化

- ✅ 使用 HashSet 优化位置查找（O(n*m) → O(n+m)）
- ✅ StringBuilder 预分配容量，减少内存重分配
- ✅ 避免重复 IP 验证
- ✅ 线程安全的单例设计

## 设计原则

### 1-based 索引

位置索引从 1 开始，符合人类认知：

- IPv4: 第 1 段 = 192, 第 2 段 = 168, 第 3 段 = 1, 第 4 段 = 1
- IPv6: 第 1 组 = 2001, 第 2 组 = 0db8, ...

### 脱敏语义

参数指定的是**要脱敏的位置**，而不是要保留的位置：

```java
mask("192.168.1.1",new int[] {
    3, 4
})  // 脱敏第 3、4 段
// 输出: 192.168.*.*
```

### 无效 IP 处理

`SimpleIpSensitiveHelper` 对无法识别的 IP 返回原值，不抛异常：

```java
helper.mask("not-an-ip");  // 返回 "not-an-ip"
```

专用 Helper 会抛出异常：

```java
ipv4Helper.mask("not-an-ip");  // 抛出 InvalidIpFormatException
```

## 线程安全

所有 Helper 类都是线程安全的：

- Helper 类是 Spring 单例
- 方法无状态（只操作参数）
- 配置类线程安全

## 许可证

Apache License 2.0

## 作者

surezzzzzz
