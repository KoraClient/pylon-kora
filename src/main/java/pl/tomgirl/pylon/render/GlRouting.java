package pl.tomgirl.pylon.render;

import java.util.logging.Level;

import pl.tomgirl.pylon.Pylon;

/**
 * Hands the game's and mods' OpenGL calls to another implementation (Hestia fork, added 2026-10-18).
 *
 * <p>A renderer that replaces OpenGL (a Vulkan one, for example) installs a {@link Router} once, before any class that
 * calls OpenGL is loaded. From then on, Pylon's class transformer rewrites the static calls into {@code org/lwjgl/opengl}
 * classes in every class it loads, as the router says: to a method of the same name and descriptor in another class,
 * to nothing, or to a report. Without a router, which is the default, no class changes.
 */
public final class GlRouting {
    /** {@link Router#route} answer: the call does nothing and hands back 0, false or null. */
    public static final String IGNORE = "*ignore";
    /** {@link Router#route} answer: the call hands its name to {@link Router#missing}, then back 0, false or null. */
    public static final String MISSING = "*missing";

    private static volatile Router router;

    private GlRouting() {}

    public interface Router {
        /** Whether the calls in this class (an internal name, such as {@code net/minecraft/client/Minecraft}) are routed. */
        boolean routes(String className);

        /**
         * Where a static call to {@code owner.name(descriptor)} in a routed class goes: the internal name of a class
         * whose public static method of the same name and descriptor takes its place, {@link #IGNORE}, {@link #MISSING},
         * or null to leave the call as it is. Asked while classes load, from any thread.
         */
        String route(String owner, String name, String descriptor);

        /** A {@link #MISSING} call ran. {@code call} names it ({@code GL11.glFoo(I)V}). It may throw. */
        void missing(String call);
    }

    /** Installs the router. Only once, and before the classes whose calls it routes are loaded. */
    public static synchronized void install(Router router) {
        if (router == null) {
            throw new NullPointerException("router");
        }
        if (GlRouting.router != null) {
            throw new IllegalStateException("A GL router is already installed: " + GlRouting.router.getClass().getName());
        }
        GlRouting.router = router;
        Pylon.LOG.log(Level.INFO, "OpenGL calls are routed by {0}", router.getClass().getName());
    }

    public static Router router() {
        return router;
    }

    /** Called by the methods the transformer adds for {@link #MISSING} calls. */
    public static void missing(String call) {
        router.missing(call);
    }

    /** For Pylon's class transformer: the class with its GL calls routed, or the same bytes without a router. */
    public static byte[] routed(byte[] bytecode) {
        Router current = router;
        if (current == null || bytecode == null) {
            return bytecode;
        }
        byte[] routed = GlRouteTransformer.transform(bytecode, current);
        return routed != null ? routed : bytecode;
    }
}
