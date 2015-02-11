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
package com.globalsight.ling.tm;

import java.util.Vector;
import java.util.Collection;
import java.util.Iterator;
import java.util.HashMap;
import java.util.ArrayList;

import com.globalsight.util.GlobalSightLocale;
import com.globalsight.ling.tm.fuzzy.Tokenizer;
import com.globalsight.ling.tm.fuzzy.Token;
import com.globalsight.ling.tm2.BaseTmTuv;
import com.globalsight.ling.docproc.GlobalsightBreakIterator;
import com.globalsight.ling.docproc.GlobalsightRuleBasedBreakIterator;

public class StoredProcedureParams 
{
    private static final String DUMMY_STYLE = "__non_existing_style__";

    private Vector m_numberLocalizable = null; // exact match search
    private Vector m_numberTranslatable = null; // fuzzy match search
    private Vector m_numberForReimport = null; // number params for reimport
    //    private Vector m_numberLevMatchSave = null; // leverage match save
    private Vector m_stringParams = null; // String parameters
    
    private Tokenizer m_tokenizer = null;

    /** Creates new StoredPrcedureParams */
    public StoredProcedureParams() 
    {        
        m_tokenizer = new Tokenizer();
    }
                
    public Vector getNumberLocalizable()
    {     
        return m_numberLocalizable;
    }
    
    public Vector getNumberTranslatable()
    {
        return m_numberTranslatable;
    }
    
    public Vector getNumberForReimport()
    {
        return m_numberForReimport;
    }
    
//      public void addNumberLevMatchSave(double p_param)
//      {
//          m_numberLevMatchSave.add(new Double(p_param));   
//      }
    
//      public Vector getNumberLevMatchSave()
//      {
//          return m_numberLevMatchSave;
//      }    


    public Vector getStringParams()
    {
        return m_stringParams;
    }    


    public void setParamsForLgem(
        Collection p_tm2Tuvs, GlobalSightLocale p_sourceLocale,
        Collection p_targetLocales, Collection p_prevleverageGroupIds)
        throws LingManagerException
    {
        // clear out the previous setting
        clear();
        m_numberForReimport = new Vector(500);
        
        addNumberForReimport(p_sourceLocale.getIdAsLong().doubleValue());
        addNumberForReimport(leverageGroupIds(p_prevleverageGroupIds));
        addNumberForReimport((double)-1);
        addNumberForReimport(localeIds(p_targetLocales));
        addNumberForReimport((double)-1);

        // Loop through each BaseTmTuv in the current leverage group
        // for each BaseTmTuv generates parameters needed to
        Iterator tuvIterator = p_tm2Tuvs.iterator();
        while (tuvIterator.hasNext())
        {
            BaseTmTuv tuv = (BaseTmTuv)tuvIterator.next();
            boolean bTranslatable = tuv.isTranslatable();

            addNumberForReimport((double)tuv.getId());
            // localization_type
            addNumberForReimport((bTranslatable ? (double)1 : (double)0));
            addNumberForReimport((double)tuv.getExactMatchKey());
            addNumberForReimport((double)-1);

        }

        m_stringParams = new Vector();
        addStringParams(DUMMY_STYLE);
    }


    public void setParamsForLatest(
        Collection p_tuvLings, GlobalSightLocale p_sourceLocale,
        Collection p_targetLocales)
        throws LingManagerException
    {
        // clear out the previous setting
        clear();
        m_numberForReimport = new Vector(500);

        addNumberForReimport(p_sourceLocale.getIdAsLong().doubleValue());
        addNumberForReimport(localeIds(p_targetLocales));
        addNumberForReimport((double)-1);


        // Loop through each TuvLing in the current leverage group
        // for each TuvLing generate parameters needed to
        Iterator tuvLingIterator = p_tuvLings.iterator();
        while (tuvLingIterator.hasNext())
        {
            TuvLing tuvLing = (TuvLing)tuvLingIterator.next();
            boolean bLocalizable = tuvLing.isLocalizable();

            addNumberForReimport((double)tuvLing.getId());
            addNumberForReimport((double)tuvLing.getExactMatchKey());
            addNumberForReimport((bLocalizable ? (double)0 : (double)1));
            addNumberForReimport((double)-1);
        }

        m_stringParams = new Vector();
        addStringParams(DUMMY_STYLE);
    }


    public void setParams(
        Collection p_tuvLings, GlobalSightLocale p_sourceLocale,
        Collection p_leveragingLocales,
        LeverageProperties p_leverageProperties)
        throws LingManagerException
    {
        GlobalsightBreakIterator ruleBasedBreakIterator =
            GlobalsightRuleBasedBreakIterator.getWordInstance(
                p_sourceLocale.getLocale());

        // clear out the previous setting
        clear();
        m_numberLocalizable = new Vector(500);
        m_numberTranslatable = new Vector(500);
        
        // localizable match params
        addNumberLocalizable(p_sourceLocale.getIdAsLong().doubleValue());
        addNumberLocalizable(localeIds(p_leveragingLocales));
        addNumberLocalizable((double)-1);
        int localizableHeaderSize = getNumberLocalizable().size();
        
        // translatable match params
        addNumberTranslatable(
            (double)p_leverageProperties.getFuzzyThreshold() * 0.01);
        addNumberTranslatable(p_sourceLocale.getIdAsLong().doubleValue());
        addNumberTranslatable(localeIds(p_leveragingLocales));
        addNumberTranslatable((double)-1);
        int translatableHeaderSize = getNumberTranslatable().size();

        // Loop through each TuvLing in the current leverage group
        // for each TuvLing generate parameters needed to
        Iterator tuvLingIterator = p_tuvLings.iterator();
        while (tuvLingIterator.hasNext())
        {
            TuvLing tuvLing = (TuvLing)tuvLingIterator.next();
            boolean bLocalizable = tuvLing.isLocalizable();

            if (bLocalizable)
            {
                addNumberLocalizable((double)tuvLing.getId());
                addNumberLocalizable((double)tuvLing.getExactMatchKey());
                addNumberLocalizable((double)-1);
            }
            else
            { // translatable
                HashMap tokens = m_tokenizer.tokenize(
                    tuvLing.getFuzzyMatchFormat(),
                    p_sourceLocale.getLocale(),
                    ruleBasedBreakIterator);
                if(tokens.size() > 0)
                {
                    addNumberTranslatable((double)tuvLing.getId());
                    addNumberTranslatable((double)tokens.size());
                    addNumberTranslatable(tokensAsDoubles(tokens));
                    addNumberTranslatable((double)-1);
                }
            }
        }

        m_stringParams = new Vector();
        addStringParams(DUMMY_STYLE);

        // there were no localizables
        if (getNumberLocalizable().size() <= localizableHeaderSize)
        {
            getNumberLocalizable().clear();
        }

        // there were no translatables
        if (getNumberTranslatable().size() <= translatableHeaderSize)
        {
            getNumberTranslatable().clear();
        }
    }


    private Vector makeExcludedList(Collection p_items)
    {
        Vector excludes = new Vector(p_items.size());

        for (Iterator it = p_items.iterator(); it.hasNext();)
        {
            String item = (String)it.next();
            excludes.add(item);
        }

        return excludes;
    }

    private Vector tokensAsDoubles(HashMap p_tokens)
    {
        Collection theTokens = p_tokens.values();
        Iterator it = theTokens.iterator();
        Vector tokensAsDoubles = new Vector(theTokens.size());

        while (it.hasNext())
        {
            Token token = (Token)it.next();
            tokensAsDoubles.add(new Double((double)token.getTokenCrc()));
        }

        return tokensAsDoubles;
    }

    private Vector localeIds(Collection p_targetLocales)
    {
        Vector v = new Vector();
        Iterator it = p_targetLocales.iterator();
        while (it.hasNext())
        {
            v.add(new Double(((GlobalSightLocale)it.next())
                .getIdAsLong().doubleValue()));
        }
        return v;
    }

    private Vector leverageGroupIds(Collection p_leverageGroupIds)
    {
        Vector v = new Vector();
        Iterator it = p_leverageGroupIds.iterator();
        while (it.hasNext())
        {
            v.add(new Double(((Long)it.next()).doubleValue()));
        }
        return v;
    }

    private void clear()
    {
        m_numberLocalizable = null;
        m_numberTranslatable = null;
        m_numberForReimport = null;
        m_stringParams = null;
        //        m_numberLevMatchSave.clear();
    }
    
    private void addNumberLocalizable(double p_param)
    {
        m_numberLocalizable.add(new Double(p_param));
    }
    
    private void addNumberTranslatable(double p_param)
    {
        m_numberTranslatable.add(new Double(p_param));
    }
    
    private void addNumberForReimport(double p_param)
    {
        m_numberForReimport.add(new Double(p_param));
    }
    
    private void addNumberLocalizable(Vector p_param)
    {
        m_numberLocalizable.addAll(p_param);
    }
    
    private void addNumberTranslatable(Vector p_param)
    {
        m_numberTranslatable.addAll(p_param);
    }
    
    private void addNumberForReimport(Vector p_param)
    {
        m_numberForReimport.addAll(p_param);
    }

    private void addStringParams(String p_param)
    {
        m_stringParams.add(p_param);
    }
    
    private void addStringParams(Vector p_param)
    {
        m_stringParams.addAll(p_param);
    }
    
}