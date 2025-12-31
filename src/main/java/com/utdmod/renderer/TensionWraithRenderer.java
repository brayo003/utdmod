package com.utdmod.renderer;

import com.utdmod.UTDMod;
import com.utdmod.entity.TensionWraithEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import com.utdmod.client.model.TensionWraithModel; // Placeholder for the model we will create next

// NOTE: The Model Class (TensionWraithModel) is currently missing and will cause a compiler error until defined.

public class TensionWraithRenderer extends MobEntityRenderer<TensionWraithEntity, TensionWraithModel> {

    // Define the primary texture file location
    private static final Identifier TEXTURE = new Identifier(UTDMod.MOD_ID, "textures/entity/tension_wraith.png");

    public TensionWraithRenderer(EntityRendererFactory.Context context) {
        // The Wraith is similar in size to a Vex/Phantom, using a small scale.
        // We reference the model class we will create next.
        super(context, new TensionWraithModel(context.getPart(TensionWraithModel.LAYER_LOCATION)), 0.4f);
    }

    @Override
    public Identifier getTexture(TensionWraithEntity entity) {
        // Wraiths typically use a single, static texture.
        return TEXTURE;
    }
}
