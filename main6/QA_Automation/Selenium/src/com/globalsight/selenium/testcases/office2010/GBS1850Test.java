/**
 *  Copyright 2009, 2012 Welocalize, Inc. 
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  
 *  You may obtain a copy of the License at 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */

package com.globalsight.selenium.testcases.office2010;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.globalsight.selenium.functions.FileProfileFuncs;
import com.globalsight.selenium.pages.FileProfile;
import com.globalsight.selenium.testcases.BaseTestCase;
import com.globalsight.selenium.testcases.util.SeleniumTestHelper;

/**
 * Automation test case for GBS-1850
 * w:r and w:t missing from 2010 export
 * 
 * Reporter         Andrew Gibbons 
 * Assignee         Donald Pan
 * Affects version  8.0
 * Fix version      None
 *
 * @author Vincent Yan
 * @date 2012/02/04
 */

public class GBS1850Test extends BaseTestCase {
	private boolean usingExist = false;
	private FileProfileFuncs fpFuncs = new FileProfileFuncs();

	@Test
	public void testGBS1850() throws Exception {
		String usingExistStr = getDataInCase("usingExist");
		usingExist = Boolean.parseBoolean(usingExistStr);

		//Add file profile
		FileProfile fileProfile = fpFuncs.getFileProfileInfo(testCaseName);
		SeleniumTestHelper.addFileProfile(selenium, fileProfile, usingExist);

		//Create job
		String jobName = getDataInCase("jobName");
		String filePaths = getDataInCase("filePaths");
		String fileProfiles = getDataInCase("fileProfiles");
		String targetLocales = getDataInCase("targetLocales");

		String createdJobName = SeleniumTestHelper.createJob(jobName, filePaths, fileProfiles, targetLocales);

		//Verify word count
		String expectedWordCount = getDataInCase("expectedWordCount");
		Assert.assertTrue(SeleniumTestHelper.verifyWordCount(selenium, createdJobName, expectedWordCount));

		//Verify segment count
		String expectedSegmentCount = getDataInCase("expectedSegmentCount");
		Assert.assertTrue(SeleniumTestHelper.verifySegmentCount(selenium, createdJobName, expectedSegmentCount));

		//Export job
		String workflow = getDataInCase("workflow");
		SeleniumTestHelper.exportJob(selenium, createdJobName, workflow);

		//Download and compare source and target files
		SeleniumTestHelper.downloadAndCompare(createdJobName, filePaths);
	}
}