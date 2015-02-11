package com.globalsight.everest.edit.offline.xliff;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;

import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

import com.globalsight.diplomat.util.XmlUtil;
import com.globalsight.everest.comment.Comment;
import com.globalsight.everest.edit.offline.AmbassadorDwUpConstants;
import com.globalsight.everest.edit.offline.AmbassadorDwUpException;
import com.globalsight.everest.edit.offline.XliffConstants;
import com.globalsight.everest.edit.offline.download.DownloadParams;
import com.globalsight.everest.edit.offline.page.OfflinePageData;
import com.globalsight.everest.edit.offline.page.OfflineSegmentData;
import com.globalsight.everest.integration.ling.tm2.LeverageMatch;
import com.globalsight.everest.jobhandler.Job;
import com.globalsight.everest.projecthandler.ProjectTmTuvT;
import com.globalsight.everest.projecthandler.TranslationMemoryProfile;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.taskmanager.Task;
import com.globalsight.everest.taskmanager.TaskImpl;
import com.globalsight.everest.tda.TdaHelper;
import com.globalsight.everest.tuv.TuImpl;
import com.globalsight.everest.tuv.Tuv;
import com.globalsight.everest.webapp.pagehandler.tasks.TaskHelper;
import com.globalsight.everest.workflow.Activity;
import com.globalsight.everest.workflowmanager.Workflow;
import com.globalsight.ling.common.DiplomatBasicParserException;
import com.globalsight.ling.common.RegExException;
import com.globalsight.ling.common.Text;
import com.globalsight.ling.docproc.IFormatNames;
import com.globalsight.ling.docproc.extractor.xliff.Extractor;
import com.globalsight.ling.docproc.extractor.xliff.XliffAlt;
import com.globalsight.ling.tm2.leverage.Leverager;
import com.globalsight.ling.tw.internal.InternalTextUtil;
import com.globalsight.ling.tw.internal.XliffInternalTag;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.machineTranslation.promt.ProMTProxy;
import com.globalsight.persistence.hibernate.HibernateUtil;
import com.globalsight.terminology.termleverager.TermLeverageMatchResult;
import com.globalsight.util.StringUtil;
import com.globalsight.util.edit.EditUtil;
import com.globalsight.util.edit.GxmlUtil;
import com.globalsight.util.edit.SegmentUtil;

public class ListViewWorkXLIFFWriter extends XLIFFWriterUnicode
{
    static private final GlobalSightCategory logger = (GlobalSightCategory) GlobalSightCategory
            .getLogger(ListViewWorkXLIFFWriter.class);

    static public final String WORK_DOC_TITLE = "GlobalSight Extracted List-View Export";
    static private final String TERMINOLOGY_PATTERN = "<alt-trans origin=\"terminology\">\r\n"
            + "<source>{0}</source>\r\n"
            + "<target>{1}</target>\r\n"
            + "</alt-trans>\r\n";
    static private final String TM_PATTERN = "<alt-trans match-quality=\"{0}\" origin=\"TM\">\r\n"
            + "<source>{1}</source>\r\n"
            + "<target>{2}</target>\r\n"
            + "</alt-trans>\r\n";
    // for worldserver export
    static private final String TM_PATTERN1 = "<alt-trans match-quality=\"{0}\" origin=\"TM\">"
            + "<source>{1}</source>" + "<target>{2}</target>" + "</alt-trans>";

    static private final String PHASE = "<phase-group>\r\n"
            + "<phase phase-name=\"{0}\" process-name=\"{1}\"/>\r\n"
            + "</phase-group>\r\n";

    public ListViewWorkXLIFFWriter()
    {
    }

    protected void writeXlfDocBody(boolean isTmx, boolean editAll)
            throws AmbassadorDwUpException
    {
        OfflineSegmentData osd = null;

        for (ListIterator it = m_page.getSegmentIterator(); it.hasNext();)
        {
            osd = (OfflineSegmentData) it.next();

            try
            {
                writeTranslationUnit(osd, m_page, isTmx, editAll);
            }
            catch (Exception ex)
            {
                throw new AmbassadorDwUpException(ex);
            }
        }
    }

    protected void writeXlfDocHeader(DownloadParams p_downloadParams)
            throws IOException, AmbassadorDwUpException
    {
        writeDocumentHeader(p_downloadParams);
    }

    private void writeAltTranslationUnit(OfflineSegmentData p_osd)
            throws IOException
    {
        writeTmMatch(p_osd);
        writeTerminology(p_osd);
    }

    private String repairForAltTrans(String segment)
    {
        try
        {
            new SAXReader().read(new StringReader("<target>" + segment
                    + "</target>"));
        }
        catch (DocumentException e)
        {
            return EditUtil.encodeXmlEntities(segment);
        }

        return segment;
    }

    private String processInternalText(String segment)
    {
        InternalTextUtil util = new InternalTextUtil(new XliffInternalTag());
        try
        {
            return util.preProcessInternalText(segment).getSegment();
        }
        catch (DiplomatBasicParserException e)
        {
            logger.error(e);
        }

        return segment;
    }

    /*
     * If the osd is null, this method is called by the PageTemplate, use for
     * writing leverage match results into exported xlif file
     */
    public String getAltByMatch(LeverageMatch leverageMatch,
            OfflineSegmentData osd)
    {
        String altTrans = new String();

        //altFlag to flag the leverageMatch form alt-trans
        int altFlag = -100;
        String score = StringUtil.formatPercent(leverageMatch.getScoreNum(), 2);

        /**
         * Retrieve the source segment from TM if the leverage match is from
         * local TM. Default is string in source TUV.
         */
        long matchedTmTuvId = leverageMatch.getMatchedTuvId();

        try
        {
            Tuv sourceTuv = ServerProxy.getTuvManager().getTuvForSegmentEditor(
                    leverageMatch.getOriginalSourceTuvId());
            String targetLocal = new String();
            String sourceLocal = new String();
            boolean isFromXliff = false;
            
            if(leverageMatch.getProjectTmIndex() != altFlag) {
                sourceLocal = 
                    ServerProxy.getLocaleManager().getLocaleById
                        (sourceTuv.getLocaleId()).getLocaleCode();
                targetLocal = leverageMatch.getTargetLocale().getLocaleCode();
                
                if (sourceTuv != null
                        && sourceTuv.getTu().getDataType().equals(
                                IFormatNames.FORMAT_XLIFF))
                {
                    isFromXliff = true;
                }
            }
            
            String sourceStr = leverageMatch.getMatchedOriginalSource();
            String targetStr = leverageMatch.getLeveragedTargetString();
            
            if (leverageMatch.getProjectTmIndex() == Leverager.TDA_TM_PRIORITY)
            {
                //not do anything
            }
            else if (leverageMatch.getProjectTmIndex() == Leverager.REMOTE_TM_PRIORITY)
            {
                sourceStr = GxmlUtil.stripRootTag(sourceStr);
            }
            else if(leverageMatch.getProjectTmIndex() == Leverager.MT_PRIORITY) {
                if (isFromXliff)
                {
                    sourceStr = SegmentUtil.restoreSegment(sourceStr, sourceLocal);
                    targetStr = SegmentUtil.restoreSegment(targetStr, targetLocal);
                }
                else {
                    sourceStr = GxmlUtil.stripRootTag(sourceStr);
                }
            }
            else if(leverageMatch.getProjectTmIndex() == altFlag) {
                //for offline download, write out the alt-trans
                sourceStr = GxmlUtil.stripRootTag(sourceStr);
            }
            else if (leverageMatch.getProjectTmIndex() == Leverager.XLIFF_PRIORITY)
            {
                TuImpl tu = (TuImpl) sourceTuv.getTu();
                String xliffTarget = tu.getXliffTargetGxml().getTextValue();

                if (xliffTarget != null && Text.isBlank(xliffTarget))
                {
                    // is populate from alt-trans
                    sourceStr = GxmlUtil.stripRootTag(leverageMatch
                            .getMatchedOriginalSource());
                    sourceStr = EditUtil.decodeXmlEntities(sourceStr);
                    targetStr = EditUtil.decodeXmlEntities(targetStr);
                }
                else
                {
                    // is populate by the xliff target, need to return to the
                    // original
                    sourceStr = SegmentUtil.restoreSegment(sourceStr, sourceLocal);
                    targetStr = SegmentUtil.restoreSegment(targetStr, targetLocal);
                }
               
            }
            else
            {
                if (matchedTmTuvId == -1)
                {
                    if (osd == null)
                    {
                        sourceStr = SegmentUtil.restoreSegment(sourceStr, sourceLocal);
                        targetStr = SegmentUtil.restoreSegment(targetStr, targetLocal);
                    }
                    else
                    {
                        sourceStr = osd
                                .getDisplaySourceTextWithNewLinebreaks(String
                                        .valueOf(NORMALIZED_LINEBREAK));
                    }
                }
                else
                {
                    ProjectTmTuvT proTmTuvT = HibernateUtil.get(
                            ProjectTmTuvT.class, matchedTmTuvId);
                    String srcTmTuvString = proTmTuvT.getTu().getSourceTuv()
                            .getSegmentString();
                    sourceStr = GxmlUtil.stripRootTag(srcTmTuvString);
                }
            }

            sourceStr = processInternalText(sourceStr);
            targetStr = processInternalText(targetStr);

            if (osd == null)
            {
                altTrans = MessageFormat.format(TM_PATTERN1, score,
                        repairForAltTrans(sourceStr), repairForAltTrans(targetStr));
            }
            else
            {
                altTrans = MessageFormat.format(TM_PATTERN, score,
                        repairForAltTrans(sourceStr), repairForAltTrans(targetStr));
            }

            // If target TUV is never changed, adjust the "origin".
            int projectTmIndex = leverageMatch.getProjectTmIndex();
            if (projectTmIndex < -1)
            {
                if (projectTmIndex == Leverager.MT_PRIORITY)
                {
                    String origin = leverageMatch.getMtName();

                    if ("".equals(origin) || origin == null)
                    {
                        origin = "MT";
                    }

                    altTrans = altTrans.replace("origin=\"TM\"", "origin=\""
                            + origin + "\"");
                }
                else if (projectTmIndex == Leverager.XLIFF_PRIORITY)
                {
                    TuImpl tu = (TuImpl) sourceTuv.getTu();
                    if (tu != null && tu.isXliffTranslationMT() && osd == null)
                    {
                        String temp = Extractor.IWS_TRANSLATION_MT;
                        altTrans = altTrans.replace("origin=\"TM\"",
                                "origin=\"" + temp + "\"");
                    }
                    else
                    {
                        altTrans = altTrans.replace("origin=\"TM\"",
                                "origin=\"XLF\"");
                    }
                }
                else if (projectTmIndex == Leverager.REMOTE_TM_PRIORITY)
                {
                    altTrans = altTrans.replace("origin=\"TM\"",
                            "origin=\"REMOTE_TM\"");
                }
                else if (projectTmIndex == Leverager.TDA_TM_PRIORITY)
                {
                    altTrans = altTrans.replace("origin=\"TM\"",
                            "origin=\"TDA\"");
                }
                else if (projectTmIndex == Leverager.PO_TM_PRIORITY)
                {
                    altTrans = altTrans.replace("origin=\"TM\"",
                            "origin=\"PO\"");
                }
                else if (projectTmIndex == altFlag)
                {
                    altTrans = altTrans.replace("origin=\"TM\"",
                            "origin=\"XLF Source\"");
                }
            }
        }
        catch (Exception he)
        {
        }

        return altTrans;
    }

    private void writeTmMatch(OfflineSegmentData osd) throws IOException
    {
        List<LeverageMatch> list = osd.getOriginalFuzzyLeverageMatchList();
        List<LeverageMatch> list2 = new ArrayList();

        if (list != null)
        {
            list2.addAll(list);
        }

        Tuv tuv = ServerProxy.getTuvManager().getTuvForSegmentEditor(
                osd.getTrgTuvId());
        Set xliffAltSet = tuv.getXliffAlt();
        int altFlag = -100;

        if (xliffAltSet != null)
        {
            Iterator it = xliffAltSet.iterator();

            while (it.hasNext())
            {
                XliffAlt alt = (XliffAlt) it.next();
                LeverageMatch lm = new LeverageMatch();
                String str = EditUtil.decodeXmlEntities(alt.getSourceSegment());
                lm.setMatchedOriginalSource(str);
                lm.setMatchedText(EditUtil.decodeXmlEntities(alt.getSegment()));
                float score = (float) TdaHelper
                        .PecentToDouble(alt.getQuality());

                lm.setScoreNum(score);
                lm.setProjectTmIndex(altFlag);
                list2.add(lm);
            }
        }

        if (list2 != null)
        {
            LeverageMatch.orderMatchResult(list2);

            for (int i = 0; i < list2.size(); i++)
            {
                LeverageMatch leverageMatch = list2.get(i);
                m_outputStream.write(getAltByMatch(leverageMatch, osd));
            }
        }
    }

    private void writeTerminology(OfflineSegmentData osd) throws IOException
    {
        List<TermLeverageMatchResult> matchs = osd.getTermLeverageMatchList();
        if (matchs == null)
            return;

        for (TermLeverageMatchResult result : matchs)
        {
            String src = result.getSourceTerm();
            String tag = result.getFirstTargetTerm();
            while (tag != null)
            {
                String altTrans = MessageFormat.format(TERMINOLOGY_PATTERN,
                        repairForAltTrans(src), repairForAltTrans(tag));
                m_outputStream.write(altTrans);
                tag = result.getNextTargetTerm();
            }
        }
    }

    private boolean isExtractMatch(OfflineSegmentData data)
    {
        return "DO NOT TRANSLATE OR MODIFY (Locked).".equals(data
                .getDisplayMatchType());
    }

    private boolean isPenaltiedExtarctMatch(OfflineSegmentData data)
    {
        return "Exact Match.".equals(data.getDisplayMatchType());
    }

    private boolean isInContextMatch(OfflineSegmentData data)
    {
        return "Context Exact Match".equals(data.getDisplayMatchType());
    }

    private boolean isFuzzyMatch(OfflineSegmentData data)
    {
        return data.getMatchTypeId() == AmbassadorDwUpConstants.MATCH_TYPE_FUZZY;
    }

    private String getState(OfflineSegmentData data)
    {
        String state = "new";

        if (isInContextMatch(data) || isExtractMatch(data))
        {
            state = "final";
        }
        else if (isPenaltiedExtarctMatch(data))
        {
            state = "translated";
        }
        else if (isFuzzyMatch(data))
        {
            state = "needs-review-translation";
        }

        return state;
    }

    private void writeTranslationUnit(OfflineSegmentData p_osd,
            OfflinePageData m_page, boolean isTmx, boolean editAll)
            throws IOException, RegExException
    {
        String srcSegment;
        String trgSegment;
        String dataType = p_osd.getDisplaySegmentFormat();

        // Special treatment for HTML.
        if (("html").equalsIgnoreCase(dataType))
        {
            if (!("text").equals(dataType))
            {
                dataType = p_osd.getSegmentType();
            }
        }

        InternalTextUtil util = new InternalTextUtil(new XliffInternalTag());

        srcSegment = p_osd.getDisplaySourceTextWithNewLinebreaks(String
                .valueOf(NORMALIZED_LINEBREAK));
        try
        {
            srcSegment = util.preProcessInternalText(srcSegment).getSegment();

            if (("xlf").equalsIgnoreCase(dataType))
            {
                String sourceLocal = ServerProxy.getLocaleManager()
                        .getLocaleById(p_osd.getSourceTuv().getLocaleId())
                        .getLocaleCode();
                srcSegment = SegmentUtil.restoreSegment(p_osd.getSourceTuv()
                        .getGxml(), sourceLocal);
            }
        }
        catch (DiplomatBasicParserException e)
        {
            logger.error(e);
        }

        trgSegment = p_osd.getDisplayTargetTextWithNewLineBreaks(String
                .valueOf(NORMALIZED_LINEBREAK));
        try
        {
            trgSegment = util.preProcessInternalText(trgSegment).getSegment();

            if (("xlf").equalsIgnoreCase(dataType))
            {
                String targetLocal = ServerProxy.getLocaleManager()
                        .getLocaleById(p_osd.getTargetTuv().getLocaleId())
                        .getLocaleCode();
                trgSegment = SegmentUtil.restoreSegment(p_osd.getTargetTuv()
                        .getGxml(), targetLocal);
            }
        }
        catch (DiplomatBasicParserException e)
        {
            logger.error(e);
        }

        // write ID and mactch type
        if (p_osd.getDisplaySegmentID() != null)
        {
            m_outputStream.write("<trans-unit id=");
            m_outputStream.write(str2DoubleQuotation(String.valueOf(p_osd
                    .getDisplaySegmentID())));
            m_outputStream.write(" translate=");
            if (!editAll && (isInContextMatch(p_osd) || isExtractMatch(p_osd)))
            {
                m_outputStream.write(str2DoubleQuotation("no"));
            }
            else
            {
                m_outputStream.write(str2DoubleQuotation("yes"));
            }

            m_outputStream.write(">");
            m_outputStream.write(m_strEOL);
        }

        // write Source
        if (srcSegment != null)
        {
            m_outputStream.write("<source>");
            m_outputStream.write(srcSegment);
            m_outputStream.write("</source>");
            m_outputStream.write(m_strEOL);
        }

        // write Target, set state in target node
        if (trgSegment != null)
        {
            m_outputStream.write("<target");
            m_outputStream.write(" state=");
            m_outputStream.write(str2DoubleQuotation(getState(p_osd)));
            m_outputStream.write(">");
            m_outputStream.write(trgSegment);
            m_outputStream.write("</target>");
            m_outputStream.write(m_strEOL);
        }

        if (!isTmx)
        {
            // if (p_osd.getOriginalFuzzyLeverageMatchList() != null)
            // {
            writeAltTranslationUnit(p_osd);
            // }
        }

        m_outputStream.write("</trans-unit>");
        m_outputStream.write(m_strEOL);

    }

    /**
     * Writes the document header - encoding, formats, languages etc.
     */
    private void writeDocumentHeader(DownloadParams p_downloadParams)
            throws IOException, AmbassadorDwUpException
    {
        String fileName = str2DoubleQuotation(XmlUtil.escapeString(m_page
                .getPageName()));
        m_outputStream.write("<file ");

        if (m_page.getPageName() != null)
        {
            m_outputStream.write("original=" + fileName);
            m_outputStream.write(m_space);
        }

        String sLocale = m_page.getSourceLocaleName();
        if (sLocale != null)
        {
            sLocale = changeLocaleToXlfFormat(sLocale);
            m_outputStream.write("source-language="
                    + str2DoubleQuotation(sLocale));
            m_outputStream.write(m_space);
        }

        String tLocale = m_page.getTargetLocaleName();
        if (tLocale != null)
        {
            tLocale = changeLocaleToXlfFormat(tLocale);
            m_outputStream.write("target-language="
                    + str2DoubleQuotation(tLocale));
            m_outputStream.write(m_space);
        }

        // m_outputStream.write("tool="
        // + str2DoubleQuotation("Transware Ambassador"));
        // m_outputStream.write(m_space);

        if (m_page.getDocumentFormat() != null)
        {
            m_outputStream.write("datatype="
                    + str2DoubleQuotation(m_page.getDocumentFormat()));
        }

        m_outputStream.write(">");
        m_outputStream.write(m_strEOL);
        writeAnnotation(p_downloadParams);
        m_outputStream.write("<body>");
        m_outputStream.write(m_strEOL);

    }

    private void writePhase(DownloadParams p_downloadParams) throws IOException
    {
        Task task = getTask(p_downloadParams);
        String name = task.getTaskDisplayName();
        String typeName = "Translation";

        try
        {
            Activity act = ServerProxy.getJobHandler().getActivity(
                    task.getTaskName());

            int type = act.getActivityType();

            if (Activity.TYPE_REVIEW == type)
            {
                typeName = "Review";
            }
            else if (TaskImpl.TYPE_REVIEW_EDITABLE == type)
            {
                typeName = "Review Editable";
            }
        }
        catch (Exception e)
        {
            logger.error(e);
            throw new AmbassadorDwUpException(e);
        }

        String phase = MessageFormat.format(PHASE, name, typeName);
        m_outputStream.write(phase);
    }

    private void writeAnnotation(DownloadParams p_downloadParams)
            throws IOException, AmbassadorDwUpException
    {
        String acceptTaskTime = getAcceptTaskTime(getTask(p_downloadParams));

        m_outputStream.write("<header>");
        m_outputStream.write(m_strEOL);

        writePhase(p_downloadParams);

        m_outputStream.write("<note>");
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("GlobalSight Download File");
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Activity Type:");
        m_outputStream.write(p_downloadParams.getActivityType());
        m_outputStream.write(m_strEOL);

        if (p_downloadParams.getUser() != null)
        {
            m_outputStream.write(XliffConstants.HASH_MARK);
            m_outputStream.write("User name:");
            m_outputStream.write(p_downloadParams.getUser().getUserId());
            m_outputStream.write(m_strEOL);
        }
        else
        {
            if (p_downloadParams.getAutoActionNodeEmail() != null)
            {
                m_outputStream.write(XliffConstants.HASH_MARK);
                m_outputStream.write("User name:");

                for (int x = 0; x < p_downloadParams.getAutoActionNodeEmail()
                        .size(); x++)
                {
                    String email = p_downloadParams.getAutoActionNodeEmail()
                            .get(x).toString();
                    email = email.replace("<", " - ").replace(">", "");
                    m_outputStream.write(email);

                    if (x < p_downloadParams.getAutoActionNodeEmail().size() - 1)
                    {
                        m_outputStream.write(",");
                    }
                }

                m_outputStream.write(m_strEOL);
            }
        }

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Accept time:");
        m_outputStream.write(acceptTaskTime);
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Encoding:");
        // m_outputStream.write(m_page.getEncoding());
        m_outputStream.write(XLIFF_ENCODING);
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Document Format:");
        m_outputStream.write(m_page.getDocumentFormat());
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Placeholder Format:");
        m_outputStream.write(m_page.getPlaceholderFormat());
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Source Locale:");
        m_outputStream.write(changeLocaleToXlfFormat(m_page
                .getSourceLocaleName()));
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Target Locale:");
        m_outputStream.write(changeLocaleToXlfFormat(m_page
                .getTargetLocaleName()));
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Page ID:");
        m_outputStream.write(m_page.getPageId());
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Workflow ID:");
        m_outputStream.write(m_page.getWorkflowId());
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Task ID:");
        m_outputStream.write(m_page.getTaskId());
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Exact Match word count:");
        m_outputStream.write(m_page.getExactMatchWordCountAsString());
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Fuzzy Match word count:");
        m_outputStream.write(m_page.getFuzzyMatchWordCountAsString());
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("No Match word count:");
        m_outputStream.write(m_page.getNoMatchWordCountAsString());
        m_outputStream.write(m_strEOL);

        Workflow wf = ServerProxy.getWorkflowManager().getWorkflowById(
                Long.parseLong(m_page.getWorkflowId()));
        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("In-Context Match word count:");
        m_outputStream.write(wf.getInContextMatchWordCount() + "");
        m_outputStream.write(m_strEOL);

        m_outputStream.write(XliffConstants.HASH_MARK);
        m_outputStream.write("Edit all:");
        m_outputStream.write(m_page.getDisplayDownloadEditAll());
        m_outputStream.write(m_strEOL);

        if (p_downloadParams.getJob() != null)
        {
            m_outputStream.write(XliffConstants.HASH_MARK);
            m_outputStream.write("GlobalSight TM Profile id:");
            TranslationMemoryProfile tmprofile = p_downloadParams.getJob()
                    .getL10nProfile().getTranslationMemoryProfile();
            m_outputStream.write(tmprofile.getId() + "");
            m_outputStream.write(m_strEOL);

            m_outputStream.write(XliffConstants.HASH_MARK);
            m_outputStream.write("GlobalSight TM Profile name:");
            m_outputStream.write(tmprofile.getName());
            m_outputStream.write(m_strEOL);

            m_outputStream.write(XliffConstants.HASH_MARK);
            m_outputStream.write("GlobalSight Termbase:");
            m_outputStream.write(p_downloadParams.getJob().getL10nProfile()
                    .getProject().getTermbaseName());
            m_outputStream.write(m_strEOL);
        }

        m_outputStream.write("</note>");
        m_outputStream.write(m_strEOL);

        // writeUserComments(p_downloadParams);

        m_outputStream.write("</header>");
        m_outputStream.write(m_strEOL);

    }

    @SuppressWarnings("unused")
    private void writeUserComments(DownloadParams p_downloadParams)
            throws IOException, AmbassadorDwUpException
    {
        List commentLists = getComments(getTask(p_downloadParams));
        String comments = "";
        if (commentLists == null)
        {
            comments = "No user comments";
        }
        else
        {
            for (int i = 0; i < commentLists.size(); i++)
            {
                commentLists.get(i);
            }

        }

        m_outputStream.write("<note>");
        m_outputStream.write(m_strEOL);
        m_outputStream.write(comments);
        m_outputStream.write(m_strEOL);
        m_outputStream.write("</note>");
        m_outputStream.write(m_strEOL);

    }

    @SuppressWarnings("unchecked")
    private List getComments(Task task)
    {
        List<Comment> comments = new ArrayList<Comment>();
        Workflow wf = task.getWorkflow();
        Job job = wf.getJob();
        Iterator workflows = job.getWorkflows().iterator();
        while (workflows.hasNext())
        {
            Workflow t_wf = (Workflow) workflows.next();
            Hashtable tasks = t_wf.getTasks();
            for (Iterator i = tasks.values().iterator(); i.hasNext();)
            {
                Task t = (Task) i.next();
                comments.addAll(t.getTaskComments());
            }
        }
        return comments;
    }

    private String getAcceptTaskTime(Task task)
    {
        DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        return dateFormat.format(task.getEstimatedAcceptanceDate());
    }

    private Task getTask(DownloadParams p_downloadParams)
    {
        Task task = null;
        long taskId = Long.parseLong(p_downloadParams.getTaskID());
        task = TaskHelper.getTask(taskId);
        return task;
    }

    // parse string to "string"
    private String str2DoubleQuotation(String str)
    {
        String result = null;
        result = new StringBuffer().append("\"").append(str).append("\"")
                .toString();
        return result;
    }

    public void write(OfflinePageData p_page, OutputStream p_outputStream,
            Locale p_uiLocale) throws IOException, AmbassadorDwUpException
    {
        // TODO Auto-generated method stub

    }
}