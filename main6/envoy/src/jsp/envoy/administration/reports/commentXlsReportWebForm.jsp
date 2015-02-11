<%@ page contentType="text/html; charset=UTF-8"
         errorPage="/envoy/common/activityError.jsp"
         import="java.util.*, com.globalsight.everest.servlet.util.SessionManager,
                  com.globalsight.everest.company.CompanyThreadLocal,
                  com.globalsight.everest.webapp.WebAppConstants,
                  com.globalsight.everest.webapp.javabean.NavigationBean,
                  com.globalsight.everest.webapp.pagehandler.PageHandler,
                  com.globalsight.everest.webapp.pagehandler.administration.users.UserHandlerHelper,
                  com.globalsight.everest.webapp.pagehandler.projects.workflows.JobSearchConstants,
                  com.globalsight.util.resourcebundle.ResourceBundleConstants,
                  com.globalsight.util.resourcebundle.SystemResourceBundle,
                  com.globalsight.everest.foundation.SearchCriteriaParameters,
                  com.globalsight.everest.webapp.pagehandler.administration.vendors.ProjectComparator,
                  com.globalsight.everest.foundation.User,
                  com.globalsight.everest.projecthandler.Project,
                  com.globalsight.everest.util.comparator.JobComparator,
                  com.globalsight.everest.jobhandler.Job,
                  com.globalsight.everest.jobhandler.JobSearchParameters,
                  com.globalsight.everest.projecthandler.ProjectInfo,
                  com.globalsight.everest.webapp.webnavigation.LinkHelper,
                  com.globalsight.everest.servlet.util.ServerProxy,
                  com.globalsight.everest.util.comparator.LocaleComparator,
                  com.globalsight.everest.servlet.EnvoyServletException,
                  com.globalsight.everest.util.system.SystemConfigParamNames,
                  com.globalsight.everest.util.system.SystemConfiguration,
                  com.globalsight.util.GeneralException,
                  com.globalsight.util.GlobalSightLocale,
                  com.globalsight.everest.comment.Issue,
                  com.globalsight.everest.comment.IssueHistory,
                  com.globalsight.everest.comment.IssueOptions,
                  java.text.MessageFormat,
                  java.util.Locale,
                  java.util.ResourceBundle,
                  java.util.List"
          session="true"
%>
<%
    ResourceBundle bundle = PageHandler.getBundle(session);
    SessionManager sessionMgr = (SessionManager)session.getAttribute(WebAppConstants.SESSION_MANAGER);
    Locale uiLocale = (Locale)session.getAttribute(WebAppConstants.UILOCALE);
    if (uiLocale == null) uiLocale = Locale.ENGLISH;
    String userName = (String)session.getAttribute(WebAppConstants.USER_NAME);
    //Set current company id.
    String companyId = CompanyThreadLocal.getInstance().getValue();
    session.setAttribute("current_company_id", companyId);
    
    // Field names
    String nameField = JobSearchConstants.NAME_FIELD;
    String nameOptions = JobSearchConstants.NAME_OPTIONS;
    String idField = JobSearchConstants.ID_FIELD;
    String idOptions = JobSearchConstants.ID_OPTIONS;
    String statusOptions = JobSearchConstants.STATUS_OPTIONS;
    String projectOptions = JobSearchConstants.PROJECT_OPTIONS;
    String srcLocale = JobSearchConstants.SRC_LOCALE;
    String targLocale = JobSearchConstants.TARG_LOCALE;
    String priorityOptions = JobSearchConstants.PRIORITY_OPTIONS;
    String creationStart = JobSearchConstants.CREATION_START;
    String creationStartOptions = JobSearchConstants.CREATION_START_OPTIONS;
    String creationEnd = JobSearchConstants.CREATION_END;
    String creationEndOptions = JobSearchConstants.CREATION_END_OPTIONS;
    String completionStart = JobSearchConstants.EST_COMPLETION_START;
    String completionStartOptions = JobSearchConstants.EST_COMPLETION_START_OPTIONS;
    String completionEnd = JobSearchConstants.EST_COMPLETION_END;
    String completionEndOptions = JobSearchConstants.EST_COMPLETION_END_OPTIONS;
%>
<html>
<!-- This JSP is: /envoy/administration/reports/commentXlsReportWebForm.jsp-->
<head>
<title><%=bundle.getString("comments_report_web_form")%></title>
</head>
<body leftmargin="0" rightrmargin="0" topmargin="0" marginwidth="0" marginheight="0"
bgcolor="LIGHTGREY">
<SCRIPT LANGUAGE="JAVASCRIPT">
// If user selected "now", then blank out the preceeding numeric field.
function checkNow(field, text)
{
    if (field.options[1].selected)
        text.value = "";
}

function isInteger(value)
{
    if (value == "") return true;
    return (parseInt(value) == value);
}

function validateForm()
{
    if ((-1 != searchForm.<%=creationStartOptions%>.value) &&
        (searchForm.<%=creationStart%>.value == ""))
        return ('<%=bundle.getString("jsmsg_job_search_bad_date")%>');
    if ((-1 != searchForm.<%=creationEndOptions%>.value) &&
    	("<%=SearchCriteriaParameters.NOW%>" != searchForm.<%=creationEndOptions%>.value) &&
        (searchForm.<%=creationEnd%>.value == ""))
        return ('<%=bundle.getString("jsmsg_job_search_bad_date")%>');
    if (!isInteger(searchForm.<%=creationStart%>.value))
        return ('<%=bundle.getString("jsmsg_job_search_bad_date")%>');
    if (!isInteger(searchForm.<%=creationEnd%>.value))
        return ('<%=bundle.getString("jsmsg_job_search_bad_date")%>');
    return "";
}

function submitForm()
{
   var msg = validateForm();
   if (msg != "")
   {
    alert(msg);
    return;
   }
   else
    searchForm.submit();
}

</script>
<TABLE WIDTH="100%" BGCOLOR="WHITE">
<TR><TD ALIGN="CENTER"><IMG SRC="/globalsight/images/logo_header.gif"></TD></TR>
</TABLE><BR>
<span class="mainHeading"><B><%=bundle.getString("segment_comments_retrieval_form")%></B></span>
<BR><BR>
<TABLE WIDTH="80%">
<TR><TD>
<SPAN CLASS="smallText">
<%=bundle.getString("optionally_select_values")%><em><%=bundle.getString("lb_shutdownSubmit")%></em> <%=bundle.getString("to_generate_the_report")%>
<%=bundle.getString("the")%> <em>&lt;<%=bundle.getString("all")%>&gt;</EM> <%=bundle.getString("selection_chooses")%> <%=bundle.getString("hold_the")%> <EM><%=bundle.getString("shift")%></EM> <%=bundle.getString("key_to_multi_select_items")%>
</SPAN>
</TD></TR></TABLE>

<form name="searchForm" method="post" action="/globalsight/envoy/administration/reports/commentXlsReport.jsp">

<table border="0" cellspacing="2" cellpadding="2" class="standardText">
<tr>
<td class="standardText"><%=bundle.getString("job_name")%>:</td>
<td class="standardText" VALIGN="BOTTOM">
<select name="jobId" MULTIPLE size="4">
<option value="*" SELECTED><B>&lt;<%=bundle.getString("all")%>&gt;</B></OPTION>
<%
         Vector stateList = new Vector();
         stateList.add(Job.DISPATCHED);
         stateList.add(Job.LOCALIZED);
         stateList.add(Job.EXPORTED);
         Collection jobs = ServerProxy.getJobHandler().getJobsByStateList(stateList);
         ArrayList jobList = new ArrayList(jobs);
         Collections.sort(jobList, new JobComparator(JobComparator.NAME,uiLocale));
         Iterator iter = jobList.iterator();
         ArrayList projects = new ArrayList();
         while (iter.hasNext())
         {
             Job j = (Job) iter.next();
             Project p = j.getL10nProfile().getProject();
             if (projects.contains(p)==false)
                 projects.add(p);
%>
<option VALUE="<%=j.getJobId()%>"><%=j.getJobName()%></OPTION>
<%
         }
%>
</select>
</td>
</tr>

<tr>
<td class="standardText"><%=bundle.getString("lb_project")%>:</td>
<td class="standardText" VALIGN="BOTTOM">
<select name="projectId" MULTIPLE size="4">
<option VALUE="*" SELECTED>&lt;<%=bundle.getString("all")%>&gt;</OPTION>
<%
         Collections.sort(projects, new ProjectComparator(Locale.US));
         iter = projects.iterator();
         while (iter.hasNext())
         {
             Project p = (Project) iter.next();
%>
<option VALUE="<%=p.getId()%>"><%=p.getName()%></OPTION>
<%
         }
%>
</select>
</td>
</tr>

<tr>
<td class="standardText">
<%=bundle.getString("lb_status")%><span class="asterisk">*</span>:
</td>
<td class="standardText" VALIGN="BOTTOM">
<select name="status" MULTIPLE size="4">
<option value="*" SELECTED>&lt;<%=bundle.getString("all")%>&gt;</OPTION>
<option value='<%=Job.DISPATCHED%>'><%= bundle.getString("lb_inprogress") %></option>
<option value='<%=Job.LOCALIZED%>'><%= bundle.getString("lb_localized") %></option>
<option value='<%=Job.EXPORTED%>'><%= bundle.getString("lb_exported") %></option>
</select>
</td>
</tr>

<tr>
<td class="standardText"><%= bundle.getString("lb_target_language") %><span class="asterisk">*</span>:</td>
<td class="standardText" VALIGN="BOTTOM">
<select name="targetLang" multiple="true" size="4">
<OPTION value="*" selected>&lt;<%=bundle.getString("all")%>&gt;</OPTION>
<%
         Vector targetLocales = ServerProxy.getLocaleManager().getAllTargetLocales();                 
         int sortColumn = 1;
         LocaleComparator localeComparator = new LocaleComparator(sortColumn, uiLocale);
         Collections.sort(targetLocales, localeComparator);
         iter = targetLocales.iterator();
         while (iter.hasNext())
         {
             GlobalSightLocale gsl = (GlobalSightLocale) iter.next();
             %><option VALUE="<%=gsl.toString()%>"><%=gsl.getDisplayName(uiLocale)%></OPTION><%
		 }

%>
</select>
</td>
</tr>

<tr>
<td class="standardText">
<%=bundle.getString("comments_options")%>:
</td>
<td class="standardText" VALIGN="BOTTOM">
<table border="0.5" cellspacing="2" cellpadding="2" class="standardText" >
<tr>
<td>
<input type="checkbox" name="commentType_Job" value="<%=bundle.getString("lb_job")%>"> <%=bundle.getString("include_job_comments")%><br>
<input type="checkbox" name="commentType_Activity" value="<%=bundle.getString("lb_activity")%>"> <%=bundle.getString("include_activity_comments")%><br>
<input type="checkbox" name="commentPriority_On" value="on"> <%=bundle.getString("include_segment_priority")%><br>
<input type="checkbox" name="commentCategory_On" value="on"> <%=bundle.getString("include_segment_category")%><br>
<%
          List statusList = IssueOptions.getAllStatus();
          for (int i = 0 ; i < statusList.size() ; i++)
          {
              String status = (String)statusList.get(i);
%>
            <input type="checkbox" name="commenStatus_<%=status%>" value="<%=status%>">
                    <%=bundle.getString("lb_include_segment_status")%> <%=bundle.getString("issue.status." + status)%><br>
<%
          }
%>
</td>
</tr>
</table>
</td>
</tr>

<tr>
<td class="standardText" colspan=2>
<%=bundle.getString("lb_creation_date_range")%>:
</td>
</tr>
<tr>
<td class="standardText" style="padding-left:70px" colspan=2 VALIGN="BOTTOM">
<%=bundle.getString("lb_starts")%>:
<input type="text" name="<%=creationStart%>" size="3" maxlength="9">
<select name="<%=creationStartOptions%>">
<option value='-1'></option>
<option value='<%=SearchCriteriaParameters.HOURS_AGO%>'><%=bundle.getString("lb_hours_ago")%></option>
<option value='<%=SearchCriteriaParameters.DAYS_AGO%>'><%=bundle.getString("lb_days_ago")%></option>
<option value='<%=SearchCriteriaParameters.WEEKS_AGO%>'><%=bundle.getString("lb_weeks_ago")%></option>
<option value='<%=SearchCriteriaParameters.MONTHS_AGO%>'><%=bundle.getString("lb_months_ago")%></option>
</select>
<%=bundle.getString("lb_ends")%>:
<input type="text" name="<%=creationEnd%>" size="3" maxlength="9">
<select name="<%=creationEndOptions%>" onChange="checkNow(this, searchForm.<%=creationEnd%>)">
<option value='-1'></option>
<option value='<%=SearchCriteriaParameters.NOW%>'><%=bundle.getString("lb_now")%></option>
<option value='<%=SearchCriteriaParameters.HOURS_AGO%>'><%=bundle.getString("lb_hours_ago")%></option>
<option value='<%=SearchCriteriaParameters.DAYS_AGO%>'><%=bundle.getString("lb_days_ago")%></option>
<option value='<%=SearchCriteriaParameters.WEEKS_AGO%>'><%=bundle.getString("lb_weeks_ago")%></option>
<option value='<%=SearchCriteriaParameters.MONTHS_AGO%>'><%=bundle.getString("lb_months_ago")%></option>
</select>
</td>
</tr>

<tr>
<td class="standardText"><%=bundle.getString("date_display_format")%>:</td>
<td class="standardText" VALIGN="BOTTOM">
<select name="dateFormat">
<%
 String dateFormats[] = new String[4];
 int i=0;
 dateFormats[i++] = "MM/dd/yy hh:mm:ss a z";
 dateFormats[i++] = "MM/dd/yy HH:mm:ss z";
 dateFormats[i++] = "yyyy/MM/dd HH:mm:ss z";
 dateFormats[i++] = "yyyy/MM/dd hh:mm:ss a z";
 for (i=0;i<dateFormats.length;i++) {
 %>
 <OPTION VALUE="<%=dateFormats[i]%>"><%=dateFormats[i]%></OPTION>
<%}%>
</select>
</td>
</tr>
<tr>
<td><input type="BUTTON" VALUE="<%=bundle.getString("lb_shutdownSubmit")%>" onClick="submitForm()"></td>
<TD><INPUT type="BUTTON" VALUE="<%=bundle.getString("lb_cancel")%>" onClick="window.close()"></TD>
</tr>
</table>
</form>
<BODY>
</HTML>
