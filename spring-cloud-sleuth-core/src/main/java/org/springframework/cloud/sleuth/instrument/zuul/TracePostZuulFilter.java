/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.zuul;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAccessor;
import org.springframework.cloud.sleuth.event.ClientReceivedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

import com.netflix.zuul.ZuulFilter;

/**
 * @author Dave Syer
 *
 */
public class TracePostZuulFilter extends ZuulFilter
		implements ApplicationEventPublisherAware {

	private ApplicationEventPublisher publisher;

	private final SpanAccessor accessor;

	public TracePostZuulFilter(SpanAccessor accessor) {
		this.accessor = accessor;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public boolean shouldFilter() {
		return getCurrentSpan() != null;
	}

	@Override
	public Object run() {
		// TODO: the client sent event should come from the client not the filter!
		publish(new ClientReceivedEvent(this, getCurrentSpan()));
		return null;
	}

	@Override
	public String filterType() {
		return "post";
	}

	@Override
	public int filterOrder() {
		return 0;
	}

	private void publish(ApplicationEvent event) {
		if (this.publisher != null) {
			this.publisher.publishEvent(event);
		}
	}

	private Span getCurrentSpan() {
		return this.accessor.getCurrentSpan();
	}
}
