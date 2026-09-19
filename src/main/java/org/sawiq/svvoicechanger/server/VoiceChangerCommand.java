package org.sawiq.svvoicechanger.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.protocol.SharedPreset;
import org.sawiq.svvoicechanger.protocol.VoiceSourceRule;

/**
 * {@code /svvoicechanger} — what a moderator uses to run the policy.
 *
 * <p>Muting works on the UUID behind the name, so it applies to an offline
 * player, survives a name change, and cannot be shed by reconnecting. The name
 * is resolved through the vanilla profile argument, which is the same lookup
 * {@code /ban} uses.</p>
 */
public final class VoiceChangerCommand {
    private static final String NAME = "svvoicechanger";
    private static final String ALIAS = "svc";
    private static final String PLAYER_ARGUMENT = "player";
    private static final String RULE_ARGUMENT = "rule";

    private VoiceChangerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal(NAME)
                .requires(ServerPermissions::canCommand)
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("on").executes(context -> setAllowed(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setAllowed(context.getSource(), false)))
                .then(Commands.literal("restrict").then(
                        Commands.argument(RULE_ARGUMENT, StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (VoiceSourceRule rule : VoiceSourceRule.values()) {
                                        builder.suggest(rule.configValue());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(VoiceChangerCommand::setRestriction)))
                .then(Commands.literal("mute").then(Commands.argument(PLAYER_ARGUMENT, GameProfileArgument.gameProfile())
                        .executes(context -> setMuted(context, true))))
                .then(Commands.literal("unmute").then(Commands.argument(PLAYER_ARGUMENT, GameProfileArgument.gameProfile())
                        .executes(context -> setMuted(context, false))))
                .then(Commands.literal("mutelist").executes(context -> muteList(context.getSource())))
                .then(Commands.literal("voices").executes(context -> voices(context.getSource())))
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())));

        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(command);
        dispatcher.register(Commands.literal(ALIAS)
                .requires(ServerPermissions::canCommand)
                .redirect(node));
    }

    // --- Subcommands ---------------------------------------------------------

    private static int status(CommandSourceStack source) {
        VoiceChangerServerRuntime runtime = VoiceChangerServerRuntime.INSTANCE;
        boolean allowed = runtime.config().isAllowed();

        source.sendSuccess(() -> Component.literal("Voice changer: ")
                .append(Component.literal(allowed ? "allowed" : "denied")
                        .withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        source.sendSuccess(() -> info("Voices allowed: "
                + runtime.config().getAllowedVoices().configValue()), false);
        source.sendSuccess(() -> info(runtime.mutes().size() + " player(s) muted, "
                + runtime.presetLibrary().presets().size() + " shared voice(s)"), false);
        source.sendSuccess(() -> info(
                "The voice is changed on the player's machine, so this is a policy an "
                        + "unmodified client follows, not something the server can enforce."), false);
        return 1;
    }

    private static int setAllowed(CommandSourceStack source, boolean allowed) {
        VoiceChangerServerRuntime runtime = VoiceChangerServerRuntime.INSTANCE;
        if (runtime.config().isAllowed() == allowed) {
            source.sendSuccess(() -> info("The voice changer is already "
                    + (allowed ? "allowed" : "denied") + " here."), false);
            return 0;
        }

        try {
            runtime.config().setAllowed(allowed);
        } catch (IOException exception) {
            // The change is live either way; what failed is remembering it.
            source.sendFailure(Component.literal("Applied, but could not write the config: "
                    + describe(exception) + ". It will revert on restart."));
        }

        runtime.broadcastPolicy();
        source.sendSuccess(() -> success("Voice changer " + (allowed ? "allowed" : "denied")
                + " for everyone on this server."), true);
        return 1;
    }

    /**
     * Changing this is a policy change like any other, so every client is told
     * at once rather than finding out the next time they reconnect.
     */
    private static int setRestriction(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String requested = StringArgumentType.getString(context, RULE_ARGUMENT);

        VoiceSourceRule rule = VoiceSourceRule.byConfigValue(requested, null);
        if (rule == null) {
            source.sendFailure(Component.literal("Unknown value " + requested
                    + ". Expected one of: " + String.join(", ", ruleNames())));
            return 0;
        }

        VoiceChangerServerRuntime runtime = VoiceChangerServerRuntime.INSTANCE;
        try {
            runtime.config().setAllowedVoices(rule);
        } catch (IOException exception) {
            // The change is live either way; what failed is remembering it.
            source.sendFailure(Component.literal("Applied, but could not write the config: "
                    + describe(exception) + ". It will revert on restart."));
        }

        runtime.broadcastPolicy();
        source.sendSuccess(() -> success("Voices allowed: " + rule.configValue() + "."), true);
        source.sendSuccess(() -> info(describeRule(rule)), false);
        return 1;
    }

    private static String describeRule(VoiceSourceRule rule) {
        return switch (rule) {
            case ALL -> "Players may tune their own voice freely.";
            case READY_MADE -> "Players may only pick a ready-made voice: built-in, "
                    + "contributed by another mod, or shared by this server.";
            case SERVER_ONLY -> "Players may only pick a voice this server shares.";
        };
    }

    private static List<String> ruleNames() {
        return Arrays.stream(VoiceSourceRule.values()).map(VoiceSourceRule::configValue).toList();
    }

    private static int setMuted(CommandContext<CommandSourceStack> context, boolean muted)
            throws CommandSyntaxException {
        List<CommandTargets.Target> targets = CommandTargets.resolve(context, PLAYER_ARGUMENT);
        VoiceChangerServerRuntime runtime = VoiceChangerServerRuntime.INSTANCE;
        CommandSourceStack source = context.getSource();
        int changed = 0;

        for (CommandTargets.Target target : targets) {
            try {
                boolean applied = muted
                        ? runtime.mutes().mute(target.id(), target.name())
                        : runtime.mutes().unmute(target.id());

                if (!applied) {
                    source.sendSuccess(() -> info(target.name() + " was already "
                            + (muted ? "muted" : "not muted") + "."), false);
                    continue;
                }
            } catch (IOException exception) {
                source.sendFailure(Component.literal("Applied, but could not write the mute list: "
                        + describe(exception) + ". It will revert on restart."));
            }

            runtime.sendPolicyIfPresent(target.id());
            source.sendSuccess(() -> success("Voice changer " + (muted ? "muted" : "unmuted")
                    + " for " + target.name() + "."), true);
            changed++;
        }
        return changed;
    }

    private static int muteList(CommandSourceStack source) {
        List<String> names = VoiceChangerServerRuntime.INSTANCE.mutes().mutedNames();
        if (names.isEmpty()) {
            source.sendSuccess(() -> info("Nobody is muted."), false);
            return 0;
        }

        source.sendSuccess(() -> info("Muted (" + names.size() + "): " + String.join(", ", names)), false);
        return names.size();
    }

    private static int voices(CommandSourceStack source) {
        VoiceChangerServerRuntime runtime = VoiceChangerServerRuntime.INSTANCE;
        if (!runtime.config().isSharePresets()) {
            source.sendSuccess(() -> info("Sharing voices is switched off in the config."), false);
            return 0;
        }

        List<SharedPreset> presets = runtime.presetLibrary().presets();
        if (presets.isEmpty()) {
            source.sendSuccess(() -> info("No shared voices. Put preset files in "
                    + runtime.presetLibrary().directory() + " and run /" + NAME + " reload."), false);
            return 0;
        }

        source.sendSuccess(() -> info("Shared voices (" + presets.size() + "):"), false);
        for (SharedPreset preset : presets) {
            source.sendSuccess(() -> info("  " + preset.id() + " - " + preset.name()), false);
        }
        return presets.size();
    }

    private static int reload(CommandSourceStack source) {
        VoiceChangerServerRuntime runtime = VoiceChangerServerRuntime.INSTANCE;
        try {
            runtime.reload();
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Reload failed: " + describe(exception)));
            return 0;
        }

        source.sendSuccess(() -> success("Reloaded. "
                + runtime.presetLibrary().presets().size() + " shared voice(s), "
                + runtime.mutes().size() + " muted."), true);
        return 1;
    }

    // --- Helpers -------------------------------------------------------------

    private static Component info(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component success(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
