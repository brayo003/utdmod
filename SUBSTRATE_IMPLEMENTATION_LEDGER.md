# SUBSTRATE_IMPLEMENTATION_LEDGER

## Milestone: chunk-local substrate state

- Status: implemented in runtime code path
- Scope: added a first-class substrate state object carrying activation, memory, transport, and field depth alongside the existing chunk tension scalar
- Runtime entry point: [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java)
- Storage: [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java)
- Observation path: [src/main/java/com/utdmod/research/ResearchObserver.java](src/main/java/com/utdmod/research/ResearchObserver.java) and [src/main/java/com/utdmod/client/ui/TensionHud.java](src/main/java/com/utdmod/client/ui/TensionHud.java)
- Regression test: [src/test/java/com/utdmod/tension/SubstrateStateTest.java](src/test/java/com/utdmod/tension/SubstrateStateTest.java)

## Verification evidence

- The implementation compiles successfully via Gradle compile task.
- The regression test class exists and targets the new substrate-state behavior.
- Full Gradle test execution is currently blocked by Fabric Loom environment setup during Minecraft remapping, not by a Java compilation error in the substrate code.
