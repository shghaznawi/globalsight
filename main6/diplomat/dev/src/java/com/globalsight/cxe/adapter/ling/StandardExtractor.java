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
package com.globalsight.cxe.adapter.ling;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.globalsight.cxe.adapter.adobe.AdobeHelper;
import com.globalsight.cxe.adapter.adobe.InddRuleHelper;
import com.globalsight.cxe.adapter.idml.IdmlRuleHelper;
import com.globalsight.cxe.adapter.msoffice.OfficeXmlRuleHelper;
import com.globalsight.cxe.adapter.openoffice.OpenOfficeHelper;
import com.globalsight.cxe.adapter.openoffice.OpenOfficeRuleHelper;
import com.globalsight.cxe.engine.util.FileUtils;
import com.globalsight.cxe.entity.fileprofile.FileProfileImpl;
import com.globalsight.cxe.entity.filterconfiguration.FilterConstants;
import com.globalsight.cxe.entity.filterconfiguration.FilterHelper;
import com.globalsight.cxe.entity.filterconfiguration.MSOffice2010Filter;
import com.globalsight.cxe.entity.filterconfiguration.OpenOfficeFilter;
import com.globalsight.cxe.entity.segmentationrulefile.SegmentationRuleFile;
import com.globalsight.cxe.entity.segmentationrulefile.SegmentationRuleFileValidator;
import com.globalsight.cxe.entity.xmlrulefile.XmlRuleFile;
import com.globalsight.cxe.message.CxeMessage;
import com.globalsight.cxe.message.CxeMessageType;
import com.globalsight.cxe.message.FileMessageData;
import com.globalsight.cxe.message.MessageData;
import com.globalsight.cxe.message.MessageDataFactory;
import com.globalsight.cxe.message.MessageDataReader;
import com.globalsight.diplomat.util.Logger;
import com.globalsight.diplomat.util.database.ConnectionPool;
import com.globalsight.diplomat.util.database.ConnectionPoolException;
import com.globalsight.everest.aligner.AlignerExtractor;
import com.globalsight.everest.foundation.L10nProfile;
import com.globalsight.everest.projecthandler.TranslationMemoryProfile;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.util.system.SystemConfiguration;
import com.globalsight.ling.common.XmlEntities;
import com.globalsight.ling.docproc.AbstractExtractor;
import com.globalsight.ling.docproc.DiplomatAPI;
import com.globalsight.ling.docproc.DiplomatSegmenterException;
import com.globalsight.ling.docproc.DiplomatWordCounterException;
import com.globalsight.ling.docproc.DiplomatWriter;
import com.globalsight.ling.docproc.DocumentElement;
import com.globalsight.ling.docproc.EFInputData;
import com.globalsight.ling.docproc.ExtractorException;
import com.globalsight.ling.docproc.ExtractorRegistryException;
import com.globalsight.ling.docproc.IFormatNames;
import com.globalsight.ling.docproc.LocalizableElement;
import com.globalsight.ling.docproc.Output;
import com.globalsight.ling.docproc.SegmentNode;
import com.globalsight.ling.docproc.SkeletonElement;
import com.globalsight.ling.docproc.TranslatableElement;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.persistence.hibernate.HibernateUtil;
import com.globalsight.util.edit.GxmlUtil;

/**
 * StandardExtractor is a helper class for the LingAdapter.
 * 
 * <p>
 * The StandardExtractor is a wrapper for calls made to the DiplomatAPI for
 * extracting any of the known formats as documented in the javadoc for
 * DiplomatAPI.
 */
public class StandardExtractor
{
    // ////////////////////////////////////
    // Private Members //
    // ////////////////////////////////////

    private GlobalSightCategory m_logger;
    private String m_locale = null;
    private String m_encoding = null;
    private String m_formatType = null;
    private String m_formatName = null;
    private String m_ruleFile = null;
    private String m_displayName = "";
    private String m_fileProfile = null;
    private CxeMessage m_cxeMessage = null;
    private String[] m_errorArgs = new String[2];
    // For PPT issue
    private String m_bullets_MsOffice = null;
    // For Adobe Indesign Issue
    private int m_docPageNum;
    private int m_docPageCount;
    // for open office
    private String m_office_unPara = null;
    private String m_office_unChar = null;
    private String m_xlsx_numStyleIds = null;
    private String m_xlsx_hiddenSharedSI = null;
    private String m_xlsx_sheetHiddenCell = null;

//    private static final String SQL_SELECT_RULE = "SELECT RULE_TEXT FROM FILE_PROFILE, XML_RULE"
//            + " WHERE FILE_PROFILE.ID=?"
//            + " and XML_RULE.ID=FILE_PROFILE.XML_RULE_ID";
    
    private static final String SQL_SELECT_RULE = "select xr.rule_text from " +
            "xml_rule xr, xml_rule_filter xrf, file_profile " +
            "fp where xr.id = xrf.xml_rule_id and xrf.id = " +
            "fp.filter_id and fp.filter_table_name='xml_rule_filter' and fp.id = ?";
    
    public static final String m_tag_start = "&lt;AllYourBaseAreBelongToUs&gt;";
    public static final String m_tag_end = "&lt;/AllYourBaseAreBelongToUs&gt;";
    public static final String m_tag_amp = "AmpersandOfGS";

    /**
     * Creates an Extractor
     * 
     * @param p_logger
     *            a logger to use
     * @param p_cxeMessage
     */
    StandardExtractor(GlobalSightCategory p_logger, CxeMessage p_cxeMessage)
    {
        m_logger = p_logger;
        m_cxeMessage = p_cxeMessage;
    }

    /**
     * Extracts the given content held in the MessageData and returns a
     * MessageData containing GXML
     * 
     * @return MessageData corresponding to new GXML
     * @exception LingAdapterException
     */
    MessageData extract() throws LingAdapterException
    {
        parseEventFlowXml();
        String alignerExtractorName = (String) m_cxeMessage.getParameters()
                .get("AlignerExtractor");
        AlignerExtractor alignerExtractor = AlignerExtractor
                .getAlignerExtractor(alignerExtractorName);

        if (alignerExtractor == null)
        {
            m_logger.debug("aligner extractor is null - normal extraction");
            queryRuleFile();
        }
        else
        {
            m_logger.debug("aligner extractor is not null - aligner extraction");
            XmlRuleFile ruleFile = alignerExtractor.getXmlRule();
            if (ruleFile != null)
                m_ruleFile = ruleFile.getRuleText();
        }

        try
        {            
            return createMessageData();
        }
        catch (Exception e)
        {
            m_logger.error("Failed to extract " + m_displayName, e);
            throw new LingAdapterException("Extraction", m_errorArgs, e);
        }
    }
    
    private FileMessageData createMessageData() throws Exception
    {
        long fileSizeBytes = m_cxeMessage.getMessageData().getSize();
        m_logger.info("Extracting: " + m_displayName + ", size: " + fileSizeBytes);
        String gxml = null;
        if (m_cxeMessage.getMessageType().getValue() == CxeMessageType.PRSXML_IMPORTED_EVENT)
            gxml = extractWithPrsXmlExtractor();
        else
            gxml = extractWithLingAPI();

        gxml = fixGxml(gxml);

        FileMessageData fmd = MessageDataFactory.createFileMessageData();
        OutputStreamWriter osw = new OutputStreamWriter(fmd
                .getOutputStream(), "UTF8");
        osw.write(gxml, 0, gxml.length());
        osw.close();
        m_logger.info("Done Extracting: " + m_displayName
                + ", result size: " + gxml.length());
        Logger.writeDebugFile("lae_gxml.xml", gxml);
        m_cxeMessage.setDeleteMessageData(true);
        
        return fmd;
    }

    private String fixGxml(String p_gxml)
    {
        if (m_formatType.equals(DiplomatAPI.FORMAT_WORD_HTML))
        {
            if (p_gxml != null && !"".equals(p_gxml.trim()))
            {
                // Remove the tag <title>XX</title> in the gxml to resolve the
                // Fragmented markup in RTF document results in empty export
                // issue.
                String regexStr = "&lt;title&gt;.*&lt;/title&gt;";
                p_gxml = p_gxml.replaceFirst(regexStr, "");
                // Removes all spell error tags and gram error tags added by
                // word.
                p_gxml = GxmlUtil.moveTages(p_gxml,
                        "&lt;span class=SpellE&gt;", "&lt;/span&gt;");
                p_gxml = GxmlUtil.moveTages(p_gxml, "&lt;span class=GramE&gt;",
                        "&lt;/span&gt;");
            }
        } else if (m_formatType.equals(DiplomatAPI.FORMAT_EXCEL_HTML) && !m_formatName.contains("2003")) {
            if (p_gxml != null && !"".equals(p_gxml.trim())) {
                //GBS-1016, Added by Vincent Yan 2010/07/02
                //Add some xml codes to make the outputting excel file can be shown gridlines
                String regexStr = "&lt;/head&gt;";
                int index = p_gxml.indexOf(regexStr);
                if (index > 0) {
                    String preStr = p_gxml.substring(0, index-1);
                    String lastStr = p_gxml.substring(index);
                    StringBuffer tmp = new StringBuffer();
                    tmp.append("&lt;!--[if gte mso 9]&gt;\n");
                    tmp.append("\t&lt;xml&gt;\n");
                    tmp.append("\t\t&lt;x:ExcelWorkbook&gt;\n");
                    tmp.append("\t\t\t&lt;x:ExcelWorksheets&gt;\n");
                    tmp.append("\t\t\t\t&lt;x:ExcelWorksheet&gt;\n");
                    tmp.append("\t\t\t\t\t&lt;x:WorksheetOptions&gt;\n");
                    tmp.append("\t\t\t\t\t\t&lt;x:Print&gt;\n");
                    tmp.append("\t\t\t\t\t\t\t&lt;x:ValidPrinterInfo /&gt;\n");
                    tmp.append("\t\t\t\t\t\t&lt;/x:Print&gt;\n");
                    tmp.append("\t\t\t\t\t&lt;/x:WorksheetOptions&gt;\n");
                    tmp.append("\t\t\t\t&lt;/x:ExcelWorksheet&gt;\n");
                    tmp.append("\t\t\t&lt;/x:ExcelWorksheets&gt;\n");
                    tmp.append("\t\t&lt;/x:ExcelWorkbook&gt;\n");
                    tmp.append("\t&lt;/xml&gt;\n");
                    tmp.append("&lt;![endif]--&gt;\n");
                    
                    p_gxml = preStr + tmp.toString() + lastStr;
                }
            }
        }

        return p_gxml;
    }

    /**
     * Prepares a DiplomatAPI object for extracting the given content, and then
     * does the extraction.
     * 
     * @return GXML As String
     */
    private String extractWithLingAPI() throws Exception
    {
        DiplomatAPI diplomat = new DiplomatAPI();
        
        Object excelStyleMap = m_cxeMessage.getParameters().get("excelStyleMap");
        if (excelStyleMap != null) {
            diplomat.setExcelStyle((HashMap) excelStyleMap);
        }
        
        // Now we get segmentationRuleFile through FileProfileId parameter
        // and then get segmentation rule text
        String fpId = (String) m_cxeMessage.getParameters().get("FileProfileId");
        SegmentationRuleFile srf = null;
        // Not from aligner.
        FileProfileImpl fp = null;
        if (fpId != null)
        {
            fp = HibernateUtil.get(FileProfileImpl.class,Long.valueOf(fpId), false);
            long lpId = fp.getL10nProfileId();
            L10nProfile l10nProfile = ServerProxy.getProjectHandler().getL10nProfile(lpId);

            TranslationMemoryProfile tmp = l10nProfile.getTranslationMemoryProfile();
            long tmpId = tmp.getId();

            srf = ServerProxy.getSegmentationRuleFilePersistenceManager()
                    .getSegmentationRuleFileByTmpid(String.valueOf(tmpId));
            
            diplomat.setFileProfileId(fpId);
            diplomat.setFilterId(fp.getFilterId());
            diplomat.setFilterTableName(fp.getFilterTableName());
            
            diplomat.setJsFilterRegex(fp.getJsFilterRegex());
        }
        
        // Set segmentation rule text
        if (srf == null)
        {
            // Using default system segmentation rule.
            srf = ServerProxy.getSegmentationRuleFilePersistenceManager()
                    .getDefaultSegmentationRuleFile();
        }
        if (srf == null)
        {
            m_logger.error("Could not get the Default segmentaion rule");
            throw new Exception("Could not get the Default segmentaion rule");
        }
        String ruleText = srf.getRuleText();
        // Validate xml rule text
        SegmentationRuleFileValidator val = new SegmentationRuleFileValidator();
        if (ruleText == null || ruleText.equalsIgnoreCase("default"))
        {
            // Use existing segmentaion function in GlobalSight.
            ruleText = "default";
            diplomat.setSegmentationRuleText(ruleText);
        }
        else
        {
            // Use segmentaion rule configed.
            if (val.validate(ruleText, srf.getType()))
            {
                diplomat.setSegmentationRuleText(ruleText);
            }
            else
            {
                System.err.println("Error in segmentaion rule text");
                throw new Exception("segmentation rule text is not valid");
            }
        }

        diplomat.setEncoding(m_encoding);
        diplomat.setLocale(m_locale);
        diplomat.setInputFormat(m_formatType);

        if (m_ruleFile != null) {
            String styleRule = createRuleForStyles(m_office_unChar, m_office_unPara, m_formatType);
            
            if (styleRule != null && !"".equals(styleRule))
            {
                StringBuffer rsb = new StringBuffer(m_ruleFile);
                String rulesetStr = "</ruleset>";
                int index_ruleset = m_ruleFile.lastIndexOf(rulesetStr);
                
                if ("".equals(m_ruleFile) || index_ruleset < 0)
                {
                    rsb.append("<?xml version=\"1.0\"?>");
                    rsb.append("\r\n");
                    rsb.append("<schemarules>");
                    rsb.append("\r\n");
                    rsb.append(styleRule);
                    rsb.append("\r\n");
                    rsb.append("</schemarules>");
                }
                else
                {
                    rsb.insert(index_ruleset + rulesetStr.length(), styleRule);
                }
                
                m_ruleFile = rsb.toString();
            }
            
            diplomat.setRules(m_ruleFile);
        }

        // For PPT issue
        diplomat.setBulletsMsOffice(m_bullets_MsOffice);

        FileMessageData fmd = null;
        boolean deleteFmd = false;
        if (m_cxeMessage.getMessageData() instanceof FileMessageData)
        {
            fmd = (FileMessageData) m_cxeMessage.getMessageData();
        }
        else
        {
            // create a secondary file message data object to hold the
            // data from this message data
            fmd = MessageDataFactory.createFileMessageData();
            fmd.copyFrom(m_cxeMessage.getMessageData());
            deleteFmd = true;
        }

        diplomat.setSourceFile(fmd.getFile());

        // NOTE: This gets the whole String into memory...since this might be huge, 
        // the DiplomatAPI should be changed to return a filename or InputStream.
        String gxml = diplomat.extract();
        
        if (fp != null && fp.isExtractWithSecondFilter())
        {
            String secondFilterTableName = "";
            long secondFilterId = -1;
            
            // Not from aligner.
            secondFilterTableName = fp.getSecondFilterTableName();
            secondFilterId = fp.getSecondFilterId();
                
            boolean isFilterExist = false;
            if (secondFilterTableName != null
                    && !"".equals(secondFilterTableName.trim())
                    && secondFilterId > 0)
            {
                isFilterExist = FilterHelper.isFilterExist(
                        secondFilterTableName, secondFilterId);
            }

            if (isFilterExist)
            {
                Output extractedOutPut = diplomat.getOutput();
                Iterator it = extractedOutPut.documentElementIterator();
                extractedOutPut.clearDocumentElements();
                String dataType = extractedOutPut.getDiplomatAttribute().getDataType();
                boolean isPO = IFormatNames.FORMAT_PO.equals(dataType);
                
                if(isPO)
                {
                    doSecondFilterForPO(extractedOutPut, it, diplomat, fp, fpId,
                            secondFilterId, secondFilterTableName);
                }
                else
                {
                    doSecondFilter(extractedOutPut, it, diplomat, fp, fpId,
                            secondFilterId, secondFilterTableName);
                }            
                
                //re-calculate the total word-count after secondary parsing.
                Iterator extractedIt = extractedOutPut.documentElementIterator();
                int newWordCount = 0;
                while (extractedIt.hasNext())
                {
                    DocumentElement element3 = (DocumentElement) extractedIt.next();
                    if (element3 instanceof TranslatableElement)
                    {
                        int wc = ((TranslatableElement) element3).getWordcount();
                        newWordCount += wc;
                    }
                }
                int originalTotalWC = extractedOutPut.getWordCount();
                //set the total word count to 0 first
                extractedOutPut.setTotalWordCount(-originalTotalWC);
                extractedOutPut.setTotalWordCount(newWordCount);
                
                gxml = DiplomatWriter.WriteXML(extractedOutPut);
            }
        }
        
        if (deleteFmd) {
            fmd.delete();
        }

        return gxml;
    }
    
    /**
     * Apply secondary filter.
     */
    private void doSecondFilter(Output extractedOutPut, Iterator it,
            DiplomatAPI diplomat, FileProfileImpl fp, String fpId,
            long secondFilterId, String secondFilterTableName)
            throws ExtractorException, DiplomatWordCounterException,
            DiplomatSegmenterException, ExtractorRegistryException, Exception
    {
        while (it.hasNext())
        {
            DocumentElement element = (DocumentElement) it.next();
            if (element instanceof TranslatableElement)
            {
                ArrayList segments = ((TranslatableElement) element)
                        .getSegments();
                if (segments != null && segments.size() > 0)
                {
                    for (int i = 0, max = segments.size(); i < max; i++)
                    {
                        // boolean needDecodeTwice = false;
                        diplomat.resetForChainFilter();

                        // Not from aligner.
                        if (fp != null)
                        {
                            diplomat.setFileProfileId(fpId);
                            diplomat.setFilterId(secondFilterId);
                            diplomat.setFilterTableName(secondFilterTableName);
                        }

                        String inputFormatName = getInputFormatName(secondFilterTableName);
                        diplomat.setInputFormat(inputFormatName);

                        SegmentNode node = (SegmentNode) segments.get(i);
                        XmlEntities xe = new XmlEntities();
                        boolean hasLtGt = node.getSegment().contains("<")
                                || node.getSegment().contains(">");
                        String segmentValue = xe.decodeStringBasic(node
                                .getSegment());
                        // decode TWICE to make sure secondary parser can work
                        // as expected,
                        // but it will result in an entity issue,seems it can't
                        // be resolved
                        // in current framework of GS.
                        if (segmentValue.indexOf("&") > -1)
                        {
                            // needDecodeTwice = true;
                            segmentValue = xe.decodeStringBasic(segmentValue);
                        }

                        if (inputFormatName != null
                                && inputFormatName.equals("html"))
                        {
                            segmentValue = checkHtmlTags(segmentValue);
                        }
                        diplomat.setSourceString(segmentValue);

                        if (m_logger.isDebugEnabled())
                        {
                            m_logger.info("Before extracted string : "
                                    + segmentValue);
                        }
                        // extract this segment
                        String str = diplomat.extract();
                        if (m_logger.isDebugEnabled())
                        {
                            m_logger.info("After extracted string : " + str);
                        }

                        Output _output = diplomat.getOutput();
                        Iterator it2 = _output.documentElementIterator();
                        while (it2.hasNext())
                        {
                            DocumentElement element2 = (DocumentElement) it2
                                    .next();
                            if (element2 instanceof SkeletonElement)
                            {
                                String text = ((SkeletonElement) element2)
                                        .getSkeleton();
                                // fixing for GBS-1043
                                if (!hasLtGt)
                                {
                                    text = xe.encodeStringBasic(text);
                                }
                                ((SkeletonElement) element2).setSkeleton(text);
                            }
                            else if (element2 instanceof LocalizableElement)
                            {
                                String text = ((LocalizableElement) element2)
                                        .getChunk();
                                text = xe.encodeStringBasic(text);
                                ((LocalizableElement) element2).setChunk(text);
                            }
                            extractedOutPut.addDocumentElement(element2);
                        }
                    }
                }
            }
            else
            {
                extractedOutPut.addDocumentElement(element);
            }
        }
    }
    
    /**
     * Apply secondary filter for PO File.
     */
    private void doSecondFilterForPO(Output p_extractedOutPut, Iterator p_it,
            DiplomatAPI p_diplomat, FileProfileImpl p_fp, String p_fpId,
            long p_secondFilterId, String p_secondFilterTableName)
            throws ExtractorException, DiplomatWordCounterException,
            DiplomatSegmenterException, ExtractorRegistryException, Exception
    {
        ArrayList segSource = new ArrayList();
        ArrayList segTarget = new ArrayList();
        TranslatableElement elemSource = new TranslatableElement();
        TranslatableElement elemTarget = new TranslatableElement();
        String xliffpart;
        boolean isXML = FilterConstants.XMLRULE_TABLENAME
                .equals(p_secondFilterTableName);
        boolean isHTML = FilterConstants.HTML_TABLENAME
                .equals(p_secondFilterTableName);
        while (p_it.hasNext())
        {
            DocumentElement element = (DocumentElement) p_it.next();

            if (element instanceof TranslatableElement)
            {
                ArrayList segments = ((TranslatableElement) element)
                        .getSegments();
                xliffpart = ((TranslatableElement) element)
                        .getXliffPartByName();
                if (xliffpart.equals("source"))
                {
                    segSource = segments;
                    elemSource = (TranslatableElement) element;
                    continue;
                }
                else if (xliffpart.equals("target"))
                {
                    segTarget = segments;
                    elemTarget = (TranslatableElement) element;
                }

                // If need do a secondary filter.
                boolean isSecondaryFilter = true;
                if (segSource != null && segTarget != null
                        && segSource.size() == segTarget.size())
                {
                    String source;
                    for (int i = 0, max = segSource.size(); i < max; i++)
                    {
                        source = (String) ((SegmentNode) segSource.get(i))
                                .getSegment();
                        if (source == null
                                || (!source.equals(((SegmentNode) segTarget
                                        .get(i)).getSegment())))
                        {
                            isSecondaryFilter = false;
                            break;
                        }
                    }
                }

                SegmentNode sn;
                String seg;
                if (isSecondaryFilter && segments != null
                        && segments.size() > 0)
                {
                    // Modify Segment for HTML/XML Filter
                    for (int i = 0, max = segments.size(); i < max; i++)
                    {
                        sn = (SegmentNode) segments.get(i);
                        seg = sn.getSegment();
                        seg = seg.replace("&amp;", m_tag_amp);
                        if (isXML && !checkIfXMLIsWellFormed(seg))
                        {
                            seg = m_tag_start + seg + m_tag_end;
                            if (checkIfXMLIsWellFormed(seg))
                            {
                                sn.setSegment(seg);
                            }
                        }
                        else if (isHTML)
                        {
                            sn.setSegment(seg);
                        }

                        if (isXML
                                && !checkIfXMLIsWellFormed(((SegmentNode) segments
                                        .get(i)).getSegment()))
                        {
                            p_extractedOutPut.addDocumentElement(elemSource);
                            p_extractedOutPut.addDocumentElement(elemTarget);
                            isSecondaryFilter = false;
                            break;
                        }
                    }

                    for (int i = 0, max = segments.size(); (i < max)
                            && isSecondaryFilter; i++)
                    {
//                        boolean needDecodeTwice = false;
                        p_diplomat.resetForChainFilter();

                        // Not from aligner.
                        if (p_fp != null)
                        {
                            p_diplomat.setFileProfileId(p_fpId);
                            p_diplomat.setFilterId(p_secondFilterId);
                            p_diplomat
                                    .setFilterTableName(p_secondFilterTableName);
                        }

                        String inputFormatName = getInputFormatName(p_secondFilterTableName);
                        p_diplomat.setInputFormat(inputFormatName);

                        SegmentNode node = (SegmentNode) segments.get(i);
                        XmlEntities xe = new XmlEntities();
//                        boolean hasLtGt = node.getSegment().contains("<")
//                                || node.getSegment().contains(">");
                        String segmentValue = xe.decodeStringBasic(node
                                .getSegment());
                        // decode TWICE to make sure secondary parser can work
                        // as expected,
                        // but it will result in an entity issue,seems it can't
                        // be resolved
                        // in current framework of GS.
                        if (segmentValue.indexOf("&") > -1)
                        {
//                            needDecodeTwice = true;
                            segmentValue = xe.decodeStringBasic(segmentValue);
                        }

                        if (inputFormatName != null
                                && inputFormatName.equals("html"))
                        {
                            segmentValue = checkHtmlTags(segmentValue);
                        }
                        p_diplomat.setSourceString(segmentValue);

                        // extract this segment
                        try
                        {
                            p_diplomat.extract();
                        }
                        catch (Exception e)
                        {
                            p_extractedOutPut.addDocumentElement(elemSource);
                            p_extractedOutPut.addDocumentElement(elemTarget);
                            break;
                        }

                        Output _output = p_diplomat.getOutput();
                        Iterator it2 = _output.documentElementIterator();
                        while (it2.hasNext())
                        {
                            DocumentElement element2 = (DocumentElement) it2
                                    .next();
                            if (element2 instanceof SkeletonElement)
                            {
                                String text = ((SkeletonElement) element2)
                                        .getSkeleton();
                                if (isXML && text.startsWith(m_tag_start))
                                {
                                    text = text.substring(m_tag_start.length());
                                }
                                else if (isXML && text.endsWith(m_tag_end))
                                {
                                    text = text.substring(0, text.length()
                                            - m_tag_end.length());
                                }

                                text = text.replace(m_tag_amp, "&amp;");

                                ((SkeletonElement) element2).setSkeleton(text);
                            }
                            else if (element2 instanceof LocalizableElement)
                            {
                                String text = ((LocalizableElement) element2)
                                        .getChunk();
                                text = xe.encodeStringBasic(text);
                                ((LocalizableElement) element2).setChunk(text);
                            }
                            else if (element2 instanceof TranslatableElement)
                            {
                                List segs = ((TranslatableElement) element2)
                                        .getSegments();
                                String text;
                                for (int j = 0; i < segs.size(); i++)
                                {
                                    sn = (SegmentNode) segs.get(j);
                                    text = sn.getSegment();
                                    text = text.replace(m_tag_amp, "&amp;");
                                    sn.setSegment(text);
                                }

                            }

                            p_extractedOutPut.addDocumentElement(element2);
                        }
                    }
                }
                else
                {
                    p_extractedOutPut.addDocumentElement(elemSource);
                    p_extractedOutPut.addDocumentElement(elemTarget);
                }
            }
            else
            {
                p_extractedOutPut.addDocumentElement(element);
            }
        }
    }
    
    /*
     * The XML data must be well-formed, otherwise SAX will throw exception.
     */
    private boolean checkIfXMLIsWellFormed(String p_xmlData)
    {
        boolean result = false;

        String xmlData = p_xmlData;
        if (xmlData.startsWith("&lt;") && xmlData.endsWith("&gt;"))
        {
            xmlData = xmlData.replace("&lt;", "<").replace("&gt;", ">");
        }

        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            InputSource source = new InputSource(new ByteArrayInputStream(
                    xmlData.getBytes()));
            factory.newDocumentBuilder().parse(source);

            result = true;
        }
        catch (Exception e)
        {
        }

        return result;
    }
    
    private String createRuleForStyles(String unCharStyles,
            String unParaStyles, String formatType)
    {
        if (DiplomatAPI.FORMAT_OPENOFFICE_XML.equals(formatType))
        {
            return createRuleForOOStyles(unCharStyles, unParaStyles);
        }
        else if (DiplomatAPI.FORMAT_OFFICE_XML.equals(formatType))
        {
            String styleRule = createRuleForOfficeStyles(unCharStyles, unParaStyles);
            String numRule = createRuleForExcelNumber();
            String cellRule = createRuleForExcelSheetCell();
            String sharedRule = createRuleForExcelSharedXml();
            
            StringBuffer result = new StringBuffer();
            result.append(styleRule != null ? styleRule : "");
            result.append(numRule != null ? numRule : "");
            result.append(cellRule != null ? cellRule : "");
            result.append(sharedRule != null ? sharedRule : "");
            
            return result.toString();
        }
        else
        {
            return null;
        }
    }

    private String createRuleForOfficeStyles(String unCharStyles, String unParaStyles)
    {
        String styleRule = null;
        
        List<String> unchar = MSOffice2010Filter.toList(unCharStyles);
        List<String> unpara = MSOffice2010Filter.toList(unParaStyles);
        
        boolean added = false;
        StringBuffer styleSB = new StringBuffer();     
        
        for (String style : unchar)
        {
            added = true;
            styleSB.append("<dont-translate path='//w:r/w:rPr/w:rStyle[@w:val=\"" + style + "\"]/../..' />");
            styleSB.append("\r\n");
            styleSB.append("<dont-translate path='//w:r/w:rPr/w:rStyle[@w:val=\"" + style + "\"]/../..//*' />");
            styleSB.append("\r\n");
        }
        
        for (String style : unpara)
        {
            added = true;
            styleSB.append("<dont-translate path='//w:p/w:pPr/w:pStyle[@w:val=\"" + style + "\"]/../..' />");
            styleSB.append("\r\n");
            styleSB.append("<dont-translate path='//w:p/w:pPr/w:pStyle[@w:val=\"" + style + "\"]/../..//*' />");
            styleSB.append("\r\n");
        }
        
        if (added)
        {
            StringBuffer allStyleSB = new StringBuffer();
            allStyleSB.append("\r\n");
            allStyleSB.append("<ruleset schema=\"w:document\">");
            allStyleSB.append("\r\n");
            allStyleSB.append(styleSB);
            allStyleSB.append("</ruleset>");
            allStyleSB.append("\r\n");
            
            allStyleSB.append("\r\n");
            allStyleSB.append("<ruleset schema=\"w:hdr\">");
            allStyleSB.append("\r\n");
            allStyleSB.append(styleSB);
            allStyleSB.append("</ruleset>");
            allStyleSB.append("\r\n");
            
            allStyleSB.append("\r\n");
            allStyleSB.append("<ruleset schema=\"w:ftr\">");
            allStyleSB.append("\r\n");
            allStyleSB.append(styleSB);
            allStyleSB.append("</ruleset>");
            allStyleSB.append("\r\n");
            
            styleRule = allStyleSB.toString();
        }
        return styleRule;
    }
    
    private String createRuleForExcelNumber()
    {
        String styleRule = null;
        boolean added = false;
        StringBuffer styleSB = new StringBuffer();
        styleSB.append("\r\n");
        styleSB.append("<ruleset schema=\"worksheet\">");
        styleSB.append("\r\n");
        
        List<String> ids = MSOffice2010Filter.toList(m_xlsx_numStyleIds);
        
        for (String style : ids)
        {
            added = true;
            styleSB.append("<dont-translate path='//*[local-name()=\"c\"][@s=\"" + style + "\"]' />");
            styleSB.append("\r\n");
            styleSB.append("<dont-translate path='//*[local-name()=\"c\"][@s=\"" + style + "\"]//*' />");
            styleSB.append("\r\n");
        }
        
        styleSB.append("</ruleset>");
        styleSB.append("\r\n");
        
        if (added)
        {
            styleRule = styleSB.toString();
        }
        return styleRule;
    }
    
    private String createRuleForExcelSheetCell()
    {
        String styleRule = null;
        boolean added = false;
        StringBuffer styleSB = new StringBuffer();
        styleSB.append("\r\n");
        styleSB.append("<ruleset schema=\"worksheet\">");
        styleSB.append("\r\n");
        
        List<String> ids = MSOffice2010Filter.toList(m_xlsx_sheetHiddenCell);
        
        for (String id : ids)
        {
            added = true;
            styleSB.append("<dont-translate path='//*[local-name()=\"c\"][@r=\"" + id + "\"]' />");
            styleSB.append("\r\n");
            styleSB.append("<dont-translate path='//*[local-name()=\"c\"][@r=\"" + id + "\"]//*' />");
            styleSB.append("\r\n");
        }
        
        styleSB.append("</ruleset>");
        styleSB.append("\r\n");
        
        if (added)
        {
            styleRule = styleSB.toString();
        }
        return styleRule;
    }
    
    private String createRuleForExcelSharedXml()
    {
        String styleRule = null;
        boolean added = false;
        StringBuffer styleSB = new StringBuffer();
        styleSB.append("\r\n");
        styleSB.append("<ruleset schema=\"sst\">");
        styleSB.append("\r\n");
        
        List<String> ids = MSOffice2010Filter.toList(m_xlsx_hiddenSharedSI);
        
        for (String idstr : ids)
        {
            int id = Integer.parseInt(idstr) + 1;
            added = true;
            styleSB.append("<dont-translate path='//*[local-name()=\"si\"][" + id + "]' />");
            styleSB.append("\r\n");
            styleSB.append("<dont-translate path='//*[local-name()=\"si\"][" + id + "]//*' />");
            styleSB.append("\r\n");
        }
        
        styleSB.append("</ruleset>");
        styleSB.append("\r\n");
        
        if (added)
        {
            styleRule = styleSB.toString();
        }
        return styleRule;
    }

    private String createRuleForOOStyles(String unCharStyles, String unParaStyles)
    {
        String ooStyleRule = null;
        boolean added = false;
        StringBuffer ooStyle = new StringBuffer();
        ooStyle.append("\r\n");
        ooStyle.append("<ruleset schema=\"office:document-content\">");
        ooStyle.append("\r\n");
        
        List<String> unchar = OpenOfficeFilter.toList(unCharStyles);
        List<String> unpara = OpenOfficeFilter.toList(unParaStyles);
        
        for (String style : unchar)
        {
            added = true;
            ooStyle.append("<dont-translate path='//text:span[@text:style-name=\"" + style + "\"]'/>");
            ooStyle.append("\r\n");
            ooStyle.append("<dont-translate path='//text:span[@text:style-name=\"" + style + "\"]//*'/>");
            ooStyle.append("\r\n");
            ooStyle.append("<dont-translate path='//table:table-cell[@table:style-name=\"" + style + "\"]'/>");
            ooStyle.append("\r\n");
            ooStyle.append("<dont-translate path='//table:table-cell[@table:style-name=\"" + style + "\"]//*'/>");
            ooStyle.append("\r\n");
        }
        
        for (String style : unpara)
        {
            added = true;
            ooStyle.append("<dont-translate path='//text:p[@text:style-name=\"" + style + "\"]'/>");
            ooStyle.append("\r\n");
            ooStyle.append("<dont-translate path='//text:p[@text:style-name=\"" + style + "\"]//*'/>");
            ooStyle.append("\r\n");
        }
        
        ooStyle.append("</ruleset>");
        ooStyle.append("\r\n");
        
        if (added)
        {
            ooStyleRule = ooStyle.toString();
        }
        return ooStyleRule;
    }

    /**
     * Prepares a PrsXml object for extracting the given content, and then does
     * the extraction.
     * 
     * @return GXML As String
     */
    private String extractWithPrsXmlExtractor() throws Exception
    {
        Connection connection = null;

        try
        {
            connection = ConnectionPool.getConnection();
            StringTokenizer st = new StringTokenizer(m_locale, "_");
            String language = st.nextToken();
            String country = st.nextToken();
            Locale theLocale = new Locale(language, country);

            EFInputData input = new EFInputData();
            input.setLocale(theLocale);
            input.setUnicodeInput(readXmlFromMessageData());
            Output output = new Output();
            AbstractExtractor extractor = new com.globalsight.ling.docproc.extractor.paginated.Extractor(
                    connection);
            extractor.init(input, output);
            extractor.loadRules();
            extractor.extract();
            return ((com.globalsight.ling.docproc.extractor.paginated.Extractor) extractor)
                    .getDiplomatizedXml();
        }
        finally
        {
            try
            {
                ConnectionPool.returnConnection(connection);
            }
            catch (ConnectionPoolException cpe)
            {
            }
        }
    }

    /**
     * Gets needed values from the event flow xml
     * 
     * @exception LingAdapterException
     */
    private void parseEventFlowXml() throws LingAdapterException
    {
        StringReader sr = null;
        try
        {
            sr = new StringReader(m_cxeMessage.getEventFlowXml());
            InputSource is = new InputSource(sr);
            DOMParser parser = new DOMParser();
            parser.setFeature("http://xml.org/sax/features/validation", false);
            parser.parse(is);
            Element elem = parser.getDocument().getDocumentElement();
            NodeList nl = elem.getElementsByTagName("source");
            Element sourceElement = (Element) nl.item(0);

            // get format type
            m_formatType = sourceElement.getAttribute("formatType");
            // take rtf format as word format to convert.
            if (m_formatType.equals(IFormatNames.FORMAT_RTF))
            {
                m_formatType = IFormatNames.FORMAT_WORD_HTML;
            }
            
            // get format name, set it empty if can not get
            try
            {
                m_formatName = sourceElement.getAttribute("formatName");
            }
            catch (Exception e)
            {
                // ignore this exception
            }
            if (null == m_formatName)
            {
                m_formatName = "";
            }
            
            // get file profile id
            m_fileProfile = sourceElement.getAttribute("dataSourceId");

            Element localeElement = (Element) sourceElement
                    .getElementsByTagName("locale").item(0);
            m_locale = localeElement.getFirstChild().getNodeValue();

            Element charsetElement = (Element) sourceElement
                    .getElementsByTagName("charset").item(0);
            m_encoding = charsetElement.getFirstChild().getNodeValue();

            // Get the display name to use when logging an extractor error.
            nl = elem.getElementsByTagName("displayName");
            Element displayNameElement = (Element) nl.item(0);
            m_displayName = displayNameElement.getFirstChild().getNodeValue();
            m_errorArgs[0] = "Extractor";
            // the filename is the first arg to the error messages
            m_errorArgs[1] = m_displayName;

            // For PPT issue
            if (DiplomatAPI.isMsOfficePowerPointFormat(m_formatType))
            {
                nl = elem.getElementsByTagName("da");
                for (int i = 0; i < nl.getLength(); i++)
                {
                    Element e = (Element) nl.item(i);
                    if (e.getAttribute("name").equals("css_bullet"))
                    {
                        Element dv = (Element) e.getElementsByTagName("dv")
                                .item(0);
                        if (dv != null)
                        {
                            m_bullets_MsOffice = dv.getFirstChild()
                                    .getNodeValue();
                        }
                        break;
                    }
                }
            }
            
            if (DiplomatAPI.isOpenOfficeFormat(m_formatType)
                    || DiplomatAPI.isOfficeXmlFormat(m_formatType))
            {
                nl = elem.getElementsByTagName("da");
                for (int i = 0; i < nl.getLength(); i++)
                {
                    Element e = (Element) nl.item(i);
                    String aname = e.getAttribute("name");
                    if ("unParaStyles".equals(aname))
                    {
                        Element dv = (Element) e.getElementsByTagName("dv")
                                .item(0);
                        if (dv != null)
                        {
                            Node valueText = dv.getFirstChild();
                            m_office_unPara = (valueText != null) ?
                                    valueText.getNodeValue() : "";
                        }
                    }
                    if ("unCharStyles".equals(aname))
                    {
                        Element dv = (Element) e.getElementsByTagName("dv")
                                .item(0);
                        if (dv != null)
                        {
                            Node valueText = dv.getFirstChild();
                            m_office_unChar = (valueText != null) ?
                                    valueText.getNodeValue() : "";
                        }
                    }
                    if ("numStyleIds".equals(aname))
                    {
                        Element dv = (Element) e.getElementsByTagName("dv")
                                .item(0);
                        if (dv != null)
                        {
                            Node valueText = dv.getFirstChild();
                            m_xlsx_numStyleIds = (valueText != null) ?
                                    valueText.getNodeValue() : "";
                        }
                    }
                    if ("hiddenSharedSI".equals(aname))
                    {
                        Element dv = (Element) e.getElementsByTagName("dv")
                                .item(0);
                        if (dv != null)
                        {
                            Node valueText = dv.getFirstChild();
                            m_xlsx_hiddenSharedSI = (valueText != null) ?
                                    valueText.getNodeValue() : "";
                        }
                    }
                    if ("sheetHiddenCell".equals(aname))
                    {
                        Element dv = (Element) e.getElementsByTagName("dv")
                                .item(0);
                        if (dv != null)
                        {
                            Node valueText = dv.getFirstChild();
                            m_xlsx_sheetHiddenCell = (valueText != null) ?
                                    valueText.getNodeValue() : "";
                        }
                    }
                }
            }

            // For Adobe indesign issue
            nl = elem.getElementsByTagName("batchInfo");
            Element batchInfoElement = (Element) nl.item(0);
            Element docPageCountElement = (Element) batchInfoElement
                    .getElementsByTagName("docPageCount").item(0);
            m_docPageCount = Integer.parseInt(docPageCountElement
                    .getFirstChild().getNodeValue());
            Element docPageNumElement = (Element) batchInfoElement
                    .getElementsByTagName("docPageNumber").item(0);
            m_docPageNum = Integer.parseInt(docPageNumElement.getFirstChild()
                    .getNodeValue());
        }
        catch (Exception e)
        {
            m_logger.error(
                    "Unable to parse EventFlowXml. Cannot determine locale, "
                            + "encoding, and format_type for extraction.", e);
            throw new LingAdapterException("CxeInternal", m_errorArgs, e);
        }
        finally
        {
            if (sr != null)
            {
                sr.close();
            }
        }
    }

    /**
     * Queries the rule file associated with the file profile out of the DB.
     * 
     * @exception LingAdapterException
     */
    private void queryRuleFile() throws LingAdapterException
    {
        Connection connection = null;
        PreparedStatement query = null;
        ResultSet results = null;
        try
        {
            // Retrieve the (XML) Rule File from the Database.
            connection = ConnectionPool.getConnection();
            query = connection.prepareStatement(SQL_SELECT_RULE);
            query.setString(1, m_fileProfile);
            results = query.executeQuery();

            if (results.next())
            {
                m_ruleFile = results.getString(1);
                Logger.writeDebugFile("ruleFile.xml", m_ruleFile);
            }
            else
            {
                if (m_formatName != null && m_formatName.equalsIgnoreCase("resx"))
                {
                    try
                    {
                        String fileName = SystemConfiguration
                            .getCompanyResourcePath("/properties/ResxRule.properties");
                        m_ruleFile = FileUtils.read(StandardExtractor.class
                                .getResourceAsStream(fileName));
                    }
                    catch (Exception e)
                    {
                        m_ruleFile = "";
                    }
                }
                else
                {
                    m_ruleFile = null;
                }
            }
            doPostQueryRule(connection);
        }
        catch (ConnectionPoolException cpe)
        {
            m_logger.error(
                    "Unable to connect to database retrieve XML rule file"
                            + " for FileProfileID " + m_fileProfile, cpe);
            throw new LingAdapterException("DbConnection", m_errorArgs, cpe);
        }
        catch (SQLException sqle)
        {
            m_logger.error(
                    "Unable to retrieve XML rule file for FileProfileID "
                            + m_fileProfile, sqle);
            throw new LingAdapterException("SqlException", m_errorArgs, sqle);
        }
        finally
        {
            ConnectionPool.silentClose(results);
            ConnectionPool.silentClose(query);
            try
            {
                ConnectionPool.returnConnection(connection);
            }
            catch (ConnectionPoolException cpe)
            {
            }
        }
    }

    /**
     * just for indd
     * 
     * @param connection
     * @throws LingAdapterException
     */
    private void doPostQueryRule(Connection connection)
            throws LingAdapterException
    {
        String QUERY_FOR_FORMAT = "select k.format_type, k.pre_extract_event from known_format_type k, file_profile f where f.id=? and f.known_format_type_id = k.id";
        PreparedStatement query = null;
        ResultSet rs = null;
        try
        {
            query = connection.prepareStatement(QUERY_FOR_FORMAT);
            query.setString(1, m_fileProfile);
            rs = query.executeQuery();
            if (rs.next())
            {
                String rs1 = rs.getString(1);
                String event = rs.getString(2);
                
                if (InddRuleHelper.isIndd(rs1))
                {
                    if (m_docPageCount >= 2
                            && m_displayName
                                    .startsWith(AdobeHelper.XMP_DISPLAY_NAME_PREFIX))
                        m_ruleFile = InddRuleHelper.loadAdobeXmpRule();
                    else
                        m_ruleFile = InddRuleHelper.loadRule();
                }
                else if (OpenOfficeRuleHelper.isOpenOffice(rs1))
                {
                    if (m_docPageCount >= 2
                            && m_displayName
                                    .startsWith(OpenOfficeHelper.OO_HEADER_DISPLAY_NAME_PREFIX))
                        m_ruleFile = OpenOfficeRuleHelper.loadStylesRule();
                    else
                        m_ruleFile = OpenOfficeRuleHelper.loadRule(m_displayName);
                }
                else if (OfficeXmlRuleHelper.isOfficeXml(rs1))
                {
                    m_ruleFile = OfficeXmlRuleHelper.loadRule(m_displayName, m_docPageCount);
                }
                else if (IdmlRuleHelper.isIdml(event))
                {
                    m_ruleFile = IdmlRuleHelper.loadRule();
                }
            }
        }
        catch (SQLException e)
        {
            m_logger.error("Unable to retrieve format_type  for FileProfileID "
                    + m_fileProfile, e);
            throw new LingAdapterException("SqlException", m_errorArgs, e);
        }
        finally
        {
            ConnectionPool.silentClose(rs);
            ConnectionPool.silentClose(query);
        }
    }

    /**
     * Reads the content of the gxml or prsxml file and returns a String of XML
     * either GXML or PRSXML. This also deletes the file after reading its
     * contents
     * 
     * @param p_gxmlFileName
     *            filename containing the GXML (or PRSXML)
     * @exception IOException
     * @return String
     */
    private String readXmlFromMessageData() throws IOException
    {
        String s = MessageDataReader.readString(m_cxeMessage.getMessageData());
        m_cxeMessage.setDeleteMessageData(true);
        return s;
    }
    
    /**
     * Get its corresponding input format name by the filter table name.
     * 
     * If more filter is added in future, need add that in the list.
     * 
     * @param filterTableName
     * @return input format name
     */
    private String getInputFormatName(String filterTableName)
    {
        String inputFormatName = null;
        if (filterTableName != null) {
            filterTableName = filterTableName.trim();
            if ("html_filter".equalsIgnoreCase(filterTableName)) {
                inputFormatName = IFormatNames.FORMAT_HTML;
            } else if ("java_properties_filter".equalsIgnoreCase(filterTableName)){
                inputFormatName = IFormatNames.FORMAT_JAVAPROP;
            } else if ("java_script_filter".equalsIgnoreCase(filterTableName)) {
                inputFormatName = IFormatNames.FORMAT_JAVASCRIPT;
            } else if ("xml_rule_filter".equalsIgnoreCase(filterTableName)) {
                inputFormatName = IFormatNames.FORMAT_XML;
            } else if ("jsp_filter".equalsIgnoreCase(filterTableName)) {
                inputFormatName = IFormatNames.FORMAT_JSP;
            } else if ("ms_office_doc_filter".equalsIgnoreCase(filterTableName)) {
                inputFormatName = IFormatNames.FORMAT_WORD_HTML;
            }
        }
        
        return inputFormatName;
    }
    
    /**
     * Check if the html snippet is valid.
     * Commonly,for secondary filter/parser,the input html content
     * is snippet, maybe invalid,it will cause parse error.To avoid
     * this,encode the invalid "<" or ">" to entity.
     * @param p_str
     * @return
     */
    public static String checkHtmlTags(String p_str)
    {
        if (p_str == null)
        {
            return null;
        }
        else
        {
            StringBuffer sb = new StringBuffer();
            StringBuffer sb_p = new StringBuffer(p_str);
            
            // replace comments into comments_timespan(index)
            int commentStartIndex = sb_p.indexOf("<!--");
            int commentEndIndex = sb_p.indexOf("-->", commentStartIndex);
            long timespan = (new java.util.Date()).getTime();
            String keyPre = "gshtmlcomments_" + timespan + "_";
            int index = 0;
            Map<String, String> comments = new HashMap<String, String>();
            while (commentStartIndex > -1 && commentEndIndex > -1)
            {
                String key = keyPre + index;
                String comment = sb_p.substring(commentStartIndex, commentEndIndex + 3);
                comments.put(key, comment);
                sb_p.replace(commentStartIndex, commentEndIndex + 3, key);
                
                commentStartIndex = sb_p.indexOf("<!--");
                commentEndIndex = sb_p.indexOf("-->", commentStartIndex);
                index++;
            }
            p_str = sb_p.toString();
            
            // check tags
            int ltIndex = p_str.indexOf("<");
            int gtIndex = p_str.indexOf(">");
            
            if (ltIndex == -1 && gtIndex == -1)
            {
                return restoreComments(p_str, comments);
            }
            while (ltIndex > -1 || gtIndex > -1)
            {
                String strA = "";
                //has "<", no ">"
                if (ltIndex > -1 && gtIndex == -1)
                {
                    p_str = p_str.replace("<", "&lt;");
                    sb.append(p_str);
                    ltIndex = -1;
                    gtIndex = -1;
                }
                //has ">", no "<"
                else if (ltIndex == -1 && gtIndex > -1)
                {
                    p_str = p_str.replace(">", "&gt;");
                    sb.append(p_str);
                    ltIndex = -1;
                    gtIndex = -1;
                }
                else if (ltIndex > -1 && gtIndex > -1)
                {
                    if (gtIndex < ltIndex) {
                        strA = p_str.substring(0, gtIndex);
                        p_str = p_str.substring(gtIndex+1);
                        sb.append(strA).append("&gt;");
                        
                        ltIndex = p_str.indexOf("<");
                        gtIndex = p_str.indexOf(">");
                    } else {
                        strA = p_str.substring(0, gtIndex+1);
                        p_str = p_str.substring(gtIndex+1);
                        
                        int left = strA.lastIndexOf("<");
                        String leftStr = strA.substring(0, left);
                        leftStr = leftStr.replace("<", "&lt;");                     
                        String rightStr = strA.substring(left);

                        sb.append(leftStr).append(rightStr);
                        
                        ltIndex = p_str.indexOf("<");
                        gtIndex = p_str.indexOf(">");
                        
                        if (ltIndex == -1 && gtIndex == -1)
                        {
                            sb.append(p_str);
                        }
                    }
                }
            }
            return restoreComments(sb.toString(), comments);
        }
    }

    private static String restoreComments(String pStr, Map<String, String> comments)
    {
        if (comments == null || comments.isEmpty())
        {
            return pStr;
        }
        
        
        Set<String> keys = comments.keySet();
        for (String key : keys)
        {
            pStr = pStr.replace(key, comments.get(key));
        }
        
        return pStr;
    }
    
}