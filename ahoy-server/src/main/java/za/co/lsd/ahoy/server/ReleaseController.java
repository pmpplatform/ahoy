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

package za.co.lsd.ahoy.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.lsd.ahoy.server.environmentrelease.EnvironmentRelease;
import za.co.lsd.ahoy.server.environmentrelease.EnvironmentReleaseId;
import za.co.lsd.ahoy.server.environmentrelease.EnvironmentReleaseRepository;
import za.co.lsd.ahoy.server.releases.PromoteOptions;
import za.co.lsd.ahoy.server.releases.ReleaseVersion;
import za.co.lsd.ahoy.server.releases.ReleaseVersionRepository;
import za.co.lsd.ahoy.server.releases.UpgradeOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/api/release")
@Slf4j
public class ReleaseController {
	private final EnvironmentReleaseRepository environmentReleaseRepository;
	private final ReleaseVersionRepository releaseVersionRepository;
	private final ReleaseService releaseService;

	public ReleaseController(EnvironmentReleaseRepository environmentReleaseRepository, ReleaseVersionRepository releaseVersionRepository, ReleaseService releaseService) {
		this.environmentReleaseRepository = environmentReleaseRepository;
		this.releaseVersionRepository = releaseVersionRepository;
		this.releaseService = releaseService;
	}

	@PostMapping("/deploy/{environmentId}/{releaseId}/{releaseVersionId}")
	public ResponseEntity<EnvironmentRelease> deploy(@PathVariable Long environmentId,
													 @PathVariable Long releaseId,
													 @PathVariable Long releaseVersionId,
													 @RequestBody DeployDetails deployDetails) throws ExecutionException, InterruptedException {

		EnvironmentReleaseId environmentReleaseId = new EnvironmentReleaseId(environmentId, releaseId);

		EnvironmentRelease environmentRelease = environmentReleaseRepository.findById(environmentReleaseId)
			.orElseThrow(() -> new ResourceNotFoundException("Could not find environment release: " + environmentReleaseId));

		ReleaseVersion releaseVersion = releaseVersionRepository.findById(releaseVersionId)
			.orElseThrow(() -> new ResourceNotFoundException("Could not find releaseVersion in release, releaseVersionId: " + releaseVersionId));

		Future<EnvironmentRelease> deployedEnvironmentRelease = releaseService.deploy(environmentRelease, releaseVersion, deployDetails);
		return new ResponseEntity<>(deployedEnvironmentRelease.get(), new HttpHeaders(), HttpStatus.OK);
	}

	@PostMapping("/undeploy/{environmentId}/{releaseId}")
	public ResponseEntity<EnvironmentRelease> undeploy(@PathVariable Long environmentId,
													   @PathVariable Long releaseId) throws ExecutionException, InterruptedException {

		EnvironmentReleaseId environmentReleaseId = new EnvironmentReleaseId(environmentId, releaseId);

		EnvironmentRelease environmentRelease = environmentReleaseRepository.findById(environmentReleaseId)
			.orElseThrow(() -> new ResourceNotFoundException("Could not find environment release: " + environmentReleaseId));

		Future<EnvironmentRelease> undeployedEnvironmentRelease = releaseService.undeploy(environmentRelease);
		return new ResponseEntity<>(undeployedEnvironmentRelease.get(), new HttpHeaders(), HttpStatus.OK);
	}

	@PostMapping("/promote/{environmentId}/{releaseId}")
	public ResponseEntity<EnvironmentRelease> promote(@PathVariable Long environmentId, @PathVariable Long releaseId,
													  @RequestBody PromoteOptions promoteOptions) {
		EnvironmentRelease promotedEnvironmentRelease = releaseService.promote(environmentId, releaseId, promoteOptions);
		return new ResponseEntity<>(promotedEnvironmentRelease, new HttpHeaders(), HttpStatus.OK);
	}

	@PostMapping("/upgrade/{releaseVersionId}")
	public ResponseEntity<ReleaseVersion> upgrade(@PathVariable Long releaseVersionId,
												  @RequestBody UpgradeOptions upgradeOptions) {
		ReleaseVersion upgradedReleaseVersion = releaseService.upgrade(releaseVersionId, upgradeOptions);
		return new ResponseEntity<>(upgradedReleaseVersion, new HttpHeaders(), HttpStatus.OK);
	}

	@PostMapping("/remove/{environmentId}/{releaseId}")
	public ResponseEntity<EnvironmentRelease> remove(@PathVariable Long environmentId,
													 @PathVariable Long releaseId) throws ExecutionException, InterruptedException {

		EnvironmentReleaseId environmentReleaseId = new EnvironmentReleaseId(environmentId, releaseId);

		Future<EnvironmentRelease> removedEnvironmentRelease = releaseService.remove(environmentReleaseId);
		return new ResponseEntity<>(removedEnvironmentRelease.get(), new HttpHeaders(), HttpStatus.OK);
	}

	@PostMapping("/copyEnvConfig/{environmentId}/{releaseId}/{sourceReleaseVersionId}/{destReleaseVersionId}")
	public ResponseEntity<EnvironmentRelease> copyEnvConfig(@PathVariable Long environmentId, @PathVariable Long releaseId, @PathVariable Long sourceReleaseVersionId, @PathVariable Long destReleaseVersionId) {
		EnvironmentRelease environmentRelease = releaseService.copyEnvConfig(environmentId, releaseId, sourceReleaseVersionId, destReleaseVersionId);
		return new ResponseEntity<>(environmentRelease, new HttpHeaders(), HttpStatus.OK);
	}
}
