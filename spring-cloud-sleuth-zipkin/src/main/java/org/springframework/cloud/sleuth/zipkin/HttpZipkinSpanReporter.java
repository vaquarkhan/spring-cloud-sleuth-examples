package org.springframework.cloud.sleuth.zipkin;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.cloud.sleuth.metric.SpanReporterService;
import zipkin.Codec;
import zipkin.Span;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Submits spans using Zipkin's {@code POST /spans} endpoint.
 */
@CommonsLog
public final class HttpZipkinSpanReporter
		implements ZipkinSpanReporter, Flushable, Closeable {
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private final String url;
	private final BlockingQueue<Span> pending = new LinkedBlockingQueue<>(1000);
	private final Flusher flusher; // Nullable for testing
	private final SpanReporterService spanReporterService;

	/**
	 * @param baseUrl       URL of the zipkin query server instance. Like: http://localhost:9411/
	 * @param flushInterval in seconds. 0 implies spans are {@link #flush() flushed} externally.
	 * @param spanReporterService service to count number of accepted / dropped spans
	 */
	public HttpZipkinSpanReporter(String baseUrl, int flushInterval,
			SpanReporterService spanReporterService) {
		this.url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/spans";
		this.flusher = flushInterval > 0 ? new Flusher(this, flushInterval) : null;
		this.spanReporterService = spanReporterService;
	}

	/**
	 * Queues the span for collection, or drops it if the queue is full.
	 *
	 * @param span Span, should not be <code>null</code>.
	 */
	@Override
	public void report(Span span) {
		this.spanReporterService.incrementAcceptedSpans(1);
		if (!this.pending.offer(span)) {
			this.spanReporterService.incrementDroppedSpans(1);
		}
	}

	/**
	 * Calling this will flush any pending spans to the http transport on the current thread.
	 */
	@Override
	public void flush() {
		if (this.pending.isEmpty())
			return;
		List<Span> drained = new ArrayList<>(this.pending.size());
		this.pending.drainTo(drained);
		if (drained.isEmpty())
			return;

		// json-encode the spans for transport
		byte[] json = Codec.JSON.writeSpans(drained);
		// NOTE: https://github.com/openzipkin/zipkin-java/issues/66 will throw instead of return null.
		if (json == null) {
			log.debug("failed to encode spans, dropping them: " + drained);
			this.spanReporterService.incrementDroppedSpans(drained.size());
			return;
		}

		// Send the json to the zipkin endpoint
		try {
			postSpans(json);
		}
		catch (IOException e) {
			if (log.isDebugEnabled()) { // don't pollute logs unless debug is on.
				// TODO: logger test
				log.debug(
						"error POSTing spans to " + this.url + ": as json: " + new String(json,
								UTF_8), e);
			}
			this.spanReporterService.incrementDroppedSpans(drained.size());
		}
	}

	/**
	 * Calls flush on a fixed interval
	 */
	static final class Flusher implements Runnable {
		final Flushable flushable;
		final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

		Flusher(Flushable flushable, int flushInterval) {
			this.flushable = flushable;
			this.scheduler.scheduleWithFixedDelay(this, 0, flushInterval, SECONDS);
		}

		@Override
		public void run() {
			try {
				this.flushable.flush();
			}
			catch (IOException ignored) {
			}
		}
	}

	void postSpans(byte[] json) throws IOException {
		// intentionally not closing the connection, so as to use keep-alives
		HttpURLConnection connection = (HttpURLConnection) new URL(this.url).openConnection();
		connection.setRequestMethod("POST");
		connection.addRequestProperty("Content-Type", "application/json");
		connection.setDoOutput(true);
		connection.setFixedLengthStreamingMode(json.length);
		connection.getOutputStream().write(json);

		try (InputStream in = connection.getInputStream()) {
			while (in.read() != -1)
				; // skip
		}
		catch (IOException e) {
			try (InputStream err = connection.getErrorStream()) {
				if (err != null) { // possible, if the connection was dropped
					while (err.read() != -1)
						; // skip
				}
			}
			throw e;
		}
	}

	/**
	 * Requests a cease of delivery. There will be at most one in-flight request processing after this
	 * call returns.
	 */
	@Override
	public void close() {
		if (this.flusher != null)
			this.flusher.scheduler.shutdown();
		// throw any outstanding spans on the floor
		int dropped = this.pending.drainTo(new LinkedList<>());
		this.spanReporterService.incrementDroppedSpans(dropped);
	}
}
