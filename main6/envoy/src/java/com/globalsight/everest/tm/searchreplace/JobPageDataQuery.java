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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import com.globalsight.ling.common.Text;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.util.GlobalSightLocale;


public class JobPageDataQuery
{
    static private GlobalSightCategory c_category =
        (GlobalSightCategory)GlobalSightCategory.getLogger(
            JobPageDataQuery.class);

    static private String SEARCH_CASE_SENSITIVE;
    static private String SEARCH_CASE_INSENSITIVE;

    static
    {
        // construct a query string
        SEARCH_CASE_SENSITIVE = SEARCH_CASE_INSENSITIVE =
            " SELECT " +
            "   j.id, " +
            "   j.name," +
            "   wf.target_locale_id, " +
            "   concat(l1.iso_lang_code, '_', l1.iso_country_code), " +
            "   tp.id, " +
            "   sp.external_page_id, " +
            "   tu.data_type, " +
            "   tuv.id, " +
            "   tuv.locale_id, " +
            "   tuv.segment_string " +
            " FROM job j, " +
            "   workflow wf, " +
            "   locale l1, " +
            "   source_page sp, " +
            "   target_page tp, " +
            "   target_page_leverage_group tplg, " +
            "   translation_unit tu, " +
            "   translation_unit_variant tuv " +
            " WHERE j.id = wf.job_id " +
            "   and wf.target_locale_id = l1.id " +
            "   and wf.iflow_instance_id = tp.workflow_iflow_instance_id  " +
            "   and tp.source_page_id = sp.id " +
            "   and tp.id = tplg.tp_id " +
            "   and tu.leverage_group_id = tplg.lg_id " +
            "   and tuv.tu_id = tu.id " +
            "   and tuv.locale_id = wf.target_locale_id " +
            "   and j.state in ('DISPATCHED','READY_TO_BE_DISPATCHED')";

        SEARCH_CASE_SENSITIVE +=
            " AND tuv.segment_string like ? escape '&'";

        // TODO: use NLS_LOWER instead of LOWER
        SEARCH_CASE_INSENSITIVE +=
            " AND LOWER(tuv.segment_string) like LOWER(?) escape '&'";
    }

    private Connection m_connection;

    public JobPageDataQuery(Connection p_connection)
    {
        m_connection = p_connection;
    }

    public ArrayList query(String p_searchString,
        Collection p_targetLocales, Collection p_jobIds,
        boolean p_caseSensitiveSearch)
        throws Exception
    {
        ArrayList result = new ArrayList();
        PreparedStatement stmt = null;
        ResultSet rset = null;

        String orderingClause = " order by j.name, l1.iso_lang_code || '_' || l1.iso_country_code, sp.external_page_id";

        try
        {
            String inWFTClauseHolder = addWFTLocaleClause(p_targetLocales);
            String inJobClauseHolder = addJobIds(p_jobIds);
            String inTUVClauseHolder = addTUVLocaleClause(p_targetLocales);

            if (p_caseSensitiveSearch)
            {
                StringBuffer sb = new StringBuffer(SEARCH_CASE_SENSITIVE);
                sb.append(inWFTClauseHolder);
                sb.append(inJobClauseHolder);
                sb.append(inTUVClauseHolder);
                sb.append(orderingClause);

                if (c_category.isDebugEnabled())
                {
                    c_category.debug("job page query = " + sb);
                }

                stmt = m_connection.prepareStatement(sb.toString());
            }
            else
            {
                StringBuffer sb = new StringBuffer(SEARCH_CASE_INSENSITIVE);
                sb.append(inWFTClauseHolder);
                sb.append(inJobClauseHolder);
                sb.append(inTUVClauseHolder);
                sb.append(orderingClause);

                if (c_category.isDebugEnabled())
                {
                    c_category.debug("job page query = " + sb);
                }

                stmt = m_connection.prepareStatement(sb.toString());
            }

            String queryPattern = makeWildcardQueryString(p_searchString, '&');
            stmt.setString(1, queryPattern);
            rset = stmt.executeQuery();

            String currentJobName = "";
            String currentTargetLocaleName = "";
            String currentTargetPageName = "";
            while (rset.next())
            {
                long jobId = rset.getLong(1);
                String jobName = rset.getString(2);

                //information about the job first
                JobInfo jobInfo = new JobInfo();
                jobInfo.setJobId(jobId);
                jobInfo.setJobName(jobName);

                //information about the target locale next
                TargetLocaleInfo targetLocaleInfo = new TargetLocaleInfo();
                targetLocaleInfo.setId(rset.getLong(3));
                String targetLocaleName = rset.getString(4);
                targetLocaleInfo.setName(targetLocaleName);

                jobInfo.setTargetLocaleInfo(targetLocaleInfo);

                //information about the target page next
                TargetPageInfo targetPageInfo = new TargetPageInfo();
                targetPageInfo.setId(rset.getLong(5));
                String targetPageName = rset.getString(6);
                targetPageInfo.setName(targetPageName);

                jobInfo.setTargetPageInfo(targetPageInfo);

                //information about the Tuv Info next
                TuvInfo tuvInfo = new TuvInfo();
                tuvInfo.setDataType(rset.getString(7));
                tuvInfo.setId(rset.getLong(8));
                tuvInfo.setLocaleId(rset.getLong(9));
                tuvInfo.setSegment(rset.getString(10));

                jobInfo.setTuvInfo(tuvInfo);

                //add the Job info to the collection of jobInfos
                currentJobName = jobName;
                currentTargetLocaleName = targetLocaleName;
                currentTargetPageName = targetPageName;

                result.add(jobInfo);
            }
        }
        catch (Exception ex)
        {
            c_category.error("error in JobPageDataQuery", ex);
            throw ex;
        }
        finally
        {
            try
            {
                if (rset != null) { rset.close(); }
                if (stmt != null) { stmt.close(); }
            }
            catch (Exception ex)
            {
                c_category.warn("Unable to close statement and result set", ex);
            }
        }

        return result;
    }

    private String addWFTLocaleClause(Collection p_listOfLocales)
    {
        StringBuffer sb = new StringBuffer();
        sb.append(" and wf.target_locale_id IN (");

        int i = 0;
        Iterator it = p_listOfLocales.iterator();
        while (it.hasNext())
        {
            String targetLocale = (String)it.next();

            sb.append("'");
            sb.append(targetLocale);
            sb.append("'");

            if (i < p_listOfLocales.size() - 1)
            {
                sb.append(", ");
            }

            i++;
        }

        sb.append(")");

        return sb.toString();
    }

    private String addJobIds(Collection p_jobIds)
    {
        StringBuffer sb = new StringBuffer();
        sb.append(" and j.id IN (");

        int i = 0;
        Iterator it = p_jobIds.iterator();
        while (it.hasNext())
        {
            String jobId = (String)it.next();

            sb.append("'");
            sb.append(jobId);
            sb.append("'");

            if (i < p_jobIds.size() - 1)
            {
                sb.append(", ");
            }

            i++;
        }

        sb.append(")");

        return sb.toString();
    }

    private String addTUVLocaleClause(Collection p_listOfLocales)
    {
        StringBuffer sb = new StringBuffer();
        sb.append(" and tuv.locale_id IN (");

        int i = 0;
        Iterator it = p_listOfLocales.iterator();
        while (it.hasNext())
        {
            String targetLocale = (String)it.next();

            sb.append("'");
            sb.append(targetLocale);
            sb.append("'");

            if (i < p_listOfLocales.size() - 1)
            {
                sb.append(", ");
            }

            i++;
        }

        sb.append(")");

        return sb.toString();
    }

    /**
     * '*' is the only allowed wildcard char the user can enter. '*'
     * is escaped using '\' to make '*' literal.
     *
     * p_escapeChar is an escape character used in LIKE predicate to
     * escape '%' and '_' wildcards.
     */
    static private String makeWildcardQueryString(String p_queryString,
        char p_escapeChar)
    {
        String pattern = p_queryString;
        String escape = String.valueOf(p_escapeChar);
        String asterisk = "*";
        String percent = "%";
        String underScore = "_";

        // remove the first and the last '*' from the string
        if (pattern.startsWith(asterisk))
        {
            pattern = pattern.substring(1);
        }
        if (pattern.endsWith(asterisk) &&
            pattern.charAt(pattern.length() - 2) != '\\')
        {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        // '&' -> '&&' (escape itself)
        pattern = Text.replaceString(pattern, escape, escape + escape);

        // '%' -> '&%' (escape wildcard char)
        pattern = Text.replaceString(pattern, percent, escape + percent);

        // '_' -> '&_' (escape wildcard char)
        pattern = Text.replaceString(pattern, underScore, escape + underScore);

        // '*' -> '%'  (change wildcard) '\*' -> '*' (literal *)
        pattern = Text.replaceChar(pattern, '*', '%', '\\');

        // Add '%' to the beginning and the end of the string (because
        // the segment text is enclosed with <segment></segment> or
        // <localizable></localizable>)
        pattern = percent + pattern + percent;

        pattern = "<%>" + pattern + "</%>";

        if (c_category.isDebugEnabled())
        {
            c_category.debug("search + replace pattern = " + pattern);
        }

        return pattern;
    }
}