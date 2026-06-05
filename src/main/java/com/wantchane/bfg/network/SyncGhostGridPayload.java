package com.wantchane.bfg.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.wantchane.bfg.CreateBetterFactoryGauge;
import com.wantchane.bfg.factory_panel.GhostGridAccessor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record SyncGhostGridPayload(FactoryPanelPosition position, List<ItemStack> ghostGrid) implements CustomPacketPayload {

	public static final Type<SyncGhostGridPayload> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(CreateBetterFactoryGauge.MODID, "sync_ghost_grid")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, SyncGhostGridPayload> STREAM_CODEC = StreamCodec.composite(
		FactoryPanelPosition.STREAM_CODEC,
		SyncGhostGridPayload::position,
		ItemStack.OPTIONAL_LIST_STREAM_CODEC,
		SyncGhostGridPayload::ghostGrid,
		SyncGhostGridPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(ServerPlayer player) {
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), position);
		if (behaviour != null) {
			((GhostGridAccessor) behaviour).bfg$setGhostGrid(ghostGrid);
			behaviour.blockEntity.sendData();
			behaviour.blockEntity.setChanged();
		}
	}
}
