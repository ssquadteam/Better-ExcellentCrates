package su.nightexpress.excellentcrates.opening.inventory.spinner;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.opening.AsyncOpeningUpdate;

public interface AsyncSpinnerProcessable {
    
    void processAsync(@NotNull AsyncOpeningUpdate update);
}
