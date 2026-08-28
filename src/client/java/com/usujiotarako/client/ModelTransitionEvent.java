package com.usujiotarako.client;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public final class ModelTransitionEvent {

    /*
     * ================================================================
     * TRANSITION TYPE
     * ================================================================
     */
    public enum Type {
        CHANGE,
        DELAYED
    }


    /*
     * ================================================================
     * EVENT DATA
     * ================================================================
     *
     * previousValue / newValue are intentionally Object rather than
     * boolean so the same event can represent:
     *
     *     conditional -> Boolean
     *     range/value -> Float
     *     range/threshold -> Integer range index
     *     select -> the selected value object
     */
    public record Event(
            Identifier item,
            String property,
            int ownerId,
            long stackIdentity,
            Object previousValue,
            Object newValue,
            Type type,
            ModelDelayConfig.TransitionSoundConfig soundConfig
    ) {
    }


    private ModelTransitionEvent() {
    }


    /*
     * ================================================================
     * CONDITIONAL COMPATIBILITY
     * ================================================================
     *
     * Existing conditional-state calls do not need to change.
     *
     * Their sound config is resolved here and attached to the event.
     */
    public static void fire(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity,
            boolean previousValue,
            boolean newValue,
            Type type
    ) {

        ModelDelayConfig.DelayConfig config =
                ModelDelayConfig.get(
                        itemId,
                        property
                );


        ModelDelayConfig.TransitionSoundConfig soundConfig =
                config != null
                        ? config.sound()
                        : ModelDelayConfig
                        .TransitionSoundConfig
                        .none();


        fire(
                itemId,
                property,
                ownerId,
                stackIdentity,
                previousValue,
                newValue,
                type,
                soundConfig
        );
    }


    /*
     * ================================================================
     * GENERIC FIRE
     * ================================================================
     *
     * Range/select state passes its already-resolved sound config here.
     */
    public static void fire(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity,
            Object previousValue,
            Object newValue,
            Type type,
            ModelDelayConfig.TransitionSoundConfig soundConfig
    ) {

        if (
                Objects.equals(
                        previousValue,
                        newValue
                )
        ) {

            return;
        }


        Event event =
                new Event(
                        itemId,
                        property,
                        ownerId,
                        stackIdentity,
                        previousValue,
                        newValue,
                        type,
                        soundConfig
                );


        ModelTransitionSounds.onTransition(
                event
        );
    }


    /*
     * ================================================================
     * CONDITIONAL HELPERS
     * ================================================================
     */
    public static void fireDelayed(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity,
            boolean previousValue,
            boolean newValue
    ) {

        fire(
                itemId,
                property,
                ownerId,
                stackIdentity,
                previousValue,
                newValue,
                Type.DELAYED
        );
    }


    public static void fireChange(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity,
            boolean previousValue,
            boolean newValue
    ) {

        fire(
                itemId,
                property,
                ownerId,
                stackIdentity,
                previousValue,
                newValue,
                Type.CHANGE
        );
    }


    /*
     * ================================================================
     * RANGE / SELECT HELPERS
     * ================================================================
     */
    public static void fireDelayed(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity,
            Object previousValue,
            Object newValue,
            ModelDelayConfig.TransitionSoundConfig soundConfig
    ) {

        fire(
                itemId,
                property,
                ownerId,
                stackIdentity,
                previousValue,
                newValue,
                Type.DELAYED,
                soundConfig
        );
    }


    public static void fireChange(
            Identifier itemId,
            String property,
            int ownerId,
            long stackIdentity,
            Object previousValue,
            Object newValue,
            ModelDelayConfig.TransitionSoundConfig soundConfig
    ) {

        fire(
                itemId,
                property,
                ownerId,
                stackIdentity,
                previousValue,
                newValue,
                Type.CHANGE,
                soundConfig
        );
    }
}
