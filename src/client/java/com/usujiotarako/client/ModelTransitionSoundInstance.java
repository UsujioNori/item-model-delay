package com.usujiotarako.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;

import net.minecraft.resources.Identifier;

import net.minecraft.sounds.SoundSource;

import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

import org.jspecify.annotations.Nullable;

public final class ModelTransitionSoundInstance
        extends AbstractSoundInstance {

    /*
     * ================================================================
     * DIRECT SOUND RESOURCE
     * ================================================================
     *
     * This Identifier represents the sound WITHOUT:
     *
     *     sounds/
     *     .ogg
     *
     * Example:
     *
     *     minecraft:block/amethyst/break4
     *
     * Sound.getPath() later converts that to:
     *
     *     minecraft:sounds/block/amethyst/break4.ogg
     */
    private final Identifier directSound;


    /*
     * ================================================================
     * CONSTRUCTOR
     * ================================================================
     */
    public ModelTransitionSoundInstance(
            Identifier directSound,
            float volume,
            float pitch,
            double x,
            double y,
            double z
    ) {

        /*
         * AbstractSoundInstance requires an identifier representing the
         * logical sound instance.
         *
         * We use the direct sound resource identifier itself.
         *
         * Our overridden resolve() below prevents Minecraft from trying
         * to look this up as a normal registered SoundEvent.
         */
        super(
                directSound,
                SoundSource.PLAYERS,
                RandomSource.create()
        );


        this.directSound =
                directSound;


        this.volume =
                volume;


        this.pitch =
                pitch;


        this.x =
                x;


        this.y =
                y;


        this.z =
                z;


        /*
         * Transition sounds are one-shot.
         */
        this.looping =
                false;


        this.delay =
                0;


        /*
         * Positional sound.
         *
         * This means the sound behaves like something produced by the
         * player/item rather than like a UI sound fixed to the listener.
         */
        this.attenuation =
                Attenuation.LINEAR;


        this.relative =
                false;
    }


    /*
     * ================================================================
     * DIRECT FILE RESOLUTION
     * ================================================================
     *
     * Normal AbstractSoundInstance.resolve() does:
     *
     *     soundManager.getSoundEvent(identifier)
     *
     * That would interpret:
     *
     *     block/amethyst/break4
     *
     * as a registered SoundEvent.
     *
     * We DO NOT want that.
     *
     * Instead we create a Sound directly. Minecraft's SoundEngine will
     * later call:
     *
     *     sound.getPath()
     *
     * which turns:
     *
     *     minecraft:block/amethyst/break4
     *
     * into:
     *
     *     minecraft:sounds/block/amethyst/break4.ogg
     */
    @Override
    public @Nullable WeighedSoundEvents resolve(
            SoundManager soundManager
    ) {

        Sound direct =
                new Sound(
                        this.directSound,

                        /*
                         * The .mdprop config controls final volume.
                         *
                         * Keep the Sound's own multiplier at 1.
                         */
                        ConstantFloat.of(
                                1.0F
                        ),

                        /*
                         * Same for pitch.
                         */
                        ConstantFloat.of(
                                1.0F
                        ),

                        /*
                         * Weight.
                         *
                         * This synthetic event contains only one sound,
                         * so a weight of one is sufficient.
                         */
                        1,

                        /*
                         * DIRECT FILE rather than another sound event.
                         */
                        Sound.Type.FILE,

                        /*
                         * stream
                         */
                        false,

                        /*
                         * preload
                         */
                        false,

                        /*
                         * Vanilla-style attenuation distance.
                         */
                        16
                );


        /*
         * AbstractSoundInstance.getVolume() and getPitch() use
         * this.sound, so make sure it is populated.
         */
        this.sound =
                direct;


        /*
         * SoundEngine requires resolve() to produce a non-null
         * WeighedSoundEvents.
         *
         * This synthetic event exists only for this playback.
         */
        WeighedSoundEvents syntheticEvent =
                new WeighedSoundEvents(
                        this.directSound,
                        null
                );


        syntheticEvent.addSound(
                direct
        );


        return syntheticEvent;
    }
}