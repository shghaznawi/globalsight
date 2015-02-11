package com.globalsight.ling.tm3.integration.segmenttm;

import static com.globalsight.ling.tm3.integration.segmenttm.SegmentTmAttribute.*;

import java.util.Map;

import com.globalsight.everest.tm.Tm;
import com.globalsight.ling.tm2.BaseTmTuv;
import com.globalsight.ling.tm2.leverage.LeverageMatches;
import com.globalsight.ling.tm2.leverage.LeverageOptions;
import com.globalsight.ling.tm2.leverage.LeveragedSegmentTu;
import com.globalsight.ling.tm2.leverage.LeveragedSegmentTuv;
import com.globalsight.ling.tm2.leverage.MatchState;
import com.globalsight.ling.tm3.core.TM3Attribute;
import com.globalsight.ling.tm3.core.TM3Event;
import com.globalsight.ling.tm3.core.TM3LeverageMatch;
import com.globalsight.ling.tm3.core.TM3LeverageResults;
import com.globalsight.ling.tm3.core.TM3MatchType;
import com.globalsight.ling.tm3.core.TM3Tm;
import com.globalsight.ling.tm3.core.TM3Tu;
import com.globalsight.ling.tm3.core.TM3Tuv;
import com.globalsight.ling.tm3.integration.GSTuvData;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.util.GlobalSightLocale;

/**
 * This code gathers up some of the clutter of leveraging
 * segments in TM3.
 */
// XXX This code would be simplified if TM3 could search across multiple TMs 
// in a single query.  
class Tm3Leverager {

    private static final GlobalSightCategory LOGGER =
        (GlobalSightCategory) GlobalSightCategory.getLogger(
                Tm3Leverager.class);
    
    static final int MAX_HITS = 10;
    
    private Map<BaseTmTuv, LeverageMatches> progress;
    private LeverageOptions leverageOptions;
    private Tm projectTm;
    private TM3Tm<GSTuvData> tm;
    private TM3Attribute typeAttr;
    private TM3Attribute formatAttr;
    private TM3Attribute sidAttr;
    private TM3Attribute translatableAttr;
    private TM3Attribute fromWsAttr;
    private TM3Attribute projectAttr;
    private TM3MatchType matchType;
    private GlobalSightLocale srcLocale;
    
    Tm3Leverager(Tm projectTm, TM3Tm<GSTuvData> tm, GlobalSightLocale srcLocale, LeverageOptions options,
            Map<BaseTmTuv, LeverageMatches> progress) {
        this.projectTm = projectTm;
        this.tm = tm;
        this.srcLocale = srcLocale;
        this.leverageOptions = options;
        this.progress = progress;
        
        initialize();
    }
    
    void initialize() {
        typeAttr = TM3Util.getAttr(tm, TYPE);
        formatAttr = TM3Util.getAttr(tm, FORMAT);
        sidAttr = TM3Util.getAttr(tm, SID);
        translatableAttr = TM3Util.getAttr(tm, TRANSLATABLE);
        fromWsAttr = TM3Util.getAttr(tm, FROM_WORLDSERVER);
        projectAttr = TM3Util.getAttr(tm, UPDATED_BY_PROJECT);
        
        matchType = leverageOptions.leverageOnlyExactMatches() ?
                TM3MatchType.EXACT : TM3MatchType.FALLBACK;
    }
    
    void leverageSegment(BaseTmTuv srcTuv, Map<TM3Attribute, Object> attrs) {
        LOGGER.debug("leverageSegment: " + srcTuv.toDebugString());
        TM3LeverageResults<GSTuvData> results = tm.findMatches(new GSTuvData(srcTuv),
                srcLocale, leverageOptions.getLeveragingLocales().getAllTargetLocales(), 
                attrs, matchType, leverageOptions.isMultiLingLeveraging(), 
                MAX_HITS, leverageOptions.getMatchThreshold());
        
        // NB in this conversion, we lose which tuv was matched, only which
        // tus; identical tus will later be coalesced by
        // leverageDataCenter.addLeverageResultsOfSegmentTmMatching
        // which tuv will globalsight pick as the match? probably random
        LeverageMatches lm = progress.get(srcTuv);
        if (lm == null) {
            lm = new LeverageMatches(srcTuv, leverageOptions);
            progress.put(srcTuv, lm);
        }
        
        int order = 0;
        for (TM3LeverageMatch<GSTuvData> match : results.getMatches()) {
            TM3Tu<GSTuvData> tu = match.getTu();
            LeveragedSegmentTu ltu = new LeveragedSegmentTu(tu.getId(), projectTm.getId(),
                    (String) tu.getAttribute(formatAttr),
                    (String) tu.getAttribute(typeAttr), true, srcLocale);
            ltu.setScore(match.getScore());
            ltu.setMatchState(getMatchState(leverageOptions, match.getScore()));
            ltu.setFromWorldServer((Boolean) tu.getAttribute(fromWsAttr));
            if ((Boolean) tu.getAttribute(translatableAttr)) {
                ltu.setTranslatable();
            }
            else {
                ltu.setLocalizable();
            }
            
            String sid = (String) tu.getAttribute(sidAttr);
            
            for (TM3Tuv<GSTuvData> tuv : tu.getAllTuv()) {
                LeveragedSegmentTuv ltuv = new LeveragedSegmentTuv(tuv.getId(), 
                        tuv.getContent().getData(), (GlobalSightLocale)tuv.getLocale());
                ltuv.setTu(ltu);
                ltuv.setOrgSid(srcTuv.getSid());
                ltuv.setSid(sid);
                ltuv.setOrder(order);
                              
                TM3Event latestEvent = tuv.getLatestEvent();
                TM3Event firstEvent = tuv.getFirstEvent();
                // XXX Are these even needed?  
                ltuv.setModifyDate(TM3Util.toTimestamp(latestEvent));
                ltuv.setModifyUser(latestEvent.getUsername());
                ltuv.setCreationDate(TM3Util.toTimestamp(firstEvent));
                ltuv.setCreationUser(firstEvent.getUsername());
                ltuv.setUpdatedProject((String) tu.getAttribute(projectAttr));
               
                // I'm leaving some fields blank, like 
                // - setExactMatchKey
                // - setOrgSegment
                // etc.
                // I am pretty sure these are not used with leveraged
                // segments.  This is a sign that the Tu/v interface
                // hierarchy needs cleaning up or splitting up -- 
                // since LeveragedTu/v inherits fields it has no 
                // need for.
                ltu.addTuv(ltuv);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Score " + ltu.getScore() + ": " + 
                        ltu.getFirstTuv(srcLocale));
            }
            lm.add(ltu);
            order++;
        }
    }
    
    // TODO: refactor with LeverageIterator.getSegmentTmMatchState
    private MatchState getMatchState(LeverageOptions options, int score)
    {
        MatchState state = MatchState.FUZZY_MATCH;

        if (score == 100) {
            state = MatchState.SEGMENT_TM_EXACT_MATCH;
        }
        else if (score < options.getMatchThreshold()) {
            state = MatchState.STATISTICS_MATCH;
        }
        return state;
    }
}