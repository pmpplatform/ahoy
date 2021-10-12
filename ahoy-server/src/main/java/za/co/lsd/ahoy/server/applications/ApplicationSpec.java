/*
 * Copyright  2021 LSD Information Technology (Pty) Ltd
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package za.co.lsd.ahoy.server.applications;

import lombok.Data;

import java.util.List;

@Data
public class ApplicationSpec {
	private List<Integer> servicePorts;

	private String healthEndpointPath;
	private Integer healthEndpointPort;
	private String healthEndpointScheme;

	private List<ApplicationEnvironmentVariable> environmentVariables;

	private String configPath;
	private List<ApplicationConfig> configs;

	private List<ApplicationVolume> volumes;

	private List<ApplicationSecret> secrets;
}
