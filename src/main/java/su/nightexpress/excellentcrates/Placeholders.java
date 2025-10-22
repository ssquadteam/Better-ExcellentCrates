package su.nightexpress.excellentcrates;

import org.bukkit.Material;
import org.bukkit.block.Block;
import su.nightexpress.excellentcrates.api.crate.Reward;
import su.nightexpress.excellentcrates.crate.cost.Cost;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.crate.impl.Milestone;
import su.nightexpress.excellentcrates.crate.impl.Rarity;
import su.nightexpress.excellentcrates.key.CrateKey;
import su.nightexpress.excellentcrates.util.FoliaBlockUtils;
import su.nightexpress.excellentcrates.util.inspect.Inspection;
import su.nightexpress.excellentcrates.util.inspect.Inspectors;
import su.nightexpress.nightcore.language.LangAssets;
import su.nightexpress.nightcore.util.*;
import su.nightexpress.nightcore.util.placeholder.PlaceholderList;

public class Placeholders extends su.nightexpress.nightcore.util.Placeholders {

    public static final String WIKI_URL          = "https://nightexpressdev.com/excellentcrates/";
    public static final String WIKI_WEIGHTS      = WIKI_URL + "rewards/rarity-weights/";
    public static final String WIKI_PLACEHOLDERS = WIKI_URL + "placeholders/internal";

    public static final String GENERIC_NAME       = "%name%";
    public static final String GENERIC_AMOUNT     = "%amount%";
    public static final String GENERIC_ID         = "%id%";
    public static final String GENERIC_CURRENT    = "%current%";
    public static final String GENERIC_MAX        = "%max%";
    public static final String GENERIC_TIME       = "%time%";
    public static final String GENERIC_KEYS       = "%keys%";
    public static final String GENERIC_MODE       = "%mode%";
    public static final String GENERIC_TYPE       = "%type%";
    public static final String GENERIC_REWARDS    = "%rewards%";
    public static final String GENERIC_COSTS      = "%costs%";
    public static final String GENERIC_AVAILABLE  = "%available%";
    public static final String GENERIC_STATE      = "%state%";
    public static final String GENERIC_PROBLEMS   = "%problems%";
    public static final String GENERIC_INSPECTION = "%inspection%";
    public static final String GENERIC_COOLDOWN   = "%cooldown%";
    public static final String GENERIC_LIMITS     = "%limits%";

    public static final String RARITY_ID          = "%rarity_id%";
    public static final String RARITY_NAME        = "%rarity_name%";
    public static final String RARITY_WEIGHT      = "%rarity_weight%";
    public static final String RARITY_ROLL_CHANCE = "%rarity_roll_chance%";

    public static final String MILESTONE_OPENINGS       = "%milestone_openings%";
    public static final String MILESTONE_REWARD_ID      = "%milestone_reward_id%";

    public static final String CRATE_ID          = "%crate_id%";
    public static final String CRATE_NAME        = "%crate_name%";
    public static final String CRATE_DESCRIPTION = "%crate_description%";
    public static final String CRATE_LAST_OPENER = "%crate_last_opener%";
    public static final String CRATE_LAST_REWARD = "%crate_last_reward%";

    public static final String CRATE_ITEM_STACKABLE        = "%crate_item_stackable%";
    public static final String CRATE_ANIMATION_ENABLED     = "%crate_animation_enabled%";
    public static final String CRATE_ANIMATION_ID          = "%crate_animation_id%";
    public static final String CRATE_PREVIEW_ENABLED       = "%crate_preview_enabled%";
    public static final String CRATE_PREVIEW_ID            = "%crate_preview_id%";
    public static final String CRATE_PERMISSION_REQUIRED   = "%crate_permission_required%";
    public static final String CRATE_KEY_REQUIRED          = "%crate_key_required%";
    public static final String CRATE_KEYS                  = "%crate_key_ids%";
    public static final String CRATE_PUSHBACK_ENABLED      = "%crate_pushback_enabled%";
    public static final String CRATE_HOLOGRAM_ENABLED      = "%crate_hologram_enabled%";
    public static final String CRATE_HOLOGRAM_TEMPLATE     = "%crate_hologram_template%";
    public static final String CRATE_HOLOGRAM_Y_OFFSET     = "%crate_hologram_y_offset%";
    public static final String CRATE_LOCATIONS             = "%crate_locations%";
    public static final String CRATE_EFFECT_MODEL          = "%crate_effect_model%";
    public static final String CRATE_EFFECT_PARTICLE_NAME  = "%crate_effect_particle_name%";
    public static final String CRATE_REWARDS_AMOUNT        = "%crate_rewards_amount%";
    public static final String CRATE_MILESTONES_AMOUNT     = "%crate_milestones_amount%";
    public static final String CRATE_MILESTONES_REPEATABLE = "%crate_milestones_repeatable%";

    public static final String KEY_ID             = "%key_id%";
    public static final String KEY_NAME           = "%key_name%";
    public static final String KEY_VIRTUAL        = "%key_virtual%";
    public static final String KEY_UUID           = "%key_uuid%";
    public static final String KEY_CREATION_TIME  = "%key_creation_time%";
    public static final String KEY_VALID_CHECK    = "%key_valid_check%";

    public static final String REWARD_ID                 = "%reward_id%";
    public static final String REWARD_NAME               = "%reward_name%";
    public static final String REWARD_DESCRIPTION        = "%reward_description%";
    public static final String REWARD_WEIGHT             = "%reward_weight%";
    public static final String REWARD_ROLL_CHANCE        = "%reward_roll_chance%";
    public static final String REWARD_RARITY_NAME        = "%reward_rarity_name%";
    public static final String REWARD_RARITY_WEIGHT      = "%reward_rarity_weight%";
    public static final String REWARD_RARITY_ROLL_CHANCE = "%reward_rarity_roll_chance%";

    public static final String COST_ID   = "%cost_id%";
    public static final String COST_NAME = "%cost_name%";

    public static final PlaceholderList<Crate> CRATE = PlaceholderList.create(list -> list
        .add(CRATE_ID, Crate::getId)
        .add(CRATE_NAME, Crate::getName)
        .add(CRATE_DESCRIPTION, crate -> String.join("\n", crate.getDescription()))
        .add(CRATE_LAST_OPENER, Crate::getLastOpenerName)
        .add(CRATE_LAST_REWARD, Crate::getLastRewardName)
    );

    public static final Function<Inspection<?>, String> INSPECTION_TYPE     = type -> "%inspection_" + type.name().toLowerCase() + "%";
    public static final String                          INSPECTION_PROBLEMS = "%inspection_problems%";

    public static final PlaceholderList<Crate> CRATE = PlaceholderList.create(list -> {
        list
            .add(CRATE_ID, Crate::getId)
            .add(CRATE_NAME, Crate::getName)
            .add(CRATE_DESCRIPTION, crate -> String.join("\n", crate.getDescription()))
            .add(CRATE_PERMISSION, Crate::getPermission)
            .add(CRATE_OPEN_COST, crate -> {
                return crate.getOpenCosts().stream().filter(Cost::isValid).map(Cost::format).collect(Collectors.joining(", "));
            })
            .add(CRATE_OPEN_COOLDOWN, crate -> {
                if (crate.getOpenCooldown() == 0L) return Lang.OTHER_DISABLED.getString();
                if (crate.getOpenCooldown() < 0L) return Lang.OTHER_ONE_TIMED.getString();

                return TimeFormats.toLiteral(crate.getOpenCooldown() * 1000L);
            })
            .add(CRATE_LAST_OPENER, Crate::getLastOpenerName)
            .add(CRATE_LAST_REWARD, Crate::getLastRewardName);

        Inspectors.CRATE.addPlaceholders(list);
    });

    public static final PlaceholderList<Crate> CRATE_EDITOR = PlaceholderList.create(list -> {
        list.add(CRATE);
        list
            .add(CRATE_ITEM_STACKABLE, crate -> Lang.getYesOrNo(crate.isItemStackable()))
            .add(CRATE_PERMISSION_REQUIRED, crate -> Lang.getYesOrNo(crate.isPermissionRequired()))
            .add(CRATE_KEY_REQUIRED, crate -> Lang.getYesOrNo(crate.isKeyRequired()))
            .add(CRATE_KEYS, crate -> {
                return crate.getKeyIds().stream().map(id -> {
                    CrateKey key = CratesAPI.getKeyManager().getKeyById(id);
                    return key == null ? Lang.badEntry(id) : Lang.goodEntry(key.getName());
                }).collect(Collectors.joining("\n"));
            })
            .add(CRATE_PUSHBACK_ENABLED, crate -> Lang.getEnabledOrDisabled(crate.isPushbackEnabled()))
            .add(CRATE_HOLOGRAM_ENABLED, crate -> Lang.getEnabledOrDisabled(crate.isHologramEnabled()))
            .add(CRATE_HOLOGRAM_TEMPLATE, Crate::getHologramTemplateId)
            .add(CRATE_HOLOGRAM_Y_OFFSET, crate -> NumberUtil.format(crate.getHologramYOffset()))
            .add(CRATE_LOCATIONS, crate -> {
                return crate.getBlockPositions().stream().map(worldPos -> {
                    String x = Tags.LIGHT_ORANGE.wrap(NumberUtil.format(worldPos.getX()));
                    String y = Tags.LIGHT_ORANGE.wrap(NumberUtil.format(worldPos.getY()));
                    String z = Tags.LIGHT_ORANGE.wrap(NumberUtil.format(worldPos.getZ()));
                    String world = Tags.LIGHT_ORANGE.wrap(worldPos.getWorldName());
                    String coords = x + ", " + y + ", " + z + " in " + world;
                    return Lang.goodEntry(coords);
                }).collect(Collectors.joining("\n"));
            })
            .add(CRATE_EFFECT_MODEL, crate -> StringUtil.capitalizeUnderscored(crate.getEffectType()))
            .add(CRATE_EFFECT_PARTICLE_NAME, crate -> {
                UniParticle wrapped = crate.getEffectParticle();
                return wrapped.isEmpty() ? "null" : BukkitThing.toString(wrapped.getParticle());
            })
            .add(CRATE_REWARDS_AMOUNT, crate -> NumberUtil.format(crate.getRewards().size()))
            .add(CRATE_MILESTONES_AMOUNT, crate -> NumberUtil.format(crate.getMilestones().size()))
            .add(CRATE_MILESTONES_REPEATABLE, crate -> Lang.getYesOrNo(crate.isMilestonesRepeatable()))
            .add(CRATE_ANIMATION_ENABLED, crate -> Lang.getYesOrNo(crate.isAnimationEnabled()))
            .add(CRATE_ANIMATION_ID, Crate::getAnimationId)
            .add(CRATE_PREVIEW_ENABLED, crate -> Lang.getYesOrNo(crate.isPreviewEnabled()))
            .add(CRATE_PREVIEW_ID, Crate::getPreviewId);
    });

    public static final PlaceholderList<Reward> REWARD = PlaceholderList.create(list -> {
        list
            .add(REWARD_ID, Reward::getId)
            .add(REWARD_NAME, Reward::getName)
            .add(REWARD_DESCRIPTION, reward -> String.join("\n", reward.getDescription()))
            .add(REWARD_WEIGHT, reward -> NumberUtil.format(reward.getWeight()))
            .add(REWARD_ROLL_CHANCE, reward -> NumberUtil.format(reward.getRollChance()))
            .add("%reward_chance%", reward -> NumberUtil.format(reward.getWeight()))
            .add("%reward_real_chance%", reward -> NumberUtil.format(reward.getRollChance()))
            .add(REWARD_RARITY_NAME, reward -> reward.getRarity().getName())
            .add("%reward_rarity_chance%", reward -> NumberUtil.format(reward.getRarity().getWeight()))
            .add(REWARD_RARITY_WEIGHT, reward -> NumberUtil.format(reward.getRarity().getWeight()))
            .add(REWARD_RARITY_ROLL_CHANCE, reward -> NumberUtil.format(reward.getRarity().getRollChance(reward.getCrate())))
            .add("%reward_preview_name%", Reward::getName)
            .add("%reward_preview_lore%", reward -> String.join("\n", reward.getDescription()));
    });

    public static final PlaceholderList<Reward> REWARD_EDITOR = PlaceholderList.create(list -> {
        list.add(REWARD);
        list
            .add(REWARD_BROADCAST, reward -> Lang.getYesOrNo(reward.isBroadcast()))
            .add(REWARD_PLACEHOLDER_APPLY, reward -> Lang.getYesOrNo(reward.isPlaceholderApply()))
            .add(REWARD_IGNORED_PERMISSIONS, reward -> {
                return String.join("\n", Lists.modify(reward.getIgnoredPermissions(), Lang::goodEntry));
            })
            .add(REWARD_REQUIRED_PERMISSIONS, reward -> {
                return String.join("\n", Lists.modify(reward.getRequiredPermissions(), Lang::goodEntry));
            })
            .add("%reward_inspect_content%", reward -> "");

        Inspectors.REWARD.addPlaceholders(list);
    });

    public static final PlaceholderList<ItemReward> ITEM_REWARD_EDITOR = PlaceholderList.create(list -> {
        list.add(REWARD_EDITOR);
        list.add(REWARD_CUSTOM_PREVIEW, reward -> Lang.getYesOrNo(reward.isCustomPreview()));
        list.add(REWARD_ITEMS_CONTENT, reward -> {
            return reward.getItems().stream().map(ItemProvider::getItemStack)
                .map(item -> Lang.goodEntry(ItemUtil.getSerializedName(item) + " x" + item.getAmount())).collect(Collectors.joining("\n"));
        });
    });

    public static final PlaceholderList<CommandReward> COMMAND_REWARD_EDITOR = PlaceholderList.create(list -> {
        list.add(REWARD_EDITOR);
        list.add(REWARD_COMMANDS_CONTENT, reward -> {
            return reward.getCommands().stream().map(Lang::goodEntry).collect(Collectors.joining("\n"));
        });
    });

    public static final PlaceholderList<LimitValues> LIMIT_VALUES = PlaceholderList.create(list -> list
        .add(LIMIT_ENABLED, values -> Lang.getYesOrNo(values.isEnabled()))
        .add(LIMIT_AMOUNT, values -> values.isUnlimitedAmount() ? Lang.OTHER_INFINITY.getString() : String.valueOf(values.getAmount()))
        .add(LIMIT_RESET_TIME_STEP, values -> String.valueOf(values.getResetStep()))
        .add(LIMIT_RESET_TIME, values -> {
            if (values.isMidnight()) return Lang.OTHER_MIDNIGHT.getString();
            if (values.isNeverReset()) return Lang.OTHER_NEVER.getString();

            return TimeFormats.toLiteral(values.getResetTime() * 1000L);
        })
    );

    public static final PlaceholderList<Milestone> MILESTONE = PlaceholderList.create(list -> list
        .add(MILESTONE_OPENINGS, milestone -> NumberUtil.format(milestone.getOpenings()))
        .add(MILESTONE_REWARD_ID, Milestone::getRewardId)
    );

    public static final PlaceholderList<Rarity> RARITY = PlaceholderList.create(list -> list
        .add(RARITY_ID, Rarity::getId)
        .add(RARITY_NAME, Rarity::getName)
        .add(RARITY_WEIGHT, rarity -> NumberUtil.format(rarity.getWeight()))
        .add(RARITY_ROLL_CHANCE, rarity -> NumberUtil.format(rarity.getRollChance()))
    );

    public static final PlaceholderList<CrateKey> KEY = PlaceholderList.create(list -> {
        list
            .add(KEY_ID, CrateKey::getId)
            .add(KEY_NAME, CrateKey::getName)
            .add(KEY_VIRTUAL, key -> Lang.getYesOrNo(key.isVirtual()))
            .add(KEY_UUID, key -> "-")
            .add(KEY_CREATION_TIME, key -> "-")
            .add(KEY_VALID_CHECK, key -> "-");

    public static final PlaceholderList<Cost> COST = PlaceholderList.create(list -> list
        .add(COST_ID, Cost::getId)
        .add(COST_NAME, Cost::getName)
    );
}
