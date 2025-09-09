package su.nightexpress.excellentcrates.opening;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.nightcore.bridge.wrap.NightSound;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AsyncOpeningUpdate {
    
    private final Map<Integer, ItemStack> inventoryUpdates;
    private final Map<String, NightSound> soundsToPlay;
    private final Player player;
    private final Inventory inventory;
    private boolean shouldCloseInventory;
    private boolean shouldOpenInventory;
    private Inventory inventoryToOpen;
    private boolean shouldStopOpening;
    private boolean shouldCompleteOpening;
    
    public AsyncOpeningUpdate(@NotNull Player player, @NotNull Inventory inventory) {
        this.player = player;
        this.inventory = inventory;
        this.inventoryUpdates = new ConcurrentHashMap<>();
        this.soundsToPlay = new ConcurrentHashMap<>();
        this.shouldCloseInventory = false;
        this.shouldOpenInventory = false;
        this.inventoryToOpen = null;
        this.shouldStopOpening = false;
        this.shouldCompleteOpening = false;
    }
    
    public void addInventoryUpdate(int slot, @Nullable ItemStack item) {
        if (item != null) {
            this.inventoryUpdates.put(slot, item);
        }
    }
    
    public void addSound(@NotNull String soundId, @NotNull NightSound sound) {
        this.soundsToPlay.put(soundId, sound);
    }
    
    public void setCloseInventory(boolean close) {
        this.shouldCloseInventory = close;
    }
    
    public void setOpenInventory(@Nullable Inventory inventory) {
        this.shouldOpenInventory = inventory != null;
        this.inventoryToOpen = inventory;
    }
    
    public void setStopOpening(boolean stop) {
        this.shouldStopOpening = stop;
    }
    
    public void setCompleteOpening(boolean complete) {
        this.shouldCompleteOpening = complete;
    }
    
    public boolean hasUpdates() {
        return !inventoryUpdates.isEmpty() || 
               !soundsToPlay.isEmpty() || 
               shouldCloseInventory || 
               shouldOpenInventory || 
               shouldStopOpening || 
               shouldCompleteOpening;
    }
    
    public void applyToMainThread() {
        for (Map.Entry<Integer, ItemStack> entry : inventoryUpdates.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
        
        for (NightSound sound : soundsToPlay.values()) {
            sound.play(player);
        }
        
        if (shouldOpenInventory && inventoryToOpen != null) {
            player.openInventory(inventoryToOpen);
        }
        
        if (shouldCloseInventory) {
            player.closeInventory();
        }
    }
    
    @NotNull
    public Map<Integer, ItemStack> getInventoryUpdates() {
        return new HashMap<>(inventoryUpdates);
    }
    
    @NotNull
    public Map<String, NightSound> getSoundsToPlay() {
        return new HashMap<>(soundsToPlay);
    }
    
    public boolean shouldCloseInventory() {
        return shouldCloseInventory;
    }
    
    public boolean shouldOpenInventory() {
        return shouldOpenInventory;
    }
    
    @Nullable
    public Inventory getInventoryToOpen() {
        return inventoryToOpen;
    }
    
    public boolean shouldStopOpening() {
        return shouldStopOpening;
    }
    
    public boolean shouldCompleteOpening() {
        return shouldCompleteOpening;
    }
}
