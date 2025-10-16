package cn.simplevaultreset;

import cn.simplevaultreset.adapter.VaultAccessor;
import cn.simplevaultreset.adapter.impl.VaultAccessorLegacy;
import cn.simplevaultreset.adapter.impl.VaultAccessorModern;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Vault;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SimpleVaultReset extends JavaPlugin implements Listener {
    private static final ConcurrentHashMap<Location, VaultResetTask> PENDING_RESETS = new ConcurrentHashMap<>();
    private static VaultAccessor vaultAccessor;
    private final RegionScheduler regionScheduler = getServer().getRegionScheduler();
    private long resetDelay;
    private String cooldownMessage;

    @Override
    public void onEnable() {
        try {
            Vault.class.getMethod("getVaultState");
            vaultAccessor = new VaultAccessorModern();
        } catch (NoSuchMethodException e) {
            vaultAccessor = new VaultAccessorLegacy();
        } catch (Exception e) {
            if (vaultAccessor == null) {
                getLogger().severe("Plugin initialization failed!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);

        MainCommand svrCommand = new MainCommand(this, vaultAccessor);
        Objects.requireNonNull(this.getCommand("simplevaultreset")).setExecutor(svrCommand);
        Objects.requireNonNull(this.getCommand("simplevaultreset")).setTabCompleter(svrCommand);

        getLogger().info("SimpleVaultReset has been enabled!");
    }

    public void loadConfigValues() {
        long resetDelay = getConfig().getLong("reset-delay", 200L);
        if (resetDelay <= 0L) {
            this.resetDelay = 200L;
            getLogger().warning("Invalid reset delay in configuration. The delay must be a positive number. Using default value of 200 ticks.");
        } else {
            this.resetDelay = resetDelay;
        }
        this.cooldownMessage = getConfig().getString("message.cooldown", "<red>This vault is on cooldown.</red>");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getServer().getGlobalRegionScheduler().cancelTasks(this);
        getServer().getAsyncScheduler().cancelTasks(this);
        if (!PENDING_RESETS.isEmpty()) {
            for (VaultResetTask runnable : PENDING_RESETS.values()) {
                runnable.reset();
            }
        }
        getLogger().info("SimpleVaultReset has been disabled!");
    }

    @EventHandler(ignoreCancelled = true)
    public void onVaultInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.VAULT) {
            return;
        }
        if (!event.hasItem()) {
            return;
        }

        final Location location = clickedBlock.getLocation();
        if (PENDING_RESETS.containsKey(location)) {
            event.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(cooldownMessage));
            return;
        }

        final Vault vaultData = (Vault) clickedBlock.getBlockData();
        if (vaultAccessor.getState(vaultData) != Vault.State.ACTIVE) {
            return;
        }

        if (vaultData.isOminous()) {
            if (event.getMaterial() != Material.OMINOUS_TRIAL_KEY) {
                return;
            }
        } else {
            if (event.getMaterial() != Material.TRIAL_KEY) {
                return;
            }
        }

        final VaultResetTask task = new VaultResetTask(location);
        PENDING_RESETS.put(location, task);
        regionScheduler.runDelayed(this, location, task, resetDelay);
    }

    static class VaultResetTask implements Consumer<ScheduledTask> {
        private final Location location;

        public VaultResetTask(final Location location) {
            this.location = location;
        }

        public void reset() {
            final Block currentBlock = location.getBlock();
            if (currentBlock.getType() == Material.VAULT) {
                vaultAccessor.clearRewardedPlayers(currentBlock);
                if (location.getNearbyPlayers(4).isEmpty()) {
                    vaultAccessor.setState((Vault) currentBlock.getBlockData(), Vault.State.INACTIVE);
                }
            }
        }

        @Override
        public void accept(ScheduledTask scheduledTask) {
            try {
                reset();
            } finally {
                PENDING_RESETS.remove(location);
            }
        }
    }
}
