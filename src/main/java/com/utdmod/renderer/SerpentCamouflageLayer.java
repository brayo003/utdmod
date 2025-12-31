package com.utdmod.renderer;

import com.utdmod.entity.TensionSerpentEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;

public class SerpentCamouflageLayer extends FeatureRenderer<TensionSerpentEntity, EntityModel<TensionSerpentEntity>> {

    public SerpentCamouflageLayer(FeatureRendererContext<TensionSerpentEntity, EntityModel<TensionSerpentEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, 
                      TensionSerpentEntity entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        
        if (entity.isInvisible()) {
            VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(new Identifier("utdmod", "textures/entity/tension_serpent.png")));
            
            float alpha = 0.75f;
            this.getContextModel().render(matrices, vertexConsumer, light, 0, 1.0f, 1.0f, 1.0f, alpha);
        }
    }
}
