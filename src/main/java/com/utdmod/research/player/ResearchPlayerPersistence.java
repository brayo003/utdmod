package com.utdmod.research.player;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public class ResearchPlayerPersistence extends PersistentState {
  public static final Type<ResearchPlayerPersistence> TYPE = new Type<>(
      ResearchPlayerPersistence::new,
      ResearchPlayerPersistence::fromNbt,
      null);
  private final ResearchPlayerSnapshot snapshot = new ResearchPlayerSnapshot();

  public static ResearchPlayerPersistence get(ServerWorld world, java.util.UUID agentUuid) {
    String key = "utd_research_player_" + agentUuid.toString();
    return world.getPersistentStateManager().getOrCreate(TYPE, key);
  }

  public ResearchPlayerPersistence() {}

  public ResearchPlayerSnapshot snapshot() {
    return snapshot;
  }

  public void saveSnapshot(ResearchPlayerSnapshot source) {
    snapshot.state = source.state;
    snapshot.previousState = source.previousState;
    snapshot.resumeState = source.resumeState;
    snapshot.transitionReason = source.transitionReason;

    snapshot.agentId = source.agentId;
    snapshot.experimentId = source.experimentId;
    snapshot.protocolId = source.protocolId;
    snapshot.previousProtocolId = source.previousProtocolId;
    snapshot.durationTicks = source.durationTicks;
    snapshot.allowedRadiusChunks = source.allowedRadiusChunks;
    snapshot.loggingLabel = source.loggingLabel;
    snapshot.experimentSeed = source.experimentSeed;
    snapshot.miningOreId = source.miningOreId;
    snapshot.miningGoalCount = source.miningGoalCount;

    snapshot.botSeed = source.botSeed;
    snapshot.rngState = source.rngState;
    snapshot.stateTicks = source.stateTicks;
    snapshot.taskProgress = source.taskProgress;
    snapshot.explorationIndex = source.explorationIndex;
    snapshot.homePos = source.homePos;
    snapshot.targetPos = source.targetPos;
    snapshot.shelterOrigin = source.shelterOrigin;
    snapshot.shelterStep = source.shelterStep;
    snapshot.running = source.running;
    snapshot.paused = source.paused;
    snapshot.loggingEnabled = source.loggingEnabled;
    snapshot.spawned = source.spawned;
    markDirty();
  }

  @Override
  public NbtCompound writeNbt(NbtCompound nbt) {
    nbt.putString("state", snapshot.state.name());
    nbt.putString("previousState", snapshot.previousState.name());
    nbt.putString("resumeState", snapshot.resumeState.name());
    nbt.putString("transitionReason", snapshot.transitionReason);

    nbt.putString("agentId", snapshot.agentId);
    nbt.putString("experimentId", snapshot.experimentId);
    nbt.putString("protocolId", snapshot.protocolId);
    nbt.putString("previousProtocolId", snapshot.previousProtocolId);
    nbt.putInt("durationTicks", snapshot.durationTicks);
    nbt.putInt("allowedRadiusChunks", snapshot.allowedRadiusChunks);
    nbt.putString("loggingLabel", snapshot.loggingLabel);
    nbt.putInt("experimentSeed", snapshot.experimentSeed);
    nbt.putString("miningOreId", snapshot.miningOreId);
    nbt.putInt("miningGoalCount", snapshot.miningGoalCount);

    nbt.putInt("botSeed", snapshot.botSeed);
    nbt.putLong("rngState", snapshot.rngState);
    nbt.putInt("stateTicks", snapshot.stateTicks);
    nbt.putInt("taskProgress", snapshot.taskProgress);
    nbt.putInt("explorationIndex", snapshot.explorationIndex);
    nbt.putInt("shelterStep", snapshot.shelterStep);
    nbt.putBoolean("running", snapshot.running);
    nbt.putBoolean("paused", snapshot.paused);
    nbt.putBoolean("loggingEnabled", snapshot.loggingEnabled);
    nbt.putBoolean("spawned", snapshot.spawned);
    writeBlockPos(nbt, "home", snapshot.homePos);
    writeBlockPos(nbt, "target", snapshot.targetPos);
    writeBlockPos(nbt, "shelter", snapshot.shelterOrigin);
    return nbt;
  }

  public static ResearchPlayerPersistence fromNbt(NbtCompound nbt) {
    ResearchPlayerPersistence state = new ResearchPlayerPersistence();
    ResearchPlayerSnapshot s = state.snapshot;
    s.state = ResearchPlayerState.parse(nbt.getString("state"));
    if (s.state == null) s.state = ResearchPlayerState.IDLE;
    s.previousState = ResearchPlayerState.parse(nbt.getString("previousState"));
    if (s.previousState == null) s.previousState = ResearchPlayerState.IDLE;
    s.resumeState = ResearchPlayerState.parse(nbt.getString("resumeState"));
    if (s.resumeState == null) s.resumeState = ResearchPlayerState.SURVEY;
    s.transitionReason = nbt.getString("transitionReason");

    s.agentId = nbt.getString("agentId");
    if (s.agentId == null || s.agentId.isBlank()) s.agentId = "agent0";
    s.experimentId = nbt.getString("experimentId");
    if (s.experimentId == null || s.experimentId.isBlank()) s.experimentId = "default_experiment";
    s.protocolId = nbt.getString("protocolId");
    if (s.protocolId == null || s.protocolId.isBlank()) s.protocolId = "PASSIVE_WANDER";
    s.previousProtocolId = nbt.getString("previousProtocolId");
    if (s.previousProtocolId == null || s.previousProtocolId.isBlank()) s.previousProtocolId = s.protocolId;
    s.durationTicks = nbt.getInt("durationTicks");
    if (s.durationTicks <= 0) s.durationTicks = 2000;
    s.allowedRadiusChunks = nbt.getInt("allowedRadiusChunks");
    if (s.allowedRadiusChunks <= 0) s.allowedRadiusChunks = 8;
    s.loggingLabel = nbt.getString("loggingLabel");
    if (s.loggingLabel == null) s.loggingLabel = "";
    s.experimentSeed = nbt.getInt("experimentSeed");
    s.miningOreId = nbt.getString("miningOreId");
    if (s.miningOreId == null || s.miningOreId.isBlank()) s.miningOreId = "COAL";
    s.miningGoalCount = nbt.getInt("miningGoalCount");
    if (s.miningGoalCount <= 0) s.miningGoalCount = 16;

    s.botSeed = nbt.getInt("botSeed");
    s.rngState = nbt.getLong("rngState");
    s.stateTicks = nbt.getInt("stateTicks");
    s.taskProgress = nbt.getInt("taskProgress");
    s.explorationIndex = nbt.getInt("explorationIndex");
    s.shelterStep = nbt.getInt("shelterStep");
    s.running = nbt.getBoolean("running");
    s.paused = nbt.getBoolean("paused");
    s.loggingEnabled = nbt.getBoolean("loggingEnabled");
    s.spawned = nbt.getBoolean("spawned");
    s.homePos = readBlockPos(nbt, "home");
    s.targetPos = readBlockPos(nbt, "target");
    s.shelterOrigin = readBlockPos(nbt, "shelter");
    return state;
  }

  private static void writeBlockPos(NbtCompound nbt, String key, BlockPos pos) {
    NbtCompound c = new NbtCompound();
    c.putInt("x", pos.getX());
    c.putInt("y", pos.getY());
    c.putInt("z", pos.getZ());
    nbt.put(key, c);
  }

  private static BlockPos readBlockPos(NbtCompound nbt, String key) {
    if (!nbt.contains(key)) {
      return BlockPos.ORIGIN;
    }
    NbtCompound c = nbt.getCompound(key);
    return new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z"));
  }
}
