<%@ page contentType="application/vnd.ms-excel"
         errorPage="/envoy/common/error.jsp"
         import="com.globalsight.everest.webapp.pagehandler.administration.reports.ReviewerLisaQAXlsReportHelper"
         session="true"
%><%
response.setHeader("Content-Disposition","attachment; filename=ImplementedCommentsCheck.xlsx" );
response.setHeader("Expires", "0");
response.setHeader("Cache-Control","must-revalidate, post-check=0,pre-check=0");
response.setHeader("Pragma","public");
response.setContentType("application/x-excel");
ReviewerLisaQAXlsReportHelper reviewerLisaQAXlsReportHelper = 
		new ReviewerLisaQAXlsReportHelper(request, response, ReviewerLisaQAXlsReportHelper.IMPLEMENTED_COMMENTS_CHECK_REPORT);
reviewerLisaQAXlsReportHelper.generateReport();
%>