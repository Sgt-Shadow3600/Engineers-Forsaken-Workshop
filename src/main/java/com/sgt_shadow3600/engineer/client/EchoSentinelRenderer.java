package com.sgt_shadow3600.engineer.client;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ShulkerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Shulker;

public class EchoSentinelRenderer extends ShulkerRenderer {

    // Points to your custom texture file
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/entity/echo_sentinel.png");

    public EchoSentinelRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Shulker entity) {
        return TEXTURE;
    }
}