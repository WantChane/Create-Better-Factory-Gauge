package com.wantchane.bfg.mixin;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;

import com.wantchane.bfg.factory_panel.FactoryPanelMenu;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourMixin {

    @Unique
    private FactoryPanelBehaviour bfg$self() {
        return (FactoryPanelBehaviour) (Object) this;
    }

    /**
     * Replace HashMap with LinkedHashMap in constructor to preserve gauge connection insertion order.
     * Mirrors PR #10391: targetedBy = new LinkedHashMap<>()
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bfg$useLinkedHashMapInInit(CallbackInfo ci) {
        Map<FactoryPanelPosition, FactoryPanelConnection> original = bfg$self().targetedBy;
        Map<FactoryPanelPosition, FactoryPanelConnection> ordered = new LinkedHashMap<>();
        ordered.putAll(original);
        bfg$self().targetedBy = ordered;
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
        int index = 0;
        for (FactoryPanelConnection conn : self.targetedBy.values()) {
            FactoryPanelBehaviour source = FactoryPanelBehaviour.at(self.getWorld(), conn);
            if (source != null) {
                ItemStack item = source.getFilter();
                if (!item.isEmpty() && !orderMap.containsKey(item)) {
                    orderMap.put(item, index++);
                }
            }
        }

        if (orderMap.isEmpty())
            return orderedItems;

        List<BigItemStack> sorted = new ArrayList<>(orderedItems);
        sorted.sort(Comparator.comparingInt(
            bigStack -> orderMap.getOrDefault(bigStack.stack, Integer.MAX_VALUE)
        ));
        return sorted;
    }

    /**
     * Cancel the client-side direct screen opening. Replaced by menu-based container opening.
     */
    @Inject(method = "displayScreen", at = @At("HEAD"), cancellable = true)
    private void bfg$cancelDirectScreen(CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Open the factory panel menu on the server side when onShortInteract falls through
     * to the screen-opening path (no wrench, no link item).
     */
    @Inject(method = "onShortInteract", at = @At("TAIL"))
    private void bfg$openPanelMenuOnServer(Player player, InteractionHand hand, Direction side,
                                           BlockHitResult hit, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        FactoryPanelBehaviour self = bfg$self();

        if (!Create.LOGISTICS.mayInteract(self.network, player)) return;

        if (self.targetedBy.size() + self.targetedByLinks.size() > 0
            && player.getItemInHand(hand).is(Tags.Items.TOOLS_WRENCH)) return;

        if (player.getItemInHand(hand).getItem() instanceof LogisticallyLinkedBlockItem) return;

        player.openMenu(
            new SimpleMenuProvider(
                (containerId, inv, p) -> FactoryPanelMenu.create(containerId, inv, self),
                self.getDisplayName()
            ),
            buf -> FactoryPanelPosition.STREAM_CODEC.encode(buf, self.getPanelPosition())
        );
    }
}
