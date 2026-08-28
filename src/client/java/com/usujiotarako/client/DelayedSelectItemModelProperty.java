package com.usujiotarako.client;

import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
        ItemStack valueStack =
                authoritativeHandStack(
                        itemStack,
                        owner,
                        displayContext
                );


        T vanillaValue =
                original.get(
                        valueStack,
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
                valueStack,
                owner,
                itemId,
                stateKey,
                vanillaValue,
                config
        );
    }


    /*
     * ================================================================
     * AUTHORITATIVE HELD STACK
     * ================================================================
     *
     * Hand rendering may evaluate select properties against a renderer
     * copy whose components lag behind the real selected/offhand stack.
     *
     * Component-backed select properties such as block_state, rarity,
     * trim material, and charge type must read the authoritative physical
     * hand stack or their GUI state can update while the held model stays
     * on the stale value.
     *
     * Only normalize first/third-person hand contexts. GUI/menu/ground
     * rendering keeps vanilla's supplied ItemStack exactly.
     */
    private static ItemStack authoritativeHandStack(
            ItemStack renderStack,
            @Nullable LivingEntity owner,
            ItemDisplayContext displayContext
    ) {

        boolean rightArm =
                displayContext
                        == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND

                        || displayContext
                        == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;


        boolean leftArm =
                displayContext
                        == ItemDisplayContext.FIRST_PERSON_LEFT_HAND

                        || displayContext
                        == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;


        if (!rightArm && !leftArm) {

            return renderStack;
        }


        Player player =
                owner instanceof Player ownerPlayer
                        ? ownerPlayer
                        : null;


        if (player == null) {

            Minecraft minecraft =
                    Minecraft.getInstance();


            LocalPlayer localPlayer =
                    minecraft.player;


            if (localPlayer != null) {

                player =
                        localPlayer;
            }
        }


        if (player == null) {

            return renderStack;
        }


        boolean renderedArmIsMain =
                rightArm
                        == (
                        player.getMainArm()
                                == HumanoidArm.RIGHT
                );


        InteractionHand hand =
                renderedArmIsMain
                        ? InteractionHand.MAIN_HAND
                        : InteractionHand.OFF_HAND;


        ItemStack authoritative =
                player.getItemInHand(
                        hand
                );


        if (
                authoritative.isEmpty()

                        || authoritative.getItem()
                        != renderStack.getItem()
        ) {

            return renderStack;
        }


        return authoritative;
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