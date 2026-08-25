package com.usujiotarako.client;

import net.minecraft.client.renderer.item.properties.numeric.BundleFullness;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngle;
import net.minecraft.client.renderer.item.properties.numeric.Cooldown;
import net.minecraft.client.renderer.item.properties.numeric.Count;
import net.minecraft.client.renderer.item.properties.numeric.CrossbowPull;
import net.minecraft.client.renderer.item.properties.numeric.CustomModelDataProperty;
import net.minecraft.client.renderer.item.properties.numeric.Damage;
import net.minecraft.client.renderer.item.properties.numeric.Time;
import net.minecraft.client.renderer.item.properties.numeric.UseCycle;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;

import java.util.concurrent.atomic.AtomicLong;

public final class ModelDelayRangeProperties {

    /*
     * ================================================================
     * RANGE PROPERTY INFO
     * ================================================================
     *
     * configKey:
     *
     *     User-facing key written in .mdprop.
     *
     *
     * stateKey:
     *
     *     Exact internal identity used by ModelDelayRangeState.
     *
     * Parameterized/stateful properties can share a user-facing config
     * while retaining independent runtime state.
     */
    public record PropertyInfo(
            String configKey,
            String stateKey
    ) {
    }


    /*
     * ================================================================
     * STATEFUL NODE IDS
     * ================================================================
     */
    private static final AtomicLong NEXT_TIME_NODE_ID =
            new AtomicLong();


    private static final AtomicLong NEXT_COMPASS_NODE_ID =
            new AtomicLong();


    private ModelDelayRangeProperties() {
    }


    /*
     * ================================================================
     * PROPERTY LOOKUP
     * ================================================================
     */
    public static PropertyInfo getInfo(
            Object property
    ) {

        /*
         * ============================================================
         * CUSTOM MODEL DATA
         * ============================================================
         */
        if (
                property
                        instanceof CustomModelDataProperty customModelData
        ) {

            String key =
                    "range.custom_model_data["
                            + customModelData.index()
                            + "]";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * BUNDLE FULLNESS
         * ============================================================
         */
        if (
                property
                        instanceof BundleFullness
        ) {

            String key =
                    "range.bundle/fullness";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * DAMAGE
         * ============================================================
         */
        if (
                property
                        instanceof Damage damage
        ) {

            String configKey =
                    "range.damage";


            String stateKey =
                    "range.damage"
                            + "{normalize="
                            + damage.normalize()
                            + "}";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        /*
         * ============================================================
         * COOLDOWN
         * ============================================================
         */
        if (
                property
                        instanceof Cooldown
        ) {

            String key =
                    "range.cooldown";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * TIME
         * ============================================================
         */
        if (
                property
                        instanceof Time
        ) {

            String configKey =
                    "range.time";


            long nodeId =
                    NEXT_TIME_NODE_ID.getAndIncrement();


            String stateKey =
                    "range.time"
                            + "{node="
                            + nodeId
                            + "}";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        /*
         * ============================================================
         * COMPASS
         * ============================================================
         */
        if (
                property
                        instanceof CompassAngle
        ) {

            String configKey =
                    "range.compass";


            long nodeId =
                    NEXT_COMPASS_NODE_ID.getAndIncrement();


            String stateKey =
                    "range.compass"
                            + "{node="
                            + nodeId
                            + "}";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        /*
         * ============================================================
         * CROSSBOW PULL
         * ============================================================
         */
        if (
                property
                        instanceof CrossbowPull
        ) {

            String key =
                    "range.crossbow/pull";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * USE DURATION
         * ============================================================
         */
        if (
                property
                        instanceof UseDuration useDuration
        ) {

            String configKey =
                    "range.use_duration";


            String stateKey =
                    "range.use_duration"
                            + "{remaining="
                            + useDuration.remaining()
                            + "}";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        /*
         * ============================================================
         * USE CYCLE
         * ============================================================
         */
        if (
                property
                        instanceof UseCycle useCycle
        ) {

            String configKey =
                    "range.use_cycle";


            String stateKey =
                    "range.use_cycle"
                            + "{period="
                            + useCycle.period()
                            + "}";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        /*
         * ============================================================
         * COUNT
         * ============================================================
         *
         * Vanilla:
         *
         *     normalize=true
         *
         *         count / max stack size
         *
         *         usually:
         *
         *             0.0 -> 1.0
         *
         *
         *     normalize=false
         *
         *         raw stack count
         *
         *         usually:
         *
         *             1 -> max stack size
         *
         *
         * .mdprop:
         *
         *     range.count.delay=40
         *
         *
         * normalize is part of the internal state key so normalized and
         * raw range nodes cannot interfere with each other.
         */
        if (
                property
                        instanceof Count count
        ) {

            String configKey =
                    "range.count";


            String stateKey =
                    "range.count"
                            + "{normalize="
                            + count.normalize()
                            + "}";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        /*
         * Unsupported numeric property.
         */
        return null;
    }
}