package cn.simplevaultreset;

import cn.simplevaultreset.adapter.VaultAccessor;
import cn.simplevaultreset.adapter.impl.VaultAccessorLegacy;
import cn.simplevaultreset.adapter.impl.VaultAccessorModern;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleVaultReset extends JavaPlugin implements Listener {
    private final Set<Location> PENDING_RESETS = ConcurrentHashMap.newKeySet();
    private final RegionScheduler regionScheduler = getServer().getRegionScheduler();
    private VaultAccessor vaultAccessor;
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
            for (Location location : PENDING_RESETS) {
                resetVault(location);
            }
        }
        PENDING_RESETS.clear();
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
        if (PENDING_RESETS.contains(location)) {
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

        PENDING_RESETS.add(location);
        regionScheduler.runDelayed(this, location, task -> resetVault(location), resetDelay);
    }

    private void resetVault(Location location) {
        try {
            final Block currentBlock = location.getBlock();
            if (currentBlock.getType() == Material.VAULT) {
                vaultAccessor.clearRewardedPlayers(currentBlock);
            }
        } finally {
            PENDING_RESETS.remove(location);
        }
    }

}
