# Projectiles: port plan + fix ledger

Port of the old `minestom-mechanics-lib` projectile implementation (working, but unstructured) into this
library's architecture (config / resolver / snapshot / context / event layer / system / producers).
Old source: `C:\Users\Gabriel\Documents\GitHub\minestom-mechanics-lib\minestom-mechanics-lib\src\main\java\com\minestom\mechanics\`.

## 0. Decisions (ratified) + methodology

- **Knockback**: plain `KnockbackConfig` through `KnockbackSystem.apply` - no separate projectile KB config.
- **Per-item overrides**: config lambdas via `ctx.item()`. The old item-NBT tag system is dropped;
  TODO(attributes): per-item custom configs (custom items carrying their own projectile tuning) belong to
  the future attributes system - revisit there, not in this port.
- **TPS scaling**: not ported yet, but keep it addable: all timing constants stay tick-denominated and
  centralized (no baked-in per-second math scattered through logic), velocities flow through the existing
  b/t vs b/s conventions, so a later TickScaler pass is mechanical.
- **Methodology - SIMPLE FIRST**: the old fixes were not necessarily necessary nor optimal. Section 1 is a
  *ledger*, not a checklist to blindly port: implement the simple/idiomatic path first, then run each
  ledger entry's edge-case test, and only apply (or re-derive) the fix where the simple path actually
  fails. Every applied fix should cite its ledger entry.

## 1. Fix ledger (what the old impl fixed, how, and how to re-test each)

Each entry: the problem, the old fix, and the edge-case test that decides whether the new impl needs it.

### Physics + entity core (`systems/projectile/entities/CustomEntityProjectile.java`)
- **Zero-size bounding box** (`setBoundingBox(0,0,0)`): collision points resolve exactly on block
  boundaries. Even `0.01` overshoots and breaks the modern client's `floor(pos) -> block -> inGround` check.
- **Block collision** via `PhysicsResult.collisionShapes()[axis]` -> hit block / point / axis; the entity is
  placed at `physicsResult.newPosition()` (resolved), NOT the collision point — fixes modern clients seeing
  the arrow float in front of the block face.
- **Stick is "radio silence"**: when stuck, `super.tick()` never runs -> zero periodic syncs. Modern clients
  hold the arrow via `inGround` metadata alone. Subclass `update()` still runs (pickup, despawn timers).
  **⚠️ SUPERSEDED (see status "STUCK-ARROW 1.8 DESYNC"):** radio silence was WRONG - it broke 1.8 self-heal. Vanilla
  never silences a stuck arrow; it re-asserts pos+rotation every `updateInterval`. We now keep movement frozen but do
  a periodic `resyncStuck()` re-assert (+ modern inGround metadata). Movement/scheduler still don't run while stuck.
- **Stick sync is delayed one tick** (Atlas trick): teleport scheduled next tick so a client/server
  hit-disagreement doesn't snap the arrow to the hit position prematurely. **(Now subsumed: the periodic
  `resyncStuck()` naturally fires its first teleport the tick after sticking; the explicit scheduler block was removed.)**
- **Entity collision**: bbox `growSymmetrically(0.3, 0.3, 0.3)` swept from `position` (= 1.8's target `grow(0.3)`
  each side, see §5 - was the `(0.1,0.3,0.1)`/`pos-0.3` desync bug; `expand` ≠ `growSymmetrically`), shooter immune
  for the first ~5 ticks (`SHOOTER_COLLISION_DELAY_TICKS`).
- **`synchronizePosition()` override**: absolute `EntityTeleportPacket` + velocity packet, and
  `lastSyncedPosition` updated manually. Minestom's default relative-move sync is wrong for 1.8 clients
  through Via — they never see the projectile move. This + the spawn-velocity policy is "the smooth vs
  jerky projectile" optimization.
- **`updateNewViewer()`** (relog / chunk-cross viewers): version-branched spawn packets. The spawn packet's
  `data` field must be `> 0` for ViaVersion to include velocity bytes in the 1.8 SpawnObject.
- **Rotation** is displacement-based (yaw/pitch from movement delta); latched at impact when sticking.
- **Unstuck check**: block at `collisionPoint + faceNormal * 0.5` is air. (An intersect-box check
  false-unsticks on fences/slabs.)

### 1.8 stuck-arrow desync fix (`compatibility/legacy_1_8/fix/LegacyProjectileCompat.java`)
> **⚠️ SUPERSEDED - this entire LegacyProjectileCompat approach (F7/F8/F12) is NOT being ported.** It worked around
> our F2 radio silence. The real fix is to match vanilla and never silence a stuck arrow (periodic `resyncStuck()`
> re-assert; see status "STUCK-ARROW 1.8 DESYNC"). The per-tick hints, edge pullback, and edge filter below are kept
> only as historical reference. Re-open ONLY if an in-game test shows a case the periodic re-assert can't heal.
- **Root cause**: the 1.8 client raycasts `pos -> pos + motion` to detect block hits. Stuck in a wall or
  ceiling, gravity points motion away from the surface -> the client never detects the hit -> arrow
  floats/slides.
- **Center hits**: per-tick velocity "hint" packets (flight direction, magnitude 1.0) to legacy viewers
  only. No correction visible — the arrow just stops.
- **Edge hits, two phases**: (1) NO packets — the 1.8 client predicts a natural arc; (2) after
  ~20 ticks, a one-time pullback teleport (0.1 blocks back along flight, original rotation preserved) and
  the hint switches to the face normal.
- Edge detection is shape-relative (works on fences/slabs), threshold 0.35 — **known-broken filter, port
  candidate for a rework** (`// TODO: this doesn't filter properly`).
- Relog: hint embedded in the spawn packet + immediate velocity reinforcement; zero view angles make the
  1.8 client derive rotation from motion.
- `stickGeneration` counter invalidates stale scheduled pullbacks after unstick/restick.
- User assessment: "good ish fix, could need optimizing."

### Fishing bobber (`entities/FishingBobber.java`)
- **Sink-into-floor desync fix** (legacy clients): spawn packet carries ZERO velocity so the client never
  predicts; the server's absolute teleport syncs drive all visible motion; explicit zero-velocity packet on
  landing stops residual prediction.
- **Medium-cast overshoot fix**: the server applies gravity BEFORE moving, the 1.8 client applies the
  received velocity as-is — so the client runs `gravity/TPS = 0.04` blocks/tick fast. Pre-apply the next
  tick's gravity to the velocity packet. (User: "there may be a better / more optimized solution" for the
  bobber overall.)
- Gravity constants: legacy 0.04 / modern 0.03, drag 0.92, gravity added pre-tick.
- **Minemen pseudo-hook** ("bobbers don't stick to players in 1.8"): on player hit -> hook for 1 tick (the
  client renders the line) -> unhook; re-hook for 1 tick whenever the victim MOVES (position, not look)
  until the rod retracts. `PSEUDO_HOOKED_BY` tag set on the victim; LEGACY mode also stops the bobber
  hooking other players after the first pseudo-hook. MODERN mode pins the bobber at the victim's face.
- Hooked non-players get pulled `0.1 * delta * TPS`; player-pulling is a config toggle.

### Launch / items (`features/Bow.java`, `features/FishingRod.java`, `components/ProjectileCreator.java`)
- The ITEM causes the SERVER to spawn the entity: bow uses `PlayerBeginItemUseEvent` /
  `PlayerCancelItemUseEvent` / slot-change; rod toggles cast/retrieve on `PlayerUseItemEvent`.
- Bow power `(s^2 + 2s)/3` capped at 1 (s = use seconds); < 0.1 cancels; >= 1.0 = critical.
- Arrow selection offhand -> main -> inventory scan; infinity/creative; enchants (Power -> base damage,
  Punch -> KB level, Flame -> fire ticks); pickup modes.
- **Spawn positions** (`utils/ProjectileCalculator.java`): arrows eye − 0.1; throwables eye + 0.1 forward
  − 0.05 down; bobber 0.3 in front at eye height (uses the legacy eye-height system).
- Projectile yaw/pitch derived FROM the final velocity (after spread/momentum) so visuals match flight.
- Spread = gaussian × 0.0075 × multiplier; optional shooter-momentum inheritance (horizontal only when
  grounded).
- Cleanup listeners: disconnect clears bow+rod state; death clears bow only (bobbers persist).

### Edge-case test matrix (run per fix before porting it)

| # | Old fix | Edge-case test that justifies it |
|---|---------|----------------------------------|
| F1 | Zero-size bounding box | Modern client: arrow shot into a wall - does it render `inGround` flush with the face, or float/jitter? Repeat with bbox 0.01/0.25. |
| F2 | Radio silence when stuck | Modern client: stuck arrow for 60s+ - any twitch when Minestom's periodic sync fires? |
| F3 | Resolved-position placement (not collision point) | Modern client: shots into walls/ceilings at shallow angles - arrow floating in front of the face? |
| F4 | One-tick delayed stick sync (Atlas) | Point-blank wall shots at high ping - does the arrow visibly snap/jump to the hit position? |
| F5 | Absolute-teleport position sync | 1.8 client via Via: does a flying arrow visibly move with Minestom's default relative sync? (Old result: it never moves.) |
| F6 | Spawn `data > 0` for Via velocity bytes | 1.8 client: does a freshly spawned projectile fly smoothly or sit still until first teleport? |
| F7 | Legacy stuck hints (center) | 1.8 client: arrow into wall/ceiling center - float/slide without per-tick hints? (Root cause is structural: client raycast needs motion into the block. Likely needed.) |
| F8 | Edge pullback two-phase | 1.8 client: arrow into block edges/corners - with only F7 hints, does the client miss the surface? Old filter is known-broken; re-derive threshold. |
| F9 | Bobber zero-velocity spawn + teleport-driven motion | 1.8 client: cast bobber - does it sink into the floor with normal velocity-driven sync? |
| F10 | Bobber gravity pre-application in velocity packets | 1.8 client: medium-range cast - does the bobber visually overshoot the landing point by ~0.04 b/t drift? |
| F11 | Pseudo-hook re-hook on move | Visual: does the line render persistently on a moving victim with plain hook metadata? (This is a feature - Minemen mode - not a fix.) |
| F12 | Stuck-arrow relog spawn (hint-in-spawn, zero view) | 1.8 client: relog / walk far away and return while an arrow is stuck - floats or sticks? |
| F13 | Chunk-cross viewer handling (`updateNewViewer`) | Both clients: shoot across a chunk border into an unseen chunk; walk in - projectile state correct? |

## 2. Target architecture (this library's idiom)

```
mechanics/projectile/
  ProjectileSystem           install(mm, ProjectileConfig, Shootable...) — config enables self-launching types
                             (typeConfigs presence, like DamageSystem.install); Shootables are the item launchers
  ProjectileConfig           generic defaults + per-type ProjectileTypeConfig map (extends Config; mirrors DamageConfig)
  ProjectileTypeConfig       per-type FieldValue knobs, extends Config<ProjectileContext, Self> like AttackConfig
  ProjectileConfigResolver   ProjectileContext -> ResolvedFlight (launch) + ResolvedHit (impact)
  ProjectileSnapshot         shooter, item, type, power, spawn/velocity overrides, config override
  shootables/                pluggable item launchers (the attack `HitDetection` analog): Shootable, Bow
  types/                     projectile identity + behavior: ProjectileType, Arrow (pure identity),
                             ThrowableItemType (self-launching base) + Snowball/Egg/Pearl
  entities/                  ProjectileEntity, ManagedProjectile, ArrowEntity, PearlEntity (egg uses the default
                             ManagedProjectile - its chicken easter egg is now an example ProjectileBehavior)
api/event/                   ProjectileLaunchEvent (spawn: cancellable, spawn/velocity mutable, resolvedFlight()),
                             ProjectileHitEvent (entity/block: cancellable, resolvedHit() + per-hit overrides)
```

> **Launcher vs self-launching type (the key split):** a bow/crossbow/rod is an ITEM that fires a projectile -
> modeled as a pluggable `Shootable` (mirrors `AttackSystem`'s `HitDetection`), passed to `install(mm, cfg, new Bow())`.
> A snowball/egg/pearl IS the thrown item - a self-launching `ProjectileType` (`ThrowableItemType`) that wires its own
> use trigger and is enabled by its config entry. Arrow is pure identity; the `Bow` Shootable fires it.
>
> **Three event surfaces (consistent with Damage/Knockback/Attack):** SPAWN = `ProjectileLaunchEvent` (cancel /
> redirect spawn+velocity / attach cosmetics, `resolvedFlight()` preview); FLIGHT folds INTO spawn (attach a trail or
> use `ProjectileBehavior.onTick` - no separate per-tick event); HIT = `ProjectileHitEvent` (`resolvedHit()` preview +
> per-hit overrides: `damage`, `knockback`/`knockbackSource`, `removeOnHit`, and `response(...)` to force the outcome).
>
> **Per-projectile behavior without subclassing:** attach a `ProjectileBehavior` (no-op default lifecycle hooks
> `onSpawn`/`onTick`/`onImpact`/`onDeflect`/`onStuck`/`onUnstuck`/`onRemove`) as the config `behavior` knob or per-launch
> via `ProjectileSnapshot.withBehavior`; `ManagedProjectile` delegates to it additively (the type's built-in effects
> still run). The projectile analog of the attack `Ruleset`. `ProjectileTypeConfig` extends the generic `Config<Ctx,Self>`
> base like `AttackConfig` (public `FieldValue` fields, `merge`); knobs include `momentumHorizontal`/`momentumVertical`
> (scales over a `VelocityRule` source, resolved config -> shooter profile -> DEFAULT like knockback), `deflect(...)`,
> `physicsOrder`, `leftOwnerImmunity`, `stickPullback`, `shakeTicks`, and the renamed `spawnOffset(forward, vertical, sideways)`.
> (The `deflectParticles` cosmetic crit-trail moved OUT to the `compatibility/` package - `LegacyArrowVisibilityConfig.deflectParticles`,
> resolved per-shooter - since it's a 1.8-visual compat cosmetic, grouped with the arrow-visibility fix.)

**Per-type configurability requirement** (parity with the old system, expressed as `FieldValue` knobs on
`ProjectileTypeConfig` - constants or per-context lambdas like every other config in the lib):
bounding box size, spawn offset (forward/vertical, eye-height source), initial speed/power curve,
spread, shooter-momentum inheritance, aerodynamics (gravity + horizontal/vertical drag), shooter-immunity
ticks, sync interval, knockback config, damage amount/type, pickup mode + delay, despawn ticks, and the
legacy-compat toggles (hint magnitude / pullback / edge threshold; bobber fix mode). The entity classes
stay dumb - launchers resolve the config and stamp the entity (aerodynamics, bbox, sync interval) at spawn.
Performance: resolve once per launch, not per tick; per-tick reads (aerodynamics) live on the entity.

Integration decisions (keep responsibilities where they already live):
- **Knockback**: NO separate ProjectileKnockbackConfig. Hits route through `KnockbackSystem.apply` with a
  `KnockbackSnapshot` (origin = projectile position or shooterOriginPos, direction = flight direction,
  melee = false) carrying a per-type `KnockbackConfig`. quantizeVelocity / entityPush / velocity-rule knobs
  come along free. (Old lib's bobber-relative vs shooter-relative KB = snapshot origin choice.)
- **Damage**: arrow/thrown hits emit `DamageSnapshot`s through new `DamageType`s (`minecraft:arrow`,
  `minecraft:thrown` ...), so invul windows, overdamage, hurt broadcast, and the event layer all apply
  unchanged. Punch/Power live in the type's producer like melee's crit/sprint logic.
- **Version branching**: `ClientInfoTracker.getProtocol` replaces the old ClientVersionDetector.
- **Velocity tracking**: projectiles are server-authoritative entities — `mm.velocity()` and MotionTracker
  are NOT involved; the entity's own velocity is the truth (the simulated() non-player path already returns it).
- **No TickScaler port** initially: the lib elsewhere assumes `ServerFlag.SERVER_TICKS_PER_SECOND` (see
  DamageSystem's TPS TODO); TPS scaling is one coherent later pass.
- **Per-item overrides**: prefer config lambdas reading `ctx.item()` (consistent with everything else)
  over the old item-NBT-tag system (`ProjectileTagRegistry`/`VelocityTagValue`) — revisit only if per-item
  runtime data (not config) is actually needed.

## Status (HANDOFF - read this first)

All paths below are relative to repo root `C:\Users\Gabriel\Documents\GitHub\MinestomMechanics`. Build/verify:
`cd` to root, `./gradlew compileJava compileTestJava --console=plain -q` (PowerShell tool, the user is on
Windows). Minestom source (read-only, for API) is the sibling repo `../Minestom`; vanilla 1.8.8 ref is
`C:\Users\Gabriel\Desktop\Development\paper_source_1.8.8` and modern is `...\26.1-src`. The old (messy but
working) projectile impl to port from: `C:\Users\Gabriel\Documents\GitHub\minestom-mechanics-lib\minestom-mechanics-lib\src\main\java\com\minestom\mechanics\`.

### Polish pass (config/event/parity refactor - COMPLETE, compiling)
- **`DeflectRule` interface removed** → a `deflect(mul, turn, min, max)` knob backed by the nested `ProjectileTypeConfig.Deflect`
  record (like `PickupBox`); transform inline in `ManagedProjectile`. 1.8 = `deflect(-0.1)`, 26.1 = `deflect(-0.5, 0, -10, 10)`.
- **Parity knobs added** (all 1.8↔26.1 deltas now expressible): `physicsOrder` (DRAG_AFTER/BEFORE_MOVE), `leftOwnerImmunity`
  (geometric vs tick), `stickPullback`, arrow `shakeTicks`. The egg's chicken easter egg was REMOVED from core (it's an
  example `ProjectileBehavior` in `ExampleServer`); `EggEntity` deleted.
- **`ProjectileBehavior` lifecycle** rounded out: `onSpawn`/`onTick`/`onImpact`/`onDeflect`/`onStuck`/`onUnstuck`/`onRemove`.
- **Events aligned with Damage/Knockback/Attack**: `ProjectileHitEvent` exposes `resolvedHit()` + per-hit overrides
  (`damage`/`knockback`/`knockbackSource`/`removeOnHit`/`response`); `ProjectileLaunchEvent` exposes `resolvedFlight()`.
  Three surfaces: spawn (launch) / flight (folded in) / hit.
- **Modern preset** `Vanilla.projectiles()`/`projectileDefaults()`/`arrow()` (26.1 deltas over the `Vanilla18` base).
- **Stuck-arrow count**: `StuckArrows` (count/set/add/clear + a lazy global decay task) + `ArrowEntity.onImpact` increment.
- **Profiles**: per-shooter projectile config resolves player→instance→global via `MechanicsProfiles.projectilesFor`
  (used by `ProjectileSystem.configFor`) - same chain as every other system.
- **Custom items - TWO equivalent paths** (the lib never needs to know your items):
  - **Events (preferred for minigames):** `ProjectileHitEvent` per-hit overrides (`damage`/`knockback`/`response`/...)
    for impact behavior; `ProjectileLaunchEvent.behavior(...)`/`setVelocity`/entity tweaks for spawn/flight. One listener
    switches on `snapshot().item()` (stash an id on the item, map id->behavior in your own registry - never N lambdas).
    See the event-driven heavy-snowball example in `ExampleServer`.
  - **Config:** per-knob `ctx.item()` lambdas or one `subConfig(ctx -> registry.get(ctx.item()))` overlay - best for
    flight physics (resolved before spawn). The egg's chicken easter egg (config `behavior` knob) is the example.
- **VANILLA PARITY (audited + cited):** 1.8 throwables EXACT vs `EntityProjectile` (spawn `-0.16`/`-0.1`, speed `1.5`
  `j()`, spread `0.0075`, drag `0.99` + gravity `0.03` AFTER the move, 5-tick immunity, 0.3 grow, no momentum). 26.1:
  `ThrowableProjectile` applies gravity+drag BEFORE the move; `Projectile.shootFromRotation` adds `getKnownMovement`
  (h always, v airborne); `ProjectileDeflection.REVERSE` = `scale(-0.5)` + yaw `170+rand*20`; `leftOwner` re-checks each
  tick with `inflate(1.0)`. All matched by the modern preset/knobs. (Open nuance: 26.1 spread is `triangle`, not gaussian
  - see lingering TODOs.)

### Built and compiling
- **Entity core** - `src/main/java/.../mechanics/projectile/entities/ProjectileEntity.java`: swept block
  physics, block stick (movement frozen, but a vanilla-style periodic `resyncStuck()` re-assert - NOT radio silence,
  see status "STUCK-ARROW 1.8 DESYNC"), entity hits, absolute-teleport sync, viewer spawn. Velocity is **b/t
  internally** (`velocityBt`), mirrored to `super.velocity` in b/s. The legacy stuck-sync (F7/F8/F12) is no longer
  needed - the periodic re-assert + modern inGround metadata cover both clients. Block-hit detection is swept-only
  (`handlePhysics`); a 1.8 `world.rayTrace` knob (`BlockRaytrace`) was built then deleted as indistinguishable (3f).
- **Config/event layer** (mirrors the damage system exactly): `mechanics/projectile/ProjectileConfig.java`
  (generic `defaults` config + per-type map, presence-enables), `types/ProjectileTypeConfig.java` (ALL per-type
  knobs as `FieldValue`: bbox, aerodynamics, `spawnOffsetH`/`spawnOffsetV` (+ combined `spawnOffset(h,v)`), speed,
  spread, momentum, immunity, sync, knockback, `knockbackSource` enum {PROJECTILE, SHOOTER}, damage, damageType,
  remove-on-hit; has keyless `builder()` / `builder(base)` / `toBuilder()` for composition), `ProjectileConfigResolver.java`
  (context + resolved + hard fallbacks; **chain = per-type override -> config `defaults` -> type intrinsic -> fallbacks**),
  `ProjectileSnapshot.java`, `ProjectileSystem.java` (launch: resolve->spawn/velocity->`ProjectileLaunchEvent`->stamp+spawn;
  register/enable like `DamageSystem.install`), `types/ProjectileType.java` (abstract; config-free 3-arg ctor + optional
  intrinsic-defaults 4-arg ctor; `createEntity` + `enable`/`disable`), `entities/ManagedProjectile.java`
  (the generic hit handler). API events: `api/event/ProjectileLaunchEvent.java`, `.../ProjectileHitEvent.java`.
- **Throwable types** - `types/{ThrowableItemType,Snowball,Egg,Pearl}.java`: `ThrowableItemType` (shared base)
  wires the item-use throw + consume; the three types are **config-free** (entity + which item only) and share one
  baseline. ALL tuning is in `Vanilla18.projectileDefaults()` (researched vanilla 1.8: `speed 1.5`, `spread 0.0075`,
  `gravity 0.03`, `drag 0.99`, `inheritMomentum=false`, spawn `-0.1`V + `0.16` lateral, `ignoreShooterHit=true`,
  `knockbackSource=SHOOTER`, vanilla KB, `damage 0` via `ProjectileDamage`). Pearl adds
  `entities/PearlEntity.java` (shooter teleport via the `ManagedProjectile.onImpact(hitEntity)` hook, which fires on
  entity AND block impact); the egg uses the default `ManagedProjectile` (its baby-chicken easter egg moved to an
  example `ProjectileBehavior` in `ExampleServer` - it is no longer core).
- **Momentum**: `ProjectileSystem.launchVelocity` folds `MotionTracker.positionDelta(shooter)` (= 26.1
  `getKnownMovement`), NOT `getVelocity()`; horizontal always, vertical only if airborne. Default `false` (1.8). §5.
- **Hit knobs resolve at IMPACT** (the big restructure): `ProjectileConfigResolver` split into `resolveFlight` (launch:
  spawn/physics) + `resolveHit` (impact: damage/knockback/selfHits/removal) against a `ProjectileContext` carrying
  `target` / `isSelfHit` / `throwOrigin` / `hitPos`. `ManagedProjectile` stores the merged `effectiveConfig` + snapshot
  and resolves the hit knobs each hit. This makes self-vs-other a plain config lambda (`ctx -> ctx.isSelfHit() ? ...`),
  no dedicated `SelfHit` record. `createEntity(shooter, snap, effectiveConfig)` now takes the merged type config.
- **Self-hit**: `selfHit` (`HitResponse` {HIT, PASS_THROUGH, DEFLECT}, default HIT) - PASS_THROUGH ignores + flies on,
  DEFLECT bounces off (reverse+damp); constant-friendly (pearl = PASS_THROUGH). The enum generalizes to any "not
  allowed to hit" case. Richer self behavior is a lambda on the hit knobs. `KnockbackSource.SHOOTER` carries
  `source = shooter` so the KB config's `yawWeight` does shooter->victim vs yaw (dropped `SHOOTER_LOOK`).
  `throwOrigin` exposed on `ctx` + the event. §5.
- **Spawn/velocity research**: §5 has the exact 1.8 spawn (eye, `0.16` lateral, `0.1` down), velocity (`1.5` +
  `0.0075` spread), collision (point raytrace; size `0.25` is render-only -> our F1 box 0 matches), physics order
  (1.8 post-move vs 26.1 pre-move), and the 1.8->26.1 delta table for the future modern preset.
- **Pearl**: teleport + `FallDamage.resetFallDistance(shooter)` (DONE) + flat 5 (vanilla is a CONSTANT, FALL-typed;
  via `GenericDamage` stand-in for now). TODOs: dedicated FALL/enderPearl damage type (the 1.8-vs-26 type delta),
  5% endermite, cross-instance teleport.
- **Bow + arrow (phase 4)**: `types/Arrow.java` is PURE IDENTITY (createEntity only); the bow ITEM is a pluggable
  `Shootable` launcher in `shootables/Bow.java` (draw `PlayerCancelItemUseEvent` -> power `(s^2+2s)/3` -> consume arrow ->
  launch + set crit/pickup), passed to `ProjectileSystem.install(mm, cfg, new Bow())` - the attack `HitDetection` analog.
  `entities/ArrowEntity.java` (velocity-based damage `ceil(speed*2)+crit`, `setCritical` -> meta crit particles, sticks
  in blocks via the inherited machinery, pickup-while-stuck). `Vanilla18.arrow()`: speed 3.0, gravity 0.05, SHOOTER-relative KB, stick.
  (KB is SHOOTER, not PROJECTILE: 1.8 `damageEntity` knocks away from `DamageSource.arrow.getEntity()` = the shooter,
  not the arrow's flight; verified in source. Punch adds a separate motion-direction KB - TODO.)
  Velocity-based damage uses the new `ManagedProjectile.hitDamage(hit, target)` override hook. **TODO**: gate draw
  on having arrows; offhand-first selection + Infinity; Power/Punch/Flame enchants; pickup delay + survival/creative
  mode; dedicated `minecraft:arrow` damage type; deflect refinement. **Verify in-game**: bow release actually fires
  `PlayerCancelItemUseEvent`.
- **API - two lifecycle events** (merged `Launch`+`Spawn` - they were confusingly similar): `ProjectileLaunchEvent`
  now fires with the BUILT-but-not-yet-spawned `projectile()` entity (Bukkit-style) - cancel discards it, mutate
  spawn/velocity to redirect, or keep the reference to attach a trail/behavior (tasks run next tick once it's in the
  instance). `ProjectileHitEvent` (hit/land - `throwOrigin`/`isSelfHit`/`hitPoint`; spawn-entity-on-land etc.). The
  projectile's damage/KB also fire their own `Damage`/`KnockbackEvent` for per-hit tweaks.
- **LIFECYCLE (vanilla, exact)**: throwables (snowball/egg/pearl = `EntityProjectile`) `die()` on ANY hit (block OR
  entity) - they do their effect then despawn, NEVER stick. ONLY arrows (`EntityArrow`) stick in blocks
  (`inGround`, `shake=7`, despawn after 1200 ticks) and break on an entity hit (or DEFLECT off an invuln target).
  So in our config `removeOnBlockHit=true` = break (throwables), `false` = stick (arrows).
- **CRASH FIX + breaker regression**: removing the entity inside `movementTick` nulled `instance` before
  `Entity.tick`'s `touchTick` -> NPE; my first attempt (`scheduleNextProcess`) then entered the stuck state first,
  so the deferred remove never ran in radio silence -> ALL throwables stuck forever. Fixed properly: `stick()` now
  decides break-vs-stick (from `onStuck()`'s `removeOnBlockHit`) BEFORE any stuck state; a breaker sets a
  `pendingRemove` flag and `tick()` removes AFTER `super.tick()` (post-`touchTick`), never going radio-silent. The
  void + entity-hit removals use the same flag. (The old impl avoided the NPE only via immediate `remove()` on an
  older Minestom that deferred the instance-null.)
- **Arrow pickup cooldown**: 1.8 `EntityArrow.shake = 7` on stick gates pickup (`inGround && shake <= 0`).
  `ArrowEntity` now sets a 7-tick `shake` in `onStuck` and blocks pickup until it ends.
- **STUCK-ARROW 1.8 DESYNC (relog rotation + edge-hit overshoot) - ROOT-CAUSED + FIXED (this session). The cause was
  our own F2 RADIO SILENCE, not a 1.8 patch we were missing.** User test that cracked it: a 1.8 client on a *vanilla
  Paper 26 + Via* server does NOT have these bugs - stuck arrows look briefly weird then self-heal, exactly like a
  native 1.8 server. So vanilla's sync model already handles 1.8; ours diverged.
  - **Mechanism (vanilla `ServerEntity.sendChanges`, 26.1):** a stuck arrow is NEVER silent. The broadcaster re-asserts
    position **+ rotation** every `updateInterval` - note the explicit `&& !(this.entity instanceof AbstractArrow)`
    that DISABLES the pos-only/rot-only branch for arrows, forcing them into the `MoveEntity.PosRot` (position +
    rotation) branch - plus a forced position update every 60t (`tickCount % 60`), a full teleport every 400t
    (`teleportDelay > 400`), and a one-time zero-velocity `SetEntityMotion` when the arrow stops. That continuous
    re-assert is the ONLY thing that corrects a 1.8 client: it has no authoritative inGround stop (its `inGround` is
    self-raytraced and its `t_()` re-derives rotation from motion on a fresh spawn), so without periodic re-syncs a
    mispredicted stuck arrow (relog angle, edge-hit overshoot) never recovers.
  - **Our bug:** F2 made a stuck projectile radio-silent (`super.tick()` skipped, `synchronizePosition` early-returns)
    on the theory that the modern client holds via inGround metadata. But (a) we never actually set inGround until this
    session, and (b) radio silence killed the 1.8 self-heal entirely.
  - **Fix:** keep movement frozen, but replace radio silence with a vanilla-style **periodic re-assert**:
    `ProjectileEntity.tick()` now calls `resyncStuck()` (absolute `EntityTeleportPacket` with current pos + rotation +
    zero velocity, plus a zero `EntityVelocityPacket`) every `getSynchronizationTicks()` (=20, matching vanilla's arrow
    `updateInterval`), starting the tick after sticking (which also subsumes the old one-tick-delayed Atlas F4 teleport
    - that scheduler block is removed). `getVelocityForPacket()` now reports ZERO while stuck. The **modern** client
    stays frozen via the inGround metadata (`ArrowEntity.onStuck/onUnstuck`, kept), so each re-assert is a no-op for it
    (same pos+rotation, zero velocity does not unground); the **1.8** client self-heals from the periodic teleports,
    matching vanilla.
  - **This SUPERSEDES the whole legacy stuck-sync plan:** F7 (per-tick center hints), F8 (edge pullback), and F12 (the
    legacy relog spawn hint) are no longer needed - they were all working around the absence of the vanilla re-assert.
    The F12 spawn-hint code added earlier this session was REVERTED (incl. `stuckFlightDir` / `isLegacyViewer` /
    `LEGACY_HINT_MAGNITUDE`); `updateNewViewer` is back to a single version-agnostic spawn. (`ClientInfoTracker.isLegacy`
    + `LEGACY_PROTOCOL_MAX` were kept - `SilentDamage` uses them.)
  - **Tunable:** the self-heal latency is the sync interval (20t = up to ~1s, vanilla parity). If snappier relog is
    wanted later, lower `syncInterval` for arrows or trigger an extra `resyncStuck()` on `updateNewViewer` while stuck.
- **Hit model (vanilla-faithful)**: `ManagedProjectile.onHit` routes through the **damage system first** with
  a 0-damage snapshot, then applies knockback **only if it landed**. The 0-damage hit still plays hurt + opens
  the invul window (the GATE) because `damage/types/projectile/ProjectileDamage.java` (`minecraft:thrown`,
  `baseAmount 0`, `triggersInvul` on) and `DamageSystem` now lets a 0-damage hit land when its type triggers
  invul. Projectile + melee count as `knockbackOwnsVelocity` (no double hurt-velocity broadcast). The
  projectile still breaks on an invul'd victim (no KB).
- **Velocity tracking is a profile member** (not `mm.velocity()`, which is DELETED): `MechanicsProfile.velocity`
  / `MechanicsProfiles.velocityFor(victim)`; `KnockbackCalculator` resolves `KnockbackConfig.velocity` ->
  profile velocity -> `VelocityRule.DEFAULT`. Presets expose `Vanilla18.velocity()`, `Hypixel.velocity()`,
  `Minemen.velocity()`. `MechanicsProfile.projectiles` + `projectilesFor` added too.
  - **Velocity is ONE per-player thing, not a per-system tracker.** `VelocityRule` (simulated/delta/closed/split)
    is a *stateless read strategy* over the single `MotionTracker` (one per-tick tracker for all players); switching
    or mixing modes costs nothing per tick. "Multiple velocities per player" was a non-issue. Profile = the single
    source of truth; melee KB, **projectile KB**, and the hurt broadcast all inherit it via `velocityFor(victim)`.
  - **Resolved velocity is threaded onto `KnockbackContext`** (`ctx.velocityRule()` / `ctx.victimVelocity()`):
    `KnockbackCalculator` resolves the rule once (config -> profile -> DEFAULT) and hands it to the custom
    `KnockbackComponent`s via `ctx.withVelocity(...)`, so a component reads the SAME velocity the friction fold
    used instead of hard-pinning a static rule. `Minemen.kb()` no longer sets `.velocity(...)`; its axial-drag /
    cap-hold components read `ctx.victimVelocity()` and it relies on `MechanicsProfile.velocity(Minemen.velocity())`.
    This removed the melee-vs-projectile velocity divergence (the projectile carries its own `Vanilla18.kb()`, so a
    velocity pinned on a melee KB config never reached it - only the profile channel does).
- **Clean install**: `ProjectileSystem.install(mm, Minemen.projectiles())` (test server uses this).
  `Vanilla18.projectiles()` = generic `defaults` + `snowball()` entry; `Minemen.projectiles()` re-bases from it
  (`builder(Vanilla18.projectiles())`, vanilla projectile behavior today, seam to give projectiles Minemen feel).
  Profile-scoped via `projectilesFor`.

### Decisions locked (from the user)
1. Projectile KB = plain `KnockbackConfig` through `KnockbackSystem.apply`. 2. Per-item override = config
lambdas; **base/vanilla configs must stay constant-only (no lambdas)**; custom-item NBT deferred to the future
attributes system. 3. No TickScaler yet, but all timing stays tick-denominated so it's addable.
4. **Velocity tracking = one per-player rule set ONCE on a profile scope** (instance/global/player), inherited by
every system (melee KB, projectile KB, hurt broadcast). `KnockbackConfig.velocity` stays as a rare per-config
override; components read the resolved rule via `ctx.victimVelocity()` (no static pinning). NOT building a registry
of stateful per-scope trackers - the single `MotionTracker` + stateless `VelocityRule` reads already give "one
velocity per player" for free. Revisit only if a future mode needs its own per-tick state.
5. **Projectile config lives in the PRESETS, never in the type class.** Types are identity + behavior (config-free
`ProjectileType(key, name, entityType)`); `Vanilla18`/`Minemen` own the tuning. A `ProjectileConfig` carries a
generic `defaults` (the shared baseline, applied to every type) plus sparse per-type overrides, so a type entry
restates only its deltas (`ProjectileTypeConfig.builder(KEY).damage(x)`) or is empty just to enable. Resolution:
per-type -> generic `defaults` -> type intrinsic -> hard fallbacks. Spawn offset is `spawnOffsetH`/`spawnOffsetV`
(+ combined `spawnOffset(h,v)`).
6. **Projectile config resolves in TWO phases**: flight knobs at launch, hit knobs at IMPACT (against a
`ProjectileContext` with `target`/`isSelfHit`/`throwOrigin`/`hitPos`). Self-vs-other and throw-time behavior are
plain config lambdas (`ctx -> ctx.isSelfHit() ? ...`), NOT a dedicated structure or the event API; `selfHits(false)`
is the one constant knob (pass-through-self) so vanilla stays constant-only. KB yaw is `KnockbackSource.SHOOTER`
(source = shooter) + `yawWeight`, never a `SHOOTER_LOOK` enum. The event API stays for genuinely dynamic per-hit logic.

### Immediate next (rough order)
1. **In-game test snowball/egg/pearl** (user does this): flight, shooter-relative KB, hurt flash + invul gate
   (rapid throws - only ~1 per window applies KB), break-on-hit; egg baby-chicken (1/8); pearl teleport + 5 fall
   damage + the 1.8 self-pass-through (throw straight up -> it falls through you -> teleports on landing). Watch
   the 1.8 client for desync to decide which F-ledger fixes are needed. NOTE: verify `living.damage(0)` plays the
   hurt flash; if not, force the hurt status packet. Verify the pearl 5-damage (GenericDamage stand-in) hurts + respects invul.
2. **Egg + pearl DONE** (this session). Remaining polish: pearl fall-reset / endermite / dedicated damage type +
   cross-instance; egg chicken has no AI (plain Entity); snowball blaze-3 (needs entity-type-aware damage).
3. **Bow + arrow CORE DONE** (draw/power, `ArrowEntity` velocity-damage + crit + stick + 7-tick pickup cooldown,
   `Vanilla18.arrow()`). OPEN, roughly in priority:
   a. **Stuck-arrow 1.8 desync (relog rotation + edge-hit overshoot) - DONE.** Root cause was our F2 radio silence;
      fix = vanilla-style periodic `resyncStuck()` re-assert + modern inGround metadata. See the status bullet
      "STUCK-ARROW 1.8 DESYNC". This SUPERSEDED F7/F8/F12 (legacy stuck-sync no longer needed). User-confirmed working.
      **UNSTUCK on a relogged 1.8 client (freeze-before-fall) - ACCEPTED, not fixed:** a 1.8 arrow that is client-side
      `inGround` IGNORES position teleports, and a relog-spawned stuck arrow holds a stale inGround state, so when the
      block breaks it briefly freezes at the old spot before falling. A tried `unstick()` re-spawn fixed it but the
      user disliked it; this same behavior is present on vanilla 26+Via (and occasionally vanilla 1.8), so it's
      vanilla-accurate and we keep it. `unstick()` is back to the simple form (just a NOTE comment).
   b. **Arrow pickup - DONE (animation + vanilla bbox + player-only scan + pickup MODE).** `ArrowEntity` sends
      `CollectItemPacket` before `remove()` (arrow flies into the player, vanilla `player.take`). Geometry is the exact
      vanilla AABB overlap: the arrow's `0.5 x 0.5` box (1.8 `setSize(0.5,0.5)`; our collision box is 0 per F1) vs the
      player's bbox inflated `(1.0, 0.5, 1.0)` - identical in 1.8 `EntityHuman` (`grow(1,0.5,1)`) and 26.1
      `Player#aiStep` (`inflate(1,0.5,1)`). Scan is `EntityTracker.Target.PLAYERS` (nearby PLAYERS only) + the box test.
      Pickup who/what is now `ArrowEntity.Pickup` {DISALLOWED, ALLOWED, CREATIVE_ONLY} (replaced the ad-hoc `pickupable`
      boolean; the bow sets ALLOWED survival / CREATIVE_ONLY creative - mirrors 1.8 `fromPlayer`). Geometry is now the
      `ProjectileTypeConfig.PickupBox` knob (inflateH/V + arrow box; default vanilla), stamped on `ArrowEntity`. The
      scan radius is DERIVED from the geometry (vanilla has no scan radius - it iterates players in the grown box;
      Minestom's entity query is radius-based, so the radius is only a broad-phase pre-filter and `withinPickupBox` is
      the exact 1:1 test). TODO: inventory-full = no pickup; "random.pop" sound.
   c. **ENTITY-HIT DESYNC - FIXED + made configurable (this session).** 1.8 grows the TARGET by 0.3 each side and
      ray-tests the flight path; we were too tight, so arrows the 1.8 client predicts as a hit flew past server-side
      ("arrow disappears for shooter / bounces for target"). Now `growSymmetrically(entityHitGrow,...)` from `position`
      (§5). `entityHitGrow` is a per-type `ProjectileTypeConfig` flight knob (default 0.3, stamped at launch). Arrow KB
      is also SHOOTER-relative now (1.8 `damageEntity` knocks from `DamageSource.arrow.getEntity()` = the shooter).
   d. **Invuln-hit response + deflection + pass-through 1:1 - DONE.** When a projectile's hit is BLOCKED (target
      invulnerable / creative), the response is the `invulnHit` config knob (`HitResponse` {HIT, PASS_THROUGH, DEFLECT,
      DESTROY}, the same enum as `selfHit`): vanilla arrows `DEFLECT` (bounce `motion *= -0.1`), throwables `DESTROY`
      (break/effect, like their `die()`). This REPLACED the ad-hoc `deflectOnInvuln` boolean (kept types config-free;
      vanilla values live in `Vanilla18.arrow()`/`projectileDefaults()`). "Bypass" is deliberately NOT a projectile
      response - it's the damage type's `bypassImmune` / `bypassInvul` (a bypassing type makes the hit LAND, never reaching `invulnHit`).
      `deflect()` does `-0.1` + re-arms shooter immunity (vanilla `as = 0`) + clamps placement to the hit point (no
      overshoot). **CREATIVE FIX:** `DamageSystem.apply` now treats CREATIVE/SPECTATOR targets as invulnerable
      (BLOCKED) - they took KB + arrows never deflected before, because `apply` had no game-mode check (only the
      environmental producers did). Pearl pass-through verified 1:1 (1.8 `EntityEnderPearl.a`: `if (hit == shooter)
      return;` = `selfHit(PASS_THROUGH)`); **pearl yaw fix** - the teleport keeps the shooter's view (vanilla
      `enderTeleportTo` is x/y/z only; was overwriting yaw with the pearl's flight rotation). Remaining: 1200-tick
      despawn; Power/Punch/Flame; offhand/Infinity selection; draw-gate.
   e. **Post-stick 0.05 pull-back - DONE; flight-sync throttle (O1) - REVERTED (this session).** Stick pulls the arrow
      back 0.05 along the flight direction (vanilla 1.8 `locX -= motX/|mot|*0.05`; 26.1 per-axis) so the tip pokes out
      of the block face - `stuckPlacement`. (No visible "nudge" - the 1.8 client pulls back 0.05 itself, so client +
      server agree.) **O1 (throttle the in-flight teleport to `syncInterval`) is KEPT** - it IS the vanilla cadence
      (sparse absolute teleport + client prediction). A per-tick teleport was tried to kill the edge drift but it SHAKES
      the client, so it was reverted; see 3g (FLIGHT SYNC).
   f. **BLOCK-COLLISION + FLIGHT-SYNC - SETTLED on the VANILLA MODEL (RAYTRACE tried then DELETED).** A 1.8 client runs
      its OWN projectile physics + block raytrace each tick (`EntityArrow.t_` / `EntityProjectile.t_`); the server only
      corrects it with packets. So edge-stick + deflect rendering are governed by the CLIENT's prediction, not by how the
      SERVER detects hits.
      - **Vanilla broadcast** (1.8 `EntityTracker`/`EntityTrackerEntry`, 26.1 `ServerEntity` - §5 "Entity tracking"): a
        projectile gets an ABSOLUTE TELEPORT only every `updateInterval` (arrow 20t, throwable 10t - they move >4
        blocks/interval so vanilla's relative-move path always falls back to teleport), forced pos+rotation, NO arrow
        velocity in flight (`ai` is set only by knockback `g()`, not normal flight); the CLIENT predicts the arc between.
        It can be that sparse because the 1.8 client's physics are BIT-IDENTICAL to the server's, staying in lockstep.
      - **A `blockCollision` `{SWEPT, RAYTRACE}` knob + `BlockRaytrace` (1.8 `world.rayTrace` DDA) was BUILT then DELETED**
        - it was INDISTINGUISHABLE in-game. For our zero-size box the swept sweep ≈ a ray, so server detection was never
        the divergence; the 1.8 CLIENT mispredicts the edge from its own drifted prediction, which the server's detection
        method can't fix. We're back to swept-only (`handlePhysics`).
      - **Edge "hit wall -> slide -> snap" = ROOT-CAUSED + FIXED (the per-tick velocity packet).** The drift was NOT
        physics: it's that Minestom's `Entity.tick` re-broadcasts the arrow's velocity EVERY tick, whereas vanilla 1.8
        registers arrows `sendVelocity=false` and sends velocity ONLY ONCE - in the spawn packet (`PacketPlayOutSpawnEntity`
        with `data > 0` -> `motX*8000`) - then never again, so the 1.8 client predicts the WHOLE flight locally in lockstep
        with the server. Our per-tick velocity packets, arriving lagged + quantized over Via, repeatedly overwrite that
        clean local prediction, so the client's OWN raytrace mispredicts the edge (slide) AND its bounce prediction drifts
        (the deflect "looks wrong to the target"). This is exactly why RAYTRACE made no difference - the CLIENT decides
        `inGround` from its prediction, not the server's detection. **Fix:** `ProjectileEntity.sendPacketToViewers` drops
        the automatic per-tick `EntityVelocityPacket` (an `allowVelocityPacket` flag lets our deliberate sends - the
        spawn velocity rides the `SpawnEntityPacket`, the stuck zero rides `sendVelocityToViewers` - through). Now matches
        vanilla: velocity once at spawn, client predicts in lockstep. **CONFIGURABLE:** the `velocitySyncInterval` flight
        knob - `<= 0` (resolver default) = never (vanilla arrow / the fix), `1` = every tick (Minestom default), `N` =
        every N ticks. User-confirmed "much better." (Arrow BLOCK collision box double-checked against 1.8: it's a pure
        POINT raytrace - `world.rayTrace(Vec3D(locX,locY,locZ), ..)` - so our zero-size box (F1) is correct; the
        `setSize(0.5,0.5)` is render + entity-hit + pickup only, never block sticking.)
      - **EXPLICIT velocity changes still broadcast (vanilla `velocityChanged` parity).** A ViaRewind maintainer flagged
        that dropping ALL motion packets would break plugins that re-set arrow velocity mid-flight (e.g. HomingArrow) -
        correct, and the 1.8 source proves the split: `sendVelocity=false` only gates the AUTOMATIC per-tick velocity;
        `EntityTrackerEntry` line ~246 broadcasts `velocityChanged` EVERY tick OUTSIDE that gate (set by `g()`/setVelocity/
        knockback), so a deliberate redirect ALWAYS reaches 1.8 clients. (26.1 is the same: `hurtMarked` -> immediate
        `SetEntityMotion`; and modern arrows have `trackDeltas()==true`, so vanilla-26 DOES send per-tick arrow velocity
        every 20t - this is a vanilla-26-wide ViaRewind issue, not just Minestom, just milder than our every-tick.) So we
        match the split: `setVelocityBt` is SILENT (per-tick physics / deflect / stick - vanilla's direct `motX` write),
        but the public `ProjectileEntity.setVelocity` override broadcasts via `sendVelocityToViewers` (the `velocityChanged`
        path), so redirect/homing behaviors work through the suppression. ViaRewind can't make this split (automatic and
        explicit are the same `SetEntityMotion` packet) - the fix belongs server-side, exactly as the maintainer said.
      - **Edge-stick "sticks then snaps forward" (1.8-only) = ROOT-CAUSED + FIXED (spawn-position lockstep).** The residual
        the velocity fix unmasked. A 1.8 client predicts the WHOLE flight from the spawn packet, whose position ViaVersion
        truncates to 1/32 (`(int)(v*32)/32`, toward zero). Our server simulates from the FULL-PRECISION spawn, so the client
        starts up to ~0.03 off (e.g. y `41.52`->`41.50`) and that offset PERSISTS - physics are bit-identical (drag `0.99` /
        gravity `0.05` / post-move, verified tick-by-tick against the logged wire). On a shallow descending graze that
        0.02-0.03 is the difference between the client clipping a block edge and the server clearing it: the client sticks
        where the server flies on, and the position resync then snaps the stuck arrow forward. **Fix:** `ProjectileSystem.launch`
        spawns the entity on the SAME 1/32 grid via `quantizeToWireGrid` (truncate like Via), so server and client simulate
        the trajectory in LOCKSTEP and agree on the edge stick (a t=0 stick has no time for velocity drift, so position
        lockstep alone resolves it). Velocity (~1/8000 on the wire) is left exact - its residual is far below the position
        grid. Cost: a `<= 0.03` spawn grid-snap (the 1.8 wire precision anyway); modern clients also start on the grid (the
        entity IS there) so ALL viewers agree with the server. Present on vanilla too (it sends the same 1/32 spawn to 1.8
        clients) and invisible to modern clients (full-precision + server-authoritative) -> a NON-vanilla 1.8-client
        improvement. **Tried first + DELETED:** a swept-vs-ray "server misses the edge" theory + an additive vanilla
        ray-march - they did nothing, because the server's arrow genuinely was not reaching the block (the divergence is the
        spawn offset, NOT the detection method; Minestom's swept AABB and a true ray agree here).
      - **Per-tick sync was tried and REVERTED:** an absolute teleport EVERY tick killed the drift but SHAKES both 1.8 and
        26.1 clients (it fights the client's interpolation + prediction: predict forward, yank back) and on a deflect made
        distant viewers lose the entity. `synchronizePosition` is back to the throttled (sparse, `syncInterval`) teleport;
        `synchronizeNextTick()` + the `syncFlightNow()` deflect push were BOTH reverted.
      - **Deflect "invisible to OTHER viewers" = VANILLA (user-confirmed), so NOT fixed in itself.** Vanilla lets the
        client predict the bounce; through Via distant viewers (who don't know the target's invuln state) mispredict and
        the arrow looks invisible-but-pickable - exactly how vanilla behaves, so we leave it. (**FIXED (creative nuance):** 1.8
        PASSES THROUGH an `abilities.isInvulnerable` creative target - `movingobjectposition = null` - and only deflects a
        `damageEntity`-false (invul-WINDOW) hit. `DamageSystem` now returns a distinct `HitResult.IMMUNE` for creative/spectator
        (vs `BLOCKED` for the window), and the projectile's `invulnHit` knob is a single `InvulnResponse` record with just TWO
        cases `(invulWindow, immune)`, set via builder overloads in the `Deflect`/`PickupBox` house style (`invulnHit(all)` /
        `invulnHit(window, immune)` / lambda). Only two cases: vanilla (1.8 `EntityArrow` + 26.1 `AbstractArrow`) NEVER splits
        creative vs spectator (both `isInvulnerable`; spectators excluded by `canBeHitByProjectile`/`isPickable`) - per-gamemode
        is a lambda. The window-vs-immune split exists ONLY because **1.8 differs** (creative null -> PASS_THROUGH, window ->
        DEFLECT) while **26.1 deflects BOTH** (`onHitEntity`: `hurtOrSimulate` false -> `deflect(REVERSE)`). `Vanilla18.arrow()` =
        `invulnHit(DEFLECT, PASS_THROUGH)`; `Vanilla.arrow()` = `invulnHit(DEFLECT)` (FIXED - was wrongly `PASS_THROUGH`; 26.1
        deflects creative); throwables `invulnHit(DESTROY)`. NOTE: with `legacyArrowVisibility` on, a 1.8 client renders a `DEFLECT` as
        "fly-through then snap" (it nulls the collision to keep the arrow visible, so it never shows the bounce) - `PASS_THROUGH`
        agrees with the client and reads smooth, so vanilla creative `PASS_THROUGH` is both accurate AND the clean visual.)
      - **UPDATE - the entity-visibility glitch is NOW FIXED for real** via the legacy arrow-visibility compat feature,
        which lives in the dedicated **`compatibility/` package** (`compatibility/visuals/legacy_1_8/LegacyArrowVisibilityConfig`
        [knobs: `enabled` + `deflectParticles`] + `LegacyArrowVisibility` manager, installed by `CompatibilitySystem`; per-scope
        via the `MechanicsProfile.compatibility` member): a neutral FF-off scoreboard team so the 1.8 client's own
        `canAttackPlayer` null-out stops it hiding the arrow. User-confirmed in-game. PURELY visual, per-player (profile chain),
        runtime `setEnabled` toggle. The `deflectParticles` cosmetic crit-trail moved here too (separate knob, same fix; resolved
        per-shooter, read by `ManagedProjectile` via `services().compatibility()`). The STATUS block below (accepted-limitation)
        is SUPERSEDED - kept only for the history of what didn't work (`respawnForViewers`, decoy id, the `deflectParticles` trail).
        The opt-in **`deflectParticles` hit knob** (default `false`; a server sets `arrow().deflectParticles(true)`) is the
        only thing we can do for it: on a DEFLECT / PASS_THROUGH it sets the base `deflectVisible` flag, and `ArrowEntity`
        sends **server-spawned crit `ParticlePacket`s at the authoritative position** each tick (NOT the crit metadata -
        that renders client-side from each viewer's own mispredicted arrow position, so it showed to the target instead of
        the outside viewers who need it). This traces the bounce path for 1.8 viewers; the arrow ENTITY stays invisible to
        them (see STATUS below). This is the "Hypixel shows the movement with particles" approach. Non-vanilla, never
        hard-coded into the vanilla preset. (An earlier `respawnForViewers` entity re-spawn was REMOVED - it never worked
        on 1.8; see STATUS.)
        - **STATUS: the entity-visibility half does NOT work on 1.8 - it is a HARD client-side limitation (accepted).**
          User-confirmed: the glitch is a **native 1.8 CLIENT bug** (reproduces on vanilla 1.8, NOT a Via artifact) and is
          **1.8-only** (26.1 renders the deflected arrow perfectly). Documented vanilla bug: **MC-129934** ("arrows
          bouncing off creative players can only be seen by the person fired at" - our exact symptom), **MC-101723**
          (bow/crossbow on an invulnerable entity makes the arrow invisible), **MC-140857** (bounces off entities during
          their invuln ticks), **MC-417** (bounce-back then appear); ProtocolSupport #1289 reports the same under a
          Via-style translation with no fix. **Root cause:** the 1.8 client runs its OWN arrow→entity collision and
          locally bounces/hides the arrow when it contacts an entity the server won't let it damage; the render decision
          lives entirely on the client, so NO server packet overrides it. That is why every server-side attempt failed -
          (a) same-id `respawnForViewers`; (b) split across two ticks; (c) a fresh-id decoy for legacy viewers
          (`ClientInfoTracker.isLegacy`) - the client re-runs its collision on ANY arrow entity near the target and hides
          it regardless of entity id; (d) an `ignoredHitEntities` "re-hit loop" theory (DISPROVEN - 26.1 fine ⇒ not a
          server loop). Known client-side fixes (newer MC; a modded client force-syncing pos via DataWatcher) need a
          modified client, unavailable to vanilla-1.8-via-Via. **Only the crit-particle TRAIL shows on 1.8** - it is now
          the WHOLE of `deflectParticles` (the entity re-spawn was removed); accept the entity invisibility. Tofaa's idea
          of bumping the victim's "arrows-in-body" count metadata (`LivingEntityMeta.setArrowCount`, a separate render
          path immune to the bug) was considered + rejected: it renders generic arrows in the PLAYER's model, not the
          actual bounced / block-stuck arrow, and vanilla only adds those on a DAMAGING hit (TODO: do replicate that for
          real damaging hits as parity - unrelated to this cosmetic).
      - **Parity scan (1.8 `ItemBow`/`EntityArrow` + 26.1 `BowItem`):** launch speed = `power*3` in BOTH (1.8 bow:
        `new EntityArrow(.., power*2)` then ctor `shoot(.., power*2*1.5, ..)`; 26.1 `shoot(.., power*3, ..)`) -> our
        `speed(3.0)*power` is exact; power curve `(s²+2s)/3` capped 1, crit at full draw, spread `0.0075`/uncertainty
        `1.0`, damage mult `2.0` (`ceil(|mot|*2)+crit`), gravity `0.05`, drag `0.99`, 7-tick shake, pickup modes all
        match. **FIXED:** the arrow's `0.16` throwing-hand lateral - we had overridden it to `0`; 1.8 `EntityArrow` ctor
        has `locX -= cos(yaw)*0.16` (26.1 has none = the documented 1.8->26 delta), so arrow now inherits `0.16` from
        `projectileDefaults()`. **DONE:** the victim "arrows-in-body" stuck-arrow count on a damaging hit (vanilla
        `EntityLiving.o(bv()+1)`) - `ArrowEntity.onImpact` calls `StuckArrows.add(victim, 1)`; they fall out on the
        vanilla `20 * (30 - count)` cadence (identical in 1.8 `EntityLiving:1198` and 26.1 `LivingEntity:2699`). Remaining
        GAPS (feature TODOs, not parity bugs): Infinity, Power/Punch/Flame enchants, draw-start gate, bow durability +
        shoot sound, dedicated `minecraft:arrow` damage type.
4. **Fishing rod** (phase 5): bobber + desync handling (revisit whether the bobber needs the same periodic-resync
      treatment or its own model) + pseudo-hook modes (config enum).
5. Hypixel/Minemen projectile presets. (The old `LegacyStuckSync`/F7/F8/F12 plan is dropped - superseded by 3a.)

### Lingering projectile TODOs (after the polish pass)
Feature gaps, NOT parity bugs in existing behavior - the system is structurally complete (all 1.8/26.1 deltas are knobs):
- **Arrow** (`ArrowEntity`/`Bow`): Infinity, Power/Punch/Flame enchants; gate the draw-start (min charge); bow durability
  + shoot sound; a dedicated `minecraft:arrow` damage type (death message); pickup nits (inventory-full = no pickup,
  offhand vs inventory, `random.pop` sound).
- **Pearl** (`PearlEntity`): cross-instance teleport (vanilla only teleports in the pearl's own world); a dedicated
  `enderPearl` damage type (currently `GenericDamage` stand-in, numerically `5` = FALL); 5% endermite spawn (cosmetic).
- **Snowball**: `3` damage to a Blaze (needs entity-type-aware damage).
- **Spread distribution (parity nuance):** 1.8 `shoot()` uses `gaussian * 0.0075`; 26.1 `getMovementToShoot` uses
  `triangle(0, 0.0172275 * uncertainty)`. Our `launchVelocity` uses gaussian × `spread`, so the 26.1 preset's spread is
  approximate (distribution + magnitude differ). A `spreadDistribution` knob (gaussian/triangle) + the 26.1 value is the fix.
- **Arrow despawn timer**: a stuck arrow lives forever (until pickup / block broken); vanilla despawns it after
  `arrowDespawnRate` (~1200t) and a flying throwable dies after 1200t inGround (`EntityProjectile`). Add a max-age knob.
- See **"Remaining projectile types & shooters"** below for the unimplemented entity/launcher list.

### Remaining projectile types & shooters (NOT implemented - notes only)
**Launchers** (a `Shootable`, like `Bow`):
- **Crossbow** - load-then-fire (charge + hold), Multishot (3 arrows at +-10 deg), Quick Charge; fires arrows or a firework.
- **Trident** - thrown entity that returns (Loyalty) or launches the player (Riptide in water/rain); impaling damage; pickup.
- **Fishing rod** - cast/retrieve a bobber entity (own desync handling - revisit the periodic-resync vs a dedicated
  model) + pseudo-hook modes (VANILLA stick / LEGACY_18 no-stick / MINEMEN pseudo-hook, a per-type enum) + reel pull.

**Self-launching throwable types** (`ThrowableItemType` / `ProjectileType`):
- **Splash potion** (`ThrownPotion`) - shatter on impact, AoE effect application with radius falloff, color particles.
- **Lingering potion** - `ThrownPotion` + an `AreaEffectCloud` entity (the cloud is its own entity to add).
- **Bottle o' enchanting / XP bottle** (`ThrownExperienceBottle`) - shatter -> spawn 3-11 XP orbs.
- **Fireball / small fireball** - no gravity, constant accel along aim, explode/ignite on hit (ghast/blaze + dispenser).
- **Wither skull**, **eye of ender** (non-combat, low priority), **firework** (crossbow/elytra boost + detonation),
  **llama spit / shulker bullet / dragon fireball** (mob projectiles, low priority for a PvP lib).

### Preset TODOs
**`Vanilla18` (1.8 baseline):** arrow Power/Punch/Flame/Infinity enchants, bow durability + shoot sound, dedicated
`minecraft:arrow` damage type; snowball `3`-damage to a Blaze (entity-type-aware); fishing-rod/trident/crossbow presets
once those launchers exist. (The egg's chicken is intentionally opt-in via a behavior now, not a preset default.)
**`Vanilla` (26.1 modern):** the spread distribution (`triangle` vs gaussian - above); dedicated `enderPearl` +
`minecraft:arrow` damage types; confirm `updateInterval(10)` vs our `syncInterval(20)`; the modern potion/firework/
crossbow/trident deltas once those types exist. **UNTESTED in-game** - the whole `Vanilla.projectiles()` preset
(momentum carry, pre-move physics, leftOwner immunity) needs a 26.1-client pass.

### API surface for consumers
**Available now:**
- **Events** - `ProjectileLaunchEvent` (spawn/flight: cancel, `setSpawnPos`/`setVelocity`, `behavior(...)`, the typed
  `projectile()` handle for physics setters, `resolvedFlight()`); `ProjectileHitEvent` (impact: `damage`/`knockback`/
  `knockbackSource`/`response`/`removeOnHit` overrides, `resolvedHit()`, `target`/`hitPoint`/`isBlockHit`/`isSelfHit`).
- **Entity queries** - `getSpawnPosition()` (where fired), `isStuck()` + `getStuckPoint()`/`getStuckBlockPosition()`/
  `getStuckBlock()` (where it landed / what it stuck in), `velocityBt()` + `getAerodynamics()` + `getPosition()` (live
  physics for trajectory particles), `getShooter()`.
- `StuckArrows` (arrows-in-body get/set/add/clear). Custom items / minigame registry: `docs/projectiles-custom-items.md`.

**Proposed / NEEDS-CONFIRM (not built - confirm before implementing):**
- **`ProjectileSystem.predictPath(snapshot)`** -> simulate the arc (e.g. `List<Pos>`) WITHOUT spawning, for a pre-shot
  particle trajectory preview (aiming aid). Needs the single-tick step (`movementTick`/`applyDragGravity`) extracted to
  a pure function run against throwaway state. High value for minigames.
- A despawn/expire **event or hook** distinct from a hit (currently only `ProjectileBehavior.onRemove`).
- Config/`ResolvedFlight` introspection for a type without launching (for UIs / debug overlays).
- A `ProjectileSnapshot` builder convenience for non-item launches (turrets, spells) - currently `of(shooter, type)` + withers.

### Known cross-system TODOs (not projectile-specific)
- `KnockbackCalculator` / `KnockbackConfig.customComponents` / `DamageConfig.customComponents`: **stages plan**
  - make built-in stages (friction/combine/bounds, damage invul rule) replaceable strategies, not just
  append-only post-components. `DamageSystem.knockbackOwnsVelocity` should become a `DamageTypeConfig` flag.
- `DamageSystem.apply` / `KnockbackSystem.apply`: phased event layer (PreDamage/DamageModify/FinalDamage) +
  kill the double config-resolution.

## 3. Port order

## 3. Port order

1. **Entity core**: ProjectileEntity port (zero bbox, physics, stick machinery, absolute-teleport sync,
   updateNewViewer) — modern-correct first.
2. **LegacyStuckSync**: the 1.8 hint/pullback system, gated per viewer by protocol. Rework the edge filter.
3. **Throwables**: ThrowableLauncher + ThrownItemEntity (snowball/egg) — smallest item->spawn->hit loop;
   proves snapshot/config/event plumbing end to end. Then pearls (teleport on land).
4. **Bow**: draw state, power, arrow selection/consumption, ArrowEntity (pickup, despawn, crit), enchants.
5. **Fishing rod**: cast/retrieve, bobber entity with both desync fixes, pseudo-hook modes
   (VANILLA stick / LEGACY_18 no-stick / MINEMEN pseudo-hook as a per-type config enum), pull logic.
6. **Presets**: `Vanilla18.projectiles()`, Hypixel/Minemen overrides (bobber mode, rod KB, spawn offsets).

## 4. Open questions

- Pearl/egg gameplay effects (teleport, spawn chicken?) — in scope or stub the hooks?
- Old known issues to fix during port (from old TODOs): rod can't hit the shooter; bobber "pickup" feel
  (collides with own player when running forward); bobber tunnels through blocks on long casts;
  >30-block auto-retract unimplemented; edge-hit filter misfires.
- Naming for the hit->damage types and whether arrows get their own invul interaction (vanilla 1.8 arrows
  respect the same noDamageTicks window).

## 5. Vanilla parity notes (researched from 1.8 PaperSpigot + 26.1 source)

Source paths: 1.8 = `...\paper_source_1.8.8\...\net\minecraft\server\Entity{Projectile,Snowball,Egg,EnderPearl}.java`;
26.1 = `...\26.1-src\net\minecraft\world\entity\projectile\{Projectile,ThrowableProjectile,throwableitemprojectile\*}.java`.

### Momentum inheritance (shooter velocity folded into the throw)
- **1.8** (`EntityProjectile` ctor + `shoot()`): **NONE.** A thrown snowball/egg/pearl gets velocity =
  `look x 1.5 + gaussian(0.0075)` only - the shooter's `motX/Y/Z` is never added. So vanilla-1.8
  `inheritMomentum = FALSE`.
- **26.1** (`Projectile.shootFromRotation`): `delta += source.getKnownMovement().x/z` always, `.y` only when the
  shooter is airborne (`source.onGround() ? 0 : y`). Critically it reads **`getKnownMovement()`** (the entity's
  REAL tracked movement), NOT server velocity. Our analog is **`MotionTracker.positionDelta(shooter)`** (b/t
  move-delta), NOT `Entity.getVelocity()` (which is ~0/stale for client-driven players - the "feels off" bug).
- **Decision**: `inheritMomentum` defaults FALSE (vanilla 1.8 + resolver fallback). When TRUE (modern presets),
  the momentum source is `MotionTracker.positionDelta(shooter)`; horizontal always, vertical only if airborne.

### Self-hit vs other-entity hit (via hit-time resolution, NOT a dedicated record)
The point is NOT immunity - a projectile can behave DIFFERENTLY on its thrower vs any other entity. After exploring
a `SelfHit` record (rejected as "preset-enum stiff" + limiting), the clean solution is to **resolve the hit knobs at
IMPACT** against a context that carries the target, so plain config lambdas express any self-vs-other difference.
- **Resolution split** (`ProjectileConfigResolver`): `resolveFlight(tc, ctx)` at launch (spawn/physics);
  `resolveHit(tc, ctx)` at impact, where `ctx = ProjectileContext.of(snap, services).atHit(target, throwOrigin, hitPos)`.
  `ManagedProjectile` stores the merged `effectiveConfig` + snapshot and resolves the hit knobs each impact (rare, so
  late resolution is cheap). A hit lambda reads `ctx.isSelfHit()`, `ctx.target()`, `ctx.throwOrigin()`.
- **Vanilla has NO self-immunity** (the user confirmed: singleplayer only). 1.8 excludes the shooter for the first
  5 ticks (`ar < 5`); after that a self-hit is normal - a snowball thrown straight up hits you on the descent. So the
  default is just NORMAL. The **ender pearl** is the one genuine 1.8 self-ignore (`if (hit.entity == shooter) return;`).
- **Native knobs (no lambda for the common case)**:
  - `selfHit` (`HitResponse` knob {HIT, PASS_THROUGH, DEFLECT}, default HIT): the response when the projectile hits
    its OWN shooter. `PASS_THROUGH` = ignore + keep flying (1.8 pearl, Hypixel "self does nothing"); `DEFLECT` =
    bounce off (reverse+damp velocity, no break/dmg/KB). Constant-friendly, so vanilla stays constant-only -
    `Vanilla18.pearl()` = `selfHit(PASS_THROUGH)`. The same `HitResponse` enum generalizes to any future "not allowed
    to hit this entity" case (team filters, etc.).
  - Everything else self-specific is a lambda on the existing hit knobs: Minemen self-KB-along-yaw =
    `knockback(ctx -> ctx.isSelfHit() ? KnockbackConfig.builder(kb).yawWeight(1).build() : kb)`.
- **Yaw, no `SHOOTER_LOOK`**: `KnockbackSource.SHOOTER` now carries `source = shooter` in the snapshot (like melee),
  so the KB calculator has the shooter's look and `yawWeight` does the work - `yawWeight(1)` pushes along the yaw.
  `PROJECTILE` is unchanged (origin = projectile, dir = flight). Dropped `SHOOTER_LOOK`.
- **Throw-time snapshot**: `shooterOriginPos` (captured at spawn) is now exposed as `ctx.throwOrigin()` and on
  `ProjectileHitEvent` (+ `isSelfHit()`), so lambdas AND the event can push KB/teleport from the throw pose.

### Spawn position, launch velocity, collision box (1.8 `EntityProjectile` ctor + `shoot`)
- **Spawn**: shooter eye height, then offset `(-cos(yaw)*0.16, -0.1, -sin(yaw)*0.16)` - a **0.16 LATERAL** shift
  (perpendicular to look = the throwing-hand offset) and **0.1 DOWN**, NO forward. Our config had only the -0.1
  vertical (no lateral) - that's a Hypixel-style accuracy trim, not vanilla. Added `spawnOffsetLateral` (vanilla
  `0.16`). 26.1 (`ThrowableItemProjectile`): eye `- 0.1`, NO lateral.
- **Velocity**: `look_unit * 1.5 + gaussian*0.0075` (speed `j()=1.5`, uncertainty `1.0` -> **spread `0.0075`**). Our
  config had `spread = 0` (Hypixel/accuracy); vanilla is `0.0075`. Gravity `m()=0.03`, drag `0.99` (`0.8` in water).
  Same constants in 26.1.
- **Collision box**: entity SIZE `0.25 x 0.25` (1.8 `setSize`, 26.1 `sized(0.25,0.25)`) is render/broad-phase only.
  BLOCK collision is a POINT raytrace (center -> center+motion) in both - our `bbox = 0` (F1) matches it and is the
  reason we keep size 0 (modern client `inGround` flush). ENTITY collision: 1.8 grows the TARGET by `0.3` on EACH side
  (`Entity{Arrow,Projectile}`: `f=0.3F`, both verified) and ray-tests the path. **FIXED (was the bug):** we now
  `growSymmetrically(grow, grow, grow)` the zero projectile box from the un-offset `position` (Minkowski dual of
  target+0.3), where `grow` is the per-type `ProjectileTypeConfig.entityHitGrow` knob (default `0.3`, stamped on the
  entity at launch like `shooterImmunityTicks`; `DEFAULT_ENTITY_HIT_GROW`). GOTCHA: Minestom's `expand(0.3)` only adds
  0.3 to the TOTAL size (±0.15) and offsets y, so a first attempt with `expand` reached only ~0.45 to the side and
  looked unchanged; `growSymmetrically` adds 0.3 to BOTH sides (±0.3, centered, = 1.8's `grow`). The old `(0.1,0.3,0.1)`
  from `pos-0.3` was too tight: arrows the 1.8 CLIENT predicts as a hit flew right past on the server -> "arrow
  disappears for shooter / bounces for target" desync (1.8-vs-1.8 only; modern's hitbox matches the server). 26.1 uses
  a different margin -> set `entityHitGrow` in the future modern preset.
- **Physics order**: 1.8 moves THEN applies drag+gravity (post-move); 26.1 applies gravity+inertia THEN moves
  (pre-move). Ours = 1.8. Modern preset needs a pre-move toggle later (ties into bobber F10).

### 1.8 -> 26.1 deltas the system must express natively (the modern preset is `Vanilla.projectiles()`)
> All deltas below are now expressible as knobs, and **`Vanilla.projectiles()`/`projectileDefaults()`/`arrow()`** wire
> them (base on the `Vanilla18` config + override the deltas): `momentum(1) + velocity(delta())` (airborne-vertical
> lambda), `spawnOffsetSideways(0)`, `deflect(-0.5, 0, -10, 10)`, `physicsOrder(DRAG_BEFORE_MOVE)`, `leftOwnerImmunity(true)`,
> arrow `invulnHit(PASS_THROUGH)`. Still deferred: dedicated `enderPearl`/`minecraft:arrow` damage types (on the
> damage-type TODO).
>
> **FUTURE (not implemented) - path prediction:** keep `ProjectileSystem.spawnPos`/`launchVelocity` and the single-tick
> physics step (`ProjectileEntity.movementTick` now factors `applyDragGravity()`) pure/reusable so a future
> `ProjectileSystem.predictPath(snapshot)` can simulate the arc WITHOUT spawning an entity - for a client-side particle
> preview of where a throw/shot will land. No API added yet; just don't entangle the launch math with entity state.
| Aspect | 1.8 | 26.1 | Knob today |
|---|---|---|---|
| Momentum | none | `getKnownMovement` (h always, v airborne) | `momentumHorizontal`/`momentumVertical` scales over a `VelocityRule` source (config `velocity` rule -> shooter profile -> DEFAULT; 26.1 = 1/1 + `velocity(delta())`, airborne-vertical via a lambda) |
| Spawn lateral | 0.16 | 0 | `spawnOffsetSideways` |
| Deflect | `motion *= -0.1` | `REVERSE` `*= -0.5` + random +-10 deg | `deflect(mul, turn, min, max)` knob (`ProjectileTypeConfig.Deflect` nested record, like `PickupBox`): 1.8 = `deflect(-0.1)`, 26.1 = `deflect(-0.5, 0, -10, 10)` |
| Spread | 0.0075 | 0.0075 | `spread` |
| Self-hit (pearl) | pass-through | self-teleport | `selfHits(false)` + hit-knob lambdas on `ctx.isSelfHit()` |
| Shooter immunity | 5 ticks | `leftOwner` (geometric) | `shooterImmunityTicks` (1.8) / `leftOwnerImmunity(true)` (26.1) |
| Physics order | post-move drag/grav | pre-move grav/inertia | `physicsOrder` (`DRAG_AFTER_MOVE` 1.8 / `DRAG_BEFORE_MOVE` 26.1) |
| Block collision | per-tick `world.rayTrace` | swept AABB (server-auth) | swept-only (a RAYTRACE knob was built then deleted - indistinguishable; see 3f) |
| Pearl dmg type | FALL, 5 | `enderPearl`, 5 | amount yes; type = TODO (GenericDamage stand-in) |
| Pearl teleport target | pre-move pos | `oldPosition()` | ~same (pre-move) |
| Sync interval | n/a here | `updateInterval(10)` | `syncInterval` (ours 20) |

### Egg / pearl impact + pearl damage (both fire on entity AND block hit)
- **Egg**: 0 dmg to a hit entity; `1/8` chance baby chicken (`1/32` -> 4), break. (26.1 adds a chicken-variant
  component - skipped.)
- **Snowball**: 0 dmg, `3` to a Blaze (defer - needs entity-type-aware damage), break.
- **Pearl damage is a CONSTANT, not distance-based** (answers the open question): 1.8
  `entityliving.fallDistance = 0; damageEntity(DamageSource.FALL, 5.0F)` - it ZEROES the fall distance then deals a
  flat **5**, typed as FALL (feather-falling/resistance apply, armor does not). 26.1:
  `hurtServer(damageSources().enderPearl(), 5.0F)` - flat **5** via a DEDICATED `enderPearl` type. So the amount is
  constant 5 in both; only the damage TYPE differs (FALL vs enderPearl). We now call
  `FallDamage.resetFallDistance(shooter)` (already public "for ender pearls") + deal a flat 5 (currently via
  `GenericDamage` - no armor model yet so numerically == FALL; a dedicated EnderPearl/FALL-amount type is the clean
  follow-up that also captures the 1.8-vs-26 type delta).

### Entity tracking / position sync (1.8 `EntityTracker`/`EntityTrackerEntry`, 26.1 `ServerEntity`) - drives the FLIGHT SYNC model (status 3f)
The 1.8 CLIENT runs each projectile's full physics + raytrace locally every tick (`EntityArrow.t_` / `EntityProjectile.t_`); the server only CORRECTS it with packets. So how often/what the server broadcasts decides whether the client's local prediction (incl. its own block-hit raytrace) agrees with the server.
- **1.8 register intervals** (`EntityTracker.addEntity(entity, range, updateInterval, sendVelocity)`): arrow `(64, 20, false)`, snowball/egg/pearl `(64, 10, true)`, fishing hook `(64, 5, true)`, fireball `(64, 10, false)`.
- **1.8 `track()` per tick**: the position/velocity block runs every `updateInterval` ticks (`m % c == 0`). **Arrows are forced into a pos+rotation packet** (`!(tracker instanceof EntityArrow)` disables the pos-only / rot-only branches - the same `&& !AbstractArrow` exists in 26.1 `ServerEntity` line ~158). Relative move when the delta fits a byte (±4 blocks/update), else teleport (also on onGround change, `v > 400`, every 60t). **Velocity** is broadcast only when `sendVelocity` (`u`) is on AND motion changed `> 0.02` - so a vanilla **arrow sends ZERO velocity packets in flight** (the client predicts from the spawn velocity); throwables send it ~each 10t. An abrupt `velocityChanged` impulse (knockback) broadcasts velocity immediately, outside the interval - but an arrow deflect sets `motX *= -0.1` DIRECTLY (no `velocityChanged`), so it gets no special broadcast.
- **26.1 `ServerEntity.sendChanges`**: same shape - forced PosRot for `AbstractArrow`; position every `updateInterval` (or `needsSync`/dirty), `pos = positionChanged || tickCount%60==0`; teleport on `requiresPrecisePosition`/deltaTooBig/`teleportDelay>400`/onGround change; velocity (`SetEntityMotion`) only on change `> 1e-7` when `trackDelta`; `hurtMarked` broadcasts velocity immediately.
- **What we landed on** (status 3f): match the vanilla broadcast in BOTH dimensions.
  - **Position:** `synchronizePosition` sends the absolute teleport throttled to `syncInterval`, client predicts between (a per-tick teleport SHAKES - it fights the client's interpolation/prediction; reverted, along with `synchronizeNextTick()` and a `syncFlightNow()` deflect push).
  - **Velocity:** THE edge-slide fix. We replicate `sendVelocity=false` for arrows - velocity goes ONCE in the spawn packet (the `SpawnEntityPacket` velocity bytes), then the per-tick `EntityVelocityPacket` from Minestom's `Entity.tick` is DROPPED in `ProjectileEntity.sendPacketToViewers` (an `allowVelocityPacket` flag still lets the deliberate stuck-zero through). Per-tick velocity packets - lagged + quantized over Via - were overwriting the 1.8 client's clean local prediction, so its own raytrace mispredicted edges (slide) and its bounce prediction drifted (deflect "wrong to the target"). This is why RAYTRACE made no difference (the CLIENT decides via its prediction). Stripping them = vanilla lockstep.
  - The deflect "invisible to OTHER viewers" stays (vanilla); the opt-in `deflectParticles` knob renders a server-spawned crit trail at the authoritative position for visibility. The stuck-arrow `resyncStuck` (every `syncInterval`) mirrors vanilla `ServerEntity`'s re-assert for a stopped arrow. A future modern preset keeps the same cadence (modern physics match better).
