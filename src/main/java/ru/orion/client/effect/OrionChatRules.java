package ru.orion.client.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import ru.orion.client.config.OrionConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Правила применяются только к отображению входящего чата. */
public final class OrionChatRules {
    private record Span(String text, Style style, int start) { }
    private OrionChatRules() { }

    private static List<String> words(String input) {
        if (input == null || input.isBlank()) return List.of();
        return java.util.Arrays.stream(input.split(","))
                .map(String::trim).filter(word -> !word.isEmpty()).distinct().toList();
    }

    public static boolean shouldHide(Component message, OrionConfig config) {
        if (message == null || config == null || !config.isChatFilterEnabled()) return false;
        String text = message.getString().toLowerCase(Locale.ROOT);
        return words(config.getChatFilterWords()).stream().anyMatch(word -> text.contains(word.toLowerCase(Locale.ROOT)));
    }

    public static Component highlight(Component message, OrionConfig config) {
        if (message == null || config == null || !config.isChatHighlightEnabled()) return message;
        List<String> terms = words(config.getChatHighlightWords());
        if (terms.isEmpty()) return message;
        terms = terms.stream().sorted((a, b) -> Integer.compare(b.length(), a.length())).toList();
        Pattern pattern = Pattern.compile(terms.stream().map(Pattern::quote).collect(java.util.stream.Collectors.joining("|")),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        List<Span> spans = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        message.visit((style, part) -> {
            spans.add(new Span(part, style, text.length()));
            text.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        boolean[] highlighted = new boolean[text.length()];
        Matcher matcher = pattern.matcher(text);
        boolean found = false;
        while (matcher.find()) {
            java.util.Arrays.fill(highlighted, matcher.start(), matcher.end(), true);
            found = true;
        }
        if (!found) return message;
        int color = OrionBreadcrumbs.parseColor(config.getChatHighlightColor()) & 0xFFFFFF;
        MutableComponent result = Component.empty();
        for (Span span : spans) {
            int start = 0;
            while (start < span.text.length()) {
                boolean selected = highlighted[span.start + start];
                int end = start + 1;
                while (end < span.text.length() && highlighted[span.start + end] == selected) end++;
                Style style = selected ? span.style.withColor(color) : span.style;
                result.append(Component.literal(span.text.substring(start, end)).setStyle(style));
                start = end;
            }
        }
        return result;
    }
}
