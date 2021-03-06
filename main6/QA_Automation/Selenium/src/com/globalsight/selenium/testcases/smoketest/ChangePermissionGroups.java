/**
 *  Copyright 2009, 2011 Welocalize, Inc. 
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
package com.globalsight.selenium.testcases.smoketest;

/*
 * TestCaseName: ChangePermissionGroups.java
 * Author:Jester
 * Tests:Change_PermissionGr oups()
 * 
 * History:
 * Date       Comments       Updater
 * 2011-6-8  First Version  Jester
 * 2012-12-25
 */

import org.testng.Assert;
import org.testng.annotations.Test;
import com.globalsight.selenium.functions.CommonFuncs;
import com.globalsight.selenium.functions.PermissionGroupsFuncs;
import com.globalsight.selenium.pages.MainFrame;
import com.globalsight.selenium.testcases.BaseTestCase;

public class ChangePermissionGroups extends BaseTestCase
{
    private PermissionGroupsFuncs permissionGroupsFuncs = new PermissionGroupsFuncs();

    @Test
    public void changePermissionGroups() throws Exception
    {
        openMenuItemAndWait(selenium, MainFrame.SETUP_MENU,
                MainFrame.PERMISSION_GROUPS_SUBMENU);

        permissionGroupsFuncs.editPermissionGroups(selenium,
                getProperty("permission.permissionGroup"), getProperty("permission.permissions"));

        CommonFuncs.logoutSystem(selenium);
        CommonFuncs.loginSystemWithAdmin(selenium);

        selenium.click(MainFrame.SETUP_MENU);
        Assert.assertEquals(
                selenium.isElementPresent(MainFrame.ATTRIBUTES_SUBMENU), true);
        
         /*selenium.click(MainFrame.Setup_MENU);
         selenium.click(MainFrame.PermissionGroups_SUBMENU);
         selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
         
         iPermissionGroupsFuncs.editPermissionGroups(selenium,
         ConfigUtil.getDataInCase(testCaseName, "GROUP"),
         ConfigUtil.getDataInCase(testCaseName, "PERMISSONPROFILE"));
         */
    }
}
