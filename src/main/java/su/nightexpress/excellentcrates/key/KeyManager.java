package su.nightexpress.excellentcrates.key;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.config.Config;
import su.nightexpress.excellentcrates.config.Keys;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.item.ItemTypes;
import su.nightexpress.excellentcrates.user.CrateUser;
import su.nightexpress.excellentcrates.util.CrateUtils;
import su.nightexpress.excellentcrates.util.inspect.Inspectors;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.*;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class KeyManager extends AbstractManager<CratesPlugin> {

    private final Map<String, CrateKey> keyByIdMap;

    public KeyManager(@NotNull CratesPlugin plugin) {
        super(plugin);
        this.keyByIdMap = new HashMap<>();
    }

    @Override
    protected void onLoad() {
        this.loadKeys();
        this.plugin.runNextTick(() -> this.runInspections()); // When everything is loaded.

        this.addListener(new KeyListener(this.plugin, this));
    }

    @Override
    protected void onShutdown() {
        this.keyByIdMap.clear();
    }

    private void loadKeys() {
        for (File file : FileUtil.getFiles(plugin.getDataFolder() + Config.DIR_KEYS, true)) {
            this.loadKey(new CrateKey(this.plugin, file));
        }
        this.plugin.info("Loaded " + this.keyByIdMap.size() + " crate keys.");
    }

    private void loadKey(@NotNull CrateKey crateKey) {
        if (crateKey.load()) {
            this.keyByIdMap.put(crateKey.getId(), crateKey);
        }
        else this.plugin.error("Key not loaded: '" + crateKey.getFile().getName() + "'.");
    }

    private void runInspections() {
        this.getKeys().forEach(key -> Inspectors.KEY.printConsole(this.plugin, key, "Problems in key config (" + key.getFile().getPath() + "):"));
    }

    public boolean create(@NotNull String id) {
        id = CrateUtils.createID(id);
        if (this.getKeyById(id) != null) return false;

        File file = new File(plugin.getDataFolder() + Config.DIR_KEYS, id + ".yml");
        FileUtil.create(file);

        CrateKey key = new CrateKey(this.plugin, file);
        key.setName(StringUtil.capitalizeFully(id) + " Key");
        key.setVirtual(false);

        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemUtil.editMeta(item, meta -> {
            meta.setDisplayName(key.getName());
        });

        key.setProvider(ItemTypes.vanilla(item));
        key.save();

        this.loadKey(key);
        return true;
    }

    public boolean delete(@NotNull CrateKey key) {
        if (key.getFile().delete()) {
            this.keyByIdMap.remove(key.getId());
            return true;
        }
        return false;
    }

    public boolean dropKeyItem(@NotNull CrateKey key, @NotNull Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        world.dropItemNaturally(location, key.getItem());
        return true;
    }

    @NotNull
    public Map<String, CrateKey> getKeyByIdMap() {
        return this.keyByIdMap;
    }

    @NotNull
    public Set<CrateKey> getKeys() {
        return new HashSet<>(this.keyByIdMap.values());
    }

    @NotNull
    public List<String> getKeyIds() {
        return new ArrayList<>(this.keyByIdMap.keySet());
    }

    @Nullable
    public CrateKey getKeyById(@NotNull String id) {
        return this.keyByIdMap.get(id.toLowerCase());
    }

    @Nullable
    public CrateKey getKeyByItem(@NotNull ItemStack item) {
        String id = PDCUtil.getString(item, Keys.keyId).orElse(null);
        return id == null ? null : this.getKeyById(id);
    }

    /**
     * Validates a key item including UUID anti-dupe check
     * @param item The key item to validate
     * @param player The player using the key (for logging)
     * @return true if key is valid and not duped
     */
    public boolean validateKeyItem(@NotNull ItemStack item, @Nullable Player player) {
        CrateKey key = this.getKeyByItem(item);
        if (key == null) return false;

        // Virtual keys don't need UUID validation
        if (key.isVirtual()) return true;

        // Physical keys must pass UUID validation
        return this.plugin.getUuidAntiDupeManager().validateKeyUuid(item, player);
    }

    @NotNull
    public Set<CrateKey> getKeys(@NotNull Player player, @NotNull Crate crate) {
        return crate.getRequiredKeys().stream().filter(key -> this.hasKey(player, key)).collect(Collectors.toSet());
    }

    @Nullable
    public ItemStack getFirstKeyStack(@NotNull Player player, @NotNull CrateKey key) {
        Predicate<ItemStack> predicate = this.getItemStackPredicate(key);
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && predicate.test(item)) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    public CrateKey getOpenKey(@NotNull Player player, @NotNull Crate crate) {
        // Check out physical keys first.
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> content = new ArrayList<>();
        if (!crate.isAllVirtualKeys()) {
            content.add(inventory.getItemInMainHand());
            content.add(inventory.getItemInOffHand());
            if (!Config.CRATE_HOLD_KEY_TO_OPEN.get()) {
                content.addAll(Arrays.asList(inventory.getContents()));
            }
        }

        Set<UUID> alreadyValidated = new HashSet<>();

        for (ItemStack itemStack : content) {
            CrateKey key = itemStack == null ? null : this.getKeyByItem(itemStack);
            if (key != null && crate.isGoodKey(key)) {
                if (!key.isVirtual()) {
                    UUID keyUuid = this.plugin.getUuidAntiDupeManager().getKeyUuid(itemStack);
                    if (keyUuid != null && alreadyValidated.contains(keyUuid)) {
                        continue;
                    }
                    if (keyUuid != null) {
                        alreadyValidated.add(keyUuid);
                    }

                    if (!this.validateKeyItem(itemStack, player)) {
                        continue;
                    }
                }
                return key;
            }
        }

        // Check out virtual keys if no physical ones present.
        CrateUser user = plugin.getUserManager().getOrFetch(player);
        for (CrateKey key : crate.getRequiredKeys()) {
            if (key.isVirtual() && user.hasKeys(key)) {
                return key;
            }
        }

        return null;
    }

    public boolean isKey(@NotNull ItemStack item) {
        return this.getKeyByItem(item) != null;
    }

    public boolean isKey(@NotNull ItemStack item, @NotNull CrateKey key) {
        return this.getItemStackPredicate(key).test(item);
    }

    public boolean isKey(@NotNull ItemStack item, @NotNull Crate crate) {
        CrateKey key = this.getKeyByItem(item);
        return key != null && crate.isGoodKey(key);
    }

    public int getKeysAmount(@NotNull Player player, @NotNull Crate crate) {
        return crate.getRequiredKeys().stream().mapToInt(key -> this.getKeysAmount(player, key)).sum();
    }

    public int getKeysAmount(@NotNull Player player, @NotNull CrateKey key) {
        if (key.isVirtual()) {
            CrateUser user = plugin.getUserManager().getOrFetch(player);
            return user.countKeys(key);
        }
        return Players.countItem(player, this.getItemStackPredicate(key));
    }

    public boolean hasKey(@NotNull Player player, @NotNull Crate crate) {
        return crate.getRequiredKeys().stream().anyMatch(key -> this.hasKey(player, key));
    }

    public boolean hasKey(@NotNull Player player, @NotNull CrateKey key) {
        if (key.isVirtual()) return this.getKeysAmount(player, key) > 0;

        return this.getFirstKeyStack(player, key) != null;
    }

    public void giveKeysOnHold(@NotNull Player player) {

        plugin.getUserManager().manageUserSynchronized(player.getUniqueId(), user -> {
            user.getKeysOnHold().forEach((keyId, amount) -> {
                CrateKey crateKey = this.getKeyById(keyId);
                if (crateKey == null) return;

                this.giveKey(player, crateKey, amount);
            });
            user.cleanKeysOnHold();
            this.plugin.getUserManager().save(user);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishUser(user));
        });
    }

    public void setKey(@NotNull CrateUser user, @NotNull CrateKey key, int amount) {
        Player player = user.getPlayer();
        if (player != null) {
            this.setKey(player, key, amount);
            return;
        }

        if (key.isVirtual()) {
            user.setKeys(key.getId(), amount);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishUser(user));
        }
    }

    public void setKey(@NotNull Player player, @NotNull CrateKey key, int amount) {
        if (key.isVirtual()) {
            CrateUser user = plugin.getUserManager().getOrFetch(player);
            user.setKeys(key.getId(), amount);
            plugin.getUserManager().save(user);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishUser(user));
        }
        else {
            ItemStack keyItem = key.getItem();
            int has = Players.countItem(player, keyItem);
            if (has > amount) {
                Players.takeItem(player, keyItem, has - amount);
            }
            else if (has < amount) {
                Players.addItem(player, keyItem, amount - has);
            }
        }
        //return true;
    }

    public void giveKey(@NotNull CrateUser user, @NotNull CrateKey key, int amount) {
        Player player = user.getPlayer();
        if (player != null) {
            this.giveKey(player, key, amount);
            return;
        }

        if (key.isVirtual()) {
            user.addKeys(key.getId(), amount);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishUser(user));
        }
        else {
            user.addKeysOnHold(key.getId(), amount);
        }
    }

    public void giveKey(@NotNull Player player, @NotNull CrateKey key, int amount) {
        if (key.isVirtual()) {
            CrateUser user = plugin.getUserManager().getOrFetch(player);
            user.addKeys(key.getId(), amount);
            plugin.getUserManager().save(user);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishUser(user));
        }
        else {
            int actualAmount = amount < 0 ? Math.abs(amount) : amount;
            for (int i = 0; i < actualAmount; i++) {
                ItemStack keyItem = key.getItem();
                try {
                    java.util.UUID uuid = this.plugin.getUuidAntiDupeManager().getKeyUuid(keyItem);
                    if (uuid != null) {
                        long created = this.plugin.getUuidAntiDupeManager().getCreationTime(uuid);
                        su.nightexpress.nightcore.util.ItemReplacer.create(keyItem)
                            .readMeta()
                            .replace(su.nightexpress.excellentcrates.Placeholders.KEY_UUID, () -> uuid.toString())
                            .replace(su.nightexpress.excellentcrates.Placeholders.KEY_CREATION_TIME, () -> {
                                if (created <= 0) return "-";
                                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern(su.nightexpress.excellentcrates.config.Config.LOGS_DATE_FORMAT.get());
                                return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(created), java.time.ZoneId.systemDefault()).format(fmt);
                            })
                            .replace(su.nightexpress.excellentcrates.Placeholders.KEY_VALID_CHECK, () -> {
                                boolean valid = this.plugin.getUuidAntiDupeManager().isValidUnusedUuid(uuid);
                                return valid ? "✔" : "✖";
                            })
                            .writeMeta();
                    }
                } catch (Throwable ignored) {}
                Players.addItem(player, keyItem);
            }
        }
    }

    @Nullable
    public CrateKey takeKey(@NotNull Player player, @NotNull Crate crate) {
        CrateKey key = this.getKeys(player, crate).stream().findFirst().orElse(null);
        if (key == null) return null;

        this.takeKey(player, key, 1);
        return key;
    }

    public void takeKey(@NotNull CrateUser user, @NotNull CrateKey key, int amount) {
        Player player = user.getPlayer();
        if (player != null) {
            this.takeKey(player, key, amount);
            return;
        }

        if (key.isVirtual()) {
            user.takeKeys(key.getId(), amount);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishUser(user));
        }
    }

    public void takeKey(@NotNull Player player, @NotNull CrateKey key, int amount) {
        if (key.isVirtual()) {
            CrateUser user = plugin.getUserManager().getOrFetch(player);
            user.takeKeys(key.getId(), amount);
            plugin.getUserManager().save(user);
            this.plugin.getRedisSyncManager().ifPresent(sync -> sync.publishUser(user));
        }
        else {
            this.markPhysicalKeysAsUsed(player, key, amount);

            Predicate<ItemStack> predicate = this.getItemStackPredicate(key);
            int has = Players.countItem(player, predicate);
            if (has < amount) amount = has;

            Players.takeItem(player, predicate, amount);
        }
    }

    /**
     * Marks physical key UUIDs as used before consumption
     */
    private void markPhysicalKeysAsUsed(@NotNull Player player, @NotNull CrateKey key, int amount) {
        PlayerInventory inventory = player.getInventory();
        int marked = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item == null || marked >= amount) break;

            if (this.isKey(item, key)) {
                UUID keyUuid = this.plugin.getUuidAntiDupeManager().getKeyUuid(item);
                if (keyUuid != null) {
                    this.plugin.getUuidAntiDupeManager().markKeyAsUsed(keyUuid);
                    marked++;
                }
            }
        }
    }

    public boolean givePhysicalKeyCrossServer(@NotNull String keyId, @NotNull UUID playerId, int amount) {
        CrateKey key = this.getKeyById(keyId);
        if (key == null || key.isVirtual()) return false;
        return this.givePhysicalKeyCrossServer(key, playerId, amount);
    }

    public boolean givePhysicalKeyCrossServer(@NotNull CrateKey key, @NotNull UUID playerId, int amount) {
        if (key.isVirtual()) return false;

        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player != null) {
            // Player is on this server, give the physical key immediately.
            this.giveKey(player, key, amount);
            return true;
        }
        // Player is not on this server: publish a cross-server request.
        return this.plugin.getRedisSyncManager()
            .map(sync -> {
                // Pre-generate UUIDs for cross-server keys
                Set<UUID> keyUuids = new HashSet<>();
                for (int i = 0; i < amount; i++) {
                    UUID keyUuid = UUID.randomUUID();
                    keyUuids.add(keyUuid);
                    this.plugin.getUuidAntiDupeManager().registerValidUuid(keyUuid);
                }

                sync.publishGivePhysicalKeyWithUuid(key.getId(), playerId, amount, keyUuids);
                return true;
            })
            .orElse(false);
    }

    /**
     * Gives physical keys with pre-generated UUIDs (used for cross-server)
     */
    public void givePhysicalKeysWithUuids(@NotNull Player player, @NotNull CrateKey key, int amount, @NotNull Set<UUID> keyUuids) {
        if (key.isVirtual()) return;

        Iterator<UUID> uuidIterator = keyUuids.iterator();
        for (int i = 0; i < amount && uuidIterator.hasNext(); i++) {
            ItemStack keyItem = key.getRawItem();
            ItemUtil.editMeta(keyItem, meta -> {
                PDCUtil.set(meta, Keys.keyId, key.getId());
                PDCUtil.set(meta, Keys.keyUuid, uuidIterator.next());
            });

            try {
                java.util.UUID uuid = this.plugin.getUuidAntiDupeManager().getKeyUuid(keyItem);
                if (uuid != null) {
                    long created = this.plugin.getUuidAntiDupeManager().getCreationTime(uuid);
                    su.nightexpress.nightcore.util.ItemReplacer.create(keyItem)
                        .readMeta()
                        .replace(su.nightexpress.excellentcrates.Placeholders.KEY_UUID, () -> uuid.toString())
                        .replace(su.nightexpress.excellentcrates.Placeholders.KEY_CREATION_TIME, () -> {
                            if (created <= 0) return "-";
                            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern(su.nightexpress.excellentcrates.config.Config.LOGS_DATE_FORMAT.get());
                            return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(created), java.time.ZoneId.systemDefault()).format(fmt);
                        })
                        .replace(su.nightexpress.excellentcrates.Placeholders.KEY_VALID_CHECK, () -> {
                            boolean valid = this.plugin.getUuidAntiDupeManager().isValidUnusedUuid(uuid);
                            return valid ? "✔" : "✖";
                        })
                        .writeMeta();
                }
            } catch (Throwable ignored) {}

            Players.addItem(player, keyItem);
        }
    }

    @NotNull
    private Predicate<ItemStack> getItemStackPredicate(@NotNull CrateKey key) {
        return stack -> this.getKeyByItem(stack) == key;
    }
}
