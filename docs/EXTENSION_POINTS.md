# Extension Points Guide

This short guide is meant to make the project easier to extend without needing to read the whole codebase first.

The current design can be read as a pipeline:

Player Actions → Field Simulation → Regions → History → Policies → Ecology → Presentation

## 1. Field
The field layer is the simulation core. It computes how tension changes over time, how it spreads through chunks, and how it contributes to regional state. This is the place for rules that describe the world’s dynamic behavior itself.

If you want to change how tension grows, decays, diffuses, or responds to events, start here.

## 2. History
The history layer records what has happened in a region over time. It is not the simulation itself; it is the memory of the simulation. This can include mining activity, tree cutting, fires, structure work, or other repeated human or world actions.

History is useful when you want ecological or presentation behavior to reflect long-term change instead of only the current tick snapshot.

## 3. Policy
The policy layer decides how a region should respond to its current state and its history. A policy is a high-level rule that answers questions such as: should this region grow, recover, strain, fracture, or appear ancient?

This layer is intentionally separate from the world-editing code so that behavior can be tuned or swapped without rewriting the block-placement logic.

## 4. Ecology
The ecology layer applies the chosen policy to the world. It performs the actual decorative or environmental changes: vegetation, decay, unusual blocks, recovery effects, and similar visual or subtle world changes.

This layer should stay focused on applying effects. It should not decide the region’s intent by itself; it should follow the policy chosen upstream.

## 5. Presentation
The presentation layer controls how the player perceives the region. This includes diagnostics, overlays, audio cues, HUD hints, or other feedback that communicates the state of the field without changing the simulation itself.

Presentation is the most flexible layer for experimentation because it can be changed or expanded without altering the underlying model.

## Suggested contributor workflow
If you are extending the system, a good mental model is:

1. Identify the current field behavior.
2. Decide whether the change belongs in history, policy, ecology, or presentation.
3. Keep the simulation logic separate from the world effects.
4. Prefer small, composable changes over large rewrites.

That structure makes it easier to add new behaviors while preserving the core design.
