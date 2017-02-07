/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.web;

import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.TraceKeys;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.sleuth.trace.DefaultTracer;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Random;

import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 */
public class TraceFilterMockChainIntegrationTests {

	private StaticApplicationContext context = new StaticApplicationContext();

	private Tracer tracer = new DefaultTracer(new AlwaysSampler(),
			new Random(), this.context);
	private TraceKeys traceKeys = new TraceKeys();

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private MockFilterChain filterChain;

	@Before
	@SneakyThrows
	public void init() {
		SpanContextHolder.removeCurrentSpan();
		this.context.refresh();
		this.request = builder().buildRequest(new MockServletContext());
		this.response = new MockHttpServletResponse();
		this.response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.filterChain = new MockFilterChain();
	}

	public MockHttpServletRequestBuilder builder() {
		return get("/").accept(MediaType.APPLICATION_JSON)
				.header("User-Agent", "MockMvc");
	}

	@Test
	public void startsNewTrace() throws Exception {
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);
		filter.doFilter(this.request, this.response, this.filterChain);
		assertNull(SpanContextHolder.getCurrentSpan());
	}

	@Test
	public void continuesSpanFromHeaders() throws Exception {
		Random generator = new Random();
		this.request = builder().header(Span.SPAN_ID_NAME, generator.nextLong())
				.header(Span.TRACE_ID_NAME, generator.nextLong()).buildRequest(new MockServletContext());
		TraceFilter filter = new TraceFilter(this.tracer, this.traceKeys);
		filter.doFilter(this.request, this.response, this.filterChain);
		assertNull(SpanContextHolder.getCurrentSpan());
	}

}
