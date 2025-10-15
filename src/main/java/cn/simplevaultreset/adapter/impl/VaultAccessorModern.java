package cn.simplevaultreset.adapter.impl;

import cn.simplevaultreset.adapter.VaultAccessor;
import org.bukkit.block.data.type.Vault;
import org.jetbrains.annotations.NotNull;

public class VaultAccessorModern implements VaultAccessor {
    @Override
    public Vault.State getState(@NotNull Vault vault) {
        return vault.getVaultState();
    }

    @Override
    public void setState(@NotNull Vault vault, Vault.State state) {
        vault.setVaultState(state);
    }
}
