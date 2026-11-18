package ru.orion.client.config;

/** Сохраняемые настройки Animated Lyrics внутри общего конфига клиента. */
public final class GleamLyricsSettings {
    private volatile boolean enabled;
    private String style = "Cinematic";
    private volatile int intensity = 70;
    private float textScale = 1F;
    private volatile int beatSensitivity = 55;
    private int offset;
    private String font = "Poppins";
    private String placement = "Sky";
    private boolean followView;
    private String color = "#F5F3FF";
    private boolean glow = true;
    private boolean shadow = true;
    private int backgroundDim = 18;
    private boolean wordHighlight = true;
    private boolean randomizedTransitions = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getStyle() { return style; }
    public void setStyle(String value) { style = choice(value, "Cinematic", "Dynamic", "Minimal", "Edit"); }
    public int getIntensity() { return intensity; }
    public void setIntensity(int value) { intensity = Math.clamp(value, 0, 100); }
    public float getTextScale() { return textScale; }
    public void setTextScale(float value) { textScale = Float.isFinite(value) ? Math.clamp(value, .5F, 2F) : 1F; }
    public int getBeatSensitivity() { return beatSensitivity; }
    public void setBeatSensitivity(int value) { beatSensitivity = Math.clamp(value, 0, 100); }
    public int getOffset() { return offset; }
    public void setOffset(int value) { offset = Math.clamp(value, -10000, 10000); }
    public String getFont() { return font; }
    public void setFont(String value) { font = choice(value, "Poppins", "Paradox", "Sans", "Serif", "Mono"); }
    public String getPlacement() { return placement; }
    public void setPlacement(String value) { placement = choice(value, "Sky", "Floating"); }
    public boolean isFollowView() { return followView; }
    public void setFollowView(boolean value) { followView = value; }
    public String getColor() { return color; }
    public void setColor(String value) { color = value != null && value.matches("#[0-9a-fA-F]{6}") ? value : "#F5F3FF"; }
    public boolean isGlow() { return glow; }
    public void setGlow(boolean value) { glow = value; }
    public boolean isShadow() { return shadow; }
    public void setShadow(boolean value) { shadow = value; }
    public int getBackgroundDim() { return backgroundDim; }
    public void setBackgroundDim(int value) { backgroundDim = Math.clamp(value, 0, 70); }
    public boolean isWordHighlight() { return wordHighlight; }
    public void setWordHighlight(boolean value) { wordHighlight = value; }
    public boolean isRandomizedTransitions() { return randomizedTransitions; }
    public void setRandomizedTransitions(boolean value) { randomizedTransitions = value; }

    public void sanitize() {
        setStyle(style); setIntensity(intensity); setTextScale(textScale); setBeatSensitivity(beatSensitivity);
        setOffset(offset); setFont(font); setColor(color); setBackgroundDim(backgroundDim); setPlacement(placement);
    }
    private static String choice(String value, String... options) {
        for (String option : options) if (option.equals(value)) return value;
        return options[0];
    }
}
