package com.usujiotarako.client;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ModelTransitionEffects {

    /*
     * ================================================================
     * EFFECT KEY
     * ================================================================
     */
    public record EffectKey(
            Identifier item,
            String property,
            int ownerId,
            long stackIdentity
    ) {
    }


    /*
     * ================================================================
     * MODEL CENTER
     * ================================================================
     */
    public record ModelCenter(
            float x,
            float y,
            float z
    ) {
    }


    /*
     * ================================================================
     * ACTIVE EFFECT
     * ================================================================
     */
    public record ActiveEffect(
            EffectKey key,
            ModelDelayConfig.TransitionEffectConfig config,
            int remainingTicks,
            int totalTicks
    ) {

        public float progress() {

            if (totalTicks <= 0) {

                return 1.0F;
            }


            return 1.0F
                    - (
                    (float) remainingTicks
                            / (float) totalTicks
            );
        }
    }


    /*
     * ================================================================
     * ACTIVE EFFECTS
     * ================================================================
     */
    private static final Map<
            EffectKey,
            ActiveEffect
            > ACTIVE_EFFECTS =
            new HashMap<>();


    /*
     * ================================================================
     * LAST VISIBLE CONDITIONAL VALUE
     * ================================================================
     */
    private record VisibleValueKey(
            Identifier item,
            String property
    ) {
    }


    private static final Map<
            VisibleValueKey,
            Boolean
            > LAST_VISIBLE_VALUES =
            new HashMap<>();


    /*
     * ================================================================
     * STABLE MODEL CENTERS
     * ================================================================
     *
     * Model bounds are display-context dependent. A first-person center is not
     * interchangeable with a third-person center, and left/right hand contexts
     * may differ too.
     */
    private record RenderCenterKey(
            Identifier item,
            ItemDisplayContext displayContext
    ) {
    }


    private record PropertyCenterKey(
            Identifier item,
            String property,
            ItemDisplayContext displayContext
    ) {
    }


    private static final Map<RenderCenterKey, ModelCenter>
            LAST_RENDERED_MODEL_CENTERS =
            new HashMap<>();


    /*
     * One persistent model anchor for each delayed property in each display
     * context. putIfAbsent is intentional: once the base/first model has been
     * learned, reverse transitions do not remap the poof to the alternate
     * model's bounding-box center.
     */
    private static final Map<PropertyCenterKey, ModelCenter>
            PROPERTY_MODEL_CENTERS =
            new HashMap<>();


    private ModelTransitionEffects() {
    }


    /*
     * ================================================================
     * TRIGGER
     * ================================================================
     */
    public static void trigger(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity,
            ModelDelayConfig.TransitionEffectConfig config
    ) {

        if (
                config == null

                        || !config.enabled()
        ) {

            return;
        }


        int duration =
                Math.max(
                        1,
                        config.duration()
                );


        EffectKey key =
                new EffectKey(
                        itemId,
                        property,
                        ownerId,
                        stackIdentity
                );


        /*
         * Learn any still-missing context anchors from the model which was
         * rendered immediately before this transition. Never overwrite an
         * existing property/context anchor here.
         */
        for (
                Map.Entry<RenderCenterKey, ModelCenter> entry
                : LAST_RENDERED_MODEL_CENTERS.entrySet()
        ) {

            RenderCenterKey renderKey =
                    entry.getKey();


            if (
                    !renderKey.item().equals(
                            itemId
                    )
            ) {

                continue;
            }


            PROPERTY_MODEL_CENTERS.putIfAbsent(
                    new PropertyCenterKey(
                            itemId,
                            property,
                            renderKey.displayContext()
                    ),
                    entry.getValue()
            );
        }


        ACTIVE_EFFECTS.put(
                key,
                new ActiveEffect(
                        key,
                        config,
                        duration,
                        duration
                )
        );
    }


    /*
     * ================================================================
     * CANCEL
     * ================================================================
     *
     * Cancel an effect belonging to this logical item/property.
     *
     * This is used when a new transition supersedes a still-running
     * transition effect.
     *
     * Example:
     *
     *     sword RELEASE finishes
     *         ->
     *     poof starts
     *         ->
     *     player swings again before poof expires
     *         ->
     *     old poof is removed immediately
     *
     * We deliberately DO NOT cancel effects belonging to another
     * property.
     */
    public static void cancel(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity
    ) {

        ACTIVE_EFFECTS
                .entrySet()
                .removeIf(
                        entry -> {

                            EffectKey key =
                                    entry.getKey();


                            /*
                             * Different item.
                             */
                            if (
                                    !key.item()
                                            .equals(
                                                    itemId
                                            )
                            ) {

                                return false;
                            }


                            /*
                             * Different delayed property.
                             */
                            if (
                                    !key.property()
                                            .equals(
                                                    property
                                            )
                            ) {

                                return false;
                            }


                            /*
                             * Exact physical/logical state.
                             */
                            if (
                                    key.ownerId()
                                            == ownerId

                                            && key.stackIdentity()
                                            == stackIdentity
                            ) {

                                return true;
                            }


                            /*
                             * Generic conditional effects such as
                             * normal keybind_down use:
                             *
                             *     owner = -1
                             *     stack = 0
                             *
                             * Allow the corresponding transition to
                             * cancel that generic effect too.
                             */
                            return key.ownerId()
                                    == -1

                                    && key.stackIdentity()
                                    == 0L;
                        }
                );
    }


    /*
     * ================================================================
     * OBSERVE CONDITIONAL VALUE
     * ================================================================
     */
    public static void observeConditionalValue(
            Identifier itemId,
            String property,
            boolean visibleValue,
            ModelDelayConfig.TransitionEffectConfig config
    ) {

        if (
                config == null

                        || !config.enabled()

                        || config.trigger()
                        != ModelDelayConfig.TransitionEffectTrigger.CHANGE
        ) {

            return;
        }


        VisibleValueKey key =
                new VisibleValueKey(
                        itemId,
                        property
                );


        Boolean previous =
                LAST_VISIBLE_VALUES.put(
                        key,
                        visibleValue
                );


        /*
         * First observation only establishes the baseline.
         */
        if (
                previous == null
        ) {

            return;
        }


        if (
                previous
                        == visibleValue
        ) {

            return;
        }


        /*
         * Generic conditional effect.
         */
        trigger(
                itemId,
                property,
                -1,
                0L,
                config
        );
    }


    /*
     * ================================================================
     * OBSERVE RENDERED MODEL CENTER
     * ================================================================
     */
    public static void observeRenderedModelCenter(
            Identifier itemId,
            ItemDisplayContext displayContext,
            float centerX,
            float centerY,
            float centerZ
    ) {

        ModelCenter center =
                new ModelCenter(
                        centerX,
                        centerY,
                        centerZ
                );


        LAST_RENDERED_MODEL_CENTERS.put(
                new RenderCenterKey(
                        itemId,
                        displayContext
                ),
                center
        );


        ModelDelayConfig.ItemConfig itemConfig =
                ModelDelayConfig.getItemConfig(
                        itemId
                );


        if (
                itemConfig == null
        ) {

            return;
        }


        for (String property : itemConfig.properties().keySet()) {

            PROPERTY_MODEL_CENTERS.putIfAbsent(
                    new PropertyCenterKey(
                            itemId,
                            property,
                            displayContext
                    ),
                    center
            );
        }


        for (String property : itemConfig.rangeProperties().keySet()) {

            PROPERTY_MODEL_CENTERS.putIfAbsent(
                    new PropertyCenterKey(
                            itemId,
                            property,
                            displayContext
                    ),
                    center
            );
        }


        for (String property : itemConfig.selectProperties().keySet()) {

            PROPERTY_MODEL_CENTERS.putIfAbsent(
                    new PropertyCenterKey(
                            itemId,
                            property,
                            displayContext
                    ),
                    center
            );
        }
    }


    /*
     * ================================================================
     * STABLE MODEL CENTER
     * ================================================================
     */
    public static @Nullable ModelCenter getModelCenter(
            EffectKey effectKey,
            ItemDisplayContext displayContext
    ) {

        if (
                effectKey == null
        ) {

            return null;
        }


        return PROPERTY_MODEL_CENTERS.get(
                new PropertyCenterKey(
                        effectKey.item(),
                        effectKey.property(),
                        displayContext
                )
        );
    }


    /*
     * ================================================================
     * TICK
     * ================================================================
     */
    public static void tick() {

        Iterator<
                Map.Entry<
                        EffectKey,
                        ActiveEffect
                        >
                > iterator =
                ACTIVE_EFFECTS
                        .entrySet()
                        .iterator();


        while (
                iterator.hasNext()
        ) {

            Map.Entry<
                    EffectKey,
                    ActiveEffect
                    > entry =
                    iterator.next();


            ActiveEffect effect =
                    entry.getValue();


            int remaining =
                    effect.remainingTicks()
                            - 1;


            if (
                    remaining <= 0
            ) {

                iterator.remove();

                continue;
            }


            entry.setValue(
                    new ActiveEffect(
                            effect.key(),
                            effect.config(),
                            remaining,
                            effect.totalTicks()
                    )
            );
        }
    }


    /*
     * ================================================================
     * ACTIVE EFFECT FAST GATE
     * ================================================================
     *
     * Held-item rendering happens every frame, while transition effects
     * exist only briefly. Callers can use this before doing any expensive
     * physical-stack identity resolution.
     */
    public static boolean hasActiveEffectForItem(
            Identifier itemId,
            int ownerId
    ) {

        for (
                EffectKey key
                : ACTIVE_EFFECTS.keySet()
        ) {

            if (
                    !key.item().equals(
                            itemId
                    )
            ) {

                continue;
            }


            if (
                    key.ownerId() == ownerId

                            || (
                            key.ownerId() == -1

                                    && key.stackIdentity() == 0L
                    )
            ) {

                return true;
            }
        }


        return false;
    }


    /*
     * ================================================================
     * EXACT LOOKUP
     * ================================================================
     */
    public static @Nullable ActiveEffect get(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity
    ) {

        return ACTIVE_EFFECTS.get(
                new EffectKey(
                        itemId,
                        property,
                        ownerId,
                        stackIdentity
                )
        );
    }


    /*
     * ================================================================
     * RENDER LOOKUP
     * ================================================================
     */
    public static @Nullable ActiveEffect getForRender(
            Identifier itemId,
            int ownerId,
            long stackIdentity
    ) {

        /*
         * Exact effect.
         */
        for (
                Map.Entry<
                        EffectKey,
                        ActiveEffect
                        > entry
                : ACTIVE_EFFECTS.entrySet()
        ) {

            EffectKey key =
                    entry.getKey();


            if (
                    key.item()
                            .equals(
                                    itemId
                            )

                            && key.ownerId()
                            == ownerId

                            && key.stackIdentity()
                            == stackIdentity
            ) {

                return entry.getValue();
            }
        }


        /*
         * Generic conditional effect.
         */
        for (
                Map.Entry<
                        EffectKey,
                        ActiveEffect
                        > entry
                : ACTIVE_EFFECTS.entrySet()
        ) {

            EffectKey key =
                    entry.getKey();


            if (
                    key.item()
                            .equals(
                                    itemId
                            )

                            && key.ownerId()
                            == -1

                            && key.stackIdentity()
                            == 0L
            ) {

                return entry.getValue();
            }
        }


        return null;
    }


    /*
     * ================================================================
     * OWNERLESS RENDER LOOKUP
     * ================================================================
     */
    public static @Nullable ActiveEffect getForRender(
            Identifier itemId,
            long stackIdentity
    ) {

        if (
                stackIdentity != 0L
        ) {

            for (
                    Map.Entry<
                            EffectKey,
                            ActiveEffect
                            > entry
                    : ACTIVE_EFFECTS.entrySet()
            ) {

                EffectKey key =
                        entry.getKey();


                if (
                        key.item()
                                .equals(
                                        itemId
                                )

                                && key.stackIdentity()
                                == stackIdentity
                ) {

                    return entry.getValue();
                }
            }
        }


        /*
         * Generic fallback.
         */
        for (
                Map.Entry<
                        EffectKey,
                        ActiveEffect
                        > entry
                : ACTIVE_EFFECTS.entrySet()
        ) {

            EffectKey key =
                    entry.getKey();


            if (
                    key.item()
                            .equals(
                                    itemId
                            )

                            && key.ownerId()
                            == -1

                            && key.stackIdentity()
                            == 0L
            ) {

                return entry.getValue();
            }
        }


        return null;
    }


    /*
     * ================================================================
     * RANGE-AWARE RENDER LOOKUP
     * ================================================================
     *
     * Conditional and range state use separate physical-stack identity
     * trackers. A range effect therefore cannot be attached using only
     * ModelDelayState's identity.
     */
    public static @Nullable ActiveEffect getForRender(
            Identifier itemId,
            int ownerId,
            long conditionalIdentity,
            long rangeIdentity,
            long rangeCountIdentity,
            long selectIdentity
    ) {

        for (
                Map.Entry<EffectKey, ActiveEffect> entry
                : ACTIVE_EFFECTS.entrySet()
        ) {

            EffectKey key =
                    entry.getKey();


            if (
                    !key.item().equals(
                            itemId
                    )

                            || key.ownerId()
                            != ownerId
            ) {

                continue;
            }


            long renderIdentity;


            if (
                    key.property().equals(
                            "range.count"
                    )
            ) {

                renderIdentity =
                        rangeCountIdentity;

            } else if (
                    key.property().startsWith(
                            "range."
                    )
            ) {

                renderIdentity =
                        rangeIdentity;

            } else if (
                    key.property().startsWith(
                            "select."
                    )
            ) {

                renderIdentity =
                        selectIdentity;

            } else {

                renderIdentity =
                        conditionalIdentity;
            }


            if (
                    key.stackIdentity()
                            == renderIdentity
            ) {

                return entry.getValue();
            }
        }


        /*
         * Generic conditional fallback such as normal keybind_down.
         */
        for (
                Map.Entry<EffectKey, ActiveEffect> entry
                : ACTIVE_EFFECTS.entrySet()
        ) {

            EffectKey key =
                    entry.getKey();


            if (
                    key.item().equals(
                            itemId
                    )

                            && key.ownerId()
                            == -1

                            && key.stackIdentity()
                            == 0L
            ) {

                return entry.getValue();
            }
        }


        return null;
    }


    /*
     * ================================================================
     * HAS EFFECT
     * ================================================================
     */
    public static boolean hasForRender(
            Identifier itemId,
            int ownerId,
            long stackIdentity
    ) {

        return getForRender(
                itemId,
                ownerId,
                stackIdentity
        ) != null;
    }


    /*
     * ================================================================
     * CLEAR
     * ================================================================
     */
    public static void clear() {

        ACTIVE_EFFECTS.clear();

        LAST_VISIBLE_VALUES.clear();

        LAST_RENDERED_MODEL_CENTERS.clear();

        PROPERTY_MODEL_CENTERS.clear();
    }
}