package su.nightexpress.excellentcrates.hologram;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.config.Config;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.hologram.entity.FakeDisplay;
import su.nightexpress.excellentcrates.hologram.entity.FakeEntity;
import su.nightexpress.excellentcrates.hologram.entity.FakeEntityGroup;
import su.nightexpress.excellentcrates.hologram.handler.HologramPacketsHandler;
import su.nightexpress.excellentcrates.hologram.listener.HologramListener;
import su.nightexpress.excellentcrates.hooks.HookId;
import su.nightexpress.excellentcrates.util.pos.WorldPos;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.LocationUtil;
import su.nightexpress.nightcore.util.Plugins;

import java.util.*;

public class HologramManager extends AbstractManager<CratesPlugin> {

    private final Map<String, FakeDisplay> displayMap;
    private final AsyncHologramProcessor asyncProcessor;

    private HologramHandler handler;

    public HologramManager(@NotNull CratesPlugin plugin) {
        super(plugin);
        this.displayMap = new HashMap<>();
        this.asyncProcessor = new AsyncHologramProcessor();
    }

    @Override
    protected void onLoad() {
        if (this.detectHandler()) {
            this.addListener(new HologramListener(this.plugin, this));

            this.startAsyncHologramTicker();
        }
    }

    @Override
    protected void onShutdown() {
        this.displayMap.values().forEach(this::discard);
        this.displayMap.clear();

        this.handler = null;
    }

    private boolean detectHandler() {
        if (Plugins.isInstalled(HookId.PACKET_EVENTS)) {
            this.handler = new HologramPacketsHandler();
        }
        else {
            this.plugin.warn("*".repeat(25));
            this.plugin.warn("You have no packet library plugin installed for the Holograms feature to work.");
            this.plugin.warn("Please install PacketEvents (" + HookId.PACKET_EVENTS + ") to enable crate holograms.");
            this.plugin.warn("*".repeat(25));
        }

        return this.hasHandler();
    }

    private void startAsyncHologramTicker() {
        this.plugin.getFoliaScheduler().runTimerAsync(() -> {
            if (this.plugin.getCrateManager() == null) return;
            this.processHologramsAsync();
        }, 0L, Config.CRATE_HOLOGRAM_UPDATE_INTERVAL.get());
    }

    private void processHologramsAsync() {
        this.plugin.getCrateManager().getCrates().forEach(crate -> {
            if (!crate.isHologramEnabled()) return;

            try {
                this.processHologramAsync(crate);
            } catch (Exception e) {
                this.plugin.error("Error processing hologram for crate " + crate.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void processHologramAsync(@NotNull Crate crate) {
        this.createIfAbsent(crate);

        FakeDisplay display = this.getDisplay(crate);
        if (display == null) return;

        AsyncHologramUpdate update = this.asyncProcessor.processHologramAsync(crate, display);

        if (update.hasUpdates()) {
            this.plugin.getFoliaScheduler().runNextTick(() -> {
                try {
                    update.applyToMainThread(this.handler);
                } catch (Exception e) {
                    this.plugin.error("Error applying hologram update for crate " + crate.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    public boolean hasHandler() {
        return this.handler != null;
    }

    @Nullable
    private FakeDisplay getDisplay(@NotNull Crate crate) {
        return this.displayMap.get(crate.getId());
    }

    public void disableBlockHologram(@NotNull Crate crate, @NotNull WorldPos blockPos) {
        this.toggleBlockHologram(crate, blockPos, false);
    }

    public void enableBlockHologram(@NotNull Crate crate, @NotNull WorldPos blockPos) {
        this.toggleBlockHologram(crate, blockPos, true);
    }

    private void toggleBlockHologram(@NotNull Crate crate, @NotNull WorldPos blockPos, boolean enabled) {
        FakeDisplay display = this.getDisplay(crate);
        if (display == null) return;

        FakeEntityGroup group = display.getGroup(blockPos);
        if (group == null) return;

        group.setDisabled(!enabled);

        if (group.isDisabled()) {
            this.discard(group);
        }
        else {
            this.render(crate);
        }
    }



    public void removeForViewer(@NotNull Player player) {
        this.displayMap.values().forEach(display -> this.removeForViewer(player, display));
    }

    public void removeForViewer(@NotNull Player player, @NotNull FakeDisplay display) {
        display.getGroups().forEach(group -> this.removeForViewer(player, group));
    }

    public void removeForViewer(@NotNull Player player, @NotNull FakeEntityGroup group) {
        group.removeViewer(player);
        this.handler.sendDestroyEntityPacket(player, group.getEntityIDs());
    }



    public void discard(@NotNull Crate crate) {
        FakeDisplay display = this.displayMap.remove(crate.getId());
        if (display == null) return;

        this.discard(display);
    }

    public void discard(@NotNull FakeDisplay display) {
        display.getGroups().forEach(this::discard);
    }

    public void discard(@NotNull FakeEntityGroup group) {
        group.clearViewers();
        this.handler.sendDestroyEntityPacket(group.getEntityIDs());
    }



    public void render(@NotNull Crate crate) {
        this.processHologramAsync(crate);
    }

    private void createIfAbsent(@NotNull Crate crate) {
        if (!this.hasHandler()) return;
        if (this.displayMap.containsKey(crate.getId())) return;

        List<String> originText = crate.getHologramText();
        if (originText.isEmpty()) return;

        FakeDisplay display = new FakeDisplay();

        double yOffset = crate.getHologramYOffset() + 0.2;
        double lineGap = Config.CRATE_HOLOGRAM_LINE_GAP.get();

        crate.getBlockPositions().forEach(blockPos -> {
            Location location = blockPos.toLocation();
            if (location == null) return;

            this.plugin.runAtLocation(location, () -> {
                Block block = blockPos.toBlock();
                if (block == null) return;

                double height = block.getBoundingBox().getHeight() / 2D + yOffset;

                // Allocate ID values for our fake entities, so there is no clash with new server entities.
                FakeEntityGroup group = display.getGroupOrCreate(blockPos);

                for (int index = 0; index < originText.size(); index++) {
                    double gap = lineGap * index;

                    Location hologramLocation = LocationUtil.setCenter3D(block.getLocation()).add(0, height + gap, 0);
                    group.addEntity(FakeEntity.create(hologramLocation));
                }
            });
        });

        this.displayMap.put(crate.getId(), display);
    }
}
