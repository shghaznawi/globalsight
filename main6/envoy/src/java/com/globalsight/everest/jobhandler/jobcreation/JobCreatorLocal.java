/**
] *  Copyright 2009 Welocalize, Inc. 
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
package com.globalsight.everest.jobhandler.jobcreation;

// Java
import java.io.File;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Vector;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.w3c.dom.Element;

import com.globalsight.cxe.adapter.documentum.DocumentumOperator;
import com.globalsight.cxe.entity.fileprofile.FileProfile;
import com.globalsight.cxe.entity.knownformattype.KnownFormatType;
import com.globalsight.cxe.message.CxeMessageType;
import com.globalsight.cxe.util.CxeProxy;
import com.globalsight.cxe.util.EventFlowXmlParser;
import com.globalsight.everest.comment.Comment;
import com.globalsight.everest.comment.CommentManagerWLRemote;
import com.globalsight.everest.comment.Issue;
import com.globalsight.everest.comment.IssueEditionRelation;
import com.globalsight.everest.comment.IssueHistoryImpl;
import com.globalsight.everest.corpus.CorpusDoc;
import com.globalsight.everest.corpus.CorpusDocGroup;
import com.globalsight.everest.corpus.CorpusManagerWLRemote;
import com.globalsight.everest.edit.CommentHelper;
import com.globalsight.everest.foundation.L10nProfile;
import com.globalsight.everest.foundation.User;
import com.globalsight.everest.foundation.WorkObject;
import com.globalsight.everest.jobhandler.Job;
import com.globalsight.everest.jobhandler.JobEditionInfo;
import com.globalsight.everest.jobhandler.JobHandler;
import com.globalsight.everest.jobhandler.JobImpl;
import com.globalsight.everest.jobhandler.JobPersistenceAccessor;
import com.globalsight.everest.jobhandler.jobmanagement.JobDispatchEngine;
import com.globalsight.everest.page.AddingSourcePageManager;
import com.globalsight.everest.page.DataSourceType;
import com.globalsight.everest.page.PageEventObserver;
import com.globalsight.everest.page.PageState;
import com.globalsight.everest.page.SourcePage;
import com.globalsight.everest.page.TargetPage;
import com.globalsight.everest.page.UpdateSourcePageManager;
import com.globalsight.everest.persistence.PersistenceService;
import com.globalsight.everest.projecthandler.Project;
import com.globalsight.everest.projecthandler.WorkflowTemplateInfo;
import com.globalsight.everest.request.BatchInfo;
import com.globalsight.everest.request.Request;
import com.globalsight.everest.request.RequestHandler;
import com.globalsight.everest.request.RequestImpl;
import com.globalsight.everest.request.reimport.ActivePageReimporter;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.util.system.SystemConfigParamNames;
import com.globalsight.everest.util.system.SystemConfiguration;
import com.globalsight.everest.webapp.pagehandler.administration.config.xmldtd.XmlDtdManager;
import com.globalsight.everest.workflow.EventNotificationHelper;
import com.globalsight.everest.workflowmanager.Workflow;
import com.globalsight.everest.workflowmanager.WorkflowImpl;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.persistence.hibernate.HibernateUtil;
import com.globalsight.persistence.jobcreation.AddRequestToJobCommand;
import com.globalsight.persistence.jobcreation.JobCreationQuery;
import com.globalsight.persistence.jobcreation.UpdateWorkflowAndPageStatesCommand;
import com.globalsight.util.GeneralException;
import com.globalsight.util.GlobalSightLocale;
import com.globalsight.util.date.DateHelper;
import com.globalsight.util.mail.MailerConstants;
import com.globalsight.util.modules.Modules;
import com.globalsight.util.resourcebundle.LocaleWrapper;
import com.globalsight.util.resourcebundle.ResourceBundleConstants;
import com.globalsight.util.resourcebundle.SystemResourceBundle;

/**
 * JobCreatorLocal implements JobCreator and is responsible for importing the
 * request, creating jobs and adding requests (and pages) to the appropriate
 * job. It also notifies the JobDispatcher of the job.
 */
public class JobCreatorLocal implements JobCreator
{
    private static GlobalSightCategory c_logger = (GlobalSightCategory) GlobalSightCategory
            .getLogger(JobCreatorLocal.class);
    private static SystemResourceBundle m_sysResBundle = SystemResourceBundle
            .getInstance();

    private RequestProcessor m_requestProcessor;
    private JobAdditionEngine m_jobAdditionEngine;

    private List m_specialFormatTypes = new ArrayList();

    // determines whether the system-wide notification is enabled
    private boolean m_systemNotificationEnabled = EventNotificationHelper
            .systemNotificationEnabled();

    public static Vector<String> PROCESS_BATCH_IDS = new Vector<String>();
    
    //
    // PUBLIC CONSTRUCTOR
    //
    /**
     * Creates the JobCreator.
     * 
     * @throws JobCreationException
     *             if an error occurs
     */
    public JobCreatorLocal() throws JobCreationException
    {
        m_requestProcessor = new RequestProcessor();
        m_jobAdditionEngine = new JobAdditionEngine();

        loadValidFormatTypes();
    }

    /**
     * Processes the request (ie. Imports the SourcePage and creates the Target
     * Pages) and adds the Request to a Job.
     * 
     * @param p_request
     *            the request object that will be processed and added to a job.
     * 
     * @throws JobCreationException
     *             if any errors occur
     * @throws RemoteException
     *             if there is a network issue
     */
    public void addRequestToJob(Request p_request) throws RemoteException,
            JobCreationException
    {
        c_logger.info("Running in addRequestToJob.");
        HashMap pages = null;
        Job job = null;
        long profileId = p_request.getL10nProfile().getId();
        String batchId = null;
        
        try
        {
            BatchInfo info = p_request.getBatchInfo();
            boolean isBatch = (info != null);
            
            if (isBatch)
            {
                batchId = info.getBatchId();
                while (true)
                {
                    if (!PROCESS_BATCH_IDS.contains(batchId))
                    {
                        PROCESS_BATCH_IDS.add(batchId);
                        break;
                    }
                    Thread.sleep(3000);
                }
            }
            
            pages = m_requestProcessor.processRequest(p_request);

            SourcePage sp = (SourcePage) pages.remove(p_request
                    .getL10nProfile().getSourceLocale().getIdAsLong());

            BatchMonitor monitor = new BatchMonitor();
            job = availableJob(p_request, monitor, isBatch, pages);
            
            //for GS Edition job
            long jobId = job.getId();
            HashMap editionJobParams = p_request.getEditionJobParams();
            if (editionJobParams != null && editionJobParams.size() > 0)
            {
                //save edition job related info.
                saveEditionJobInfo(jobId, editionJobParams);
                
                //job comments info (seems this won't be used)
                Comment comment = saveJobComments(jobId, editionJobParams, p_request);
                
                //segment comments handling
                HashMap segComments = (HashMap) editionJobParams.get("segComments");
                long targetLocaleId = p_request.getTargetLocalesToImport()[0].getId();
                saveSegCommentsToTargetServer(segComments, sp);
            }

            boolean isBatchComplete = isBatchComplete(job, monitor);

            if (p_request.getType() == Request.EXTRACTED_LOCALIZATION_REQUEST)
            {
                addSourceDocToCorpus(sp, p_request, job, isBatchComplete,
                        p_request.getBatchInfo());
            }

            updateJobState(job, isBatch, isBatchComplete, sp);

            // Is this documentum job?
            if (p_request.getDataSourceType().equalsIgnoreCase(
                    DocumentumOperator.DCTM_CATEGORY))
            {
                try
                {
                    String eventFlowXml = p_request.getEventFlowXml();
                    EventFlowXmlParser parser = new EventFlowXmlParser();
                    parser.parse(eventFlowXml);
                    Element msCategory = parser
                            .getCategory(DocumentumOperator.DCTM_CATEGORY);
                    c_logger.debug("Starting to create a documentum job......");

                    String dctmObjId = parser.getCategoryDaValue(msCategory,
                            DocumentumOperator.DCTM_OBJECTID)[0];
                    String isAttrFileStr = parser.getCategoryDaValue(
                            msCategory, DocumentumOperator.DCTM_ISATTRFILE)[0];
                    String userId = parser.getCategoryDaValue(msCategory,
                            DocumentumOperator.DCTM_USERID)[0];
                    Boolean isAttrFile = Boolean.valueOf(isAttrFileStr);

                    if (!isAttrFile.booleanValue())
                    {
                        handleDocumentumJob(job, dctmObjId, userId);
                    }
                    c_logger.debug("Finish to create a documentum job");
                }
                catch (NoSuchElementException nex)
                {
                    c_logger.debug("Not a valid Documentum job");
                }
                catch (Exception ex)
                {
                    c_logger
                            .error(
                                    "Failed to write attribute back to Documentum server",
                                    ex);
                }
            }
            
            if (isBatchComplete)
            {
                XmlDtdManager.validateJob(job);
            }
        }
        catch (Exception e)
        {
            c_logger.debug("exception in job creation", e);

            // with this failure the pages need to be marked as import
            // failed because they aren't successfully added to a job.
            // remove the source page from the collection of pages -
            // should just hold the target pages
            try
            {
                getPageEventObserver().notifyImportFailEvent(
                        p_request.getSourcePage(), pages.values());
            }
            catch (Exception pe)
            {
            }

            String[] args = { Long.toString(p_request.getId()),
                    job == null ? null : Long.toString(job.getId()) };
            throw new JobCreationException(
                    JobCreationException.MSG_FAILED_TO_ADD_REQUEST_TO_JOB,
                    args, e);
        }
        finally
        {
            if (batchId != null)
            {
                PROCESS_BATCH_IDS.remove(batchId);
            }
        }
    }

    //
    // PRIVATE SUPPORT METHODS
    //

    /**
     * Return an appropriate existing job, or create a new one if none exists.
     * This method uses a try-finally (no catch required) block to ensure that
     * all waiting threads are notified when the method is finished.
     */
    private synchronized Job availableJob(Request p_request,
            BatchMonitor p_monitor, boolean p_isBatch, HashMap p_targetPages)
            throws JobCreationException
    {
        JobImpl job = null;
        Connection connection = null;
        int numberOfRowsUpdated = 0;
        Session session = HibernateUtil.getSession();
        Transaction transaction = session.beginTransaction();
        long profileId = p_request.getL10nProfile().getId();
        try
        {
            connection = session.connection();

            // add the request (and source page) to the job
            AddRequestToJobCommand arjc = new AddRequestToJobCommand(
                    (RequestImpl) p_request);
            arjc.persistObjects(connection);

            numberOfRowsUpdated = arjc.getNumberOfRowsUpdated();

            if (numberOfRowsUpdated > 0)
            {
                // get the job
                c_logger.debug("The number of rows is " + numberOfRowsUpdated);
                job = (JobImpl)HibernateUtil.get(JobImpl.class, arjc.getJobId());
                job.setId(arjc.getJobId());
                job.setState(p_isBatch ? Job.BATCHRESERVED : Job.PENDING);
                
                if(p_request.getPriority() != null && 
                        !"".equals(p_request.getPriority()) && 
                        !"null".equals(p_request.getPriority()) ) {
                    job.setPriority(Integer.parseInt(p_request.getPriority()));
                }
                
                session.update(job);

                // verify if there are target pages add them to the workflow(s)
                // if only error pages then target pages won't exist.                
                if (p_targetPages != null && p_targetPages.size() > 0)
                {
                    Collection c = p_targetPages.values();
                    Iterator it = c.iterator();
                    String hql = "from WorkflowImpl w where w.job.id = :jId "
                            + "and w.targetLocale.id = :tId";
                    Map<String,Long> map = new HashMap<String, Long>();
                    map.put("jId", job.getIdAsLong());
                    while (it.hasNext())
                    {
                        TargetPage tp = (TargetPage) it.next();
                        map.put("tId", new Long(tp.getLocaleId()));
                        WorkflowImpl w = (WorkflowImpl) HibernateUtil.search(hql,
                                map).get(0);
                        tp.setWorkflowInstance(w);
                        w.addTargetPage(tp);
                        tp.setTimestamp(new Timestamp(System.currentTimeMillis()));
                        session.update(tp);
                        session.update(w);
                    }
                    
                    // Add job notes for uploaded files if it had.
                    JobAdditionEngine.addJobNote(p_targetPages, job);
                }
            }
            
            connection.commit();
            // commit all together
            transaction.commit();
            
        }
        catch (Exception e)
        {
            c_logger.debug("exception in job creator local", e);

            try
            {
                transaction.rollback();
            }
            catch (Exception sqle)
            {
            }

            String args[] = new String[2];
            args[0] = Long.toString(p_request.getId());
            if (job != null)
            {
                args[1] = Long.toString(p_request.getId());
            }

            throw new JobCreationException(
                    JobCreationException.MSG_FAILED_TO_ADD_REQUEST_TO_JOB,
                    args, e);
        }
        finally
        {
            try
            {
                if (connection != null)
                {
                    connection.close();
                }
            }
            catch(Exception e)
            {
                c_logger.error(e);
            }
        }
        
        

        // A job does not exist yet, create one.
        if (numberOfRowsUpdated == 0)
        {
            try
            {
                job = (JobImpl) newJob(p_request, p_monitor, p_isBatch,
                        p_targetPages);
                
                if(p_request.getPriority() != null && 
                        !"".equals(p_request.getPriority()) && 
                        !"null".equals(p_request.getPriority()) ) {
                    job.setPriority(Integer.parseInt(p_request.getPriority()));
                }
            }
            catch (Exception e)
            {
                String args[] = new String[1];
                args[0] = Long.toString(p_request.getId());
                throw new JobCreationException(
                        JobCreationException.MSG_FAILED_TO_CREATE_NEW_JOB,
                        args, e);
            }
        }
        
        job.setPageCount(job.getSourcePages().size());

        return job;
    }

    /**
     * Create and persist a new job whose name and state depend on the contents
     * of the given request. Return the job.
     */
    private Job newJob(Request p_request, BatchMonitor p_monitor,
            boolean p_isBatch, HashMap p_targetPages)
            throws JobCreationException
    {
        return m_jobAdditionEngine.createNewJob(p_request,
                (p_isBatch ? Job.BATCHRESERVED : Job.PENDING),
                (p_isBatch ? p_monitor.generateJobName(p_request) : null),
                p_targetPages);
    }

    private synchronized void updateJobState(Job p_job, boolean p_isBatch,
            boolean p_isBatchComplete, SourcePage p_sp)
            throws JobCreationException
    {
        try
        {
            if (p_isBatch)
            {

                // if batch and complete
                if (p_isBatchComplete)
                {
                    AddingSourcePageManager.removeAllAddingFiles(p_job.getId());
                    UpdateSourcePageManager.removeAllUpdatedFiles(p_job.getId());
                    
                    List<Workflow> dispatchedWK = new ArrayList<Workflow>();
                    Job job = null;
                    
                    Collection<Workflow> wks = p_job.getWorkflows();
                    for (Workflow w : wks)
                    {
                        if (Workflow.DISPATCHED.equals(w.getState()))
                        {
                            dispatchedWK.add(w);
                        }
                    }

                    updateWorkflowAndSourcePageStates(p_job);

                    boolean containsFailedImports = false;
                    boolean shouldNotify = false;
                    if (containsImportFailRequests(p_job))
                    {
                        containsFailedImports = true;
                        job = loadJobIntoCacheFromDB(p_job);

                        if (isAlreadyInImportFailState(job.getState()))
                        {
                            // doNothing block
                        }
                        else
                        {
                            shouldNotify = handleImportFailure(job);
                            job = loadJobIntoCacheFromDB(job); // refresh with
                            // state change
                        }
                    }
                    else
                    {
                        job = loadJobIntoCacheFromDB(p_job);

                        if (isAlreadyInDispatchedState(job.getState()))
                        {
                            // doNothing block
                        }
                        else
                        {
                            // Sends email for creating job successfully
                            sendEmailForJobImportSucc(job);
                            
                            getJobDispatchEngine().dispatchBatchJob(job);
                            
                            String orgState = job.getOrgState();
                            if (orgState != null)
                            {
                                job.setOrgState(null);
                                job.setState(orgState);
                                HibernateUtil.update(job);
                                
                                for (Workflow w : dispatchedWK)
                                {
                                    if (Workflow.READY_TO_BE_DISPATCHED.equals(w.getState()))
                                    {
                                        w.setState(Workflow.DISPATCHED);
                                        HibernateUtil.update(w);
                                    }
                                }
                            }
                        }
                    }

//                    job.setPageCount(job.getSourcePages().size());
                    String details = getJobContentInfo(job);
                    // if there are errors in the job send an email to
                    // the appropriate people
                    if ((containsFailedImports || job.containsFailedWorkflow())
                            && shouldNotify)
                    {
                        // Import Failed
                        sendEmailFromAdmin(job, false);
                    }
                    notifyCXEofJobState(job, p_sp, details);
                }
            }
            else if (containsImportFailRequests(p_job))
            {
                Job job = loadJobIntoCacheFromDB(p_job);

                if (isAlreadyInImportFailState(job.getState()))
                {
                    // doNothing block
                }
                else
                {
                    handleImportFailure(job);
                }
            }
            else
            {
                Job job = loadJobIntoCacheFromDB(p_job);

                if (isAlreadyInDispatchedState(job.getState()))
                {
                    // doNothing block
                }
                else
                {
                    getJobDispatchEngine().wordCountIncreased(job);
                }
            }
        }
        catch (Exception e)
        {
            String[] args = new String[2];
            args[0] = "" + p_job.getId();
            args[1] = Job.IMPORTFAILED;
            throw new JobCreationException(
                    JobCreationException.MSG_FAILED_TO_UPDATE_JOB_STATE, args,
                    e);
        }
    }

    private boolean isAlreadyInDispatchedState(String p_currentState)
    {
        boolean isDispatched = false;

        if (p_currentState.equals(Job.DISPATCHED))
        {
            isDispatched = true;
        }

        return isDispatched;
    }

    private boolean isAlreadyInImportFailState(String p_currentState)
    {
        boolean isImportFail = false;

        if (p_currentState.equals(Job.IMPORTFAILED))
        {
            isImportFail = true;
        }

        return isImportFail;
    }

    private boolean isBatchComplete(Job p_job, BatchMonitor p_monitor)
            throws JobCreationException
    {
        try
        {
            JobCreationQuery query = new JobCreationQuery();

            List requestList = query.getRequestListByJobId(p_job.getId());

            return p_monitor.isBatchComplete(requestList);
        }
        catch (Exception ex)
        {
            String[] args = new String[1];
            args[0] = String.valueOf(p_job.getId());
            throw new JobCreationException(
                    JobCreationException.MSG_FAILED_TO_GET_REQUEST_LIST, args,
                    ex);
        }
    }

    /**
     * Return true if the job itself or any of its requests have failed import.
     */
    private boolean containsImportFailRequests(Job p_job)
            throws JobCreationException
    {
        boolean importFailed = false;
        List requestList = null;

        try
        {
            JobCreationQuery jcq = new JobCreationQuery();
            requestList = jcq.getRequestListByJobId(p_job.getId());

            Iterator it = requestList.iterator();
            while (!importFailed && it.hasNext())
            {
                Request r = (Request) it.next();
                importFailed = (r.getType() < 0); // if the type is less than
                // '0' then
                // it is a negative error type.
            }
        }
        catch (Exception e)
        {
            String[] args = new String[1];
            args[0] = "" + p_job.getId();
            throw new JobCreationException(
                    JobCreationException.MSG_FAILED_TO_GET_REQUEST_LIST, args,
                    e);
        }

        return importFailed;
    }

    /**
     * Load the job stored in the DB into the TOPLink cache. JDBC was used to
     * create the job in the DB - need to load it into the cache once it has
     * been created.
     */
    private Job loadJobIntoCacheFromDB(Job p_job) throws JobCreationException
    {
        Job job = null;

        try
        {
            job = getJobHandler().getJobById(p_job.getId());
        }
        catch (Exception e)
        {
            String[] args = new String[1];
            args[0] = "" + p_job.getId();
            throw new JobCreationException(
                    JobCreationException.MSG_FAILED_TO_FIND_JOB_IN_DB, args, e);
        }

        return job;
    }

    /* Return the job dispatch engine. */
    private JobDispatchEngine getJobDispatchEngine()
            throws JobCreationException
    {

        try
        {
            return ServerProxy.getJobDispatchEngine();
        }
        catch (Exception e)
        {
            c_logger.error("Unable to retrieve JobDispatch Engine", e);

            throw new JobCreationException(
                    JobCreationException.MSG_FAILED_TO_FIND_JOB_DISPATCHER,
                    null, e);
        }
    }

    /**
     * Update the workflows and source page states according to the state of the
     * target pages in the job.
     */
    private void updateWorkflowAndSourcePageStates(Job p_job)
            throws JobCreationException
    {
        // only perform this if reimport is set up to handle it
        int reimportOption = ActivePageReimporter.getReimportOption();
        if (reimportOption == ActivePageReimporter.REIMPORT_NEW_TARGETS)
        {
            Connection connection = null;

            try
            {
                connection = HibernateUtil.getSession().connection();
                connection.setAutoCommit(false);

                UpdateWorkflowAndPageStatesCommand updateState = new UpdateWorkflowAndPageStatesCommand(
                        p_job);
                updateState.persistObjects(connection);
                connection.commit();

                // check if there are any requests that should be marked as
                // failed
                List failedRequestIds = updateState.getFailedRequestsById();
                RequestHandler rh = ServerProxy.getRequestHandler();
                for (Iterator fri = failedRequestIds.iterator(); fri.hasNext();)
                {
                    long requestId = ((Long) fri.next()).intValue();
                    JobCreationException jce = new JobCreationException(
                            JobCreationException.MSG_FAILED_TO_IMPORT_ALL_TARGETS_SUCCESSFULLY,
                            null, null);
                    rh.setExceptionInRequest(requestId, jce);
                }
            }
            catch (Exception e)
            {
                c_logger
                        .debug(
                                "exception while updating the workflow and source page states depending on the target.",
                                e);
                try
                {
                    connection.rollback();
                }
                catch (Exception sqle)
                {
                }
                String[] args = { Long.toString(p_job.getId()) };
                throw new JobCreationException(
                        JobCreationException.MSG_FAILED_TO_UPDATE_WORKFLOW_AND_PAGE_STATE,
                        args, e);
            }
            finally
            {
                try
                {
                    if (connection != null)
                    {
                        connection.close();
                    }
                }
                catch (Exception e)
                {
                    c_logger.error(e);
                }
            }
        }
    }

    /**
     * Wraps the code for getting the page manager and handling any exceptions.
     */
    private PageEventObserver getPageEventObserver()
            throws JobCreationException
    {
        PageEventObserver peo = null;

        try
        {
            peo = ServerProxy.getPageEventObserver();
        }
        catch (GeneralException ge)
        {
            c_logger.error("Couldn't find the PageEventObserver", ge);

            throw new JobCreationException(
                    JobCreationException.MSG_FAILED_TO_FIND_PAGE_EVENT_OBSERVER,
                    null, ge);
        }

        return peo;
    }

    private JobHandler getJobHandler() throws Exception
    {
        return ServerProxy.getJobHandler();
    }

    /**
     * Adds the source language document to the CorpusTM. Does not impact the
     * import process if there are CorpusTM errors. Those errors get logged.
     * 
     * @param p_sourcePage
     * @param p_request
     * @param p_job
     * @param p_isBatchComplete
     *            the source page that has successfully imported.
     */
    private void addSourceDocToCorpus(SourcePage p_sourcePage,
            Request p_request, Job p_job, boolean p_isBatchComplete,
            BatchInfo p_batchInfo)
    {
        if (!Modules.isCorpusInstalled())
        {
            // need to delete the original source file that would have
            // been used as the native format corpus doc
            String fileName = p_request.getOriginalSourceFileContent();

            if (fileName != null)
            {
                File originalSourceFile = new File(fileName);

                if (originalSourceFile.exists())
                {
                    originalSourceFile.delete();
                }
            }

            return;
        }

        try
        {
            boolean deleteOriginal = true;
            String gxml = p_request.getGxml();
            File originalSourceFile = new File(p_request
                    .getOriginalSourceFileContent());

            CorpusManagerWLRemote corpusManager = ServerProxy
                    .getCorpusManager();
            CorpusDoc sourceCorpusDoc = corpusManager
                    .addNewSourceLanguageCorpusDoc(p_sourcePage, gxml,
                            originalSourceFile, deleteOriginal);
            CorpusDocGroup cdg = sourceCorpusDoc.getCorpusDocGroup();

            StringBuffer msg = new StringBuffer("Added source page ");
            msg.append(p_sourcePage.getExternalPageId());
            msg.append(" (");
            msg.append(cdg.getId()).append("/");
            msg.append(p_sourcePage.getGlobalSightLocale().toString());
            msg.append(") to corpusTM");
            c_logger.info(msg.toString());

            updateSourcePageCuvId(p_sourcePage, sourceCorpusDoc, p_job);

            c_logger.debug("p_sourcePage cuv_id = " + p_sourcePage.getCuvId());
        }
        catch (Throwable ex)
        {
            // Tue Oct 04 21:11:58 2005 CvdL: when I did a delayed
            // reimport of an office file into an existing job and
            // restarted GlobalSight to fix some errors, when the
            // reimport finally ran the call above "new File(
            // p_request.getOriginalSourceFileContent())" encountered
            // a null pointer exception.
            c_logger.error("Could not add source page "
                    + p_sourcePage.getExternalPageId() + " to corpus.", ex);
        }
    }

    /**
     * Updates the cuv id in the SourcePage
     * 
     * @param p_sourcePage
     * @param p_corpusDoc
     * @exception Exception
     */
    private void updateSourcePageCuvId(SourcePage p_sourcePage,
            CorpusDoc p_sourceCorpusDoc, Job p_job) throws Exception
    {
        // first get the SourcePage object refreshed in the cache
        SourcePage sp = ServerProxy.getPageManager().getSourcePage(
                p_sourcePage.getId());
        Session session = null;
        Transaction transaction = null;
        try
        {
            session = HibernateUtil.getSession();
            transaction = session.beginTransaction();
            SourcePage clone = (SourcePage) session.get(SourcePage.class, sp
                    .getIdAsLong());
            clone.setCuvId(p_sourceCorpusDoc.getIdAsLong());
            session.update(clone);
            transaction.commit();
        }
        catch (Exception e)
        {
            if (transaction != null)
            {
                transaction.rollback();
            }
            throw new Exception(e);
        }
        finally
        {
            if (session != null)
            {
                // session.close();
            }
        }

        // replace the clone in the job's list of pages
        p_job.getSourcePages();

    }

    // Sends the notification for creating job successful
    private void sendEmailForJobImportSucc(Job p_job) throws Exception
    {
        if (!m_systemNotificationEnabled)
        {
            return;
        }

        Project project = ServerProxy.getProjectHandler().getProjectById(p_job.getProjectId());
        GlobalSightLocale sourceLocale = p_job.getSourceLocale();
        Date createDate = p_job.getCreateDate();
        User jobUploader = p_job.getCreateUser();
        String fpNames = "";
        List<FileProfile> fps = p_job.getAllFileProfiles();
        Set<String> fpSet = new HashSet<String>();
        for (FileProfile fp : fps)
        {
            String tempFPName = fp.getName();
            boolean isXLZ = ServerProxy.getFileProfilePersistenceManager()
                                       .isXlzReferenceXlfFileProfile(tempFPName);
            if (isXLZ)
            {
                tempFPName = tempFPName.substring(0, tempFPName.length() - 4);
            }
            fpSet.add(tempFPName);
        }
        fpNames = fpSet.toString();
        fpNames = fpNames.substring(1, fpNames.length() - 1);

        String messageArgs[] = new String[8];
        messageArgs[0] = Long.toString(p_job.getId());
        messageArgs[1] = p_job.getJobName();
        messageArgs[2] = project.getName();
        messageArgs[3] = sourceLocale.getDisplayName();
        messageArgs[4] = String.valueOf(p_job.getWordCount());
//        messageArgs[5] = DateHelper.getFormattedDateAndTime(createDate,Locale.US);
        messageArgs[6] = jobUploader.getSpecialNameForEmail();
        messageArgs[7] = fpNames;

        String subject = MailerConstants.JOB_IMPORT_SUCC_SUBJECT;
        String message = MailerConstants.JOB_IMPORT_SUCC_MESSAGE;
        List<String> receiverList = new ArrayList<String>();
        receiverList.add(jobUploader.getUserId());
        String managerID = project.getProjectManagerId();
        if (null != managerID && !receiverList.contains(managerID))
        {
            receiverList.add(managerID);
        }

        for (int i = 0; i < receiverList.size(); i++)
        {
            User receiver = (User) ServerProxy.getUserManager().getUser(receiverList.get(i));
            messageArgs[5] = DateHelper.getFormattedDateAndTimeFromUser(createDate,receiver);
            sendEmail(receiver, messageArgs, subject, message);
        }
    }
    
    // Sends mail from the Admin to the PM about a Job that contains Import
    // Failures
    private void sendEmailFromAdmin(Job p_job, boolean p_reimportAsUnextracted)
            throws Exception
    {
        if (!m_systemNotificationEnabled)
        {
            return;
        }

        SystemConfiguration config = SystemConfiguration.getInstance();
        String capLoginUrl = config
                .getStringParameter(SystemConfigParamNames.CAP_LOGIN_URL);

        String messageArgs[] = new String[4];
        // send email to project manager
        messageArgs[0] = Long.toString(p_job.getId());
        messageArgs[1] = p_job.getJobName();
        messageArgs[3] = capLoginUrl;

        Request r = (Request) p_job.getRequestList().iterator().next();
        L10nProfile l10nProfile = r.getL10nProfile();
        GlobalSightLocale[] targetLocales = r.getTargetLocalesToImport();
        boolean shouldNotifyPm = false;
        Locale loc = null;
        String subject = p_reimportAsUnextracted ? MailerConstants.JOB_IMPORT_CORRECTION_SUBJECT
                : MailerConstants.JOB_IMPORT_FAILED_SUBJECT;
        String message = p_reimportAsUnextracted ? "jobImportCorrectionMessage"
                : "jobImportFailedMessage";
        String messageBody = new String();
        List<String> mailList = new ArrayList<String>();
        for (int i = 0; i < targetLocales.length; i++)
        {
            WorkflowTemplateInfo wfti = l10nProfile
                    .getWorkflowTemplateInfo(targetLocales[i]);
            if (!shouldNotifyPm && wfti.notifyProjectManager())
            {
                shouldNotifyPm = true;
            }
            List userIds = wfti.getWorkflowManagerIds();
            mailList.addAll(userIds);
            if (userIds != null)
            {
                for (Iterator uii = userIds.iterator(); uii.hasNext();)
                {
                    User user = ServerProxy.getUserManager().getUser(
                            (String) uii.next());
                    Locale userLocale = LocaleWrapper.getLocale(user
                            .getDefaultUILocale());
                    // if not generated yet or if they aren't the same
                    // regenerate the messageBody
                    if (loc == null || userLocale != loc)
                    {
                        loc = userLocale;
                        messageBody = createEmailMessageBody(p_job, loc,
                                p_reimportAsUnextracted);
                    }
                    messageArgs[2] = messageBody;

                    sendEmail(user, messageArgs, subject, message);
                }
            }
        }
        // if at least one of the wfInfos had the pm notify flag on, notify PM.
        if (shouldNotifyPm)
        {
            String pmUserId = l10nProfile.getProject().getProjectManagerId();
            if (!mailList.contains(pmUserId)) {
                User pm = ServerProxy.getUserManager().getUser(pmUserId);
                Locale pmLocale = LocaleWrapper.getLocale(pm
                        .getDefaultUILocale());

                if (pmLocale != loc) {
                    messageArgs[2] = createEmailMessageBody(p_job, pmLocale,
                            p_reimportAsUnextracted);
                }

                sendEmail(pm, messageArgs, subject, message);
            }
        }
    }

    // send mail to user (Project manager / Workflow Manager)
    private void sendEmail(User p_user, String[] p_messageArgs,
            String p_subject, String p_message) throws Exception
    {
        if (!m_systemNotificationEnabled)
        {
            return;
        }

        ServerProxy.getMailer().sendMailFromAdmin(p_user, p_messageArgs,
                p_subject, p_message);
    }

    // create the localized email message for a job that contains failed imports
    private String createEmailMessageBody(Job p_job, Locale p_locale,
            boolean p_reimportAsUnextracted)
    {
        ResourceBundle bundle = m_sysResBundle.getResourceBundle(
                ResourceBundleConstants.LOCALE_RESOURCE_NAME, p_locale);

        StringBuffer msgBody = new StringBuffer();

        // if the job is marked as import failure then
        // atleast one of its source pages has failed
        if (p_job.getState().equals(Job.IMPORTFAILED)
                || p_reimportAsUnextracted)
        {
            msgBody
                    .append(bundle
                            .getString(p_reimportAsUnextracted ? "msg_import_correction_source_pages"
                                    : "msg_import_fail_source_pages"));

            msgBody.append(":\n\n");

            Collection sourcePages = p_job.getSourcePages();
            for (Iterator spi = sourcePages.iterator(); spi.hasNext();)
            {
                SourcePage sp = (SourcePage) spi.next();

                // if there was an error
                if (sp.getRequest().getType() < 0)
                {
                    msgBody.append(bundle.getString("lb_source_page"));
                    msgBody.append(": ");
                    msgBody.append(sp.getExternalPageId());
                    msgBody.append("\n   ");
                    msgBody.append(bundle.getString("lb_failed_due_to"));
                    msgBody.append(": ");
                    msgBody.append(sp.getRequest().getException()
                            .getLocalizedMessage());
                    msgBody.append("\n");
                }
            }
        }

        if (p_job.containsFailedWorkflow())
        {
            msgBody.append("\n");
            msgBody.append(bundle.getString("msg_import_fail_workflows"));
            msgBody.append(":\n\n");

            Collection workflows = p_job.getWorkflows();
            for (Iterator wi = workflows.iterator(); wi.hasNext();)
            {
                Workflow w = (Workflow) wi.next();

                if (w.getState().equals(Workflow.IMPORT_FAILED))
                {
                    msgBody.append(bundle.getString("lb_target_locale"));
                    msgBody.append(": ");
                    msgBody.append(w.getTargetLocale().toString());
                    msgBody.append("\n");

                    Collection targetPages = w.getAllTargetPages();
                    for (Iterator tpi = targetPages.iterator(); tpi.hasNext();)
                    {
                        TargetPage tp = (TargetPage) tpi.next();
                        if (tp.getPageState().equals(PageState.IMPORT_FAIL))
                        {
                            msgBody.append("   ");
                            msgBody.append(bundle.getString("lb_target_page"));
                            msgBody.append(": ");
                            msgBody.append(tp.getSourcePage()
                                    .getExternalPageId());
                            msgBody.append("\n      ");
                            msgBody
                                    .append(bundle
                                            .getString("lb_failed_due_to"));
                            msgBody.append(": ");
                            msgBody.append(tp.getImportError()
                                    .getLocalizedMessage());
                            msgBody.append("\n");
                        }
                    }
                }
            }
        }
        return msgBody.toString();
    }

    /**
     * This method handles a special case with an import failure. If the
     * imported content's format type is equal to the ones listed in the
     * property file, an unextracted reimport is enforced and the failed job
     * would be discarded.
     */
    private boolean handleImportFailure(Job p_job) throws Exception
    {
        boolean notifyFailure = p_job.getRequestList().size() == 0
                || m_specialFormatTypes.size() == 0;

        // if no format types are set in property file, just udpate job state
        if (notifyFailure)
        {
            updateJobState(p_job, Job.IMPORTFAILED);
        }
        else
        {
            Request r = (Request) (p_job.getRequestList().iterator().next());

            FileProfile fp = ServerProxy.getFileProfilePersistenceManager()
                    .readFileProfile(r.getDataSourceId());

            KnownFormatType format = ServerProxy
                    .getFileProfilePersistenceManager().queryKnownFormatType(
                            fp.getKnownFormatTypeId());
            String name = format.getName().toLowerCase();

            EventFlowXmlParser parser = new EventFlowXmlParser();
            parser.parse(r.getEventFlowXml());
            String preMergeEvent = parser.getPreMergeEvent();

            notifyFailure = preMergeEvent == null
                    || CxeMessageType.getCxeMessageType(
                            CxeMessageType.UNEXTRACTED_LOCALIZED_EVENT)
                            .getName().equals(preMergeEvent)
                    || !m_specialFormatTypes.contains(name);
            if (notifyFailure)
            {
                updateJobState(p_job, Job.IMPORTFAILED);
            }
            else
            {
                notifyFailure = importByDataSourceType(parser, p_job);
            }
        }

        return notifyFailure;
    }

    private boolean importByDataSourceType(EventFlowXmlParser p_parser,
            Job p_job) throws Exception
    {
        boolean isInvalidDataSource = false;
        String jobName = p_job.getJobName();
        String batchId = jobName + System.currentTimeMillis();
        String dataSourceType = p_parser.getSourceDataSourceType();

        if (DataSourceType.TEAM_SITE.equals(dataSourceType))
        {
            CxeProxy.importFromTeamSite(p_parser.getDataValue("category",
                    "SourceFileName"), p_parser.getConvertedFileName(), Integer
                    .parseInt(p_parser.getSourceFileSize()), p_parser
                    .getSourceLocale(), p_parser.getSourceEncoding(), p_parser
                    .getBatchId(), 1, 1, 1, 1,
                    p_parser.getSourceDataSourceId(), p_parser
                            .getL10nProfileId(), jobName, p_parser
                            .getDataValue("category", "UserName"), p_parser
                            .getServerName(), p_parser.getStoreName(),
                    Boolean.TRUE, CxeProxy.IMPORT_TYPE_L10N);

        }
        else if (DataSourceType.VIGNETTE.equals(dataSourceType))
        {
            CxeProxy.importFromVignette(jobName, p_parser.getBatchId(), 1, 1,
                    1, 1, p_parser.getDataValue("category", "ObjectId"),
                    p_parser.getDataValue("category", "Path"), p_parser
                            .getSourceDataSourceId(), p_parser.getDataValue(
                            "category", "SourceProjectMid")
                            + "|"
                            + p_parser.getDataValue("category",
                                    "TargetProjectMid"), p_parser.getDataValue(
                            "category", "ReturnStatus"), p_parser.getDataValue(
                            "category", "VersionFlag"), Boolean.TRUE,
                    CxeProxy.IMPORT_TYPE_L10N);
        }
        else if (dataSourceType != null
                && dataSourceType.startsWith(DataSourceType.FILE_SYSTEM))
        {
            boolean isAutoImport = dataSourceType.indexOf("AutoImport") > 0;
            String fileName = p_parser.getDataValue("source", "Filename");
            String initiatorId = p_parser.getDataValue("source",
                    "importInitiator");
            CxeProxy.importFromFileSystem(fileName, jobName, null, batchId, p_parser
                    .getSourceDataSourceId(), new Integer(1), new Integer(1),
                    new Integer(1), new Integer(1), new Boolean(isAutoImport),
                    Boolean.TRUE, CxeProxy.IMPORT_TYPE_L10N, initiatorId,
                    new Integer(0));
        }
        else
        {
            isInvalidDataSource = true;
        }

        if (!isInvalidDataSource)
        {
            sendImportCorrectionEmail(p_job);
        }
        return isInvalidDataSource;
    }

    /*
     * Notify the PM and WFMs about the re-import of the failed job.
     */
    private void sendImportCorrectionEmail(Job p_job) throws Exception
    {
        // notify the PM about the new un-extracted import
        sendEmailFromAdmin(p_job, true);
        try
        {
            // now discard the old job (that had failed)
            getJobDispatchEngine().cancelJob(p_job);
        }
        catch (Exception e)
        {
            c_logger
                    .error("Failed to discard the job with import failed state. "
                            + e);
        }
    }

    /*
     * Update the job state to be the given state.
     */
    private void updateJobState(Job p_job, String p_state) throws Exception
    {
        Session session = null;
        Transaction transaction = null;
        try
        {
            session = HibernateUtil.getSession();
            transaction = session.beginTransaction();
            Job jobClone = (Job) session.get(p_job.getClass(), new Long(p_job
                    .getId()));
            jobClone.setState(p_state);
            session.update(jobClone);
            transaction.commit();
        }
        catch (Exception e)
        {
            transaction.rollback();
            throw e;
        }
        finally
        {
            if (session != null)
            {
                // session.close();
            }
        }
    }

    /*
     * Load the valid format types that require special handling during an
     * extracted import failure.
     */
    private void loadValidFormatTypes()
    {
        try
        {
            List validFormatTypeNames = new ArrayList();
            validFormatTypeNames.add(KnownFormatType.PDF.toLowerCase());
            validFormatTypeNames.add(KnownFormatType.WORD.toLowerCase());
            validFormatTypeNames.add(KnownFormatType.EXCEL.toLowerCase());
            validFormatTypeNames.add(KnownFormatType.POWERPOINT.toLowerCase());

            SystemConfiguration sc = SystemConfiguration.getInstance();
            String value = sc
                    .getStringParameter(SystemConfiguration.HANDLE_IMPORT_FAILURE);

            if (value == null || value.length() == 0)
            {
                c_logger
                        .debug("No format types to handle during import failure.");
                return;
            }

            String[] formatTypes = value.split(",");

            for (int i = 0; i < formatTypes.length; i++)
            {
                String s = formatTypes[i].trim().toLowerCase();
                if (validFormatTypeNames.contains(s))
                {
                    m_specialFormatTypes.add(s);
                }
                else
                {
                    c_logger.info("Invalid format type: " + s);
                }
            }
        }
        catch (Exception e)
        {
            c_logger.error("Failed to get the format types that should "
                    + "be handled different upon an import failure.", e);
        }
    }

    // package methods

    /**
     * Notify TeamSite regarding the Job State
     * 
     * @exception RemoteException
     */
    private void notifyCXEofJobState(Job p_job, SourcePage p_sp,
            String p_details) throws RemoteException
    {
        String state = p_job.getState();
        try
        {
            String efxml = p_sp.getRequest().getEventFlowXml();

            Collection<Request> requests = p_job.getRequestList();
            if (requests.size() == 0)
            {
                return;
            }
            
            Request r = requests.iterator().next();

            EventFlowXmlParser parser = new EventFlowXmlParser();
            parser.parse(r.getEventFlowXml());
            String dataSourceType = parser.getSourceDataSourceType();

            if (DataSourceType.TEAM_SITE.equals(dataSourceType))
            {
                CxeProxy.returnImportStatusToTeamSite(efxml, state, p_details,
                        p_job.getCompanyId());
            }
            else if (DataSourceType.VIGNETTE.equals(dataSourceType))
            {
                // CxeProxy.returnImportStatusToVignette(efxml, state,
                // p_details);
            }
            else if (dataSourceType != null
                    && dataSourceType.startsWith(DataSourceType.FILE_SYSTEM))
            {
                // CxeProxy.returnImportStatusTofileSystem(efxml, state,
                // p_details);
            }
        }
        catch (Exception e)
        {
            c_logger.error("Failed to notify TeamSite", e);
        }
    }

    private String getJobContentInfo(Job p_job)
    {
        Locale uiLocale = new Locale("en", "US");
        StringBuffer sB = new StringBuffer();
        List sourcePages = new ArrayList(p_job.getSourcePages());
        for (int i = 0; i < sourcePages.size(); i++)
        {
            SourcePage curPage = (SourcePage) sourcePages.get(i);
            // Page Name
            sB.append("Page Name =" + curPage.getExternalPageId() + "\n");
            // Word Count
            sB.append("Word Count =" + curPage.getWordCount() + "\n");
            // Status
            String state = curPage.getPageState();
            sB.append("Status =" + state + "\n");
            // Message
            sB.append("Message="
                    + ((state.equals(PageState.IMPORT_FAIL) || (curPage
                            .getRequest().getType() < 0)) ? curPage
                            .getRequest().getException().getTopLevelMessage(
                                    uiLocale) : "") + "\n");
        }
        return sB.toString();
    }

    /**
     * Write the custom attributes back to Documentum Server, including jobId,
     * Workflow Ids.
     * 
     * @param job -
     *            The new job created just now.
     * @param objId -
     *            Documentum Object Id.
     * @throws Exception
     */
    private void handleDocumentumJob(Job job, String objId, String userId)
            throws Exception
    {

        Collection wfIdsList = new ArrayList();
        Connection connection = PersistenceService.getInstance()
                .getConnection();
        PreparedStatement psQueryWfIds = null;
        StringBuffer debugInfo = new StringBuffer();

        // Find all the workflows for this Documentum Job.
        String jobId = String.valueOf(job.getJobId());
        debugInfo.append("JobId=").append(jobId).append(", ");
        debugInfo.append("WorkflowIds=");

        psQueryWfIds = connection
                .prepareStatement(DocumentumOperator.DCTM_SELWFI_SQL);
        psQueryWfIds.setLong(1, job.getId());
        ResultSet rs = psQueryWfIds.executeQuery();
        while (rs.next())
        {
            long wfId = rs.getLong(1);
            wfIdsList.add(new Long(wfId));
            debugInfo.append(wfId).append(" ");
        }
        psQueryWfIds.close();

        PersistenceService.getInstance().returnConnection(connection);
        c_logger
                .debug("Writing the custom attributes(jobId, workflow ids) back to Documentum server");
        // Write custom attributes(jobId, workflow ids) back to Documentum
        // Server.
        DocumentumOperator.getInstance().writeCustomAttrsBack(userId, objId,
                jobId, wfIdsList);
        c_logger.debug(debugInfo.toString());
    }
    
    /**
     * Save Edition job info.
     * @param p_jobId
     * @param p_editonJobInfoMap
     * @throws Exception
     */
    private void saveEditionJobInfo(long p_jobId, HashMap p_editonJobInfoMap) 
        throws Exception
    {
        String jobId = String.valueOf(p_jobId);
        String originalTaskId = (String) p_editonJobInfoMap.get("taskId");
        String originalEndpoint = (String) p_editonJobInfoMap.get("wsdlUrl");
        String originalUserName = (String) p_editonJobInfoMap.get("userName");
        String originalPassword = (String) p_editonJobInfoMap.get("password");
        
        JobEditionInfo jei = new JobEditionInfo(jobId, originalTaskId, 
                originalEndpoint, originalUserName, originalPassword);
        
        if(!isEdtionJobInfoExist(jobId, originalEndpoint)) {
            try {
                HibernateUtil.save(jei);
            } catch (Exception e) {
                String message = "Failed to save edition job info.";
                c_logger.error(message);
    //          throw new Exception(message, e);
            }
        }
    }
    
    /*
     * If a job has 2 or more pages, when create job on GS Edition server, 
     * ambassador will create JobEditionInfo 2 or more times, so check if
     * the JobEditionInfo has been created, that will insure the JobEditionInfo
     * will only be created once.
     */
    private boolean isEdtionJobInfoExist(String jobId, String originalEndpoint) {
        try {
            String hql = "from JobEditionInfo a where a.jobId = :id and a.url = :url";
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("id", jobId);
            map.put("url", originalEndpoint);
            Collection servers = HibernateUtil.search(hql, map);
            Iterator i = servers.iterator();
            
            if(i.hasNext()) {
                return true;
            }
        }
        catch (Exception pe) {
            c_logger.error("Persistence Exception when retrieving JobEditionInfo", pe);
        }
        
        return false;
    }
    
    /*
     * When create GS Edition job on remote GS EDITION server, the issues of 
     * serverA will take to the remote server and be new issues of the new job of
     * remote GS Edition server, if the job has 2 or more pages, every page will
     * call  saveSegCommentsToTargetServer() to save issues, so should check if the
     * tuv issues have been added.
     */
    private boolean isEditionIssueExist(long newTUVId) {
        try {
            String hql = "from IssueImpl a where a.levelObjectId = :id";
            HashMap<String, Long> map = new HashMap<String, Long>();
            map.put("id", newTUVId);
            Collection issues = HibernateUtil.search(hql, map);
            Iterator i = issues.iterator();
            
            if(i.hasNext()) {
                return true;
            }
        }
        catch (Exception pe) {
            c_logger.error("Persistence Exception when retrieving JobEditionInfo", pe);
        }
        
        return false;
    }
    
    private Comment saveJobComments(long p_jobId, HashMap p_editionJobInfoMap, Request p_request)
        throws Exception
    {
        Comment comment = null;
        try {
            EventFlowXmlParser parser = new EventFlowXmlParser();
            parser.parse(p_request.getEventFlowXml());
            String currentUserName = parser.getSourceImportInitiatorId();
            
            Vector jobComments = (Vector) p_editionJobInfoMap.get("jobComments");
            String originalWsdlUrl = (String) p_editionJobInfoMap.get("wsdlUrl");
            if (jobComments != null && jobComments.size() > 0)
            {
                Iterator jobCommentsIter = jobComments.iterator();
                WorkObject currentJob = JobPersistenceAccessor.getJob(p_jobId, true);
                while (jobCommentsIter.hasNext())
                {
                    //jobComment style: <id>+_+<create_date>+_+<creator_user_id>
                    //+_+<comment_text>+_+<comment_object_id>+_+<comment_object_type>+_+<original_id>
                    String jobComment = (String) jobCommentsIter.next();
                    String[] jcs = jobComment.split("\\+\\_\\+");
                    String originalId = null;
                    String commentStr = null;
                    for (int i=0; i<jcs.length; i++)
                    {
                        if (i==0){
                            originalId = jcs[i];
                        }
                        if (i==3){
                            commentStr = jcs[i];
                        }
                    }

                    comment = ServerProxy.getCommentManager().saveComment(
                            currentJob, p_jobId, currentUserName+ " " + currentUserName, 
                            commentStr, originalId, originalWsdlUrl);
                }
            }
        } catch (Exception ex) {
            String msg = "Failed to save comment for job : " + p_jobId;
            c_logger.error(msg);
//          throw new Exception(msg, ex);
        }
        
        return comment;
    }
    
    private void saveSegCommentsToTargetServer(HashMap p_segComments, SourcePage p_sourcePage)
    {
        CommentManagerWLRemote commentManager = ServerProxy.getCommentManager();
        
        Iterator iter = null;
        if (p_segComments != null && p_segComments.size() > 0) 
        {
            iter = p_segComments.entrySet().iterator();
        }

        while (iter != null && iter.hasNext())
        {
            Map.Entry entry = (Map.Entry) iter.next();
            long _tuId = ((Long) entry.getKey()).longValue();//key
            HashMap issueCommentMap = (HashMap) entry.getValue();//value
            
            long _issueId = ((Long) issueCommentMap.get("IssueID")).longValue();
            long _issueObjectId = ((Long) issueCommentMap.get("LevelObjectId")).longValue(); //original segment/tuv id
//          String _issueObjectType = (String) issueCommentMap.get("LevelObjectType");
            //java.util.Date _createDate = (java.util.Date) issueCommentMap.get("CreateDate");
            String _creatorUserId = (String) issueCommentMap.get("CreatorId");
            String _title = (String) issueCommentMap.get("Title");
            String _priority = (String) issueCommentMap.get("Priority");
            String _status = (String) issueCommentMap.get("Status");
//          String _logicalKey = (String) issueCommentMap.get("LogicalKey");
            String _category = (String) issueCommentMap.get("Category");
            
            //get newTuvId by original TuId and TuvId.
            long newTuvId = -1;
            IssueEditionRelation ier = null;
            try {
                String hql = "from IssueEditionRelation a where a.originalTuId = :originalTuId and a.originalTuvId = :originalTuvId";
                HashMap<String, Long> map = new HashMap<String, Long>();
                map.put("originalTuId", new Long(_tuId));
                map.put("originalTuvId", new Long(_issueObjectId));
                Collection coll = HibernateUtil.search(hql, map);
                Iterator itr = coll.iterator();

                //Iterator itr = HibernateUtil.search(hql, new Long(_tuId), new Long(_issueObjectId)).iterator();
                if (itr != null && itr.hasNext())
                {
                    ier = (IssueEditionRelation) itr.next();
                    newTuvId = ier.getTuv().getId();
                }
            } catch (Exception ex) {
                c_logger.error("Failed to get newTuvId by originalTuId : "
                        + _tuId + " and originalTuvId : " + _issueObjectId + " from IssueEditionRelation");
            }

            //When create GS Edition job on remote GS EDITION server, the issues of 
            // serverA will take to the remote server and be new issues of the new job of
            //remote GS Edition server, if the job has 2 or more pages, every page will
            //call  saveSegCommentsToTargetServer() to save issues, so should check if the new
            //tuv issues have been added.
            if(!isEditionIssueExist(newTuvId)) {
                //1.Save segment comments
                //2.Fill out values of columns:ORIGINAL_TUV_ID and ORIGINAL_ISSUE_HISTORY_ID in "issue_edition_relation" table.
                if (newTuvId > 0) 
                {
                    List issueHistoryIdPairList = new ArrayList();
                    Vector _issueHistoriesVector = (Vector) issueCommentMap.get("HistoryVec");
                    if (_issueHistoriesVector != null && _issueHistoriesVector.size() > 0 )
                    {
                        for (int i=0; i<_issueHistoriesVector.size(); i++)
                        {
                            HashMap issueHistoryMap = (HashMap) _issueHistoriesVector.get(i);
                            long _historyId = ((Long)issueHistoryMap.get("HistoryID")).longValue();
                            String _reportedBy = (String) issueHistoryMap.get("ReportedBy");
                            String _comment = (String) issueHistoryMap.get("Comment");
                            GlobalSightLocale[] aaa = p_sourcePage.getRequest().getTargetLocalesToImport();
                            //add an new issue
                            long newIssueId = -1;
                            if (i==0)
                            {
                                try {
                                    /*
                                    Iterator targetPageItr = p_sourcePage.getTargetPages().iterator();
                                    long tpLocaleId = -1;
                                    if (targetPageItr.hasNext()) {
                                        TargetPage tp = (TargetPage) targetPageItr.next();
                                        tpLocaleId = tp.getLocaleId();
                                    }
                                    */
                                    long targetPageId = ServerProxy.getPageManager()
                                            .getTargetPage(p_sourcePage.getId(), aaa[0].getId()).getId();
                                    long newTuId = ServerProxy.getTuvManager()
                                            .getTuvForSegmentEditor(newTuvId).getTu().getId();
    
                                    String logicalKey = CommentHelper.makeLogicalKey(targetPageId, newTuId, newTuvId, 0);
    
                                    Issue newIssue = commentManager
                                        .addIssue(Issue.TYPE_SEGMENT, newTuvId, _title, 
                                                _priority, _status, _category, 
                                                        _creatorUserId, _comment, logicalKey);
                                    newIssueId = newIssue.getId();
                                    //only one history in this issue now
                                    IssueHistoryImpl his = (IssueHistoryImpl)newIssue.getHistory().get(0);
                                    long newHistoryId = ((IssueHistoryImpl) newIssue.getHistory().get(0)).getDbId();
                                    issueHistoryIdPairList.add(_historyId + "_" + newHistoryId);
                                } catch (Exception e) {
                                    c_logger.error("Failed to add new issue for tuvId : " + newTuvId);
                                }
                            }
                            //reply one by one
                            else
                            {
                                //add issue successfully when i==0
                                if (newIssueId > 0) 
                                {
                                    try
                                    {
                                        Issue newIssue = commentManager.replyToIssue(newIssueId, _title, _priority, 
                                                        _status, _category, _reportedBy, _comment);
                                        Iterator historyItr = newIssue.getHistory().iterator();
                                        while (historyItr != null && historyItr.hasNext())
                                        {
                                            long newHistoryId = ((IssueHistoryImpl) historyItr.next()).getId();
                                            String historyIDPair = _historyId + "_" + newHistoryId;
                                            if ( !issueHistoryIdPairList.contains(historyIDPair) )
                                            {
                                                issueHistoryIdPairList.add(historyIDPair);
                                            }
                                        }
                                    }
                                    catch (Exception e) 
                                    {
                                        c_logger.error("Failed to replyToIssue for issueId : " + newIssueId);
                                    }
                                }
                            }
                        }
                    }
                    //end of issueHistory handling
                    
                    //2.Fill out values of columns:ORIGINAL_TUV_ID and ORIGINAL_ISSUE_HISTORY_ID in "issue_edition_relation" table.
                    ier.setOriginalTuvId(_issueObjectId);
                    Iterator tmpItr = issueHistoryIdPairList.iterator();
                    StringBuffer tmpStrBuffer = new StringBuffer();
                    while (tmpItr.hasNext()) {
                        if (tmpStrBuffer.length() <= 0) {
                            tmpStrBuffer = tmpStrBuffer.append((String) tmpItr.next());
                        } else {
                            tmpStrBuffer.append(",").append((String) tmpItr.next());
                        }
                    }
                    if (tmpStrBuffer.length() > 0) {
                        ier.setOriginalIssueHistoryId(tmpStrBuffer.toString());                 
                    }
    
                    HibernateUtil.saveOrUpdate(ier);
                }
            }

        }//end of segComments handling
    }

}