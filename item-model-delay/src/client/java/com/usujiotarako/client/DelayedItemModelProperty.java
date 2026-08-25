package com.usujiotarako.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.conditional.ItemModelPropertyTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Wraps a vanilla boolean item-model property with the shared delayed-state
 * system.
 *
 * <p>configKey identifies the entry used in the .mdprop file.
 *
 * <p>stateKey identifies the exact condition instance used internally by
 * ModelDelayState.
 *
 * <p>For ordinary properties those values are identical. Parameterized
 * properties such as has_component can use a simple user-facing config key
 * while retaining a more specific internal state identity.
 */
public final class DelayedItemModelProperty
        implements ItemModelPropertyTest {

    private static final String USING_ITEM_CLASS =
            "net.minecraft.client.renderer.item.properties.conditional.IsUsingItem";

    private static final boolean PUNCHY_LOADED =
            FabricLoader.getInstance().isModLoaded("punchy");


    private final ItemModelPropertyTest original;

    private final String configKey;

    private final String stateKey;


    public DelayedItemModelProperty(
            ItemModelPropertyTest original,
            String configKey,
            String stateKey
    ) {
        this.original = original;
        this.configKey = configKey;
        this.stateKey = stateKey;
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


        /*
         * Look up the user-facing configuration key.
         *
         * Example:
         *
         *     has_component[custom_name]
         */
        ModelDelayConfig.DelayConfig config =
                ModelDelayConfig.get(
                        itemId,
                        configKey
                );


        /*
         * No delay configured:
         *
         * preserve the wrapped property's normal behaviour completely.
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
         * Ask the actual runtime property for its normal result first.
         */
        boolean propertyValue =
                original.get(
                        itemStack,
                        level,
                        owner,
                        seed,
                        displayContext
                );


        /*
         * Value supplied to ModelDelayState.
         */
        boolean delayInput =
                propertyValue;


        /*
         * ============================================================
         * PUNCHY / USING_ITEM COMPATIBILITY
         * ============================================================
         */
        if (
                PUNCHY_LOADED
                        && "using_item".equals(configKey)
                        && USING_ITEM_CLASS.equals(
                        original.getClass().getName()
                )
                        && owner != null
        ) {

            boolean gameplayUsingItem =
                    owner.isUsingItem()
                            && owner.getUseItem() == itemStack;


            if (gameplayUsingItem && !propertyValue) {

                delayInput = true;
            }
        }


        /*
         * ============================================================
         * SELECTED COMPATIBILITY
         * ============================================================
         *
         * Vanilla IsSelected uses exact ItemStack object identity.
         *
         * For hand rendering, normalize equivalent render-time copies of
         * the selected stack so temporary false values cannot repeatedly
         * cancel HOLD/BOTH.
         */
        if (
                "selected".equals(configKey)
                        && !propertyValue
                        && owner instanceof LocalPlayer player
                        && isHandContext(displayContext)
        ) {

            ItemStack selectedStack =
                    player.getInventory().getSelectedItem();


            if (
                    !selectedStack.isEmpty()
                            && ItemStack.isSameItemSameComponents(
                            selectedStack,
                            itemStack
                    )
            ) {

                delayInput = true;
            }
        }


        /*
         * stateKey, rather than configKey, is passed to ModelDelayState.
         *
         * This is what allows two parameterized conditions to use separate
         * timers even though they belong to the same general property type.
         */
        return ModelDelayState.get(
                itemStack,
                owner,
                stateKey,
                delayInput,
                config,
                seed,
                displayContext
        );
    }


    private static boolean isHandContext(
            ItemDisplayContext displayContext
    ) {

        return displayContext
                == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND

                || displayContext
                == ItemDisplayContext.FIRST_PERSON_LEFT_HAND

                || displayContext
                == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND

                || displayContext
                == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }
}