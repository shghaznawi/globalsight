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


import com.globalsight.everest.jobhandler.Job;
import com.globalsight.everest.servlet.EnvoyServletException;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.servlet.util.SessionManager;
import com.globalsight.everest.webapp.WebAppConstants;
import com.globalsight.everest.webapp.pagehandler.ControlFlowHelper;
import com.globalsight.everest.webapp.pagehandler.PageHandler;
import com.globalsight.everest.vendormanagement.VendorException;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.util.GeneralException;
import java.io.IOException;
import java.rmi.RemoteException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Dispatches the user to the correct JSP.
 */
class JobSearchControlFlowHelper
    implements ControlFlowHelper, WebAppConstants
{
    private static final GlobalSightCategory CATEGORY =
        (GlobalSightCategory) GlobalSightCategory.getLogger(
            JobSearchControlFlowHelper.class);

    // local variables
    private HttpServletRequest m_request = null;
    private HttpServletResponse m_response = null;

    public JobSearchControlFlowHelper(HttpServletRequest p_request,
        HttpServletResponse p_response)
    {
        m_request = p_request;
        m_response = p_response;
    }

    /**
     * Does the processing then 
     * returns the name of the link to follow
     * 
     * @return 
     * @exception EnvoyServletException
     */
    public String determineLinkToFollow()
        throws EnvoyServletException
    {
        HttpSession p_session = m_request.getSession(false);
        String destinationPage = null;
        String fromRequest = (String) m_request.getParameter("fromRequest");
        String searchType = (String) m_request.getParameter("searchType");

        // searchType should only be null for paging purposes
        if (searchType == null || searchType.equals("search") ||
            fromRequest == null)
        {
            destinationPage = (String)m_request.getParameter("linkName");
        }
        else if (fromRequest != null &&
             (searchType.equals(JobSearchConstants.JOB_SEARCH_COOKIE) ||
                 searchType.equals(JobSearchConstants.MINI_JOB_SEARCH_COOKIE)))
        {  
            HttpSession session = m_request.getSession(false);
            SessionManager sessionMgr =
                (SessionManager)session.getAttribute(SESSION_MANAGER);
            String status = (String) m_request.getParameter(JobSearchConstants.STATUS_OPTIONS);
            if (status.equals(Job.PENDING))
                 destinationPage = "pending";
            else if (status.equals(Job.READY_TO_BE_DISPATCHED))
                destinationPage = "ready";
            else if (status.equals(Job.DISPATCHED))
                destinationPage = "progress";
            else if (status.equals(Job.LOCALIZED))
                destinationPage = "complete";
            else if (status.equals(Job.DTPINPROGRESS))
                destinationPage = "dtpprogress";
            else if (status.equals(Job.ARCHIVED))
                destinationPage = "archived";
            else if (status.equals(Job.EXPORTED))
                destinationPage = "exported";
            else
            	destinationPage = "allStatus";
        }
        else if (searchType.equals("goToSearch"))
        {
            destinationPage = "jobSearch";
        }
        return destinationPage;
    }
}
