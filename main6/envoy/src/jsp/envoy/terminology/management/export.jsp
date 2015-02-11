<%@ page
    contentType="text/html; charset=UTF-8"
    errorPage="/envoy/common/error.jsp"
    import="java.util.*,com.globalsight.everest.webapp.webnavigation.LinkHelper,
        java.util.ResourceBundle,
        com.globalsight.terminology.exporter.ExportOptions,
        com.globalsight.util.edit.EditUtil,
        com.globalsight.everest.servlet.util.SessionManager,
        com.globalsight.everest.webapp.pagehandler.PageHandler,
        com.globalsight.everest.webapp.WebAppConstants"
    session="true"
%>
<%--
State machinery for export:

export --> exportFileOptions --\
                               |
     /-------------------------/
     |
     |--XML/MTF--> exportOutputOptions --Next----------------------------------> exportProgress
     |                                                                            ^
     |                                                                            |
     \--CSV------> exportOutputOptionsCSV --> exportColumnOptions ------Next------/

export.jsp sets <selectOptions>
exportFileOptions sets <fileOptions>
exportOutputOptions.jsp sets <outputOptions>
exportOutputOptionsCSV.jsp sets <outputOptions> for CSV files
exportColumnOptions.jsp sets <columnOptions>
exportProgress.jsp runs the export and allows to download result file

--%>
<jsp:useBean id="nextXML" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="nextCSV" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="cancel" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<%
ResourceBundle bundle = PageHandler.getBundle(session);
SessionManager sessionMgr = (SessionManager)session.getAttribute(
  WebAppConstants.SESSION_MANAGER);

String xmlDefinition =
  (String)sessionMgr.getAttribute(WebAppConstants.TERMBASE_DEFINITION);
String xmlExportOptions =
  (String)sessionMgr.getAttribute(WebAppConstants.TERMBASE_EXPORT_OPTIONS);
String termbaseName =
  (String)sessionMgr.getAttribute(WebAppConstants.TERMBASE_TB_NAME);

String urlNextXML = nextXML.getPageURL();
String urlNextHTM = nextXML.getPageURL(); // same as XML for now
String urlNextCSV = nextCSV.getPageURL();
String urlCancel  = cancel.getPageURL();
String lb_calendar_title = bundle.getString("lb_calendar_title");
%>
<HTML>
<!-- This is envoy\terminology\management\export.jsp -->
<HEAD>
<TITLE><%=bundle.getString("lb_terminology_export")%></TITLE>
<STYLE>
{ font: Tahoma Verdana Arial 10pt; }
TABLE, TD, INPUT, SELECT { font: Tahoma Verdana Arial 10pt; }

.link         { color: blue; cursor: hand; cursor:pointer; text-decoration: underline; }
.info         { font-style: normal; }
.calendar     { width:16px; height:15px; cursor:pointer; vertical-align:middle; }
</STYLE>
<SCRIPT SRC="/globalsight/includes/setStyleSheet.js"></SCRIPT>
<%@ include file="/envoy/wizards/guidesJavascript.jspIncl" %>
<%@ include file="/envoy/common/warning.jspIncl" %>
<%@ include file="/includes/compatibility.jspIncl" %>
<SCRIPT SRC="/globalsight/includes/library.js"></SCRIPT>
<SCRIPT SRC="envoy/terminology/management/import.js"></SCRIPT>
<SCRIPT SRC="envoy/terminology/management/objects_js.jsp"></SCRIPT>
<SCRIPT type="text/javascript" src="/globalsight/includes/report/calendar2.js"></SCRIPT>
<SCRIPT>
var needWarning = false;
var objectName = "";
var guideNode = "terminology";
var helpFile = "<%=bundle.getString("help_termbase_export")%>";
var xmlDefinition = 
	'<%=xmlDefinition.replace("\\", "\\\\").replace("\r","").replace("\n","").trim()%>';
var xmlExportOptions = 
	'<%=xmlExportOptions.replace("\\", "\\\\").replace("\r","").replace("\n","").trim()%>';

// Array of fields defined in the termbase
var g_termbaseFields = new Array();
var g_termbaseLanguages = new Array();
var g_advancedFilter = new Array();

function Result(message, description, element)
{
    this.message = message;
    this.description = description;
    this.element = element;
    this.dom = null;
}

function FilterParameters(filter)
{
    this.languages = g_termbaseLanguages;
    this.fields = g_termbaseFields;
    this.filter = filter;
    this.window = window;
}

FilterParameters.prototype.getLanguages = function()
{
    return this.languages;
}

FilterParameters.prototype.getFields = function()
{
    return this.fields;
}

FilterParameters.prototype.getFilter = function()
{
    return this.filter;
}

FilterParameters.prototype.getWindow = function()
{
    return this.window;
}

function parseExportOptions()
{
  var form = document.oDummyForm;
  var dom = fnGetDOM(oExportOptions,xmlExportOptions);//var dom = oExportOptions.XMLDocument;
  var nodes, node;
  var selectMode, selectLanguage, duplicateHandling, fileType, fileEncoding;
  var createdBy, modifiedBy, status, domain, project;
  var createdAfter, createdBefore, modifiedAfter, modifiedBefore;
  var level, field, operator, value, matchcase;

  
  node = dom.selectSingleNode("/exportOptions/selectOptions");
  selectMode = node.selectSingleNode("selectMode").text;
  selectLanguage = node.selectSingleNode("selectLanguage").text;
  duplicateHandling = node.selectSingleNode("duplicateHandling").text;

  node = dom.selectSingleNode("/exportOptions/filterOptions");
  createdBy = node.selectSingleNode("createdby").text;
  modifiedBy = node.selectSingleNode("modifiedby").text;
  createdAfter = node.selectSingleNode("createdafter").text;
  createdBefore = node.selectSingleNode("createdbefore").text;
  modifiedAfter = node.selectSingleNode("modifiedafter").text;
  modifiedBefore = node.selectSingleNode("modifiedbefore").text;
  status = node.selectSingleNode("status").text;
  domain = node.selectSingleNode("domain").text;
  project = node.selectSingleNode("project").text;

  nodes = dom.selectNodes("/exportOptions/filterOptions/conditions/condition");
  for (var i = 0; i < nodes.length; i++)
  {
    node = nodes[i];//node = nodes.item(i);

    level = node.selectSingleNode("level").text;
    field = node.selectSingleNode("field").text;
    operator = node.selectSingleNode("operator").text;
    value = node.selectSingleNode("value").text;
    matchcase = node.selectSingleNode("matchcase").text;

    g_advancedFilter.push(new FilterCondition(level, field,
      operator, value, matchcase));
  }

  node = dom.selectSingleNode("/exportOptions/fileOptions");
  fileType = node.selectSingleNode("fileType").text;
  fileEncoding = node.selectSingleNode("fileEncoding").text;

  if (selectMode == "<%=ExportOptions.SELECT_LANGUAGE%>")
  {
    form.oEntries[1].checked = true;
    form.oEntryLang.disabled = false;
  }

  selectValue(form.oEntryLang, selectLanguage);

<%--
  if (duplicateHandling == "<%=ExportOptions.DUPLICATE_OUTPUT%>")
  {
    form.oSynonyms[1].checked = true;
  }
--%>

  form.fltCreatedBy.value = createdBy;
  form.fltModifiedBy.value = modifiedBy;
  form.fltCreatedAfter.value = createdAfter;
  form.fltCreatedBefore.value = createdBefore;
  form.fltModifiedAfter.value = modifiedAfter;
  form.fltModifiedBefore.value = modifiedBefore;
  selectMultipleValues(form.fltStatus, status);
  form.fltDomain.value = domain;
  form.fltProject.value = project;

  selectValue(form.oType, fileType);
  selectValue(form.oEncoding, fileEncoding);
}

function fnGetDOM(xmlId,xmlStr)
{
  var dom;
  if(ie)
  {
	dom = xmlId.XMLDocument;
  }
  else if(window.DOMParser)
  { 
	var parser = new DOMParser();
	dom = parser.parseFromString(xmlStr,"text/xml");
  }
  return dom;
}

function buildExportOptions()
{
  var result = new Result("", "", null);
  var form = document.oDummyForm;
  var dom;
  //var dom = oExportOptions.XMLDocument;
  dom = fnGetDOM(oExportOptions,xmlExportOptions);
  var node;
  var sel;

  // SELECT OPTIONS
  node = dom.selectSingleNode("/exportOptions/selectOptions");

  if (form.oEntries[0].checked)
  {
     node.selectSingleNode("selectMode").text =
       "<%=ExportOptions.SELECT_ALL%>";
  }
  else
  {
     node.selectSingleNode("selectMode").text =
       "<%=ExportOptions.SELECT_LANGUAGE%>";
  }

  sel = form.oEntryLang;
  node.selectSingleNode("selectLanguage").text =
    sel.options[sel.selectedIndex].value;

<%--
  if (form.oSynonyms[0].checked)
  {
     node.selectSingleNode("duplicateHandling").text =
       "<%=ExportOptions.DUPLICATE_DISCARD%>";
  }
  else
  {
     node.selectSingleNode("duplicateHandling").text =
       "<%=ExportOptions.DUPLICATE_OUTPUT%>";
  }
--%>

  var datecheck;
  if (datecheck = validateDate(form.fltCreatedAfter)) return datecheck;
  if (datecheck = validateDate(form.fltCreatedBefore)) return datecheck;
  if (datecheck = validateDate(form.fltModifiedAfter)) return datecheck;
  if (datecheck = validateDate(form.fltModifiedBefore)) return datecheck;

  // FILTER OPTIONS
  node = dom.selectSingleNode("/exportOptions/filterOptions");
  node.selectSingleNode("createdby").text  = form.fltCreatedBy.value;
  node.selectSingleNode("modifiedby").text = form.fltModifiedBy.value;
  node.selectSingleNode("createdafter").text = form.fltCreatedAfter.value;
  node.selectSingleNode("createdbefore").text = form.fltCreatedBefore.value;
  node.selectSingleNode("modifiedafter").text = form.fltModifiedAfter.value;
  node.selectSingleNode("modifiedbefore").text = form.fltModifiedBefore.value;

  sel = form.fltStatus;
  node.selectSingleNode("status").text = getSelectedValues(sel);

  node.selectSingleNode("domain").text = form.fltDomain.value;
  node.selectSingleNode("project").text = form.fltProject.value;

  // ADVANCED FILTER OPTONS
  node = dom.selectSingleNode("/exportOptions/filterOptions/conditions");
  while (node.hasChildNodes())
  {
    node.removeChild(node.firstChild);
  }

  for (var i = 0; i < g_advancedFilter.length; ++i)
  {
    var condition = g_advancedFilter[i];

    var elem  = dom.createElement("condition");
    var level = dom.createElement("level");
    var field = dom.createElement("field");
    var operator = dom.createElement("operator");
    var value = dom.createElement("value");
    var matchcase = dom.createElement("matchcase");

    level.text = condition.getLevel();
    field.text = condition.getField();
    operator.text = condition.getOperator();
    value.text = condition.getValue();
    matchcase.text = (condition.getMatchCase() ? "true" : "false");

    elem.appendChild(level);
    elem.appendChild(field);
    elem.appendChild(operator);
    elem.appendChild(value);
    elem.appendChild(matchcase);

    node.appendChild(elem);
  }

  // FILE OPTIONS
  node = dom.selectSingleNode("/exportOptions/fileOptions");

  sel = form.oType;
  node.selectSingleNode("fileType").text =
    sel.options[sel.selectedIndex].value;

  sel = form.oEncoding;
  node.selectSingleNode("fileEncoding").text =
    sel.options[sel.selectedIndex].value;

  //alert("options = " + oExportOptions.xml);

  result.dom = dom;
  return result;
}

// Validates a date to be DD/MM/YYYY and nothing else.
function validateDate(control)
{
  var date = control.value;
  if (!date) return;

  if (date.length != 10)
  {
    return dateError(control);
  }

  var arr = date.split('/');
  if (arr.length != 3)
  {
    return dateError(control);
  }

  var val;

  val = parseInt(arr[0], 10);
  if (!(val >= 1 && val <= 31))
  {
    return dateError(control, "Invalid day " + arr[0] + ".");
  }

  var val = parseInt(arr[1], 10);
  if (!(val >= 1 && val <= 12))
  {
    return dateError(control, "Invalid month " + arr[1] + ".");
  }

  var val = parseInt(arr[2], 10);
  if (!(val >= 1900 && val <= 2099))
  {
    return dateError(control, "Year " + arr[2] + " must be >= 1900 and <= 2099.");
  }

  return null;
}

function dateError(control, desc)
{
  return new Result("Dates must be in the format DD/MM/YYYY.",
    (desc ? desc : ""), control);
}

// Called by exportFilter dialog, need to copy objects because
// code created in a closed window cannot be executed (for both
// Array and FilterCondition objects)
function SetFilterConditions(filters)
{
  g_advancedFilter = new Array();

  for (var i = 0; i < filters.length; i++)
  {
    var filter = filters[i];

    var clone = new FilterCondition(
      filter.getLevel(), filter.getField(), filter.getOperator(),
      filter.getValue(), filter.getMatchCase());

    //alert(clone);

    g_advancedFilter.push(clone);
  }
}

function advancedFilters()
{
  var arg = new FilterParameters(g_advancedFilter);

  var temp = window.showModalDialog(
    "/globalsight/envoy/terminology/management/exportFilter.jsp", arg,
    "dialogHeight:400px; dialogWidth:500px; " +
    "center:yes; resizable:no; status:no; help:no;");

  // return value set with SetFilterConditions()
}

function doCancel()
{
    window.navigate("<%=urlCancel%>");
}

function doNext()
{
    var result = buildExportOptions();

    if (result.message != null && result.message != "")
    {
        alert(result.message +
          (result.description ? '\n' + result.description : ''));
        result.element.focus();
    }
    else
    {
        var url;
        var dom;
        if(window.navigator.userAgent.indexOf("MSIE")>0)
        {
        	dom = oExportOptions.XMLDocument;
        }
        else
        {
        	dom = result.dom;
        }
        
        var node = dom.selectSingleNode("/exportOptions/fileOptions/fileType");

        if (node.text == "<%=ExportOptions.TYPE_XML%>" ||
            node.text == "<%=ExportOptions.TYPE_MTF%>" ||
            node.text == "<%=ExportOptions.TYPE_HTM%>" ||
            node.text == "<%=ExportOptions.TYPE_TBX%>")
        {
            url = "<%=urlNextXML%>";
        }
        else if (node.text == "<%=ExportOptions.TYPE_CSV%>")
        {
            url = "<%=urlNextCSV%>";
        }

        url +=
            "&<%=WebAppConstants.TERMBASE_ACTION%>" +
            "=<%=WebAppConstants.TERMBASE_ACTION_ANALYZE_TERMBASE%>";

        oForm.action = url;

        if(window.navigator.userAgent.indexOf("MSIE")>0)
        {
        	oForm.exportoptions.value = oExportOptions.xml;
        }
        else
        {
        	oForm.exportoptions.value = XML.getDomString(result.dom);
        }
        
        oForm.submit();
    }
}

function doByEntry()
{
  document.oDummyForm.oEntryLang.disabled = true;
}

function doByLanguage()
{
  document.oDummyForm.oEntries[1].click();
  document.oDummyForm.oEntryLang.disabled = false;
}

function doTypeChanged()
{
  var form = document.oDummyForm;
  var select = form.oType;
  var type = select.options[select.selectedIndex].value;

  // HTM use UTF-8. Everything else can select encoding.
  select = form.oEncoding;
  if (type == "<%=ExportOptions.TYPE_HTM%>")
  {
    selectValue(select, "UTF-8")
    select.disabled = true;
    form.fltStatus.disabled = false;
  }
  else if (type == "<%=ExportOptions.TYPE_TBX%>")
  {
	form.fltStatus.disabled = true;
	select.disabled = false;
  }
  else
  {
    select.disabled = false;
    form.fltStatus.disabled = false;
  }
}

// for selects with multiple selections
function getSelectedValues(select)
{
  var result = '';

  var options = select.options;
  for (var i = 0; i < options.length; ++i)
  {
    var option = options[i];
    if (option.selected)
    {
      result += option.value;
      result += ',';
    }
  }

  return result;
}

// for selects with multiple selections
function selectMultipleValues(select, values)
{
  var vals = values.split(',');
  var options = select.options;

  for (var i = 0; i < vals.length; ++i)
  {
    var val = vals[i];
    if (!val) continue;

    for (var j = 0; j < options.length; j++)
    {
      var option = options[j];//var option = options.item(j);

      if (option.value == val)
      {
        option.selected = true;
      }
    }
  }
}

function selectValue(select, value)
{
  for (var i = 0; i < select.options.length; ++i)
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
  var domDefinition = fnGetDOM(oDefinition,xmlDefinition);//var dom = oDefinition.XMLDocument;
  var names = domDefinition.selectNodes("/definition/languages/language/name");

  for (i = 0; i < names.length; ++i)
  {
    var name = names[i].text;//var name = names.item(i).text;

    oOption = document.createElement("OPTION");
    oOption.text = name;
    oOption.value = name;
    oDummyForm.oEntryLang.add(oOption);
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

function compareLanguages(p_a, p_b)
{
    var aname = p_a.name;
    var bname = p_b.name;
    if (aname == bname) return 0;
    if (aname > bname) return 1;
    if (aname < bname) return -1;
}

function setTermbaseLanguages(p_definition)
{
    var nodes, node;

    nodes = p_definition.selectNodes("/definition/languages/language");
    for (var i = 0; i < nodes.length; i++)
    {
        node = nodes[i];//node = nodes.item(i);

        var name = node.selectSingleNode("name").text;
        var locale = node.selectSingleNode("locale").text;
        var hasterms = node.selectSingleNode("hasterms").text;
        hasterms = (hasterms == "true" ? true : false);
        var exists = true;

        var language = new Language(name, locale, hasterms, exists);

        g_termbaseLanguages.push(language);
    }

    g_termbaseLanguages.sort(compareLanguages);
}

function setTermbaseFields(p_definition)
{
    // compute cached array of known fields
    var nodes = p_definition.selectNodes("/definition/fields/field");
    for (var i = 0; i < nodes.length; i++)
    {
        var node = nodes[i];//var node = nodes.item(i);

        var name = node.selectSingleNode("name").text;
        var type = node.selectSingleNode("type").text;
        var system =
          (node.selectSingleNode("system").text == "true" ? true : false);
        var values = node.selectSingleNode("values").text;
        var format = getFieldFormatByType(type);

        var field = new Field(name, type, format, system, values);

        g_termbaseFields.push(field);
    }

    // alert(g_termbaseFields);
}

// Overwrite field names if customer uses custom names for e.g. Domain.
function overwriteFieldNames()
{
  for (var i = 0; i < g_termbaseFields.length; i++)
  {
    var field = g_termbaseFields[i];

    if (field.type == 'domain')
    {
      idFldDomain.innerText = field.name;
    }
    else if (field.type == 'project')
    {
      idFldProject.innerText = field.name;
    }
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

   var domDefinition = fnGetDOM(oDefinition,xmlDefinition);
   setTermbaseLanguages(domDefinition);
   setTermbaseFields(domDefinition);
   overwriteFieldNames();

   parseExportOptions();
}
</SCRIPT>
</HEAD>
<BODY LEFTMARGIN="0" RIGHTMARGIN="0" TOPMARGIN="0" MARGINWIDTH="0"
 MARGINHEIGHT="0" CLASS="standardText" onload="doOnLoad()">
<%@ include file="/envoy/common/header.jspIncl" %>
<%@ include file="/envoy/common/navigation.jspIncl" %>
<%@ include file="/envoy/wizards/guides.jspIncl" %>

<DIV style="display:none">
<XML id="oDefinition"><%=xmlDefinition%></XML>
<XML id="oExportOptions"><%=xmlExportOptions%></XML>
</DIV>

<FORM NAME="oForm" ACTION="" METHOD="post">
<INPUT TYPE="hidden" NAME="exportoptions"
 VALUE="ExportOptions XML goes here"></INPUT>
</FORM>

<DIV ID="contentLayer"
 STYLE=" POSITION: ABSOLUTE; Z-INDEX: 9; TOP: 108px; LEFT: 20px; RIGHT: 20px;">
<SPAN CLASS="mainHeading" ID="idHeading"><%=bundle.getString("lb_terminology_export_data")%></SPAN>
<p/>

<FORM NAME="oDummyForm">

<div style="margin-bottom:10px">
<B><%=bundle.getString("lb_terminology_select_entries_to_export")%></B><BR>
<div style="margin-left: 40px">
<input type="radio" name="oEntries" id="idEntries1" CHECKED
  onclick="doByEntry()">
  <label for="idEntries1"><%=bundle.getString("lb_terminology_entire_termbase")%></label>
<br>
<input type="radio" name="oEntries" id="idEntries2"
  onclick="idLanguageList.disabled = false; idLanguageList.focus();">
  <label for="idEntries2"><%=bundle.getString("lb_by_language")%></label>
  <select name="oEntryLang" id="idLanguageList" disabled
    onchange="doByLanguage()"></select>
<br>
</div>
</div>

<div style="margin-bottom:10px;">
<B><%=bundle.getString("lb_tm_filter_entries_by") %>:</B><BR>
<div style="margin-left: 40px">
<table class="standardText">
  <thead>
    <col align="right" valign="baseline" class="standardText">
    <col align="left"  valign="baseline" class="standardText">
  </thead>
  <tr>
    <td><%=bundle.getString("lb_creation_user") %>:</td>
    <td><input name="fltCreatedBy" type="text" size="20"></td>
  </tr>
  <tr>
    <td><%=bundle.getString("lb_modification_user") %>:</td>
    <td><input name="fltModifiedBy" type="text" size="20"></td>
  </tr>
  <tr>
    <td><%=bundle.getString("lb_creation_date") %>:</td>
    <td>
      <%=bundle.getString("lb_after") %> <input name="fltCreatedAfter" id="idCos" type="text" size="10">
      <img src="/globalsight/includes/Calendar.gif" class="calendar" title="<%=lb_calendar_title %>"
      onclick="showCalendar('idCos')"> &nbsp;
      <%=bundle.getString("lb_and_or") %>
      <%=bundle.getString("lb_before") %> <input name="fltCreatedBefore" id="idCoe" type="text" size="10">
      <img src="/globalsight/includes/Calendar.gif" class="calendar" title="<%=lb_calendar_title %>"
      onclick="showCalendar('idCoe')"> &nbsp;
      <span class='info'>(DD/MM/YYYY)</span>
    </td>
  </tr>
  <tr>
    <td><%=bundle.getString("lb_modification_date") %>:</td>
    <td>
      <%=bundle.getString("lb_after") %> <input name="fltModifiedAfter" id="idMos" type="text" size="10">
      <img src="/globalsight/includes/Calendar.gif" class="calendar" title="<%=lb_calendar_title %>"
      onclick="showCalendar('idMos')"> &nbsp;
      <%=bundle.getString("lb_and_or") %>
      <%=bundle.getString("lb_before") %> <input name="fltModifiedBefore" id="idMoe" type="text" size="10">
      <img  src="/globalsight/includes/Calendar.gif" class="calendar" title="<%=lb_calendar_title %>"
      onclick="showCalendar('idMoe')"> &nbsp;
      <span class='info'>(DD/MM/YYYY)</span>
    </td>
  </tr>
  <tr>
    <td><span id="idFldDomain"><%=bundle.getString("lb_domain") %></span>:</td>
    <td><input name="fltDomain" type="text" size="20"></td>
  </tr>
  <tr>
    <td><span id="idFldProject"><%=bundle.getString("lb_project") %></span>:</td>
    <td><input name="fltProject" type="text" size="20"></td>
  </tr>
  <tr>
    <td valign="top"><%=bundle.getString("lb_status") %>:</td>
    <td>
      <select name="fltStatus" multiple size="3">
	<option value="proposed"><%=bundle.getString("lb_proposed_l") %></option>
	<option value="reviewed"><%=bundle.getString("lb_reviewed_l") %></option>
	<option value="approved"><%=bundle.getString("lb_approved_l") %></option>
      </select>
    </td>
  </tr>
  <tr>
    <td colspan="2" align="left">
      <span class="link" onclick="advancedFilters()">
      <B><%=bundle.getString("lb_advanced_filters") %>...</B></span>
    </td>
  </tr>
</table>
</div>
</div>

<div style="margin-bottom:10px;">
</div>

<%-- Field-specific filters, not implemented yet.
<div style="margin-bottom:10px;">
<B>Advanced filters:</B><BR>
<div style="margin-left: 40px">
Field:
<select>
  <option value="definition">Definition</option>
</select>
<select>
  <option value="contains">contains</option>
  <option value="contains">doesn't contain</option>
  <option value="equals">equals</option>
</select>
<input type="text" size="20">
</div>
</div>
--%>

<div style="margin-bottom:10px">
<span style="width:100px">
<B><%=bundle.getString("lb_terminology_export_format")%></B>
</span>
  <select name="oType" id="idType" onclick="doTypeChanged()">
    <option value="<%=ExportOptions.TYPE_XML%>"><%=bundle.getString("lb_terminology_globalsight_format")%></option>
    <option value="<%=ExportOptions.TYPE_MTF%>"><%=bundle.getString("lb_terminology_multiterm_ix_format")%></option>
<%-- Not implemented yet
    <option value="<%=ExportOptions.TYPE_MTW%>"><%=bundle.getString("lb_terminology_multiterm_1x_format")%></option>
    <option value="<%=ExportOptions.TYPE_TBX%>"><%=bundle.getString("lb_terminology_tbx_xlt_format")%></option>
    <option value="<%=ExportOptions.TYPE_CSV%>"><%=bundle.getString("lb_terminology_csv_format")%></option>
    <option value="<%=ExportOptions.TYPE_RTF%>"><%=bundle.getString("lb_terminology_rtf_format")%></option>
--%>
    <option value="<%=ExportOptions.TYPE_HTM%>">HTML</option>
    <option value="<%=ExportOptions.TYPE_TBX%>">TBX</option>
  </select>
</div>

<div style="margin-bottom:10px">
<span style="width:100px">
<B><%=bundle.getString("lb_terminology_import_encoding")%></B>
</span>
<SELECT name="oEncoding" id="idEncoding"></SELECT>
</div>

<BR>

<DIV id="idButtons" align="left">
<input type="button" TABINDEX="0" onclick="doCancel();" value="<%=bundle.getString("lb_cancel")%>"/>&nbsp;
<input type="button" TABINDEX="0" onclick="doNext();" value="<%=bundle.getString("lb_next")%>"/>
</DIV>

</FORM>

</BODY>
</HTML>