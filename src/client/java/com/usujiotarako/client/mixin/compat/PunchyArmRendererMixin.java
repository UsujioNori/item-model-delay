package com.usujiotarako.client.mixin.compat;

import com.usujiotarako.client.compat.PunchyRenderTransformBridge;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import net.minecraft.world.entity.HumanoidArm;

import org.joml.Matrix4f;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        targets = "punchy.client.render.PunchyArmRenderer",
        remap = false
)
public abstract class PunchyArmRendererMixin {

    /*
     * ================================================================
     * CAPTURE PRE-ITEM RENDER ROOT
     * ================================================================
     *
     * PunchyArmRenderer.renderItemInHand(...) contains two calls to
     * ItemInHandRenderer.renderItem(...).
     *
     * ordinal 0 is Punchy's debug pivot item.
     *
     * ordinal 1 is the ACTUAL held item.
     *
     * Capture the PoseStack immediately before ordinal 1.
     *
     * This corresponds directly to Punchy's own:
     *
     *     Matrix4f itemRoot =
     *         new Matrix4f(
     *             poseStack.last().pose()
     *         );
     *
     * which Punchy performs immediately before rendering the item.
     */
    @Inject(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
                    ordinal = 1
            ),
            require = 0
    )
    private static void imd$capturePreItemRenderRoot(
            ItemInHandRenderer itemInHandRenderer,
            PlayerModel playerModel,
            AvatarRenderState renderState,
            LocalPlayer player,
            HumanoidArm arm,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Matrix4f armTransform,
            ModelPart armPart,
            ModelPart sleevePart,
            ModelPart itemGripPart,
            float partialTick,
            CallbackInfo ci
    ) {

        PunchyRenderTransformBridge.capturePreItemRoot(
                arm,
                poseStack.last()
                        .pose()
        );
    }
}