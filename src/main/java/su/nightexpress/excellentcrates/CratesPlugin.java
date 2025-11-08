package su.nightexpress.excellentcrates;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentcrates.api.addon.CratesAddon;
import su.nightexpress.excellentcrates.command.BaseCommands;
import su.nightexpress.excellentcrates.command.antidupe.AntiDupeCommands;
import su.nightexpress.excellentcrates.config.*;
import su.nightexpress.excellentcrates.crate.CrateManager;
import su.nightexpress.excellentcrates.data.DataHandler;
import su.nightexpress.excellentcrates.data.DataManager;
import su.nightexpress.excellentcrates.dialog.CrateDialogs;
import su.nightexpress.excellentcrates.editor.EditorManager;
import su.nightexpress.excellentcrates.hologram.HologramManager;
import su.nightexpress.excellentcrates.hooks.impl.PlaceholderHook;
import su.nightexpress.excellentcrates.key.KeyManager;
import su.nightexpress.excellentcrates.key.UuidAntiDupeManager;
import su.nightexpress.excellentcrates.opening.OpeningManager;
import su.nightexpress.excellentcrates.opening.ProviderRegistry;
import su.nightexpress.excellentcrates.registry.CratesRegistries;
import su.nightexpress.excellentcrates.user.UserManager;
import su.nightexpress.nightcore.NightPlugin;
import su.nightexpress.nightcore.config.PluginDetails;
import su.nightexpress.nightcore.util.Plugins;
import su.nightexpress.nightcore.util.Version;
import su.nightexpress.excellentcrates.sync.RedisSyncManager;
import su.nightexpress.nightcore.commands.command.NightCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class CratesPlugin extends NightPlugin {

    private final List<CratesAddon> addons = new ArrayList<>();

    private DataHandler dataHandler;
    private DataManager dataManager;
    private UserManager userManager;
    private HologramManager hologramManager;
    private OpeningManager  openingManager;
    private KeyManager      keyManager;
    private UuidAntiDupeManager uuidAntiDupeManager;
    private CrateManager    crateManager;
    private EditorManager   editorManager;
    private RedisSyncManager redisSyncManager;
    private CrateLogger     crateLogger;
    private CrateDialogs    dialogs;

    @Override
    @NotNull
    protected PluginDetails getDefaultDetails() {
        return PluginDetails.create("Crates", new String[]{"crates", "ecrates", "excellentcrates", "crate", "case", "cases"})
            .setConfigClass(Config.class)
            .setPermissionsClass(Perms.class);
    }

    @Override
    protected boolean disableCommandManager() {
        return true;
    }

    @Override
    protected void onStartup() {
        CratesAPI.load(this);
        Keys.load(this);
    }

    @Override
    protected void addRegistries() {
        this.registerLang(Lang.class);
    }

    @Override
    public void enable() {
        this.loadEngine();

        this.crateLogger = new CrateLogger(this);

        this.dataHandler = new DataHandler(this);
        this.dataHandler.setup();

        this.dataManager = new DataManager(this);
        this.dataManager.setup();

        this.userManager = new UserManager(this, this.dataHandler);
        this.userManager.setup();

        this.openingManager = new OpeningManager(this);
        this.openingManager.setup();

        this.keyManager = new KeyManager(this);
        this.keyManager.setup();

        this.uuidAntiDupeManager = new UuidAntiDupeManager(this);
        this.uuidAntiDupeManager.setup();

        this.crateManager = new CrateManager(this);
        this.crateManager.setup();

        this.editorManager = new EditorManager(this);
        this.editorManager.setup();

        this.redisSyncManager = new RedisSyncManager(this);
        this.redisSyncManager.setup();

        this.dataHandler.updateRewardLimits();

        if (Version.withDialogs()) {
            this.dialogs = new CrateDialogs(this);
            this.dialogs.setup();
        }

        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderHook.setup(this);
        }

        if (Config.HOLOGRAMS_ENABLED.get()) {
            this.hologramManager = new HologramManager(this);
            this.hologramManager.setup();
        }

        this.loadCommands();
    }

    @Override
    public void disable() {
        if (this.dialogs != null) this.dialogs.shutdown();
        if (this.editorManager != null) this.editorManager.shutdown();
        if (this.openingManager != null) this.openingManager.shutdown();
        if (this.uuidAntiDupeManager != null) this.uuidAntiDupeManager.shutdown();
        if (this.keyManager != null) this.keyManager.shutdown();
        if (this.crateManager != null) this.crateManager.shutdown();
        if (this.hologramManager != null) this.hologramManager.shutdown();
        if (this.userManager != null) this.userManager.shutdown();
        if (this.dataManager != null) this.dataManager.shutdown();
        if (this.dataHandler != null) this.dataHandler.shutdown();
        if (this.redisSyncManager != null) this.redisSyncManager.shutdown();

        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderHook.shutdown();
        }

        CratesRegistries.clear();
        ProviderRegistry.clear();
    }

    @Override
    protected void onShutdown() {
        super.onShutdown();
        Keys.clear();
        CratesAPI.clear();
    }

    private void loadEngine() {
        ProviderRegistry.load();
        CratesRegistries.load(this);
        this.proceedAddons(CratesAddon::onInit);
    }

    private void loadCommands() {
        this.rootCommand = NightCommand.forPlugin(this, root -> {
            new BaseCommands(this).load(root);
            AntiDupeCommands.load(this, root);
        });
    }

    public void registerAddon(@NotNull CratesAddon addon) {
        this.addons.add(addon);
    }

    private void proceedAddons(@NotNull Consumer<CratesAddon> action) {
        for (CratesAddon addon : this.addons) {
            try {
                action.accept(addon);
            } catch (Exception e) {
                this.warn("Addon error: " + e.getMessage());
            }
        }
    }

    public boolean hasHolograms() {
        return this.hologramManager != null && this.hologramManager.hasHandler();
    }

    @NotNull
    public CrateLogger getCrateLogger() {
        return this.crateLogger;
    }

    @NotNull
    public DataHandler getDataHandler() {
        return this.dataHandler;
    }

    @NotNull
    public DataManager getDataManager() {
        return this.dataManager;
    }

    @NotNull
    public UserManager getUserManager() {
        return this.userManager;
    }

    @NotNull
    public OpeningManager getOpeningManager() {
        return this.openingManager;
    }

    @NotNull
    public EditorManager getEditorManager() {
        return this.editorManager;
    }

    @NotNull
    public KeyManager getKeyManager() {
        return this.keyManager;
    }

    @NotNull
    public UuidAntiDupeManager getUuidAntiDupeManager() {
        return this.uuidAntiDupeManager;
    }

    @NotNull
    public CrateManager getCrateManager() {
        return this.crateManager;
    }

    @NotNull
    public Optional<RedisSyncManager> getRedisSyncManager() {
        return Optional.ofNullable(this.redisSyncManager);
    }

    @NotNull
    public Optional<HologramManager> getHologramManager() {
        return Optional.ofNullable(this.hologramManager);
    }
}
