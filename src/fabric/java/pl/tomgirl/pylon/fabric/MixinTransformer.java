package pl.tomgirl.pylon.fabric;

import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.transformers.TreeTransformer;
import pl.tomgirl.pylon.game.GameTransformer;
import pl.tomgirl.pylon.render.GlRouting;

public class MixinTransformer<T extends TreeTransformer & IMixinTransformer> extends MixinTransformerDelegate<T> {
    MixinTransformer(T delegate) throws Exception {
        this.delegate = delegate;
    }

    @Override
    public byte[] transformClassBytes(String name, String transformedName, byte[] bytecode) {
        if (bytecode == null) {
            return super.transformClassBytes(name, transformedName, null);
        }

        if (name.startsWith("pl.tomgirl.pylon")) {
            return super.transformClassBytes(name, transformedName, bytecode);
        }

        byte[] transformed = GameTransformer.transform(bytecode);
        if (transformed != null) {
            bytecode = transformed;
        }

        // Hestia fork (2026-10-18): GL calls routed after the mixins, so the code they merge in is routed too. Without an
        // installed router the bytes are the mixins' unchanged.
        return GlRouting.routed(super.transformClassBytes(name, transformedName, bytecode));
    }
}