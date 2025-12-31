package com.utdmod.client.model;

import com.utdmod.UTDMod;
import com.utdmod.entity.TensionSerpentEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class TensionSerpentModel extends EntityModel<TensionSerpentEntity> {
    
    public static final EntityModelLayer LAYER_LOCATION = new EntityModelLayer(
        new Identifier(UTDMod.MOD_ID, "tension_serpent_model"), "main");

    private final ModelPart basePart;

    public TensionSerpentModel(ModelPart modelPart) {
        this.basePart = modelPart.getChild("root");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData modelPartData = modelData.getRoot();
        
        modelPartData.addChild("root", ModelPartBuilder.create().uv(0, 0).cuboid(-6.0F, -4.0F, -16.0F, 12.0F, 8.0F, 32.0F), 
            ModelTransform.pivot(0.0F, 20.0F, 0.0F));
        
        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void setAngles(TensionSerpentEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        // Animation logic would go here
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        basePart.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}
