package com.usujiotarako.client.mixin;

import com.usujiotarako.client.ModelTransitionEffects;

import com.usujiotarako.client.render.ModelTransitionEffectRenderState;
import com.usujiotarako.client.render.ModelTransitionEffectRenderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;

import net.minecraft.resources.Identifier;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        ItemStackRenderState.class
)
public abstract class ItemStackRenderStateMixin
        implements ModelTransitionEffectRenderState {

    /*
     * ================================================================
     * DISPLAY CONTEXT
     * ================================================================
     */
    @Shadow
    ItemDisplayContext displayContext;


    /*
     * ================================================================
     * MODEL BOUNDS
     * ================================================================
     */
    @Shadow
    public abstract AABB getModelBoundingBox();


    /*
     * ================================================================
     * ACTIVE EFFECT
     * ================================================================
     */
    @Unique
    private ModelTransitionEffects.@Nullable ActiveEffect
            imd$transitionEffect;


    /*
     * ================================================================
     * ITEM ID
     * ================================================================
     */
    @Unique
    private @Nullable Identifier
            imd$renderedItemId;


    /*
     * ================================================================
     * EFFECT SET
     * ================================================================
     */
    @Override
    public void imd$setTransitionEffect(
            ModelTransitionEffects.@Nullable ActiveEffect effect
    ) {

        this.imd$transitionEffect =
                effect;
    }


    /*
     * ================================================================
     * EFFECT GET
     * ================================================================
     */
    @Override
    public ModelTransitionEffects.@Nullable ActiveEffect
    imd$getTransitionEffect() {

        return this.imd$transitionEffect;
    }


    /*
     * ================================================================
     * ITEM ID SET
     * ================================================================
     */
    @Override
    public void imd$setRenderedItemId(
            @Nullable Identifier itemId
    ) {

        this.imd$renderedItemId =
                itemId;
    }


    /*
     * ================================================================
     * ITEM ID GET
     * ================================================================
     */
    @Override
    public @Nullable Identifier imd$getRenderedItemId() {

        return this.imd$renderedItemId;
    }


    /*
     * ================================================================
     * CLEAR
     * ================================================================
     */
    @Inject(
            method = "clear",
            at = @At("HEAD")
    )
    private void imd$clearEffectState(
            CallbackInfo ci
    ) {

        this.imd$transitionEffect =
                null;


        this.imd$renderedItemId =
                null;
    }


    /*
     * ================================================================
     * HELD DISPLAY CONTEXT
     * ================================================================
     */
    @Unique
    private static boolean imd$isHeldDisplayContext(
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


    /*
     * ================================================================
     * SUBMIT
     * ================================================================
     */
    @Inject(
            method = "submit",
            at = @At("TAIL")
    )
    private void imd$submitTransitionEffect(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            CallbackInfo ci
    ) {

        /*
         * Transition particles belong to the held item model.
         *
         * GUI/hotbar/inventory render states must not update the anchor or
         * submit another copy of the same effect.
         */
        if (
                !imd$isHeldDisplayContext(
                        this.displayContext
                )
        ) {

            return;
        }


        /*
         * ============================================================
         * MODEL CENTER
         * ============================================================
         *
         * Do this EVEN WHEN NO EFFECT IS ACTIVE.
         *
         * That is the critical part of the base-anchor system:
         *
         * while the base FALSE model is sitting normally in the hand,
         * IMD continuously knows its correct transformed model center.
         */
        AABB modelBounds =
                this.getModelBoundingBox();


        float currentCenterX =
                (float) (
                        (
                                modelBounds.minX
                                        + modelBounds.maxX
                        )
                                * 0.5
                );


        float currentCenterY =
                (float) (
                        (
                                modelBounds.minY
                                        + modelBounds.maxY
                        )
                                * 0.5
                );


        float currentCenterZ =
                (float) (
                        (
                                modelBounds.minZ
                                        + modelBounds.maxZ
                        )
                                * 0.5
                );


        Identifier itemId =
                this.imd$renderedItemId;


        if (
                itemId != null
        ) {

            ModelTransitionEffects.observeRenderedModelCenter(
                    itemId,
                    this.displayContext,
                    currentCenterX,
                    currentCenterY,
                    currentCenterZ
            );
        }


        /*
         * ============================================================
         * EFFECT
         * ============================================================
         */
        ModelTransitionEffects.ActiveEffect effect =
                this.imd$transitionEffect;


        if (
                effect == null

                        || !effect.config().enabled()
        ) {

            return;
        }


        ModelTransitionEffectRenderer.submit(
                poseStack,
                submitNodeCollector,
                lightCoords,
                overlayCoords,
                this.displayContext,
                modelBounds,
                effect
        );
    }
}