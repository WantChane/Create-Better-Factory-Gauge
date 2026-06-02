package com.wantchane.bfg;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CreateBetterFactoryGauge.MODID)
public class CreateBetterFactoryGauge {
    public static final String MODID = "create_better_factory_gauge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateBetterFactoryGauge(IEventBus modEventBus) {
        LOGGER.info("Create: Better Factory Gauge initializing");
    }
}
