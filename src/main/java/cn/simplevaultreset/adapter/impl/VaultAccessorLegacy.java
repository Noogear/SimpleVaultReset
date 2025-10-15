package cn.simplevaultreset.adapter.impl;

import cn.simplevaultreset.adapter.VaultAccessor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Vault;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

public final class VaultAccessorLegacy implements VaultAccessor {

    private static final MethodHandle GET_STATE_HANDLE;
    private static final MethodHandle SET_STATE_HANDLE;

    private static final Class<?> NBT_TAG_COMPOUND_CLASS;
    private static final MethodHandle GET_TILE_ENTITY;
    private static final MethodHandle GET_REGISTRY;
    private static final MethodHandle SAVE_NBT;
    private static final MethodHandle LOAD_NBT;
    private static final MethodHandle NEW_BLOCK_POSITION;
    private static final MethodHandle GET_HANDLE;

    private static final MethodHandle NBT_GET_COMPOUND;
    private static final MethodHandle NBT_PUT;
    private static final MethodHandle NBT_CONTAINS_KEY;
    private static final MethodHandle NEW_NBT_TAG_LIST;

    private static final String SERVER_DATA = "server_data";
    private static final String REWARDED_PLAYERS = "rewarded_players";

    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            final String obcPackageName = Bukkit.getServer().getClass().getPackage().getName();
            final String nmsPackage = "net.minecraft.";

            final Class<?> craftWorldClass = Class.forName(obcPackageName + ".CraftWorld");
            final Class<?> serverLevelClass = Class.forName(nmsPackage + "server.level.ServerLevel");
            final Class<?> blockEntityClass = Class.forName(nmsPackage + "world.level.block.entity.BlockEntity");
            final Class<?> blockPosClass = Class.forName(nmsPackage + "core.BlockPos");
            final Class<?> iRegistryCustomClass = Class.forName(nmsPackage + "core.IRegistryCustom");
            final Class<?> holderLookupProviderClass = Class.forName(nmsPackage + "core.HolderLookup$Provider");
            final Class<?> nbtTagListClass = Class.forName(nmsPackage + "nbt.NBTTagList");
            final Class<?> nbtBaseClass = Class.forName(nmsPackage + "nbt.NBTBase");
            NBT_TAG_COMPOUND_CLASS = Class.forName(nmsPackage + "nbt.NBTTagCompound");

            GET_STATE_HANDLE = lookup.findVirtual(Vault.class, "getTrialSpawnerState", MethodType.methodType(Vault.State.class));
            SET_STATE_HANDLE = lookup.findVirtual(Vault.class, "setTrialSpawnerState", MethodType.methodType(void.class, Vault.State.class));

            GET_HANDLE = lookup.findVirtual(craftWorldClass, "getHandle", MethodType.methodType(serverLevelClass));
            NEW_BLOCK_POSITION = lookup.findConstructor(blockPosClass, MethodType.methodType(void.class, int.class, int.class, int.class));
            GET_TILE_ENTITY = findVirtualMethod(lookup, serverLevelClass, new String[]{"getBlockEntity", "c_"}, blockEntityClass, blockPosClass);
            GET_REGISTRY = findVirtualMethod(lookup, serverLevelClass, new String[]{"registryAccess", "K_"}, iRegistryCustomClass);
            SAVE_NBT = findVirtualMethod(lookup, blockEntityClass, new String[]{"saveWithoutMetadata", "d"}, NBT_TAG_COMPOUND_CLASS, holderLookupProviderClass);
            LOAD_NBT = findVirtualMethod(lookup, blockEntityClass, new String[]{"loadWithComponents", "c"}, void.class, NBT_TAG_COMPOUND_CLASS, holderLookupProviderClass);

            NBT_GET_COMPOUND = findVirtualMethod(lookup, NBT_TAG_COMPOUND_CLASS, new String[]{"getCompound", "getCompound"}, NBT_TAG_COMPOUND_CLASS, String.class);
            NBT_PUT = findVirtualMethod(lookup, NBT_TAG_COMPOUND_CLASS, new String[]{"put", "a"}, nbtBaseClass, String.class, nbtBaseClass);
            NBT_CONTAINS_KEY = findVirtualMethod(lookup, NBT_TAG_COMPOUND_CLASS, new String[]{"contains", "e"}, boolean.class, String.class);
            NEW_NBT_TAG_LIST = lookup.findConstructor(nbtTagListClass, MethodType.methodType(void.class));
        } catch (Throwable t) {
            throw new IllegalStateException("Server version is incompatible, plugin initialization failed.", t);
        }
    }

    private static void editNbt(Block block, Consumer<Object> editor) {
        try {
            Object nmsWorld = GET_HANDLE.invoke(block.getWorld());
            Object blockPos = NEW_BLOCK_POSITION.invoke(block.getX(), block.getY(), block.getZ());
            Object tileEntity = GET_TILE_ENTITY.invoke(nmsWorld, blockPos);
            if (tileEntity == null) {
                return;
            }
            Object registry = GET_REGISTRY.invoke(nmsWorld);
            Object rootNbt = SAVE_NBT.invoke(tileEntity, registry);

            editor.accept(rootNbt);

            LOAD_NBT.invoke(tileEntity, rootNbt, registry);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to edit block NBT data at " + block.getLocation(), t);
        }
    }

    private static MethodHandle findVirtualMethod(MethodHandles.Lookup lookup, Class<?> clazz, String[] names, Class<?> returnType, Class<?>... paramTypes) throws ReflectiveOperationException {
        for (String name : names) {
            try {
                return lookup.findVirtual(clazz, name, MethodType.methodType(returnType, paramTypes));
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("No method found in class " + clazz.getName() + ": " + String.join(", ", names));
    }

    @Override
    public Vault.State getState(Vault vault) {
        try {
            return (Vault.State) GET_STATE_HANDLE.invoke(vault);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to get Vault status", t);
        }
    }

    @Override
    public void setState(Vault vault, Vault.State state) {
        try {
            SET_STATE_HANDLE.invoke(vault, state);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to set Vault state", t);
        }
    }

    @Override
    public void clearRewardedPlayers(Block block) {
        editNbt(block, rootNbt -> {
            try {
                if ((boolean) NBT_CONTAINS_KEY.invoke(rootNbt, SERVER_DATA)) {
                    Object serverDataNbt = NBT_GET_COMPOUND.invoke(rootNbt, SERVER_DATA);
                    if ((boolean) NBT_CONTAINS_KEY.invoke(serverDataNbt, REWARDED_PLAYERS)) {
                        Object emptyList = NEW_NBT_TAG_LIST.invoke();
                        NBT_PUT.invoke(serverDataNbt, REWARDED_PLAYERS, emptyList);
                    }
                }
            } catch (Throwable t) {
                throw new RuntimeException("Failed to modify server_data NBT", t);
            }
        });
    }
}