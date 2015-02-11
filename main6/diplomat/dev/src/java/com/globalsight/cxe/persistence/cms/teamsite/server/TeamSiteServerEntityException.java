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
package com.globalsight.cxe.persistence.cms.teamsite.server;
/*
 * Copyright (c) 2001 GlobalSight Corporation. All rights reserved.
 *
 * THIS DOCUMENT CONTAINS TRADE SECRET DATA WHICH IS THE PROPERTY OF
 * GLOBALSIGHT CORPORATION. THIS DOCUMENT IS SUBMITTED TO RECIPIENT
 * IN CONFIDENCE. INFORMATION CONTAINED HEREIN MAY NOT BE USED, COPIED
 * OR DISCLOSED IN WHOLE OR IN PART EXCEPT AS PERMITTED BY WRITTEN
 * AGREEMENT SIGNED BY AN OFFICER OF GLOBALSIGHT CORPORATION.
 *
 * THIS MATERIAL IS ALSO COPYRIGHTED AS AN UNPUBLISHED WORK UNDER
 * SECTIONS 104 AND 408 OF TITLE 17 OF THE UNITED STATES CODE.
 * UNAUTHORIZED USE, COPYING OR OTHER REPRODUCTION IS PROHIBITED
 * BY LAW.
 */

import com.globalsight.util.GeneralException;
import com.globalsight.util.GeneralExceptionConstants;
import com.globalsight.cxe.entity.cms.teamsite.store.BackingStore;
import com.globalsight.cxe.entity.cms.teamsite.server.TeamSiteServer;
import com.globalsight.cxe.entity.cms.teamsite.server.TeamSiteServerImpl;

public class TeamSiteServerEntityException extends GeneralException
{
    // public statics for error messages
    public static final String MSG_TEAMSITE_SERVER_ALREADY_EXISTS = 
        "TeamSiteServerAlreadyExists";

    public TeamSiteServerEntityException(String p_msg)
    {
	super(COMP_PERSISTENCE, EX_SQL, p_msg);
    }

    public TeamSiteServerEntityException(Exception p_ex)
    {
	super(COMP_PERSISTENCE, EX_SQL, p_ex);
    }

    /* Normal constructor for TeamSiteServerEntityException used by the TeamSiteServerPersistenceManager */
    public TeamSiteServerEntityException(String p_key, String[] p_args, Exception p_exception)
    {
      super(p_key,p_args,p_exception,"TeamSiteServerEntityException");
    }

    /** Creates an EntityException in custom cases.
     ** The property file must exist in com.globalsight.resources.messages
     */
    public TeamSiteServerEntityException(String p_key, String[] p_args, Exception p_exception, String p_propertyFile)
    {
      super(p_key,p_args,p_exception, p_propertyFile);
    }
}