package com.usujiotarako.client;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.resources.Identifier;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;

import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class ModelDelayResourceLoader {

    private static final String RESOURCE_DIRECTORY =
            "items/imd";


    private static final String RESOURCE_PATH_PREFIX =
            RESOURCE_DIRECTORY
                    + "/";


    private static final String EXTENSION =
            ".mdprop";


    private static final String RANGE_PREFIX =
            "range.";


    private static final String SELECT_PREFIX =
            "select.";


    private record LoadedItemConfig(
            ModelDelayConfig.Behavior behavior,
            Map<String, ModelDelayConfig.DelayConfig> properties,
            Map<String, ModelDelayConfig.RangeDelayConfig> rangeProperties,
            Map<String, ModelDelayConfig.SelectDelayConfig> selectProperties
    ) {
    }


    private record Radius(
            float x,
            float y,
            float z
    ) {
    }


    private record RadiusBounds(
            boolean enabled,

            float minX,
            float minY,
            float minZ,

            float maxX,
            float maxY,
            float maxZ
    ) {
    }


    private ModelDelayResourceLoader() {
    }


    /*
     * ================================================================
     * INITIALIZE
     * ================================================================
     */
    public static void initialize() {

        ResourceLoader.get(
                        PackType.CLIENT_RESOURCES
                )
                .registerReloadListener(

                        Identifier.fromNamespaceAndPath(
                                "usus_item_model_delay",
                                "mdprop_loader"
                        ),

                        new SimplePreparableReloadListener<
                                Map<Identifier, LoadedItemConfig>
                                >() {

                            @Override
                            protected Map<Identifier, LoadedItemConfig> prepare(
                                    ResourceManager manager,
                                    ProfilerFiller profiler
                            ) {

                                return load(
                                        manager
                                );
                            }


                            @Override
                            protected void apply(
                                    Map<Identifier, LoadedItemConfig> configs,
                                    ResourceManager manager,
                                    ProfilerFiller profiler
                            ) {

                                ModelDelayConfig.clear();

                                ModelDelayHelperClient.clearRuntimeState();


                                for (
                                        Map.Entry<
                                                Identifier,
                                                LoadedItemConfig
                                                > entry
                                        : configs.entrySet()
                                ) {

                                    LoadedItemConfig config =
                                            entry.getValue();


                                    ModelDelayConfig.registerItem(
                                            entry.getKey(),
                                            config.behavior(),
                                            config.properties(),
                                            config.rangeProperties(),
                                            config.selectProperties()
                                    );
                                }
                            }
                        }
                );
    }


    /*
     * ================================================================
     * LOAD
     * ================================================================
     */
    private static Map<
            Identifier,
            LoadedItemConfig
            > load(
            ResourceManager manager
    ) {

        Map<
                Identifier,
                LoadedItemConfig
                > result =
                new HashMap<>();


        Map<Identifier, Resource> resources =
                manager.listResources(
                        RESOURCE_DIRECTORY,
                        id ->
                                id.getPath()
                                        .endsWith(
                                                EXTENSION
                                        )
                );


        for (
                Map.Entry<Identifier, Resource> entry
                : resources.entrySet()
        ) {

            Identifier configId =
                    entry.getKey();


            String path =
                    configId.getPath();


            if (
                    !path.startsWith(
                            RESOURCE_PATH_PREFIX
                    )
            ) {

                continue;
            }


            String itemPathWithExtension =
                    path.substring(
                            RESOURCE_PATH_PREFIX.length()
                    );


            String itemPath =
                    itemPathWithExtension.substring(
                            0,
                            itemPathWithExtension.length()
                                    - EXTENSION.length()
                    );


            Identifier itemId =
                    Identifier.fromNamespaceAndPath(
                            configId.getNamespace(),
                            itemPath
                    );


            LoadedItemConfig config =
                    loadFile(
                            entry.getValue(),
                            configId.getNamespace()
                    );


            if (
                    !config.properties().isEmpty()

                            || !config.rangeProperties().isEmpty()

                            || !config.selectProperties().isEmpty()
            ) {

                result.put(
                        itemId,
                        config
                );
            }
        }


        return result;
    }


    /*
     * ================================================================
     * LOAD FILE
     * ================================================================
     */
    private static LoadedItemConfig loadFile(
            Resource resource,
            String resourceNamespace
    ) {

        Map<
                String,
                ModelDelayConfig.DelayConfig
                > configs =
                new HashMap<>();


        Map<
                String,
                ModelDelayConfig.RangeDelayConfig
                > rangeConfigs =
                new HashMap<>();


        Map<
                String,
                ModelDelayConfig.SelectDelayConfig
                > selectConfigs =
                new HashMap<>();


        Properties properties =
                new Properties();


        ModelDelayConfig.Behavior itemBehavior =
                ModelDelayConfig.Behavior.NORMAL;


        try (
                InputStream stream =
                        resource.open();

                InputStreamReader reader =
                        new InputStreamReader(
                                stream,
                                StandardCharsets.UTF_8
                        )
        ) {

            properties.load(
                    reader
            );


            String itemBehaviorString =
                    properties.getProperty(
                                    "behavior",
                                    "normal"
                            )
                            .trim()
                            .toLowerCase();


            switch (
                    itemBehaviorString
            ) {

                case "evolving" ->
                        itemBehavior =
                                ModelDelayConfig.Behavior.EVOLVING;


                default ->
                        itemBehavior =
                                ModelDelayConfig.Behavior.NORMAL;
            }


            for (
                    String key
                    : properties.stringPropertyNames()
            ) {

                if (
                        !key.endsWith(
                                ".delay"
                        )
                ) {

                    continue;
                }


                String propertyName =
                        key.substring(
                                0,
                                key.length()
                                        - ".delay".length()
                        );


                int delay;


                try {

                    delay =
                            Integer.parseInt(
                                    properties
                                            .getProperty(
                                                    key
                                            )
                                            .trim()
                            );

                } catch (
                        NumberFormatException e
                ) {

                    continue;
                }


                ModelDelayConfig.TransitionEffectConfig effectConfig =
                        parseTransitionEffect(
                                properties,
                                propertyName,
                                resourceNamespace
                        );

                ModelDelayConfig.TransitionSoundConfig soundConfig =
                        parseTransitionSound(
                                properties,
                                propertyName,
                                resourceNamespace
                        );

                /*
                 * ====================================================
                 * SELECT
                 * ====================================================
                 */
                if (
                        propertyName.startsWith(
                                SELECT_PREFIX
                        )
                ) {

                    selectConfigs.put(
                            propertyName,
                            new ModelDelayConfig.SelectDelayConfig(
                                    delay,
                                    effectConfig,
                                    soundConfig
                            )
                    );


                    continue;
                }


                /*
                 * ====================================================
                 * RANGE
                 * ====================================================
                 */
                if (
                        propertyName.startsWith(
                                RANGE_PREFIX
                        )
                ) {

                    String rangeBehaviorString =
                            properties.getProperty(
                                            propertyName
                                                    + ".behavior",
                                            "threshold"
                                    )
                                    .trim()
                                    .toLowerCase();


                    ModelDelayConfig.RangeBehavior rangeBehavior =
                            switch (
                                    rangeBehaviorString
                                    ) {

                                case "value" ->
                                        ModelDelayConfig.RangeBehavior.VALUE;


                                default ->
                                        ModelDelayConfig.RangeBehavior.THRESHOLD;
                            };


                    rangeConfigs.put(
                            propertyName,
                            new ModelDelayConfig.RangeDelayConfig(
                                    delay,
                                    rangeBehavior,
                                    effectConfig,
                                    soundConfig
                            )
                    );


                    continue;
                }


                /*
                 * ====================================================
                 * CONDITIONAL
                 * ====================================================
                 */
                String modeString =
                        properties.getProperty(
                                        propertyName
                                                + ".mode",
                                        "release"
                                )
                                .trim()
                                .toLowerCase();


                ModelDelayConfig.Mode mode =
                        switch (
                                modeString
                                ) {

                            case "hold" ->
                                    ModelDelayConfig.Mode.HOLD;


                            case "both" ->
                                    ModelDelayConfig.Mode.BOTH;


                            default ->
                                    ModelDelayConfig.Mode.RELEASE;
                        };


                String propertyBehaviorString =
                        properties.getProperty(
                                        propertyName
                                                + ".behavior",
                                        "normal"
                                )
                                .trim()
                                .toLowerCase();


                ModelDelayConfig.PropertyBehavior propertyBehavior =
                        switch (
                                propertyBehaviorString
                                ) {

                            case "held" ->
                                    ModelDelayConfig.PropertyBehavior.HELD;


                            default ->
                                    ModelDelayConfig.PropertyBehavior.NORMAL;
                        };


                configs.put(
                        propertyName,
                        new ModelDelayConfig.DelayConfig(
                                delay,
                                mode,
                                propertyBehavior,
                                effectConfig,
                                soundConfig
                        )
                );
            }

        } catch (
                IOException e
        ) {

            e.printStackTrace();
        }


        return new LoadedItemConfig(
                itemBehavior,
                configs,
                rangeConfigs,
                selectConfigs
        );
    }


    /*
     * ================================================================
     * TRANSITION EFFECT
     * ================================================================
     */
    private static ModelDelayConfig.TransitionEffectConfig
    parseTransitionEffect(
            Properties properties,
            String propertyName,
            String resourceNamespace
    ) {

        String effectString =
                properties.getProperty(
                                propertyName
                                        + ".effect",
                                "none"
                        )
                        .trim()
                        .toLowerCase();


        ModelDelayConfig.TransitionEffect effect =
                switch (
                        effectString
                        ) {

                    case "poof" ->
                            ModelDelayConfig.TransitionEffect.POOF;


                    default ->
                            ModelDelayConfig.TransitionEffect.NONE;
                };


        if (
                effect
                        == ModelDelayConfig.TransitionEffect.NONE
        ) {

            return ModelDelayConfig
                    .TransitionEffectConfig
                    .none();
        }


        /*
         * ============================================================
         * TRIGGER
         * ================================================================
         */
        String triggerString =
                properties.getProperty(
                                propertyName
                                        + ".effect.trigger",
                                "change"
                        )
                        .trim()
                        .toLowerCase();


        ModelDelayConfig.TransitionEffectTrigger trigger =
                switch (
                        triggerString
                        ) {

                    case "delayed" ->
                            ModelDelayConfig.TransitionEffectTrigger.DELAYED;


                    default ->
                            ModelDelayConfig.TransitionEffectTrigger.CHANGE;
                };


        int frames =
                parsePositiveInt(
                        properties,
                        propertyName
                                + ".effect.frames",
                        ModelDelayConfig
                                .TransitionEffectConfig
                                .DEFAULT_FRAMES
                );


        int duration =
                parsePositiveInt(
                        properties,
                        propertyName
                                + ".effect.duration",
                        ModelDelayConfig
                                .TransitionEffectConfig
                                .DEFAULT_DURATION
                );


        int count =
                parsePositiveInt(
                        properties,
                        propertyName
                                + ".effect.count",
                        ModelDelayConfig
                                .TransitionEffectConfig
                                .DEFAULT_COUNT
                );


        count =
                Math.min(
                        count,
                        128
                );


        float size =
                parseNonNegativeFloat(
                        properties,
                        propertyName
                                + ".effect.size",
                        ModelDelayConfig
                                .TransitionEffectConfig
                                .DEFAULT_SIZE
                );




        /*
         * ============================================================
         * SYMMETRIC RADIUS
         * ============================================================
         */
        Radius radius =
                parseRadius(
                        properties,
                        propertyName
                                + ".effect.radius",
                        ModelDelayConfig
                                .TransitionEffectConfig
                                .DEFAULT_RADIUS
                );


        /*
         * ============================================================
         * DIRECTIONAL RADIUS
         * ============================================================
         *
         * Both must be present and valid.
         *
         * Otherwise we fall back to the existing symmetric radius.
         */
        RadiusBounds radiusBounds =
                parseRadiusBounds(
                        properties,
                        propertyName
                                + ".effect.radius_min",
                        propertyName
                                + ".effect.radius_max"
                );


        float spread =
                parseNonNegativeFloat(
                        properties,
                        propertyName
                                + ".effect.spread",
                        ModelDelayConfig
                                .TransitionEffectConfig
                                .DEFAULT_SPREAD
                );


        float stagger =
                parseNonNegativeFloat(
                        properties,
                        propertyName
                                + ".effect.stagger",
                        ModelDelayConfig
                                .TransitionEffectConfig
                                .DEFAULT_STAGGER
                );


        stagger =
                Math.min(
                        stagger,
                        0.95F
                );


        /*
         * ============================================================
         * TEXTURE
         * ============================================================
         */
        Identifier texture =
                ModelDelayConfig
                        .TransitionEffectConfig
                        .DEFAULT_TEXTURE;


        String textureString =
                properties.getProperty(
                        propertyName
                                + ".effect.texture"
                );


        if (
                textureString != null

                        && !textureString
                        .trim()
                        .isEmpty()
        ) {

            String texturePath =
                    textureString.trim();


            if (
                    !texturePath.startsWith(
                            "textures/"
                    )
            ) {

                texturePath =
                        "textures/"
                                + texturePath;
            }


            if (
                    !texturePath.endsWith(
                            ".png"
                    )
            ) {

                texturePath =
                        texturePath
                                + ".png";
            }


            try {

                texture =
                        Identifier.fromNamespaceAndPath(
                                resourceNamespace,
                                texturePath
                        );

            } catch (
                    Exception ignored
            ) {
            }
        }


        /*
         * ============================================================
         * ORIGIN
         * ============================================================
         */
        float originX =
                0.0F;


        float originY =
                0.0F;


        float originZ =
                0.0F;


        String originString =
                properties.getProperty(
                        propertyName
                                + ".effect.origin"
                );


        if (
                originString != null
        ) {

            float[] origin =
                    parseVector3(
                            originString
                    );


            if (
                    origin != null
            ) {

                originX =
                        origin[0];


                originY =
                        origin[1];


                originZ =
                        origin[2];
            }
        }


        return new ModelDelayConfig.TransitionEffectConfig(
                effect,
                trigger,
                texture,
                frames,
                duration,
                count,
                size,

                radius.x(),
                radius.y(),
                radius.z(),

                radiusBounds.enabled(),

                radiusBounds.minX(),
                radiusBounds.minY(),
                radiusBounds.minZ(),

                radiusBounds.maxX(),
                radiusBounds.maxY(),
                radiusBounds.maxZ(),

                spread,
                stagger,

                originX,
                originY,
                originZ
        );
    }


    /*
     * ================================================================
     * PARSE SYMMETRIC RADIUS
     * ================================================================
     */

    /*
     * ================================================================
     * TRANSITION SOUND
     * ================================================================
     */
    private static ModelDelayConfig.TransitionSoundConfig
    parseTransitionSound(
            Properties properties,
            String propertyName,
            String resourceNamespace
    ) {

        /*
         * ============================================================
         * SOUND PATHS
         * ============================================================
         *
         * Example:
         *
         * keybind_down.sound=
         *     block/amethyst/break1,
         *     block/amethyst/break2,
         *     block/amethyst/break3
         *
         *
         * These become:
         *
         * <mdprop namespace>:block/amethyst/break1
         * <mdprop namespace>:block/amethyst/break2
         * <mdprop namespace>:block/amethyst/break3
         *
         *
         * The Sound class will later resolve those identifiers to:
         *
         * sounds/block/amethyst/break1.ogg
         *
         * etc.
         */
        String soundString =
                properties.getProperty(
                        propertyName
                                + ".sound"
                );


        if (
                soundString == null

                        || soundString
                        .trim()
                        .isEmpty()
        ) {

            return ModelDelayConfig
                    .TransitionSoundConfig
                    .none();
        }


        String[] soundParts =
                soundString.split(
                        ","
                );


        java.util.ArrayList<Identifier> sounds =
                new java.util.ArrayList<>();


        for (
                String soundPart
                : soundParts
        ) {

            String soundPath =
                    soundPart.trim();


            if (
                    soundPath.isEmpty()
            ) {

                continue;
            }


            /*
             * Allow people to accidentally include:
             *
             *     sounds/
             *
             * or:
             *
             *     .ogg
             *
             * without turning that into:
             *
             *     sounds/sounds/foo.ogg.ogg
             *
             * The documented syntax remains:
             *
             *     block/amethyst/break4
             */
            if (
                    soundPath.startsWith(
                            "sounds/"
                    )
            ) {

                soundPath =
                        soundPath.substring(
                                "sounds/".length()
                        );
            }


            if (
                    soundPath.endsWith(
                            ".ogg"
                    )
            ) {

                soundPath =
                        soundPath.substring(
                                0,
                                soundPath.length()
                                        - ".ogg".length()
                        );
            }


            try {

                sounds.add(
                        Identifier.fromNamespaceAndPath(
                                resourceNamespace,
                                soundPath
                        )
                );

            } catch (
                    Exception ignored
            ) {
            }
        }


        if (
                sounds.isEmpty()
        ) {

            return ModelDelayConfig
                    .TransitionSoundConfig
                    .none();
        }


        /*
         * ============================================================
         * TRIGGER
         * ============================================================
         */
        String triggerString =
                properties.getProperty(
                                propertyName
                                        + ".sound.trigger",
                                "change"
                        )
                        .trim()
                        .toLowerCase();


        ModelDelayConfig.TransitionSoundTrigger trigger =
                switch (
                        triggerString
                        ) {

                    case "delayed" ->
                            ModelDelayConfig
                                    .TransitionSoundTrigger
                                    .DELAYED;


                    default ->
                            ModelDelayConfig
                                    .TransitionSoundTrigger
                                    .CHANGE;
                };


        /*
         * ============================================================
         * VOLUME
         * ============================================================
         */
        float volume =
                parseNonNegativeFloat(
                        properties,
                        propertyName
                                + ".sound.volume",
                        ModelDelayConfig
                                .TransitionSoundConfig
                                .DEFAULT_VOLUME
                );


        /*
         * SoundEngine ultimately clamps normal playback volume anyway,
         * but keeping our config sane makes its behavior predictable.
         */
        volume =
                Math.min(
                        volume,
                        1.0F
                );


        /*
         * ============================================================
         * PITCH
         * ============================================================
         *
         * Simple:
         *
         *     sound.pitch=1.0
         *
         *
         * Advanced:
         *
         *     sound.pitch_min=0.9
         *     sound.pitch_max=1.1
         *
         *
         * If min/max are both present and valid, they override pitch.
         */
        float pitch =
                parseNonNegativeFloat(
                        properties,
                        propertyName
                                + ".sound.pitch",
                        ModelDelayConfig
                                .TransitionSoundConfig
                                .DEFAULT_PITCH
                );


        pitch =
                clampSoundPitch(
                        pitch
                );


        float pitchMin =
                pitch;


        float pitchMax =
                pitch;


        String pitchMinString =
                properties.getProperty(
                        propertyName
                                + ".sound.pitch_min"
                );


        String pitchMaxString =
                properties.getProperty(
                        propertyName
                                + ".sound.pitch_max"
                );


        if (
                pitchMinString != null

                        && pitchMaxString != null
        ) {

            try {

                float parsedMin =
                        Float.parseFloat(
                                pitchMinString.trim()
                        );


                float parsedMax =
                        Float.parseFloat(
                                pitchMaxString.trim()
                        );


                if (
                        Float.isFinite(
                                parsedMin
                        )

                                && Float.isFinite(
                                parsedMax
                        )

                                && parsedMin >= 0.0F

                                && parsedMax >= 0.0F
                ) {

                    parsedMin =
                            clampSoundPitch(
                                    parsedMin
                            );


                    parsedMax =
                            clampSoundPitch(
                                    parsedMax
                            );


                    /*
                     * Be forgiving if the creator accidentally writes:
                     *
                     *     pitch_min=1.1
                     *     pitch_max=0.9
                     *
                     * Rather than disabling sound, normalize the range.
                     */
                    pitchMin =
                            Math.min(
                                    parsedMin,
                                    parsedMax
                            );


                    pitchMax =
                            Math.max(
                                    parsedMin,
                                    parsedMax
                            );
                }

            } catch (
                    NumberFormatException ignored
            ) {
            }
        }


        return new ModelDelayConfig.TransitionSoundConfig(
                sounds.toArray(
                        new Identifier[0]
                ),
                trigger,
                volume,
                pitchMin,
                pitchMax
        );
    }


    /*
     * ================================================================
     * SOUND PITCH
     * ================================================================
     */
    private static float clampSoundPitch(
            float pitch
    ) {

        return Math.max(
                0.5F,
                Math.min(
                        pitch,
                        2.0F
                )
        );
    }

    private static Radius parseRadius(
            Properties properties,
            String key,
            float defaultValue
    ) {

        String string =
                properties.getProperty(
                        key
                );


        if (
                string == null

                        || string
                        .trim()
                        .isEmpty()
        ) {

            return new Radius(
                    defaultValue,
                    defaultValue,
                    defaultValue
            );
        }


        String[] parts =
                string
                        .trim()
                        .split(
                                ","
                        );


        if (
                parts.length == 1
        ) {

            try {

                float value =
                        Float.parseFloat(
                                parts[0]
                                        .trim()
                        );


                if (
                        !Float.isFinite(
                                value
                        )

                                || value < 0.0F
                ) {

                    return new Radius(
                            defaultValue,
                            defaultValue,
                            defaultValue
                    );
                }


                return new Radius(
                        value,
                        value,
                        value
                );

            } catch (
                    NumberFormatException e
            ) {

                return new Radius(
                        defaultValue,
                        defaultValue,
                        defaultValue
                );
            }
        }


        if (
                parts.length == 3
        ) {

            try {

                float x =
                        Float.parseFloat(
                                parts[0]
                                        .trim()
                        );


                float y =
                        Float.parseFloat(
                                parts[1]
                                        .trim()
                        );


                float z =
                        Float.parseFloat(
                                parts[2]
                                        .trim()
                        );


                if (
                        !Float.isFinite(
                                x
                        )

                                || !Float.isFinite(
                                y
                        )

                                || !Float.isFinite(
                                z
                        )

                                || x < 0.0F

                                || y < 0.0F

                                || z < 0.0F
                ) {

                    return new Radius(
                            defaultValue,
                            defaultValue,
                            defaultValue
                    );
                }


                return new Radius(
                        x,
                        y,
                        z
                );

            } catch (
                    NumberFormatException e
            ) {

                return new Radius(
                        defaultValue,
                        defaultValue,
                        defaultValue
                );
            }
        }


        return new Radius(
                defaultValue,
                defaultValue,
                defaultValue
        );
    }


    /*
     * ================================================================
     * PARSE DIRECTIONAL RADIUS
     * ================================================================
     */
    private static RadiusBounds parseRadiusBounds(
            Properties properties,
            String minKey,
            String maxKey
    ) {

        String minString =
                properties.getProperty(
                        minKey
                );


        String maxString =
                properties.getProperty(
                        maxKey
                );


        if (
                minString == null

                        || maxString == null
        ) {

            return disabledRadiusBounds();
        }


        float[] min =
                parseVector3(
                        minString
                );


        float[] max =
                parseVector3(
                        maxString
                );


        if (
                min == null

                        || max == null
        ) {

            return disabledRadiusBounds();
        }


        /*
         * A minimum may be positive and a maximum may be negative.
         *
         * We only require:
         *
         *     min <= max
         *
         * for each individual axis.
         */
        if (
                min[0] > max[0]

                        || min[1] > max[1]

                        || min[2] > max[2]
        ) {

            return disabledRadiusBounds();
        }


        return new RadiusBounds(
                true,

                min[0],
                min[1],
                min[2],

                max[0],
                max[1],
                max[2]
        );
    }


    private static RadiusBounds disabledRadiusBounds() {

        return new RadiusBounds(
                false,

                0.0F,
                0.0F,
                0.0F,

                0.0F,
                0.0F,
                0.0F
        );
    }


    /*
     * ================================================================
     * VECTOR 3
     * ================================================================
     */
    private static float[] parseVector3(
            String string
    ) {

        String[] parts =
                string
                        .trim()
                        .split(
                                ","
                        );


        if (
                parts.length != 3
        ) {

            return null;
        }


        try {

            float x =
                    Float.parseFloat(
                            parts[0]
                                    .trim()
                    );


            float y =
                    Float.parseFloat(
                            parts[1]
                                    .trim()
                    );


            float z =
                    Float.parseFloat(
                            parts[2]
                                    .trim()
                    );


            if (
                    !Float.isFinite(
                            x
                    )

                            || !Float.isFinite(
                            y
                    )

                            || !Float.isFinite(
                            z
                    )
            ) {

                return null;
            }


            return new float[]{
                    x,
                    y,
                    z
            };

        } catch (
                NumberFormatException e
        ) {

            return null;
        }
    }


    /*
     * ================================================================
     * POSITIVE INT
     * ================================================================
     */
    private static int parsePositiveInt(
            Properties properties,
            String key,
            int defaultValue
    ) {

        String string =
                properties.getProperty(
                        key
                );


        if (
                string == null
        ) {

            return defaultValue;
        }


        try {

            int value =
                    Integer.parseInt(
                            string.trim()
                    );


            return value >= 1
                    ? value
                    : defaultValue;

        } catch (
                NumberFormatException e
        ) {

            return defaultValue;
        }
    }


    /*
     * ================================================================
     * NON-NEGATIVE FLOAT
     * ================================================================
     */
    private static float parseNonNegativeFloat(
            Properties properties,
            String key,
            float defaultValue
    ) {

        String string =
                properties.getProperty(
                        key
                );


        if (
                string == null
        ) {

            return defaultValue;
        }


        try {

            float value =
                    Float.parseFloat(
                            string.trim()
                    );


            if (
                    !Float.isFinite(
                            value
                    )

                            || value < 0.0F
            ) {

                return defaultValue;
            }


            return value;

        } catch (
                NumberFormatException e
        ) {

            return defaultValue;
        }
    }
}