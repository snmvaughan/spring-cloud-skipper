/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.cloud.skipper.controller;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.skipper.AbstractMockMvcTests;
import org.springframework.cloud.skipper.config.SkipperServerProperties;
import org.springframework.cloud.skipper.domain.PackageMetadata;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.StatusCode;
import org.springframework.cloud.skipper.domain.skipperpackage.DeployProperties;
import org.springframework.cloud.skipper.repository.PackageMetadataRepository;
import org.springframework.cloud.skipper.repository.ReleaseRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 */
@ActiveProfiles("repo-test")
@TestPropertySource(properties = { "spring.cloud.skipper.server.synchonizeIndexOnContextRefresh=true",
		"spring.cloud.skipper.server.platform.local.accounts[test].key=value",
		"maven.remote-repositories.repo1.url=http://repo.spring.io/libs-snapshot" })
public class ReleaseControllerTests extends AbstractMockMvcTests {

	@Autowired
	private PackageMetadataRepository packageMetadataRepository;

	@Autowired
	private ReleaseRepository releaseRepository;

	@Autowired
	private SkipperServerProperties skipperServerProperties;

	@Before
	public void cleanupPackageDir() {
		File packageDirectory = new File(skipperServerProperties.getPackageDir());
		FileSystemUtils.deleteRecursively(new File(skipperServerProperties.getPackageDir()));
		assertThat(packageDirectory).doesNotExist();
	}

	@Test
	public void checkDeployStatus() throws Exception {
		String packageName = "log";
		String releaseName = "log-sink-app";
		String initialVersion = "1.0.0";
		DeployProperties deployProperties = new DeployProperties();
		deployProperties.setPlatformName("test");
		deployProperties.setReleaseName(releaseName);
		PackageMetadata packageMetadata = this.packageMetadataRepository.findByNameAndVersion(packageName,
				initialVersion);
		mockMvc.perform(post("/package/" + packageMetadata.getId() + "/deploy")
				.content(convertObjectToJson(deployProperties))).andDo(print())
				.andExpect(status().isCreated()).andReturn();
		CountDownLatch latch = new CountDownLatch(1);
		long startTime = System.currentTimeMillis();
		while (!isDeployed(releaseName, initialVersion)
				|| (System.currentTimeMillis() - startTime) < 12000) {
			Thread.sleep(10000);
		}
		if (isDeployed(releaseName, initialVersion)) {
			latch.countDown();
		}
		assertThat(latch.await(1, TimeUnit.SECONDS)).describedAs("Status check timed out").isTrue();
		// Undeploy
		mockMvc.perform(post("/package/undeploy/" + releaseName + "/" + initialVersion)).andDo(print())
				.andExpect(status().isCreated()).andReturn();
		Release undeployedRelease = this.releaseRepository.findByNameAndVersion(releaseName, initialVersion);
		assertThat(undeployedRelease.getInfo().getStatus().getStatusCode()).isEqualTo(StatusCode.DELETED);
	}

	private boolean isDeployed(String releaseName, String version) {
		try {
			MvcResult result = mockMvc.perform(get(String.format("/release/status/%s/%s", releaseName, version)))
					.andDo(print()).andReturn();
			String content = result.getResponse().getContentAsString();
			return content.startsWith(getSuccessStatus(releaseName, version));
		}
		catch (Exception e) {
			return false;
		}
	}

	private String getSuccessStatus(String release, String version) {
		return "{\"name\":\"" + release + "\",\"version\":\"" + version + "\","
				+ "\"info\":{\"status\":{\"statusCode\":\"DEPLOYED\","
				+ "\"platformStatus\":\"All the applications are deployed successfully.";
	}
}