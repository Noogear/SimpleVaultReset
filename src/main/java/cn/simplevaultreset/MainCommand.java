package cn.simplevaultreset;

import cn.simplevaultreset.adapter.VaultAccessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.Vault;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final SimpleVaultReset plugin;
    private final VaultAccessor vaultAccessor;

    public MainCommand(SimpleVaultReset plugin, VaultAccessor vaultAccessor) {
        this.plugin = plugin;
        this.vaultAccessor = vaultAccessor;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "giveominous":
                return handleGiveOminous(sender);
            case "resetchunk":
                return handleResetChunk(sender);
            default:
                sendUsage(sender, label);
                break;
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("simplevaultreset.reload")) {
            return false;
        }
        plugin.reloadConfig();
        plugin.loadConfigValues();
        sender.sendMessage(Component.text("SimpleVaultReset configuration has been reloaded.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveOminous(CommandSender sender) {
        if (!sender.hasPermission("simplevaultreset.giveominous")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            return false;
        }

        try {
            ItemStack ominous = Bukkit.getItemFactory().createItemStack("minecraft:vault[minecraft:block_entity_data={id:\"minecraft:vault\",config:{loot_table:\"minecraft:chests/trial_chambers/reward_ominous\",key_item:{count:1,id:\"minecraft:ominous_trial_key\"}}},minecraft:block_state={ominous:\"true\"}]");
            player.getInventory().addItem(ominous);
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to create the Ominous Vault item. Please ensure you are using a compatible server version.", NamedTextColor.RED));
            return true;
        }
        return true;
    }

    private boolean handleResetChunk(CommandSender sender) {
        if (!sender.hasPermission("simplevaultreset.resetchunk")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            return false;
        }

        for (BlockState state : player.getChunk().getTileEntities(false)) {
            if (state instanceof Vault) {
                vaultAccessor.clearRewardedPlayers(state.getBlock());
            }
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage: /" + label + " <reload|giveominous|resetchunk>", NamedTextColor.YELLOW));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>();
            if (sender.hasPermission("simplevaultreset.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("simplevaultreset.giveominous")) {
                completions.add("giveominous");
            }
            if (sender.hasPermission("simplevaultreset.resetchunk")) {
                completions.add("resetchunk");
            }
            return completions.stream()
                    .filter(str -> str.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}