package com.wantchane.bfg.factory_panel;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import com.simibubi.create.foundation.utility.CreateLang;
import com.wantchane.bfg.BFGMenuTypes;
import com.wantchane.bfg.compat.CALCompatHelper;
import com.wantchane.bfg.network.SyncGhostGridPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class FactoryPanelMenu extends GhostItemMenu<FactoryPanelBehaviour> {

	public boolean craftingActive;
	private boolean restocker;

	public boolean isInteractionLocked() {
		return craftingActive || restocker;
	}

	public FactoryPanelMenu(MenuType<?> type, int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
		super(type, containerId, inv, buf);
		restocker = contentHolder.panelBE().restocker;
	}

	public FactoryPanelMenu(MenuType<?> type, int containerId, Inventory inv, FactoryPanelBehaviour behaviour) {
		super(type, containerId, inv, behaviour);
		restocker = behaviour.panelBE().restocker;
	}

	public static FactoryPanelMenu create(int containerId, Inventory inv, FactoryPanelBehaviour behaviour) {
		return new FactoryPanelMenu(BFGMenuTypes.FACTORY_PANEL.get(), containerId, inv, behaviour);
	}

	@Override
	protected FactoryPanelBehaviour createOnClient(RegistryFriendlyByteBuf buf) {
		FactoryPanelPosition pos = FactoryPanelPosition.STREAM_CODEC.decode(buf);
		ClientLevel level = Minecraft.getInstance().level;
		return FactoryPanelBehaviour.at(level, pos);
	}

	@Override
	protected ItemStackHandler createGhostInventory() {
		if (contentHolder.panelBE().restocker)
			return new ItemStackHandler(1);

		boolean[] loading = { true };

		ItemStackHandler inventory = new ItemStackHandler(9) {
			@Override
			public void setStackInSlot(int slot, ItemStack stack) {
				if (!stack.isEmpty() && (isInteractionLocked() || !isLinkedItem(stack))) {
					if (!loading[0] && contentHolder.getWorld().isClientSide()) {
						var player = Minecraft.getInstance().player;
						if (player != null) {
							player.displayClientMessage(
								CreateLang.translate("gui.factory_panel.unlinked_item").style(ChatFormatting.RED)
									.component(),
								true);
							AllSoundEvents.DENY.playAt(player.level(), player.blockPosition(), 1, 1, false);
						}
					}
					return;
				}
				super.setStackInSlot(slot, stack);
			}
		};

		// Load from persisted ghost grid first
		List<ItemStack> saved = ((GhostGridAccessor) contentHolder).bfg$getGhostGrid();
		boolean hasSaved = false;
		for (ItemStack s : saved) {
			if (!s.isEmpty()) { hasSaved = true; break; }
		}

		if (hasSaved) {
			for (int i = 0; i < Math.min(9, saved.size()); i++)
				inventory.setStackInSlot(i, saved.get(i).copy());

			clearUnlinkedItems(inventory);

			// Merge new connections not in ghost grid into empty slots
			for (FactoryPanelConnection conn : contentHolder.targetedBy.values()) {
				FactoryPanelBehaviour source = FactoryPanelBehaviour.at(contentHolder.getWorld(), conn.from);
				if (source == null)
					continue;
				ItemStack filter = source.getFilter();
				if (filter.isEmpty())
					continue;

				boolean alreadyInGrid = false;
				for (int i = 0; i < 9; i++) {
					ItemStack s = inventory.getStackInSlot(i);
					if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, filter)) {
						alreadyInGrid = true;
						break;
					}
				}
				if (alreadyInGrid)
					continue;

				for (int i = 0; i < 9; i++) {
					if (inventory.getStackInSlot(i).isEmpty()) {
						inventory.setStackInSlot(i, filter.copyWithCount(1));
						break;
					}
				}
			}
			loading[0] = false;
			return inventory;
		}

		// Fallback: derive from connections (1:1 mapping, count = connection amount)
		int slot = 0;
		for (FactoryPanelConnection conn : contentHolder.targetedBy.values()) {
			FactoryPanelBehaviour source = FactoryPanelBehaviour.at(contentHolder.getWorld(), conn.from);
			if (source != null) {
				ItemStack filter = source.getFilter();
				if (!filter.isEmpty())
					inventory.setStackInSlot(slot, filter.copyWithCount(conn.amount));
			}
			slot++;
			if (slot >= 9)
				break;
		}
		loading[0] = false;
		return inventory;
	}

	@Override
	protected boolean allowRepeats() {
		return true;
	}

	@Override
	protected void addSlots() {
		boolean restocker = contentHolder.panelBE().restocker;
		int baseHeight = restocker ? 104 : 160;
		int calGap = CALCompatHelper.getVerticalGap();
		addPlayerSlots(16, baseHeight + 18 + 4 + calGap);

		if (restocker) {
			addSlot(new SlotItemHandler(ghostInventory, 0, 88, 12) {
				@Override
				public boolean isActive() { return false; }
			});
		} else {
			for (int i = 0; i < 9; i++) {
				int col = i % 3;
				int row = i / 3;
				addSlot(new SlotItemHandler(ghostInventory, i, 68 + col * 20, 28 + row * 20) {
					@Override
					public boolean isActive() {
						return !craftingActive;
					}

					@Override
					public boolean mayPlace(ItemStack stack) {
						return !craftingActive && super.mayPlace(stack);
					}

					@Override
					public boolean mayPickup(Player player) {
						return !craftingActive && super.mayPickup(player);
					}
				});
			}
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return super.quickMoveStack(player, index);
	}

	public boolean isLinkedItem(ItemStack stack) {
		return findConnectionForItem(stack) != null;
	}

	public FactoryPanelPosition findConnectionForItem(ItemStack stack) {
		if (stack.isEmpty())
			return null;
		for (FactoryPanelConnection conn : contentHolder.targetedBy.values()) {
			FactoryPanelBehaviour source = FactoryPanelBehaviour.at(contentHolder.getWorld(), conn.from);
			if (source != null && ItemStack.isSameItemSameComponents(source.getFilter(), stack))
				return conn.from;
		}
		return null;
	}

	public void clearUnlinkedItems(ItemStackHandler inventory) {
		for (int i = 0; i < inventory.getSlots(); i++) {
			ItemStack s = inventory.getStackInSlot(i);
			if (!s.isEmpty() && !isLinkedItem(s))
				inventory.setStackInSlot(i, ItemStack.EMPTY);
		}
	}

	@Override
	public boolean canDragTo(Slot slotIn) {
		if (slotIn.index >= 36)
			return !getCarried().isEmpty();
		return super.canDragTo(slotIn);
	}

	@Override
	protected void saveData(FactoryPanelBehaviour behaviour) {
		if (this.restocker)
			return;

		List<ItemStack> grid;
		if (craftingActive) {
			grid = ((GhostGridAccessor) behaviour).bfg$getGhostGrid();
		} else {
			grid = new ArrayList<>();
			for (int i = 0; i < ghostInventory.getSlots(); i++)
				grid.add(ghostInventory.getStackInSlot(i).copy());
			while (grid.size() < 9)
				grid.add(ItemStack.EMPTY);
		}

		((GhostGridAccessor) behaviour).bfg$setGhostGrid(grid);
		if (behaviour.getWorld().isClientSide())
			PacketDistributor.sendToServer(new SyncGhostGridPayload(behaviour.getPanelPosition(), grid));
	}
}
