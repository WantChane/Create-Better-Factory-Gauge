package com.wantchane.bfg.factory_panel;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
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
import net.createmod.catnip.gui.element.GuiGameElement;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.core.NonNullList;

import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.*;

/**
 * 工厂仪表主界面 —— 包含玩家物品栏的容器屏幕。
 *
 * <h2>坐标系说明</h2>
 * 整个窗口的坐标原点由父类 {@code AbstractContainerScreen} 自动计算：
 * <ul>
 *   <li>{@code x = getGuiLeft()} —— 窗口左边缘在屏幕上的 X 像素坐标</li>
 *   <li>{@code y = getGuiTop()}  —— 窗口顶边缘在屏幕上的 Y 像素坐标</li>
 * </ul>
 * Minecraft 自动居中：{@code x = (屏幕宽度 - imageWidth) / 2}，{@code y = (屏幕高度 - imageHeight) / 2}。
 * imageWidth / imageHeight 由 {@link #setWindowSize(int, int)} 设定。
 * <b>所有渲染位置和点击判定都是 `x + 偏移量` / `y + 偏移量` 的形式。</b>
 * x 和 y 会随窗口大小变化自动重算，只需关心偏移量。
 *
 * <h2>贴图尺寸常量</h2>
 * <table>
 *   <tr><th>贴图</th><th>宽</th><th>高</th><th>用途</th></tr>
 *   <tr><td>FACTORY_GAUGE_RECIPE</td><td>192</td><td>96</td><td>配方模式主贴图</td></tr>
 *   <tr><td>FACTORY_GAUGE_RESTOCK</td><td>192</td><td>40</td><td>补货模式主贴图</td></tr>
 *   <tr><td>FACTORY_GAUGE_BOTTOM</td><td>200</td><td>64</td><td>底部贴图（地址框、按钮区域）</td></tr>
 *   <tr><td>PLAYER_INVENTORY</td><td>176</td><td>~108</td><td>玩家物品栏背景贴图</td></tr>
 * </table>
 *
 * <h2>窗口尺寸计算</h2>
 * <pre>
 *   baseHeight = 主贴图高 + 底部贴图高    // 配方模式 96+64=160，补货模式 40+64=104
 *   windowHeight = baseHeight + 玩家物品栏高  // 配方 160+108=268，补货 104+108=212
 *   windowWidth = 200（跟随 BOTTOM 贴图宽度）
 * </pre>
 * 玩家物品栏紧贴在仪表内容下方，无重叠。
 * 如需间距，将 windowHeight 改为 {@code baseHeight + playerInvHeight + 间距}。
 *
 * <h2>控件 Y 坐标规则</h2>
 * 底部栏控件（地址框、按钮等）位于底部贴图区域内，使用公式：
 * <pre>y + baseHeight - 偏移量</pre>
 * "距仪表内容底部向上 N 像素" 的意思。例如：
 * <ul>
 *   <li>{@code y + baseHeight - 51} → 距底部向上 51px（地址输入框）</li>
 *   <li>{@code y + baseHeight - 25} → 距底部向上 25px（确认/删除按钮）</li>
 *   <li>{@code y + baseHeight - 24} → 距底部向上 24px（承诺过期选择器、红石槽、包裹箱）</li>
 * </ul>
 * 注意：这里用的是 {@code baseHeight}（仪表内容高度，160 或 104），
 * 不是 {@code windowHeight}（含物品栏的窗口总高度，268 或 212）。
 * 因为控件属于仪表内容的一部分，和物品栏无关。
 *
 * <h2>参考</h2>
 * 本屏幕的布局参考了 Create 原版的 {@code FactoryPanelSetItemScreen}（位于
 * {@code com.simibubi.create.content.logistics.factoryBoard}），
 * 它同样继承 {@code AbstractSimiContainerScreen} 并包含玩家物品栏。
 */
public class FactoryPanelScreen extends AbstractSimiContainerScreen<FactoryPanelMenu> {

	// ==================== 控件字段 ====================

	/** 地址输入框 —— 底部贴图区域内，位于 (x+36, y+baseHeight-51)，尺寸 108×10 */
	private AddressEditBox addressBox;
	/** 确认按钮 —— 右下角，位于 (x+width-33, y+baseHeight-25) */
	private IconButton confirmButton;
	/** 删除/重置按钮 —— 确认按钮左侧，位于 (x+width-55, y+baseHeight-25) */
	private IconButton deleteButton;
	/** 新增输入按钮 —— 仅配方模式，左侧 (x+31, y+47) */
	private IconButton newInputButton;
	/** 重新定位按钮 —— 仅配方模式，新增按钮下方 (x+31, y+67) */
	private IconButton relocateButton;
	/** 激活合成按钮 —— 配方模式且检测到合成配方时出现，(x+31, y+27) */
	private IconButton activateCraftingButton;
	/** 承诺过期时间滚动选择器 —— (x+97, y+baseHeight-24)，尺寸 28×16 */
	private ScrollInput promiseExpiration;

	// ==================== 数据字段 ====================

	/** 关联的 FactoryPanelBehaviour，数据来源 */
	private final FactoryPanelBehaviour behaviour;
	/** true=补货模式，false=配方模式。决定使用哪套贴图和布局 */
	private final boolean restocker;
	/** 发送重置标志（删除按钮触发） */
	private boolean sendReset;
	/** 发送红石重置标志 */
	private boolean sendRedstoneReset;
	/** 当前连接列表（从 behaviour.targetedBy 复制） */
	private List<FactoryPanelConnection> connections;
	/** 输出物品配置（过滤器物品 + 数量） */
	private BigItemStack outputConfig;
	/** 匹配到的合成配方，null 表示无匹配 */
	private CraftingRecipe availableCraftingRecipe;
	/** 合成是否激活 */
	private boolean craftingActive;
	/** 合成配料列表（激活合成时替换 inputGrid 显示） */
	private List<BigItemStack> craftingIngredients;
	/** 输入配置列表（连接→物品+数量），用于 sendIt() 索引查找 */
	private List<BigItemStack> inputConfig;


	public FactoryPanelScreen(FactoryPanelMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		minecraft = Minecraft.getInstance();
		this.behaviour = menu.contentHolder;
		this.restocker = behaviour.panelBE().restocker;
		this.craftingActive = !behaviour.activeCraftingArrangement.isEmpty();
		menu.craftingActive = this.craftingActive;
		if (craftingActive) {
			for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
				menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
		}
		updateConfigs();
	}

	/**
	 * 刷新全部配置数据。当连接数量变化时（targetedBy 大小改变），
	 * {@link #containerTick()} 会检测到并调用此方法 + {@link #init()} 重建界面。
	 */
	private void updateConfigs() {
		connections = new ArrayList<>(behaviour.targetedBy.values());
		outputConfig = new BigItemStack(behaviour.getFilter(), behaviour.recipeOutput);

		// Build inputConfig for crafting recipe search
		inputConfig = connections.stream()
			.map(c -> {
				FactoryPanelBehaviour b = FactoryPanelBehaviour.at(minecraft.level, c.from);
				return b == null ? new BigItemStack(ItemStack.EMPTY, 0) : new BigItemStack(b.getFilter(), c.amount);
			})
			.toList();
		searchForCraftingRecipe(inputConfig);
		if (availableCraftingRecipe == null) {
			if (craftingActive) {
				craftingActive = false;
				menu.craftingActive = false;
				rebuildGhostInventory();
			}
		} else {
			craftingIngredients = convertRecipeToPackageOrderContext(availableCraftingRecipe, inputConfig, false);
		}
	}

	/**
	 * 搜索匹配的合成配方。
	 */
	private void searchForCraftingRecipe(List<BigItemStack> inputConfig) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		ItemStack outputItem = outputConfig.stack;
		if (outputItem.isEmpty()) return;
		if (behaviour.targetedBy.isEmpty()) return;

		Set<ItemStack> inputItems = new HashSet<>();
		for (BigItemStack bis : inputConfig) {
			if (!bis.stack.isEmpty()) inputItems.add(bis.stack);
		}
		if (inputItems.isEmpty()) return;

		availableCraftingRecipe = mc.level.getRecipeManager()
			.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
			.parallelStream()
			.filter(holder -> !AllRecipeTypes.shouldIgnoreInAutomation(holder))
			.filter(holder -> {
				ItemStack result = holder.value().getResultItem(mc.level.registryAccess());
				return result.getItem() == outputItem.getItem();
			})
			.filter(holder -> {
				Set<ItemStack> requiredItems = new HashSet<>();
				for (Ingredient ingredient : holder.value().getIngredients()) {
					if (ingredient.isEmpty()) continue;
					boolean found = false;
					for (BigItemStack bis : inputConfig) {
						if (!bis.stack.isEmpty() && ingredient.test(bis.stack)) {
							requiredItems.add(bis.stack);
							found = true;
							break;
						}
					}
					if (!found) return false;
				}
				return requiredItems.size() >= inputItems.size();
			})
			.findAny()
			.map(holder -> (CraftingRecipe) holder.value())
			.orElse(null);
	}

	/**
	 * 将合成配方转换为 3×3 格子展示格式。
	 */
	public static List<BigItemStack> convertRecipeToPackageOrderContext(
			CraftingRecipe recipe, List<BigItemStack> inputConfig, boolean testOnly) {
		List<BigItemStack> result = new ArrayList<>();
		BigItemStack empty = new BigItemStack(ItemStack.EMPTY, 1);
		NonNullList<Ingredient> ingredients = recipe.getIngredients();
		List<BigItemStack> wrappers = BigItemStack.duplicateWrappers(inputConfig);

		int width = Math.min(3, ingredients.size());
		int height = Math.min(3, ingredients.size() / 3 + 1);
		if (recipe instanceof ShapedRecipe shaped) {
			width = shaped.getWidth();
			height = shaped.getHeight();
		}
		if (height == 1) {
			for (int i = 0; i < 3; i++) result.add(empty);
		}
		if (width == 1) result.add(empty);

		for (int i = 0; i < ingredients.size(); i++) {
			Ingredient ingredient = ingredients.get(i);
			BigItemStack matched = empty;
			if (!ingredient.isEmpty()) {
				for (BigItemStack wrapper : wrappers) {
					if (wrapper.count > 0 && ingredient.test(wrapper.stack)) {
						matched = new BigItemStack(wrapper.stack, 1);
						if (testOnly) wrapper.count--;
						break;
					}
				}
			}
			result.add(matched);
			if (width < 3 && (i + 1) % width == 0) {
				for (int j = 0; j < 3 - width; j++) {
					if (result.size() < 9) result.add(empty);
				}
			}
		}
		while (result.size() < 9) result.add(empty);
		return result;
	}

	// ==================== 生命周期 ====================

	@Override
	protected void init() {
		int width = AllGuiTextures.FACTORY_GAUGE_BOTTOM.getWidth();
		AllGuiTextures contentTex = restocker ? AllGuiTextures.FACTORY_GAUGE_RESTOCK : AllGuiTextures.FACTORY_GAUGE_RECIPE;
		int baseHeight = contentTex.getHeight() + AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
		int playerInvHeight = AllGuiTextures.PLAYER_INVENTORY.getHeight();
		int windowHeight = baseHeight + 4 + playerInvHeight;
		setWindowSize(width, windowHeight);
		super.init();
		clearWidgets();

		int x = getGuiLeft();
		int y = getGuiTop();

		if (addressBox == null) {
			String frogAddress = behaviour.getFrogAddress();
			addressBox = new AddressEditBox(this, new NoShadowFontWrapper(font),
				x + 36, y + baseHeight - 51, 108, 10, false, frogAddress);
			addressBox.setValue(behaviour.recipeAddress);
			addressBox.setTextColor(0x555555);
		}
		addressBox.setX(x + 36);
		addressBox.setY(y + baseHeight - 51);
		addRenderableWidget(addressBox);

		confirmButton = new IconButton(x + width - 33, y + baseHeight - 25, AllIcons.I_CONFIRM);
		confirmButton.withCallback(this::onConfirm);
		confirmButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.save_and_close"));
		addRenderableWidget(confirmButton);

		deleteButton = new IconButton(x + width - 55, y + baseHeight - 25, AllIcons.I_TRASH);
		deleteButton.withCallback(this::onDelete);
		deleteButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.reset"));
		addRenderableWidget(deleteButton);

		promiseExpiration = new ScrollInput(x + 97, y + baseHeight - 24, 28, 16);
		promiseExpiration.withRange(-1, 31);
		promiseExpiration.titled(CreateLang.translateDirect("gui.factory_panel.promises_expire_title"));
		promiseExpiration.setState(behaviour.promiseClearingInterval);
		addRenderableWidget(promiseExpiration);

		if (!restocker) {
			newInputButton = new IconButton(x + 31, y + 47, AllIcons.I_ADD);
			newInputButton.withCallback(this::onNewInput);
			newInputButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.connect_input"));
			addRenderableWidget(newInputButton);

			relocateButton = new IconButton(x + 31, y + 67, AllIcons.I_MOVE_GAUGE);
			relocateButton.withCallback(this::onRelocate);
			relocateButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.relocate"));
			addRenderableWidget(relocateButton);
		}

		activateCraftingButton = null;
		if (availableCraftingRecipe != null) {
			activateCraftingButton = new IconButton(x + 31, y + 27, AllIcons.I_3x3);
			activateCraftingButton.withCallback(this::onActivateCrafting);
			activateCraftingButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.activate_crafting"));
			addRenderableWidget(activateCraftingButton);
		}
	}

	@Override
	public void containerTick() {
		super.containerTick();
		if (connections.size() != behaviour.targetedBy.size()) {
			updateConfigs();
			if (!craftingActive)
				rebuildGhostInventory();
			init();
		}
		if (activateCraftingButton != null) {
			activateCraftingButton.green = craftingActive;
		}
		if (addressBox != null) addressBox.tick();
		if (promiseExpiration != null) {
			promiseExpiration.titled(
				promiseExpiration.getState() == -1
					? CreateLang.translateDirect("gui.factory_panel.promises_do_not_expire")
					: CreateLang.translateDirect("gui.factory_panel.promises_expire_title"));
		}
	}

	// ==================== 渲染 ====================

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
		int x = getGuiLeft();
		int y = getGuiTop();

		AllGuiTextures contentTex = restocker
			? AllGuiTextures.FACTORY_GAUGE_RESTOCK
			: AllGuiTextures.FACTORY_GAUGE_RECIPE;

		if (restocker) {
			AllGuiTextures.FACTORY_GAUGE_RECIPE.render(graphics, x, y - 16);
		}

		contentTex.render(graphics, x, y);

		int baseHeight = contentTex.getHeight() + AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
		AllGuiTextures.FACTORY_GAUGE_BOTTOM.render(graphics, x, y + contentTex.getHeight());

		renderPlayerInventory(graphics, x + 8, y + baseHeight + 4);

		Component title = CreateLang.translateDirect(
			restocker ? "gui.factory_panel.title_as_restocker" : "gui.factory_panel.title_as_recipe");
		graphics.drawString(font, title,
			x + 97 - font.width(title) / 2,
			y + (restocker ? -12 : 4),
			0x3D3D3D, false);

		int previewY = restocker ? 0 : 55;
		graphics.pose().pushPose();
		graphics.pose().translate(0, previewY, 0);
		GuiGameElement.of(AllBlocks.FACTORY_GAUGE.asStack())
			.scale(4.0d)
			.at(0, 0, -200)
			.render(graphics, x + 195, y + 55);

		if (!behaviour.getFilter().isEmpty()) {
			GuiGameElement.of(behaviour.getFilter())
				.scale(1.625d)
				.at(0, 0, 100)
				.render(graphics, x + 214, y + 68);
		}
		graphics.pose().popPose();

		if (!restocker) {
			renderOutputItem(graphics, outputConfig, mouseX, mouseY);
		}

		renderInputGrid(graphics, mouseX, mouseY);

		if (!behaviour.targetedByLinks.isEmpty()) {
			int linkX = x + 9;
			int linkY = y + baseHeight - 24;
			AllGuiTextures.FROGPORT_SLOT.render(graphics, linkX - 1, linkY - 1);
			graphics.renderItem(AllBlocks.REDSTONE_LINK.asStack(), linkX, linkY);
		}

		int boxX = x + 68;
		int boxY = y + baseHeight - 24;
		ItemStack defaultBox = PackageStyles.getDefaultBox();
		graphics.renderItem(defaultBox, boxX, boxY);
		graphics.renderItemDecorations(font, defaultBox, boxX, boxY,
			String.valueOf(behaviour.getPromised()));

		int state = promiseExpiration.getState();
		graphics.drawString(font,
			Component.literal(state == -1 ? " /" : state == 0 ? "30s" : state + "m"),
			promiseExpiration.getX() + 3, promiseExpiration.getY() + 4,
			0xffeeeeee, true);
	}

	// ==================== 物品格子渲染 ====================

	/**
	 * 渲染 3×3 输入格子。
	 * 非合成模式：物品由 GhostItemMenu 的 SlotItemHandler 自动渲染，此处只渲染合成配料或空提示。
	 * 合成模式：手动渲染 craftingIngredients。
	 */
	private void renderInputGrid(GuiGraphics graphics, int mouseX, int mouseY) {
		int slot = 0;

		if (craftingActive) {
			for (BigItemStack itemStack : craftingIngredients)
				renderInputItem(graphics, slot++, itemStack, mouseX, mouseY);
		} else if (!restocker) {
			// Ghost slots auto-render items. Render tooltip for empty slots.
			if (connections.isEmpty()) {
				int inputX = getGuiLeft() + 68;
				int inputY = getGuiTop() + 28;
				if (mouseY > inputY && mouseY < inputY + 60 && mouseX > inputX && mouseX < inputX + 60)
					graphics.renderComponentTooltip(font,
						List.of(CreateLang.translateDirect("gui.factory_panel.unconfigured_input")
							.withStyle(ChatFormatting.GRAY),
							CreateLang.translateDirect("gui.factory_panel.unconfigured_input_tip")
								.withStyle(ChatFormatting.GRAY),
							CreateLang.translateDirect("gui.factory_panel.unconfigured_input_tip_1")
								.withStyle(ChatFormatting.GRAY)),
						mouseX, mouseY);
			}
		}

		if (restocker)
			renderInputItem(graphics, slot, new BigItemStack(behaviour.getFilter(), 1), mouseX, mouseY);
	}

	private void renderInputItem(GuiGraphics graphics, int slot, BigItemStack itemStack, int mouseX, int mouseY) {
		int inputX = getGuiLeft() + (restocker ? 88 : 68 + (slot % 3 * 20));
		int inputY = getGuiTop() + (restocker ? 12 : 28) + (slot / 3 * 20);

		graphics.renderItem(itemStack.stack, inputX, inputY);
		if (craftingActive && !itemStack.stack.isEmpty())
			graphics.renderItemDecorations(font, itemStack.stack, inputX, inputY, itemStack.count + "");

		if (mouseX < inputX - 2 || mouseX >= inputX - 2 + 20 || mouseY < inputY - 2 || mouseY >= inputY - 2 + 20)
			return;

		if (craftingActive) {
			graphics.renderComponentTooltip(font, List.of(
				CreateLang.translateDirect("gui.factory_panel.crafting_input"),
				CreateLang.translateDirect("gui.factory_panel.crafting_input_tip")
					.withStyle(ChatFormatting.GRAY),
				CreateLang.translateDirect("gui.factory_panel.crafting_input_tip_1")
					.withStyle(ChatFormatting.GRAY)),
				mouseX, mouseY);
			return;
		}

		if (itemStack.stack.isEmpty()) {
			graphics.renderComponentTooltip(font, List.of(
				CreateLang.translateDirect("gui.factory_panel.empty_panel"),
				CreateLang.translateDirect("gui.factory_panel.left_click_disconnect")
					.withStyle(ChatFormatting.DARK_GRAY)
					.withStyle(ChatFormatting.ITALIC)),
				mouseX, mouseY);
			return;
		}

		if (restocker) {
			graphics.renderComponentTooltip(font, List.of(
				CreateLang.translateDirect("gui.factory_panel.sending_item",
					itemStack.stack.getHoverName().getString()),
				CreateLang.translateDirect("gui.factory_panel.sending_item_tip")
					.withStyle(ChatFormatting.GRAY),
				CreateLang.translateDirect("gui.factory_panel.sending_item_tip_1")
					.withStyle(ChatFormatting.GRAY)),
				mouseX, mouseY);
		}
	}

	/**
	 * 渲染每格请求数量的角标（类似红石请求器 renderForeground）。
	 */
	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		super.renderForeground(graphics, mouseX, mouseY, partialTicks);

		if (craftingActive || restocker)
			return;

		int x = getGuiLeft();
		int y = getGuiTop();

		for (int i = 0; i < 9; i++) {
			ItemStack stack = menu.ghostInventory.getStackInSlot(i);
			if (stack.isEmpty())
				continue;
			int slotX = x + 68 + (i % 3 * 20);
			int slotY = y + 28 + (i / 3 * 20);
			int amt = stack.getCount();
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 100);
			graphics.renderItemDecorations(font, stack, slotX, slotY, "" + amt);
			graphics.pose().popPose();
		}
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		if (!(hoveredSlot instanceof SlotItemHandler) || craftingActive || restocker)
			return super.getTooltipFromContainerItem(stack);

		int slotIndex = hoveredSlot.getSlotIndex();
		if (slotIndex >= 9)
			return super.getTooltipFromContainerItem(stack);

		int amt = stack.getCount();
		if (stack.isEmpty())
			return super.getTooltipFromContainerItem(stack);

		return List.of(
			CreateLang.translateDirect("gui.factory_panel.sending_item",
				stack.getHoverName().getString() + " x" + amt),
			CreateLang.translateDirect("gui.factory_panel.scroll_to_change_amount")
				.withStyle(ChatFormatting.DARK_GRAY)
				.withStyle(ChatFormatting.ITALIC),
			CreateLang.translateDirect("gui.factory_panel.left_click_disconnect")
				.withStyle(ChatFormatting.DARK_GRAY)
				.withStyle(ChatFormatting.ITALIC));
	}

	private void renderOutputItem(GuiGraphics graphics, BigItemStack bigStack, int mouseX, int mouseY) {
		int outputX = getGuiLeft() + 160;
		int outputY = getGuiTop() + 48;
		graphics.renderItem(outputConfig.stack, outputX, outputY);
		graphics.renderItemDecorations(font, behaviour.getFilter(), outputX, outputY, outputConfig.count + "");

		if (mouseX >= outputX - 1 && mouseX < outputX - 1 + 18 && mouseY >= outputY - 1
			&& mouseY < outputY - 1 + 18) {
			MutableComponent c1 = CreateLang.translateDirect(
				"gui.factory_panel.expected_output",
				CreateLang.itemName(outputConfig.stack)
					.add(CreateLang.text(" x" + outputConfig.count))
					.string());
			MutableComponent c2 = CreateLang.translateDirect("gui.factory_panel.expected_output_tip")
				.withStyle(ChatFormatting.GRAY);
			MutableComponent c3 = CreateLang.translateDirect("gui.factory_panel.expected_output_tip_1")
				.withStyle(ChatFormatting.GRAY);
			MutableComponent c4 = CreateLang.translateDirect("gui.factory_panel.expected_output_tip_2")
				.withStyle(ChatFormatting.DARK_GRAY)
				.withStyle(ChatFormatting.ITALIC);
			graphics.renderComponentTooltip(font,
				craftingActive ? List.of(c1, c2, c3) : List.of(c1, c2, c3, c4),
				mouseX, mouseY);
		}
	}

	// ==================== 鼠标交互 ====================

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int pButton) {
		if (getFocused() != null && !getFocused().isMouseOver(mouseX, mouseY))
			setFocused(null);

		int x = getGuiLeft();
		int y = getGuiTop();

		// Validate ghost slot placement: only linked items allowed
		if (!craftingActive && !restocker && !getMenu().getCarried().isEmpty()) {
			Slot slot = findSlot(mouseX, mouseY);
			if (slot != null && slot.index >= 36 && slot.index < 45) {
				if (!isLinkedItem(getMenu().getCarried())) {
					if (minecraft.player != null) {
						minecraft.player.displayClientMessage(
							CreateLang.translateDirect("gui.factory_panel.unlinked_item")
								.withStyle(ChatFormatting.RED),
							true);
						AllSoundEvents.DENY.playAt(minecraft.player.level(), minecraft.player.blockPosition(), 1, 1, false);
					}
					return true;
				}
			}
		}

		// Block ghost slot interactions in crafting or restocker mode
		if (craftingActive || restocker) {
			Slot slot = findSlot(mouseX, mouseY);
			if (slot != null && slot.index >= 36 && slot.index < 45)
				return true;
		}

		// Left-click on LAST instance of item in ghost slot: disconnect the gauge
		if (!craftingActive && !restocker && pButton == 0 && getMenu().getCarried().isEmpty()) {
			Slot slot = findSlot(mouseX, mouseY);
			if (slot != null && slot.index >= 36 && slot.index < 45) {
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
						FactoryPanelPosition toRemove = findConnectionForItem(clickedItem);
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

		// Right-click on ghost slot to disconnect the matching connection
		if (!craftingActive && !restocker && pButton == 1) {
			Slot slot = findSlot(mouseX, mouseY);
			if (slot != null && slot.index >= 36 && slot.index < 45) {
				int ghostIdx = slot.index - 36;
				ItemStack clickedItem = menu.ghostInventory.getStackInSlot(ghostIdx);
				if (!clickedItem.isEmpty()) {
					FactoryPanelPosition toRemove = findConnectionForItem(clickedItem);
					if (toRemove != null) {
						menu.ghostInventory.setStackInSlot(ghostIdx, ItemStack.EMPTY);
						sendIt(toRemove, false);
						playButtonSound();
					}
				}
				return true;
			}
		}

		// Promise clear (bottom bar box area)
		AllGuiTextures ct = restocker ? AllGuiTextures.FACTORY_GAUGE_RESTOCK : AllGuiTextures.FACTORY_GAUGE_RECIPE;
		int gaugeBottom = ct.getHeight() + AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
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

		if (craftingActive)
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

		if (!restocker) {
			for (int i = 0; i < 9; i++) {
				int inputX = x + 68 + (i % 3 * 20);
				int inputY = y + 28 + (i / 3 * 20);
				if (mouseX >= inputX && mouseX < inputX + 16 && mouseY >= inputY && mouseY < inputY + 16) {
					ItemStack slotItem = menu.ghostInventory.getStackInSlot(i);
					if (slotItem.isEmpty())
						return true;
					int delta = (int) Math.signum(scrollY) * (hasShiftDown() ? 10 : 1);
					slotItem.setCount(Mth.clamp(slotItem.getCount() + delta, 1, 64));
					return true;
				}
			}
		}

		if (!restocker) {
			int outputX = x + 160;
			int outputY = y + 48;
			if (mouseX >= outputX && mouseX < outputX + 16 && mouseY >= outputY && mouseY < outputY + 16) {
				BigItemStack itemStack = outputConfig;
				itemStack.count =
					Mth.clamp((int) (itemStack.count + Math.signum(scrollY) * (hasShiftDown() ? 10 : 1)), 1, 64);
				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ==================== 键盘输入 ====================

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

	// ==================== 关闭与网络发包 ====================

	@Override
	public void removed() {
		sendIt(null, false);
		super.removed();
	}

	private void playButtonSound() {
		Minecraft.getInstance().getSoundManager()
			.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
	}

	/**
	 * 构建并发送配置数据包到服务器。
	 */
	private void sendIt(FactoryPanelPosition removePos, boolean clearPromises) {
		Map<FactoryPanelPosition, Integer> inputAmounts = new HashMap<>();

		if (!craftingActive && !restocker) {
			// Aggregate ghost slot amounts per connection (free-form grid)
			for (int c = 0; c < connections.size(); c++) {
				BigItemStack cfg = inputConfig.get(c);
				int total = 0;
				if (!cfg.stack.isEmpty()) {
					for (int s = 0; s < 9; s++) {
						ItemStack ghostItem = menu.ghostInventory.getStackInSlot(s);
						if (!ghostItem.isEmpty() && ItemStack.isSameItemSameComponents(ghostItem, cfg.stack))
							total += ghostItem.getCount();
					}
				}
				inputAmounts.put(connections.get(c).from, total);
			}
		} else if (craftingActive) {
			for (int i = 0; i < connections.size(); i++) {
				BigItemStack stackInConfig = inputConfig.get(i);
				inputAmounts.put(connections.get(i).from, (int) craftingIngredients.stream()
					.filter(ci -> !ci.stack.isEmpty()
						&& ItemStack.isSameItemSameComponents(ci.stack, stackInConfig.stack))
					.count());
			}
		} else {
			ItemStack restockStack = menu.ghostInventory.getStackInSlot(0);
			if (!restockStack.isEmpty() && !connections.isEmpty())
				inputAmounts.put(connections.get(0).from, restockStack.getCount());
		}

		List<ItemStack> craftingArrangement = craftingActive
			? craftingIngredients.stream().map(bis -> bis.stack).toList()
			: List.of();

		FactoryPanelConfigurationPacket packet = new FactoryPanelConfigurationPacket(
			behaviour.getPanelPosition(),
			addressBox.getValue(),
			inputAmounts,
			craftingArrangement,
			outputConfig.count,
			promiseExpiration.getState(),
			removePos,
			clearPromises,
			sendReset,
			sendRedstoneReset
		);
		CatnipServices.NETWORK.sendToServer(packet);
	}

	// ==================== 按钮回调 ====================

	private void onConfirm() {
		String address = addressBox.getValue();
		int promiseInterval = promiseExpiration.getState();
		behaviour.recipeAddress = address;
		behaviour.promiseClearingInterval = promiseInterval;
		sendIt(null, false);
		onClose();
	}

	private void onDelete() {
		sendReset = true;
		if (!restocker) {
			for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
				menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
		}
		sendIt(null, false);
		onClose();
	}

	private void onNewInput() {
		FactoryPanelConnectionHandler.startConnection(behaviour);
		minecraft.setScreen(null);
	}

	private void onRelocate() {
		FactoryPanelConnectionHandler.startRelocating(behaviour);
		minecraft.setScreen(null);
	}

	private void onActivateCrafting() {
		craftingActive = !craftingActive;
		menu.craftingActive = craftingActive;
		if (craftingActive) {
			// Save current ghost inventory to ghost grid before clearing
			List<ItemStack> grid = new ArrayList<>();
			for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
				grid.add(menu.ghostInventory.getStackInSlot(i).copy());
			((GhostGridAccessor) behaviour).bfg$setGhostGrid(grid);
			((GhostGridAccessor) behaviour).bfg$setCraftingBackup(grid);

			for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
				menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
			if (availableCraftingRecipe != null) {
				outputConfig.count = availableCraftingRecipe
					.getResultItem(minecraft.level.registryAccess())
					.getCount();
			}
		} else {
			rebuildGhostInventory();
		}
		init();
	}

	// ==================== 辅助方法 ====================

	/**
	 * 检查物品是否来自任一已连接工厂仪表的过滤器。即「已链接物品」判定。
	 */
	private boolean isLinkedItem(ItemStack carried) {
		if (carried.isEmpty())
			return false;
		for (FactoryPanelConnection conn : connections) {
			FactoryPanelBehaviour source = FactoryPanelBehaviour.at(minecraft.level, conn.from);
			if (source != null) {
				ItemStack filter = source.getFilter();
				if (!filter.isEmpty() && ItemStack.isSameItemSameComponents(filter, carried))
					return true;
			}
		}
		return false;
	}

	/**
	 * 根据幽灵槽中的物品，找到首个匹配的连接来源位置（用于断开连接）。
	 */
	private FactoryPanelPosition findConnectionForItem(ItemStack item) {
		for (FactoryPanelConnection conn : connections) {
			FactoryPanelBehaviour source = FactoryPanelBehaviour.at(minecraft.level, conn.from);
			if (source != null && ItemStack.isSameItemSameComponents(source.getFilter(), item))
				return conn.from;
		}
		return null;
	}

	/**
	 * 从连接列表重建幽灵物品栏（用量 1 每格）。
	 */
	private void rebuildGhostInventory() {
		if (restocker || craftingActive)
			return;

		for (int i = 0; i < menu.ghostInventory.getSlots(); i++)
			menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);

		// Restore from crafting backup first (isolated from NBT sync),
		// fall back to persisted ghost grid
		GhostGridAccessor accessor = (GhostGridAccessor) behaviour;
		List<ItemStack> saved = accessor.bfg$getCraftingBackup();
		boolean hasBackup = false;
		for (ItemStack s : saved) {
			if (!s.isEmpty()) { hasBackup = true; break; }
		}
		if (!hasBackup)
			saved = accessor.bfg$getGhostGrid();
		else
			accessor.bfg$setCraftingBackup(List.of()); // consumed

		for (int i = 0; i < Math.min(9, saved.size()); i++)
			menu.ghostInventory.setStackInSlot(i, saved.get(i).copy());

		// Clear items whose connections no longer exist
		for (int i = 0; i < 9; i++) {
			ItemStack s = menu.ghostInventory.getStackInSlot(i);
			if (!s.isEmpty() && !isLinkedItem(s))
				menu.ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
		}
	}

	/**
	 * 在给定坐标处查找槽位，返回 null 表示未命中任何槽位。
	 */
	private Slot findSlot(double mouseX, double mouseY) {
		for (Slot slot : menu.slots) {
			if (mouseX >= slot.x + getGuiLeft() && mouseX < slot.x + getGuiLeft() + 16
				&& mouseY >= slot.y + getGuiTop() && mouseY < slot.y + getGuiTop() + 16)
				return slot;
		}
		return null;
	}
}
