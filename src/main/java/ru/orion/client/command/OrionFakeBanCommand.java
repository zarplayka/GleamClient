package ru.orion.client.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import ru.orion.client.config.OrionAccounts;

import java.util.concurrent.CompletableFuture;

/** Клиентские розыгрыши с имитацией бана или мута. */
public final class OrionFakeBanCommand {
    private static final int ACCENT_COLOR = 0xE1BB4B;
    private static final int TEXT_COLOR = 0xDBD5C3;
    private static final int REASON_MAX_LENGTH = 256;
    private static final String SECOND_LINE_INDENT = "         ";
    private static final ArgumentType<String> UNICODE_WORD = reader -> {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    };
    private static final FontDescription BAN_ICON_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("minecraft", "chat_icons_2")
    );

    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(permanentBanCommand());
            dispatcher.register(temporaryCommand("faketempban", Punishment.BAN));
            dispatcher.register(temporaryCommand("faketempmute", Punishment.MUTE));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> permanentBanCommand() {
        return ClientCommands.literal("fakeban")
                .then(playerArgument("кто")
                        .then(playerArgument("кого")
                                .then(ClientCommands.argument("причина", StringArgumentType.greedyString())
                                        .executes(context -> execute(context, Punishment.BAN, null)))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> temporaryCommand(
            String commandName, Punishment punishment) {
        return ClientCommands.literal(commandName)
                .then(playerArgument("кто")
                        .then(playerArgument("кого")
                                .then(ClientCommands.argument("время", UNICODE_WORD)
                                        .then(ClientCommands.argument("причина", StringArgumentType.greedyString())
                                                .executes(context -> execute(
                                                        context,
                                                        punishment,
                                                        context.getArgument("время", String.class)
                                                ))))));
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> playerArgument(String name) {
        return ClientCommands.argument(name, StringArgumentType.word()).suggests(OrionFakeBanCommand::suggestPlayers);
    }

    private static CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder);
    }

    private static int execute(CommandContext<FabricClientCommandSource> context,
                               Punishment punishment, String duration) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null || client.getConnection() == null) {
            return 0;
        }
        OrionAccounts accounts = OrionAccounts.INSTANCE;
        if (!accounts.plusActive()) {
            feedback(client, "Для этой команды нужна подписка Plus");
            return 0;
        }

        String safeModerator = sanitizeName(StringArgumentType.getString(context, "кто"));
        String safeTarget = sanitizeName(StringArgumentType.getString(context, "кого"));
        String safeDuration = duration == null ? null : sanitizeDuration(duration);
        String safeReason = sanitizeReason(StringArgumentType.getString(context, "причина"));
        if (safeModerator.isEmpty() || safeTarget.isEmpty() || safeReason.isEmpty()
                || duration != null && safeDuration.isEmpty()) {
            return 0;
        }

        String scope = OrionAccounts.fakePunishmentScope(client);
        if (scope.isEmpty()) {
            show(client, punishment, safeModerator, safeTarget, safeDuration, safeReason);
        } else {
            JsonObject event = new JsonObject();
            event.addProperty("scope", scope);
            event.addProperty("kind", punishment == Punishment.BAN ? "ban" : "mute");
            event.addProperty("moderator", safeModerator);
            event.addProperty("target", safeTarget);
            event.addProperty("duration", safeDuration);
            event.addProperty("reason", safeReason);
            var connection = client.getConnection();
            accounts.publishFakePunishment(event).whenComplete((sent, failure) -> client.execute(() -> {
                if (client.getConnection() != connection) return;
                if (failure != null) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    feedback(client, cause instanceof IllegalStateException ? cause.getMessage() : "Сервис розыгрышей недоступен");
                } else if (!sent) {
                    feedback(client, "Для этой команды нужна подписка Plus");
                } else {
                    show(client, punishment, safeModerator, safeTarget, safeDuration, safeReason);
                }
            }));
        }
        return Command.SINGLE_SUCCESS;
    }

    public static void showRemote(Minecraft client, JsonObject event) {
        String author = eventString(event, "author");
        if (author == null || !author.matches("[A-Za-z0-9_]{2,24}")) return;
        String verb = eventString(event, "kind");
        Punishment kind = "ban".equals(verb) ? Punishment.BAN : "mute".equals(verb) ? Punishment.MUTE : null;
        String moderator = sanitizeName(eventString(event, "moderator"));
        String target = sanitizeName(eventString(event, "target"));
        String reason = sanitizeReason(eventString(event, "reason"));
        String rawDuration = eventString(event, "duration");
        String duration = rawDuration == null ? null : sanitizeDuration(rawDuration);
        if (kind == null || moderator.isEmpty() || target.isEmpty() || reason.isEmpty()
                || (duration == null ? kind == Punishment.MUTE : duration.isEmpty())) return;
        show(client, kind, moderator, target, duration, reason);
    }

    private static String eventString(JsonObject event, String field) {
        var value = event.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static void show(Minecraft client, Punishment punishment, String moderator, String target,
                             String duration, String reason) {
        MutableComponent punishmentLine = Component.empty()
                .append(accent(moderator))
                .append(text(punishment.verb))
                .append(accent(target));
        if (duration == null) {
            punishmentLine.append(text(" навсегда:"));
        } else {
            punishmentLine.append(text(" на ")).append(accent(duration)).append(text(":"));
        }

        MutableComponent message = Component.empty()
                .append(Component.literal(" ").append(icon()))
                .append("\n")
                .append(Component.literal(SECOND_LINE_INDENT).append(punishmentLine))
                .append("\n")
                .append(Component.literal(SECOND_LINE_INDENT).append(text(reason)))
                .append("\n");

        client.gui.hud.getChat().addClientSystemMessage(message);
    }

    private static void feedback(Minecraft client, String message) {
        client.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
    }

    private static MutableComponent icon() {
        return Component.literal("e").withStyle(style -> style.withFont(BAN_ICON_FONT));
    }

    private static MutableComponent accent(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(ACCENT_COLOR));
    }

    private static MutableComponent text(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(TEXT_COLOR));
    }

    private static String sanitizeName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
            return "";
        }
        return name;
    }

    private static String sanitizeReason(String reason) {
        if (reason == null) {
            return "";
        }
        String sanitized = reason.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]", " ").trim();
        return sanitized.length() <= REASON_MAX_LENGTH
                ? sanitized
                : sanitized.substring(0, REASON_MAX_LENGTH);
    }

    private static String sanitizeDuration(String duration) {
        if (duration == null) {
            return "";
        }
        String sanitized = duration.replaceAll("(?U)[\\p{Cntrl}\\p{Cf}\\s]", "");
        return sanitized.length() <= 16 ? sanitized : sanitized.substring(0, 16);
    }

    private enum Punishment {
        BAN(" забанил "),
        MUTE(" замутил ");

        private final String verb;

        Punishment(String verb) {
            this.verb = verb;
        }
    }
}
