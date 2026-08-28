package com.usujiotarako.client.render;

import com.usujiotarako.client.ModelDelayConfig;
import com.usujiotarako.client.ModelTransitionEffects;

import com.usujiotarako.client.compat.PunchyCompat;
import com.usujiotarako.client.compat.PunchyRenderTransformBridge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

import net.minecraft.resources.Identifier;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import org.jspecify.annotations.Nullable;

public final class ModelTransitionEffectRenderer {

    private ModelTransitionEffectRenderer() {
    }


    /*
     * ================================================================
     * SUBMIT
     * ================================================================
     */
    public static void submit(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            int overlayCoords,
            ItemDisplayContext displayContext,
            AABB modelBounds,
            ModelTransitionEffects.ActiveEffect activeEffect
    ) {

        ModelDelayConfig.TransitionEffectConfig config =
                activeEffect.config();


        if (
                config == null

                        || !config.enabled()
        ) {

            return;
        }


        switch (
                config.effect()
        ) {

            case POOF ->
                    submitPoof(
                            poseStack,
                            submitNodeCollector,
                            lightCoords,
                            displayContext,
                            modelBounds,
                            activeEffect
                    );


            case NONE -> {
            }
        }
    }


    /*
     * ================================================================
     * POOF
     * ================================================================
     */
    private static void submitPoof(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            ItemDisplayContext displayContext,
            AABB modelBounds,
            ModelTransitionEffects.ActiveEffect activeEffect
    ) {

        ModelDelayConfig.TransitionEffectConfig config =
                activeEffect.config();


        float globalProgress =
                clamp01(
                        activeEffect.progress()
                );


        /*
         * ============================================================
         * RENDER ROOT
         * ============================================================
         */
        Matrix4f renderRoot =
                resolveRenderRoot(
                        poseStack,
                        displayContext
                );


        /*
         * ============================================================
         * CURRENT MODEL CENTER
         * ============================================================
         */
        float modelCenterX =
                (float) (
                        (
                                modelBounds.minX
                                        + modelBounds.maxX
                        )
                                * 0.5
                );


        float modelCenterY =
                (float) (
                        (
                                modelBounds.minY
                                        + modelBounds.maxY
                        )
                                * 0.5
                );


        float modelCenterZ =
                (float) (
                        (
                                modelBounds.minZ
                                        + modelBounds.maxZ
                        )
                                * 0.5
                );


        /*
         * ============================================================
         * STABLE PROPERTY / CONTEXT ANCHOR
         * ============================================================
         *
         * Use one learned model center for this property in this exact render
         * context. Reverse transitions therefore stay mapped to the same model,
         * while first-person and third-person anchors remain independent.
         */
        ModelTransitionEffects.ModelCenter stableCenter =
                ModelTransitionEffects.getModelCenter(
                        activeEffect.key(),
                        displayContext
                );


        if (
                stableCenter != null
        ) {

            modelCenterX =
                    stableCenter.x();


            modelCenterY =
                    stableCenter.y();


            modelCenterZ =
                    stableCenter.z();
        }


        final float effectCenterX =
                modelCenterX;


        final float effectCenterY =
                modelCenterY;


        final float effectCenterZ =
                modelCenterZ;


        /*
         * ============================================================
         * ANIMATION FRAMES
         * ============================================================
         */
        int frames =
                Math.max(
                        1,
                        config.frames()
                );


        for (
                int frame = 0;
                frame < frames;
                frame++
        ) {

            final int currentFrame =
                    frame;


            Identifier frameTexture =
                    getFrameTexture(
                            config,
                            currentFrame
                    );


            RenderType renderType =
                    RenderTypes.itemTranslucent(
                            frameTexture
                    );


            submitNodeCollector.submitCustomGeometry(
                    poseStack,
                    renderType,
                    (callbackPose, buffer) ->
                            renderParticlesForFrame(
                                    buffer,
                                    lightCoords,
                                    config,
                                    globalProgress,
                                    currentFrame,
                                    renderRoot,
                                    effectCenterX,
                                    effectCenterY,
                                    effectCenterZ
                            )
            );
        }
    }


    /*
     * ================================================================
     * RENDER ROOT
     * ================================================================
     */
    private static Matrix4f resolveRenderRoot(
            PoseStack poseStack,
            ItemDisplayContext displayContext
    ) {

        if (
                PunchyCompat.isLoaded()
        ) {

            HumanoidArm arm =
                    armForDisplayContext(
                            displayContext
                    );


            if (
                    arm != null
            ) {

                Matrix4f punchyRoot =
                        PunchyRenderTransformBridge.getPreItemRoot(
                                arm
                        );


                if (
                        punchyRoot != null
                ) {

                    return punchyRoot;
                }
            }
        }


        return new Matrix4f(
                poseStack.last()
                        .pose()
        );
    }


    /*
     * ================================================================
     * DISPLAY CONTEXT -> ARM
     * ================================================================
     */
    private static @Nullable HumanoidArm armForDisplayContext(
            ItemDisplayContext displayContext
    ) {

        if (
                displayContext
                        == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
        ) {

            return HumanoidArm.RIGHT;
        }


        if (
                displayContext
                        == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        ) {

            return HumanoidArm.LEFT;
        }


        return null;
    }


    /*
     * ================================================================
     * RENDER PARTICLES
     * ================================================================
     */
    private static void renderParticlesForFrame(
            VertexConsumer buffer,
            int lightCoords,
            ModelDelayConfig.TransitionEffectConfig config,
            float globalProgress,
            int requestedFrame,
            Matrix4f renderRoot,
            float modelCenterX,
            float modelCenterY,
            float modelCenterZ
    ) {

        int count =
                Math.max(
                        1,
                        config.count()
                );


        for (
                int i = 0;
                i < count;
                i++
        ) {

            /*
             * ========================================================
             * PARTICLE PERSONALITY
             * ========================================================
             */
            float startRandom =
                    hash01(
                            i,
                            11
                    );


            float sizeRandom =
                    hash01(
                            i,
                            23
                    );


            float speedRandom =
                    hash01(
                            i,
                            37
                    );


            float movementAngleRandom =
                    hash01(
                            i,
                            53
                    );


            float movementVerticalRandom =
                    hash01(
                            i,
                            71
                    );


            float movementDepthRandom =
                    hash01(
                            i,
                            89
                    );


            float rollRandom =
                    hash01(
                            i,
                            107
                    );


            float radiusAngleRandom =
                    hash01(
                            i,
                            131
                    );


            float radiusHeightRandom =
                    hash01(
                            i,
                            149
                    );


            float radiusDistanceRandom =
                    hash01(
                            i,
                            167
                    );


            /*
             * ========================================================
             * STAGGER
             * ========================================================
             */
            float startOffset =
                    startRandom
                            * config.stagger();


            if (
                    globalProgress
                            < startOffset
            ) {

                continue;
            }


            float availableLifetime =
                    1.0F
                            - startOffset;


            if (
                    availableLifetime
                            <= 0.0001F
            ) {

                continue;
            }


            float localProgress =
                    clamp01(
                            (
                                    globalProgress
                                            - startOffset
                            )
                                    / availableLifetime
                    );


            /*
             * ========================================================
             * ANIMATION FRAME
             * ========================================================
             */
            int particleFrame =
                    getFrameIndex(
                            config,
                            localProgress
                    );


            if (
                    particleFrame
                            != requestedFrame
            ) {

                continue;
            }


            /*
             * ========================================================
             * INITIAL 3D VOLUME DIRECTION
             * ========================================================
             *
             * First generate a normal point inside a UNIT sphere.
             *
             * We then scale the three axes independently to turn that
             * sphere into an ellipsoid.
             */
            float radiusAngle =
                    radiusAngleRandom
                            * (
                            (float) Math.PI
                                    * 2.0F
                    );


            float radiusYDirection =
                    radiusHeightRandom
                            * 2.0F
                            - 1.0F;


            float radiusHorizontal =
                    (float) Math.sqrt(
                            Math.max(
                                    0.0F,
                                    1.0F
                                            - radiusYDirection
                                            * radiusYDirection
                            )
                    );


            float radiusDirectionX =
                    (float) Math.cos(
                            radiusAngle
                    )
                            * radiusHorizontal;


            float radiusDirectionY =
                    radiusYDirection;


            float radiusDirectionZ =
                    (float) Math.sin(
                            radiusAngle
                    )
                            * radiusHorizontal;


            /*
             * Cube-root distribution keeps points distributed through
             * the volume instead of concentrating them at the center.
             *
             * This is a NORMALIZED 0 -> 1 distance now.
             */
            float normalizedRadiusDistance =
                    (float) Math.cbrt(
                            radiusDistanceRandom
                    );


            /*
             * ========================================================
             * ELLIPSOID INITIAL POSITION
             * ========================================================
             *
             * THIS is the important anisotropic-radius change.
             *
             * Uniform:
             *
             *     radius=0.25
             *
             * means:
             *
             *     radiusX = .25
             *     radiusY = .25
             *     radiusZ = .25
             *
             *
             * Stretched:
             *
             *     radius=0.10,0.50,0.10
             *
             * gives a long thin volume.
             */
            /*
             * ================================================================
             * INITIAL POSITION
             * ================================================================
             *
             * Two supported modes:
             *
             * SYMMETRIC
             *
             *     radius=0.05,0.60,0.05
             *
             * produces:
             *
             *     X = -0.05 -> +0.05
             *     Y = -0.60 -> +0.60
             *     Z = -0.05 -> +0.05
             *
             *
             * DIRECTIONAL
             *
             *     radius_min=-0.05,0.0,-0.05
             *     radius_max= 0.05,0.60,0.05
             *
             * produces a volume entirely above the effect origin.
             */
            float initialX;


            float initialY;


            float initialZ;


            if (
                    config.directionalRadius()
            ) {

                /*
                 * Convert our unit-sphere coordinates from:
                 *
                 *     -1 -> +1
                 *
                 * into:
                 *
                 *      0 -> 1
                 *
                 * before mapping them into the configured min/max range.
                 *
                 * normalizedRadiusDistance is retained so the cloud still has
                 * volumetric randomness instead of becoming a solid shell.
                 */
                float normalizedX =
                        (
                                radiusDirectionX
                                        * normalizedRadiusDistance
                                        + 1.0F
                        )
                                * 0.5F;


                float normalizedY =
                        (
                                radiusDirectionY
                                        * normalizedRadiusDistance
                                        + 1.0F
                        )
                                * 0.5F;


                float normalizedZ =
                        (
                                radiusDirectionZ
                                        * normalizedRadiusDistance
                                        + 1.0F
                        )
                                * 0.5F;


                initialX =
                        config.radiusMinX()
                                + normalizedX
                                * (
                                config.radiusMaxX()
                                        - config.radiusMinX()
                        );


                initialY =
                        config.radiusMinY()
                                + normalizedY
                                * (
                                config.radiusMaxY()
                                        - config.radiusMinY()
                        );


                initialZ =
                        config.radiusMinZ()
                                + normalizedZ
                                * (
                                config.radiusMaxZ()
                                        - config.radiusMinZ()
                        );

            } else {

                /*
                 * Existing symmetric ellipsoid behavior.
                 */
                initialX =
                        radiusDirectionX
                                * normalizedRadiusDistance
                                * config.radiusX();


                initialY =
                        radiusDirectionY
                                * normalizedRadiusDistance
                                * config.radiusY();


                initialZ =
                        radiusDirectionZ
                                * normalizedRadiusDistance
                                * config.radiusZ();
            }


            /*
             * ========================================================
             * MOVEMENT DIRECTION
             * ========================================================
             *
             * Spread remains completely independent of radius.
             */
            float movementAngle =
                    movementAngleRandom
                            * (
                            (float) Math.PI
                                    * 2.0F
                    );


            float movementHorizontal =
                    0.55F
                            + movementDepthRandom
                            * 0.45F;


            float movementDirectionX =
                    (float) Math.cos(
                            movementAngle
                    )
                            * movementHorizontal;


            float movementDirectionZ =
                    (float) Math.sin(
                            movementAngle
                    )
                            * movementHorizontal;


            float movementDirectionY =
                    -0.35F
                            + movementVerticalRandom
                            * 1.25F;


            /*
             * ========================================================
             * SPEED
             * ========================================================
             */
            float speed =
                    0.78F
                            + speedRandom
                            * 0.44F;


            /*
             * ========================================================
             * MOVEMENT
             * ========================================================
             */
            float movementProgress =
                    1.0F
                            - (
                            1.0F
                                    - localProgress
                    )
                            * (
                            1.0F
                                    - localProgress
                    );


            float movementDistance =
                    (
                            0.035F
                                    + movementProgress
                                    * 0.28F
                    )
                            * config.spread()
                            * speed;


            /*
             * ========================================================
             * LOCAL CENTER
             * ========================================================
             */
            float localX =
                    modelCenterX
                            + config.originX()
                            + initialX
                            + movementDirectionX
                            * movementDistance;


            float localY =
                    modelCenterY
                            + config.originY()
                            + initialY
                            + movementDirectionY
                            * movementDistance;


            float localZ =
                    modelCenterZ
                            + config.originZ()
                            + initialZ
                            + movementDirectionZ
                            * movementDistance;


            /*
             * Gentle upward drift.
             */
            localY +=
                    localProgress
                            * 0.035F
                            * config.spread();


            /*
             * ========================================================
             * FINAL CENTER
             * ========================================================
             */
            Vector3f center =
                    transformPosition(
                            renderRoot,
                            localX,
                            localY,
                            localZ
                    );


            /*
             * ========================================================
             * SIZE
             * ========================================================
             *
             * Still completely separate from radius and spread.
             */
            float individualScale =
                    0.78F
                            + sizeRandom
                            * 0.44F;


            float growth =
                    0.095F
                            + localProgress
                            * 0.065F;


            float size =
                    growth
                            * individualScale
                            * config.size();


            if (
                    size <= 0.0F
            ) {

                continue;
            }


            /*
             * ========================================================
             * ALPHA
             * ========================================================
             */
            float fadeProgress =
                    Math.max(
                            0.0F,
                            (
                                    localProgress
                                            - 0.45F
                            )
                                    / 0.55F
                    );


            float alpha =
                    1.0F
                            - fadeProgress;


            alpha *=
                    alpha;


            int alphaByte =
                    Math.max(
                            0,
                            Math.min(
                                    255,
                                    (int) (
                                            alpha
                                                    * 255.0F
                                    )
                            )
                    );


            if (
                    alphaByte <= 0
            ) {

                continue;
            }


            /*
             * ========================================================
             * RANDOM ROLL
             * ========================================================
             */
            float roll =
                    (
                            rollRandom
                                    * 2.0F
                                    - 1.0F
                    )
                            * (float) Math.PI;


            /*
             * ========================================================
             * SCREEN-ALIGNED SPRITE
             * ========================================================
             */
            addScreenAlignedQuad(
                    buffer,
                    center.x,
                    center.y,
                    center.z,
                    size,
                    roll,
                    alphaByte,
                    lightCoords
            );
        }
    }


    /*
     * ================================================================
     * SCREEN-ALIGNED QUAD
     * ================================================================
     */
    private static void addScreenAlignedQuad(
            VertexConsumer buffer,
            float centerX,
            float centerY,
            float centerZ,
            float size,
            float roll,
            int alpha,
            int lightCoords
    ) {

        float cos =
                (float) Math.cos(
                        roll
                );


        float sin =
                (float) Math.sin(
                        roll
                );


        float x1 =
                (
                        cos
                                + sin
                )
                        * size;


        float y1 =
                (
                        sin
                                - cos
                )
                        * size;


        float x2 =
                (
                        cos
                                - sin
                )
                        * size;


        float y2 =
                (
                        sin
                                + cos
                )
                        * size;


        float x3 =
                (
                        -cos
                                - sin
                )
                        * size;


        float y3 =
                (
                        -sin
                                + cos
                )
                        * size;


        float x4 =
                (
                        -cos
                                + sin
                )
                        * size;


        float y4 =
                (
                        -sin
                                - cos
                )
                        * size;


        /*
         * ============================================================
         * FRONT
         * ============================================================
         */
        screenVertex(
                buffer,
                centerX + x1,
                centerY + y1,
                centerZ,
                1.0F,
                1.0F,
                alpha,
                lightCoords,
                1.0F
        );


        screenVertex(
                buffer,
                centerX + x2,
                centerY + y2,
                centerZ,
                1.0F,
                0.0F,
                alpha,
                lightCoords,
                1.0F
        );


        screenVertex(
                buffer,
                centerX + x3,
                centerY + y3,
                centerZ,
                0.0F,
                0.0F,
                alpha,
                lightCoords,
                1.0F
        );


        screenVertex(
                buffer,
                centerX + x4,
                centerY + y4,
                centerZ,
                0.0F,
                1.0F,
                alpha,
                lightCoords,
                1.0F
        );


        /*
         * ============================================================
         * BACK
         * ============================================================
         */
        screenVertex(
                buffer,
                centerX + x4,
                centerY + y4,
                centerZ,
                0.0F,
                1.0F,
                alpha,
                lightCoords,
                -1.0F
        );


        screenVertex(
                buffer,
                centerX + x3,
                centerY + y3,
                centerZ,
                0.0F,
                0.0F,
                alpha,
                lightCoords,
                -1.0F
        );


        screenVertex(
                buffer,
                centerX + x2,
                centerY + y2,
                centerZ,
                1.0F,
                0.0F,
                alpha,
                lightCoords,
                -1.0F
        );


        screenVertex(
                buffer,
                centerX + x1,
                centerY + y1,
                centerZ,
                1.0F,
                1.0F,
                alpha,
                lightCoords,
                -1.0F
        );
    }


    /*
     * ================================================================
     * SCREEN VERTEX
     * ================================================================
     */
    private static void screenVertex(
            VertexConsumer buffer,
            float x,
            float y,
            float z,
            float u,
            float v,
            int alpha,
            int lightCoords,
            float normalZ
    ) {

        buffer.addVertex(
                x,
                y,
                z,
                packColor(
                        255,
                        255,
                        255,
                        alpha
                ),
                u,
                v,
                OverlayTexture.NO_OVERLAY,
                lightCoords,
                0.0F,
                0.0F,
                normalZ
        );
    }


    /*
     * ================================================================
     * TRANSFORM POSITION
     * ================================================================
     */
    private static Vector3f transformPosition(
            Matrix4f matrix,
            float x,
            float y,
            float z
    ) {

        return matrix.transformPosition(
                x,
                y,
                z,
                new Vector3f()
        );
    }


    /*
     * ================================================================
     * FRAME INDEX
     * ================================================================
     */
    private static int getFrameIndex(
            ModelDelayConfig.TransitionEffectConfig config,
            float progress
    ) {

        int frames =
                Math.max(
                        1,
                        config.frames()
                );


        if (
                frames == 1
        ) {

            return 0;
        }


        return Math.min(
                frames - 1,
                (int) (
                        clamp01(
                                progress
                        )
                                * frames
                )
        );
    }


    /*
     * ================================================================
     * FRAME TEXTURE
     * ================================================================
     */
    private static Identifier getFrameTexture(
            ModelDelayConfig.TransitionEffectConfig config,
            int frame
    ) {

        Identifier baseTexture =
                config.texture();


        int frames =
                Math.max(
                        1,
                        config.frames()
                );


        if (
                frames == 1
        ) {

            return baseTexture;
        }


        String path =
                baseTexture.getPath();


        String basePath;


        if (
                path.endsWith(
                        ".png"
                )
        ) {

            basePath =
                    path.substring(
                            0,
                            path.length()
                                    - ".png".length()
                    );

        } else {

            basePath =
                    path;
        }


        return Identifier.fromNamespaceAndPath(
                baseTexture.getNamespace(),
                basePath
                        + "_"
                        + frame
                        + ".png"
        );
    }


    /*
     * ================================================================
     * PACK COLOR
     * ================================================================
     */
    private static int packColor(
            int red,
            int green,
            int blue,
            int alpha
    ) {

        return (
                alpha & 255
        ) << 24

                | (
                red & 255
        ) << 16

                | (
                green & 255
        ) << 8

                | (
                blue & 255
        );
    }


    /*
     * ================================================================
     * DETERMINISTIC RANDOM
     * ================================================================
     */
    private static float hash01(
            int index,
            int salt
    ) {

        int value =
                index
                        * 0x1f1f1f1f
                        + salt
                        * 0x45d9f3b;


        value ^=
                value >>> 16;


        value *=
                0x45d9f3b;


        value ^=
                value >>> 16;


        int positive =
                value
                        & 0x00FFFFFF;


        return positive
                / 16777216.0F;
    }


    /*
     * ================================================================
     * CLAMP
     * ================================================================
     */
    private static float clamp01(
            float value
    ) {

        if (
                value < 0.0F
        ) {

            return 0.0F;
        }


        if (
                value > 1.0F
        ) {

            return 1.0F;
        }


        return value;
    }
}