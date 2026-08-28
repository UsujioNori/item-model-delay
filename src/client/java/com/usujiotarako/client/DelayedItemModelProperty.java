package com.usujiotarako.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ItemModelPropertyTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Wraps a vanilla boolean item-model property with IMD's delayed state
 * system.
 *
 * Vanilla remains the source of truth.
 */
public final class DelayedItemModelProperty
        implements ItemModelPropertyTest {

    private final ItemModelPropertyTest original;

    private final String propertyName;

    private final String keybindTranslationKey;


    public DelayedItemModelProperty(
            ItemModelPropertyTest original,
            String propertyName,
            String keybindTranslationKey
    ) {

        this.original =
                original;


        this.propertyName =
                propertyName;


        this.keybindTranslationKey =
                keybindTranslationKey;
    }


    @Override
    public boolean get(
            ItemStack itemStack,
            @Nullable ClientLevel level,
            @Nullable LivingEntity owner,
            int seed,
            ItemDisplayContext displayContext
    ) {

        Identifier itemId =
                BuiltInRegistries.ITEM.getKey(
                        itemStack.getItem()
                );


        ModelDelayConfig.DelayConfig config =
                ModelDelayConfig.get(
                        itemId,
                        propertyName
                );


        /*
         * No IMD configuration:
         *
         * preserve vanilla exactly.
         */
        if (config == null) {

            return original.get(
                    itemStack,
                    level,
                    owner,
                    seed,
                    displayContext
            );
        }


        /*
         * Always evaluate the real vanilla property first.
         */
        boolean vanillaValue =
                original.get(
                        itemStack,
                        level,
                        owner,
                        seed,
                        displayContext
                );


        /*
         * Let the existing IMD state system decide which value should
         * actually be exposed to the item model.
         */
        boolean delayedValue =
                ModelDelayState.get(
                        itemStack,
                        owner,
                        propertyName,
                        vanillaValue,
                        config,
                        seed,
                        displayContext
                );


        /*
         * Transition sounds and particle effects are dispatched by
         * ModelDelayState at the exact moment the exposed delayed value
         * changes.
         *
         * Do not observe the returned value a second time here. The old
         * observer was global to item + property, so simultaneous GUI,
         * ground, inventory, and hand renders could look like unrelated
         * value changes and create duplicate or stray effects.
         */
        return delayedValue;
    }
}