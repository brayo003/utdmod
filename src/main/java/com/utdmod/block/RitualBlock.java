package com.utdmod.block;

import com.mojang.serialization.MapCodec;
import com.utdmod.core.TensionActivityLedger;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.tick.TensionLogger;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public class RitualBlock extends BlockWithEntity {

    public static final MapCodec<RitualBlock> CODEC = AbstractBlock.createCodec(RitualBlock::new);
    public static final BooleanProperty DEGRADED = BooleanProperty.of("degraded");

    public RitualBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(DEGRADED, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(DEGRADED);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RitualBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient() && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity sp) {
            ServerWorld sw = (ServerWorld) world;
            ChunkPos cp = new ChunkPos(pos);
            double before = ChunkTensionManager.getLocalTension(sw, cp);
            ChunkTensionManager.addLocalTension(sw, cp, 0.03);
            double after = ChunkTensionManager.getLocalTension(sw, cp);
            TensionActivityLedger.addRitual(0.03);
            TensionLogger.log(TensionTraceClock.getServerTick(), "RITUAL", "ritual_block_activation", 0.03, before, after, cp, sp, sw);
            player.sendMessage(Text.literal("Ritual invoked. Local tension rises..."), true);
            world.updateListeners(pos, state, state, 3);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }
}
