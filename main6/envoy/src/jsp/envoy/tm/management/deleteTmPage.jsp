<%@page import="com.globalsight.everest.projecthandler.ProjectTM"%>
<%@page import="com.globalsight.util.GlobalSightLocale"%>
<%@ page
    contentType="text/html; charset=UTF-8"
    errorPage="/envoy/common/error.jsp"
    import="java.util.*,
        com.globalsight.everest.webapp.webnavigation.LinkHelper,
        java.util.ResourceBundle,
        com.globalsight.everest.webapp.pagehandler.PageHandler,
        com.globalsight.everest.webapp.WebAppConstants,
        com.globalsight.everest.servlet.util.SessionManager"
    session="true"
%>
<jsp:useBean id="delete" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
 <jsp:useBean id="cancel" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean"/>
<%
ResourceBundle bundle = PageHandler.getBundle(session);
SessionManager sessionMgr = (SessionManager)session.getAttribute(
        WebAppConstants.SESSION_MANAGER);
String cancelUrl = cancel.getPageURL();
String tmIdStr = (String) sessionMgr.getAttribute(WebAppConstants.TM_TM_ID);
HashSet<GlobalSightLocale> tmLocales = (HashSet<GlobalSightLocale>) sessionMgr.getAttribute("tmLocales");
ArrayList<ProjectTM> tms = (ArrayList<ProjectTM>) sessionMgr.getAttribute("projectTms");
%>
<HEAD>
<TITLE><%=bundle.getString("lb_tm_removing")%></TITLE>

<SCRIPT SRC="/globalsight/includes/setStyleSheet.js"></SCRIPT>
<%@ include file="/envoy/wizards/guidesJavascript.jspIncl" %>
<%@ include file="/envoy/common/warning.jspIncl" %>
<%@ include file="/includes/compatibility.jspIncl" %>
<SCRIPT SRC="/globalsight/includes/library.js"></SCRIPT>
<SCRIPT SRC="/globalsight/includes/xmlHttpInit.js"></SCRIPT>
<SCRIPT SRC="/globalsight/includes/progressBarUpdate.js"></SCRIPT>
<SCRIPT LANGUAGE="JavaScript">
var isIE = window.navigator.userAgent.indexOf("MSIE")>0;
var needWarning = true;
var objectName = "<%=bundle.getString("lb_user")%>";
var guideNode = "tm";
    
function doOnLoad() {
    loadGuides();
}

function submitForm(selectedButton) {
    if (selectedButton == 'Cancel') {
        window.location.href = "<%=cancelUrl%>";
        return;
    }
    else if (selectedButton == 'OK') {
        if(TMForm.checkboxTm[0].checked) {
            TMForm.action =  '<%=delete.getPageURL() +
                             "&" + WebAppConstants.TM_ACTION +
                             "=" + WebAppConstants.TM_ACTION_DELETE%>';
        }
        else {
            TMForm.action =  '<%=delete.getPageURL() +
                             "&" + WebAppConstants.TM_ACTION +
                             "=deleteTMLanguage"%>';
        }

        TMForm.submit();
        return;
    }
}

function clickRadio(flag) {
    if(flag == true) {
        TMForm.LanguageList.disabled = true;
    }
    else if(flag == false) {
        TMForm.LanguageList.disabled = false;
    }
}
</SCRIPT>
</HEAD>
<BODY LEFTMARGIN="0" RIGHTMARGIN="0" TOPMARGIN="0" MARGINWIDTH="0" MARGINHEIGHT="0"
    ONLOAD="doOnLoad()">
<%@ include file="/envoy/common/header.jspIncl" %>
<%@ include file="/envoy/common/navigation.jspIncl" %>
<%@ include file="/envoy/wizards/guides.jspIncl" %>
<DIV ID="contentLayer"
 STYLE=" POSITION: ABSOLUTE; Z-INDEX: 9; TOP: 108px; LEFT: 20px; RIGHT: 20px;">

<SPAN CLASS="mainHeading"><%=bundle.getString("lb_tm_removing")%></SPAN>
<br><br>
<FORM NAME=TMForm method="post">
<INPUT TYPE="hidden" NAME="<%=WebAppConstants.TM_TM_ID%>" VALUE="<%=tmIdStr%>">
    <div class='standardText'nowrap>
       <%=bundle.getString("lb_select_entries_to_remove")%>
    </div>
    <div class='standardText'nowrap>
	    <input type="radio" name="checkboxTm" value="deleteTm" checked onclick="clickRadio(true)"> 
	       <%= bundle.getString("lb_entire_tm")%>
	    <br>
	      <%
		  for (ProjectTM tm : tms) {
		      out.print("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<i>" + tm.getName() + "</i><br>");
		  }
		  %>
    </div>
    <br>
    <div class='standardText'nowrap>
        <input type="radio" name="checkboxTm" value="deleteLanguage" onclick="clickRadio(false)">
           <%= bundle.getString("lb_by_language")%>
        <select name="LanguageList" id="LanguageList" disabled=true>
          <%
          GlobalSightLocale locale = null;
          if (tmLocales != null && tmLocales.size() > 0) {
              for (Iterator iterator = tmLocales.iterator(); iterator.hasNext();) {
                 locale = (GlobalSightLocale) iterator.next();
                 %>
                 <option value="<%=locale.getId() %>"><%=locale.getDisplayName() %></option>
                 <%
              }
          }
          %>
       </select>
    </div>
    <br>

<table  border="0">
    <tr>
        <td>
            <INPUT TYPE="BUTTON" VALUE="<%=bundle.getString("lb_cancel")%>" onClick="submitForm('Cancel');">
            <INPUT TYPE="BUTTON" VALUE="<%=bundle.getString("lb_ok")%>" onClick="submitForm('OK');">
        </td>
    </tr>
</table>
</FORM>
</BODY>
</HTML>