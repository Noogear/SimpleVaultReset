package cn.simplevaultreset.adapter.impl;

import cn.simplevaultreset.adapter.VaultAccessor;
import org.bukkit.block.data.type.Vault;

import java.lang.invoke.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class VaultAccessorLegacy implements VaultAccessor {
    private static final Function<Vault, Vault.State> GET_STATE_FUNCTION;
    private static final BiConsumer<Vault, Vault.State> SET_STATE_FUNCTION;

    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            MethodHandle getStateHandle = lookup.findVirtual(Vault.class, "getTrialSpawnerState",
                    MethodType.methodType(Vault.State.class));
            MethodHandle setStateHandle = lookup.findVirtual(Vault.class, "setTrialSpawnerState",
                    MethodType.methodType(void.class, Vault.State.class));
            GET_STATE_FUNCTION = createGetter(lookup, getStateHandle);
            SET_STATE_FUNCTION = createSetter(lookup, setStateHandle);
        } catch (Throwable t) {
            throw new IllegalStateException("The server version is not compatible.", t);
        }
    }

    private static Function<Vault, Vault.State> createGetter(MethodHandles.Lookup lookup, MethodHandle handle) throws Throwable {
        CallSite site = LambdaMetafactory.metafactory(
                lookup, "apply",
                MethodType.methodType(Function.class),
                MethodType.methodType(Object.class, Object.class),
                handle,
                MethodType.methodType(Vault.State.class, Vault.class)
        );
        return (Function<Vault, Vault.State>) site.getTarget().invokeExact();
    }

    private static BiConsumer<Vault, Vault.State> createSetter(MethodHandles.Lookup lookup, MethodHandle handle) throws Throwable {
        CallSite site = LambdaMetafactory.metafactory(
                lookup, "accept",
                MethodType.methodType(BiConsumer.class),
                MethodType.methodType(void.class, Object.class, Object.class),
                handle,
                MethodType.methodType(void.class, Vault.class, Vault.State.class)
        );
        return (BiConsumer<Vault, Vault.State>) site.getTarget().invokeExact();
    }

    @Override
    public Vault.State getState(Vault vault) {
        return GET_STATE_FUNCTION.apply(vault);
    }

    @Override
    public void setState(Vault vault, Vault.State state) {
        SET_STATE_FUNCTION.accept(vault, state);
    }

}
