package org.sakaiproject.portal.charon.handlers;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.portal.api.Portal;
import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.portal.api.PortalRenderContext;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.ToolException;

/**
 * Handler for managing the joining of a user to a site.
 * Handles URLs like /portal/join/siteId. Will get user to login first if
 * not already authenticated. Redirects back to site after a successful join
 * or sends the user to the error page.
 * @author buckett
 *
 */
public class JoinHandler extends BasePortalHandler
{

	private static final String URL_FRAGMENT = "join";

	public JoinHandler()
	{
		setUrlFragment(URL_FRAGMENT);
	}
	

	@Override
	public int doGet(String[] parts, HttpServletRequest req,
			HttpServletResponse res, Session session)
			throws PortalHandlerException
	{
		if ((parts.length == 3) && (parts[1].equals(URL_FRAGMENT)))
		{
			try {
				showJoin(parts, req, res, session);
				return END;
			} catch (Exception e) {
				throw new PortalHandlerException(e);
			}
		}
		return NEXT;
	}
	
	@Override
	public int doPost(String[] parts, HttpServletRequest req,
			HttpServletResponse res, Session session)
			throws PortalHandlerException {
		
		if ((parts.length == 3) && (parts[1].equals(URL_FRAGMENT)))
		{
			try {
				doJoin(parts, req, res, session);
				return END;
			} catch (Exception e) {
				throw new PortalHandlerException(e);
			}
		}
		return NEXT;
	}


	protected void doJoin(String[] parts, HttpServletRequest req,
			HttpServletResponse res, Session session) throws ToolException, IOException {
		String siteId = parts[2];
		try
		{
			if (req.getParameter("join") != null) {
				SiteService.getInstance().join(siteId);
				Site site = SiteService.getInstance().getSite(siteId);
				sendToSite(res, site);
			} else {
				// The user didn't opt to join so show the error.
				portal.doError(req, res, session, Portal.ERROR_SITE);
			}
		}
		catch (IdUnusedException e) {
			portal.doError(req, res, session, Portal.ERROR_SITE);
		} catch (PermissionException e) {
			portal.doError(req, res, session, Portal.ERROR_SITE);
		}
	}


	private void sendToSite(HttpServletResponse res, Site site)
			throws IOException, IdUnusedException {
		res.sendRedirect(site.getUrl());
	}


	protected void showJoin(String[] parts, HttpServletRequest req,
			HttpServletResponse res, Session session) throws IOException, ToolException {
		// Handle user not logged in.
		if (session.getUserId() == null)
		{
			portal.doLogin(req, res, session, req.getPathInfo(), Portal.LoginRoute.NONE);
		}
		else
		{
			try
			{
				String siteId = parts[2];
				Site site = SiteService.getInstance().getSite(siteId);
				// Check that the current user can access the site before we redirect.
				if (site.getUserRole(session.getUserId()) != null && SiteService.allowAccessSite(siteId))
				{
					sendToSite(res, site);
					return;
				}
				if (site.isJoinable())
				{
					String siteType = portal.calcSiteType(siteId);
					String title = ServerConfigurationService.getString("ui.service")+ " : "+ site.getTitle();
					String skin = site.getSkin();
					PortalRenderContext context = portal.startPageContext(siteType, title, skin, req);
					context.put("currentSite", portal.getSiteHelper().convertSiteToMap(req, site, null, siteId, null, false, false, false, false, null, true));
					context.put("uiService", ServerConfigurationService.getString("ui.service"));
					portal.sendResponse(context, res, "join", "text/html");
					return;
				}
			}
			catch (IdUnusedException e) {
			}
		}
		portal.doError(req, res, session, Portal.ERROR_SITE);
	}

}
