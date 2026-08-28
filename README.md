# Usu's Item Model Delay

Usu's Item Model Delay is a client-side Fabric mod for Minecraft 26.2 that lets resource packs delay item-model property transitions and optionally play transition particles and sounds.

Minecraft's normal item-model properties remain the source of truth. Resource packs opt individual item properties into the delay system with matching `.mdprop` files. Items and properties without `.mdprop` configuration retain vanilla behavior.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2

## `.mdprop` files

Place `.mdprop` files in an `imd` folder beside the corresponding item definition:

```text
assets/minecraft/items/diamond_sword.json
assets/minecraft/items/imd/diamond_sword.mdprop
```

The `.mdprop` filename must match the item filename.

Delays are measured in client ticks:

```text
20 ticks = 1 second
40 ticks = 2 seconds
100 ticks = 5 seconds
```

Only properties listed in the `.mdprop` file are delayed.

---

## Conditional properties

Conditional properties return `true` or `false`.

```properties
using_item.delay=20
using_item.mode=both
```

### Delay modes

- `hold` — delay the transition into the `true` state.
- `release` — delay the return from `true` to `false`. This is the default.
- `both` — delay both directions.

Example:

```properties
keybind_down.delay=10
keybind_down.mode=release
```

### Supported conditional properties

```text
custom_model_data[index]
using_item
broken
damaged
fishing_rod/cast
has_component[component]
bundle/has_selected_item
selected
carried
extended_view
keybind_down
view_entity
component[predicate]
```

Examples:

```properties
using_item.delay=20
using_item.mode=both
```

```properties
custom_model_data[0].delay=40
custom_model_data[0].mode=hold
```

```properties
has_component[custom_name].delay=40
has_component[custom_name].mode=hold
```

```properties
component[damage].delay=20
component[damage].mode=both
```

For vanilla component and predicate identifiers, omit the `minecraft:` namespace in `.mdprop` keys:

```properties
component[damage].delay=20
```

not:

```properties
component[minecraft:damage].delay=20
```

### Held behavior

Conditional properties can use:

```properties
property_name.behavior=held
```

`held` keeps the delay state associated with the held item's lifecycle rather than treating its current inventory slot as the identity. This is useful when the underlying stack can move or have components replaced while it remains the logical held item.

The default is:

```properties
property_name.behavior=normal
```

---

## Range properties

Range properties return numeric values and are used by Minecraft's `range_dispatch` item model.

They use the `range.` prefix:

```properties
range.count.delay=20
```

### Supported range properties

```text
range.custom_model_data[index]
range.bundle/fullness
range.damage
range.cooldown
range.time
range.compass
range.crossbow/pull
range.use_cycle
range.use_duration
range.count
```

### Threshold behavior

The default is:

```properties
range.count.behavior=threshold
```

`threshold` delays when the numeric value moves into a different `range_dispatch` model region. Changes that remain within the same model region do not start or restart the timer.

### Value behavior

To delay every numeric value change:

```properties
range.count.behavior=value
```

`value` is an advanced option. `threshold` is normally the better fit for model transitions.

Parameterized vanilla range properties are tracked separately internally, including normalized/raw damage and count, use-duration direction, use-cycle period, and individual time/compass nodes.

---

## Select properties

Select properties return one value from a set of possible values.

```properties
select.charge_type.delay=20
```

The old select value remains exposed until the configured delay completes.

Select properties do not use conditional `hold`, `release`, or `both` modes.

### Supported select properties

```text
select.main_hand
select.charge_type
select.trim_material
select.block_state[property]
select.local_time
select.context_entity_type
select.context_dimension
select.component[component]
```

Examples:

```properties
select.charge_type.delay=20
```

```properties
select.trim_material.delay=20
```

```properties
select.block_state[facing].delay=20
```

```properties
select.local_time.delay=40
```

```properties
select.context_dimension.delay=20
```

```properties
select.component[damage].delay=20
```

For vanilla data components, omit the `minecraft:` namespace:

```properties
select.component[rarity].delay=20
```

not:

```properties
select.component[minecraft:rarity].delay=20
```

### Display context

Minecraft's `display_context` select property is deliberately not delayed.

GUI, first-person hand, third-person hand, ground, fixed, and other display contexts can exist simultaneously for the same item. They are render contexts rather than one item state changing over time, so applying one shared transition delay would produce incorrect results.

Some vanilla items also use special renderers. Their held rendering can differ from ordinary item-model rendering even when the same model definition behaves normally in GUI contexts.

---

## Transition effects

A delayed property can optionally create a particle-style transition effect.

The currently supported effect is:

```properties
property.effect=poof
```

Example:

```properties
using_item.effect=poof
using_item.effect.trigger=change
```

### Effect triggers

```properties
property.effect.trigger=change
```

`change` fires when the value/model actually exposed by Item Model Delay changes. If that transition is delayed, the effect waits for the delay and fires when the visible transition completes. It can fire in either direction.

```properties
property.effect.trigger=delayed
```

`delayed` is specifically a delayed-return/release event. It fires only when a configured delayed return to the original/fallback state completes. It is not a generic "any transition that had a delay" trigger.

The default effect trigger is `change`.

### Effect options

The poof renderer also supports:

```properties
property.effect.frames=<positive integer>
property.effect.duration=<positive integer>
property.effect.count=<positive integer>
property.effect.size=<non-negative number>
property.effect.radius=<x,y,z or supported radius form>
property.effect.radius_min=<x,y,z>
property.effect.radius_max=<x,y,z>
property.effect.spread=<non-negative number>
property.effect.stagger=<0.0 to 0.95>
property.effect.origin=<x,y,z>
property.effect.texture=<texture path>
```

`effect.count` is capped at 128.

A custom texture path is resolved in the namespace containing the `.mdprop` file. `textures/` and `.png` are added automatically when omitted.

---

## Transition sounds

Properties can also play sounds when their transition event fires.

```properties
using_item.sound=block/amethyst/break1
using_item.sound.trigger=change
```

The sound path refers to an actual `.ogg` resource inside the namespace containing the `.mdprop` file.

For example:

```text
assets/example/sounds/block/amethyst/break1.ogg
```

with:

```properties
using_item.sound=block/amethyst/break1
```

Do not use a vanilla sound-event ID here. This setting addresses sound resource files directly.

Multiple sound files can be supplied as a comma-separated list:

```properties
using_item.sound=block/amethyst/break1,block/amethyst/break2,block/amethyst/break3
```

The loader also tolerates an accidental `sounds/` prefix or `.ogg` suffix.

### Sound triggers

Sound triggers use the same semantics as effects:

```properties
property.sound.trigger=change
```

fires when the exposed model/property value actually changes.

```properties
property.sound.trigger=delayed
```

fires only when a delayed return/release completes.

The default sound trigger is `change`.

Additional sound options supported by the current configuration parser include per-property volume and other sound playback settings.

---

## Item behavior

An `.mdprop` file can set an item-level behavior:

```properties
behavior=normal
```

or:

```properties
behavior=evolving
```

`normal` is the default.

`evolving` is used by the conditional evolving-state system. Its state follows the active selected-item lifecycle so stale evolving state can be reset when the relevant selection changes.

Unless a pack specifically needs evolving behavior, leave this as `normal`.

---

## Complete examples

### Using-item transition with an effect and sound

```properties
using_item.delay=20
using_item.mode=both

using_item.effect=poof
using_item.effect.trigger=change

using_item.sound=block/amethyst/break1
using_item.sound.trigger=change
```

The normal model remains visible for 20 ticks after use begins, then the using model appears and the `change` effect/sound fires. When use ends, the using model remains for another 20 ticks before returning, and `change` fires again.

### Return-only effect

```properties
using_item.delay=20
using_item.mode=both

using_item.effect=poof
using_item.effect.trigger=delayed
```

The poof fires only when the delayed `true -> false` return completes.

### Stack-count range transition

```properties
range.count.delay=20
range.count.behavior=threshold

range.count.effect=poof
range.count.effect.trigger=change
```

Only transitions between model threshold regions are delayed.

### Crossbow charge-type select

```properties
select.charge_type.delay=20
select.charge_type.effect=poof
select.charge_type.effect.trigger=change
```

Transitions between `none`, `arrow`, and `rocket` keep the previous select value visible until the delay completes.

### Component select

```properties
select.component[rarity].delay=20
select.component[rarity].effect=poof
select.component[rarity].effect.trigger=change
```

When the item's `minecraft:rarity` component changes, the old selected model remains visible until the transition completes.

### Multiple property families on one item

```properties
using_item.delay=10
using_item.mode=both

range.damage.delay=20
range.damage.behavior=threshold

select.component[damage].delay=20
```

Each property family maintains its own delay state.

---

## Resource reloads, worlds, and long sessions

Runtime state is cleared at the major client lifecycle boundaries:

- resource reload
- world/server disconnect
- world/server join

This includes conditional, range, select, transition-effect, and compatible hand-transform runtime state.

Long-unused completed property state is also retired automatically during play, and temporary stack-identity/derived-state bookkeeping is pruned as stacks disappear.

The `.mdprop` configuration itself is rebuilt from the active resource packs during resource reload.

These cleanup rules are intended to prevent old item state from accumulating across long sessions or repeated world/resource-pack changes.

---

## Notes for pack authors

- Delay values are integer client ticks.
- Conditional properties default to `mode=release`.
- Conditional properties default to `behavior=normal`.
- Range properties default to `behavior=threshold`.
- Select properties need only `.delay`.
- `change` means an exposed model/property change.
- `delayed` means a completed delayed return/release.
- Effect and sound triggers default to `change`.
- Vanilla component identifiers are shortened in `.mdprop` keys: use `damage`, not `minecraft:damage`.
- Unsupported or unconfigured properties retain vanilla behavior.
- Reload resources after changing `.mdprop` files.

## License

This project is released under CC0 1.0 Universal. See `LICENSE` for the full license text.
