package pl.tomgirl.pylon.window;

public interface GpuSurface {
    long createWindow(String title, int width, int height, boolean resizable);
    void makeCurrent();
    void releaseCurrent();
    void swapBuffers();
    void setVSyncEnabled(boolean enabled);
    void destroy();

    /**
     * Hestia fork (2026-10-19): makes the window again, with the same title, size and flags at {@code x}, {@code y},
     * shows and raises it, and destroys the old one. The new window's handle, or 0 when this surface can't.
     */
    default long recreateWindow(String title, int width, int height, boolean resizable, int x, int y) {
        return 0;
    }
}
