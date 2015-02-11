<%@ page
    contentType="text/html; charset=UTF-8"
    errorPage="/envoy/common/error.jsp"
    import="java.util.*,com.globalsight.everest.webapp.webnavigation.LinkHelper,
        java.util.ResourceBundle,
        com.globalsight.util.edit.EditUtil,
        com.globalsight.everest.servlet.util.SessionManager,
        com.globalsight.everest.webapp.pagehandler.PageHandler,
        com.globalsight.everest.webapp.WebAppConstants"
    session="true"
%>
<jsp:useBean id="ok" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="refresh" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="save" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="reindex" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="schedule" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<%
ResourceBundle bundle = PageHandler.getBundle(session);
SessionManager sessionMgr = (SessionManager)session.getAttribute(
    WebAppConstants.SESSION_MANAGER);

String xmlDefinition =
  (String)sessionMgr.getAttribute(WebAppConstants.TERMBASE_DEFINITION);
String xmlStatistics =
  (String)sessionMgr.getAttribute(WebAppConstants.TERMBASE_STATISTICS);

String str_tbid =
  (String)sessionMgr.getAttribute(WebAppConstants.TERMBASE_TB_ID);
String str_tbname =
  (String)sessionMgr.getAttribute(WebAppConstants.TERMBASE_TB_NAME);

String urlOK       = ok.getPageURL();
String urlRefresh  = refresh.getPageURL();
String urlSave     = save.getPageURL();
String urlReindex  = reindex.getPageURL();
String urlSchedule = schedule.getPageURL();

// Perform error handling, then clear out session attribute.
String errorScript = "";
String error = (String)sessionMgr.getAttribute(WebAppConstants.TERMBASE_ERROR);
if (error != null)
{
  errorScript = "var error = new Error();" +
    "error.message = '" + EditUtil.toJavascript(bundle.getString("lb_import_error")) + "';" + 
    "error.description = '" + EditUtil.toJavascript(error) +
    "'; showError(error);";
}
sessionMgr.removeElement(WebAppConstants.TERMBASE_ERROR);

String title = bundle.getString("lb_termbase_indexes");
String helper = bundle.getString("helper_text_tb_indexes_main");
%>
<HTML XMLNS:gs>
<HEAD>
<TITLE><%=title %></TITLE>
<STYLE>
#idGeneral,
#idFields    { margin-top: 5pt; }

FORM         { display: inline; }

TEXTAREA     { overflow: auto; }
TD           { font: 9pt arial;}
.header      { font: bold 9pt arial; color: black; }
.link        { color: blue; cursor: hand; margin-bottom: 2pt;
               text-decoration: underline;
             }
</STYLE>
<SCRIPT SRC="/globalsight/includes/setStyleSheet.js"></SCRIPT>
<%@ include file="/envoy/wizards/guidesJavascript.jspIncl" %>
<%@ include file="/envoy/common/warning.jspIncl" %>
<%@ include file="/includes/compatibility.jspIncl" %>
<SCRIPT src="/globalsight/includes/library.js"></SCRIPT>
<SCRIPT src="/globalsight/includes/xmlextras.js"></SCRIPT>
<SCRIPT src="/globalsight/includes/utilityScripts.js"></SCRIPT>
<SCRIPT src="envoy/terminology/management/protocol.js"></SCRIPT>
<SCRIPT>
var needWarning = true;
var objectName = "Termbase";
var guideNode = "terminology";
var helpFile = "<%=bundle.getString("help_termbase_index_main")%>";

eval("<%=errorScript%>");

var tbid = "<%=str_tbid%>";
var tbname = "<%=str_tbname%>";

var aLanguages = new Array();
var aIndexes = new Array();
var aIndexStats = new Array();

var isIndexing = false;

/**
 * Object holding information about languages defined in a termbase.
 */
function Language(name, locale, hasterms)
{
    this.name = name;
    this.locale = locale;
    this.hasterms = hasterms;
}

function compareLanguages(p_a, p_b)
{
  var aname = p_a.name;
  var bname = p_b.name;
  if (aname == bname) return 0;
  if (aname > bname) return 1;
  if (aname < bname) return -1;
}

/**
 * Object holding information about indexes defined on languages in a termbase.
 */
function Index(name, locale, type)
{
    this.name = name;
    this.locale = locale;
    this.type = type;
}

Index.prototype.toString = function()
{
    return "Index `" + this.name + "' type " + this.type + " (" + this.locale + ")";
}

function IndexStat(name, entryCount, fulltextCount, fuzzyCount)
{
    this.name = name;
    this.entryCount = parseInt(entryCount);
    this.fulltextCount = parseInt(fulltextCount);
    this.fuzzyCount = parseInt(fuzzyCount);
}

function doPrevious()
{
    window.location.href = "<%=urlOK%>";
}

function doRefresh()
{
    window.location.href = "<%=urlRefresh%>";
}

function doSave()
{
    if (isIndexing)
    {
      alert("<%=bundle.getString("jsmsg_tb_being_reindexed") %>");
      return;
    }

    var result = buildDefinition();

    // alert(oDefinition.XMLDocument.xml); //return;

    try
    {
       if(window.navigator.userAgent.indexOf("MSIE")>0)
       {
	        sendTermbaseManagementRequest(
	          "<%=WebAppConstants.TERMBASE_ACTION_MODIFY%>", tbid,
	          oDefinition.XMLDocument.xml);
       }
       else
       {
             sendTermbaseManagementRequest(
	          "<%=WebAppConstants.TERMBASE_ACTION_MODIFY%>", tbid,
	          XML.getDomString(result.dom));
       }

      indexform.action = "<%=urlRefresh%>";
      indexform.submit();
    }
    catch (error)
    {
      error.message =
        "<%=EditUtil.toJavascript(bundle.getString("lb_tb_modification_failed"))%>";
      showError(error);
    }
}

function doReindex()
{
    if (isIndexing)
    {
      alert("<%=bundle.getString("jsmsg_tb_already_reindexed") %>");
      return;
    }

    indexform.action = "<%=urlReindex%>" +
      "&<%=WebAppConstants.TERMBASE_ACTION%>" +
      "=<%=WebAppConstants.TERMBASE_ACTION_INDEX%>";
    indexform.submit();
}

function doSchedule()
{
  frmSchedule.submit();
}

function Result(message, errorFlag, element, dom)
{
    this.message = message;
    this.error   = errorFlag;
    this.element = element;
    this.dom = dom;
}

function buildDefinition()
{    
    var xmlStr = "<%=xmlDefinition%>";
    var dom;

    if(window.navigator.userAgent.indexOf("MSIE")>0)
    {
      dom = oDefinition.XMLDocument;
    }
    else if(window.DOMParser)
    { 
      var parser = new DOMParser();
      dom = parser.parseFromString(xmlStr,"text/xml");
    }
    
    
    var result = new Result("", 0, null, null);

    var node;

    node = dom.selectSingleNode("/definition/indexes");
    while (node.hasChildNodes())
    {
      node.removeChild(node.firstChild);
    }
    
    var form = document.indexform;    
    /*for (var i = 0; i < form.elements.length; i++)
    {
	  if (form.elements[i].type == "checkbox" &&
        form.elements[i].name == "checkbox" &&
	    !form.elements[i].disabled &&
	    form.elements[i].checked == true)
      {
         var box = form.elements[i];
		 var key = box.value;
		 var arr = key.split(",");
		 var type = arr[0];
		 var name = arr[1];
         
        var elem = dom.createElement("index");
        var langname = dom.createElement("languagename");
        var locale = dom.createElement("locale");
        var langtype = dom.createElement("type");

        if(window.navigator.userAgent.indexOf("MSIE")>0)
        {
        langname.text = name;
        locale.text = getLocale(name);
        langtype.text = type;
        }
        else
        {
        langname.textContent = name;
        locale.textContent = getLocale(name);
        langtype.textContent = type;        
        }

        elem.appendChild(langname);
        elem.appendChild(locale);
        elem.appendChild(langtype);

        node.appendChild(elem);
         
      }
    }*/

    var eles = document.getElementsByTagName("*");
    for (var i = 0; i < eles.length; i++)
    {
	  if (eles[i].type == "checkbox" &&
        eles[i].name == "checkbox" &&
	    !eles[i].disabled &&
	    eles[i].checked == true)
      {
         var box = eles[i];
		 var key = box.value;
		 var arr = key.split(",");
		 var type = arr[0];
		 var name = arr[1];
         
        var elem = dom.createElement("index");
        var langname = dom.createElement("languagename");
        var locale = dom.createElement("locale");
        var langtype = dom.createElement("type");

        if(window.navigator.userAgent.indexOf("MSIE")>0)
        {
        langname.text = name;
        locale.text = getLocale(name);
        langtype.text = type;
        }
        else
        {
        langname.textContent = name;
        locale.textContent = getLocale(name);
        langtype.textContent = type;        
        }

        elem.appendChild(langname);
        elem.appendChild(locale);
        elem.appendChild(langtype);

        node.appendChild(elem);
         
      }
    }
    
    result.dom = dom;
    
    return result;
}

function clickIndex()
{
  idReindex.disabled = true;
  idSchedule.disabled = true;
  //idSave.focus();
}

var rowNum = 0;

function createRow(name, displayName, type, indexed, entryCount, indexCount)
{
  var tbody = idTableBody;
  var row, cell;

  row = tbody.insertRow(rowNum);
  rowNum++;
  cell = row.insertCell(0);
  cell.innerHTML = displayName;
  cell = row.insertCell(1);
  cell.innerHTML = (type == "fulltext" ? "<%=bundle.getString("lb_fulltext")%>" : "<%=bundle.getString("lb_fuzzy_report")%>");
  cell = row.insertCell(2);
  cell.align = "center";
  cell.innerHTML = "<INPUT TYPE=checkbox NAME=checkbox VALUE='" +
    type + "," + name + "' " + (indexed ? "CHECKED" : "") +
    " onclick='clickIndex()'>";
  cell = row.insertCell(3);
  cell.align = "right";
  cell.innerHTML = (entryCount < 0 ? "\u00a0" : entryCount);
  cell = row.insertCell(4);
  cell.align = "right";
  cell.innerHTML = (indexCount < 0 ? "\u00a0" : indexCount);
}

function getLocale(name)
{
  var o = aLanguages[name];
  if (o)
  {
    return o.locale;
  }

  // This is for the concept-level fulltext index with the empty name ("")
  return "en";
}

function getIsIndexed(name, type)
{
  var o = aIndexes[type + "," + name];
  if (o)
  {
    return o.type == type;
  }

  return false;
}

function getEntryCount(name)
{
  var o = aIndexStats[name];
  if (o)
  {
    return o.entryCount;
  }

  return -1;
}

function getFulltextCount(name)
{
  var o = aIndexStats[name];
  if (o)
  {
    return o.fulltextCount;
  }

  return -1;
}

function getFuzzyCount(name)
{
  var o = aIndexStats[name];
  if (o)
  {
    return o.fuzzyCount;
  }

  return -1;
}

function parseDefinition()
{  
  var defStr = "<%=xmlDefinition%>";
  var statStr = "<%String cleanStr = xmlStatistics.replaceAll("\\n","");out.print(cleanStr);%>";
  
  var def;
  var stat;

  if(window.navigator.userAgent.indexOf("MSIE")>0)
  {
      def = oDefinition.XMLDocument;
      stat = oStatistics.XMLDocument;
  }
  else if(window.DOMParser)
  { 
      var parser = new DOMParser();
      def = parser.parseFromString(defStr,"text/xml");
      parser = new DOMParser();
      stat = parser.parseFromString(statStr,"text/xml");   
  }
  
   
  var nodes, node;

  if(window.navigator.userAgent.indexOf("MSIE")>0)
  {
	  var termbaseName = def.selectSingleNode("/definition/name").text;
	  var indexingStatus = stat.selectSingleNode("/statistics/indexstatus").text;
	  var concepts = stat.selectSingleNode("/statistics/concepts").text;
	  var ftconcepts = stat.selectSingleNode("/statistics/fulltextcount").text;
  }
  else
  {
	  var termbaseName = def.selectSingleNode("/definition/name").textContent;
	  var indexingStatus = stat.selectSingleNode("/statistics/indexstatus").textContent;
	  var concepts = stat.selectSingleNode("/statistics/concepts").textContent;
	  var ftconcepts = stat.selectSingleNode("/statistics/fulltextcount").textContent;  
  }

  if (indexingStatus == "ok")
  {
    isIndexing = false;
    idSave.disabled = false;
    idReindex.disabled = false;
    idSchedule.disabled = false;
  }
  else
  {
    isIndexing = true;
  }

  idTermbaseName.innerHTML = termbaseName;
  idIndexingStatus.innerHTML = isIndexing ? "<%=bundle.getString("lb_building")%>" : "<%=bundle.getString("lb_built")%>";
  
  // Read languages from the database definition
  nodes = def.selectNodes("/definition/languages/language");
  for (var i = 0; i < nodes.length; i++)
  {
    if(window.navigator.userAgent.indexOf("MSIE")>0)
    {      
        node = nodes.item(i);
	    var name = node.selectSingleNode("name").text;
	    var locale = node.selectSingleNode("locale").text;
	    var hasterms = node.selectSingleNode("hasterms").text;
    }
    else
    {     
        node = nodes[i];
	    var name = node.selectSingleNode("name").textContent;
	    var locale = node.selectSingleNode("locale").textContent;
	    var hasterms = node.selectSingleNode("hasterms").textContent;    
    }
    
    hasterms = (hasterms == "true" ? true : false);

    aLanguages[name] = new Language(name, locale, hasterms);
  }

  aLanguages.sort(compareLanguages);

  // Read indexes defined on these languages
  nodes = def.selectNodes("/definition/indexes/index");
  for (var i = 0; i < nodes.length; i++)
  {

    if(window.navigator.userAgent.indexOf("MSIE")>0)
    {
        node = nodes.item(i);
	    var name = node.selectSingleNode("languagename").text;
	    var locale = node.selectSingleNode("locale").text;
	    var type = node.selectSingleNode("type").text;
    }
    else
    {
        node = nodes[i];
	    var name = node.selectSingleNode("languagename").textContent;
	    var locale = node.selectSingleNode("locale").textContent;
	    var type = node.selectSingleNode("type").textContent;    
    }

    aIndexes[type + "," + name] = new Index(name, locale, type);
  }

  // Collect index statistics (concept-level)
  aIndexStats[""] = new IndexStat("", concepts, ftconcepts, "-1");

  // Collect index statistics (term-level)
  nodes = stat.selectNodes("/statistics/indexes/index");
  for (var i = 0; i < nodes.length; i++)
  {
    
    if(window.navigator.userAgent.indexOf("MSIE")>0)
    {
        node = nodes.item(i);
	    var name = node.selectSingleNode("language").text;
	    var termCount = node.selectSingleNode("terms").text;
	    var fulltextCount = node.selectSingleNode("fulltextcount").text;
	    var fuzzyCount = node.selectSingleNode("fuzzycount").text;
    }
    else
    {
        node = nodes[i];
	    var name = node.selectSingleNode("language").textContent;
	    var termCount = node.selectSingleNode("terms").textContent;
	    var fulltextCount = node.selectSingleNode("fulltextcount").textContent;
	    var fuzzyCount = node.selectSingleNode("fuzzycount").textContent;    
    }

    aIndexStats[name.toLowerCase()] = new IndexStat(name.toLowerCase(), termCount, fulltextCount, fuzzyCount);
  }

  // Display first row for concept-level fulltext index
  createRow("", "<%=bundle.getString("lb_concept_level")%>", "fulltext", getIsIndexed("", "fulltext"),
    getEntryCount(""), getFulltextCount(""));

  // Display one language and one index per row
  for (key in aLanguages)
  {
    var lang = aLanguages[key];

    createRow(lang.name, lang.name, "fuzzy", getIsIndexed(lang.name, "fuzzy"),
      getEntryCount(lang.name.toLowerCase()), getFuzzyCount(lang.name.toLowerCase()));
    createRow(lang.name, "\u00a0", "fulltext", getIsIndexed(lang.name, "fulltext"),
      /*-1*/getEntryCount(lang.name.toLowerCase()), getFulltextCount(lang.name.toLowerCase()));
  }
}

function doOnLoad()
{
   // This loads the guides in guides.js and the 
   loadGuides();
   rowNum = 0; //reset table row number
   parseDefinition();
}
</SCRIPT>
</HEAD>
<BODY onload="doOnLoad();" LEFTMARGIN="0" RIGHTMARGIN="0" 
      TOPMARGIN="0" MARGINWIDTH="0" MARGINHEIGHT="0">
<%@ include file="/envoy/common/header.jspIncl" %>
<%@ include file="/envoy/common/navigation.jspIncl" %>
<%@ include file="/envoy/wizards/guides.jspIncl" %>

<DIV ID="contentLayer"
 STYLE="POSITION: absolute; Z-INDEX: 0; TOP: 108px; LEFT: 20px; RIGHT: 20px;">
<P CLASS="mainHeading" id="idHeading"><%=title %></P>

<XML id="oDefinition" style="display:none;"><%=xmlDefinition%></XML>
<XML id="oStatistics" style="display:none;"><%=xmlStatistics%></XML>

<TABLE CELLPADDING=0 CELLSPACING=0 BORDER=0 CLASS=standardText>
  <TR>
    <TD WIDTH=538>
      <%=helper %>
    </TD>
  </TR>
</TABLE>
<BR>

<DIV CLASS="standardText"><%=bundle.getString("lb_termbase_index_status") %>
<SPAN id="idTermbaseName"></SPAN>:
<SPAN id="idIndexingStatus" style="font-weight: bold"></SPAN>
</DIV>
<BR>

<DIV ID="idStatistics">
<TABLE id="idTable" CELLPADDING=2 CELLSPACING=0 BORDER=1
  CLASS="standardText" style="border-collapse: collapse">
  <THEAD>
    <TR style="background-color: #a6b8ce">
      <TD align="left"   width="40%"><%=bundle.getString("lb_termbase_index") %></TD>
      <TD align="left"   width="20%"><%=bundle.getString("lb_type") %></TD>
      <TD align="center" width="10%"><%=bundle.getString("lb_termbase_indexed") %></TD>
      <TD align="right"  width="15%"><%=bundle.getString("lb_size") %></TD>
      <TD align="right"  width="15%"><%=bundle.getString("lb_termbase_index_size") %></TD>
    </TR>
  </THEAD>
  <FORM name="indexform" method="post">
  <TBODY id="idTableBody"></TBODY>
  </FORM>
  <tfoot>
    <TR>
      <TD align="left"   width="40%"></TD>
      <TD align="left"  colspan="3"><div align="center">
                <A CLASS="standardHREF" 
                   HREF="javascript:checkAllWithName('indexform', 'checkbox'); clickIndex();"><%=bundle.getString("lb_check_all") %></A> | 
                <A CLASS="standardHREF" 
                   HREF="javascript:clearAll('indexform'); clickIndex();"><%=bundle.getString("lb_clear_all") %></A></div>
       </TD>
       <TD align="right"  width="15%"></TD>
    </TR>
  </tfoot>
</TABLE>
</DIV>
<BR>

<DIV>
<INPUT TYPE="BUTTON" VALUE="<%=bundle.getString("lb_previous")%>"
  ID="idPrevious" onclick="doPrevious()">
<INPUT TYPE="BUTTON" VALUE="<%=bundle.getString("lb_refresh")%>"
  ID="idRefresh" onclick="doRefresh()">
&nbsp;
<INPUT TYPE="BUTTON" VALUE="<%=bundle.getString("lb_save")%>"
  ID="idSave" DISABLED onclick="doSave()">
&nbsp;
<INPUT TYPE="BUTTON" VALUE="<%=bundle.getString("lb_termbase_reindex") %>"
  ID="idReindex" DISABLED onclick="doReindex()">
&nbsp;&nbsp;&nbsp;
<INPUT TYPE="BUTTON" VALUE="<%=bundle.getString("lb_termbase_reindex_schedule") %>..."
  ID="idSchedule" onclick="doSchedule()">
</DIV>
<BR>

</DIV>

<FORM name="frmSchedule" method="post" action="<%=urlSchedule%>">
<INPUT type="hidden" name="<%=WebAppConstants.CRON_ACTION%>"
 value="<%=WebAppConstants.CRON_ACTION_LIST%>">
<INPUT type="hidden" name="<%=WebAppConstants.CRON_EVENT%>"
 value="<%=WebAppConstants.CRON_EVENT_REINDEX_TERMBASE%>">
<INPUT type="hidden" name="<%=WebAppConstants.CRON_OBJECT_NAME%>"
 value="<%=str_tbname%>">
<INPUT type="hidden" name="<%=WebAppConstants.CRON_OBJECT_ID%>"
 value="<%=str_tbid%>">
<INPUT type="hidden" name="<%=WebAppConstants.CRON_BACKPOINTER%>"
 value="<%=urlRefresh%>">
</FORM>

</BODY>
</HTML>