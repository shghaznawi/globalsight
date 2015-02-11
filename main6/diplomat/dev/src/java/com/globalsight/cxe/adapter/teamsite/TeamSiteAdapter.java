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
package com.globalsight.cxe.adapter.teamsite;

import com.globalsight.cxe.adapter.AdapterResult;
import com.globalsight.cxe.adapter.BaseAdapter;
import com.globalsight.cxe.adapter.CxeProcessor;
import com.globalsight.cxe.adapter.teamsite.Importer;
import com.globalsight.cxe.message.CxeMessage;
import com.globalsight.cxe.message.CxeMessageType;
import com.globalsight.cxe.message.MessageData;
import com.globalsight.cxe.util.EventFlowXmlParser;
import com.globalsight.everest.page.SourcePage;
import com.globalsight.everest.util.system.SystemConfigParamNames;
import com.globalsight.everest.util.system.SystemConfiguration;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.diplomat.util.Logger;
import com.globalsight.util.GeneralException;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.HashMap;

import java.rmi.RemoteException;

/**
 * The TeamSiteAdapter class provides a means of interacting with the TeamSite
 * in order to do import/export (and possibly other) functions within CXE.
 */
public class TeamSiteAdapter extends BaseAdapter
{
    // ////////////////////////////////////
    // Private Members //
    // ////////////////////////////////////
    private static boolean s_isInstalled = false;

    // ////////////////////////////////////
    // Constructor //
    // ////////////////////////////////////
    /**
     * Creates a TeamSiteAdapter object
     * 
     * @param p_loggingCategory
     *            the given logging category name (for example
     *            "TeamSiteSourceAdapter" or "TeamSiteTargetAdapter")
     * 
     * @exception GeneralException
     */
    public TeamSiteAdapter(String p_loggingCategory) throws GeneralException
    {
        super(p_loggingCategory);
        if (!isInstalled())
            throw new GeneralException(
                    "This adapter is not installed properly.");
    }

    /** * Public Methods ** */

    /**
     * Returns true if the given adapter is installed. This method may check
     * installation key validity.
     * 
     * @return true | false
     */
    public static boolean isInstalled()
    {
        String installKey = "TS-" + "GS".hashCode() + "-" + "iwov".hashCode();
        s_isInstalled = isInstalled(
                SystemConfigParamNames.TEAMSITE_INSTALL_KEY, installKey);

        return s_isInstalled;
    }

    /**
     * <P>
     * Performs the main function of the adapter based on the CxeMessage. For
     * the TeamSiteAdapter this is either "reading" the file in from the
     * TeamSite and creating the necessary EventFlowXml as a part of import, or
     * "writing" the file back to the file system for export.
     * </P>
     * <P>
     * The adapter switches on the CxeMessage message type and handles the
     * following events:<br>
     * <ol>
     * <li>TEAMSITE_FILE_SELECTED_EVENT </li>
     * <li>TEAMSITE_EXPORT_EVENT </li>
     * </ol>
     * </P>
     * 
     * @param p_cxeMessage
     *            a CxeMessage object containing EventFlowXml, content, and
     *            possibly parameters
     * @return AdapterResult[]
     * @exception GeneralException
     */
    public AdapterResult[] handleMessage(CxeMessage p_cxeMessage)
            throws GeneralException
    {
        switch (p_cxeMessage.getMessageType().getValue())
        {
            case CxeMessageType.TEAMSITE_FILE_SELECTED_EVENT:
                return handleSelectedFileEvent(p_cxeMessage);

            case CxeMessageType.TEAMSITE_EXPORT_EVENT:
                return handleExportEvent(p_cxeMessage);

            case CxeMessageType.TEAMSITE_JOB_STATUS_EVENT:
                return handleStatusEvent(p_cxeMessage);

            case CxeMessageType.TEAMSITE_IGNORE_EVENT:
                AdapterResult[] results = null;
                return results;

            default:
                Exception e = new Exception("Unhandled message type: "
                        + p_cxeMessage.getMessageType().getName());
                throw new TeamSiteAdapterException("Unexpected", getLogger()
                        .getName(), e);
        }
    }

    /**
     * Handles the import case where a user has selected a file and a "selected
     * file event" comes in to the file system adapter
     * 
     * @param p_cxeMessage
     *            CxeMessage of type FILE_SYSTEM_FILE_SELECTED_EVENT
     * @return AdapterResult[]
     * @exception GeneralException
     */
    private AdapterResult[] handleSelectedFileEvent(CxeMessage p_cxeMessage)
            throws GeneralException
    {
        AdapterResult[] results = null;
        String filename = null;
        try
        {
            HashMap params = p_cxeMessage.getParameters();
            HashMap newParams = new HashMap();
            Importer importer = new Importer(p_cxeMessage, getLogger());
            CxeMessage outputMsg = importer.performImport();
            results = new AdapterResult[1];
            results[0] = new AdapterResult(outputMsg);
            getLogger().info(
                    "Publishing " + outputMsg.getMessageType().getName()
                            + " of size "
                            + outputMsg.getMessageData().getSize());
        }
        catch (TeamSiteAdapterException fsae)
        {
            results = makeImportError(p_cxeMessage, fsae);
        }
        catch (Exception e)
        {
            getLogger().error("TeamSite import error.", e);
            String[] errorArgs = new String[2];
            errorArgs[0] = this.getLogger().getName();
            errorArgs[1] = filename;
            TeamSiteAdapterException fsae = new TeamSiteAdapterException(
                    "UnexpectedIm", errorArgs, e);
            results = makeImportError(p_cxeMessage, fsae);
        }

        return results;
    }

    /**
     * Handles the export case. Writes the given content out.
     * 
     * @param p_cxeMessage
     *            CxeMessage of type TEAMSITE_EXPORT_EVENT
     * @return AdapterResult[]
     * @exception GeneralException
     */
    private AdapterResult[] handleExportEvent(CxeMessage p_cxeMessage)
            throws GeneralException
    {
        AdapterResult[] results = new AdapterResult[1];
        CxeMessage returnMessage = null;
        try
        {
            if (isStfCreationExport(p_cxeMessage))
            {
                returnMessage = makeStfCreationMsg(p_cxeMessage);
                results[0] = new AdapterResult(returnMessage);
            }
            else
            {
                Exporter exporter = new Exporter(p_cxeMessage, this.getLogger());
                returnMessage = exporter.exportFile();
                results = makeExportStatus(returnMessage, null);
            }

        }
        catch (Exception e)
        {
            String[] errorArgs = new String[1];
            errorArgs[0] = this.getLogger().getName();
            TeamSiteAdapterException fsae = new TeamSiteAdapterException(
                    "UnexpectedIm", errorArgs, e);
            results = makeExportStatus(p_cxeMessage, fsae);
        }
        return results;
    }

    /**
     * Handles the export case. Writes the given content out.
     * 
     * @param p_cxeMessage
     *            CxeMessage of type TEAMSITE_STATUS_EVENT
     * @return AdapterResult[]
     * @exception GeneralException
     */
    private AdapterResult[] handleStatusEvent(CxeMessage p_cxeMessage)
            throws GeneralException
    {
        AdapterResult[] results = new AdapterResult[1];
        CxeMessage returnMessage = null;
        try
        {
            JobStateReporter ImpStatus = new JobStateReporter(p_cxeMessage,
                    this.getLogger());
            returnMessage = ImpStatus.sendTeamSiteJobState();
            results = makeSingleAdapterResult(returnMessage);
        }
        catch (Exception e)
        {
            String[] errorArgs = new String[1];
            errorArgs[0] = this.getLogger().getName();
            TeamSiteAdapterException fsae = new TeamSiteAdapterException(
                    "UnexpectedIm", errorArgs, e);
            results = makeExportStatus(p_cxeMessage, fsae);
        }
        return results;
    }
}