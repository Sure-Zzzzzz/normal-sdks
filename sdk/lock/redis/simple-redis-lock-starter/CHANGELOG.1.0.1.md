# Changelog

## [1.0.1] - 2026-03-19

### 🐛 Bug 修复

#### 修复 unlock 方法的竞态条件问题

**问题描述：**
原实现中 `unlock()` 方法使用分离的 `get` 和 `delete` 操作，存在竞态条件：
```java
// 原实现（存在问题）
String currentValue = ops.get(lockKey);
if (lockValue.equals(currentValue)) {
    simpleRedisLockRedisTemplate.delete(lockKey);  // 非原子操作
}
```

在高并发场景下可能出现：
1. 线程A检查锁值匹配
2. 锁恰好过期，线程B获取了新锁
3. 线程A误删除了线程B的锁

**解决方案：**
使用 Lua 脚本实现原子性的检查和删除操作：
```java
// 新实现（原子操作）
private static final String UNLOCK_SCRIPT =
    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
    "    return redis.call('del', KEYS[1]) " +
    "else " +
    "    return 0 " +
    "end";

public void unlock(String lockKey, String lockValue) {
    Long result = simpleRedisLockRedisTemplate.execute(
        UNLOCK_REDIS_SCRIPT,
        Collections.singletonList(lockKey),
        lockValue
    );
    // 处理结果...
}
```

**改进效果：**
- ✅ 保证检查和删除操作的原子性
- ✅ 避免误删除其他客户端的锁
- ✅ 提高分布式锁的安全性和可靠性

### 🧪 测试改进

#### 移除嵌入式 Redis 依赖

**变更说明：**
- 移除了 `embedded-redis` 依赖
- 测试改为使用本地 Redis 实例（localhost:6379）
- 添加了 `application.yml` 配置文件用于测试环境

**原因：**
- 嵌入式 Redis 在 Windows 环境下可能遇到内存分配问题
- 使用真实 Redis 实例更接近生产环境
- 提高测试的稳定性和可靠性

### ⚡ 升级说明

- ✅ 完全向后兼容，API 无变化
- ✅ 无需修改现有代码
- ✅ 建议升级以获得更安全的锁实现
