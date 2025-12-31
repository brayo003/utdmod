package com.utdmod.client.visual;

import com.mojang.blaze3d.systems.RenderSystem;
import com.utdmod.entity.TensionSerpentEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.ParticleEffect;
import org.lwjgl.opengl.GL11;

public class TensionSerpentRenderer extends EntityRenderer<TensionSerpentEntity> {

    private static final float INVISIBILITY_FADE = 0.8f;
    private static final int TRAIL_PARTICLE_RATE = 2;
    private static final float SPAWN_PARTICLE_COUNT = 40;

    public TensionSerpentRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(TensionSerpentEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        createTrailEffect(entity);
        
        boolean isInvisible = entity.isInvisible();
        
        if (isInvisible) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.75F);
            RenderSystem.disableCull(); 
        }
        
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);

        if (isInvisible) {
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableCull();
        }
    }

    public static void createSpawnEffect(TensionSerpentEntity serpent) {
        if (serpent.getWorld().isClient()) return;
        
        Vec3d pos = serpent.getPos();
        
        for (int i = 0; i < SPAWN_PARTICLE_COUNT; i++) {
            double angle = Math.toRadians(i * 9);
            double radius = 1.5 + Math.random() * 1.0;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + Math.random() * 2.0;
            
            serpent.getWorld().addParticle(ParticleTypes.BUBBLE, x, y, z, 0, 0.1, 0);
        }

        for (int i = 0; i < 20; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = Math.random() * 3.0;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + Math.random() * 1.0;
            
            serpent.getWorld().addParticle(ParticleTypes.SQUID_INK, x, y, z, 0, -0.05, 0);
        }
    }

    private void createTrailEffect(TensionSerpentEntity entity) {
        if (entity.age % TRAIL_PARTICLE_RATE != 0) return;
        
        Vec3d pos = entity.getPos();
        Vec3d velocity = entity.getVelocity();
        
        entity.getWorld().addParticle(ParticleTypes.BUBBLE,
                                 pos.x, pos.y + 0.5, pos.z,
                                 -velocity.x * 0.1, 0.2, -velocity.z * 0.1);

        if (entity.getWorld().random.nextFloat() < 0.3f) {
            entity.getWorld().addParticle(ParticleTypes.SQUID_INK,
                                     pos.x + (Math.random() - 0.5) * 0.5,
                                     pos.y + Math.random() * 0.3,
                                     pos.z + (Math.random() - 0.5) * 0.5,
                                     0, 0, 0);
        }
    }

    public static void createAttackEffect(TensionSerpentEntity serpent, Vec3d targetPos) {
        if (serpent.getWorld().isClient()) return;
        
        for (int i = 0; i < 25; i++) {
            double angle = Math.toRadians(i * 14.4);
            double speed = 0.6 + Math.random() * 0.4;
            double x = targetPos.x + Math.cos(angle) * speed;
            double z = targetPos.z + Math.sin(angle) * speed;
            double y = targetPos.y + Math.random() * 0.8;
            
            serpent.getWorld().addParticle(ParticleTypes.BUBBLE, x, y, z,
                                     Math.cos(angle) * 0.2, 0.3, Math.sin(angle) * 0.2);
        }

        for (int i = 0; i < 15; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = Math.random() * 1.5;
            double x = targetPos.x + Math.cos(angle) * radius;
            double z = targetPos.z + Math.sin(angle) * radius;
            double y = targetPos.y + Math.random() * 0.5;
            
            serpent.getWorld().addParticle(ParticleTypes.DAMAGE_INDICATOR, x, y, z, 0, 0, 0);
        }
    }

    @Override
    public Identifier getTexture(TensionSerpentEntity entity) {
        if (entity.isInvisible()) {
            return new Identifier("utdmod", "textures/entity/tension_serpent_invisible.png");
        } else {
            return new Identifier("utdmod", "textures/entity/tension_serpent.png");
        }
    }
}
