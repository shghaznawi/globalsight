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
package com.globalsight.ling.tm2.leverage;

import java.util.Date;
import java.util.List;
import java.util.Vector;

import com.globalsight.everest.edit.SegmentProtectionManager;
import com.globalsight.everest.integration.ling.LingServerProxy;
import com.globalsight.everest.integration.ling.tm2.MatchTypeStatistics;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.tuv.Tuv;
import com.globalsight.everest.tuv.TuvImpl;
import com.globalsight.everest.tuv.TuvManager;
import com.globalsight.ling.inprogresstm.DynamicLeveragedSegment;
import com.globalsight.ling.tm.LeverageMatchLingManager;
import com.globalsight.ling.tm2.SegmentTmTuv;
import com.globalsight.log.GlobalSightCategory;

public class LeverageUtil
{
    public static final String DUMMY_SUBID = "0";
    static private final GlobalSightCategory logger = (GlobalSightCategory) GlobalSightCategory
            .getLogger(LeverageUtil.class);
    private static TuvManager tuvManager = ServerProxy.getTuvManager();
    private static LeverageMatchLingManager lingManager = LingServerProxy
            .getLeverageMatchLingManager();

    /**
     * Compares the sid of the two {@link LeveragedTuv} instances.
     * <p>
     * {@link LeveragedTuv} instance has original sid and matched sid, if the
     * original sid is same as matched sid, the priority will be higher.
     * <p>
     * In other words, the return value will be -1 only if the first original
     * sid is same as the matched sid, and the second {@link LeveragedTuv}
     * instance not.
     * 
     * @param tuv1
     *            The first {@link LeveragedTuv} instance
     * @param tuv2
     *            The second {@link LeveragedTuv} instance
     * @return -1, 0, or 1, depends on the sids of the two {@link LeveragedTuv}
     *         instances
     */
    public static int compareSid(SidComparable tuv1, SidComparable tuv2)
    {
        return getSidCompareRusult(tuv2) - getSidCompareRusult(tuv1);
    }

    /**
     * Compares orgSid with sid of the specified <code>LeveragedTuv</code>.
     * 
     * @param tuv
     *            The specified <code>LeveragedTuv</code>.
     * @return 0 or 1. Returns 1 if the orgSid matched sid, others 0.
     */
    public static int getSidCompareRusult(SidComparable tuv)
    {
        String orgSid = tuv.getOrgSid();
        
        // If the dynamic leveraged segment is NOT from gold TM, will not
        // compare SID.
        boolean flag = true;
        if (tuv instanceof DynamicLeveragedSegment)
        {
            DynamicLeveragedSegment dynamicLevSegment = (DynamicLeveragedSegment) tuv;
            int matchCategory = dynamicLevSegment.getMatchCategory();
            flag = (matchCategory == DynamicLeveragedSegment.FROM_GOLD_TM);
        }
        
        if (flag && orgSid != null && orgSid.equals(tuv.getSid()))
        {
            return 1;
        }

        return 0;
    }

    /**
     * This method compares the last modified time of the two Tuvs. The order of
     * the sort depends on the option the user chose.
     * 
     * @param tuv1
     * @param tuv2
     * @param mode
     * @return
     */
    public static int compareTime(ModifyDateComparable tuv1,
            ModifyDateComparable tuv2, int mode)
    {

        Date time1 = tuv1.getModifyDate();
        Date time2 = tuv2.getModifyDate();

        int result = time2.compareTo(time1);

        if (mode == LeverageOptions.PICK_OLDEST)
        {
            result = -result;
        }

        return result;
    }

    /**
     * This method compares the last modified time of the two Tuvs. The order of
     * the sort depends on the option the user chose.
     * 
     * @param tuv1
     * @param tuv2
     * @param leverageOptions
     * @return
     */
    public static int compareTime(ModifyDateComparable tuv1,
            ModifyDateComparable tuv2, LeverageOptions leverageOptions)
    {
        int mode = getDateMode(leverageOptions);
        return compareTime(tuv1, tuv2, mode);
    }

    public static int getDateMode(LeverageOptions leverageOptions)
    {
        if (leverageOptions == null)
        {
            return LeverageOptions.PICK_LATEST;
        }

        int mode;
        if (leverageOptions.isLatestLeveragingForReimport())
        {
            mode = LeverageOptions.PICK_LATEST;
        }
        else
        {
            mode = leverageOptions.getMultipleExactMatcheMode();
        }

        return mode;
    }

    public static boolean isIncontextMatch(Tuv srcTuv, List<TuvImpl> sourceTuvs,
            List targetTuvs, MatchTypeStatistics tuvMatchTypes,
            Vector<String> excludedItemTypes)
    {
        if (srcTuv == null)
            return false;
        
        try
        {
            int index = -1;

            for (int i = 0; i < sourceTuvs.size(); i++)
            {
                if (srcTuv.getId() == sourceTuvs.get(i).getId())
                {
                    index = i;
                    break;
                }
            }

            if (index == -1)
                return false;


            return isIncontextMatch(index, sourceTuvs, targetTuvs,
                    tuvMatchTypes, excludedItemTypes);
        }
        catch (Exception e)
        {
            logger.error(e);
        }

        return false;
    }

    /**
     * Checks the match type is in-context match or not.
     * 
     * <p>
     * Please note that the class of tuv may be <code>Tuv</code> or
     * <code>SegmentTmTuv</code>. The <code>p_targetTuvs</code> can be null If
     * the class of tuv is <code>SegmentTmTuv</code>.
     * 
     * @param index
     * @param p_sourceTuvs
     * @param p_targetTuvs
     * @param p_matchTypes
     * @param p_excludedItemTypes
     * @return
     */
    public static boolean isIncontextMatch(int index, List p_sourceTuvs,
            List p_targetTuvs, MatchTypeStatistics p_matchTypes,
            Vector<String> p_excludedItemTypes)
    {
        if (isSIDContextMatch(index, p_sourceTuvs, p_matchTypes))
        {
            return true;
        }
        
        if (isSidExistsAndNotEqual(p_sourceTuvs.get(index), p_matchTypes))
        {
            return false;
        }

        // Check current tuv is exact match.
        if (!isExactMatchLocalized(index, p_sourceTuvs, p_targetTuvs,
                p_matchTypes))
        {
            return false;
        }

        // Check previous tuv is exact matct.
        int previousIndex;
        for (previousIndex = index - 1; previousIndex >= 0; previousIndex--)
        {
            String tuType = getTuType(p_sourceTuvs.get(previousIndex));
            if (!p_excludedItemTypes.contains(tuType))
            {
                break;
            }
        }

        // No previous tuv.
        if (previousIndex < 0)
        {
            return false;
        }

        // Previous tuv is not exact match.
        if (!isExactMatch(previousIndex, p_sourceTuvs, p_matchTypes))
        {
            return false;
        }

        // Check next tuv is exact match.
        int nextIndex;
        for (nextIndex = index + 1; nextIndex < p_sourceTuvs.size(); nextIndex++)
        {
            String tuType = getTuType(p_sourceTuvs.get(previousIndex));
            if (!p_excludedItemTypes.contains(tuType))
            {
                break;
            }
        }

        // No next.
        if (nextIndex >= p_sourceTuvs.size())
        {
            return false;
        }

        // Next tuv is not exact match.
        if (!isExactMatch(nextIndex, p_sourceTuvs, p_matchTypes))
        {
            return false;
        }

        return true;
    }

    private static String getTuType(Object tuv)
    {
        if (tuv instanceof Tuv)
        {
            Tuv sourceTuv = (Tuv) tuv;
            return sourceTuv.getTu().getTuType();
        }
        else
        {
            SegmentTmTuv sourceTuv = (SegmentTmTuv) tuv;
            return sourceTuv.getTu().getType();
        }
    }

    public static boolean isSidContextMatch(Object o,
            MatchTypeStatistics p_matchTypes)
    {
        boolean result = false;
        if (isExactMatch(o, p_matchTypes))
        {
            String sid = null;
            String matchedSid = null;

            if (o instanceof Tuv)
            {
                Tuv sourceTuv = (Tuv) o;
                sid = sourceTuv.getSid();
                matchedSid = p_matchTypes
                        .getSid(sourceTuv.getId(), DUMMY_SUBID);
            }
            else if (o instanceof SegmentTmTuv)
            {
                SegmentTmTuv sourceTuv = (SegmentTmTuv) o;
                sid = sourceTuv.getSid();
                matchedSid = p_matchTypes
                        .getSid(sourceTuv.getId(), DUMMY_SUBID);
            }

            if (sid != null && sid.equals(matchedSid))
            {
                result = true;
            }
        }

        return result;
    }

    public static boolean isSIDContextMatch(int index, List p_sourceTuvs,
            MatchTypeStatistics p_matchTypes)
    {
        return isSidContextMatch(p_sourceTuvs.get(index), p_matchTypes);
    }
    
    /**
     * Check if they are Exact match but have different sid 
     * @param o
     * @param p_matchTypes
     * @return
     */
    public static boolean isSidExistsAndNotEqual(Object o,
            MatchTypeStatistics p_matchTypes)
    {
        boolean result = false;
        if (isExactMatch(o, p_matchTypes))
        {
            String sid = null;
            String matchedSid = null;

            if (o instanceof Tuv)
            {
                Tuv sourceTuv = (Tuv) o;
                sid = sourceTuv.getSid();
                matchedSid = p_matchTypes
                        .getSid(sourceTuv.getId(), DUMMY_SUBID);
            }
            else if (o instanceof SegmentTmTuv)
            {
                SegmentTmTuv sourceTuv = (SegmentTmTuv) o;
                sid = sourceTuv.getSid();
                matchedSid = p_matchTypes
                        .getSid(sourceTuv.getId(), DUMMY_SUBID);
            }

            if (sid != null && matchedSid != null
                    && !sid.equals(matchedSid)
                    && !"".equals(sid)
                    && !"".equals(matchedSid))
            {
                result = true;
            }
        }

        return result;
    }

    public static boolean isExactMatch(Object o,
            MatchTypeStatistics p_matchTypes)
    {
        int state = 0;

        if (o instanceof Tuv)
        {
            Tuv sourceTuv = (Tuv) o;
            state = p_matchTypes.getLingManagerMatchType(sourceTuv.getId(),
                    DUMMY_SUBID);
        }
        else if (o instanceof SegmentTmTuv)
        {
            SegmentTmTuv sourceTuv = (SegmentTmTuv) o;
            state = p_matchTypes.getLingManagerMatchType(sourceTuv.getId(),
                    DUMMY_SUBID);
        }

        return state == LeverageMatchLingManager.EXACT;
    }

    public static boolean isExactMatch(int index, List p_sourceTuvs,
            MatchTypeStatistics p_matchTypes)
    {
        Object o = p_sourceTuvs.get(index);
        long id;
        if (o instanceof Tuv)
        {
            Tuv sourceTuv = (Tuv) o;
            id = sourceTuv.getId();
        }
        else
        {
            SegmentTmTuv sourceTuv = (SegmentTmTuv) o;
            id = sourceTuv.getId();
        }

        int state = p_matchTypes.getLingManagerMatchType(id, DUMMY_SUBID);
        int matchType = p_matchTypes.getStatisticsMatchType(id, DUMMY_SUBID);

        return state == LeverageMatchLingManager.EXACT && matchType != 6;
    }

    /**
     * Exact match localized means the segment is same as matched segment. It
     * will be show not with green text format. And only this kind exact match
     * can be in-context match.
     * 
     * @param index
     * @param p_sourceTuvs
     * @param p_targetTuvs
     * @param p_matchTypes
     * @return
     */
    private static boolean isExactMatchLocalized(int index, List p_sourceTuvs,
            List p_targetTuvs, MatchTypeStatistics p_matchTypes)
    {
        Object o = p_sourceTuvs.get(index);
        long id;
        boolean isLocalized = false;
        if (o instanceof Tuv)
        {
            Tuv sourceTuv = (Tuv) o;
            id = sourceTuv.getId();
            Tuv targetTuv = (Tuv) p_targetTuvs.get(index);
            isLocalized = SegmentProtectionManager
                    .isTuvInProtectedState(targetTuv);
        }
        else
        {
            SegmentTmTuv sourceTuv = (SegmentTmTuv) o;
            id = sourceTuv.getId();
            isLocalized = p_matchTypes.isExactMatchLocalized(id, DUMMY_SUBID);
        }

        int state = p_matchTypes.getLingManagerMatchType(id, DUMMY_SUBID);
        int matchType = p_matchTypes.getStatisticsMatchType(id, DUMMY_SUBID);

        return state == LeverageMatchLingManager.EXACT && matchType != 6
                && isLocalized;
    }
}