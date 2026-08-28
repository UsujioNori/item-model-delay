# Changelog

## 1.0.1

### Added

- Added transition particle effects for delayed item-model changes.
  - Added `effect=poof`.
  - Added `effect.trigger=change` and `effect.trigger=delayed`.
  - Added configurable effect appearance and placement options.
- Added transition sounds loaded from resource-pack `.ogg` files.
  - Added `sound.trigger=change` and `sound.trigger=delayed`.
  - Added support for multiple comma-separated sound choices.
- Added Punchy hand-transform compatibility for transition effect positioning.

### Changed

- Defined transition trigger behavior consistently:
  - `change` fires when the value/model actually exposed by Item Model Delay changes.
  - `delayed` fires only when a configured delayed return/release completes.
- Improved held-item state and physical-stack identity tracking across inventory movement, component replacement, `/item replace`, and menu/container interactions.
- Improved transition-effect attachment so expensive stack-identity resolution is skipped when no relevant effect is active.
- Improved runtime cleanup on resource reload, world/server disconnect, and world/server join.
- Added pruning for temporary stack identities and transition bookkeeping during long play sessions.

### Fixed

- Fixed `using_item` delays failing or behaving differently between GUI and first/third-person hand rendering.
- Fixed duplicate or incorrectly timed `using_item` transition effects.
- Fixed range `change` effects firing from raw numeric/threshold changes instead of the completed visible model transition.
- Fixed `range.time` transition effects resolving against the wrong held-stack identity.
- Fixed `select.local_time` effect/sound configuration lookup.
- Fixed `select.component[...]` transition effects and sounds failing because internal namespaced component keys did not map back to their public `.mdprop` keys.
- Fixed component-backed select properties using stale held-render stack copies.
- Fixed `select.trim_material` changing immediately in smithing result previews instead of respecting its configured delay.
- Fixed state continuity for component mutations and derived smithing/menu result stacks.
- Fixed stale range threshold bookkeeping not being removed when its associated delay state expired.
- Fixed stale smithing-derived identity links being retained after their result stack disappeared.
- Fixed Punchy cached hand-transform matrices surviving resource/world lifecycle changes.

### Cleanup

- Centralized runtime-state cleanup so the conditional, range, select, transition-effect, and Punchy compatibility caches are cleared together.
