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

package org.springframework.cloud.sleuth.zipkin;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.util.InetUtils;
import org.springframework.context.event.EventListener;

import zipkin.Endpoint;

/**
 * @author Dave Syer
 *
 */
public class ServerPropertiesEndpointLocator implements EndpointLocator {

	private final ServerProperties serverProperties;
	private final String appName;
	private Integer port;

	public ServerPropertiesEndpointLocator(ServerProperties serverProperties,
																				String appName) {
		this.serverProperties = serverProperties;
		this.appName = appName;
	}

	@Override
	public Endpoint local() {
		int address = getAddress();
		Integer port = getPort();
		return Endpoint.create(this.appName, address, port);
	}

	@EventListener(EmbeddedServletContainerInitializedEvent.class)
	public void grabPort(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}

	private Integer getPort() {
		if (this.port!=null) {
			return this.port;
		}
		Integer port;
		if (this.serverProperties!=null && this.serverProperties.getPort() != null) {
			port = this.serverProperties.getPort();
		}
		else {
			port = 8080;
		}
		return port;
	}

	private int getAddress() {
		if (this.serverProperties!=null && this.serverProperties.getAddress() != null) {
			return InetUtils.getIpAddressAsInt(this.serverProperties.getAddress().getHostAddress());
		}
		else {
			return 127 << 24 | 1;
		}
	}
}
