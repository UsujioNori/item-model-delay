package com.usujiotarako.client;

import com.mojang.serialization.Codec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class DelayedSelectItemModelProperty<T>
        implements SelectItemModelProperty<T> {

    private final SelectItemModelProperty<T> original;

    private final String configKey;

    private final String stateKey;


    public DelayedSelectItemModelProperty(
            SelectItemModelProperty<T> original,
            String configKey,
            String stateKey
    ) {

        this.original =
                original;


        this.configKey =
                configKey;


        this.stateKey =
                stateKey;
    }


    /*
     * ================================================================
     * VALUE
     * ================================================================
     */
    @Override
    public @Nullable T get(
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


        ModelDelayConfig.SelectDelayConfig config =
                ModelDelayConfig.getSelect(
                        itemId,
                        configKey
                );


        /*
         * ============================================================
         * NO MODEL DELAY CONFIG
         * ============================================================
         *
         * Preserve vanilla exactly.
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
         * ============================================================
         * VANILLA VALUE
         * ============================================================
         */
        T vanillaValue =
                original.get(
                        itemStack,
                        level,
                        owner,
                        seed,
                        displayContext
                );


        /*
         * ============================================================
         * DELAYED VALUE
         * ============================================================
         */
        return ModelDelaySelectState.get(
                itemStack,
                owner,
                itemId,
                stateKey,
                vanillaValue,
                config
        );
    }


    /*
     * ================================================================
     * TYPE
     * ================================================================
     *
     * The wrapper only exists after vanilla has already decoded the
     * select property.
     *
     * Preserve the original property's type.
     */
    @Override
    public SelectItemModelProperty.Type<
            ? extends SelectItemModelProperty<T>,
            T
            > type() {

        return original.type();
    }


    /*
     * ================================================================
     * VALUE CODEC
     * ================================================================
     *
     * Different select properties use different value types.
     *
     * ComponentContents is especially important here because its value
     * codec depends on the DataComponentType selected by the model.
     *
     * Always preserve vanilla's original codec.
     */
    @Override
    public Codec<T> valueCodec() {

        return original.valueCodec();
    }
}