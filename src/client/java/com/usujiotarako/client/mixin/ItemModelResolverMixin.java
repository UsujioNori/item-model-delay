package com.usujiotarako.client.mixin;

import com.usujiotarako.client.ModelDelayState;
import com.usujiotarako.client.ModelDelayRangeState;
import com.usujiotarako.client.ModelDelaySelectState;
import com.usujiotarako.client.ModelTransitionEffects;

import com.usujiotarako.client.render.ModelTransitionEffectRenderState;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        ItemModelResolver.class
)
public abstract class ItemModelResolverMixin {

    /*
     * ================================================================
     * ATTACH TRANSITION EFFECT / ITEM ID
     * ================================================================
     */
    @Inject(
            method = "updateForTopItem",
            at = @At("TAIL")
    )
    private void imd$attachTransitionEffect(
            ItemStackRenderState output,
            ItemStack item,
            ItemDisplayContext displayContext,
            @Nullable Level level,
            @Nullable ItemOwner owner,
            int seed,
            CallbackInfo ci
    ) {

        ModelTransitionEffectRenderState effectState =
                (ModelTransitionEffectRenderState) output;


        /*
         * ItemStackRenderState objects are reused.
         */
        effectState.imd$clearTransitionEffect();

        effectState.imd$clearRenderedItemId();


        if (
                item.isEmpty()
        ) {

            return;
        }


        /*
         * ============================================================
         * ITEM
         * ============================================================
         */
        Identifier itemId =
                BuiltInRegistries.ITEM.getKey(
                        item.getItem()
                );


        /*
         * Keep the identity available during ItemStackRenderState.submit
         * even when there is currently no active effect.
         *
         * This is what allows IMD to learn the BASE model center before
         * a future change effect occurs.
         */
        effectState.imd$setRenderedItemId(
                itemId
        );


        /*
         * Transition particle effects are hand effects.
         *
         * Do not attach the same ActiveEffect to GUI/hotbar/inventory
         * ItemStackRenderState instances.
         */
        if (
                displayContext
                        != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND

                        && displayContext
                        != ItemDisplayContext.FIRST_PERSON_LEFT_HAND

                        && displayContext
                        != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND

                        && displayContext
                        != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        ) {

            return;
        }


        /*
         * ============================================================
         * OWNER
         * ============================================================
         */
        LivingEntity livingOwner =
                owner instanceof LivingEntity living
                        ? living
                        : null;


        int ownerId =
                livingOwner != null
                        ? livingOwner.getId()
                        : -1;


        /*
         * ============================================================
         * NO ACTIVE EFFECT — ZERO IDENTITY WORK
         * ============================================================
         *
         * Model centers are learned later from ItemStackRenderState.submit,
         * so there is no reason to synchronize/resolve every IMD identity
         * domain on every held-item frame while no particle is active.
         */
        if (
                !ModelTransitionEffects.hasActiveEffectForItem(
                        itemId,
                        ownerId
                )
        ) {

            return;
        }


        /*
         * ============================================================
         * PHYSICAL STACK IDENTITIES
         * ============================================================
         *
         * Conditional and range state do not share one identity map.
         * Resolve both and let the active effect choose the correct one.
         */
        long conditionalIdentity =
                ModelDelayState.resolveRenderStackIdentity(
                        item,
                        livingOwner,
                        displayContext
                );


        long rangeIdentity =
                ModelDelayRangeState.resolveRenderStackIdentity(
                        item,
                        livingOwner
                );


        long rangeCountIdentity =
                ModelDelayRangeState.resolveCountRenderStackIdentity(
                        item,
                        livingOwner
                );


        long selectIdentity =
                ModelDelaySelectState.resolveRenderStackIdentity(
                        item,
                        livingOwner
                );


        /*
         * ============================================================
         * ACTIVE EFFECT
         * ============================================================
         */
        ModelTransitionEffects.ActiveEffect effect =
                ModelTransitionEffects.getForRender(
                        itemId,
                        ownerId,
                        conditionalIdentity,
                        rangeIdentity,
                        rangeCountIdentity,
                        selectIdentity
                );


        if (
                effect == null
        ) {

            return;
        }


        effectState.imd$setTransitionEffect(
                effect
        );
    }
}