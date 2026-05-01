package fun.imiku.bot.xiaoxiang.ratelimit;

import fun.imiku.bot.xiaoxiang.config.ExternalProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class XXRateLimiter {
    private static final long UNSET_TAT = Long.MIN_VALUE;
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final ExternalProperties externalProperties;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    public XXRateLimiter(ExternalProperties externalProperties) {
        this.externalProperties = externalProperties;
    }

    @PostConstruct
    private void init() {
        applyConfig();
        log.info("速率控制器加载完毕");
    }

    /**
     * 应用当前外部配置，并清空旧的限流状态。
     * <p>
     * 配置变更后调用此方法即可重新生成 GCRA 参数和并发信号量。
     */
    public void applyConfig() {
        snapshot.set(Snapshot.from(externalProperties.getCommon()));
    }

    /**
     * 查询当前是否已经超过任意一个已配置窗口。
     * <p>
     * 此方法只做查询，不会创建状态，也不会消耗请求额度。
     */
    public boolean exceeds(Long groupId, Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Snapshot current = currentSnapshot();
        long now = System.nanoTime();
        synchronized (current) {
            List<GcraWindow> groupWindows = current.groupWindows(groupId);

            GcraState globalState = current.globalStates.get(userId);
            GcraState groupState = groupId == null ? null : current.groupStates.get(new GroupUserKey(groupId, userId));

            return stateExceeds(globalState, current.globalWindows, now) || stateExceeds(groupState, groupWindows, now);
        }
    }

    /**
     * 尝试消耗一次请求额度。
     * <p>
     * 返回 true 表示当前没有超限并且已经完成消耗；返回 false 表示至少一个窗口已经超限，本次不会消耗任何窗口。
     */
    public boolean consume(Long groupId, Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Snapshot current = currentSnapshot();
        long now = System.nanoTime();
        synchronized (current) {
            List<GcraWindow> groupWindows = current.groupWindows(groupId);

            GroupUserKey groupUserKey = groupId == null ? null : new GroupUserKey(groupId, userId);
            GcraState globalState = current.globalStates.get(userId);
            GcraState groupState = groupUserKey == null ? null : current.groupStates.get(groupUserKey);

            // 多级窗口必须一起通过；只要有一个窗口拒绝，本次请求就不推进任何状态。
            if (stateExceeds(globalState, current.globalWindows, now) || stateExceeds(groupState, groupWindows, now)) {
                return false;
            }

            globalState = stateForConsume(current.globalStates, userId, current.globalWindows);
            groupState = groupUserKey == null ? null : stateForConsume(current.groupStates, groupUserKey, groupWindows);

            if (globalState != null) {
                globalState.consume(current.globalWindows, now);
            }
            if (groupState != null) {
                groupState.consume(groupWindows, now);
            }
            return true;
        }
    }

    /**
     * 尝试获取一个全局 bot 并发许可。
     *
     * @return 需要释放的许可；如果已经达到并发上限，则返回 null
     */
    public Permit obtain() {
        Snapshot current = currentSnapshot();
        if (!current.semaphore.tryAcquire()) {
            return null;
        }
        return new Permit(current.semaphore);
    }

    /**
     * 释放由 obtain 获取到的并发许可。
     */
    public void release(Permit permit) {
        if (permit != null) {
            permit.release();
        }
    }

    private Snapshot currentSnapshot() {
        Snapshot current = snapshot.get();
        if (current != null) {
            return current;
        }
        applyConfig();
        return snapshot.get();
    }

    private static boolean stateExceeds(GcraState state, List<GcraWindow> windows, long now) {
        return state != null && !windows.isEmpty() && state.exceeds(windows, now);
    }

    private static <K> GcraState stateForConsume(Map<K, GcraState> states, K key, List<GcraWindow> windows) {
        if (windows.isEmpty()) {
            return null;
        }
        return states.computeIfAbsent(key, ignored -> new GcraState(windows.size()));
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        public void release() {
            if (released.compareAndSet(false, true)) {
                semaphore.release();
            }
        }

        @Override
        public void close() {
            release();
        }
    }

    private static final class Snapshot {
        private final List<GcraWindow> globalWindows;
        private final List<GcraWindow> defaultGroupWindows;
        private final Map<Long, List<GcraWindow>> groupWindows;
        // 状态跟随配置快照保存；applyConfig 替换快照时，旧状态会自然失效。
        private final Map<Long, GcraState> globalStates = new HashMap<>();
        private final Map<GroupUserKey, GcraState> groupStates = new HashMap<>();
        private final Semaphore semaphore;

        private Snapshot(
                List<GcraWindow> globalWindows,
                List<GcraWindow> defaultGroupWindows,
                Map<Long, List<GcraWindow>> groupWindows,
                Semaphore semaphore
        ) {
            this.globalWindows = globalWindows;
            this.defaultGroupWindows = defaultGroupWindows;
            this.groupWindows = groupWindows;
            this.semaphore = semaphore;
        }

        private static Snapshot from(ExternalProperties.Common common) {
            // 在应用配置时一次性把外部窗口配置转换成 GCRA 计算参数，避免请求路径重复解析。
            return new Snapshot(
                    toWindows(common.getGlobalRateLimit(), "common.globalRateLimit"),
                    toWindows(common.getDefaultGroupRateLimit(), "common.defaultGroupRateLimit"),
                    toGroupWindows(common.getGroupRateLimit()),
                    new Semaphore(toSemaphorePermits(common.getBotMaxConcurrency()), true)
            );
        }

        private List<GcraWindow> groupWindows(Long groupId) {
            if (groupId == null) {
                return List.of();
            }
            // 具体群配置优先；即使配置为空列表，也表示显式覆盖 defaultGroupRateLimit。
            List<GcraWindow> configured = groupWindows.get(groupId);
            return configured == null ? defaultGroupWindows : configured;
        }
    }

    private static List<GcraWindow> toWindows(
            List<ExternalProperties.RateLimitConfig> configs,
            String configPath
    ) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }

        List<GcraWindow> windows = new ArrayList<>(configs.size());
        for (int i = 0; i < configs.size(); i++) {
            ExternalProperties.RateLimitConfig config = configs.get(i);
            if (config == null) {
                continue;
            }
            // 每个配置项对应一个独立窗口；多级窗口后续会逐一检查。
            windows.add(GcraWindow.from(config, configPath + "[" + i + "]"));
        }
        return List.copyOf(windows);
    }

    private static Map<Long, List<GcraWindow>> toGroupWindows(
            Map<Long, List<ExternalProperties.RateLimitConfig>> configs
    ) {
        if (configs == null || configs.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<GcraWindow>> windows = new HashMap<>();
        configs.forEach((groupId, groupConfigs) -> {
            if (groupId == null) {
                throw new IllegalArgumentException("common.groupRateLimit contains a null group id");
            }
            windows.put(groupId, toWindows(groupConfigs, "common.groupRateLimit[" + groupId + "]"));
        });
        return Map.copyOf(windows);
    }

    private static int toSemaphorePermits(long configuredPermits) {
        if (configuredPermits < 0) {
            throw new IllegalArgumentException("common.botMaxConcurrency must not be negative");
        }
        if (configuredPermits > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("common.botMaxConcurrency must not exceed " + Integer.MAX_VALUE);
        }
        return (int) configuredPermits;
    }

    private record GcraWindow(long intervalNanos, long toleranceNanos) {

        private static GcraWindow from(ExternalProperties.RateLimitConfig config, String configPath) {
            if (config.getWindowsSeconds() <= 0) {
                throw new IllegalArgumentException(configPath + ".windowsSeconds must be positive");
            }
            if (config.getMaxRequests() <= 0) {
                throw new IllegalArgumentException(configPath + ".maxRequests must be positive");
            }

            long burstRequests = config.getBurstRequests();
            if (burstRequests <= 0 || burstRequests > config.getMaxRequests()) {
                burstRequests = config.getMaxRequests();
            }

            // GCRA 使用纳秒级 TAT 计算，这里显式检查，避免超大窗口在单位换算时溢出。
            if (config.getWindowsSeconds() > Long.MAX_VALUE / NANOS_PER_SECOND) {
                throw new IllegalArgumentException(configPath + ".windowsSeconds is too large");
            }
            long windowNanos = config.getWindowsSeconds() * NANOS_PER_SECOND;

            // interval 是两次理论到达时间之间的间隔；tolerance 则表达允许的突发量。
            long intervalNanos = ceilDiv(windowNanos, config.getMaxRequests());
            long toleranceNanos;
            try {
                toleranceNanos = Math.multiplyExact(Math.max(0, burstRequests - 1), intervalNanos);
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException(configPath + ".burstRequests is too large", ex);
            }
            return new GcraWindow(intervalNanos, toleranceNanos);
        }

        private static long ceilDiv(long dividend, long divisor) {
            long quotient = dividend / divisor;
            return dividend % divisor == 0 ? quotient : quotient + 1;
        }
    }

    private record GcraState(long[] theoreticalArrivalTimes) {
        private GcraState(int windowCount) {
            this(new long[windowCount]);
            Arrays.fill(this.theoreticalArrivalTimes, UNSET_TAT);
        }

        private boolean exceeds(List<GcraWindow> windows, long now) {
            for (int i = 0; i < windows.size(); i++) {
                long tat = theoreticalArrivalTimes[i];
                if (tat == UNSET_TAT) {
                    continue;
                }

                GcraWindow window = windows.get(i);
                long earliestAllowedAt = tat - window.toleranceNanos;
                // 当前时间早于最早允许时间，说明这个窗口已经超限。
                if (now < earliestAllowedAt) {
                    return true;
                }
            }
            return false;
        }

        private void consume(List<GcraWindow> windows, long now) {
            for (int i = 0; i < windows.size(); i++) {
                long tat = theoreticalArrivalTimes[i];
                long base = tat == UNSET_TAT ? now : Math.max(tat, now);
                // GCRA 消耗一次请求，就是把对应窗口的理论到达时间向后推进一个 interval。
                theoreticalArrivalTimes[i] = base + windows.get(i).intervalNanos;
            }
        }
    }

    private record GroupUserKey(Long groupId, Long userId) {
        private GroupUserKey {
            Objects.requireNonNull(groupId, "groupId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
        }
    }
}
