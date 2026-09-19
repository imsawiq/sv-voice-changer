package org.sawiq.svvoicechanger.server;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;

//? if >=1.21.9 {
/*import net.minecraft.server.players.NameAndId;
*///?} else {
import com.mojang.authlib.GameProfile;
//?}

/**
 * Resolves a player name to something the mute list can act on.
 *
 * <p>Uses the vanilla profile argument, which is the same lookup {@code /ban}
 * does, so it finds offline players and accepts selectors. What it hands back
 * is a plain id and name, because Minecraft 1.21.9 replaced the profile type
 * this argument returns and the rest of the mod has no reason to care.</p>
 */
public final class CommandTargets {
    /** A player to act on, whether or not they are online. */
    public record Target(UUID id, String name) {
    }

    private CommandTargets() {
    }

    public static List<Target> resolve(CommandContext<CommandSourceStack> context, String argument)
            throws CommandSyntaxException {
        List<Target> targets = new ArrayList<>();

        //? if >=1.21.9 {
        /*for (NameAndId profile : GameProfileArgument.getGameProfiles(context, argument)) {
            targets.add(new Target(profile.id(), profile.name()));
        }
        *///?} else {
        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, argument)) {
            targets.add(new Target(profile.getId(), profile.getName()));
        }
        //?}

        return targets;
    }
}
