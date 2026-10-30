package io.github.surezzzzzz.sdk.license.verifier.filesystem;

import io.github.surezzzzzz.sdk.license.core.spi.StateStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
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
        persistLong(revocationSequenceKey(issuerId, productId), sequence, true,
                legacyRevocationSequenceKey(issuerId, productId));
    }

    @Override
    public OptionalLong loadRevocationSequence(String issuerId, String productId) {
        OptionalLong current = loadLong(revocationSequenceKey(issuerId, productId));
        OptionalLong legacy = loadLong(legacyRevocationSequenceKey(issuerId, productId));
        if (!current.isPresent()) {
            return legacy;
        }
        if (!legacy.isPresent()) {
            return current;
        }
        return OptionalLong.of(Math.max(current.getAsLong(), legacy.getAsLong()));
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
        persistLong(key, value, monotonic, new String[0]);
    }

    private synchronized void persistLong(String key, long value, boolean monotonic, String... aliases) {
        synchronized (FileStateStore.class) {
            try {
                Path parent = stateFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                // 进程内多个实例也要串行，否则Windows会把同一进程的重叠锁视为错误。
                boolean lockCreated = createLockMarker();
                try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
                        FileLock ignored = channel.lock()) {
                    if (!Files.exists(stateFile) && !lockCreated) {
                        throw new IllegalStateException("状态文件缺失");
                    }
                    Map<String, String> current = readState();
                    cache.clear();
                    cache.putAll(current);
                    if (monotonic) {
                        String existing = highestExistingValue(current, key, aliases);
                        if (existing != null && parseStateLong(existing) >= value) {
                            if (!existing.equals(current.get(key))) {
                                current.put(key, existing);
                                flush(current);
                                cache.clear();
                                cache.putAll(current);
                            }
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

    private static String highestExistingValue(Map<String, String> current, String key, String[] aliases) {
        String highest = current.get(key);
        long highestValue = highest == null ? Long.MIN_VALUE : parseStateLong(highest);
        for (String alias : aliases) {
            String candidate = current.get(alias);
            if (candidate != null && (highest == null || parseStateLong(candidate) > highestValue)) {
                highest = candidate;
                highestValue = parseStateLong(candidate);
            }
        }
        return highest;
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
            // 锁文件在首次持久化前不会创建；它存在时说明该目录已经尝试提交状态，缺主文件必须失败关闭。
            if (!cache.isEmpty() || Files.exists(lockFile)) {
                throw new IllegalStateException("状态文件缺失");
            }
            return;
        }
        Map<String, String> loaded = readState();
        cache.clear();
        cache.putAll(loaded);
        createLockMarker();
    }

    private boolean createLockMarker() {
        try {
            Files.createFile(lockFile);
            return true;
        } catch (FileAlreadyExistsException exists) {
            return false;
        } catch (IOException failure) {
            throw new IllegalStateException("状态锁文件创建失败", failure);
        }
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

    private static String revocationSequenceKey(String issuerId, String productId) {
        return "revocation.v2." + frame(issuerId) + frame(productId) + ".sequence";
    }

    private static String legacyRevocationSequenceKey(String issuerId, String productId) {
        return "revocation." + issuerId + "." + productId + ".sequence";
    }
}
