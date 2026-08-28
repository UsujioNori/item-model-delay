package com.usujiotarako.client.mixin;

import com.usujiotarako.client.DelayedRangeItemModelProperty;
import com.usujiotarako.client.ModelDelayRangeProperties;
import net.minecraft.client.renderer.item.RangeSelectItemModel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(RangeSelectItemModel.Unbaked.class)
public class RangeSelectItemModelMixin {

    /*
     * ================================================================
     * WRAP NUMERIC PROPERTY WITH THRESHOLD INFORMATION
     * ================================================================
     *
     * Vanilla bake() eventually calls:
     *
     *     new RangeSelectItemModel(
     *         this.property,     // argument 0
     *         this.scale,        // argument 1
     *         thresholds,       // argument 2
     *         models,           // argument 3
     *         bakedFallback     // argument 4
     *     );
     *
     *
     * We need both:
     *
     *     argument 0 = RangeSelectItemModelProperty
     *     argument 2 = float[] thresholds
     *
     *
     * ModifyArgs lets us inspect both while still allowing VANILLA to
     * invoke its own private constructor.
     *
     * We therefore do NOT need to call:
     *
     *     new RangeSelectItemModel(...)
     *
     * ourselves.
     */
    @ModifyArgs(
            method = "bake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/RangeSelectItemModel;<init>(Lnet/minecraft/client/renderer/item/properties/numeric/RangeSelectItemModelProperty;F[F[Lnet/minecraft/client/renderer/item/ItemModel;Lnet/minecraft/client/renderer/item/ItemModel;)V"
            )
    )
    private void modelDelayHelper$wrapRangeProperty(
            Args args
    ) {

        /*
         * Original numeric property.
         */
        RangeSelectItemModelProperty originalProperty =
                args.get(
                        0
                );


        /*
         * Sorted threshold array produced by vanilla bake().
         */
        float[] thresholds =
                args.get(
                        2
                );


        ModelDelayRangeProperties.PropertyInfo info =
                ModelDelayRangeProperties.getInfo(
                        originalProperty
                );


        /*
         * Unsupported numeric property:
         *
         * preserve vanilla completely.
         */
        if (info == null) {

            return;
        }


        /*
         * Replace ONLY constructor argument 0.
         *
         * Vanilla continues constructing RangeSelectItemModel itself.
         */
        args.set(
                0,
                new DelayedRangeItemModelProperty(
                        originalProperty,
                        info.configKey(),
                        info.stateKey(),
                        thresholds
                )
        );
    }
}