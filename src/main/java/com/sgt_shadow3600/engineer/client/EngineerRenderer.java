package com.sgt_shadow3600.engineer.client;

import com.sgt_shadow3600.engineer.EngineerCompanion;
import com.sgt_shadow3600.engineer.entity.EngineerCompanionEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class EngineerRenderer extends MobRenderer<EngineerCompanionEntity, PlayerModel<EngineerCompanionEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(EngineerCompanion.MODID, "textures/entity/engineer.png");

    public EngineerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);

        // Adds rendering for items held in the Main Hand and Offhand
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));

        // ARCHITECT FIX: Reverted to ModelManager.
        // EquipmentLayerRenderer is not introduced until 1.21.2!
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(EngineerCompanionEntity entity) {
        return TEXTURE;
    }
}