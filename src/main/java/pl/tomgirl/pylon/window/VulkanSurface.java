package pl.tomgirl.pylon.window;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLProperties.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLVulkan.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memUTF8;

/**
 * A window for a Vulkan renderer, without an OpenGL context (Hestia fork, added 2026-10-18). Install it with
 * {@link DisplaySdl#setSurface} before {@code Display.create}: the window is made as {@link GlSurface} makes its own
 * (same position, size, title and flags), but for Vulkan, and swapping buffers or setting VSync goes to the
 * {@link VulkanPresenter}. The static helpers take and return raw Vulkan handles, so Pylon needs no Vulkan bindings.
 */
public class VulkanSurface implements GpuSurface {
    private final VulkanPresenter presenter;
    private long window;
    private boolean presenterCreated;

    public VulkanSurface(VulkanPresenter presenter) {
        if (presenter == null) {
            throw new NullPointerException("presenter");
        }
        this.presenter = presenter;
    }

    @Override
    public long createWindow(String title, int width, int height, boolean resizable) {
        if (window != 0) {
            throw new IllegalStateException("Surface has already been created");
        }

        MainThread.run(() -> createSdlWindow(title, width, height, resizable, SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED));
        try {
            presenter.windowCreated(window);
            presenterCreated = true;
            return window;
        } catch (RuntimeException | Error throwable) {
            destroy();
            throw throwable;
        }
    }

    /**
     * Hestia fork (2026-10-19): a new window for the presenter ({@link VulkanPresenter#windowReplaced}), shown and raised
     * before the old one is destroyed, so the game keeps the foreground. If the presenter can't move, the old window
     * stays.
     */
    @Override
    public long recreateWindow(String title, int width, int height, boolean resizable, int x, int y) {
        long old = window;
        if (old == 0 || !presenterCreated) {
            return 0;
        }
        MainThread.run(() -> createSdlWindow(title, width, height, resizable, x, y));
        long created = window;
        try {
            presenter.windowReplaced(created);
        } catch (RuntimeException | Error throwable) {
            window = old;
            MainThread.run(() -> SDL_DestroyWindow(created));
            throw throwable;
        }
        MainThread.run(() -> {
            SDL_ShowWindow(created);
            SDL_RaiseWindow(created);
            SDL_DestroyWindow(old);
        });
        return created;
    }

    private void createSdlWindow(String title, int width, int height, boolean resizable, int x, int y) {
        int properties = (int) check(SDL_CreateProperties());
        try {
            check(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_X_NUMBER, x));
            check(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_Y_NUMBER, y));
            check(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER, width));
            check(SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER, height));
            check(SDL_SetStringProperty(properties, SDL_PROP_WINDOW_CREATE_TITLE_STRING, title));
            check(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_VULKAN_BOOLEAN, true));
            check(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_HIDDEN_BOOLEAN, true));
            check(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_RESIZABLE_BOOLEAN, resizable));
            check(SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_HIGH_PIXEL_DENSITY_BOOLEAN, DisplaySdl.instance().isHighPixelDensity()));
            window = check(SDL_CreateWindowWithProperties(properties));
        } finally {
            SDL_DestroyProperties(properties);
        }
    }

    @Override
    public void makeCurrent() {
        presenter.makeCurrent();
    }

    @Override
    public void releaseCurrent() {
        presenter.releaseCurrent();
    }

    @Override
    public void swapBuffers() {
        presenter.present();
    }

    @Override
    public void setVSyncEnabled(boolean enabled) {
        presenter.setVSync(enabled);
    }

    @Override
    public void destroy() {
        if (presenterCreated) {
            presenterCreated = false;
            presenter.destroy();
        }
        if (window != 0) {
            long created = window;
            MainThread.run(() -> SDL_DestroyWindow(created));
            window = 0;
        }
    }

    /** The instance extensions SDL needs for Vulkan surfaces here. SDL's video subsystem must be initialized. */
    public static String[] instanceExtensions() {
        return MainThread.call(() -> {
            try (MemoryStack stack = stackPush()) {
                IntBuffer count = stack.mallocInt(1);
                long names = nSDL_Vulkan_GetInstanceExtensions(memAddress(count));
                if (names == NULL) {
                    throw new IllegalStateException("SDL can't create Vulkan surfaces: " + SDL_GetError());
                }
                PointerBuffer pointers = PointerBuffer.create(names, count.get(0));
                String[] extensions = new String[count.get(0)];
                for (int i = 0; i < extensions.length; i++) {
                    extensions[i] = memUTF8(pointers.get(i));
                }
                return extensions;
            }
        });
    }

    /** Creates a VkSurfaceKHR for an SDL window made for Vulkan; {@code instance} is the VkInstance handle. */
    public static long createSurface(long window, long instance) {
        return MainThread.call(() -> {
            try (MemoryStack stack = stackPush()) {
                LongBuffer surface = stack.mallocLong(1);
                if (!nSDL_Vulkan_CreateSurface(window, instance, NULL, memAddress(surface))) {
                    throw new IllegalStateException("Unable to create a Vulkan surface: " + SDL_GetError());
                }
                return surface.get(0);
            }
        });
    }

    /** Destroys a surface made by {@link #createSurface}. */
    public static void destroySurface(long instance, long surface) {
        MainThread.run(() -> nSDL_Vulkan_DestroySurface(instance, surface, NULL));
    }

    private static void check(boolean success) {
        if (!success) {
            throw new IllegalStateException("SDL error encountered: " + SDL_GetError());
        }
    }

    private static long check(long pointer) {
        if (pointer == 0) {
            throw new IllegalStateException("SDL error encountered: " + SDL_GetError());
        }
        return pointer;
    }
}
