package net.zhaiji.catburger.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side mirror of the telekinesis mode flag.
 * Toggled locally when the keybind fires; the packet tells the server.
 */
@OnlyIn(Dist.CLIENT)
public class ClientTKState {
    private static boolean tkModeActive = false;

    public static boolean isModeActive() {
        return tkModeActive;
    }

    public static void toggle() {
        tkModeActive = !tkModeActive;
    }

    public static void reset() {
        tkModeActive = false;
    }
}
