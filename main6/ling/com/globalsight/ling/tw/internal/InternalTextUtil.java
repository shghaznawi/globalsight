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

package com.globalsight.ling.tw.internal;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.globalsight.ling.common.DiplomatBasicParserException;

public class InternalTextUtil
{
    private final static String INTERNAL_START_REGEX_1 = "<bpt[^>]*?i=\"(\\d*?)\"[^>]*?internal=\"yes\"[^>]*?>[^<]*?</bpt>";
    private final static String INTERNAL_ALL_REGEX_1 = "<bpt[^>]*?i=\"%n%\"[^>]*?internal=\"yes\"[^>]*?>[^<]*?</bpt>(.*?)<ept i=\"%n%\">[^<]*?</ept>";
    private final static String INTERNAL_START_REGEX_2 = "<bpt[^>]*?internal=\"yes\"[^>]*?i=\"(\\d*?)\"[^>]*?>[^<]*?</bpt>";
    private final static String INTERNAL_ALL_REGEX_2 = "<bpt[^>]*?internal=\"yes\"[^>]*?i=\"%n%\"[^>]*?>[^<]*?</bpt>(.*?)<ept i=\"%n%\">[^<]*?</ept>";
    private final static String INTERNAL_START_REGEX_3 = "<bpt[^>]*?internal=\"yes\"[^>]*?i=\"(\\d*?)\"[^>]*?/>";
    private final static String INTERNAL_ALL_REGEX_3 = "<bpt[^>]*?internal=\"yes\"[^>]*?i=\"%n%\"[^>]*?/>(.*?)<ept i=\"%n%\"/>";

    private static final String TAG_REGEX  = "<.pt.*?>[^<]*?</.pt>";
    private static final String TAG_REGEX_ALONE  = "<[^>]*?>";
    
    private InternalTexts texts = new InternalTexts();
    private InternalTag internalTag;

    public InternalTextUtil(InternalTag internalTag)
    {
        this.internalTag = internalTag;
    }
    
    private String removeTags(String segment)
    {
        String s1, s2;
        s2 = segment;
        s1 = segment.replaceAll(TAG_REGEX, "");
        while (!s1.equals(s2))
        {
            s2 = s1;
            s1 = segment.replaceAll(TAG_REGEX, "");
        }
        
        s1 = s1.replaceAll(TAG_REGEX_ALONE, "");
        return s1;
    }
    
    private String removeWhiteSpace(String segment)
    {
        String s1, s2;
        s2 = segment;
        s1 = segment.replaceAll("  ", " ");
        while (!s1.equals(s2))
        {
            s2 = s1;
            s1 = segment.replaceAll("  ", " ");
        }
        
        return s1;
    }

    private String preProcessInternalText(String segment, String bptRegex,
            String allRegex) throws DiplomatBasicParserException
    {
        String newSegment = segment;
        Pattern p = Pattern.compile(bptRegex);
        Matcher m = p.matcher(segment);
        while (m.find())
        {
            String i = m.group(1);
            String regex = allRegex.replace("%n%", i);
            Pattern p2 = Pattern.compile(regex);
            Matcher m2 = p2.matcher(segment);

            if (m2.find())
            {
                String matchedSegment = m2.group();
                String internalSegment = m2.group(1);
                internalSegment = removeTags(internalSegment);
                internalSegment= removeWhiteSpace(internalSegment);
                String replaceTag = internalTag.getInternalTag(internalSegment, matchedSegment, texts);
                newSegment = newSegment.replace(matchedSegment, replaceTag);
                texts.addInternalTags(replaceTag, matchedSegment);
                m = p.matcher(newSegment);
            }
            else
            {
                throw new DiplomatBasicParserException("Can not find <ept i=\""
                        + i + "\"> from segment:" + segment);
            }
        }

        return newSegment;
    }
    
    private static Set<String> getInternalIndex(String segment, String regex)
    {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(segment);
        Set<String> indexs = new HashSet<String>();
        while (m.find())
        {
            indexs.add(m.group(1));
        }
        
        return indexs;
    }

    public static Set<String> getInternalIndex(String segment)
    {
        Set<String> indexs = new HashSet<String>();
        indexs.addAll(getInternalIndex(segment, INTERNAL_START_REGEX_1));
        indexs.addAll(getInternalIndex(segment, INTERNAL_START_REGEX_2));
        indexs.addAll(getInternalIndex(segment, INTERNAL_START_REGEX_3));
        
        return indexs;
    }

    public InternalTexts preProcessInternalText(String segment)
            throws DiplomatBasicParserException
    {
        segment = preProcessInternalText(segment, INTERNAL_START_REGEX_1,
                INTERNAL_ALL_REGEX_1);
        segment = preProcessInternalText(segment, INTERNAL_START_REGEX_2,
                INTERNAL_ALL_REGEX_2);
        segment = preProcessInternalText(segment, INTERNAL_START_REGEX_3,
                INTERNAL_ALL_REGEX_3);

        texts.setSegment(segment);
        return texts;
    }
}