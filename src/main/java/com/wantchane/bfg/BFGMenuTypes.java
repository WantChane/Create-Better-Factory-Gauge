package com.wantchane.bfg;

import com.wantchane.bfg.factory_panel.FactoryPanelMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class BFGMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, CreateBetterFactoryGauge.MODID);

    private static final AtomicReference<MenuType<FactoryPanelMenu>> MENU_TYPE_REF = new AtomicReference<>();

    public static final Supplier<MenuType<FactoryPanelMenu>> FACTORY_PANEL =
        MENU_TYPES.register("factory_panel", () -> {
            MenuType<FactoryPanelMenu> type = IMenuTypeExtension.create(
                (containerId, inv, buf) -> new FactoryPanelMenu(MENU_TYPE_REF.get(), containerId, inv, buf)
            );
            MENU_TYPE_REF.set(type);
            return type;
        });

    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}
