package com.wantchane.bfg.factory_panel;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.gui.menu.MenuBase;
import com.wantchane.bfg.BFGMenuTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class FactoryPanelMenu extends MenuBase<FactoryPanelBehaviour> {

    public FactoryPanelMenu(MenuType<?> type, int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        super(type, containerId, inv, buf);
    }

    public FactoryPanelMenu(MenuType<?> type, int containerId, Inventory inv, FactoryPanelBehaviour behaviour) {
        super(type, containerId, inv, behaviour);
    }

    public static FactoryPanelMenu create(int containerId, Inventory inv, FactoryPanelBehaviour behaviour) {
        return new FactoryPanelMenu(BFGMenuTypes.FACTORY_PANEL.get(), containerId, inv, behaviour);
    }

    @Override
    protected FactoryPanelBehaviour createOnClient(RegistryFriendlyByteBuf buf) {
        FactoryPanelPosition pos = FactoryPanelPosition.STREAM_CODEC.decode(buf);
        ClientLevel level = Minecraft.getInstance().level;
        return FactoryPanelBehaviour.at(level, pos);
    }

    @Override
    protected void initAndReadInventory(FactoryPanelBehaviour behaviour) {
    }

    @Override
    protected void addSlots() {
        boolean restocker = contentHolder.panelBE().restocker;
        int baseHeight = restocker ? 104 : 160;
        // X+8, Y+18: texture internal padding — first slot visual in PLAYER_INVENTORY
        // +4: gap between gauge bottom and player inventory
        addPlayerSlots(16, baseHeight + 18 + 4);
    }


    @Override
    protected void saveData(FactoryPanelBehaviour behaviour) {
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
