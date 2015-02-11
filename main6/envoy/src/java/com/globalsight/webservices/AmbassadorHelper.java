/**
 * Copyright 2009 Welocalize, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.globalsight.webservices;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.hibernate.Query;
import org.hibernate.Session;
import org.jbpm.JbpmContext;

import com.globalsight.everest.company.Company;
import com.globalsight.everest.company.CompanyWrapper;
import com.globalsight.everest.foundation.ContainerRole;
import com.globalsight.everest.foundation.LocalePair;
import com.globalsight.everest.foundation.Role;
import com.globalsight.everest.foundation.User;
import com.globalsight.everest.foundation.UserRole;
import com.globalsight.everest.jobhandler.JobHandlerWLRemote;
import com.globalsight.everest.permission.Permission;
import com.globalsight.everest.permission.PermissionGroup;
import com.globalsight.everest.permission.PermissionManager;
import com.globalsight.everest.projecthandler.Project;
import com.globalsight.everest.projecthandler.ProjectHandlerWLRemote;
import com.globalsight.everest.servlet.EnvoyServletException;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.taskmanager.Task;
import com.globalsight.everest.usermgr.UserInfo;
import com.globalsight.everest.usermgr.UserManagerWLRemote;
import com.globalsight.everest.webapp.pagehandler.administration.permission.PermissionHelper;
import com.globalsight.everest.webapp.pagehandler.administration.users.UserUtil;
import com.globalsight.everest.workflow.Activity;
import com.globalsight.everest.workflow.WorkflowConfiguration;
import com.globalsight.everest.workflow.WorkflowInstance;
import com.globalsight.everest.workflow.WorkflowProcessAdapter;
import com.globalsight.everest.workflow.WorkflowTask;
import com.globalsight.everest.workflow.WorkflowTaskInstance;
import com.globalsight.everest.workflowmanager.Workflow;
import com.globalsight.ling.tm2.persistence.DbUtil;
import com.globalsight.log.ActivityLog;
import com.globalsight.util.Assert;
import com.globalsight.util.GeneralException;
import com.globalsight.util.RegexUtil;
import com.globalsight.util.StringUtil;
import com.globalsight.util.XmlParser;

/**
 * Helper for Ambassador.java.
 * 
 * @author YorkJin
 * @version 8.5.0.1
 * @since 2013-06-19
 */
public class AmbassadorHelper extends AbstractWebService
{
    private static final Logger logger = Logger.getLogger(AmbassadorHelper.class);
    
    //Error constants used in createUser()/modifyUser()
    private final int UNKNOWN_ERROR = -1;
    private final int SUCCESS = 0;
    private final int INVALID_ACCESS_TOKEN = 1;
    private final int INVALID_USER_ID = 2;
    private final int CAN_NOT_CREATE_SUPER_USER = 3;
    private final int USER_EXISTS = 4;
    private final int USER_NOT_EXIST = 5;
    private final int NOT_IN_SAME_COMPANY = 6;
    private final int INVALID_PASSWORD = 7;
    private final int INVALID_FIRST_NAME = 8;
    private final int INVALID_LAST_NAME = 9;
    private final int INVALID_EMAIL_ADDRESS = 10;
    private final int INVALID_PERMISSION_GROUPS = 11;
    private final int INVALID_PROJECTS = 12;
    private final int INVALID_ROLES = 13;
    
    private static final String PROCESS_DEFINITION_IDS_PLACEHOLDER = "\uE000"
            + "_processDefinition_Ids_" + "\uE000";

    private static String QUERY_TASK_ASSIGNEE_SQL1 =
            "SELECT ti.workflow_id, ti.task_id, ti.name AS task_name, node.name_ AS node_name, node.processdefinition_ AS processdefinition "
            + " FROM workflow wf, task_info ti, jbpm_node node "
            + " WHERE wf.iflow_instance_id = ti.workflow_id "
            + " AND ti.task_id = node.id_ "
            + "AND node.class_ = 'K' "
            + "AND wf.job_id = ? "
            + "AND ti.user_id IS NULL";

    private static String QUERY_TASK_ASSIGNEE_SQL2 =
            "SELECT jd.id_ AS delegation_id, jd.processdefinition_ AS processDefinition, jd.configuration_ AS configuration FROM jbpm_delegation jd "
            + " WHERE jd.processdefinition_ IN (" + PROCESS_DEFINITION_IDS_PLACEHOLDER + ") "
            + " AND jd.classname_ = 'com.globalsight.everest.workflow.WorkflowAssignment'";


    private static String QUERY_TASK_ISSKIP_SQL=
    	"SELECT instance.id_ FROM jbpm_task jt, jbpm_taskinstance instance"
    	+ " WHERE jt.id_ = instance.task_ "
    	+ " AND jt.tasknode_ = ";

    protected static boolean isSkippedTask(long taskId)
    {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        JbpmContext ctx = WorkflowConfiguration.getInstance().getJbpmContext();
        boolean isSkipped = false;
        
        try
        {
        	con = DbUtil.getConnection();
            ps = con.prepareStatement(QUERY_TASK_ISSKIP_SQL +  taskId + " order by instance.id_ desc");
            rs = ps.executeQuery(); 
            while (rs.next())
            {
            	long tiId = rs.getLong(1);
				String hql = "from JbpmVariable j where j.name='skip' and j.taskInstance.id = :tiId";
				Session dbSession = ctx.getSession();
				Query query = dbSession.createQuery(hql);
				query.setParameter("tiId", tiId);
				List skipped = query.list();
				if (skipped != null && skipped.size() > 0)
				{
					isSkipped = true;
				}
				break;
            }
        }
        catch(Exception e)
        {
            logger.error("Error when determine skippedTasks.", e);
        }
        finally
        {
            DbUtil.silentClose(rs);
            DbUtil.silentClose(ps);
            DbUtil.silentReturnConnection(con);
            ctx.close();
        }

        return isSkipped;
    }
    
    /**
     * Get available users (assignees) for not accepted tasks in specified job.
     * 
     * @param jobId
     * @return Map<Long, String> : taskId as key, assignees as value.
     */
    protected static Map<Long, String> getTaskAssigneesByJob(long jobId)
    {
        Map<Long, String> availablTaskAssigneeMap = new HashMap<Long, String>();

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try
        {
            //1. Get all available tasks basic info.
            List<TaskAssignee> availableTasks = new ArrayList<TaskAssignee>();
            Set<Long> processDefinitions = new HashSet<Long>();

            con = DbUtil.getConnection();
            ps = con.prepareStatement(QUERY_TASK_ASSIGNEE_SQL1);
            ps.setLong(1, jobId);
            rs = ps.executeQuery();
            if (rs == null)
            {
                return availablTaskAssigneeMap;                
            }
            while (rs.next())
            {
                TaskAssignee ta = new TaskAssignee();
                ta.setWorkflowId(rs.getLong(1));
                ta.setTaskId(rs.getLong(2));
                ta.setTaskName(rs.getString(3));
                ta.setNodeName(rs.getString(4));
                long processDefinition = rs.getLong(5);
                ta.setProcessDefinition(processDefinition);

                processDefinitions.add(processDefinition);
                availableTasks.add(ta);
            }
            if (availableTasks.size() == 0 || processDefinitions.size() == 0)
            {
                return availablTaskAssigneeMap;                
            }

            //2. Get all configurations from "jbpm_delegation" for current job.
            List<TaskConfiguration> tcs = new ArrayList<TaskConfiguration>();
            String sql = QUERY_TASK_ASSIGNEE_SQL2.replace(
                    PROCESS_DEFINITION_IDS_PLACEHOLDER,
                    getProcessDefinitions(processDefinitions));
            ps = con.prepareStatement(sql);
            rs = ps.executeQuery();
            if (rs == null)
            {
                return availablTaskAssigneeMap;                
            }
            while (rs.next())
            {
                TaskConfiguration tc = new TaskConfiguration();
                tc.setDelegationId(rs.getLong(1));
                tc.setProcessDefinition(rs.getLong(2));
                tc.setConfiguration(rs.getString(3));
                tcs.add(tc);
            }

            // 3. Get a map:: "[processDefinition]_[nodeName]" as key, assignee
            // as value, e.x. "6600_node_1_Translation1_2"->"myUserName".
            // This key equals "node name" from "jbpm_node" table.
            Map<String, String> tcMap = new HashMap<String, String>();
            for (TaskConfiguration tc : tcs)
            {
                String configuration = tc.getConfiguration();
                String sequence = getValueForSpecifiedTag(configuration,
                        "sequence");
                String activity = getValueForSpecifiedTag(configuration,
                        "activity");
                String roleName = getValueForSpecifiedTag(configuration,
                        "role_name");
                // form the key
                String key = tc.getProcessDefinition() + "_node_" + sequence + "_" + activity;
                tcMap.put(key, roleName);
            }

            //4. result
            for (TaskAssignee ta : availableTasks)
            {
                String value = WorkflowTask.DEFAULT_ROLE_NAME;
                String key = ta.getProcessDefinition() + "_" + ta.getNodeName();
                String assignees = tcMap.get(key);
                if (!StringUtil.isEmpty(assignees))
                {
                    StringBuilder userNames = new StringBuilder();
                    String[] availableUsers = assignees.split(",");
                    for (int i = 0; i < availableUsers.length; i++)
                    {
                        String userName = UserUtil
                                .getUserNameById(availableUsers[i].trim());
                        userNames.append(userName);
                        if (i < availableUsers.length - 1)
                        {
                            userNames.append(",");
                        }
                    }
                    value = userNames.toString();
                }

                availablTaskAssigneeMap.put(ta.getTaskId(), value);
            }
        }
        catch (Exception e)
        {
            logger.error("Error when get available tasks assignees.", e);
        }
        finally
        {
            DbUtil.silentClose(rs);
            DbUtil.silentClose(ps);
            DbUtil.silentReturnConnection(con);
        }

        return availablTaskAssigneeMap;
    }

    /**
     * For "jbpm_delegation" table "configuration_" column "sequence",
     * "activity" and "role_name" tags.
     * 
     * @param p_str
     *            -- "configuration_" column content.
     * @param p_tagName
     *            -- "sequence", "activity", "role_name".
     * @return String
     */
    private static String getValueForSpecifiedTag(String p_str, String p_tagName)
    {
        if (StringUtil.isEmpty(p_str) || StringUtil.isEmpty(p_tagName))
        {
            return "";
        }

        String result = "";
        try
        {
            int index = p_str.indexOf("</" + p_tagName + ">");
            if (index > -1)
            {
                result = p_str.substring(0, index);
                index = result.lastIndexOf(">");
                result = result.substring(index + 1);
            }            
        }
        catch (Exception ignore)
        {
        }

        return result;
    }

    /**
     * Get a process definitions string like "1,2,3".
     */
    private static String getProcessDefinitions(Set<Long> processDefinitions)
    {
        StringBuilder processDefs = new StringBuilder();
        int count = 0;
        for (Long pd : processDefinitions)
        {
            processDefs.append(pd);
            count++;
            if (count < processDefinitions.size())
            {
                processDefs.append(",");
            }
        }

        return processDefs.toString();
    }

    /**
     * Help class for "getTaskAssigneesByJob(jobId)" method.
     */
    static class TaskAssignee
    {
        private long workflowId = -1;
        private long taskId = -1;
        private String taskName = null;
        private String nodeName = null;
        private long processDefinition = -1;

        public long getWorkflowId()
        {
            return workflowId;
        }

        public void setWorkflowId(long workflowId)
        {
            this.workflowId = workflowId;
        }

        public long getTaskId()
        {
            return taskId;
        }

        public void setTaskId(long taskId)
        {
            this.taskId = taskId;
        }

        public String getTaskName()
        {
            return taskName;
        }

        public void setTaskName(String taskName)
        {
            this.taskName = taskName;
        }

        public String getNodeName()
        {
            return nodeName;
        }

        public void setNodeName(String nodeName)
        {
            this.nodeName = nodeName;
        }

        public long getProcessDefinition()
        {
            return processDefinition;
        }

        public void setProcessDefinition(long processDefinition)
        {
            this.processDefinition = processDefinition;
        }
    }

    /**
     * Help class for "getTaskAssigneesByJob(jobId)" method.
     */
    static class TaskConfiguration
    {
        private long delegationId = -1;
        private long processDefinition = -1;
        private String configuration = null;

        public long getDelegationId()
        {
            return delegationId;
        }

        public void setDelegationId(long delegationId)
        {
            this.delegationId = delegationId;
        }

        public long getProcessDefinition()
        {
            return processDefinition;
        }

        public void setProcessDefinition(long processDefinition)
        {
            this.processDefinition = processDefinition;
        }

        public String getConfiguration()
        {
            return configuration;
        }

        public void setConfiguration(String configuration)
        {
            this.configuration = configuration;
        }
    }

    /**
     * Create new user
     * 
     * @param p_accessToken
     *            String Access token. This field cannot be null
     * @param p_userId
     *            String User ID. This field cannot be null. 
     *            Example: 'qaadmin'
     * @param p_password
     *            String Password. This field cannot be null
     * @param p_firstName
     *            String First name. This field cannot be null
     * @param p_lastName
     *            String Last name. This field cannot be null
     * @param p_email
     *            String Email address. This field cannot be null. 
     *            If the email address is not vaild then the user's status will be set up as inactive
     * @param p_permissionGrps
     *            String[] Permission groups which the new user belongs to.
     *            The element in the array is the name of permission group.
     *            Example: [{"Administrator"}, {"ProjectManager"}]
     * @param p_status
     *            String Status of user. This parameter is not using now, it should be null.
     * @param p_roles
     *            Roles String information of user. It uses a string with XML format to mark all roles information of user.
     *            Example:
     *              <?xml version=\"1.0\"?>
     *                <roles>
     *                  <role>
     *                    <sourceLocale>en_US</sourceLocale>
     *                    <targetLocale>de_DE</targetLocale>
     *                    <activities>
     *                      <activity>
     *                        <name>Dtp1</name>
     *                      </activity>
     *                      <activity>
     *                        <name>Dtp2</name>
     *                      </activity>
     *                    </activities>
     *                  </role>
     *                </roles>
     * @param p_isInAllProject
     *            boolean If the user need to be included in all project.
     * @param p_projectIds
     *            String[] ID of projects which user should be included in. If p_isInAllProject is true, this will not take effect.
     *            Example: [{"1"}, {"3"}]
     * @return int Return code 
     *        0 -- Success 
     *        1 -- Invalid access token 
     *        2 -- Invalid user id 
     *        3 -- Cannot create super user
     *        4 -- User exists
     *        5 -- User does NOT exist
     *        6 -- User is NOT in the same company with logged user
     *        7 -- Invalid user password 
     *        8 -- Invalid first name 
     *        9 -- Invalid last name 
     *       10 -- Invalid email address 
     *       11 -- Invalid permission groups 
     *       12 -- Invalid project information 
     *       13 -- Invalid role information 
     *       -1 -- Unknown exception
     * @throws WebServiceException
     */
    int createUser(String p_accessToken, String p_userId,
            String p_password, String p_firstName, String p_lastName,
            String p_email, String[] p_permissionGrps, String p_status,
            String p_roles, boolean p_isInAllProject, String[] p_projectIds)
            throws WebServiceException
    {
        checkAccess(p_accessToken, "createUser");
        checkPermission(p_accessToken, Permission.USERS_NEW);

        int checkResult = validateUserInfo(p_accessToken, p_userId, p_password,
                p_firstName, p_lastName, p_email, p_permissionGrps,
                p_isInAllProject, p_projectIds, true);
        if (checkResult > 0)
            return checkResult;
        
        try
        {
            // Get current user as requesting user

            UserManagerWLRemote userManager = ServerProxy.getUserManager();

            User loggedUser = getUser(getUsernameFromSession(p_accessToken));
            Company company = ServerProxy.getJobHandler().getCompany(
                    loggedUser.getCompanyName());
            long companyId = company.getId();
            String companyIdString = String.valueOf(companyId);

            // Set up basic user information
            User user = userManager.createUser();
            //Because UserUtil.newUserId(...) will insert the relationship between
            //userid and username into user_id_user_name table directly, Then
            //for creating new user, it should be generated later after role checking
            //user.setUserId(UserUtil.newUserId(p_userId));
            user.setUserName(p_userId.trim());
            user.setFirstName(p_firstName.trim());
            user.setLastName(p_lastName.trim());
            user.setEmail(p_email.trim());
            user.setPassword(p_password.trim());
            user.setCompanyName(loggedUser.getCompanyName());
            user.isInAllProjects(p_isInAllProject);

            // Set up project information
            ArrayList<Long> projectIds = new ArrayList<Long>();
            ProjectHandlerWLRemote projectManager = ServerProxy
                    .getProjectHandler();
            if (p_isInAllProject)
            {
                // user is in all projects
                List<Project> projects = (List<Project>)projectManager.getAllProjects();
                if (projects == null || projects.size() == 0)
                    return INVALID_PROJECTS;
                for (Project project : projects)
                    projectIds.add(project.getIdAsLong());
            }
            else
            {
                // user is in some special projects
                for (String projectId : p_projectIds)
                    projectIds.add(Long.parseLong(projectId));
            }

            List<UserRole> roles = parseRoles(user, p_roles);
            if (roles == null)
                return INVALID_ROLES;
            
            user.setUserId(UserUtil.newUserId(p_userId.trim()));
            for (UserRole ur : roles)
                ur.setUser(user.getUserId());
            
            // Check the argument of permssion groups
            // Get all permission groups in special company
            ArrayList<PermissionGroup> permissionGroups = new ArrayList<PermissionGroup>();
            PermissionManager permissionManager = Permission.getPermissionManager();
            List<PermissionGroup> companyPermissionGroups = (List<PermissionGroup>) permissionManager
                    .getAllPermissionGroupsByCompanyId(companyIdString);
            
            //Get permission group map
            HashMap<String, PermissionGroup> permissionGroupsMap = new HashMap<String, PermissionGroup>();
            for (PermissionGroup pg : companyPermissionGroups)
                permissionGroupsMap.put(pg.getName(), pg);
            
            for (String pgName : p_permissionGrps)
                permissionGroups.add(permissionGroupsMap.get(pgName));
            
            // Add user
            userManager.addUser(loggedUser, user, projectIds, null, roles);
            user = userManager.getUser(user.getUserId());
            if (user != null) {
                // Set up user's permission groups
                ArrayList<String> users = new ArrayList<String>(1);
                users.add(user.getUserId());
                for (PermissionGroup pg : permissionGroups) 
                    permissionManager.mapUsersToPermissionGroup(users, pg);
            }
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
            return UNKNOWN_ERROR;
        }

        return SUCCESS;
    }
    
    private int validateUserInfo(String accessToken, String userId,
            String password, String firstName, String lastName, String email,
            String[] permissionGroups, boolean isInAllProjects,
            String[] projectIds, boolean isToCreateUser)
    {
        // Basic check of parameters
        if (StringUtil.isEmpty(accessToken))
            return INVALID_ACCESS_TOKEN;
        if (StringUtil.isEmpty(userId) || !RegexUtil.validUserId(userId))
            return INVALID_USER_ID;
        if (isToCreateUser) {
            //Create new user
            if (StringUtil.isEmpty(password) || password.length() < 8)
                return INVALID_PASSWORD;
            if (StringUtil.isEmpty(firstName) || firstName.length() > 100)
                return INVALID_FIRST_NAME;
            if (StringUtil.isEmpty(lastName) || lastName.length() > 100)
                return INVALID_LAST_NAME;
            if (StringUtil.isEmpty(email) || !RegexUtil.validEmail(email))
                return INVALID_EMAIL_ADDRESS;
            if (permissionGroups == null || permissionGroups.length == 0)
                return INVALID_PERMISSION_GROUPS;
            if (projectIds == null || projectIds.length == 0)
                return INVALID_PROJECTS;
        } else {
            //Modify user
            if (StringUtil.isNotEmpty(password) && password.length() < 8)
                return INVALID_PASSWORD;
            if (StringUtil.isNotEmpty(firstName) && firstName.length() > 100)
                return INVALID_FIRST_NAME;
            if (StringUtil.isNotEmpty(lastName) && lastName.length() > 100)
                return INVALID_LAST_NAME;
            if (StringUtil.isNotEmpty(email) && !RegexUtil.validEmail(email))
                return INVALID_EMAIL_ADDRESS;
        }
        
        userId = userId.trim();

        // Check access token and get logged user and his company info
        String loggedUserName = getUsernameFromSession(accessToken);
        User loggedUser = null;
        try
        {
            loggedUser = getUser(loggedUserName);
            if (StringUtil.isEmpty(loggedUserName) || loggedUser == null)
                return INVALID_ACCESS_TOKEN;
        }
        catch (WebServiceException e)
        {
            return INVALID_ACCESS_TOKEN;
        }
        Company companyOfLoggedUser = CompanyWrapper
                .getCompanyByName(loggedUser.getCompanyName());
        if (CompanyWrapper.SUPER_COMPANY_ID.equals(String
                .valueOf(companyOfLoggedUser.getId())))
            return CAN_NOT_CREATE_SUPER_USER;
        long companyIdOfLoggedUser = companyOfLoggedUser.getId();
        String companyIdStringOfLoggedUser = String
                .valueOf(companyIdOfLoggedUser);

        // Check user id
        User user = null;
        String k = "";
        try
        {
            UserManagerWLRemote userManager = ServerProxy.getUserManager();
            if (isToCreateUser)
            {
                Vector<User> usersInCompany = userManager
                        .getUsersFromCompany(companyIdStringOfLoggedUser);
                for (User iUser : usersInCompany)
                {
                    if (iUser.getUserName().equalsIgnoreCase(userId))
                        return USER_EXISTS;
                } 
            }
            else
            {
                user = userManager.getUser(userId);
                if (user == null)
                    return USER_NOT_EXIST;
                if (!user.getCompanyName().equals(loggedUser.getCompanyName()))
                    return NOT_IN_SAME_COMPANY;
            }
        }
        catch (Exception e)
        {
            return INVALID_USER_ID;
        }

        // Check permission groups
        if (permissionGroups != null && permissionGroups.length > 0) {
            PermissionManager permissionManager = null;
            ArrayList<String> permissionGroupList = new ArrayList<String>();
            try
            {
                for (String perm : permissionGroups)
                {
                    if (StringUtil.isEmpty(perm))
                        return INVALID_PERMISSION_GROUPS;
                    permissionGroupList.add(perm.trim());
                }
    
                permissionManager = Permission.getPermissionManager();
                ArrayList<PermissionGroup> companyPermissionGroups = (ArrayList<PermissionGroup>) permissionManager
                        .getAllPermissionGroupsByCompanyId(companyIdStringOfLoggedUser);
                ArrayList<String> validPermissionGroupsList = new ArrayList<String>();
                for (PermissionGroup pg : companyPermissionGroups)
                    validPermissionGroupsList.add(pg.getName());
    
                for (String pg : permissionGroupList)
                {
                    if (!validPermissionGroupsList.contains(pg))
                        return INVALID_PERMISSION_GROUPS;
                }
            }
            catch (Exception e)
            {
                return INVALID_PERMISSION_GROUPS;
            }
        }

        // Check project Ids
        if (!isInAllProjects)
        {
            if (projectIds != null && projectIds.length > 0) {
                try
                {
                    Project project = null;
                    ProjectHandlerWLRemote projectManager = ServerProxy
                            .getProjectHandler();
                    long projectId = -1L;
    
                    for (String pid : projectIds)
                    {
                        if (StringUtil.isEmpty(pid))
                            return INVALID_PROJECTS;
                        projectId = Long.parseLong(pid.trim());
                        project = projectManager.getProjectById(projectId);
                        if (project == null
                                || project.getCompanyId() != companyIdOfLoggedUser)
                            return INVALID_PROJECTS;
                    }
                }
                catch (Exception e)
                {
                    return INVALID_PROJECTS;
                }
            }
        }

        return SUCCESS;
    }

    /**
     * Modify user
     * 
     * @param p_accessToken
     *            String Access token. This field cannot be null
     * @param p_userId
     *            String User ID. This field cannot be null. 
     *            Example: 'qaadmin'
     * @param p_password
     *            String Password. This field cannot be null
     * @param p_firstName
     *            String First name. This field cannot be null
     * @param p_lastName
     *            String Last name. This field cannot be null
     * @param p_email
     *            String Email address. This field cannot be null. 
     *            If the email address is not vaild then the user's status will be set up as inactive
     * @param p_permissionGrps
     *            String[] Permission groups which the new user belongs to.
     *            The element in the array is the name of permission group.
     *            Example: [{"Administrator"}, {"ProjectManager"}]
     * @param p_status
     *            String Status of user. This parameter is not using now, it should be null.
     * @param p_roles
     *            Roles String information of user. It uses a string with XML format to mark all roles information of user.
     *            Example:
     *              <?xml version=\"1.0\"?>
     *                <roles>
     *                  <role>
     *                    <sourceLocale>en_US</sourceLocale>
     *                    <targetLocale>de_DE</targetLocale>
     *                    <activities>
     *                      <activity>
     *                        <name>Dtp1</name>
     *                      </activity>
     *                      <activity>
     *                        <name>Dtp2</name>
     *                      </activity>
     *                    </activities>
     *                  </role>
     *                </roles>
     * @param p_isInAllProject
     *            boolean If the user need to be included in all project.
     * @param p_projectIds
     *            String[] ID of projects which user should be included in. If p_isInAllProject is true, this will not take effect.
     *            Example: [{"1"}, {"3"}]
     * @return int Return code 
     *        0 -- Success 
     *        1 -- Invalid access token 
     *        2 -- Invalid user id 
     *        3 -- Cannot create super user
     *        4 -- User exists
     *        5 -- User does NOT exist
     *        6 -- User is NOT in the same company with logged user
     *        7 -- Invalid user password 
     *        8 -- Invalid first name 
     *        9 -- Invalid last name 
     *       10 -- Invalid email address 
     *       11 -- Invalid permission groups 
     *       12 -- Invalid project information 
     *       13 -- Invalid role information 
     *       -1 -- Unknown exception
     * @throws WebServiceException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    int modifyUser(String p_accessToken, String p_userId, String p_password,
            String p_firstName, String p_lastName, String p_email,
            String[] p_permissionGrps, String p_status, String p_roles,
            boolean p_isInAllProject, String[] p_projectIds)
            throws WebServiceException
    {
        checkAccess(p_accessToken, "modifyUser");
        checkPermission(p_accessToken, Permission.USERS_EDIT);
        
        int checkResult = validateUserInfo(p_accessToken, p_userId, p_password,
                p_firstName, p_lastName, p_email, p_permissionGrps,
                p_isInAllProject, p_projectIds, false);
        if (checkResult > 0)
            return checkResult;

        try
        {
            // Get current user as requesting user
            User currentUser = getUser(getUsernameFromSession(p_accessToken));
            UserManagerWLRemote userManager = ServerProxy.getUserManager();
            PermissionManager permissionManager = Permission
                    .getPermissionManager();

            Company company = ServerProxy.getJobHandler().getCompany(
                    currentUser.getCompanyName());
            long companyId = company.getId();

            // Set up basic user information
            User user = userManager.getUser(p_userId);
            
            if (StringUtil.isNotEmpty(p_firstName))
                user.setFirstName(p_firstName.trim());
            if (StringUtil.isNotEmpty(p_lastName))
                user.setLastName(p_lastName.trim());
            if (StringUtil.isNotEmpty(p_email))
                user.setEmail(p_email.trim());
            if (StringUtil.isNotEmpty(p_password))
                user.setPassword(p_password.trim());
            user.isInAllProjects(p_isInAllProject);

            // Set up project information
            ArrayList projectIds = null;
            ProjectHandlerWLRemote projectManager = ServerProxy
                    .getProjectHandler();
            if (p_isInAllProject)
            {
                // user is in all projects
                List<Project> projects = (List<Project>) projectManager.getAllProjects();
                if (projects != null && projects.size() > 0) {
                    projectIds = new ArrayList();
                    for (Project project : projects)
                        projectIds.add(project.getIdAsLong());
                }
            }
            else
            {
                if (p_projectIds != null && p_projectIds.length > 0) {
                    Project project = null;
                    projectIds = new ArrayList();
                    for (String pid : p_projectIds) {
                        project = projectManager.getProjectById(Long.parseLong(pid.trim()));
                        projectIds.add(project.getIdAsLong());
                    }
                }
            }

            List<UserRole> roles = parseRoles(user, p_roles);
            if (roles == null)
                return INVALID_ROLES;
            
            for (UserRole ur : roles)
                ur.setUser(user.getUserId());

            // Check the argument of permssion groups
            // Get all permission groups in special company
            ArrayList updatePermissionGroups = null;
            if (p_permissionGrps != null && p_permissionGrps.length > 0) {
                updatePermissionGroups = new ArrayList();
                
                List<PermissionGroup> companyPermissionGroups = (List<PermissionGroup>) permissionManager
                        .getAllPermissionGroupsByCompanyId(String.valueOf(companyId));
                
                //Get permission group map
                HashMap<String, PermissionGroup> permissionGroupsMap = new HashMap<String, PermissionGroup>();
                for (PermissionGroup pg : companyPermissionGroups)
                    permissionGroupsMap.put(pg.getName(), pg);
                
                for (String pgName : p_permissionGrps)
                    updatePermissionGroups.add(permissionGroupsMap.get(pgName));
            }            

            // Modify user
            userManager.modifyUser(currentUser, user, projectIds, null, roles);

            // Set up user's permission groups
            updatePermissionGroups(p_userId, updatePermissionGroups);
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
            return UNKNOWN_ERROR;
        }

        return SUCCESS;
    }

    /**
     * Parse roles information from XML format string.
     * The XML format string is like below:
     * <?xml version=\"1.0\"?>
     * <roles>
     *   <role>
     *     <sourceLocale>en_US</sourceLocale>
     *     <targetLocale>de_DE</targetLocale>
     *     <activities>
     *       <activity>
     *         <name>Dtp1</name>
     *       </activity>
     *       <activity>
     *         <name>Dtp2</name>
     *       </activity>
     *     </activities>
     *   </role>
     * </roles>
     * 
     * @param p_user -- User
     * @param p_xml -- Roles information
     * @return List<UserRole>
     */
    @SuppressWarnings({ "unused", "rawtypes" })
    private List<UserRole> parseRoles(User p_user, String p_xml)
    {
        if (StringUtil.isEmpty(p_xml))
            return null;

        ArrayList<UserRole> roles = new ArrayList<UserRole>();
        try
        {
            XmlParser parser = new XmlParser();
            Document doc = parser.parseXml(p_xml);
            Element root = doc.getRootElement();
            List rolesList = root.elements();
            if (rolesList == null || rolesList.size() == 0)
                return null;

            String sourceLocale, targetLocale, activityId, activityName, activityDisplayName, activityUserType, activityType;
            Activity activity = null;
            UserRole role = null;
            LocalePair localePair = null;

            UserManagerWLRemote userManager = ServerProxy.getUserManager();
            JobHandlerWLRemote jobManager = ServerProxy.getJobHandler();
            Company company = CompanyWrapper.getCompanyByName(p_user
                    .getCompanyName());

            for (Iterator iter = rolesList.iterator(); iter.hasNext();)
            {
                Element roleElement = (Element) iter.next();
                sourceLocale = roleElement.element("sourceLocale").getText();
                targetLocale = roleElement.element("targetLocale").getText();
                localePair = ServerProxy.getLocaleManager()
                        .getLocalePairBySourceTargetStrings(sourceLocale,
                                targetLocale);
                if (localePair == null)
                    return null;

                List activitiesList = roleElement.elements("activities");
                if (activitiesList == null || activitiesList.size() == 0)
                    return null;

                for (Iterator iter1 = activitiesList.iterator(); iter1
                        .hasNext();)
                {
                    Element activitiesElement = (Element) iter1.next();

                    List activityList = activitiesElement.elements();
                    for (Iterator iter2 = activityList.iterator(); iter2
                            .hasNext();)
                    {
                        Element activityElement = (Element) iter2.next();
                        activityName = activityElement.element("name")
                                .getText();
                        activity = jobManager
                                .getActivityByDisplayName(activityName);
                        if (activity == null
                                || activity.getCompanyId() != company.getId())
                            return null;

                        role = userManager.createUserRole();
                        ((Role) role).setActivity(activity);
                        ((Role) role).setSourceLocale(sourceLocale);
                        ((Role) role).setTargetLocale(targetLocale);
                        //role.setUser(p_user.getUserId());
                        roles.add(role);
                    }
                }
            }
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
            return null;
        }
        return roles;
    }

    /**
     * Update user's permission groups
     * 
     * @param p_userId
     *            User ID
     * @param p_permissionGrps
     *            Permission groups
     * @throws EnvoyServletException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void updatePermissionGroups(String p_userId, List p_permissionGrps)
            throws EnvoyServletException
    {
        ArrayList changed = (ArrayList) p_permissionGrps;
        if (changed == null)
            return;
        ArrayList existing = (ArrayList) PermissionHelper
                .getAllPermissionGroupsForUser(p_userId);
        if (existing == null && changed.size() == 0)
            return;

        ArrayList list = new ArrayList(1);
        list.add(p_userId);
        try
        {
            PermissionManager manager = Permission.getPermissionManager();
            if (existing == null)
            {
                // just adding new perm groups
                for (int i = 0; i < changed.size(); i++)
                {
                    PermissionGroup pg = (PermissionGroup) changed.get(i);
                    manager.mapUsersToPermissionGroup(list, pg);
                }
            }
            else
            {
                // need to determine what to add and what to remove.
                // Loop thru old list and see if perm is in new list. If not,
                // remove it.
                for (int i = 0; i < existing.size(); i++)
                {
                    PermissionGroup pg = (PermissionGroup) existing.get(i);
                    boolean found = false;
                    for (int j = 0; j < changed.size(); j++)
                    {
                        PermissionGroup cpg = (PermissionGroup) changed.get(j);
                        if (pg.getId() == cpg.getId())
                        {
                            found = true;
                            break;
                        }
                    }
                    if (!found)
                        manager.unMapUsersFromPermissionGroup(list, pg);
                }

                // Loop thru new list and see if perm is in old list. If not,
                // add it.
                for (int i = 0; i < changed.size(); i++)
                {
                    boolean found = false;
                    PermissionGroup pg = (PermissionGroup) changed.get(i);
                    for (int j = 0; j < existing.size(); j++)
                    {
                        PermissionGroup cpg = (PermissionGroup) existing.get(j);
                        if (pg.getId() == cpg.getId())
                        {
                            found = true;
                            break;
                        }
                    }
                    if (!found)
                        manager.mapUsersToPermissionGroup(list, pg);
                }
            }
        }
        catch (Exception e)
        {
            throw new EnvoyServletException(e);
        }
    }

    /**
     * Reassign task to other translators
     *  
     * @param p_accessToken
     *            String Access token
     * @param p_workflowId
     *            String ID of task
     *            Example: "10"
     * @param p_users
     *            String[] Users' information who will be reassigned to. The element in the array is [{userid}].
     *            Example: ["qaadmin", "qauser"]
     * @return 
     *            Return null if the reassignment executes successfully.
     *            Otherwise it will throw exception or return error message
     * @throws WebServiceException
     */
    @SuppressWarnings("rawtypes")
    String taskReassign(String p_accessToken, String p_taskId,
            String[] p_users) throws WebServiceException
    {
        try
        {
            Assert.assertNotEmpty(p_accessToken, "Access token");
            Assert.assertNotEmpty(p_taskId, "Task Id");
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
            throw new WebServiceException(e.getMessage());
        }

        if (p_users == null || p_users.length == 0)
        {
            throw new WebServiceException("Users is null");
        }
        checkAccess(p_accessToken, "jobsReassign");
        checkPermission(p_accessToken, Permission.JOB_WORKFLOWS_REASSIGN);

        ArrayList<String> reassignedUsers = new ArrayList<String>();
        StringBuffer users = new StringBuffer();
        for (String userId : p_users)
        {
            if (StringUtil.isEmpty(userId))
                continue;
            userId = userId.trim();
            users.append(userId + ",");
            reassignedUsers.add(userId);
        }

        ActivityLog.Start activityStart = null;
        try
        {
            String userName = getUsernameFromSession(p_accessToken);
            Map<Object, Object> activityArgs = new HashMap<Object, Object>();
            activityArgs.put("loggedUserName", userName);
            activityArgs.put("taskId", p_taskId);
            activityArgs.put("users", users.toString());
            activityStart = ActivityLog
                    .start(Ambassador.class,
                            "jobsReassign(p_accessToken, p_workflowId,p_targetLocale,p_workflowId,p_users)",
                            activityArgs);

            User loggedUser = getUser(userName);

            Task taskInfo = null;
            try
            {
                taskInfo = ServerProxy.getTaskManager().getTask(
                        Integer.parseInt(p_taskId));
            }
            catch (Exception e)
            {
                logger.error("Error found in get task info", e);
                return "Incorrect task ID";
            }

            if (taskInfo == null)
                return "Cannot find task";

            Company company = CompanyWrapper.getCompanyByName(loggedUser
                    .getCompanyName());
            long companyId = company.getId();
            String companyName = company.getCompanyName();
            if (companyId != taskInfo.getCompanyId())
                return "Cannot re-assign task which is in different company";

            String taskAcceptor = taskInfo.getAcceptor();
            // If current task acceptor is the user to be reassigned, then
            // return
            if (!StringUtil.isEmpty(taskAcceptor))
            {
                if (reassignedUsers.size() == 1
                        && reassignedUsers.contains(taskAcceptor))
                    return "Current task acceptor is the same with user being reassigned";
            }

            String errorMessage = "";
            String sourceLocale = taskInfo.getSourceLocale().toString();
            String targetLocale = taskInfo.getTargetLocale().toString();
            List<Task> tasks = new ArrayList<Task>();
            tasks.add(taskInfo);
            Workflow wf = taskInfo.getWorkflow();
            long wfId = wf.getId();
            String workflowState = wf.getState();
            if (!Workflow.READY_TO_BE_DISPATCHED.equals(workflowState)
                    && !Workflow.DISPATCHED.equals(workflowState))
            {
                return "Workflow which contains the task is not in 'Ready' or 'In Progress' state.";
            }

            wf = ServerProxy.getWorkflowManager().getWorkflowById(wfId);
            Hashtable taskUserHash = new Hashtable();
            Hashtable taskSelectedUserHash = new Hashtable();

            updateUsers(tasks, taskUserHash, taskSelectedUserHash, wf);

            Enumeration keys = taskUserHash.keys();
            HashMap<Long, Vector<NewAssignee>> roleMap = new HashMap<Long, Vector<NewAssignee>>();
            long taskId = -1;
            String displayRole = "";
            ContainerRole containerRole = null;
            Activity activity = null;
            String[] roles = null;
            Vector<NewAssignee> newAssignees = null;
            Task task = null;
            User user = null;
            Hashtable<String, UserInfo> taskUsers = null;

            while (keys.hasMoreElements())
            {
                task = (Task) keys.nextElement();
                String taskState = task.getStateAsString();
                if (!Task.STATE_ACTIVE_STR.equals(taskState)
                        && !Task.STATE_DEACTIVE_STR.equals(taskState)
                        && !Task.STATE_ACCEPTED_STR.equals(taskState))
                {
                    errorMessage += " task state is not in Active, Deactive or Accepted status.";
                    continue;
                }
                taskUsers = (Hashtable<String, UserInfo>) taskUserHash
                        .get(task);
                taskId = task.getId();
                activity = ServerProxy.getJobHandler()
                        .getActivityByCompanyId(task.getTaskName(),
                                String.valueOf(task.getCompanyId()));
                containerRole = ServerProxy.getUserManager().getContainerRole(
                        activity, sourceLocale, targetLocale);
                ArrayList<User> vaildUsers = new ArrayList<User>();
                for (String userId : reassignedUsers)
                {
                    user = ServerProxy.getUserManager().getUser(userId);
                    if (!taskUsers.containsKey(userId))
                    {
                        errorMessage += " " + userId
                                + " has not corresponding role.";
                        continue;
                    }
                    vaildUsers.add(user);
                }
                if (vaildUsers.size() == 0)
                    continue;

                newAssignees = new Vector<NewAssignee>();
                roles = new String[vaildUsers.size()];
                int i = 0;
                for (User userInfo : vaildUsers)
                {
                    roles[i] = containerRole.getName() + " "
                            + userInfo.getUserId();
                    if (i == vaildUsers.size() - 1)
                    {
                        displayRole += userInfo.getUserName();
                    }
                    else
                    {
                        displayRole += userInfo.getUserName() + ",";
                    }
                    i++;
                }
                newAssignees.addElement(new NewAssignee(roles, displayRole,
                        true));
                roleMap.put(taskId, newAssignees);
            }

            boolean shouldModifyWf = false;
            WorkflowInstance wi = ServerProxy.getWorkflowServer()
                    .getWorkflowInstanceById(wf.getId());

            Vector<WorkflowTaskInstance> wfiTasks = wi
                    .getWorkflowInstanceTasks();
            for (WorkflowTaskInstance wti : wfiTasks)
            {
                newAssignees = roleMap.get(wti.getTaskId());
                if (newAssignees != null)
                {
                    for (int r = 0; r < newAssignees.size(); r++)
                    {
                        NewAssignee na = (NewAssignee) newAssignees
                                .elementAt(r);
                        if (na != null
                                && !areSameRoles(wti.getRoles(), na.m_roles))
                        {
                            shouldModifyWf = true;
                            wti.setRoleType(na.m_isUserRole);
                            wti.setRoles(na.m_roles);
                            wti.setDisplayRoleName(na.m_displayRoleName);
                        }
                    }
                }
            }

            // modify one workflow at a time and reset the flag
            if (shouldModifyWf)
            {
                shouldModifyWf = false;
                ServerProxy.getWorkflowManager().modifyWorkflow(null, wi, null,
                        null);
            }

            if (!StringUtil.isEmpty(errorMessage))
                return errorMessage.substring(1);
        }
        catch (Exception e)
        {
            throw new EnvoyServletException(e);
        }
        finally
        {
            if (activityStart != null)
            {
                activityStart.end();
            }
        }

        return null;
    }

    /**
     * Get the list of users for each Review-Only activity.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void updateUsers(List p_tasks, Hashtable p_taskUserHash,
            Hashtable p_taskSelectedUserHash, Workflow p_wf)
            throws GeneralException, RemoteException
    {
        Project proj = p_wf.getJob().getL10nProfile().getProject();
        for (Iterator iter = p_tasks.iterator(); iter.hasNext();)
        {
            Hashtable userHash = new Hashtable();
            Hashtable selectedUserHash = new Hashtable();
            Task task = (Task) iter.next();

            List selectedUsers = null;
            long taskId = task.getId();
            WorkflowInstance wi = ServerProxy.getWorkflowServer().getWorkflowInstanceById(p_wf.getId());
            //WorkflowTaskInstance wfTask = p_wf.getIflowInstance()
            //        .getWorkflowTaskById(taskId);
            WorkflowTaskInstance wfTask = wi.getWorkflowTaskById(taskId);
            String[] roles = wfTask.getRoles();
            String[] userIds = ServerProxy.getUserManager()
                    .getUserIdsFromRoles(roles, proj);
            if ((userIds != null) && (userIds.length > 0))
            {
                selectedUsers = ServerProxy.getUserManager().getUserInfos(
                        userIds);
            }

            // get all users for this task and locale pair.
            List userInfos = ServerProxy.getUserManager().getUserInfos(
                    task.getTaskName(), task.getSourceLocale().toString(),
                    task.getTargetLocale().toString());
            Set<String> projectUserIds = null;
            if (proj != null)
            {
                projectUserIds = proj.getUserIds();
            }

            if (userInfos == null)
                continue;

            for (Iterator iter2 = userInfos.iterator(); iter2.hasNext();)
            {
                UserInfo userInfo = (UserInfo) iter2.next();
                // filter user by project
                if (projectUserIds != null)
                {
                    String userId = userInfo.getUserId();
                    // if the specified user is contained in the project
                    // then add to the Hash.
                    if (projectUserIds.contains(userId))
                    {
                        userHash.put(userInfo.getUserId(), userInfo);
                    }
                }
            }
            p_taskUserHash.put(task, userHash);
            if (selectedUsers == null)
                continue;

            for (Iterator iter3 = selectedUsers.iterator(); iter3.hasNext();)
            {
                UserInfo ta = (UserInfo) iter3.next();
                selectedUserHash.put(ta.getUserId(), ta);
            }
            p_taskSelectedUserHash.put(task, selectedUserHash);
        }
    }

    /**
     * Determines whether the two array of roles contain the same set of role
     * names.
     */
    private boolean areSameRoles(String[] p_workflowRoles,
            String[] p_selectedRoles)
    {
        // First need to sort since Arrays.equals() requires
        // the parameters to be sorted
        Arrays.sort(p_workflowRoles);
        Arrays.sort(p_selectedRoles);
        return Arrays.equals(p_workflowRoles, p_selectedRoles);
    }

    /**
     * Get tasks in specified workflow
     * 
     * @param p_wfId
     *            workflow ID
     * @return ArrayList Collection of tasks which is in the specified workflow
     * @throws WebServiceException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ArrayList<Task> getTasksInWorkflow(String p_wfId)
            throws WebServiceException
    {
        ArrayList<Task> tasks = new ArrayList<Task>();
        long wfId = 0l;

        // Validate workflow ID
        if (p_wfId == null || p_wfId.trim().length() == 0)
            return tasks;
        try
        {
            wfId = Long.parseLong(p_wfId);
        }
        catch (NumberFormatException nfe)
        {
            throw new WebServiceException("Wrong workflow ID");
        }

        try
        {
            WorkflowInstance workflowInstance = WorkflowProcessAdapter
                    .getProcessInstance(wfId);
            Workflow workflow = ServerProxy.getWorkflowManager()
                    .getWorkflowByIdRefresh(wfId);
            Hashtable tasksInWF = workflow.getTasks();

            // get the NodeInstances of TYPE_ACTIVITY
            List<WorkflowTaskInstance> nodesInPath = workflowInstance
                    .getDefaultPathNode();

            for (WorkflowTaskInstance task : nodesInPath)
            {
                Task taskInfo = (Task) tasksInWF.get(task.getTaskId());

                if (taskInfo.reassignable())
                {
                    tasks.add(taskInfo);
                }
            }
            return tasks;
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
            throw new WebServiceException(e.getMessage());
        }
    }

    private class NewAssignee
    {
        String m_displayRoleName = null;

        String[] m_roles = null;

        boolean m_isUserRole = false;

        NewAssignee(String[] p_roles, String p_displayRoleName,
                boolean p_isUserRole)
        {
            m_displayRoleName = p_displayRoleName;
            m_roles = p_roles;
            m_isUserRole = p_isUserRole;
        }
    }

    @Override
    protected void checkIfInstalled() throws WebServiceException
    {
        // do nothing
    }

}