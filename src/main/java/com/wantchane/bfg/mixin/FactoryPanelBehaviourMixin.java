package com.wantchane.bfg.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.utility.CreateLang;

import com.wantchane.bfg.factory_panel.GhostGridAccessor;
import com.wantchane.bfg.network.OpenFactoryPanelPayload;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourMixin implements GhostGridAccessor {

    @Unique
    private List<ItemStack> bfg$ghostGrid = new ArrayList<>();

    @Unique
    private FactoryPanelBehaviour bfg$self() {
        return (FactoryPanelBehaviour) (Object) this;
    }

    @Unique
    public List<ItemStack> bfg$getGhostGrid() {
        return bfg$ghostGrid;
    }

    @Unique
    public void bfg$setGhostGrid(List<ItemStack> grid) {
        bfg$ghostGrid = new ArrayList<>(grid);
    }

    @Unique
    private List<ItemStack> bfg$craftingBackup = new ArrayList<>();

    @Unique
    public List<ItemStack> bfg$getCraftingBackup() {
        return bfg$craftingBackup;
    }

    @Unique
    public void bfg$setCraftingBackup(List<ItemStack> grid) {
        bfg$craftingBackup = new ArrayList<>(grid);
    }

    /**
     * Replace HashMap with LinkedHashMap in constructor to preserve gauge connection insertion order.
     * Also initialize ghost grid with 9 empty slots.
     * Mirrors PR #10391: targetedBy = new LinkedHashMap<>()
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bfg$useLinkedHashMapInInit(CallbackInfo ci) {
        Map<FactoryPanelPosition, FactoryPanelConnection> original = bfg$self().targetedBy;
        Map<FactoryPanelPosition, FactoryPanelConnection> ordered = new LinkedHashMap<>();
        ordered.putAll(original);
        bfg$self().targetedBy = ordered;

        for (int i = 0; i < 9; i++)
            bfg$ghostGrid.add(ItemStack.EMPTY);
    }

    /**
     * Replace HashMap with LinkedHashMap in disable() to preserve insertion order on reset.
     * Mirrors PR #10391: targetedBy = new LinkedHashMap<>()
     */
    @Inject(method = "disable", at = @At("TAIL"))
    private void bfg$useLinkedHashMapInDisable(CallbackInfo ci) {
        bfg$self().targetedBy = new LinkedHashMap<>();
    }

    /**
     * Replace aggregated connection items with per-slot ghost grid items in PackageOrder.
     * Follows Redstone Requester pattern: each ghost slot = one BigItemStack with its count.
     * Uses two-arg BigItemStack constructor — the one-arg constructor hardcodes count=1.
     */
    @ModifyArg(
        method = "tickRequests",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/stockTicker/PackageOrder;<init>(Ljava/util/List;)V"),
        index = 0
    )
    private List<BigItemStack> bfg$sortByConnectionOrder(List<BigItemStack> orderedItems) {
        FactoryPanelBehaviour self = bfg$self();

        if (self.targetedBy.isEmpty())
            return orderedItems;

        List<ItemStack> ghostGrid = bfg$getGhostGrid();
        List<BigItemStack> result = new ArrayList<>();
        for (ItemStack item : ghostGrid) {
            if (!item.isEmpty())
                result.add(new BigItemStack(item, item.getCount()));
        }

        if (!result.isEmpty())
            return result;

        return orderedItems;
    }

    /**
     * Cancel the client-side direct screen opening. Instead, send a network payload
     * to trigger server-side menu opening. This runs from onShortInteract's
     * {@code displayScreen} call, which only fires after all checks pass
     * (including the connection-mode early return).
     */
    @Inject(method = "displayScreen", at = @At("HEAD"), cancellable = true)
    private void bfg$cancelDirectScreen(Player player, CallbackInfo ci) {
        ci.cancel();
        if (player.level().isClientSide()) {
            PacketDistributor.sendToServer(new OpenFactoryPanelPayload(bfg$self().getPanelPosition()));
        }
    }

    @Unique
    private void bfg$writeGhostGridToNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        String key = CreateLang.asId(bfg$self().slot.name());
        CompoundTag panelTag = nbt.getCompound(key);
        if (!panelTag.isEmpty()) {
            panelTag.put("BFGGhostGrid", NBTHelper.writeItemList(new ArrayList<>(bfg$ghostGrid), registries));
            nbt.put(key, panelTag);
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void bfg$writeGhostGrid(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        bfg$writeGhostGridToNbt(nbt, registries);
    }

    @Inject(method = "writeSafe", at = @At("TAIL"))
    private void bfg$writeSafeGhostGrid(CompoundTag nbt, HolderLookup.Provider registries, CallbackInfo ci) {
        bfg$writeGhostGridToNbt(nbt, registries);
    }

    /**
     * Restore ghost grid items from NBT.
     */
    @Inject(method = "read", at = @At("TAIL"))
    private void bfg$readGhostGrid(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        String key = CreateLang.asId(bfg$self().slot.name());
        CompoundTag panelTag = nbt.getCompound(key);
        if (panelTag.contains("BFGGhostGrid")) {
            bfg$ghostGrid = NBTHelper.readItemList(panelTag.getList("BFGGhostGrid", Tag.TAG_COMPOUND), registries);
            while (bfg$ghostGrid.size() < 9)
                bfg$ghostGrid.add(ItemStack.EMPTY);
        }
    }
}
