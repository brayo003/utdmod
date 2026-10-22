# UTD Multi-Aspect Substrate Extension
## Substrate Provenance, Modal Signatures, Environmental Memory, and Cross-Mod Emergent Behavior

**Document type:** Architecture and research-design specification  
**Project:** UTD Mod — Unified Tension Dynamics  
**Target context:** Minecraft 1.20.4, Fabric, Java 17  
**Status:** Proposed extension; not yet implemented or validated in the production runtime  
**Relationship to existing work:** Extends the shared regional substrate concept without assuming the current runtime already supports modal signatures or event provenance

---

## 1. Executive summary

UTD's central opportunity is to make a Minecraft world respond not only to *how much* activity or tension a region has accumulated, but also to *what kinds of activity shaped it*, how those influences have evolved, and how they interact with other systems.

A scalar model can represent a regional quantity such as tension:

\[
U(\mathbf{x},t)\in[0,U_{\max}]
\]

But if every event contributes to the same undifferentiated pool, a quarry, a fire spell, a necromantic ritual, and an industrial reactor can become indistinguishable once their contributions are reduced to the same value. A downstream system cannot reliably infer the cause from that scalar alone.

The proposed extension is a **multi-aspect substrate with provenance-aware event ingestion**:

1. **Shared general substrate state** retains UTD's existing regional dynamics, such as activity, memory, depth, and rest.
2. **Modal channels** represent different classes of accumulated influence, such as industrial, thermal, arcane, entropic, biological, hydrological, or other categories justified by the intended modpack.
3. **Event provenance** records which source system caused an event, its type, location, magnitude, and time, subject to retention and performance limits.
4. **Interpretation rules** turn substrate state and provenance into semantic conditions that gameplay systems can use.
5. **Actuators** implement actual effects in the world: crop behavior, mobs, villages, resources, weather, machines, magic, presentation, quests, and so on.
6. **Client presentation** communicates the region's condition, while authoritative world changes remain server-controlled.

The desired result is that two regions with the same total tension can still have different signatures, histories, and future behaviors. A hybrid region can preserve several simultaneously relevant influences instead of being forced into a single dominant category.

This is a design proposal. It must not be described as functionality already present in the current UTD repository until the corresponding code, serialization, update rules, actuators, and tests exist.

---

## 2. The design goal

The target is not simply to attach a colored label to a chunk. The target is to preserve meaningful causal and historical differences in a shared substrate.

Imagine two chunks with the same aggregate tension value, `0.65`:

- **Chunk A:** its history is dominated by industrial machinery, heat, excavation, and repeated mechanical activity.
- **Chunk B:** its history is dominated by arcane rituals, magical anomalies, and supernatural events.

If the substrate stores only the aggregate scalar, the two states may be identical to downstream consumers. A system that sees only `0.65` cannot infer whether it should interpret the region as industrially disturbed or magically unstable.

The extension should make the distinction representable:

\[
\text{same aggregate magnitude}
\not\Rightarrow
\text{same substrate state}
\]

And, where the design intends it:

\[
\text{different modal history}
\Rightarrow
\text{potentially different future dynamics and actuator outcomes}
\]

The last implication is not automatic. It must be established through explicit state-update rules and gameplay integration.

### Desired player-facing result

A player should be able to build, mine, farm, fight, cast spells, establish settlements, automate production, and interact with other mods without every consequence being reduced to one generic “high tension” response.

Over time, the regional substrate should be capable of representing a persistent environmental personality. Its consequences should be understandable enough to investigate, but complex enough that multiple players and systems can create outcomes that were not individually scripted.

The goal is **surprising but explainable behavior**, not arbitrary randomness.

---

## 3. What the existing UTD architecture provides—and what it does not

Previous repository examinations identified a shared regional field with a small general state vector, including activity, memory, depth, and rest. The architecture has a common field consumed by multiple actuators rather than wholly separate regional counters for every gameplay feature. Some of the core field code is ordinary Java rather than directly dependent on Minecraft classes.

That architecture is a useful foundation for the proposed extension.

However, the existing general state vector does not automatically preserve event identity. If fire, ice, industry, and necromancy all perturb the same general values and no additional information is retained, later code may not be able to distinguish those causes.

The proposed extension therefore requires an explicit investigation of:

- where perturbation events enter the runtime;
- whether event source/type information is already retained anywhere;
- how chunk state is stored, serialized, loaded, and unloaded;
- how field values are updated and diffused;
- how `SubstrateSemantics` and intent thresholds interpret the field;
- how each actuator obtains state and decides what to do;
- which effects are server-authoritative and which are visual-only;
- how new state can be introduced without invalidating existing worlds or configuration.

Do not infer that a feature exists merely because the architecture could support it.

### Existing general state should not be casually replaced

The proposal does **not** require deleting the existing activity, memory, depth, or rest variables. The more conservative direction is to retain the existing general substrate and add a carefully bounded modal layer around it.

This preserves the existing model while allowing richer semantic information to be represented.

The actual integration point must be confirmed from the current source before implementation.

---

## 4. Scalar versus multi-aspect representation

### 4.1 Scalar substrate

A simplified scalar representation is:

\[
U(\mathbf{x},t)\in[0,U_{\max}]
\]

An event contributes a source term:

\[
\frac{\partial U}{\partial t}
=F(U,\nabla U,\ldots)+S(\mathbf{x},t)
\]

The exact equation used by UTD may be discrete and may contain coupling, saturation, decay, or other terms. The equation above is a conceptual illustration, not a statement of the production implementation.

A scalar is useful for overall intensity or general strain. Its limitation is semantic ambiguity: distinct causes can map to the same result.

### 4.2 Multi-aspect substrate

A modal vector can be represented conceptually as:

\[
\mathbf{U}(\mathbf{x},t)=
\begin{bmatrix}
U_{\text{industrial}}\\
U_{\text{thermal}}\\
U_{\text{arcane}}\\
U_{\text{entropic}}\\
U_{\text{biological}}\\
U_{\text{hydrological}}\\
\vdots
\end{bmatrix}
\]

The number and meaning of channels must be selected deliberately. This vector is a proposal, not a claim that these channels already exist in UTD.

Each channel can have its own source mapping, decay characteristics, spatial propagation, coupling, and interpretation. Channels may interact, but those interactions should be defined rather than assumed.

For example, an industrial event might contribute to industrial and thermal channels. A fire spell might contribute to thermal and arcane channels. A necromantic ritual might contribute to arcane and entropic channels.

This is better than forcing every source into exactly one category because real mod events can have multiple meaningful aspects.

### 4.3 Do not assume these are literal physical energy units

Names such as kinetic, thermal, arcane, and entropy are useful semantic labels, but in a game simulation they may be **dimensionless state variables**, not measured physical energies.

If a channel is called `thermal`, its numerical value does not automatically represent joules or temperature. Units, ranges, normalization, and interpretation must be documented. Otherwise, a formula that combines different channels can be mathematically convenient but semantically misleading.

A safer abstraction is “modal influence” or “aspect intensity.” The final names can be chosen for the mod's design language.

---

## 5. Recommended hybrid architecture

Use a shared general substrate, a bounded modal field, and explicit provenance metadata.

### Layer A — General substrate state

Retain the existing general state used by the runtime, subject to source verification. This layer describes broad regional dynamics such as activity, accumulated memory, depth, and rest.

### Layer B — Modal signature state

Each region stores a bounded vector of modal values. These values describe accumulated influence across semantic aspects.

Examples:

- industrial;
- thermal;
- arcane;
- entropic;
- biological;
- hydrological;
- other channels only when a concrete gameplay use justifies them.

A channel should not be added merely because one mod exists. It should have a defined event mapping, update behavior, and at least one intended consumer or diagnostic use.

### Layer C — Event provenance

An event envelope records the cause of a substrate perturbation. Conceptually:

```java
record SubstrateEvent(
    ResourceLocation sourceId,
    ResourceLocation eventType,
    BlockPos position,
    float magnitude,
    ModalSignature signature,
    long gameTime
) {}
```

This is illustrative pseudocode. It is not a verified class, API, or compatible implementation for the current repository.

Useful fields may include:

- `sourceId`: stable identity of the mod or source system;
- `eventType`: stable identity for the kind of event;
- position or region coordinates;
- magnitude and any required signed direction;
- modal contributions;
- game time or sequence information;
- optional contextual metadata needed for a particular supported feature.

Do not store arbitrary objects from third-party mods in persistent chunk state. Use stable identifiers and a small, versioned schema.

### Layer D — Interpretation

The interpretation layer derives semantic conditions from general state, modal state, and—only when needed—provenance history.

It can answer questions such as:

- Is the region strongly industrially influenced?
- Is its arcane signature increasing or decaying?
- Is the region a mixed industrial-arcane environment?
- Has a particular source system contributed recently?
- Is the region's overall intensity high enough for a particular effect?
- Are multiple conditions satisfied for a rare hybrid event?

The interpretation layer should not contain all Minecraft-specific implementation details. It should expose stable semantic results that actuators can consume.

### Layer E — Actuators

Actuators translate semantic state into actual game behavior. Examples include resource regeneration, crop response, mob behavior, village development, weather, particles, block interactions, machines, spells, quests, and environmental presentation.

Each actuator must have explicit conditions, bounds, cooldowns, and safety checks. A signature does not mean that every associated effect should happen every tick.

### Layer F — Client presentation

Clients can render the region's condition through particles, sounds, fog, ambient effects, or other presentation supported by the engine. Clients should receive the minimum necessary state or presentation events.

Authoritative world mutation, entity spawning, crop destruction, machine behavior, and other gameplay changes must remain server-controlled. Client rendering must not independently make consequential world decisions.

---

## 6. Provenance and modal state are different

This distinction is central to the design.

### Modal state answers:

“What kinds of influence have accumulated in this region, and how strong are they now?”

For example, a region may currently have high thermal influence and moderate arcane influence.

### Provenance answers:

“Which source systems and event types contributed to the current or past condition?”

For example, the thermal influence may have come from a blast furnace mod, a fire spell, or a volcanic event.

### Why one cannot replace the other

A full event log is not a substitute for an evolving field. It can say what happened but may be too expensive to replay or inspect on every game tick.

A modal vector is not a complete event log. It may preserve the fact that a region is strongly thermal without preserving the exact identity of every furnace, spell, or player action that contributed to it.

Recommended division:

- use the modal field for efficient, ongoing regional dynamics;
- use bounded provenance summaries or event history when causal identity is needed;
- retain detailed event records only when there is a real feature that requires them;
- avoid an unbounded permanent event log in every chunk.

A possible compact provenance summary might keep recent source categories, source contribution totals, timestamps, or a limited event history. The precise schema depends on the required quests, faction systems, debugging, and modpack integration features.

---

## 7. Preserve mixed and hybrid signatures

The example architecture's `getDominantSignature()` method can be useful for choosing a primary visual theme, but it should not be the sole representation exposed to every consumer.

Suppose a chunk has:

- industrial = 0.70;
- thermal = 0.65;
- arcane = 0.68;
- entropic = 0.05.

A dominant-only model might choose industrial and discard the fact that arcane influence is nearly as strong. That prevents the system from representing a genuinely hybrid region.

### Recommended approach

Expose both:

1. **The complete bounded modal vector**, or a safe view of the relevant channels.
2. **Derived classifications**, such as dominant aspect, significant secondary aspects, or a named hybrid category.

The dominant aspect can control a primary atmosphere or a simplified user-facing label. Hybrid rules should be able to inspect multiple channels.

For example:

- industrial + thermal → heat-polluted industrial region;
- industrial + arcane → techno-arcane anomaly;
- thermal + hydrological → steam, fog, or condensation phenomena;
- arcane + entropic → necromantic anomaly;
- several strong channels → complex or contested signature.

These combinations are examples, not mandated game mechanics. Each combination needs a clear gameplay purpose and bounded implementation.

### Avoid combinatorial explosion

If every channel can combine with every other channel, the number of possible combinations grows rapidly. With \(n\) channels, the number of nonempty subsets is \(2^n-1\).

Do not hand-author a unique elaborate effect for every possible subset. Instead:

- define a limited set of supported interactions;
- use generic rules for combinations where appropriate;
- prioritize channels that produce meaningful gameplay differences;
- define a fallback for unrecognized combinations;
- make the set of supported combinations data-driven where practical.

---

## 8. Equal total tension, different regional histories

A central test case should deliberately create two regions with equal aggregate tension but different modal distributions.

For example, suppose a chosen aggregate function is the sum of normalized channels:

\[
T = U_{\text{industrial}} + U_{\text{thermal}} + U_{\text{arcane}} + U_{\text{entropic}}
\]

Region A:

\[
\mathbf{U}_A=(0.40,0.25,0.00,0.00), \quad T_A=0.65
\]

Region B:

\[
\mathbf{U}_B=(0.00,0.00,0.50,0.15), \quad T_B=0.65
\]

Both regions have the same aggregate value under this illustrative formula. They do not have the same modal state.

**Important:** summing channels is only one possible aggregation function. If channels overlap semantically, a sum may double-count related influences. A weighted sum, norm, maximum, or separate general tension variable may be more appropriate. Choose and document the aggregation rule based on desired semantics, not convenience.

### Required behavioral test

After setting up the two regions:

1. Confirm that their chosen aggregate tension is equal within a defined tolerance.
2. Confirm that their modal vectors differ as intended.
3. Apply the same subsequent input to both.
4. Verify that their evolution differs only where the designed channel rules justify it.
5. Verify that the intended actuators respond differently.
6. Verify that no unintended differences arise from chunk initialization, random seeds, player proximity, or unrelated configuration.
7. Repeat the test with both regions unloaded and reloaded if the field is persistent.

This is a direct demonstration of the extension's core claim.

---

## 9. Persistent environmental memory

The modal field should represent an evolving regional history, not a permanent binary label.

A conceptual discrete update for channel \(i\) could be:

\[
U_i^{t+1} =
\operatorname{Clamp}\left(
U_i^t
+ I_i^t
- D_i^t
+ P_i^t
+ C_i^t,\,
0,\,
U_{i,\max}
\right)
\]

where:

- \(I_i^t\) is the mapped event input;
- \(D_i^t\) is channel decay or removal;
- \(P_i^t\) is spatial transport or diffusion;
- \(C_i^t\) is coupling from other channels.

This is a conceptual update template, not a recommendation to paste it into production without deriving consistent units, timestep behavior, stability bounds, and interactions.

### Questions each channel needs answered

- What raises it?
- What lowers it?
- Does it diffuse spatially?
- Does it saturate?
- Can it become negative, or is it strictly nonnegative?
- Does it couple to the existing general state?
- Can other channels reinforce or suppress it?
- What is its default value?
- How does it persist across save/reload?
- How does it behave when its source mod is absent?
- What is its computational cost?

### Residual signature after general tension decays

The design should allow general tension to return toward baseline while a different, slower-decaying modal profile remains, if that is the intended behavior.

This requires separate, deliberate decay rules. It must not be assumed to happen just because the vector has multiple components.

For example, short-lived thermal disturbance might decay relatively quickly, while an arcane imprint might decay slowly. These are game-design choices, not physical laws.

The system should avoid preserving a visually strong signature forever unless the intended mechanics explicitly require that. Persistent but decaying history can create meaningful long-term consequences without permanently ruining a world.

---

## 10. Cross-channel coupling

A multi-aspect field becomes more than a set of independent counters when channels can interact.

A conceptual model is:

\[
\frac{d\mathbf{U}}{dt}
=
\mathbf{F}(\mathbf{U},\nabla\mathbf{U})
+
\mathbf{S}(\mathbf{x},t)
+
\mathbf{G}(\mathbf{U})
\]

Here, \(\mathbf{G}\) represents cross-channel interaction. This is a general mathematical framing, not a validated UTD equation.

Possible design examples:

- thermal influence changes how a hydrological channel manifests;
- industrial influence alters the persistence of a thermal disturbance;
- arcane influence changes the response to another channel;
- entropic influence affects biological recovery;
- a sufficiently strong combination produces a hybrid event.

### Rules for coupling

1. Every coupling must have a documented interpretation.
2. Coupling coefficients must be bounded and tested.
3. Cross-channel interactions must not create uncontrolled numerical growth.
4. Saturation and decay must remain well-defined.
5. A coupling should be included because it produces a meaningful intended behavior, not simply because a matrix can represent it.
6. The system should include tests for zero input, uniform state, isolated channels, opposing influences, and maximum configured values.
7. Do not infer physical conservation laws unless the model explicitly defines and enforces them.

The current runtime has its own equations and known audit questions. New modal dynamics should first be prototyped separately, then compared against a clearly specified intended model before being integrated.

---

## 11. Mod adapters and shared-substrate integration

A large modpack can contain many different kinds of systems. UTD should not require a bespoke core-state variable for every mod.

Instead, participating mods or adapters map events into the shared substrate.

### Example mappings

| Source event | Possible modal contribution | Possible provenance |
|---|---|---|
| Industrial machinery | Industrial + thermal | Machine type, source mod, event type |
| Quarry or extensive mining | Industrial + excavation-related channel, if defined | Mining system and location |
| Fire spell | Thermal + arcane | Spell ID and source mod |
| Ice spell | Hydrological/cryogenic + arcane, if defined | Spell ID and source mod |
| Necromantic ritual | Entropic + arcane | Ritual ID and source mod |
| Large-scale crop cultivation | Biological or land-use channel, if justified | Farming action or subsystem |
| Explosion | Thermal + industrial or destructive channel, depending on cause | Explosive source and event type |

These are examples of mappings, not fixed assignments. A particular event may affect several channels, and different source systems may map to overlapping channels.

### Adapter responsibilities

An adapter should:

- identify the source event;
- map it to a stable event type and modal contribution;
- validate magnitude and location;
- submit the event to the substrate through a defined interface;
- avoid direct mutation of internal substrate data;
- handle the source mod being absent or disabled;
- avoid hard dependencies on optional mods where possible;
- avoid submitting the same event multiple times;
- document any limitations of its mapping.

### Compatibility philosophy

The substrate core should remain independent of individual mod APIs wherever practical. Integration code can depend on optional mods, but the core state and update model should not become entangled with every third-party implementation.

This keeps the substrate portable and allows a modpack to choose which adapters it enables.

---

## 12. Interpretation and actuator design

The substrate should not directly execute a large switch statement for every conceivable effect. Separate semantic interpretation from the actual world changes.

A semantic result might state:

- industrial influence is high;
- thermal influence is rising;
- arcane influence is significant;
- a supported industrial-arcane hybrid condition is present;
- a specific event source has contributed recently.

An actuator then decides what to do under its own safety and balance rules.

### Industrial examples

Possible presentation:

- subdued smog or particulate effects;
- metallic ambient sound;
- heat haze;
- a region-specific visual palette.

Possible gameplay effects, only if intentionally designed and tested:

- machine efficiency changes;
- corrosion on explicitly supported blocks;
- a bounded chance of an industrial hazard;
- altered resource recovery.

Do not globally break all hoppers, redstone, or machines just because the region has industrial influence. Such effects need clear rules, player feedback, compatibility checks, and counterplay.

### Arcane examples

Possible presentation:

- glyph particles;
- spatial shimmer;
- unusual ambient sounds;
- local visual distortion.

Possible gameplay effects:

- defined spell modifiers;
- bounded anomalies;
- special entities;
- rare events when multiple explicit conditions are satisfied.

Avoid uncontrolled random inversion of spell effects. Players need to understand the risk, and server-side random events should be deterministic or reproducible enough to test when practical.

### Entropic or blighted examples

Possible presentation:

- ash particles;
- subdued foliage colors;
- dark mist;
- decay-themed ambience.

Possible gameplay effects:

- altered crop growth or recovery;
- modified undead behavior;
- special necrotic variants;
- limited resource or ecosystem degradation.

Do not implement “crops die instantly” or permanently destroy player infrastructure merely because a channel is dominant. These are extreme candidate effects that could make the system frustrating rather than immersive. Any destructive effect needs thresholds, telegraphing, boundaries, and recovery/counterplay.

### Biological and ecological examples

If a biological channel is justified, it might represent accumulated cultivation, ecosystem disturbance, or ecological organization. Its interpretation could support recovery or deterioration, but it must not duplicate the existing general state without a defined reason.

### Quest and faction systems

Provenance can enable richer narrative systems:

- a faction identifies a region as affected by a known source;
- a quest investigates an unusual modal signature;
- a settlement remembers a regional catastrophe;
- an event is classified differently depending on its cause;
- players compare two regions with similar total tension but different signatures.

These features may need bounded provenance history, not just the current modal vector.

---

## 13. Presentation: make the state perceptible

The system can be mathematically sophisticated and still fail as gameplay if the player cannot notice what it is doing.

Presentation should reveal regional differences without exposing every internal variable or flooding the screen.

Possible channels of feedback:

- ambient particles;
- soundscapes;
- fog or local atmosphere;
- subtle changes in vegetation or block appearance;
- entity behavior;
- changes in resource recovery;
- map or journal indicators;
- observable reactions from villagers or factions;
- diagnostic tools for players who want to investigate.

### Principles

1. Effects should correspond to real server-side state.
2. Visuals should not imply a stronger condition than the gameplay actually has.
3. Use intensity bands or event-driven updates rather than spawning large numbers of particles every tick.
4. Respect client performance and accessibility.
5. Provide enough feedback that players can form hypotheses.
6. Let players discover causes through observation, tools, quests, or experimentation.
7. Keep purely visual effects separate from gameplay authority.

A player should be able to infer that a region has changed without needing to read raw numeric telemetry.

---

## 14. Multiplayer and shared regional history

The shared substrate becomes especially interesting when several players and several mods influence the same region.

A possible scenario:

- one player builds an industrial facility;
- another casts repeated ice spells;
- a third performs arcane rituals;
- nearby players farm, mine, or fight mobs;
- all events contribute to the same region's general state and modal profile.

The resulting state reflects accumulated interactions rather than an isolated counter belonging to each player.

### Important design questions

- Are modal values stored per chunk, region, or another spatial unit?
- How are events near chunk boundaries assigned?
- Does the influence spread to neighboring chunks?
- How are unloaded chunks updated?
- How do concurrent events avoid lost updates?
- What happens when multiple source mods contribute simultaneously?
- Can a player identify the source of a regional condition?
- Which events are public, private, or faction-specific?
- How does the server prevent malicious mods or repeated event injection from overwhelming the field?

The answers should be explicit before large-scale deployment.

### Cause identity versus anonymous field values

A shared modal vector can show that a region is arcane-dominated. It cannot necessarily identify which player or mod caused that influence unless provenance information is retained.

If quests or factions need to distinguish “water magic affected this desert” from “an earthquake disturbed this desert,” the system must preserve enough causal information to make that distinction. Numeric modal channels alone may be insufficient if two event types map to the same channels.

---

## 15. Persistence, serialization, and versioning

Adding state to a persistent world is a compatibility change.

Before implementation, identify the current persistence mechanism and decide:

- how modal values are serialized;
- how defaults are assigned to older worlds;
- how missing or malformed values are handled;
- how channel identifiers remain stable across versions;
- how a channel can be deprecated or renamed;
- what happens when an optional source mod is removed;
- whether state is stored per chunk, region, or a separate data container;
- whether the data is saved only when changed;
- how corruption and invalid numeric values are handled.

### Requirements

- Use stable IDs rather than Java class names as persistent semantic identifiers.
- Version the serialization schema.
- Validate loaded values against finite ranges.
- Handle missing channels safely.
- Avoid serializing arbitrary event objects from third-party mods.
- Test save/reload, world upgrade, chunk unload/reload, and missing optional-mod scenarios.
- Do not silently reset existing general substrate data when adding modal state.

The extension should be designed to preserve the existing world wherever possible.

---

## 16. Performance and scale

A vector increases the amount of state and computation per region. Provenance adds more cost if event records are retained.

The goal is not to avoid all additional state; it is to ensure the state has a clear value relative to its cost.

### Recommended constraints

1. Start with a small number of meaningful channels.
2. Avoid allocating a large object for every tiny event unless required.
3. Aggregate repeated events when exact event history is not needed.
4. Avoid per-tick scans of every block in a chunk.
5. Update presentation at controlled intervals or when relevant state changes.
6. Keep provenance retention bounded.
7. Avoid expensive cross-channel calculations when the region is inactive and no relevant state is changing, provided the optimization preserves the model.
8. Profile server tick time, memory, save size, and network traffic under representative conditions.
9. Test both loaded and unloaded regions.
10. Measure the cost of each adapter and actuator separately where practical.

Do not promise that a design scales to a large multiplayer server until it has been profiled at realistic scale.

---

## 17. Mathematical and numerical safeguards

Multi-channel dynamics introduce additional failure modes.

### Bounds and normalization

Every channel needs a defined valid range and normalization convention. A channel's magnitude should not be compared directly with another channel if their scales differ arbitrarily.

### Coupling stability

Cross-channel terms can amplify each other. Test whether the system remains bounded under sustained input, opposing input, and repeated mixed events.

### Spatial propagation

If channels diffuse, define the operator, boundary behavior, and timestep constraints. Do not assume that a negative diffusion-like coefficient is harmless. Verify that spatial updates do not amplify patterns unintentionally.

### Decay

Each channel's decay rate should have a clear meaning and be tested over time. Avoid choosing rates solely because a visual effect looks good in one short run.

### Saturation

Clamping may prevent runaway values but can also hide a defective update rule. Record how often clamping occurs during experiments. Frequent saturation is a signal to investigate, not proof of stability.

### Aggregation

Define whether aggregate tension is:
- an independent general state;
- a weighted sum of modal channels;
- a norm or other function of the vector;
- or a set of separate metrics with no single aggregate.

Do not count the same source contribution twice merely because it maps to multiple semantic channels.

### Reproducibility

Where randomness affects gameplay, use controlled random seeds in tests and define server-side authority. Separate deterministic state evolution from random event selection when practical.

---

## 18. Testing plan

The extension should be evaluated in stages rather than added directly to the production runtime.

### Stage 1 — Representation tests

- Construct two regions with equal aggregate tension and different modal vectors.
- Verify that their signatures remain distinct.
- Verify that zero-input states remain unchanged where expected.
- Verify that invalid, non-finite, or out-of-range values are rejected or normalized according to policy.
- Verify that adding one channel does not silently change existing general-state semantics.

### Stage 2 — Event mapping tests

- A fire event maps to the configured thermal/arcane channels.
- An ice event maps to the configured ice-related channels.
- An industrial event maps to its configured channels.
- A necromantic event maps to its configured channels.
- Repeated event delivery does not duplicate a single logical event.
- An unknown source ID follows a documented fallback.
- Missing optional mods do not crash the substrate core.

These tests validate the chosen mappings, not any universal truth about which channel an event must affect.

### Stage 3 — Dynamics tests

- Isolated channel with no input.
- Isolated channel with sustained input.
- Two channels with independent input.
- Two channels with defined coupling.
- Opposing or suppressing interactions.
- Maximum permitted input.
- Spatially uniform state.
- Localized perturbation and neighboring diffusion.
- Long-duration stability test.
- Save/reload equivalence.

### Stage 4 — Semantic interpretation tests

- Strong industrial, weak arcane state.
- Strong arcane, weak industrial state.
- Balanced hybrid state.
- No meaningful modal signature.
- Equal aggregate tension but different modal vectors.
- Dominant aspect changes as channel ratios evolve.
- Hybrid classification does not erase secondary aspects.

### Stage 5 — Actuator tests

For each implemented actuator, test:
- conditions that activate it;
- conditions that must not activate it;
- threshold boundaries;
- cooldown and repetition;
- server/client behavior;
- unloaded and reloaded chunks;
- interactions with vanilla and relevant mod systems;
- recovery or reversal behavior where applicable.

### Stage 6 — Integrated gameplay tests

Create a small world with several participating systems. Demonstrate that:
1. events enter the shared substrate;
2. modal state persists and evolves;
3. regions with different histories develop distinct signatures;
4. at least two separate actuator families react appropriately;
5. hybrid conditions produce their explicitly designed behavior;
6. player actions can influence or reverse the regional condition where intended;
7. the system remains understandable and does not create excessive performance costs.

### Stage 7 — Regression tests for existing UTD

The extension must not accidentally break existing general substrate dynamics. Run the existing tests and compare relevant baseline behavior before and after the extension.

Because prior audits identified unresolved memory-equation/telemetry discrepancies and questions about runtime anti-diffusion, do not use the new feature as an excuse to recalibrate those equations. Keep those investigations separate.

---

## 19. Suggested implementation roadmap

This roadmap is a design sequence, not authorization to modify production code immediately.

### Phase 0 — Source audit and specification

- Inspect current state classes, event ingestion, serialization, semantics, and actuators.
- Confirm which provenance features already exist.
- Document current runtime behavior.
- Define the first modal channels and event mappings.
- Define persistence, compatibility, and testing requirements.
- Identify the smallest extension point.

**Exit criterion:** a source-backed design specification with no unresolved assumptions about where state is stored or updated.

### Phase 1 — Isolated modal-state prototype

- Implement a small modal vector in a test harness or isolated branch.
- Keep the production runtime unchanged.
- Test equal aggregate tension with different signatures.
- Test bounds, decay, serialization, and basic coupling if included.
- Measure memory and update cost.

**Exit criterion:** reproducible tests demonstrate that modal identity persists independently of the chosen aggregate metric.

### Phase 2 — Provenance-aware event ingestion

- Define stable source and event IDs.
- Map events into modal contributions.
- Prevent duplicate delivery.
- Establish bounded provenance retention.
- Test unknown sources and absent optional mods.

**Exit criterion:** source events are represented consistently and do not require the core substrate to depend on every mod API.

### Phase 3 — Interpretation rules

- Define dominant and hybrid classifications.
- Define threshold behavior and hysteresis where necessary.
- Ensure secondary channels remain available to consumers.
- Add tests for equal aggregate state and different interpretations.

**Exit criterion:** the semantic layer can distinguish relevant modal histories without relying solely on one dominant label.

### Phase 4 — One or two actuators

Start with limited, observable, reversible effects. Examples could include ambient presentation and one bounded gameplay effect.

Do not begin by implementing destructive crop death, machine failures, gravity inversion, and every proposed anomaly at once.

**Exit criterion:** effects are measurable, understandable, bounded, and linked to actual server-side state.

### Phase 5 — Modpack adapters

Add adapters for a small set of real systems. Prefer stable interfaces and optional dependencies.

**Exit criterion:** multiple mod families contribute to and consume the same substrate without each creating a separate, disconnected simulation.

### Phase 6 — Hybrid interactions and content

Introduce carefully selected hybrid signatures, quests, diagnostics, and player-facing feedback.

**Exit criterion:** players can observe, investigate, and influence consequences that emerge from combined activity.

### Phase 7 — Scale and release readiness

Profile performance, validate world compatibility, test multiplayer, document configuration, and prepare examples.

**Exit criterion:** a repeatable integrated demonstration supports the intended gameplay claims.

---

## 20. Important non-goals and safeguards

The following are not automatically part of this extension:

- replacing the current general substrate state;
- adding one permanent channel for every installed mod;
- storing every event forever;
- allowing clients to mutate authoritative world state;
- making every dominant signature destructive;
- randomly inverting player actions without clear feedback;
- implementing every possible hybrid combination;
- changing the existing runtime memory, depth, or anti-diffusion equations as part of this feature;
- declaring the new system validated because a toy model works;
- claiming the existing repository already implements the proposed behavior.

The extension should be incremental, testable, and separable from the existing runtime until its behavior is demonstrated.

---

## 21. Design choices that must be resolved before production integration

These questions need explicit answers:

1. What is the first supported set of modal channels, and what is the justification for each?
2. Is general tension an independent state or an aggregate derived from the modal vector?
3. Are channel values dimensionless intensities, normalized scores, or something else?
4. Which channels decay quickly or slowly, and why?
5. Which channels diffuse spatially?
6. Which cross-channel couplings are supported initially?
7. How is provenance summarized and how long is it retained?
8. What is the spatial unit of state: chunk, region, or another scale?
9. How does the system handle unloaded regions?
10. Which actuators are the first consumers?
11. Which effects are visual-only and which modify gameplay?
12. How are destructive effects communicated, bounded, and reversed?
13. How are optional mods detected without hard dependencies?
14. How are old world saves migrated?
15. What performance budget is acceptable?
16. Which tests constitute acceptance criteria for the first release?

Do not hide these choices inside implementation details. Record them in the design specification and make them reviewable.

---

## 22. Acceptance criteria for the first meaningful prototype

The first prototype does not need dozens of channels or elaborate world effects. It must prove the central mechanism.

A successful prototype should demonstrate all of the following:

- Two regions have equal aggregate tension but different modal distributions.
- The regions retain different signatures over time.
- Their signatures evolve according to documented input, decay, and propagation rules.
- At least one downstream interpretation distinguishes them.
- At least two distinct actuator or presentation paths can consume the shared substrate.
- A hybrid state can preserve two simultaneously significant channels.
- Event provenance can identify relevant sources without an unbounded log.
- The state survives save/reload.
- Existing general substrate behavior remains unchanged unless a separately approved design decision says otherwise.
- The prototype passes its declared tests, with limitations clearly reported.

That would demonstrate a substantive extension rather than merely a renamed scalar counter.

---

## 23. The intended player experience

The desired experience is a world in which activities have lasting, differentiated consequences.

A factory and a magical ritual should not necessarily leave the same kind of imprint. An ice-focused region and a fire-focused region should be able to behave differently even when their aggregate tension is equal. A mixed industrial-arcane facility should be capable of producing effects that neither influence would produce alone, if those interactions are deliberately modeled.

The player-facing value comes from the consequences:

- places develop histories;
- the same action can have different outcomes in different regional conditions;
- multiple players and mod systems can shape one shared world;
- environmental clues help players infer what happened;
- regional conditions can evolve and potentially recover;
- new stories emerge from interactions among systems rather than solely from fixed scripts.

The substrate is the mechanism. The resulting gameplay is the proof.

---

## 24. Final recommendation

Pursue **Substrate Provenance and Modal Signatures** as a formal UTD extension.

The recommended architecture is:

1. Preserve the existing general substrate.
2. Add a bounded, well-defined modal state layer.
3. Ingest source events through stable, provenance-aware adapters.
4. Preserve hybrid signatures instead of relying exclusively on a dominant label.
5. Separate field dynamics, semantic interpretation, actuators, and client presentation.
6. Validate the extension in an isolated prototype before production integration.
7. Keep existing unresolved runtime-equation and telemetry issues separate from this new design.
8. Integrate incrementally with measurable acceptance tests.

The decisive principle is:

**The substrate should preserve enough information that equal aggregate magnitude does not force equal regional meaning or equal future behavior.**

That is the core of the extension. The specific channels, coupling rules, persistence model, and gameplay effects should be designed and tested around that requirement.

---

## Appendix A — Compact conceptual example

Suppose two regions have equal aggregate tension of `0.65`.

Region A:
- industrial: `0.40`
- thermal: `0.25`
- arcane: `0.00`
- entropic: `0.00`

Region B:
- industrial: `0.00`
- thermal: `0.00`
- arcane: `0.50`
- entropic: `0.15`

Under the illustrative sum rule, both total `0.65`. Their vectors differ.

The industrial region may be interpreted as industrially disturbed, while the second may be interpreted as arcane/entropic. The actual world effects depend on implemented actuator rules.

If a later event adds arcane influence to Region A, the region should be able to become hybrid rather than being forcibly relabeled as purely industrial or purely arcane.

This example is a specification test case, not a prediction about the current production runtime.

## Appendix B — Illustrative event envelope

```java
// Conceptual pseudocode only.
// Not an implementation verified against the current UTD repository.

record ModalContribution(
    ResourceLocation channelId,
    float amount
) {}

record SubstrateEvent(
    ResourceLocation sourceId,
    ResourceLocation eventType,
    BlockPos position,
    List<ModalContribution> contributions,
    long gameTime
) {}
```

Production code must decide whether to use records, existing event abstractions, immutable collections, server-side IDs, and a different serialization structure. The example does not prescribe a concrete API.

## Appendix C — Illustrative semantic result

```java
// Conceptual pseudocode only.

record SubstrateInterpretation(
    float aggregateIntensity,
    Map<ResourceLocation, Float> modalValues,
    Set<ResourceLocation> significantAspects,
    Optional<ResourceLocation> dominantAspect,
    Set<ResourceLocation> activeHybridRules
) {}
```

The important property is that the complete relevant signature remains available. A dominant aspect is a derived convenience, not the only surviving representation.

## Appendix D — Evidence and status discipline

When documenting this feature, use precise status labels:

- **Proposed:** architecture or behavior described but not implemented.
- **Implemented:** code exists in the relevant branch.
- **Unit-tested:** targeted tests passed.
- **Integration-tested:** connected systems were exercised together.
- **Runtime-verified:** behavior was observed in the actual Minecraft environment.
- **Performance-tested:** measured under representative load.
- **Release-ready:** compatibility, persistence, performance, and user-facing behavior have been reviewed.

Do not use “implemented,” “working,” or “validated” for a proposal that has only been discussed or modeled in a standalone harness.
