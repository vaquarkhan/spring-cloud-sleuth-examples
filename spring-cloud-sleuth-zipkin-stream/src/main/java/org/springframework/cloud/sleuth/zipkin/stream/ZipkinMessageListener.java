package org.springframework.cloud.sleuth.zipkin.stream;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.sleuth.Log;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.stream.SleuthSink;
import org.springframework.cloud.sleuth.stream.Spans;
import org.springframework.cloud.sleuth.zipkin.stream.ZipkinMessageListener.NotSleuthStreamClient;
import org.springframework.context.annotation.*;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;

import zipkin.*;
import zipkin.BinaryAnnotation.Type;
import zipkin.Span.Builder;

import javax.sql.DataSource;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

@MessageEndpoint
@CommonsLog
@Conditional(NotSleuthStreamClient.class)
public class ZipkinMessageListener {

	static final String UNKNOWN_PROCESS_ID = "unknown";

	@Autowired
	SpanStore spanStore;

	@Autowired
	Sampler sampler;

	@ServiceActivator(inputChannel = SleuthSink.INPUT)
	public void sink(Spans input) {
		Iterator<zipkin.Span> sampled = new SamplingZipkinSpanIterator(this.sampler, input);
		if (sampled.hasNext()) {
			this.spanStore.accept(sampled);
		}
	}

	/**
	 * Add annotations from the sleuth Span.
	 */
	static void addZipkinAnnotations(Builder zipkinSpan, Span span, Endpoint endpoint) {
		for (Log ta : span.logs()) {
			Annotation zipkinAnnotation = new Annotation.Builder()
					.endpoint(endpoint)
					.timestamp(ta.getTimestamp() * 1000) // Zipkin is in microseconds
					.value(ta.getEvent())
					.build();
			zipkinSpan.addAnnotation(zipkinAnnotation);
		}
	}

	/**
	 * Creates a list of Annotations that are present in sleuth Span object.
	 *
	 * @return list of Annotations that could be added to Zipkin Span.
	 */
	static void addZipkinBinaryAnnotations(Builder zipkinSpan, Span span,
			Endpoint endpoint) {
		for (Map.Entry<String, String> e : span.tags().entrySet()) {
			BinaryAnnotation.Builder binaryAnn = new BinaryAnnotation.Builder();
			binaryAnn.type(Type.STRING);
			binaryAnn.key(e.getKey());
			try {
				binaryAnn.value(e.getValue().getBytes("UTF-8"));
			}
			catch (UnsupportedEncodingException ex) {
				log.error("Error encoding string as UTF-8", ex);
			}
			binaryAnn.endpoint(endpoint);
			zipkinSpan.addBinaryAnnotation(binaryAnn.build());
		}
	}

	protected static class NotSleuthStreamClient extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			Environment environment = context.getEnvironment();
			if ("true".equals(environment
					.resolvePlaceholders("${spring.sleuth.stream.enabled:}"))) {
				return ConditionOutcome
						.noMatch("Found spring.sleuth.stream.enabled=true");
			}
			if (environment instanceof ConfigurableEnvironment) {
				ConfigurableEnvironment configurable = (ConfigurableEnvironment) environment;
				configurable.getPropertySources()
						.addLast(
								new MapPropertySource("spring.sleuth.stream",
										Collections.<String, Object>singletonMap(
												"spring.sleuth.stream.enabled",
												"false")));
			}
			return ConditionOutcome.match("Not found: spring.sleuth.stream.enabled");
		}

	}

	@Configuration
	@Profile("cloud")
	protected static class CloudDataSourceConfiguration {

		@Bean
		public Cloud cloud() {
			return new CloudFactory().getCloud();
		}

		@Value("${zipkin.collector.sample-rate:1.0}")
		float sampleRate = 1.0f;

		@Bean
		Sampler traceIdSampler() {
			return Sampler.create(this.sampleRate);
		}

		@Bean
		@ConfigurationProperties(DataSourceProperties.PREFIX)
		public DataSource dataSource() {
			return cloud().getSingletonServiceConnector(DataSource.class, null);
		}

	}

}
