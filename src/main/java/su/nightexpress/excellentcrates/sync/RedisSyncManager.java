package su.nightexpress.excellentcrates.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.Placeholders;
import su.nightexpress.excellentcrates.config.Config;
import su.nightexpress.excellentcrates.config.Lang;
import su.nightexpress.excellentcrates.data.crate.GlobalCrateData;
import su.nightexpress.excellentcrates.data.crate.UserCrateData;
import su.nightexpress.excellentcrates.data.reward.RewardData;
import su.nightexpress.excellentcrates.data.serialize.UserCrateDataSerializer;
import su.nightexpress.excellentcrates.key.CrateKey;
import su.nightexpress.excellentcrates.user.CrateUser;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RedisSyncManager {

    private static final long COMMAND_TIMEOUT_SECONDS = 3L;

    private final CratesPlugin plugin;
    private final Gson gson;
    private final String nodeId;
    private final Set<String> crossServerPlayerNames = new HashSet<>();

    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisAsyncCommands<String, String> async;
    private String channel;
    private volatile boolean active;

    public RedisSyncManager(@NotNull CratesPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .registerTypeAdapter(UserCrateData.class, new UserCrateDataSerializer())
            .create();

        String nid = Config.REDIS_NODE_ID.get();
        if (nid == null || nid.isBlank()) {
            nid = UUID.randomUUID().toString();
        }
        this.nodeId = nid;
    }

    public void setup() {
        if (!Config.REDIS_ENABLED.get()) {
            return;
        }
        String host = Config.REDIS_HOST.get();
        int port = Config.REDIS_PORT.get();
        String password = Config.REDIS_PASSWORD.get();
        boolean ssl = Config.REDIS_SSL.get();
        this.channel = Config.REDIS_CHANNEL.get();

        try {
            RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(Duration.ofSeconds(5))
                .withSsl(ssl);

            if (password != null && !password.isEmpty()) {
                builder.withPassword(password.toCharArray());
            }

            this.client = RedisClient.create(builder.build());
            this.client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(SocketOptions.builder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .keepAlive(true)
                    .build())
                .build());
            this.connection = this.client.connect();
            this.async = this.connection.async();
            this.pubSubConnection = this.client.connectPubSub();
            this.active = true;
            this.startSubscriber();

            this.plugin.info("Redis sync enabled via Lettuce. Channel: " + this.channel + " | NodeId: " + this.nodeId);
        }
        catch (Exception e) {
            this.plugin.error("Failed to initialize Redis: " + e.getMessage());
            this.shutdown();
        }
    }

    public void shutdown() {
        this.active = false;
        this.async = null;

        if (this.pubSubConnection != null) {
            try {
                this.pubSubConnection.close();
            }
            catch (Exception ignored) {}
            this.pubSubConnection = null;
        }
        if (this.connection != null) {
            try {
                this.connection.close();
            }
            catch (Exception ignored) {}
            this.connection = null;
        }
        if (this.client != null) {
            try {
                this.client.shutdown(0, 2, TimeUnit.SECONDS);
            }
            catch (Exception ignored) {}
            this.client = null;
        }
    }

    public boolean isActive() {
        return this.async != null && this.active;
    }

    @NotNull
    public String getNodeId() {
        return this.nodeId;
    }

    @NotNull
    public CrateCooldownReservation reserveCrateCooldown(@NotNull UUID playerId, @NotNull String crateId, long cooldownSeconds) {
        if (!this.canUseCrossServerCooldown(crateId, cooldownSeconds)) {
            return CrateCooldownReservation.unavailable();
        }

        long ttlMillis = Math.max(1L, cooldownSeconds) * 1000L;
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        String key = this.getCrateCooldownKey(playerId, crateId);

        try {
            String result = this.async.set(key, String.valueOf(expiresAt), SetArgs.Builder.nx().px(ttlMillis))
                .toCompletableFuture()
                .get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if ("OK".equalsIgnoreCase(result)) {
                return CrateCooldownReservation.allowed(expiresAt);
            }

            Long remaining = this.async.pttl(key).toCompletableFuture().get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String existing = this.async.get(key).toCompletableFuture().get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long cooldownUntil = remaining != null && remaining > 0L ? System.currentTimeMillis() + remaining : this.parseCooldownUntil(existing);
            return CrateCooldownReservation.denied(cooldownUntil);
        }
        catch (Exception e) {
            this.plugin.warn("Redis crate cooldown reservation failed for '" + crateId + "': " + e.getMessage());
            return CrateCooldownReservation.unavailable();
        }
    }

    public void refreshCrateCooldown(@NotNull UUID playerId, @NotNull String crateId, long cooldownSeconds) {
        if (!this.canUseCrossServerCooldown(crateId, cooldownSeconds)) {
            return;
        }

        long ttlMillis = Math.max(1L, cooldownSeconds) * 1000L;
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        String key = this.getCrateCooldownKey(playerId, crateId);

        try {
            this.async.set(key, String.valueOf(expiresAt), SetArgs.Builder.px(ttlMillis))
                .toCompletableFuture()
                .get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            this.plugin.warn("Redis crate cooldown refresh failed for '" + crateId + "': " + e.getMessage());
        }
    }

    public void releaseCrateCooldown(@NotNull UUID playerId, @NotNull String crateId) {
        if (!isActive() || !Config.isCrossServerCooldownCrate(crateId)) {
            return;
        }

        try {
            this.async.del(this.getCrateCooldownKey(playerId, crateId))
                .toCompletableFuture()
                .get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            this.plugin.warn("Redis crate cooldown release failed for '" + crateId + "': " + e.getMessage());
        }
    }

    private boolean canUseCrossServerCooldown(@NotNull String crateId, long cooldownSeconds) {
        return isActive() && cooldownSeconds > 0L && Config.isCrossServerCooldownCrate(crateId);
    }

    @NotNull
    private String getCrateCooldownKey(@NotNull UUID playerId, @NotNull String crateId) {
        return Config.CRATE_CROSS_SERVER_COOLDOWN_REDIS_PREFIX.get() + ":" + crateId.toLowerCase(Locale.ROOT) + ":" + playerId;
    }

    private long parseCooldownUntil(String value) {
        if (value == null || value.isBlank()) {
            return System.currentTimeMillis();
        }

        try {
            return Long.parseLong(value);
        }
        catch (NumberFormatException ignored) {
            return System.currentTimeMillis();
        }
    }

    public record CrateCooldownReservation(boolean available, boolean allowed, long cooldownUntil) {

        @NotNull
        public static CrateCooldownReservation unavailable() {
            return new CrateCooldownReservation(false, false, 0L);
        }

        @NotNull
        public static CrateCooldownReservation allowed(long cooldownUntil) {
            return new CrateCooldownReservation(true, true, cooldownUntil);
        }

        @NotNull
        public static CrateCooldownReservation denied(long cooldownUntil) {
            return new CrateCooldownReservation(true, false, cooldownUntil);
        }
    }

    /* =========================
       Publisher API
       ========================= */

    public void publishUser(@NotNull CrateUser user) {
        if (!isActive()) return;

        JsonObject data = new JsonObject();
        data.addProperty("id", user.getId().toString());
        data.add("keys", gson.toJsonTree(user.getKeysMap()));
        data.add("crateData", gson.toJsonTree(user.getCrateDataMap()));

        publish("USER_UPDATE", data);
    }

    public void publishCrateData(@NotNull GlobalCrateData data) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("crateId", data.getCrateId());
        d.addProperty("latestOpenerId", data.getLatestOpenerId() == null ? "" : data.getLatestOpenerId().toString());
        d.addProperty("latestOpenerName", data.getLatestOpenerName());
        d.addProperty("latestRewardId", data.getLatestRewardId());

        publish("CRATE_DATA_UPSERT", d);
    }

    public void publishCrateDataDelete(@NotNull String crateId) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("crateId", crateId);

        publish("CRATE_DATA_DELETE", d);
    }

    public void publishRewardLimit(@NotNull RewardData limit) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("crateId", limit.getCrateId());
        d.addProperty("rewardId", limit.getRewardId());
        d.addProperty("holder", limit.getHolder());
        d.addProperty("amount", limit.getRolls());
        d.addProperty("resetDate", limit.getCooldownUntil());

        publish("REWARD_LIMIT_UPSERT", d);
    }

    public void publishRewardLimitDeleteSingle(@NotNull String holder, @NotNull String crateId, @NotNull String rewardId) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("mode", "single");
        d.addProperty("holder", holder);
        d.addProperty("crateId", crateId);
        d.addProperty("rewardId", rewardId);

        publish("REWARD_LIMIT_DELETE", d);
    }

    public void publishRewardLimitDeleteByCrate(@NotNull String crateId) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("mode", "by_crate");
        d.addProperty("crateId", crateId);

        publish("REWARD_LIMIT_DELETE", d);
    }

    public void publishRewardLimitDeleteByReward(@NotNull String crateId, @NotNull String rewardId) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("mode", "by_reward");
        d.addProperty("crateId", crateId);
        d.addProperty("rewardId", rewardId);

        publish("REWARD_LIMIT_DELETE", d);
    }

    public void publishRewardLimitDeleteByHolder(@NotNull String holder) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("mode", "by_holder");
        d.addProperty("holder", holder);

        publish("REWARD_LIMIT_DELETE", d);
    }

    public void publishGivePhysicalKey(@NotNull String keyId, @NotNull UUID playerId, int amount) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("playerId", playerId.toString());
        d.addProperty("keyId", keyId);
        d.addProperty("amount", amount);
        d.addProperty("origin", this.nodeId);

        publish("GIVE_PHYSICAL_KEY", d);
    }

    /**
     * Publishes a new key UUID registration across servers
     */
    public void publishKeyUuidRegistered(@NotNull UUID keyUuid) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("keyUuid", keyUuid.toString());
        d.addProperty("timestamp", System.currentTimeMillis());

        publish("KEY_UUID_REGISTERED", d);
    }

    /**
     * Publishes a key UUID usage across servers
     */
    public void publishKeyUuidUsed(@NotNull UUID keyUuid) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("keyUuid", keyUuid.toString());
        d.addProperty("timestamp", System.currentTimeMillis());

        publish("KEY_UUID_USED", d);
    }

    /**
     * Publishes cross-server physical key giving with UUID injection
     */
    public void publishGivePhysicalKeyWithUuid(@NotNull String keyId, @NotNull UUID playerId, int amount, @NotNull Set<UUID> keyUuids) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("playerId", playerId.toString());
        d.addProperty("keyId", keyId);
        d.addProperty("amount", amount);
        d.addProperty("origin", this.nodeId);

        JsonArray uuidArray = new JsonArray();
        keyUuids.forEach(uuid -> uuidArray.add(uuid.toString()));
        d.add("keyUuids", uuidArray);

        publish("GIVE_PHYSICAL_KEY_WITH_UUID", d);
    }

    /**
     * Publishes key delivery notification across servers
     */
    public void publishKeyDeliveryNotification(@NotNull UUID playerId, @NotNull String keyId, int amount, @NotNull String origin) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("playerId", playerId.toString());
        d.addProperty("keyId", keyId);
        d.addProperty("amount", amount);
        d.addProperty("origin", origin);
        d.addProperty("timestamp", System.currentTimeMillis());

        publish("KEY_DELIVERY_NOTIFICATION", d);
    }

    /**
     * Publishes cross-server crate item giving
     */
    public void publishGiveCrateItem(@NotNull String crateId, @NotNull UUID playerId, int amount) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("playerId", playerId.toString());
        d.addProperty("crateId", crateId);
        d.addProperty("amount", amount);
        d.addProperty("origin", this.nodeId);

        publish("GIVE_CRATE_ITEM", d);
    }

    /**
     * Publishes cross-server crate item giving by player name (when UUID is not known on this node)
     */
    public void publishGiveCrateItemByName(@NotNull String crateId, @NotNull String playerName, int amount) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("playerName", playerName);
        d.addProperty("crateId", crateId);
        d.addProperty("amount", amount);
        d.addProperty("origin", this.nodeId);

        publish("GIVE_CRATE_ITEM_BY_NAME", d);
    }

    /**
     * Publishes crate opening state cleanup across servers
     */
    public void publishOpeningStateCleanup(@NotNull UUID playerId, @NotNull String reason) {
        if (!isActive()) return;

        JsonObject d = new JsonObject();
        d.addProperty("playerId", playerId.toString());
        d.addProperty("reason", reason);
        d.addProperty("origin", this.nodeId);
        d.addProperty("timestamp", System.currentTimeMillis());

        publish("OPENING_STATE_CLEANUP", d);
    }

    private void publish(@NotNull String type, @NotNull JsonObject data) {
        if (!isActive()) return;

        JsonObject root = new JsonObject();
        root.addProperty("type", type);
        root.addProperty("nodeId", this.nodeId);
        root.add("data", data);

        String payload = this.gson.toJson(root);
        this.plugin.getFoliaScheduler().runAsync(() -> {
            if (this.async == null) return;
            this.async.publish(this.channel, payload).whenComplete((result, error) -> {
                if (error != null) {
                    this.plugin.warn("Redis publish failed: " + error.getMessage());
                }
            });
        });
    }

    /* =========================
       Subscriber
       ========================= */

    private void startSubscriber() {
        if (this.pubSubConnection == null) return;

        this.pubSubConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                handleIncoming(message);
            }
        });
        this.pubSubConnection.async().subscribe(this.channel);

        this.plugin.getFoliaScheduler().runTimerAsync(this::syncPlayerNames, 0L, 600L);
    }

    private void handleIncoming(@NotNull String message) {
        try {
            JsonObject root = this.gson.fromJson(message, JsonObject.class);
            if (root == null) return;

            String origin = root.has("nodeId") && !root.get("nodeId").isJsonNull() ? root.get("nodeId").getAsString() : null;
            if (origin != null && origin.equals(this.nodeId)) return;

            String type = root.has("type") ? root.get("type").getAsString() : null;
            JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : null;
            if (type == null || data == null) return;

            switch (type) {
                case "USER_UPDATE" -> applyUserUpdate(data);
                case "CRATE_DATA_UPSERT" -> applyCrateDataUpsert(data);
                case "CRATE_DATA_DELETE" -> applyCrateDataDelete(data);
                case "REWARD_LIMIT_UPSERT" -> applyRewardLimitUpsert(data);
                case "REWARD_LIMIT_DELETE" -> applyRewardLimitDelete(data);
                case "GIVE_PHYSICAL_KEY" -> applyGivePhysicalKey(data);
                case "KEY_UUID_REGISTERED" -> applyKeyUuidRegistered(data);
                case "KEY_UUID_USED" -> applyKeyUuidUsed(data);
                case "GIVE_PHYSICAL_KEY_WITH_UUID" -> applyGivePhysicalKeyWithUuid(data);
                case "KEY_DELIVERY_NOTIFICATION" -> applyKeyDeliveryNotification(data);
                case "OPENING_STATE_CLEANUP" -> applyOpeningStateCleanup(data);
                case "GIVE_CRATE_ITEM" -> applyGiveCrateItem(data);
                case "GIVE_CRATE_ITEM_BY_NAME" -> applyGiveCrateItemByName(data);
                case "PLAYER_NAMES_UPDATE" -> applyPlayerNamesUpdate(data);
                default -> {}
            }
        }
        catch (Exception e) {
            this.plugin.warn("Failed to handle Redis message: " + e.getMessage());
        }
    }

    private void applyUserUpdate(@NotNull JsonObject data) {
        UUID id = UUID.fromString(data.get("id").getAsString());

        Type mapSI = new TypeToken<Map<String, Integer>>() {}.getType();
        Type mapSUserData = new TypeToken<Map<String, UserCrateData>>() {}.getType();

        Map<String, Integer> keys = this.gson.fromJson(data.get("keys"), mapSI);
        Map<String, UserCrateData> crates = this.gson.fromJson(data.get("crateData"), mapSUserData);

        this.plugin.runTask(() -> {
            CrateUser user = this.plugin.getUserManager().getLoaded(id);
            if (user != null) {
                user.getKeysMap().clear();
                if (keys != null) user.getKeysMap().putAll(keys);
                user.getCrateDataMap().clear();
                if (crates != null) user.getCrateDataMap().putAll(crates);
            }
        });
    }

    private void applyCrateDataUpsert(@NotNull JsonObject data) {
        String crateId = data.get("crateId").getAsString();

        String openerIdStr = data.has("latestOpenerId") ? data.get("latestOpenerId").getAsString() : "";
        UUID openerId = (openerIdStr == null || openerIdStr.isEmpty()) ? null : UUID.fromString(openerIdStr);

        String openerName = data.has("latestOpenerName") && !data.get("latestOpenerName").isJsonNull()
            ? data.get("latestOpenerName").getAsString()
            : null;

        String rewardId = data.has("latestRewardId") && !data.get("latestRewardId").isJsonNull()
            ? data.get("latestRewardId").getAsString()
            : null;

        GlobalCrateData crateData = new GlobalCrateData(crateId, openerId, openerName, rewardId);
        this.plugin.runNextTick(() -> this.plugin.getDataManager().applyExternalCrateData(crateData));
    }

    private void applyCrateDataDelete(@NotNull JsonObject data) {
        String crateId = data.get("crateId").getAsString();
        this.plugin.runNextTick(() -> this.plugin.getDataManager().applyExternalDeleteCrateData(crateId));
    }

    private void applyRewardLimitUpsert(@NotNull JsonObject data) {
        String crateId = data.get("crateId").getAsString();
        String rewardId = data.get("rewardId").getAsString();
        String holder = data.get("holder").getAsString();
        int amount = data.get("amount").getAsInt();
        long resetDate = data.get("resetDate").getAsLong();

        RewardData limit = new RewardData(crateId, rewardId, holder, amount, resetDate);
        this.plugin.runNextTick(() -> this.plugin.getDataManager().applyExternalRewardLimit(limit));
    }

    private void applyRewardLimitDelete(@NotNull JsonObject data) {
        String mode = data.get("mode").getAsString();

        this.plugin.runNextTick(() -> {
            switch (mode) {
                case "single" -> this.plugin.getDataManager().applyExternalDeleteRewardLimit(
                    data.get("holder").getAsString(),
                    data.get("crateId").getAsString(),
                    data.get("rewardId").getAsString()
                );
                case "by_crate" -> this.plugin.getDataManager().applyExternalDeleteRewardLimitsByCrate(
                    data.get("crateId").getAsString()
                );
                case "by_reward" -> this.plugin.getDataManager().applyExternalDeleteRewardLimitsByReward(
                    data.get("crateId").getAsString(),
                    data.get("rewardId").getAsString()
                );
                case "by_holder" -> this.plugin.getDataManager().applyExternalDeleteRewardLimitsByHolder(
                    data.get("holder").getAsString()
                );
                default -> {}
            }
        });
    }

    private void applyGivePhysicalKey(@NotNull JsonObject data) {
        String playerIdStr = data.get("playerId").getAsString();
        String keyId = data.get("keyId").getAsString();
        int amount = data.get("amount").getAsInt();
        String origin = data.has("origin") && !data.get("origin").isJsonNull() ? data.get("origin").getAsString() : "unknown";

        UUID playerId = UUID.fromString(playerIdStr);

        this.runForOnlinePlayer(playerId, player -> {
            CrateKey key = this.plugin.getKeyManager().getKeyById(keyId);
            if (key == null || key.isVirtual()) return;

            this.plugin.getKeyManager().giveKey(player, key, amount);
            this.plugin.info("Gave physical key '" + keyId + "' x" + amount + " to " + player.getName() + " via Redis request from " + origin + ".");
        });
    }

    private void applyKeyUuidRegistered(@NotNull JsonObject data) {
        String uuidStr = data.get("keyUuid").getAsString();

        try {
            UUID keyUuid = UUID.fromString(uuidStr);
            this.plugin.runTask(() -> this.plugin.getUuidAntiDupeManager().applyExternalKeyUuidRegistered(keyUuid));
        } catch (IllegalArgumentException e) {
            this.plugin.warn("Invalid UUID received from Redis: " + uuidStr);
        }
    }

    private void applyKeyUuidUsed(@NotNull JsonObject data) {
        String uuidStr = data.get("keyUuid").getAsString();

        try {
            UUID keyUuid = UUID.fromString(uuidStr);
            this.plugin.runTask(() -> this.plugin.getUuidAntiDupeManager().applyExternalKeyUuidUsed(keyUuid));
        } catch (IllegalArgumentException e) {
            this.plugin.warn("Invalid used UUID received from Redis: " + uuidStr);
        }
    }

    private void applyGivePhysicalKeyWithUuid(@NotNull JsonObject data) {
        UUID playerId = UUID.fromString(data.get("playerId").getAsString());
        String keyId = data.get("keyId").getAsString();
        int amount = data.get("amount").getAsInt();
        String origin = data.get("origin").getAsString();

        // Extract UUIDs for the keys
        Set<UUID> keyUuids = new HashSet<>();
        if (data.has("keyUuids")) {
            data.getAsJsonArray("keyUuids").forEach(element -> {
                try {
                    keyUuids.add(UUID.fromString(element.getAsString()));
                } catch (IllegalArgumentException e) {
                    this.plugin.warn("Invalid key UUID in cross-server key giving: " + element.getAsString());
                }
            });
        }

        this.runForOnlinePlayer(playerId, player -> {
            CrateKey key = this.plugin.getKeyManager().getKeyById(keyId);
            if (key == null || key.isVirtual()) return;

            this.plugin.getKeyManager().givePhysicalKeysWithUuids(player, key, amount, keyUuids);
            this.plugin.info("Gave physical key '" + keyId + "' x" + amount + " with UUIDs to " + player.getName() + " via Redis request from " + origin + ".");

            publishKeyDeliveryNotification(playerId, keyId, amount, origin);
        });
    }

    private void applyKeyDeliveryNotification(@NotNull JsonObject data) {
        UUID playerId = UUID.fromString(data.get("playerId").getAsString());
        String keyId = data.get("keyId").getAsString();
        int amount = data.get("amount").getAsInt();
        String origin = data.get("origin").getAsString();

        this.runForOnlinePlayer(playerId, player -> {
            CrateKey key = this.plugin.getKeyManager().getKeyById(keyId);
            if (key == null) return;

            Lang.COMMAND_KEY_GIVE_NOTIFY.message().send(player, replacer -> replacer
                .replace(Placeholders.GENERIC_AMOUNT, amount)
                .replace(key.replacePlaceholders())
            );

            this.plugin.info("Sent cross-server key delivery notification to " + player.getName() +
                           " for " + amount + "x " + keyId + " from server " + origin);
        });
    }

    private void applyGiveCrateItem(@NotNull JsonObject data) {
        UUID playerId = UUID.fromString(data.get("playerId").getAsString());
        String crateId = data.get("crateId").getAsString();
        int amount = data.get("amount").getAsInt();
        String origin = data.has("origin") && !data.get("origin").isJsonNull() ? data.get("origin").getAsString() : "unknown";

        this.runForOnlinePlayer(playerId, player -> {
            var crate = this.plugin.getCrateManager().getCrateById(crateId);
            if (crate == null) return;

            this.plugin.getCrateManager().giveCrateItem(player, crate, amount);
            Lang.COMMAND_GIVE_NOTIFY.message().send(player, replacer -> replacer
                .replace(Placeholders.GENERIC_AMOUNT, amount)
                .replace(crate.replacePlaceholders())
            );
            this.plugin.info("Gave crate '" + crateId + "' x" + amount + " to " + player.getName() + " via Redis request from " + origin + ".");
        });
    }

    private void applyGiveCrateItemByName(@NotNull JsonObject data) {
        String playerName = data.get("playerName").getAsString();
        String crateId = data.get("crateId").getAsString();
        int amount = data.get("amount").getAsInt();
        String origin = data.has("origin") && !data.get("origin").isJsonNull() ? data.get("origin").getAsString() : "unknown";

        su.nightexpress.excellentcrates.util.FoliaTasks.runForOnlinePlayer(this.plugin, playerName, player -> {
            var crate = this.plugin.getCrateManager().getCrateById(crateId);
            if (crate == null) return;

            this.plugin.getCrateManager().giveCrateItem(player, crate, amount);
            Lang.COMMAND_GIVE_NOTIFY.message().send(player, replacer -> replacer
                .replace(Placeholders.GENERIC_AMOUNT, amount)
                .replace(crate.replacePlaceholders())
            );
            this.plugin.info("Gave crate '" + crateId + "' x" + amount + " to " + player.getName() + " via Redis-by-name request from " + origin + ".");
        });
    }

    private void applyOpeningStateCleanup(@NotNull JsonObject data) {
        UUID playerId = UUID.fromString(data.get("playerId").getAsString());
        String reason = data.get("reason").getAsString();
        String origin = data.get("origin").getAsString();

        this.runForOnlinePlayer(playerId, player -> {
            this.plugin.getOpeningManager().stopOpening(player);
            this.plugin.info("Cleaned up opening state for " + player.getName() +
                           " due to cross-server event: " + reason + " from " + origin);
        });
    }

    public void publishPlayerNames(@NotNull Set<String> playerNames) {
        if (!isActive()) return;

        JsonObject data = new JsonObject();
        JsonArray namesArray = new JsonArray();
        playerNames.forEach(namesArray::add);
        data.add("playerNames", namesArray);
        data.addProperty("timestamp", System.currentTimeMillis());

        publish("PLAYER_NAMES_UPDATE", data);
    }

    private void syncPlayerNames() {
        if (!isActive()) return;

        Set<String> localPlayerNames = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            localPlayerNames.add(player.getName());
        }

        if (!localPlayerNames.isEmpty()) {
            publishPlayerNames(localPlayerNames);
        }
    }

    private void applyPlayerNamesUpdate(@NotNull JsonObject data) {
        JsonArray namesArray = data.getAsJsonArray("playerNames");

        synchronized (this.crossServerPlayerNames) {
            this.crossServerPlayerNames.clear();

            for (int i = 0; i < namesArray.size(); i++) {
                String playerName = namesArray.get(i).getAsString();
                this.crossServerPlayerNames.add(playerName);
            }
        }
    }

    private void runForOnlinePlayer(@NotNull UUID playerId, @NotNull Consumer<Player> action) {
        su.nightexpress.excellentcrates.util.FoliaTasks.runForOnlinePlayer(this.plugin, playerId, action);
    }

    @NotNull
    public Set<String> getAllPlayerNames() {
        Set<String> allNames = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            allNames.add(player.getName());
        }

        synchronized (this.crossServerPlayerNames) {
            allNames.addAll(this.crossServerPlayerNames);
        }

        return allNames;
    }
}
