function findSegment(tuId, sourceSegment, targetSegment, donotMove)
{
	// check is source or target
	var segment = sourceSegment;
	try
	{
		var element = parent.sourceMenu.highlightedElement;
		var text = element.textContent;
		
		if ("Target PDF" == text)
		{
			segment = targetSegment;
		}
		
	} catch(exc) {}
	
	// navigate to page
	if (!donotMove)
	{
		var segObj = getSegmentByTuid(tuId);
		
		if (segObj)
		{
			PDFViewerApplication.pdfViewer.scrollPageIntoView(segObj.pageNum);
		}
		
		// navigate to dest
		var matchedDiv;
	    var dest = getGlobalSightDest(tuId);
	    if (dest == "")
	    {
		    for (var i = 1; i < 10;  i++)
		    {
		    	dest = getGlobalSightDest(tuId - i);
		    	
		    	if (dest != "")
		    	{
		    		break;
		    	}
		    }
	    }
	    
	    PDFViewerApplication.navigateTo(dest);
    }
    
    // find segment
    var find = false;
    var loPn = PDFViewerApplication.pdfViewer.location.pageNumber;
    
    // find by GlobalSight
    var pageDiv = document.getElementById("pageContainer" + loPn);
    var pageDivChildrens = pageDiv.childNodes;
    var textLayerChildrens;
	var textLayerDiv;
    
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
  	  // 1 find extract match
  	  for(var i = 0; i < textLayerChildrens.length; i++)
  	  {
  		  var divChild = textLayerChildrens[i];
  		  var divContent = divChild.textContent;
  		  var text = divContent.trim();
			
  		  if (segment == text)
  		  {
  			find = true;
  			  matchedDiv = divChild;
  			  var textnode = document.createTextNode(divContent);
  			  var spannode = document.createElement('span');
  			  spannode.className = "highlight";
  			  spannode.appendChild(textnode);
  			matchedDiv.innerHTML = spannode.outerHTML;
  			matchedDiv.focus();
  		  }
  		  else // clean last match
  		  {
  			divChild.innerHTML = divContent;
  			
  			var className = divChild.className;
  			if (className.indexOf("highlight") != -1)
  			{
  				className = className.replace("highlight", "");
  	  			divChild.className = className;
  			}
  		  }
  	  }
    }
    
    // find by PDF.js
    if (!find)
    {
	    var findStr = find ? "" : segment;
	    var event = document.createEvent('CustomEvent');
	    event.initCustomEvent('find', true, true, {
	      query: segment,
	      caseSensitive: true,
	      highlightAll: false,
	      findPrevious: ""
	    });
	    
	    PDFViewerApplication.pdfViewer.findController.dirtyMatch = true;
	    PDFViewerApplication.pdfViewer.findController.hadMatch = false;
	    PDFViewerApplication.pdfViewer.findController.pagesToSearch = loPn;
	    PDFViewerApplication.pdfViewer.findController.handleEvent(event);
	    
	    find = PDFViewerApplication.pdfViewer.findController.hadMatch;
    }
    
      if (!find)
	  {
		 // find more
	  	  var startMatch = false;
		  var endMatch = false;
	  	  matchedDiv = new Array();
	  	  var textLayerContent = textLayerDiv.textContent;
	  	  
	  	  if (textLayerContent.indexOf(segment) != -1)
	  	  {
	  		  var index = textLayerContent.indexOf(segment);
	  		  var segmentLen = segment.length;
	  		  var count = 0;
	  		  for(var i = 0; i < textLayerChildrens.length; i++)
  	  	      {// for
  	  		  var divChild = textLayerChildrens[i];
  	  		  var divContent = divChild.textContent;
  			  var divContentLen = divContent.length;
  			  
  			  if (index < (count + divContentLen) && index >= count)
  			  {
  				  // find segment in one div
  				  if ((index + segmentLen) <= (count + divContentLen))
  				  {
  					  var obj = new Object();
  					  obj.div = divChild;
  					  obj.start = index - count;
  					  obj.end = index + segmentLen - count;
  					  matchedDiv.push(obj);
      				  break;
  				  }
  				  else
  				  {
  					  var obj = new Object();
  					  obj.div = divChild;
  					  obj.start = index - count;
  					  obj.end = divContentLen;
  					  matchedDiv.push(obj);
  				  }
  			  }
  			  
  			  if (matchedDiv.length > 0)
  			  {
  				  if ((index + segmentLen) <= (count + divContentLen))
  				  {
  					  var obj = new Object();
  					  obj.div = divChild;
  					  obj.start = 0;
  					  obj.end = index + segmentLen - count;
  					  matchedDiv.push(obj);
      				  break;
  				  }
  				  else
  				  {
  					  var obj = new Object();
  					  obj.div = divChild;
  					  obj.start = 0;
  					  obj.end = divContentLen;
  					  matchedDiv.push(obj);
  				  }
  			  }
  			  
  			  
  			  count = count + divContentLen;
  	  	  }// for
	  	  }// if
	  	  
	  	  if (matchedDiv.length > 0)
	  	  {
	  		  find = true;
	  		  for(var i = 0; i < matchedDiv.length; i++)
	  		  {
	  			  var obj = matchedDiv[i];
	  			  var cdiv = obj.div;
	  			  var textContent = cdiv.textContent;
	  			  var strStart = textContent.substr(0, obj.start);
	  			  var strMid = textContent.substr(obj.start, obj.end);
	  			  var strEnd = obj.end < textContent.length ? textContent.substr(obj.end) : "";
	  			  
	  			  var newDiv = document.createElement('div');
	  			  if (strStart.length > 0)
	  				  {
	  				var textnode = document.createTextNode(strStart);
	  				newDiv.appendChild(textnode);
	  				  }
	  			  if (strMid.length > 0)
	  				  {
	  				var textnode = document.createTextNode(strMid);
	  				var spannode = document.createElement('span');
  	  			  spannode.className = "highlight";
  	  			  spannode.appendChild(textnode);
  	  			newDiv.appendChild(spannode);
	  				  }
	  			  if (strEnd.length > 0)
	  				  {
	  				var textnode = document.createTextNode(strEnd);
	  				newDiv.appendChild(textnode);
	  				  }
	  			 
	  			  
	  			cdiv.innerHTML = newDiv.innerHTML;
	  		  }
	  	  }
	  }
}

function getGlobalSightDest(tuId)
{
	var allAnchors = document.getElementsByTagName("a");
    var num = allAnchors.length;
	for(var iii = 0; iii < num; iii++)
	{
		var ele = allAnchors[iii];
		var gsid = "GlobalSight_" + tuId;
		
		if (ele.href.indexOf(gsid) != -1)
		{
			var index_s = ele.href.lastIndexOf("#");
			var dest = ele.href.substring(index_s + 1);
			dest = dest.replace(/%3A/g, ":");
			
			return dest;
		}
	}
	
	return "";
}

function navigateToDiv(pageNumber, left, top, curScale)
{
	var para = new Array();
	para[0] = "XYZ";
	var paraName = new Object();
	paraName.name = "XYZ";
	para[1] = paraName;
	para[2] = left;
	para[3] = top;
	para[4] = curScale;
	
	PDFViewerApplication.pdfViewer.scrollPageIntoView(pageNumber, para);
}

function sortDivByOffset(divA, divB)
{
	var offsetTopA = divA.offsetTop;
	var offsetTopB = divB.offsetTop;
	
	var offsetLeftA = divA.offsetLeft;
	var offsetLeftB = divB.offsetLeft;
	
	if (offsetTopA == offsetTopB)
	{
		return offsetLeftA - offsetLeftB;
	}
	else
	{
		return offsetTopA - offsetTopB;
	}
}

function getSegmentByTuid(tuId)
{
	var localData = window.parent.parent.parent.localData;

	for (var i = 0; i < localData.source.length; i++)
	{
		var seg = localData.source[i];
		
		if (seg.tuId && seg.tuId == tuId)
		{
		    if (seg.pageNum > 0)
		    {
			var segment = new Object();
			segment.tuId = seg.tuId;
			segment.subId = seg.subId;
			segment.srcTuvId = localData.source[i].tuvId;
			segment.srcSegment = localData.source[i].segment;
			segment.srcSegmentNoTag = handleSpecialChar(localData.source[i].segmentNoTag);
			
			segment.tgtTuvId = localData.target[i].tuvId;
			segment.tgtSegment = localData.target[i].segment;
			segment.tgtSegmentNoTag = handleSpecialChar(localData.target[i].segmentNoTag);
			
			segment.pageNum = seg.pageNum;
			
			return segment;
			}
			else
			{
			return false;
			}
		}
	}
	
	return false;
}

function buildPageContent(pageNum, localData, isTarget)
{
	var content = "";
	var segments = new Array();
	
	for (var i = 0; i < localData.source.length; i++)
	{
		var seg = isTarget ? localData.target[i] : localData.source[i];
		
		if (seg.pageNum && seg.pageNum == pageNum)
		{
			var segment = new Object();
			segment.tuId = seg.tuId;
			segment.subId = seg.subId;
			segment.srcTuvId = localData.source[i].tuvId;
			segment.srcSegment = localData.source[i].segment;
			segment.srcSegmentNoTag = handleSpecialChar(localData.source[i].segmentNoTag);
			
			segment.tgtTuvId = localData.target[i].tuvId;
			segment.tgtSegment = localData.target[i].segment;
			segment.tgtSegmentNoTag = handleSpecialChar(localData.target[i].segmentNoTag);
			
			segment.pageNum = seg.pageNum;
			
			segment.start = content.length;
			content = content + handleSpecialChar(seg.segmentNoTag) + " ";
			segment.end = content.length;
			
			segments[segments.length] = segment;
		}
	}
	
	var result = new Object();
	result.segments = segments;
	result.content = content;
	
	return result;
}

function getPageNumberFromParentId(o)
{
	var pa = o.parentElement;
	while (pa)
	{
		if ("page" == pa.className && pa.id && pa.id.indexOf("pageContainer") != -1)
		{
			var num = pa.id.substr(13);
			return num;
		}
		
		pa = pa.parentElement;
	}
	
	return -1;
}

function handleSpecialChar(seg)
{
	var rrr = seg.replace(/^\s+|\s+$/g,'');
	
	rrr = rrr.replace('\t', ' ')
	
	var result = "";
	for (var i = 0; i < rrr.length; i++)
    {
        var ccc = rrr.charAt(i);
        var cccCode = rrr.charCodeAt(i);

        if (cccCode == 9632)
        {
            continue;
        }

        if (i > 0)
        {
            var lastChar = result.length > 0 ? result.charAt(result.length - 1)
                    : 'N';
            // ignore tab
            if (ccc == '\t' && lastChar == ' ')
            {
                continue;
            }
        }

        result = result + ccc;
    }
	
	
	return result;
}

function sendAjax(obj)
{
	dojo.xhrPost(
	{
		url:"/globalsight/ControlServlet?linkName=refreshSelf&pageName=inctxrvED8",
		content:obj,
		handleAs: "text", 
		load:function(data){
			alert("Segment is updated. ");
		},
		error:function(error)
		{
			alert(error.message);
		}
	});
}

function getSegment(pageContent, o, i, divArr)
{
	if (i == 0)
	{
		return pageContent.segments[0];
	}
	else if (i == (divArr.length - 1))
	{
		return pageContent.segments[pageContent.segments.length - 1];
	}
	else
	{
		var o_0 = divArr[i-1];
		var o_1 = o;
		var o_2 =  divArr[i+1];
		
		var o0text = o_0.textContent;
		var o1text = o_1.textContent;
		var o2text = o_2.textContent;
		
		// 1 find before current next
		var index = pageContent.content.indexOf(o1text);
		var index0, index2;
		if (index != -1)
		{
			index0 = pageContent.content.lastIndexOf(o0text, index);
			index2 = pageContent.content.indexOf(o2text, index);
		}
		
		while (index != -1)
		{
			if ((index == (index0 + o0text.length + 1) || index == (index0 + o0text.length )) 
				&&  ((index + o1text.length)  == index2 || (index + o1text.length + 1)  == index2 ))
			{
				break;
			}
			
			index = pageContent.content.indexOf(o1text, (index + o1text.length));
			if (index != -1)
			{
				index0 = pageContent.content.lastIndexOf(o0text, index);
				index2 = pageContent.content.indexOf(o2text, index);
			}
		}
		
		// 2 find before current
		if (index == -1)
		{
			index = pageContent.content.indexOf(o1text);
			if (index != -1)
			{
				index0 = pageContent.content.lastIndexOf(o0text, index);
			}
			
			while (index != -1)
			{
				if (index == (index0 + o0text.length + 1) || index == (index0 + o0text.length ))
				{
					break;
				}
				
				index = pageContent.content.indexOf(o1text, (index + o1text.length));
				if (index != -1)
				{
					index0 = pageContent.content.lastIndexOf(o0text, index);
				}
			}
		}
		
		// 3 find current next
		if (index == -1)
		{
			index = pageContent.content.indexOf(o1text);
			if (index != -1)
			{
				index2 = pageContent.content.indexOf(o2text, index);
			}
			
			while (index != -1)
			{
				if ((index + o1text.length)  == index2 || (index + o1text.length + 1)  == index2 )
				{
					break;
				}
				
				index = pageContent.content.indexOf(o1text, (index + o1text.length));
				if (index != -1)
				{
					index2 = pageContent.content.indexOf(o2text, index);
				}
			}
		}
		
		// 4 find current
		if (index == -1)
		{
			index = pageContent.content.indexOf(o1text);
		}
		
		if (index != -1)
		{
			for (var j = 0; j < pageContent.segments.length; j++)
			{
				var seg = pageContent.segments[j];
				if (index >= seg.start && index < seg.end)
				{
					return seg;
				}
			}
			
			
			return false;
		}
		else
		{
			return false;
		}
	}
}