package com.wantchane.bfg.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;

import com.wantchane.bfg.network.OpenFactoryPanelPayload;

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
}
