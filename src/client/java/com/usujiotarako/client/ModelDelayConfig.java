package com.usujiotarako.client;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class ModelDelayConfig {

    private static final Map<
            Identifier,
            ItemConfig
            > CONFIGS =
            new HashMap<>();


    private ModelDelayConfig() {
    }


    /*
     * ================================================================
     * CONDITIONAL DELAY MODE
     * ================================================================
     */
    public enum Mode {
        HOLD,
        RELEASE,
        BOTH
    }


    /*
     * ================================================================
     * ITEM BEHAVIOR
     * ================================================================
     */
    public enum Behavior {
        NORMAL,
        EVOLVING
    }


    /*
     * ================================================================
     * CONDITIONAL PROPERTY BEHAVIOR
     * ================================================================
     */
    public enum PropertyBehavior {
        NORMAL,
        HELD
    }


    /*
     * ================================================================
     * RANGE PROPERTY BEHAVIOR
     * ================================================================
     */
    public enum RangeBehavior {
        THRESHOLD,
        VALUE
    }


    /*
     * ================================================================
     * TRANSITION EFFECT TYPE
     * ================================================================
     */
    public enum TransitionEffect {
        NONE,
        POOF
    }


    /*
     * ================================================================
     * TRANSITION EFFECT TRIGGER
     * ================================================================
     */
    public enum TransitionEffectTrigger {
        CHANGE,
        DELAYED
    }

    /*
     * ================================================================
     * TRANSITION SOUND TRIGGER
     * ================================================================
     *
     * Kept separate from TransitionEffectTrigger deliberately.
     *
     * Particles and sounds currently share the same trigger names, but
     * they are independent systems. Keeping their configuration types
     * separate means either system can grow later without tying the two
     * together.
     */
    public enum TransitionSoundTrigger {
        CHANGE,
        DELAYED
    }


    /*
     * ================================================================
     * TRANSITION SOUND CONFIG
     * ================================================================
     */
    public record TransitionSoundConfig(
            Identifier[] sounds,
            TransitionSoundTrigger trigger,
            float volume,
            float pitchMin,
            float pitchMax
    ) {

        public static final float DEFAULT_VOLUME =
                1.0F;


        public static final float DEFAULT_PITCH =
                1.0F;


        public static TransitionSoundConfig none() {

            return new TransitionSoundConfig(
                    new Identifier[0],
                    TransitionSoundTrigger.CHANGE,
                    DEFAULT_VOLUME,
                    DEFAULT_PITCH,
                    DEFAULT_PITCH
            );
        }


        public boolean enabled() {

            return sounds != null
                    && sounds.length > 0;
        }


        public boolean randomPitch() {

            return pitchMin != pitchMax;
        }
    }


    /*
     * ================================================================
     * TRANSITION EFFECT CONFIG
     * ================================================================
     */
    public record TransitionEffectConfig(
            TransitionEffect effect,
            TransitionEffectTrigger trigger,
            Identifier texture,
            int frames,
            int duration,
            int count,
            float size,

            /*
             * Symmetric radius.
             *
             * Kept so existing:
             *
             *     effect.radius=0.2
             *
             * and:
             *
             *     effect.radius=0.1,0.5,0.1
             *
             * continue working.
             */
            float radiusX,
            float radiusY,
            float radiusZ,

            /*
             * Optional directional radius bounds.
             *
             * When directionalRadius is false, the renderer uses the
             * symmetric radius values above.
             *
             * When true, the renderer uses these explicit min/max
             * extents instead.
             */
            boolean directionalRadius,

            float radiusMinX,
            float radiusMinY,
            float radiusMinZ,

            float radiusMaxX,
            float radiusMaxY,
            float radiusMaxZ,

            float spread,
            float stagger,

            float originX,
            float originY,
            float originZ
    ) {

        public static final Identifier DEFAULT_TEXTURE =
                Identifier.fromNamespaceAndPath(
                        "usus_item_model_delay",
                        "textures/particle/poof.png"
                );


        public static final int DEFAULT_FRAMES =
                1;


        public static final int DEFAULT_DURATION =
                8;


        public static final int DEFAULT_COUNT =
                10;


        public static final float DEFAULT_SIZE =
                1.0F;


        public static final float DEFAULT_RADIUS =
                0.0F;


        public static final float DEFAULT_SPREAD =
                1.0F;


        public static final float DEFAULT_STAGGER =
                0.0F;


        public static TransitionEffectConfig none() {

            return new TransitionEffectConfig(
                    TransitionEffect.NONE,
                    TransitionEffectTrigger.CHANGE,
                    DEFAULT_TEXTURE,
                    DEFAULT_FRAMES,
                    DEFAULT_DURATION,
                    DEFAULT_COUNT,
                    DEFAULT_SIZE,

                    DEFAULT_RADIUS,
                    DEFAULT_RADIUS,
                    DEFAULT_RADIUS,

                    false,

                    -DEFAULT_RADIUS,
                    -DEFAULT_RADIUS,
                    -DEFAULT_RADIUS,

                    DEFAULT_RADIUS,
                    DEFAULT_RADIUS,
                    DEFAULT_RADIUS,

                    DEFAULT_SPREAD,
                    DEFAULT_STAGGER,

                    0.0F,
                    0.0F,
                    0.0F
            );
        }


        public boolean enabled() {

            return effect
                    != TransitionEffect.NONE;
        }


        public boolean hasUniformRadius() {

            return radiusX == radiusY
                    && radiusY == radiusZ;
        }
    }


    /*
     * ================================================================
     * CONDITIONAL CONFIG
     * ================================================================
     */
    public record DelayConfig(
            int delay,
            Mode mode,
            PropertyBehavior behavior,
            TransitionEffectConfig effect,
            TransitionSoundConfig sound
    ) {

        public DelayConfig(
                int delay,
                Mode mode,
                PropertyBehavior behavior,
                TransitionEffectConfig effect
        ) {

            this(
                    delay,
                    mode,
                    behavior,
                    effect,
                    TransitionSoundConfig.none()
            );
        }


        public DelayConfig(
                int delay,
                Mode mode,
                PropertyBehavior behavior
        ) {

            this(
                    delay,
                    mode,
                    behavior,
                    TransitionEffectConfig.none(),
                    TransitionSoundConfig.none()
            );
        }


        public DelayConfig(
                int delay,
                Mode mode
        ) {

            this(
                    delay,
                    mode,
                    PropertyBehavior.NORMAL,
                    TransitionEffectConfig.none(),
                    TransitionSoundConfig.none()
            );
        }
    }


    /*
     * ================================================================
     * RANGE CONFIG
     * ================================================================
     */
    public record RangeDelayConfig(
            int delay,
            RangeBehavior behavior,
            TransitionEffectConfig effect,
            TransitionSoundConfig sound
    ) {

        public RangeDelayConfig(
                int delay,
                RangeBehavior behavior,
                TransitionEffectConfig effect
        ) {

            this(
                    delay,
                    behavior,
                    effect,
                    TransitionSoundConfig.none()
            );
        }


        public RangeDelayConfig(
                int delay,
                RangeBehavior behavior
        ) {

            this(
                    delay,
                    behavior,
                    TransitionEffectConfig.none(),
                    TransitionSoundConfig.none()
            );
        }


        public RangeDelayConfig(
                int delay
        ) {

            this(
                    delay,
                    RangeBehavior.THRESHOLD,
                    TransitionEffectConfig.none(),
                    TransitionSoundConfig.none()
            );
        }
    }


    /*
     * ================================================================
     * SELECT CONFIG
     * ================================================================
     */
    public record SelectDelayConfig(
            int delay,
            TransitionEffectConfig effect,
            TransitionSoundConfig sound
    ) {

        public SelectDelayConfig(
                int delay,
                TransitionEffectConfig effect
        ) {

            this(
                    delay,
                    effect,
                    TransitionSoundConfig.none()
            );
        }


        public SelectDelayConfig(
                int delay
        ) {

            this(
                    delay,
                    TransitionEffectConfig.none(),
                    TransitionSoundConfig.none()
            );
        }
    }


    /*
     * ================================================================
     * ITEM CONFIG
     * ================================================================
     */
    public record ItemConfig(
            Behavior behavior,
            Map<String, DelayConfig> properties,
            Map<String, RangeDelayConfig> rangeProperties,
            Map<String, SelectDelayConfig> selectProperties
    ) {
    }


    /*
     * ================================================================
     * CLEAR
     * ================================================================
     */
    public static void clear() {

        CONFIGS.clear();
    }


    /*
     * ================================================================
     * REGISTER ITEM
     * ================================================================
     */
    public static void registerItem(
            Identifier itemId,
            Behavior behavior,
            Map<String, DelayConfig> properties,
            Map<String, RangeDelayConfig> rangeProperties,
            Map<String, SelectDelayConfig> selectProperties
    ) {

        CONFIGS.put(
                itemId,
                new ItemConfig(
                        behavior,
                        new HashMap<>(properties),
                        new HashMap<>(rangeProperties),
                        new HashMap<>(selectProperties)
                )
        );
    }


    public static void registerItem(
            Identifier itemId,
            Behavior behavior,
            Map<String, DelayConfig> properties,
            Map<String, RangeDelayConfig> rangeProperties
    ) {

        registerItem(
                itemId,
                behavior,
                properties,
                rangeProperties,
                Map.of()
        );
    }


    public static void registerItem(
            Identifier itemId,
            Behavior behavior,
            Map<String, DelayConfig> properties
    ) {

        registerItem(
                itemId,
                behavior,
                properties,
                Map.of(),
                Map.of()
        );
    }


    public static void registerItem(
            Identifier itemId,
            Map<String, DelayConfig> properties
    ) {

        registerItem(
                itemId,
                Behavior.NORMAL,
                properties,
                Map.of(),
                Map.of()
        );
    }


    /*
     * ================================================================
     * ITEM CONFIG LOOKUP
     * ================================================================
     *
     * Used by the transition-effect renderer to discover every delayed
     * property belonging to an item so one stable model-space anchor can
     * be learned per property and per display context.
     */
    public static ItemConfig getItemConfig(
            Identifier itemId
    ) {

        return CONFIGS.get(
                itemId
        );
    }


    /*
     * ================================================================
     * CONDITIONAL PROPERTY CONFIG
     * ================================================================
     */
    public static DelayConfig get(
            Identifier itemId,
            String propertyName
    ) {

        ItemConfig itemConfig =
                CONFIGS.get(
                        itemId
                );


        if (
                itemConfig == null
        ) {

            return null;
        }


        return itemConfig
                .properties()
                .get(
                        propertyName
                );
    }


    /*
     * ================================================================
     * RANGE PROPERTY CONFIG
     * ================================================================
     */
    public static RangeDelayConfig getRange(
            Identifier itemId,
            String propertyName
    ) {

        ItemConfig itemConfig =
                CONFIGS.get(
                        itemId
                );


        if (
                itemConfig == null
        ) {

            return null;
        }


        return itemConfig
                .rangeProperties()
                .get(
                        propertyName
                );
    }


    /*
     * ================================================================
     * SELECT PROPERTY CONFIG
     * ================================================================
     */
    public static SelectDelayConfig getSelect(
            Identifier itemId,
            String propertyName
    ) {

        ItemConfig itemConfig =
                CONFIGS.get(
                        itemId
                );


        if (
                itemConfig == null
        ) {

            return null;
        }


        return itemConfig
                .selectProperties()
                .get(
                        propertyName
                );
    }


    /*
     * ================================================================
     * ITEM BEHAVIOR
     * ================================================================
     */
    public static Behavior getBehavior(
            Identifier itemId
    ) {

        ItemConfig itemConfig =
                CONFIGS.get(
                        itemId
                );


        if (
                itemConfig == null
        ) {

            return Behavior.NORMAL;
        }


        return itemConfig.behavior();
    }


    public static boolean isEvolving(
            Identifier itemId
    ) {

        return getBehavior(
                itemId
        ) == Behavior.EVOLVING;
    }
}