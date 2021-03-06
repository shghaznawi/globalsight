/**
 *  Copyright 2009 Welocalize, Inc. 
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  
 *  You may obtain a copy of the License at 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */
package com.globalsight.connector.mindtouch.util;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.json.JSONObject;
import org.xml.sax.InputSource;

import com.globalsight.connector.mindtouch.vo.MindTouchPage;
import com.globalsight.connector.mindtouch.vo.MindTouchPageInfo;
import com.globalsight.cxe.entity.mindtouch.MindTouchConnector;
import com.globalsight.ling.common.URLEncoder;
import com.globalsight.util.FileUtil;
import com.globalsight.util.StringUtil;
import com.globalsight.util.edit.EditUtil;

public class MindTouchHelper
{
    static private final Logger logger = Logger
            .getLogger(MindTouchHelper.class);

    private MindTouchConnector mtc = null;

    public MindTouchHelper(MindTouchConnector mtc)
    {
        this.mtc = mtc;
    }

    /**
     * Test if it can connect to MindTouch server successfully.
     * 
     * @return error message if failed; return null if successfully.
     */
    public String doTest()
    {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        try
        {
            String url = mtc.getUrl() + "/@api/deki/pages/home/info";
            HttpGet httpget = getHttpGet(url);

            HttpResponse httpResponse = httpClient.execute(httpget);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == 200)
            {
                return null;
            }
            else
            {
                return httpResponse.getStatusLine().toString();
            }
        }
        catch (Exception e)
        {
            logger.warn("Fail to test MindTouch connector: " + e.getMessage());
            return "Failed to connect to MindTouch server";
        }
        finally
        {
            shutdownHttpClient(httpClient);
        }
    }

    public void deletePage(long pageId) throws Exception
    {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        try
        {
            String url = mtc.getUrl() + "/@api/deki/pages/" + pageId;
            HttpDelete httpDelete = getHttpDelete(url);

            HttpResponse httpResponse = httpClient.execute(httpDelete);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode != 200)
            {
                logger.error("Fail to delete page: " + pageId
                        + ", returning info is: "
                        + EntityUtils.toString(httpResponse.getEntity()));
            }
        }
        catch (Exception e)
        {
            logger.error("Fail to delete page: " + pageId, e);
        }
        finally
        {
            shutdownHttpClient(httpClient);
        }
    }

    /**
     * Get the tree in XML format for specified pageId. For root page, the
     * parameter can be "home".
     */
    public String getTreeXml(String pageId)
    {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        try
        {
            String url = mtc.getUrl() + "/@api/deki/pages/" + pageId + "/tree";
            HttpGet httpget = getHttpGet(url);

            HttpResponse httpResponse = httpClient.execute(httpget);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == 200)
            {
                return EntityUtils.toString(httpResponse.getEntity());
            }
            else
            {
                logger.warn("Fail to get sitemap tree: "
                        + httpResponse.getStatusLine().toString());
                return null;
            }
        }
        catch (Exception e)
        {
            logger.error("Fail to get sitemap tree: " + e.getMessage());
            return null;
        }
        finally
        {
            shutdownHttpClient(httpClient);
        }
    }

    /**
     * Parse tree xml from "getTreeXml(pageId)" method to form a tree.
     * 
     * @param treeXml
     *            -- the tree information in XML format.
     * @return MindTouchPage
     */
    @SuppressWarnings("rawtypes")
    public MindTouchPage parseTreeXml(String treeXml)
            throws DocumentException
    {
        MindTouchPage rootMtp = null;
        Document doc = getDocument(treeXml);

        String id = null;
        String href = null;
        List pageNodes = doc.selectNodes("//page");
        List<MindTouchPage> allPages = new ArrayList<MindTouchPage>();
        Iterator it = pageNodes.iterator();
        while (it.hasNext())
        {
            MindTouchPage mtp = new MindTouchPage();
            Element pageNode = (Element) it.next();
            // page id
            id = pageNode.attributeValue("id");
            mtp.setId(Long.parseLong(id));
            // href
            href = pageNode.attributeValue("href");
            mtp.setHref(href);
            // parent page id
            String parentName = null;
            if (pageNode.getParent() != null)
            {
                parentName = pageNode.getParent().getName();
            }
            if ("subpages".equals(parentName))
            {
                String parentId = pageNode.getParent().getParent()
                        .attributeValue("id");
                mtp.setParentId(Long.parseLong(parentId));
            }
            else if ("pages".equals(parentName))
            {
                rootMtp = mtp;
            }

            Iterator subNodeIt = pageNode.nodeIterator();
            while (subNodeIt.hasNext())
            {
                Element node = (Element) subNodeIt.next();
                String name = node.getName();
                String text = node.getText();
                if ("uri.ui".equals(name))
                {
                    mtp.setUriUi(text);
                }
                else if ("title".equals(name))
                {
                    mtp.setTitle(text);
                }
                else if ("path".equals(name))
                {
                    mtp.setPath(text);
                }
                else if ("date.created".equals(name))
                {
                    mtp.setDateCreated(text);
                }
            }
            allPages.add(mtp);
        }

        HashMap<Long, MindTouchPage> map = new HashMap<Long, MindTouchPage>();
        for (MindTouchPage mtp : allPages)
        {
            map.put(mtp.getId(), mtp);
        }

        for (MindTouchPage mtp : allPages)
        {
            long parentId = mtp.getParentId();
            MindTouchPage parent = map.get(parentId);
            if (parent != null)
            {
                parent.addSubPage(mtp);
            }
        }

        return rootMtp;
    }

    /**
     * Get page contents with "contents" API.
     * 
     * @param pageId
     */
    public String getPageContents(String pageId)
    {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        try
        {
            String url = mtc.getUrl() + "/@api/deki/pages/" + pageId
                    + "/contents?mode=edit";
            HttpGet httpget = getHttpGet(url);

            HttpResponse httpResponse = httpClient.execute(httpget);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == 200)
            {
                return EntityUtils.toString(httpResponse.getEntity());
            }
            else
            {
                logger.warn("Fail to get page content for pageId " + pageId
                        + " : " + httpResponse.getStatusLine().toString());
            }
        }
        catch (Exception e)
        {
            logger.error("Fail to get page content for pageId: " + pageId, e);
        }
        finally
        {
            shutdownHttpClient(httpClient);
        }

        return null;
    }

    /**
     * Get page tags with "tags" API.
     * 
     * @param pageId
     * @return String
     */
    public String getPageTags(long pageId)
    {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        try
        {
            String url = mtc.getUrl() + "/@api/deki/pages/" + pageId + "/tags";
            HttpGet httpget = getHttpGet(url);

            HttpResponse httpResponse = httpClient.execute(httpget);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == 200)
            {
                return EntityUtils.toString(httpResponse.getEntity());
            }
            else
            {
                logger.warn("Fail to get page tags for pageId " + pageId
                        + " : " + httpResponse.getStatusLine().toString());
            }
        }
        catch (Exception e)
        {
            logger.error("Fail to get page tags for pageId: " + pageId, e);
        }
        finally
        {
            shutdownHttpClient(httpClient);
        }

        return null;
    }

    /**
     * Get page info with "info" API.
     * 
     * @param pageId
     * @return String
     */
    public String getPageInfo(long pageId)
    {
        String url = mtc.getUrl() + "/@api/deki/pages/" + pageId + "/info";
        return getPageInfo2(url);
    }

    /**
     * Get page info with "info" API.
     * 
     * @param path
     * @return String
     */
    public String getPageInfo(String path)
    {
        String url = mtc.getUrl() + "/@api/deki/pages/=" + path + "/info";
        return getPageInfo2(url);
    }

    private String getPageInfo2(String url)
    {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        try
        {
            HttpGet httpget = getHttpGet(url);

            HttpResponse httpResponse = httpClient.execute(httpget);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == 200)
            {
                return EntityUtils.toString(httpResponse.getEntity());
            }
        }
        catch (Exception e)
        {
            logger.warn("Fail to get page info for page: " + url, e);
        }
        finally
        {
            shutdownHttpClient(httpClient);
        }

        return null;
    }

    /**
     * Parse page info xml from "getPageInfo()" method.
     * 
     * @param pageInfoXml
     * @return MindTouchPage
     * @throws DocumentException
     */
    @SuppressWarnings("rawtypes")
    public MindTouchPage parsePageInfoXml(String pageInfoXml)
            throws DocumentException
    {
        MindTouchPage mtp = new MindTouchPage();
        Document doc = getDocument(pageInfoXml);

        String id = null;
        String href = null;
        List pageNodes = doc.selectNodes("//page");
        if (pageNodes != null && pageNodes.size() > 0)
        {
            Element pageNode = (Element) pageNodes.get(0);
            // page id
            id = pageNode.attributeValue("id");
            mtp.setId(Long.parseLong(id));
            // href
            href = pageNode.attributeValue("href");
            mtp.setHref(href);

            String name = null;
            String text = null;
            Iterator subNodeIt = pageNode.nodeIterator();
            while (subNodeIt.hasNext())
            {
                Element node = (Element) subNodeIt.next();
                name = node.getName();
                text = node.getText();
                if ("uri.ui".equals(name))
                {
                    mtp.setUriUi(text);
                }
                else if ("title".equals(name))
                {
                    mtp.setTitle(text);
                }
                else if ("path".equals(name))
                {
                    mtp.setPath(text);
                }
                else if ("date.created".equals(name))
                {
                    mtp.setDateCreated(text);
                }
            }
        }

        return mtp;
    }

    /**
     * Send the translated contents back to MindTouch server via pages "post"
     * API. If the path specified page has already exists, it will be
     * updated;Otherwise, create a new page.
     * 
     * @param contentsTrgFile
     * @param pageInfo
     * @param targetLocale
     * @throws Exception
     */
    public synchronized void postPageContents(File contentsTrgFile,
            MindTouchPageInfo pageInfo, String sourceLocale, String targetLocale)
            throws Exception
    {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        String path = null;
        try
        {
            // to be safe, it must use "text/plain" content type instead of
            // "text/xml" or "application/xml".
            String content = FileUtil.readFile(contentsTrgFile, "UTF-8");
            content = EditUtil.decodeXmlEntities(content);
            content = EditUtil.decodeXmlEntities(content);
            String title = getTitleFromTranslatedContentXml(content);
            content = content.substring(content.indexOf("<body>") + 6);
            content = content.substring(0, content.indexOf("</body>"));
            StringEntity reqEntity = new StringEntity(content, "UTF-8");
            reqEntity.setContentType("text/plain; charset=UTF-8");

            path = getNewPath(pageInfo, sourceLocale, targetLocale);
			String strUrl = mtc.getUrl() + "/@api/deki/pages/=" + path
					+ "/contents?edittime=now&abort=never";
			if (title != null)
			{
				strUrl += "&title=" + title;
			}

			URL url = new URL(strUrl);
			URI uri = new URI(url.getProtocol(), url.getHost(), url.getPath(),
					url.getQuery(), null);
			HttpPost httppost = getHttpPost(uri);
            httppost.setEntity(reqEntity);

            HttpResponse response = httpClient.execute(httppost);

            String entityContent = null;
            if (response.getEntity() != null) {
                entityContent = EntityUtils.toString(response.getEntity());
            }
            if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode())
            {
				logger.error("Fail to post contents back to MindTouch server for page '"
						+ path + "' : " + entityContent);
            }
        }
        catch (Exception e)
        {
            logger.error(
                    "Fail to post contents back to MindTouch server for page '"
                            + path + "'.", e);
        }
        finally
        {
            shutdownHttpClient(httpClient);
        }
    }

    /**
     * Put translated tags to MindTouch server page.
     * 
     * If tags are the same, MindTouch server will ignore them, tag IDs keep
     * unchanged. Otherwise, MindTouch will delete old ones and add new to
     * create new tags(with new tag IDs).
     * 
     * @param tagsTrgFile
     * @param pageInfo
     * @param targetLocale
     */
    public void putPageTags(File tagsTrgFile, MindTouchPageInfo pageInfo,
            String sourceLocale, String targetLocale)
    {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        String path = null;
        try
        {
            path = getNewPath(pageInfo, sourceLocale, targetLocale);

            // To add tags to page, the page must exist. Wait at most 3 minutes.
            int count = 0;
            while (count < 300 && getPageInfo(path) == null)
            {
                count++;
                Thread.sleep(1000);
            }

            String url = mtc.getUrl() + "/@api/deki/pages/=" + path + "/tags";
            HttpPut httpput = getHttpPut(url);

            String content = FileUtil.readFile(tagsTrgFile, "UTF-8");
            content = getTagTitlesXml(content);
            StringEntity reqEntity = new StringEntity(content, "UTF-8");
            reqEntity.setContentType("application/xml; charset=UTF-8");
            httpput.setEntity(reqEntity);

            HttpResponse response = httpClient.execute(httpput);

            String entityContent = null;
            if (response.getEntity() != null) {
                entityContent = EntityUtils.toString(response.getEntity());
            }
            if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode())
            {
				logger.error("Fail to put tags back to MindTouch server for page '"
						+ path + "' : " + entityContent);
            }
        }
        catch (Exception e)
        {
            logger.error(
                    "Fail to put tags back to MindTouch server for page '"
                            + path + "'.", e);
        }
        finally
        {
            shutdownHttpClient(httpClient);
        }
    }

    /**
     * If the original page path is like "en-us/Developer_Resources", the target
     * path should be like "zh-cn/Developer_Resources"; If the original page
     * path has no source locale information, it will add target locale as
     * suffix.
     * 
     * @param pageInfo
     * @param sourceLocale
     * @param targetLocale
     * @return String
     */
    private String getNewPath(MindTouchPageInfo pageInfo, String sourceLocale,
            String targetLocale)
    {
        sourceLocale = sourceLocale.replace("_", "-").toLowerCase();
        targetLocale = targetLocale.replace("_", "-").toLowerCase();

        String path = pageInfo.getPath();
        // this must be root page
        if (StringUtil.isEmpty(path))
        {
            path = pageInfo.getTitle() + "(" + targetLocale + ")";
        }
        // any non-root pages
        else
        {
            path = path.replace("\\", "/");// to be safe
            int index = path.indexOf(sourceLocale);
            if (index > -1)
            {
                String part1 = path.substring(0, index);
                String part2 = path.substring(index + sourceLocale.length());
                path = part1 + targetLocale + part2;
            }
            else
            {
                path += "(" + targetLocale + ")";
            }
        }
        path = URLEncoder.encode(path);
        path = URLEncoder.encode(path);
        return path;
    }

    /**
     * Return an XML like
     * "<tags><tag value=\"title1\"/><tag value=\"title2\"/></tags>".
     * 
     * @param tagsXml
     * @return String
     * @throws DocumentException
     */
    @SuppressWarnings("rawtypes")
    private String getTagTitlesXml(String tagsXml) throws DocumentException
    {
        StringBuffer titles = new StringBuffer();
        titles.append("<tags>");

        Document doc = getDocument(tagsXml);
        List titleNodes = doc.selectNodes("//title");
        Iterator it = titleNodes.iterator();
        String title = null;
        while (it.hasNext())
        {
            Element titleNode = (Element) it.next();
            title = titleNode.getTextTrim();
            if (title != null && title.length() > 0)
            {
                titles.append("<tag value=\"").append(title).append("\"/>");
            }
        }
        titles.append("</tags>");

        return titles.toString();
    }

    /**
	 * As the "title" need to be translated, get the translated title from
	 * target file.
	 * 
	 * @param contentXml
	 * @return title
	 */
    private String getTitleFromTranslatedContentXml(String contentXml)
    {
    	try
    	{
    		String content = contentXml.substring(0, contentXml.indexOf("<body>"));
    		content = content.replace("&nbsp;", " ");
    		content += "</content>";
    		Element root = getDocument(content).getRootElement();
    		String title = root.attributeValue("title");
    		if (title.trim().length() > 0)
    			return new String(title.trim().getBytes("UTF-8"), "UTF-8");
    	}
    	catch (Exception e)
    	{
			logger.error("Fail to get title from translated contents xml: "
					+ contentXml, e);
    		return null;
    	}
    	return null;
    }

    /**
     * An object file content is like:
     * {"title":"Get Involved","PageId":"1845","MindTouchConnectorId":"7","path":"en-us/Developer_Resources/Community/Get_Involved"}
     * 
     * @return MindTouchPageInfo
     * 
     */
    public static MindTouchPageInfo parseObjFile(File objFile)
    {
        MindTouchPageInfo info = new MindTouchPageInfo();
        if (objFile.exists() && objFile.isFile())
        {
            try
            {
                String json = FileUtil.readFile(objFile, "UTF-8");
                JSONObject jsonObj = new JSONObject(json);

                String mindTouchConnectorId = String.valueOf(jsonObj.get("mindTouchConnectorId"));
                info.setMindTouchConnectorId(mindTouchConnectorId);

                String pageId = String.valueOf(jsonObj.get("pageId"));
                info.setPageId(pageId);

                String path = (String) jsonObj.get("path");
                info.setPath(path);

                String title = (String) jsonObj.get("title");
                info.setTitle(title);
            }
            catch (Exception e)
            {
                logger.warn(e.getMessage());
            }
        }

        return info;
    }

    public static Document getDocument(String xml) throws DocumentException
    {
        SAXReader reader = new SAXReader();
        return reader.read(new InputSource(new StringReader(xml)));
    }

    private HttpPost getHttpPost(URI uri)
    {
        HttpPost httppost = new HttpPost(uri);
        httppost.setHeader(HttpHeaders.AUTHORIZATION,
                authorizationHeader(mtc.getUsername(), mtc.getPassword()));
        return httppost;
    }

    private HttpGet getHttpGet(String url)
    {
        HttpGet httpget = new HttpGet(url);
        httpget.setHeader(HttpHeaders.AUTHORIZATION,
                authorizationHeader(mtc.getUsername(), mtc.getPassword()));
        return httpget;
    }

    private HttpPut getHttpPut(String url)
    {
        HttpPut httpput = new HttpPut(url);
        httpput.setHeader(HttpHeaders.AUTHORIZATION,
                authorizationHeader(mtc.getUsername(), mtc.getPassword()));
        return httpput;
    }

    private HttpDelete getHttpDelete(String url)
    {
        HttpDelete httpdelete = new HttpDelete(url);
        httpdelete.setHeader(HttpHeaders.AUTHORIZATION,
                authorizationHeader(mtc.getUsername(), mtc.getPassword()));
        return httpdelete;
    }

    private void shutdownHttpClient(HttpClient httpClient)
    {
        if (httpClient == null)
            return;

        httpClient.getConnectionManager().shutdown();
    }

    private final String authorizationHeader(String username, String password)
    {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset
                .forName("US-ASCII")));
        String authHeader = "Basic " + new String(encodedAuth);

        return authHeader;
    }
}
