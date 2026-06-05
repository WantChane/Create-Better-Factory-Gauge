package com.wantchane.bfg.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.wantchane.bfg.CreateBetterFactoryGauge;
import com.wantchane.bfg.factory_panel.GhostGridAccessor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record SyncCraftCountPayload(FactoryPanelPosition position, int craftCount) implements CustomPacketPayload {

	public static final Type<SyncCraftCountPayload> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(CreateBetterFactoryGauge.MODID, "sync_craft_count")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, SyncCraftCountPayload> STREAM_CODEC = StreamCodec.composite(
		FactoryPanelPosition.STREAM_CODEC,
		SyncCraftCountPayload::position,
		ByteBufCodecs.VAR_INT,
		SyncCraftCountPayload::craftCount,
		SyncCraftCountPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(ServerPlayer player) {
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), position);
		if (behaviour != null) {
			((GhostGridAccessor) behaviour).bfg$setRecipeCraftCount(craftCount);
			behaviour.blockEntity.sendData();
			behaviour.blockEntity.setChanged();
		}
	}
}
