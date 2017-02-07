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
package integration;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import integration.MessagingApplicationTests.IntegrationSpanCollectorConfig;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.sleuth.zipkin.ZipkinSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import sample.SampleMessagingApplication;
import tools.AbstractIntegrationTest;
import zipkin.Constants;
import zipkin.Span;

import static org.assertj.core.api.BDDAssertions.then;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = { IntegrationSpanCollectorConfig.class, SampleMessagingApplication.class })
@WebIntegrationTest
@TestPropertySource(properties="sample.zipkin.enabled=true")
public class MessagingApplicationTests extends AbstractIntegrationTest {

	private static int port = 3381;
	private static String sampleAppUrl = "http://localhost:" + port;
	@Autowired IntegrationTestZipkinSpanReporter integrationTestSpanCollector;

	@After
	public void cleanup() {
		this.integrationTestSpanCollector.hashedSpans.clear();
	}

	@Test
	public void should_have_passed_trace_id_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/", traceId));

		await().until(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
		});
	}

	@Test
	public void should_have_passed_trace_id_and_generate_new_span_id_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();
		long spanId = new Random().nextLong();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/", traceId, spanId));

		await().until(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
			thenTheSpansHaveProperParentStructure();
		});
	}

	@Test
	public void should_have_passed_trace_id_with_annotations_in_async_thread_when_message_is_about_to_be_sent() {
		long traceId = new Random().nextLong();

		await().until(httpMessageWithTraceIdInHeadersIsSuccessfullySent(sampleAppUrl + "/xform", traceId));

		await().until(() -> {
			thenAllSpansHaveTraceIdEqualTo(traceId);
			thenThereIsAtLeastOneBinaryAnnotationWithKey("background-sleep-millis");
		});
	}

	private void thenThereIsAtLeastOneBinaryAnnotationWithKey(String binaryAnnotationKey) {
		then(this.integrationTestSpanCollector.hashedSpans.stream()
				.map(s -> s.binaryAnnotations)
				.flatMap(Collection::stream)
				.anyMatch(b -> b.key.equals(binaryAnnotationKey))).isTrue();
	}

	private void thenAllSpansHaveTraceIdEqualTo(long traceId) {
		then(this.integrationTestSpanCollector.hashedSpans.stream().allMatch(span -> span.traceId == traceId)).isTrue();
	}

	private void thenTheSpansHaveProperParentStructure() {
		Optional<Span> firstHttpSpan = findFirstHttpRequestSpan();
		List<Span> eventSpans = findAllEventRelatedSpans();
		Optional<Span> eventSentSpan = findSpanWithAnnotation(eventSpans, Constants.SERVER_SEND);
		Optional<Span> eventReceivedSpan = findSpanWithAnnotation(eventSpans, Constants.CLIENT_RECV);
		Optional<Span> lastHttpSpan = findLastHttpSpan();
		thenAllSpansArePresent(firstHttpSpan, eventSpans, lastHttpSpan, eventSentSpan, eventReceivedSpan);
		then(lastHttpSpan.get().parentId).isEqualTo(eventSentSpan.get().id);
		then(eventSentSpan.get().parentId).isEqualTo(firstHttpSpan.get().id);
		then(eventSentSpan.get()).isNotEqualTo(eventReceivedSpan.get());
	}

	private Optional<Span> findLastHttpSpan() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> "http/foo".equals(span.name)).findFirst();
	}

	private Optional<Span> findSpanWithAnnotation(List<Span> eventSpans, String annotationName) {
		return eventSpans.stream()
				.filter(span -> span.annotations.stream().filter(annotation -> annotationName
						.equals(annotation.value)).findFirst().isPresent())
				.findFirst();
	}

	private List<Span> findAllEventRelatedSpans() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> "message/messages".equals(span.name) && span.parentId != null).collect(
						Collectors.toList());
	}

	private Optional<Span> findFirstHttpRequestSpan() {
		return this.integrationTestSpanCollector.hashedSpans.stream()
				.filter(span -> "http/".equals(span.name) && span.parentId != null).findFirst();
	}

	private void thenAllSpansArePresent(Optional<Span> firstHttpSpan,
			List<Span> eventSpans, Optional<Span> lastHttpSpan,
			Optional<Span> eventSentSpan, Optional<Span> eventReceivedSpan) {
		then(firstHttpSpan.isPresent()).isTrue();
		then(eventSpans).isNotEmpty();
		then(eventSentSpan.isPresent()).isTrue();
		then(eventReceivedSpan.isPresent()).isTrue();
		then(lastHttpSpan.isPresent()).isTrue();
	}

	@Configuration
	public static class IntegrationSpanCollectorConfig {
		@Bean
		ZipkinSpanReporter integrationTestZipkinSpanReporter() {
			return new IntegrationTestZipkinSpanReporter();
		}
	}

}
