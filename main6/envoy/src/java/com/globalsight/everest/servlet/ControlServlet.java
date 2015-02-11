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

package com.globalsight.everest.servlet;

// Envoy packages
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.globalsight.everest.company.CompanyThreadLocal;
import com.globalsight.everest.foundation.User;
import com.globalsight.everest.servlet.util.AppletDirectory;
import com.globalsight.everest.servlet.util.SessionManager;
import com.globalsight.everest.util.system.AmbassadorServer;
import com.globalsight.everest.webapp.WebAppConstants;
import com.globalsight.everest.webapp.javabean.ErrorBean;
import com.globalsight.everest.webapp.pagehandler.ControlFlowHelper;
import com.globalsight.everest.webapp.pagehandler.PageHandler;
import com.globalsight.everest.webapp.pagehandler.PageHandlerFactory;
import com.globalsight.everest.webapp.pagehandler.administration.users.UserUtil;
import com.globalsight.everest.webapp.pagehandler.tasks.TaskFilter;
import com.globalsight.everest.webapp.webnavigation.LinkHelper;
import com.globalsight.everest.webapp.webnavigation.WebActivityDescriptor;
import com.globalsight.everest.webapp.webnavigation.WebPageDescriptor;
import com.globalsight.everest.webapp.webnavigation.WebSiteDescription;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.util.GeneralException;
import com.globalsight.util.j2ee.AppServerWrapper;
import com.globalsight.util.j2ee.AppServerWrapperFactory;
import com.globalsight.util.resourcebundle.LocaleWrapper;


public class ControlServlet extends HttpServlet
// implements SingleThreadModel
{
	
    private static final long serialVersionUID = 1L;

	private static final GlobalSightCategory CATEGORY = (GlobalSightCategory) GlobalSightCategory
            .getLogger(ControlServlet.class);

    private static AppServerWrapper s_appServerWrapper = AppServerWrapperFactory
            .getAppServerWrapper();

    //
    // Public Constants
    //
    static public final String ENTRY_PAGE = "LOG1";

    static public final String WELCOME_PAGE = "LOG4";    

    static public final String RETRIEVE_PAGE = "retrieve";

    // Actually should have our own page, id est, "You do not have
    // permission to perform the selected activity." LOG1 for now.
    static public final String NO_PERMISSION_PAGE = ENTRY_PAGE;
    

    //
    // Global Variables
    //

    // Caches the servlet context to make it accessible during
    // individual requests
    static private ServletContext m_servletContext = null;
    static
    {
        try
        {
            if (s_appServerWrapper.getJ2EEServerName().equals(
                    AppServerWrapperFactory.WEBSPHERE))
            {
                System.out
                        .println("-----------WebSphere -- GlobalSight starting up------------");
                AmbassadorServer.getAmbassadorServer().startup("GlobalSight",
                        null);
                System.out
                        .println("-----------WebSphere -- GlobalSight started ------------");
            }
            if (s_appServerWrapper.getJ2EEServerName().equals(
                    AppServerWrapperFactory.JBOSS))
            {
                System.out
                        .println("-----------JBOSS -- GlobalSight starting up------------");
                AmbassadorServer.getAmbassadorServer().startup("GlobalSight",
                        null);
                System.out
                        .println("-----------JBOSS -- GlobalSight started ------------");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Reads the XML navigation file (EnvoyConfig.xml).
     */
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        // cache the context
        synchronized (this)
        {
            m_servletContext = config.getServletContext();
        }

        // load the applet directory
        AppletDirectory.getInstance();

        // Read in the navigation configuration file, if needed (once
        // for all the Servlet instances).
        if (!WebSiteDescription.isInitialized())
        {
            if (!WebSiteDescription
                    .createSiteDescription(WebAppConstants.ENVOY_CONFIG_FILE))
            {
                CATEGORY.error("Error reading XML site description file."
                        + WebAppConstants.ENVOY_CONFIG_FILE);
            }
        }
    }

    /**
     * Clean up
     */
    public void destroy()
    {
        System.out.println("ControlServlet.destroy() called.");
        try
        {
            if (s_appServerWrapper.getJ2EEServerName().equals(
                    AppServerWrapperFactory.WEBSPHERE))
            {
                System.out
                        .println("-----------WebSphere -- GlobalSight shutting down------------");
                AmbassadorServer.getAmbassadorServer().shutdown("GlobalSight",
                        null);
                System.out
                        .println("-----------WebSphere -- GlobalSight stopped ------------");
            }
            if (s_appServerWrapper.getJ2EEServerName().equals(
                    AppServerWrapperFactory.JBOSS))
            {
                System.out
                        .println("-----------JBOSS -- GlobalSight shutting down------------");
                AmbassadorServer.getAmbassadorServer().shutdown("GlobalSight",
                        null);
                System.out
                        .println("-----------JBOSS -- GlobalSight stopped ------------");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Serve a GET request
     */
    public void doGet(HttpServletRequest p_request,
            HttpServletResponse p_response) throws ServletException,
            IOException
    {
        try
        {
            _doGet(p_request, p_response);
        }
        catch (SocketException se)
        {
            // GSDEF8496 -- special handling for socket exceptions so the
            // log file is not overwhelmed. This typically happens when the
            // user aborts the request out of impatience
            CATEGORY.error("Socket Exception2: " + se.getMessage());
        }
        catch (Exception e)
        {
            CATEGORY.error("Exception servicing request", e);
        }
    }

    /**
     * Dumps out the request parameter and attribute names and values to the
     * log, if debugging is on for the logging category.
     * 
     * @param p_request
     *            http request
     */
    private void dumpRequestValues(HttpServletRequest p_request)
    {
        if (CATEGORY.isDebugEnabled())
        {
            CATEGORY
                    .debug("\r\nDumping Request Attributes and Parameter Values");
            System.out.println("-------------------------------");
            System.out.println("HTTP request=" + p_request.toString());
            System.out.println("HTTP request URI=" + p_request.getRequestURI());
            System.out.println("HTTP content type="
                    + p_request.getContentType());

            Enumeration enumeration = p_request.getAttributeNames();
            while (enumeration.hasMoreElements())
            {
                String name = (String) enumeration.nextElement();
                Object value = (Object) p_request.getAttribute(name);
                System.out.println("attribute=" + name + ", value= " + value);
            }
            enumeration = p_request.getParameterNames();
            while (enumeration.hasMoreElements())
            {
                String name = (String) enumeration.nextElement();
                String value = (String) p_request.getParameter(name);
                System.out.println("parameter=" + name + ", value= " + value);
            }
        }
    }

    /**
     * Serve a GET request
     */
    private void _doGet(HttpServletRequest p_request,
            HttpServletResponse p_response) throws ServletException,
            IOException
    {
        if (AmbassadorServer.isSystem4Accessible() == false)
        {
            throw new ServletException(
                    "System4 is not yet accessible. It may be starting up or shutting down.");
        }

        
        dumpRequestValues(p_request);

        WebPageDescriptor targetPageDescriptor = null;
        WebPageDescriptor sourcePageDescriptor = null;
        HttpSession userSession = null;

        String isApplet = p_request.getParameter(WebAppConstants.APPLET);
        if (isApplet == null)
        {
            // attempt to access the session object
            userSession = p_request.getSession(false);
        }
        else
        {
            String rand = p_request
                    .getParameter(WebAppConstants.APPLET_DIRECTORY_SESSION_NAME_RANDOM);
            AppletDirectory directory = AppletDirectory.getInstance();
            userSession = directory.getSession(rand);
        }

        /*
         * TomyD -- I commented out isRequestedSessionIdValid call since for an
         * applet making two separate calls to servlet with different content
         * type, it returns false
         */
        // is there no user session in progress?
        if (!isLoginSession(userSession, p_request))
        {
            // If there is no user session, only three pages can be
            // invoked: entry and welcome, and the terminology viewer.
            String activityName = p_request.getParameter(LinkHelper.ACTIVITY_NAME);
            String pageName = p_request.getParameter(WebAppConstants.PAGE_NAME);

            if (activityName != null && activityName.equals("termviewer"))
            {
                WebActivityDescriptor activityDescriptor = WebSiteDescription
                        .instance().getActivityDescriptor(activityName);
                targetPageDescriptor = activityDescriptor
                        .getDefaultPageDescriptor();
            }
            else if (pageName!=null && pageName.equals(RETRIEVE_PAGE))
            {
            	targetPageDescriptor = WebSiteDescription.instance()
                .getPageDescriptor(RETRIEVE_PAGE);
            }
            // Determine if we came from the login page.
            else if (p_request.getParameter(WebAppConstants.LOGIN_NAME_FIELD) == null
                    && p_request.getParameter("ssoResponseData") == null)
            {
                // the entry page does not receive login parameters
                // retrieve the page descriptor for entry page (this is
                // a default)
                targetPageDescriptor = WebSiteDescription.instance()
                        .getPageDescriptor(ENTRY_PAGE);
            }
            else
            {
                // if so, proceed to login the user
                sourcePageDescriptor = WebSiteDescription.instance()
                        .getPageDescriptor(ENTRY_PAGE);

                // verify that there is no extra flow of control test
                PageHandler sourcePageHandler = null;
                try
                {
                    sourcePageHandler = getPageHandlerInstance(sourcePageDescriptor);
                }
                catch (EnvoyServletException e)
                {
                    CATEGORY.error("Problem getting sourcePageHandler "
                            + "(no user session exists): ", e);
                    reportErrorPage(isApplet != null, null, e, p_request,
                            p_response, m_servletContext, userSession,
                            sourcePageHandler);
                }

                ControlFlowHelper controlFlowHelper = sourcePageHandler
                        .getControlFlowHelper(p_request, p_response);

                String linkName = "";

                if (controlFlowHelper != null)
                {
                    try
                    {
                        linkName = controlFlowHelper.determineLinkToFollow();
                    }
                    catch (EnvoyServletException e)
                    {
                        CATEGORY.error("Problem determining link to follow "
                                + "(no user session exists): ", e);

                        reportErrorPage(isApplet != null, null, e, p_request,
                                p_response, m_servletContext, userSession,
                                sourcePageHandler);
                    }
                    catch (Throwable t)
                    {
                        CATEGORY.error("Throwable thrown when determining "
                                + "link to follow: " + t.getMessage() + "\r\n"
                                + GeneralException.getStackTraceString(t));

                        reportErrorPage(
                                isApplet != null,
                                null,
                                new EnvoyServletException(
                                        EnvoyServletException.EX_GENERAL,
                                        GeneralException.getStackTraceString(t)),
                                p_request, p_response, m_servletContext,
                                userSession, sourcePageHandler);
                    }
                }

                // determine the target page
                targetPageDescriptor = WebSiteDescription.instance()
                        .getPageDescriptor(
                                sourcePageDescriptor
                                        .getDestinationPageName(linkName));
            }
        }
        else
        // there is a session in progress
        {
            // Determine which is the next page to be visited.
            // There are two options:
            // -- 1. the Http request includes information about the
            // source page and the link to be traversed
            // -- 2. the request only includes the name of an activity
            // to be visited (the request was caused by a menu
            // selection).

            // retrieve the name of the sourcePage and link.
            String linkName = null;
            String sourcePageName = p_request
                    .getParameter(LinkHelper.PAGE_NAME);

            // Multi-Company: get current user's company from the session
            // String companyName =
            // (String)userSession.getAttribute(WebAppConstants.SELECTED_COMPANY_NAME_FOR_SUPER_PM);
            // if (UserUtil.isBlank(companyName))
            // {
            // companyName =
            // (String)userSession.getAttribute(UserLdapHelper.LDAP_ATTR_COMPANY);
            // }
            String companyName = UserUtil.getCurrentCompanyName(p_request);
            if (companyName != null)
            {
                CompanyThreadLocal.getInstance().setValue(companyName);
            }

            if (CATEGORY.isDebugEnabled())
            {
                CATEGORY.debug("sourcePageName=" + sourcePageName);
            }

            // if the page name and link name exist, get the source
            // page descriptor
            if (sourcePageName != null)
            {
                sourcePageDescriptor = WebSiteDescription.instance()
                        .getPageDescriptor(sourcePageName);

                // verify that there is no extra flow of control test
                PageHandler sourcePageHandler = null;
                try
                {
                    sourcePageHandler = getPageHandlerInstance(sourcePageDescriptor);
                }
                catch (EnvoyServletException e)
                {
                    CATEGORY.error("Problem getting sourcePageHandler "
                            + "(user session exists): ", e);

                    reportErrorPage(isApplet != null, null, e, p_request,
                            p_response, m_servletContext, userSession,
                            sourcePageHandler);
                }

                ControlFlowHelper controlFlowHelper = sourcePageHandler
                        .getControlFlowHelper(p_request, p_response);

                if (CATEGORY.isDebugEnabled())
                {
                    CATEGORY.debug("controlFlowHelper=" + controlFlowHelper);
                }

                if (controlFlowHelper != null)
                {
                    try
                    {
                        linkName = controlFlowHelper.determineLinkToFollow();
                    }
                    catch (EnvoyServletException e)
                    {
                        CATEGORY.error("Problem determining link to follow "
                                + "(user session exists): ", e);

                        reportErrorPage(isApplet != null, null, e, p_request,
                                p_response, m_servletContext, userSession,
                                sourcePageHandler);
                    }
                    catch (Throwable t)
                    {
                        CATEGORY.error("Throwable thrown when determining "
                                + "link to follow: " + t.getMessage() + "\r\n"
                                + GeneralException.getStackTraceString(t));

                        reportErrorPage(
                                isApplet != null,
                                null,
                                new EnvoyServletException(
                                        EnvoyServletException.EX_GENERAL,
                                        GeneralException.getStackTraceString(t)),
                                p_request, p_response, m_servletContext,
                                userSession, sourcePageHandler);
                    }
                }
                else
                {
                    linkName = p_request.getParameter(LinkHelper.LINK_NAME);
                }

                // determine the target page
                targetPageDescriptor = WebSiteDescription.instance()
                        .getPageDescriptor(
                                sourcePageDescriptor
                                        .getDestinationPageName(linkName));

                if (CATEGORY.isDebugEnabled())
                {
                    CATEGORY.debug("linkName=" + linkName);
                }
            }
            else
            {
                // if the page name does not exist, look for an
                // activity name -- that is how the menus items are
                // defined
                String activityName = p_request
                        .getParameter(LinkHelper.ACTIVITY_NAME);

                if (CATEGORY.isDebugEnabled())
                {
                    CATEGORY.debug("activityName=" + activityName);
                }

                // if the activity name is found, retrieve the default
                // page within the activity
                if (activityName != null)
                {
                    WebActivityDescriptor activityDescriptor = WebSiteDescription
                            .instance().getActivityDescriptor(activityName);
                    targetPageDescriptor = activityDescriptor
                            .getDefaultPageDescriptor();

                    // now clean up session manager, but only if we
                    // change to an activity that is a "normal" one.
                    // when requesting a "long-ranging activity" like
                    // the termviewer, which is a standalone window
                    // that is opened *during* an activity but does
                    // not end it, then don't clear out the state of
                    // the current activity.
                    if (isApplet == null
                            && activityDescriptor.shouldClearSession())
                    {
                        SessionManager sessionMgr = (SessionManager) userSession
                                .getAttribute(WebAppConstants.SESSION_MANAGER);
                        if (sessionMgr != null)
                        {
                            sessionMgr.clear();
                        }
                    }
                    else
                    {
                        if (CATEGORY.isDebugEnabled())
                        {
                            CATEGORY.debug("NOT CLEARING SESSION FOR: "
                                    + activityDescriptor.getActivityName());
                        }
                    }
                }
                else
                {
                    // there is no page name or activity name but
                    // there is a session, then goto the WELCOME page
                    targetPageDescriptor = WebSiteDescription.instance()
                            .getPageDescriptor(WELCOME_PAGE);
                }
            }
        }

        if (CATEGORY.isDebugEnabled())
        {
            CATEGORY.debug("targetPageDescriptor="
                    + targetPageDescriptor.toString());
        }

        // no target page is found
        if (targetPageDescriptor == null)
        {
            CATEGORY.error("Target page not found "
                    + getRequestParameters(p_request));

            // report page not found error
            reportErrorPage(isApplet != null, "Target page not found", null,
                    p_request, p_response, m_servletContext, userSession, null);
            return;
        }

        PageHandler targetPageHandler = null;
        try
        {
            // process the page using the correct page handler
            targetPageHandler = getPageHandlerInstance(targetPageDescriptor);
            String initial = p_request
                    .getParameter(WebAppConstants.INITIAL_SCREEN);

            if (isApplet == null || initial != null)
            {

                if (!TaskFilter.doFilter(targetPageHandler, p_request, p_response,
                        m_servletContext))
                {
                    return;
                }
                
                // for non-applet pages or applet to applet
                if (CATEGORY.isDebugEnabled())
                {
                    System.out.println("PageHandler="
                            + targetPageHandler.getClass().getName());
                    System.out.println("JSP="
                            + targetPageDescriptor.getJspURL());
                }
                targetPageHandler.invokePageHandler(targetPageDescriptor,
                        p_request, p_response, m_servletContext);
            }
            else
            {
                // only applet
                // Fix for Defect 4279 & 3738
                // Passing a parameter to define the request is POST
                // and not GET. We should probably be using
                // HttpURLConnection that has method to set the request
                // method.
                //
                // p_request.getInputStream().available() == 0;
                boolean isDoGet = p_request.getParameter("doPost") == null;
                if (CATEGORY.isDebugEnabled())
                {
                    System.out.println("AppletPageHandler="
                            + targetPageHandler.getClass().getName());
                    System.out.println("AppletJSP="
                            + targetPageDescriptor.getJspURL());
                }

                Vector objs = targetPageHandler.invokePageHandlerForApplet(
                        isDoGet, targetPageDescriptor, p_request,
                        p_response, m_servletContext, userSession);

                Locale uiLocale = null;
                if (userSession != null)
                {
                    uiLocale = (Locale) userSession
                            .getAttribute(WebAppConstants.UILOCALE);
                }

                outputToApplet(objs, uiLocale, p_response);
            }
        }
        catch (EnvoyServletException e)
        {
            CATEGORY.error("Problem invoking targetPageHandler (isApplet="
                    + isApplet + "): ", e);
            reportErrorPage(isApplet != null, null, e, p_request,
                    p_response, m_servletContext, userSession,
                    targetPageHandler);
        }
        catch (SocketException se)
        {
            // GSDEF8496 -- special handling for socket exceptions so the
            // log file is not overwhelmed. This typically happens when the
            // user aborts the request out of impatience
            CATEGORY.error("Socket Exception: " + se.getMessage());
        }
        catch (Throwable t)
        {
            String pageHandlerClassName = null;
            String pageName = null;
            if (targetPageDescriptor != null)
            {
                pageHandlerClassName = targetPageDescriptor
                        .getPageHandlerClassName();
                pageName = targetPageDescriptor.getPageName();
            }

            CATEGORY.error(
                    "Throwable thrown while invoking targetPageHandler["
                            + pageHandlerClassName + "," + pageName
                            + "](isApplet=" + isApplet + "): "
                            + t.getMessage(), t);

            // print out all the parameters
            Enumeration e = p_request.getParameterNames();
            String param = null;
            while (e.hasMoreElements())
            {
                param = (String) e.nextElement();
                CATEGORY.error("parameter: " + param + "="
                        + p_request.getParameter(param));
            }

            reportErrorPage(isApplet != null, null,
                    new EnvoyServletException(
                            EnvoyServletException.EX_GENERAL,
                            GeneralException.getStackTraceString(t)),
                    p_request, p_response, m_servletContext, userSession,
                    targetPageHandler);
        }
        
    }

    /**
     * Serve a POST request
     */
    public void doPost(HttpServletRequest p_request,
            HttpServletResponse p_response) throws ServletException,
            IOException
    {
        doGet(p_request, p_response);
    }

    /*
     * Creates an instance of the PageHandler class that corresponds to the
     * pageDescriptor argument.
     * 
     * @param pageDescriptor a page descriptor for a given web page
     * 
     * @return a page handler instance appropriate to the page descriptor
     */
    private PageHandler getPageHandlerInstance(WebPageDescriptor pageDescriptor)
            throws EnvoyServletException
    {
        return PageHandlerFactory.getPageHandlerInstance(pageDescriptor
                .getPageHandlerClassName());
    }

    // write back to applet
    private void outputToApplet(Vector p_objects, Locale p_uiLocale,
            HttpServletResponse p_response) throws IOException
    {
        ObjectOutputStream outputToApplet = new ObjectOutputStream(p_response
                .getOutputStream());

        outputToApplet.writeObject(p_objects);
        outputToApplet.writeObject(p_uiLocale);

        outputToApplet.flush();
        outputToApplet.close();
    }

  

    /**
     * Reports an error using a generic error page.
     */
    private void reportErrorPage(boolean p_isApplet, String p_message,
            EnvoyServletException p_exception, HttpServletRequest p_request,
            HttpServletResponse p_response, ServletContext p_context,
            HttpSession p_session, PageHandler p_pageHandler)
            throws ServletException, IOException
    {
        if (p_isApplet)
        {
            sendErrorToApplet(p_session, p_exception, p_response);
        }
        else
        {
            if (p_message == null)
            {
                p_message = p_exception.getMessage();
            }

            ErrorBean errorBean = new ErrorBean(0, p_message, p_exception);

            p_request.setAttribute(WebAppConstants.ERROR_BEAN_NAME, errorBean);

            if (p_pageHandler == null)
            {
                p_context.getRequestDispatcher(WebAppConstants.ERROR_PAGE)
                        .forward(p_request, p_response);
            }
            else
            {
                p_context.getRequestDispatcher(p_pageHandler.getErrorPage())
                        .forward(p_request, p_response);
            }
        }
    }

    // send a serializable ExceptionMessage object to the applet.
    @SuppressWarnings("unchecked")
    private void sendErrorToApplet(HttpSession p_session,
            EnvoyServletException p_exception, HttpServletResponse p_response)
            throws IOException
    {
        String message = p_exception.getTopLevelMessage();
        if (p_session != null)
        {
            SessionManager manager = (SessionManager) p_session
                    .getAttribute(WebAppConstants.SESSION_MANAGER);

            // Thu Dec 12 20:10:16 2002 CvdL: if an operation takes
            // very long, the session may have timed out. Send the
            // message in the default locale (meaning, don't fail
            // because of a NullPointerException, not here!).
            if (manager != null)
            {
                User user = (User) manager.getAttribute(WebAppConstants.USER);
                message = p_exception.getTopLevelMessage(LocaleWrapper
                        .getLocale(user.getDefaultUILocale()));
            }
        }

        Vector objs = new Vector();
        ExceptionMessage em = new ExceptionMessage(message, p_exception
                .getStackTraceString(), CATEGORY.isDebugEnabled());
        objs.addElement(em);

        Locale uiLocale = null;
        if (p_session != null)
        {
            uiLocale = (Locale) p_session
                    .getAttribute(WebAppConstants.UILOCALE);
        }

        outputToApplet(objs, uiLocale, p_response);
    }

    /**
     * Return String representation of request parameters. This is basically a
     * HttpServletRequest.toString() method.
     * 
     * @param p_request
     *            the request
     * @return representation of request parameters.
     */
    public static String getRequestParameters(HttpServletRequest p_request)
    {
        Enumeration enumeration = p_request.getParameterNames();
        String parameters = "";
        while (enumeration.hasMoreElements())
        {
            String name = (String) enumeration.nextElement();
            parameters = parameters + " " + name + "="
                    + p_request.getParameter(name);
        }

        return parameters;
    }

    private boolean isLoginSession(HttpSession p_userSession,
            HttpServletRequest p_request)
    {
        boolean isLogin = true;
        if (p_userSession == null)
        {
            isLogin = false;
        }
        else
        {
            String sessionUserName = (String) p_userSession
                    .getAttribute(WebAppConstants.USER_NAME);
            String loginFrom = p_request
                    .getParameter(WebAppConstants.LOGIN_FROM);
            if (sessionUserName == null || sessionUserName.length() == 0)
            {
                isLogin = false;
            }
            else if (loginFrom != null
                    && WebAppConstants.LOGIN_FROM_EMAIL.equals(loginFrom))
            {
                String loginName = p_request
                        .getParameter(WebAppConstants.LOGIN_NAME_FIELD);
                if (loginName != null && loginName.length() > 0
                        && !sessionUserName.equals(loginName))
                {
                    isLogin = false;
                }
            }
        }
        return isLogin;
    }
}