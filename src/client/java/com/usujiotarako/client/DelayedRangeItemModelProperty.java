package com.usujiotarako.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.UseCycle;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class DelayedRangeItemModelProperty
        implements RangeSelectItemModelProperty {

    private final RangeSelectItemModelProperty original;

    private final String configKey;

    private final String stateKey;

    private final float[] thresholds;


    public DelayedRangeItemModelProperty(
            RangeSelectItemModelProperty original,
            String configKey,
            String stateKey,
            float[] thresholds
    ) {

        this.original =
                original;


        this.configKey =
                configKey;


        this.stateKey =
                stateKey;


        this.thresholds =
                thresholds.clone();
    }


    @Override
    public float get(
            ItemStack itemStack,
            @Nullable ClientLevel level,
            @Nullable ItemOwner owner,
            int seed
    ) {

        Identifier itemId =
                BuiltInRegistries.ITEM.getKey(
                        itemStack.getItem()
                );


        ModelDelayConfig.RangeDelayConfig config =
                ModelDelayConfig.getRange(
                        itemId,
                        configKey
                );


        if (config == null) {

            return original.get(
                    itemStack,
                    level,
                    owner,
                    seed
            );
        }


        float vanillaValue =
                getVanillaValue(
                        itemStack,
                        level,
                        owner,
                        seed
                );


        return ModelDelayRangeState.get(
                itemStack,
                owner,
                itemId,
                stateKey,
                vanillaValue,
                config,
                seed,
                thresholds
        );
    }


    /*
     * ================================================================
     * VANILLA / NORMALIZED VALUE
     * ================================================================
     */
    private float getVanillaValue(
            ItemStack itemStack,
            @Nullable ClientLevel level,
            @Nullable ItemOwner owner,
            int seed
    ) {

        /*
         * ============================================================
         * COOLDOWN
         * ============================================================
         */
        if (
                "range.cooldown".equals(
                        configKey
                )
        ) {

            if (
                    owner != null

                            && owner.asLivingEntity()
                            instanceof Player
            ) {

                return original.get(
                        itemStack,
                        level,
                        owner,
                        seed
                );
            }


            Minecraft minecraft =
                    Minecraft.getInstance();


            Player localPlayer =
                    minecraft.player;


            if (localPlayer != null) {

                return localPlayer
                        .getCooldowns()
                        .getCooldownPercent(
                                itemStack,
                                0.0F
                        );
            }


            return 0.0F;
        }


        /*
         * ============================================================
         * USE DURATION
         * ============================================================
         */
        if (
                "range.use_duration".equals(
                        configKey
                )

                        && original
                        instanceof UseDuration useDuration
        ) {

            LivingEntity entity =
                    resolveUsingEntity(
                            owner
                    );


            if (entity == null) {

                return 0.0F;
            }


            ItemStack actualUseItem =
                    entity.getUseItem();


            if (
                    !representsActualUseItem(
                            itemStack,
                            actualUseItem
                    )
            ) {

                return 0.0F;
            }


            if (useDuration.remaining()) {

                return entity.getUseItemRemainingTicks();
            }


            return UseDuration.useDuration(
                    actualUseItem,
                    entity
            );
        }


        /*
         * ============================================================
         * USE CYCLE
         * ============================================================
         *
         * Vanilla UseCycle#get() performs an exact:
         *
         *     entity.getUseItem() == itemStack
         *
         * check.
         *
         * Render copies can fail that check even while representing the
         * same actively-used item.
         *
         * Normalize those copies to the actual entity.getUseItem().
         */
        if (
                "range.use_cycle".equals(
                        configKey
                )

                        && original
                        instanceof UseCycle useCycle
        ) {

            LivingEntity entity =
                    resolveUsingEntity(
                            owner
                    );


            if (entity == null) {

                return 0.0F;
            }


            ItemStack actualUseItem =
                    entity.getUseItem();


            if (
                    !representsActualUseItem(
                            itemStack,
                            actualUseItem
                    )
            ) {

                return 0.0F;
            }


            return entity
                    .getUseItemRemainingTicks()
                    % useCycle.period();
        }


        /*
         * ============================================================
         * EVERYTHING ELSE
         * ============================================================
         */
        return original.get(
                itemStack,
                level,
                owner,
                seed
        );
    }


    /*
     * ================================================================
     * RESOLVE USING ENTITY
     * ================================================================
     */
    private static @Nullable LivingEntity resolveUsingEntity(
            @Nullable ItemOwner owner
    ) {

        if (owner != null) {

            LivingEntity entity =
                    owner.asLivingEntity();


            if (entity != null) {

                return entity;
            }
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        return minecraft.player;
    }


    /*
     * ================================================================
     * ACTUAL USE ITEM MATCHING
     * ================================================================
     */
    private static boolean representsActualUseItem(
            ItemStack renderedStack,
            ItemStack actualUseItem
    ) {

        if (actualUseItem.isEmpty()) {

            return false;
        }


        /*
         * Exact object is obviously valid.
         */
        if (renderedStack == actualUseItem) {

            return true;
        }


        /*
         * Renderer copy of the same active stack.
         */
        return ItemStack.isSameItemSameComponents(
                renderedStack,
                actualUseItem
        );
    }


    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {

        return original.type();
    }
}