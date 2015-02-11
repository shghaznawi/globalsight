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
package com.globalsight.everest.projecthandler;


public class ProjectInfo implements java.io.Serializable
{
    private long m_projectId = -1;
    private String m_name = null;    
    private String m_description = null;
    private String m_companyId = null;
    private String m_userId = null;
    private String m_projectManagerName = null;
    private String m_tmName = null;
    private String m_termbaseName = null;

    /**
    * Default Constructor
    */
    public ProjectInfo(long p_projectId, String p_name, String p_description, 
        String p_companyId, String p_userId, String p_termbaseName)
    {
        m_projectId = p_projectId;
        m_name = p_name;
        m_description = p_description;
        m_userId = p_userId;
        m_termbaseName = p_termbaseName;
        m_companyId = p_companyId;
    }
    
    /**
     * Default Constructor
     */
     public ProjectInfo(long p_projectId, String p_name, String p_description, 
         String p_userId, String p_termbaseName)
     {
         m_projectId = p_projectId;
         m_name = p_name;
         m_description = p_description;
         m_userId = p_userId;
         m_termbaseName = p_termbaseName;
     }

    /**
    * Get the project name.
    */
    public String getName() 
    {
    	return m_name;
    }

    /**
    * Get the project description.
    */
    public String getDescription() 
    {
        return m_description;
    }   

    /**
    * Get the project id
    */
    public long getProjectId()
    {
        return m_projectId;
    }

    /**
    * Get the project manager userid
    */
    public String getProjectManagerId()
    {
        return m_userId;
    }
    
    /**
    * Get the project manager full name
    */
    public String getProjectManagerName()
    {
        return m_projectManagerName;
    }
    
    /**
    * Set the project manager full name. This attribute is not saved
    * in database, but retrieved later on from UserManager and set. 
    *
    * This value is set in ProjectHandlerLocal. We could have done this in 
    * ResultHandler in persistence service also when we are creating an 
    * instance of this object.
    */
    public void setProjectManagerName(String p_projectManagerName)
    {
        m_projectManagerName = p_projectManagerName;
    }
    
    public String getTermbaseName()
    {
        return m_termbaseName;
    }
    
    public String getCompanyId()
    {
        return m_companyId;
    }
    
    /**
    * Returns a string representation of the object (based on the object name).
    */
    public String toString()
    { 
        return getName();
    }
}