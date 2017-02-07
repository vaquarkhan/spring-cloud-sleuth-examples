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

package sample;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.kristofa.brave.LoggingSpanCollector;
import com.github.kristofa.brave.SpanCollector;

/**
 * @author Spencer Gibb
 */
@SpringBootApplication
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAsync
@IntegrationComponentScan
@RestController
public class SampleMessagingApplication {

	@Autowired
	private SampleSink gateway;

	@Autowired
	private SampleRequestResponse transformer;

	@Bean
	public Sampler defaultSampler() {
		return new AlwaysSampler();
	}

	@RequestMapping("/")
	public String home() {
		String msg = "Hello";
		this.gateway.send(msg);
		return msg;
	}

	@RequestMapping("/foo")
	public String foo() {
		return "foo";
	}

	@RequestMapping("/xform")
	public String xform() {
		String msg = "Hello";
		return this.transformer.send(msg);
	}

	public static void main(String[] args) {
		SpringApplication.run(SampleMessagingApplication.class, args);
	}

	// Use this for debugging (or if there is no Zipkin server running on port 9411)
	@Bean
	@ConditionalOnProperty(value="sample.zipkin.enabled", havingValue="false")
	public SpanCollector spanCollector() {
		return new LoggingSpanCollector();
	}

}
