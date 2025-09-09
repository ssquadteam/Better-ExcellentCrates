package su.nightexpress.excellentcrates.opening;

import org.jetbrains.annotations.Nullable;

public interface AsyncProcessable {

    @Nullable
    AsyncOpeningUpdate processAsync();
}
