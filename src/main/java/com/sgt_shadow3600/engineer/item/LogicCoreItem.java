package com.sgt_shadow3600.engineer.item;

import net.minecraft.world.item.Item;

public class LogicCoreItem extends Item {
    public LogicCoreItem(Properties properties) {
        // Built-in vanilla properties to make it float in lava and not burn
        super(properties);
    }
}