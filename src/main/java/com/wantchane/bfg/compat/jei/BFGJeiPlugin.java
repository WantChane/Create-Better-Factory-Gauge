package com.wantchane.bfg.compat.jei;

import com.simibubi.create.compat.jei.GhostIngredientHandler;
import com.wantchane.bfg.CreateBetterFactoryGauge;
import com.wantchane.bfg.factory_panel.FactoryPanelScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class BFGJeiPlugin implements IModPlugin {

	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CreateBetterFactoryGauge.MODID, "jei_plugin");

	@Override
	@NotNull
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		IGhostIngredientHandler handler = new GhostIngredientHandler<>();
		registration.addGhostIngredientHandler(FactoryPanelScreen.class, handler);
	}
}
