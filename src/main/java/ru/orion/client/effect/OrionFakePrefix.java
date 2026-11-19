package ru.orion.client.effect;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.PlayerTeam;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Клиентская подмена префикса локального игрока с поддержкой JSON-компонентов. */
public final class OrionFakePrefix {
    public enum ServerPreset {
        REALLY_WORLD("ReallyWorld", List.of("reallyworld.me", "reallyworld.ru", "reallyworld.online", "reallyworld.org", "playrw.ru"),
                List.of(Rank.of("HERO", "#55FF55"), Rank.of("TITAN", "#FFAA00"), Rank.of("DRAGON", "#FF5555"),
                        Rank.of("AVENGER", "#55FFFF"), Rank.of("OVERLORD", "#FF55FF"), Rank.of("MAGISTER", "#A680FF"),
                        Rank.of("IMPERATOR", "#FFC868"), Rank.of("HELPER", "#4DE394"), Rank.of("DIABLO", "#E83E58"),
                        Rank.of("GOD", "#FFE789"))),
        FUN_TIME("FunTime", List.of("funtime.su", "funtime.me"),
                List.of(Rank.of("ГЕРОЙ", "#55FF55"), Rank.of("АСПИД", "#55FFFF"), Rank.of("ТИТАН", "#FFAA00"),
                        Rank.of("БАРОН", "#E0B75F"), Rank.of("СТРАЖ", "#8BD4A9"), Rank.of("СКВИД", "#F68BC7"),
                        Rank.of("ГЛАВА", "#58BCFF"), Rank.of("ЭЛИТА", "#AE87FF"), Rank.of("ПРИНЦ", "#EF717C"),
                        Rank.of("КНЯЗЬ", "#FF9B51"), Rank.of("ГЕРЦОГ", "#FFE789"))),
        HOLY_WORLD("HolyWorld", List.of("holyworld.ru", "holyworld.me"),
                List.of(Rank.of("GLADIATOR", "#FFAA00"), Rank.of("DRAGON", "#FF5555"),
                        Rank.of("HOLY", "#F8E277"), Rank.of("PALADIN", "#71DAFF"), Rank.of("MASTER", "#75E0AE"),
                        Rank.of("OVERLORD", "#FC72C7"), Rank.of("ELDER", "#B38AFF"), Rank.of("PHANTOM", "#7E9AEB"),
                        Rank.of("KING", "#FFB65A"), Rank.of("GRIEFER", "#5DDF8F"), Rank.of("MUSTANG", "#F2AC60"),
                        Rank.of("GHAST", "#E3EDF5"), Rank.of("WITHER", "#A992D6"), Rank.of("KRAKEN", "#54CADA"),
                        Rank.of("STINGER", "#FFB04F"), Rank.of("ETERNITY", "#E284FF"), Rank.of("СТАЖЁР", "#4DE394"),
                        Rank.of("КАСТОМ", "#F9B1FF"), Rank.of("GOLEM", "#D6D2BE"), Rank.of("CREEPER", "#86D86F"),
                        Rank.of("LORD", "#F3B562"), Rank.of("ORACLE", "#73DBE8"), Rank.of("ENDER", "#DB83ED"),
                        Rank.of("BEDROCK", "#ACC4DA"), Rank.of("PRIME", "#F2CA76"), Rank.of("LITE", "#86E6AC"),
                        Rank.of("PLUS", "#77C5F5"), Rank.of("ULTRA", "#ECA8FA"), Rank.of("PLATINUM", "#DAF4FF"))),
        ARES_MINE("AresMine", List.of("aresmine.ru", "aresmine.me"),
                List.of(Rank.ares("ГИДРА", "hydra"), Rank.ares("ЦЕРБЕР", "cerberus"), Rank.ares("ТРИТОН", "triton"),
                        Rank.ares("ФЕНИКС", "phoenix"), Rank.ares("ПАНДАР", "pandar"), Rank.ares("КРОНОС", "kronos"),
                        Rank.ares("ФОБОС", "phobos"), Rank.ares("АРЕС", "ares"), Rank.ares("АРИСТОКРАТ", "aristocrat")));

        private final String label;
        private final List<String> domains;
        private final List<Rank> ranks;

        ServerPreset(String label, List<String> domains, List<Rank> ranks) {
            this.label = label;
            this.domains = domains;
            this.ranks = ranks;
        }

        public String label() { return label; }
        public String[] choices() {
            var options = new String[ranks.size() + 1];
            for (int i = 0; i < ranks.size(); i++) options[i] = ranks.get(i).text();
            options[ranks.size()] = "Ручной";
            return options;
        }

        public int selected(OrionConfig config) {
            int saved = config.getFakePrefixRank(ordinal());
            return saved == OrionConfig.MANUAL_FAKE_PREFIX ? ranks.size() : Math.clamp(saved, 0, ranks.size() - 1);
        }

        public void select(OrionConfig config, int choice) {
            config.setFakePrefixRank(ordinal(), choice == ranks.size() ? OrionConfig.MANUAL_FAKE_PREFIX : choice);
        }

        private boolean includes(String host) {
            return domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        }
    }

    private static final Identifier ARES_DEFAULT_FONT = Identifier.fromNamespaceAndPath("custom", "font/groups/default.json");

    private record Rank(String text, String encoded, Identifier requiredFont) {
        static Rank of(String text, String color) {
            JsonObject value = new JsonObject();
            value.addProperty("text", "[" + text + "] ");
            value.addProperty("color", color);
            return new Rank(text, value.toString(), null);
        }

        static Rank ares(String text, String group) {
            String font = "groups/" + group;
            JsonObject value = new JsonObject();
            value.addProperty("text", "a");
            value.addProperty("font", "custom:" + font);
            value.addProperty("color", "white");
            return new Rank(text, value.toString(), Identifier.fromNamespaceAndPath("custom", "font/" + font + ".json"));
        }

        String component() {
            Minecraft client = Minecraft.getInstance();
            return requiredFont == null || client != null && client.getResourceManager().getResource(requiredFont).isPresent()
                    ? encoded : "";
        }
    }

    private static String cachedSource;
    private static Component cachedComponent = Component.empty();
    private static String cachedPlayerName;
    private static String cachedDetectedPrefix;
    private static Object cachedConnection;

    private OrionFakePrefix() {
    }

    public static String replace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Replacement replacement = replacement(text);
        if (replacement == null || replacement.aresGlyph() || !text.contains(replacement.original())) {
            return text;
        }
        return replacePlainText(text, replacement);
    }

    public static FormattedText replace(FormattedText text) {
        if (text == null) {
            return null;
        }

        String plainText = text.getString();
        Replacement replacement = replacement(plainText);
        if (replacement == null || !plainText.contains(replacement.original())) {
            return text;
        }

        StyledCodepoints source = decode(text);
        int[] prefix = replacement.original().codePoints().toArray();
        List<int[]> playerNames = codepointNames(replacement.playerNames());
        boolean prefixOnlyComponent = allowPrefixWithoutName(plainText, replacement);
        boolean fakeEndsWithWhitespace = endsWithWhitespace(replacement.fake().getString());
        List<FormattedText> parts = new ArrayList<>();
        StringBuilder unchanged = new StringBuilder();
        Style unchangedStyle = null;

        for (int index = 0; index < source.codepoints().size();) {
            Match match = matchAt(source, replacement, prefix, playerNames, index, prefixOnlyComponent, fakeEndsWithWhitespace);
            if (match != null) {
                flush(parts, unchanged, unchangedStyle);
                unchangedStyle = null;
                parts.add(replacement.fake());
                index += match.consumedCodepoints();
                continue;
            }

            Style style = source.styles().get(index);
            if (unchangedStyle != null && !Objects.equals(unchangedStyle, style)) {
                flush(parts, unchanged, unchangedStyle);
                unchangedStyle = null;
            }
            if (unchangedStyle == null) {
                unchangedStyle = style;
            }
            unchanged.appendCodePoint(source.codepoints().getInt(index));
            index++;
        }
        flush(parts, unchanged, unchangedStyle);
        return FormattedText.composite(parts);
    }

    public static Component replace(Component component) {
        if (component == null) {
            return null;
        }
        FormattedText replaced = replace((FormattedText) component);
        if (replaced == component) {
            return component;
        }

        MutableComponent result = Component.empty();
        replaced.visit((style, part) -> {
            result.append(Component.literal(part).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static FormattedCharSequence replace(FormattedCharSequence sequence) {
        if (sequence == null) {
            return null;
        }

        StyledCodepoints source = decode(sequence);
        Replacement replacement = replacement(source.plainText());
        if (replacement == null || !source.plainText().contains(replacement.original())) {
            return sequence;
        }

        StyledCodepoints fake = decode(replacement.fake().getVisualOrderText());
        int[] prefix = replacement.original().codePoints().toArray();
        List<int[]> playerNames = codepointNames(replacement.playerNames());
        boolean prefixOnlyComponent = allowPrefixWithoutName(source.plainText(), replacement);
        boolean fakeEndsWithWhitespace = endsWithWhitespace(replacement.fake().getString());
        IntArrayList outputCodepoints = new IntArrayList(source.codepoints().size());
        List<Style> outputStyles = new ArrayList<>(source.styles().size());

        for (int index = 0; index < source.codepoints().size();) {
            Match match = matchAt(source, replacement, prefix, playerNames, index, prefixOnlyComponent, fakeEndsWithWhitespace);
            if (match != null) {
                outputCodepoints.addAll(fake.codepoints());
                outputStyles.addAll(fake.styles());
                index += match.consumedCodepoints();
            } else {
                outputCodepoints.add(source.codepoints().getInt(index));
                outputStyles.add(source.styles().get(index));
                index++;
            }
        }

        int count = outputCodepoints.size();
        return sink -> {
            for (int index = 0; index < count; index++) {
                if (!sink.accept(index, outputStyles.get(index), outputCodepoints.getInt(index))) {
                    return false;
                }
            }
            return true;
        };
    }

    /** Чат позволяет точно отделить последний донатный компонент от voice-иконки перед ним. */
    public static FormattedCharSequence replaceChat(FormattedCharSequence sequence) {
        if (sequence == null) {
            return null;
        }

        resetDetectionOnServerChange();
        String text = decode(sequence).plainText();
        String playerName = playerName();
        String detected = null;
        for (String name : configuredPlayerNames()) {
            detected = detectPrefixBeforePlayer(text, name);
            if (detected != null) {
                break;
            }
        }
        if (detected != null && isPartOfTeamPrefix(detected)) {
            if (!playerName.equals(cachedPlayerName)) {
                cachedPlayerName = playerName;
            }
            cachedDetectedPrefix = detected;
        }
        return replace(sequence);
    }

    private static String replacePlainText(String text, Replacement replacement) {
        String original = replacement.original();
        List<String> playerNames = replacement.playerNames();
        String fake = replacement.fake().getString();
        boolean prefixOnlyComponent = isStatusLine(text) || isPrefixOnlyComponent(text, original);
        boolean fakeEndsWithWhitespace = endsWithWhitespace(fake);
        StringBuilder result = new StringBuilder(text.length() - original.length() + fake.length());
        int cursor = 0;

        while (cursor < text.length()) {
            int prefixIndex = text.indexOf(original, cursor);
            if (prefixIndex < 0) {
                result.append(text, cursor, text.length());
                break;
            }

            int afterPrefix = prefixIndex + original.length();
            int nameIndex = afterPrefix;
            while (nameIndex < text.length() && Character.isWhitespace(text.charAt(nameIndex))) {
                nameIndex++;
            }
            boolean belongsToPlayer = matchesAnyName(text, nameIndex, playerNames);
            if (!belongsToPlayer && !prefixOnlyComponent) {
                result.append(text, cursor, afterPrefix);
                cursor = afterPrefix;
                continue;
            }

            result.append(text, cursor, prefixIndex).append(fake);
            cursor = belongsToPlayer && fakeEndsWithWhitespace ? nameIndex : afterPrefix;
        }
        return result.toString();
    }

    private static Replacement replacement(String observedText) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isFakePrefixEnabled()) {
            return null;
        }

        resetDetectionOnServerChange();
        String playerName = playerName();
        List<String> playerNames = configuredPlayerNames();
        if (playerName.isBlank() || playerNames.isEmpty()) {
            return null;
        }
        var client = Minecraft.getInstance();
        var server = client == null ? null : client.getCurrentServer();
        boolean aresGlyph = server != null && ServerPreset.ARES_MINE.includes(serverHost(server.ip))
                && client.getResourceManager().getResource(ARES_DEFAULT_FONT).isPresent();
        DetectedPrefix detected = aresGlyph ? null : detectConfiguredPlayerPrefix(observedText, playerNames);
        String original = aresGlyph ? "a" : detected == null ? currentPrefix(observedText, playerName) : detected.prefix();
        if (original == null || original.isBlank()) {
            return null;
        }

        Component fake = parse(activePreset(config).prefix());
        // Одинаковый символ может отображать совершенно разные bitmap-глифы в разных JSON-шрифтах.
        // Поэтому сравнивать исходный и поддельный префиксы только по getString() нельзя.
        if (fake.getString().isEmpty()) {
            return null;
        }
        return new Replacement(original, fake, playerNames, aresGlyph);
    }

    private static DetectedPrefix detectConfiguredPlayerPrefix(String text, List<String> playerNames) {
        for (String playerName : playerNames) {
            String prefix = detectPrefixBeforePlayer(text, playerName);
            if (prefix != null && isPartOfPlayerTeamPrefix(playerName, prefix)) {
                return new DetectedPrefix(prefix, playerName);
            }
        }
        return null;
    }

    private static boolean isPartOfPlayerTeamPrefix(String playerName, String candidate) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return false;
        }
        PlayerInfo info = client.getConnection().getPlayerInfoIgnoreCase(playerName);
        PlayerTeam team = info == null ? null : info.getTeam();
        return team != null && team.getPlayerPrefix().getString().contains(candidate);
    }

    private static String currentPrefix(String observedText, String playerName) {
        if (!playerName.equals(cachedPlayerName)) {
            cachedPlayerName = playerName;
            cachedDetectedPrefix = null;
        }

        if (cachedDetectedPrefix != null
                && (containsPrefixForPlayer(observedText, cachedDetectedPrefix, playerName)
                || isStatusLine(observedText) && observedText.contains(cachedDetectedPrefix))) {
            return cachedDetectedPrefix;
        }

        String detected = detectStatusPrefix(observedText);
        if (detected != null) {
            cachedDetectedPrefix = detected;
            return detected;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return cachedDetectedPrefix;
        }

        PlayerTeam team = client.player.getTeam();
        if (team != null) {
            String teamPrefix = prefixWithoutVoiceChat(team.getPlayerPrefix());
            if (teamPrefix != null) {
                cachedDetectedPrefix = teamPrefix;
                return teamPrefix;
            }
        }

        detected = detectPrefixBeforePlayer(observedText, playerName);
        if (detected != null) {
            cachedDetectedPrefix = detected;
            return detected;
        }
        return cachedDetectedPrefix;
    }

    private static String prefixWithoutVoiceChat(Component component) {
        StringBuilder result = new StringBuilder();
        component.visit((style, part) -> {
            if (!isVoiceChatFont(style.getFont())) {
                result.append(part);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return normalizePrefix(result.toString());
    }

    private static boolean isVoiceChatFont(FontDescription font) {
        if (!(font instanceof FontDescription.Resource resource)) {
            return false;
        }
        String id = resource.id().toString().toLowerCase(Locale.ROOT);
        return id.contains("voicechat") || id.contains("voice_chat") || id.contains("simple_voice");
    }

    private static boolean isPartOfTeamPrefix(String candidate) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.player.getTeam() == null) {
            return true;
        }
        return client.player.getTeam().getPlayerPrefix().getString().contains(candidate);
    }

    private static String detectStatusPrefix(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int labelIndex = lower.indexOf("статус");
        if (labelIndex < 0) {
            labelIndex = lower.indexOf("status");
        }
        if (labelIndex < 0) {
            return null;
        }
        int colonIndex = text.indexOf(':', labelIndex);
        return colonIndex < 0 ? null : normalizePrefix(text.substring(colonIndex + 1));
    }

    private static String detectPrefixBeforePlayer(String text, String playerName) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int nameIndex = indexOfIgnoreCase(text, playerName);
        if (nameIndex <= 0) {
            return null;
        }

        int end = nameIndex;
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }

        String candidate = normalizePrefix(text.substring(start, end));
        if (candidate == null || candidate.endsWith(":") || candidate.endsWith("：")) {
            return null;
        }
        return candidate;
    }

    private static boolean containsPrefixForPlayer(String text, String prefix, String playerName) {
        if (text == null || prefix == null) {
            return false;
        }
        int prefixIndex = text.indexOf(prefix);
        while (prefixIndex >= 0) {
            int nameIndex = prefixIndex + prefix.length();
            while (nameIndex < text.length() && Character.isWhitespace(text.charAt(nameIndex))) {
                nameIndex++;
            }
            if (text.regionMatches(true, nameIndex, playerName, 0, playerName.length())) {
                return true;
            }
            prefixIndex = text.indexOf(prefix, prefixIndex + prefix.length());
        }
        return false;
    }

    private static int indexOfIgnoreCase(String text, String target) {
        int lastStart = text.length() - target.length();
        for (int index = 0; index <= lastStart; index++) {
            if (text.regionMatches(true, index, target, 0, target.length())) {
                return index;
            }
        }
        return -1;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return null;
        }
        String normalized = prefix.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String playerName() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.player == null ? "" : client.player.getScoreboardName();
    }

    private static List<String> configuredPlayerNames() {
        List<String> names = new ArrayList<>();
        String ownName = playerName();
        if (!ownName.isBlank()) {
            names.add(ownName);
        }

        OrionConfig config = OrionClient.getConfigOrNull();
        String configured = config == null ? "" : activePreset(config).names();
        for (String candidate : configured.split("[,;\\s]+")) {
            if (!candidate.matches("[A-Za-z0-9_]{1,16}")) {
                continue;
            }
            boolean duplicate = names.stream().anyMatch(existing -> existing.equalsIgnoreCase(candidate));
            if (!duplicate) {
                names.add(candidate);
            }
        }
        return List.copyOf(names);
    }

    /** Порядок имён для клиентского списка администрации. */
    public static List<String> prioritizedPlayerNames() {
        OrionConfig config = OrionClient.getConfigOrNull();
        return config == null || !config.isFakePrefixEnabled() ? List.of() : configuredPlayerNames();
    }

    public static Component configuredPrefixComponent() {
        OrionConfig config = OrionClient.getConfigOrNull();
        return config == null || !config.isFakePrefixEnabled() ? Component.empty() : parse(activePreset(config).prefix());
    }

    private static OrionConfig.FakePrefixPreset activePreset(OrionConfig config) {
        var client = Minecraft.getInstance();
        var server = client == null ? null : client.getCurrentServer();
        return preset(config, server == null ? "" : server.ip);
    }

    static OrionConfig.FakePrefixPreset preset(OrionConfig config, String address) {
        String host = serverHost(address);
        for (ServerPreset server : ServerPreset.values()) {
            if (server.includes(host)) {
                int selected = server.selected(config);
                if (selected == server.ranks.size()) {
                    return new OrionConfig.FakePrefixPreset("", config.getFakePrefix(), config.getFakePrefixAdditionalNames());
                }
                return new OrionConfig.FakePrefixPreset(address, server.ranks.get(selected).component(),
                        config.getFakePrefixAdditionalNames());
            }
        }
        return config.fakePrefixForServer(address);
    }

    private static String serverHost(String address) {
        String host = OrionConfig.normalizeFakePrefixAddress(address);
        int port = host.indexOf(':');
        return port < 0 ? host : host.substring(0, port);
    }

    private static void resetDetectionOnServerChange() {
        var client = Minecraft.getInstance();
        var connection = client == null ? null : client.getConnection();
        if (connection != cachedConnection) {
            cachedConnection = connection;
            cachedPlayerName = null;
            cachedDetectedPrefix = null;
        }
    }

    private static boolean matchesAnyName(String text, int index, List<String> playerNames) {
        for (String playerName : playerNames) {
            int end = index + playerName.length();
            if (text.regionMatches(true, index, playerName, 0, playerName.length())
                    && (end == text.length() || !isNameCharacter(text.charAt(end)))) {
                return true;
            }
        }
        return false;
    }

    private static List<int[]> codepointNames(List<String> playerNames) {
        return playerNames.stream().map(name -> name.codePoints().toArray()).toList();
    }

    private static Component parse(String source) {
        String safeSource = source == null ? "" : source;
        if (safeSource.equals(cachedSource)) {
            return cachedComponent;
        }

        Component parsed = null;
        try {
            parsed = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(safeSource))
                    .result()
                    .orElse(null);
        } catch (RuntimeException ignored) {
            // Обычный текст и незавершённый JSON во время ввода отображаются буквально.
        }
        cachedSource = safeSource;
        cachedComponent = parsed == null ? Component.literal(safeSource) : parsed;
        return cachedComponent;
    }

    private static StyledCodepoints decode(FormattedText text) {
        IntArrayList codepoints = new IntArrayList();
        List<Style> styles = new ArrayList<>();
        StringBuilder plainText = new StringBuilder();
        text.visit((style, part) -> {
            part.codePoints().forEach(codepoint -> {
                codepoints.add(codepoint);
                styles.add(style);
                plainText.appendCodePoint(codepoint);
            });
            return Optional.empty();
        }, Style.EMPTY);
        return new StyledCodepoints(codepoints, styles, plainText.toString());
    }

    private static StyledCodepoints decode(FormattedCharSequence sequence) {
        IntArrayList codepoints = new IntArrayList();
        List<Style> styles = new ArrayList<>();
        StringBuilder plainText = new StringBuilder();
        sequence.accept((index, style, codepoint) -> {
            codepoints.add(codepoint);
            styles.add(style);
            plainText.appendCodePoint(codepoint);
            return true;
        });
        return new StyledCodepoints(codepoints, styles, plainText.toString());
    }

    private static Match matchAt(StyledCodepoints styled, Replacement replacement, int[] prefix, List<int[]> playerNames, int index,
                                 boolean prefixOnlyComponent, boolean fakeEndsWithWhitespace) {
        if (replacement.aresGlyph()) {
            var font = styled.styles().get(index).getFont();
            if (!(font instanceof FontDescription.Resource resource)
                    || !resource.id().getNamespace().equals("custom")
                    || !resource.id().getPath().startsWith("groups/")) return null;
        }
        IntArrayList source = styled.codepoints();
        if (!matchesAt(source, prefix, index)) {
            return null;
        }

        int afterPrefix = index + prefix.length;
        int nameIndex = afterPrefix;
        while (nameIndex < source.size() && Character.isWhitespace(source.getInt(nameIndex))) {
            nameIndex++;
        }
        if (matchesAnyName(source, playerNames, nameIndex)) {
            int consumedWhitespace = fakeEndsWithWhitespace ? nameIndex - afterPrefix : 0;
            return new Match(prefix.length + consumedWhitespace);
        }
        return prefixOnlyComponent ? new Match(prefix.length) : null;
    }

    private static boolean matchesAnyName(IntArrayList source, List<int[]> playerNames, int index) {
        for (int[] playerName : playerNames) {
            int end = index + playerName.length;
            if (matchesAtIgnoreCase(source, playerName, index)
                    && (end == source.size() || !isNameCharacter(source.getInt(end)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNameCharacter(int codepoint) {
        return codepoint >= 'a' && codepoint <= 'z' || codepoint >= 'A' && codepoint <= 'Z'
                || codepoint >= '0' && codepoint <= '9' || codepoint == '_';
    }

    private static boolean matchesAt(IntArrayList source, int[] target, int index) {
        if (target.length == 0 || index < 0 || index + target.length > source.size()) {
            return false;
        }
        for (int offset = 0; offset < target.length; offset++) {
            if (source.getInt(index + offset) != target[offset]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAtIgnoreCase(IntArrayList source, int[] target, int index) {
        if (target.length == 0 || index < 0 || index + target.length > source.size()) {
            return false;
        }
        for (int offset = 0; offset < target.length; offset++) {
            if (Character.toLowerCase(source.getInt(index + offset)) != Character.toLowerCase(target[offset])) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsWithWhitespace(String text) {
        return !text.isEmpty() && Character.isWhitespace(text.codePointBefore(text.length()));
    }

    private static boolean isStatusLine(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return (lower.contains("статус") || lower.contains("status")) && text.indexOf(':') >= 0;
    }

    private static boolean isPrefixOnlyComponent(String text, String prefix) {
        return text != null && prefix != null && text.strip().equals(prefix);
    }

    private static boolean allowPrefixWithoutName(String text, Replacement replacement) {
        return isStatusLine(text) || !replacement.aresGlyph() && isPrefixOnlyComponent(text, replacement.original());
    }

    private static void flush(List<FormattedText> parts, StringBuilder text, Style style) {
        if (!text.isEmpty() && style != null) {
            parts.add(FormattedText.of(text.toString(), style));
            text.setLength(0);
        }
    }

    private record Replacement(String original, Component fake, List<String> playerNames, boolean aresGlyph) {
    }

    private record DetectedPrefix(String prefix, String playerName) {
    }

    private record Match(int consumedCodepoints) {
    }

    private record StyledCodepoints(IntArrayList codepoints, List<Style> styles, String plainText) {
    }
}
