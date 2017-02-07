package org.springframework.cloud.sleuth.instrument.integration;

import java.util.Random;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.integration.context.IntegrationObjectSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptorAdapter;

/**
 * Abstraction over classes related to channel intercepting
 *
 * @author Marcin Grzejszczak
 */
abstract class AbstractTraceChannelInterceptor extends ChannelInterceptorAdapter {

	protected static final String MESSAGE_NAME_PREFIX = "message/";

	private final Tracer tracer;

	private final Random random;

	private final TraceKeys traceKeys;

	protected AbstractTraceChannelInterceptor(Tracer tracer, TraceKeys traceKeys, Random random) {
		this.tracer = tracer;
		this.traceKeys = traceKeys;
		this.random = random;
	}

	protected Tracer getTracer() {
		return this.tracer;
	}

	protected TraceKeys getTraceKeys() {
		return this.traceKeys;
	}

	/**
	 * Returns a span given the message and a channel. Returns null when there was no
	 * trace id passed initially.
	 */
	Span buildSpan(Message<?> message) {
		if (!hasHeader(message, Span.TRACE_ID_NAME) || !hasHeader(message, Span.SPAN_ID_NAME)) {
			return null; // cannot build a span without ids
		}
		long spanId = hasHeader(message, Span.SPAN_ID_NAME) ?
				Span.fromHex(getHeader(message, Span.SPAN_ID_NAME)) : this.random.nextLong();
		long traceId = Span.fromHex(getHeader(message, Span.TRACE_ID_NAME));
		Span.SpanBuilder span = Span.builder().traceId(traceId).spanId(spanId);
		if (message.getHeaders().containsKey(Span.NOT_SAMPLED_NAME)) {
			span.exportable(false);
		}
		String parentId = getHeader(message, Span.PARENT_ID_NAME);
		String processId = getHeader(message, Span.PROCESS_ID_NAME);
		String spanName = getHeader(message, Span.SPAN_NAME_NAME);
		if (spanName != null) {
			span.name(spanName);
		}
		if (processId != null) {
			span.processId(processId);
		}
		if (parentId != null) {
			span.parent(Span.fromHex(parentId));
		}
		span.remote(true);
		return span.build();
	}

	String getHeader(Message<?> message, String name) {
		return getHeader(message, name, String.class);
	}

	<T> T getHeader(Message<?> message, String name, Class<T> type) {
		return message.getHeaders().get(name, type);
	}

	boolean hasHeader(Message<?> message, String name) {
		return message.getHeaders().containsKey(name);
	}

	String getChannelName(MessageChannel channel) {
		String name = null;
		if (channel instanceof IntegrationObjectSupport) {
			name = ((IntegrationObjectSupport) channel).getComponentName();
		}
		if (name == null && channel instanceof AbstractMessageChannel) {
			name = ((AbstractMessageChannel) channel).getFullChannelName();
		}
		if (name == null) {
			name = channel.toString();
		}
		return name;
	}

	String getMessageChannelName(MessageChannel channel) {
		return MESSAGE_NAME_PREFIX + getChannelName(channel);
	}

}
