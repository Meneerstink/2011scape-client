import com.jagex.Client;
import com.jagex.core.constants.MaxScreenSize;
import org.openrs2.deob.annotation.OriginalMember;

public final class Static133 {

    @OriginalMember(owner = "client!ed", name = "e", descriptor = "F")
    public static float aFloat63;

    @OriginalMember(owner = "client!ed", name = "b", descriptor = "I")
    public static int publicChatFilter = 0;

    @OriginalMember(owner = "client!ed", name = "a", descriptor = "(I)V")
    public static void method2316() {
        RspsGraphicsProfile.apply(ClientOptions.instance);
        Static32.setToolkit(ClientOptions.instance.toolkit.getValue(), false);
        Static296.updateFeatureMask();
        InterfaceManager.loginOpened();
        Client.changingWindowMode = true;
    }
}
