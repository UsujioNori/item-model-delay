package com.usujiotarako.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ThreadLocalRandom;

public final class ModelTransitionSounds {

    private ModelTransitionSounds() {
    }


    /*
     * ================================================================
     * TRANSITION
     * ================================================================
     */
    public static void onTransition(
            ModelTransitionEvent.Event event
    ) {

        /*
         * The state system which created the event already knows which
         * config type it came from:
         *
         *     conditional
         *     range
         *     select
         *
         * Therefore the event carries the exact sound config with it.
         *
         * This avoids guessing with ModelDelayConfig.get()/getRange()/
         * getSelect() here.
         */
        ModelDelayConfig.TransitionSoundConfig soundConfig =
                event.soundConfig();


        if (
                soundConfig == null

                        || !soundConfig.enabled()
        ) {

            return;
        }


        /*
         * ============================================================
         * TRIGGER FILTER
         * ============================================================
         */
        if (
                event.type()
                        == ModelTransitionEvent.Type.CHANGE

                        && soundConfig.trigger()
                        != ModelDelayConfig.TransitionSoundTrigger.CHANGE
        ) {

            return;
        }


        if (
                event.type()
                        == ModelTransitionEvent.Type.DELAYED

                        && soundConfig.trigger()
                        != ModelDelayConfig.TransitionSoundTrigger.DELAYED
        ) {

            return;
        }


        Minecraft minecraft =
                Minecraft.getInstance();


        LocalPlayer player =
                minecraft.player;


        if (
                player == null
        ) {

            return;
        }


        play(
                minecraft,
                player,
                soundConfig
        );
    }


    /*
     * ================================================================
     * PLAY
     * ================================================================
     */
    private static void play(
            Minecraft minecraft,
            LocalPlayer player,
            ModelDelayConfig.TransitionSoundConfig config
    ) {

        Identifier[] sounds =
                config.sounds();


        if (
                sounds == null

                        || sounds.length == 0
        ) {

            return;
        }


        /*
         * ============================================================
         * RANDOM SOUND
         * ================================================================
         */
        int soundIndex =
                ThreadLocalRandom
                        .current()
                        .nextInt(
                                sounds.length
                        );


        Identifier sound =
                sounds[
                        soundIndex
                        ];


        /*
         * ============================================================
         * RANDOM PITCH
         * ================================================================
         */
        float pitch =
                randomBetween(
                        config.pitchMin(),
                        config.pitchMax()
                );


        ModelTransitionSoundInstance instance =
                new ModelTransitionSoundInstance(
                        sound,
                        config.volume(),
                        pitch,
                        player.getX(),
                        player.getY(),
                        player.getZ()
                );


        minecraft
                .getSoundManager()
                .play(
                        instance
                );
    }


    /*
     * ================================================================
     * RANDOM RANGE
     * ================================================================
     */
    private static float randomBetween(
            float minimum,
            float maximum
    ) {

        if (
                minimum >= maximum
        ) {

            return minimum;
        }


        return minimum
                + ThreadLocalRandom
                .current()
                .nextFloat()
                * (
                maximum
                        - minimum
        );
    }
}
