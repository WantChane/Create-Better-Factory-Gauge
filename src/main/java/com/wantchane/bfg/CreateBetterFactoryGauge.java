package com.wantchane.bfg;

import com.mojang.logging.LogUtils;
import com.wantchane.bfg.factory_panel.FactoryPanelScreen;
import com.wantchane.bfg.network.OpenFactoryPanelPayload;
import com.wantchane.bfg.network.SyncCraftCountPayload;
import com.wantchane.bfg.network.SyncGhostGridPayload;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

import org.slf4j.Logger;

@Mod(CreateBetterFactoryGauge.MODID)
public class CreateBetterFactoryGauge {
    public static final String MODID = "create_better_factory_gauge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateBetterFactoryGauge(IEventBus modEventBus) {
        LOGGER.info("Create: Better Factory Gauge initializing");

        BFGMenuTypes.register(modEventBus);

        modEventBus.addListener(RegisterMenuScreensEvent.class, event ->
            event.register(BFGMenuTypes.FACTORY_PANEL.get(), FactoryPanelScreen::new)
        );

        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            PayloadRegistrar registrar = event.registrar("1");
            registerPlayToServer(registrar, OpenFactoryPanelPayload.TYPE, OpenFactoryPanelPayload.STREAM_CODEC, OpenFactoryPanelPayload::handle);
            registerPlayToServer(registrar, SyncGhostGridPayload.TYPE, SyncGhostGridPayload.STREAM_CODEC, SyncGhostGridPayload::handle);
            registerPlayToServer(registrar, SyncCraftCountPayload.TYPE, SyncCraftCountPayload.STREAM_CODEC, SyncCraftCountPayload::handle);
        });
    }

    private static <T extends CustomPacketPayload> void registerPlayToServer(
        PayloadRegistrar registrar, CustomPacketPayload.Type<T> type,
        StreamCodec<RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        registrar.playToServer(type, codec,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer sp)
                    handler.accept(payload, sp);
            })
        );
    }
}
