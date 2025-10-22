package su.nightexpress.excellentcrates.command;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.config.Lang;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.key.CrateKey;
import su.nightexpress.nightcore.command.experimental.argument.ArgumentTypes;
import su.nightexpress.nightcore.command.experimental.argument.CommandArgument;
import su.nightexpress.nightcore.command.experimental.builder.ArgumentBuilder;
import su.nightexpress.nightcore.util.Players;

import java.util.ArrayList;
import java.util.List;

public class CommandArguments {

    public static final String PLAYER = "player";
    public static final String CRATE  = "crate";
    public static final String KEY    = "key";
    public static final String AMOUNT = "amount";
    public static final String X      = "x";
    public static final String Y      = "y";
    public static final String Z      = "z";
    public static final String WORLD  = "world";

    @NotNull
    public static ArgumentNodeBuilder<Crate> forCrate(@NotNull CratesPlugin plugin) {
        return Commands.argument(CRATE, (context, string) -> Optional.ofNullable(plugin.getCrateManager().getCrateById(string)).orElseThrow(() -> CommandSyntaxException.custom(Lang.ERROR_COMMAND_INVALID_CRATE_ARGUMENT)))
            .localized(Lang.COMMAND_ARGUMENT_NAME_CRATE)
            .suggestions((reader, context) -> plugin.getCrateManager().getCrateIds());
    }

    @NotNull
    public static ArgumentNodeBuilder<CrateKey> forKey(@NotNull CratesPlugin plugin) {
        return Commands.argument(KEY, (context, string) -> Optional.ofNullable(plugin.getKeyManager().getKeyById(string)).orElseThrow(() -> CommandSyntaxException.custom(Lang.ERROR_COMMAND_INVALID_KEY_ARGUMENT)))
            .localized(Lang.COMMAND_ARGUMENT_NAME_KEY)
            .suggestions((reader, context) -> plugin.getKeyManager().getKeyIds());
    }

    @NotNull
    public static ArgumentBuilder<String> crossServerPlayerName(@NotNull CratesPlugin plugin) {
        return CommandArgument.builder(PLAYER, ArgumentTypes.STRING)
            .localized(Lang.COMMAND_ARGUMENT_NAME_PLAYER)
            .withSamples(context -> {
                List<String> names = new ArrayList<>();
                if (context.getPlayer() != null) {
                    names.addAll(Players.playerNames(context.getPlayer()));
                } else {
                    names.addAll(Players.playerNames());
                }
                plugin.getRedisSyncManager().ifPresent(redis -> {
                    names.addAll(redis.getAllPlayerNames());
                });
                return names.stream().distinct().sorted().toList();
            });
    }
}
