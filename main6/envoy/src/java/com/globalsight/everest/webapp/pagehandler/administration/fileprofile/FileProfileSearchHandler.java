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
package com.globalsight.everest.webapp.pagehandler.administration.fileprofile;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Hashtable;


import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.globalsight.cxe.persistence.fileprofile.FileProfileEntityException;
import com.globalsight.everest.projecthandler.ProjectHandlerException;

import com.globalsight.everest.servlet.EnvoyServletException;
import com.globalsight.everest.servlet.util.ServerProxy;

import com.globalsight.everest.webapp.pagehandler.PageHandler;

import com.globalsight.everest.webapp.webnavigation.WebPageDescriptor;
import com.globalsight.util.GeneralException;
import com.globalsight.util.collections.HashtableValueOrderWalkerFactory;

public class FileProfileSearchHandler extends PageHandler
{

	/**
     * Invokes this PageHandler
     *
     * @param p_pageDescriptor the page desciptor
     * @param p_request the original request sent from the browser
     * @param p_response the original response object
     * @param p_context context the Servlet context 
     * @throws ServletException
     * @throws IOException
     */
	
	public void invokePageHandler(WebPageDescriptor p_pageDescriptor,
            HttpServletRequest p_request,
            HttpServletResponse p_response,
            ServletContext p_context)
    throws ServletException, IOException
    {
		
//      create hashtable of L10nProfile names and ids
        Hashtable ht=null;
		try {
			
			ht = ServerProxy.getProjectHandler().getAllL10nProfileNames();
			
		} catch (Exception e) {
			
			throw new EnvoyServletException(e);
			
		} 
        p_request.setAttribute("locProfiles",
            HashtableValueOrderWalkerFactory.createHashtableValueOrderWalker(ht));
//      format types
        try {
			p_request.setAttribute("formatTypes", 
			        ServerProxy.getFileProfilePersistenceManager().getAllKnownFormatTypes());
		} catch (Exception e) {
			
			throw new EnvoyServletException(e);
			
		}
       
        // Call parent invokePageHandler() to set link beans and invoke JSP
        super.invokePageHandler(p_pageDescriptor, p_request, p_response, p_context);
    
    }
}