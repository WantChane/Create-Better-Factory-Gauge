package com.wantchane.bfg;

import com.mojang.logging.LogUtils;
import com.wantchane.bfg.factory_panel.FactoryPanelScreen;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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
    }
}
