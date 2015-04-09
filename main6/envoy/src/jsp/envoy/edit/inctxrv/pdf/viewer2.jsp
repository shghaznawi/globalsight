﻿<%@ page
    contentType="text/html; charset=UTF-8"
    errorPage="error.jsp"
    import="com.globalsight.everest.webapp.javabean.NavigationBean,
            com.globalsight.everest.webapp.pagehandler.PageHandler,
            com.globalsight.everest.webapp.pagehandler.edit.online.EditorState,
            com.globalsight.everest.webapp.pagehandler.edit.online.EditorConstants,
            com.globalsight.everest.servlet.util.SessionManager,
            com.globalsight.everest.webapp.WebAppConstants,
            com.globalsight.everest.webapp.pagehandler.projects.workflows.JobManagementHandler,
            java.util.*"
    session="true"
%>
<%
SessionManager sessionMgr = (SessionManager)session.getAttribute(
  WebAppConstants.SESSION_MANAGER);
EditorState state =
  (EditorState)sessionMgr.getAttribute(WebAppConstants.EDITORSTATE);

String pdfUrl = (String) request.getAttribute("pdfUrl");
String errorMsg = (String) request.getAttribute("errorMsg");

int iViewMode = state.getLayout().getTargetViewMode();
String viewMode = "";
switch (iViewMode)
{
  case EditorConstants.VIEWMODE_PREVIEW: viewMode = "preview"; break;
  case EditorConstants.VIEWMODE_TEXT:    viewMode = "text"; break;
  case EditorConstants.VIEWMODE_DETAIL:  viewMode = "list"; break;
}
%>

<!DOCTYPE html>
<html dir="ltr" mozdisallowselectionprint moznomarginboxes>
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <meta name="google" content="notranslate">
    <title>PDF.js viewer</title>


    <link rel="stylesheet" href="viewer.css"/>
    <SCRIPT type="text/javascript" src="/globalsight/jquery/jquery-1.6.4.min.js"></SCRIPT>
    <script src="compatibility.js"></script>

<link rel="STYLESHEET" type="text/css" href="/globalsight/includes/ContextMenu.css">
<script src="/globalsight/includes/ContextMenu.js"></script>
        <SCRIPT LANGUAGE="JavaScript" SRC="/globalsight/includes/dojo.js">
        </SCRIPT>

<!-- This snippet is used in production (included from viewer.html) -->
<link rel="resource" type="application/l10n" href="locale/locale.properties"/>
<script src="l10n.js"></script>
<script src="pdf.js"></script>
<script src="viewerCommon.js"></script>



    <script src="debugger.js"></script>
    <script src="viewer2.js"></script>
<script>
var inViewerPage = true;
String.prototype.trim=function(){return this.replace(/(^\s*)|(\s*$)/g, "");}

function load()
{
	ContextMenu.intializeContextMenu();
	
	<% if(pdfUrl != null)  { %>
	$("#outerContainer").hide();
	var nurl =  "/globalsight/envoy/edit/inctxrv/pdf/viewer2.jsp?file=<%=pdfUrl %>";
	window.location = nurl;
	
	<% } %>
	
	<% if(errorMsg != null)  { %>
	
	$("#outerContainer").hide();
	$("#messageDiv").show();
	$("#messageDiv").html("<%= errorMsg %>");
		<% } %>
		
	try{
		var pages = PDFViewerApplication.pdfViewer.pages;
		
		for(var i = 0; i < pages.length; i++)
		{
			var ppp = pages[i];
			
			if (ppp.annotationLayer && ppp.annotationLayer != null)
			{
				ppp.annotationLayer.setAttribute('hidden', 'true');
			}
		}
	}catch (eee) {}
}

function contextForPage(e)
{
    if(!e) e = window.event;

    var o;
    if(window.event)
    {
    o = e.srcElement;
    }
    else
    {
    o = e.target;
    while(o.nodeType != o.ELEMENT_NODE)
	o = o.parentNode;
    }
	
	if (o.nodeName == "DIV" && "textLayer" != o.className && o.id != "segDiv" && o.className.indexOf("segDiv") == -1)
	{
	var popupoptions = [
        new ContextItem("Edit segment",
          function(){editSegment(o)}),
        new ContextItem("Add/edit comment",
          function(){editComment(o)})
        ];
		
	ContextMenu.display(popupoptions, e);
	
	//editSegment(o);
	
	if(e instanceof Object)
    {
	    e.preventDefault();
	    e.stopPropagation();
    }
    else
    {
	    e.returnValue  = false;
	    e.cancelBubble = true
    }
	}
}

var currentSegment;

function highlightObjects(o)
{
	var pagennn = PDFViewerApplication.pdfViewer.location.pageNumber;
	var pageLeft = PDFViewerApplication.pdfViewer.location.left;
	var pageTop = PDFViewerApplication.pdfViewer.location.top;
	var curScale = PDFViewerApplication.pdfViewer.currentScale;
	
	
	var loPn = getPageNumberFromParentId(o);
	if (loPn == -1)
	{
		loPn = PDFViewerApplication.pdfViewer._currentPageNumber;
	}
// 1 un highlight
    var pageDiv = document.getElementById("pageContainer" + loPn);
    var pageDivChildrens = pageDiv.childNodes;
    var textLayerChildrens;
	var textLayerDiv;
	var divArr;
    
    if (pageDivChildrens && pageDivChildrens.length > 0)
    {
  	  for(var i = 0; i < pageDivChildrens.length; i++)
  	  {
  		  var divChild = pageDivChildrens[i];
  		  
  		  if (divChild.nodeName == "DIV" && "textLayer" == divChild.className)
  		  {
  			  textLayerDiv = divChild;
  			  break;
  		  }
  	  }
  	  
  	  textLayerChildrens = textLayerDiv.childNodes;
  	  divArr = new Array(textLayerChildrens.length);
  	  // 1 find extract match
  	  for(var i = 0; i < textLayerChildrens.length; i++)
  	  {
  		  var divChild = textLayerChildrens[i];
  		  divArr[i] = divChild;
  		  var divContent = divChild.textContent;
  		  
  			divChild.innerHTML = divContent;
  			
  			var className = divChild.className;
  			if (className.indexOf("highlight") != -1)
  			{
  				className = className.replace("highlight", "");
  	  			divChild.className = className;
  			}
  	  }
  	  
  	divArr.sort(sortDivByOffset);
    }

//2
var pageContent = buildPageContent(loPn, window.parent.parent.parent.localData, true);

while (pageContent.segments.length == 0 && pageContent.content.length == 0)
{
	var loPn1 = loPn - 1;
	
	if (loPn1 > 0)
	{
		pageContent = buildPageContent(loPn1, window.parent.parent.parent.localData, true);
	}
	else
	{
		break;
	}
}

var find = false;
if (divArr && divArr.length > 0)
{
	for(var i = 0; i < divArr.length; i++)
	{
		var divChild = divArr[i];
		
		if (o == divChild)
		{
			var otext = o.textContent;
			if (otext == "")
			{
				if (typeof(parent.parent.source.content.findSegment) != "undefined")
			    {
					parent.parent.source.content.findSegment(1234, "thisisnotbefoundanymore141516", "thisisnotbefoundanymore141516", true);
			    }
				return;
			}
			
			var segment = getSegment(pageContent, o, i, divArr);
			
			if (segment)
			{
				currentSegment = segment;
				
				findSegment(segment.tuId, segment.tgtSegmentNoTag, "", true);
				
				if (typeof(parent.parent.source.content.findSegment) != "undefined")
			    {
					parent.parent.source.content.findSegment(segment.tuId, segment.srcSegmentNoTag, segment.tgtSegmentNoTag, true);
			    }
			}
			else
			{
				if (typeof(parent.parent.source.content.findSegment) != "undefined")
			    {
					parent.parent.source.content.findSegment(1234, "thisisnotbefoundanymore141516", "thisisnotbefoundanymore141516", true);
			    }
			}
		}
	}
}
else
{
	o.className = "highlight";
}

navigateToDiv(pagennn, pageLeft, pageTop, curScale);
}

function editSegment(o, donotHighlight)
{
	var donothhh = false;
	if (donotHighlight)
	{
		donothhh = true;
	}
	
	if (!donothhh)
	{
		highlightObjects(o);
	}

	if (currentSegment)
	{
		var textcontent = currentSegment.tgtSegment;
		var segDivEle = document.getElementById("segDiv");
		segDivEle.removeAttribute('hidden');
		
		var segTextEle = document.getElementById("segText");
		segTextEle.value = textcontent;
	}
}

function saveSegment(saveIt)
{
	var segDivEle = document.getElementById("segDiv");
	segDivEle.setAttribute('hidden', '');
	
	var segTextEle = document.getElementById("segText");
	var newText = segTextEle.value;
	segTextEle.value = "";
	
	if (saveIt && newText != currentSegment.tgtSegment)
	{
		var obj = {
				save : newText,
				saveFromInCtxRv : newText,
			    refresh : "0",
			    tuId : currentSegment.tuId,
			    tuvId  : currentSegment.tuvId,
			    subId  : currentSegment.subId,
			    ptags  : "compact"
			};
		
		sendAjax(obj);
	}
}

function editComment(o)
{
}

</script>

<script type="text/javascript" src="/globalsight/jquery/me_table.js"></script>
  </head>

  <body tabindex="1" class="loadingInProgress" onload="load()"> <!-- oncontextmenu="contextForPage(event)" -->
  <div id="messageDiv" style="display: none"></div>
    <div id="outerContainer">

      <div id="sidebarContainer">
        <div id="toolbarSidebar">
          <div class="splitToolbarButton toggled">
            <button id="viewThumbnail" class="toolbarButton group toggled" title="Show Thumbnails" tabindex="2" data-l10n-id="thumbs">
               <span data-l10n-id="thumbs_label">Thumbnails</span>
            </button>
            <button id="viewOutline" class="toolbarButton group" title="Show Document Outline" tabindex="3" data-l10n-id="outline">
               <span data-l10n-id="outline_label">Document Outline</span>
            </button>
            <button id="viewAttachments" class="toolbarButton group" title="Show Attachments" tabindex="4" data-l10n-id="attachments">
               <span data-l10n-id="attachments_label">Attachments</span>
            </button>
          </div>
        </div>
        <div id="sidebarContent">
          <div id="thumbnailView">
          </div>
          <div id="outlineView" class="hidden">
          </div>
          <div id="attachmentsView" class="hidden">
          </div>
        </div>
      </div>  <!-- sidebarContainer -->

      <div id="mainContainer">
        <div class="findbar hidden doorHanger hiddenSmallView" id="findbar">
          <label for="findInput" class="toolbarLabel" data-l10n-id="find_label">Find:</label>
          <input id="findInput" class="toolbarField" tabindex="91">
          <div class="splitToolbarButton">
            <button class="toolbarButton findPrevious" title="" id="findPrevious" tabindex="92" data-l10n-id="find_previous">
              <span data-l10n-id="find_previous_label">Previous</span>
            </button>
            <div class="splitToolbarButtonSeparator"></div>
            <button class="toolbarButton findNext" title="" id="findNext" tabindex="93" data-l10n-id="find_next">
              <span data-l10n-id="find_next_label">Next</span>
            </button>
          </div>
          <input type="checkbox" id="findHighlightAll" class="toolbarField">
          <label for="findHighlightAll" class="toolbarLabel" tabindex="94" data-l10n-id="find_highlight">Highlight all</label>
          <input type="checkbox" id="findMatchCase" class="toolbarField">
          <label for="findMatchCase" class="toolbarLabel" tabindex="95" data-l10n-id="find_match_case_label">Match case</label>
          <span id="findMsg" class="toolbarLabel"></span>
        </div>  <!-- findbar -->

        <div id="secondaryToolbar" class="secondaryToolbar hidden doorHangerRight">
          <div id="secondaryToolbarButtonContainer">
            <button id="secondaryPresentationMode" class="secondaryToolbarButton presentationMode visibleLargeView" title="Switch to Presentation Mode" tabindex="51" data-l10n-id="presentation_mode">
              <span data-l10n-id="presentation_mode_label">Presentation Mode</span>
            </button>

            <button id="secondaryOpenFile" class="secondaryToolbarButton openFile visibleLargeView" title="Open File" tabindex="52" data-l10n-id="open_file">
              <span data-l10n-id="open_file_label">Open</span>
            </button>

            <button id="secondaryPrint" class="secondaryToolbarButton print visibleMediumView" title="Print" tabindex="53" data-l10n-id="print">
              <span data-l10n-id="print_label">Print</span>
            </button>

            <button id="secondaryDownload" class="secondaryToolbarButton download visibleMediumView" title="Download" tabindex="54" data-l10n-id="download">
              <span data-l10n-id="download_label">Download</span>
            </button>

            <a href="#" id="secondaryViewBookmark" class="secondaryToolbarButton bookmark visibleSmallView" title="Current view (copy or open in new window)" tabindex="55" data-l10n-id="bookmark">
              <span data-l10n-id="bookmark_label">Current View</span>
            </a>

            <div class="horizontalToolbarSeparator visibleLargeView"></div>

            <button id="firstPage" class="secondaryToolbarButton firstPage" title="Go to First Page" tabindex="56" data-l10n-id="first_page">
              <span data-l10n-id="first_page_label">Go to First Page</span>
            </button>
            <button id="lastPage" class="secondaryToolbarButton lastPage" title="Go to Last Page" tabindex="57" data-l10n-id="last_page">
              <span data-l10n-id="last_page_label">Go to Last Page</span>
            </button>

            <div class="horizontalToolbarSeparator"></div>

            <button id="pageRotateCw" class="secondaryToolbarButton rotateCw" title="Rotate Clockwise" tabindex="58" data-l10n-id="page_rotate_cw">
              <span data-l10n-id="page_rotate_cw_label">Rotate Clockwise</span>
            </button>
            <button id="pageRotateCcw" class="secondaryToolbarButton rotateCcw" title="Rotate Counterclockwise" tabindex="59" data-l10n-id="page_rotate_ccw">
              <span data-l10n-id="page_rotate_ccw_label">Rotate Counterclockwise</span>
            </button>

            <div class="horizontalToolbarSeparator"></div>

            <button id="toggleHandTool" class="secondaryToolbarButton handTool" title="Enable hand tool" tabindex="60" data-l10n-id="hand_tool_enable">
              <span data-l10n-id="hand_tool_enable_label">Enable hand tool</span>
            </button>
            
            <div class="horizontalToolbarSeparator"></div>

            <button id="documentProperties" class="secondaryToolbarButton documentProperties" title="Document Properties…" tabindex="61" data-l10n-id="document_properties">
              <span data-l10n-id="document_properties_label">Document Properties…</span>
            </button>
          </div>
        </div>  <!-- secondaryToolbar -->

        <div class="toolbar">
          <div id="toolbarContainer">
            <div id="toolbarViewer">
              <div id="toolbarViewerLeft">
                <button id="sidebarToggle" class="toolbarButton" title="Toggle Sidebar" tabindex="11" data-l10n-id="toggle_sidebar">
                  <span data-l10n-id="toggle_sidebar_label">Toggle Sidebar</span>
                </button>
                <div class="toolbarButtonSpacer"></div>
                <button id="viewFind" class="toolbarButton group hiddenSmallView" title="Find in Document" tabindex="12" data-l10n-id="findbar">
                   <span data-l10n-id="findbar_label">Find</span>
                </button>
                <div class="splitToolbarButton">
                  <button class="toolbarButton pageUp" title="Previous Page" id="previous" tabindex="13" data-l10n-id="previous">
                    <span data-l10n-id="previous_label">Previous</span>
                  </button>
                  <div class="splitToolbarButtonSeparator"></div>
                  <button class="toolbarButton pageDown" title="Next Page" id="next" tabindex="14" data-l10n-id="next">
                    <span data-l10n-id="next_label">Next</span>
                  </button>
                </div>
                <label id="pageNumberLabel" class="toolbarLabel" for="pageNumber" data-l10n-id="page_label">Page: </label>
                <input type="number" id="pageNumber" class="toolbarField pageNumber" value="1" size="4" min="1" tabindex="15">
                <span id="numPages" class="toolbarLabel"></span>
              </div>
              <div id="toolbarViewerRight">
                <button id="presentationMode" class="toolbarButton presentationMode hiddenLargeView" title="Switch to Presentation Mode" tabindex="31" data-l10n-id="presentation_mode">
                  <span data-l10n-id="presentation_mode_label">Presentation Mode</span>
                </button>

                <button id="openFile" class="toolbarButton openFile hiddenLargeView" title="Open File" tabindex="32" data-l10n-id="open_file">
                  <span data-l10n-id="open_file_label">Open</span>
                </button>

                <button id="print" class="toolbarButton print hiddenMediumView" title="Print" tabindex="33" data-l10n-id="print">
                  <span data-l10n-id="print_label">Print</span>
                </button>

                <button id="download" class="toolbarButton download hiddenMediumView" title="Download" tabindex="34" data-l10n-id="download">
                  <span data-l10n-id="download_label">Download</span>
                </button>
                <!-- <div class="toolbarButtonSpacer"></div> -->
                <a href="#" id="viewBookmark" class="toolbarButton bookmark hiddenSmallView" title="Current view (copy or open in new window)" tabindex="35" data-l10n-id="bookmark">
                  <span data-l10n-id="bookmark_label">Current View</span>
                </a>

                <div class="verticalToolbarSeparator hiddenSmallView"></div>
                
                <button id="secondaryToolbarToggle" class="toolbarButton" title="Tools" tabindex="36" data-l10n-id="tools">
                  <span data-l10n-id="tools_label">Tools</span>
                </button> 
              </div>
              <div class="outerCenter">
                <div class="innerCenter" id="toolbarViewerMiddle">
                  <div class="splitToolbarButton">
                    <button id="zoomOut" class="toolbarButton zoomOut" title="Zoom Out" tabindex="21" data-l10n-id="zoom_out">
                      <span data-l10n-id="zoom_out_label">Zoom Out</span>
                    </button>
                    <div class="splitToolbarButtonSeparator"></div>
                    <button id="zoomIn" class="toolbarButton zoomIn" title="Zoom In" tabindex="22" data-l10n-id="zoom_in">
                      <span data-l10n-id="zoom_in_label">Zoom In</span>
                     </button>
                  </div>
                  <span id="scaleSelectContainer" class="dropdownToolbarButton">
                     <select id="scaleSelect" title="Zoom" tabindex="23" data-l10n-id="zoom">
                      <option id="pageAutoOption" title="" value="auto" selected="selected" data-l10n-id="page_scale_auto">Automatic Zoom</option>
                      <option id="pageActualOption" title="" value="page-actual" data-l10n-id="page_scale_actual">Actual Size</option>
                      <option id="pageFitOption" title="" value="page-fit" data-l10n-id="page_scale_fit">Fit Page</option>
                      <option id="pageWidthOption" title="" value="page-width" data-l10n-id="page_scale_width">Full Width</option>
                      <option id="customScaleOption" title="" value="custom"></option>
                      <option title="" value="0.5" data-l10n-id="page_scale_percent" data-l10n-args='{ "scale": 50 }'>50%</option>
                      <option title="" value="0.75" data-l10n-id="page_scale_percent" data-l10n-args='{ "scale": 75 }'>75%</option>
                      <option title="" value="1" data-l10n-id="page_scale_percent" data-l10n-args='{ "scale": 100 }'>100%</option>
                      <option title="" value="1.25" data-l10n-id="page_scale_percent" data-l10n-args='{ "scale": 125 }'>125%</option>
                      <option title="" value="1.5" data-l10n-id="page_scale_percent" data-l10n-args='{ "scale": 150 }'>150%</option>
                      <option title="" value="2" data-l10n-id="page_scale_percent" data-l10n-args='{ "scale": 200 }'>200%</option>
                      <option title="" value="3" data-l10n-id="page_scale_percent" data-l10n-args='{ "scale": 300 }'>300%</option>
                      <option title="" value="4" data-l10n-id="page_scale_percent" data-l10n-args='{ "scale": 400 }'>400%</option>
                    </select>
                  </span>
                </div>
              </div>
            </div>
            <div id="loadingBar">
              <div class="progress">
                <div class="glimmer">
                </div>
              </div>
            </div>
          </div>
        </div>

        <menu type="context" id="viewerContextMenu">
          <menuitem id="contextFirstPage" label="First Page"
                    data-l10n-id="first_page"></menuitem>
          <menuitem id="contextLastPage" label="Last Page"
                    data-l10n-id="last_page"></menuitem>
          <menuitem id="contextPageRotateCw" label="Rotate Clockwise"
                    data-l10n-id="page_rotate_cw"></menuitem>
          <menuitem id="contextPageRotateCcw" label="Rotate Counter-Clockwise"
                    data-l10n-id="page_rotate_ccw"></menuitem>
        </menu>

        <div id="viewerContainer" tabindex="0">
          <div id="viewer" class="pdfViewer"></div>
        </div>

        <div id="errorWrapper" hidden='true'>
          <div id="errorMessageLeft">
            <span id="errorMessage"></span>
            <button id="errorShowMore" data-l10n-id="error_more_info">
              More Information
            </button>
            <button id="errorShowLess" data-l10n-id="error_less_info" hidden='true'>
              Less Information
            </button>
          </div>
          <div id="errorMessageRight">
            <button id="errorClose" data-l10n-id="error_close">
              Close
            </button>
          </div>
          <div class="clearBoth"></div>
          <textarea id="errorMoreInfo" hidden='true' readonly="readonly"></textarea>
        </div>
      </div> <!-- mainContainer -->

      <div id="overlayContainer" class="hidden">
        <div id="passwordOverlay" class="container hidden">
          <div class="dialog">
            <div class="row">
              <p id="passwordText" data-l10n-id="password_label">Enter the password to open this PDF file:</p>
            </div>
            <div class="row">
              <input type="password" id="password" class="toolbarField" />
            </div>
            <div class="buttonRow">
              <button id="passwordCancel" class="overlayButton"><span data-l10n-id="password_cancel">Cancel</span></button>
              <button id="passwordSubmit" class="overlayButton"><span data-l10n-id="password_ok">OK</span></button>
            </div>
          </div>
        </div>
        <div id="documentPropertiesOverlay" class="container hidden">
          <div class="dialog">
            <div class="row">
              <span data-l10n-id="document_properties_file_name">File name:</span> <p id="fileNameField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_file_size">File size:</span> <p id="fileSizeField">-</p>
            </div>
            <div class="separator"></div>
            <div class="row">
              <span data-l10n-id="document_properties_title">Title:</span> <p id="titleField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_author">Author:</span> <p id="authorField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_subject">Subject:</span> <p id="subjectField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_keywords">Keywords:</span> <p id="keywordsField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_creation_date">Creation Date:</span> <p id="creationDateField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_modification_date">Modification Date:</span> <p id="modificationDateField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_creator">Creator:</span> <p id="creatorField">-</p>
            </div>
            <div class="separator"></div>
            <div class="row">
              <span data-l10n-id="document_properties_producer">PDF Producer:</span> <p id="producerField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_version">PDF Version:</span> <p id="versionField">-</p>
            </div>
            <div class="row">
              <span data-l10n-id="document_properties_page_count">Page Count:</span> <p id="pageCountField">-</p>
            </div>
            <div class="buttonRow">
              <button id="documentPropertiesClose" class="overlayButton"><span data-l10n-id="document_properties_close">Close</span></button>
            </div>
          </div>
        </div>
      </div>  <!-- overlayContainer -->

    </div> <!-- outerContainer -->
    <div id="segDiv" hidden>
	<style scoped>
#segDiv {
  position: fixed;
  top: 0;
  left: 0;
  height: 100%;
  width: 100%;
  z-index: 9999999;

  display: block;
  text-align: center;
  background-color: rgba(0, 0, 0, 0.5);
}
#segDiv[hidden] {
  display: none;
}

#segDiv .segDiv-dialog-box {
  display: inline-block;
  margin: -50px auto 0;
  position: relative;
  top: 45%;
  left: 0;
  min-width: 220px;
  max-width: 768px;

  padding: 9px;

  border: 1px solid hsla(0, 0%, 0%, .5);
  border-radius: 2px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);

  background-color: #474747;

  color: hsl(0, 0%, 85%);
  font-size: 16px;
  line-height: 20px;
}
#segDiv .segDiv-row {
  clear: both;
  padding: 1em 0;
}
#segDiv .segDiv-actions {
  clear: both;
}
  </style>
	<div class="segDiv-dialog-box">
    <div class="segDiv-row">
      <textarea id="segText" rows="5"></textarea>
    </div>
    <div class="segDiv-actions">
	  <input type="button" value="Cancel" onclick="saveSegment(false)"> &nbsp;&nbsp;
	  <input type="button" value="Save" onclick="saveSegment(true)"> &nbsp;&nbsp;
      <!-- <input type="checkbox" id="rePDF">Re-Generate PDF?  -->
    </div>
  </div>
	</div>
    <div id="printContainer"></div>
<div id="mozPrintCallback-shim" hidden>
  <style scoped>
#mozPrintCallback-shim {
  position: fixed;
  top: 0;
  left: 0;
  height: 100%;
  width: 100%;
  z-index: 9999999;

  display: block;
  text-align: center;
  background-color: rgba(0, 0, 0, 0.5);
}
#mozPrintCallback-shim[hidden] {
  display: none;
}
@media print {
  #mozPrintCallback-shim {
    display: none;
  }
}

#mozPrintCallback-shim .mozPrintCallback-dialog-box {
  display: inline-block;
  margin: -50px auto 0;
  position: relative;
  top: 45%;
  left: 0;
  min-width: 220px;
  max-width: 400px;

  padding: 9px;

  border: 1px solid hsla(0, 0%, 0%, .5);
  border-radius: 2px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);

  background-color: #474747;

  color: hsl(0, 0%, 85%);
  font-size: 16px;
  line-height: 20px;
}
#mozPrintCallback-shim .progress-row {
  clear: both;
  padding: 1em 0;
}
#mozPrintCallback-shim progress {
  width: 100%;
}
#mozPrintCallback-shim .relative-progress {
  clear: both;
  float: right;
}
#mozPrintCallback-shim .progress-actions {
  clear: both;
}
  </style>
  <div class="mozPrintCallback-dialog-box">
    <!-- TODO: Localise the following strings -->
    Preparing document for printing...
    <div class="progress-row">
      <progress value="0" max="100"></progress>
      <span class="relative-progress">0%</span>
    </div>
    <div class="progress-actions">
      <input type="button" value="Cancel" class="mozPrintCallback-cancel">
    </div>
  </div>
</div>

  </body>
</html>

