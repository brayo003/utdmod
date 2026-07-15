package com.utdmod.research.player.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.ICustomGoalProcess;
import com.utdmod.research.player.ResearchPlayerEntityFactory;
import net.minecraft.entity.MovementType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Translates navigation intents into Baritone CustomGoalProcess commands.
 * Falls back to deterministic server-side walking when Baritone is unavailable.
 */
final class BaritoneNavigationAdapter {
  private static final double WALK_SPEED = 0.18;

  private final ServerWorld world;
  private final ServerPlayerEntity player;
  private BlockPos target = BlockPos.ORIGIN;
  private int stuckTicks;
  private BlockPos lastPos = BlockPos.ORIGIN;

  BaritoneNavigationAdapter(ServerWorld world, ServerPlayerEntity player) {
    this.world = world;
    this.player = player;
    this.lastPos = player.getBlockPos();
  }

  void setTarget(BlockPos target) {
    if (target != null && !target.equals(this.target)) {
      this.target = target;
      stuckTicks = 0;
      IBaritone baritone = resolveBaritone();
      if (baritone != null) {
        ICustomGoalProcess goal = baritone.getCustomGoalProcess();
        goal.setGoalAndPath(new GoalBlock(target));
      }
    }
  }

  BlockPos getTarget() {
    return target;
  }

  boolean tick() {
    IBaritone baritone = resolveBaritone();
    if (baritone != null && baritone.getCustomGoalProcess().isActive()) {
      return isAtTarget(2.5);
    }
    return tickServerNavigation();
  }

  boolean isAtTarget(double tolerance) {
    if (target == BlockPos.ORIGIN) {
      return true;
    }
    return ResearchPlayerEntityFactory.horizontalDistance(player, target) <= tolerance;
  }

  void face(BlockPos pos) {
    Vec3d eye = player.getEyePos();
    Vec3d targetVec = Vec3d.ofCenter(pos);
    Vec3d delta = targetVec.subtract(eye);
    double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0f;
    float pitch = (float) (-(MathHelper.atan2(delta.y, horiz) * (180.0 / Math.PI)));
    player.setYaw(yaw);
    player.setPitch(pitch);
    player.setHeadYaw(yaw);
  }

  Direction faceDirection(BlockPos from, BlockPos to) {
    int dx = Integer.compare(to.getX(), from.getX());
    int dy = Integer.compare(to.getY(), from.getY());
    int dz = Integer.compare(to.getZ(), from.getZ());
    if (Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz)) {
      return dy > 0 ? Direction.UP : Direction.DOWN;
    }
    if (Math.abs(dx) >= Math.abs(dz)) {
      return dx > 0 ? Direction.EAST : Direction.WEST;
    }
    return dz > 0 ? Direction.SOUTH : Direction.NORTH;
  }

  void stop() {
    target = BlockPos.ORIGIN;
    IBaritone baritone = resolveBaritone();
    if (baritone != null) {
      baritone.getCustomGoalProcess().onLostControl();
      baritone.getPathingBehavior().cancelEverything();
    }
  }

  boolean isActive() {
    if (target != BlockPos.ORIGIN) {
      return true;
    }
    IBaritone baritone = resolveBaritone();
    return baritone != null && baritone.getCustomGoalProcess().isActive();
  }

  private boolean tickServerNavigation() {
    if (target == BlockPos.ORIGIN) {
      return true;
    }

    Vec3d targetVec = new Vec3d(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
    Vec3d pos = player.getPos();
    Vec3d delta = targetVec.subtract(pos);
    double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

    if (horiz > 1e-4) {
      float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0 / Math.PI)) - 90.0f;
      player.setYaw(yaw);
      player.setHeadYaw(yaw);
      double step = Math.min(WALK_SPEED, horiz);
      double nx = delta.x / horiz * step;
      double nz = delta.z / horiz * step;
      double ny = 0.0;
      if (Math.abs(delta.y) > 0.5) {
        ny = MathHelper.clamp(delta.y, -0.2, 0.2);
      }
      player.move(MovementType.SELF, new Vec3d(nx, ny, nz));
    }

    BlockPos now = player.getBlockPos();
    if (now.equals(lastPos)) {
      stuckTicks++;
    } else {
      stuckTicks = 0;
      lastPos = now;
    }

    if (stuckTicks > 40) {
      BlockPos step = now.up();
      if (world.getBlockState(step).isAir() && !world.getBlockState(step.down()).isAir()) {
        player.move(MovementType.SELF, new Vec3d(0, 0.25, 0));
      } else {
        int dx = Integer.compare(target.getX(), now.getX());
        int dz = Integer.compare(target.getZ(), now.getZ());
        player.move(MovementType.SELF, new Vec3d(dx * 0.15, 0, dz * 0.15));
      }
      stuckTicks = 0;
    }
    return isAtTarget(2.5);
  }

  private static IBaritone resolveBaritone() {
    try {
      return BaritoneAPI.getProvider().getPrimaryBaritone();
    } catch (Throwable ignored) {
      return null;
    }
  }
}
