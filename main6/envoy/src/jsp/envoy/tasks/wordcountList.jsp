<%@ taglib uri="/WEB-INF/tlds/globalsight.tld" prefix="amb" %>
<%@ page contentType="text/html; charset=UTF-8"
    errorPage="/envoy/common/error.jsp"
    import="com.globalsight.everest.webapp.javabean.NavigationBean,
      com.globalsight.everest.taskmanager.Task,
      com.globalsight.everest.util.comparator.TaskComparator,
      com.globalsight.everest.util.system.SystemConfigParamNames,
      com.globalsight.everest.webapp.pagehandler.PageHandler, 
      com.globalsight.everest.webapp.pagehandler.tasks.WordCountHandler, 
      com.globalsight.everest.workflowmanager.Workflow,
      com.globalsight.everest.costing.WordcountForCosting,
      com.globalsight.everest.webapp.WebAppConstants,
      com.globalsight.everest.jobhandler.Job,      
      java.util.*"
    session="true"
%>
<jsp:useBean id="self" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="back" scope="request"
 class="com.globalsight.everest.webapp.javabean.NavigationBean" />
<jsp:useBean id="tasks" scope="request"
 class="java.util.ArrayList" />
<% 
    ResourceBundle bundle = PageHandler.getBundle(session);
    SessionManager sessionMgr =
      (SessionManager)session.getAttribute(WebAppConstants.SESSION_MANAGER);
    Locale uiLocale = (Locale)session.getAttribute(WebAppConstants.UILOCALE);
 
    String title = bundle.getString("lb_detailed_word_counts");

    String sortCount = (String)sessionMgr.getAttribute("sortCount");

    String taskListStartParam = WebAppConstants.TASK_LIST_START;
    String taskListStartStr = request.getParameter(taskListStartParam);
    if (taskListStartStr == null) 
    {
        taskListStartStr = "0";
    }
    String action = back.getPageURL() + "&action=wcBack"+ "&" + taskListStartParam + "=" + taskListStartStr;

	//ArrayList theTasks = (ArrayList) request.getAttribute("tasks");
    //int leverageMatchThreshold = ((Task) theTasks.get(0)).getWorkflow().getJob().getLeverageMatchThreshold();
    
    String detailedStatistics = bundle.getString("lb_detailed_statistics");
    String summaryStatistics = bundle.getString("lb_summary_statistics");
    //String leverageMatchThreshold = bundle.getString("lb_leverage_match_threshold") + 
    //  " = "+ sessionMgr.getAttribute(WordCountHandler.LMT) + "%";
    
    boolean noWordCountPermission = true;
    boolean isDell = ((Boolean)request.getAttribute(
      SystemConfigParamNames.IS_DELL)).booleanValue();

    String pageName = self.getPageName();
    if (pageName != null) {
        pageName += "&" + taskListStartParam + "=" + taskListStartStr;
        self.setPageName(pageName);
    }
    
    boolean isUseInContext = ((Boolean)request.getAttribute(WebAppConstants.IS_USE_IN_CONTEXT)).booleanValue();
    boolean leverageExactOnly = ((Boolean)request.getAttribute(WebAppConstants.LEVERAGE_EXACT_ONLY)).booleanValue();
    boolean isInContextMatch = ((Boolean)request.getAttribute(WebAppConstants.IS_IN_CONTEXT_MATCH));
    boolean isDefaultContextMatch = (Boolean)request.getAttribute(WebAppConstants.IS_DEFAULT_CONTEXT_MATCH);
%>

<%!
    private boolean isUseInContext(Task task) {
        boolean isUseInContext = false;
        isUseInContext = task.getWorkflow().getJob().getL10nProfile().getTranslationMemoryProfile().getIsContextMatchLeveraging();
        try
        {
        	isUseInContext = PageHandler.isInContextMatch(task.getWorkflow().getJob(), isUseInContext);
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
        return isUseInContext;
    }
%>

<HTML>
<!-- This JSP is: envoy/tasks/wordcountList.jsp -->
<HEAD>
<META HTTP-EQUIV="content-type" CONTENT="text/html;charset=UTF-8">
<TITLE><%= title %></TITLE>
<SCRIPT SRC="/globalsight/includes/setStyleSheet.js"></SCRIPT>
<SCRIPT SRC="/globalsight/includes/radioButtons.js"></SCRIPT>
<%@ include file="/envoy/wizards/guidesJavascript.jspIncl" %>
<%@ include file="/envoy/common/warning.jspIncl" %>
<SCRIPT LANGUAGE="JavaScript">
var needWarning = false;
var objectName = "";
var guideNode = "myActivities";
var helpFile = "<%=bundle.getString("help_activity_wordcounts")%>";
</SCRIPT>
</HEAD>

<BODY LEFTMARGIN="0" RIGHTMARGIN="0" TOPMARGIN="0" MARGINWIDTH="0"
 MARGINHEIGHT="0" onload="loadGuides()">
<%@ include file="/envoy/common/header.jspIncl" %>
<%@ include file="/envoy/common/navigation.jspIncl" %>
<%@ include file="/envoy/wizards/guides.jspIncl" %>
<DIV ID="contentLayer" STYLE=" POSITION: ABSOLUTE; Z-INDEX: 9; TOP: 108px; LEFT: 20px; RIGHT: 20px;">

<amb:header title="<%=title%>" />
<BR>

<form name="wcForm" method="post" action="<%=action%>">
<% if (userPerms.getPermissionFor(Permission.ACTIVITIES_DETAIL_STATISTICS)){
   noWordCountPermission = false;
%>
<amb:header title="<%=detailedStatistics%>" />
<%=bundle.getString("helper_text_detailed_statistics")%>
<BR><BR>
<table cellpadding=0 cellspacing=0 border=0 class="standardText">
  <tr valign="top">
    <td align="right">
      <amb:tableNav bean="tasks" key="<%=WordCountHandler.TASK_KEY%>" pageUrl="self" />
      
    </td>
  </tr>
  <tr>
    <td>
      <amb:table bean="tasks" id="task"
             key="<%=WordCountHandler.TASK_KEY%>"
             dataClass="com.globalsight.everest.taskmanager.Task"
             pageUrl="self" emptyTableMsg="" taskListStart="<%=taskListStartStr%>">
        <amb:column label="lb_job_id" width="50px"
             sortBy="<%=TaskComparator.JOB_ID%>">
            <%= task.getWorkflow().getJob().getJobId() %>
        </amb:column>
        <amb:column label="lb_job_name" width="200px"
             sortBy="<%=TaskComparator.JOB_NAME%>">
            <%= task.getJobName() %>
        </amb:column>
        <amb:column label="lb_activity" width="150px"
             sortBy="<%=TaskComparator.ACTIVITY%>">
            <%= task.getTaskDisplayName() %>
        </amb:column>
        <amb:column label="lb_leverage_match_option" width="50px">
	      <% 
	      if(isInContextMatch){
			  	out.print("Leverage in context matches");
		      }else{
		    	  if(isDefaultContextMatch){
		    		  out.print("Default");
		    	  }else{
		    		  out.print("100% match only");
		    	  }
		      } 
	      %>
	    </amb:column>
      
        <%if(isInContextMatch){ %>
            <amb:column label="lb_100" width="50px"
                 sortBy="<%=TaskComparator.EXACT%>">
                <%= task.getWorkflow().getSegmentTmWordCount() %>
            </amb:column>
        <%}else if(isDefaultContextMatch){ %>
            <amb:column label="lb_100" width="50px"
                 sortBy="<%=TaskComparator.DEFAULT_CONTEXT_EXACT%>">
                <%= task.getWorkflow().getNoUseExactMatchWordCount() - task.getWorkflow().getContextMatchWordCount()%>
            </amb:column>
        <%} else {%>
            <amb:column label="lb_100" width="50px"
                 sortBy="<%=TaskComparator.NO_USE_EXACT%>">
                <%= task.getWorkflow().getNoUseExactMatchWordCount() %>
            </amb:column>
		<%} %>
        <amb:column label="lb_95" width="50px"
             sortBy="<%=TaskComparator.BAND1%>">
            <%= task.getWorkflow().getHiFuzzyMatchWordCount() %>
        </amb:column>
        <amb:column label="lb_85" width="50px"
             sortBy="<%=TaskComparator.BAND2%>">
            <%= task.getWorkflow().getMedHiFuzzyMatchWordCount() %>
        </amb:column>
        <amb:column label="lb_75" width="50px"
             sortBy="<%=TaskComparator.BAND3%>">
            <%= task.getWorkflow().getMedFuzzyMatchWordCount() %>
        </amb:column>
        <amb:column label="lb_50" width="50px"
             sortBy="<%=TaskComparator.BAND4%>">
            <%= task.getWorkflow().getLowFuzzyMatchWordCount() %>
        </amb:column>
        <amb:column label="lb_no_match" width="50px"
             sortBy="<%=TaskComparator.NO_MATCH%>">
            <%= task.getWorkflow().getNoMatchWordCount() %>
        </amb:column>
        <amb:column label="lb_repetition_word_cnt" width="50px"
             sortBy="<%=TaskComparator.REPETITIONS%>">
            <%= task.getWorkflow().getRepetitionWordCount() %>
        </amb:column>
        <%if(isInContextMatch){ %>
	        <amb:column label="lb_in_context_tm" width="50px"
		      sortBy="<%=TaskComparator.IN_CONTEXT%>">
		      <%= task.getWorkflow().getInContextMatchWordCount() %>
		     </amb:column>
	     <%}else{ %>
			<%if(isDefaultContextMatch){ %>
		        <amb:column label="lb_context_tm" width="50px"
		             sortBy="<%=TaskComparator.CONTEXT%>">
		            <%= task.getWorkflow().getContextMatchWordCount() %>
		        </amb:column>
			<%} %>
	     <%} %>
        <amb:column label="lb_sublevrep" width="50px"
             sortBy="<%=TaskComparator.SUBLEVREP%>">
            <%= task.getWorkflow().getSubLevRepetitionWordCount() %>
        </amb:column>
        
        <amb:column label="lb_sublevmatch" width="50px"
             sortBy="<%=TaskComparator.SUBLEVMATCH%>">
            <%= task.getWorkflow().getSubLevMatchWordCount() %>
        </amb:column>
        
        <amb:column label="lb_total" width="50px"
             sortBy="<%=TaskComparator.WC_TOTAL%>">
            <%= task.getWorkflow().getTotalWordCount() %>
        </amb:column>
      </amb:table>
    </td>
  </tr>
</TABLE>
<BR><BR><BR>
<%}
if (userPerms.getPermissionFor(Permission.ACTIVITIES_SUMMARY_STATISTICS)){
   noWordCountPermission = false;
%>
<amb:header title="<%=summaryStatistics%>" />
<%=bundle.getString("helper_text_summary_statistics")%>
<BR><BR>
<table cellpadding=0 cellspacing=0 border=0 class="standardText">
  <tr valign="top">
    <td align="right">
      <amb:tableNav bean="tasks" key="<%=WordCountHandler.TASK_KEY%>" pageUrl="self" />
      
    </td>
  </tr>
  <tr>
    <td>
      <amb:table bean="tasks" id="task"
             key="<%=WordCountHandler.TASK_KEY%>"
             dataClass="com.globalsight.everest.taskmanager.Task"
             pageUrl="self" emptyTableMsg="">
        <%
        String lmt = null;
        int totalFuzzy = 0;
        Workflow wf = null;
        WordcountForCosting wfc = null;
        
        if (task != null)
        {
           wf = task.getWorkflow();
           lmt = wf.getJob().getLeverageMatchThreshold()+"%";
           wfc = new WordcountForCosting(wf);
           
           if (isDell)
           {
              totalFuzzy = wfc.updatedLowFuzzyMatchCount() + 
                           wfc.updatedMedFuzzyMatchCount() + 
                           wfc.updatedMedHiFuzzyMatchCount() + 
                           wfc.updatedHiFuzzyMatchCount();
           }
        }
        %>
        <amb:column label="lb_job_id" width="50px"
             sortBy="<%=TaskComparator.JOB_ID%>">
            <%= wf == null ? 0 : wf.getJob().getJobId() %>
        </amb:column>
        <amb:column label="lb_job_name" width="150px"
             sortBy="<%=TaskComparator.JOB_NAME%>">
            <%= task.getJobName() %>
        </amb:column>
        <amb:column label="lb_leverage_match_threshold" width="80px"
             sortBy="<%=TaskComparator.LMT%>">
            <%= lmt %>
        </amb:column>
        <amb:column label="lb_activity" width="100px"
             sortBy="<%=TaskComparator.ACTIVITY%>">
            <%= task.getTaskDisplayName() %>
        </amb:column>
        <amb:column label="lb_leverage_match_option" width="50px">
	      <% 
	      if(isInContextMatch){
			  	out.print("Leverage in context matches");
		      }else{
		    	  if(isDefaultContextMatch){
		    		  out.print("Default");
		    	  }else{
		    		  out.print("100% match only");
		    	  }
		      } 
	      %>
	    </amb:column>
      
        
        <%if(isInContextMatch){ %>
	        <amb:column label="lb_100" width="50px"
	             sortBy="<%=TaskComparator.EXACT%>">
	            <%= wf == null ? 0 : wf.getSegmentTmWordCount() %>
	        </amb:column>
        <%}else if(isDefaultContextMatch){ %>
            <amb:column label="lb_100" width="50px"
                 sortBy="<%=TaskComparator.DEFAULT_CONTEXT_EXACT%>">
                <%= wf == null ? 0 :(wf.getNoUseExactMatchWordCount() - wf.getContextMatchWordCount())%>
            </amb:column>
        <%} else {%>
	        <amb:column label="lb_100" width="50px"
	             sortBy="<%=TaskComparator.NO_USE_EXACT%>">
	            <%= wf == null ? 0 : wf.getNoUseExactMatchWordCount() %>
	        </amb:column>
        <%} %>
        <%if (isDell) {%>
        <amb:column label="lb_fuzzy_match" width="80px"
             sortBy="<%=TaskComparator.TOTAL_FUZZY%>">
            <%= totalFuzzy %>
        </amb:column>
        <%}
        else{%>
        <amb:column label="lb_95" width="50px"
             sortBy="<%=TaskComparator.BAND1%>">
            <%= wf == null ? 0 : wfc.updatedHiFuzzyMatchCount() %>
        </amb:column>
        <amb:column label="lb_85" width="50px"
             sortBy="<%=TaskComparator.BAND2%>">
            <%= wf == null ? 0 : wfc.updatedMedHiFuzzyMatchCount() %>             
        </amb:column>
        <amb:column label="lb_75" width="50px"
             sortBy="<%=TaskComparator.BAND3%>">
            <%= wf == null ? 0 : wfc.updatedMedFuzzyMatchCount() %>                          
        </amb:column>
        <%}%>
        <amb:column label="lb_no_match" width="50px"
             sortBy="<%=TaskComparator.NO_MATCH%>">
            <%= wf == null ? 0 : wf.getNoMatchWordCount() + wf.getSubLevMatchWordCount()%>
        </amb:column>
        <amb:column label="lb_repetition_word_cnt" width="50px"
             sortBy="<%=TaskComparator.REPETITIONS%>">
            <%= wf == null ? 0 : wf.getRepetitionWordCount() + wf.getSubLevRepetitionWordCount() %>
        </amb:column>
        <%if (!isDell) {%>
        <%if(isInContextMatch){ %>
        <amb:column label="lb_in_context_tm" width="50px"
	       sortBy="<%=TaskComparator.IN_CONTEXT%>">
	       <%= task.getWorkflow().getInContextMatchWordCount() %>
	      </amb:column>
        <%}else{%>
	        <%if(isDefaultContextMatch){ %>
		        <amb:column label="lb_context_tm" width="50px"
		             sortBy="<%=TaskComparator.CONTEXT%>">
		            <%= wf == null ? 0 : wf.getContextMatchWordCount() %>
		        </amb:column>
			<%} %>
       
        <%} %>
        <%}%>
 
        <amb:column label="lb_total" width="50px"
             sortBy="<%=TaskComparator.WC_TOTAL%>">
            <%= wf == null ? 0 : wf.getTotalWordCount() %>
        </amb:column>
      </amb:table>
    </td>
  </tr>
<%}
if (noWordCountPermission)
{%>
<%=bundle.getString("lb_no_wordcount_statistic_permission")%>
<%}%>
</TABLE>
<BR><BR>
      <input type="submit" value='<%=bundle.getString("lb_back_to_activities")%>'>
</FORM>
</DIV>
</BODY>