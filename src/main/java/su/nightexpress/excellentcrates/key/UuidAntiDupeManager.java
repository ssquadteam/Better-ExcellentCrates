package su.nightexpress.excellentcrates.key;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.config.Config;
import su.nightexpress.excellentcrates.config.Keys;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.PDCUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * UUID Anti-Dupe Manager for ExcellentCrates
 */
public class UuidAntiDupeManager extends AbstractManager<CratesPlugin> {

    private final Map<UUID, Boolean> validKeyCache;
    private final Map<UUID, Boolean> usedKeyCache;
    private final Map<UUID, Long> creationTimeCache;

    private long totalKeysGenerated;
    private long totalDupeAttempts;
    private long totalValidUsages;

    public UuidAntiDupeManager(@NotNull CratesPlugin plugin) {
        super(plugin);
        int maxValid = Math.max(1, Config.ANTI_DUPE_CACHE_MAX_VALID.get());
        int maxUsed = Math.max(1, Config.ANTI_DUPE_CACHE_MAX_USED.get());
        int maxCreated = Math.max(1, Config.ANTI_DUPE_CACHE_MAX_CREATION_TIMES.get());

        this.validKeyCache = createLruMap(maxValid);
        this.usedKeyCache = createLruMap(maxUsed);
        this.creationTimeCache = createLruMap(maxCreated);

        this.totalKeysGenerated = 0;
        this.totalDupeAttempts = 0;
        this.totalValidUsages = 0;
    }

    @Override
    protected void onLoad() {
        this.plugin.info("UUID Anti-Dupe LRU caches initialized (valid: " + this.validKeyCache.size() + 
            ", used: " + this.usedKeyCache.size() + ", created: " + this.creationTimeCache.size() + ").");
    }

    @Override
    protected void onShutdown() {
        this.saveData();
        this.validKeyCache.clear();
        this.usedKeyCache.clear();
        this.creationTimeCache.clear();
    }

    /**
     * Injects a unique UUID into a physical key item
     * @param keyItem The key item to inject UUID into
     * @return The UUID that was injected
     */
    @NotNull
    public UUID injectUuid(@NotNull ItemStack keyItem) {
        UUID keyUuid = UUID.randomUUID();
        PDCUtil.set(keyItem, Keys.keyUuid, keyUuid);

        this.registerValidUuid(keyUuid);

        this.totalKeysGenerated++;
        this.plugin.debug("Injected UUID " + keyUuid + " into key item");
        
        return keyUuid;
    }

    /**
     * Returns creation timestamp for a key UUID, if known.
     * @param keyUuid UUID of the key
     * @return millis since epoch or -1 if unknown
     */
    public long getCreationTime(@NotNull UUID keyUuid) {
        Long cached = this.creationTimeCache.get(keyUuid);
        if (cached != null) return cached;
        Long fromDb = this.plugin.getDataHandler().getKeyUuidCreationTime(keyUuid);
        if (fromDb != null) {
            this.creationTimeCache.put(keyUuid, fromDb);
            return fromDb;
        }
        return -1L;
    }

    /**
     * Gets the UUID from a key item
     * @param keyItem The key item to extract UUID from
     * @return The UUID if present, null otherwise
     */
    @Nullable
    public UUID getKeyUuid(@NotNull ItemStack keyItem) {
        return PDCUtil.getUUID(keyItem, Keys.keyUuid).orElse(null);
    }

    /**
     * Validates if a key item has a valid UUID
     * @param keyItem The key item to validate
     * @param player The player using the key (for logging)
     * @return true if valid, false if invalid/duped
     */
    public boolean validateKeyUuid(@NotNull ItemStack keyItem, @Nullable Player player) {
        UUID keyUuid = this.getKeyUuid(keyItem);
        
        if (keyUuid == null) {
            this.plugin.warn("Key validation failed: No UUID found in key item" + 
                (player != null ? " (Player: " + player.getName() + ")" : ""));
            return false;
        }

        if (Boolean.TRUE.equals(this.usedKeyCache.get(keyUuid))) {
            this.totalDupeAttempts++;
            this.plugin.warn("ANTI-DUPE: Already used key UUID detected: " + keyUuid + 
                (player != null ? " (Player: " + player.getName() + ")" : ""));
            
            if (player != null) {
                this.notifyAdminsOfDupeAttempt(player, keyUuid);
            }
            
            return false;
        }

        if (Boolean.TRUE.equals(this.validKeyCache.get(keyUuid))) {
            this.totalValidUsages++;
            return true;
        }

        Boolean used = this.plugin.getDataHandler().isKeyUuidUsed(keyUuid);
        if (used == null) {
            this.totalDupeAttempts++;
            this.plugin.warn("ANTI-DUPE: Invalid key UUID detected: " + keyUuid +
                (player != null ? " (Player: " + player.getName() + ")" : ""));
            if (player != null) this.notifyAdminsOfDupeAttempt(player, keyUuid);
            return false;
        }
        if (used) {
            this.usedKeyCache.put(keyUuid, Boolean.TRUE);
            this.totalDupeAttempts++;
            this.plugin.warn("ANTI-DUPE: Already used key UUID detected: " + keyUuid +
                (player != null ? " (Player: " + player.getName() + ")" : ""));
            if (player != null) this.notifyAdminsOfDupeAttempt(player, keyUuid);
            return false;
        }

        this.validKeyCache.put(keyUuid, Boolean.TRUE);
        this.totalValidUsages++;
        return true;
    }

    /**
     * Marks a key UUID as used (consumed)
     * @param keyUuid The UUID to mark as used
     */
    public void markKeyAsUsed(@NotNull UUID keyUuid) {
        this.usedKeyCache.put(keyUuid, Boolean.TRUE);
        this.validKeyCache.remove(keyUuid);
        this.plugin.runTaskAsync(() -> {
            this.plugin.getDataHandler().markKeyUuidAsUsed(keyUuid);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishKeyUuidUsed(keyUuid));
        });
        this.plugin.debug("Marked key UUID as used: " + keyUuid);
    }

    /**
     * Registers a UUID as valid in the system
     * @param keyUuid The UUID to register
     */
    public void registerValidUuid(@NotNull UUID keyUuid) {
        long now = System.currentTimeMillis();
        this.validKeyCache.put(keyUuid, Boolean.TRUE);
        this.creationTimeCache.put(keyUuid, now);

        this.plugin.runTaskAsync(() -> {
            this.plugin.getDataHandler().insertKeyUuid(keyUuid);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishKeyUuidRegistered(keyUuid));
        });
    }

    /**
     * Checks if a UUID is valid and unused
     * @param keyUuid The UUID to check
     * @return true if valid and unused
     */
    public boolean isValidUnusedUuid(@NotNull UUID keyUuid) {
        if (Boolean.TRUE.equals(this.usedKeyCache.get(keyUuid))) return false;
        if (Boolean.TRUE.equals(this.validKeyCache.get(keyUuid))) return true;
        Boolean used = this.plugin.getDataHandler().isKeyUuidUsed(keyUuid);
        if (used == null) return false;
        if (used) {
            this.usedKeyCache.put(keyUuid, Boolean.TRUE);
            return false;
        }
        this.validKeyCache.put(keyUuid, Boolean.TRUE);
        return true;
    }

    /**
     * Gets statistics about the anti-dupe system
     * @return Array of statistics [generated, dupeAttempts, validUsages, cachedValid, cachedUsed]
     */
    @NotNull
    public long[] getStatistics() {
        return new long[] {
            this.totalKeysGenerated,
            this.totalDupeAttempts,
            this.totalValidUsages,
            this.validKeyCache.size(),
            this.usedKeyCache.size()
        };
    }

    /**
     * Loads valid UUIDs from database
     */
    private void loadValidUuids() {
    }

    /**
     * Saves current data to database
     */
    private void saveData() {
        this.plugin.runTaskAsync(() -> {
            this.plugin.getDataHandler().saveAntiDupeStatistics(
                this.totalKeysGenerated, 
                this.totalDupeAttempts, 
                this.totalValidUsages
            );
        });
    }

    /**
     * Notifies online admins about a dupe attempt
     * @param player The player who attempted to use duped key
     * @param keyUuid The invalid UUID
     */
    private void notifyAdminsOfDupeAttempt(@NotNull Player player, @NotNull UUID keyUuid) {
        String message = "§c[ANTI-DUPE] §f" + player.getName() + " §cattempted to use invalid/duped key UUID: §e" + keyUuid;
        
        this.plugin.getServer().getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("excellentcrates.admin"))
            .forEach(admin -> admin.sendMessage(message));
        
        this.plugin.warn("Dupe attempt by " + player.getName() + " with UUID: " + keyUuid);
    }

    public void applyExternalKeyUuidRegistered(@NotNull UUID keyUuid) {
        this.validKeyCache.put(keyUuid, Boolean.TRUE);
        this.creationTimeCache.put(keyUuid, System.currentTimeMillis());
    }

    public void applyExternalKeyUuidUsed(@NotNull UUID keyUuid) {
        this.usedKeyCache.put(keyUuid, Boolean.TRUE);
        this.validKeyCache.remove(keyUuid);
    }

    private static <K, V> Map<K, V> createLruMap(int maxEntries) {
        LinkedHashMap<K, V> delegate = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return this.size() > maxEntries;
            }
        };
        return Collections.synchronizedMap(delegate);
    }
}
