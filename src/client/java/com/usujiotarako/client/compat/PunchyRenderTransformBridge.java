package com.usujiotarako.client.compat;

import net.minecraft.world.entity.HumanoidArm;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class PunchyRenderTransformBridge {

    /*
     * ================================================================
     * PRE-ITEM RENDER ROOTS
     * ================================================================
     *
     * These are captured immediately BEFORE Punchy calls:
     *
     *     ItemInHandRenderer.renderItem(...)
     *
     * At this point Punchy has already applied:
     *
     *     arm animation
     *     grip transform
     *     Hand Editor / ToolTuning positioning
     *     Punchy item translation
     *     Punchy item rotation
     *     Punchy item scale
     *
     * but Minecraft has NOT yet applied the ItemStackRenderState
     * layer's ItemTransform/localTransform.
     *
     * That makes this the correct matrix to combine with
     * ItemStackRenderState.getModelBoundingBox(), because that AABB
     * already includes those layer transforms itself.
     */
    private static @Nullable Matrix4f rightHandPreItemRoot;

    private static @Nullable Matrix4f leftHandPreItemRoot;


    private PunchyRenderTransformBridge() {
    }


    /*
     * ================================================================
     * CAPTURE PRE-ITEM ROOT
     * ================================================================
     */
    public static void capturePreItemRoot(
            HumanoidArm arm,
            Matrix4f transform
    ) {

        Matrix4f copy =
                new Matrix4f(
                        transform
                );


        if (
                arm == HumanoidArm.RIGHT
        ) {

            rightHandPreItemRoot =
                    copy;

        } else {

            leftHandPreItemRoot =
                    copy;
        }
    }


    /*
     * ================================================================
     * GET PRE-ITEM ROOT
     * ================================================================
     */
    public static @Nullable Matrix4f getPreItemRoot(
            HumanoidArm arm
    ) {

        Matrix4f root =
                arm == HumanoidArm.RIGHT
                        ? rightHandPreItemRoot
                        : leftHandPreItemRoot;


        if (
                root == null
        ) {

            return null;
        }


        return new Matrix4f(
                root
        );
    }


    /*
     * ================================================================
     * HAS PRE-ITEM ROOT
     * ================================================================
     */
    public static boolean hasPreItemRoot(
            HumanoidArm arm
    ) {

        return arm == HumanoidArm.RIGHT
                ? rightHandPreItemRoot != null
                : leftHandPreItemRoot != null;
    }


    /*
     * ================================================================
     * CLEAR
     * ================================================================
     */
    public static void clear() {

        rightHandPreItemRoot =
                null;

        leftHandPreItemRoot =
                null;
    }
}