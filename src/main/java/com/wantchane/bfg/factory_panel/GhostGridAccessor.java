package com.wantchane.bfg.factory_panel;

import net.minecraft.world.item.ItemStack;
import java.util.List;

public interface GhostGridAccessor {
    List<ItemStack> bfg$getGhostGrid();
    void bfg$setGhostGrid(List<ItemStack> grid);
    List<ItemStack> bfg$getCraftingBackup();
    void bfg$setCraftingBackup(List<ItemStack> grid);
}
