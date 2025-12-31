# Minecraft 1.20.4 Compilation Fix Mapping

## Package Import Updates Required

### Critical Package Changes (Minecraft 1.20.4)
```
OLD (1.17-1.19) → NEW (1.20.4)
net.minecraft.server.world.ServerWorld → net.minecraft.server.level.ServerLevel
net.minecraft.world.World → net.minecraft.world.level.Level
net.minecraft.util.math.BlockPos → net.minecraft.core.BlockPos
net.minecraft.entity.player.PlayerEntity → net.minecraft.server.level.ServerPlayer (server-side)
net.minecraft.item.ItemStack → net.minecraft.world.item.ItemStack
net.minecraft.util.Formatting → net.minecraft.network.chat.Component (for text styling)
net.minecraft.text.Text → net.minecraft.network.chat.Component
net.minecraft.util.Hand → net.minecraft.world.InteractionHand
net.minecraft.util.hit.BlockHitResult → net.minecraft.world.phys.BlockHitResult
net.minecraft.util.TypedActionResult → net.minecraft.world.InteractionResult
net.minecraft.block.* → net.minecraft.world.level.block.*
net.minecraft.item.* → net.minecraft.world.item.*
net.minecraft.entity.player.* → net.minecraft.world.entity.player.*
net.minecraft.particle.* → net.minecraft.core.particles.*
net.minecraft.sound.* → net.minecraft.sounds.*
net.minecraft.util.* → net.minecraft.core.* (many moved)
```

## Per-File Fix Mapping

### Files Requiring ServerWorld → ServerLevel Updates
```
src/main/java/com/utdmod/storm/StormManager.java
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel
- All ServerWorld parameters → ServerLevel
- world.getRegistryKey() → world.dimension()
- world.getDimensionKey() → world.dimension().location()

src/main/java/com/utdmod/ritual/RitualHandler.java  
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel
- performRitual(ServerWorld world, PlayerEntity player) → performRitual(ServerLevel world, ServerPlayer player)

src/main/java/com/utdmod/debug/AIDebugLogger.java
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel
- All ServerWorld references → ServerLevel

src/main/java/com/utdmod/blocks/RitualBlock.java
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel

src/main/java/com/utdmod/item/WardingCrystal.java
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel

src/main/java/com/utdmod/tension/TensionWorldEffects.java
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel

src/main/java/com/utdmod/signals/PlayerStateTension.java
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel

src/main/java/com/utdmod/signals/TensionWorldEffects.java
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel

src/main/java/com/utdmod/debug/UTDDiagnostics.java
- import net.minecraft.server.world.ServerWorld → import net.minecraft.server.level.ServerLevel
```

### Files Requiring World → Level Updates
```
src/main/java/com/utdmod/item/WardingCrystal.java
- import net.minecraft.world.World → import net.minecraft.world.level.Level

src/main/java/com/utdmod/signals/BiomeResponder.java
- import net.minecraft.world.World → import net.minecraft.world.level.Level

src/main/java/com/utdmod/signals/PlayerStateTension.java
- import net.minecraft.world.World → import net.minecraft.world.level.Level

src/main/java/com/utdmod/blocks/RitualBlock.java
- import net.minecraft.world.World → import net.minecraft.world.level.Level
```

### Files Requiring BlockPos Package Update
```
src/main/java/com/utdmod/items/SignalProbeItem.java
- import net.minecraft.util.math.BlockPos → import net.minecraft.core.BlockPos

src/main/java/com/utdmod/blocks/RitualBlock.java
- import net.minecraft.util.math.BlockPos → import net.minecraft.core.BlockPos

src/main/java/com/utdmod/signals/BiomeResponder.java
- import net.minecraft.util.math.BlockPos → import net.minecraft.core.BlockPos
```

## Client-Only Files (Require @Environment(EnvType.CLIENT) and relocation)

### Files Already in client/ directory (correctly placed)
```
src/main/java/com/utdmod/client/SignalProbeItem.java ✓
src/main/java/com/utdmod/client/ClientTensionCache.java ✓
src/main/java/com/utdmod/client/ClientTensionSync.java ✓
src/main/java/com/utdmod/client/AIDebugCommand.java ✓
src/main/java/com/utdmod/client/TensionHud.java ✓
```

### Files That Need @Environment(EnvType.CLIENT) Annotation
```
src/main/java/com/utdmod/client/SignalProbeItem.java
- Add: @Environment(EnvType.CLIENT)

src/main/java/com/utdmod/client/TensionHud.java
- Add: @Environment(EnvType.CLIENT)

src/main/java/com/utdmod/client/ClientTensionCache.java
- Add: @Environment(EnvType.CLIENT)

src/main/java/com/utdmod/client/ClientTensionSync.java
- Add: @Environment(EnvType.CLIENT)

src/main/java/com/utdmod/client/AIDebugCommand.java
- Add: @Environment(EnvType.CLIENT)
```

### Files Requiring Client-Side Import Updates
```
src/main/java/com/utdmod/client/TensionHud.java
- import net.minecraft.client.MinecraftClient → KEEP (correct)
- import net.minecraft.client.util.math.MatrixStack → import net.minecraft.client.gui.GuiGraphics
- import net.minecraft.client.gui.DrawContext → REMOVE (replaced by GuiGraphics)
- Update rendering method signatures

src/main/java/com/utdmod/client/SignalProbeItem.java
- Update all client-side imports to 1.20.4 versions
```

## Block/Item Package Updates
```
src/main/java/com/utdmod/blocks/CustomBlocks.java
- import net.minecraft.block.Block → import net.minecraft.world.level.block.Block
- import net.minecraft.block.Blocks → import net.minecraft.world.level.block.Blocks
- import net.minecraft.block.Material → import net.minecraft.world.level.material.Material

src/main/java/com/utdmod/blocks/RitualBlock.java
- import net.minecraft.block.Block → import net.minecraft.world.level.block.Block
- import net.minecraft.block.BlockState → import net.minecraft.world.level.block.state.BlockState
- import net.minecraft.block.Material → import net.minecraft.world.level.material.Material

src/main/java/com/utdmod/items/CustomItems.java
- import net.minecraft.item.Item → import net.minecraft.world.item.Item
- import net.minecraft.item.ItemGroup → import net.minecraft.world.item.CreativeModeTab

src/main/java/com/utdmod/item/WardingCrystal.java
- import net.minecraft.item.Item → import net.minecraft.world.item.Item
- import net.minecraft.item.ItemStack → import net.minecraft.world.item.ItemStack
- import net.minecraft.item.UseAction → import net.minecraft.world.item.UseAnim
```

## Registry/Util Package Updates
```
src/main/java/com/utdmod/registry/ModBlocks.java
- import net.minecraft.block.Block → import net.minecraft.world.level.block.Block
- import net.minecraft.block.Material → import net.minecraft.world.level.material.Material
- import net.minecraft.item.BlockItem → import net.minecraft.world.item.BlockItem
- import net.minecraft.item.Item → import net.minecraft.world.item.Item
- import net.minecraft.item.ItemGroup → import net.minecraft.world.item.CreativeModeTab
- import net.minecraft.registry.Registries → import net.minecraft.core.Registry
- import net.minecraft.registry.Registry → import net.minecraft.core.Registry
- import net.minecraft.util.Identifier → import net.minecraft.resources.ResourceLocation

src/main/java/com/utdmod/registry/ModItems.java
- import net.minecraft.item.Item → import net.minecraft.world.item.Item
- import net.minecraft.item.ItemGroup → import net.minecraft.world.item.CreativeModeTab
- import net.minecraft.registry.Registries → import net.minecraft.core.Registry
- import net.minecraft.registry.Registry → import net.minecraft.core.Registry
- import net.minecraft.util.Identifier → import net.minecraft.resources.ResourceLocation
```

## Text/Formatting Updates
```
src/main/java/com/utdmod/probe/ProbeHandler.java
- import net.minecraft.text.Text → import net.minecraft.network.chat.Component
- net.minecraft.text.Text.literal → net.minecraft.network.chat.Component.literal

src/main/java/com/utdmod/registry/ModItemGroups.java
- import net.minecraft.text.Text → import net.minecraft.network.chat.Component
- import net.minecraft.text.Text.literal → net.minecraft.network.chat.Component.literal

src/main/java/com/utdmod/item/WardingCrystal.java
- import net.minecraft.text.Text → import net.minecraft.network.chat.Component
- import net.minecraft.util.Formatting → REMOVE (moved to Style)
- net.minecraft.text.Text.literal → net.minecraft.network.chat.Component.literal

src/main/java/com/utdmod/blocks/RitualBlock.java
- import net.minecraft.text.Text → import net.minecraft.network.chat.Component
- import net.minecraft.util.Formatting → REMOVE (moved to Style)
- net.minecraft.text.Text.literal → net.minecraft.network.chat.Component.literal

src/main/java/com/utdmod/ritual/RitualHandler.java
- import net.minecraft.text.Text → import net.minecraft.network.chat.Component
- player.sendMessage(Text.literal(...)) → player.sendSystemMessage(Component.literal(...))

src/main/java/com/utdmod/items/SignalProbeItem.java
- import net.minecraft.text.Text → import net.minecraft.network.chat.Component
- import net.minecraft.util.Formatting → REMOVE (moved to Style)
- net.minecraft.text.Text.literal → net.minecraft.network.chat.Component.literal

src/main/java/com/utdmod/debug/AIDebugCommand.java
- import net.minecraft.text.Text → import net.minecraft.network.chat.Component
- import net.minecraft.util.Formatting → REMOVE (moved to Style)
- net.minecraft.text.Text.literal → net.minecraft.network.chat.Component.literal
```

## Hand/Interaction Updates
```
src/main/java/com/utdmod/item/WardingCrystal.java
- import net.minecraft.util.Hand → import net.minecraft.world.InteractionHand
- import net.minecraft.util.TypedActionResult → import net.minecraft.world.InteractionResult
- Hand.MAIN_HAND → InteractionHand.MAIN_HAND
- TypedActionResult.success → InteractionResult.SUCCESS

src/main/java/com/utdmod/blocks/RitualBlock.java
- import net.minecraft.util.Hand → import net.minecraft.world.InteractionHand
- import net.minecraft.util.ActionResult → import net.minecraft.world.InteractionResult
- import net.minecraft.util.hit.BlockHitResult → import net.minecraft.world.phys.BlockHitResult
```

## Network/Packet Updates
```
src/main/java/com/utdmod/network/TensionSyncPayload.java
- import net.minecraft.network.PacketByteBuf → import net.minecraft.network.FriendlyByteBuf
- import net.minecraft.network.codec.PacketCodec → import net.minecraft.network.codec.StreamCodec
- import net.minecraft.network.packet.CustomPayload → import net.minecraft.network.protocol.common.custom.CustomPacketPayload
- import net.minecraft.util.Identifier → import net.minecraft.resources.ResourceLocation

src/main/java/com/utdmod/network/TensionSyncPacket.java
- Same updates as TensionSyncPayload
```

## Particle/Sound Updates
```
src/main/java/com/utdmod/item/WardingCrystal.java
- import net.minecraft.particle.ParticleTypes → import net.minecraft.core.particles.ParticleTypes
- import net.minecraft.sound.SoundCategory → import net.minecraft.sounds.SoundSource
- import net.minecraft.sound.SoundEvents → import net.minecraft.sounds.SoundEvents

src/main/java/com/utdmod/blocks/RitualBlock.java
- Same particle/sound updates
```

## Critical Method/API Changes
```
ServerLevel/World method changes:
- world.getRegistryKey() → world.dimension()
- world.getDimensionKey() → world.dimension().location()
- player.sendMessage(Text) → player.sendSystemMessage(Component)
- world.spawnEntity() → world.addFreshEntity()
- world.getNonSpectatingEntities() → world.getEntities()
```

## Gradle Configuration Check
```
build.gradle should include:
sourceCompatibility = JavaVersion.VERSION_17
targetCompatibility = JavaVersion.VERSION_17

dependencies {
    minecraft 'com.mojang:minecraft:1.20.4'
    mappings loom.officialMojangMappings()
    modImplementation 'net.fabricmc:fabric-loader:0.15.10'
    modImplementation 'net.fabricmc.fabric-api:fabric-api:0.97.1+1.20.4'
}
```

## Implementation Priority Order
1. Fix package imports in core server classes (StormManager, RitualHandler, AIDebugLogger)
2. Update block/item registry classes
3. Fix client-side classes with @Environment annotations
4. Update network/packet classes
5. Update remaining utility classes
6. Test compilation after each major category

## Verification Commands
```bash
# Test compilation after fixes
./gradlew compileJava

# Check for remaining import issues
./gradlew compileJava 2>&1 | grep "cannot find symbol"

# Verify dependencies are correct
./gradlew dependencies --configuration compileClasspath | grep minecraft
```
