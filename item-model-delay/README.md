# Usu's Item Model Delay

Usu's Item Model Delay is a client-side Fabric mod for Minecraft 26.2 that
lets resource packs delay item-model property changes.

The mod keeps Minecraft's normal item-model properties as the source of
truth. A resource pack adds a matching `.mdprop` file to control how
long a model transition is held before the new property value is
exposed.

## Requirements

-   Minecraft 26.2
-   Fabric Loader 0.19.3
-   Fabric API 0.158.0+26.2

## `.mdprop` files

Put `.mdprop` files beside the corresponding item definition in the
resource pack's `items` directory.

For example:

``` text
assets/minecraft/items/diamond_sword.json
assets/minecraft/items/imd/diamond_sword.mdprop
```

The `.mdprop` filename must match the item filename.

Delays are measured in Minecraft client ticks. At the normal 20 ticks
per second:

``` text
20 ticks = 1 second
40 ticks = 2 seconds
100 ticks = 5 seconds
```

Only properties listed in the `.mdprop` file are delayed. If an item or
property has no matching configuration, vanilla behavior is preserved.

------------------------------------------------------------------------

## Conditional properties

Conditional properties return `true` or `false`.

Basic syntax:

``` properties
using_item.delay=20
using_item.mode=both
```

### Delay modes

Conditional properties support three modes:

  -----------------------------------------------------------------------
  Mode                                Meaning
  ----------------------------------- -----------------------------------
  `hold`                              Delay the transition into the
                                      `true` state.

  `release`                           Delay the transition back out of
                                      the `true` state. This is the
                                      default.

  `both`                              Delay both directions.
  -----------------------------------------------------------------------

Example:

``` properties
keybind_down.delay=10
keybind_down.mode=release
```

### Supported conditional properties

``` text
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

``` properties
using_item.delay=20
using_item.mode=both
```

``` properties
custom_model_data[0].delay=40
custom_model_data[0].mode=hold
```

``` properties
has_component[custom_name].delay=40
has_component[custom_name].mode=hold
```

``` properties
component[damage].delay=20
component[damage].mode=both
```

For vanilla component and predicate identifiers, the `minecraft:`
namespace is intentionally omitted in `.mdprop` keys.

Use:

``` properties
component[damage].delay=20
```

not:

``` properties
component[minecraft:damage].delay=20
```

### Held property behavior

Conditional properties also support:

``` properties
property_name.behavior=held
```

`held` makes the delay state follow the held item stack's lifecycle
rather than treating its current inventory slot as the state identity.
This is useful for properties whose delayed state needs to remain
attached to the physical held stack while it moves.

The default is:

``` properties
property_name.behavior=normal
```

------------------------------------------------------------------------

## Range properties

Range properties return numeric values and are used by Minecraft's
`range_dispatch` item model.

Range properties use the `range.` prefix:

``` properties
range.count.delay=20
```

### Supported range properties

``` text
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

The default range behavior is:

``` properties
range.count.behavior=threshold
```

`threshold` delays only when the numeric value crosses into a different
`range_dispatch` threshold/model region. Changes that stay inside the
same model region do not start or restart the timer.

Example:

``` properties
range.count.delay=20
range.count.behavior=threshold
```

### Value behavior

To delay every actual numeric value change:

``` properties
range.count.delay=20
range.count.behavior=value
```

`value` is an advanced option. For most model transitions, `threshold`
is the intended behavior.

### Parameterized range properties

Some vanilla range properties contain extra JSON parameters. Model Delay
Helper keeps those parameters separate internally while allowing one
simple `.mdprop` configuration family.

Examples include normalized/raw `damage` and `count`, `use_duration`
direction, `use_cycle` period, and individual `time` or `compass` nodes.

------------------------------------------------------------------------

## Select properties

Select properties return one value from a set of possible values.

They use the `select.` prefix:

``` properties
select.charge_type.delay=20
```

A select transition keeps the old value visible for the configured
number of ticks before exposing the new value.

Select properties do not use conditional `hold`, `release`, or `both`
modes.

### Supported select properties

``` text
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

``` properties
select.charge_type.delay=20
```

``` properties
select.block_state[axis].delay=20
```

``` properties
select.local_time.delay=40
```

``` properties
select.context_dimension.delay=20
```

``` properties
select.component[damage].delay=20
```

For vanilla data components, omit the `minecraft:` namespace:

``` properties
select.component[damage].delay=20
```

### Display context

Minecraft's `display_context` select property is deliberately not
delayed.

Display contexts such as GUI, first-person hand, third-person hand,
ground, and fixed can exist as simultaneous render contexts for the same
item. They are not a single item state changing over time, so applying
one shared transition delay would produce incorrect model selection.

------------------------------------------------------------------------

## Item behavior

An `.mdprop` file can set an item-level behavior:

``` properties
behavior=normal
```

or:

``` properties
behavior=evolving
```

`normal` is the default.

`evolving` is used by the conditional evolving-state system for
supported evolving properties. Its state is tied to the active selected
item lifecycle so old evolving state is reset when the relevant
selection changes.

Unless a pack specifically needs evolving behavior, leave this as
`normal`.

------------------------------------------------------------------------

## Complete examples

### Delay a using-item model in both directions

`bow.mdprop`:

``` properties
using_item.delay=20
using_item.mode=both
```

The normal model remains visible for 20 ticks after use begins, then the
using model appears. After use ends, the using model remains visible for
another 20 ticks before returning.

### Delay stack-count range transitions

``` properties
range.count.delay=20
range.count.behavior=threshold
```

Only transitions between model threshold regions are delayed.

### Delay crossbow charge-type selection

``` properties
select.charge_type.delay=20
```

A transition between `none`, `arrow`, and `rocket` keeps the previous
select value visible for 20 ticks.

### Delay a damage component select

``` properties
select.component[damage].delay=20
```

When the item's `minecraft:damage` component changes, the old selected
model remains visible for 20 ticks before the new damage value is
exposed.

### Configure multiple property families on one item

``` properties
using_item.delay=10
using_item.mode=both

range.damage.delay=20
range.damage.behavior=threshold

select.component[damage].delay=20
```

Each property family maintains its own delay state.

------------------------------------------------------------------------

## Resource reloads and world changes

Usu's Item Model Delay clears runtime delay state when resources reload and
when the client disconnects from a world/server.

Long-unused completed state is also retired automatically so old
item-stack state does not accumulate indefinitely during long play
sessions.

The `.mdprop` configuration itself remains controlled by the active
resource packs.

------------------------------------------------------------------------

## Notes for pack authors

-   Delay values are integer client ticks.
-   Conditional properties default to `mode=release`.
-   Conditional properties default to `behavior=normal`.
-   Range properties default to `behavior=threshold`.
-   Select properties need only `.delay`.
-   Vanilla component identifiers are shortened in `.mdprop` keys: use
    `damage`, not `minecraft:damage`.
-   Unsupported or unconfigured properties retain vanilla behavior.
-   Reload the resource pack after changing `.mdprop` files.

## License

CC0-1.0
