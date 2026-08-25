package com.usujiotarako.client;

import net.minecraft.client.renderer.item.properties.select.Charge;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.minecraft.client.renderer.item.properties.select.ContextDimension;
import net.minecraft.client.renderer.item.properties.select.ContextEntityType;
import net.minecraft.client.renderer.item.properties.select.ItemBlockState;
import net.minecraft.client.renderer.item.properties.select.LocalTime;
import net.minecraft.client.renderer.item.properties.select.MainHand;
import net.minecraft.client.renderer.item.properties.select.TrimMaterialProperty;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicLong;

public final class ModelDelaySelectProperties {

    /*
     * ================================================================
     * SELECT PROPERTY INFO
     * ================================================================
     *
     * configKey:
     *
     *     User-facing key written in .mdprop.
     *
     *
     * stateKey:
     *
     *     Exact internal identity used by ModelDelaySelectState.
     *
     * Parameterized/stateful properties can share one user-facing config
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
     *
     * LocalTime contains private configuration/state:
     *
     *     pattern
     *     locale
     *     time_zone
     *
     * Each baked LocalTime node therefore receives its own runtime ID.
     */
    private static final AtomicLong NEXT_LOCAL_TIME_NODE_ID =
            new AtomicLong();


    private ModelDelaySelectProperties() {
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
         * MAIN HAND
         * ============================================================
         *
         * .mdprop:
         *
         *     select.main_hand.delay=20
         */
        if (
                property
                        instanceof MainHand
        ) {

            String key =
                    "select.main_hand";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * CHARGE TYPE
         * ============================================================
         *
         * Vanilla values:
         *
         *     none
         *     arrow
         *     rocket
         *
         *
         * .mdprop:
         *
         *     select.charge_type.delay=20
         */
        if (
                property
                        instanceof Charge
        ) {

            String key =
                    "select.charge_type";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * TRIM MATERIAL
         * ============================================================
         *
         * .mdprop:
         *
         *     select.trim_material.delay=20
         */
        if (
                property
                        instanceof TrimMaterialProperty
        ) {

            String key =
                    "select.trim_material";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * BLOCK STATE
         * ============================================================
         *
         * The selected block-state property is part of the key.
         *
         * Examples:
         *
         *     select.block_state[axis].delay=20
         *     select.block_state[facing].delay=20
         */
        if (
                property
                        instanceof ItemBlockState blockState
        ) {

            String key =
                    "select.block_state["
                            + blockState.property()
                            + "]";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * LOCAL TIME
         * ============================================================
         *
         * .mdprop:
         *
         *     select.local_time.delay=20
         *
         * Each baked LocalTime property gets an independent runtime
         * state because its pattern/locale/time-zone configuration is
         * private.
         */
        if (
                property
                        instanceof LocalTime
        ) {

            String configKey =
                    "select.local_time";


            long nodeId =
                    NEXT_LOCAL_TIME_NODE_ID.getAndIncrement();


            String stateKey =
                    "select.local_time"
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
         * CONTEXT ENTITY TYPE
         * ============================================================
         *
         * Vanilla:
         *
         *     owner != null
         *         -> owner's entity type
         *
         *     owner == null
         *         -> null
         *
         *
         * .mdprop:
         *
         *     select.context_entity_type.delay=20
         */
        if (
                property
                        instanceof ContextEntityType
        ) {

            String key =
                    "select.context_entity_type";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * CONTEXT DIMENSION
         * ============================================================
         *
         * Vanilla:
         *
         *     level != null
         *         -> level.dimension()
         *
         *     level == null
         *         -> null
         *
         *
         * .mdprop:
         *
         *     select.context_dimension.delay=20
         */
        if (
                property
                        instanceof ContextDimension
        ) {

            String key =
                    "select.context_dimension";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * COMPONENT CONTENTS
         * ============================================================
         *
         * Vanilla:
         *
         *     itemStack.get(componentType)
         *
         *
         * The selected component type determines both:
         *
         *     the value
         *     the value codec
         *
         *
         * ComponentContents exposes its DataComponentType directly.
         *
         *
         * ------------------------------------------------------------
         * USER-FACING CONFIG KEY
         * ------------------------------------------------------------
         *
         * Vanilla minecraft components use only their path:
         *
         *     minecraft:damage
         *
         * becomes:
         *
         *     select.component[damage]
         *
         *
         * Therefore the .mdprop syntax is:
         *
         *     select.component[damage].delay=20
         *
         *
         * This keeps vanilla component configuration consistent with
         * the rest of Model Delay Helper's user-facing property names
         * and avoids requiring a ':' inside a Java Properties key.
         *
         *
         * Non-minecraft components retain their complete namespaced ID:
         *
         *     example_mod:special_state
         *
         * becomes:
         *
         *     select.component[example_mod:special_state]
         *
         *
         * ------------------------------------------------------------
         * INTERNAL STATE KEY
         * ------------------------------------------------------------
         *
         * Runtime state always retains the complete component ID:
         *
         *     select.component[minecraft:damage]
         *
         * This prevents component identities from becoming ambiguous
         * internally.
         */
        if (
                property
                        instanceof ComponentContents<?> componentContents
        ) {

            DataComponentType<?> componentType =
                    componentContents.componentType();


            Identifier componentId =
                    BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(
                            componentType
                    );


            /*
             * --------------------------------------------------------
             * CONFIG COMPONENT NAME
             * --------------------------------------------------------
             *
             * Vanilla namespace:
             *
             *     minecraft:damage
             *         ->
             *     damage
             *
             *
             * Other namespaces:
             *
             *     example_mod:special_state
             *         ->
             *     example_mod:special_state
             */
            String configComponentName;


            if (
                    componentId.getNamespace()
                            .equals(
                                    "minecraft"
                            )
            ) {

                configComponentName =
                        componentId.getPath();

            } else {

                configComponentName =
                        componentId.toString();
            }


            /*
             * User-facing .mdprop key.
             */
            String configKey =
                    "select.component["
                            + configComponentName
                            + "]";


            /*
             * Fully-qualified internal state identity.
             */
            String stateKey =
                    "select.component["
                            + componentId
                            + "]";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        /*
         * ============================================================
         * DISPLAY CONTEXT
         * ============================================================
         *
         * Deliberately unsupported.
         *
         * A single physical stack can simultaneously be rendered in
         * GUI, hand, ground, fixed, etc. Those values are simultaneous
         * rendering contexts rather than one state changing over time.
         */


        /*
         * Unsupported select property.
         */
        return null;
    }
}