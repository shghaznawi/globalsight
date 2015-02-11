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
package com.globalsight.cxe.util;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.globalsight.cxe.adapter.AdapterResult;
import com.globalsight.cxe.adapter.database.DatabaseAdapter;
import com.globalsight.cxe.adapter.documentum.DocumentumOperator;
import com.globalsight.cxe.adapter.msoffice.MsOfficeAdapter;
import com.globalsight.cxe.adapter.pdf.PdfAdapter;
import com.globalsight.cxe.adapter.quarkframe.QuarkFrameAdapter;
import com.globalsight.cxe.adapter.serviceware.ServiceWareAdapter;
import com.globalsight.cxe.adapter.teamsite.TeamSiteAdapter;
import com.globalsight.cxe.adapter.vignette.VignetteAdapter;
import com.globalsight.cxe.adaptermdb.EventTopicMap;
import com.globalsight.cxe.entity.fileprofile.FileProfile;
import com.globalsight.cxe.message.CxeMessage;
import com.globalsight.cxe.message.CxeMessageType;
import com.globalsight.cxe.message.MessageData;
import com.globalsight.everest.aligner.AlignerExtractor;
import com.globalsight.everest.company.CompanyThreadLocal;
import com.globalsight.everest.company.CompanyWrapper;
import com.globalsight.everest.page.pageexport.ExportConstants;
import com.globalsight.everest.servlet.EnvoyServletException;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.util.system.SystemConfiguration;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.util.j2ee.AppServerWrapper;
import com.globalsight.util.j2ee.AppServerWrapperFactory;

/**
 * The CxeProxy class allows clients to make import/export
 * requests to CXE without having to directly use JMS.
 */
public class CxeProxy
{
    static private final GlobalSightCategory s_logger =
        (GlobalSightCategory)GlobalSightCategory.getLogger("CXE");

    // PUBLIC CONSTANTS

    // import request types

    /** The name of the import type parameter. */
    static public final String IMPORT_TYPE = "ImportType";

    /** The normal localization import request. */
    static public final String IMPORT_TYPE_L10N = "l10n";

    /** The aligner import request **/
    static public final String IMPORT_TYPE_ALIGNER = "aligner";

    static public final String JMS_FACTORY =
        "com.globalsight.jms.GlobalSightTopicConnectionFactory";
    
    static private final String s_TS_KEY_SENDMSG = "TopicSessionKeySendMessage";

    static private final Integer ONE = new Integer(1);

    static private Context s_context = null;
    static private HashMap s_topicSessions = new HashMap();
    static private HashMap s_targetLocales = new HashMap();
    static private TopicConnectionFactory tcf = null;
    static private TopicSession s_sendMessageTopicSession = null;
    
    static
    {
        try
        {
            s_context = new InitialContext();
            tcf = (TopicConnectionFactory)s_context.lookup(JMS_FACTORY);
            s_sendMessageTopicSession = getTopicSession(s_TS_KEY_SENDMSG);
        }
        catch (Exception ex)
        {
            s_logger.error("Failed to create initial context", ex);
        }
    }
    
    /**
     * store target locales which were choosed when importing file
     * 
     * @param p_key
     *            key (batchId + fileName + pageNumber) to store target locales
     * @param p_locales
     *            like en_US,zh_CN,zh_TW
     * 
     * @see com.globalsight.webservices.GlobalSight#publishEventToCxe()
     * @see this#{@link #importFromFileSystem(String, String, String, String, Integer, Integer, Integer, Integer, Boolean, Boolean, String, String, Integer)}
     */
    public static void setTargetLocales(String p_key, String p_locales)
    {
        s_targetLocales.put(p_key, p_locales);
    }

    /**
     * Initiates a file system import using JMS.
     *
     * @param p_fileName file name relative to the docs directory
     * @param p_jobName  job name
     * @param p_batchId  batch Id
     * @param p_fileProfileId file profile ID
     * @param p_pageCount number of pages in the batch
     * @param p_pageNum number of the current page in the batch
     * (starting from 1)
     * @param p_docPageCount number of pages in the document
     * @param p_docPageNum number of the current page in the document
     * (starting from 1)
     * @param p_isAutoImport FALSE if this is a manual import
     * @param p_importRequestType IMPORT_TYPE_XXX
     * @param p_importInitiatorId
     *                   The user id of the user who initiated the import for this file.
     * @param p_exitValueByScript
     *                   The return value by the script on import.                  
     * @exception JMSException
     * @exception NamingException
     */
    static public void importFromFileSystem(
        String p_fileName, String p_jobName, String jobUuid,
        String p_batchId, String p_fileProfileId,
        Integer p_pageCount, Integer p_pageNum,
        Integer p_docPageCount, Integer p_docPageNum,
        Boolean p_isAutoImport, String p_importRequestType,
        String p_importInitiatorId, Integer p_exitValueByScript)
        throws JMSException, NamingException
    {
        importFromFileSystem(p_fileName, p_jobName, jobUuid, p_batchId, p_fileProfileId,
            p_pageCount, p_pageNum, p_docPageCount, p_docPageNum,
            p_isAutoImport, Boolean.FALSE, p_importRequestType,
                             p_importInitiatorId, p_exitValueByScript);
    }

    static public void importFromFileSystem(
            String p_fileName, String p_jobName, String jobUuid,
            String p_batchId, String p_fileProfileId,
            Integer p_pageCount, Integer p_pageNum,
            Integer p_docPageCount, Integer p_docPageNum,
            Boolean p_isAutoImport, String p_importRequestType,
            String p_importInitiatorId, Integer p_exitValueByScript, String p_priority)
            throws JMSException, NamingException
        {
            importFromFileSystem(p_fileName, p_jobName, jobUuid, p_batchId, p_fileProfileId,
                p_pageCount, p_pageNum, p_docPageCount, p_docPageNum,
                p_isAutoImport, Boolean.FALSE, p_importRequestType,
                                 p_importInitiatorId, p_exitValueByScript, p_priority);
        }

    static public void importFromFileSystem(
            String p_fileName, String p_jobName,
            String p_batchId, String p_fileProfileId,
            Integer p_pageCount, Integer p_pageNum,
            Integer p_docPageCount, Integer p_docPageNum,
            Boolean p_isAutoImport, String p_importRequestType,
            String p_importInitiatorId, Integer p_exitValueByScript, String p_priority,
            String p_originalTaskId, String p_originalEndpoint, String p_originalUserName, String p_originalPassword,
            Vector p_jobComments, HashMap p_segComments )
            throws JMSException, NamingException
    {
        importFromFileSystem(p_fileName, p_jobName, p_batchId, p_fileProfileId,
            p_pageCount, p_pageNum, p_docPageCount, p_docPageNum,
            p_isAutoImport, Boolean.FALSE, p_importRequestType,
            p_importInitiatorId, p_exitValueByScript, p_priority,
            p_originalTaskId, p_originalEndpoint, p_originalUserName, p_originalPassword,
            p_jobComments, p_segComments);
    }

    /**
     * Initiates a file system import using JMS.
     *
     * @param p_fileName file name relative to the docs directory
     * @param p_jobName  job name
     * @param p_batchId  batch Id
     * @param p_fileProfileId file profile ID
     * @param p_pageCount number of pages in the batch
     * @param p_pageNum number of the current page in the batch
     * (starting from 1)
     * @param p_isAutoImport FALSE if this is a manual import
     * @param p_overrideFileProfileAsUnextracted - Determines whether
     * the import process is invoked upon a failure for an extracted
     * file (will override the file profile's format type).
     * @param p_importRequestType IMPORT_TYPE_XXX
     * @param p_importInitiatorId
     *                   The user id of the user who initiated the import for this file.
     * @param p_exitValueByScript
     *                   The return value by the script on import.                  
     * @exception JMSException
     * @exception NamingException
     */
    static public void importFromFileSystem(
        String p_fileName, String p_jobName, String uuid,
        String p_batchId, String p_fileProfileId,
        Integer p_pageCount, Integer p_pageNum,
        Integer p_docPageCount, Integer p_docPageNum,
        Boolean p_isAutoImport,
        Boolean  p_overrideFileProfileAsUnextracted,
        String p_importRequestType,
        String p_importInitiatorId, Integer p_exitValueByScript)
        throws JMSException, NamingException
    {
        CxeMessageType type = CxeMessageType.getCxeMessageType(
            CxeMessageType.FILE_SYSTEM_FILE_SELECTED_EVENT);
        CxeMessage cxeMessage = new CxeMessage(type);
        HashMap params = cxeMessage.getParameters();
        params.put("Filename", p_fileName);
        String jobName = p_jobName;
        if (p_jobName == null || p_jobName.length() == 0)
        {
            long timeInSec = System.currentTimeMillis() / 1000L;
            jobName = "job_" + timeInSec;
        }

        //To make JMS message support CompanyThreadLocal, we have to do so.
        String currentCompanyId = getCompanyIdByFileProfileId(p_fileProfileId);
        params.put(CompanyWrapper.CURRENT_COMPANY_ID, currentCompanyId);
        
        params.put("JobName", jobName);
        params.put("uuid", uuid);
        params.put("BatchId", p_batchId);
        params.put("FileProfileId",p_fileProfileId);
        params.put("PageCount", p_pageCount);
        params.put("PageNum", p_pageNum);
        params.put("DocPageCount", p_docPageCount);
        params.put("DocPageNum", p_docPageNum);
        params.put("IsAutomaticImport", p_isAutoImport);
        params.put("OverrideFileProfileAsUnextracted",
            p_overrideFileProfileAsUnextracted);
        params.put(IMPORT_TYPE, p_importRequestType);
        params.put("ImportInitiator", p_importInitiatorId);
        params.put("ScriptOnImport", p_exitValueByScript);
        
        if (p_isAutoImport.booleanValue() == false)
        {
            simulateAutoImportIfNeeded(params); 
        }
        
        // make target locales selectable when import file
        String key = p_batchId + p_fileName + p_pageNum.intValue();
        if (s_targetLocales.containsKey(key))
        {
            String targetLocales = (String) s_targetLocales.get(key);
            if (!targetLocales.equals(""))
                params.put("TargetLocales", targetLocales);
            s_targetLocales.remove(key);
        }
        
        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_FILE_SYSTEM_SOURCE_ADAPTER;
        sendMessage(cxeMessage,jmsTopic);
    }

    static public void importFromFileSystem(
            String p_fileName, String p_jobName, String uuid,
            String p_batchId, String p_fileProfileId,
            Integer p_pageCount, Integer p_pageNum,
            Integer p_docPageCount, Integer p_docPageNum,
            Boolean p_isAutoImport,
            Boolean  p_overrideFileProfileAsUnextracted,
            String p_importRequestType,
            String p_importInitiatorId, Integer p_exitValueByScript, String p_priority)
            throws JMSException, NamingException
        {
            CxeMessageType type = CxeMessageType.getCxeMessageType(
                CxeMessageType.FILE_SYSTEM_FILE_SELECTED_EVENT);
            CxeMessage cxeMessage = new CxeMessage(type);
            HashMap params = cxeMessage.getParameters();
            params.put("Filename", p_fileName);
            String jobName = p_jobName;
            if (p_jobName == null || p_jobName.length() == 0)
            {
                long timeInSec = System.currentTimeMillis() / 1000L;
                jobName = "job_" + timeInSec;
            }

            //To make JMS message support CompanyThreadLocal, we have to do so.
            String currentCompanyId = getCompanyIdByFileProfileId(p_fileProfileId);
            params.put(CompanyWrapper.CURRENT_COMPANY_ID, currentCompanyId);
            
            params.put("JobName", jobName);
            params.put("uuid", uuid);
            params.put("BatchId", p_batchId);
            params.put("FileProfileId",p_fileProfileId);
            params.put("PageCount", p_pageCount);
            params.put("PageNum", p_pageNum);
            params.put("DocPageCount", p_docPageCount);
            params.put("DocPageNum", p_docPageNum);
            params.put("IsAutomaticImport", p_isAutoImport);
            params.put("OverrideFileProfileAsUnextracted",
                p_overrideFileProfileAsUnextracted);
            params.put(IMPORT_TYPE, p_importRequestType);
            params.put("ImportInitiator", p_importInitiatorId);
            params.put("ScriptOnImport", p_exitValueByScript);
            params.put("priority", p_priority);
            
            if (p_isAutoImport.booleanValue() == false)
            {
                simulateAutoImportIfNeeded(params); 
            }
            
            // make target locales selectable when import file
            String key = p_batchId + p_fileName + p_pageNum.intValue();
            if (s_targetLocales.containsKey(key))
            {
                String targetLocales = (String) s_targetLocales.get(key);
                if (!targetLocales.equals(""))
                    params.put("TargetLocales", targetLocales);
                s_targetLocales.remove(key);
            }
            
            String jmsTopic = EventTopicMap.JMS_PREFIX +
                EventTopicMap.FOR_FILE_SYSTEM_SOURCE_ADAPTER;
            sendMessage(cxeMessage,jmsTopic);
        }

    static public void importFromFileSystem(
            String p_fileName, String p_jobName,
            String p_batchId, String p_fileProfileId,
            Integer p_pageCount, Integer p_pageNum,
            Integer p_docPageCount, Integer p_docPageNum,
            Boolean p_isAutoImport,
            Boolean  p_overrideFileProfileAsUnextracted,
            String p_importRequestType,
            String p_importInitiatorId, Integer p_exitValueByScript, String p_priority,
            String p_originalTaskId, String p_originalEndpoint, String p_originalUserName, String p_originalPassword,
            Vector p_jobComments, HashMap p_segComments )
            throws JMSException, NamingException
    {
            CxeMessageType type = CxeMessageType.getCxeMessageType(
                CxeMessageType.FILE_SYSTEM_FILE_SELECTED_EVENT);
            CxeMessage cxeMessage = new CxeMessage(type);
            HashMap params = cxeMessage.getParameters();
            params.put("Filename", p_fileName);
            String jobName = p_jobName;
            if (p_jobName == null || p_jobName.length() == 0)
            {
                long timeInSec = System.currentTimeMillis() / 1000L;
                jobName = "job_" + timeInSec;
            }

            //To make JMS message support CompanyThreadLocal, we have to do so.
            String currentCompanyId = getCompanyIdByFileProfileId(p_fileProfileId);
            params.put(CompanyWrapper.CURRENT_COMPANY_ID, currentCompanyId);
            
            params.put("JobName", jobName);
            params.put("BatchId", p_batchId);
            params.put("FileProfileId",p_fileProfileId);
            params.put("PageCount", p_pageCount);
            params.put("PageNum", p_pageNum);
            params.put("DocPageCount", p_docPageCount);
            params.put("DocPageNum", p_docPageNum);
            params.put("IsAutomaticImport", p_isAutoImport);
            params.put("OverrideFileProfileAsUnextracted", p_overrideFileProfileAsUnextracted);
            params.put(IMPORT_TYPE, p_importRequestType);
            params.put("ImportInitiator", p_importInitiatorId);
            params.put("ScriptOnImport", p_exitValueByScript);
            params.put("priority", p_priority);
            params.put("taskId", p_originalTaskId);
            params.put("wsdlUrl", p_originalEndpoint);
            params.put("userName", p_originalUserName);
            params.put("password", p_originalPassword);
            params.put("jobComments", p_jobComments);
            params.put("segComments", p_segComments);
            
            if (p_isAutoImport.booleanValue() == false)
            {
                simulateAutoImportIfNeeded(params); 
            }
            
            // make target locales selectable when import file
            String key = p_batchId + p_fileName + p_pageNum.intValue();
            if (s_targetLocales.containsKey(key))
            {
                String targetLocales = (String) s_targetLocales.get(key);
                if (!targetLocales.equals(""))
                    params.put("TargetLocales", targetLocales);
                s_targetLocales.remove(key);
            }
            
            String jmsTopic = EventTopicMap.JMS_PREFIX +
                EventTopicMap.FOR_FILE_SYSTEM_SOURCE_ADAPTER;
            sendMessage(cxeMessage,jmsTopic);
    }

    /**
     * Modifies the parameters in the HashMap to simulate certain
     * aspects of auto import with a batch size of 1, including 1 page
     * per job and the jobname being the page name. If the value of
     * the property import.manualImportSingleBatch in envoy.properties
     * is false, then nothing happens here.
     */
    static private void simulateAutoImportIfNeeded(HashMap p_params)
    {
        try
        {
            SystemConfiguration config = SystemConfiguration.getInstance();

            if (config.getBooleanParameter("import.manualImportSingleBatch") == true)
            {
                s_logger.info("using manual import single batch");

                String filename = (String) p_params.get("Filename");
                int slashIdx = filename.lastIndexOf("/");
                int bslashIdx = filename.lastIndexOf("\\");
                int idx = (slashIdx > bslashIdx) ? slashIdx : bslashIdx;

                idx++;

                String baseName = filename.substring(idx);
                p_params.put("JobName", baseName);
                p_params.put("PageCount", ONE);
                p_params.put("PageNum", ONE);
                p_params.put("DocPageCount", ONE);
                p_params.put("DocPageNum", ONE);
                String newBatchId = baseName.hashCode() +
                    Long.toString(System.currentTimeMillis());
                p_params.put("BatchId", newBatchId);
            }
        }
        catch (Exception ex)
        {
            s_logger.error("Could not handle import.manualImportSingleBatch processing.", ex);
        }
    }

    /**
     * @see exportFile(String, MessageData, String, String, String, String, String, String
     *      String, Integer, Integer, Integer, Integer, long, boolean, String, boolean).
     */
    static public void exportFile(String p_eventFlowXml, MessageData p_gxml,
        String p_cxeRequestType, String p_targetLocale, String p_targetCharset,
        String p_messageId, String p_exportLocation, String p_localeSubDir,
        String p_exportBatchId, Integer p_pageCount, Integer p_pageNum,
        Integer p_docPageCount, Integer p_docPageNum, boolean p_isUnextracted, 
        String p_fileName, boolean p_isFinalExport, String p_companyId)
        throws JMSException, NamingException, IOException
    {
        exportFile(p_eventFlowXml, p_gxml, p_cxeRequestType, p_targetLocale,p_targetCharset,
                p_messageId, p_exportLocation, p_localeSubDir, p_exportBatchId, p_pageCount,
                p_pageNum, p_docPageCount, p_docPageNum, null, 0, false, p_isUnextracted, 
                p_fileName, p_isFinalExport, p_companyId);
    }
    
    /**
     * Initiates an export within CXE using JMS(Override method for documentum workflow Id).
     *
     * @param p_eventFlowXml string of event flow xml
     * @param p_gxmlFileName filename of a file that contains GXML
     * @param p_cxeRequestType CXE Request Type
     * @param p_targetLocale target locale
     * @param p_targetCharset target encoding
     * @param p_messageId export message ID
     * @param p_exportLocation export location
     * @param p_localeSubDir
     * @param p_exportBatchId
     * @param p_pageCount
     * @param p_pageNum
     * @param p_docPageCount
     * @param p_docPageNum
     * @param wfId
     * @param p_isUnextracted
     * @param p_fileName The name of the file to be exported (relative path)
     * @exception JMSException
     * @exception NamingException
     */
    static public void exportFile(String p_eventFlowXml, MessageData p_gxml,
        String p_cxeRequestType, String p_targetLocale, String p_targetCharset,
        String p_messageId, String p_exportLocation, String p_localeSubDir,
        String p_exportBatchId, Integer p_pageCount, Integer p_pageNum,
        Integer p_docPageCount, Integer p_docPageNum, String newObjId,
        long wfId, boolean isJobDone, boolean p_isUnextracted, String p_fileName,
        boolean p_isFinalExport, String p_companyId) 
        throws JMSException, NamingException, IOException 
    {
        if (s_logger.isDebugEnabled())
        {
            s_logger.debug("CXE Export Request: (requestType=" +
                p_cxeRequestType + ", " +
                "pageId=" + p_messageId + ", " +
                "page " + p_docPageNum + " of " + p_docPageCount + ", " +
                "targetLocale=" + p_targetLocale + ", " +
                "unextracted=" + p_isUnextracted + ", " +
                "dir=" + p_localeSubDir + ")");
        }

        exportFile(p_eventFlowXml, p_gxml,
            p_cxeRequestType, p_targetLocale, p_targetCharset,
            p_messageId, p_exportLocation, p_localeSubDir,
            p_exportBatchId, p_pageCount, p_pageNum,
            p_docPageCount, p_docPageNum, newObjId, wfId, isJobDone,
            p_isUnextracted, null, p_fileName, p_isFinalExport, p_companyId);
    }

    /**
     * Export for dynamic preview
     *
     * @param p_eventFlowXml
     * @param p_gxml
     * @param p_cxeRequestType
     * @param p_targetLocale
     * @param p_targetCharset
     * @param p_messageId
     * @param p_exportLocation
     * @param p_localeSubDir
     * @param p_exportBatchId
     * @param p_pageCount
     * @param p_pageNum
     * @param p_sessionId
     * @exception JMSException
     * @exception NamingException
     * @exception IOException
     */
    static public void exportForDynamicPreview(String p_eventFlowXml,
        MessageData p_gxml, String p_cxeRequestType, String p_targetLocale,
        String p_targetCharset, String p_messageId, String p_exportLocation,
        String p_localeSubDir, String p_exportBatchId, Integer p_pageCount,
        Integer p_pageNum, String p_sessionId)
        throws JMSException, NamingException, IOException
    {
        exportFile(p_eventFlowXml, p_gxml,
            p_cxeRequestType, p_targetLocale, p_targetCharset,
            p_messageId, p_exportLocation, p_localeSubDir,
            p_exportBatchId, p_pageCount, p_pageNum,
            /*TODO*/ONE, ONE, null, 0, false,
            false, p_sessionId,
            null, false, CompanyThreadLocal.getInstance().getValue());
    }


    /**
     * Initiates a teamsite import using JMS.
     *
     * @param p_originalFilename
     * @param p_convertedFileName
     * @param p_fileSize
     * @param p_locale
     * @param p_charset
     * @param p_batchId
     * @param p_pageNum
     * @param p_pageCount
     * @param p_fileProfileId
     * @param p_localizationProfile
     * @param p_jobName
     * @param p_userName
     * @param p_importRequestType IMPORT_TYPE_XXX
     * @exception JMSException
     * @exception NamingException
     */
    static public void importFromTeamSite(String p_originalFilename,
        String p_convertedFileName,
        int p_fileSize,
        String p_locale,
        String p_charset,
        String p_batchId,
        int p_pageNum,
        int p_pageCount,
        int p_docPageNum,
        int p_docPageCount,
        String p_fileProfileId,
        String p_localizationProfile,
        String p_jobName,
        String p_userName,
        String p_serverName,
        String p_storeName,
        String p_taskId,
        String p_overwriteSource,
        String p_callbackImmediately,
        String p_importRequestType)
        throws JMSException, NamingException
    {
        importFromTeamSite(p_originalFilename, p_convertedFileName,
            p_fileSize, p_locale, p_charset, p_batchId,
            p_pageNum, p_pageCount, p_docPageNum, p_docPageCount,
            p_fileProfileId, p_localizationProfile, p_jobName, p_userName,
            p_serverName, p_storeName, p_taskId, p_overwriteSource,
            p_callbackImmediately, Boolean.FALSE,
            p_importRequestType);
    }

    /**
     * Initiates a teamsite import using JMS.
     *
     * @param p_originalFilename
     * @param p_convertedFileName
     * @param p_fileSize
     * @param p_locale
     * @param p_charset
     * @param p_batchId
     * @param p_pageNum
     * @param p_pageCount
     * @param p_docPageNum this is not the p_pageNum
     * @param p_docPageCount this is not the p_pageCount
     * @param p_fileProfileId
     * @param p_localizationProfile
     * @param p_jobName
     * @param p_userName
     * @param p_overrideFileProfileAsUnextracted - Determines whether
     * the import process is invoked upon a failure for an extracted
     * file (will override the file profile's format type).
     * @param p_importRequestType IMPORT_TYPE_XXX
     * @exception JMSException
     * @exception NamingException
     */
    static public void importFromTeamSite(String p_originalFilename,
        String p_convertedFileName,
        int p_fileSize,
        String p_locale,
        String p_charset,
        String p_batchId,
        int p_pageNum,
        int p_pageCount,
        int p_docPageNum,
        int p_docPageCount,
        String p_fileProfileId,
        String p_localizationProfile,
        String p_jobName,
        String p_userName,
        String p_serverName,
        String p_storeName,
        String p_taskId,
        String p_overwriteSource,
        String p_callbackImmediately,
        Boolean  p_overrideFileProfileAsUnextracted,
        String p_importRequestType)
        throws JMSException, NamingException
    {
        s_logger.debug("CXE import server is " + p_serverName);
        s_logger.debug("CXE import store is " + p_storeName);

        CxeMessageType type = CxeMessageType.getCxeMessageType(
            CxeMessageType.TEAMSITE_FILE_SELECTED_EVENT);
        CxeMessage cxeMessage = new CxeMessage(type);
        HashMap params = new HashMap();
        String companyId = getCompanyIdByFileProfileId(p_fileProfileId);
        params.put(CompanyWrapper.CURRENT_COMPANY_ID, companyId);
        params.put("TeamSiteServer", p_serverName);
        params.put("TeamSiteStore", p_storeName);
        params.put("TeamSiteTaskId", p_taskId);
        params.put("TeamSiteOverwriteSource", p_overwriteSource);
        params.put("TeamSiteCallbackImmediately", p_callbackImmediately);
        params.put("SourceFileName", p_originalFilename);
        params.put("FileName", p_convertedFileName);
        params.put("FileSize", new Integer(p_fileSize));
        params.put("Locale", p_locale);
        params.put("Charset", p_charset);
        params.put("BatchId", p_batchId);
        params.put("PageNum", new Integer(p_pageNum));
        params.put("PageCount", new Integer(p_pageCount));
        params.put("DocPageNum", new Integer(p_docPageNum));
        params.put("DocPageCount", new Integer(p_docPageCount));
        params.put("DataSourceProfile", p_fileProfileId);
        params.put("LocalizationProfile", p_localizationProfile);
        params.put("JobName", p_jobName);
        params.put("UserName", p_userName);
        params.put("OverrideFileProfileAsUnextracted",
            p_overrideFileProfileAsUnextracted);

        cxeMessage.setParameters(params);

        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_TEAMSITE_SOURCE_ADAPTER;
        ObjectMessage om = getTopicSession().createObjectMessage(cxeMessage);
        TopicPublisher sender = getPublisher(jmsTopic);
        sender.publish(om);
    }

    static public void importFromTeamSite(String p_originalFilename,
        String p_convertedFileName,
        int p_fileSize,
        String p_locale,
        String p_charset,
        String p_batchId,
        int p_pageNum,
        int p_pageCount,
        int p_docPageNum,
        int p_docPageCount,
        String p_fileProfileId,
        String p_localizationProfile,
        String p_jobName,
        String p_userName,
        String p_serverName,
        String p_storeName,
        Boolean  p_overrideFileProfileAsUnextracted,
        String p_importRequestType)
        throws JMSException, NamingException
    {
        importFromTeamSite(p_originalFilename, p_convertedFileName,
            p_fileSize, p_locale, p_charset, p_batchId,
            p_pageNum, p_pageCount, p_docPageNum, p_docPageCount,
            p_fileProfileId, p_localizationProfile, p_jobName, p_userName,
            p_serverName, p_storeName, "", "", "",
            p_overrideFileProfileAsUnextracted,
            p_importRequestType);
    }


    /**
     * Publishes the CxeMessage objects contained in AdapterResult[]
     *
     * @param p_msgs array of messages
     * @param p_topic the desired topic to publish to
     * @exception
     */
    static public void publishEvents(AdapterResult[] p_msgs, String p_topic)
        throws Exception
    {
        CxeMessage cxeMessage = null;
        String jmsTopic = EventTopicMap.JMS_PREFIX + p_topic;
        TopicPublisher sender = getPublisher(jmsTopic);
        for (int i = 0; i < p_msgs.length; i++)
        {
            cxeMessage = p_msgs[i].cxeMessage;
            CompanyWrapper.saveCurrentCompanyIdInMap(cxeMessage.getParameters(), s_logger);
            sender.publish(getTopicSession().createObjectMessage(cxeMessage));
        }
    }

    /**
     * Initiates a vignette import using JMS.
     *
     * @exception Exception
     */
    static public void importFromVignette(String p_jobName,
        String p_batchId,
        int p_pageNum,
        int p_pageCount,
        int p_docPageNum,
        int p_docPageCount,
        String p_srcMid,
        String p_path,
        String p_fileProfileId,
        String p_targetProjectMid,
        String p_returnStatus,
        String p_versionFlag,
        String p_importRequestType)
        throws Exception
    {
        importFromVignette(p_jobName, p_batchId, p_pageNum, p_pageCount,
            p_docPageNum, p_docPageCount,
            p_srcMid, p_path, p_fileProfileId,
            p_targetProjectMid, p_returnStatus,
            p_versionFlag, Boolean.FALSE,
            p_importRequestType);
    }

    /**
     * Initiates a vignette import using JMS.
     *
     * @exception Exception
     */
    static public void importFromVignette(String p_jobName,
        String p_batchId,
        int p_pageNum,
        int p_pageCount,
        int p_docPageNum,
        int p_docPageCount,
        String p_srcMid,
        String p_path,
        String p_fileProfileId,
        String p_targetProjectMid,
        String p_returnStatus,
        String p_versionFlag,
        Boolean  p_overrideFileProfileAsUnextracted,
        String p_importRequestType)
        throws Exception
    {
        CxeMessageType type = CxeMessageType.getCxeMessageType(
            CxeMessageType.VIGNETTE_FILE_SELECTED_EVENT);
        CxeMessage cxeMessage = new CxeMessage(type);
        HashMap params = new HashMap();
        String companyId = getCompanyIdByFileProfileId(p_fileProfileId);
        params.put(CompanyWrapper.CURRENT_COMPANY_ID, companyId);
        params.put("ObjectId", p_srcMid);
        params.put("Path", p_path);
        params.put("FileProfileId", p_fileProfileId);
        params.put("JobName",p_jobName);
        params.put("BatchId",p_batchId);
        params.put("PageCount", new Integer(p_pageCount));
        params.put("PageNum", new Integer(p_pageNum));
        params.put("DocPageCount", new Integer(p_docPageCount));
        params.put("DocPageNum", new Integer(p_docPageNum));
        params.put("TargetProjectMid", p_targetProjectMid);
        params.put("ReturnStatus", p_returnStatus);
        params.put("VersionFlag", p_versionFlag);
        params.put("OverrideFileProfileAsUnextracted",
            p_overrideFileProfileAsUnextracted);
        params.put(IMPORT_TYPE, p_importRequestType);
        cxeMessage.setParameters(params);

        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_VIGNETTE_SOURCE_ADAPTER;
        ObjectMessage om = getTopicSession().createObjectMessage(cxeMessage);
        TopicPublisher sender = getPublisher(jmsTopic);
        sender.publish(om);
    }


    /**
     * Initiates an import of a knowledge object and associated
     * Concepts from ServiceWare.
     *
     * @param p_koId knowledge object ID
     * @param p_fpId file profile id
     * @param p_jobName suggested job name
     * @exception Exception
     */
    static public void importFromServiceWare(
        String p_koId, String p_fpId, String p_jobName)
        throws Exception
    {
        CxeMessageType type = CxeMessageType.getCxeMessageType(
            CxeMessageType.SERVICEWARE_FILE_SELECTED_EVENT);
        CxeMessage cxeMessage = new CxeMessage(type);
        HashMap params = new HashMap();

        String companyId = getCompanyIdByFileProfileId(p_fpId);
        params.put(CompanyWrapper.CURRENT_COMPANY_ID, companyId);
        params.put("KOID", p_koId);
        params.put("JobName", p_jobName);
        params.put("BatchId", p_jobName + new Date());
        params.put("PageCount", new Integer(1));
        params.put("PageNum", new Integer(1));
        params.put("DocPageCount", new Integer(1));
        params.put("DocPageNum", new Integer(1));
        params.put("FileProfileId", p_fpId);
        params.put("OverrideFileProfileAsUnextracted",
            Boolean.FALSE);
        cxeMessage.setParameters(params);

        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_SERVICEWARE_SOURCE_ADAPTER;
        ObjectMessage om = getTopicSession().createObjectMessage(cxeMessage);
        TopicPublisher sender = getPublisher(jmsTopic);
        sender.publish(om);
    }

    /**
     * Initiates a Mediasurface import using JMS for one MS leaf item.
     * The caller has already figured out the batch information and
     * should know what to pass for batchId, pageNum, and pageCount.
     *
     * @exception Exception
     */
    static public void importFromMediasurface(
        int p_mediasurfaceItemKey,
        String p_mediasurfaceContentServerUrl,
        String p_mediasurfaceContentServerName,
        String p_mediasurfaceContentServerPort,
        String p_mediasurfaceUser,
        String p_mediasurfacePassword,
        String p_jobName,
        String p_batchId,
        int p_pageNum,
        int p_pageCount,
        int p_docPageNum,
        int p_docPageCount,
        String p_fileProfileId,
        boolean p_overrideFileProfileAsUnextracted,
        String p_importRequestType)
        throws Exception
    {
        CxeMessageType type = CxeMessageType.getCxeMessageType(
            CxeMessageType.MEDIASURFACE_FILE_SELECTED_EVENT);
        CxeMessage cxeMessage = new CxeMessage(type);
        HashMap params = new HashMap();

        String companyId = getCompanyIdByFileProfileId(p_fileProfileId);
        params.put(CompanyWrapper.CURRENT_COMPANY_ID, companyId);
        params.put("MediasurfaceItemKey", new Integer(p_mediasurfaceItemKey));
        params.put("MediasurfaceContentServerUrl", p_mediasurfaceContentServerUrl);
        params.put("MediasurfaceContentServerName", p_mediasurfaceContentServerName);
        params.put("MediasurfaceContentServerPort", p_mediasurfaceContentServerPort);
        params.put("MediasurfaceUser", p_mediasurfaceUser);
        params.put("MediasurfacePassword", p_mediasurfacePassword);
        params.put("JobName", p_jobName);
        params.put("BatchId", p_batchId);
        params.put("PageCount", new Integer(p_pageCount));
        params.put("PageNum", new Integer(p_pageNum));
        params.put("DocPageCount", new Integer(p_docPageCount));
        params.put("DocPageNum", new Integer(p_docPageNum));
        params.put("FileProfileId", p_fileProfileId);
        params.put("OverrideFileProfileAsUnextracted",
            new Boolean(p_overrideFileProfileAsUnextracted));
        params.put(IMPORT_TYPE, p_importRequestType);
        cxeMessage.setParameters(params);

        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_MEDIASURFACE_SOURCE_ADAPTER;
        ObjectMessage om = getTopicSession().createObjectMessage(cxeMessage);
        TopicPublisher sender = getPublisher(jmsTopic);
        sender.publish(om);
    }

    /**
     * Returns true if the MS Office Adapter is installed
     *
     * @return true | false
     */
    static public boolean isMsOfficeAdapterInstalled()
    {
        return MsOfficeAdapter.isInstalled();
    }

    /**
     * Returns true if the TeamSite Adapter is installed
     *
     * @return true | false
     */
    static public boolean isTeamSiteAdapterInstalled()
    {
        return TeamSiteAdapter.isInstalled();
    }

    /**
     * Returns true if the Database Adapter is installed
     *
     * @return true | false
     */
    static public boolean isDatabaseAdapterInstalled()
    {
        return DatabaseAdapter.isInstalled();
    }

    /**
     * Returns true if the Pdf Adapter is installed
     *
     * @return true | false
     */
    static public boolean isPdfAdapterInstalled()
    {
        return PdfAdapter.isInstalled();
    }

    /**
     * Returns true if the Pdf Adapter is installed
     *
     * @return true | false
     */
    static public boolean isVignetteAdapterInstalled()
    {
        return VignetteAdapter.isInstalled();
    }

    /**
     * Returns true if the ServiceWare Adapter is installed
     *
     * @return true | false
     */
    static public boolean isServiceWareAdapterInstalled()
    {
        return ServiceWareAdapter.isInstalled();
    }

    /**
     * Returns true if the Desktop Publishing (Quark/Frame) Adapter is installed
     *
     * @return true | false
     */
    static public boolean isQuarkFrameAdapterInstalled()
    {
        return QuarkFrameAdapter.isInstalled();
    }


    ///////////////////////
    // Private methods  //
    //////////////////////


    /**
     * Gets a publisher for the given topic
     *
     * @param p_topicName JMS topic name
     * @return JMS Publisher
     * @exception JMSException
     * @exception NamingException
     */
    static private TopicPublisher getPublisher(String p_topicName)
        throws JMSException, NamingException
    {
        // This is JBOSS specified, when jboss bind the JMS destination(Queue or Topic) in to naming context,
        // it will automatically add the prefix "topic/queue" ahead of the JNDI name, such as: 
        // "topic/com.globalsight.cxe.jms.ForExtractor".  
        // So if the GlobalSight works on JBOSS, we need add the prefix manually when lookup the JMS destination.
        AppServerWrapper s_appServerWrapper = AppServerWrapperFactory.getAppServerWrapper();
        if (s_appServerWrapper.getJ2EEServerName().equals(AppServerWrapperFactory.JBOSS)) 
        {
            p_topicName = EventTopicMap.TOPIC_PREFIX_JBOSS + p_topicName;
        }

        Topic newTopic = (Topic) s_context.lookup(p_topicName);
        TopicPublisher sender = getTopicSession().createPublisher(newTopic);
        return sender;
    }

    /**
     * Gets a JMS topic session on a per-thread basis. This stores the
     * TopicSessions per thread in a static HashMap.
     *
     * @return JMS TopicSession
     * @exception JMSException
     * @exception NamingException
     */
    static private TopicSession getTopicSession()
        throws JMSException, NamingException
    {
        String threadName = Thread.currentThread().getName();
        return getTopicSession(threadName);
    }
    
    static private TopicSession getTopicSession(String key)
        throws JMSException, NamingException
    {
        TopicSession ts = (TopicSession) s_topicSessions.get(key);

        if (ts == null)
        {
            TopicConnectionFactory cf = (TopicConnectionFactory) s_context.lookup(JMS_FACTORY);
            TopicConnection topicConnection = cf.createTopicConnection();
            topicConnection.start();
            ts = topicConnection.createTopicSession(false, Session.CLIENT_ACKNOWLEDGE);
            s_topicSessions.put(key, ts);
        }

        return ts;
    }

    /**
     * Initiates an export within CXE using JMS
     *
     * @param p_eventFlowXml string of event flow xml
     * @param p_gxmlFileName filename of a file that contains GXML
     * @param p_cxeRequestType CXE Request Type
     * @param p_targetLocale target locale
     * @param p_targetCharset target encoding
     * @param p_messageId export message ID
     * @param p_exportLocation export location
     * @param p_localeSubDir
     * @param p_exportBatchId
     * @param p_pageCount
     * @param p_pageNum
     * @param p_isUnextracted
     * @param p_sessionId
     * @param p_fileName The name of the file that is being exported
     * (relative path)
     * @exception JMSException
     * @exception NamingException
     */
    static private void exportFile(String p_eventFlowXml, MessageData p_gxml,
        String p_cxeRequestType, String p_targetLocale, String p_targetCharset,
        String p_messageId, String p_exportLocation, String p_localeSubDir,
        String p_exportBatchId, Integer p_pageCount, Integer p_pageNum,
        Integer p_docPageCount, Integer p_docPageNum, String newObjId, 
        long wfId, boolean isJobDone, boolean p_isUnextracted, String p_sessionId,
        String p_fileName, boolean p_isFinalExport, String p_companyId)
        throws JMSException, NamingException, IOException
    {
        CxeMessageType type;
        if (p_isUnextracted)
        {
            type = CxeMessageType.getCxeMessageType(
                CxeMessageType.UNEXTRACTED_LOCALIZED_EVENT);
        }
        else
        {
            type = CxeMessageType.getCxeMessageType(
                CxeMessageType.GXML_LOCALIZED_EVENT);
        }

        // Handle STF created files as unextracted as well if it's not
        // the STF creation, but a regular STF export.
        if (ExportConstants.EXPORT_STF.equals(p_cxeRequestType))
        {
            type = CxeMessageType.getCxeMessageType(
                CxeMessageType.UNEXTRACTED_LOCALIZED_EVENT);
        }

        CxeMessage exportMsg = new CxeMessage(type);
        HashMap params = new HashMap();
        
        if (p_targetCharset != null && p_targetCharset.endsWith("_di"))
        {
            p_targetCharset = p_targetCharset.replace("_di", "");
        }
       
        else if ("Unicode Escape".equalsIgnoreCase(p_targetCharset))
        {
            p_targetCharset = "UTF-8";
            params.put("UnicodeEscape", "true");
        }
        else
        {
            params.put("UnicodeEscape", "false");
        }
        if ("Entity Escape".equalsIgnoreCase(p_targetCharset))
        {
            p_targetCharset = "ISO-8859-1";
            params.put("EntityEscape","true");
        }
        else
        {
            params.put("EntityEscape","false");
        }
        params.put(CompanyWrapper.CURRENT_COMPANY_ID, p_companyId);
        params.put("TargetLocale", p_targetLocale);
        params.put("TargetCharset", p_targetCharset);
        params.put("CxeRequestType", p_cxeRequestType);
        params.put("MessageId", p_messageId);
        params.put("ExportLocation", p_exportLocation);
        params.put("LocaleSubDir", p_localeSubDir);
        params.put("ExportBatchId", p_exportBatchId);
        params.put("PageCount", p_pageCount);
        params.put("PageNum", p_pageNum);
        params.put("DocPageCount", p_docPageCount);
        params.put("DocPageNum", p_docPageNum);
        
        params.put(DocumentumOperator.DCTM_NEWOBJECTID, newObjId);
        params.put(DocumentumOperator.DCTM_WORKFLOWID, String.valueOf(wfId));
        params.put(DocumentumOperator.DCTM_ISJOBDONE, new Boolean(isJobDone));
        
        params.put("SessionId", p_sessionId);
        params.put("TargetFileName", p_fileName);
        params.put("IsFinalExport", new Boolean (p_isFinalExport));

        exportMsg.setParameters(params);
        exportMsg.setEventFlowXml(p_eventFlowXml);
        exportMsg.setMessageData(p_gxml);

        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_CAP_SOURCE_ADAPTER;
        sendMessage(exportMsg, jmsTopic);
    }

    /**
     * Returns the Import success or Failure message to TeamSite
     *
     * @param p_efxml string of event flow xml
     * @param p_jobState string of IMPORT_FAILED or PENDING
     * @param p_message string of message with details of files
     * @exception IOException
     * @exception JMSException
     * @exception NamingException
     */
    static public void returnImportStatusToTeamSite(String p_efxml,
        String p_jobState, String p_message, String p_companyId)
        throws JMSException, NamingException, IOException
    {
        CxeMessageType type = CxeMessageType.getCxeMessageType(
            CxeMessageType.TEAMSITE_JOB_STATUS_EVENT);
        CxeMessage cxeMessage = new CxeMessage(type);
        cxeMessage.setEventFlowXml(p_efxml);
        HashMap params = new HashMap();
        params.put(CompanyWrapper.CURRENT_COMPANY_ID, p_companyId);
        params.put("TeamSiteJobState", p_jobState);
        params.put("TeamSiteMessage", p_message);
        cxeMessage.setParameters(params);

        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_TEAMSITE_SOURCE_ADAPTER;
        ObjectMessage om = getTopicSession().createObjectMessage(cxeMessage);
        TopicPublisher sender = getPublisher(jmsTopic);
        sender.publish(om);
    }

    /**
     * A special import for aligner/filesystem method.
     * To invoke this method, we assume this method can get correct company id 
     * from the CompanyThreadLocal. That's to say, the invoker should have already 
     * set the correct company id in CompanyThreadLocal.
     *
     * @param p_alignerExtractor An object used to control the
     * asynchronous alignment process and wait for the results
     * @exception JMSException
     * @exception NamingException
     */
    static public void importFromFileSystemForAligner(
        AlignerExtractor p_alignerExtractor)
        throws JMSException, NamingException
    {
        CxeMessageType type = CxeMessageType.getCxeMessageType(
            CxeMessageType.FILE_SYSTEM_FILE_SELECTED_EVENT);
        CxeMessage cxeMessage = new CxeMessage(type);
        HashMap params = cxeMessage.getParameters(); 
        CompanyWrapper.saveCurrentCompanyIdInMap(params, s_logger);
        params.put("Filename", p_alignerExtractor.getFilename());
        params.put("JobName", p_alignerExtractor.getName());
        params.put("BatchId", p_alignerExtractor.getName());
        params.put("PageCount", ONE);
        params.put("PageNum", ONE);
        params.put("DocPageCount", ONE);
        params.put("DocPageNum", ONE);
        params.put("IsAutomaticImport", Boolean.FALSE);
        params.put("OverrideFileProfileAsUnextracted", Boolean.FALSE);

        //put in special values for the aligner
        params.put("AlignerExtractor", p_alignerExtractor.getName());
        params.put(IMPORT_TYPE, IMPORT_TYPE_ALIGNER);

        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_FILE_SYSTEM_SOURCE_ADAPTER;
        ObjectMessage om = getTopicSession().createObjectMessage(cxeMessage);
        TopicPublisher sender = getPublisher(jmsTopic);
        sender.publish(om);
    }

    /**
     * Initiates a Documentum import using JMS.
     *
     * @param p_objID Documentum object ID
     * @param p_filename path based filename
     * @param p_jobName job name
     * @param p_batchId batch Id
     * @param p_fileProfileId file profile ID
     * @param p_pageCount number of pages in the batch
     * @param p_pageNum number of the current page in the batch
     * (starting from 1)
     * @param dctmFileAttrXml - the xml string for DCTM file attributes.
     * 
     * @exception JMSException
     * @exception NamingException
     */
    static public void importFromDocumentum(String p_objID, String p_fileName, String p_jobName, 
            String p_batchId, String p_fileProfileId, Integer p_pageCount, Integer p_pageNum, 
            Integer p_docPageCount, Integer p_docPageNum, boolean isAttrFile, String dctmFileAttrXml,
            String userId)
        throws JMSException, NamingException
    {
        CxeMessageType type = CxeMessageType.getCxeMessageType(
            CxeMessageType.DOCUMENTUM_FILE_SELECTED_EVENT);
        CxeMessage cxeMessage = new CxeMessage(type);
        HashMap params = cxeMessage.getParameters();
        params.put(DocumentumOperator.DCTM_OBJECTID, p_objID);
        //        params.put("DocbaseName", p_docbaseName);
        String companyId = getCompanyIdByFileProfileId(p_fileProfileId);
        params.put(CompanyWrapper.CURRENT_COMPANY_ID, companyId);
        params.put("Filename", p_fileName);
        String jobName = p_jobName;
        if (p_jobName == null || p_jobName.length() == 0)
        {
            long timeInSec = System.currentTimeMillis() / 1000L;
            jobName = "dctmjob_" + timeInSec;
        }

        params.put("JobName", jobName);
        params.put("BatchId", p_batchId);
        params.put("FileProfileId", p_fileProfileId);
        params.put("PageCount", p_pageCount);
        params.put("PageNum", p_pageNum);
        params.put("DocPageCount", p_docPageCount);
        params.put("DocPageNum", p_docPageNum);

        params.put(DocumentumOperator.DCTM_ISATTRFILE, new Boolean(isAttrFile));
        params.put(DocumentumOperator.DCTM_FILEATTRXML, dctmFileAttrXml);
        params.put(DocumentumOperator.DCTM_USERID, userId);

        params.put("IsAutomaticImport", Boolean.FALSE);
        params.put("OverrideFileProfileAsUnextracted", Boolean.FALSE);

        String jmsTopic = EventTopicMap.JMS_PREFIX +
            EventTopicMap.FOR_DOCUMENTUM_SOURCE_ADAPTER;
        ObjectMessage om = getTopicSession().createObjectMessage(cxeMessage);
        TopicPublisher sender = getPublisher(jmsTopic);
        sender.publish(om);
    }
    
    private static String getCompanyIdByFileProfileId(String p_fileProfileId)
    {
        String companyId = null;
        try {
            FileProfile fp = ServerProxy.getFileProfilePersistenceManager()
                .getFileProfileById(Long.parseLong(p_fileProfileId), false);
            companyId = fp.getCompanyId();
        } catch (Exception e) {
            throw new EnvoyServletException(e);
        } 
        
        return companyId;
    }
    
    /**
     * Sends message using JMS, after sending, it should close some resources.
     * @param msg
     * @param jmsTopic
     */
    private static void sendMessage(CxeMessage msg, String jmsTopic) 
    {
        ObjectMessage om = null;
        TopicPublisher sender = null;
        try 
        {
            om = s_sendMessageTopicSession.createObjectMessage(msg);
            
            AppServerWrapper s_appServerWrapper = AppServerWrapperFactory.getAppServerWrapper();
            if (s_appServerWrapper.getJ2EEServerName().equals(AppServerWrapperFactory.JBOSS)) 
            {
                jmsTopic = EventTopicMap.TOPIC_PREFIX_JBOSS + jmsTopic;
            }
            Topic newTopic = (Topic) s_context.lookup(jmsTopic);
            sender = s_sendMessageTopicSession.createPublisher(newTopic);
            sender.publish(om);
        } 
        catch (NamingException e) 
        {
            s_logger.error("Failed to open topic connection", e);
        }
        catch (JMSException e) 
        {
            s_logger.error("Failed to open jms", e);
        } 
        finally 
        {
            try 
            {
                if (sender != null) 
                {
                    sender.close();
                }
                if (om != null) 
                {
                    om = null;
                }

            } 
            catch (JMSException e)
            {
                 s_logger.error("Failed to close jms", e);
            }
        }               

    }
}