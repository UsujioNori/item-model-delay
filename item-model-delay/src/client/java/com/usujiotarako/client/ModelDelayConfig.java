package com.usujiotarako.client;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class ModelDelayConfig {

    private static final Map<
            Identifier,
            ItemConfig
            > CONFIGS = new HashMap<>();


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
     * CONDITIONAL CONFIG
     * ================================================================
     */
    public record DelayConfig(
            int delay,
            Mode mode,
            PropertyBehavior behavior
    ) {

        /*
         * Compatibility constructor.
         */
        public DelayConfig(
                int delay,
                Mode mode
        ) {

            this(
                    delay,
                    mode,
                    PropertyBehavior.NORMAL
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
            RangeBehavior behavior
    ) {

        /*
         * Threshold is the default range behavior.
         */
        public RangeDelayConfig(
                int delay
        ) {

            this(
                    delay,
                    RangeBehavior.THRESHOLD
            );
        }
    }


    /*
     * ================================================================
     * SELECT CONFIG
     * ================================================================
     *
     * Select properties do not have HOLD / RELEASE directions.
     *
     * A change:
     *
     *     old select value
     *         ->
     *     new select value
     *
     * simply keeps the old value visible for the configured number of
     * ticks before allowing the new value through.
     */
    public record SelectDelayConfig(
            int delay
    ) {
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


    /*
     * Compatibility overload.
     */
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


    /*
     * Compatibility overload.
     */
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


    /*
     * Compatibility overload.
     */
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


        if (itemConfig == null) {

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


        if (itemConfig == null) {

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


        if (itemConfig == null) {

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


        if (itemConfig == null) {

            return Behavior.NORMAL;
        }


        return itemConfig.behavior();
    }


    public static boolean isEvolving(
            Identifier itemId
    ) {

        return getBehavior(itemId)
                == Behavior.EVOLVING;
    }
}