package su.nightexpress.excellentcrates.hologram;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.hologram.entity.FakeEntity;
import su.nightexpress.excellentcrates.hologram.entity.FakeEntityGroup;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AsyncHologramUpdate {
    
    private final Map<Player, Map<FakeEntity, HologramPacketData>> packetsToSend;
    private final Map<Player, Set<Integer>> destroyPackets;
    private final Map<FakeEntityGroup, Set<Player>> viewersToAdd;
    private final Map<FakeEntityGroup, Set<Player>> viewersToRemove;
    
    public AsyncHologramUpdate() {
        this.packetsToSend = new ConcurrentHashMap<>();
        this.destroyPackets = new ConcurrentHashMap<>();
        this.viewersToAdd = new ConcurrentHashMap<>();
        this.viewersToRemove = new ConcurrentHashMap<>();
    }
    
    public void addHologramPacket(@NotNull Player player, @NotNull FakeEntity entity, boolean needSpawn, @NotNull String text) {
        this.packetsToSend.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
            .put(entity, new HologramPacketData(needSpawn, text));
    }
    
    public void addDestroyPacket(@NotNull Player player, @NotNull Set<Integer> entityIds) {
        this.destroyPackets.put(player, entityIds);
    }
    
    public void addViewer(@NotNull FakeEntityGroup group, @NotNull Player player) {
        this.viewersToAdd.computeIfAbsent(group, k -> ConcurrentHashMap.newKeySet()).add(player);
    }
    
    public void removeViewer(@NotNull FakeEntityGroup group, @NotNull Player player) {
        this.viewersToRemove.computeIfAbsent(group, k -> ConcurrentHashMap.newKeySet()).add(player);
    }
    
    public boolean hasUpdates() {
        return !packetsToSend.isEmpty() || !destroyPackets.isEmpty() || 
               !viewersToAdd.isEmpty() || !viewersToRemove.isEmpty();
    }
    
    public void applyToMainThread(@NotNull HologramHandler handler) {
        for (Map.Entry<Player, Map<FakeEntity, HologramPacketData>> entry : packetsToSend.entrySet()) {
            Player player = entry.getKey();
            if (!player.isOnline()) continue;
            
            for (Map.Entry<FakeEntity, HologramPacketData> packetEntry : entry.getValue().entrySet()) {
                FakeEntity entity = packetEntry.getKey();
                HologramPacketData data = packetEntry.getValue();
                handler.sendHologramPackets(player, entity, data.needSpawn(), data.text());
            }
        }
        
        for (Map.Entry<Player, Set<Integer>> entry : destroyPackets.entrySet()) {
            Player player = entry.getKey();
            if (!player.isOnline()) continue;
            
            handler.sendDestroyEntityPacket(player, entry.getValue());
        }
        
        for (Map.Entry<FakeEntityGroup, Set<Player>> entry : viewersToAdd.entrySet()) {
            FakeEntityGroup group = entry.getKey();
            for (Player player : entry.getValue()) {
                if (player.isOnline()) {
                    group.addViewer(player);
                }
            }
        }
        
        for (Map.Entry<FakeEntityGroup, Set<Player>> entry : viewersToRemove.entrySet()) {
            FakeEntityGroup group = entry.getKey();
            for (Player player : entry.getValue()) {
                group.removeViewer(player);
            }
        }
    }
    
    @NotNull
    public Map<Player, Map<FakeEntity, HologramPacketData>> getPacketsToSend() {
        return new HashMap<>(packetsToSend);
    }
    
    public record HologramPacketData(boolean needSpawn, @NotNull String text) {}
}
