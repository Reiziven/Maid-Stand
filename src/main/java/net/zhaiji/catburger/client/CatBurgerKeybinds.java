package net.zhaiji.catburger.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class CatBurgerKeybinds {

    public static final String CATEGORY = "key.category.catburger";

    /** Toggle companion visibility (stand/invisible mode). */
    public static final KeyMapping TOGGLE_COMPANION_VISIBLE = new KeyMapping(
            "key.catburger.toggle_companion_visible",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    /** Toggle active telekinesis control mode (right-click mob to control). */
    public static final KeyMapping TOGGLE_TELEKINESIS_MODE = new KeyMapping(
            "key.catburger.toggle_telekinesis_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    /** Open Cirno's maid inventory/task GUI. */
    public static final KeyMapping OPEN_CIRNO_GUI = new KeyMapping(
            "key.catburger.open_cirno_gui",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_COMPANION_VISIBLE);
        event.register(TOGGLE_TELEKINESIS_MODE);
        event.register(OPEN_CIRNO_GUI);
    }
}
