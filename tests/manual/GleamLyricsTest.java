package ru.orion.client.media;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import ru.orion.client.config.OrionConfig;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Парсинг настоящих форматов, конфиг, HTTP-кеш, отмена и ограничение запросов. */
public final class GleamLyricsTest {
    private static int checks;
    private static void check(boolean value, String message) { if(!value)throw new AssertionError(message);checks++; }
    private static JsonObject record(String lrc) { var value=new JsonObject();value.addProperty("syncedLyrics",lrc);return value; }
    private static void await(GleamLyricsService service, String status) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until) {
            if(service.snapshot().lyrics().status().equals(status))return;
            Thread.sleep(10);
        }
        throw new AssertionError("Expected "+status+", got "+service.snapshot());
    }
    private static GleamLyricsService.Track track(String title) { return new GleamLyricsService.Track("player",title,"Artist","Album",120); }
    public static void main(String[] args) throws Exception {
        var lrc=GleamLyrics.parse(record("[ar:Artist]\n[offset:100]\n[00:01.20][00:05.200]Same line\n[00:06.90]\n[00:07.000]Last"));
        check(lrc.lines().size()==4,"multiple tags and empty cue");
        check(lrc.lines().getFirst().start()==1300,"fraction and embedded offset");
        check(lrc.lines().get(1).start()==5300,"repeated cue");
        check(lrc.lines().get(2).text().isEmpty(),"instrumental gap marker");
        check(lrc.lines().getFirst().words().isEmpty(),"never synthesize word timings");
        check(lrc.lines().getLast().end()==-1,"do not invent vocal end");
        var enhanced=GleamLyrics.parse(record("[00:01.000]<00:01.000>One <00:01.500>two<00:02.000>"));
        check(enhanced.lines().getFirst().words().size()==2,"enhanced LRC words");
        check(enhanced.lines().getFirst().words().getLast().end()==2000,"explicit word end");
        var value=new JsonObject();
        value.addProperty("lyricsfile", "version: '1.0'\nmetadata: {}\nlines:\n- text: 'Новые звёзды'\n  start_ms: 1000\n  end_ms: 2500\n  words:\n  - {text: 'Новые ', start_ms: 1000, end_ms: 1800}\n  - {text: 'звёзды', start_ms: 1800, end_ms: 2500}\n- text: 'After'\n  start_ms: 3000\n");
        var parsed=GleamLyrics.parse(value);
        check(parsed.lines().size()==2&&parsed.lines().getFirst().words().size()==2,"Lyricsfile word sync and Cyrillic");
        check(parsed.lines().getLast().words().isEmpty(),"mixed line sync remains honest");
        value.addProperty("syncedLyrics","[00:01.00]Fallback");
        value.addProperty("lyricsfile", "version: '9.0'\nlines: []");
        check(GleamLyrics.parse(value).lines().getFirst().text().equals("Fallback"),"unknown YAML version fallback");
        value.addProperty("lyricsfile", "!!java.net.URL [https://invalid.test]");
        check(GleamLyrics.parse(value).source().endsWith("LRC"),"safe YAML constructor rejects arbitrary types");
        value.addProperty("lyricsfile", "version: '1.0'\nversion: '1.0'\nlines: []");
        check(GleamLyrics.parse(value).source().endsWith("LRC"),"duplicate keys rejected");
        value.addProperty("lyricsfile", "version: '1.0'\nlines:\n- text: a\n  start_ms: -1\n");
        check(GleamLyrics.parse(value).source().endsWith("LRC"),"negative timing rejected");
        var plain=new JsonObject();plain.addProperty("plainLyrics","Unsynced words");
        check(GleamLyrics.parse(plain).status().equals("unsynced"),"unsynced state");
        plain.addProperty("instrumental",true);
        check(GleamLyrics.parse(plain).status().equals("instrumental"),"instrumental state");
        check(GleamLyricsService.url(new GleamLyricsService.Track("", "A & Б", "Artist", "", 120)).contains("A+%26+%D0%91"),"UTF-8 encoded lookup");
        check(GleamLyricsService.retryDelay("2")>=2000,"Retry-After respected");
        var gson=new Gson();var config=gson.fromJson("{\"animatedLyrics\":null}",OrionConfig.class);config.sanitize();
        check(!config.getAnimatedLyrics().isEnabled(),"old/null config defaults off");
        var settings=config.getAnimatedLyrics();settings.setTextScale(Float.NaN);settings.setOffset(999999);settings.setStyle(null);settings.setColor("url(x)");settings.sanitize();
        check(settings.getTextScale()==1&&settings.getOffset()==10000&&settings.getStyle().equals("Cinematic")&&settings.getColor().equals("#F5F3FF"),"sanitize corrupt settings");
        settings.setEnabled(true);settings.setFont("Serif");settings.setIntensity(27);
        var roundtrip=gson.fromJson(gson.toJson(config),OrionConfig.class);roundtrip.sanitize();
        check(roundtrip.getAnimatedLyrics().isEnabled()&&roundtrip.getAnimatedLyrics().getFont().equals("Serif")&&roundtrip.getAnimatedLyrics().getIntensity()==27,"settings roundtrip");

        Path directory=Path.of(args[0]);Files.createDirectories(directory);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var serverThreads=Executors.newCachedThreadPool();server.setExecutor(serverThreads);
        var requests=new AtomicInteger();var started=new CountDownLatch(1);var throttled=new AtomicInteger();
        server.createContext("/lyrics",exchange->{
            requests.incrementAndGet();String query=exchange.getRequestURI().getRawQuery();
            check(exchange.getRequestHeaders().getFirst("User-Agent").startsWith("GleamClient/"),"identified HTTP client");
            if(query.contains("Slow")){started.countDown();try{Thread.sleep(400);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            int status=query.contains("Missing")?404:query.contains("Throttled")&&throttled.getAndIncrement()==0?429:200;
            if(status==429)exchange.getResponseHeaders().set("Retry-After","1");
            exchange.getResponseHeaders().set("Content-Type","application/json");
            byte[] body=gson.toJson(record("[00:01.00]"+(query.contains("Fast")?"Fast":"Other"))).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try{exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);}catch(java.io.IOException cancelled){}finally{exchange.close();}
        });server.start();
        URI endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/lyrics");
        try {
            try(var service=new GleamLyricsService(directory,endpoint)) {
                service.select(track("Slow"),true);check(started.await(2,TimeUnit.SECONDS),"async request started");
                service.select(track("Fast"),true);await(service,"ready");Thread.sleep(450);
                check(service.snapshot().lyrics().lines().getFirst().text().equals("Fast"),"late response cannot replace new track");
                int count=requests.get();service.select(null,false);service.select(track("Fast"),true);await(service,"ready");
                check(requests.get()==count,"memory cache avoids network");
                service.select(track("Missing"),true);await(service,"missing");count=requests.get();
                service.select(null,false);service.select(track("Missing"),true);await(service,"missing");
                check(count==requests.get(),"negative cache avoids repeated misses");
                service.select(track("Throttled"),true);await(service,"rate_limited");count=requests.get();Thread.sleep(200);
                check(count==requests.get(),"no retry before Retry-After");await(service,"ready");
                service.select(new GleamLyricsService.Track("p","","","",0),true);await(service,"metadata");
                service.select(null,false);await(service,"off");
            }
            int count=requests.get();
            try(var service=new GleamLyricsService(directory,endpoint)) {service.select(track("Fast"),true);await(service,"ready");}
            check(count==requests.get(),"persistent cache survives service restart");
        } finally {server.stop(0);serverThreads.shutdownNow();}
        System.out.println("Lyrics checks passed: "+checks);
    }
}
