package ru.orion.client.media;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
/** Ограничение входящего тела до выделения памяти сверх заданного размера, включая Java 21. */
final class GleamHttpBodyHandlers {
    private GleamHttpBodyHandlers() {}
    static HttpResponse.BodyHandler<byte[]> bytes(long limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative body limit");
        return info -> new HttpResponse.BodySubscriber<>() {
            final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
            Flow.Subscription subscription;
            long remaining = limit;
            boolean done;
            public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
            public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
            public void onNext(List<ByteBuffer> buffers) {
                if (done) return;
                for (ByteBuffer buffer : buffers) {
                    if (buffer.remaining() > remaining) {
                        done = true;
                        subscription.cancel();
                        delegate.onError(new IOException("HTTP body exceeds " + limit + " bytes"));
                        return;
                    }
                    remaining -= buffer.remaining();
                }
                delegate.onNext(buffers);
            }
            public void onError(Throwable error) { if (!done) { done = true; delegate.onError(error); } }
            public void onComplete() { if (!done) { done = true; delegate.onComplete(); } }
        };
    }
}
