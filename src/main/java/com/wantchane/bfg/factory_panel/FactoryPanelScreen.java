package com.wantchane.bfg.factory_panel;

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
import net.createmod.catnip.gui.element.GuiGameElement;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.core.NonNullList;

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
    /** 输入物品配置列表，与 connections 一一对应 */
    private List<BigItemStack> inputConfig;
    /** 匹配到的合成配方，null 表示无匹配 */
    private CraftingRecipe availableCraftingRecipe;
    /** 合成是否激活 */
    private boolean craftingActive;
    /** 合成配料列表（激活合成时替换 inputGrid 显示） */
    private List<BigItemStack> craftingIngredients;

    public FactoryPanelScreen(FactoryPanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.behaviour = menu.contentHolder;
        this.restocker = behaviour.panelBE().restocker;
        this.craftingActive = !behaviour.activeCraftingArrangement.isEmpty();
        updateConfigs();
    }

    /**
     * 刷新全部配置数据。当连接数量变化时（targetedBy 大小改变），
     * {@link #containerTick()} 会检测到并调用此方法 + {@link #init()} 重建界面。
     */
    private void updateConfigs() {
        connections = new ArrayList<>(behaviour.targetedBy.values());
        outputConfig = new BigItemStack(behaviour.getFilter(), behaviour.recipeOutput);
        inputConfig = connections.stream()
            .map(conn -> {
                FactoryPanelBehaviour source = FactoryPanelBehaviour.at(behaviour.getWorld(), conn);
                if (source != null) {
                    ItemStack filter = source.getFilter();
                    int count = source.count;
                    return new BigItemStack(filter.isEmpty() ? ItemStack.EMPTY : filter, count);
                }
                return new BigItemStack(ItemStack.EMPTY, 0);
            })
            .toList();
        searchForCraftingRecipe();
        if (availableCraftingRecipe == null) {
            craftingActive = false;
        } else {
            craftingIngredients = convertRecipeToPackageOrderContext(availableCraftingRecipe, inputConfig, false);
        }
    }

    /**
     * 搜索匹配的合成配方。
     * 过滤条件：
     * <ol>
     *   <li>输出物品类型匹配（getItem() 相同）</li>
     *   <li>所有配方原料都能在输入配置中找到</li>
     *   <li>输入物品种类数量 >= 配方所需种类数量</li>
     *   <li>排除自动化中应忽略的配方（{@code AllRecipeTypes.shouldIgnoreInAutomation}）</li>
     * </ol>
     * 使用 parallelStream 提升搜索性能（与原版 Create 一致）。
     */
    private void searchForCraftingRecipe() {
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
     *
     * @param recipe      合成配方
     * @param inputConfig 输入物品配置（会被 testOnly 模式消耗计数）
     * @param testOnly    true=仅测试匹配不实际消耗，false=实际消耗计数
     * @return 9 个元素的列表（始终补齐到 3×3），空位用 EMPTY 填充
     */
    public static List<BigItemStack> convertRecipeToPackageOrderContext(
            CraftingRecipe recipe, List<BigItemStack> inputConfig, boolean testOnly) {
        List<BigItemStack> result = new ArrayList<>();
        BigItemStack empty = new BigItemStack(ItemStack.EMPTY, 1);
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        List<BigItemStack> wrappers = BigItemStack.duplicateWrappers(inputConfig);

        // 确定配方网格宽高（ShapedRecipe 从配方定义读取，ShapelessRecipe 自动推断）
        int width = Math.min(3, ingredients.size());
        int height = Math.min(3, ingredients.size() / 3 + 1);
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        }
        // 单行配方：顶部填充一行空位使其垂直居中
        if (height == 1) {
            for (int i = 0; i < 3; i++) result.add(empty);
        }
        // 单列配方：左侧填充一个空位使其水平居中
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
            // 配方宽度 < 3 时，每行末尾补空位
            if (width < 3 && (i + 1) % width == 0) {
                for (int j = 0; j < 3 - width; j++) {
                    if (result.size() < 9) result.add(empty);
                }
            }
        }
        // 补齐到 9 格
        while (result.size() < 9) result.add(empty);
        return result;
    }

    // ==================== 生命周期 ====================

    /**
     * 初始化界面 —— 创建所有控件并设置位置。
     *
     * <h3>窗口尺寸</h3>
     * 宽度 = 200（FACTORY_GAUGE_BOTTOM 宽度）。
     * 高度 = 仪表内容高度 + 玩家物品栏高度（无重叠）。
     * <pre>
     *   配方模式：160 + 108 = 268
     *   补货模式：104 + 108 = 212
     * </pre>
     *
     * <h3>控件位置速查表（配方模式，y 偏移量以 baseHeight=160 为基准计算）</h3>
     * <table>
     *   <tr><th>控件</th><th>X 公式</th><th>Y 公式</th><th>绝对 Y</th></tr>
     *   <tr><td>地址框</td><td>x + 36</td><td>y + baseHeight - 51</td><td>y + 109</td></tr>
     *   <tr><td>确认按钮</td><td>x + width - 33 (=167)</td><td>y + baseHeight - 25</td><td>y + 135</td></tr>
     *   <tr><td>删除按钮</td><td>x + width - 55 (=145)</td><td>y + baseHeight - 25</td><td>y + 135</td></tr>
     *   <tr><td>承诺过期</td><td>x + 97</td><td>y + baseHeight - 24</td><td>y + 136</td></tr>
     *   <tr><td>新增输入</td><td>x + 31</td><td>y + 47</td><td>y + 47</td></tr>
     *   <tr><td>重新定位</td><td>x + 31</td><td>y + 67</td><td>y + 67</td></tr>
     *   <tr><td>激活合成</td><td>x + 31</td><td>y + 27</td><td>y + 27</td></tr>
     * </table>
     */
    @Override
    protected void init() {
        // ---- 窗口尺寸 ----
        // width=200，跟随 FACTORY_GAUGE_BOTTOM 贴图宽度
        int width = AllGuiTextures.FACTORY_GAUGE_BOTTOM.getWidth();
        // 根据模式选择主贴图
        AllGuiTextures contentTex = restocker ? AllGuiTextures.FACTORY_GAUGE_RESTOCK : AllGuiTextures.FACTORY_GAUGE_RECIPE;
        // 仪表内容总高度 = 主贴图高 + 底部贴图高（配方 96+64=160，补货 40+64=104）
        int baseHeight = contentTex.getHeight() + AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
        // 玩家物品栏贴图高度，约 108px
        int playerInvHeight = AllGuiTextures.PLAYER_INVENTORY.getHeight();
        // 窗口总高度 = 仪表内容 + 间距 + 物品栏
        int windowHeight = baseHeight + 4 + playerInvHeight;
        setWindowSize(width, windowHeight);
        super.init();
        clearWidgets();

        int x = getGuiLeft();
        int y = getGuiTop();

        // ---- 地址输入框 ----
        // X: +36 —— 距窗口左边缘 36px
        // Y: +baseHeight-51 —— 距仪表内容底部向上 51px
        // 尺寸: 108×10 —— 宽 108px（约 10 个字符），高 10px
        if (addressBox == null) {
            String frogAddress = behaviour.getFrogAddress();
            addressBox = new AddressEditBox(this, new NoShadowFontWrapper(font),
                x + 36, y + baseHeight - 51, 108, 10, false, frogAddress);
            addressBox.setValue(behaviour.recipeAddress);
            addressBox.setTextColor(0x555555);  // 文字颜色 #555555
        }
        addressBox.setX(x + 36);
        addressBox.setY(y + baseHeight - 51);
        addRenderableWidget(addressBox);

        // ---- 确认按钮（右下角）----
        // X: width-33=167 —— 距窗口右边缘 33px
        // Y: baseHeight-25 —— 距仪表内容底部向上 25px
        confirmButton = new IconButton(x + width - 33, y + baseHeight - 25, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onConfirm);
        confirmButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.save_and_close"));
        addRenderableWidget(confirmButton);

        // ---- 删除按钮（确认按钮左侧 22px）----
        // X: width-55=145 —— 距窗口右边缘 55px（= 确认按钮 -22）
        // Y: baseHeight-25 —— 与确认按钮同高
        deleteButton = new IconButton(x + width - 55, y + baseHeight - 25, AllIcons.I_TRASH);
        deleteButton.withCallback(this::onDelete);
        deleteButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.reset"));
        addRenderableWidget(deleteButton);

        // ---- 承诺过期时间滚动选择器 ----
        // X: +97 —— 距窗口左边缘 97px
        // Y: baseHeight-24 —— 距仪表内容底部向上 24px
        // 尺寸: 28×16
        // 范围: -1（永不过期）到 31
        promiseExpiration = new ScrollInput(x + 97, y + baseHeight - 24, 28, 16);
        promiseExpiration.withRange(-1, 31);
        promiseExpiration.titled(CreateLang.translateDirect("gui.factory_panel.promises_expire_title"));
        promiseExpiration.setState(behaviour.promiseClearingInterval);
        addRenderableWidget(promiseExpiration);

        // ---- 以下控件仅配方模式显示 ----
        if (!restocker) {
            // 新增输入按钮 —— 左侧，距顶 47px
            newInputButton = new IconButton(x + 31, y + 47, AllIcons.I_ADD);
            newInputButton.withCallback(this::onNewInput);
            newInputButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.connect_input"));
            addRenderableWidget(newInputButton);

            // 重新定位按钮 —— 左侧，距顶 67px（新增按钮下方 20px）
            relocateButton = new IconButton(x + 31, y + 67, AllIcons.I_MOVE_GAUGE);
            relocateButton.withCallback(this::onRelocate);
            relocateButton.setToolTip(CreateLang.translateDirect("gui.factory_panel.relocate"));
            addRenderableWidget(relocateButton);
        }

        // 激活合成按钮 —— 只有检测到合成配方时才显示
        // X: +31, Y: 距顶 27px（新增按钮上方 20px）
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
        // 连接数量变化 → 刷新数据并重建界面
        if (inputConfig.size() != behaviour.targetedBy.size()) {
            updateConfigs();
            init();
        }
        // 合成激活时按钮高亮为绿色
        if (activateCraftingButton != null) {
            activateCraftingButton.green = craftingActive;
        }
        if (addressBox != null) addressBox.tick();
        // 动态更新承诺过期文本：-1 显示 "永不过期"，其余显示标题
        if (promiseExpiration != null) {
            promiseExpiration.titled(
                promiseExpiration.getState() == -1
                    ? CreateLang.translateDirect("gui.factory_panel.promises_do_not_expire")
                    : CreateLang.translateDirect("gui.factory_panel.promises_expire_title"));
        }
    }

    // ==================== 渲染 ====================

    /**
     * 渲染背景层 —— 绘制贴图、3D 模型、物品格子、玩家物品栏。
     *
     * <h3>渲染层次（从底到顶）</h3>
     * <ol>
     *   <li>补货模式配方标签叠加（y-16）</li>
     *   <li>主贴图（y+0）</li>
     *   <li>底部贴图（y+主贴图高）</li>
     *   <li>玩家物品栏（y+baseHeight）</li>
     *   <li>标题文本</li>
     *   <li>3D 工厂仪表模型（x+195, y+55）</li>
     *   <li>过滤器物品叠加（x+214, y+68）</li>
     *   <li>输出物品（配方模式，x+160, y+48）</li>
     *   <li>输入格子 3×3</li>
     *   <li>红石链路槽位（底部栏 x+9）</li>
     *   <li>包裹箱/承诺数量（底部栏 x+68）</li>
     *   <li>承诺过期数值文本</li>
     * </ol>
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = getGuiLeft();
        int y = getGuiTop();

        // 根据模式选择主贴图：配方=FACTORY_GAUGE_RECIPE(192×96)，补货=FACTORY_GAUGE_RESTOCK(192×40)
        AllGuiTextures contentTex = restocker
            ? AllGuiTextures.FACTORY_GAUGE_RESTOCK
            : AllGuiTextures.FACTORY_GAUGE_RECIPE;

        // --- 补货模式：在主贴图上方叠加配方标签贴图 ---
        // y-16：配方标签从主贴图上方 16px 开始渲染，形成"标签页"视觉效果
        if (restocker) {
            AllGuiTextures.FACTORY_GAUGE_RECIPE.render(graphics, x, y - 16);
        }

        // --- 主贴图 ---
        // 位置 (x+0, y+0)，左上角对齐窗口原点
        contentTex.render(graphics, x, y);

        // --- 底部贴图 ---
        // y + 主贴图高：紧接在主贴图下方
        // 配方模式：y+96；补货模式：y+40
        int baseHeight = contentTex.getHeight() + AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
        AllGuiTextures.FACTORY_GAUGE_BOTTOM.render(graphics, x, y + contentTex.getHeight());

        // --- 玩家物品栏 ---
        // Y 位置 = baseHeight + 4（仪表内容底部 + 4px 间距）
        // 贴图 X=8 是基准，槽位 addPlayerSlots X=16 含贴图内部 8px 左填充
        renderPlayerInventory(graphics, x + 8, y + baseHeight + 4);

        // --- 标题文本 ---
        // X: 97 是标题中心点 X 坐标（大致在窗口中心偏左）
        // 减去 font.width/2 实现文字水平居中
        // Y: 配方模式 +4（主贴图顶部附近），补货模式 -12（在重叠的配方标签上方）
        // 颜色 0x3D3D3D 是深灰色
        Component title = CreateLang.translateDirect(
            restocker ? "gui.factory_panel.title_as_restocker" : "gui.factory_panel.title_as_recipe");
        graphics.drawString(font, title,
            x + 97 - font.width(title) / 2,
            y + (restocker ? -12 : 4),
            0x3D3D3D, false);

        // --- 3D 工厂仪表方块模型（右侧图标） ---
        // 位置 (x+195, y+55)：窗口宽 200，195 表示在右边缘内侧 5px
        // scale(4.0)：4 倍缩放渲染
        // at(0,0,-200)：深度偏移 -200，渲染在远处作为背景层
        GuiGameElement.of(AllBlocks.FACTORY_GAUGE.asStack())
            .scale(4.0d)
            .at(0, 0, -200)
            .render(graphics, x + 195, y + 55);

        // --- 过滤器物品叠加（显示在 3D 仪表模型上方） ---
        // 位置 (x+214, y+68)：模型右下方
        // scale(1.625)：约 1.6 倍缩放，比模型小但清晰可见
        // at(0,0,100)：正 Z 深度，渲染在模型前方
        if (!behaviour.getFilter().isEmpty()) {
            GuiGameElement.of(behaviour.getFilter())
                .scale(1.625d)
                .at(0, 0, 100)
                .render(graphics, x + 214, y + 68);
        }

        // --- 输出物品（仅配方模式） ---
        if (!restocker) {
            renderOutputItem(graphics, outputConfig, x, y);
        }

        // --- 输入物品 3×3 格子（或合成配料） ---
        renderInputGrid(graphics, mouseX, mouseY);

        // --- 红石链路槽位（底部栏左下角） ---
        // X: +9 —— 距左边缘 9px
        // Y: baseHeight-24 —— 距仪表内容底部向上 24px，在底部贴图区域内
        if (!behaviour.targetedByLinks.isEmpty()) {
            int linkX = x + 9;
            int linkY = y + baseHeight - 24;
            AllGuiTextures.FROGPORT_SLOT.render(graphics, linkX - 1, linkY - 1);
            graphics.renderItem(AllBlocks.REDSTONE_LINK.asStack(), linkX, linkY);
        }

        // --- 包裹箱 / 承诺数量（底部栏，地址框下方） ---
        // X: +68 —— 距左边缘 68px
        // Y: baseHeight-24 —— 与红石槽同水平线
        int boxX = x + 68;
        int boxY = y + baseHeight - 24;
        ItemStack defaultBox = PackageStyles.getDefaultBox();
        graphics.renderItem(defaultBox, boxX, boxY);
        // 装饰文本显示已承诺数量
        graphics.renderItemDecorations(font, defaultBox, boxX, boxY,
            String.valueOf(behaviour.getPromised()));

        // --- 承诺过期数值文本 ---
        // 跟随 promiseExpiration 组件位置
        // +3, +4 是组件内部文字偏移（左上角留白）
        // 颜色 0xEEEEEE 是浅灰白色
        graphics.drawString(font,
            String.valueOf(promiseExpiration.getState()),
            promiseExpiration.getX() + 3, promiseExpiration.getY() + 4,
            0xEEEEEE, false);
    }

    // ==================== 物品格子渲染 ====================

    /**
     * 渲染 3×3 输入格子。
     *
     * <h3>格子坐标公式</h3>
     * <pre>
     *   gx = x + 起始X + (列号 × 20)
     *   gy = y + 起始Y + (行号 × 20)
     * </pre>
     * <table>
     *   <tr><th>参数</th><th>配方模式</th><th>补货模式</th></tr>
     *   <tr><td>起始 X</td><td>68</td><td>88</td></tr>
     *   <tr><td>起始 Y</td><td>28</td><td>12</td></tr>
     *   <tr><td>列间距</td><td>20</td><td>20</td></tr>
     *   <tr><td>行间距</td><td>20</td><td>20</td></tr>
     * </table>
     *
     * <h3>配方模式 9 格坐标</h3>
     * <pre>
     *   (68,28)  (88,28)  (108,28)
     *   (68,48)  (88,48)  (108,48)
     *   (68,68)  (88,68)  (108,68)
     * </pre>
     */
    private void renderInputGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = getGuiLeft();
        int y = getGuiTop();
        int idx = 0;

        if (craftingActive) {
            for (BigItemStack ingredient : craftingIngredients) {
                renderInputItem(graphics, idx++, ingredient, mouseX, mouseY);
            }
        } else {
            for (BigItemStack input : inputConfig) {
                renderInputItem(graphics, idx++, input, mouseX, mouseY);
            }
            if (inputConfig.isEmpty()) {
                // 无输入连接时不渲染任何背景（贴图自身已有槽位背景）
            }
        }
    }

    /**
     * 渲染单个输入物品格子。
     *
     * <h3>格子内部布局</h3>
     * 格子尺寸 18×18，物品图标 16×16：
     * <ul>
     *   <li>物品渲染位置：gx+2, gy+2（2px 偏移使 16×16 图标在 18×18 格子内居中）</li>
     *   <li>数量文本：渲染在物品右下角（ItemStack 标准装饰渲染）</li>
     *   <li>鼠标悬停检测：gx ~ gx+18, gy ~ gy+18（整个格子区域）</li>
     *   <li>悬停效果：渲染高亮边框 + 物品提示文本（tooltip）</li>
     * </ul>
     */
    private void renderInputItem(GuiGraphics graphics, int idx, BigItemStack bigStack, int mouseX, int mouseY) {
        int x = getGuiLeft();
        int y = getGuiTop();
        // 列号=idx%3, 行号=idx/3
        // 配方模式起始 (68,28)，补货模式起始 (88,12)
        int gx = x + (restocker ? 88 + (idx % 3) * 20 : 68 + (idx % 3) * 20);
        int gy = y + (restocker ? 12 + (idx / 3) * 20 : 28 + (idx / 3) * 20);

        if (bigStack != null && !bigStack.stack.isEmpty()) {
            graphics.renderItem(bigStack.stack, gx, gy);
            graphics.renderItemDecorations(font, bigStack.stack, gx, gy,
                bigStack.stack.getCount() > 1 ? String.valueOf(bigStack.count) : null);
        }
        // 鼠标悬停检测区域 = 整个 18×18 格子
        if (mouseX >= gx && mouseX < gx + 18 && mouseY >= gy && mouseY < gy + 18) {
            renderSlotHighlight(graphics, gx, gy, 0);
            if (bigStack != null && !bigStack.stack.isEmpty()) {
                graphics.renderTooltip(font, bigStack.stack, mouseX, mouseY);
            }
        }
    }

    /**
     * 渲染输出物品（仅配方模式）。
     * 位置：(x+160, y+48)，在 3×3 格子区域右侧。
     */
    private void renderOutputItem(GuiGraphics graphics, BigItemStack bigStack, int x, int y) {
        // 输出物品位置：距左 160px，距顶 48px
        int ox = x + 160;
        int oy = y + 48;
        if (bigStack != null && !bigStack.stack.isEmpty()) {
            graphics.renderItem(bigStack.stack, ox, oy);
            graphics.renderItemDecorations(font, bigStack.stack, ox, oy,
                bigStack.count > 1 ? String.valueOf(bigStack.count) : null);
        }
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 无前景内容，前景由控件系统处理
    }

    // ==================== 鼠标交互 ====================

    /**
     * 鼠标点击处理。
     *
     * <h3>点击判定区域</h3>
     * <b>重要：所有坐标公式必须与 {@link #renderInputItem}、{@link #renderOutputItem}
     * 中的渲染位置完全一致，否则会出现"点了没反应"或"点 A 触发 B"的错位。</b>
     *
     * <h3>点击行为</h3>
     * <ul>
     *   <li>输入格子（非合成模式）：断开该连接（sendIt 带 removePos）</li>
     *   <li>输出物品左半：数量 +1（范围 1-64）</li>
     *   <li>输出物品右半：数量 -1（范围 1-64）</li>
     *   <li>包裹箱区域：清除承诺（sendIt 带 clearPromises=true）</li>
     *   <li>红石链路槽位：发送红石重置</li>
     * </ul>
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = getGuiLeft();
        int y = getGuiTop();

        // --- 检查输入格子点击 ---
        // 遍历所有输入配置，检测鼠标是否在对应格子的 18×18 区域内
        for (int i = 0; i < inputConfig.size(); i++) {
            int gx = x + (restocker ? 88 : 68 + (i % 3) * 20);
            int gy = y + (restocker ? 12 : 28 + (i / 3) * 20);
            if (mx >= gx && mx < gx + 18 && my >= gy && my < gy + 18) {
                // 非合成模式下，点击输入格子 = 断开该连接
                if (!craftingActive && i < connections.size()) {
                    FactoryPanelPosition pos = connections.get(i).from;
                    if (pos != null) {
                        sendIt(pos, false);  // removePos=该连接位置, clearPromises=false
                        playButtonSound();
                        return true;
                    }
                }
            }
        }

        // --- 检查输出物品点击（仅配方模式） ---
        // 输出物品在 (x+160, y+48)，18×18 区域
        if (!restocker) {
            int ox = x + 160;
            int oy = y + 48;
            // 点击物品本身（18×18 区域）= 数量 +1
            if (mx >= ox && mx < ox + 18 && my >= oy && my < oy + 18) {
                outputConfig.count = Mth.clamp(outputConfig.count + 1, 1, 64);
                playButtonSound();
                return true;
            }
            // 点击物品右侧（ox+19 到 ox+37，也是 18px 宽）= 数量 -1
            // 这个区域虽然没有视觉元素，但作为减量热区
            if (mx >= ox + 19 && mx < ox + 37 && my >= oy && my < oy + 18) {
                outputConfig.count = Mth.clamp(outputConfig.count - 1, 1, 64);
                playButtonSound();
                return true;
            }
        }

        // --- 清除承诺按钮区域（包裹箱位置） ---
        // X: +68, Y: gaugeBottom-24, 16×16 检测区域
        AllGuiTextures ct = restocker ? AllGuiTextures.FACTORY_GAUGE_RESTOCK : AllGuiTextures.FACTORY_GAUGE_RECIPE;
        int gaugeBottom = ct.getHeight() + AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
        int clearX = x + 68;
        int clearY = y + gaugeBottom - 24;
        if (mx >= clearX && mx < clearX + 16 && my >= clearY && my < clearY + 16) {
            sendIt(null, true);  // removePos=null, clearPromises=true
            playButtonSound();
            return true;
        }

        // --- 红石重置区域（红石链路槽位） ---
        // X: +9, Y: gaugeBottom-24, 16×16 检测区域
        int rstX = x + 9;
        int rstY = y + gaugeBottom - 24;
        if (mx >= rstX && mx < rstX + 16 && my >= rstY && my < rstY + 16) {
            sendRedstoneReset = true;
            sendIt(null, false);
            playButtonSound();
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    /**
     * 鼠标滚轮处理。
     *
     * <h3>滚动行为</h3>
     * <ul>
     *   <li>输入格子：调整该输入物品的数量（Shift+滚轮=±10，普通滚轮=±1），范围 0-64</li>
     *   <li>输出物品：调整输出数量（Shift+滚轮=±10，普通滚轮=±1），范围 1-64</li>
     *   <li>合成激活时：滚轮不做自定义处理（交给父类）</li>
     * </ul>
     *
     * <h3>输出物品滚轮检测区域</h3>
     * {@code ox-2 到 ox+20, oy-2 到 oy+20}，比 18×18 格子稍大（含 2px 容差），
     * 使滚轮操作更加宽松易用。
     */
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int x = getGuiLeft();
        int y = getGuiTop();

        // 合成激活时禁用自定义滚轮行为
        if (craftingActive)
            return super.mouseScrolled(mx, my, scrollX, scrollY);

        // --- 滚动输入格子 ---
        // 遍历输入配置，找到鼠标所在的格子
        for (int i = 0; i < inputConfig.size(); i++) {
            int gx = x + (restocker ? 88 : 68 + (i % 3) * 20);
            int gy = y + (restocker ? 12 : 28 + (i / 3) * 20);
            if (mx >= gx && mx < gx + 18 && my >= gy && my < gy + 18) {
                BigItemStack bis = inputConfig.get(i);
                // signum(scrollY) 返回 -1（向下滚）或 +1（向上滚）
                // Shift 按下时步长为 10，否则为 1
                int delta = (int) Math.signum(scrollY) * (hasShiftDown() ? 10 : 1);
                bis.count = Mth.clamp(bis.count + delta, 0, 64);
                return true;
            }
        }

        // --- 滚动输出物品（仅配方模式） ---
        // 检测区域比 18×18 稍大（含 2px 容差边界），方便操作
        if (!restocker) {
            int ox = x + 160;
            int oy = y + 48;
            if (mx >= ox - 2 && mx < ox + 20 && my >= oy - 2 && my < oy + 20) {
                int delta = (int) Math.signum(scrollY) * (hasShiftDown() ? 10 : 1);
                outputConfig.count = Mth.clamp(outputConfig.count + delta, 1, 64);
                return true;
            }
        }

        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ==================== 键盘输入 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 地址框优先处理键盘事件（输入地址文本）
        if (addressBox.keyPressed(keyCode, scanCode, modifiers))
            return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 地址框优先处理字符输入
        if (addressBox.charTyped(codePoint, modifiers))
            return true;
        return super.charTyped(codePoint, modifiers);
    }

    // ==================== 关闭与网络发包 ====================

    /**
     * 屏幕移除时自动发送当前配置到服务器。
     * 确保关闭屏幕（ESC 或点击外部）时不会丢失修改。
     */
    @Override
    public void removed() {
        sendIt(null, false);
        super.removed();
    }

    /** 播放 UI 按钮点击音效（音量 1.0，音高 0.25）。 */
    private void playButtonSound() {
        Minecraft.getInstance().getSoundManager()
            .play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
    }

    /**
     * 构建并发送配置数据包到服务器。
     *
     * @param removePos     要断开的连接位置，{@code null} 表示不断开
     * @param clearPromises 是否清除所有承诺
     */
    private void sendIt(FactoryPanelPosition removePos, boolean clearPromises) {
        // 构建输入数量映射：每个连接位置 → 数量
        Map<FactoryPanelPosition, Integer> inputAmounts = new HashMap<>();
        if (inputConfig.size() == connections.size()) {
            for (int i = 0; i < inputConfig.size(); i++) {
                BigItemStack bis = inputConfig.get(i);
                FactoryPanelPosition pos = connections.get(i).from;
                // 合成模式下从 craftingIngredients 统计该物品出现次数
                int amount = craftingActive
                    ? (int) craftingIngredients.stream()
                        .filter(ci -> !ci.stack.isEmpty()
                            && ItemStack.isSameItemSameComponents(ci.stack, bis.stack))
                        .count()
                    : bis.count;
                inputAmounts.put(pos, amount);
            }
        }

        // 构建合成排列（合成模式下使用合成配料物品列表）
        List<ItemStack> craftingArrangement = craftingActive
            ? craftingIngredients.stream().map(bis -> bis.stack).toList()
            : List.of();

        FactoryPanelConfigurationPacket packet = new FactoryPanelConfigurationPacket(
            behaviour.getPanelPosition(),   // 面板位置
            addressBox.getValue(),          // 地址
            inputAmounts,                   // 输入数量映射
            craftingArrangement,            // 合成排列
            outputConfig.count,             // 输出数量
            promiseExpiration.getState(),   // 承诺过期时间
            removePos,                      // 要断开的连接（null=不断开）
            clearPromises,                  // 是否清除承诺
            sendReset,                      // 是否重置
            sendRedstoneReset               // 是否红石重置
        );
        CatnipServices.NETWORK.sendToServer(packet);
    }

    // ==================== 按钮回调 ====================

    /** 确认按钮：保存地址和承诺过期时间，发送配置，关闭屏幕。 */
    private void onConfirm() {
        String address = addressBox.getValue();
        int promiseInterval = promiseExpiration.getState();
        behaviour.recipeAddress = address;
        behaviour.promiseClearingInterval = promiseInterval;
        sendIt(null, false);
        onClose();
    }

    /** 删除按钮：设置重置标志，发送配置，关闭屏幕。 */
    private void onDelete() {
        sendReset = true;
        sendIt(null, false);
        onClose();
    }

    /**
     * 新增输入按钮：启动连接模式（通过 {@link FactoryPanelConnectionHandler}），
     * 关闭当前屏幕等待玩家选择目标面板。
     */
    private void onNewInput() {
        FactoryPanelConnectionHandler.startConnection(behaviour);
        minecraft.setScreen(null);
    }

    /**
     * 重新定位按钮：启动重定位模式（通过 {@link FactoryPanelConnectionHandler}），
     * 关闭当前屏幕等待玩家选择新位置。
     */
    private void onRelocate() {
        FactoryPanelConnectionHandler.startRelocating(behaviour);
        minecraft.setScreen(null);
    }

    /**
     * 激活合成按钮：切换合成模式。
     * 激活时自动将输出数量设置为配方产物的默认数量。
     */
    private void onActivateCrafting() {
        craftingActive = !craftingActive;
        if (craftingActive && availableCraftingRecipe != null) {
            outputConfig.count = availableCraftingRecipe
                .getResultItem(minecraft.level.registryAccess())
                .getCount();
        }
        init();
    }
}
