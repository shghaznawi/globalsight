/**
 *  Copyright 2009 Welocalize, Inc. 
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
package com.globalsight.everest.webapp.pagehandler.projects.workflows;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.globalsight.everest.servlet.util.SessionManager;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TreeSet;

import java.rmi.RemoteException;

// globalsight
import com.globalsight.util.edit.EditUtil;
import com.globalsight.everest.webapp.pagehandler.PageHandler;
import com.globalsight.everest.jobhandler.Job;
import com.globalsight.everest.servlet.EnvoyServletException;
import com.globalsight.everest.webapp.WebAppConstants;
import com.globalsight.everest.webapp.javabean.NavigationBean;
import com.globalsight.everest.webapp.webnavigation.WebPageDescriptor;
import com.globalsight.everest.tm.searchreplace.JobSearchReportQueryResult;
import com.globalsight.everest.tm.searchreplace.JobInfo;

public class SearchJobsReplaceHandler extends PageHandler
{
    public static String NUM_PER_PAGE  = "numPerPage";
    public static String NUM_PAGES = "numPages";
    public static String SORTING = "sorting";
    public static String REVERSE_SORT = "reverseSort";
    public static String PAGE_NUM = "pageNum";
    public static String LAST_PAGE_NUM = "lastPageNum";
    public static String LIST_SIZE = "listSize";

    /**
     * Invokes this EntryPageHandler object
     * <p>
     * @param p_ageDescriptor the description of the page to be produced.
     * @param p_request original request sent from the browser.
     * @param p_response original response object.
     * @param p_context the Servlet context.
     */
    public void invokePageHandler(WebPageDescriptor pageDescriptor,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  ServletContext context)
    throws ServletException, IOException, RemoteException, EnvoyServletException
    {
        HttpSession session = request.getSession(false);
        SessionManager sessionMgr = (SessionManager)
            session.getAttribute(WebAppConstants.SESSION_MANAGER);
        ArrayList results = (ArrayList) sessionMgr.getAttribute("replaceResults");
        String newString = EditUtil.utf8ToUnicode((String)request.getParameter("newString"));
        if (newString != null)
        {
            // clear paging from session
            sessionMgr.removeElement(NUM_PER_PAGE);
            sessionMgr.removeElement(NUM_PAGES);
            sessionMgr.removeElement(SORTING);
            sessionMgr.removeElement(REVERSE_SORT);
            sessionMgr.removeElement(PAGE_NUM);
            sessionMgr.removeElement(LAST_PAGE_NUM);
            sessionMgr.removeElement(LIST_SIZE);

            // get values from form
            String isCaseSensitive = (String)
                sessionMgr.getAttribute("isCaseSensitive");
            String oldString = EditUtil.utf8ToUnicode((String)request.getParameter("oldString"));
            request.setAttribute("newString", newString);
            String jobInfosString = EditUtil.utf8ToUnicode((String)request.getParameter("jobInfos"));
            String[] jobs = jobInfosString.split(" ");
            List allJobInfos = (List)sessionMgr.getAttribute("searchResults");
            ArrayList jobInfos = new ArrayList();
            for (int i = 0; i < jobs.length; i++) 
            {
                jobInfos.add(allJobInfos.get(Integer.parseInt(jobs[i])));
            }
            
            // do the replace
            results = (ArrayList)
                 SearchHandlerHelper.replaceForPreview(oldString, newString,
                 jobInfos, new Boolean(isCaseSensitive).booleanValue());

            // set results in the session
            sessionMgr.setAttribute("replaceResults", results);
            if (results != null)
            {
                // save just the tuvinfos in the session
                ArrayList tuvInfos = new ArrayList();
                // Save all the tuvinfos in the session
                for (int i = 0; i < results.size(); i++)
                {
                    JobInfo jobInfo = (JobInfo) results.get(i);
                    tuvInfos.add(jobInfo.getTuvInfo());
                }
                sessionMgr.setAttribute("tuvInfos", tuvInfos);
            }
        }
        
        Locale locale = (Locale)session.getAttribute(WebAppConstants.UILOCALE);
        setTableNavigation(request, session,
                           results,
                           null,
                           10,   // change this to be configurable!
                           "numPerPage",
                           "numPages", "results",
                           "sorting",
                           "reverseSort",
                           "pageNum",
                           "lastPageNum",
                           "listSize");

        super.invokePageHandler(pageDescriptor, request, response, context);
    }
}