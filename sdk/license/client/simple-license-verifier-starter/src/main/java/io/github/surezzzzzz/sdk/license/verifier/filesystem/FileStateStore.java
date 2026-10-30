package io.github.surezzzzzz.sdk.license.verifier.filesystem;

import io.github.surezzzzzz.sdk.license.core.spi.StateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件状态存储默认实现：主副本+原子替换+版本键。
 *
 * <p>持久化临时文件后原子替换并验证；读-判定-写无法原子时拒绝共享模式由锁文件承担（后续项）。
 * 状态只增不减：T_max/序号/水位仅接受更大值。</p>
 *
 * @author surezzzzzz
 */
public class FileStateStore implements StateStore {

    private final Path stateFile;
    private final Path lockFile;
    private final Map<String, String> cache = new ConcurrentHashMap<String, String>();

    public FileStateStore(Path stateDirectory, String fileName) {
        this.stateFile = stateDirectory.resolve(fileName);
        this.lockFile = stateDirectory.resolve(fileName + ".lock");
        load();
    }

    @Override
    public void persistMaxTrustedTime(String stateKey, long epochSeconds) {
        persistLong(stateKey + ".tmax", epochSeconds, true);
    }

    @Override
    public OptionalLong loadMaxTrustedTime(String stateKey) {
        return loadLong(stateKey + ".tmax");
    }

    @Override
    public void persistRevocationSequence(String issuerId, String productId, long sequence) {
        persistLong("revocation." + issuerId + "." + productId + ".sequence", sequence, true);
    }

    @Override
    public OptionalLong loadRevocationSequence(String issuerId, String productId) {
        return loadLong("revocation." + issuerId + "." + productId + ".sequence");
    }

    @Override
    public void persistNegativeWatermark(String stateKey, String scope, long version) {
        persistLong(stateKey + ".negative." + scope, version, true);
    }

    @Override
    public OptionalLong loadNegativeWatermark(String stateKey, String scope) {
        return loadLong(stateKey + ".negative." + scope);
    }

    @Override
    public void persistRecoveryGeneration(String stateKey, long generation) {
        persistLong(stateKey + ".recoveryGeneration", generation, true);
    }

    @Override
    public OptionalLong loadRecoveryGeneration(String stateKey) {
        return loadLong(stateKey + ".recoveryGeneration");
    }

    @Override
    public String buildStateKey(String issuerId, String productId, String customerId, String grantId,
                                String stableObjectKey) {
        // ID允许保留冒号；长度分帧避免不同对象拼成同一属性键，保持水位按授权对象隔离。
        return frame(issuerId) + frame(productId) + frame(customerId) + frame(grantId) + frame(stableObjectKey);
    }

    private synchronized void persistLong(String key, long value, boolean monotonic) {
        synchronized (FileStateStore.class) {
            try {
                Path parent = stateFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                // 进程内多个实例也要串行，否则Windows会把同一进程的重叠锁视为错误。
                try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
                    Map<String, String> current = readState();
                    cache.clear();
                    cache.putAll(current);
                    if (monotonic) {
                        String existing = current.get(key);
                        if (existing != null && parseStateLong(existing) >= value) {
                            return; // 只增不减：以最新磁盘快照为准，旧实例不能覆盖高水位
                        }
                    }
                    // 先持久化候选快照，再提交内存水位。写盘失败不能污染缓存。
                    current.put(key, String.valueOf(value));
                    flush(current);
                    cache.put(key, String.valueOf(value));
                }
            } catch (IOException | OverlappingFileLockException failure) {
                throw new IllegalStateException("状态文件写入失败", failure);
            }
        }
    }

    private synchronized OptionalLong loadLong(String key) {
        refresh();
        String value = cache.get(key);
        return value == null ? OptionalLong.empty() : OptionalLong.of(parseStateLong(value));
    }

    private void load() {
        refresh();
    }

    private void refresh() {
        if (!Files.exists(stateFile)) {
            if (!cache.isEmpty()) {
                throw new IllegalStateException("状态文件缺失");
            }
            return;
        }
        Map<String, String> loaded = readState();
        cache.clear();
        cache.putAll(loaded);
    }

    private Map<String, String> readState() {
        if (!Files.exists(stateFile)) {
            return new HashMap<String, String>();
        }
        try {
            Properties properties = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            Map<String, String> loaded = new HashMap<String, String>();
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                loaded.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            return loaded;
        } catch (IOException failure) {
            throw new IllegalStateException("状态文件读取失败", failure);
        }
    }

    private void flush(Map<String, String> candidate) {
        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : candidate.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }
        try {
            Path parent = stateFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(stateFile.getParent(), "license-state", ".tmp");
            try (java.io.Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                properties.store(writer, "license verifier state");
            }
            Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("状态文件写入失败", failure);
        }
    }

    /** 状态文件损坏值兜底：解析失败按状态损坏处理（STATE_RECOVERY_REQUIRED 路径），不抛裸运行时异常。 */
    private static long parseStateLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException corrupt) {
            throw new IllegalStateException("状态文件数值损坏", corrupt);
        }
    }

    private static String frame(String value) {
        if (value == null) {
            throw new IllegalArgumentException("状态键字段不能为空");
        }
        return value.length() + ":" + value;
    }
}
