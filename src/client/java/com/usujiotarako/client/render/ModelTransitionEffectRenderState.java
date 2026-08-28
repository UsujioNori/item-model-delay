package com.usujiotarako.client.render;

import com.usujiotarako.client.ModelTransitionEffects;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

public interface ModelTransitionEffectRenderState {

    /*
     * ================================================================
     * EFFECT
     * ================================================================
     */
    void imd$setTransitionEffect(
            ModelTransitionEffects.@Nullable ActiveEffect effect
    );


    ModelTransitionEffects.@Nullable ActiveEffect
    imd$getTransitionEffect();


    default void imd$clearTransitionEffect() {

        imd$setTransitionEffect(
                null
        );
    }


    /*
     * ================================================================
     * RENDERED ITEM ID
     * ================================================================
     *
     * The ItemStackRenderState itself does not retain the ItemStack.
     *
     * IMD remembers the item ID while ItemModelResolver is preparing
     * the state so the submit-side mixin can associate rendered bounds
     * with the correct item.
     */
    void imd$setRenderedItemId(
            @Nullable Identifier itemId
    );


    @Nullable Identifier imd$getRenderedItemId();


    default void imd$clearRenderedItemId() {

        imd$setRenderedItemId(
                null
        );
    }
}