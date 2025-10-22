package su.nightexpress.excellentcrates.command.basic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.Placeholders;
import su.nightexpress.excellentcrates.command.CommandArguments;
import su.nightexpress.excellentcrates.command.CommandFlags;
import su.nightexpress.excellentcrates.config.Lang;
import su.nightexpress.excellentcrates.config.Perms;
import su.nightexpress.excellentcrates.crate.cost.Cost;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.crate.impl.CrateSource;
import su.nightexpress.excellentcrates.crate.impl.OpenOptions;
import su.nightexpress.excellentcrates.key.CrateKey;
import su.nightexpress.nightcore.commands.Arguments;
import su.nightexpress.nightcore.commands.Commands;
import su.nightexpress.nightcore.commands.builder.HubNodeBuilder;
import su.nightexpress.nightcore.commands.builder.LiteralNodeBuilder;
import su.nightexpress.nightcore.commands.context.CommandContext;
import su.nightexpress.nightcore.commands.context.ParsedArguments;
import su.nightexpress.nightcore.core.config.CoreLang;
import su.nightexpress.nightcore.util.Lists;
import su.nightexpress.nightcore.util.NumberUtil;
import su.nightexpress.nightcore.util.Players;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class BaseCommands {

    public static void load(@NotNull CratesPlugin plugin, @NotNull HubNodeBuilder root) {
        root.branch(Commands.literal("reload")
            .description(CoreLang.COMMAND_RELOAD_DESC)
            .permission(Perms.COMMAND_RELOAD)
            .executes((context, arguments) -> {
                plugin.doReload(context.getSender());
                return true;
            })
        );

        root.branch(Commands.hub("key")
            .description(Lang.COMMAND_KEY_DESC)
            .permission(Perms.COMMAND_KEY)
            .branch(Commands.literal("drop")
                .description(Lang.COMMAND_DROP_KEY_DESC)
                .permission(Perms.COMMAND_DROP_KEY)
                .withArguments(
                    CommandArguments.forKey(plugin),
                    Arguments.decimal(CommandArguments.X).localized(Lang.COMMAND_ARGUMENT_NAME_X).suggestions((reader, context) -> getCoords(context, Location::getBlockX)),
                    Arguments.decimal(CommandArguments.Y).localized(Lang.COMMAND_ARGUMENT_NAME_Y).suggestions((reader, context) -> getCoords(context, Location::getBlockY)),
                    Arguments.decimal(CommandArguments.Z).localized(Lang.COMMAND_ARGUMENT_NAME_Z).suggestions((reader, context) -> getCoords(context, Location::getBlockZ)),
                    Arguments.world(CommandArguments.WORLD)
                )
                .executes((context, arguments) -> dropKey(plugin, context, arguments))
            )
            .branch(Commands.literal("inspect")
                .description(Lang.COMMAND_KEY_INSPECT_DESC)
                .permission(Perms.COMMAND_KEY_INSPECT)
                .withArgument(CommandArguments.crossServerPlayerName(plugin).permission(Perms.COMMAND_KEY_INSPECT_OTHERS))
                .executes((context, arguments) -> inspectKeys(plugin, context, arguments))
            )
            .branch(Commands.literal("giveall")
                .description(Lang.COMMAND_KEY_GIVE_ALL_DESC)
                .permission(Perms.COMMAND_KEY_GIVE)
                .withArgument(CommandArguments.forKey(plugin).required())
                .withArgument(ArgumentTypes.integerAbs(CommandArguments.AMOUNT).localized(Lang.COMMAND_ARGUMENT_NAME_AMOUNT).withSamples(context -> Lists.newList("1", "5", "10")))
                .withArgument(ArgumentTypes.world(CommandArguments.WORLD))
                .withFlag(CommandFlags.silent())
                .withFlag(CommandFlags.silentFeedback())
                .executes((context, arguments) -> giveKeyAll(plugin, context, arguments))
            )
            .branch(Commands.literal("give", builder -> buildKeyManage(plugin, builder)
                .description(Lang.COMMAND_KEY_GIVE_DESC)
                .permission(Perms.COMMAND_KEY_GIVE)
                .executes((context, arguments) -> giveKey(plugin, context, arguments))
            ))
            .branch(Commands.literal("set", builder -> buildKeyManage(plugin, builder)
                .description(Lang.COMMAND_KEY_SET_DESC)
                .permission(Perms.COMMAND_KEY_SET)
                .executes((context, arguments) -> setKey(plugin, context, arguments))
            ))
            .branch(Commands.literal("take", builder -> buildKeyManage(plugin, builder)
                .description(Lang.COMMAND_KEY_TAKE_DESC)
                .permission(Perms.COMMAND_KEY_TAKE)
                .executes((context, arguments) -> takeKey(plugin, context, arguments))
            ))
        );

        root.branch(Commands.literal("drop")
            .description(Lang.COMMAND_DROP_DESC)
            .permission(Perms.COMMAND_DROP)
            .withArguments(
                CommandArguments.forCrate(plugin),
                Arguments.decimal(CommandArguments.X).localized(Lang.COMMAND_ARGUMENT_NAME_X).suggestions((reader, context) -> getCoords(context, Location::getBlockX)),
                Arguments.decimal(CommandArguments.Y).localized(Lang.COMMAND_ARGUMENT_NAME_Y).suggestions((reader, context) -> getCoords(context, Location::getBlockY)),
                Arguments.decimal(CommandArguments.Z).localized(Lang.COMMAND_ARGUMENT_NAME_Z).suggestions((reader, context) -> getCoords(context, Location::getBlockZ)),
                Arguments.world(CommandArguments.WORLD)
            )
            .executes((context, arguments) -> dropCrate(plugin, context, arguments))
        );

        root.branch(Commands.literal("editor")
            .description(Lang.COMMAND_EDITOR_DESC)
            .permission(Perms.COMMAND_EDITOR)
            .playerOnly()
            .executes((context, arguments) -> openEditor(plugin, context))
        );

        root.branch(Commands.literal("give")
            .description(Lang.COMMAND_GIVE_DESC)
            .permission(Perms.COMMAND_GIVE)
            .withArgument(CommandArguments.crossServerPlayerName(plugin).required())
            .withArgument(CommandArguments.forCrate(plugin).required())
            .withArgument(ArgumentTypes.integerAbs(CommandArguments.AMOUNT).localized(Lang.COMMAND_ARGUMENT_NAME_AMOUNT).withSamples(context -> Lists.newList("1", "5", "10")))
            .withFlag(CommandFlags.silent())
            .withFlag(CommandFlags.silentFeedback())
            .executes((context, arguments) -> giveCrate(plugin, context, arguments))
        );

        root.branch(Commands.literal("open")
            .description(Lang.COMMAND_OPEN_DESC)
            .permission(Perms.COMMAND_OPEN)
            .playerOnly()
            .withArguments(CommandArguments.forCrate(plugin))
            .executes((context, arguments) -> openCrate(plugin, context, arguments))
        );

        root.branch(Commands.literal("openfor")
            .description(Lang.COMMAND_OPEN_FOR_DESC)
            .permission(Perms.COMMAND_OPEN_FOR)
            .withArguments(
                Arguments.player(CommandArguments.PLAYER),
                CommandArguments.forCrate(plugin)
            )
            .withFlags(CommandFlags.SILENT, CommandFlags.FORCE)
            .executes((context, arguments) -> openCrateFor(plugin, context, arguments))
        );

        root.branch(Commands.literal("preview")
            .description(Lang.COMMAND_PREVIEW_DESC)
            .permission(Perms.COMMAND_PREVIEW)
            .withArgument(CommandArguments.forCrate(plugin).required())
            .withArgument(CommandArguments.crossServerPlayerName(plugin).permission(Perms.COMMAND_PREVIEW_OTHERS))
            .executes((context, arguments) -> previewCrate(plugin, context, arguments))
        );

        root.branch(Commands.literal("resetcooldown")
            .description(Lang.COMMAND_RESET_COOLDOWN_DESC)
            .permission(Perms.COMMAND_RESETCOOLDOWN)
            .withArgument(CommandArguments.crossServerPlayerName(plugin).required())
            .withArgument(CommandArguments.forCrate(plugin).required())
            .executes((context, arguments) -> resetCrateCooldown(plugin, context, arguments))
        );
    }

    @NotNull
    private static List<String> getCoords(@NotNull CommandContext context, @NotNull Function<Location, Integer> function) {
        Player player = context.getPlayer();
        if (player == null) return Collections.emptyList();

        Location location = player.getLocation();
        return Lists.newList(String.valueOf(function.apply(location)));
    }

    @NotNull
    private static LiteralNodeBuilder buildKeyManage(@NotNull CratesPlugin plugin, @NotNull LiteralNodeBuilder builder) {
        return builder
            .withArgument(CommandArguments.crossServerPlayerName(plugin).required())
            .withArgument(CommandArguments.forKey(plugin).required())
            .withArgument(ArgumentTypes.integerAbs(CommandArguments.AMOUNT).localized(Lang.COMMAND_ARGUMENT_NAME_AMOUNT).withSamples(context -> Lists.newList("1", "5", "10")))
            .withFlag(CommandFlags.silent())
            .withFlag(CommandFlags.silentFeedback());
    }

    private static boolean dropCrate(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        Crate crate = arguments.get(CommandArguments.CRATE, Crate.class);

        double x = arguments.getDouble(CommandArguments.X);
        double y = arguments.getDouble(CommandArguments.Y);
        double z = arguments.getDouble(CommandArguments.Z);
        World world = arguments.getWorld(CommandArguments.WORLD);

        Location location = new Location(world, x, y, z);

        plugin.runAtLocation(location, () -> {
            if (plugin.getCrateManager().dropCrateItem(crate, location)) {
                Lang.COMMAND_DROP_DONE.getMessage().send(context.getSender(), replacer -> replacer
                    .replace(crate.replacePlaceholders())
                    .replace(Placeholders.forLocation(location))
                );
            }
        });
        return true;
    }

    private static boolean openEditor(@NotNull CratesPlugin plugin, @NotNull CommandContext context) {
        plugin.getEditorManager().openEditor(context.getPlayerOrThrow());
        return true;
    }

    private static boolean giveCrate(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        Crate crate = arguments.getArgument(CommandArguments.CRATE, Crate.class);
        int amount = arguments.getIntArgument(CommandArguments.AMOUNT, 1);
        if (amount <= 0) return false;

        plugin.getUserManager().manageUser(arguments.getStringArgument(CommandArguments.PLAYER), user -> {
            if (user == null) {
                context.errorBadPlayer();
                return;
            }

            Player online = user.getPlayer();
            if (online != null) {
                plugin.getCrateManager().giveCrateItem(online, crate, amount);
                if (!arguments.hasFlag(CommandFlags.SILENT)) {
                    Lang.COMMAND_GIVE_NOTIFY.getMessage().send(online, replacer -> replacer
                        .replace(Placeholders.GENERIC_AMOUNT, amount)
                        .replace(crate.replacePlaceholders())
                    );
                }
            }
            else {
                plugin.getCrateManager().giveCrateItemCrossServer(crate, user.getId(), amount);
            }

            if (!arguments.hasFlag(CommandFlags.SILENT_FEEDBACK)) {
                Lang.COMMAND_GIVE_DONE.getMessage().send(context.getSender(), replacer -> replacer
                    .replace(Placeholders.PLAYER_NAME, user.getName())
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(crate.replacePlaceholders())
                );
            }
        });
        return true;
    }

    private static boolean openCrate(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        Crate crate = arguments.get(CommandArguments.CRATE, Crate.class);
        Player player = context.getPlayerOrThrow();
        plugin.getCrateManager().preOpenCrate(player, new CrateSource(crate));
        return true;
    }

    private static boolean openCrateFor(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        Player player = arguments.getPlayer(CommandArguments.PLAYER);
        Crate crate = arguments.get(CommandArguments.CRATE, Crate.class);

        if (!context.hasFlag(CommandFlags.SILENT)) {
            Lang.COMMAND_OPEN_FOR_NOTIFY.message().send(context.getSender(), replacer -> replacer.replace(crate.replacePlaceholders()));
        }
        if (context.getSender() != player) {
            Lang.COMMAND_OPEN_FOR_DONE.message().send(context.getSender(), replacer -> replacer
                .replace(Placeholders.forPlayer(player))
                .replace(crate.replacePlaceholders())
            );
        }

        boolean force = context.hasFlag(CommandFlags.FORCE);
        CrateSource source = new CrateSource(crate);

        if (!force) {
            Cost cost = crate.getCosts().stream().filter(c -> c.isAvailable() && c.canAfford(player)).findAny().or(crate::getFirstCost).orElse(null);
            plugin.getCrateManager().openCrate(player, source, OpenOptions.empty(), cost);
        }
        else {
            plugin.getCrateManager().openCrate(player, source, OpenOptions.ignoreRestrictions(), null);
        }
        return true;
    }

    private static boolean previewCrate(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        Crate crate = arguments.get(CommandArguments.CRATE, Crate.class);
        Player player = plugin.getServer().getPlayer(arguments.getString(CommandArguments.PLAYER, context.getSender().getName()));
        if (player == null) {
            context.errorBadPlayer();
            return false;
        }

        plugin.getCrateManager().previewCrate(player, new CrateSource(crate));

        if (context.getSender() != player) {
            Lang.COMMAND_PREVIEW_DONE_OTHERS.message().send(context.getSender(), replacer -> replacer
                .replace(Placeholders.forPlayer(player))
                .replace(crate.replacePlaceholders())
            );
        }
        return true;
    }

    private static boolean resetCrateCooldown(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        plugin.getUserManager().manageUser(arguments.getString(CommandArguments.PLAYER), user -> {
            if (user == null) {
                context.errorBadPlayer();
                return;
            }

            Crate crate = arguments.get(CommandArguments.CRATE, Crate.class);
            user.getCrateData(crate).setOpenCooldown(0);
            plugin.getUserManager().save(user);

            Lang.COMMAND_RESET_COOLDOWN_DONE.message().send(context.getSender(), replacer -> replacer
                .replace(Placeholders.PLAYER_NAME, user.getName())
                .replace(crate.replacePlaceholders())
            );
        });
        return true;
    }



    private static boolean dropKey(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        CrateKey key = arguments.get(CommandArguments.KEY, CrateKey.class);

        double x = arguments.getDouble(CommandArguments.X);
        double y = arguments.getDouble(CommandArguments.Y);
        double z = arguments.getDouble(CommandArguments.Z);
        World world = arguments.getWorld(CommandArguments.WORLD);

        Location location = new Location(world, x, y, z);

        plugin.runAtLocation(location, () -> {
            if (plugin.getKeyManager().dropKeyItem(key, location)) {
                Lang.COMMAND_DROP_KEY_DONE.getMessage().send(context.getSender(), replacer -> replacer
                    .replace(key.replacePlaceholders())
                    .replace(Placeholders.forLocation(location))
                );
            }
        });
        return true;
    }

    private static boolean giveKeyAll(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        CrateKey key = arguments.get(CommandArguments.KEY, CrateKey.class);

        int amount = arguments.getInt(CommandArguments.AMOUNT, 1);
        if (amount == 0) return false;

        boolean silent = arguments.hasFlag(CommandFlags.SILENT);
        World targetWorld = arguments.hasArgument(CommandArguments.WORLD)
            ? arguments.getWorldArgument(CommandArguments.WORLD)
            : null;

        Players.getOnline().forEach(player -> {
            if (!player.hasPermission(Perms.INCLUDE_KEY_GIVEALL)) return;
            if (targetWorld != null && !player.getWorld().equals(targetWorld)) return;

            plugin.getKeyManager().giveKey(player, key, amount);

            if (!silent) {
                Lang.COMMAND_KEY_GIVE_NOTIFY.message().send(player, replacer -> replacer
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders())
                );
            }
        });

        if (!context.hasFlag(CommandFlags.SILENT_FEEDBACK)) {
            Lang.COMMAND_KEY_GIVE_ALL_DONE.message().send(context.getSender(), replacer -> replacer
                .replace(Placeholders.GENERIC_AMOUNT, amount)
                .replace(key.replacePlaceholders()));
        }
        return true;
    }

    private static boolean inspectKeys(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        plugin.getUserManager().manageUser(arguments.getString(CommandArguments.PLAYER, context.getSender().getName()), user -> {
            if (user == null) {
                context.errorBadPlayer();
                return;
            }

            Lang.COMMAND_KEY_INSPECT_LIST.message().send(context.getSender(), replacer -> replacer
                .replace(Placeholders.PLAYER_NAME, user.getName())
                .replace(Placeholders.GENERIC_ENTRY, list -> {
                    plugin.getKeyManager().getKeys().forEach(key -> {
                        if (!key.isVirtual()) return;

                        list.add(key.replacePlaceholders().apply(Lang.COMMAND_KEY_INSPECT_ENTRY.text()
                            .replace(Placeholders.GENERIC_AMOUNT, NumberUtil.format(user.countKeys(key.getId())))
                        ));
                    });
                })
            );
        });
        return true;
    }

    private static boolean giveKey(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        CrateKey key = arguments.get(CommandArguments.KEY, CrateKey.class);

        int amount = arguments.getInt(CommandArguments.AMOUNT, 1);
        if (amount <= 0) return false;

        plugin.getUserManager().manageUser(arguments.getString(CommandArguments.PLAYER), user -> {
            if (user == null) {
                context.errorBadPlayer();
                return;
            }

            if (key.isVirtual()) {
                plugin.getKeyManager().giveKey(user, key, amount);
                plugin.getUserManager().save(user);
            }
            else {
                Player online = user.getPlayer();
                if (online != null) {
                    plugin.getKeyManager().giveKey(online, key, amount);
                }
                else {
                    plugin.getKeyManager().givePhysicalKeyCrossServer(key, user.getId(), amount);
                    user.addKeysOnHold(key.getId(), amount);
                    plugin.getUserManager().save(user);
                }
            }

            Player target = user.getPlayer();
            if (target != null && !arguments.hasFlag(CommandFlags.SILENT)) {
                Lang.COMMAND_KEY_GIVE_NOTIFY.getMessage().send(target, replacer -> replacer
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders())
                );
            }

            if (!arguments.hasFlag(CommandFlags.SILENT_FEEDBACK)) {
                Lang.COMMAND_KEY_GIVE_DONE.getMessage().send(context.getSender(), replacer -> replacer
                    .replace(Placeholders.PLAYER_NAME, user.getName())
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders()));
            }
        });
        return true;
    }

    // Cross-server immediate PHYSICAL key delivery:
    // - If target is on this server, item is handed instantly.
    // - Otherwise, a Redis pub/sub request is broadcast and the node with the player online will deliver.
    private static boolean givePhysicalKeyCross(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        CrateKey key = arguments.getArgument(CommandArguments.KEY, CrateKey.class);

        int amount = arguments.getIntArgument(CommandArguments.AMOUNT, 1);
        if (amount <= 0) return false;

        plugin.getUserManager().manageUser(arguments.getStringArgument(CommandArguments.PLAYER), user -> {
            if (user == null) {
                context.errorBadPlayer();
                return;
            }

            if (key.isVirtual()) {
                // Fall back to regular give for virtual keys.
                plugin.getKeyManager().giveKey(user, key, amount);
                plugin.getUserManager().save(user);
            }
            else {
                // Use Redis cross-server path for physical keys.
                plugin.getKeyManager().givePhysicalKeyCrossServer(key, user.getId(), amount);
            }

            Player target = user.getPlayer();
            if (target != null && !context.hasFlag(CommandFlags.SILENT)) {
                Lang.COMMAND_KEY_GIVE_NOTIFY.message().send(target, replacer -> replacer
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders())
                );
            }

            if (!context.hasFlag(CommandFlags.SILENT_FEEDBACK)) {
                Lang.COMMAND_KEY_GIVE_DONE.message().send(context.getSender(), replacer -> replacer
                    .replace(Placeholders.PLAYER_NAME, user.getName())
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders()));
            }
        });
        return true;
    }

    private static boolean setKey(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        CrateKey key = arguments.get(CommandArguments.KEY, CrateKey.class);

        int amount = arguments.getInt(CommandArguments.AMOUNT, 1);
        if (amount <= 0) return false;

        plugin.getUserManager().manageUser(arguments.getString(CommandArguments.PLAYER), user -> {
            if (user == null) {
                context.errorBadPlayer();
                return;
            }

            plugin.getKeyManager().setKey(user, key, amount);
            plugin.getUserManager().save(user);

            Player target = user.getPlayer();
            if (target != null && !context.hasFlag(CommandFlags.SILENT)) {
                Lang.COMMAND_KEY_SET_NOTIFY.message().send(target, replacer -> replacer
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders())
                );
            }

            if (!context.hasFlag(CommandFlags.SILENT_FEEDBACK)) {
                Lang.COMMAND_KEY_SET_DONE.message().send(context.getSender(), replacer -> replacer
                    .replace(Placeholders.PLAYER_NAME, user.getName())
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders()));
            }
        });
        return true;
    }

    private static boolean takeKey(@NotNull CratesPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
        CrateKey key = arguments.get(CommandArguments.KEY, CrateKey.class);

        int amount = arguments.getInt(CommandArguments.AMOUNT, 1);
        if (amount <= 0) return false;

        plugin.getUserManager().manageUser(arguments.getString(CommandArguments.PLAYER), user -> {
            if (user == null) {
                context.errorBadPlayer();
                return;
            }

            plugin.getKeyManager().takeKey(user, key, amount);
            plugin.getUserManager().save(user);

            Player target = user.getPlayer();
            if (target != null && !context.hasFlag(CommandFlags.SILENT)) {
                Lang.COMMAND_KEY_TAKE_NOTIFY.message().send(target, replacer -> replacer
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders())
                );
            }

            if (!context.hasFlag(CommandFlags.SILENT_FEEDBACK)) {
                Lang.COMMAND_KEY_TAKE_DONE.message().send(context.getSender(), replacer -> replacer
                    .replace(Placeholders.PLAYER_NAME, user.getName())
                    .replace(Placeholders.GENERIC_AMOUNT, amount)
                    .replace(key.replacePlaceholders()));
            }
        });
        return true;
    }
}
