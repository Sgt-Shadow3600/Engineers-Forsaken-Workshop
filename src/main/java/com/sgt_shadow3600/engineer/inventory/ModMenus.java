package com.sgt_shadow3600.engineer.inventory;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, EngineerCompanion.MODID);

    // Uses NeoForge's standard IMenuTypeExtension to safely pass windowId, player inventory, and custom ByteBuf data.
    public static final DeferredHolder<MenuType<?>, MenuType<EngineerCompanionMenu>> COMPANION_MENU =
            MENUS.register("companion_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new EngineerCompanionMenu(windowId, inv, data)));

    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU =
            MENUS.register("terminal_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new TerminalMenu(windowId, inv, data)));
}