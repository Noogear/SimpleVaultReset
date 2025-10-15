package cn.simplevaultreset.adapter.impl;

import cn.simplevaultreset.adapter.VaultAccessor;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Vault;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

public class VaultAccessorModern implements VaultAccessor {
    @Override
    public Vault.State getState(@NotNull Vault vault) {
        return vault.getVaultState();
    }

    @Override
    public void setState(@NotNull Vault vault, Vault.State state) {
        vault.setVaultState(state);
    }

    @Override
    public void clearRewardedPlayers(@NotNull Block block) {
        if (block.getState() instanceof org.bukkit.block.Vault vault) {
            Collection<UUID> uuids = vault.getRewardedPlayers();
            for (UUID uuid : uuids) {
                vault.removeRewardedPlayer(uuid);
            }
        }
    }
}
