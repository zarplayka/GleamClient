package ru.orion.client.ares;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

final class GleamFreeimage {
    private static final URI ENDPOINT = URI.create("https://freeimage.host/api/1/upload");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private GleamFreeimage() {}

    static final class Failure extends IOException {
        Failure(String message) { super(message); }
    }

    static URI upload(String key, Path screenshot) throws IOException, InterruptedException {
        String boundary = "Gleam" + UUID.randomUUID();
        String head = field(boundary, "key", key) + field(boundary, "action", "upload")
                + field(boundary, "format", "json") + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"source\"; filename=\"screenshot.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        var body = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(head),
                HttpRequest.BodyPublishers.ofFile(screenshot),
                HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        var response = HTTP.send(HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofMinutes(2))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(body).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new Failure("Freeimage.host · HTTP " + response.statusCode());
        return imageUrl(response.body());
    }

    static URI imageUrl(String body) throws IOException {
        try {
            var data = JsonParser.parseString(body).getAsJsonObject();
            int status = data.get("status_code").getAsInt();
            if (status != 200) throw new Failure("Freeimage.host · HTTP " + status);
            URI url = URI.create(data.getAsJsonObject("image").get("url").getAsString());
            String host = url.getHost();
            if (("iili.io".equalsIgnoreCase(host) || "freeimage.host".equalsIgnoreCase(host)
                    || "www.freeimage.host".equalsIgnoreCase(host)) && url.getUserInfo() == null) {
                if ("https".equals(url.getScheme()) && (url.getPort() == -1 || url.getPort() == 443)) return url;
                if ("http".equals(url.getScheme()) && url.getPort() == -1)
                    return URI.create("https" + url.toASCIIString().substring(4));
            }
        } catch (RuntimeException invalid) { throw new Failure("Freeimage.host вернул неожиданный ответ"); }
        throw new Failure("Freeimage.host не вернул ссылку на скриншот");
    }

    private static String field(String boundary, String name, String value) {
        return "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n";
    }
}
