package net.zhaiji.catburger.network.client.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.client.gui.CirnoQuickSelectScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sent server→client in response to {@link net.zhaiji.catburger.network.server.packet.RequestSlotSyncPacket}.
 * Also sent after any slot action to keep the screen up-to-date.
 *
 * Each entry covers one slot:
 *   index 0  = Curio slot  (hasCurio flag governs whether it's relevant)
 *   index 1–4 = Command slots 1–4
 */
public class SlotSyncPacket {

    /** Possible slot states — mirrors what CommonEventHandler tracks. */
    public enum SlotState { EMPTY, STORED, SUMMONED }

    /**
     * @param modelId    TLM model ID string from {@code EntityMaid#getModelId()} — empty string if unknown
     * @param ysmModelId YSM model ID string (from config / entity data) — empty string if not a YSM model
     */
    public record SlotInfo(int index, boolean isCurio, SlotState state,
                           String modelId, String ysmModelId) {
        /** Backwards-compat constructor for slots with no model info. */
        public SlotInfo(int index, boolean isCurio, SlotState state) {
            this(index, isCurio, state, "", "");
        }
    }

    private final boolean hasCurio;
    private final List<SlotInfo> slots;

    public SlotSyncPacket(boolean hasCurio, List<SlotInfo> slots) {
        this.hasCurio = hasCurio;
        this.slots = slots;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(hasCurio);
        buf.writeVarInt(slots.size());
        for (SlotInfo s : slots) {
            buf.writeVarInt(s.index());
            buf.writeBoolean(s.isCurio());
            buf.writeVarInt(s.state().ordinal());
            buf.writeUtf(s.modelId());
            buf.writeUtf(s.ysmModelId());
        }
    }

    public static SlotSyncPacket decode(FriendlyByteBuf buf) {
        boolean hasCurio = buf.readBoolean();
        int count = buf.readVarInt();
        List<SlotInfo> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int idx = buf.readVarInt();
            boolean isCurio = buf.readBoolean();
            SlotState state = SlotState.values()[buf.readVarInt()];
            String modelId = buf.readUtf();
            String ysmModelId = buf.readUtf();
            slots.add(new SlotInfo(idx, isCurio, state, modelId, ysmModelId));
        }
        return new SlotSyncPacket(hasCurio, slots);
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof CirnoQuickSelectScreen screen) {
                screen.applySync(hasCurio, slots);
            }
        });
        ctx.setPacketHandled(true);
    }

    public boolean hasCurio() { return hasCurio; }
    public List<SlotInfo> getSlots() { return slots; }
}
