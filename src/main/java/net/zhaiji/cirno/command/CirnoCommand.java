package net.zhaiji.cirno.command;

import net.zhaiji.cirno.event.CirnoStateAccess;
import net.zhaiji.cirno.event.CommandCirnoManager;
import net.zhaiji.cirno.event.CompanionLifecycleHandler;
import net.zhaiji.cirno.event.TelekinesisHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.zhaiji.cirno.entity.CirnoEntity;

/**
 * Command system for managing command-mode (no Curios item) Cirno companions.
 *
 * All of the actual spawn/store/remove/liveness logic lives in CommandCirnoManager,
 * shared with the H-key toggle/menu and death handling. This class only parses
 * arguments and sends player-facing messages.
 *
 * Up to {@link CirnoStateAccess#MAX_COMMAND_CIRNOS} independent Cirnos can exist
 * at once, each in its own fixed slot (1..3). A slot is addressed by number
 * everywhere below; /cirno list shows the current state of every slot.
 *
 * This is completely independent from the Curios-equipped "Cirno" companion —
 * that one is still a single instance tracked on the item itself and is untouched
 * by any of this (see CirnoStateAccess's hasCurio()/getCirnoStack() gate).
 */
public class CirnoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cirno")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("spawn", BoolArgumentType.bool())
                                .then(Commands.argument("visible", BoolArgumentType.bool())
                                        .executes(CirnoCommand::executeAdd))))
                .then(Commands.literal("remove")
                        .executes(CirnoCommand::executeRemoveAll)
                        .then(Commands.literal("all").executes(CirnoCommand::executeRemoveAll))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, CirnoStateAccess.MAX_COMMAND_CIRNOS))
                                .executes(CirnoCommand::executeRemoveAt)))
                .then(Commands.literal("toggle")
                        .then(Commands.argument("spawn", BoolArgumentType.bool())
                                .then(Commands.argument("visible", BoolArgumentType.bool())
                                        .executes(CirnoCommand::executeToggleAll)))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, CirnoStateAccess.MAX_COMMAND_CIRNOS))
                                .then(Commands.argument("spawn", BoolArgumentType.bool())
                                        .then(Commands.argument("visible", BoolArgumentType.bool())
                                                .executes(CirnoCommand::executeToggleAt)))))
                .then(Commands.literal("list")
                        .executes(CirnoCommand::executeList))
                .then(Commands.literal("curio")
                        .then(Commands.argument("spawn", BoolArgumentType.bool())
                                .executes(CirnoCommand::executeCurioToggle))));
    }

    // ── /cirno add <spawn> <visible> ─────────────────────────────────────────
    // Always creates a brand-new, independent Cirno in the first free slot —
    // up to MAX_COMMAND_CIRNOS at a time. Unlike the old single-instance command,
    // this never refuses because "one already exists"; it only refuses once all
    // slots are full.

    private static int executeAdd(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context);
        if (player == null) return 0;

        boolean shouldSpawn = BoolArgumentType.getBool(context, "spawn");
        boolean visible     = BoolArgumentType.getBool(context, "visible");

        int result = CommandCirnoManager.addCommandCirno(player, shouldSpawn, visible);
        if (result == -1) {
            player.sendSystemMessage(Component.literal(
                    "§c[Cirno] Limit reached (" + CirnoStateAccess.MAX_COMMAND_CIRNOS
                            + "/" + CirnoStateAccess.MAX_COMMAND_CIRNOS + ")! Remove one first with /cirno remove <index>."));
            return 0;
        }
        if (result == -2) {
            player.sendSystemMessage(Component.literal("§c[Cirno] Failed to create entity!"));
            return 0;
        }

        TelekinesisHandler.applyAttributeBuffs(player);
        TelekinesisHandler.enableTelekinesisMode(player);

        if (shouldSpawn) {
            player.sendSystemMessage(Component.literal(
                    "§b[Cirno] Slot " + result + ": spawned (" + visText(visible) + ")! Attribute buffs and telekinesis granted."));
        } else {
            player.sendSystemMessage(Component.literal(
                    "§b[Cirno] Slot " + result + ": reserved, visibility preference " + visText(visible)));
        }
        return 1;
    }

    // ── /cirno remove [index|all] ─────────────────────────────────────────────

    private static int executeRemoveAt(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context);
        if (player == null) return 0;

        int index = IntegerArgumentType.getInteger(context, "index");
        boolean removed = CommandCirnoManager.removeCommandCirnoAt(player, index);
        if (!removed) {
            player.sendSystemMessage(Component.literal("§c[Cirno] Slot " + index + " is already empty!"));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§b[Cirno] Slot " + index + ": removed!"));
        return 1;
    }

    private static int executeRemoveAll(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context);
        if (player == null) return 0;

        int removed = CommandCirnoManager.removeAllCommandCirno(player);
        if (removed == 0) {
            player.sendSystemMessage(Component.literal("§c[Cirno] No command-spawned Cirno found!"));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§b[Cirno] Removed " + removed + " companion(s)!"));
        return 1;
    }

    // ── /cirno toggle <index> <spawn> <visible> ───────────────────────────────

    private static int executeToggleAt(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context);
        if (player == null) return 0;

        int index           = IntegerArgumentType.getInteger(context, "index");
        boolean shouldSpawn  = BoolArgumentType.getBool(context, "spawn");
        boolean visible      = BoolArgumentType.getBool(context, "visible");

        if (!CommandCirnoManager.isCommandCirnoSlotUsedPublic(player, index) && !shouldSpawn) {
            player.sendSystemMessage(Component.literal("§c[Cirno] Slot " + index + " is empty — use /cirno add to create one."));
            return 0;
        }

        if (shouldSpawn) {
            // Auto-add if this exact slot has never been used before.
            if (!CommandCirnoManager.isCommandCirnoSlotUsedPublic(player, index)) {
                CommandCirnoManager.addCommandCirno(player, false, visible);
            }
            boolean wasLive = CommandCirnoManager.isCommandCirnoSlotLivePublic(player, index);
            CirnoEntity cirno = CommandCirnoManager.spawnOrRestoreCommandCirnoAt(player, index, visible);
            if (cirno == null) {
                player.sendSystemMessage(Component.literal("§c[Cirno] Slot " + index + ": failed to create entity!"));
                return 0;
            }
            player.sendSystemMessage(Component.literal("§b[Cirno] Slot " + index + ": "
                    + (wasLive ? "updated" : "spawned") + " (" + visText(visible) + ")"));
        } else {
            boolean stored = CommandCirnoManager.storeCommandCirnoAt(player, index);
            if (!stored) {
                player.sendSystemMessage(Component.literal("§c[Cirno] Slot " + index + " is already stored!"));
                return 0;
            }
            player.sendSystemMessage(Component.literal(
                    "§b[Cirno] Slot " + index + ": stored (NBT preserved, visibility when restored: " + visText(visible) + ")"));
        }
        return 1;
    }

    // ── /cirno toggle <spawn> <visible> (legacy 2-arg form: applies to ALL slots) ──

    private static int executeToggleAll(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context);
        if (player == null) return 0;

        boolean shouldSpawn = BoolArgumentType.getBool(context, "spawn");
        boolean visible     = BoolArgumentType.getBool(context, "visible");

        if (shouldSpawn) {
            if (CommandCirnoManager.getUsedCommandCirnoSlotCountPublic(player) == 0) {
                // Nobody tracked yet — behave like the old single-instance command
                // and create the first one.
                int result = CommandCirnoManager.addCommandCirno(player, true, visible);
                if (result < 0) {
                    player.sendSystemMessage(Component.literal("§c[Cirno] Failed to create entity!"));
                    return 0;
                }
                TelekinesisHandler.applyAttributeBuffs(player);
                TelekinesisHandler.enableTelekinesisMode(player);
                player.sendSystemMessage(Component.literal("§b[Cirno] Spawned (" + visText(visible) + ")! Attribute buffs and telekinesis granted."));
                return 1;
            }
            int restored = CommandCirnoManager.restoreAllCommandCirno(player);
            TelekinesisHandler.applyAttributeBuffs(player);
            TelekinesisHandler.enableTelekinesisMode(player);
            player.sendSystemMessage(Component.literal(
                    "§b[Cirno] " + restored + " companion(s) summoned (" + visText(visible) + "). Attribute buffs and telekinesis granted."));
        } else {
            CommandCirnoManager.storeAllCommandCirno(player);
            player.sendSystemMessage(Component.literal(
                    "§b[Cirno] All companions stored (NBT preserved, visibility when restored: " + visText(visible) + ")"));
        }
        return 1;
    }

    // ── /cirno list ────────────────────────────────────────────────────────────

    private static int executeList(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context);
        if (player == null) return 0;

        player.sendSystemMessage(Component.literal("§b[Cirno] ── Companion slots ──"));
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            player.sendSystemMessage(Component.literal(
                    "§7Slot " + i + ": §f" + CommandCirnoManager.describeCommandCirnoSlotPublic(player, i)));
        }
        return 1;
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private static int executeCurioToggle(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context);
        if (player == null) return 0;

        boolean spawn = BoolArgumentType.getBool(context, "spawn");
        if (!net.zhaiji.cirno.compat.CuriosCompat.isLoaded()) {
            player.sendSystemMessage(Component.literal("§c[Cirno] Curios is not installed, so there is no Curious Cirno."));
            return 0;
        }
        boolean hasCurio = CirnoStateAccess.hasCurioPublic(player);

        if (!hasCurio) {
            player.sendSystemMessage(Component.literal("§c[Cirno] No Curious Cirno equipped."));
            return 0;
        }

        if (spawn) {
            CompanionLifecycleHandler.summonCurioCompanion(player);
            player.sendSystemMessage(Component.literal("§b[Cirno] Curious Cirno: summoned."));
        } else {
            CompanionLifecycleHandler.storeCurioCompanion(player);
            player.sendSystemMessage(Component.literal("§b[Cirno] Curious Cirno: stored."));
        }
        return 1;
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) return player;
        context.getSource().sendFailure(Component.literal("Only players can use this command"));
        return null;
    }

    private static String visText(boolean visible) {
        return visible ? "visible" : "invisible";
    }
}
