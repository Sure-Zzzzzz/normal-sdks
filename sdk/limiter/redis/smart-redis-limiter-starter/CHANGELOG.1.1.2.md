# Changelog

## [1.1.2] - 2026-05-11

### Performance

- **滑动窗口 ZREMRANGEBYSCORE 优化**：正常流量下跳过清理，仅当配额即将耗尽（`remaining <= 0`）时才触发过期数据清理和重新计数，Redis CPU 开销大幅降低，无精度损失
