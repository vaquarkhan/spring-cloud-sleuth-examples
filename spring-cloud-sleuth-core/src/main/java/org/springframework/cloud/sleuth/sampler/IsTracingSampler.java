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

package org.springframework.cloud.sleuth.sampler;

import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.trace.SpanContextHolder;

/**
 * @author Spencer Gibb
 */
public class IsTracingSampler implements Sampler {

	public static IsTracingSampler INSTANCE = new IsTracingSampler();

	@Override
	public boolean isSampled() {
		return SpanContextHolder.isTracing();
	}
}
