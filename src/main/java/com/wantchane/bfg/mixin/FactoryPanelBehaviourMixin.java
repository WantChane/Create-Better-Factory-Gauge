package com.wantchane.bfg.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.utility.CreateLang;

import com.wantchane.bfg.factory_panel.GhostGridAccessor;
import com.wantchane.bfg.network.OpenFactoryPanelPayload;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bfg$initGhostGrid(CallbackInfo ci) {
        for (int i = 0; i < 9; i++)
            bfg$ghostGrid.add(ItemStack.EMPTY);
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

    // === ValueSettingsBoard range expansion (100 → 1000 actual, 200 board) ===

    @Unique
    private static int bfg$boardToActual(int board) {
        if (board <= 100) return board;
        if (board <= 150) return 100 + (board - 100) * 6;
        return 400 + (board - 150) * 12;
    }

    @Unique
    private static int bfg$actualToBoard(int actual) {
        if (actual <= 100) return actual;
        if (actual <= 400) return 100 + (actual - 100) / 6;
        return 150 + (actual - 400) / 12;
    }

    /**
     * Replace the board: maxValue 100→200 (actual 1000), milestoneInterval 10→100,
     * wrap the formatter so displayed values show actual counts.
     */
    @Inject(method = "createBoard", at = @At("RETURN"), cancellable = true)
    private void bfg$modifyCreateBoard(Player player, BlockHitResult hitResult, CallbackInfoReturnable<ValueSettingsBoard> cir) {
        ValueSettingsBoard original = cir.getReturnValue();
        cir.setReturnValue(new ValueSettingsBoard(
            original.title(),
            200,
            100,
            original.rows(),
            new ValueSettingsFormatter(v -> bfg$self().formatValue(
                new ValueSettingsBehaviour.ValueSettings(v.row(), bfg$boardToActual(v.value()))))
        ));
    }

    /**
     * Convert board-space value from UI into actual count before storing.
     * Redirects {@code settings.value()} call in {@code setValueSettings}.
     */
    @Redirect(
        method = "setValueSettings",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/ValueSettingsBehaviour$ValueSettings;value()I")
    )
    private int bfg$redirectSettingsValue(ValueSettingsBehaviour.ValueSettings settings) {
        return bfg$boardToActual(settings.value());
    }

    /**
     * Convert actual count back to board-space so UI cursor matches stored value.
     */
    @Inject(method = "getValueSettings", at = @At("RETURN"), cancellable = true)
    private void bfg$getValueConvertActualToBoard(CallbackInfoReturnable<ValueSettingsBehaviour.ValueSettings> cir) {
        ValueSettingsBehaviour.ValueSettings original = cir.getReturnValue();
        cir.setReturnValue(new ValueSettingsBehaviour.ValueSettings(original.row(), bfg$actualToBoard(original.value())));
    }
}
