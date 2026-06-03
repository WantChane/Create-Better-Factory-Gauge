package com.wantchane.bfg.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
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
     * Sort PackageOrder items by gauge connection order when all connections share one network.
     * Mirrors PR #10391: connectionOrder + orderedItems.sort()
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

        UUID commonNetwork = null;
        boolean sameNetwork = true;
        for (FactoryPanelConnection conn : self.targetedBy.values()) {
            FactoryPanelBehaviour source = FactoryPanelBehaviour.at(self.getWorld(), conn);
            if (source == null)
                continue;
            if (commonNetwork == null) {
                commonNetwork = source.network;
            } else if (!commonNetwork.equals(source.network)) {
                sameNetwork = false;
                break;
            }
        }

        if (!sameNetwork || commonNetwork == null)
            return orderedItems;

        Map<ItemStack, Integer> orderMap = new HashMap<>();
        List<ItemStack> ghostGrid = bfg$getGhostGrid();
        for (int i = 0; i < ghostGrid.size(); i++) {
            ItemStack item = ghostGrid.get(i);
            if (!item.isEmpty()) {
                ItemStack key = item.copyWithCount(1);
                if (!orderMap.containsKey(key))
                    orderMap.put(key, i);
            }
        }

        if (orderMap.isEmpty())
            return orderedItems;

        List<BigItemStack> sorted = new ArrayList<>(orderedItems);
        sorted.sort(Comparator.comparingInt(
            bigStack -> orderMap.getOrDefault(bigStack.stack.copyWithCount(1), Integer.MAX_VALUE)
        ));
        return sorted;
    }

    /**
     * Inject ghost grid ordering into PackageOrderWithCrafts when not in crafting mode.
     * Packager uses orderedCrafts to arrange items within the package.
     */
    @ModifyArg(
        method = "tickRequests",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;<init>(Lcom/simibubi/create/content/logistics/stockTicker/PackageOrder;Ljava/util/List;)V"),
        index = 1
    )
    private List<PackageOrderWithCrafts.CraftingEntry> bfg$injectGhostGridOrdering(
        List<PackageOrderWithCrafts.CraftingEntry> orderedCrafts) {
        if (!orderedCrafts.isEmpty())
            return orderedCrafts;

        List<ItemStack> ghostGrid = bfg$getGhostGrid();
        boolean hasItems = false;
        for (ItemStack s : ghostGrid) {
            if (!s.isEmpty()) { hasItems = true; break; }
        }
        if (!hasItems)
            return orderedCrafts;

        List<BigItemStack> pattern = new ArrayList<>();
        for (ItemStack item : ghostGrid)
            pattern.add(new BigItemStack(item.copyWithCount(1)));
        return List.of(new PackageOrderWithCrafts.CraftingEntry(new PackageOrder(pattern), 1));
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

    /**
     * Persist ghost grid items (with counts) into NBT for network sync and disk save.
     */
    @Inject(method = "write", at = @At("TAIL"))
    private void bfg$writeGhostGrid(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        String key = CreateLang.asId(bfg$self().slot.name());
        CompoundTag panelTag = nbt.getCompound(key);
        if (!panelTag.isEmpty()) {
            panelTag.put("BFGGhostGrid", NBTHelper.writeItemList(new ArrayList<>(bfg$ghostGrid), registries));
            nbt.put(key, panelTag);
        }
    }

    /**
     * Persist ghost grid to disk (writeSafe path for world save).
     */
    @Inject(method = "writeSafe", at = @At("TAIL"))
    private void bfg$writeSafeGhostGrid(CompoundTag nbt, HolderLookup.Provider registries, CallbackInfo ci) {
        String key = CreateLang.asId(bfg$self().slot.name());
        CompoundTag panelTag = nbt.getCompound(key);
        if (!panelTag.isEmpty()) {
            panelTag.put("BFGGhostGrid", NBTHelper.writeItemList(new ArrayList<>(bfg$ghostGrid), registries));
            nbt.put(key, panelTag);
        }
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
