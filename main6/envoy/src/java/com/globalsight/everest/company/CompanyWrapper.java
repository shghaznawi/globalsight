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
package com.globalsight.everest.company;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.globalsight.everest.persistence.PersistenceException;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.persistence.hibernate.HibernateUtil;

/**
 * This class only represent a wrapper object for the company names defined in
 * Envoy database and is used for defining a workflow template (template node
 * names).
 */
public class CompanyWrapper
{
    public static final String SUPER_COMPANY_ID = "1";

    public static final String COMPANY_ATTR = "m_companyId";

    public static final String COPMANY_ID_ARG = "companyIdArg";

    public static final String COPMANY_ID_START_ARG = "companyIdStartArg";

    public static final String COPMANY_ID_END_ARG = "companyIdEndArg";

    public static final String CURRENT_COMPANY_ID = "currentCompanyId";

    private static Company superCompany;

    // ////////////////////////////////////////////////////////////////////////////////
    // Begin: Constructor
    // ////////////////////////////////////////////////////////////////////////////////
    /**
     * Default Company constructor used ONLY for TopLink.
     */
    public CompanyWrapper()
    {
    }

    // ////////////////////////////////////////////////////////////////////////////////
    // End: Constructor
    // ////////////////////////////////////////////////////////////////////////////////

    // ////////////////////////////////////////////////////////////////////////////////
    // Begin: Local Methods
    // ////////////////////////////////////////////////////////////////////////////////
    /**
     * Get the company name.
     * 
     * @return The company name.
     */
    @SuppressWarnings("unchecked")
    public static String[] getAllCompanyNames() throws PersistenceException
    {
        String[] strCompanyNames = null;
        String hql = "from Company";
        Collection<?> col = HibernateUtil.search(hql);
        Set<String> companyNames = new TreeSet<String>();
        for (Iterator<?> iter = col.iterator(); iter.hasNext();)
        {
            Company company = (Company) iter.next();
            companyNames.add(company.getCompanyName());
        }
        strCompanyNames = new String[companyNames.size()];
        companyNames.toArray(strCompanyNames);

        return strCompanyNames;
    }

    public static Vector<Long> addCompanyIdBoundArgs(Vector<Long> args)
            throws PersistenceException
    {
        String currentId = CompanyThreadLocal.getInstance().getValue();

        if (CompanyWrapper.SUPER_COMPANY_ID.equals(currentId))
        {
            Long[] bounds = CompanyWrapper.getCompanyIdBound();
            args.add(bounds[0]);
            args.add(bounds[1]);
        }
        else
        {
            args.add(new Long(currentId));
            args.add(new Long(currentId));
        }

        return args;
    }

    public static HashMap<String, Long> addCompanyIdBoundArgs(String mapKey1, String mapKey2)
            throws PersistenceException
    {
        String currentId = CompanyThreadLocal.getInstance().getValue();
        HashMap<String, Long> map = new HashMap<String, Long>();

        if (CompanyWrapper.SUPER_COMPANY_ID.equals(currentId))
        {
            Long[] bounds = CompanyWrapper.getCompanyIdBound();
            map.put(mapKey1, bounds[0]);
            map.put(mapKey2, bounds[1]);
        }
        else
        {
            map.put(mapKey1, new Long(currentId));
            map.put(mapKey2, new Long(currentId));
        }
        return map;
    }

    private static Long[] getCompanyIdBound() throws PersistenceException
    {
        Long[] bounds = new Long[2];
        bounds[0] = new Long(-1);
        String maxCompanySql = "select max(id) from COMPANY";
        Number maxId = (Number) HibernateUtil
                .getFirstWithSql(maxCompanySql);
        bounds[1] = new Long(maxId.longValue());

        return bounds;
    }

    public static String getCompanyNameById(String id)
    {
        Company company = null;
        try
        {
            company = getCompanyById(id);
        }
        catch (PersistenceException e)
        {
            return "Null Company";
        }
        if (company == null)
        {
            return "Null Company";
        }

        return company.getName();
    }

    public static String getCompanyIdByName(String strName)
            throws PersistenceException
    {
        return String.valueOf(getCompanyByName(strName).getId());
    }

    public static Company getCompanyByName(String strName)
            throws PersistenceException
    {
        try
        {
            return ServerProxy.getJobHandler().getCompany(
                    strName.toUpperCase());
        }
        catch (Exception e)
        {
            throw new PersistenceException(e);
        }
    }

    public static Company getCompanyById(String id) throws PersistenceException
    {
        Company company = null;

        try
        {
            company = ServerProxy.getJobHandler().getCompanyById(
                    Long.parseLong(id));
        }
        catch (Exception e)
        {
            throw new PersistenceException(e);
        }

        return company;
    }

    public static String getCurrentCompanyName()
    {
        return CompanyWrapper.getCompanyNameById(CompanyWrapper
                .getCurrentCompanyId());
    }

    public static String getCurrentCompanyId()
    {
        return CompanyThreadLocal.getInstance().getValue();
    }

    public static long getCurrentCompanyIdAsLong()
    {
        return Long.parseLong(getCurrentCompanyId());
    }

    public static String getSuperCompanyName()
    {
        if (superCompany == null)
        {
            superCompany = getCompanyById(SUPER_COMPANY_ID);
        }

        return superCompany.getName();
    }

    public static boolean isSuperCompanyName(String companyName)
    {
        return getSuperCompanyName().equalsIgnoreCase(companyName);
    }

    public static boolean isSuperCompany(String id)
    {
        return isSuperCompanyName(getCompanyNameById(id));
    }

    public static void saveCurrentCompanyIdInMap(Map<String, String> map, Logger logger)
    {
        String companyId = CompanyThreadLocal.getInstance().getValue();
        if (logger != null && logger.isDebugEnabled())
        {
            logger.debug("Current company id get from ThreadLocal is: "
                    + companyId);
        }
        if (map != null)
        {
            map.put(CompanyWrapper.CURRENT_COMPANY_ID, companyId);
        }
    }
}