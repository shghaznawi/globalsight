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
package com.globalsight.everest.edit.offline.upload;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jms.Message;
import javax.jms.ObjectMessage;

import com.globalsight.everest.comment.CommentManager;
import com.globalsight.everest.company.CompanyThreadLocal;
import com.globalsight.everest.company.CompanyWrapper;
import com.globalsight.everest.edit.CommentHelper;
import com.globalsight.everest.edit.SynchronizationManager;
import com.globalsight.everest.edit.offline.OfflineEditHelper;
import com.globalsight.everest.edit.offline.page.UploadIssue;
import com.globalsight.everest.foundation.User;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.tuv.TuvManager;
import com.globalsight.everest.tuv.Tuv;
import com.globalsight.everest.util.jms.GenericQueueMDB;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.persistence.hibernate.HibernateUtil;
import com.globalsight.util.GlobalSightLocale;

/**
 * PageSaverMDB is a message driven bean responsible for saving modified tuvs
 * (target tuvs) and then indexing the source tuvs during upload.
 */
public class PageSaverMDB extends GenericQueueMDB
{
    static private final GlobalSightCategory s_category = (GlobalSightCategory) GlobalSightCategory
            .getLogger(PageSaverMDB.class);

    /**
     * Default constructor.
     */
    public PageSaverMDB()
    {
        super(s_category);
    }

    /**
     * Start the upload process as a separate thread. This method is not a
     * public API and is ONLY invoked by it's consumer for saving target tuvs
     * durning an upload.
     * 
     * @param p_message -
     *            The message to be passed. In this case, it's an object message
     *            that contains a HashMap containing: 1. A collection of
     *            modified tuvs 2. A map of modified comments (organized by tuv
     *            id) 3. The source locale object. 4. The user object 5. The
     *            file name. 6. The target page id (as string).
     */
    public void onMessage(Message p_message)
    {
        User user = null;
        String fileName = null;
        Long targetPageId = null;
        GlobalSightLocale sourceLocale = null;
        GlobalSightLocale targetLocale = null;
        GlobalSightLocale userLocale = null;

        SynchronizationManager syncMgr = getSynchronizationManager();

        try
        {
            if (s_category.isDebugEnabled())
            {
                s_category.debug("Received message for offline upload.");
            }

            if (p_message.getJMSRedelivered())
            {
                s_category.warn("Ignoring duplicate upload message.");
                return;
            }

            // get the hashtable that contains the export info
            HashMap map = (HashMap) ((ObjectMessage) p_message).getObject();

            CompanyThreadLocal.getInstance().setIdValue(
                    (String) map.get(CompanyWrapper.CURRENT_COMPANY_ID));

            sourceLocale = (GlobalSightLocale) map
                    .get(UploadPageSaver.UPLOAD_PAGE_SOURCE_LOCALE);
            targetLocale = (GlobalSightLocale) map
                    .get(UploadPageSaver.UPLOAD_PAGE_TARGET_LOCALE);
            userLocale = (GlobalSightLocale) map
                    .get(UploadPageSaver.UPLOAD_PAGE_USER_LOCALE);
            List modifiedTuvs = (List) map.get(UploadPageSaver.MODIFIED_TUVS);
            List newComments = (List) map.get(UploadPageSaver.NEW_COMMENTS);
            Map modifiedComments = (Map) map
                    .get(UploadPageSaver.MODIFIED_COMMENTS);
            user = (User) map.get(UploadPageSaver.USER);
            fileName = (String) map.get(UploadPageSaver.FILE_NAME);
            targetPageId = (Long) map.get(UploadPageSaver.UPLOAD_PAGE_ID);

            // Notify editor of page uploaded having started.
            try
            {
                syncMgr.uploadStarted(targetPageId);
            }
            catch (Throwable ex)
            {
            }

            savePageToDb(modifiedTuvs, newComments, modifiedComments,
                    sourceLocale, targetLocale, userLocale, user, fileName,
                    targetPageId);
        }
        catch (Exception ex)
        {
            s_category.error("PageSaverListener :: Failed to save segments: "
                    + ex.getMessage(), ex);

            String localePair = OfflineEditHelper.localePair(sourceLocale,
                    targetLocale, userLocale);

            OfflineEditHelper.notifyUser(user, fileName, localePair,
                    OfflineEditHelper.UPLOAD_FAIL_SUBJECT,
                    OfflineEditHelper.UPLOAD_FAIL_MESSAGE);
        }
        finally
        {
            // Notify editor of page upload having finished.
            try
            {
                syncMgr.uploadFinished(targetPageId);
            }
            catch (Throwable ex)
            {
            }

            HibernateUtil.closeSession();
        }
    }

    /**
     * Saves unprotected offline segments to the appropriate target Tuvs. The
     * caller is expected to have submitted this page to the Offline Error
     * Checker before passing it to this method.
     * 
     * Since all relevant subflows must be merged into a given Tuv at the same
     * time, this method builds two hash maps. One is the Tuvs to be saved and
     * the other is the subflows to be saved. After both maps are populated, we
     * merge the subflows to the Tuvs to be saved. Working this way, we are able
     * to collect subflows in any order that they may appear in the upload file
     * and eventually join them with the correct Tuv.
     * 
     * @exception UploadPageSaverException
     */
    private void savePageToDb(List modifiedTuvs, List newComments,
            Map modifiedComments, GlobalSightLocale sourceLocale,
            GlobalSightLocale targetLocale, GlobalSightLocale userLocale,
            User p_user, String p_fileName, Long p_targetPageId)
            throws UploadPageSaverException
    {
        // save target tuvs
        Iterator tuvIter = modifiedTuvs.iterator();
        while (tuvIter.hasNext())
        {
            Tuv tuv = (Tuv) tuvIter.next();
            tuv.setLastModifiedUser(p_user.getUserId());
        }
        saveTuvs(modifiedTuvs, targetLocale);

        // save comments
        saveNewComments(newComments, p_user.getUserId(), p_targetPageId);
        saveModifiedComments(modifiedComments, p_user.getUserId());

        // After a successful save, notify user.
        // (Including when no modified Tuvs or comments are uploaded.)
        String localePair = OfflineEditHelper.localePair(sourceLocale,
                targetLocale, userLocale);

        OfflineEditHelper.notifyUser(p_user, p_fileName, localePair,
                OfflineEditHelper.UPLOAD_SUCCESSFUL_SUBJECT,
                OfflineEditHelper.UPLOAD_SUCCESSFUL_MESSAGE);
    }

    private void saveTuvs(List p_tuvsToBeSaved, GlobalSightLocale p_targetLocale)
            throws UploadPageSaverException
    {
        try
        {
            TuvManager mgr = ServerProxy.getTuvManager();
            mgr.saveTuvsFromOffline(p_tuvsToBeSaved);
        }
        catch (Exception ex)
        {
            s_category.error("Cannot save TUVs", ex);
            throw new UploadPageSaverException(ex);
        }
    }

    private void saveNewComments(List p_comments, String p_user,
            Long p_targetPageId)
    {
        // p_comments is just a flat ArrayList of UploadIssue objects.

        for (int i = 0, max = p_comments.size(); i < max; i++)
        {
            UploadIssue issue = (UploadIssue) p_comments.get(i);

            long tuId = issue.getTuId();
            long tuvId = issue.getTuvId();
            long subId = issue.getSubId();

            String logicalKey = CommentHelper.makeLogicalKey(p_targetPageId
                    .longValue(), tuId, tuvId, subId);

            if (s_category.isDebugEnabled())
            {
                s_category.debug("Creating new comment for " + logicalKey);
            }

            try
            {
                CommentManager mgr = getCommentManager();

                mgr.addIssue(issue.getLevelObjectType(), issue.getTuvId(),
                        issue.getTitle(), issue.getPriority(), issue
                                .getStatus(), issue.getCategory(), p_user,
                        issue.getComment(), logicalKey);
            }
            catch (Exception ex)
            {
                // Don't fail the upload because of comments.
                s_category.error("Error creating new comment for " + logicalKey
                        + " (continuing anyway)", ex);
            }
        }
    }

    private void saveModifiedComments(Map p_comments, String p_user)
    {
        // p_comments is a HashMap with original Issue objects as
        // keys, and UploadIssue objects as values.

        Collection oldIssues = p_comments.keySet();

        for (Iterator it = oldIssues.iterator(); it.hasNext();)
        {
            Long oldIssueId = (Long) it.next();
            UploadIssue issue = (UploadIssue) p_comments.get(oldIssueId);

            if (s_category.isDebugEnabled())
            {
                s_category.debug("Replying to comment id=" + oldIssueId);
            }

            try
            {
                CommentManager mgr = getCommentManager();

                mgr.replyToIssue(oldIssueId.longValue(), issue.getTitle(),
                        issue.getPriority(), issue.getStatus(), issue
                                .getCategory(), p_user, issue.getComment());
            }
            catch (Exception ex)
            {
                // Don't fail the upload because of comments.
                s_category.error("Error replying to comment " + oldIssueId
                        + " (continuing anyway)", ex);
            }
        }
    }

    private SynchronizationManager getSynchronizationManager()
    {
        try
        {
            return ServerProxy.getSynchronizationManager();
        }
        catch (Exception ex)
        {
            s_category.error("Internal error: cannot send offline/online "
                    + "synchronization messages", ex);
        }

        return null;
    }

    private CommentManager getCommentManager()
    {
        try
        {
            return ServerProxy.getCommentManager();
        }
        catch (Exception ex)
        {
            s_category
                    .error("Internal error: cannot access CommentManager", ex);
        }

        return null;
    }
}