package com.wantchane.bfg.network;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.wantchane.bfg.CreateBetterFactoryGauge;
import com.wantchane.bfg.factory_panel.FactoryPanelMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public record OpenFactoryPanelPayload(FactoryPanelPosition position) implements CustomPacketPayload {

	public static final Type<OpenFactoryPanelPayload> TYPE = new Type<>(
		ResourceLocation.fromNamespaceAndPath(CreateBetterFactoryGauge.MODID, "open_factory_panel")
	);

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenFactoryPanelPayload> STREAM_CODEC = StreamCodec.composite(
		FactoryPanelPosition.STREAM_CODEC,
		OpenFactoryPanelPayload::position,
		OpenFactoryPanelPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(ServerPlayer player) {
		FactoryPanelBehaviour behaviour = FactoryPanelBehaviour.at(player.level(), position);
		if (behaviour != null) {
			player.openMenu(
				new SimpleMenuProvider(
					(containerId, inv, p) -> FactoryPanelMenu.create(containerId, inv, behaviour),
					behaviour.getDisplayName()
				),
				buf -> FactoryPanelPosition.STREAM_CODEC.encode(buf, behaviour.getPanelPosition())
			);
		}
	}
}
