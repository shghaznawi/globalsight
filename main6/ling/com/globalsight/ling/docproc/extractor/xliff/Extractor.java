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
package com.globalsight.ling.docproc.extractor.xliff;

// Java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.globalsight.ling.common.RegEx;
import com.globalsight.ling.common.RegExException;
import com.globalsight.ling.common.RegExMatchInterface;
import com.globalsight.ling.common.Text;
import com.globalsight.ling.common.XmlEntities;
import com.globalsight.ling.docproc.AbstractExtractor;
import com.globalsight.ling.docproc.DocumentElementException;
import com.globalsight.ling.docproc.ExtractorException;
import com.globalsight.ling.docproc.ExtractorExceptionConstants;
import com.globalsight.ling.docproc.ExtractorInterface;
import com.globalsight.ling.docproc.ExtractorRegistry;
import com.globalsight.ling.docproc.extractor.xml.GsDOMParser;
import com.globalsight.util.edit.SegmentUtil;

/**
 * Xliff Extractor.
 * 
 * <p>
 * The behavior of the XML Extractor is rule-file-driven (see schemarules.dtd).
 * If no rules are specified, the default rules are:
 * </p>
 * 
 * <ul>
 * <li>Contents of all element nodes are extracted as translatable.</li>
 * <li>All elements break a segment. In other words, all tags go into the
 * skeleton and no tag is included in an extracted translatable segment.</li>
 * <li>No attributes are extracted.</li>
 * </ul>
 * 
 * The rule file basically contains two sets of rules:
 * 
 * <ol>
 * <li>&lt;dont-translate&gt; elements specify elements or attributes that
 * should not be extracted.</li>
 * 
 * <li>&lt;translate&gt; elements specify elements or attributes that are to be
 * extracted for translation or localization.</li>
 * </ol>
 * 
 * <p>
 * Attributes on &lt;translatable&gt;:
 * </p>
 * 
 * <ul>
 * <li>path: XPath expression to address the elements and attributes that are to
 * be extracted for translation or localization.</li>
 * 
 * <li>loctype: Localization type. Specifies whether the extracted data are
 * translatable or localizable. Possible values are "translatable" or
 * "localizable". The default value is "translatable".</li>
 * 
 * <li>datatype: Format of the data. If the extracted data needs further
 * extraction, the data format should be specified in this attribute. The
 * typical use case is that a HTML snippet is stored in an XML element. When the
 * datatype attribute has the value "html", the XML extractor extracts the
 * content of the element and calls the HTML extractor, passing it the extracted
 * content.</li>
 * 
 * <li>type: Type of the data. This attribute is used when the type of the
 * extracted data needs to be explicitly specified. Examples of types are
 * "link", "bold", "underline" etc.</li>
 * 
 * <li>inline: This attribute specifies whether the elements specified by the
 * path attribute break a segment. If an element breaks a segment, the element
 * tag is not included in the extracted data. If an element does not break a
 * segment (if the tag is inline), the element tag is included in the extracted
 * data. Possible values for the attribute are "yes" or "no". "yes" means the
 * tag does not break segments. "no" means the tag breaks segments. The default
 * value is "no".</li>
 * 
 * <li>movable: the DiplomatXML attribute for bpt,it,ut,ph tags, specifying
 * whether these tags can be moved around in the editor.</li>
 * 
 * <li>erasable: the DiplomatXML attribute for bpt,it,ut,ph tags, specifying
 * whether these tags can be deleted in the editor.</li>
 * </ul>
 * 
 * <p>
 * When multiple rules match a single node, the rules are merged according to
 * the algorithm in Rule.java. A side-effect of merging is that the first
 * matching rule determines whether a node is translatable or not; sub-sequent
 * rule matches will never change the type of the first rule.
 * </p>
 * 
 * <p>
 * A tag that switches to a different extractor can not be embeddable.
 * </p>
 */
public class Extractor extends AbstractExtractor implements ExtractorInterface,
        EntityResolver, ExtractorExceptionConstants, ErrorHandler
{
    private ExtractorAdmin m_admin = new ExtractorAdmin(null);

    // XML declaration flags
    private boolean m_haveXMLDecl = false;
    private String m_version = null;
    private String m_standalone = null;
    private String encoding = null;

    // XML encoder
    private XmlEntities m_xmlEncoder = new XmlEntities();

    // For recording the original source content to write into skeleton.
    private String sourceContent = new String();
    // For recording the tag transformed source content to write source tuv.
    private String tuvSourceContent = new String();
 // For recording the original target content to write into skeleton.
    private String targetContent = new String();
    //For recording the tag transformed source content to write target tuv.
    private String tuvTargetContent = new String();
    // The content removed tag, for validating the no tag content is null or not
    private String contentNoTag = new String();

    // For recording the alt source content to write into tuv.
    private String tuvAltSource = new String();
    // For recording the alt target content to write into tuv.
    private String tuvAltTarget = new String();
    // Because maybe source have several tag, and we will re-package them by bpt
    // or ph, and use the array to record the source tag index, and put them to
    // the target packaged tag in the same sequence to ensure the source and
    // target tag have same sequence.
    private ArrayList sourceIndex = new ArrayList();
    // if the source content only have place holder and real content is null,
    // only write it into skeleton, and the place holder should not occupy index
    // value, should set the current index value to the pre-trans-unit index
    // value.
    private int lastIndex = 1;

    private final String IWS_SEGMENT_DATA = "iws:segment-metadata";
    private final String IWS_STATUS = "iws:status";
    static public final String IWS_TRANSLATION_TYPE = "translation_type";
    static public final String IWS_TRANSLATION_MT = "machine_translation_mt";
    static public final String IWS_TM_SCORE = "tm_score";
    private final String IWS_SID = "sid";
    static public final String IWS_SOURCE_CONTENT = "source_content";
    static public final String IWS_REPETITION = "repetition";
    static public final String IWS_REPEATED = "repeated";

    //
    // Constructors
    //
    public Extractor()
    {
        super();

        m_admin = new ExtractorAdmin(null);
        m_haveXMLDecl = false;
        m_version = null;
        m_standalone = null;
        encoding = null;
    }

    public void setFormat()
    {
        setMainFormat(ExtractorRegistry.FORMAT_XLIFF);
    }

    /**
     * Extracts the input document.
     * 
     * Parses the XML File into DOM using xerces.
     * 
     * Skips the external entity (DTD, etc) by providing a null byte array.
     * 
     * Then invokes domNodeVisitor for the Document 'Node' ('virtual root') to
     * traverse the DOM tree recursively, using the AbstractExtractor API to
     * write out skeleton and segments.
     */
    public void extract() throws ExtractorException
    {
        try
        {
            // Set the main format depending on which (derived) class
            // we're called in.
            setFormat();

            GsDOMParser parser = new GsDOMParser();

            // don't read external DTDs
            parser.setEntityResolver(this);
            // provide detailed error report
            parser.setErrorHandler(this);
            // parse and create DOM tree
            parser.parse(new InputSource(readInput()));

            // preserve the values in the inputs' XML declaration
            m_haveXMLDecl = parser.getHaveXMLDecl();
            m_version = parser.getXMLVersion();
            m_standalone = parser.getStandalone();
            encoding = parser.getEncoding();

            // traverse the DOM tree
            Node doc = parser.getDocument();
            domNodeVisitor(doc, true);
        }
        catch (Exception e)
        {
            throw new ExtractorException(e);
        }
    }

    /**
     * Overrides EntityResolver#resolveEntity.
     * 
     * The purpose of this method is to read Schemarules.dtd from resource and
     * feed it to the validating parser, but what it really does is returning a
     * null byte array to the XML parser.
     */
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException
    {
        return new InputSource(new ByteArrayInputStream(new byte[0]));
    }

    // ErrorHandler interface methods

    public void error(SAXParseException e) throws SAXException
    {
        throw new SAXException("XML parse error at\n  line "
                + e.getLineNumber() + "\n  column " + e.getColumnNumber()
                + "\n  Message:" + e.getMessage());
    }

    public void fatalError(SAXParseException e) throws SAXException
    {
        error(e);
    }

    public void warning(SAXParseException e)
    {
        System.err.println("Xliff parse warning at\n  line "
                + e.getLineNumber() + "\n  column " + e.getColumnNumber()
                + "\n  Message:" + e.getMessage());
    }

    private void outputXMLDeclaration()
    {
        outputSkeleton("<?xml");
        if (m_version != null)
        {
            outputSkeleton(" version=\"" + m_version + "\"");
        }
        if (encoding != null)
        {
            outputSkeleton(" encoding=\"" + encoding + "\"");
        }
        if (m_standalone != null)
        {
            outputSkeleton(" standalone=\"" + m_standalone + "\"");
        }
        outputSkeleton(" ?>\n");
    }

    private void docTypeProcessor(DocumentType docType)
    {
        String systemId = SegmentUtil.restoreEntity(docType.getSystemId());
        String publicId = SegmentUtil.restoreEntity(docType.getPublicId());
        String internalSubset = SegmentUtil.restoreEntity(docType.getInternalSubset());

        if (systemId != null || publicId != null || internalSubset != null)
        {
            outputSkeleton("<!DOCTYPE " + SegmentUtil.restoreEntity(docType.getName()) + " ");
            String externalId = null;

            if (systemId != null && publicId != null)
            {
                externalId = "PUBLIC \"" + publicId + "\" \"" + systemId + "\"";
            }
            else if (systemId != null)
            {
                externalId = "SYSTEM \"" + systemId + "\"";
            }

            if (externalId != null)
            {
                outputSkeleton(externalId);
            }
            if (internalSubset != null)
            {
                outputSkeleton(" [" + internalSubset + "]>\n");
            }
            else
            {
                outputSkeleton(">\n");
            }
        }
    }

    private void commentProcessor(Node p_node, boolean isTranslatable)
    {
        if (!processGsaSnippet(p_node.getNodeValue()))
        {
            String comment = "<!--" + p_node.getNodeValue() + "-->";
            outputSkeleton(SegmentUtil.restoreEntity(comment));
        }
        // else: the GSA snippet is to be ignored.
    }

    private void outputPi(Node p_node, boolean isTranslatable)
    {
        String piString = "<?" + p_node.getNodeName() + " "
                + p_node.getNodeValue() + "?>";
        outputSkeleton(piString);
    }

    private void cdataProcessor(Node p_node, boolean isTranslatable)
    {
        outputSkeleton("<![CDATA[" + SegmentUtil.restoreEntity(p_node.getNodeValue())
                + "]]>");
    }

    private void entityProcessor(Node p_node, boolean isTranslatable)
    {
        String entityTag = p_node.getNodeName();
        String name = "&" + entityTag + ";";
        outputSkeleton(name);
    }

    /**
     * A visitor that recursivly traverses the input document. Element nodes are
     * handed off to domElementProcessor().
     */
    private void domNodeVisitor(Node p_node, boolean isTranslatable)
            throws ExtractorException
    {
        while (true)
        {
            if (p_node == null)
            {
                return;
            }

            switch (p_node.getNodeType())
            {
                case Node.DOCUMENT_NODE: // the document itself XML Declaration
                    if (m_haveXMLDecl)
                        outputXMLDeclaration();

                    // Document Type Declaration <!DOCTYPE...>
                    DocumentType docType = ((Document) p_node).getDoctype();
                    if (docType != null)
                        docTypeProcessor(docType);

                    domNodeVisitor(p_node.getFirstChild(), isTranslatable);

                    return;

                case Node.PROCESSING_INSTRUCTION_NODE: // PI
                    outputPi(p_node, isTranslatable);
                    p_node = p_node.getNextSibling();

                    break;

                case Node.ELEMENT_NODE:
                    domElementProcessor(p_node);
                    p_node = p_node.getNextSibling();

                    break;

                case Node.COMMENT_NODE:
                    commentProcessor(p_node, isTranslatable);
                    p_node = p_node.getNextSibling();

                    break;

                case Node.ENTITY_REFERENCE_NODE:
                    entityProcessor(p_node, isTranslatable);
                    p_node = p_node.getNextSibling();

                    break;

                case Node.TEXT_NODE:
                    textProcessor(p_node, isTranslatable);
                    p_node = p_node.getNextSibling();

                    break;

                case Node.CDATA_SECTION_NODE:
                    // String cdata = "<![CDATA[" + p_node.getNodeValue() +
                    // "]]>";
                    // (CvdL: yes, this will lose CDATA nodes.)
                    // 2006-09-12 Updated For CDATA issue: this will pick up
                    // CDATA
                    // nodes.
                    cdataProcessor(p_node, isTranslatable);

                    p_node = p_node.getNextSibling();

                    break;

                default:
                    // shouldn't reach here.
                    // outputSkeleton(domDumpXML(p_node));
                    domNodeVisitor(p_node.getNextSibling(), isTranslatable);

                    return;
            }
        }
    }

    /**
     * Recursively processes an element node, its attributes and children. The
     * rules are consulted to determine whether the node needs to be extracted
     * etc. Attributes are handed off to outputAttributes(), and all nodes below
     * this node are passed to domNodeVisitor().
     */
    private void domElementProcessor(Node p_node) throws ExtractorException
    {
        String name = p_node.getNodeName();
        int bptIndex = m_admin.getBptIndex();
        boolean isEmbeddable = isEmbeddedNode(p_node);
        boolean isTranslatable = true;
        boolean isEmptyTag = p_node.getFirstChild() == null ? true : false;
        HashMap<String, String> map = getNodeTierInfo(p_node);
        String stuff = null;
        
        if(name.toLowerCase().equals("trans-unit")) {
            lastIndex = m_admin.getBptIndex();
        }

        if (isEmbeddable)
        {
            if (isEmptyTag)
            {
                if (map.get("xliffPart").equals("target"))
                {
                    // if target tags are more than source tags, then increase
                    // max sourceindex number to set target tag bpt index.
                    if (sourceIndex.size() > 0)
                    {
                        bptIndex = (Integer) sourceIndex.get(0);
                    }
                    else
                    {
                        bptIndex = m_admin.incrementBptIndex();
                    }
                }

                stuff = "<ph type=\"" + name + "\" id=\"" + bptIndex
                        + "\" x=\"" + bptIndex + "\"";
                stuff = stuff + ">&lt;" + name
                        + getAttributeString(p_node.getAttributes());
                stuff = stuff + "&gt;&lt;/" + name + "&gt;</ph>";

                if (map.get("xliffPart").equals("source"))
                {
                    bptIndex = m_admin.incrementBptIndex();
                    sourceIndex.add(bptIndex);
                    sourceContent = sourceContent + "<" + name
                            + getAttributeString(p_node.getAttributes(), false) + ">";
                    tuvSourceContent = tuvSourceContent + stuff;
                }
                else if (map.get("xliffPart").equals("target"))
                {
                    tuvTargetContent = tuvTargetContent + stuff;
                    targetContent = targetContent + "<" + name
                            + getAttributeString(p_node.getAttributes(), false) + ">";
                    if (sourceIndex.size() > 0)
                    {
                        sourceIndex.remove(0);
                    }
                }
                else if (map.get("xliffPart").equals("altSource"))
                {
                    sourceContent = sourceContent + "<" + name
                            + getAttributeString(p_node.getAttributes(), false)
                            + ">";
                    tuvAltSource = tuvAltSource + "<" + name
                            + getAttributeString(p_node.getAttributes()) + ">";
                }
                else if (map.get("xliffPart").equals("altTarget"))
                {
                    tuvTargetContent = tuvTargetContent + "<" + name
                            + getAttributeString(p_node.getAttributes(), false)
                            + ">";
                    tuvAltTarget = tuvAltTarget + "<" + name
                            + getAttributeString(p_node.getAttributes()) + ">";
                }
            }
            else
            {
                if (map.get("xliffPart").equals("target"))
                {
                    if (sourceIndex.size() > 0)
                    {
                        bptIndex = (Integer) sourceIndex.get(0);
                        sourceIndex.remove(0);
                    }
                    else
                    {
                        bptIndex = m_admin.incrementBptIndex();
                    }
                }

                if (isInlineTag(name))
                {
                    stuff = "<ph type=\"" + name + "\" id=\"" + bptIndex
                            + "\" x=\"" + bptIndex + "\"";
                    stuff += ">&lt;" + name
                            + getAttributeString(p_node.getAttributes());
                    stuff = stuff + "&gt;";
                }
                else
                {
                    boolean isTranslate = true;
                    if (checkTextNode(p_node))
                    {
                        isTranslate = true;
                    }
                    else
                    {
                        isTranslate = false;
                    }

                    stuff = "<bpt i=\"" + bptIndex + "\" type=\"" + name
                            + "\" isTranslate=\"" + isTranslate + "\" x=\""
                            + bptIndex + "\"";
                    stuff += ">&lt;" + name
                            + getAttributeString(p_node.getAttributes())
                            + "&gt;</bpt>";
                }

                if (map.get("xliffPart").equals("source"))
                {
                    bptIndex = m_admin.incrementBptIndex();
                    sourceIndex.add(bptIndex);
                    sourceContent = sourceContent + "<" + name
                            + getAttributeString(p_node.getAttributes(), false)
                            + ">";

                    if (isEmbeddedInline(p_node))
                    {
                        tuvSourceContent = tuvSourceContent
                                + "&lt;"
                                + name
                                + getAttributeString(p_node.getAttributes(),
                                        false) + "&gt;";
                    }
                    else
                    {
                        tuvSourceContent = tuvSourceContent + stuff;
                    }
                }
                else if (map.get("xliffPart").equals("altSource"))
                {
                    sourceContent = sourceContent + "<" + name
                            + getAttributeString(p_node.getAttributes(), false)
                            + ">";
                    tuvAltSource = tuvAltSource + "<" + name
                            + getAttributeString(p_node.getAttributes()) + ">";
                }
                else if (map.get("xliffPart").equals("altTarget"))
                {
                    tuvTargetContent = tuvTargetContent + "<" + name
                            + getAttributeString(p_node.getAttributes(), false)
                            + ">";
                    tuvAltTarget = tuvAltTarget + "<" + name
                            + getAttributeString(p_node.getAttributes()) + ">";
                }
                else if (map.get("xliffPart").equals("target"))
                {
                    targetContent = targetContent + "<" + name
                            + getAttributeString(p_node.getAttributes(), false) + ">";
                    
                    if (isEmbeddedInline(p_node))
                    {
                        tuvTargetContent = tuvTargetContent + "&lt;" + name
                                + getAttributeString(p_node.getAttributes())
                                + "&gt;";
                    }
                    else
                    {
                        tuvTargetContent = tuvTargetContent + stuff;
                    }
                }
            }
        }
        else
        {
            outputSkeleton("<" + name);
            NamedNodeMap attrs = p_node.getAttributes();
            outputAttributes(attrs, isEmbeddable);
            outputSkeleton(">");
        }

        // Traverse the tree
        domNodeVisitor(p_node.getFirstChild(), isTranslatable);

        if (isEmbeddable)
        {
            if (map.get("xliffPart").equals("source"))
            {
                sourceContent = sourceContent + "</" + name + ">";

                if (isEmbeddedInline(p_node))
                {
                    tuvSourceContent = tuvSourceContent + "&lt;/" + name
                            + "&gt;";
                }
                else
                {
                    if (isInlineTag(name))
                    {
                        if (!isEmptyTag)
                        {
                            tuvSourceContent = tuvSourceContent + "&lt;/"
                                    + name + "&gt;</ph>";
                        }
                    }
                    else
                    {
                        if (!isEmptyTag)
                        {
                            tuvSourceContent = tuvSourceContent + "<ept i=\""
                                    + bptIndex + "\">&lt;/" + name
                                    + "&gt;</ept>";
                        }
                    }
                }
            }
            else if (map.get("xliffPart").equals("altSource"))
            {
                sourceContent = sourceContent + "</" + name + ">";
                tuvAltSource = tuvAltSource + "</" + name + ">";
            }
            else if (map.get("xliffPart").equals("altTarget"))
            {
                tuvTargetContent = tuvTargetContent + "</" + name + ">";
                tuvAltTarget = tuvAltTarget + "</" + name + ">";
            }
            else if (map.get("xliffPart").equals("target"))
            {
                targetContent = targetContent + "</" + name + ">";
                /*
                 * if (name.equals("bpt") || name.equals("ept")) { targetContent
                 * = targetContent + "</" + name + ">"; } else
                 */
                if (isEmbeddedInline(p_node))
                {
                    tuvTargetContent = tuvTargetContent + "&lt;/" + name + "&gt;";
                }
                else
                {
                    if (isInlineTag(name))
                    {
                        if (!isEmptyTag)
                            tuvTargetContent = tuvTargetContent + "&lt;/" + name
                                    + "&gt;</ph>";
                    }
                    else
                    {
                        if (!isEmptyTag)
                            tuvTargetContent = tuvTargetContent + "<ept i=\""
                                    + bptIndex + "\">&lt;/" + name
                                    + "&gt;</ept>";
                    }
                }
            }
        }
        else
        {
            if (isEmptyTag)
            {
                /*
                 * @ by walter.xu if the target content is "", such as
                 * <target></target>, there will be no text child node of
                 * <target> element node, should output " " to "translatable",
                 * so that there will be an GXML node of "target", the next
                 * process will deal with it. If not do this, the target process
                 * when create tu will never be done.
                 */
                if (name.equals("source")
                        && map.get("xliffPart").equals("source"))
                {
                    // if source is empty or null, only write into skeleton.
                    outputSkeleton(sourceContent);
                }
                else if (map.get("xliffPart").equals("target")
                        && name.equals("target"))
                {
                    // if the coresponding source is null or empty, even if the
                    // target is not null, only write into skeleton.
                    if (checkSourceIsNull(contentNoTag))
                    {
                        outputSkeleton(targetContent);
                        m_admin.setBptIndex(lastIndex);
                    }
                    else
                    {
                        outputExtractedStuff(" ", isTranslatable, map, false);
                    }

                    sourceContent = "";
                    tuvSourceContent = "";
                    targetContent = "";
                    tuvTargetContent = "";
                    contentNoTag = "";
                    sourceIndex.clear();
                }
                else if (map.get("xliffPart").equals("altSource"))
                {
                    outputSkeleton(sourceContent);
                }
                else if (name.equals("target")
                        && map.get("xliffPart").equals("altTarget"))
                {
                    outputSkeleton(tuvTargetContent);
                    sourceContent = "";
                    tuvTargetContent = "";
                    tuvAltSource = "";
                    tuvAltTarget = "";
                }
            }
            else
            {
                if (name.equals("source"))
                {
                    if (map.get("xliffPart").equals("source"))
                    {
                        outputSkeleton(sourceContent);

                        if (!checkSourceIsNull(contentNoTag))
                        {
                            outputExtractedStuff(tuvSourceContent,
                                    isTranslatable, map, true);
                        }
                    }
                    else if (map.get("xliffPart").equals("altSource"))
                    {
                        outputSkeleton(sourceContent);
                        
                        if (!checkAltSourceOrTargetIsEmpty(p_node, "target"))
                        {
                            outputExtractedStuff(tuvAltSource, isTranslatable,
                                    map, true);
                        }
                        else {
                            sourceContent = "";
                            tuvTargetContent = "";
                            tuvAltSource = "";
                            tuvAltTarget = "";
                        }
                    }
                }
                else if (name.equals("target")
                        && map.get("xliffPart").equals("altTarget"))
                {
                    outputSkeleton(tuvTargetContent);
                    
                    if (!checkAltSourceOrTargetIsEmpty(p_node, "source"))
                    {
                        outputExtractedStuff(tuvAltTarget, isTranslatable, map,
                                false);
                    }
                    
                    sourceContent = "";
                    tuvTargetContent = "";
                    tuvAltSource = "";
                    tuvAltTarget = "";
                }
                else if (map.get("xliffPart").equals("target")
                        && name.equals("target"))
                {
                    if (!checkSourceIsNull(contentNoTag))
                    {
                        outputExtractedStuff(tuvTargetContent, isTranslatable,
                                map, false);
                    }
                    else
                    {
                        m_admin.setBptIndex(lastIndex);
                        outputSkeleton(targetContent);
                    }

                    sourceContent = "";
                    tuvSourceContent = "";
                    targetContent = "";
                    tuvTargetContent = "";
                    contentNoTag = "";
                    sourceIndex.clear();
                }
            }

            outputSkeleton("</" + name + ">");

            // if a trans unit only source part, add target part and set the
            // target content same as source content.
            if (name.equals("source") && map.get("xliffPart").equals("source"))
            {
                if (!checkHaveTargetPart(p_node, "trans-unit"))
                {
                    if (!checkSourceIsNull(contentNoTag))
                    {
                        HashMap<String, String> newMap = new HashMap<String, String>();
                        newMap.putAll(map);
                        newMap.put("xliffPart", "target");
                        outputSkeleton("<target>");
                        outputExtractedStuff(tuvSourceContent, isTranslatable,
                                newMap, false);
                        outputSkeleton("</target>");

                        sourceContent = "";
                        tuvSourceContent = "";
                        targetContent = "";
                        tuvTargetContent = "";
                        contentNoTag = "";
                    }
                    else
                    {
                        m_admin.setBptIndex(lastIndex);
                    }
                }
            }
        }
    }

    private void textProcessor(Node p_node, boolean isTranslatable)
    {
        HashMap<String, String> map = getNodeTierInfo(p_node);
        String parentName = p_node.getParentNode().getNodeName();

        String nodeValue = SegmentUtil.restoreEntity(p_node.getNodeValue());
        String encodeString = new String();

        if (map.get("xliffPart").equals("source")
                || map.get("xliffPart").equals("target"))
        {
            /*
             * If there are "&lt;content&gt;", need to wrap it by place holder
             */
            if (!isInlineTag(parentName))
            {
                // first, encode other tags except "&lt" and "&gt"
                ArrayList array = new ArrayList();
                array.add("&amp;");
                array.add("&quot;");
                array.add("&apos;");
                array.add("&#xd;");
                array.add("&#x9;");
                String tuvValue = SegmentUtil.restoreEntity(p_node.getNodeValue(), array);
                tuvValue = protectElement(tuvValue);

                // then wrap the "&lt" and "&gt" to become place holder
                encodeString = m_xmlEncoder.encodeStringBasic(tuvValue);
                array.clear();
                array.add("&lt;");
                array.add("&gt;");
                encodeString = SegmentUtil.restoreEntity(encodeString, array);
                encodeString = ltgtProcess(encodeString, map.get("xliffPart"));
                // wrap the "&nbsp;"
                encodeString = packageProtectElement(encodeString, map
                        .get("xliffPart"));
            }
            else {
                encodeString = m_xmlEncoder.encodeStringBasic(nodeValue);
            }
        }
        else
        {
            encodeString = m_xmlEncoder.encodeStringBasic(nodeValue);
        }

        if (map.get("xliffPart").equals("source"))
        {
            sourceContent = sourceContent + nodeValue;
            tuvSourceContent = tuvSourceContent + encodeString;
        }
        else if (map.get("xliffPart").equals("altSource"))
        {
            sourceContent = sourceContent + nodeValue;
            tuvAltSource = tuvAltSource + encodeString;
        }
        else if (map.get("xliffPart").equals("altTarget"))
        {
            tuvTargetContent = tuvTargetContent + nodeValue;
            tuvAltTarget = tuvAltTarget + encodeString;
        }
        else if (map.get("xliffPart").equals("target"))
        {
            targetContent = targetContent + nodeValue;
            tuvTargetContent = tuvTargetContent + encodeString;
        }
        else if (parentName.equals("note"))
        {
            outputSkeleton(nodeValue);
        }
        else
        {
            outputSkeleton(nodeValue);
        }
    }
    
    private int getbptIndex(String xliffPart) {
        int bptIndex = 0;

        if (xliffPart.equals("source"))
        {
            bptIndex = m_admin.incrementBptIndex();
            sourceIndex.add(bptIndex);
        }
        else if (xliffPart.equals("target"))
        {
            if (sourceIndex.size() > 0)
            {
                bptIndex = (Integer) sourceIndex.get(0);
                sourceIndex.remove(0);
            }
            else
            {
                bptIndex = m_admin.incrementBptIndex();
            }
        }
        
        return bptIndex;
    }

    private String packageProtectElement(String p_str, String xliffPart)
    {
        ArrayList array = getNeedProtectedElements();

        for (int i = 0; i < array.size(); i++)
        {
            String eleName = (String) array.get(i);

            if (eleName.equals("nbsp"))
            {
                if (p_str.indexOf("_xliff_nbsp_tag") > -1)
                {
                    String temp = "<ph x=\""
                            + getbptIndex(xliffPart)
                            + "\" type=\"x-nbspace\" erasable=\"yes\">&amp;amp;nbsp;</ph> ";
                    p_str = p_str.replaceAll("_xliff_nbsp_tag", temp);
                    
                    if (xliffPart.equals("source"))
                    {
                        contentNoTag = contentNoTag.replaceAll(
                                "_xliff_nbsp_tag", "");
                    }
                }
            }
            else if (eleName.equals("xa"))
            {
                if (p_str.indexOf("_xliff_xa_tag") > -1)
                {
                    String temp = "<ph x=\"" + getbptIndex(xliffPart)
                            + "\" type=\"xa\" erasable=\"yes\">&amp;#xa;</ph> ";
                    p_str = p_str.replaceAll("_xliff_xa_tag", temp);
                    
                    if (xliffPart.equals("source"))
                    {
                        contentNoTag = contentNoTag.replaceAll("_xliff_xa_tag",
                                "");
                    }
                }
            }
        }

        return p_str;
    }

    // protect the element not to be encoded.
    private String protectElement(String p_str)
    {
        ArrayList array = getNeedProtectedElements();
        // needn't protect "&#xa;", because it has been protected in the
        // AbstractExtractor
        for (int i = 0; i < array.size(); i++)
        {
            String eleName = (String) array.get(i);

            if (eleName.equalsIgnoreCase("nbsp"))
            {
                p_str = p_str.replaceAll("&amp;nbsp;", "_xliff_nbsp_tag");
            }
        }

        return p_str;
    }

    /*
     * The &amp;nbsp; or "&#xa;" need to be wrapped into ph tag
     */
    private ArrayList getNeedProtectedElements()
    {
        ArrayList array = new ArrayList();
        array.add("nbsp");
        array.add("xa");

        return array;
    }

    /*
     * Wrap the "&lt;content &gt;" into <ph> tag
     */
    private String ltgtProcess(String p_str, String xliffPart)
    {
        boolean ltflag = false;
        int begin = 0;
        int end = 0;
        int lastEnd = 0;
        int bptIndex = 0;
        String newStr = new String();

        for (int i = 0; i < p_str.length(); i++)
        {
            if (p_str.charAt(i) == '&' && p_str.charAt(i + 1) == 'l'
                    && p_str.charAt(i + 2) == 't')
            {
                if (!ltflag)
                {
                    ltflag = true;
                    begin = i;
                }
            }

            if (p_str.charAt(i) == '&' && p_str.charAt(i + 1) == 'g'
                    && p_str.charAt(i + 2) == 't' && ltflag)
            {
                if (ltflag)
                {
                    ltflag = false;
                    end = i;
                }

                if (end > 0 && end > begin)
                {
                    String str = p_str.substring(begin + 4, end);
                    StringBuffer sb = new StringBuffer();

                    if (xliffPart.equals("source"))
                    {
                        bptIndex = m_admin.incrementBptIndex();
                        sourceIndex.add(bptIndex);
                    }
                    else if (xliffPart.equals("target"))
                    {
                        if (sourceIndex.size() > 0)
                        {
                            bptIndex = (Integer) sourceIndex.get(0);
                            sourceIndex.remove(0);
                        }
                        else
                        {
                            bptIndex = m_admin.incrementBptIndex();
                        }
                    }

                    sb = sb.append("<ph type=\"ltgt\" id=\"");

                    sb = sb.append(bptIndex).append("\" x=\"").append(bptIndex);
                    sb = sb.append("\">&amp;lt;").append(str).append(
                            "&amp;gt;</ph>");
                    newStr = newStr + p_str.substring(lastEnd, begin)
                            + sb.toString();
                    
                    if (xliffPart.equals("source"))
                    {
                        contentNoTag = contentNoTag
                                + p_str.substring(lastEnd, begin);
                    }
                }

                lastEnd = end + 4;
            }
        }
        
        if (xliffPart.equals("source"))
        {
            if (lastEnd == 0)
            {
                contentNoTag = contentNoTag + p_str;
            }
            else if (lastEnd > 0 && lastEnd < p_str.length())
            {
                contentNoTag = contentNoTag
                        + p_str.substring(lastEnd, p_str.length());
            }
        }

        if (lastEnd < p_str.length())
        {
            newStr = newStr + p_str.substring(lastEnd, p_str.length());
        }

        return newStr;
    }

    /*
     * Judge if the tag is inline tag. such as "<ph> pContent1 <aaa> content
     * </aaa> pContent2 </ph>", the <aaa> tag will not be exracted, and just
     * package the <ph> tag, and treat " <aaa> content</aaa>" as ph tag inner
     * content and not shown on segment editor.
     */
    private boolean isInlineTag(String tagName)
    {
        String[] tagArray =
        { "it", "ph", "bpt", "ept" };
        List list = Arrays.asList(tagArray);

        if (list.contains(tagName))
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    /*
     * Judge if a tag is a inner tag of inline tag. For example, the "ph" tag is
     * an inline tag, and there is "<ph> pContent1 <aaa> content </aaa>
     * pContent2 </ph>", the <aaa> tag is an embedded inline tag. It will not be
     * extracted.
     */
    private boolean isEmbeddedInline(Node node)
    {
        Node parentNode = node.getParentNode();

        while (parentNode != null)
        {
            String name = parentNode.getNodeName();

            if (isInlineTag(name))
            {

                return true;
            }

            parentNode = parentNode.getParentNode();
        }

        return false;
    }

    /**
     * Checks if this node is a child of source or target.
     * 
     * @param node
     *            The current node.
     * 
     * @return true or false (true: embedded; false: not embedded).
     */
    private boolean isEmbeddedNode(Node node)
    {
        if (node.getNodeName().equals("source")
                || node.getNodeName().equals("target"))
        {
            return false;
        }

        HashMap<String, String> map = getNodeTierInfo(node);

        if (map.get("xliffPart") != null
                && map.get("xliffPart").equals("altSource")
                || map.get("xliffPart").equals("altTarget")
                || map.get("xliffPart").equals("source")
                || map.get("xliffPart").equals("target"))
        {
            return true;
        }

        return false;
    }

    /*
     * Check if the source content only have tag and no other text content.
     */
    private boolean checkSourceIsNull(String sourceContent)
    {
        if (sourceContent == null)
        {
            return true;
        }
        else if (Text.isBlank(sourceContent))
        {
            return true;
        }
        
        return false;
    }

    private HashMap<String, String> getNodeTierInfo(Node node)
    {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("xliffPart", "");
        Node parentNode = node;

        while (parentNode != null)
        {
            String name = parentNode.getNodeName();

            if ("source".equals(name) || "target".equals(name))
            {
                if (parentNode.getParentNode() != null)
                {
                    if (parentNode.getParentNode().getNodeName()
                            .equalsIgnoreCase("alt-trans"))
                    {
                        if ("source".equals(name))
                        {
                            map.put("xliffPart", "altSource");
                        }
                        else if ("target".equals(name))
                        {
                            map.put("xliffPart", "altTarget");
                        }

                        NamedNodeMap attrs = parentNode.getAttributes();
                        String attname = null;

                        for (int i = 0; i < attrs.getLength(); ++i)
                        {
                            Node att = attrs.item(i);
                            attname = att.getNodeName();
                            String value = att.getNodeValue();

                            if (attname.equals("xml:lang"))
                            {
                                map.put("altLanguage", value);
                                break;
                            }
                        }

                        NamedNodeMap grandAttrs = parentNode.getParentNode()
                                .getAttributes();

                        for (int i = 0; i < grandAttrs.getLength(); ++i)
                        {
                            Node att = grandAttrs.item(i);
                            attname = att.getNodeName();
                            String value = att.getNodeValue();

                            if (attname.equals("match-quality"))
                            {
                                map.put("altQuality", value);
                            }
                        }
                    }
                    else if (parentNode.getParentNode().getNodeName()
                            .equalsIgnoreCase("trans-unit"))
                    {
                        if ("source".equals(name))
                        {
                            map.put("xliffPart", "source");
                        }
                        else if ("target".equals(name))
                        {
                            map.put("xliffPart", "target");
                        }

                        NamedNodeMap grandAttrs = parentNode.getParentNode()
                                .getAttributes();
                        String attname = null;

                        for (int i = 0; i < grandAttrs.getLength(); ++i)
                        {
                            Node att = grandAttrs.item(i);
                            attname = att.getNodeName();
                            String value = att.getNodeValue();

                            if (attname.equals("id"))
                            {
                                map.put("tuID", value);
                            }
                        }

                        Node grandNode = parentNode.getParentNode();
                        NodeList childs = grandNode.getChildNodes();

                        for (int i = 0; i < childs.getLength(); i++)
                        {
                            Node childNode = childs.item(i);

                            if (childNode.getNodeName().equalsIgnoreCase(
                                    IWS_SEGMENT_DATA))
                            {
                                NodeList segNodes = childNode.getChildNodes();

                                for (int j = 0; j < segNodes.getLength(); j++)
                                {
                                    Node segNode = segNodes.item(j);
                                    if (IWS_STATUS.equalsIgnoreCase(segNode.getNodeName()))
                                    {
                                        NamedNodeMap attrs = segNode.getAttributes();

                                        for (int x = 0; x < attrs.getLength(); x++)
                                        {
                                            Node attr = attrs.item(x);
                                            // translation_type
                                            if (IWS_TRANSLATION_TYPE.equals(attr.getNodeName())
                                                    && IWS_TRANSLATION_MT.equals(attr.getNodeValue()))
                                            {
                                                map.put(IWS_TRANSLATION_TYPE, IWS_TRANSLATION_MT);
                                            }
                                            // source_content
                                            if (IWS_SOURCE_CONTENT.equals(attr.getNodeName()))
                                            {
                                                String sourceContent = attr.getNodeValue();
                                                if (sourceContent != null && sourceContent.trim().length() > 0)
                                                {
                                                    map.put(IWS_SOURCE_CONTENT, sourceContent);                                                    
                                                }
                                            }
                                        }
                                    }
                                }

                                NamedNodeMap attrs = childNode.getAttributes();

                                for (int x = 0; x < attrs.getLength(); x++)
                                {
                                    Node attr = attrs.item(x);
                                    if (attr.getNodeName().equals(IWS_TM_SCORE))
                                    {
                                        map.put(IWS_TM_SCORE, attr
                                                .getNodeValue());
                                    }
                                    else if (attr.getNodeName().equals(IWS_SID))
                                    {
                                        map.put(IWS_SID, attr.getNodeValue());
                                    }
                                }

                            }
                        }
                    }
                }

                break;
            }

            parentNode = parentNode.getParentNode();
        }

        return map;
    }

    /*
     * Some trans-unit or alt-trans does not have target part. This method used to check if
     * the source part have the responsible target part. If have not, just treat
     * as empty target content.
     */
    private boolean checkHaveTargetPart(Node node, String parentName)
    {
        Node parentNode = node.getParentNode();

        if (parentNode.getNodeName().equalsIgnoreCase(parentName))
        {
            NodeList nodes = parentNode.getChildNodes();

            for (int i = 0; i < nodes.getLength(); i++)
            {
                Node childNode = nodes.item(i);

                if (childNode.getNodeName().equalsIgnoreCase("target"))
                {
                    return true;
                }
            }
        }

        return false;
    }
    
    private boolean checkAltSourceOrTargetIsEmpty(Node node, String nodeName) {
        Node parentNode = node.getParentNode();

        if (parentNode.getNodeName().equalsIgnoreCase("alt-trans"))
        {
            NodeList nodes = parentNode.getChildNodes();

            for (int i = 0; i < nodes.getLength(); i++)
            {
                Node childNode = nodes.item(i);

                if (childNode.getNodeName().equalsIgnoreCase(nodeName))
                {
                    boolean isEmpty = 
                        childNode.getFirstChild() == null ? true : false;
                    return isEmpty;
                }
            }
        }
        
        return true;
    }
    
    private String getTextNodeIndex(Node p_node)
    {
        String s = "";
        NodeList nodes = p_node.getChildNodes();
        if (nodes.getLength() > 0)
        {
            for (int i = 0; i < nodes.getLength(); i++)
            {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.TEXT_NODE)
                {
                    s += i + ",";
                }
            }
        }
        return s;
    }

    /**
     * Check if text node exists
     * 
     * @param p_node
     * @return true if text node exists
     */
    private boolean checkTextNode(Node p_node)
    {
        return getTextNodeIndex(p_node).length() != 0;
    }

    /**
     * <p>
     * Outputs the attributes of the element node being processed by
     * domElementProcessor().
     * </p>
     * 
     * <p>
     * Note that the <code>isInTranslatable</code> argument is not used.
     * </p>
     */
    private void outputAttributes(NamedNodeMap attrs, boolean isEmbeded)
            throws ExtractorException
    {
        if (attrs == null)
        {
            return;
        }
        for (int i = 0; i < attrs.getLength(); ++i)
        {
            Node att = attrs.item(i);
            String attname = att.getNodeName();
            String value = att.getNodeValue();

            if (isEmbeded)
            {
                String stuff = null;

                stuff = " " + attname + "=\"" + SegmentUtil.restoreEntity(value) + "\"";

                m_admin.addContent(stuff);
            }
            else
            {
                outputSkeleton(" " + attname + "=\"" + SegmentUtil.restoreEntity(value)
                        + "\"");
            }
        }
    }

    private String getAttributeString(NamedNodeMap attrs)
    {
        return getAttributeString(attrs, true);
    }

    private String getAttributeString(NamedNodeMap attrs, boolean isEncode)
    {
        String temp = new String();

        if (attrs == null)
        {
            return null;
        }

        for (int i = 0; i < attrs.getLength(); ++i)
        {
            Node att = attrs.item(i);
            String attname = att.getNodeName();
            String value = SegmentUtil.restoreEntity(att.getNodeValue());

            if (isEncode)
            {
                temp = temp + " " + attname + "=\""
                        + m_xmlEncoder.encodeStringBasic(value) + "\"";
            }
            else
            {
                temp = temp + " " + attname + "=\"" + value + "\"";
            }
        }

        return temp;
    }

    /*
     * Get <bpt> and <ept> tags attribute. <bpt> <ept> must have "i" "x"
     * attribute in GS. but the import file's tag maybe have no "i" or "x"
     * atribute, so if it hasn't the two attributes, generate them and add them
     * into the <bpt> <ept> tag.
     * 
     * private String getBptEptAttribute(String name, NamedNodeMap attrs) {
     * String temp = new String();
     * 
     * if (attrs == null) { return null; }
     * 
     * int index = m_admin.getBptIndex();
     * 
     * if (name.equals("ept")) { index = m_admin.incrementBptIndex(); }
     * 
     * boolean iAttribute = false; boolean xAttribute = false;
     * 
     * for (int i = 0; i < attrs.getLength(); ++i) { Node att = attrs.item(i);
     * String attname = att.getNodeName(); String value = att.getNodeValue();
     * 
     * if (attname.equalsIgnoreCase("i")) { temp = temp + " i=\"" + value +
     * "\""; iAttribute = true; } else if (attname.equalsIgnoreCase("x")) { temp
     * = temp + " x=\"" + value + "\""; xAttribute = true; } else { temp = temp
     * + " " + attname + "=\"" + DecodeEntity(value) + "\""; } }
     * 
     * if (!iAttribute) { temp = temp + " i=\"" + index + "\""; }
     * 
     * if (!xAttribute && name.equals("bpt")) { temp = temp + " x=\"" + index +
     * "\""; }
     * 
     * return temp; }
     */
    /**
     * Output translatable text
     */
    private void outputTranslatable(String p_ToAdd,
            Map<String, String> xliffAttributes, boolean p_blankTextAsSkeleton)
    {
        if (m_admin.getOutputType() != OutputWriter.TRANSLATABLE)
        {
            m_admin.reset(new TranslatableWriter(getOutput()),
                    p_blankTextAsSkeleton);
            m_admin.setXliffTransPart(xliffAttributes);
        }

        m_admin.addContent(p_ToAdd);
    }

    /**
     * Output localizable text
     */
    private void outputLocalizable(String p_ToAdd)
    {
        if (m_admin.getOutputType() != OutputWriter.LOCALIZABLE)
        {
            m_admin.reset(new LocalizableWriter(getOutput()));
        }
        m_admin.addContent(p_ToAdd);
    }

    /**
     * Outputs skeleton text.
     */
    private void outputSkeleton(String p_ToAdd)
    {
        if (m_admin.getOutputType() != OutputWriter.SKELETON)
        {
            m_admin.reset(new SkeletonWriter(getOutput()));
        }
        m_admin.addContent(p_ToAdd);
    }

    /**
     * Utility function that outputs translatable or localizable text.
     * 
     * @param stuff
     * @param isTranslatable
     * @param xliffAttributes
     * @param p_blankTextAsSkeleton
     *            For target content,"stuff" will be output to "translatable"
     *            whatever it is blank. For others,if "stuff" is blank, it will
     *            be output to "skeleton" part.
     */
    private void outputExtractedStuff(String stuff, boolean isTranslatable,
            Map<String, String> xliffAttributes, boolean p_blankTextAsSkeleton)
    {
        if (isTranslatable)
        {
            outputTranslatable(stuff, xliffAttributes, p_blankTextAsSkeleton);
        }
        else
        {
            outputLocalizable(stuff);
        }
    }

    /**
     * Processes a GSA comment. If it's GSA snippet, adds the GSA tag to the
     * Output object and returns true.
     */
    private boolean processGsaSnippet(String comments)
            throws ExtractorException
    {
        try
        {
            RegExMatchInterface match = RegEx.matchSubstring(comments,
                    "^\\s*gs\\s", false);

            if (match == null)
            {
                match = RegEx.matchSubstring(comments, "^\\s*/gs", false);
                if (match == null)
                {
                    return false;
                }

                outputGsaEnd();
                return true;
            }

            boolean delete = false;
            String extract = null;
            String description = null;
            String locale = null;
            String add = null;
            String added = null;
            String deleted = null;
            String snippetName = null;
            String snippetId = null;

            match = RegEx.matchSubstring(comments,
                    "\\sadd\\s*=\\s*\"([^\"]+)\"\\s", false);
            if (match != null)
            {
                add = match.group(1);
            }

            match = RegEx.matchSubstring(comments,
                    "\\sextract\\s*=\\s*\"([^\"]+)\"\\s", false);
            if (match != null)
            {
                extract = match.group(1);
            }

            match = RegEx.matchSubstring(comments,
                    "\\sdescription\\s*=\\s*\"([^\"]+)\"\\s", false);
            if (match != null)
            {
                description = match.group(1);
            }

            match = RegEx.matchSubstring(comments,
                    "\\slocale\\s*=\\s*\"([^\"]+)\"\\s", false);
            if (match != null)
            {
                locale = match.group(1);
            }

            match = RegEx.matchSubstring(comments,
                    "\\sname\\s*=\\s*\"([^\"]+)\"\\s", false);
            if (match != null)
            {
                snippetName = match.group(1);
            }

            match = RegEx.matchSubstring(comments,
                    "\\sid\\s*=\\s*\"([^\"]+)\"\\s", false);
            if (match != null)
            {
                snippetId = match.group(1);
            }

            match = RegEx.matchSubstring(comments,
                    "\\sdelete\\s*=\\s*\"?(1|yes|true)\"?\\s", false);
            if (match != null)
            {
                delete = true;
            }

            match = RegEx.matchSubstring(comments,
                    "\\sdeleted\\s*=\\s*\"([^\"]+)\"\\s", false);
            if (match != null)
            {
                deleted = match.group(1);
            }

            match = RegEx.matchSubstring(comments,
                    "\\sadded\\s*=\\s*\"([^\"]+)\"\\s", false);
            if (match != null)
            {
                added = match.group(1);
            }

            outputGsaStart(extract, description, locale, add, delete, added,
                    deleted, snippetName, snippetId);
        }
        catch (RegExException e)
        {
            // Shouldn't reach here.
            System.err.println("Malformed re pattern in XML extractor.");
        }

        return true;
    }

    private void outputGsaStart(String extract, String description,
            String locale, String add, boolean delete, String added,
            String deleted, String snippetName, String snippetId)
            throws ExtractorException
    {
        m_admin.reset(null);

        try
        {
            getOutput().addGsaStart(extract, description, locale, add, delete,
                    added, deleted, snippetName, snippetId);
        }
        catch (DocumentElementException ex)
        {
            throw new ExtractorException(HTML_GS_TAG_ERROR, ex.toString());
        }
    }

    private void outputGsaEnd()
    {
        m_admin.reset(null);

        getOutput().addGsaEnd();
    }

    @Override
    public void loadRules() throws ExtractorException
    {
    }
}