/*
 * Copyright  2022 LSD Information Technology (Pty) Ltd
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

package za.co.lsd.ahoy.server.helm;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.yaml.snakeyaml.Yaml;
import za.co.lsd.ahoy.server.AhoyServerApplication;
import za.co.lsd.ahoy.server.applications.*;
import za.co.lsd.ahoy.server.cluster.Cluster;
import za.co.lsd.ahoy.server.cluster.ClusterType;
import za.co.lsd.ahoy.server.docker.DockerRegistry;
import za.co.lsd.ahoy.server.docker.DockerRegistryProvider;
import za.co.lsd.ahoy.server.environmentrelease.EnvironmentRelease;
import za.co.lsd.ahoy.server.environments.Environment;
import za.co.lsd.ahoy.server.helm.sealedsecrets.DockerConfigSealedSecretProducer;
import za.co.lsd.ahoy.server.helm.sealedsecrets.SecretDataSealedSecretProducer;
import za.co.lsd.ahoy.server.helm.values.*;
import za.co.lsd.ahoy.server.releases.Release;
import za.co.lsd.ahoy.server.releases.ReleaseVersion;
import za.co.lsd.ahoy.server.util.HashUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = AhoyServerApplication.class)
@ActiveProfiles(profiles = {"test", "keycloak"})
@Slf4j
public class ChartGeneratorTest {
	@Autowired
	private ChartGenerator chartGenerator;
	@MockBean
	private DockerRegistryProvider dockerRegistryProvider;
	@MockBean
	private ApplicationEnvironmentConfigProvider environmentConfigProvider;
	@MockBean
	private DockerConfigSealedSecretProducer dockerConfigSealedSecretProducer;
	@MockBean
	private SecretDataSealedSecretProducer secretDataSealedSecretProducer;
	@Autowired
	private Yaml yaml;
	@TempDir
	Path temporaryFolder;
	private Path repoPath;

	@BeforeEach
	public void setupRepoPath() throws IOException {
		repoPath = temporaryFolder.resolve("repo");

		when(dockerConfigSealedSecretProducer.produce(any())).thenReturn("encrypted-docker-config");
		when(secretDataSealedSecretProducer.produce(any(ApplicationSecret.class))).thenAnswer(invocationOnMock -> {
			ApplicationSecret applicationSecret = (ApplicationSecret) invocationOnMock.getArguments()[0];
			return applicationSecret.getData();
		});
	}

	@Test
	public void generateBasic() throws Exception {
		// given
		Cluster cluster = new Cluster("test-cluster", "https://kubernetes.default.svc", ClusterType.KUBERNETES);
		Environment environment = new Environment("dev");
		cluster.addEnvironment(environment);
		Release release = new Release("release1");
		EnvironmentRelease environmentRelease = new EnvironmentRelease(environment, release);

		Application application = new Application("app1");
		ApplicationVersion applicationVersion = new ApplicationVersion("1.0.0", application);
		ApplicationSpec spec = new ApplicationSpec("image", "docker-registry");
		applicationVersion.setSpec(spec);

		ReleaseVersion releaseVersion = new ReleaseVersion("1.0.0");
		release.addReleaseVersion(releaseVersion);
		releaseVersion.setApplicationVersions(Collections.singletonList(applicationVersion));

		when(environmentConfigProvider.environmentConfigFor(any(), any(), any())).thenReturn(Optional.empty());

		Path basePath = repoPath.resolve(cluster.getName()).resolve(environment.getName()).resolve(release.getName());
		Path templatesPath = basePath.resolve("templates");

		// when
		chartGenerator.generate(environmentRelease, releaseVersion, repoPath);

		// then
		assertFilesExist(basePath, "chart",
			"Chart.yaml",
			"values.yaml");

		assertFilesExist(templatesPath, "template",
			"configmap.yaml",
			"pvc.yaml",
			"deployment.yaml",
			"deployment-app1.yaml",
			"ingress.yaml",
			"service.yaml",
			"secret-dockerconfig.yaml",
			"secret-generic.yaml");

		Path valuesPath = basePath.resolve("values.yaml");
		log.info("Values: \n" + Files.readString(valuesPath));
		Values actualValues = yaml.loadAs(Files.newInputStream(valuesPath), Values.class);

		ApplicationValues expectedApplicationValues = ApplicationValues.builder()
			.name("app1")
			.version("1.0.0")
			.image("image")
			.replicas(1)
			.environmentVariablesEnabled(false)
			.environmentVariables(new LinkedHashMap<>())
			.configFilesEnabled(false)
			.configFiles(new LinkedHashMap<>())
			.volumes(new LinkedHashMap<>())
			.secrets(new LinkedHashMap<>())
			.build();
		Map<String, ApplicationValues> expectedApps = new LinkedHashMap<>();
		expectedApps.put("app1", expectedApplicationValues);

		Values expectedValues = Values.builder()
			.environment("dev")
			.releaseName("release1")
			.releaseVersion("1.0.0")
			.applications(expectedApps)
			.build();

		assertEquals(expectedValues, actualValues, "Values incorrect");
	}

	@Test
	public void generateFull() throws Exception {
		// given
		Cluster cluster = new Cluster("test-cluster", "https://kubernetes.default.svc", ClusterType.KUBERNETES);
		cluster.setHost("my-host");
		Environment environment = new Environment("dev");
		cluster.addEnvironment(environment);
		Release release = new Release("release1");
		EnvironmentRelease environmentRelease = new EnvironmentRelease(environment, release);

		Application application = new Application("app1");
		ApplicationVersion applicationVersion = new ApplicationVersion("1.0.0", application);
		List<Integer> servicePorts = Collections.singletonList(8080);
		ApplicationSpec spec = new ApplicationSpec("image", "docker-registry");
		spec.setDockerRegistryName("docker-registry");
		applicationVersion.setSpec(spec);
		spec.setCommand("/bin/sh");
		spec.setArgs(Arrays.asList("-c", "echo hello"));
		spec.setServicePortsEnabled(true);
		spec.setServicePorts(servicePorts);
		List<ApplicationEnvironmentVariable> environmentVariables = Arrays.asList(
			new ApplicationEnvironmentVariable("ENV", "VAR"),
			new ApplicationEnvironmentVariable("SECRET_ENV", "my-secret", "secret-key")
		);
		spec.setEnvironmentVariablesEnabled(true);
		spec.setEnvironmentVariables(environmentVariables);
		spec.setHealthChecksEnabled(true);
		spec.setHealthEndpointPath("/");
		spec.setHealthEndpointPort(8080);
		spec.setHealthEndpointScheme("HTTP");
		spec.setLivenessProbe(new ApplicationProbe(60L, 10L, 5L, 1L, 3L));
		spec.setReadinessProbe(new ApplicationProbe(10L, 10L, 5L, 1L, 3L));
		spec.setConfigPath("/opt/config");
		spec.setConfigFilesEnabled(true);
		List<ApplicationConfigFile> appConfigs = Collections.singletonList(new ApplicationConfigFile("application.properties", "greeting=hello"));
		spec.setConfigFiles(appConfigs);
		ReleaseVersion releaseVersion = new ReleaseVersion("1.0.0");
		release.addReleaseVersion(releaseVersion);
		releaseVersion.setApplicationVersions(Collections.singletonList(applicationVersion));

		ApplicationEnvironmentSpec environmentSpec = new ApplicationEnvironmentSpec("myapp1-route", 8080);
		ApplicationEnvironmentConfig environmentConfig = new ApplicationEnvironmentConfig(environmentSpec);
		environmentSpec.setReplicas(2);
		environmentSpec.setTls(true);
		environmentSpec.setTlsSecretName("my-tls-secret");
		environmentSpec.setConfigFilesEnabled(true);
		List<ApplicationConfigFile> appEnvConfigs = Collections.singletonList(new ApplicationConfigFile("application-dev.properties", "anothergreeting=hello"));
		environmentSpec.setConfigFiles(appEnvConfigs);

		List<ApplicationVolume> appVolumes = Arrays.asList(
			new ApplicationVolume("my-volume", "/opt/vol", "standard", VolumeAccessMode.ReadWriteOnce, 2L, StorageUnit.Gi),
			new ApplicationVolume("my-secret-volume", "/opt/secret-vol", "my-secret")
		);
		spec.setVolumes(appVolumes);

		List<ApplicationSecret> appSecrets = Arrays.asList(
			new ApplicationSecret("my-secret", SecretType.Generic, Collections.singletonMap("secret-key", "secret-value")),
			new ApplicationSecret("my-tls-secret", SecretType.Tls, Collections.singletonMap("cert", "my-cert"))
		);
		spec.setSecrets(appSecrets);

		ApplicationResources resources = new ApplicationResources(1000L, 100L, QuantityUnit.Mi, 500L, 50L, QuantityUnit.Mi);
		spec.setResources(resources);

		List<ApplicationEnvironmentVariable> environmentVariablesEnv = Arrays.asList(
			new ApplicationEnvironmentVariable("DEV_ENV", "VAR"),
			new ApplicationEnvironmentVariable("SECRET_DEV_ENV", "my-secret", "secret-key")
		);
		environmentSpec.setEnvironmentVariablesEnabled(true);
		environmentSpec.setEnvironmentVariables(environmentVariablesEnv);

		List<ApplicationVolume> envVolumes = Arrays.asList(
			new ApplicationVolume("my-env-volume", "/opt/env-vol", "standard", VolumeAccessMode.ReadWriteOnce, 2L, StorageUnit.Gi),
			new ApplicationVolume("my-env-secret-volume", "/opt/env-secret-vol", "my-env-secret")
		);
		environmentSpec.setVolumes(envVolumes);

		List<ApplicationSecret> envSecrets = Collections.singletonList(new ApplicationSecret("my-env-secret", SecretType.Generic, Collections.singletonMap("env-secret-key", "env-secret-value")));
		environmentSpec.setSecrets(envSecrets);

		ApplicationResources envResources = new ApplicationResources(1200L, 120L, QuantityUnit.Mi, null, null, QuantityUnit.Mi);
		environmentSpec.setResources(envResources);

		DockerRegistry dockerRegistry = new DockerRegistry("docker-registry", "docker-server", "username", "password");
		when(dockerRegistryProvider.dockerRegistryFor(eq("docker-registry"))).thenReturn(Optional.of(dockerRegistry));
		when(environmentConfigProvider.environmentConfigFor(any(), any(), any())).thenReturn(Optional.of(environmentConfig));

		Path basePath = repoPath.resolve(cluster.getName()).resolve(environment.getName()).resolve(release.getName());
		Path templatesPath = basePath.resolve("templates");

		// when
		chartGenerator.generate(environmentRelease, releaseVersion, repoPath);

		// then
		assertFilesExist(basePath, "chart",
			"Chart.yaml",
			"values.yaml");

		assertFilesExist(templatesPath, "template",
			"configmap.yaml",
			"configmap-app1.yaml",
			"pvc.yaml",
			"pvc-app1.yaml",
			"deployment.yaml",
			"deployment-app1.yaml",
			"ingress.yaml",
			"ingress-app1.yaml",
			"service.yaml",
			"service-app1.yaml",
			"secret-dockerconfig.yaml",
			"secret-dockerconfig-app1.yaml",
			"secret-generic.yaml",
			"secret-generic-app1.yaml");

		Path valuesPath = basePath.resolve("values.yaml");
		log.info("Values: \n" + Files.readString(valuesPath));
		Values actualValues = yaml.loadAs(Files.newInputStream(valuesPath), Values.class);

		Map<String, EnvironmentVariableValues> expectedEnvironmentVariables = new LinkedHashMap<>();
		expectedEnvironmentVariables.put("ENV", new EnvironmentVariableValues("ENV", "VAR"));
		expectedEnvironmentVariables.put("SECRET_ENV", new EnvironmentVariableValues("SECRET_ENV", "my-secret", "secret-key"));
		expectedEnvironmentVariables.put("DEV_ENV", new EnvironmentVariableValues("DEV_ENV", "VAR"));
		expectedEnvironmentVariables.put("SECRET_DEV_ENV", new EnvironmentVariableValues("SECRET_DEV_ENV", "my-secret", "secret-key"));

		Map<String, ApplicationConfigFileValues> configFiles = new LinkedHashMap<>();
		configFiles.put("application-config-file-188deccf", new ApplicationConfigFileValues("application.properties", "greeting=hello"));
		configFiles.put("application-config-file-c1fcd7e5", new ApplicationConfigFileValues("application-dev.properties", "anothergreeting=hello"));

		String configFileHashes = "{"
			+ "\"application.properties\":\"" + HashUtil.hash("greeting=hello")
			+ "\",\"application-dev.properties\":\"" + HashUtil.hash("anothergreeting=hello")
			+ "\"}";

		Map<String, ApplicationVolumeValues> volumes = new LinkedHashMap<>();
		volumes.put("my-volume", new ApplicationVolumeValues("my-volume", "/opt/vol", "standard", "ReadWriteOnce", "2Gi"));
		volumes.put("my-secret-volume", new ApplicationVolumeValues("my-secret-volume", "/opt/secret-vol", "my-secret"));
		volumes.put("my-env-volume", new ApplicationVolumeValues("my-env-volume", "/opt/env-vol", "standard", "ReadWriteOnce", "2Gi"));
		volumes.put("my-env-secret-volume", new ApplicationVolumeValues("my-env-secret-volume", "/opt/env-secret-vol", "my-env-secret"));

		Map<String, ApplicationSecretValues> secrets = new LinkedHashMap<>();
		secrets.put("my-secret", new ApplicationSecretValues("my-secret", "Opague", Collections.singletonMap("secret-key", "secret-value")));
		secrets.put("my-tls-secret", new ApplicationSecretValues("my-tls-secret", "kubernetes.io/tls", Collections.singletonMap("cert", "my-cert")));
		secrets.put("my-env-secret", new ApplicationSecretValues("my-env-secret", "Opague", Collections.singletonMap("env-secret-key", "env-secret-value")));

		ApplicationValues expectedApplicationValues = ApplicationValues.builder()
			.name("app1")
			.version("1.0.0")
			.image("image")
			.command("/bin/sh")
			.args(Arrays.asList("-c", "echo hello"))
			.dockerConfigJson("encrypted-docker-config")
			.servicePortsEnabled(true)
			.servicePorts(servicePorts)
			.healthChecksEnabled(true)
			.healthEndpointPath("/")
			.healthEndpointPort(8080)
			.healthEndpointScheme("HTTP")
			.livenessProbe(new ApplicationProbe(60L, 10L, 5L, 1L, 3L))
			.readinessProbe(new ApplicationProbe(10L, 10L, 5L, 1L, 3L))
			.replicas(2)
			.routeHostname("myapp1-route")
			.routeTargetPort(8080)
			.tls(true)
			.tlsSecretName("my-tls-secret")
			.environmentVariablesEnabled(true)
			.environmentVariables(expectedEnvironmentVariables)
			.configPath("/opt/config")
			.configFilesEnabled(true)
			.configFiles(configFiles)
			.configFileHashes(configFileHashes)
			.volumes(volumes)
			.secrets(secrets)
			.resources(new ResourcesValues(
				new ResourcesValues.ResourceValue("1200m", "120Mi"),
				new ResourcesValues.ResourceValue("500m", "50Mi")
			))
			.build();
		Map<String, ApplicationValues> expectedApps = new LinkedHashMap<>();
		expectedApps.put("app1", expectedApplicationValues);

		Values expectedValues = Values.builder()
			.host("my-host")
			.environment("dev")
			.releaseName("release1")
			.releaseVersion("1.0.0")
			.applications(expectedApps)
			.build();

		assertEquals(expectedValues, actualValues, "Values incorrect");
	}

	@Test
	public void generateEnvConfigOnly() throws Exception {
		// given
		Cluster cluster = new Cluster("test-cluster", "https://kubernetes.default.svc", ClusterType.KUBERNETES);
		cluster.setHost("my-host");
		Environment environment = new Environment("dev");
		cluster.addEnvironment(environment);
		Release release = new Release("release1");
		EnvironmentRelease environmentRelease = new EnvironmentRelease(environment, release);

		Application application = new Application("app1");
		ApplicationVersion applicationVersion = new ApplicationVersion("1.0.0", application);
		ApplicationSpec spec = new ApplicationSpec("image", "docker-registry");
		applicationVersion.setSpec(spec);

		// needed for route
		List<Integer> servicePorts = Collections.singletonList(8080);
		spec.setServicePortsEnabled(true);
		spec.setServicePorts(servicePorts);

		ReleaseVersion releaseVersion = new ReleaseVersion("1.0.0");
		release.addReleaseVersion(releaseVersion);
		releaseVersion.setApplicationVersions(Collections.singletonList(applicationVersion));

		ApplicationEnvironmentSpec environmentSpec = new ApplicationEnvironmentSpec("myapp1-route", 8080);
		ApplicationEnvironmentConfig environmentConfig = new ApplicationEnvironmentConfig(environmentSpec);
		environmentSpec.setReplicas(2);
		environmentSpec.setTls(true);
		environmentSpec.setTlsSecretName("my-tls-secret");
		environmentSpec.setConfigFilesEnabled(true);
		List<ApplicationConfigFile> appEnvConfigs = Collections.singletonList(new ApplicationConfigFile("application-dev.properties", "anothergreeting=hello"));
		environmentSpec.setConfigFiles(appEnvConfigs);

		List<ApplicationEnvironmentVariable> environmentVariablesEnv = Arrays.asList(
			new ApplicationEnvironmentVariable("DEV_ENV", "VAR"),
			new ApplicationEnvironmentVariable("SECRET_DEV_ENV", "my-secret", "secret-key")
		);
		environmentSpec.setEnvironmentVariablesEnabled(true);
		environmentSpec.setEnvironmentVariables(environmentVariablesEnv);

		List<ApplicationVolume> envVolumes = Arrays.asList(
			new ApplicationVolume("my-env-volume", "/opt/env-vol", "standard", VolumeAccessMode.ReadWriteOnce, 2L, StorageUnit.Gi),
			new ApplicationVolume("my-env-secret-volume", "/opt/env-secret-vol", "my-env-secret")
		);
		environmentSpec.setVolumes(envVolumes);

		List<ApplicationSecret> envSecrets = Arrays.asList(
			new ApplicationSecret("my-env-secret", SecretType.Generic, Collections.singletonMap("env-secret-key", "env-secret-value")),
			new ApplicationSecret("my-tls-secret", SecretType.Tls, Collections.singletonMap("cert", "my-cert"))
		);
		environmentSpec.setSecrets(envSecrets);

		ApplicationResources envResources = new ApplicationResources(1200L, 120L, QuantityUnit.Mi, 520L, 52L, QuantityUnit.Mi);
		environmentSpec.setResources(envResources);

		when(environmentConfigProvider.environmentConfigFor(any(), any(), any())).thenReturn(Optional.of(environmentConfig));

		Path basePath = repoPath.resolve(cluster.getName()).resolve(environment.getName()).resolve(release.getName());
		Path templatesPath = basePath.resolve("templates");

		// when
		chartGenerator.generate(environmentRelease, releaseVersion, repoPath);

		// then
		assertFilesExist(basePath, "chart",
			"Chart.yaml",
			"values.yaml");

		assertFilesExist(templatesPath, "template",
			"configmap.yaml",
			"configmap-app1.yaml",
			"pvc.yaml",
			"pvc-app1.yaml",
			"deployment.yaml",
			"deployment-app1.yaml",
			"ingress.yaml",
			"ingress-app1.yaml",
			"service.yaml",
			"service-app1.yaml",
			"secret-dockerconfig.yaml",
			"secret-generic.yaml",
			"secret-generic-app1.yaml");

		Path valuesPath = basePath.resolve("values.yaml");
		log.info("Values: \n" + Files.readString(valuesPath));
		Values actualValues = yaml.loadAs(Files.newInputStream(valuesPath), Values.class);

		Map<String, EnvironmentVariableValues> expectedEnvironmentVariables = new LinkedHashMap<>();
		expectedEnvironmentVariables.put("DEV_ENV", new EnvironmentVariableValues("DEV_ENV", "VAR"));
		expectedEnvironmentVariables.put("SECRET_DEV_ENV", new EnvironmentVariableValues("SECRET_DEV_ENV", "my-secret", "secret-key"));

		Map<String, ApplicationConfigFileValues> configFiles = new LinkedHashMap<>();
		configFiles.put("application-config-file-c1fcd7e5", new ApplicationConfigFileValues("application-dev.properties", "anothergreeting=hello"));

		String configFileHashes = "{"
			+ "\"application-dev.properties\":\"" + HashUtil.hash("anothergreeting=hello")
			+ "\"}";

		Map<String, ApplicationVolumeValues> volumes = new LinkedHashMap<>();
		volumes.put("my-env-volume", new ApplicationVolumeValues("my-env-volume", "/opt/env-vol", "standard", "ReadWriteOnce", "2Gi"));
		volumes.put("my-env-secret-volume", new ApplicationVolumeValues("my-env-secret-volume", "/opt/env-secret-vol", "my-env-secret"));

		Map<String, ApplicationSecretValues> secrets = new LinkedHashMap<>();
		secrets.put("my-env-secret", new ApplicationSecretValues("my-env-secret", "Opague", Collections.singletonMap("env-secret-key", "env-secret-value")));
		secrets.put("my-tls-secret", new ApplicationSecretValues("my-tls-secret", "kubernetes.io/tls", Collections.singletonMap("cert", "my-cert")));

		ApplicationValues expectedApplicationValues = ApplicationValues.builder()
			.name("app1")
			.version("1.0.0")
			.image("image")
			.servicePortsEnabled(true)
			.servicePorts(servicePorts)
			.replicas(2)
			.routeHostname("myapp1-route")
			.routeTargetPort(8080)
			.tls(true)
			.tlsSecretName("my-tls-secret")
			.environmentVariablesEnabled(true)
			.environmentVariables(expectedEnvironmentVariables)
			.configFilesEnabled(true)
			.configFiles(configFiles)
			.configFileHashes(configFileHashes)
			.volumes(volumes)
			.secrets(secrets)
			.resources(new ResourcesValues(
				new ResourcesValues.ResourceValue("1200m", "120Mi"),
				new ResourcesValues.ResourceValue("520m", "52Mi")
			))
			.build();
		Map<String, ApplicationValues> expectedApps = new LinkedHashMap<>();
		expectedApps.put("app1", expectedApplicationValues);

		Values expectedValues = Values.builder()
			.host("my-host")
			.environment("dev")
			.releaseName("release1")
			.releaseVersion("1.0.0")
			.applications(expectedApps)
			.build();

		assertEquals(expectedValues, actualValues, "Values incorrect");
	}

	@Test
	public void generateBasicPruneTemplateFiles() throws Exception {
		// given
		Cluster cluster = new Cluster("test-cluster", "https://kubernetes.default.svc", ClusterType.KUBERNETES);
		Environment environment = new Environment("dev");
		cluster.addEnvironment(environment);
		Release release = new Release("release1");
		EnvironmentRelease environmentRelease = new EnvironmentRelease(environment, release);

		Application application = new Application("app1");
		ApplicationVersion applicationVersion = new ApplicationVersion("1.0.0", application);
		ReleaseVersion releaseVersion = new ReleaseVersion("1.0.0");
		release.addReleaseVersion(releaseVersion);
		releaseVersion.setApplicationVersions(Collections.singletonList(applicationVersion));

		when(environmentConfigProvider.environmentConfigFor(any(), any(), any())).thenReturn(Optional.empty());

		Path basePath = repoPath.resolve(cluster.getName()).resolve(environment.getName()).resolve(release.getName());
		Path templatesPath = basePath.resolve("templates");
		Files.createDirectories(templatesPath);
		Path extraTemplatePath = templatesPath.resolve("extratemplate.yaml");
		Files.writeString(extraTemplatePath, "test");

		// when
		chartGenerator.generate(environmentRelease, releaseVersion, repoPath);

		// then
		assertFalse(Files.exists(extraTemplatePath), "Extra template file should no longer exist");

		assertFilesExist(basePath, "chart",
			"Chart.yaml",
			"values.yaml");

		assertFilesExist(templatesPath, "template",
			"configmap.yaml",
			"pvc.yaml",
			"deployment.yaml",
			"deployment-app1.yaml",
			"ingress.yaml",
			"service.yaml",
			"secret-dockerconfig.yaml",
			"secret-generic.yaml");
	}

	private void assertFilesExist(Path path, String fileType, String... files) throws IOException {
		for (String file : files) {
			assertTrue(Files.exists(path.resolve(file)), "We should have a " + file + " " + fileType);
		}
		// make sure we don't have any more unexpected files
		assertEquals(files.length, Files.list(path).filter(Files::isRegularFile).count(), "Incorrect amount of " + fileType + " files");
	}
}
