package com.usujiotarako.client;

import net.minecraft.client.renderer.item.properties.conditional.Broken;
import net.minecraft.client.renderer.item.properties.conditional.BundleHasSelectedItem;
import net.minecraft.client.renderer.item.properties.conditional.ComponentMatches;
import net.minecraft.client.renderer.item.properties.conditional.CustomModelDataProperty;
import net.minecraft.client.renderer.item.properties.conditional.Damaged;
import net.minecraft.client.renderer.item.properties.conditional.ExtendedView;
import net.minecraft.client.renderer.item.properties.conditional.FishingRodCast;
import net.minecraft.client.renderer.item.properties.conditional.HasComponent;
import net.minecraft.client.renderer.item.properties.conditional.IsCarried;
import net.minecraft.client.renderer.item.properties.conditional.IsKeybindDown;
import net.minecraft.client.renderer.item.properties.conditional.IsSelected;
import net.minecraft.client.renderer.item.properties.conditional.IsUsingItem;
import net.minecraft.client.renderer.item.properties.conditional.IsViewEntity;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicLong;

public final class ModelDelayProperties {

    /*
     * ================================================================
     * PROPERTY INFO
     * ================================================================
     *
     * configKey:
     *
     *     What is written in the .mdprop file.
     *
     *
     * stateKey:
     *
     *     Exact internal condition identity used by ModelDelayState.
     *
     *
     * Simple properties use the same value for both.
     *
     * Parameterized properties can share a user-facing configuration
     * family while maintaining independent runtime state.
     */
    public record PropertyInfo(
            String configKey,
            String stateKey
    ) {
    }


    /*
     * ================================================================
     * COMPONENT NODE IDS
     * ================================================================
     *
     * Each baked minecraft:component condition receives its own node ID.
     *
     * This allows two predicates of the same type, such as:
     *
     *     damage >= 10
     *     damage >= 20
     *
     * to both use:
     *
     *     component[damage]
     *
     * while maintaining independent timers.
     */
    private static final AtomicLong NEXT_COMPONENT_NODE_ID =
            new AtomicLong();


    private ModelDelayProperties() {
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
         * ------------------------------------------------------------
         * SIMPLE BOOLEAN PROPERTIES
         * ------------------------------------------------------------
         */

        if (property instanceof IsKeybindDown) {

            return simple(
                    "keybind_down"
            );
        }


        if (property instanceof IsUsingItem) {

            return simple(
                    "using_item"
            );
        }


        if (property instanceof IsSelected) {

            return simple(
                    "selected"
            );
        }


        if (property instanceof IsCarried) {

            return simple(
                    "carried"
            );
        }


        if (property instanceof Broken) {

            return simple(
                    "broken"
            );
        }


        if (property instanceof Damaged) {

            return simple(
                    "damaged"
            );
        }


        if (property instanceof FishingRodCast) {

            return simple(
                    "fishing_rod/cast"
            );
        }


        if (property instanceof ExtendedView) {

            return simple(
                    "extended_view"
            );
        }


        if (property instanceof IsViewEntity) {

            return simple(
                    "view_entity"
            );
        }


        if (property instanceof BundleHasSelectedItem) {

            return simple(
                    "bundle/has_selected_item"
            );
        }


        /*
         * ============================================================
         * CUSTOM MODEL DATA — BOOLEAN
         * ============================================================
         *
         * This is specifically:
         *
         * net.minecraft.client.renderer.item.properties.conditional
         *     .CustomModelDataProperty
         *
         *
         * Vanilla behavior:
         *
         *     customModelData.getBoolean(index)
         *
         *
         * Item-model JSON:
         *
         *     "property": "minecraft:custom_model_data",
         *     "index": 0
         *
         *
         * .mdprop:
         *
         *     custom_model_data[0].delay=40
         *     custom_model_data[0].mode=hold
         *
         *
         * Another boolean index can have completely different settings:
         *
         *     custom_model_data[1].delay=20
         *     custom_model_data[1].mode=both
         *
         *
         * The index is part of both the config key and state key.
         */
        if (
                property
                        instanceof CustomModelDataProperty customModelData
        ) {

            String key =
                    "custom_model_data["
                            + customModelData.index()
                            + "]";


            return new PropertyInfo(
                    key,
                    key
            );
        }


        /*
         * ============================================================
         * HAS_COMPONENT
         * ============================================================
         *
         * JSON:
         *
         *     "property": "minecraft:has_component",
         *     "component": "minecraft:custom_name"
         *
         *
         * .mdprop:
         *
         *     has_component[custom_name].delay=40
         *     has_component[custom_name].mode=hold
         */
        if (property instanceof HasComponent hasComponent) {

            Identifier componentId =
                    BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(
                            hasComponent.componentType()
                    );


            if (componentId == null) {

                return null;
            }


            String configComponentName =
                    shortIdentifier(
                            componentId
                    );


            String configKey =
                    "has_component["
                            + configComponentName
                            + "]";


            /*
             * ignore_default remains part of the exact runtime identity.
             */
            String stateKey =
                    "has_component["
                            + componentId
                            + "]"
                            + "{ignore_default="
                            + hasComponent.ignoreDefault()
                            + "}";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        /*
         * ============================================================
         * COMPONENT
         * ============================================================
         *
         * Generic minecraft:component support.
         *
         * Examples:
         *
         *     component[damage]
         *     component[enchantments]
         *     component[stored_enchantments]
         *     component[potion_contents]
         *     component[custom_data]
         *     component[container]
         *     component[bundle_contents]
         *     component[firework_explosion]
         *     component[fireworks]
         *     component[writable_book_content]
         *     component[written_book_content]
         *     component[attribute_modifiers]
         *     component[trim]
         *     component[jukebox_playable]
         *     component[villager/variant]
         *
         *
         * Each baked condition gets a unique internal node ID.
         */
        if (property instanceof ComponentMatches componentMatches) {

            DataComponentPredicate.Single<?> single =
                    componentMatches.predicate();


            Identifier predicateTypeId =
                    getPredicateTypeId(
                            single
                    );


            if (predicateTypeId == null) {

                return null;
            }


            String configPredicateName =
                    shortIdentifier(
                            predicateTypeId
                    );


            String configKey =
                    "component["
                            + configPredicateName
                            + "]";


            long nodeId =
                    NEXT_COMPONENT_NODE_ID.getAndIncrement();


            String stateKey =
                    "component["
                            + predicateTypeId
                            + "]"
                            + "{node="
                            + nodeId
                            + "}";


            return new PropertyInfo(
                    configKey,
                    stateKey
            );
        }


        return null;
    }


    /*
     * ================================================================
     * COMPONENT PREDICATE TYPE ID
     * ================================================================
     */
    private static Identifier getPredicateTypeId(
            DataComponentPredicate.Single<?> single
    ) {

        DataComponentPredicate.Type<?> type =
                single.type();


        /*
         * Raw data-component predicate.
         */
        if (
                type
                        instanceof DataComponentPredicate.AnyValueType anyValueType
        ) {

            return BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(
                    anyValueType.componentType()
            );
        }


        /*
         * Registered specialized component predicate.
         */
        return BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE.getKey(
                type
        );
    }


    /*
     * ================================================================
     * SIMPLE PROPERTY
     * ================================================================
     */
    private static PropertyInfo simple(
            String name
    ) {

        return new PropertyInfo(
                name,
                name
        );
    }


    /*
     * ================================================================
     * USER-FACING IDENTIFIER
     * ================================================================
     *
     * minecraft:damage
     *     -> damage
     *
     * minecraft:villager/variant
     *     -> villager/variant
     *
     * examplemod:predicate
     *     -> examplemod:predicate
     */
    private static String shortIdentifier(
            Identifier id
    ) {

        if (
                "minecraft".equals(
                        id.getNamespace()
                )
        ) {

            return id.getPath();
        }


        return id.toString();
    }
}