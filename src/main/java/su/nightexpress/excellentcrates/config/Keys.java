package su.nightexpress.excellentcrates.config;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.CratesPlugin;

public class Keys {

    public static NamespacedKey crateId;
    public static NamespacedKey keyId;
    public static NamespacedKey keyUuid;
    public static NamespacedKey linkToolCrateId;
    //public static NamespacedKey rewardId;
    public static NamespacedKey dummyItem;

    public static void load(@NotNull CratesPlugin plugin) {
        crateId = new NamespacedKey(plugin, "crate.id");
        keyId = new NamespacedKey(plugin, "crate_key.id");
        keyUuid = new NamespacedKey(plugin, "crate_key.uuid");
        linkToolCrateId = new NamespacedKey(plugin, "crate_link_tool.crate_id");
        //rewardId = new NamespacedKey(plugin, "reward.id");
        dummyItem = new NamespacedKey(plugin, "dummy_item");
    }

    public static void clear() {
        crateId = null;
        keyId = null;
        keyUuid = null;
        linkToolCrateId = null;
        //rewardId = null;
        dummyItem = null;
    }
}
