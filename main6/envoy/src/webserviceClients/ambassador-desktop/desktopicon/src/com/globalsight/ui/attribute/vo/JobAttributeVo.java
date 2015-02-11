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

package com.globalsight.ui.attribute.vo;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class JobAttributeVo
{
    private static final long serialVersionUID = 4341436923688472768L;

    private String internalName;
    private String displayName;
    private String type;
    private boolean required;
    private boolean fromSuperCompany;

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getInternalName()
    {
        return internalName;
    }

    public void setInternalName(String internalName)
    {
        this.internalName = internalName;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public boolean isRequired()
    {
        return required;
    }

    public void setRequired(boolean required)
    {
        this.required = required;
    }
    
    public boolean isSetted()
    {
        return false;
    }

    public boolean isFromSuperCompany()
    {
        return fromSuperCompany;
    }

    public void setFromSuperCompany(boolean fromSuperCompany)
    {
        this.fromSuperCompany = fromSuperCompany;
    }
}