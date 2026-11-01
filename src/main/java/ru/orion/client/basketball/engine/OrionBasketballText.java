package ru.orion.client.basketball.engine;

/** Сообщения игрового движка в интерфейсе Orion. */
public final class OrionBasketballText {
    private OrionBasketballText() { }
    public static String get(String key) {
        return switch(key.substring(key.lastIndexOf('.')+1)) {
            case "myach_daleko_podoydi_k" -> "Подойдите ближе к мячу";
            case "nekuda_stavit_kolco_nuzhen" -> "Посмотрите на поверхность для установки кольца";
            case "kolco_postavleno" -> "Кольцо установлено";
            case "chisto" -> "Чистое попадание!";
            case "est" -> "Попадание!";
            case "myach_uletel_za_predely" -> "Мяч возвращён на площадку";
            default -> "Баскетбол";
        };
    }
    public static String format(String key,Object... arguments) {
        if(key.endsWith("myach_u_s_dozhdis_pasa"))return "Мяч у "+(arguments.length==0?"игрока":arguments[0])+". Дождитесь паса";
        return get(key)+(arguments.length==0?"":" "+java.util.Arrays.toString(arguments));
    }
}
