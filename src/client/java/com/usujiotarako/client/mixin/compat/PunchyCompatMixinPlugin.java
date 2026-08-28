package com.usujiotarako.client.mixin.compat;

import net.fabricmc.loader.api.FabricLoader;

import org.objectweb.asm.tree.ClassNode;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class PunchyCompatMixinPlugin
        implements IMixinConfigPlugin {

    private boolean punchyLoaded;


    @Override
    public void onLoad(
            String mixinPackage
    ) {

        punchyLoaded =
                FabricLoader.getInstance()
                        .isModLoaded(
                                "punchy"
                        );
    }


    @Override
    public String getRefMapperConfig() {

        return null;
    }


    @Override
    public boolean shouldApplyMixin(
            String targetClassName,
            String mixinClassName
    ) {

        return punchyLoaded;
    }


    @Override
    public void acceptTargets(
            Set<String> myTargets,
            Set<String> otherTargets
    ) {
    }


    @Override
    public List<String> getMixins() {

        return null;
    }


    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }


    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }
}