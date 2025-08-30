package su.nightexpress.excellentcrates.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for safely accessing block properties in both Folia and Paper environments.
 * Handles threading issues that can occur in Folia when accessing block data from the wrong thread context.
 */
public class FoliaBlockUtils {

    /**
     * Safely gets the block type, handling potential Folia threading issues.
     * 
     * @param block The block to get the type from
     * @return The block type, or AIR if there was an error accessing it
     */
    @NotNull
    public static Material getBlockTypeSafe(@Nullable Block block) {
        if (block == null) {
            return Material.AIR;
        }
        
        try {
            return block.getType();
        } catch (Exception e) {
            return Material.AIR;
        }
    }

    /**
     * Safely checks if a block is empty, handling potential Folia threading issues.
     * 
     * @param block The block to check
     * @return true if the block is empty (air), false otherwise or if there was an error
     */
    public static boolean isBlockEmptySafe(@Nullable Block block) {
        if (block == null) {
            return true;
        }
        
        try {
            return block.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Safely checks if a block type is interactable, handling potential Folia threading issues.
     * 
     * @param block The block to check
     * @return true if the block is interactable, false otherwise or if there was an error
     */
    public static boolean isBlockInteractableSafe(@Nullable Block block) {
        if (block == null) {
            return false;
        }
        
        try {
            return block.getType().isInteractable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Safely gets block information as a formatted string, handling potential Folia threading issues.
     * 
     * @param block The block to get information from
     * @return A string representation of the block, or "UNKNOWN" if there was an error
     */
    @NotNull
    public static String getBlockInfoSafe(@Nullable Block block) {
        if (block == null) {
            return "NULL";
        }
        
        try {
            return block.getType().name();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
