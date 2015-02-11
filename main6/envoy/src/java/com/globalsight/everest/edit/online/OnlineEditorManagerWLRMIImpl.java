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

package com.globalsight.everest.edit.online;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import com.globalsight.everest.integration.ling.tm2.MatchTypeStatistics;
import com.globalsight.everest.util.system.RemoteServer;
import com.globalsight.everest.webapp.pagehandler.edit.online.EditorState;
import com.globalsight.util.GeneralException;
import com.globalsight.util.GlobalSightLocale;

public class OnlineEditorManagerWLRMIImpl
    extends RemoteServer
    implements OnlineEditorManagerWLRemote
{
    OnlineEditorManager m_localReference;

    public OnlineEditorManagerWLRMIImpl()
        throws RemoteException, OnlineEditorException
    {
        super(OnlineEditorManager.SERVICE_NAME);
        m_localReference = new OnlineEditorManagerLocal();
    }

    OnlineEditorManagerWLRMIImpl(OnlineEditorManager p_lm)
        throws RemoteException
    {
        super();
        m_localReference = p_lm;
    }

    public OnlineEditorManager newInstance()
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.newInstance();
    }

    public Object getLocalReference()
    {
        return m_localReference;
    }

    public String getSourcePageGxml(long p_srcPageId)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getSourcePageGxml(p_srcPageId);
    }

    public ArrayList validateSourcePageGxml(String p_gxml)
        throws RemoteException
    {
        return m_localReference.validateSourcePageGxml(p_gxml);
    }

    public ArrayList updateSourcePageGxml(long p_srcPageId, String p_gxml)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.updateSourcePageGxml(p_srcPageId, p_gxml);
    }

    public String getGxmlPreview(String p_gxml, String p_locale)
        throws Exception, RemoteException
    {
        return m_localReference.getGxmlPreview(p_gxml, p_locale);
    }

    public String getSourcePageView(long p_srcPageId,
        RenderingOptions p_options, GlobalSightLocale p_locale,
        boolean p_dirtyTemplate, PaginateInfo p_pi)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getSourcePageView(p_srcPageId,
            p_options, p_locale, p_dirtyTemplate, p_pi);
    }
    
    public String getSourcePageView(long p_srcPageId,
            RenderingOptions p_options, GlobalSightLocale p_locale,
            boolean p_dirtyTemplate, PaginateInfo p_pi, HashMap searchMap)
            throws OnlineEditorException, RemoteException
        {
            return m_localReference.getSourcePageView(p_srcPageId,
                p_options, p_locale, p_dirtyTemplate, p_pi, searchMap);
        }

    /**
     * @see OnlineEditorManager.getTargetPageView(long,
     * RenderingOptions, Vector)
     */
    public String getTargetPageView(long p_targetPageId,
        RenderingOptions p_options, Vector p_excludedItemTypes,
        boolean p_dirtyTemplate)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getTargetPageView(p_targetPageId,
            p_options, p_excludedItemTypes, p_dirtyTemplate);
    }

    // Webex proposed reviewer view of term memory,
    /**
     * @see OnlineEditorManager.getTargetPageView(long,
     * EditorState, Vector)
     */
    public String getTargetPageView(long p_targetPageId,
    		EditorState p_state, Vector p_excludedItemTypes,
        boolean p_dirtyTemplate)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getTargetPageView(p_targetPageId,
            p_state, p_excludedItemTypes, p_dirtyTemplate);
    }
    
    public String getTargetPageView(long p_targetPageId,
            EditorState p_state, Vector p_excludedItemTypes,
        boolean p_dirtyTemplate, HashMap p_map)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getTargetPageView(p_targetPageId,
            p_state, p_excludedItemTypes, p_dirtyTemplate, p_map);
    }

    public SegmentView getSegmentView(long p_tuId, long p_tuvId,
        String p_subId, long p_targetPageId,
        long p_sourceLocaleId, long p_targetLocaleId,
        String[] p_tmNames, String p_termbase, boolean p_releverage)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getSegmentView(p_tuId, p_tuvId, p_subId,
            p_targetPageId, p_sourceLocaleId, p_targetLocaleId,
            p_tmNames, p_termbase, p_releverage);
    }

    public PageInfo getPageInfo(long p_srcPageId)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getPageInfo(p_srcPageId);
    }

    public ArrayList getTuIdsInPage(Long p_srcPageId)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getTuIdsInPage(p_srcPageId);
    }

    public void updateTUV(long p_tuvId, String p_subId, String p_newContent)
        throws OnlineEditorException, RemoteException
    {
        m_localReference.updateTUV(p_tuvId, p_subId, p_newContent);
    }
    
    public void updateTUV(long p_tuvId, String p_subId, String p_newContent, String p_userId)
    throws OnlineEditorException, RemoteException
	{
	    m_localReference.updateTUV(p_tuvId, p_subId, p_newContent, p_userId);
	}

    public void createImageMap(Long p_trgPageId, long p_tuvId, long p_subId,
        String p_tempName, String p_realName)
        throws OnlineEditorException, RemoteException
    {
        m_localReference.createImageMap(p_trgPageId, p_tuvId, p_subId,
            p_tempName, p_realName);
    }

    public HashSet getInterpretedTuIds(long p_srcPageId,
        GlobalSightLocale p_locale)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getInterpretedTuIds(p_srcPageId, p_locale);
    }

    public void invalidateCache()
        throws OnlineEditorException, RemoteException
    {
        m_localReference.invalidateCache();
    }

    public void invalidateCachedTemplates()
        throws OnlineEditorException, RemoteException
    {
        m_localReference.invalidateCachedTemplates();
    }

    public void splitSegments (long p_tuv1, long p_tuv2, String p_location)
        throws OnlineEditorException, RemoteException
    {
        m_localReference.splitSegments(p_tuv1, p_tuv2, p_location);
    }

    public void mergeSegments (long p_tuv1, long p_tuv2)
        throws OnlineEditorException, RemoteException
    {
        m_localReference.mergeSegments(p_tuv1, p_tuv2);
    }

    public CommentThreadView getCommentThreads(long p_trgPageId)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getCommentThreads(p_trgPageId);
    }

    public CommentView getCommentView(long p_commentId, long p_trgPageId,
        long p_tuId, long p_tuvId, long p_subId)
        throws OnlineEditorException, RemoteException
    {
        return m_localReference.getCommentView(p_commentId, p_trgPageId,
            p_tuId, p_tuvId, p_subId);
    }

    public void createComment(long p_tuId, long p_tuvId, long p_subId,
        String p_title, String p_comment, String p_priority, String p_status,
        String p_category, String p_user)
        throws OnlineEditorException, RemoteException
    {
        m_localReference.createComment(p_tuId, p_tuvId, p_subId,
            p_title, p_comment, p_priority, p_status, p_category, p_user);
    }

    public void editComment(CommentView p_view, String p_title,
        String p_comment, String p_priority, String p_status, 
        String p_category, String p_user)
        throws OnlineEditorException, RemoteException
    {
        m_localReference.editComment(p_view, p_title, p_comment,
            p_priority, p_status, p_category, p_user);
    }
    
    public void editComment(CommentView p_view, String p_title,
            String p_comment, String p_priority, String p_status,
            String p_category, String p_user, boolean share, boolean overwrite)
            throws OnlineEditorException, RemoteException
    {
        m_localReference.editComment(p_view, p_title, p_comment, p_priority,
                p_status, p_category, p_user, share, overwrite);
    }
    
    public void addComment(CommentView p_view, String p_title,
        String p_comment, String p_priority, String p_status, 
        String p_category, String p_user)
        throws OnlineEditorException, RemoteException
    {
        m_localReference.addComment(p_view, p_title, p_comment,
            p_priority, p_status, p_category, p_user);
    }
    
    public void addComment(CommentView p_view, String p_title,
            String p_comment, String p_priority, String p_status, 
            String p_category, String p_user, boolean share, boolean overwrite)
            throws OnlineEditorException, RemoteException
        {
            m_localReference.addComment(p_view, p_title, p_comment,
                p_priority, p_status, p_category, p_user, share, overwrite);
        }
    
    public void closeAllComment(ArrayList p_issueList, String p_user)
        throws OnlineEditorException, RemoteException
    {
    	m_localReference.closeAllComment(p_issueList, p_user);
    }

    public void createComment(long p_tuId, long p_tuvId, long p_subId,
            String p_title, String p_comment, String p_priority,
            String p_status, String p_category, String p_user, boolean share,
            boolean overwrite) throws OnlineEditorException, RemoteException
    {
        m_localReference.createComment(p_tuId, p_tuvId, p_subId, p_title,
                p_comment, p_priority, p_status, p_category, p_user, share,
                overwrite);
    }
    
    public ArrayList getPageLastModifyUserList(EditorState p_state)
            throws GeneralException, RemoteException
    {
        return m_localReference.getPageLastModifyUserList(p_state);
    }
    
    public ArrayList getPageSidList(EditorState p_state)
            throws GeneralException, RemoteException
    {
        return m_localReference.getPageSidList(p_state);
    }
    
    public MatchTypeStatistics getMatchTypes(Long p_sourcePageId,
            Long p_targetLocaleId) throws GeneralException, RemoteException
    {
        return m_localReference.getMatchTypes(p_sourcePageId, p_targetLocaleId);
    }
}