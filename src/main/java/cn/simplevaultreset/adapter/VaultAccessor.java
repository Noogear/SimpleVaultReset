package cn.simplevaultreset.adapter;

import org.bukkit.block.Block;
import org.bukkit.block.data.type.Vault;

public interface VaultAccessor {

    Vault.State getState(Vault vault);

    void setState(Vault vault, Vault.State state);

    void clearRewardedPlayers(Block block);
}
