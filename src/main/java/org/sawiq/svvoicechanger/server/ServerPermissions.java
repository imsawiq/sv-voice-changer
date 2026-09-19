package org.sawiq.svvoicechanger.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

//? if >=1.21.11 {
/*import net.minecraft.commands.Commands;
*///?}
//? if neoforge {
/*import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.sawiq.svvoicechanger.SvVoiceChanger;
*///?}

/**
 * Who may change their voice, and who may run the command.
 *
 * <p>NeoForge ships a permission API that any permission manager plugs into, so
 * there both questions are real permission nodes. Fabric has no equivalent in
 * the loader or in Fabric API, and the community library that fills the gap is
 * not a dependency this mod takes, so there the command falls back to the
 * vanilla operator level and per-player control is the mute list instead.</p>
 */
public final class ServerPermissions {
    //? if <1.21.11 {
    /** Vanilla level for commands normally reserved to operators. */
    private static final int OPERATOR_LEVEL = 2;
    //?}

    //? if neoforge {
    /*/^* Granted to everyone; a manager can take it away. ^/
    public static final PermissionNode<Boolean> USE = new PermissionNode<>(
            ResourceLocation.parse(SvVoiceChanger.MOD_ID + ":use"),
            PermissionTypes.BOOLEAN,
            (player, playerId, context) -> true);

    /^*
     * Granted to nobody by default, because operators already pass the check
     * that falls back on this one. Its point is letting a manager hand the
     * command to somebody who is not an operator.
     ^/
    public static final PermissionNode<Boolean> COMMAND = new PermissionNode<>(
            ResourceLocation.parse(SvVoiceChanger.MOD_ID + ":command"),
            PermissionTypes.BOOLEAN,
            (player, playerId, context) -> false);
    *///?}

    private ServerPermissions() {
    }

    //? if neoforge {
    /*/^* Subscribed by the common entrypoint so managers can see the nodes. ^/
    public static void gather(PermissionGatherEvent.Nodes event) {
        USE.setInformation(
                Component.literal("Use the voice changer"),
                Component.literal("Whether this player may change their voice on this server."));
        COMMAND.setInformation(
                Component.literal("Voice changer command"),
                Component.literal("Whether this player may run /svvoicechanger without being an operator."));
        event.addNodes(List.of(USE, COMMAND));
    }
    *///?}

    /** Whether this player may change their voice at all. */
    public static boolean canUse(ServerPlayer player) {
        //? if neoforge {
        /*return PermissionAPI.getPermission(player, USE);
        *///?} else {
        // No permission API on Fabric, so nobody is refused on this ground and
        // the mute list is what a moderator uses instead.
        return true;
        //?}
    }

    /** Whether this source may run the moderation command. */
    public static boolean canCommand(CommandSourceStack source) {
        //? if neoforge {
        /*ServerPlayer player = source.getPlayer();
        if (player != null && PermissionAPI.getPermission(player, COMMAND)) {
            return true;
        }
        *///?}
        return isOperator(source);
    }

    /**
     * The vanilla moderator level, however the running version spells it.
     * Minecraft 1.21.11 replaced numeric permission levels with a permission
     * set, so there is no one spelling that works across the supported range.
     */
    private static boolean isOperator(CommandSourceStack source) {
        //? if >=1.21.11 {
        /*return Commands.<CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
        *///?} else {
        return source.hasPermission(OPERATOR_LEVEL);
        //?}
    }
}
