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
package tools;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ConditionFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import zipkin.Codec;
import zipkin.Span;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@Slf4j
public abstract class AbstractIntegrationTest {

	protected static int pollInterval = 1;
	protected static int timeout = 20;
	protected RestTemplate restTemplate = new AssertingRestTemplate();

	@Before
	public void clearSpanBefore() {
		SpanContextHolder.removeCurrentSpan();
	}

	@After
	public void clearSpanAfter() {
		SpanContextHolder.removeCurrentSpan();
	}

	public static ConditionFactory await() {
		return Awaitility.await().pollInterval(pollInterval, SECONDS).atMost(timeout, SECONDS);
	}

	protected Runnable zipkinQueryServerIsUp() {
		return checkServerHealth("Zipkin Query Server", this::endpointToCheckZipkinQueryHealth);
	}

	protected Runnable zipkinServerIsUp() {
		return checkServerHealth("Zipkin Stream Server", this::endpointToCheckZipkinServerHealth);
	}

	protected Runnable checkServerHealth(String appName, RequestExchanger requestExchanger) {
		return () -> {
			ResponseEntity<String> response = requestExchanger.exchange();
			log.info("Response from the [{}] health endpoint is [{}]", appName, response);
			then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			log.info("[{}] is up!", appName);
		};
	}

	private interface RequestExchanger {
		ResponseEntity<String> exchange();
	}

	protected ResponseEntity<String> endpointToCheckZipkinQueryHealth() {
		URI uri = URI.create(getZipkinServicesQueryUrl());
		log.info("Sending request to the Zipkin query service [{}]", uri);
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> endpointToCheckZipkinServerHealth() {
		URI uri = URI.create("http://localhost:" +getZipkinServerPort()+"/health");
		log.info("Sending request to the Zipkin Server [{}]", uri);
		return exchangeRequest(uri);
	}

	protected int getZipkinServerPort() {
		return 9411;
	}

	protected ResponseEntity<String> checkStateOfTheTraceId(long traceId) {
		URI uri = URI.create(getZipkinTraceQueryUrl() + Long.toHexString(traceId));
		log.info("Sending request to the Zipkin query service [{}]. Checking presence of trace id [{}]", uri, traceId);
		return exchangeRequest(uri);
	}

	protected ResponseEntity<String> exchangeRequest(URI uri) {
		return this.restTemplate.exchange(
				new RequestEntity<>(new HttpHeaders(), HttpMethod.GET, uri), String.class
		);
	}

	protected String getZipkinTraceQueryUrl() {
		return "http://localhost:"+getZipkinServerPort()+"/api/v1/trace/";
	}

	protected String getZipkinServicesQueryUrl() {
		return "http://localhost:"+getZipkinServerPort()+"/api/v1/services";
	}

	protected Runnable httpMessageWithTraceIdInHeadersIsSuccessfullySent(String endpoint, long traceId) {
		return new RequestSendingRunnable(this.restTemplate, endpoint, traceId, null);
	}

	protected Runnable httpMessageWithTraceIdInHeadersIsSuccessfullySent(String endpoint, long traceId, Long spanId) {
		return new RequestSendingRunnable(this.restTemplate, endpoint, traceId, spanId);
	}

	protected Runnable allSpansWereRegisteredInZipkinWithTraceIdEqualTo(long traceId) {
		return () -> {
			ResponseEntity<String> response = checkStateOfTheTraceId(traceId);
			log.info("Response from the Zipkin query service about the trace id [{}] for trace with id [{}]", response, traceId);
			then(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			then(response.hasBody()).isTrue();
			List<Span> spans = Codec.JSON.readSpans(response.getBody().getBytes());
			List<String> serviceNamesNotFoundInZipkin = serviceNamesNotFoundInZipkin(spans);
			List<String> spanNamesNotFoundInZipkin = annotationsNotFoundInZipkin(spans);
			log.info("The following services were not found in Zipkin {}", serviceNamesNotFoundInZipkin);
			log.info("The following annotations were not found in Zipkin {}", spanNamesNotFoundInZipkin);
			then(serviceNamesNotFoundInZipkin).isEmpty();
			then(spanNamesNotFoundInZipkin).isEmpty();
			log.info("Zipkin tracing is working! Sleuth is working! Let's be happy!");
		};
	}

	protected List<String> serviceNamesNotFoundInZipkin(List<zipkin.Span> spans) {
		List<String> serviceNamesFoundInAnnotations = spans.stream()
				.filter(span -> span.annotations != null)
				.map(span -> span.annotations)
				.flatMap(Collection::stream)
				.filter(span -> span.endpoint != null)
				.map(annotation -> annotation.endpoint)
				.map(endpoint -> endpoint.serviceName)
				.distinct()
				.collect(Collectors.toList());
		List<String> serviceNamesFoundInBinaryAnnotations = spans.stream()
				.filter(span -> span.binaryAnnotations != null)
				.map(span -> span.binaryAnnotations)
				.flatMap(Collection::stream)
				.filter(span -> span.endpoint != null)
				.map(annotation -> annotation.endpoint)
				.map(endpoint -> endpoint.serviceName)
				.distinct()
				.collect(Collectors.toList());
		List<String> names = new ArrayList<>();
		names.addAll(serviceNamesFoundInAnnotations);
		names.addAll(serviceNamesFoundInBinaryAnnotations);
		return names.contains(getAppName()) ? Collections.emptyList() : names;
	}

	protected String getAppName() {
		return "unknown";
	}

	protected List<String> annotationsNotFoundInZipkin(List<zipkin.Span> spans) {
		String binaryAnnotationName = getRequiredBinaryAnnotationName();
		Optional<String> names = spans.stream()
				.filter(span -> span.binaryAnnotations != null)
				.map(span -> span.binaryAnnotations)
				.flatMap(Collection::stream)
				.filter(span -> span.endpoint != null)
				.map(annotation -> annotation.key)
				.filter(binaryAnnotationName::equals)
				.findFirst();
		return names.isPresent() ? Collections.emptyList() : Collections.singletonList(binaryAnnotationName);
	}

	protected String getRequiredBinaryAnnotationName() {
		return "random-sleep-millis";
	}

}
