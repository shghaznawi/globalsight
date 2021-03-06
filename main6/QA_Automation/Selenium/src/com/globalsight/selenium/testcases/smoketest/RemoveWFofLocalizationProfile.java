package com.globalsight.selenium.testcases.smoketest;

import org.testng.annotations.Test;

import com.globalsight.selenium.functions.LocalizationFuncs;

import com.globalsight.selenium.pages.MainFrame;
import com.globalsight.selenium.testcases.BaseTestCase;

public class RemoveWFofLocalizationProfile extends BaseTestCase
{
    private LocalizationFuncs localizationFuncs = new LocalizationFuncs();

    @Test
    public void removeWorkflow() throws Exception
    {
        openMenuItemAndWait(selenium, MainFrame.SETUP_MENU,
                MainFrame.LOCALIZATION_PROFILES_SUBMENU);

        String iLocName = getProperty("localization.name");
        String iWFName = getProperty("localization.workflow");

        localizationFuncs.removeWorkflow(selenium, iLocName, iWFName);

    }
}
