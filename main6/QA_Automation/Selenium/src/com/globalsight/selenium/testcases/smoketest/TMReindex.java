package com.globalsight.selenium.testcases.smoketest;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.globalsight.selenium.functions.CommonFuncs;
import com.globalsight.selenium.functions.TMFuncs;
import com.globalsight.selenium.pages.MainFrame;
import com.globalsight.selenium.pages.TMManagement;
import com.globalsight.selenium.testcases.BaseTestCase;

public class TMReindex extends BaseTestCase
{
    TMFuncs iTMFuncs = new TMFuncs();

    @Test
    public void verifyReindex() throws Exception
    {
        openMenuItemAndWait(selenium, MainFrame.SETUP_MENU,
                MainFrame.TRANSLATION_MEMORY_SUBMENU);

        /*iTMFuncs.selectRadioButtonFromTable(selenium,
                TMManagement.TM_MANAGEMENT_TABLE, getProperty("tm.tmName"));*/

        clickAndWait(selenium, TMManagement.REINDEX_BUTTON);

        //selenium.click(TMManagement.SELECTED_TM_RADIO);
        clickAndWait(selenium,TMManagement.REINDEX_NEXT_BUTTON);

        clickAndWait(selenium, TMManagement.REINDEX_OK_BUTTON);
    }
}
