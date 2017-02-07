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

package org.springframework.cloud.sleuth.log;

import org.apache.commons.logging.Log;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnBean(Tracer.class)
@AutoConfigureAfter(TraceAutoConfiguration.class)
public class SleuthLogAutoConfiguration {

	@Configuration
	@ConditionalOnClass(MDC.class)
	protected static class Slf4jConfiguration {
		@Bean
		@ConditionalOnProperty(value = "spring.sleuth.log.slf4j.enabled", matchIfMissing = true)
		public Slf4jSpanListener slf4jSpanStartedListener() {
			// Sets up MDC entries X-Trace-Id and X-Span-Id
			return new Slf4jSpanListener();
		}
	}

	@Configuration
	@ConditionalOnClass(Log.class)
	protected static class JsonConfiguration {
		@Bean
		@ConditionalOnProperty("spring.sleuth.log.json.enabled")
		public JsonLogSpanListener jsonSlf4jSpanListener() {
			return new JsonLogSpanListener();
		}
	}

}
