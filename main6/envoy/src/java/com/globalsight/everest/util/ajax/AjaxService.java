package com.globalsight.everest.util.ajax;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Iterator;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.globalsight.cxe.entity.filterconfiguration.Filter;
import com.globalsight.cxe.entity.filterconfiguration.FilterHelper;
import com.globalsight.cxe.entity.filterconfiguration.HtmlFilter;
import com.globalsight.cxe.entity.filterconfiguration.HtmlInternalTag;
import com.globalsight.cxe.entity.filterconfiguration.InddFilter;
import com.globalsight.cxe.entity.filterconfiguration.InternalTagException;
import com.globalsight.cxe.entity.filterconfiguration.JSPFilter;
import com.globalsight.cxe.entity.filterconfiguration.JavaScriptFilter;
import com.globalsight.cxe.entity.filterconfiguration.JsonUtil;
import com.globalsight.cxe.entity.filterconfiguration.MSOffice2010Filter;
import com.globalsight.cxe.entity.filterconfiguration.MSOfficeDocFilter;
import com.globalsight.cxe.entity.filterconfiguration.MSOfficePPTFilter;
import com.globalsight.cxe.entity.filterconfiguration.OpenOfficeFilter;
import com.globalsight.cxe.entity.filterconfiguration.POFilter;
import com.globalsight.cxe.entity.filterconfiguration.RemoveInfo;
import com.globalsight.cxe.entity.filterconfiguration.SpecialFilterToDelete;
import com.globalsight.cxe.entity.filterconfiguration.XMLRuleFilter;
import com.globalsight.cxe.entity.filterconfiguration.XmlFilterConfigParser;
import com.globalsight.everest.company.CompanyThreadLocal;
import com.globalsight.everest.foundation.L10nProfile;
import com.globalsight.everest.foundation.User;
import com.globalsight.everest.gsedition.GSEdition;
import com.globalsight.everest.gsedition.GSEditionManagerLocal;
import com.globalsight.everest.servlet.util.ServerProxy;
import com.globalsight.everest.servlet.util.SessionManager;
import com.globalsight.everest.webapp.WebAppConstants;
import com.globalsight.everest.webapp.pagehandler.PageHandler;
import com.globalsight.everest.webapp.pagehandler.administration.users.UserUtil;
import com.globalsight.everest.webapp.pagehandler.projects.l10nprofiles.LocProfileHandlerHelper;
import com.globalsight.everest.webapp.pagehandler.tasks.TaskHelper;
import com.globalsight.everest.webapp.pagehandler.terminology.management.FileUploadHelper;
import com.globalsight.ling.common.XmlEntities;
import com.globalsight.log.GlobalSightCategory;
import com.globalsight.persistence.hibernate.HibernateUtil;
import com.globalsight.terminology.ITermbase;
import com.globalsight.terminology.TermbaseException;
import com.globalsight.util.AmbFileStoragePathUtils;
import com.globalsight.webservices.client.Ambassador;
import com.globalsight.webservices.client.WebServiceClientHelper;

public class AjaxService extends HttpServlet
{
    private static final GlobalSightCategory CATEGORY = (GlobalSightCategory) GlobalSightCategory
            .getLogger(AjaxService.class);
    private static final long serialVersionUID = 1L;
    private static XmlEntities m_xmlEntities = new XmlEntities();
    private HttpServletRequest request;
    private HttpServletResponse response;
    private PrintWriter writer;
    private long companyId;

    public void service(HttpServletRequest request, HttpServletResponse response)
    {
        this.request = request;
        this.response = response;
        setCompanyId();

        String method = request.getParameter("action");
        try
        {
            writer = response.getWriter();
            AjaxService.class.getMethod(method, null).invoke(AjaxService.this);
        }
        catch (Exception e)
        {
            CATEGORY.error("Can not invoke the method:" + method);
        }
    }

    public void setCompanyId()
    {
        String companyName = UserUtil.getCurrentCompanyName(request);
        try
        {
            companyId = ServerProxy.getJobHandler().getCompany(companyName)
                    .getIdAsLong();
            CompanyThreadLocal.getInstance().setIdValue("" + companyId);
        }
        catch (Exception e)
        {
            CATEGORY.error("Can not get the Company!");
        }
    }

    public void test()
    {
        // writer = response.getWriter();
        HttpSession sess = request.getSession();
        User user = TaskHelper.getUser(sess);
        String action = request.getParameter(WebAppConstants.TASK_ACTION);
        writer.write(user.getEmail());
        writer.close();
    }

    public void loadFilterConfigurations()
    {
        // writer = response.getWriter();
        String filterConfigurationsJSON = FilterHelper
                .filterConfigurationsToJSON(companyId);
        writer.write(filterConfigurationsJSON);
        writer.close();
    }

    public void loadXmlRules()
    {
        String xmlRulesJSON = FilterHelper.allXmlRulesToJSON(companyId);
        writer.write(xmlRulesJSON);
        writer.close();
    }

    public void checkExist()
    {
        // writer = response.getWriter();
        String filterName = request.getParameter("filterName");
        String filterTableName = request.getParameter("filterTableName");
        if (FilterHelper.checkExist(filterTableName, filterName, companyId))
        {
            writer.write("true");
        }
        else
        {
            writer.write("false");
        }
    }

    public void saveJavaPropertiesFilter()
    {
        // writer = response.getWriter();
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        boolean isSupportSid = Boolean.parseBoolean(request
                .getParameter("isSupportSid"));
        boolean isUnicodeEscape = Boolean.parseBoolean(request
                .getParameter("isUnicodeEscape"));
        boolean isPreserveSpaces = Boolean.parseBoolean(request
                .getParameter("isPreserveSpaces"));
        long secondFilterId = -2;
        try {
            secondFilterId = Long.parseLong(request.getParameter("secondFilterId"));        	
        } catch (Exception ex) {}
        String secondFilterTableName = request.getParameter("secondFilterTableName");
        
        JSONArray internalTexts = new JSONArray();
        try
        {
            internalTexts = new JSONArray(request.getParameter("internalTexts"));
        }
        catch (JSONException e)
        {
            CATEGORY.error("Update java properties filter with error:", e);
        }
        
        long filterId = FilterHelper.saveJavaPropertiesFilter(filterName,
                filterDesc, isSupportSid, isUnicodeEscape, isPreserveSpaces, companyId,
                secondFilterId, secondFilterTableName, internalTexts);
        writer.write(filterId + "");
    }

    public void updateJavaPropertiesFilter()
    {
        
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        boolean isSupportSid = Boolean.parseBoolean(request
                .getParameter("isSupportSid"));
        boolean isUnicodeEscape = Boolean.parseBoolean(request
                .getParameter("isUnicodeEscape"));
        boolean isPreserveSpaces = Boolean.parseBoolean(request
                .getParameter("isPreserveSpaces"));
        
        long secondFilterId = -2;
        try {
            secondFilterId = Long.parseLong(request.getParameter("secondFilterId"));        	
        } catch (Exception ex) {}
        String secondFilterTableName = request.getParameter("secondFilterTableName");
        
        JSONArray internalTexts = new JSONArray();
        try
        {
            internalTexts = new JSONArray(request.getParameter("internalTexts"));
        }
        catch (JSONException e)
        {
            CATEGORY.error("Update java properties filter with error:", e);
        }
        
        FilterHelper.updateJavaPropertiesFilter(filterName, filterDesc,
                isSupportSid, isUnicodeEscape, isPreserveSpaces, companyId,
                secondFilterId, secondFilterTableName, internalTexts);
    }
    
    public void saveMSOfficeExcelFilter()
    {
        // writer = response.getWriter();
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        long secondFilterId = -2;
        
        try {
            secondFilterId = Long.parseLong(request.getParameter("secondFilterId"));            
        } catch (Exception ex) {}
        
        String secondFilterTableName = request.getParameter("secondFilterTableName");
        
        long filterId = FilterHelper.saveMSOfficeExcelFilter(filterName,
                filterDesc, companyId, secondFilterId, secondFilterTableName);
        writer.write(filterId + "");
    }
    
    public void updateMSOfficeExcelFilter()
    {
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        
        long secondFilterId = -2;
        
        try {
            secondFilterId = 
                Long.parseLong(request.getParameter("secondFilterId"));            
        } 
        catch (Exception ex) {}
        
        String secondFilterTableName = 
            request.getParameter("secondFilterTableName");
        
        FilterHelper.updateMSOfficeExcelFilter(filterName, filterDesc,
            companyId, secondFilterId, secondFilterTableName);
    }
    
    public void saveMSOfficePPTFilter()
    {
    	String filterName = request.getParameter("filterName");
    	MSOfficePPTFilter filter = new MSOfficePPTFilter();
    	filter.setFilterName(filterName);
    	filter.setCompanyId(companyId);
    	loadPPTFilterParameter(filter);

    	HibernateUtil.saveOrUpdate(filter);
        writer.write(Long.toString(filter.getId()));
    }
    
    public void updateMSOfficePPTFilter()
    {
    	String filterName = request.getParameter("filterName");

        String hql = "from MSOfficePPTFilter ms where ms.filterName=:name "
                + "and ms.companyId = :companyId";
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", filterName);
        map.put("companyId", companyId);
        MSOfficePPTFilter filter = (MSOfficePPTFilter) HibernateUtil.getFirst(hql, map);

        if (filter != null)
        {
        	loadPPTFilterParameter(filter);
            HibernateUtil.update(filter);
        }
    }
    
    private void loadPPTFilterParameter(MSOfficePPTFilter filter)
    {
    	String filterDesc = request.getParameter("filterDesc");
    	boolean isExtractAlt = Boolean.parseBoolean(request
                .getParameter("extractAlt"));
        
        long secondFilterId = -2;        
        try {
            secondFilterId = Long.parseLong(request.getParameter("secondFilterId"));            
        } catch (Exception ex) {}
        
        String secondFilterTableName = request.getParameter("secondFilterTableName");        
        
        filter.setFilterDescription(filterDesc);
        filter.setSecondFilterId(secondFilterId);
        filter.setSecondFilterTableName(secondFilterTableName);
        filter.setExtractAlt(isExtractAlt);
    }
    
    public void savePOFilter()
    {
        String filterName = request.getParameter("filterName");
        POFilter filter = new POFilter();
        filter.setFilterName(filterName);
        filter.setCompanyId(companyId);
        loadPOFilterParameter(filter);

        HibernateUtil.saveOrUpdate(filter);
        writer.write(Long.toString(filter.getId()));
    }
    
    public void updatePOFilter()
    {
        String filterName = request.getParameter("filterName");

        String hql = "from POFilter f where f.filterName=:name "
                + "and f.companyId = :companyId";
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", filterName);
        map.put("companyId", companyId);
        POFilter filter = (POFilter) HibernateUtil.getFirst(hql, map);

        if (filter != null)
        {
            loadPOFilterParameter(filter);
            HibernateUtil.update(filter);
        }
    }
    
    private void loadPOFilterParameter(POFilter filter)
    {
        String filterDesc = request.getParameter("filterDesc");
        
        long secondFilterId = -2;        
        try {
            secondFilterId = Long.parseLong(request.getParameter("secondFilterId"));            
        } catch (Exception ex) {}
        
        String secondFilterTableName = request.getParameter("secondFilterTableName");        
        
        filter.setFilterDescription(filterDesc);
        filter.setSecondFilterId(secondFilterId);
        filter.setSecondFilterTableName(secondFilterTableName);
    }

    public void saveJavaScriptFilter()
    {
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        String jsFunctionText = request.getParameter("jsFunctionText");
        boolean enableUnicodeEscape = Boolean.parseBoolean(request
                .getParameter("enableUnicodeEscape"));
        long filterId = FilterHelper.saveJavaScriptFilter(filterName,
                filterDesc, jsFunctionText, companyId, enableUnicodeEscape);
        writer.write(filterId + "");
    }

    public void updateJavaScriptFilter()
    {
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        String jsFunctionText = request.getParameter("jsFunctionText");
        boolean enableUnicodeEscape = Boolean.parseBoolean(request
                .getParameter("enableUnicodeEscape"));
        FilterHelper.updateJavaScriptFilter(filterName, filterDesc,
                jsFunctionText, companyId, enableUnicodeEscape);
    }
    
    public void saveInddFilter()
    {
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        boolean translateHiddenLayer = Boolean.parseBoolean(request
                .getParameter("translateHiddenLayer"));
        boolean translateMasterLayer = Boolean.parseBoolean(request
                .getParameter("translateMasterLayer"));
        boolean translateFileInfo = Boolean.parseBoolean(request
                .getParameter("translateFileInfo"));

        InddFilter filter = new InddFilter();
        filter.setCompanyId(companyId);
        filter.setTranslateHiddenLayer(translateHiddenLayer);
        filter.setTranslateMasterLayer(translateMasterLayer);
        filter.setTranslateFileInfo(translateFileInfo);
        filter.setFilterDescription(filterDesc);
        filter.setFilterName(filterName);
        long filterId = FilterHelper.saveFilter(filter);
        writer.write(filterId + "");
    }

    public void updateInddFilter()
    {
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        boolean translateHiddenLayer = Boolean.parseBoolean(request
                .getParameter("translateHiddenLayer"));
        boolean translateMasterLayer = Boolean.parseBoolean(request
                .getParameter("translateMasterLayer"));
        boolean translateFileInfo = Boolean.parseBoolean(request
                .getParameter("translateFileInfo"));

        String hql = "from InddFilter infl where infl.filterName=:name "
                + "and infl.companyId = :companyId";
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", filterName);
        map.put("companyId", companyId);

        InddFilter filter = (InddFilter) HibernateUtil.getFirst(hql, map);
        if (filter != null)
        {
            filter.setFilterDescription(filterDesc);
            filter.setTranslateHiddenLayer(translateHiddenLayer);
            filter.setTranslateMasterLayer(translateMasterLayer);
            filter.setTranslateFileInfo(translateFileInfo);
            HibernateUtil.update(filter);
        }
    }

    public void saveMSOfficeDocFilter()
    {
        String filterName = request.getParameter("filterName");
        MSOfficeDocFilter filter = new MSOfficeDocFilter();
        filter.setCompanyId(companyId);
        filter.setFilterName(filterName);
        loadDocFilterParameter(filter);
        HibernateUtil.saveOrUpdate(filter);

        writer.write(Long.toString(filter.getId()));
    }
    
    private void loadDocFilterParameter(MSOfficeDocFilter filter)
    {
        filter.setFilterDescription(request.getParameter("filterDesc"));
        boolean headerTranslate = Boolean.parseBoolean(request
                .getParameter("headerTranslate"));
        filter.setHeaderTranslate(headerTranslate);

        String selectParaStyles = request
                .getParameter("unextractableWordParagraphStyles");
        String allParaStyles = request.getParameter("allParagraphStyles");
        filter.setParaStyles(selectParaStyles, allParaStyles);

        String selectCharStyles = request
                .getParameter("unextractableWordCharacterStyles");
        String allCharStyles = request.getParameter("allCharacterStyles");
        filter.setCharStyles(selectCharStyles, allCharStyles);
        
        long secondFilterId = -2;        
        try {
            secondFilterId = Long.parseLong(request.getParameter("secondFilterId"));            
        } catch (Exception ex) {}
        filter.setSecondFilterId(secondFilterId);
        String secondFilterTableName = request.getParameter("secondFilterTableName");
        filter.setSecondFilterTableName(secondFilterTableName);

    }

    public void updateMSOfficeDocFilter()
    {
        String filterName = request.getParameter("filterName");

        String hql = "from MSOfficeDocFilter ms where ms.filterName=:name "
                + "and ms.companyId = :companyId";
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", filterName);
        map.put("companyId", companyId);
        MSOfficeDocFilter filter = (MSOfficeDocFilter) HibernateUtil.getFirst(
                hql, map);

        if (filter != null)
        {
            loadDocFilterParameter(filter);
            HibernateUtil.update(filter);
        }
    }
    
    public void saveOpenOfficeFilter()
    {
        String filterName = request.getParameter("filterName");
        OpenOfficeFilter filter = new OpenOfficeFilter();
        filter.setCompanyId(companyId);
        filter.setFilterName(filterName);
        loadOpenOfficeFilterParameter(filter);
        HibernateUtil.saveOrUpdate(filter);

        writer.write(Long.toString(filter.getId()));
    }
    
    private void loadOpenOfficeFilterParameter(OpenOfficeFilter filter)
    {
        filter.setFilterDescription(request.getParameter("filterDesc"));
        boolean headerTranslate = Boolean.parseBoolean(request
                .getParameter("headerTranslate"));
        filter.setHeaderTranslate(headerTranslate);

        String selectParaStyles = request
                .getParameter("unextractableWordParagraphStyles");
        String allParaStyles = request.getParameter("allParagraphStyles");
        filter.setParaStyles(selectParaStyles, allParaStyles);

        String selectCharStyles = request
                .getParameter("unextractableWordCharacterStyles");
        String allCharStyles = request.getParameter("allCharacterStyles");
        filter.setCharStyles(selectCharStyles, allCharStyles);
        
        long xmlFilterId = tryParse(request.getParameter("xmlFilterId"), -2);
        filter.setXmlFilterId(xmlFilterId);
    }

    public void updateOpenOfficeFilter()
    {
        String filterName = request.getParameter("filterName");

        String hql = "from OpenOfficeFilter oof where oof.filterName=:name "
                + "and oof.companyId = :companyId";
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", filterName);
        map.put("companyId", companyId);
        OpenOfficeFilter filter = (OpenOfficeFilter) HibernateUtil.getFirst(
                hql, map);

        if (filter != null)
        {
            loadOpenOfficeFilterParameter(filter);
            HibernateUtil.update(filter);
        }
    }
    
    public void saveMSOffice2010Filter()
    {
        String filterName = request.getParameter("filterName");
        MSOffice2010Filter filter = new MSOffice2010Filter();
        filter.setCompanyId(companyId);
        filter.setFilterName(filterName);
        loadMSOffice2010FilterParameter(filter);
        HibernateUtil.saveOrUpdate(filter);

        writer.write(Long.toString(filter.getId()));
    }
    
    private void loadMSOffice2010FilterParameter(MSOffice2010Filter filter)
    {
        filter.setFilterDescription(request.getParameter("filterDesc"));
        boolean headerTranslate = Boolean.parseBoolean(request
                .getParameter("headerTranslate"));
        filter.setHeaderTranslate(headerTranslate);

        boolean masterTranslate = Boolean.parseBoolean(request
                .getParameter("masterTranslate"));
        filter.setMasterTranslate(masterTranslate);
        
        String selectParaStyles = request
                .getParameter("unextractableWordParagraphStyles");
        String allParaStyles = request.getParameter("allParagraphStyles");
        filter.setParaStyles(selectParaStyles, allParaStyles);

        String selectCharStyles = request
                .getParameter("unextractableWordCharacterStyles");
        String allCharStyles = request.getParameter("allCharacterStyles");
        filter.setCharStyles(selectCharStyles, allCharStyles);
        
        long xmlFilterId = tryParse(request.getParameter("xmlFilterId"), -2);
        filter.setXmlFilterId(xmlFilterId);
    }

    public void updateMSOffice2010Filter()
    {
        String filterName = request.getParameter("filterName");

        String hql = "from MSOffice2010Filter msf where msf.filterName=:name "
                + "and msf.companyId = :companyId";
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("name", filterName);
        map.put("companyId", companyId);
        MSOffice2010Filter filter = (MSOffice2010Filter) HibernateUtil.getFirst(
                hql, map);

        if (filter != null)
        {
            loadMSOffice2010FilterParameter(filter);
            HibernateUtil.update(filter);
        }
    }

    public static long tryParse(String s, long defaultV)
    {
        try
        {
            return Long.parseLong(s);
        }
        catch (Exception e)
        {
            return defaultV;
        }
    }
    
    public void deleteFilter()
    {
        String filterTableName = request.getParameter("filterTableName");
        long filterId = Long.parseLong(request.getParameter("filterId"));

        try
        {
            if (FilterHelper.isFilterExist(filterTableName, filterId))
            {
                Filter filter = FilterHelper.getFilter(filterTableName,
                        filterId);
                if (FilterHelper.checkExistInFileProfile(filter))
                {
                    writer.write("deleteFilterExistInFileProfile");
                }
                else
                {
                    FilterHelper.deleteFilter(filterTableName, filterId);
                }
            }
        }
        catch (Exception e)
        {
            CATEGORY.error("Can not delete the filter!");
            writer.write("false");
        }
    }

    public void deleteSpecialFilters()
    {
        String checkedSpecialFilters = request
                .getParameter("checkedSpecialFilters");
        String[] specialFilters = checkedSpecialFilters.split(":");
        ArrayList<SpecialFilterToDelete> specialFilterToDeletes = buildSpecialFilterToDeletes(specialFilters);
        RemoveInfo rmInfo = FilterHelper.checkExistInFileProfile(specialFilterToDeletes);
        FilterHelper.checkExistInFiters(specialFilterToDeletes, rmInfo, companyId);
        FilterHelper.checkUsedInJob(specialFilterToDeletes, rmInfo, companyId);
        if (rmInfo.isExistInFileProfile() || rmInfo.isUsedByFilters() || rmInfo.isUsedInJob())
        {
            writer.write(rmInfo.toJSON());
        }
        else
        {
            try
            {
                FilterHelper.deleteFilters(specialFilterToDeletes);
                rmInfo.setDeleted("true");
                rmInfo.setExistInFileProfile(false);
                rmInfo.setFilterInfos(new ArrayList());
                writer.write(rmInfo.toJSON());
            }
            catch (Exception e)
            {
                CATEGORY.error("Can not delete the filters!");
                rmInfo.setDeleted("false");
                rmInfo.setFilterInfos(new ArrayList());
                writer.write(rmInfo.toJSON());
            }
        }

    }

    private ArrayList<SpecialFilterToDelete> buildSpecialFilterToDeletes(
            String[] specialFilters)
    {
        ArrayList<SpecialFilterToDelete> specialFilterToDeletes = new ArrayList<SpecialFilterToDelete>();
        for (int i = 0; i < specialFilters.length; i++)
        {
            String specialFilter = specialFilters[i];
            if (!"".equals(specialFilter))
            {
                String[] filtersArray = specialFilter.split(",");
                SpecialFilterToDelete specialFilterToDelete = new SpecialFilterToDelete(
                        Integer.parseInt(filtersArray[0]), Long
                                .parseLong(filtersArray[1]), filtersArray[2],
                        filtersArray[3]);
                specialFilterToDeletes.add(specialFilterToDelete);
            }
        }
        return specialFilterToDeletes;
    }
    
    public void decodeTextvalue()
    {
        String value = request.getParameter("textvalue");
        String vType = request.getParameter("valuetype");
        
        if ("xml".equals(vType))
        {
            writer.write(m_xmlEntities.decodeStringBasic(value));
            return;
        }
        
        writer.write(value);
    }
    
    public void isFilterValid()
    {
        // 1. get parameters 
        String filterName = request.getParameter("filterName");
        String filterTableName = request.getParameter("filterTableName");
        boolean isNew = Boolean.parseBoolean(request.getParameter("isNew"));
        
        // 2. check if name exists when new filter 
        if (isNew && FilterHelper.checkExist(filterTableName, filterName, companyId))
        {
            writer.write("name_exists");
            return;
        }
        
        // 3. do other check
        writer.write(FilterHelper.isFilterValid(request, filterTableName));
    }

    public void saveXmlRuleFilter()
    {
        XMLRuleFilter filter = readXmlFilterFromRequest();
        
        long filterId = FilterHelper.saveXmlRuleFilter(filter);
        writer.write(filterId + "");
    }

    public void updateXmlRuleFilter()
    {
        long filterId = Long.parseLong(request.getParameter("filterId"));
        XMLRuleFilter filter = readXmlFilterFromRequest();
        filter.setId(filterId);
        
        FilterHelper.updateFilter(filter);
    }
    
    private XMLRuleFilter readXmlFilterFromRequest()
    {
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        long xmlRuleId = Long.parseLong(request.getParameter("xmlRuleId"));
        boolean convertHtmlEntity = Boolean.parseBoolean(request.getParameter("convertHtmlEntity"));
        
        long secondFilterId = -2;
        boolean useXmlRule = Boolean.parseBoolean(request.getParameter("useXmlRule"));
        String extendedWhitespaceChars = request.getParameter("extendedWhitespaceChars");
        int phConsolidationMode = XmlFilterConfigParser.PH_CONSOLIDATE_DONOT;
        int phTrimMode = XmlFilterConfigParser.PH_TRIM_DONOT;
        int nonasciiAs = XmlFilterConfigParser.NON_ASCII_AS_CHARACTER;
        int wsHandleMode = XmlFilterConfigParser.WHITESPACE_HANDLE_COLLAPSE;
        int emptyTagFormat = XmlFilterConfigParser.EMPTY_TAG_FORMAT_CLOSE;
        String elementPostFilter = request.getParameter("elementPostFilter");
        String elementPostFilterId = request.getParameter("elementPostFilterId");
        String cdataPostFilter = request.getParameter("cdataPostFilter");
        String cdataPostFilterId = request.getParameter("cdataPostFilterId");
        String preserveWsTags = request.getParameter("preserveWsTags");
        String embTags = request.getParameter("embTags");
        String transAttrTags = request.getParameter("transAttrTags");
        String contentInclTags = request.getParameter("contentInclTags");
        String cdataPostfilterTags = request.getParameter("cdataPostfilterTags");
        String sidSupportTagName = request.getParameter("sidSupportTagName");
        String sidSupportAttName = request.getParameter("sidSupportAttName");
        String isCheckWellFormed = request.getParameter("isCheckWellFormed");
        String isGerateLangInfo = request.getParameter("isGerateLangInfo");
        String entities = request.getParameter("entities");
        String processIns = request.getParameter("processIns");
        JSONArray jsonArrayPreserveWsTags = new JSONArray();
        JSONArray jsonArrayEmbTags = new JSONArray();
        JSONArray jsonArrayTransAttrTags = new JSONArray();
        JSONArray jsonArrayContentInclTags = new JSONArray();
        JSONArray jsonArrayCdataPostfilterTags = new JSONArray();
        JSONArray jsonArrayEntities = new JSONArray();
        JSONArray jsonArrayProcessIns = new JSONArray();
        try
        {
            secondFilterId = Long.parseLong(request.getParameter("secondFilterId"));
            phConsolidationMode = Integer.parseInt(request.getParameter("phConsolidationMode"));
            phTrimMode = Integer.parseInt(request.getParameter("phTrimMode"));
            nonasciiAs = Integer.parseInt(request.getParameter("nonasciiAs"));
            wsHandleMode = Integer.parseInt(request.getParameter("wsHandleMode"));
            emptyTagFormat = Integer.parseInt(request.getParameter("emptyTagFormat"));
            
            jsonArrayPreserveWsTags = new JSONArray(preserveWsTags);
            jsonArrayEmbTags = new JSONArray(embTags);
            jsonArrayTransAttrTags = new JSONArray(transAttrTags);
            jsonArrayContentInclTags = new JSONArray(contentInclTags);
            jsonArrayCdataPostfilterTags = new JSONArray(cdataPostfilterTags);
            jsonArrayEntities = new JSONArray(entities);
            jsonArrayProcessIns = new JSONArray(processIns);
        }
        catch (Exception e)
        {
            CATEGORY.error("Update xml filter with error:", e);
        }
        String secondaryFilterTableName = request.getParameter("secondFilterTableName");
        
        String configXml = XmlFilterConfigParser.nullConfigXml;
        try
        {
            configXml = XmlFilterConfigParser.toXml(extendedWhitespaceChars, phConsolidationMode,
                    phTrimMode, nonasciiAs, wsHandleMode, emptyTagFormat, elementPostFilter,
                    elementPostFilterId, cdataPostFilter, cdataPostFilterId, sidSupportTagName,
                    sidSupportAttName, isCheckWellFormed, isGerateLangInfo,
                    jsonArrayPreserveWsTags, jsonArrayEmbTags, jsonArrayTransAttrTags,
                    jsonArrayContentInclTags, jsonArrayCdataPostfilterTags, jsonArrayEntities,
                    jsonArrayProcessIns);
        }
        catch (Exception e)
        {
            CATEGORY.error("Update xml filter with error:", e);
        }
        
        XMLRuleFilter filter = new XMLRuleFilter(filterName, filterDesc,
                xmlRuleId, companyId, convertHtmlEntity, secondFilterId, secondaryFilterTableName);
        filter.setConfigXml(configXml);
        filter.setUseXmlRule(useXmlRule);
        return filter;
    }

    public void saveHtmlFilter()
    {
        long filterId = FilterHelper.saveFilter(loadHtmlFilter());
        writer.write(filterId + "");
    }
    
    private String getValue(String key)
    {
        return FilterHelper.removeComma(request.getParameter(key));
    }
    
    private HtmlFilter loadHtmlFilter()
    {
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        boolean convertHtmlEntry = Boolean.parseBoolean(request
                .getParameter("convertHtmlEntry"));
        boolean ignoreInvalideHtmlTags = Boolean.parseBoolean(request
                .getParameter("ignoreInvalideHtmlTags"));
        String localizeFunction = request.getParameter("localizeFunction");

        String defaultEmbeddableTags = getValue("defaultEmbeddableTags");
        String embeddableTags = getValue("embeddableTags");

        String defaultPairedTags = getValue("defaultPairedTags");
        String pairedTags = getValue("pairedTags");

        String defaultUnpairedTags = getValue("defaultUnPairedTags");
        String unpairedTags = getValue("unpairedTags");

        String defaultSwitchTagMaps = getValue("defaultSwitchTagMaps");
        String switchTagMaps = getValue("switchTagMaps");

        String defaultWhitePreservingTags = getValue("defaultWhitePreservingTags");
        String whitePreservingTags = getValue("whitePreservingTags");

        String defaultNonTranslatableMetaAttributes = getValue("defaultNonTranslatableMetaAttributes");
        if (defaultNonTranslatableMetaAttributes == null)
        {
            defaultNonTranslatableMetaAttributes = "";
        }

        String nonTranslatableMetaAttributes = getValue("nonTranslatableMetaAttributes");
        if (nonTranslatableMetaAttributes == null)
        {
            nonTranslatableMetaAttributes = "";
        }
        
        String defaultInternalTags = getValue("defaultInternalTag");
        if (defaultInternalTags == null)
        {
            defaultInternalTags = "";
        }
        String internalTags = getValue("internalTag");
        if (internalTags == null)
        {
            internalTags = "";
        }

        String defaultTranslatableAttributes = getValue("defaultTranslatableAttributes");
        String translatableAttributes = getValue("translatableAttributes");

        HtmlFilter htmlFilter = new HtmlFilter(filterName, filterDesc,
                defaultEmbeddableTags, embeddableTags, "embeddable_tags",
                companyId, convertHtmlEntry, ignoreInvalideHtmlTags,
                localizeFunction, defaultPairedTags, pairedTags,
                defaultUnpairedTags, unpairedTags, defaultSwitchTagMaps,
                switchTagMaps, defaultWhitePreservingTags, whitePreservingTags,
                defaultNonTranslatableMetaAttributes,
                nonTranslatableMetaAttributes, defaultTranslatableAttributes,
                translatableAttributes);
        
        htmlFilter.setDefaultInternalTagMaps(defaultInternalTags);
        htmlFilter.setInternalTagMaps(internalTags);

        return htmlFilter;
    }

    public void validateHtmlInternalTag()
    {
        ResourceBundle bundle = PageHandler.getBundle(request.getSession());
        String internalTag = request.getParameter("internalTag");
        String error = "";
        try
        {
            HtmlInternalTag tag = HtmlInternalTag.string2tag(internalTag, bundle);
            internalTag = tag.toString();
        }
        catch (InternalTagException e)
        {
            error = e.getMessage();
        }
        
        String s = "({\"error\":\"" + error + "\", \"tag\" : " + JsonUtil.toJson(internalTag) + "})";
        writer.write(s);
        writer.close();
    }
    
    public void updateHtmlFilter()
    {
        long filterId = Long.parseLong(request.getParameter("filterId"));

        HtmlFilter htmlFilter = loadHtmlFilter();
        htmlFilter.setId(filterId);

        FilterHelper.updateFilter(htmlFilter);
    }

    public void saveJSPFilter()
    {
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        boolean isAdditionalHeadAdded = Boolean.parseBoolean(request
                .getParameter("isAdditionalHeadAdded"));
        boolean isEscapeEntity = Boolean.parseBoolean(request
                .getParameter("isEscapeEntity"));
        Filter jspFilter = new JSPFilter(filterName, filterDesc, companyId,
                isAdditionalHeadAdded, isEscapeEntity);
        long filterId = FilterHelper.saveFilter(jspFilter);
        writer.write(filterId + "");
    }

    public void updateJSPFilter()
    {
        long filterId = Long.parseLong(request.getParameter("filterId"));
        String filterName = request.getParameter("filterName");
        String filterDesc = request.getParameter("filterDesc");
        boolean isAdditionalHeadAdded = Boolean.parseBoolean(request
                .getParameter("isAdditionalHeadAdded"));
        boolean isEscapeEntity = Boolean.parseBoolean(request
                .getParameter("isEscapeEntity"));
        Filter jspFilter = new JSPFilter(filterId, filterName, filterDesc,
                companyId, isAdditionalHeadAdded, isEscapeEntity);
        FilterHelper.updateFilter(jspFilter);
    }
    
    public void uploadFile()
    {
    	boolean isMultiPart = FileUpload.isMultipartContent(request);
        if (isMultiPart)
        {
            StringBuffer tmpPath = new StringBuffer(AmbFileStoragePathUtils.getXslDir().getPath());
            
            tmpPath.append("/")
                   .append("~TMP")
            	   .append(System.currentTimeMillis())
            	   .append("/");
            
            try
            {
	            DiskFileUpload upload = new DiskFileUpload();
	            List<FileItem> items = upload.parseRequest(request);
            		            
	            File uploadedFile = null;
	            String fileName = "";
	            String filePath = "";
	            for (FileItem item : items)
	            {
	            	if (!item.isFormField()) {
		            	fileName = item.getName();
		            	fileName = fileName.substring(fileName.lastIndexOf(File.separator) + 1, fileName.length());
		            	if (fileName.toLowerCase().endsWith("xsl") 
		            			|| fileName.toLowerCase().endsWith("xml")
		            			|| fileName.toLowerCase().endsWith("xslt") )
		            	{   	
			            	filePath = tmpPath.toString() + fileName;
			            	uploadedFile = new File(filePath);
			            	uploadedFile.getParentFile().mkdirs();
			            	item.write(uploadedFile);
			            	CATEGORY.info("Succeeded in uploading file: " + filePath);
		            	}
		            	else
		            	{
		            		continue;
		            	}
	            	}
	            }
	               
	            if (uploadedFile != null)
	            {
	            	writer.write("<html><body><textarea>" + filePath + "</textarea></body></html>");
	            	writer.flush();
	            } 
	            else
	            {
	            	writer.write("<html><body><textarea>error</textarea></body></html>");
	            	writer.flush();
	            }
            }
            catch (Exception e)
            {
            	CATEGORY.error("Failed to upload XSL file! Details: " + e.getMessage());
            	writer.write("<html><body><textarea>error</textarea></body></html>");
            	writer.flush();
            }
        }
    	
    }
    
    public void removeFile()
    {
    	String filePath = request.getParameter("filePath");
    	String docRoot = AmbFileStoragePathUtils.getXslDir().getPath();
        String fullPath = docRoot + filePath;
        
        File file = new File(fullPath);
        boolean deleted = false;
        
        if(file.exists())
        {
           deleted = file.delete();
        }
        
        String message = "";
        if (deleted)
        {
        	message = "true";
        }
        else
        {
        	message = "false";
        }
        
        writer.write(message);
        writer.flush();
        
    }
    
    public void getRemoteFileProfile() {
        long GSEditionID = Long.parseLong(request.getParameter("id"));
        GSEditionManagerLocal gsEditionManager = new GSEditionManagerLocal();
        GSEdition edition = gsEditionManager.getGSEditionByID(GSEditionID);
        
        try{
            Ambassador ambassador = WebServiceClientHelper.getClientAmbassador(edition.getHostName(), 
                edition.getHostPort(),
                edition.getUserName(),
                edition.getPassword(),
                edition.getEnableHttps());
            String fullAccessToken = ambassador.login(edition.getUserName(), edition.getPassword());
            String realAccessToken = WebServiceClientHelper.getRealAccessToken(fullAccessToken);
            
            HashMap xliffFP = ambassador.getXliffFileProfile(realAccessToken);
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            
            Iterator itera = (Iterator) xliffFP.keySet().iterator(); 
            int i = 0;
            
            if(itera.hasNext()) {
                while(itera.hasNext()) {
                    i++;
                    Object key = itera.next(); 
                    String val = (String)xliffFP.get(key); 
                    sb.append("{");
                    sb.append("\"fileprofileID\":").append(key).append(",");
                    sb.append("\"fileprofileName\":").append("\"").append(val).append("\"").append("}");
                    
                    if(i < xliffFP.size()) {
                        sb.append(",");
                    }
                }
            }
            else {
                sb.append("{");
                sb.append("\"noXliffFile\":").append("\"true").append("\"").append("}");
            }
            
            sb.append("]");
            
            writer.write(sb.toString());
            writer.close();
        }
        catch(Exception e) {
            String msg = e.getMessage();
            String errorInfo = null;
            
            if(msg != null && msg.indexOf("No such operation") > -1) {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                sb.append("{");
                sb.append("\"lowVersion\":").append("\"true").append("\"").append("}");
                sb.append("]");
                
                writer.write(sb.toString());
                writer.close(); 
            }
            else {
                if (msg != null && (msg.indexOf("Connection timed out") > -1 
                        || msg.indexOf("UnknownHostException") > -1
                        || msg.indexOf("java.net.ConnectException") > -1))
                {
                    errorInfo = "Can not connect to server. Please check GS Edition configuration.";
                } 
                else if (msg != null && msg.indexOf("Illegal web service access attempt from IP address") > -1) 
                {
                    errorInfo = "User name or password of GS Edition is wrong. Or the IP is not allowed to access server.";
                }
                else if (msg != null && msg.indexOf("The username or password may be incorrect") > -1)
                {
                    errorInfo = "Can not connect to server. Please check GS Edition configuration.";
                }
                else if(msg != null && msg.indexOf("com.globalsight.webservices.WebServiceException") > -1)
                {
                    errorInfo = "Can not connect to server.";
                }
                else
                {
                    errorInfo = msg;
                }
                
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                sb.append("{");
                sb.append("\"errorInfo\":").append("\"").append(errorInfo).append("\"").append("}");
                sb.append("]");
                writer.write(sb.toString());
                writer.close(); 
                }
            //e.printStackTrace();
        }
    }
    
    /**
     * 
     */
    public void getAllRemoteTmProfiles()
    {
        long GSEditionID = Long.parseLong(request.getParameter("id"));
        GSEditionManagerLocal gsEditionManager = new GSEditionManagerLocal();
        GSEdition edition = gsEditionManager.getGSEditionByID(GSEditionID);
        
        try {
            Ambassador ambassador = WebServiceClientHelper.getClientAmbassador(edition.getHostName(), 
                edition.getHostPort(),
                edition.getUserName(),
                edition.getPassword(),
                edition.getEnableHttps());
            String fullAccessToken = ambassador.login(edition.getUserName(), edition.getPassword());
            String realAccessToken = WebServiceClientHelper.getRealAccessToken(fullAccessToken);
            
            String strAllTmProfiles = ambassador.getAllTMProfiles(realAccessToken);
            CATEGORY.debug("allTmProfiles :: " + strAllTmProfiles);

    		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        	DocumentBuilder db = dbf.newDocumentBuilder();
        	InputStream stream = new ByteArrayInputStream(strAllTmProfiles.getBytes("UTF-8"));
        	org.w3c.dom.Document doc = db.parse(stream);
        	
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            
        	Element root = doc.getDocumentElement();
        	NodeList TMProfileNL = root.getElementsByTagName("TMProfile");
        	for (int i=0; i<TMProfileNL.getLength(); i++)
        	{
        		String id = null;
        		String name = null;
        		
        		Node subNode = TMProfileNL.item(i);
        		if (subNode instanceof Element)
        		{
        			NodeList childNodeList = subNode.getChildNodes();
            		for (int j=0; j<childNodeList.getLength(); j++)
            		{
            			if (childNodeList.item(j) instanceof Element)
            			{
            				String nodeName = childNodeList.item(j).getNodeName();
                    		NodeList subNodeList = childNodeList.item(j).getChildNodes();
                    		String nodeValue = null;
                    		if (subNodeList != null && subNodeList.getLength() > 0)
                    		{
                        		nodeValue = subNodeList.item(0).getNodeValue();
                    		}
                    		CATEGORY.debug("nodeName :: " + nodeName + "; nodeValue :: " + nodeValue);
                    		
            				if ("id".equals(nodeName.toLowerCase())) {
            					id = nodeValue;
            				} else if ("name".equals(nodeName.toLowerCase())) {
            					name = nodeValue;
            				}
            			}
            		}
        		}
        		
        		if (id != null && name != null)
        		{
        			sb.append("{");
        			sb.append("\"tmProfileId\":").append(id).append(",");
        			sb.append("\"tmProfileName\":").append("\"").append(name).append("\"").append("}");
        		}
        		if ((i+1)<TMProfileNL.getLength())
        		{
        			sb.append(",");
        		}
        	}
            sb.append("]");
            
            writer.write(sb.toString());
            writer.close();
        }
        catch(Exception e) {
            
        }
    }
    
    public void isProjectUseTerbase() {
        long localizitionId = Long.parseLong(request.getParameter("locProfileId"));
        L10nProfile lp = LocProfileHandlerHelper.getL10nProfile(localizitionId);
        String outStr = new String();
        
        if(lp.getProject().getTermbaseName().equals("")) {
            outStr = "[{isProjectUseTerbase:\"false\"}]";
        }
        else {
            outStr = "[{isProjectUseTerbase:\"true\"}]";
        }
        
        writer.write(outStr);
        writer.close();
    }
    
    public void deleteTermImg() {
        String termImgPath = FileUploadHelper.DOCROOT + "terminologyImg";
        File file = new File(termImgPath, request.getParameter("termImgName"));
        
        if(file.exists()) {
            file.delete();
        }
    }
    
    //for terminology browser
    public void getDefinition() {
        String xml;
        HttpSession sess = request.getSession();
        
        try{
            ITermbase termbase = (ITermbase)sess.getAttribute(WebAppConstants.TERMBASE);

            if (termbase == null)
            {
              SessionManager sessionMgr = (SessionManager)sess.getAttribute(
                  WebAppConstants.SESSION_MANAGER);
              termbase = (ITermbase)sessionMgr.getAttribute(WebAppConstants.TERMBASE);
            }

            xml = termbase.getDefinition();
        }
        catch (Exception ex)
        {
            // TODO: error handling
            xml = "";
        }

        writer.write(xml);
    }
}