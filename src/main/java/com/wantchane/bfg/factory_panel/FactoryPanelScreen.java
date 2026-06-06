package com.wantchane.bfg.factory_panel;

import static com.simibubi.create.foundation.gui.AllGuiTextures.FACTORY_GAUGE_BOTTOM;
import static com.simibubi.create.foundation.gui.AllGuiTextures.FACTORY_GAUGE_RECIPE;
import static com.simibubi.create.foundation.gui.AllGuiTextures.FACTORY_GAUGE_RESTOCK;
import static com.simibubi.create.foundation.gui.AllGuiTextures.FROGPORT_SLOT;
import static com.simibubi.create.foundation.gui.AllGuiTextures.PLAYER_INVENTORY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;

import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConfigurationPacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnectionHandler;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.wantchane.bfg.compat.CALCompatHelper;
import com.wantchane.bfg.network.SyncCraftCountPayload;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

public class FactoryPanelScreen extends AbstractSimiContainerScreen<FactoryPanelMenu> {

	private static final ResourceLocation CAL_PROMISE_LIMIT_TEX = ResourceLocation.fromNamespaceAndPath(
		"createadditionallogistics", "textures/gui/promise_limit.png");

	// Widgets

	private AddressEditBox addressBox;
	private IconButton confirmButton;
	private IconButton deleteButton;
	private IconButton newInputButton;
	private IconButton relocateButton;
	private IconButton activateCraftingButton;
	private ScrollInput promiseExpiration;
	private ScrollInput calPromiseLimit;
	private ScrollInput calAdditionalStock;

	// Data

	private final FactoryPanelBehaviour behaviour;
	private final boolean restocker;
	private boolean sendReset;
	private boolean sendRedstoneReset;
	private List<FactoryPanelConnection> connections;
	private BigItemStack outputConfig;
	private CraftingRecipe availableCraftingRecipe;
	private List<BigItemStack> craftingIngredients;
	private int recipeCraftCount = 1;

	public FactoryPanelScreen(FactoryPanelMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		minecraft = Minecraft.getInstance();
		behaviour = menu.contentHolder;
		restocker = behaviour.panelBE().restocker;
		menu.craftingActive = !behaviour.activeCraftingArrangement.isEmpty();
		if (menu.craftingActive) {
			for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
				menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
		}
		recipeCraftCount = ((GhostGridAccessor) behaviour).bfg$getRecipeCraftCount();
		updateConfigs();
	}

	private void updateConfigs() {
		connections = new ArrayList<>(behaviour.targetedBy.values());
		outputConfig = new BigItemStack(behaviour.getFilter(), behaviour.recipeOutput);

		List<BigItemStack> inputConfig = connections.stream()
			.map(c -> {
				FactoryPanelBehaviour b = FactoryPanelBehaviour.at(minecraft.level, c.from);
				return b == null ? new BigItemStack(ItemStack.EMPTY, 0) : new BigItemStack(b.getFilter(), c.amount);
			})
			.toList();
		searchForCraftingRecipe(inputConfig);
		if (availableCraftingRecipe == null) {
			if (menu.craftingActive) {
				menu.craftingActive = false;
				rebuildGhostInventory();
			}
		} else {
			craftingIngredients = com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen
				.convertRecipeToPackageOrderContext(availableCraftingRecipe, inputConfig, false);
		}
	}

	private void searchForCraftingRecipe(List<BigItemStack> inputConfig) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null)
			return;
		ItemStack outputItem = outputConfig.stack;
		if (outputItem.isEmpty())
			return;
		if (behaviour.targetedBy.isEmpty())
			return;

		Set<Item> inputItems = new HashSet<>();
		for (BigItemStack bis : inputConfig)
			if (!bis.stack.isEmpty())
				inputItems.add(bis.stack.getItem());
		if (inputItems.isEmpty())
			return;

		availableCraftingRecipe = mc.level.getRecipeManager()
			.getAllRecipesFor(RecipeType.CRAFTING)
			.parallelStream()
			.filter(holder -> !AllRecipeTypes.shouldIgnoreInAutomation(holder))
			.filter(holder -> {
				ItemStack result = holder.value().getResultItem(mc.level.registryAccess());
				return result.getItem() == outputItem.getItem();
			})
			.filter(holder -> {
				Set<Item> requiredItems = new HashSet<>();
				for (Ingredient ingredient : holder.value().getIngredients()) {
					if (ingredient.isEmpty())
						continue;
					boolean found = false;
					for (BigItemStack bis : inputConfig) {
						if (!bis.stack.isEmpty() && ingredient.test(bis.stack)) {
							requiredItems.add(bis.stack.getItem());
							found = true;
							break;
						}
					}
					if (!found)
						return false;
				}
				return requiredItems.size() >= inputItems.size();
			})
			.findAny()
			.map(holder -> (CraftingRecipe) holder.value())
			.orElse(null);
	}

	//

	@Override
	protected void init() {
		int sizeX = FACTORY_GAUGE_BOTTOM.getWidth();
		AllGuiTextures contentTex = restocker ? FACTORY_GAUGE_RESTOCK : FACTORY_GAUGE_RECIPE;
		int baseHeight = contentTex.getHeight() + FACTORY_GAUGE_BOTTOM.getHeight();
		int calGap = CALCompatHelper.getVerticalGap();
		int windowHeight = baseHeight + 4 + PLAYER_INVENTORY.getHeight() + calGap;
		setWindowSize(sizeX, windowHeight);
		super.init();
		clearWidgets();

		int x = getGuiLeft();
		int y = getGuiTop();

		if (addressBox == null) {
			String frogAddress = behaviour.getFrogAddress();
			addressBox = new AddressEditBox(this, new NoShadowFontWrapper(font), x + 36, y + baseHeight - 51, 108, 10,
				false, frogAddress);
			addressBox.setValue(behaviour.recipeAddress);
			addressBox.setTextColor(0x555555);
		}
		addressBox.setX(x + 36);
		addressBox.setY(y + baseHeight - 51);
		addRenderableWidget(addressBox);

		confirmButton = new IconButton(x + sizeX - 33, y + baseHeight - 25, AllIcons.I_CONFIRM);
		confirmButton.withCallback(this::onConfirm);
		confirmButton.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());
		addRenderableWidget(confirmButton);

		deleteButton = new IconButton(x + sizeX - 55, y + baseHeight - 25, AllIcons.I_TRASH);
		deleteButton.withCallback(() -> {
			sendReset = true;
			if (!restocker)
				for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
					menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
			recipeCraftCount = 1;
			PacketDistributor.sendToServer(new SyncCraftCountPayload(behaviour.getPanelPosition(), 1));
			sendIt(null, false);
			onClose();
		});
		deleteButton.setToolTip(CreateLang.translate("gui.factory_panel.reset").component());
		addRenderableWidget(deleteButton);

		promiseExpiration = new ScrollInput(x + 97, y + baseHeight - 24, 28, 16).withRange(-1, 31)
			.titled(CreateLang.translate("gui.factory_panel.promises_expire_title").component());
		promiseExpiration.setState(behaviour.promiseClearingInterval);
		addRenderableWidget(promiseExpiration);

		newInputButton = new IconButton(x + 31, y + 47, AllIcons.I_ADD);
		newInputButton.withCallback(() -> {
			FactoryPanelConnectionHandler.startConnection(behaviour);
			minecraft.setScreen(null);
		});
		newInputButton.setToolTip(CreateLang.translate("gui.factory_panel.connect_input").component());

		relocateButton = new IconButton(x + 31, y + 67, AllIcons.I_MOVE_GAUGE);
		relocateButton.withCallback(() -> {
			FactoryPanelConnectionHandler.startRelocating(behaviour);
			minecraft.setScreen(null);
		});
		relocateButton.setToolTip(CreateLang.translate("gui.factory_panel.relocate").component());

		if (!restocker) {
			addRenderableWidget(newInputButton);
			addRenderableWidget(relocateButton);
		}

		activateCraftingButton = null;
		if (availableCraftingRecipe != null) {
			activateCraftingButton = new IconButton(x + 31, y + 27, AllIcons.I_3x3);
			activateCraftingButton.withCallback(this::onActivateCrafting);
			activateCraftingButton.setToolTip(CreateLang.translate("gui.factory_panel.activate_crafting").component());
			addRenderableWidget(activateCraftingButton);
		}
		if (CALCompatHelper.isLoaded()) {
			if (CALCompatHelper.isPromiseLimitsEnabled()) {
				calPromiseLimit = new ScrollInput(x + 68, y + baseHeight + 3, 56, 16)
					.withRange(-1, restocker ? 64 * 100 * 20 : 1000);
				if (restocker)
					calPromiseLimit = calPromiseLimit.withShiftStep(behaviour.getFilter().getMaxStackSize());
				else
					calPromiseLimit = calPromiseLimit.withShiftStep(10)
						.withStepFunction(c -> {
							if (menu.craftingActive) {
								if (c.currentValue < 0)
									return recipeCraftCount + 1;
								return c.shift ? 10 : recipeCraftCount;
							}
							return c.shift ? 10 : 1;
						});
				calPromiseLimit.setState(CALCompatHelper.getPromiseLimit(behaviour));
				calUpdatePromiseLimitLabel();
				addRenderableWidget(calPromiseLimit);
			}
			if (restocker && CALCompatHelper.isAdditionalStockEnabled()) {
				int maxSize = behaviour.getFilter().getMaxStackSize();
				calAdditionalStock = new ScrollInput(x + 4, y + baseHeight - 24, 47, 16)
					.withRange(0, 1 + maxSize * 100)
					.withStepFunction(c -> {
						if (!c.shift)
							return 1;
						if (maxSize == 1)
							return 5;
						int remaining = c.currentValue % maxSize;
						if (remaining == 0)
							return maxSize;
						if (c.forward)
							return maxSize - remaining;
						return remaining;
					})
					.withShiftStep(maxSize == 1 ? 5 : maxSize);
				calAdditionalStock.setState(CALCompatHelper.getAdditionalStock(behaviour));
				calUpdateAdditionalStockLabel();
				addRenderableWidget(calAdditionalStock);
			}
		}
	}

	//

	private void calUpdatePromiseLimitLabel() {
		if (calPromiseLimit == null)
			return;
		String key = "createadditionallogistics.gauge.promise_limit";
		if (calPromiseLimit.getState() == -1)
			key = key + ".none";
		calPromiseLimit.titled(Component.translatable(key));
	}

	private void calUpdateAdditionalStockLabel() {
		if (calAdditionalStock == null)
			return;
		String key = "createadditionallogistics.gauge.request_additional";
		if (calAdditionalStock.getState() <= 0)
			key = key + ".none";
		calAdditionalStock.titled(Component.translatable(key));
	}

	private String calFormatAdditional() {
		int additional = calAdditionalStock == null ? 0 : calAdditionalStock.getState();
		if (additional <= 0)
			return "---";
		int stackSize = behaviour.getFilter().getMaxStackSize();
		if (stackSize == 1)
			return String.valueOf(additional);
		int stacks = additional / stackSize;
		int items = additional % stackSize;
		if (stacks == 0)
			return String.valueOf(items);
		if (items != 0)
			return String.valueOf(additional);
		return stacks + "▤";
	}

	@Override
	public void containerTick() {
		super.containerTick();
		if (connections.size() != behaviour.targetedBy.size()) {
			updateConfigs();
			if (!menu.craftingActive)
				rebuildGhostInventory();
			init();
		}
		if (activateCraftingButton != null)
			activateCraftingButton.green = menu.craftingActive;
		addressBox.tick();
		promiseExpiration.titled(CreateLang
			.translate(promiseExpiration.getState() == -1 ? "gui.factory_panel.promises_do_not_expire"
				: "gui.factory_panel.promises_expire_title")
			.component());
		calUpdatePromiseLimitLabel();
		calUpdateAdditionalStockLabel();
	}

	//

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		int x = getGuiLeft();
		int y = getGuiTop();

		AllGuiTextures contentTex = restocker ? FACTORY_GAUGE_RESTOCK : FACTORY_GAUGE_RECIPE;
		if (restocker)
			FACTORY_GAUGE_RECIPE.render(graphics, x, y - 16);

		contentTex.render(graphics, x, y);

		int baseHeight = contentTex.getHeight() + FACTORY_GAUGE_BOTTOM.getHeight();
		FACTORY_GAUGE_BOTTOM.render(graphics, x, y + contentTex.getHeight());

		renderPlayerInventory(graphics, x + 8, y + baseHeight + 4 + CALCompatHelper.getVerticalGap());

		Component title = CreateLang
			.translate(restocker ? "gui.factory_panel.title_as_restocker" : "gui.factory_panel.title_as_recipe")
			.component();
		graphics.drawString(font, title, x + 97 - font.width(title) / 2, y + (restocker ? -12 : 4), 0x3D3C48, false);

		int previewY = restocker ? 0 : 55;
		graphics.pose().pushPose();
		graphics.pose().translate(0, previewY, 0);
		GuiGameElement.of(AllBlocks.FACTORY_GAUGE.asStack())
			.scale(4.0)
			.at(0, 0, -200)
			.render(graphics, x + 195, y + 55);

		if (!behaviour.getFilter().isEmpty()) {
			GuiGameElement.of(behaviour.getFilter())
				.scale(1.625)
				.at(0, 0, 100)
				.render(graphics, x + 214, y + 68);
		}
		graphics.pose().popPose();

		if (!restocker)
			renderOutputItem(graphics, mouseX, mouseY);

		renderInputGrid(graphics, mouseX, mouseY);

		if (!behaviour.targetedByLinks.isEmpty()) {
			int linkX = x + 9;
			int linkY = y + baseHeight - 24;
			FROGPORT_SLOT.render(graphics, linkX - 1, linkY - 1);
			graphics.renderItem(AllBlocks.REDSTONE_LINK.asStack(), linkX, linkY);
		}

		int boxX = x + 68;
		int boxY = y + baseHeight - 24;
		ItemStack defaultBox = PackageStyles.getDefaultBox();
		graphics.renderItem(defaultBox, boxX, boxY);
		graphics.renderItemDecorations(font, defaultBox, boxX, boxY, String.valueOf(behaviour.getPromised()));

		int state = promiseExpiration.getState();
		graphics.drawString(font,
			Component.literal(state == -1 ? " /" : state == 0 ? "30s" : state + "m"),
			promiseExpiration.getX() + 3, promiseExpiration.getY() + 4, 0xffeeeeee, true);

		if (CALCompatHelper.isLoaded()) {
						if (calPromiseLimit != null) {
				graphics.blit(CAL_PROMISE_LIMIT_TEX, calPromiseLimit.getX() - 8, calPromiseLimit.getY() - 4, 0, 0, 72, 28, 128, 32);
				int limit = calPromiseLimit.getState();
				if (limit >= 0 && !restocker && outputConfig != null)
					limit *= outputConfig.count;
				graphics.drawString(font,
					CreateLang.text(limit == -1 ? " ---" : " " + limit).component(),
					calPromiseLimit.getX() + 3, calPromiseLimit.getY() + 4, 0xffeeeeee, true);
			}
			if (calAdditionalStock != null) {
				graphics.blit(CAL_PROMISE_LIMIT_TEX, calAdditionalStock.getX() + 2, calAdditionalStock.getY() - 1, 72, 0, 47, 18, 128, 32);
				graphics.drawString(font,
					CreateLang.text(" " + calFormatAdditional()).component(),
					calAdditionalStock.getX() + 15, calAdditionalStock.getY() + 4, 0xffeeeeee, true);
			}
		}
	}

	//

	private void renderInputGrid(GuiGraphics graphics, int mouseX, int mouseY) {
		int slot = 0;

		if (menu.craftingActive) {
			for (BigItemStack itemStack : craftingIngredients)
				renderInputItem(graphics, slot++, itemStack, mouseX, mouseY);
		} else if (!restocker) {
			if (connections.isEmpty()) {
				int inputX = getGuiLeft() + 68;
				int inputY = getGuiTop() + 28;
				if (mouseY > inputY && mouseY < inputY + 60 && mouseX > inputX && mouseX < inputX + 60)
					graphics.renderComponentTooltip(font,
						List.of(CreateLang.translate("gui.factory_panel.unconfigured_input")
							.color(ScrollInput.HEADER_RGB)
							.component(),
							CreateLang.translate("gui.factory_panel.unconfigured_input_tip")
								.style(ChatFormatting.GRAY)
								.component(),
							CreateLang.translate("gui.factory_panel.unconfigured_input_tip_1")
								.style(ChatFormatting.GRAY)
								.component()),
						mouseX, mouseY);
			}
		}

		if (restocker)
			renderInputItem(graphics, slot, new BigItemStack(behaviour.getFilter(), 1), mouseX, mouseY);
	}

	private void renderInputItem(GuiGraphics graphics, int slot, BigItemStack itemStack, int mouseX, int mouseY) {
		int inputX = restocker ? getGuiLeft() + 88 : gridSlotX(slot);
		int inputY = restocker ? getGuiTop() + 12 + (slot / 3 * 20) : gridSlotY(slot);

		graphics.renderItem(itemStack.stack, inputX, inputY);
		if (menu.craftingActive && !itemStack.stack.isEmpty())
			graphics.renderItemDecorations(font, itemStack.stack, inputX, inputY, (itemStack.count * recipeCraftCount) + "");

		if (mouseX < inputX - 2 || mouseX >= inputX - 2 + 20 || mouseY < inputY - 2 || mouseY >= inputY - 2 + 20)
			return;

		if (menu.craftingActive) {
			graphics.renderComponentTooltip(font,
				List.of(CreateLang.translate("gui.factory_panel.crafting_input")
					.color(ScrollInput.HEADER_RGB)
					.component(),
					CreateLang.translate("gui.factory_panel.crafting_input_tip")
						.style(ChatFormatting.GRAY)
						.component(),
					CreateLang.translate("gui.factory_panel.crafting_input_tip_1")
						.style(ChatFormatting.GRAY)
						.component(),
					CreateLang.translate("gui.factory_panel.craft_count_scroll_tip")
						.style(ChatFormatting.DARK_GRAY)
						.style(ChatFormatting.ITALIC)
						.component()),
			mouseX, mouseY);
			return;
		}

		if (itemStack.stack.isEmpty()) {
			graphics.renderComponentTooltip(font, List.of(CreateLang.translate("gui.factory_panel.empty_panel")
				.color(ScrollInput.HEADER_RGB)
				.component(),
				CreateLang.translate("gui.factory_panel.left_click_disconnect")
					.style(ChatFormatting.DARK_GRAY)
					.style(ChatFormatting.ITALIC)
					.component()),
				mouseX, mouseY);
			return;
		}

		if (restocker) {
			graphics.renderComponentTooltip(font,
				List.of(CreateLang.translate("gui.factory_panel.sending_item", CreateLang.itemName(itemStack.stack)
					.string())
					.color(ScrollInput.HEADER_RGB)
					.component(),
					CreateLang.translate("gui.factory_panel.sending_item_tip")
						.style(ChatFormatting.GRAY)
						.component(),
					CreateLang.translate("gui.factory_panel.sending_item_tip_1")
						.style(ChatFormatting.GRAY)
						.component()),
				mouseX, mouseY);
			return;
		}
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.renderForeground(graphics, mouseX, mouseY, partialTicks);

		if (menu.isInteractionLocked())
			return;

		int x = getGuiLeft();
		int y = getGuiTop();

		for (int i = 0; i < 9; i++) {
			ItemStack stack = menu.ghostInventory.getStackInSlot(i);
			if (stack.isEmpty())
				continue;
			int slotX = gridSlotX(i);
			int slotY = gridSlotY(i);
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 100);
			graphics.renderItemDecorations(font, stack, slotX, slotY, "" + stack.getCount());
			graphics.pose().popPose();
		}
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		if (!(hoveredSlot instanceof SlotItemHandler) || menu.isInteractionLocked())
			return super.getTooltipFromContainerItem(stack);

		int slotIndex = hoveredSlot.getSlotIndex();
		if (slotIndex >= 9)
			return super.getTooltipFromContainerItem(stack);

		if (stack.isEmpty())
			return super.getTooltipFromContainerItem(stack);

		return List.of(
			CreateLang.translate("gui.factory_panel.sending_item",
				stack.getHoverName().getString() + " x" + stack.getCount()).component(),
			CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
				.style(ChatFormatting.DARK_GRAY)
				.style(ChatFormatting.ITALIC)
				.component(),
			CreateLang.translate("gui.factory_panel.left_click_disconnect")
				.style(ChatFormatting.DARK_GRAY)
				.style(ChatFormatting.ITALIC)
				.component());
	}

	private void renderOutputItem(GuiGraphics graphics, int mouseX, int mouseY) {
		int outputX = getGuiLeft() + 160;
		int outputY = getGuiTop() + 48;
		graphics.renderItem(outputConfig.stack, outputX, outputY);
		graphics.renderItemDecorations(font, behaviour.getFilter(), outputX, outputY, (menu.craftingActive ? outputConfig.count * recipeCraftCount : outputConfig.count) + "");

		if (mouseX >= outputX - 1 && mouseX < outputX - 1 + 18 && mouseY >= outputY - 1
			&& mouseY < outputY - 1 + 18) {
			MutableComponent c1 = CreateLang
				.translate("gui.factory_panel.expected_output", CreateLang.itemName(outputConfig.stack)
					.add(CreateLang.text(" x" + (menu.craftingActive ? outputConfig.count * recipeCraftCount : outputConfig.count)))
					.string())
				.color(ScrollInput.HEADER_RGB)
				.component();
			MutableComponent c2 = CreateLang.translate("gui.factory_panel.expected_output_tip")
				.style(ChatFormatting.GRAY)
				.component();
			MutableComponent c3 = CreateLang.translate("gui.factory_panel.expected_output_tip_1")
				.style(ChatFormatting.GRAY)
				.component();
			MutableComponent c4 = CreateLang.translate("gui.factory_panel.expected_output_tip_2")
				.style(ChatFormatting.DARK_GRAY)
				.style(ChatFormatting.ITALIC)
				.component();
			MutableComponent c5 = CreateLang.translate("gui.factory_panel.craft_count_scroll_tip")
				.style(ChatFormatting.DARK_GRAY)
				.style(ChatFormatting.ITALIC)
				.component();
			graphics.renderComponentTooltip(font, menu.craftingActive ? List.of(c1, c2, c3, c5) : List.of(c1, c2, c3, c4),
				mouseX, mouseY);
		}
	}

	//

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int pButton) {
		if (getFocused() != null && !getFocused().isMouseOver(mouseX, mouseY))
			setFocused(null);

		int x = getGuiLeft();
		int y = getGuiTop();
			Slot slot = findSlot(mouseX, mouseY);

		// Block ghost slot interactions in crafting or restocker mode
		if (menu.isInteractionLocked()) {
						if (isGhostSlot(slot))
				return true;
		}

		// Left-click last instance of an item: disconnect the connection
		if (!menu.isInteractionLocked() && pButton == 0 && getMenu().getCarried().isEmpty()) {
						if (isGhostSlot(slot)) {
				int ghostIdx = slot.index - 36;
				ItemStack clickedItem = menu.ghostInventory.getStackInSlot(ghostIdx);
				if (!clickedItem.isEmpty()) {
					long instanceCount = 0;
					for (int i = 0; i < 9; i++) {
						ItemStack s = menu.ghostInventory.getStackInSlot(i);
						if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, clickedItem))
							instanceCount++;
					}
					if (instanceCount == 1) {
						FactoryPanelPosition toRemove = menu.findConnectionForItem(clickedItem);
						if (toRemove != null) {
							menu.ghostInventory.setStackInSlot(ghostIdx, ItemStack.EMPTY);
							sendIt(toRemove, false);
							playButtonSound();
							return true;
						}
					}
				}
			}
		}

		// Right-click ghost slot: disconnect the connection
		if (!menu.isInteractionLocked() && pButton == 1) {
						if (isGhostSlot(slot)) {
				int ghostIdx = slot.index - 36;
				ItemStack clickedItem = menu.ghostInventory.getStackInSlot(ghostIdx);
				if (!clickedItem.isEmpty()) {
					FactoryPanelPosition toRemove = menu.findConnectionForItem(clickedItem);
					if (toRemove != null) {
						menu.ghostInventory.setStackInSlot(ghostIdx, ItemStack.EMPTY);
						sendIt(toRemove, false);
						playButtonSound();
					}
				}
				return true;
			}
		}

		// Promise clear
		AllGuiTextures ct = restocker ? FACTORY_GAUGE_RESTOCK : FACTORY_GAUGE_RECIPE;
		int gaugeBottom = ct.getHeight() + FACTORY_GAUGE_BOTTOM.getHeight();
		int itemX = x + 68;
		int itemY = y + gaugeBottom - 24;
		if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
			sendIt(null, true);
			playButtonSound();
			return true;
		}

		// Remove redstone connections
		itemX = x + 9;
		itemY = y + gaugeBottom - 24;
		if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
			sendRedstoneReset = true;
			sendIt(null, false);
			playButtonSound();
			return true;
		}

		return super.mouseClicked(mouseX, mouseY, pButton);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int x = getGuiLeft();
		int y = getGuiTop();

		if (addressBox.mouseScrolled(mouseX, mouseY, scrollX, scrollY))
			return true;

		if (menu.craftingActive) {
			boolean overGrid = false;
			for (int i = 0; i < 9; i++) {
				int gx = gridSlotX(i);
				int gy = gridSlotY(i);
				if (mouseX >= gx && mouseX < gx + 16 && mouseY >= gy && mouseY < gy + 16) {
					overGrid = true;
					break;
				}
			}
			int outputX = x + 160;
			int outputY = y + 48;
			boolean overOutput = mouseX >= outputX && mouseX < outputX + 16 && mouseY >= outputY && mouseY < outputY + 16;

			if (overGrid || overOutput) {
				int delta = (int) Math.signum(scrollY) * (hasShiftDown() ? 10 : 1);
				((GhostGridAccessor) behaviour).bfg$setRecipeCraftCount(recipeCraftCount + delta);
				recipeCraftCount = ((GhostGridAccessor) behaviour).bfg$getRecipeCraftCount();
				PacketDistributor.sendToServer(new SyncCraftCountPayload(behaviour.getPanelPosition(), recipeCraftCount));
				return true;
			}
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		if (!restocker) {
			int gridSlot = getGridSlotIndex(mouseX, mouseY);
			if (gridSlot != -1) {
				ItemStack slotItem = menu.ghostInventory.getStackInSlot(gridSlot);
				if (slotItem.isEmpty())
					return true;
				int delta = (int) Math.signum(scrollY) * (hasShiftDown() ? 10 : 1);
				slotItem.setCount(Mth.clamp(slotItem.getCount() + delta, 1, 64));
				return true;
			}

			int outputX = x + 160;
			int outputY = y + 48;
			if (mouseX >= outputX && mouseX < outputX + 16 && mouseY >= outputY && mouseY < outputY + 16) {
				outputConfig.count = Math.max(1, (int) (outputConfig.count + Math.signum(scrollY) * (hasShiftDown() ? 10 : 1)));
				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	//

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (addressBox.keyPressed(keyCode, scanCode, modifiers))
			return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (addressBox.charTyped(codePoint, modifiers))
			return true;
		return super.charTyped(codePoint, modifiers);
	}

	//

	@Override
	public void removed() {
		sendIt(null, false);
		super.removed();
	}

	private void playButtonSound() {
		Minecraft.getInstance()
			.getSoundManager()
			.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
	}

	private void sendIt(FactoryPanelPosition removePos, boolean clearPromises) {
		Map<FactoryPanelPosition, Integer> inputAmounts = new HashMap<>();

		if (!menu.isInteractionLocked()) {
			for (FactoryPanelConnection conn : connections) {
				FactoryPanelBehaviour source = FactoryPanelBehaviour.at(minecraft.level, conn.from);
				if (source == null)
					continue;
				ItemStack filter = source.getFilter();
				if (filter.isEmpty())
					continue;
				int total = 0;
				for (int s = 0; s < 9; s++) {
					ItemStack ghostItem = menu.ghostInventory.getStackInSlot(s);
					if (!ghostItem.isEmpty() && ItemStack.isSameItemSameComponents(ghostItem, filter))
						total += ghostItem.getCount();
				}
				inputAmounts.put(conn.from, total);
			}
		} else if (menu.craftingActive) {
			for (FactoryPanelConnection conn : connections) {
				FactoryPanelBehaviour source = FactoryPanelBehaviour.at(minecraft.level, conn.from);
				if (source == null)
					continue;
				ItemStack filter = source.getFilter();
				if (filter.isEmpty())
					continue;
				int count = (int) craftingIngredients.stream()
					.filter(ci -> !ci.stack.isEmpty() && ItemStack.isSameItemSameComponents(ci.stack, filter))
					.count();
				inputAmounts.put(conn.from, count * recipeCraftCount);
			}
		} else {
			ItemStack restockStack = menu.ghostInventory.getStackInSlot(0);
			if (!restockStack.isEmpty() && !connections.isEmpty())
				inputAmounts.put(connections.get(0).from, restockStack.getCount());
		}

		List<ItemStack> craftingArrangement = menu.craftingActive
			? craftingIngredients.stream().map(bis -> bis.stack).toList()
			: List.of();

		FactoryPanelConfigurationPacket packet = new FactoryPanelConfigurationPacket(behaviour.getPanelPosition(),
			addressBox.getValue(), inputAmounts, craftingArrangement, outputConfig.count, promiseExpiration.getState(),
			removePos, clearPromises, sendReset, sendRedstoneReset);
		CatnipServices.NETWORK.sendToServer(packet);

		if (CALCompatHelper.isLoaded() && calPromiseLimit != null) {
			int limit = calPromiseLimit.getState();
			int additional = calAdditionalStock != null ? calAdditionalStock.getState() : 0;
			CALCompatHelper.sendUpdatePacket(behaviour.getPanelPosition(), limit, additional);
		}
	}

	// Button callbacks

	private void onConfirm() {
		String address = addressBox.getValue();
		int promiseInterval = promiseExpiration.getState();
		behaviour.recipeAddress = address;
		behaviour.promiseClearingInterval = promiseInterval;
		sendIt(null, false);
		onClose();
	}

	private void onActivateCrafting() {
		menu.craftingActive = !menu.craftingActive;
		if (menu.craftingActive) {
			List<ItemStack> grid = new ArrayList<>();
			for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
				grid.add(menu.ghostInventory.getStackInSlot(i).copy());
			((GhostGridAccessor) behaviour).bfg$setGhostGrid(grid);

			for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
				menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
			if (availableCraftingRecipe != null)
				outputConfig.count = availableCraftingRecipe.getResultItem(minecraft.level.registryAccess()).getCount();
		} else {
			rebuildGhostInventory();
		}
		init();
	}

	//

	private void rebuildGhostInventory() {
		if (menu.isInteractionLocked())
			return;

		for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
			menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);

		List<ItemStack> saved = ((GhostGridAccessor) behaviour).bfg$getGhostGrid();

		for (int i = 0; i < Math.min(9, saved.size()); i++)
			menu.ghostInventory.setStackInSlot(i, saved.get(i).copy());

		menu.clearUnlinkedItems(menu.ghostInventory);
	}

	private Slot findSlot(double mouseX, double mouseY) {
		for (Slot slot : menu.slots) {
			if (mouseX >= slot.x + getGuiLeft() && mouseX < slot.x + getGuiLeft() + 16
				&& mouseY >= slot.y + getGuiTop() && mouseY < slot.y + getGuiTop() + 16)
				return slot;
		}
		return null;
	}

	private static boolean isGhostSlot(Slot slot) {
		return slot != null && slot.index >= 36 && slot.index < 45;
	}

	private int gridSlotX(int index) {
		return getGuiLeft() + 68 + (index % 3 * 20);
	}

	private int gridSlotY(int index) {
		return getGuiTop() + 28 + (index / 3 * 20);
	}

	private int getGridSlotIndex(double mouseX, double mouseY) {
		for (int i = 0; i < 9; i++) {
			int sx = gridSlotX(i);
			int sy = gridSlotY(i);
			if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16)
				return i;
		}
		return -1;
	}
}
