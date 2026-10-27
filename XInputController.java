import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

final class XInputController {
    private static final int SUCCESS = 0;
    private static final int USER_COUNT = 4;
    private final XInputApi api;
    private final int[] previousButtons = new int[USER_COUNT];

    private XInputController(XInputApi api) {
        this.api = api;
    }

    static XInputController connect() {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) return null;

        UnsatisfiedLinkError lastError = null;
        for (String library : new String[]{"xinput1_4", "xinput1_3", "xinput9_1_0"}) {
            try {
                return new XInputController(Native.load(library, XInputApi.class));
            } catch (UnsatisfiedLinkError e) {
                lastError = e;
            }
        }
        System.err.println("XInput controller support is unavailable: "
            + (lastError == null ? "no XInput library found." : lastError.getMessage()));
        return null;
    }

    PadState poll() {
        PadState firstConnected = null;
        for (int user = 0; user < USER_COUNT; user++) {
            XInputState state = new XInputState();
            int result = api.XInputGetState(user, state);
            if (result != SUCCESS) {
                previousButtons[user] = 0;
                continue;
            }

            int buttons = Short.toUnsignedInt(state.gamepad.buttons);
            int pressed = buttons & ~previousButtons[user];
            previousButtons[user] = buttons;
            if (firstConnected == null) {
                firstConnected = new PadState(buttons, pressed,
                    state.gamepad.thumbLX / 32768f, state.gamepad.thumbLY / 32768f);
            }
        }
        return firstConnected;
    }

    static final class PadState {
        final int buttons;
        final int pressed;
        final float leftX;
        final float leftY;

        PadState(int buttons, int pressed, float leftX, float leftY) {
            this.buttons = buttons;
            this.pressed = pressed;
            this.leftX = leftX;
            this.leftY = leftY;
        }
    }

    @FunctionalInterface
    private interface XInputApi extends StdCallLibrary {
        int XInputGetState(int userIndex, XInputState state);
    }

    @Structure.FieldOrder({"packetNumber", "gamepad"})
    public static class XInputState extends Structure {
        public int packetNumber;
        public XInputGamepad gamepad = new XInputGamepad();
    }

    @Structure.FieldOrder({"buttons", "leftTrigger", "rightTrigger", "thumbLX", "thumbLY", "thumbRX", "thumbRY"})
    public static class XInputGamepad extends Structure {
        public short buttons;
        public byte leftTrigger;
        public byte rightTrigger;
        public short thumbLX;
        public short thumbLY;
        public short thumbRX;
        public short thumbRY;
    }
}
