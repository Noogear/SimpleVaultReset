package cn.simplevaultreset;

import cn.simplevaultreset.adapter.VaultAccessor;
import cn.simplevaultreset.adapter.impl.VaultAccessorLegacy;
import cn.simplevaultreset.adapter.impl.VaultAccessorModern;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Vault;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SimpleVaultReset extends JavaPlugin implements Listener {
    private static final ConcurrentHashMap<Location, VaultResetRunnable> PENDING_RESETS = new ConcurrentHashMap<>();
    private static VaultAccessor vaultAccessor;
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

        MainCommand svrCommand = new MainCommand(this);
        Objects.requireNonNull(this.getCommand("simplevaultreset")).setExecutor(svrCommand);
        Objects.requireNonNull(this.getCommand("simplevaultreset")).setTabCompleter(svrCommand);

        getLogger().info("SimpleVaultReset has been enabled!");
    }

    public void loadConfigValues() {
        this.resetDelay = getConfig().getLong("reset-delay", 200L);
        this.cooldownMessage = getConfig().getString("message.cooldown", "<red>This vault is on cooldown.</red>");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (!PENDING_RESETS.isEmpty()) {
            for (VaultResetRunnable runnable : PENDING_RESETS.values()) {
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
        final ItemStack itemInHand = event.getItem();
        if (itemInHand == null || (itemInHand.getType() != Material.TRIAL_KEY && itemInHand.getType() != Material.OMINOUS_TRIAL_KEY)) {
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

        final VaultResetRunnable runnable = new VaultResetRunnable(location, vaultData);
        PENDING_RESETS.put(location, runnable);
        runnable.runTaskLater(this, resetDelay);
    }


    static class VaultResetRunnable extends BukkitRunnable {
        private final Location location;
        private final Vault vaultData;

        public VaultResetRunnable(final Location location, final Vault vaultData) {
            this.location = location;
            this.vaultData = vaultData;
        }

        @Override
        public void run() {
            try {
                reset();
            } finally {
                PENDING_RESETS.remove(location);
            }
        }

        public void reset() {
            final Block currentBlock = location.getBlock();
            if (currentBlock.getType() == Material.VAULT) {
                currentBlock.setType(Material.AIR, false);

                if (location.getNearbyPlayers(4).isEmpty()) {
                    vaultAccessor.setState(vaultData, Vault.State.INACTIVE);
                } else {
                    location.getWorld().playSound(location, Sound.BLOCK_VAULT_ACTIVATE, 1.0f, 1.0f);
                }
                currentBlock.setBlockData(vaultData, true);
            }
        }

    }
}
