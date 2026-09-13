import com.jagex.graphics.ToolkitType;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

/** A native/driver failure gets one Java fallback, never a retry of the failed renderer. */
public final class GraphicsRendererPolicy {
    private static final Set<Integer> failed = new HashSet<>();

    private GraphicsRendererPolicy() { }

    static int defaultRenderer(boolean signed, boolean openGlAvailable, boolean directXAvailable) {
        if (!signed) return ToolkitType.JAVA;
        if (openGlAvailable) return ToolkitType.GL;
        return ToolkitType.JAVA;
    }

    static boolean isKnown(int renderer) {
        return ToolkitType.isSoftware(renderer) || ToolkitType.isHardware(renderer);
    }

    static int usablePreference(int renderer) {
        return isKnown(renderer) && !failed.contains(renderer) ? renderer : ToolkitType.JAVA;
    }

    static <T> Result<T> create(int requested, IntFunction<T> factory) {
        int renderer = usablePreference(requested);
        try {
            return new Result<>(renderer, factory.apply(renderer));
        } catch (RuntimeException | LinkageError | OutOfMemoryError failure) {
            if (renderer == ToolkitType.JAVA) throw failure;
            failed.add(renderer);
            System.err.println("Graphics renderer " + renderer + " failed ("
                    + failure.getClass().getSimpleName() + "); using Java software rendering.");
            return new Result<>(ToolkitType.JAVA, factory.apply(ToolkitType.JAVA));
        }
    }

    static final class Result<T> {
        final int renderer;
        final T value;
        Result(int renderer, T value) {
            this.renderer = renderer;
            this.value = value;
        }
    }
}
