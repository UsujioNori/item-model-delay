package com.usujiotarako.client.mixin;

import com.usujiotarako.client.DelayedItemModelProperty;
import com.usujiotarako.client.ModelDelayProperties;
import net.minecraft.client.renderer.item.ConditionalItemModel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.conditional.ItemModelPropertyTest;
import net.minecraft.util.RegistryContextSwapper;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConditionalItemModel.Unbaked.class)
public class ConditionalItemModelMixin {

    /*
     * ================================================================
     * WRAP SUPPORTED CONDITIONAL PROPERTIES
     * ================================================================
     *
     * Vanilla adaptProperty() has already produced the final
     * ItemModelPropertyTest, including any registry-context adaptation.
     *
     * We wrap that final vanilla property so vanilla remains the source
     * of truth for the actual boolean result.
     */
    @Inject(
            method = "adaptProperty",
            at = @At("RETURN"),
            cancellable = true
    )
    private void modelDelayHelper$wrapProperty(
            ConditionalItemModelProperty originalProperty,
            @Nullable RegistryContextSwapper contextSwapper,
            CallbackInfoReturnable<ItemModelPropertyTest> cir
    ) {

        ModelDelayProperties.PropertyInfo propertyInfo =
                ModelDelayProperties.getInfo(
                        originalProperty
                );


        /*
         * Unsupported property.
         *
         * Preserve vanilla exactly.
         */
        if (propertyInfo == null) {

            return;
        }


        ItemModelPropertyTest vanillaProperty =
                cir.getReturnValue();


        cir.setReturnValue(
                new DelayedItemModelProperty(
                        vanillaProperty,
                        propertyInfo.configKey(),
                        propertyInfo.stateKey()
                )
        );
    }
}