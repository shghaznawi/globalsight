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

package com.globalsight.everest.page;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.globalsight.everest.jobhandler.Job;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.persistence.hibernate.HibernateUtil;

public class AddingSourcePageManager
{
    static private final GlobalSightCategory logger = (GlobalSightCategory) GlobalSightCategory
            .getLogger(AddingSourcePageManager.class);

    public static List<AddingSourcePage> getAllAddingSourcePage(Job job)
    {
        String hql = "from AddingSourcePage p where p.jobId = :jobId";
        Map map = new HashMap();
        map.put("jobId", job.getId());
        List result = HibernateUtil.search(hql, map);

        if (result.size() > 0)
        {
            List<SourcePage> sourcePages = (List) job.getSourcePages();
            for (SourcePage page : sourcePages)
            {
                for (int i = result.size() - 1; i >= 0; i--)
                {
                    AddingSourcePage uPage = (AddingSourcePage) result.get(i);
                    if (uPage.equal(page))
                    {
                        try
                        {
                            HibernateUtil.delete(uPage);
                        }
                        catch (Exception e)
                        {
                            logger.error(e);
                        }
                        
                        result.remove(i);
                        break;
                    }
                }

                if (result.size() == 0)
                {
                    break;
                }
            }
        }

        return result;
    }
    
    public static void removeAllAddingFiles(long jobId)
    {
        String hql = "from AddingSourcePage p where p.jobId = :jobId";
        Map map = new HashMap();
        map.put("jobId", jobId);
        List result = HibernateUtil.search(hql, map);
        try
        {
            HibernateUtil.delete(result);
        }
        catch (Exception e)
        {
            logger.error(e);
        }
    }

    public static void sort(List<AddingSourcePage> pages)
    {
        Collections.sort(pages, new Comparator<AddingSourcePage>()
        {
            @Override
            public int compare(AddingSourcePage o1, AddingSourcePage o2)
            {
                return o1.getExternalPageId().compareTo(o2.getExternalPageId());
            }
        });
    }
}