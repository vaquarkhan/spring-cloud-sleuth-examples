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

package org.springframework.cloud.sleuth.stream;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.cloud.sleuth.Span;
import org.springframework.context.event.EventListener;
import org.springframework.util.Assert;

/**
 * @author Dave Syer
 *
 */
public class ServerPropertiesHostLocator implements HostLocator {

	private final ServerProperties serverProperties; // Nullable
	private final String appName;
	private Integer port; // Lazy assigned

	public ServerPropertiesHostLocator(ServerProperties serverProperties,
			String appName) {
		this.serverProperties = serverProperties;
		this.appName = appName;
		Assert.notNull(this.appName, "appName");
	}

	@Override
	public Host locate(Span span) {
		String serviceName = getServiceName(span);
		String address = getAddress();
		Integer port = getPort();
		Host ep = new Host(serviceName, address, port);
		return ep;
	}

	@EventListener(EmbeddedServletContainerInitializedEvent.class)
	public void grabPort(EmbeddedServletContainerInitializedEvent event) {
		this.port = event.getEmbeddedServletContainer().getPort();
	}

	private Integer getPort() {
		if (this.port != null) {
			return this.port;
		}
		Integer port;
		if (this.serverProperties != null && this.serverProperties.getPort() != null) {
			port = this.serverProperties.getPort();
		}
		else {
			port = 8080;
		}
		return port;
	}

	private String getAddress() {
		String address;
		if (this.serverProperties != null && this.serverProperties.getAddress() != null) {
			address = this.serverProperties.getAddress().getHostAddress();
		}
		else {
			address = "127.0.0.1";
		}
		return address;
	}

	private String getServiceName(Span span) {
		String serviceName;
		if (span.getProcessId() != null) {
			serviceName = span.getProcessId();
		}
		else {
			serviceName = this.appName;
		}
		return serviceName;
	}

}
