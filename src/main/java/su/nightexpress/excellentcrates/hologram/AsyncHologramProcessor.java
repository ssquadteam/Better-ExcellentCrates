package su.nightexpress.excellentcrates.hologram;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.hologram.entity.FakeDisplay;
import su.nightexpress.excellentcrates.hologram.entity.FakeEntity;
import su.nightexpress.excellentcrates.hologram.entity.FakeEntityGroup;
import su.nightexpress.excellentcrates.util.CrateUtils;
import su.nightexpress.excellentcrates.util.pos.WorldPos;
import su.nightexpress.nightcore.util.placeholder.Replacer;

import java.util.ArrayList;
import java.util.List;

public class AsyncHologramProcessor {

    public AsyncHologramProcessor() {
    }
    
    @NotNull
    public AsyncHologramUpdate processHologramAsync(@NotNull Crate crate, @NotNull FakeDisplay display) {
        AsyncHologramUpdate update = new AsyncHologramUpdate();

        List<String> baseText = Replacer.create().replace(crate.replacePlaceholders()).apply(crate.getHologramText().reversed());
        if (baseText.isEmpty()) return update;

        for (FakeEntityGroup group : display.getGroups()) {
            if (group.isDisabled()) continue;

            this.processGroupAsync(group, baseText, update);
        }

        return update;
    }
    
    private void processGroupAsync(@NotNull FakeEntityGroup group, @NotNull List<String> baseText,
                                  @NotNull AsyncHologramUpdate update) {
        WorldPos blockPosition = group.getBlockPosition();
        
        if (!this.isValidLocation(blockPosition)) {
            this.scheduleGroupDestroy(group, update);
            return;
        }
        
        World world = blockPosition.getWorld();
        Location location = blockPosition.toLocation();
        if (world == null || location == null) return;
        
        List<Player> nearbyPlayers = this.getNearbyPlayers(world, location);
        List<Player> currentViewers = this.getCurrentViewers(group, world);

        for (Player viewer : currentViewers) {
            if (!nearbyPlayers.contains(viewer)) {
                update.removeViewer(group, viewer);
                update.addDestroyPacket(viewer, group.getEntityIDs());
            }
        }
        
        if (nearbyPlayers.isEmpty()) {
            this.scheduleGroupDestroy(group, update);
            return;
        }
        
        for (Player player : nearbyPlayers) {
            boolean needSpawn = !group.isViewer(player);

            List<String> playerText = this.processPlayerText(baseText, player);
            this.scheduleHologramPackets(group, player, playerText, needSpawn, update);

            if (needSpawn) {
                update.addViewer(group, player);
            }
        }
    }
    
    private boolean isValidLocation(@NotNull WorldPos blockPosition) {
        try {
            return blockPosition.isChunkLoaded();
        } catch (Exception e) {
            return false;
        }
    }
    
    @NotNull
    private List<Player> getCurrentViewers(@NotNull FakeEntityGroup group, @NotNull World world) {
        List<Player> viewers = new ArrayList<>();

        try {
            for (Player player : world.getPlayers()) {
                if (player.isOnline() && group.isViewer(player)) {
                    viewers.add(player);
                }
            }
        } catch (Exception e) {
            // Handle any concurrent modification or world access issues
        }

        return viewers;
    }

    @NotNull
    private List<Player> getNearbyPlayers(@NotNull World world, @NotNull Location location) {
        List<Player> players = new ArrayList<>();

        try {
            for (Player player : world.getPlayers()) {
                if (player.isOnline() && CrateUtils.isInEffectRange(player, location)) {
                    players.add(player);
                }
            }
        } catch (Exception e) {
            // Handle any concurrent modification or world access issues
        }

        return players;
    }
    
    @NotNull
    private List<String> processPlayerText(@NotNull List<String> baseText, @NotNull Player player) {
        try {
            return Replacer.create().replacePlaceholderAPI(player).apply(baseText);
        } catch (Exception e) {
            return baseText;
        }
    }
    
    private void scheduleHologramPackets(@NotNull FakeEntityGroup group, @NotNull Player player, 
                                       @NotNull List<String> hologramText, boolean needSpawn, 
                                       @NotNull AsyncHologramUpdate update) {
        List<FakeEntity> holograms = group.getEntities();
        
        for (int index = 0; index < hologramText.size() && index < holograms.size(); index++) {
            String line = hologramText.get(index);
            FakeEntity entity = holograms.get(index);
            update.addHologramPacket(player, entity, needSpawn, line);
        }
    }
    
    private void scheduleGroupDestroy(@NotNull FakeEntityGroup group, @NotNull AsyncHologramUpdate update) {
        WorldPos blockPosition = group.getBlockPosition();
        World world = blockPosition.getWorld();
        if (world == null) return;

        List<Player> currentViewers = this.getCurrentViewers(group, world);
        for (Player viewer : currentViewers) {
            update.removeViewer(group, viewer);
            update.addDestroyPacket(viewer, group.getEntityIDs());
        }
    }
    

}
