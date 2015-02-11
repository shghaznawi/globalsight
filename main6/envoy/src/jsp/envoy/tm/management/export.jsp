<%@ page
    contentType="text/html; charset=UTF-8"
    errorPage="/envoy/common/error.jsp"
    import="java.util.*,com.globalsight.everest.webapp.webnavigation.LinkHelper,
        java.util.ResourceBundle,
        com.globalsight.everest.tm.exporter.ExportOptions,
        com.globalsight.util.edit.EditUtil,
        com.globalsight.everest.servlet.util.SessionManager,
        com.globalsight.everest.webapp.pagehandler.PageHandler,
        com.globalsight.everest.webapp.WebAppConstants,
        com.globalsight.everest.tm.util.Tmx"
    session="true"
%>
<%--
State machinery for export:

export --> exportFileOptions --> exportOutputOptions --> exportProgress

export.jsp sets <selectOptions>
exportFileOptions sets <fileOptions>
exportOutputOptions.jsp sets <outputOptions>
exportProgress.jsp runs the export and allows to download result file

--%>
<jsp:useBean id="next" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="cancel" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<%
ResourceBundle bundle = PageHandler.getBundle(session);
SessionManager sessionMgr = (SessionManager)session.getAttribute(
  WebAppConstants.SESSION_MANAGER);

String xmlDefinition =
  (String)sessionMgr.getAttribute(WebAppConstants.TM_DEFINITION);
String xmlExportOptions =
  (String)sessionMgr.getAttribute(WebAppConstants.TM_EXPORT_OPTIONS);
String xmlProject = 
    (String)sessionMgr.getAttribute(WebAppConstants.TM_PROJECT);
String termbaseName =
  (String)sessionMgr.getAttribute(WebAppConstants.TM_TM_NAME);

String propTypeTitle = bundle.getString("lb_prop_type");
String[] propTypes = new String[]{
        Tmx.VAL_TU_LOCALIZABLE,
        Tmx.VAL_TU_TRANSLATABLE,
        Tmx.PROP_SOURCE_TM_NAME,
        Tmx.PROP_UPDATED_BY_PROJECT,
        Tmx.PROP_CREATION_PROJECT};

String urlNext   = next.getPageURL();
String urlCancel = cancel.getPageURL();

String lb_title = bundle.getString("lb_export_tm");
String lb_calendar_title = bundle.getString("lb_calendar_title");
%>
<HTML>
<!-- This is envoy\tm\management\export.jsp -->
<HEAD>
<TITLE><%=bundle.getString("lb_tm_export")%></TITLE>
<STYLE>
{ font: Tahoma Verdana Arial 10pt; }
INPUT, SELECT { font: Tahoma Verdana Arial 10pt; }

LEGEND        { font-size: smaller; font-weight: bold; }
.link         { color: blue; cursor: hand; }
.calendar     { width:16px; height:15px; cursor:pointer; vertical-align:middle; }
</STYLE>
<SCRIPT SRC="/globalsight/includes/setStyleSheet.js"></SCRIPT>
<%@ include file="/envoy/wizards/guidesJavascript.jspIncl" %>
<%@ include file="/envoy/common/warning.jspIncl" %>
<%@ include file="/includes/compatibility.jspIncl" %>
<SCRIPT SRC="/globalsight/includes/library.js"></SCRIPT>
<SCRIPT type="text/javascript" src="/globalsight/includes/report/calendar2.js"></SCRIPT>
<SCRIPT LANGUAGE="JavaScript">
var needWarning = false;
var objectName = "";
var guideNode = "tm";
var helpFile = "<%=bundle.getString("help_tm_export")%>";
var xmlDefinition	 = "<%=xmlDefinition%>";
var xmlExportOptions = "<%=xmlExportOptions%>";
var xmlProject		 = "<%=xmlProject%>";

function Result(message, description, element, dom)
{
    this.message = message;
    this.description = description;
    this.element = element;
    this.dom = dom;
}

function parseExportOptions()
{
  var form = document.oDummyForm;
  var dom ;
  var nodes, node;
  var createdAfter, createdBefore;
  var selectMode, selectLanguage, duplicateHandling, fileType, fileEncoding, selectChangeCreationId;

  if(window.navigator.userAgent.indexOf("MSIE")>0)
  {
    dom = oExportOptions.XMLDocument;
  }
  else if(window.DOMParser)
  { 
    var parser = new DOMParser();
    dom = parser.parseFromString(xmlExportOptions,"text/xml");
  }

  node = dom.selectSingleNode("/exportOptions/selectOptions");
  selectMode = node.selectSingleNode("selectMode").text;

  selectFilter = node.selectSingleNode("selectFilter").text;
  selectLanguage = node.selectSingleNode("selectLanguage").text;
  //selectPropType = node.selectSingleNode("selectPropType").text;
  selectChangeCreationId = node.selectSingleNode("selectChangeCreationId").text;
  
  node = dom.selectSingleNode("/exportOptions/filterOptions");
  createdAfter = node.selectSingleNode("createdafter").text;
  createdBefore = node.selectSingleNode("createdbefore").text;

  node = dom.selectSingleNode("/exportOptions/fileOptions");
  fileType = node.selectSingleNode("fileType").text;
  fileEncoding = node.selectSingleNode("fileEncoding").text;

  if (selectMode == "<%=ExportOptions.SELECT_FILTERED%>")
  {
    form.oEntries[1].checked = true;
    form.oEntryLang.disabled = false;
  }
  
  form.fltCreatedAfter.value = createdAfter;
  form.fltCreatedBefore.value = createdBefore;

  selectValue(form.oEntryLang, selectLanguage);
  selectValue(form.oEncoding, fileEncoding);

  if (fileType == "<%=ExportOptions.TYPE_XML%>")
  {
    form.oType[0].click();
  }
  else if (fileType == "<%=ExportOptions.TYPE_TMX1%>")
  {
    form.oType[1].click();
  }
  else if (fileType == "<%=ExportOptions.TYPE_TMX2%>")
  {
    form.oType[2].click();
  }
  else if (fileType == "<%=ExportOptions.TYPE_TTMX%>")
  {
    form.oType[3].click();
  }

  if (selectChangeCreationId == "true") {
	  form.oChangeCreationId.checked = true;
  }
}

function buildExportOptions()
{
  var result = new Result("", "", null, null);
  var form = document.oDummyForm;
  var dom ;
  var node;
  var sel;

  if(window.navigator.userAgent.indexOf("MSIE")>0)
  {
    dom = oExportOptions.XMLDocument;
  }
  else if(window.DOMParser)
  { 
    var parser = new DOMParser();
    dom = parser.parseFromString(xmlExportOptions,"text/xml");
  }
  
  // SELECT OPTIONS
  node = dom.selectSingleNode("/exportOptions/selectOptions");

  if (form.oEntries[0].checked)
  {
     node.selectSingleNode("selectMode").text =
       "<%=ExportOptions.SELECT_ALL%>";
  }
  else
  {
    if(form.oEntries[1].checked){
        node.selectSingleNode("selectMode").text =
         "<%=ExportOptions.SELECT_FILTERED%>";
    }
    else
    {
        node.selectSingleNode("selectMode").text =
         "<%=ExportOptions.SELECT_FILTER_PROP_TYPE%>";
    }
  }

  sel = form.oEntryLang;
  if (sel.options.length > 0)
  {
    node.selectSingleNode("selectLanguage").text =
      sel.options[sel.selectedIndex].value;
  }
  
  sel = form.oEntryPropType;
  if(sel.options.length > 0){
    node.selectSingleNode("selectPropType").text = "";
    optionsList = selectMulti(sel);
    
    for(var i = 0; i < optionsList.length; i ++){
        if(i == optionsList.length -1){
            node.selectSingleNode("selectPropType").text += optionsList[i];
        }else{
            node.selectSingleNode("selectPropType").text += optionsList[i]+",";
        }
        
    }
    if(form.oEntries[2].checked && node.selectSingleNode("selectPropType").text == ""){
        alert("<%=bundle.getString("jsmsg_tm_export_select_project") %>")
        return "";
    }
    //alert(node.selectSingleNode("selectPropType").text);
  }

  sel = form.oChangeCreationId;
  if (sel.checked) {
	  node.selectSingleNode("selectChangeCreationId").text = "true";
  } else {
	  node.selectSingleNode("selectChangeCreationId").text = "false";
  }

  // FILTER OPTIONS
  node = dom.selectSingleNode("/exportOptions/filterOptions");
  node.selectSingleNode("createdafter").text = form.fltCreatedAfter.value;
  node.selectSingleNode("createdbefore").text = form.fltCreatedBefore.value;
  
  // FILE OPTIONS
  node = dom.selectSingleNode("/exportOptions/fileOptions");

  if (form.oType[0].checked)
  {
    node.selectSingleNode("fileType").text =
       "<%=ExportOptions.TYPE_XML%>";
  }
  else if (form.oType[1].checked)
  {
    node.selectSingleNode("fileType").text =
       "<%=ExportOptions.TYPE_TMX1%>";
  }
  else if (form.oType[2].checked)
  {
    node.selectSingleNode("fileType").text =
       "<%=ExportOptions.TYPE_TMX2%>";
  }
  else if (form.oType[3].checked)
  {
    node.selectSingleNode("fileType").text =
       "<%=ExportOptions.TYPE_TTMX%>";
  }

  var sel = form.oEncoding;
  node.selectSingleNode("fileEncoding").text =
    sel.options[sel.selectedIndex].value;

  //alert("options = " + oExportOptions.xml);

  result.dom = dom;
  return result;
}

function doCancel()
{
    window.navigate("<%=urlCancel%>");
}

function selectMulti(selects){
    var optionsList = new Array();
    for( var loop = 0; loop < selects.options.length; loop ++){
        if (selects.options[loop].selected == true) {
            optionsList[optionsList.length] = selects.options[loop].text;
        }
    }
    return optionsList;
}

function doNext()
{
    var result = buildExportOptions();
    if(result == ""){
        return;
    }
    if (result.message != null && result.message != "")
    {
        showError(result);
        result.element.focus();
    }
    else
    {
        var url = "<%=urlNext +
            "&" + WebAppConstants.TM_ACTION +
            "=" + WebAppConstants.TM_ACTION_ANALYZE_TM%>";

        oForm.action = url;
        if(window.navigator.userAgent.indexOf("MSIE")>0)
        {
        	oForm.exportoptions.value = oExportOptions.xml;
        }
        else if(window.DOMParser)
        { 
        	oForm.exportoptions.value = XML.getDomString(result.dom);
        }       

        oForm.submit();
    }
}

function doByEntry()
{
  document.oDummyForm.oEntryLang.disabled = true;
  document.oDummyForm.oEntryPropType.disabled = true;
}

function doByLanguage()
{
  document.oDummyForm.oEntries[1].click();
  document.oDummyForm.oEntryLang.disabled = false;
  document.oDummyForm.oEntryPropType.disabled = true;
}
function doByPropType(){
    document.oDummyForm.oEntries[2].click();
    document.oDummyForm.oEntryPropType.disabled = false;
    document.oDummyForm.oEntryLang.disabled = true;
}
function doByProject(){
    document.oDummyForm.oEntries[2].click();
    document.oDummyForm.oEntryPropType.disabled = false;
    document.oDummyForm.oEntryLang.disabled = true;
}

function doTypeChanged()
{
/*
  var form = document.oDummyForm;
  var select = form.oEncoding;

  // XML, TMX, TTX use UTF-8. Everything else can select encoding.
  if (form.oType[0].checked ||
      form.oType[1].checked ||
      form.oType[2].checked ||
      form.oType[3].checked)
  {
    selectValue(select, "UTF-8")
  }
*/
}

function selectValue(select, value)
{
  for (i = 0; i < select.options.length; ++i)
  {
    if (select.options[i].value == value)
    {
      select.selectedIndex = i;
      return;
    }
  }
}

function fillLanguages()
{
  var dom;
  if(window.navigator.userAgent.indexOf("MSIE")>0)
  {
    dom = oDefinition.XMLDocument;
  }
  else if(window.DOMParser)
  { 
    var parser = new DOMParser();
    dom = parser.parseFromString(xmlDefinition,"text/xml");
  }
  
  var langs = dom.selectNodes("/statistics/languages/language");

  for (var i = 0; i < langs.length; ++i)
  {
    var lang = langs[i];//var lang = langs.item(i);

    var name = lang.selectSingleNode("name").text;
    var locale = lang.selectSingleNode("locale").text;
    
    if (locale == "iw_IL")
    {
        locale = "he_IL";
    }
    oOption = document.createElement("OPTION");
    oOption.text = name;
    oOption.value = locale;
    document.oDummyForm.oEntryLang.add(oOption);
  }

  var tuCount = dom.selectSingleNode("/statistics/tus").text;
  if (parseInt(tuCount) == 0)
  {
    idWarning.style.display = '';
  }
}

function fillProjects(){
    var dom ;
    if(window.navigator.userAgent.indexOf("MSIE")>0)
    {
      dom = oProject.XMLDocument;
    }
    else if(window.DOMParser)
    { 
      var parser = new DOMParser();
      dom = parser.parseFromString(xmlProject,"text/xml");
    }
    
    var projects = dom.selectNodes("/statistics/projects/project");
    
    for(var i = 0; i < projects.length; i ++){
        var project = projects[i];//var project = projects.item(i);
        var name = project.selectSingleNode("name").text;
        
        opt = document.createElement("OPTION");
        opt.text = opt.value = name;
        oDummyForm.oEntryPropType.add(opt);
    }
}

function fillEncodings()
{
    var form = document.oDummyForm;
    var options = form.oEncoding.options;
    for (i = options.length; i >= 1; --i)
    {
        options.remove(i-1);
    }

    var option = document.createElement("OPTION");
    option.text = option.value = "UTF-8";
    options.add(option);

    var option = document.createElement("OPTION");
    option.text = option.value = "UTF-16LE";
    options.add(option);

    var option = document.createElement("OPTION");
    option.text = option.value = "UTF-16BE";
    options.add(option);
}

function fillPropTypes(){
    var form = document.oDummyForm;
    var options = form.oEntryPropType.options;
    var option = document.createElement("OPTION");
    option.text = option.value = "<%=propTypes[0]%>";
    options.add(option);
    
    var option = document.createElement("OPTION");
    option.text = option.value = "<%=propTypes[1]%>";
    options.add(option);
    
    var option = document.createElement("OPTION");
    option.text = option.value = "<%=propTypes[2]%>";
    options.add(option);
    
    var option = document.createElement("OPTION");
    option.text = option.value = "<%=propTypes[3]%>";
    options.add(option);
    
    var option = document.createElement("OPTION");
    option.text = option.value = "<%=propTypes[4]%>";
    options.add(option);
}

function checkEmptyTM()
{
  var dom;
  if(window.navigator.userAgent.indexOf("MSIE")>0)
  {
    dom = oDefinition.XMLDocument;
  }
  else if(window.DOMParser)
  { 
    var parser = new DOMParser();
    dom = parser.parseFromString(xmlDefinition,"text/xml");
  }
  
  var tuCount = dom.selectSingleNode("/statistics/tus").text;

  if (parseInt(tuCount) == 0)
  {
    idWarning.style.display = '';
  }
}

function showCalendar(id) {
    var cal1 = new calendar2(document.getElementById(id));
    cal1.year_scroll = true;
    cal1.time_comp = false;
    cal1.popup();
}

function doOnLoad()
{

   // Load the Guides
   loadGuides();

   fillLanguages();
   fillEncodings();
   //fillPropTypes();
   
   fillProjects();
   selectValue(document.oDummyForm.oEncoding, "UTF-8");

   parseExportOptions();

   checkEmptyTM();
}
</SCRIPT>
</HEAD>
<BODY LEFTMARGIN="0" RIGHTMARGIN="0" TOPMARGIN="0" MARGINWIDTH="0"
 MARGINHEIGHT="0" CLASS="standardText" onload="doOnLoad()">
<%@ include file="/envoy/common/header.jspIncl" %>
<%@ include file="/envoy/common/navigation.jspIncl" %>
<%@ include file="/envoy/wizards/guides.jspIncl" %>

<DIV style="display:none;">
<XML id="oDefinition"><%=xmlDefinition%></XML>
<XML id="oExportOptions"><%=xmlExportOptions%></XML>
<XML id="oProject"><%=xmlProject%></XML>
</DIV>

<FORM NAME="oForm" ACTION="" METHOD="post">
<INPUT TYPE="hidden" NAME="exportoptions"
 VALUE="ExportOptions XML goes here"></INPUT>
</FORM>

<DIV ID="contentLayer"
 STYLE=" POSITION: ABSOLUTE; Z-INDEX: 9; TOP: 108px; LEFT: 20px; RIGHT: 20px;">
<DIV CLASS="mainHeading" ID="idHeading"><%=lb_title%></DIV>
<BR>

<DIV id="idWarning" style="color: red; display: none;">
<%=bundle.getString("lb_tm_export_note_tm_is_empty")%>
<BR><BR>
</DIV>

<FORM NAME="oDummyForm" ACTION="" METHOD="post">

<div style="margin-bottom:10px">
<%=bundle.getString("lb_select_entries_to_export")%>:<BR>
  <div style="margin-left: 40px">
      <TABLE CELLPADDING=2 CELLSPACING=0 BORDER=0 CLASS="standardText">
          <tr align="left" valign="center">
            <td colspan=2><input type="radio" name="oEntries" id="idEntries1" CHECKED onclick="doByEntry()">
              <label for="idEntries1"><%= bundle.getString("lb_entire_tm")%></label>
            </td>
          </tr>
          <tr>
	    <td>
	       <input type="radio" name="oEntries" id="idEntries2"
                 onclick="idLanguageList.disabled = false;idPropTypeList.disabled = true;idLanguageList.focus();">
               <label for="idEntries2"><%= bundle.getString("lb_by_language")%></label>
            </td>
            <td><select name="oEntryLang" id="idLanguageList" disabled onchange="doByLanguage()"></select></td>
          </tr>
          <tr>
            <td><input type="radio" name="oEntries" id="idEntries3"
                onclick="idPropTypeList.disabled = false;idLanguageList.disabled = true;idPropTypeList.focus();">
                <label for="idEntries2"><%= propTypeTitle%></label>
            </td>
            <td><select name="oEntryPropType" id="idPropTypeList" disabled onchange="doByProject()" MULTIPLE></select></td>
          </tr>
        </TABLE>
    <br/>
  </div>
</div>

<BR>
<div style="margin-bottom:10px">
 <%=bundle.getString("lb_tm_filter_entries_by") %>: <BR>
 <div style="margin-left: 40px">
 <TABLE CELLPADDING=2 CELLSPACING=2 BORDER=0 CLASS=standardText>
 <thead>
    <col align="right" valign="baseline" class="standardText">
    <col align="left"  valign="baseline" class="standardText">
  </thead>
 <tr>
 <td><%=bundle.getString("lb_creation_date") %>:</td>
    <td>
      <%=bundle.getString("lb_after").toLowerCase() %> <input name="fltCreatedAfter" id="idCos" type="text" size="10" readonly="true">
      <img src="/globalsight/includes/Calendar.gif" class="calendar" title="<%=lb_calendar_title %>" onclick="showCalendar('idCos')"> &nbsp;
      <%=bundle.getString("lb_and_or") %>
      <%=bundle.getString("lb_before").toLowerCase() %> <input name="fltCreatedBefore" id="idCoe" type="text" size="10" readonly="true">
      <img src="/globalsight/includes/Calendar.gif" class="calendar" title="<%=lb_calendar_title %>" onclick="showCalendar('idCoe')"> &nbsp;
      <span class='info'>(DD/MM/YYYY)</span>
    </td>
    </tr>
    </TABLE>
   </div>
</div>


<BR>
<div style="margin-bottom:10px">
<TABLE CELLPADDING=2 CELLSPACING=2 BORDER=0 CLASS=standardText>
  <TR VALIGN="TOP">
    <TD WIDTH=100><%=bundle.getString("lb_terminology_export_format")%></TD>
    <TD>
      <input type="radio" name="oType" id="idXml" CHECKED="true"
      onclick="doTypeChanged()"><label for="idXml">
      <%= bundle.getString("lb_tm_export_format_gtmx") %></label></input>
      <BR>
      <input type="radio" name="oType" id="idTmx1"
      onclick="doTypeChanged()"><label for="idTmx1">
      <%= bundle.getString("lb_tm_export_format_tmx1") %></label></input>
      <BR>
      <input type="radio" name="oType" id="idTmx2"
      onclick="doTypeChanged()"><label for="idTmx2">
      <%= bundle.getString("lb_tm_export_format_tmx2") %></label></input>
      <BR>
      <input type="radio" name="oType" id="idTtmx"
      onclick="doTypeChanged()"><label for="idTtmx">
      <%= bundle.getString("lb_tm_export_format_ttmx") %></label></input>
      <BR>
    </TD>
  </TR>
</TABLE>
</div>

<div style="margin-bottom:10px">
<span style="width:100px"><%=bundle.getString("lb_file_encoding")%></span>
<SELECT name="oEncoding" id="idEncoding"></SELECT><BR/>
</div>

<div style="margin-bottom:10px">
<INPUT type="checkbox" name="oChangeCreationId" id="idChangeCreationId" />
<span style="width:400px"><%=bundle.getString("lb_tm_export_change_creationid_for_mt")%></span>
</div>

</FORM>
<BR>

<DIV id="idButtons" align="left">
<button TABINDEX="0" onclick="doCancel();"><%=bundle.getString("lb_cancel")%></button>&nbsp;
<button TABINDEX="0" onclick="doNext();"><%=bundle.getString("lb_next")%></button>
</DIV>


<BR>
<TABLE>
<TR><TD ALIGN="LEFT"><IMG SRC="/globalsight/images/TMX.gif"></TD></tr>
<TR><TD ALIGN="LEFT"><SPAN CLASS="smallText"><%=bundle.getString("lb_tmx_logo_text1")%><BR><%=bundle.getString("lb_tmx_logo_text2")%></SPAN></TD></TR>
</TABLE>

</BODY>
</HTML>