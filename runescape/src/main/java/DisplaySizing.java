import com.jagex.core.constants.MaxScreenSize;

/** Original rev-667 rendering-area caps. Fullscreen always occupies the monitor. */
final class DisplaySizing {
    static int[] canvas(int width, int height, int maximum, boolean fullscreen) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (!fullscreen) {
            if (maximum == MaxScreenSize._800x600) {
                width = Math.min(width, 800);
                height = Math.min(height, 600);
            } else if (maximum == MaxScreenSize._1024x768) {
                width = Math.min(width, 1024);
                height = Math.min(height, 768);
            }
        }
        return new int[] { width, height };
    }

    private DisplaySizing() { }
}
