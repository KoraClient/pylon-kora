package pl.tomgirl.pylon.window;

/**
 * The renderer behind a {@link VulkanSurface} (Kora fork, added 2026-10-18). Pylon calls it on the thread that
 * created the display, at the points where an OpenGL window would make its context current or swap its buffers.
 */
public interface VulkanPresenter {
    /** The SDL window was created (hidden; Pylon shows it after this returns). Create the Vulkan surface here. */
    void windowCreated(long window);

    /** Where an OpenGL window would make its context current on this thread. */
    void makeCurrent();

    /** Where an OpenGL window would release its context on this thread. */
    void releaseCurrent();

    /** {@code Display.update} / {@code Display.swapBuffers}: present the frame. */
    void present();

    /** {@code Display.setVSyncEnabled}. */
    void setVSync(boolean enabled);

    /** The display is being destroyed; the window still exists. Release everything made for it. */
    void destroy();

    /**
     * Kora fork (2026-10-19): the window is being made again ({@link DisplaySdl#recreateWindow}). Move to
     * {@code window}, a new SDL window made for Vulkan, still hidden; the old one still exists and is destroyed after
     * this returns. Called on the display's thread, between frames.
     */
    default void windowReplaced(long window) {
        throw new UnsupportedOperationException("this presenter can't move to a new window");
    }
}
