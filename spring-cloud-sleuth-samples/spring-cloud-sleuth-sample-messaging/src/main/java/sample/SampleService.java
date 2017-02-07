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

package sample;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.web.client.RestTemplate;

/**
 * @author Dave Syer
 *
 */
@MessageEndpoint
@Slf4j
public class SampleService implements
		ApplicationListener<EmbeddedServletContainerInitializedEvent> {
	@Autowired private RestTemplate restTemplate;
	private int port;

	@ServiceActivator(inputChannel="messages")
	public void log(Message<?> message) {
		log.info("Received: " + message);
		this.restTemplate.getForObject("http://localhost:" + this.port + "/foo", String.class);
	}

	@Override public void onApplicationEvent(
			EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}
}
