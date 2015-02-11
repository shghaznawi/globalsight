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
package com.globalsight.everest.tm.searchreplace;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import org.hibernate.Session;
import org.hibernate.Transaction;

import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.tm.TmManagerException;
import com.globalsight.everest.tm.TmManagerExceptionMessages;
import com.globalsight.everest.tuv.Tuv;
import com.globalsight.ling.util.GlobalSightCrc;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.persistence.hibernate.HibernateUtil;
import com.globalsight.terminology.util.SqlUtil;
import com.globalsight.util.gxml.GxmlElement;
import com.globalsight.util.gxml.GxmlException;
import com.globalsight.util.gxml.GxmlFragmentReader;
import com.globalsight.util.gxml.GxmlFragmentReaderPool;

public class JobSearchReplaceManagerLocal
    implements JobSearchReplaceManager
{
    static private final GlobalSightCategory c_logger =
        (GlobalSightCategory)GlobalSightCategory.getLogger(
            JobSearchReplaceManagerLocal.class);

    public JobSearchReplaceManagerLocal()
    {
    }

    /**
     * Replaces the old text with the new text in the job info collection.
     *
     * @param p_old old text
     * @param p_new next text
     * @param p_jobInfos collection of job info
     * @param p_caseSensitiveSearch
     * @exception TmManagerException
     * @exception RemoteException
     */
    public Collection replaceForPreview(String p_old, String p_new,
        Collection p_jobInfos, boolean p_caseSensitiveSearch)
        throws TmManagerException, RemoteException
    {
        ArrayList notReplaced = new ArrayList();
        ArrayList replaced = new ArrayList(p_jobInfos.size());

        //no longer assumes the incoming Strings aren't unicode
        String oldText = p_old;
        String newText = p_new;

        // all TUVs in p_tuvs should be in the same locale
        try
        {
            if (p_jobInfos.size() > 0)
            {
                Iterator it = p_jobInfos.iterator();
                while (it.hasNext())
                {
                    JobInfo jobInfo = (JobInfo)it.next();
                    TuvInfo tuvInfo = jobInfo.getTuvInfo();
                    long localeId = tuvInfo.getLocaleId();
                    Locale locale = ServerProxy.getLocaleManager().getLocaleById(
                        localeId).getLocale();

                    GxmlElementSubstringReplace substringReplacer =
                        new GxmlElementSubstringReplace(oldText, newText,
                            p_caseSensitiveSearch, locale);

                    if (replaceSubstring(tuvInfo, substringReplacer))
                    {
                        jobInfo.setTuvInfo(tuvInfo);
                        replaced.add(jobInfo);
                    }
                    else
                    {
                        notReplaced.add(jobInfo);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            throw new TmManagerException(
                TmManagerExceptionMessages.MSG_FAILED_TO_UPDATE_JOB_TUV_DATA,
                null, ex);
        }

        return replaced;
    }


    public void replace(Collection p_replacedSegments)
        throws TmManagerException, RemoteException
    {
        Session session = null;
        Transaction transaction = null;
        
        try
        {
            session = HibernateUtil.getSession();
            transaction = session.beginTransaction();

            Iterator it = p_replacedSegments.iterator();
            while (it.hasNext())
            {
                TuvInfo tuvInfo = (TuvInfo)it.next();
                long tuvId = tuvInfo.getId();
                Tuv tuv = ServerProxy.getTuvManager().getTuvForSegmentEditor(tuvId);               
                tuv.setGxml(tuvInfo.getSegment());
                tuv.setExactMatchKey(tuvInfo.getExactMatchKey());
                tuv.setLastModified(new Date());
                session.update(tuv);
            }

            transaction.commit();
        }
        catch (Exception ex)
        {            
            if (transaction != null)
            {
                transaction.rollback();
            }
            throw new TmManagerException(
                TmManagerExceptionMessages.MSG_FAILED_TO_UPDATE_JOB_TUV_DATA,
                null, ex);
        }
        finally
        {
            if (session != null)
            {
                //session.close();
            }
        }
    }

    public ActivitySearchReportQueryResult searchForActivitySegments(
        boolean p_caseSensitiveSearch, String p_queryString,
        Collection p_targetLocales, Collection p_jobIds)
        throws TmManagerException, RemoteException
    {
        ActivitySearchReportQueryResult result = null;
        Connection connection = null;

        try
        {
            connection = SqlUtil.hireConnection();
            ActivityPageDataQuery query = new ActivityPageDataQuery(connection);
            result = query.query(p_queryString, p_targetLocales,
                p_jobIds, p_caseSensitiveSearch);
        }
        catch (Exception ex)
        {
            c_logger.error("activity search failed", ex);

            throw new TmManagerException(
                TmManagerExceptionMessages.MSG_FAILED_TO_SEARCH,
                null, ex);
        }
        finally
        {
            SqlUtil.fireConnection(connection);
        }

        return result;
    }

    public JobSearchReportQueryResult searchForJobSegments(
        boolean p_caseSensitiveSearch, String p_queryString,
        Collection p_targetLocales, Collection p_jobIds)
        throws TmManagerException, RemoteException
    {
        ArrayList result = new ArrayList();
        ArrayList jobIds = new ArrayList(p_jobIds);

        Connection connection = null;

        try
        {
            connection = SqlUtil.hireConnection();

            JobPageDataQuery query = new JobPageDataQuery(connection);

            int SIZE_DIVISOR = 500;
            int sizeOfJobIds = jobIds.size();
            float fraction = sizeOfJobIds / SIZE_DIVISOR;
            int iterations = (int)fraction + 1;
            int ibeg = 0;
            int iend = 499;
            int jobSize = 0;
            for (int i = 0; i < iterations; i++)
            {
                jobSize = jobIds.size();
                if (jobSize < iend)
                {
                    iend = jobSize;
                }

                Collection tempJobInfos = query.query(p_queryString,
                    p_targetLocales, p_jobIds, p_caseSensitiveSearch);

                jobIds.subList(ibeg, iend).clear();

                result.addAll(tempJobInfos);
            }
        }
        catch (Exception ex)
        {
            c_logger.error("job search failed", ex);

            throw new TmManagerException(
                TmManagerExceptionMessages.MSG_FAILED_TO_SEARCH,
                null, ex);
        }
        finally
        {
            SqlUtil.fireConnection(connection);
        }

        return new JobSearchReportQueryResult(result);
    }

    //
    // Private Methods
    //

    private boolean replaceSubstring(TuvInfo p_tuv,
        GxmlElementSubstringReplace p_substringReplacer)
        throws Exception
    {
        String segment = p_tuv.getSegment();

        GxmlElement gxmlElement = getGxmlElement(segment);

        boolean replaced = p_substringReplacer.replace(gxmlElement);

        if (replaced)
        {
            p_tuv.setSegment(gxmlElement.toGxml());

            // update exact match key - TODO - maybe the backend tm2
            // code will perform more (all) necessary updates
            String exactMatchFormat = p_tuv.getExactMatchFormat();
            p_tuv.setExactMatchKey(
                GlobalSightCrc.calculate(exactMatchFormat));
        }

        return replaced;
    }

    private GxmlElement getGxmlElement(String p_segment)
        throws GxmlException
    {
        GxmlElement result = null;

        GxmlFragmentReader reader =
            GxmlFragmentReaderPool.instance().getGxmlFragmentReader();

        try
        {
            result = reader.parseFragment(p_segment);
        }
        finally
        {
            GxmlFragmentReaderPool.instance().freeGxmlFragmentReader(reader);
        }

        return result;
    }
}