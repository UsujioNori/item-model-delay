package com.usujiotarako.client.mixin;

import com.usujiotarako.client.DelayedSelectItemModelProperty;
import com.usujiotarako.client.ModelDelaySelectProperties;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(SelectItemModel.UnbakedSwitch.class)
public class SelectItemModelMixin {

    /*
     * ================================================================
     * WRAP SELECT PROPERTY
     * ================================================================
     *
     * Vanilla UnbakedSwitch.bake() eventually executes:
     *
     *     new SelectItemModel<>(
     *         this.property,
     *         this.createModelGetter(...)
     *     );
     *
     *
     * We replace only constructor argument 0.
     *
     * Vanilla still owns:
     *
     *     case decoding
     *     model baking
     *     model selection
     *     registry remapping
     *     fallback handling
     */
    @ModifyArgs(
            method = "bake",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/SelectItemModel;<init>(Lnet/minecraft/client/renderer/item/properties/select/SelectItemModelProperty;Lnet/minecraft/client/renderer/item/SelectItemModel$ModelSelector;)V"
            )
    )
    @SuppressWarnings({
            "rawtypes",
            "unchecked"
    })
    private void modelDelayHelper$wrapSelectProperty(
            Args args
    ) {

        SelectItemModelProperty originalProperty =
                args.get(
                        0
                );


        ModelDelaySelectProperties.PropertyInfo info =
                ModelDelaySelectProperties.getInfo(
                        originalProperty
                );


        /*
         * Unsupported select property.
         *
         * Leave vanilla completely untouched.
         */
        if (info == null) {

            return;
        }


        args.set(
                0,
                new DelayedSelectItemModelProperty(
                        originalProperty,
                        info.configKey(),
                        info.stateKey()
                )
        );
    }
}