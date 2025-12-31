package com.utdmod.renderer;

import com.utdmod.entity.TensionSerpentEntity;
import com.utdmod.client.model.TensionSerpentModel;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.util.Identifier;

public class TensionSerpentRenderer extends EntityRenderer<TensionSerpentEntity> {

    private final EntityModel<TensionSerpentEntity> model;

    public TensionSerpentRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new TensionSerpentModel(context.getPart(TensionSerpentModel.LAYER_LOCATION));
    }

    @Override
    public Identifier getTexture(TensionSerpentEntity entity) {
        return new Identifier("utdmod", "textures/entity/tension_serpent.png");
    }
}
