package su.nightexpress.excellentcrates.command.antidupe;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.config.Lang;
import su.nightexpress.excellentcrates.config.Perms;
import su.nightexpress.excellentcrates.key.CrateKey;
import su.nightexpress.nightcore.commands.Commands;
import su.nightexpress.nightcore.commands.builder.HubNodeBuilder;
import su.nightexpress.nightcore.commands.context.CommandContext;
import su.nightexpress.nightcore.commands.context.ParsedArguments;
import su.nightexpress.nightcore.util.Lists;

import java.util.UUID;

/**
 * Admin commands for managing the UUID Anti-Dupe system
 */
public class AntiDupeCommands {

    public static void load(@NotNull CratesPlugin plugin, @NotNull HubNodeBuilder root) {
        root.branch(Commands.hub("antidupe")
            .description("UUID Anti-Dupe system management commands")
            .permission(Perms.COMMAND_ANTIDUPE)
            .branch(Commands.literal("stats")
                .description("View UUID Anti-Dupe system statistics")
                .permission(Perms.COMMAND_ANTIDUPE)
                .executes((context, arguments) -> executeStats(plugin, context.getSender()))
            )
            .branch(Commands.literal("validate")
                .description("Validate the key in your main hand")
                .permission(Perms.COMMAND_ANTIDUPE)
                .playerOnly()
                .executes((context, arguments) -> executeValidate(plugin, context.getSender()))
            )
            .branch(Commands.literal("info")
                .description("Get detailed information about the key in your main hand")
                .permission(Perms.COMMAND_ANTIDUPE)
                .playerOnly()
                .executes((context, arguments) -> executeInfo(plugin, context.getSender()))
            )
            .branch(Commands.literal("reload")
                .description("Reload the UUID Anti-Dupe system")
                .permission(Perms.COMMAND_ANTIDUPE)
                .executes((context, arguments) -> executeReload(plugin, context.getSender()))
            )
        );
    }

    private static boolean executeStats(@NotNull CratesPlugin plugin, @NotNull CommandSender sender) {
        long[] stats = plugin.getUuidAntiDupeManager().getStatistics();
        
        sender.sendMessage("§6=== UUID Anti-Dupe Statistics ===");
        sender.sendMessage("§eKeys Generated: §f" + stats[0]);
        sender.sendMessage("§cDupe Attempts: §f" + stats[1]);
        sender.sendMessage("§aValid Usages: §f" + stats[2]);
        sender.sendMessage("§bValid UUIDs in Cache: §f" + stats[3]);
        sender.sendMessage("§7Used UUIDs in Cache: §f" + stats[4]);
        
        if (stats[0] > 0) {
            double dupeRate = (double) stats[1] / stats[0] * 100;
            sender.sendMessage("§eDupe Attempt Rate: §f" + String.format("%.2f%%", dupeRate));
        }
        
        return true;
    }

    private static boolean executeValidate(@NotNull CratesPlugin plugin, @NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[ExcellentCrates] This command can only be used by players.");
            return false;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage("§c[ExcellentCrates] Hold a key in your main hand to validate.");
            return false;
        }

        CrateKey key = plugin.getKeyManager().getKeyByItem(item);
        if (key == null) {
            sender.sendMessage("§c[ExcellentCrates] The item in your hand is not a crate key.");
            return false;
        }

        if (key.isVirtual()) {
            sender.sendMessage("§e[ExcellentCrates] Virtual keys don't use UUID validation.");
            return true;
        }

        UUID keyUuid = plugin.getUuidAntiDupeManager().getKeyUuid(item);
        if (keyUuid == null) {
            sender.sendMessage("§c[ExcellentCrates] This key has no UUID (old key or invalid).");
            return true;
        }

        boolean isValid = plugin.getUuidAntiDupeManager().validateKeyUuid(item, player);
        if (isValid) {
            sender.sendMessage("§a[ExcellentCrates] Key is VALID!");
            sender.sendMessage("§7UUID: §f" + keyUuid);
        } else {
            sender.sendMessage("§c[ExcellentCrates] Key is INVALID/DUPED!");
            sender.sendMessage("§7UUID: §f" + keyUuid);
        }

        return true;
    }

    private static boolean executeInfo(@NotNull CratesPlugin plugin, @NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[ExcellentCrates] This command can only be used by players.");
            return false;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage("§c[ExcellentCrates] Hold a key in your main hand to get info.");
            return false;
        }

        CrateKey key = plugin.getKeyManager().getKeyByItem(item);
        if (key == null) {
            sender.sendMessage("§c[ExcellentCrates] The item in your hand is not a crate key.");
            return false;
        }

        sender.sendMessage("§6=== Key Information ===");
        sender.sendMessage("§eKey ID: §f" + key.getId());
        sender.sendMessage("§eKey Name: §f" + key.getNameTranslated());
        sender.sendMessage("§eVirtual: §f" + (key.isVirtual() ? "Yes" : "No"));

        if (!key.isVirtual()) {
            UUID keyUuid = plugin.getUuidAntiDupeManager().getKeyUuid(item);
            if (keyUuid != null) {
                sender.sendMessage("§eUUID: §f" + keyUuid);
                boolean isValid = plugin.getUuidAntiDupeManager().isValidUnusedUuid(keyUuid);
                sender.sendMessage("§eStatus: " + (isValid ? "§aValid & Unused" : "§cInvalid or Used"));
            } else {
                sender.sendMessage("§eUUID: §cNone (Old key or invalid)");
            }
        }

        return true;
    }

    private static boolean executeReload(@NotNull CratesPlugin plugin, @NotNull CommandSender sender) {
        sender.sendMessage("§e[ExcellentCrates] Reloading UUID Anti-Dupe system...");
        
        plugin.getUuidAntiDupeManager().shutdown();
        plugin.getUuidAntiDupeManager().setup();
        
        sender.sendMessage("§a[ExcellentCrates] UUID Anti-Dupe system reloaded successfully!");
        return true;
    }
}
