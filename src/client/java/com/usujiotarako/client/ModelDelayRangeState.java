package com.usujiotarako.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class ModelDelayRangeState {

    /*
     * ================================================================
     * STATE KEY
     * ================================================================
     */
    private record StateKey(
            Identifier item,
            String property,
            int ownerId,
            int context,
            long stackIdentity
    ) {
    }


    /*
     * ================================================================
     * PLAYER LOCATION
     * ================================================================
     */
    private record LocationKey(
            int ownerId,
            int slot
    ) {
    }


    private record TrackedLocation(
            ItemStack object,
            ItemStack snapshot,
            long identity
    ) {
    }


    private static final int CURSOR_SLOT =
            Integer.MIN_VALUE;


    /*
     * Ownerless NeedleDirectionHelper renders deliberately return 0.0.
     *
     * They must not share state with an owner-driven Time or Compass
     * property.
     */
    private static final int OWNERLESS_NEEDLE_CONTEXT =
            Integer.MIN_VALUE + 1;


    /*
     * ================================================================
     * RANGE STATE
     * ================================================================
     */
    private static final Map<StateKey, Float> CURRENT_VALUE =
            new HashMap<>();


    private static final Map<StateKey, Float> TARGET_VALUE =
            new HashMap<>();


    private static final Map<StateKey, Integer> CURRENT_RANGE =
            new HashMap<>();


    private static final Map<StateKey, Integer> TARGET_RANGE =
            new HashMap<>();


    private static final Map<StateKey, Integer> REMAINING =
            new HashMap<>();


    private static final Map<StateKey, Boolean> TRANSITIONING =
            new HashMap<>();


    /*
     * A threshold sequence becomes "active" after the raw property leaves
     * the fallback/base range (-1). DELAYED is emitted only when that same
     * state later returns to fallback (-1). This mirrors conditional release semantics:
     * activation does not emit DELAYED; the delayed return does.
     */
    private static final Map<StateKey, Boolean> THRESHOLD_SEQUENCE_ACTIVE =
            new HashMap<>();


    /*
     * ================================================================
     * OBSERVED THRESHOLD REGION
     * ================================================================
     *
     * CHANGE for threshold behavior means the vanilla property crossed
     * into a different model/threshold region.
     *
     * This is tracked separately from CURRENT_RANGE/TARGET_RANGE because
     * those maps are part of the delayed visible-state machine. Using them
     * as CHANGE detection causes repeated events while the visible model is
     * deliberately being held back.
     *
     * The observation key deliberately omits the model-property context/
     * seed so repeated evaluations of the same held stack do not each
     * generate their own sound/particle.
     */
    private record ThresholdObservationKey(
            Identifier item,
            String property,
            int ownerId,
            long renderIdentity
    ) {
    }


    private static final Map<ThresholdObservationKey, Integer> LAST_OBSERVED_RANGE =
            new HashMap<>();


    /*
     * ================================================================
     * STACK LIFECYCLE IDENTITIES
     * ================================================================
     */
    private static final WeakHashMap<ItemStack, Long> STACK_IDENTITIES =
            new WeakHashMap<>();


    private static final AtomicLong NEXT_STACK_ID =
            new AtomicLong(1L);


    /*
     * ================================================================
     * STALE STATE CLEANUP
     * ================================================================
     *
     * Completed range state which has not been evaluated for ten minutes
     * is discarded. Active delayed transitions are never removed.
     */
    private static final long STALE_STATE_TICKS =
            20L * 60L * 10L;


    private static final Map<StateKey, Long> LAST_SEEN =
            new HashMap<>();


    private static long CLIENT_TICK =
            0L;


    /*
     * ================================================================
     * PLAYER LOCATION SNAPSHOTS
     * ================================================================
     */
    private static final Map<LocationKey, TrackedLocation> PLAYER_LOCATIONS =
            new HashMap<>();


    private ModelDelayRangeState() {
    }


    /*
     * ================================================================
     * TRANSITION EVENT OWNERSHIP
     * ================================================================
     *
     * Range properties can be evaluated for inventory/GUI stacks as well
     * as the item actually being held.
     *
     * Sounds are not render-bound, so only emit shared transition events
     * for the local player's currently-held main-hand/offhand stack.
     *
     * For stack-sensitive range properties we match the physical stack
     * identity. Owner-level properties such as cooldown/time use
     * stackIdentity=0, so matching the held item type is sufficient.
     */
    /*
     * ================================================================
     * TRANSITION RENDER IDENTITY
     * ================================================================
     *
     * Return the CURRENT physical identity of the matching held stack.
     *
     * This matters for properties such as range.count where vanilla may
     * mutate/replace the selected ItemStack object as the count changes.
     * The delayed state can legitimately still be keyed by the previous
     * render copy, but the effect must attach to the stack being rendered
     * NOW.
     *
     * A negative result means this transition does not currently belong
     * to a held local-player item and must not emit a sound/particle.
     */
    private static long resolveTransitionRenderIdentity(
            StateKey key
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player == null
        ) {

            return -1L;
        }


        if (
                key.ownerId() != -1

                        && key.ownerId()
                        != player.getId()
        ) {

            return -1L;
        }


        Inventory inventory =
                player.getInventory();


        ItemStack mainHand =
                inventory.getSelectedItem();


        if (
                !mainHand.isEmpty()

                        && key.item().equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                mainHand.getItem()
                        )
                )
        ) {

            if (
                    key.stackIdentity()
                            == 0L
            ) {

                /*
                 * Owner/world-level range state (for example cooldown or
                 * time) deliberately uses stackIdentity=0 for state sharing.
                 * Transition effects still need the concrete held-stack
                 * identity so the renderer can attach the particle to the
                 * item that actually changed model.
                 */
                return resolvePlayerStackIdentity(
                        mainHand,
                        player
                );
            }


            if (
                    key.property().startsWith(
                            "range.count{"
                    )
            ) {

                return resolveCountStackIdentity(
                        mainHand,
                        player
                );
            }


            return resolvePlayerStackIdentity(
                    mainHand,
                    player
            );
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (
                !offhand.isEmpty()

                        && key.item().equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                offhand.getItem()
                        )
                )
        ) {

            if (
                    key.stackIdentity()
                            == 0L
            ) {

                return resolvePlayerStackIdentity(
                        offhand,
                        player
                );
            }


            if (
                    key.property().startsWith(
                            "range.count{"
                    )
            ) {

                return resolveCountStackIdentity(
                        offhand,
                        player
                );
            }


            return resolvePlayerStackIdentity(
                    offhand,
                    player
            );
        }


        return -1L;
    }


    /*
     * ================================================================
     * RANGE TRANSITION EVENTS
     * ================================================================
     */
    private static void dispatchRangeChange(
            StateKey key,
            Object previousValue,
            Object newValue,
            ModelDelayConfig.RangeDelayConfig config
    ) {

        long renderIdentity =
                resolveTransitionRenderIdentity(
                        key
                );


        /*
         * -1 is the explicit "no matching held stack" sentinel.
         *
         * Do NOT reject every negative identity here: range.count uses
         * Long.MIN_VALUE's high bit to encode a stable player-slot identity,
         * so valid count identities are intentionally negative.
         */
        if (
                renderIdentity == -1L
        ) {

            return;
        }


        String publicProperty =
                configPropertyFromStateKey(
                        key.property()
                );


        ModelTransitionEvent.fireChange(
                key.item(),
                publicProperty,
                key.ownerId(),
                renderIdentity,
                previousValue,
                newValue,
                config.sound()
        );


        if (
                config.effect() != null

                        && config.effect().enabled()

                        && config.effect().trigger()
                        == ModelDelayConfig.TransitionEffectTrigger.CHANGE
        ) {

            ModelTransitionEffects.trigger(
                    key.item(),
                    publicProperty,
                    key.ownerId(),
                    renderIdentity,
                    config.effect()
            );
        }
    }


    private static void dispatchRangeDelayed(
            StateKey key,
            Object previousValue,
            Object newValue,
            ModelDelayConfig.RangeDelayConfig config
    ) {

        long renderIdentity =
                resolveTransitionRenderIdentity(
                        key
                );


        /*
         * -1 is the explicit "no matching held stack" sentinel.
         *
         * Do NOT reject every negative identity here: range.count uses
         * Long.MIN_VALUE's high bit to encode a stable player-slot identity,
         * so valid count identities are intentionally negative.
         */
        if (
                renderIdentity == -1L
        ) {

            return;
        }


        String publicProperty =
                configPropertyFromStateKey(
                        key.property()
                );


        ModelTransitionEvent.fireDelayed(
                key.item(),
                publicProperty,
                key.ownerId(),
                renderIdentity,
                previousValue,
                newValue,
                config.sound()
        );


        if (
                config.effect() != null

                        && config.effect().enabled()

                        && config.effect().trigger()
                        == ModelDelayConfig.TransitionEffectTrigger.DELAYED
        ) {

            ModelTransitionEffects.trigger(
                    key.item(),
                    publicProperty,
                    key.ownerId(),
                    renderIdentity,
                    config.effect()
            );
        }
    }


    /*
     * ================================================================
     * CLIENT TICK
     * ================================================================
     */
    public static void tick() {

        CLIENT_TICK++;


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer localPlayer =
                minecraft.player;


        if (localPlayer != null) {

            synchronizePlayerLocations(
                    localPlayer
            );
        }


        Iterator<
                Map.Entry<StateKey, Integer>
                > iterator =
                REMAINING.entrySet().iterator();


        while (iterator.hasNext()) {

            Map.Entry<StateKey, Integer> entry =
                    iterator.next();


            StateKey key =
                    entry.getKey();


            int remaining =
                    entry.getValue() - 1;


            if (remaining > 0) {

                entry.setValue(
                        remaining
                );


                continue;
            }


            float previousValue =
                    CURRENT_VALUE.getOrDefault(
                            key,
                            0.0F
                    );


            int previousRange =
                    CURRENT_RANGE.getOrDefault(
                            key,
                            -1
                    );


            float targetValue =
                    TARGET_VALUE.getOrDefault(
                            key,
                            previousValue
                    );


            int targetRange =
                    TARGET_RANGE.getOrDefault(
                            key,
                            previousRange
                    );


            CURRENT_VALUE.put(
                    key,
                    targetValue
            );


            CURRENT_RANGE.put(
                    key,
                    targetRange
            );


            TARGET_VALUE.put(
                    key,
                    targetValue
            );


            TARGET_RANGE.put(
                    key,
                    targetRange
            );


            TRANSITIONING.put(
                    key,
                    false
            );


            /*
             * ============================================================
             * DELAYED RANGE TRANSITION COMPLETE
             * ============================================================
             *
             * VALUE behavior changes models whenever the exposed raw value
             * changes.
             *
             * THRESHOLD behavior only changes models when the exposed
             * threshold/range index changes. Raw movement inside the same
             * threshold does not count as a transition.
             */
            /*
             * State keys for parameterized range properties deliberately
             * contain runtime details, for example:
             *
             *     range.damage{normalize=true}
             *     range.count{normalize=false}
             *     range.use_cycle{period=20}
             *
             * The .mdprop config, however, is stored under the public key:
             *
             *     range.damage
             *     range.count
             *     range.use_cycle
             *
             * Strip the internal state suffix before looking the config up.
             */
            String configProperty =
                    configPropertyFromStateKey(
                            key.property()
                    );


            ModelDelayConfig.RangeDelayConfig config =
                    ModelDelayConfig.getRange(
                            key.item(),
                            configProperty
                    );


            if (
                    config != null
            ) {

                Object previousModelValue;
                Object newModelValue;


                if (
                        config.behavior()
                                == ModelDelayConfig.RangeBehavior.VALUE
                ) {

                    previousModelValue =
                            previousValue;


                    newModelValue =
                            targetValue;

                } else {

                    previousModelValue =
                            previousRange;


                    newModelValue =
                            targetRange;
                }


                /*
                 * CHANGE means the value/model actually exposed to the item
                 * model changed. For both VALUE and THRESHOLD behavior that
                 * is this completion point when a delay was active.
                 */
                if (!previousModelValue.equals(newModelValue)) {

                    dispatchRangeChange(
                            key,
                            previousModelValue,
                            newModelValue,
                            config
                    );
                }


                /*
                 * For THRESHOLD behavior, DELAYED is the completed return
                 * to the fallback/base model only. Intermediate threshold
                 * steps are ordinary CHANGE events.
                 *
                 * IMPORTANT: getRangeIndex() returns -1 below the first
                 * threshold. Index 0 is the FIRST threshold entry, not
                 * fallback.
                 *
                 * VALUE behavior has no discrete fallback region, so it
                 * keeps the normal delayed-completion semantics.
                 */
                if (
                        config.behavior()
                                == ModelDelayConfig.RangeBehavior.VALUE
                ) {

                    dispatchRangeDelayed(
                            key,
                            previousModelValue,
                            newModelValue,
                            config
                    );

                } else if (
                        config.behavior()
                                == ModelDelayConfig.RangeBehavior.THRESHOLD

                                && newModelValue instanceof Integer

                                && ((Integer) newModelValue) == -1

                                && THRESHOLD_SEQUENCE_ACTIVE.getOrDefault(
                                key,
                                false
                        )
                ) {

                    /*
                     * This is the delayed RELEASE/RETURN event:
                     * the property previously entered a non-base threshold
                     * and has now completed its delayed return to fallback.
                     */
                    dispatchRangeDelayed(
                            key,
                            previousModelValue,
                            newModelValue,
                            config
                    );


                    THRESHOLD_SEQUENCE_ACTIVE.put(
                            key,
                            false
                    );
                }
            }


            iterator.remove();
        }


        pruneStaleState();
    }


    /*
     * ================================================================
     * CONFIG PROPERTY FROM STATE KEY
     * ================================================================
     *
     * Public .mdprop keys stay stable while the internal state key may
     * contain node-specific parameters inside {...}.
     */
    private static String configPropertyFromStateKey(
            String stateProperty
    ) {

        int parameterStart =
                stateProperty.indexOf(
                        '{'
                );


        if (
                parameterStart < 0
        ) {

            return stateProperty;
        }


        return stateProperty.substring(
                0,
                parameterStart
        );
    }


    /*
     * ================================================================
     * STALE STATE CLEANUP
     * ================================================================
     */
    private static void pruneStaleState() {

        Iterator<Map.Entry<StateKey, Long>> iterator =
                LAST_SEEN.entrySet().iterator();


        while (iterator.hasNext()) {

            Map.Entry<StateKey, Long> entry =
                    iterator.next();


            StateKey key =
                    entry.getKey();


            long age =
                    CLIENT_TICK - entry.getValue();


            if (
                    age < STALE_STATE_TICKS

                            || TRANSITIONING.getOrDefault(
                            key,
                            false
                    )
            ) {

                continue;
            }


            CURRENT_VALUE.remove(key);
            TARGET_VALUE.remove(key);
            CURRENT_RANGE.remove(key);
            TARGET_RANGE.remove(key);
            REMAINING.remove(key);
            TRANSITIONING.remove(key);
            THRESHOLD_SEQUENCE_ACTIVE.remove(key);


            /*
             * Threshold CHANGE observation is separate from the delayed
             * visible-state maps. Remove matching observations too, otherwise
             * every discarded physical stack can leave one entry behind.
             */
            String publicProperty =
                    configPropertyFromStateKey(
                            key.property()
                    );


            LAST_OBSERVED_RANGE.keySet().removeIf(
                    observation ->
                            observation.item().equals(
                                    key.item()
                            )

                                    && observation.property().equals(
                                    publicProperty
                            )

                                    && observation.ownerId()
                                    == key.ownerId()

                                    && observation.renderIdentity()
                                    == key.stackIdentity()
            );


            iterator.remove();
        }
    }


    /*
     * ================================================================
     * INHERIT ACTIVE TRANSITION
     * ================================================================
     *
     * Used when Minecraft changes/replaces a stack identity during
     * inventory/cursor movement.
     */
    private static boolean inheritActiveTransition(
            StateKey newKey,
            int vanillaRange
    ) {

        StateKey candidate =
                null;


        for (
                Map.Entry<StateKey, Boolean> entry
                : TRANSITIONING.entrySet()
        ) {

            StateKey oldKey =
                    entry.getKey();


            if (!entry.getValue()) {
                continue;
            }


            if (oldKey.equals(newKey)) {
                continue;
            }


            if (
                    oldKey.ownerId()
                            != newKey.ownerId()
            ) {

                continue;
            }


            if (
                    !oldKey.item().equals(
                            newKey.item()
                    )
            ) {

                continue;
            }


            if (
                    !oldKey.property().equals(
                            newKey.property()
                    )
            ) {

                continue;
            }


            int oldTargetRange =
                    TARGET_RANGE.getOrDefault(
                            oldKey,
                            CURRENT_RANGE.getOrDefault(
                                    oldKey,
                                    -1
                            )
                    );


            if (oldTargetRange != vanillaRange) {
                continue;
            }


            if (candidate != null) {

                return false;
            }


            candidate =
                    oldKey;
        }


        if (candidate == null) {

            return false;
        }


        Float currentValue =
                CURRENT_VALUE.get(
                        candidate
                );


        Float targetValue =
                TARGET_VALUE.get(
                        candidate
                );


        Integer currentRange =
                CURRENT_RANGE.get(
                        candidate
                );


        Integer targetRange =
                TARGET_RANGE.get(
                        candidate
                );


        Integer remaining =
                REMAINING.get(
                        candidate
                );


        if (
                currentValue == null
                        || targetValue == null
                        || currentRange == null
                        || targetRange == null
                        || remaining == null
        ) {

            return false;
        }


        CURRENT_VALUE.put(
                newKey,
                currentValue
        );


        TARGET_VALUE.put(
                newKey,
                targetValue
        );


        CURRENT_RANGE.put(
                newKey,
                currentRange
        );


        TARGET_RANGE.put(
                newKey,
                targetRange
        );


        REMAINING.put(
                newKey,
                remaining
        );


        TRANSITIONING.put(
                newKey,
                true
        );


        CURRENT_VALUE.remove(
                candidate
        );


        TARGET_VALUE.remove(
                candidate
        );


        CURRENT_RANGE.remove(
                candidate
        );


        TARGET_RANGE.remove(
                candidate
        );


        REMAINING.remove(
                candidate
        );


        TRANSITIONING.remove(
                candidate
        );


        return true;
    }


    /*
     * ================================================================
     * AUTHORITATIVE COUNT VALUE
     * ================================================================
     *
     * Minecraft may continue evaluating a stale render copy of a held
     * ItemStack for a short time after pickup/merge.
     *
     * For range.count that is disastrous: the delayed state can see:
     *
     *     live inventory count  -> target range
     *     stale render count    -> current range
     *     live inventory count  -> target range
     *     ...
     *
     * and repeatedly cancel/restart a 5 tick transition until the stale
     * render copy disappears. That is what makes pickup look like a
     * several-second delay while dropping behaves correctly.
     *
     * Count state already has a stable player-slot identity. Once a count
     * evaluation belongs to that slot, always evaluate the count from the
     * CURRENT inventory stack in that slot rather than from whichever
     * render copy happened to call us.
     */
    private static float authoritativeCountValue(
            StateKey key,
            ItemStack evaluatedStack,
            float vanillaValue
    ) {

        if (
                !key.property().startsWith(
                        "range.count{"
                )
        ) {

            return vanillaValue;
        }


        /*
         * Stable count-location identities have the sign bit set.
         * -1 remains the explicit "not found" sentinel elsewhere.
         */
        if (
                key.stackIdentity() >= 0L

                        || key.stackIdentity() == -1L
        ) {

            return vanillaValue;
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player == null

                        || player.getId()
                        != key.ownerId()
        ) {

            return vanillaValue;
        }


        int slot =
                (int) (
                        key.stackIdentity()
                                & 0xFFFFFFFFL
                );


        ItemStack authoritativeStack;


        if (
                slot == Inventory.SLOT_OFFHAND
        ) {

            authoritativeStack =
                    player.getInventory().getItem(
                            Inventory.SLOT_OFFHAND
                    );

        } else if (
                slot >= 0

                        && slot < Inventory.INVENTORY_SIZE
        ) {

            authoritativeStack =
                    player.getInventory().getItem(
                            slot
                    );

        } else {

            return vanillaValue;
        }


        if (
                authoritativeStack.isEmpty()

                        || authoritativeStack.getItem()
                        != evaluatedStack.getItem()
        ) {

            return vanillaValue;
        }


        float count =
                authoritativeStack.getCount();


        if (
                key.property().contains(
                        "normalize=true"
                )
        ) {

            int maxStackSize =
                    authoritativeStack.getMaxStackSize();


            if (
                    maxStackSize > 0
            ) {

                count /=
                        (float) maxStackSize;
            }
        }


        return count;
    }


    /*
     * ================================================================
     * USE-DRIVEN RANGE ACTIVATION
     * ================================================================
     *
     * crossbow/pull can evaluate to a non-fallback range simply because a
     * crossbow is being rendered/held. That must NOT arm a DELAYED release
     * event.
     *
     * For this property, arm the release latch only while the local player
     * is actually USING this exact logical stack. The latch remains armed
     * after use stops, and the later delayed return to fallback emits the
     * one DELAYED event.
     */
    private static boolean shouldArmThresholdRelease(
            StateKey key
    ) {

        String publicProperty =
                configPropertyFromStateKey(
                        key.property()
                );


        if (
                !"range.crossbow/pull".equals(
                        publicProperty
                )
        ) {

            return true;
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player == null

                        || !player.isUsingItem()
        ) {

            return false;
        }


        ItemStack useStack =
                player.getUseItem();


        if (
                useStack.isEmpty()

                        || !key.item().equals(
                        BuiltInRegistries.ITEM.getKey(
                                useStack.getItem()
                        )
                )
        ) {

            return false;
        }


        long useIdentity =
                resolvePlayerStackIdentity(
                        useStack,
                        player
                );


        long transitionIdentity =
                resolveTransitionRenderIdentity(
                        key
                );


        return transitionIdentity != -1L

                && useIdentity
                == transitionIdentity;
    }


    /*
     * ================================================================
     * THRESHOLD CHANGE OBSERVATION
     * ================================================================
     */
    private static void observeThresholdChange(
            StateKey key,
            int vanillaRange,
            ModelDelayConfig.RangeDelayConfig config
    ) {

        long renderIdentity =
                resolveTransitionRenderIdentity(
                        key
                );


        /*
         * Inventory/GUI/world evaluations are not transition events for
         * the locally-held rendered item.
         */
        if (
                renderIdentity == -1L
        ) {

            return;
        }


        /*
         * RANGE.COUNT AUTHORITATIVE CHANGE SOURCE
         * ============================================================
         *
         * Count is unusual because Minecraft can continue evaluating stale
         * ownerless ItemStack render copies for a short time after a pickup
         * or merge. resolveTransitionRenderIdentity() intentionally maps
         * those copies back to the currently-held count slot so particles
         * can attach to the correct rendered item.
         *
         * That remapping is useful for EFFECT RENDERING, but it must not be
         * used as permission for CHANGE OBSERVATION. Otherwise a stale copy
         * containing count=1 and the real held stack containing count=2 can
         * alternate through this method:
         *
         *     1 -> 2 -> 1 -> 2 -> ...
         *
         * The active particle merely gets replaced under the same effect key,
         * but sound is event-based, so every false crossing becomes another
         * audible sound.
         *
         * The authoritative count state is keyed directly by the stable
         * player-slot identity. Only that state may announce a threshold
         * CHANGE. Ownerless/stale copies have their own positive object
         * identity and are ignored here.
         */
        if (
                key.property().startsWith(
                        "range.count{"
                )

                        && key.stackIdentity()
                        != renderIdentity
        ) {

            return;
        }


        ThresholdObservationKey observationKey =
                new ThresholdObservationKey(
                        key.item(),
                        configPropertyFromStateKey(
                                key.property()
                        ),
                        key.ownerId(),
                        renderIdentity
                );


        Integer previousRange =
                LAST_OBSERVED_RANGE.put(
                        observationKey,
                        vanillaRange
                );


        /*
         * First observation is state seeding, not a model change.
         */
        if (
                previousRange == null

                        || previousRange
                        == vanillaRange
        ) {

            return;
        }


        dispatchRangeChange(
                key,
                previousRange,
                vanillaRange,
                config
        );
    }


    /*
     * ================================================================
     * GET
     * ================================================================
     */
    public static float get(
            ItemStack stack,
            @Nullable ItemOwner owner,
            Identifier itemId,
            String property,
            float vanillaValue,
            ModelDelayConfig.RangeDelayConfig config,
            int seed,
            float[] thresholds
    ) {

        StateKey key =
                createKey(
                        stack,
                        owner,
                        itemId,
                        property,
                        seed
                );


        LAST_SEEN.put(
                key,
                CLIENT_TICK
        );


        vanillaValue =
                authoritativeCountValue(
                        key,
                        stack,
                        vanillaValue
                );


        int vanillaRange =
                getRangeIndex(
                        thresholds,
                        vanillaValue
                );


        if (
                config.behavior()
                        == ModelDelayConfig.RangeBehavior.THRESHOLD
        ) {

            /*
             * getRangeIndex() uses -1 for the fallback/base model.
             * Threshold entry 0 is therefore already an activated range.
             * Keep this latched until the delayed return to -1 completes.
             */
            if (
                    vanillaRange >= 0

                            && shouldArmThresholdRelease(
                            key
                    )
            ) {

                THRESHOLD_SEQUENCE_ACTIVE.put(
                        key,
                        true
                );
            }
        }


        /*
         * ============================================================
         * FIRST EVALUATION
         * ============================================================
         */
        if (!CURRENT_VALUE.containsKey(key)) {

            boolean inherited =
                    inheritActiveTransition(
                            key,
                            vanillaRange
                    );


            if (!inherited) {

                CURRENT_VALUE.put(
                        key,
                        vanillaValue
                );


                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                CURRENT_RANGE.put(
                        key,
                        vanillaRange
                );


                TARGET_RANGE.put(
                        key,
                        vanillaRange
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return vanillaValue;
            }
        }


        float currentValue =
                CURRENT_VALUE.get(
                        key
                );


        float targetValue =
                TARGET_VALUE.getOrDefault(
                        key,
                        currentValue
                );


        int currentRange =
                CURRENT_RANGE.getOrDefault(
                        key,
                        getRangeIndex(
                                thresholds,
                                currentValue
                        )
                );


        int targetRange =
                TARGET_RANGE.getOrDefault(
                        key,
                        currentRange
                );


        boolean transitioning =
                TRANSITIONING.getOrDefault(
                        key,
                        false
                );


        if (
                config.behavior()
                        == ModelDelayConfig.RangeBehavior.VALUE
        ) {

            return processValueBehavior(
                    key,
                    vanillaValue,
                    currentValue,
                    targetValue,
                    transitioning,
                    config
            );
        }


        return processThresholdBehavior(
                key,
                vanillaValue,
                vanillaRange,
                currentValue,
                currentRange,
                targetRange,
                transitioning,
                config
        );
    }


    /*
     * ================================================================
     * VALUE BEHAVIOR
     * ================================================================
     */
    private static float processValueBehavior(
            StateKey key,
            float vanillaValue,
            float currentValue,
            float targetValue,
            boolean transitioning,
            ModelDelayConfig.RangeDelayConfig config
    ) {

        if (transitioning) {

            if (
                    sameValue(
                            vanillaValue,
                            currentValue
                    )
            ) {

                TARGET_VALUE.put(
                        key,
                        currentValue
                );


                REMAINING.remove(
                        key
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return currentValue;
            }


            if (
                    !sameValue(
                            vanillaValue,
                            targetValue
                    )
            ) {

                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                if (config.delay() <= 0) {

                    dispatchRangeChange(
                            key,
                            currentValue,
                            vanillaValue,
                            config
                    );


                    CURRENT_VALUE.put(
                            key,
                            vanillaValue
                    );


                    TARGET_VALUE.put(
                            key,
                            vanillaValue
                    );


                    REMAINING.remove(
                            key
                    );


                    TRANSITIONING.put(
                            key,
                            false
                    );


                    return vanillaValue;
                }


                REMAINING.put(
                        key,
                        config.delay()
                );


                return currentValue;
            }


            return currentValue;
        }


        if (
                sameValue(
                        vanillaValue,
                        currentValue
                )
        ) {

            TARGET_VALUE.put(
                    key,
                    currentValue
            );


            return currentValue;
        }


        TARGET_VALUE.put(
                key,
                vanillaValue
        );


        if (config.delay() <= 0) {

            dispatchRangeChange(
                    key,
                    currentValue,
                    vanillaValue,
                    config
            );


            CURRENT_VALUE.put(
                    key,
                    vanillaValue
            );


            TARGET_VALUE.put(
                    key,
                    vanillaValue
            );


            TRANSITIONING.put(
                    key,
                    false
            );


            REMAINING.remove(
                    key
            );


            return vanillaValue;
        }


        TRANSITIONING.put(
                key,
                true
        );


        REMAINING.put(
                key,
                config.delay()
        );


        return currentValue;
    }


    /*
     * ================================================================
     * THRESHOLD BEHAVIOR
     * ================================================================
     */
    private static float processThresholdBehavior(
            StateKey key,
            float vanillaValue,
            int vanillaRange,
            float currentValue,
            int currentRange,
            int targetRange,
            boolean transitioning,
            ModelDelayConfig.RangeDelayConfig config
    ) {

        if (transitioning) {

            /*
             * Property returned to currently-displayed range.
             */
            if (vanillaRange == currentRange) {

                CURRENT_VALUE.put(
                        key,
                        vanillaValue
                );


                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                CURRENT_RANGE.put(
                        key,
                        vanillaRange
                );


                TARGET_RANGE.put(
                        key,
                        vanillaRange
                );


                REMAINING.remove(
                        key
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return vanillaValue;
            }


            /*
             * Still moving toward same target range.
             *
             * Raw value may continue changing without restarting timer.
             */
            if (vanillaRange == targetRange) {

                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                return currentValue;
            }


            /*
             * New target range.
             */
            TARGET_VALUE.put(
                    key,
                    vanillaValue
            );


            TARGET_RANGE.put(
                    key,
                    vanillaRange
            );


            if (config.delay() <= 0) {

                dispatchRangeChange(
                        key,
                        currentRange,
                        vanillaRange,
                        config
                );


                CURRENT_VALUE.put(
                        key,
                        vanillaValue
                );


                CURRENT_RANGE.put(
                        key,
                        vanillaRange
                );


                TARGET_VALUE.put(
                        key,
                        vanillaValue
                );


                TARGET_RANGE.put(
                        key,
                        vanillaRange
                );


                REMAINING.remove(
                        key
                );


                TRANSITIONING.put(
                        key,
                        false
                );


                return vanillaValue;
            }


            REMAINING.put(
                    key,
                    config.delay()
            );


            return currentValue;
        }


        /*
         * Same threshold/model region.
         */
        if (vanillaRange == currentRange) {

            CURRENT_VALUE.put(
                    key,
                    vanillaValue
            );


            TARGET_VALUE.put(
                    key,
                    vanillaValue
            );


            CURRENT_RANGE.put(
                    key,
                    vanillaRange
            );


            TARGET_RANGE.put(
                    key,
                    vanillaRange
            );


            return vanillaValue;
        }


        /*
         * Begin threshold transition.
         */
        TARGET_VALUE.put(
                key,
                vanillaValue
        );


        TARGET_RANGE.put(
                key,
                vanillaRange
        );


        if (config.delay() <= 0) {

            dispatchRangeChange(
                    key,
                    currentRange,
                    vanillaRange,
                    config
            );


            CURRENT_VALUE.put(
                    key,
                    vanillaValue
            );


            CURRENT_RANGE.put(
                    key,
                    vanillaRange
            );


            TARGET_VALUE.put(
                    key,
                    vanillaValue
            );


            TARGET_RANGE.put(
                    key,
                    vanillaRange
            );


            TRANSITIONING.put(
                    key,
                    false
            );


            REMAINING.remove(
                    key
            );


            return vanillaValue;
        }


        TRANSITIONING.put(
                key,
                true
        );


        REMAINING.put(
                key,
                config.delay()
        );


        return currentValue;
    }


    /*
     * ================================================================
     * RANGE INDEX
     * ================================================================
     */
    private static int getRangeIndex(
            float[] thresholds,
            float value
    ) {

        if (Float.isNaN(value)) {

            return -1;
        }


        if (thresholds.length < 16) {

            for (
                    int i = 0;
                    i < thresholds.length;
                    i++
            ) {

                if (thresholds[i] > value) {

                    return i - 1;
                }
            }


            return thresholds.length - 1;
        }


        int index =
                Arrays.binarySearch(
                        thresholds,
                        value
                );


        if (index < 0) {

            int insertionPoint =
                    ~index;


            return insertionPoint - 1;
        }


        return index;
    }


    private static boolean sameValue(
            float a,
            float b
    ) {

        return Float.compare(
                a,
                b
        ) == 0;
    }


    /*
     * ================================================================
     * BASIC STACK IDENTITY
     * ================================================================
     */
    private static long getOrCreateStackIdentity(
            ItemStack stack
    ) {

        Long existing =
                STACK_IDENTITIES.get(
                        stack
                );


        if (existing != null) {

            return existing;
        }


        long created =
                NEXT_STACK_ID.getAndIncrement();


        STACK_IDENTITIES.put(
                stack,
                created
        );


        return created;
    }


    /*
     * ================================================================
     * CURRENT PLAYER LOCATIONS
     * ================================================================
     */
    private static Map<LocationKey, ItemStack> collectPlayerLocations(
            Player player
    ) {

        Map<LocationKey, ItemStack> locations =
                new HashMap<>();


        Inventory inventory =
                player.getInventory();


        for (
                int slot = 0;
                slot < Inventory.INVENTORY_SIZE;
                slot++
        ) {

            ItemStack stack =
                    inventory.getItem(
                            slot
                    );


            if (!stack.isEmpty()) {

                locations.put(
                        new LocationKey(
                                player.getId(),
                                slot
                        ),
                        stack
                );
            }
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (!offhand.isEmpty()) {

            locations.put(
                    new LocationKey(
                            player.getId(),
                            Inventory.SLOT_OFFHAND
                    ),
                    offhand
            );
        }


        ItemStack carried =
                player.containerMenu.getCarried();


        if (!carried.isEmpty()) {

            locations.put(
                    new LocationKey(
                            player.getId(),
                            CURSOR_SLOT
                    ),
                    carried
            );
        }


        return locations;
    }


    /*
     * ================================================================
     * PLAYER MOVEMENT SYNCHRONIZATION
     * ================================================================
     */
    private static void synchronizePlayerLocations(
            Player player
    ) {

        int ownerId =
                player.getId();


        Map<LocationKey, ItemStack> currentLocations =
                collectPlayerLocations(
                        player
                );


        Map<LocationKey, TrackedLocation> previousLocations =
                new HashMap<>();


        for (
                Map.Entry<LocationKey, TrackedLocation> entry
                : PLAYER_LOCATIONS.entrySet()
        ) {

            if (
                    entry.getKey().ownerId()
                            == ownerId
            ) {

                previousLocations.put(
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }


        Map<LocationKey, TrackedLocation> newLocations =
                new HashMap<>();


        List<LocationKey> usedPreviousLocations =
                new ArrayList<>();


        /*
         * PASS 1 — exact known stack object.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey location =
                    entry.getKey();


            ItemStack currentStack =
                    entry.getValue();


            Long knownIdentity =
                    STACK_IDENTITIES.get(
                            currentStack
                    );


            if (knownIdentity == null) {
                continue;
            }


            newLocations.put(
                    location,
                    new TrackedLocation(
                            currentStack,
                            currentStack.copy(),
                            knownIdentity
                    )
            );


            for (
                    Map.Entry<LocationKey, TrackedLocation> previousEntry
                    : previousLocations.entrySet()
            ) {

                if (
                        previousEntry.getValue().identity()
                                == knownIdentity
                ) {

                    usedPreviousLocations.add(
                            previousEntry.getKey()
                    );
                }
            }
        }


        /*
         * PASS 2 — movement with exact item/components.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey currentLocation =
                    entry.getKey();


            if (
                    newLocations.containsKey(
                            currentLocation
                    )
            ) {

                continue;
            }


            ItemStack currentStack =
                    entry.getValue();


            LocationKey matchedPreviousLocation =
                    null;


            TrackedLocation matchedPrevious =
                    null;


            for (
                    Map.Entry<LocationKey, TrackedLocation> previousEntry
                    : previousLocations.entrySet()
            ) {

                LocationKey previousLocation =
                        previousEntry.getKey();


                if (
                        usedPreviousLocations.contains(
                                previousLocation
                        )
                ) {

                    continue;
                }


                ItemStack previousSnapshot =
                        previousEntry.getValue().snapshot();


                if (
                        ItemStack.isSameItemSameComponents(
                                currentStack,
                                previousSnapshot
                        )
                ) {

                    matchedPreviousLocation =
                            previousLocation;


                    matchedPrevious =
                            previousEntry.getValue();


                    if (
                            !previousLocation.equals(
                                    currentLocation
                            )
                    ) {

                        break;
                    }
                }
            }


            if (matchedPrevious != null) {

                long identity =
                        matchedPrevious.identity();


                STACK_IDENTITIES.put(
                        currentStack,
                        identity
                );


                newLocations.put(
                        currentLocation,
                        new TrackedLocation(
                                currentStack,
                                currentStack.copy(),
                                identity
                        )
                );


                usedPreviousLocations.add(
                        matchedPreviousLocation
                );
            }
        }


        /*
         * PASS 3 — same-location component mutation.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey location =
                    entry.getKey();


            if (
                    newLocations.containsKey(
                            location
                    )
            ) {

                continue;
            }


            ItemStack currentStack =
                    entry.getValue();


            TrackedLocation previous =
                    previousLocations.get(
                            location
                    );


            if (
                    previous == null
                            || usedPreviousLocations.contains(
                            location
                    )
            ) {

                continue;
            }


            ItemStack previousSnapshot =
                    previous.snapshot();


            if (
                    !previousSnapshot.isEmpty()
                            && previousSnapshot.getItem()
                            == currentStack.getItem()
            ) {

                long identity =
                        previous.identity();


                STACK_IDENTITIES.put(
                        currentStack,
                        identity
                );


                newLocations.put(
                        location,
                        new TrackedLocation(
                                currentStack,
                                currentStack.copy(),
                                identity
                        )
                );


                usedPreviousLocations.add(
                        location
                );
            }
        }


        /*
         * PASS 4 — movement fallback by same item + count.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey currentLocation =
                    entry.getKey();


            if (
                    newLocations.containsKey(
                            currentLocation
                    )
            ) {

                continue;
            }


            ItemStack currentStack =
                    entry.getValue();


            LocationKey candidateLocation =
                    null;


            TrackedLocation candidate =
                    null;


            int candidateCount =
                    0;


            for (
                    Map.Entry<LocationKey, TrackedLocation> previousEntry
                    : previousLocations.entrySet()
            ) {

                LocationKey previousLocation =
                        previousEntry.getKey();


                if (
                        usedPreviousLocations.contains(
                                previousLocation
                        )
                ) {

                    continue;
                }


                ItemStack previousSnapshot =
                        previousEntry.getValue().snapshot();


                if (
                        previousSnapshot.getItem()
                                == currentStack.getItem()
                                && previousSnapshot.getCount()
                                == currentStack.getCount()
                ) {

                    candidateLocation =
                            previousLocation;


                    candidate =
                            previousEntry.getValue();


                    candidateCount++;
                }
            }


            if (
                    candidate != null
                            && candidateCount == 1
            ) {

                long identity =
                        candidate.identity();


                STACK_IDENTITIES.put(
                        currentStack,
                        identity
                );


                newLocations.put(
                        currentLocation,
                        new TrackedLocation(
                                currentStack,
                                currentStack.copy(),
                                identity
                        )
                );


                usedPreviousLocations.add(
                        candidateLocation
                );
            }
        }


        /*
         * PASS 5 — genuinely new stacks.
         */
        for (
                Map.Entry<LocationKey, ItemStack> entry
                : currentLocations.entrySet()
        ) {

            LocationKey location =
                    entry.getKey();


            if (
                    newLocations.containsKey(
                            location
                    )
            ) {

                continue;
            }


            ItemStack currentStack =
                    entry.getValue();


            long identity =
                    getOrCreateStackIdentity(
                            currentStack
                    );


            newLocations.put(
                    location,
                    new TrackedLocation(
                            currentStack,
                            currentStack.copy(),
                            identity
                    )
            );
        }


        PLAYER_LOCATIONS.keySet().removeIf(
                key ->
                        key.ownerId()
                                == ownerId
        );


        PLAYER_LOCATIONS.putAll(
                newLocations
        );
    }


    /*
     * ================================================================
     * EXACT PLAYER LOCATION
     * ================================================================
     */
    private static @Nullable LocationKey findExactLocation(
            ItemStack stack,
            Player player
    ) {

        Inventory inventory =
                player.getInventory();


        for (
                int slot = 0;
                slot < Inventory.INVENTORY_SIZE;
                slot++
        ) {

            if (
                    inventory.getItem(
                            slot
                    ) == stack
            ) {

                return new LocationKey(
                        player.getId(),
                        slot
                );
            }
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (offhand == stack) {

            return new LocationKey(
                    player.getId(),
                    Inventory.SLOT_OFFHAND
            );
        }


        ItemStack carried =
                player.containerMenu.getCarried();


        if (carried == stack) {

            return new LocationKey(
                    player.getId(),
                    CURSOR_SLOT
            );
        }


        return null;
    }


    /*
     * ================================================================
     * RESOLVE PLAYER STACK IDENTITY
     * ================================================================
     */
    private static long resolvePlayerStackIdentity(
            ItemStack stack,
            Player player
    ) {

        synchronizePlayerLocations(
                player
        );


        LocationKey exactLocation =
                findExactLocation(
                        stack,
                        player
                );


        if (exactLocation != null) {

            TrackedLocation tracked =
                    PLAYER_LOCATIONS.get(
                            exactLocation
                    );


            if (tracked != null) {

                STACK_IDENTITIES.put(
                        stack,
                        tracked.identity()
                );


                return tracked.identity();
            }
        }


        Long known =
                STACK_IDENTITIES.get(
                        stack
                );


        if (known != null) {

            return known;
        }


        /*
         * Unique component-equivalent real stack.
         */
        TrackedLocation matchingTracked =
                null;


        int matches =
                0;


        for (
                Map.Entry<LocationKey, TrackedLocation> entry
                : PLAYER_LOCATIONS.entrySet()
        ) {

            if (
                    entry.getKey().ownerId()
                            != player.getId()
            ) {

                continue;
            }


            TrackedLocation tracked =
                    entry.getValue();


            if (
                    ItemStack.isSameItemSameComponents(
                            stack,
                            tracked.object()
                    )
            ) {

                matchingTracked =
                        tracked;


                matches++;
            }
        }


        if (
                matchingTracked != null
                        && matches == 1
        ) {

            STACK_IDENTITIES.put(
                    stack,
                    matchingTracked.identity()
            );


            return matchingTracked.identity();
        }


        /*
         * Selected stale render copy.
         */
        Inventory inventory =
                player.getInventory();


        ItemStack selected =
                inventory.getSelectedItem();


        if (
                !selected.isEmpty()
                        && selected.getItem()
                        == stack.getItem()
        ) {

            LocationKey selectedLocation =
                    new LocationKey(
                            player.getId(),
                            inventory.getSelectedSlot()
                    );


            TrackedLocation tracked =
                    PLAYER_LOCATIONS.get(
                            selectedLocation
                    );


            if (tracked != null) {

                STACK_IDENTITIES.put(
                        stack,
                        tracked.identity()
                );


                return tracked.identity();
            }
        }


        /*
         * Cursor stale render copy.
         */
        ItemStack carried =
                player.containerMenu.getCarried();


        if (
                !carried.isEmpty()
                        && carried.getItem()
                        == stack.getItem()
        ) {

            LocationKey cursorLocation =
                    new LocationKey(
                            player.getId(),
                            CURSOR_SLOT
                    );


            TrackedLocation tracked =
                    PLAYER_LOCATIONS.get(
                            cursorLocation
                    );


            if (tracked != null) {

                STACK_IDENTITIES.put(
                        stack,
                        tracked.identity()
                );


                return tracked.identity();
            }
        }


        return getOrCreateStackIdentity(
                stack
        );
    }


    /*
     * ================================================================
     * COUNT STACK IDENTITY
     * ================================================================
     *
     * range.count is different from most range properties because
     * changing the count can mutate or replace the ItemStack object.
     *
     * Use the player's inventory LOCATION as the stable logical identity
     * for count transitions. This lets a pending 4 -> 3 transition remain
     * the same transition even if vanilla swaps the ItemStack object.
     */
    private static long countLocationIdentity(
            int ownerId,
            int slot
    ) {

        return Long.MIN_VALUE

                | (
                (
                        (long) ownerId
                                & 0x7FFFFFFFL
                ) << 32
        )

                | (
                (long) slot
                        & 0xFFFFFFFFL
        );
    }


    private static long resolveCountStackIdentity(
            ItemStack stack,
            Player player
    ) {

        synchronizePlayerLocations(
                player
        );


        LocationKey exactLocation =
                findExactLocation(
                        stack,
                        player
                );


        if (
                exactLocation != null
        ) {

            return countLocationIdentity(
                    player.getId(),
                    exactLocation.slot()
            );
        }


        Inventory inventory =
                player.getInventory();


        ItemStack selected =
                inventory.getSelectedItem();


        if (
                !selected.isEmpty()

                        && selected.getItem()
                        == stack.getItem()
        ) {

            return countLocationIdentity(
                    player.getId(),
                    inventory.getSelectedSlot()
            );
        }


        ItemStack offhand =
                inventory.getItem(
                        Inventory.SLOT_OFFHAND
                );


        if (
                !offhand.isEmpty()

                        && offhand.getItem()
                        == stack.getItem()
        ) {

            return countLocationIdentity(
                    player.getId(),
                    Inventory.SLOT_OFFHAND
            );
        }


        return getOrCreateStackIdentity(
                stack
        );
    }


    public static long resolveCountRenderStackIdentity(
            ItemStack stack,
            @Nullable LivingEntity owner
    ) {

        if (
                owner instanceof Player player
        ) {

            return resolveCountStackIdentity(
                    stack,
                    player
            );
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player != null
        ) {

            return resolveCountStackIdentity(
                    stack,
                    player
            );
        }


        return getOrCreateStackIdentity(
                stack
        );
    }


    /*
     * ================================================================
     * RENDER STACK IDENTITY
     * ================================================================
     *
     * Range effects must be attached with the same physical-stack
     * identity domain used by ModelDelayRangeState.
     */
    public static long resolveRenderStackIdentity(
            ItemStack stack,
            @Nullable LivingEntity owner
    ) {

        if (
                owner instanceof Player player
        ) {

            return resolvePlayerStackIdentity(
                    stack,
                    player
            );
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player != null
        ) {

            return resolvePlayerStackIdentity(
                    stack,
                    player
            );
        }


        return getOrCreateStackIdentity(
                stack
        );
    }


    /*
     * ================================================================
     * STATE KEY CREATION
     * ================================================================
     */
    private static StateKey createKey(
            ItemStack stack,
            @Nullable ItemOwner owner,
            Identifier itemId,
            String property,
            int seed
    ) {

        /*
         * ============================================================
         * COOLDOWN
         * ============================================================
         *
         * Cooldown belongs to the player's cooldown manager rather than
         * one physical ItemStack.
         */
        if (
                "range.cooldown".equals(
                        property
                )
        ) {

            if (owner != null) {

                LivingEntity livingEntity =
                        owner.asLivingEntity();


                if (livingEntity instanceof Player player) {

                    return new StateKey(
                            itemId,
                            property,
                            player.getId(),
                            -1,
                            0L
                    );
                }
            }


            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            if (localPlayer != null) {

                return new StateKey(
                        itemId,
                        property,
                        localPlayer.getId(),
                        -1,
                        0L
                );
            }


            return new StateKey(
                    itemId,
                    property,
                    -1,
                    -1,
                    0L
            );
        }


        /*
         * ============================================================
         * TIME
         * ============================================================
         *
         * Time depends on owner/world state, not a physical ItemStack.
         *
         * Ownerless NeedleDirectionHelper rendering returns 0.0 and is
         * deliberately kept separate.
         */
        if (
                property.startsWith(
                        "range.time{"
                )
        ) {

            if (owner != null) {

                LivingEntity livingEntity =
                        owner.asLivingEntity();


                if (livingEntity != null) {

                    return new StateKey(
                            itemId,
                            property,
                            livingEntity.getId(),
                            -1,
                            0L
                    );
                }
            }


            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            int localOwnerId =
                    localPlayer != null
                            ? localPlayer.getId()
                            : -1;


            return new StateKey(
                    itemId,
                    property,
                    localOwnerId,
                    OWNERLESS_NEEDLE_CONTEXT,
                    0L
            );
        }


        /*
         * ============================================================
         * COMPASS
         * ============================================================
         *
         * Compass combines BOTH:
         *
         *     owner/world state
         *
         * and potentially:
         *
         *     ItemStack state
         *
         * because target=lodestone reads:
         *
         *     DataComponents.LODESTONE_TRACKER
         *
         *
         * So an owner-driven compass retains physical stack identity.
         *
         * Ownerless NeedleDirectionHelper evaluation is deliberately
         * separate because vanilla returns 0.0 when owner == null.
         */
        if (
                property.startsWith(
                        "range.compass{"
                )
        ) {

            if (owner != null) {

                LivingEntity livingEntity =
                        owner.asLivingEntity();


                if (livingEntity instanceof Player player) {

                    long identity =
                            resolvePlayerStackIdentity(
                                    stack,
                                    player
                            );


                    return new StateKey(
                            itemId,
                            property,
                            player.getId(),
                            -1,
                            identity
                    );
                }


                if (livingEntity != null) {

                    return new StateKey(
                            itemId,
                            property,
                            livingEntity.getId(),
                            -1,
                            getOrCreateStackIdentity(
                                    stack
                            )
                    );
                }
            }


            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            int localOwnerId =
                    localPlayer != null
                            ? localPlayer.getId()
                            : -1;


            /*
             * Keep ownerless GUI compass state separate from the
             * player-owned compass state.
             */
            return new StateKey(
                    itemId,
                    property,
                    localOwnerId,
                    OWNERLESS_NEEDLE_CONTEXT,
                    getOrCreateStackIdentity(
                            stack
                    )
            );
        }


        /*
         * ============================================================
         * COUNT
         * ============================================================
         *
         * Count uses a stable slot/location identity so count mutations
         * do not silently reinitialize the delayed state under a new key.
         */
        if (
                property.startsWith(
                        "range.count{"
                )
        ) {

            if (
                    owner != null
            ) {

                LivingEntity livingEntity =
                        owner.asLivingEntity();


                if (
                        livingEntity instanceof Player player
                ) {

                    return new StateKey(
                            itemId,
                            property,
                            player.getId(),
                            -1,
                            resolveCountStackIdentity(
                                    stack,
                                    player
                            )
                    );
                }
            }


            /*
             * OWNERLESS COUNT EVALUATIONS
             *
             * Minecraft can evaluate the actually-held stack with owner ==
             * null in some render/update paths. If we make every ownerless
             * evaluation independent, the held stack can alternate between:
             *
             *     player-slot state
             *
             * and
             *
             *     ownerless object state
             *
             * after a pickup. That repeatedly reinitializes/refreshes the
             * visible count transition and makes a 5 tick delay look like
             * several seconds.
             *
             * However, we must NOT go back to matching merely by item type,
             * because that caused dropped sticks to share the held stick's
             * delay state.
             *
             * The safe middle ground is:
             *
             *     ownerless + EXACT inventory object -> player slot identity
             *     anything else                    -> independent identity
             *
             * A dropped ItemEntity stack is a different ItemStack object, so
             * it cannot pass findExactLocation().
             */
            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            if (
                    localPlayer != null
            ) {

                synchronizePlayerLocations(
                        localPlayer
                );


                LocationKey exactLocation =
                        findExactLocation(
                                stack,
                                localPlayer
                        );


                if (
                        exactLocation != null
                ) {

                    return new StateKey(
                            itemId,
                            property,
                            localPlayer.getId(),
                            -1,
                            countLocationIdentity(
                                    localPlayer.getId(),
                                    exactLocation.slot()
                            )
                    );
                }
            }


            return new StateKey(
                    itemId,
                    property,
                    -1,
                    seed,
                    getOrCreateStackIdentity(
                            stack
                    )
            );
        }


        /*
         * ============================================================
         * NORMAL STACK-SENSITIVE RANGE PROPERTY
         * ============================================================
         */
        if (owner != null) {

            LivingEntity livingEntity =
                    owner.asLivingEntity();


            if (livingEntity instanceof Player player) {

                long identity =
                        resolvePlayerStackIdentity(
                                stack,
                                player
                        );


                return new StateKey(
                        itemId,
                        property,
                        player.getId(),
                        -1,
                        identity
                );
            }
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (player != null) {

            long identity =
                    resolvePlayerStackIdentity(
                            stack,
                            player
                    );


            return new StateKey(
                    itemId,
                    property,
                    player.getId(),
                    -1,
                    identity
            );
        }


        return new StateKey(
                itemId,
                property,
                -1,
                seed,
                getOrCreateStackIdentity(
                        stack
                )
        );
    }


    /*
     * ================================================================
     * CLEAR
     * ================================================================
     */
    public static void clear() {

        CURRENT_VALUE.clear();
        TARGET_VALUE.clear();
        CURRENT_RANGE.clear();
        TARGET_RANGE.clear();
        REMAINING.clear();
        TRANSITIONING.clear();
        LAST_SEEN.clear();
        LAST_OBSERVED_RANGE.clear();
        THRESHOLD_SEQUENCE_ACTIVE.clear();


        STACK_IDENTITIES.clear();
        PLAYER_LOCATIONS.clear();


        NEXT_STACK_ID.set(
                1L
        );


        CLIENT_TICK =
                0L;
    }
}