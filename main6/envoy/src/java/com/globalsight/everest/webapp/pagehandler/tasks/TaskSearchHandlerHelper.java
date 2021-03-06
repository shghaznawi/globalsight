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
package com.globalsight.everest.webapp.pagehandler.tasks;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.globalsight.everest.servlet.util.SessionManager;
import com.globalsight.everest.webapp.WebAppConstants;
import com.globalsight.everest.webapp.pagehandler.projects.workflows.JobSearchConstants;

public class TaskSearchHandlerHelper
{
    /**
     * Return the job search cookie for this user. First look in the session. If
     * not there, look in the filesystem. There are two search types, one for
     * the mini search, and one for the advanced search. Determine which to get
     * by the LAST_JOB_SEARCH_TYPE in the session. Default is advanced search.
     */
    static public Cookie getTaskSearchCookie(HttpSession session,
            HttpServletRequest request)
    {
        SessionManager sessionMgr = (SessionManager) session
                .getAttribute(WebAppConstants.SESSION_MANAGER);
        String searchType = (String) session
                .getAttribute(JobSearchConstants.LAST_TASK_SEARCH_TYPE);
        String userName = (String) session
                .getAttribute(WebAppConstants.USER_NAME);
        String cookieName = searchType + userName.hashCode();
        Cookie jobSearchCookie = (Cookie) sessionMgr.getAttribute(cookieName);
        if (jobSearchCookie != null)
            return jobSearchCookie;

        Cookie[] cookies = (Cookie[]) request.getCookies();
        if (cookies != null)
        {
            for (int i = 0; i < cookies.length; i++)
            {
                Cookie cookie = (Cookie) cookies[i];
                if (cookie.getName().equals(cookieName))
                {
                    return cookie;
                }
            }
        }
        return null;
    }
}
