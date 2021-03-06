package com.globalsight.selenium.functions;

/*
 * FileName: ProjectsFuncs.java
 * Author:Jester
 * Methods:ProjectNew()  
 * 
 * History:
 * Date       Comments       Updater
 * 2011-5-30  First Version  Jester
 */

import junit.framework.Assert;

import org.testng.Reporter;
import com.globalsight.selenium.pages.Projects;
import com.globalsight.selenium.pages.Users;
import com.globalsight.selenium.testcases.ConfigUtil;
import com.globalsight.selenium.testcases.util.SeleniumUtils;
import com.thoughtworks.selenium.Selenium;

public class ProjectsFuncs extends BasicFuncs {
	/**
	 * Create a new project with some value filled.
	 * PROJECT1=name=345,projectmanager=welocalize2amdin,termbase=jester,
	 * attributegroup=,description=welocalize,pmcost=10,
	 * porequired=true,available=superAdmin
	 */
	public String newProject(Selenium selenium, String projectProfiles)
			throws Exception {
		
        SeleniumUtils.clickAndWait(selenium, Projects.NEW_BUTTON);

        SeleniumUtils.select(selenium, Projects.PROJECT_MANAGER_SELECT,
                ConfigUtil.getConfigData("pm"), true);

		// input the values
		String[] iprojectProfile = projectProfiles.split(",");
		String iprojectName = null;

		for (String ivalues : iprojectProfile) {
			try {
				String[] ivalue = ivalues.split("=");
				String iFieldName = ivalue[0].trim();
				String iFieldValue = ivalue[1].trim();

				if (iFieldName.equals("projectname") && iFieldValue != null) {
					iprojectName = iFieldValue;
					selenium.type(Projects.Name_TEXT_FIELD, iFieldValue);
				} 
//				else if (iFieldName.equals("projectmanager")
//						&& iFieldValue != null) {
//					selenium.select(Projects.ProjectManager_SELECT, "label="
//							+ iFieldValue + " " + iFieldValue);
//				} 
				else if (iFieldName.equals("termbase") && iFieldValue != null) {
					selenium.select(Projects.TermBase_SELECT, "label="
							+ iFieldValue);
				}

				else if (iFieldName.equals("attributegroup")
						&& iFieldValue != null) {
					selenium.select(Projects.AttributeGroup_SELECT, "label="
							+ iFieldValue);
				} else if (iFieldName.equals("description")
						&& iFieldValue != null) {
					selenium.type(Projects.Description_TEXT_FIELD, iFieldValue);
				} else if (iFieldName.equals("pmcost") && iFieldValue != null) {
					selenium.type(Projects.PMCost_TEXT_FIELD, iFieldValue);
				} else if (iFieldName.equals("porequired")) {
					selenium.check(Projects.POrequired_CHECKBOX);
				} else if (iFieldName.equals("available")
						&& iFieldValue != null) {
					if (selenium.isElementPresent(Projects.Next_BUTTON)) {
						selenium.click(Projects.Next_BUTTON);
						if (selenium.isAlertPresent()) {
							// System.out.println(selenium.getAlert());
							Assert.assertEquals(
									"The Project Name you have entered already exists.  Please try a different Project Name.",
									selenium.getAlert());
							selenium.click(Projects.Cancel_BUTTON);
							Reporter.log("The project has already exists, please choose another different project name!");
						} else {
							// selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
							selenium.addSelection(Projects.Avavilable_FORM,
									iFieldValue);
							selenium.click(Projects.AddTO_BUTTON);
						}

					}
				}

			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}

		if (selenium.isElementPresent(Projects.SAVE_BUTTON)) {
			selenium.click(Projects.SAVE_BUTTON);
			selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
		}
		// to Check if the project has been created successful.

		return iprojectName;
	}
	
	//added by ShenYang  2011-06-27
	public void editProject(Selenium selenium, String iProjectName, String iNewProName, String iNewProManager) throws Exception {
		boolean selected = selectRadioButtonFromTable(selenium, Projects.PROJECT_TABLE, iProjectName);
        if (!selected)
        {
            Reporter.log("Cannot find a proper Project.");
            return;
        }
        try {
        	//selenium.click(Projects.EDIT_BUTTON);
        	selenium.click("link=" + iProjectName);
	        selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
        	selenium.type(Projects.Name_TEXT_FIELD, iNewProName);
	        selenium.select(Projects.PROJECT_MANAGER_SELECT, iNewProManager);
	        selenium.click(Projects.SAVE_BUTTON);
	        selenium.getConfirmation();
	     
		} catch (Exception e) {
			Reporter.log(e.getMessage());
			selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
		}
          Assert.assertEquals(
				isPresentInTable(selenium, Projects.PROJECT_TABLE, iNewProName),
				true);
	}
	
	//added by ShenYang  2011-06-27
	public void editProjectUser(Selenium selenium, String iProjectName, String adduser) throws Exception {
		boolean selected = selectRadioButtonFromTable(selenium, Projects.PROJECT_TABLE, iProjectName);
        if (!selected)
        {
            Reporter.log("Cannot find a proper Project.");
            return;
        }
        try {
        	//selenium.click(Projects.EDIT_BUTTON);
        	selenium.click("link=" + iProjectName);
	        selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
	        /*selenium.click(Projects.USER_BUTTON);
	        selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);*/
	        selenium.addSelection(Projects.Avavilable_FORM,	adduser);
	        selenium.click(Projects.AddTO_BUTTON);
	        /*selenium.click(Projects.USER_DONE_BUTTON);
	        selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);*/
	        selenium.click(Projects.SAVE_BUTTON);
	        selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
	        
	}catch (Exception e) {
		Reporter.log(e.getMessage());
		selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
	}
	}
	
	//added by ShenYang  2011-06-28
	public void editProjectTermbase(Selenium selenium, String iProjectName, String iTBName) throws Exception {
		boolean selected = selectRadioButtonFromTable(selenium, Projects.PROJECT_TABLE, iProjectName);
		String strn = this.getColumnText(selenium, Projects.PROJECT_TABLE, iProjectName, 4);
        if (!selected)
        {
            Reporter.log("Cannot find a proper Project.");
            return;
        }
        try {
        	//selenium.click(Projects.EDIT_BUTTON);
        	selenium.click("link=" + iProjectName);
	        selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
	        selenium.select(Projects.TermBase_SELECT, iTBName);
	        selenium.click(Projects.SAVE_BUTTON);
	        if(selenium.isElementPresent(Projects.Error_MSG_DIV))
	        {	
	        	if(selenium.getText(Projects.Error_MSG_DIV)!=null)
	        	{
	        		selenium.click(Projects.Cancel_BUTTON);
	        		selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
	        		//verify
	        		Assert.assertEquals(strn, this.getColumnText(selenium, Projects.PROJECT_TABLE, iProjectName, 4));
	        	}
	        	else
	        		{
	        			selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
	        			//verify
	        			if(iTBName=="No Termbase Selected")
	        			Assert.assertEquals(this.getColumnText(selenium,
	        					Projects.PROJECT_TABLE, iProjectName, 4), null);
	        			else Assert.assertEquals(this.getColumnText(selenium,
	        					Projects.PROJECT_TABLE, iProjectName, 4), iTBName);	
	        		}
	        }
	        	        
	}
        catch (Exception e) {
    		Reporter.log(e.getMessage());
    		selenium.waitForPageToLoad(CommonFuncs.SHORT_WAIT);
        					}
	}
	
    public boolean selectProject(Selenium selenium, String projectName)
            throws Exception
    {
        BasicFuncs basicFuncs = new BasicFuncs();
        return basicFuncs.selectRadioButtonFromTable(selenium,
                Projects.PROJECT_TABLE, projectName);
    }
}