package com.globalsight.selenium.testcases.smoketest;

import java.io.File;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.globalsight.selenium.functions.CommonFuncs;
import com.globalsight.selenium.functions.DownloadFileRead.FileRead;
import com.globalsight.selenium.pages.MainFrame;
import com.globalsight.selenium.pages.ReviewersCommentsReportWebForm;
import com.globalsight.selenium.testcases.ConfigUtil;
import com.thoughtworks.selenium.Selenium;

/**
 * Reviewers Comments Report
 * 
 * @author leon
 */
public class ReviewersCommentsReport
{
    private Selenium selenium;

    @BeforeClass
    public void beforeClass()
    {
        selenium = CommonFuncs.getSelenium();
        CommonFuncs.loginSystemWithAdmin(selenium);
    }

    @AfterClass
    public void afterClass()
    {
        selenium.stop();
    }

    @Test
    public void generateReport()
    {
        selenium.click(MainFrame.REPORTS_MENU);
        selenium.click(MainFrame.REPORTS_MAIN_SUBMENU);
        selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
        selenium.click(ReviewersCommentsReportWebForm.REPORT_LINK);

        selenium.waitForPopUp(ReviewersCommentsReportWebForm.POPUP_WINDOW_NAME,
                CommonFuncs.SHORT_WAIT);
        selenium.selectWindow("name="
                + ReviewersCommentsReportWebForm.POPUP_WINDOW_NAME);

        initOptions();

        selenium.click(ReviewersCommentsReportWebForm.SUBMIT_BUTTON);

        // Wait for the download progress finish.
        try
        {
            Thread.sleep((long) 10000);
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Verify the file exists or not
        FileRead fileRead = new FileRead();
        File file = fileRead
                .getFile(ReviewersCommentsReportWebForm.REPORT_FILE_NAME);
        Assert.assertTrue(file.exists());
        // Moved the file to the sub folder.
        fileRead.moveFile(file);
    }

    /**
     * Init the options of the report
     */
    private void initOptions()
    {
        String className = getClass().getName();
        // JobName if needed
        // String jobName = ConfigUtil.getDataInCase(className, "jobName");
        String targetLocale = ConfigUtil.getDataInCase(className,
                "targetLocale");
        String displayFormat = ConfigUtil.getDataInCase(className,
                "displayFormat");

        // selenium.select(ReviewersCommentsReportWebForm.JOBID, "label="+
        // jobName);
        selenium.select(ReviewersCommentsReportWebForm.TARGETLOCALE_SELECTOR,
                "label=" + targetLocale);
        // Time Format
        selenium.select(ReviewersCommentsReportWebForm.DATEFORMAT, "label="
                + displayFormat);
    }
}