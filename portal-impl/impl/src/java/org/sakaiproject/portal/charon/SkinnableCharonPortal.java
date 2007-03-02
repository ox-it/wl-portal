/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2005, 2006 The Sakai Foundation.
 *
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/ecl1.php
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/
// Base version: CharonPortal.java 14784 -- this must be updated to map changes in Charon 
// Patched to 17988
package org.sakaiproject.portal.charon;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.portal.api.Portal;
import org.sakaiproject.portal.api.PortalHandler;
import org.sakaiproject.portal.api.PortalRenderContext;
import org.sakaiproject.portal.api.PortalRenderEngine;
import org.sakaiproject.portal.api.PortalService;
import org.sakaiproject.portal.api.StoredState;
import org.sakaiproject.portal.charon.handlers.AtomHandler;
import org.sakaiproject.portal.charon.handlers.DirectToolHandler;
import org.sakaiproject.portal.charon.handlers.ErrorDoneHandler;
import org.sakaiproject.portal.charon.handlers.GalleryHandler;
import org.sakaiproject.portal.charon.handlers.HelpHandler;
import org.sakaiproject.portal.charon.handlers.LoginGalleryHandler;
import org.sakaiproject.portal.charon.handlers.LoginHandler;
import org.sakaiproject.portal.charon.handlers.LogoutGalleryHandler;
import org.sakaiproject.portal.charon.handlers.LogoutHandler;
import org.sakaiproject.portal.charon.handlers.NavLoginGalleryHandler;
import org.sakaiproject.portal.charon.handlers.NavLoginHandler;
import org.sakaiproject.portal.charon.handlers.OpmlHandler;
import org.sakaiproject.portal.charon.handlers.PageHandler;
import org.sakaiproject.portal.charon.handlers.PortletHandler;
import org.sakaiproject.portal.charon.handlers.PresenceHandler;
import org.sakaiproject.portal.charon.handlers.ReLoginHandler;
import org.sakaiproject.portal.charon.handlers.RssHandler;
import org.sakaiproject.portal.charon.handlers.SiteHandler;
import org.sakaiproject.portal.charon.handlers.StaticScriptsHandler;
import org.sakaiproject.portal.charon.handlers.StaticStylesHandler;
import org.sakaiproject.portal.charon.handlers.ToolHandler;
import org.sakaiproject.portal.charon.handlers.ToolResetHandler;
import org.sakaiproject.portal.charon.handlers.WorksiteHandler;
import org.sakaiproject.portal.charon.handlers.XLoginHandler;
import org.sakaiproject.portal.render.api.RenderResult;
import org.sakaiproject.portal.render.cover.ToolRenderService;
import org.sakaiproject.portal.util.ErrorReporter;
import org.sakaiproject.portal.util.PortalSiteHelper;
import org.sakaiproject.portal.util.ToolURLManagerImpl;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.thread_local.cover.ThreadLocalManager;
import org.sakaiproject.tool.api.ActiveTool;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.api.ToolException;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.tool.api.ToolURL;
import org.sakaiproject.tool.cover.ActiveToolManager;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.BasicAuth;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.util.StringUtil;
import org.sakaiproject.util.Web;

/**
 * <p/> Charon is the Sakai Site based portal.
 * </p>
 */
public class SkinnableCharonPortal extends HttpServlet implements Portal
{
	/**
	 * Our log (commons).
	 */
	private static Log M_log = LogFactory.getLog(SkinnableCharonPortal.class);

	/**
	 * messages.
	 */
	private static ResourceLoader rloader = new ResourceLoader("sitenav");

	/**
	 * Parameter value to indicate to look up a tool ID within a site
	 */
	protected static final String PARAM_SAKAI_SITE = "sakai.site";

	private BasicAuth basicAuth = null;

	private boolean enableDirect = false;

	private PortalService portalService;

	private static final String PADDING = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";

	private static final String INCLUDE_BOTTOM = "include-bottom";

	private static final String INCLUDE_LOGIN = "include-login";

	private static final String INCLUDE_TITLE = "include-title";

	private PortalSiteHelper siteHelper = new PortalSiteHelper();

	// private HashMap<String, PortalHandler> handlerMap = new HashMap<String,
	// PortalHandler>();

	private GalleryHandler galleryHandler;

	private WorksiteHandler worksiteHandler;

	private SiteHandler siteHandler;

	private String portalContext;

	public String getPortalContext()
	{
		return portalContext;
	}

	/**
	 * Shutdown the servlet.
	 */
	public void destroy()
	{
		M_log.info("destroy()");
		portalService.removePortal(this);

		super.destroy();
	}

	public void doError(HttpServletRequest req, HttpServletResponse res, Session session,
			int mode) throws ToolException, IOException
	{
		if (ThreadLocalManager.get(ATTR_ERROR) == null)
		{
			ThreadLocalManager.set(ATTR_ERROR, ATTR_ERROR);

			// send to the error site
			switch (mode)
			{
				case ERROR_SITE:
				{
					siteHandler.doSite(req, res, session, "!error", null, req
							.getContextPath()
							+ req.getServletPath());
					break;
				}
				case ERROR_GALLERY:
				{
					galleryHandler.doGallery(req, res, session, "!error", null, req
							.getContextPath()
							+ req.getServletPath());
					break;
				}
				case ERROR_WORKSITE:
				{
					worksiteHandler.doWorksite(req, res, session, "!error", null, req
							.getContextPath()
							+ req.getServletPath());
					break;
				}
			}
			return;
		}

		// error and we cannot use the error site...

		// form a context sensitive title
		String title = ServerConfigurationService.getString("ui.service") + " : Portal";

		// start the response
		PortalRenderContext rcontext = startPageContext("", title, null, req);

		showSession(rcontext, true);

		showSnoop(rcontext, true, getServletConfig(), req);

		sendResponse(rcontext, res, "error", null);
	}

	private void showSnoop(PortalRenderContext rcontext, boolean b,
			ServletConfig servletConfig, HttpServletRequest req)
	{
		Enumeration e = null;

		rcontext.put("snoopRequest", req.toString());

		if (servletConfig != null)
		{
			Map<String, Object> m = new HashMap<String, Object>();
			e = servletConfig.getInitParameterNames();

			if (e != null)
			{
				boolean first = true;
				while (e.hasMoreElements())
				{
					String param = (String) e.nextElement();
					m.put(param, servletConfig.getInitParameter(param));
				}
			}
			rcontext.put("snoopServletConfigParams", m);
		}
		rcontext.put("snoopRequest", req);

		e = req.getHeaderNames();
		if (e.hasMoreElements())
		{
			Map<String, Object> m = new HashMap<String, Object>();
			while (e.hasMoreElements())
			{
				String name = (String) e.nextElement();
				m.put(name, req.getHeader(name));
			}
			rcontext.put("snoopRequestHeaders", m);
		}

		e = req.getParameterNames();
		if (e.hasMoreElements())
		{
			Map<String, Object> m = new HashMap<String, Object>();
			while (e.hasMoreElements())
			{
				String name = (String) e.nextElement();
				m.put(name, req.getParameter(name));
			}
			rcontext.put("snoopRequestParamsSingle", m);
		}

		e = req.getParameterNames();
		if (e.hasMoreElements())
		{
			Map<String, Object> m = new HashMap<String, Object>();
			while (e.hasMoreElements())
			{
				String name = (String) e.nextElement();
				String[] vals = (String[]) req.getParameterValues(name);
				StringBuffer sb = new StringBuffer();
				if (vals != null)
				{
					sb.append(vals[0]);
					for (int i = 1; i < vals.length; i++)
						sb.append("           ").append(vals[i]);
				}
				m.put(name, sb.toString());
			}
			rcontext.put("snoopRequestParamsMulti", m);
		}

		e = req.getAttributeNames();
		if (e.hasMoreElements())
		{
			Map<String, Object> m = new HashMap<String, Object>();
			while (e.hasMoreElements())
			{
				String name = (String) e.nextElement();
				m.put(name, req.getAttribute(name));

			}
			rcontext.put("snoopRequestAttr", m);
		}
	}

	protected void doThrowableError(HttpServletRequest req, HttpServletResponse res,
			Throwable t)
	{
		ErrorReporter err = new ErrorReporter();
		err.report(req, res, t);
	}

	/*
	 * Produce a portlet like view with the navigation all at the top with
	 * implicit reset
	 */
	public PortalRenderContext includePortal(HttpServletRequest req,
			HttpServletResponse res, Session session, String siteId, String toolId,
			String toolContextPath, String prefix, boolean doPages, boolean resetTools,
			boolean includeSummary, boolean expandSite) throws ToolException, IOException
	{

		String errorMessage = null;

		// find the site, for visiting
		Site site = null;
		try
		{
			site = siteHelper.getSiteVisit(siteId);
		}
		catch (IdUnusedException e)
		{
			errorMessage = "Unable to find site: " + siteId;
			siteId = null;
			toolId = null;
		}
		catch (PermissionException e)
		{
			if (session.getUserId() == null)
			{
				errorMessage = "No permission for anynymous user to view site: " + siteId;
			}
			else
			{
				errorMessage = "No permission to view site: " + siteId;
			}
			siteId = null;
			toolId = null; // Tool needs the site and needs it to be visitable
		}

		// Get the Tool Placement
		ToolConfiguration placement = null;
		if (site != null && toolId != null)
		{
			placement = SiteService.findTool(toolId);
			if (placement == null)
			{
				errorMessage = "Unable to find tool placement " + toolId;
				toolId = null;
			}

			boolean thisTool = siteHelper.allowTool(site, placement);
			// System.out.println(" Allow Tool Display -" +
			// placement.getTitle() + " retval = " + thisTool);
			if (!thisTool)
			{
				errorMessage = "No permission to view tool placement " + toolId;
				toolId = null;
				placement = null;
			}
		}

		// Get the user's My WorkSpace and its ID
		Site myWorkspaceSite = siteHelper.getMyWorkspace(session);
		String myWorkspaceSiteId = null;
		if (myWorkspaceSite != null)
		{
			myWorkspaceSiteId = myWorkspaceSite.getId();
		}

		// form a context sensitive title
		String title = ServerConfigurationService.getString("ui.service");
		if (site != null)
		{
			title = title + ":" + site.getTitle();
			if (placement != null) title = title + " : " + placement.getTitle();
		}

		// start the response
		String siteType = null;
		String siteSkin = null;
		if (site != null)
		{
			siteType = calcSiteType(siteId);
			siteSkin = site.getSkin();
		}

		PortalRenderContext rcontext = startPageContext(siteType, title, siteSkin, req);

		// Make the top Url where the "top" url is
		String portalTopUrl = Web.serverUrl(req)
				+ ServerConfigurationService.getString("portalPath") + "/";
		if (prefix != null) portalTopUrl = portalTopUrl + prefix + "/";

		rcontext.put("portalTopUrl", portalTopUrl);
		rcontext.put("loggedIn", Boolean.valueOf(session.getUserId() != null));

		if (placement != null)
		{
			Map m = includeTool(res, req, placement);
			if (m != null) rcontext.put("currentPlacement", m);
		}

		boolean loggedIn = session.getUserId() != null;

		if (site != null)
		{
			Map m = convertSiteToMap(req, site, prefix, siteId, myWorkspaceSiteId,
					includeSummary,
					/* expandSite */true, resetTools, doPages, toolContextPath, loggedIn);
			if (m != null) rcontext.put("currentSite", m);
		}

		List mySites = siteHelper.getAllSites(req, session, true);
		List l = convertSitesToMaps(req, mySites, prefix, siteId, myWorkspaceSiteId,
				includeSummary, expandSite, resetTools, doPages, toolContextPath,
				loggedIn);
		rcontext.put("allSites", l);

		includeLogin(rcontext, req, session);
		includeBottom(rcontext);

		return rcontext;
	}

	public Map includeTool(HttpServletResponse res, HttpServletRequest req,
			ToolConfiguration placement) throws IOException
	{

		// find the tool registered for this
		ActiveTool tool = ActiveToolManager.getActiveTool(placement.getToolId());
		if (tool == null)
		{
			// doError(req, res, session);
			return null;
		}

		// FIXME: This does not look absolutely right,
		// this appears to say, reset all tools on the page since there
		// is no filtering of the tool that is bing reset, surely there
		// should be a check which tool is being reset, rather than all
		// tools on the page.
		// let the tool do some the work (include) (see note above)

		String toolUrl = ServerConfigurationService.getToolUrl() + "/"
				+ Web.escapeUrl(placement.getId()) + "/";
		String titleString = Web.escapeHtml(placement.getTitle());

		// Reset the tool state if requested
		if ("true".equals(req.getParameter(portalService.getResetStateParam()))
				|| "true".equals(portalService.getResetState()))
		{
			Session s = SessionManager.getCurrentSession();
			ToolSession ts = s.getToolSession(placement.getId());
			ts.clearAttributes();
		}

		// emit title information

		// for the reset button
		boolean showResetButton = !"false".equals(placement.getConfig().getProperty(
				Portal.TOOLCONFIG_SHOW_RESET_BUTTON));
		String resetActionUrl = PortalStringUtil.replaceFirst(toolUrl, "/tool/",
				"/tool-reset/")
				+ "?panel=Main";

		// for the help button
		// get the help document ID from the tool config (tool registration
		// usually).
		// The help document ID defaults to the tool ID
		boolean helpEnabledGlobally = ServerConfigurationService.getBoolean(
				"display.help.icon", true);
		boolean helpEnabledInTool = !"false".equals(placement.getConfig().getProperty(
				Portal.TOOLCONFIG_SHOW_HELP_BUTTON));
		boolean showHelpButton = helpEnabledGlobally && helpEnabledInTool;

		String helpActionUrl = "";
		if (showHelpButton)
		{
			String helpDocUrl = placement.getConfig().getProperty(
					Portal.TOOLCONFIG_HELP_DOCUMENT_URL);
			String helpDocId = placement.getConfig().getProperty(
					Portal.TOOLCONFIG_HELP_DOCUMENT_ID);
			if (helpDocUrl != null && helpDocUrl.length() > 0)
			{
				helpActionUrl = helpDocUrl;
			}
			else
			{
				if (helpDocId == null || helpDocId.length() == 0)
				{
					helpDocId = tool.getId();
				}
				helpActionUrl = ServerConfigurationService.getHelpUrl(helpDocId);
			}
		}

		Map<String, Object> toolMap = new HashMap<String, Object>();
		RenderResult result = ToolRenderService.render(placement, req, res,
				getServletContext());
		toolMap.put("toolRenderResult", result);
		toolMap.put("hasRenderResult", Boolean.valueOf(true));
		toolMap.put("toolUrl", toolUrl);
		toolMap
				.put("toolPlacementIDJS", Web
						.escapeJavascript("Main" + placement.getId()));
		toolMap.put("toolResetActionUrl", resetActionUrl);
		toolMap.put("toolTitle", titleString);
		toolMap.put("toolShowResetButton", Boolean.valueOf(showResetButton));
		toolMap.put("toolShowHelpButton", Boolean.valueOf(showHelpButton));
		toolMap.put("toolHelpActionUrl", helpActionUrl);
		return toolMap;
	}

	public List<Map> convertSitesToMaps(HttpServletRequest req, List mySites,
			String prefix, String currentSiteId, String myWorkspaceSiteId,
			boolean includeSummary, boolean expandSite, boolean resetTools,
			boolean doPages, String toolContextPath, boolean loggedIn)
	{
		List<Map> l = new ArrayList<Map>();
		boolean motdDone = false;
		for (Iterator i = mySites.iterator(); i.hasNext();)
		{
			Site s = (Site) i.next();

			Map m = convertSiteToMap(req, s, prefix, currentSiteId, myWorkspaceSiteId,
					includeSummary, expandSite, resetTools, doPages, toolContextPath,
					loggedIn);

			if (includeSummary && m.get("rssDescription") == null)
			{
				if (!motdDone)
				{
					siteHelper.summarizeTool(m, s, "sakai.motd");
					motdDone = true;
				}
				else
				{
					siteHelper.summarizeTool(m, s, "sakai.announcements");
				}

			}
			l.add(m);
		}
		return l;
	}

	public Map convertSiteToMap(HttpServletRequest req, Site s, String prefix,
			String currentSiteId, String myWorkspaceSiteId, boolean includeSummary,
			boolean expandSite, boolean resetTools, boolean doPages,
			String toolContextPath, boolean loggedIn)
	{
		if (s == null) return null;
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("isCurrentSite", Boolean.valueOf(currentSiteId != null
				&& s.getId().equals(currentSiteId)));
		m.put("isMyWorkspace", Boolean.valueOf(myWorkspaceSiteId != null
				&& s.getId().equals(myWorkspaceSiteId)));
		m.put("siteTitle", Web.escapeHtml(s.getTitle()));
		m.put("siteDescription", Web.escapeHtml(s.getDescription()));
		String siteUrl = Web.serverUrl(req)
				+ ServerConfigurationService.getString("portalPath") + "/";
		if (prefix != null) siteUrl = siteUrl + prefix + "/";
		siteUrl = siteUrl + Web.escapeUrl(siteHelper.getSiteEffectiveId(s));
		m.put("siteUrl", siteUrl);

		if (includeSummary)
		{
			siteHelper.summarizeTool(m, s, "sakai.announce");
		}
		if (expandSite)
		{
			Map pageMap = pageListToMap(req, loggedIn, s, /* SitePage */null,
					toolContextPath, prefix, doPages, resetTools, includeSummary);
			m.put("sitePages", pageMap);
		}

		return m;
	}

	/**
	 * Respond to navigation / access requests.
	 * 
	 * @param req
	 *        The servlet request.
	 * @param res
	 *        The servlet response.
	 * @throws javax.servlet.ServletException.
	 * @throws java.io.IOException.
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException
	{

		int stat = PortalHandler.NEXT;
		try
		{
			basicAuth.doLogin(req);
			if (!ToolRenderService.preprocess(req, res, getServletContext()))
			{
				return;
			}

			// get the Sakai session
			Session session = SessionManager.getCurrentSession();

			// recognize what to do from the path
			String option = req.getPathInfo();

			// if missing, set it to home or gateway
			if ((option == null) || ("/".equals(option)))
			{
				if (session.getUserId() == null)
				{
					option = "/site/" + ServerConfigurationService.getGatewaySiteId();
				}
				else
				{
					option = "/site/" + SiteService.getUserSiteId(session.getUserId());
				}
			}

			// get the parts (the first will be "")
			String[] parts = option.split("/");

			Map<String, PortalHandler> handlerMap = portalService.getHandlerMap(this);
			PortalHandler ph = handlerMap.get(parts[1]);
			if (ph != null)
			{
				stat = ph.doGet(parts, req, res, session);
			}
			if (stat == PortalHandler.NEXT)
			{

				List<PortalHandler> urlHandlers;
				for (Iterator<PortalHandler> i = handlerMap.values().iterator(); i
						.hasNext();)
				{
					ph = i.next();
					stat = ph.doGet(parts, req, res, session);
					// this should be
					if (stat != PortalHandler.NEXT)
					{
						break;
					}
				}
			}
			if (stat == PortalHandler.NEXT)
			{
				doError(req, res, session, Portal.ERROR_SITE);
			}

			/*
			 * // recognize and dispatch the 'tool' option: [1] = "tool", [2] = //
			 * placement id (of a site's tool placement), rest for the tool if
			 * ((parts.length > 2) && (parts[1].equals("tool"))) { // Resolve
			 * the placements of the form //
			 * /portal/tool/sakai.resources?sakai.site=~csev String
			 * toolPlacement = getPlacement(req, res, session, parts[2], false);
			 * if (toolPlacement == null) { return; } parts[2] = toolPlacement;
			 * doTool(req, res, session, parts[2], req.getContextPath() +
			 * req.getServletPath() + Web.makePath(parts, 1, 3), Web
			 * .makePath(parts, 3, parts.length)); } else if (enableDirect &&
			 * (parts.length > 2) && (parts[1].equals("directtool"))) { //
			 * Resolve the placements of the form //
			 * /portal/tool/sakai.resources?sakai.site=~csev String
			 * toolPlacement = getPlacement(req, res, session, parts[2], false);
			 * if (toolPlacement == null) { return; } parts[2] = toolPlacement;
			 * doDirectTool(req, res, session, parts[2], req.getContextPath() +
			 * req.getServletPath() + Web.makePath(parts, 1, 3),
			 * Web.makePath(parts, 3, parts.length)); } // These reset urls
			 * simply set a session value to indicate to reset // state and then
			 * redirect // This is necessary os that the URL is clean and we do
			 * not see // resets on refresh else
			 */
			/*
			 * if ((parts.length > 2) && (parts[1].equals("tool-reset"))) {
			 * String toolUrl = req.getContextPath() + "/tool" +
			 * Web.makePath(parts, 2, parts.length); // Make sure to add the
			 * parameters such as panel=Main String queryString =
			 * req.getQueryString(); if (queryString != null) { toolUrl =
			 * toolUrl + "?" + queryString; }
			 * portalService.setResetState("true"); resetDone = true;
			 * res.sendRedirect(toolUrl); } // recognize a dispatch the 'page'
			 * option (tools on a page) else
			 */
			/*
			 * if ((parts.length == 3) && (parts[1].equals("page"))) { //
			 * Resolve the placements of the form //
			 * /portal/page/sakai.resources?sakai.site=~csev String
			 * pagePlacement = getPlacement(req, res, session, parts[2], true);
			 * if (pagePlacement == null) { return; } parts[2] = pagePlacement;
			 * doPage(req, res, session, parts[2], req.getContextPath() +
			 * req.getServletPath()); }
			 */
			/*
			 * // recognize a dispatch the 'worksite' option (pages navigation + //
			 * tools on a page) else if ((parts.length >= 3) &&
			 * (parts[1].equals("worksite"))) { // recognize an optional
			 * page/pageid String pageId = null; if ((parts.length == 5) &&
			 * (parts[3].equals("page"))) { pageId = parts[4]; } doWorksite(req,
			 * res, session, parts[2], pageId, req.getContextPath() +
			 * req.getServletPath()); } // Implement the dense portlet-style
			 * portal else
			 */
			/*
			 * if ((parts.length >= 2) && (parts[1].equals("portlet"))) { //
			 * /portal/portlet/site-id String siteId = null; if (parts.length >=
			 * 3) { siteId = parts[2]; } // This is a pop-up page - it does
			 * exactly the same as /portal/page //
			 * /portal/portlet/site-id/page/page-id // 1 2 3 4 String pageId =
			 * null; if ((parts.length == 5) && (parts[3].equals("page"))) {
			 * doPage(req, res, session, parts[4], req.getContextPath() +
			 * req.getServletPath()); return; } // Tool resetting URL - clear
			 * state and forward to the real tool URL //
			 * /portal/portlet/site-id/tool-reset/toolId // 0 1 2 3 4 String
			 * toolId = null; if ((siteId != null) && (parts.length == 5) &&
			 * (parts[3].equals("tool-reset"))) { toolId = parts[4]; String
			 * toolUrl = req.getContextPath() + "/portlet/" + siteId + "/tool" +
			 * Web.makePath(parts, 4, parts.length); String queryString =
			 * req.getQueryString(); if (queryString != null) { toolUrl =
			 * toolUrl + "?" + queryString; }
			 * portalService.setResetState("true"); resetDone = true;
			 * res.sendRedirect(toolUrl); } // Tool after the reset //
			 * /portal/portlet/site-id/tool/toolId if ((parts.length == 5) &&
			 * (parts[3].equals("tool"))) { toolId = parts[4]; } String
			 * forceLogout = req.getParameter(PARAM_FORCE_LOGOUT); if
			 * ("yes".equalsIgnoreCase(forceLogout) ||
			 * "true".equalsIgnoreCase(forceLogout)) { doLogout(req, res,
			 * session, "/portlet"); return; } if (session.getUserId() == null) {
			 * String forceLogin = req.getParameter(PARAM_FORCE_LOGIN); if
			 * ("yes".equalsIgnoreCase(forceLogin) ||
			 * "true".equalsIgnoreCase(forceLogin)) { doLogin(req, res, session,
			 * req.getPathInfo(), false); return; } } PortalRenderContext
			 * rcontext = includePortal(req, res, session, siteId, toolId,
			 * req.getContextPath() + req.getServletPath(), "portlet", / *
			 * doPages * /false, /* resetTools * /true, / * includeSummary *
			 * /false, /* expandSite * /false); sendResponse(rcontext, res,
			 * "portlet", null); } // Implement the three forms of the rss
			 * portal else
			 */
			/*
			 * if ((parts.length >= 2) && (parts[1].equals("rss") ||
			 * parts[1].equals("atom") || parts[1].equals("opml"))) { if
			 * (parts[1].equals("atom")) { // /portal/rss/site-id String siteId =
			 * null; if (parts.length >= 3) { siteId = parts[2]; }
			 * PortalRenderContext rcontext = includePortal(req, res, session,
			 * siteId, /* toolId * /null, req.getContextPath() +
			 * req.getServletPath(), /* prefix * /"site", /* doPages * /true, /*
			 * resetTools * /false, /* includeSummary * /true, /* expandSite *
			 * /false); // sendResponse(rcontext, res, parts[1],
			 * "application/atom+xml"); sendResponse(rcontext, res, parts[1],
			 * "text/xml"); } else if (parts[1].equals("rss")) { //
			 * /portal/rss/site-id String siteId = null; if (parts.length >= 3) {
			 * siteId = parts[2]; } PortalRenderContext rcontext =
			 * includePortal(req, res, session, siteId, /* toolId * /null,
			 * req.getContextPath() + req.getServletPath(), /* prefix * /"site", /*
			 * doPages * /true, /* resetTools * /false, /* includeSummary *
			 * /true, /* expandSite * /false); sendResponse(rcontext, res,
			 * parts[1], "text/xml"); } else { // opml // /portal/rss/site-id
			 * String siteId = null; if (parts.length >= 3) { siteId = parts[2]; }
			 * PortalRenderContext rcontext = includePortal(req, res, session,
			 * siteId, /* toolId * /null, req.getContextPath() +
			 * req.getServletPath(), /* prefix * /"site", /* doPages * /true, /*
			 * resetTools * /false, /* includeSummary * /false, /* expandSite *
			 * /true); // sendResponse(rcontext, res, parts[1], "text/x-opml");
			 * sendResponse(rcontext, res, parts[1], "text/xml"); } } //
			 * recognize a dispatch the 'gallery' option (site tabs + pages //
			 * navigation + tools on a page) else
			 */
			/*
			 * if ((parts.length >= 2) && (parts[1].equals("gallery"))) { //
			 * recognize an optional page/pageid String pageId = null; if
			 * ((parts.length == 5) && (parts[3].equals("page"))) { pageId =
			 * parts[4]; } // site might be specified String siteId = null; if
			 * (parts.length >= 3) { siteId = parts[2]; } doGallery(req, res,
			 * session, siteId, pageId, req.getContextPath() +
			 * req.getServletPath()); } // recognize a dispatch the 'site'
			 * option (site logo and tabs + // pages navigation + tools on a
			 * page) else
			 */

			/*
			 * if ((parts.length >= 2) && (parts[1].equals("site"))) { //
			 * recognize an optional page/pageid String pageId = null; if
			 * ((parts.length == 5) && (parts[3].equals("page"))) { pageId =
			 * parts[4]; } // site might be specified String siteId = null; if
			 * (parts.length >= 3) { siteId = parts[2]; } doSite(req, res,
			 * session, siteId, pageId, req.getContextPath() +
			 * req.getServletPath()); } // recognize nav login else if
			 * ((parts.length == 3) && (parts[1].equals("nav_login"))) {
			 * doNavLogin(req, res, session, parts[2]); // recognize nav login
			 * for the gallery else if ((parts.length == 3) &&
			 * (parts[1].equals("nav_login_gallery"))) { doNavLoginGallery(req,
			 * res, session, parts[2]); } // recognize presence else if
			 * ((parts.length >= 3) && (parts[1].equals("presence"))) {
			 * doPresence(req, res, session, parts[2], req.getContextPath() +
			 * req.getServletPath() + Web.makePath(parts, 1, 3),
			 * Web.makePath(parts, 3, parts.length)); } // recognize help else
			 * if ((parts.length >= 2) && (parts[1].equals("help"))) {
			 * doHelp(req, res, session, req.getContextPath() +
			 * req.getServletPath() + Web.makePath(parts, 1, 2), Web.makePath(
			 * parts, 2, parts.length)); } // recognize and dispatch the 'login'
			 * option else if ((parts.length == 2) &&
			 * (parts[1].equals("relogin"))) { // Note: here we send a null
			 * path, meaning we will NOT set it as // a possible return path //
			 * we expect we are in the middle of a login screen processing, //
			 * and it's already set (user login button is "ulogin") -ggolden
			 * doLogin(req, res, session, null, false); } // recognize and
			 * dispatch the 'login' option else if ((parts.length == 2) &&
			 * (parts[1].equals("login"))) { doLogin(req, res, session, "",
			 * false); } // recognize and dispatch the 'login' options else if
			 * ((parts.length == 2) && ((parts[1].equals("xlogin")))) {
			 * doLogin(req, res, session, "", true); } // recognize and dispatch
			 * the 'login' option for gallery else if ((parts.length == 2) &&
			 * (parts[1].equals("login_gallery"))) { doLogin(req, res, session,
			 * "/gallery", false); } // recognize and dispatch the 'logout'
			 * option else if ((parts.length == 2) &&
			 * (parts[1].equals("logout"))) { doLogout(req, res, session, null); } //
			 * recognize and dispatch the 'logout' option for gallery else if
			 * ((parts.length == 2) && (parts[1].equals("logout_gallery"))) {
			 * doLogout(req, res, session, "/gallery"); } // recognize error
			 * done else if ((parts.length >= 2) &&
			 * (parts[1].equals("error-reported"))) { doErrorDone(req, res); }
			 * else if ((parts.length >= 2) && (parts[1].equals("styles"))) {
			 * doStatic(req, res, parts); } else if ((parts.length >= 2) &&
			 * (parts[1].equals("scripts"))) { doStatic(req, res, parts); } //
			 * handle an unrecognized request else { doError(req, res, session,
			 * ERROR_SITE); }
			 */
		}
		catch (Throwable t)
		{
			doThrowableError(req, res, t);
		}

		// Make sure to clear any reset State at the end of the request unless
		// we *just* set it
		if (stat != PortalHandler.RESET_DONE)
		{
			portalService.setResetState(null);
		}

	}

	public void doLogin(HttpServletRequest req, HttpServletResponse res, Session session,
			String returnPath, boolean skipContainer) throws ToolException
	{
		try
		{
			if (basicAuth.doAuth(req, res))
			{
				// System.err.println("BASIC Auth Request Sent to the Browser
				// ");
				return;
			}
		}
		catch (IOException ioex)
		{
			throw new ToolException(ioex);

		}

		// setup for the helper if needed (Note: in session, not tool session,
		// special for Login helper)
		// Note: always set this if we are passed in a return path... a blank
		// return path is valid... to clean up from
		// possible abandened previous login attempt -ggolden
		if (returnPath != null)
		{
			// where to go after
			session.setAttribute(Tool.HELPER_DONE_URL, Web.returnUrl(req, returnPath));
		}

		ActiveTool tool = ActiveToolManager.getActiveTool("sakai.login");

		// to skip container auth for this one, forcing things to be handled
		// internaly, set the "extreme" login path
		String loginPath = (skipContainer ? "/xlogin" : "/relogin");

		String context = req.getContextPath() + req.getServletPath() + loginPath;
		tool.help(req, res, context, loginPath);
	}

	/**
	 * Process a logout
	 * 
	 * @param req
	 *        Request object
	 * @param res
	 *        Response object
	 * @param session
	 *        Current session
	 * @param returnPath
	 *        if not null, the path to use for the end-user browser redirect
	 *        after the logout is complete. Leave null to use the configured
	 *        logged out URL.
	 * @throws IOException
	 */
	public void doLogout(HttpServletRequest req, HttpServletResponse res,
			Session session, String returnPath) throws ToolException
	{
		// where to go after
		if (returnPath == null)
		{
			// if no path, use the configured logged out URL
			String loggedOutUrl = ServerConfigurationService.getLoggedOutUrl();
			session.setAttribute(Tool.HELPER_DONE_URL, loggedOutUrl);
		}
		else
		{
			// if we have a path, use a return based on the request and this
			// path
			// Note: this is currently used only as "/gallery"
			// - we should really add a
			// ServerConfigurationService.getGalleryLoggedOutUrl()
			// and change the returnPath to a normal/gallery indicator -ggolden
			String loggedOutUrl = Web.returnUrl(req, returnPath);
			session.setAttribute(Tool.HELPER_DONE_URL, loggedOutUrl);
		}

		ActiveTool tool = ActiveToolManager.getActiveTool("sakai.login");
		String context = req.getContextPath() + req.getServletPath() + "/logout";
		tool.help(req, res, context, "/logout");
	}

	public PortalRenderContext startPageContext(String siteType, String title,
			String skin, HttpServletRequest request)
	{
		PortalRenderEngine rengine = portalService
				.getRenderEngine(portalContext, request);
		PortalRenderContext rcontext = rengine.newRenderContext(request);

		if (skin == null)
		{
			skin = ServerConfigurationService.getString("skin.default");
		}
		String skinRepo = ServerConfigurationService.getString("skin.repo");

		rcontext.put("pageSkinRepo", skinRepo);
		rcontext.put("pageSkin", skin);
		rcontext.put("pageTitle", Web.escapeHtml(title));
		rcontext.put("pageScriptPath", getScriptPath());
		rcontext.put("pageTop", Boolean.valueOf(true));
		rcontext.put("rloader", rloader);
		// rcontext.put("sitHelp", Web.escapeHtml(rb.getString("sit_help")));
		// rcontext.put("sitReset", Web.escapeHtml(rb.getString("sit_reset")));

		if (siteType != null && siteType.length() > 0)
		{
			siteType = "class=\"" + siteType + "\"";
		}
		else
		{
			siteType = "";
		}
		rcontext.put("pageSiteType", siteType);
		rcontext.put("toolParamResetState", portalService.getResetStateParam());

		return rcontext;
	}

	/**
	 * Respond to data posting requests.
	 * 
	 * @param req
	 *        The servlet request.
	 * @param res
	 *        The servlet response.
	 * @throws ServletException
	 * @throws IOException
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException
	{
		int stat = PortalHandler.NEXT;
		try
		{
			basicAuth.doLogin(req);
			if (!ToolRenderService.preprocess(req, res, getServletContext()))
			{
				System.err.println("POST FAILED, REDIRECT ?");
				return;
			}
			// get the Sakai session
			Session session = SessionManager.getCurrentSession();

			// recognize what to do from the path
			String option = req.getPathInfo();

			// if missing, we have a stray post
			if ((option == null) || ("/".equals(option)))
			{
				doError(req, res, session, ERROR_SITE);
				return;
			}

			// get the parts (the first will be "")
			String[] parts = option.split("/");

			Map<String, PortalHandler> handlerMap = portalService.getHandlerMap(this);

			PortalHandler ph = handlerMap.get(parts[1]);
			if (ph != null)
			{
				stat = ph.doPost(parts, req, res, session);
			}
			if (stat == PortalHandler.NEXT)
			{

				List<PortalHandler> urlHandlers;
				for (Iterator<PortalHandler> i = handlerMap.values().iterator(); i
						.hasNext();)
				{
					ph = i.next();
					stat = ph.doPost(parts, req, res, session);
					// this should be
					if (stat != PortalHandler.NEXT)
					{
						break;
					}
				}
			}
			if (stat == PortalHandler.NEXT)
			{
				doError(req, res, session, Portal.ERROR_SITE);
			}

			/*
			 * // recognize and dispatch the 'tool' option: [1] = "tool", [2] = //
			 * placement id (of a site's tool placement), rest for the tool if
			 * ((parts.length > 2) && (parts[1].equals("tool"))) { doTool(req,
			 * res, session, parts[2], req.getContextPath() +
			 * req.getServletPath() + Web.makePath(parts, 1, 3), Web
			 * .makePath(parts, 3, parts.length)); } else if (enableDirect &&
			 * (parts.length > 2) && (parts[1].equals("directtool"))) { //
			 * Resolve the placements of the form //
			 * /portal/tool/sakai.resources?sakai.site=~csev String
			 * toolPlacement = getPlacement(req, res, session, parts[2], false);
			 * if (toolPlacement == null) { return; } parts[2] = toolPlacement;
			 * doDirectTool(req, res, session, parts[2], req.getContextPath() +
			 * req.getServletPath() + Web.makePath(parts, 1, 3),
			 * Web.makePath(parts, 3, parts.length)); /** Title frames were no
			 * longer used in 2.3 and are not supported in 2.4 so we emit a WARN
			 * message here to help people with derived classes figure out the
			 * new way. / // TODO: Remove after 2.4 } else if ((parts.length >
			 * 2) && (parts[1].equals("title"))) { M_log.warn("The /title/ form
			 * of portal URLs is no longer supported in Sakai 2.4 and later"); } //
			 * recognize and dispatch the 'login' options else if ((parts.length ==
			 * 2) && ((parts[1].equals("login") || (parts[1].equals("xlogin")) ||
			 * (parts[1].equals("relogin"))))) { postLogin(req, res, session,
			 * parts[1]); } // recognize help { doHelp(req, res, session,
			 * req.getContextPath() + req.getServletPath() + Web.makePath(parts,
			 * 1, 2), Web.makePath( parts, 2, parts.length)); } // recognize
			 * error feedback else if ((parts.length >= 2) &&
			 * (parts[1].equals("error-report"))) { doErrorReport(req, res); } //
			 * handle an unrecognized request else { doError(req, res, session,
			 * ERROR_SITE); }
			 */
		}
		catch (Throwable t)
		{
			doThrowableError(req, res, t);
		}
	}

	protected void doErrorReport(HttpServletRequest req, HttpServletResponse res)
			throws ToolException, IOException
	{
		setupForward(req, res, null, null);

		ErrorReporter err = new ErrorReporter();
		err.postResponse(req, res);
	}

	// Checks to see which form of tool or page placement we have. The normal
	// placement is
	// a GUID. However when the parameter sakai.site is added to the request,
	// the placement
	// can be of the form sakai.resources. This routine determines which form of
	// the
	// placement id, and if this is the second type, performs the lookup and
	// returns the
	// GUID of the placement. If we cannot resolve the pllacement, we simply
	// return
	// the passed in placement ID. If we cannot visit the site, we send the user
	// to login
	// processing and return null to the caller.

	public String getPlacement(HttpServletRequest req, HttpServletResponse res,
			Session session, String placementId, boolean doPage) throws ToolException
	{

		String siteId = req.getParameter(PARAM_SAKAI_SITE);
		if (siteId == null) return placementId; // Standard placement

		// find the site, for visiting
		// Sites like the !gateway site allow visits by anonymous
		Site site = null;
		try
		{
			site = SiteService.getSiteVisit(siteId);
		}
		catch (IdUnusedException e)
		{
			return placementId; // cannot resolve placement
		}
		catch (PermissionException e)
		{
			// If we are not logged in, try again after we log in, otherwise
			// punt
			if (session.getUserId() == null)
			{
				doLogin(req, res, session, req.getPathInfo() + "?sakai.site="
						+ res.encodeURL(siteId), false);
				return null;
			}
			return placementId; // cannot resolve placement
		}

		if (site == null) return placementId;
		ToolConfiguration toolConfig = site.getToolForCommonId(placementId);
		if (toolConfig == null) return placementId;

		if (doPage)
		{
			return toolConfig.getPageId();
		}
		else
		{
			return toolConfig.getId();
		}

	}

	public void setupForward(HttpServletRequest req, HttpServletResponse res,
			Placement p, String skin) throws ToolException
	{
		// setup html information that the tool might need (skin, body on load,
		// js includes, etc).
		if (skin == null || skin.length() == 0)
			skin = ServerConfigurationService.getString("skin.default");
		String skinRepo = ServerConfigurationService.getString("skin.repo");
		String headCssToolBase = "<link href=\""
				+ skinRepo
				+ "/tool_base.css\" type=\"text/css\" rel=\"stylesheet\" media=\"all\" />\n";
		String headCssToolSkin = "<link href=\"" + skinRepo + "/" + skin
				+ "/tool.css\" type=\"text/css\" rel=\"stylesheet\" media=\"all\" />\n";
		String headCss = headCssToolBase + headCssToolSkin;
		String headJs = "<script type=\"text/javascript\" language=\"JavaScript\" src=\"/library/js/headscripts.js\"></script>\n";
		String head = headCss + headJs;
		StringBuffer bodyonload = new StringBuffer();
		if (p != null)
		{
			String element = Web.escapeJavascript("Main" + p.getId());
			bodyonload.append("setMainFrameHeight('" + element + "');");
		}
		bodyonload.append("setFocus(focus_path);");

		// to force all non-legacy tools to use the standard css
		// to help in transition (needs corresponding entry in properties)
		// if
		// ("true".equals(ServerConfigurationService.getString("skin.force")))
		// {
		// headJs = headJs + headCss;
		// }

		req.setAttribute("sakai.html.head", head);
		req.setAttribute("sakai.html.head.css", headCss);
		req.setAttribute("sakai.html.head.css.base", headCssToolBase);
		req.setAttribute("sakai.html.head.css.skin", headCssToolSkin);
		req.setAttribute("sakai.html.head.js", headJs);
		req.setAttribute("sakai.html.body.onload", bodyonload.toString());

		portalService.getRenderEngine(portalContext, req).setupForward(req, res, p, skin);
	}

	/**
	 * Forward to the tool - but first setup JavaScript/CSS etc that the tool
	 * will render
	 */
	public void forwardTool(ActiveTool tool, HttpServletRequest req,
			HttpServletResponse res, Placement p, String skin, String toolContextPath,
			String toolPathInfo) throws ToolException
	{

		// if there is a stored request state, and path, extract that from the
		// session and reinstance it

		// let the tool do the the work (forward)
		if (enableDirect)
		{
			StoredState ss = portalService.getStoredState();
			if (ss == null || !toolContextPath.equals(ss.getToolContextPath()))
			{
				setupForward(req, res, p, skin);
				req.setAttribute(ToolURL.MANAGER, new ToolURLManagerImpl(res));
				tool.forward(req, res, p, toolContextPath, toolPathInfo);
			}
			else
			{
				HttpServletRequest sreq = ss.getRequest(req);
				Placement splacement = ss.getPlacement();
				String stoolContext = ss.getToolContextPath();
				String stoolPathInfo = ss.getToolPathInfo();
				ActiveTool stool = ActiveToolManager.getActiveTool(p.getToolId());
				String sskin = ss.getSkin();
				setupForward(sreq, res, splacement, sskin);
				req.setAttribute(ToolURL.MANAGER, new ToolURLManagerImpl(res));
				stool.forward(sreq, res, splacement, stoolContext, stoolPathInfo);
				portalService.setStoredState(null);
			}
		}
		else
		{
			setupForward(req, res, p, skin);
			req.setAttribute(ToolURL.MANAGER, new ToolURLManagerImpl(res));
			tool.forward(req, res, p, toolContextPath, toolPathInfo);
		}

	}

	public void forwardPortal(ActiveTool tool, HttpServletRequest req,
			HttpServletResponse res, ToolConfiguration p, String skin,
			String toolContextPath, String toolPathInfo) throws ToolException,
			IOException
	{

		// if there is a stored request state, and path, extract that from the
		// session and reinstance it

		// generate the forward to the tool page placement
		String portalPlacementUrl = "/portal" + getPortalPageUrl(p);
		res.sendRedirect(portalPlacementUrl);
		return;

	}

	public String getPortalPageUrl(ToolConfiguration p)
	{
		return "/site/" + p.getSiteId() + "/page/" + p.getPageId();
	}

	protected String getScriptPath()
	{
		String libPath = "/library";
		return libPath + "/js/";
	}

	/**
	 * Access the Servlet's information display.
	 * 
	 * @return servlet information.
	 */
	public String getServletInfo()
	{
		return "Sakai Charon Portal";
	}

	public void includeBottom(PortalRenderContext rcontext)
	{
		if (rcontext.uses(INCLUDE_BOTTOM))
		{
			String copyright = ServerConfigurationService
					.getString("bottom.copyrighttext");
			String service = ServerConfigurationService.getString("ui.service", "Sakai");
			String serviceVersion = ServerConfigurationService.getString(
					"version.service", "?");
			String sakaiVersion = ServerConfigurationService.getString("version.sakai",
					"?");
			String server = ServerConfigurationService.getServerId();
			String[] bottomNav = ServerConfigurationService.getStrings("bottomnav");
			String[] poweredByUrl = ServerConfigurationService.getStrings("powered.url");
			String[] poweredByImage = ServerConfigurationService
					.getStrings("powered.img");
			String[] poweredByAltText = ServerConfigurationService
					.getStrings("powered.alt");

			{
				List<Object> l = new ArrayList<Object>();
				if ((bottomNav != null) && (bottomNav.length > 0))
				{
					for (int i = 0; i < bottomNav.length; i++)
					{
						l.add(bottomNav[i]);
					}
				}
				rcontext.put("bottomNav", l);
			}

			// rcontext.put("bottomNavSitNewWindow",
			// Web.escapeHtml(rb.getString("site_newwindow")));

			if ((poweredByUrl != null) && (poweredByImage != null)
					&& (poweredByAltText != null)
					&& (poweredByUrl.length == poweredByImage.length)
					&& (poweredByUrl.length == poweredByAltText.length))
			{
				{
					List<Object> l = new ArrayList<Object>();
					for (int i = 0; i < poweredByUrl.length; i++)
					{
						Map<String, Object> m = new HashMap<String, Object>();
						m.put("poweredByUrl", poweredByUrl[i]);
						m.put("poweredByImage", poweredByImage[i]);
						m.put("poweredByAltText", poweredByAltText[i]);
						l.add(m);
					}
					rcontext.put("bottomNavPoweredBy", l);

				}
			}
			else
			{
				List<Object> l = new ArrayList<Object>();
				Map<String, Object> m = new HashMap<String, Object>();
				m.put("poweredByUrl", "http://sakaiproject.org");
				m.put("poweredByImage", "/library/image/sakai_powered.gif");
				m.put("poweredByAltText", "Powered by Sakai");
				l.add(m);
				rcontext.put("bottomNavPoweredBy", l);
			}

			rcontext.put("bottomNavService", service);
			rcontext.put("bottomNavCopyright", copyright);
			rcontext.put("bottomNavServiceVersion", serviceVersion);
			rcontext.put("bottomNavSakaiVersion", sakaiVersion);
			rcontext.put("bottomNavServer", server);
		}
	}

	public void includeLogin(PortalRenderContext rcontext, HttpServletRequest req,
			Session session)
	{
		if (rcontext.uses(INCLUDE_LOGIN))
		{

			// for the main login/out link
			String logInOutUrl = Web.serverUrl(req);
			String message = null;
			String image1 = null;

			// for a possible second link
			String logInOutUrl2 = null;
			String message2 = null;
			String image2 = null;

			// check for the top.login (where the login fields are present
			// instead
			// of a login link, but ignore it if container.login is set
			boolean topLogin = Boolean.TRUE.toString().equalsIgnoreCase(
					ServerConfigurationService.getString("top.login"));
			boolean containerLogin = Boolean.TRUE.toString().equalsIgnoreCase(
					ServerConfigurationService.getString("container.login"));
			if (containerLogin) topLogin = false;

			// if not logged in they get login
			if (session.getUserId() == null)
			{
				// we don't need any of this if we are doing top login
				if (!topLogin)
				{
					logInOutUrl += ServerConfigurationService.getString("portalPath")
							+ "/login";

					// let the login url be overridden by configuration
					String overrideLoginUrl = StringUtil
							.trimToNull(ServerConfigurationService.getString("login.url"));
					if (overrideLoginUrl != null) logInOutUrl = overrideLoginUrl;

					// check for a login text override
					message = StringUtil.trimToNull(ServerConfigurationService
							.getString("login.text"));
					if (message == null) message = rloader.getString("log.login");

					// check for an image for the login
					image1 = StringUtil.trimToNull(ServerConfigurationService
							.getString("login.icon"));

					// check for a possible second, xlogin link
					if (Boolean.TRUE.toString().equalsIgnoreCase(
							ServerConfigurationService.getString("xlogin.enabled")))
					{
						// get the text and image as configured
						message2 = StringUtil.trimToNull(ServerConfigurationService
								.getString("xlogin.text"));
						image2 = StringUtil.trimToNull(ServerConfigurationService
								.getString("xlogin.icon"));
						logInOutUrl2 = ServerConfigurationService.getString("portalPath")
								+ "/xlogin";
					}
				}
			}

			// if logged in they get logout
			else
			{
				logInOutUrl += ServerConfigurationService.getString("portalPath")
						+ "/logout";

				// check for a logout text override
				message = StringUtil.trimToNull(ServerConfigurationService
						.getString("logout.text"));
				if (message == null) message = rloader.getString("sit_log");

				// check for an image for the logout
				image1 = StringUtil.trimToNull(ServerConfigurationService
						.getString("logout.icon"));

				// since we are doing logout, cancel top.login
				topLogin = false;
			}
			rcontext.put("loginTopLogin", Boolean.valueOf(topLogin));

			if (!topLogin)
			{

				rcontext.put("loginLogInOutUrl", logInOutUrl);
				rcontext.put("loginMessage", message);
				rcontext.put("loginImage1", image1);
				rcontext.put("image1HasImage1", Boolean.valueOf(image1 != null));
				rcontext.put("loginLogInOutUrl2", logInOutUrl2);
				rcontext.put("loginHasLogInOutUrl2", Boolean
						.valueOf(logInOutUrl2 != null));
				rcontext.put("loginMessage2", message2);
				rcontext.put("loginImage2", image2);
				rcontext.put("image1HasImage2", Boolean.valueOf(image2 != null));
				// put out the links version

				// else put out the fields that will send to the login interface
			}
			else
			{
				// find the login tool
				Tool loginTool = ToolManager.getTool("sakai.login");
				String eidWording = null;
				String pwWording = null;
				eidWording = StringUtil.trimToNull(rloader.getString("log.userid"));
				pwWording = StringUtil.trimToNull(rloader.getString("log.pass"));

				if (eidWording == null) eidWording = "eid";
				if (pwWording == null) pwWording = "pw";
				String loginWording = rloader.getString("log.login");

				rcontext.put("loginPortalPath", ServerConfigurationService
						.getString("portalPath"));
				rcontext.put("loginEidWording", eidWording);
				rcontext.put("loginPwWording", pwWording);
				rcontext.put("loginWording", loginWording);

				// setup for the redirect after login
				session.setAttribute(Tool.HELPER_DONE_URL, ServerConfigurationService
						.getPortalUrl());
			}
		}
	}

	/*
	 * Produce a page and/or a tool list doPage = true is best for the
	 * tabs-based portal and for RSS - these think in terms of pages doPage =
	 * false is best for the portlet-style - it unrolls all of the tools unless
	 * a page is marked as a popup. If the page is a popup - it is left a page
	 * and marked as such. restTools = true - generate resetting tool URLs.
	 */

	// TODO: Refactor code in other functions to use this code rather than doing
	// it inline
	/*
	 * Produce a page and/or a tool list doPage = true is best for the
	 * tabs-based portal and for RSS - these think in terms of pages doPage =
	 * false is best for the portlet-style - it unrolls all of the tools unless
	 * a page is marked as a popup. If the page is a popup - it is left a page
	 * and marked as such. restTools = true - generate resetting tool URLs.
	 */

	// TODO: Refactor code in other functions to use this code rather than doing
	// it inline
	protected Map pageListToMap(HttpServletRequest req, boolean loggedIn, Site site,
			SitePage page, String toolContextPath, String portalPrefix, boolean doPages,
			boolean resetTools, boolean includeSummary)
	{

		Map<String, Object> theMap = new HashMap<String, Object>();

		String pageUrl = Web.returnUrl(req, "/" + portalPrefix + "/"
				+ Web.escapeUrl(siteHelper.getSiteEffectiveId(site)) + "/page/");
		String toolUrl = Web.returnUrl(req, "/" + portalPrefix + "/"
				+ Web.escapeUrl(siteHelper.getSiteEffectiveId(site)));
		if (resetTools)
		{
			toolUrl = toolUrl + "/tool-reset/";
		}
		else
		{
			toolUrl = toolUrl + "/tool/";
		}

		String pagePopupUrl = Web.returnUrl(req, "/page/");
		boolean showHelp = ServerConfigurationService.getBoolean("display.help.menu",
				true);
		String iconUrl = site.getIconUrlFull();
		boolean published = site.isPublished();
		String type = site.getType();

		theMap.put("pageNavPublished", Boolean.valueOf(published));
		theMap.put("pageNavType", type);
		theMap.put("pageNavIconUrl", iconUrl);
		// theMap.put("pageNavSitToolsHead",
		// Web.escapeHtml(rb.getString("sit_toolshead")));

		// order the pages based on their tools and the tool order for the
		// site type
		List pages = site.getOrderedPages();

		List<Map> l = new ArrayList<Map>();
		for (Iterator i = pages.iterator(); i.hasNext();)
		{

			SitePage p = (SitePage) i.next();
			// check if current user has permission to see page
			// we will draw page button if it have permission to see at least
			List pTools = p.getTools();
			Iterator iPt = pTools.iterator();
			String toolsOnPage = null;

			boolean allowPage = false;
			while (iPt.hasNext())
			{
				ToolConfiguration placement = (ToolConfiguration) iPt.next();

				boolean thisTool = siteHelper.allowTool(site, placement);
				// System.out.println(" Allow Tool -" + placement.getTitle() + "
				// retval = " + thisTool + " page=" + allowPage);
				if (thisTool)
				{
					allowPage = true;
					if (toolsOnPage == null)
					{
						toolsOnPage = placement.getToolId();
					}
					else
					{
						toolsOnPage = toolsOnPage + ":" + placement.getToolId();
					}
				}
			}

			// Do not include pages we are not supposed to see
			if (!allowPage) continue;

			boolean current = (page != null && p.getId().equals(page.getId()) && !p
					.isPopUp());
			String pagerefUrl = pageUrl + Web.escapeUrl(p.getId());

			if (doPages || p.isPopUp())
			{
				Map<String, Object> m = new HashMap<String, Object>();
				m.put("isPage", Boolean.valueOf(true));
				m.put("current", Boolean.valueOf(current));
				m.put("ispopup", Boolean.valueOf(p.isPopUp()));
				m.put("pagePopupUrl", pagePopupUrl);
				m.put("pageTitle", Web.escapeHtml(p.getTitle()));
				m.put("jsPageTitle", Web.escapeJavascript(p.getTitle()));
				m.put("pageId", Web.escapeUrl(p.getId()));
				m.put("jsPageId", Web.escapeJavascript(p.getId()));
				m.put("pageRefUrl", pagerefUrl);
				if (toolsOnPage != null) m.put("toolsOnPage", toolsOnPage);
				if (includeSummary) siteHelper.summarizePage(m, site, p);
				l.add(m);
				continue;
			}

			// Loop through the tools again and Unroll the tools
			iPt = pTools.iterator();

			while (iPt.hasNext())
			{
				ToolConfiguration placement = (ToolConfiguration) iPt.next();

				String toolrefUrl = toolUrl + Web.escapeUrl(placement.getId());

				Map<String, Object> m = new HashMap<String, Object>();
				m.put("isPage", Boolean.valueOf(false));
				m.put("toolId", Web.escapeUrl(placement.getId()));
				m.put("jsToolId", Web.escapeJavascript(placement.getId()));
				m.put("toolRegistryId", placement.getToolId());
				m.put("toolTitle", Web.escapeHtml(placement.getTitle()));
				m.put("jsToolTitle", Web.escapeJavascript(placement.getTitle()));
				m.put("toolrefUrl", toolrefUrl);
				l.add(m);
			}

		}
		theMap.put("pageNavTools", l);

		String helpUrl = ServerConfigurationService.getHelpUrl(null);
		theMap.put("pageNavShowHelp", Boolean.valueOf(showHelp));
		theMap.put("pageNavHelpUrl", helpUrl);

		// theMap.put("pageNavSitContentshead",
		// Web.escapeHtml(rb.getString("sit_contentshead")));

		// Handle Presense
		boolean showPresence = ServerConfigurationService.getBoolean(
				"display.users.present", true);
		String presenceUrl = Web.returnUrl(req, "/presence/"
				+ Web.escapeUrl(site.getId()));

		// theMap.put("pageNavSitPresenceTitle",
		// Web.escapeHtml(rb.getString("sit_presencetitle")));
		// theMap.put("pageNavSitPresenceFrameTitle",
		// Web.escapeHtml(rb.getString("sit_presenceiframetit")));
		theMap.put("pageNavShowPresenceLoggedIn", Boolean.valueOf(showPresence
				&& loggedIn));
		theMap.put("pageNavPresenceUrl", presenceUrl);

		return theMap;
	}

	public void includeWorksite(PortalRenderContext rcontext, HttpServletResponse res,
			HttpServletRequest req, Session session, Site site, SitePage page,
			String toolContextPath, String portalPrefix) throws IOException
	{
		worksiteHandler.includeWorksite(rcontext, res, req, session, site, page,
				toolContextPath, portalPrefix);
	}

	/**
	 * Initialize the servlet.
	 * 
	 * @param config
	 *        The servlet config.
	 * @throws ServletException
	 */
	public void init(ServletConfig config) throws ServletException
	{
		super.init(config);

		portalContext = config.getInitParameter("portal.context");
		if (portalContext == null || portalContext.length() == 0)
		{
			portalContext = DEFAULT_PORTAL_CONTEXT;
		}
		portalService = org.sakaiproject.portal.api.cover.PortalService.getInstance();
		M_log.info("init()");

		basicAuth = new BasicAuth();
		basicAuth.init();

		enableDirect = portalService.isEnableDirect();
		// do this before adding handlers to prevent handlers registering 2
		// times.
		// if the handlers were already there they will be re-registered,
		// but when they are added again, they will be replaced.
		// warning messages will appear, but the end state will be the same.
		portalService.addPortal(this);

		galleryHandler = new GalleryHandler();
		worksiteHandler = new WorksiteHandler();
		siteHandler = new SiteHandler();

		addHandler(siteHandler);

		addHandler(new ToolHandler());
		addHandler(new ToolResetHandler());
		addHandler(new PageHandler());
		addHandler(worksiteHandler);
		addHandler(new RssHandler());
		addHandler(new PortletHandler());
		addHandler(new AtomHandler());
		addHandler(new OpmlHandler());
		addHandler(galleryHandler);
		addHandler(new NavLoginHandler());
		addHandler(new NavLoginGalleryHandler());
		addHandler(new PresenceHandler());
		addHandler(new HelpHandler());
		addHandler(new ReLoginHandler());
		addHandler(new LoginHandler());
		addHandler(new XLoginHandler());
		addHandler(new LoginGalleryHandler());
		addHandler(new LogoutHandler());
		addHandler(new LogoutGalleryHandler());
		addHandler(new ErrorDoneHandler());
		addHandler(new StaticStylesHandler());
		addHandler(new StaticScriptsHandler());
		addHandler(new DirectToolHandler());

	}

	/**
	 * Register a handler for a URL stub
	 * 
	 * @param handler
	 */
	private void addHandler(PortalHandler handler)
	{
		portalService.addHandler(this, handler);
	}

	private void removeHandler(String urlFragment)
	{
		portalService.removeHandler(this, urlFragment);
	}

	/**
	 * Send the POST request to login
	 * 
	 * @param req
	 * @param res
	 * @param session
	 * @throws IOException
	 */
	protected void postLogin(HttpServletRequest req, HttpServletResponse res,
			Session session, String loginPath) throws ToolException
	{
		ActiveTool tool = ActiveToolManager.getActiveTool("sakai.login");
		String context = req.getContextPath() + req.getServletPath() + "/" + loginPath;
		tool.help(req, res, context, "/" + loginPath);
	}

	/**
	 * Output some session information
	 * 
	 * @param rcontext
	 *        The print writer
	 * @param html
	 *        If true, output in HTML, else in text.
	 */
	protected void showSession(PortalRenderContext rcontext, boolean html)
	{
		// get the current user session information
		Session s = SessionManager.getCurrentSession();
		rcontext.put("sessionSession", s);
		ToolSession ts = SessionManager.getCurrentToolSession();
		rcontext.put("sessionToolSession", ts);
	}

	public void sendResponse(PortalRenderContext rcontext, HttpServletResponse res,
			String template, String contentType) throws IOException
	{
		// headers
		if (contentType == null)
		{
			res.setContentType("text/html; charset=UTF-8");
		}
		else
		{
			res.setContentType(contentType);
		}
		res.addDateHeader("Expires", System.currentTimeMillis()
				- (1000L * 60L * 60L * 24L * 365L));
		res.addDateHeader("Last-Modified", System.currentTimeMillis());
		res
				.addHeader("Cache-Control",
						"no-store, no-cache, must-revalidate, max-age=0, post-check=0, pre-check=0");
		res.addHeader("Pragma", "no-cache");

		// get the writer
		PrintWriter out = res.getWriter();

		try
		{
			PortalRenderEngine rengine = rcontext.getRenderEngine();
			rengine.render(template, rcontext, out);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to render template ", e);
		}

	}

	/**
	 * Returns the type ("course", "project", "workspace", "mySpecialSiteType",
	 * etc) of the given site; special handling of returning "workspace" for
	 * user workspace sites. This method is tightly coupled to site skinning.
	 */
	public String calcSiteType(String siteId)
	{
		String siteType = null;
		if (siteId != null && siteId.length() != 0)
		{
			if (SiteService.isUserSite(siteId))
			{
				siteType = "workspace";
			}
			else
			{
				try
				{
					siteType = SiteService.getSite(siteId).getType();
				}
				catch (IdUnusedException ex)
				{
					// ignore, the site wasn't found
				}
			}
		}

		if (siteType != null && siteType.trim().length() == 0) siteType = null;
		return siteType;
	}

	private void logXEntry()
	{
		Exception e = new Exception();
		StackTraceElement se = e.getStackTrace()[1];
		M_log.info("Log marker " + se.getMethodName() + ":" + se.getFileName() + ":"
				+ se.getLineNumber());
	}

	/**
	 * Find the site in the list that has this id - return the position.
	 * 
	 * @param value
	 *        The site id to find.
	 * @param siteList
	 *        The list of Site objects.
	 * @return The index position in siteList of the site with site id = value,
	 *         or -1 if not found.
	 */
	/*
	 * protected int indexOf(String value, List siteList) { for (int i = 0; i <
	 * siteList.size(); i++) { Site site = (Site) siteList.get(i); if
	 * (site.equals(value)) { return i; } } return -1; }
	 */

	/**
	 * Check for any just expired sessions and redirect
	 * 
	 * @return true if we redirected, false if not
	 */
	public boolean redirectIfLoggedOut(HttpServletResponse res) throws IOException
	{
		// if we are in a newly created session where we had an invalid
		// (presumed timed out) session in the request,
		// send script to cause a sakai top level redirect
		if (ThreadLocalManager.get(SessionManager.CURRENT_INVALID_SESSION) != null)
		{
			String loggedOutUrl = ServerConfigurationService.getLoggedOutUrl();
			sendPortalRedirect(res, loggedOutUrl);
			return true;
		}

		return false;
	}

	/**
	 * Send a redirect so our Portal window ends up at the url, via javascript.
	 * 
	 * @param url
	 *        The redirect url
	 */
	protected void sendPortalRedirect(HttpServletResponse res, String url)
			throws IOException
	{
		PortalRenderContext rcontext = startPageContext("", null, null, null);
		rcontext.put("redirectUrl", url);
		sendResponse(rcontext, res, "portal-redirect", null);
	}

	/**
	 * Compute the string that will identify the user site for this user - use
	 * the EID if possible
	 * 
	 * @param userId
	 *        The user id
	 * @return The site "ID" but based on the user EID
	 */
	public String getUserEidBasedSiteId(String userId)
	{
		try
		{
			// use the user EID
			String eid = UserDirectoryService.getUserEid(userId);
			return SiteService.getUserSiteId(eid);
		}
		catch (UserNotDefinedException e)
		{
			M_log.warn("getUserEidBasedSiteId: user id not found for eid: " + userId);
			return SiteService.getUserSiteId(userId);
		}
	}

}