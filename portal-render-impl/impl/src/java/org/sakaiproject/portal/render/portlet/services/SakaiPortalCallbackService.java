package org.sakaiproject.portal.render.portlet.services;

import java.util.Map;

import javax.portlet.PortletMode;
import javax.portlet.WindowState;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pluto.PortletWindow;
import org.apache.pluto.spi.PortalCallbackService;
import org.apache.pluto.spi.PortletURLProvider;
import org.apache.pluto.spi.ResourceURLProvider;
import org.sakaiproject.portal.render.portlet.services.state.EnhancedPortletStateEncoder;
import org.sakaiproject.portal.render.portlet.services.state.PortletState;
import org.sakaiproject.portal.render.portlet.services.state.PortletStateAccess;
import org.sakaiproject.portal.render.portlet.services.state.PortletStateEncoder;

/**
 *
 */
public class SakaiPortalCallbackService implements PortalCallbackService
{

	private static final Log LOG = LogFactory.getLog(SakaiPortalCallbackService.class);

	public static final String PORTLET_STATE_QUERY_PARAM = "org.sakaiproject.portal.pluto.PORTLET_STATE";

	private PortletStateEncoder portletStateEncoder = new EnhancedPortletStateEncoder();

	public PortletStateEncoder getPortletStateEncoder()
	{
		return portletStateEncoder;
	}

	public void setPortletStateEncoder(PortletStateEncoder portletStateEncoder)
	{
		this.portletStateEncoder = portletStateEncoder;
	}

	public void setTitle(HttpServletRequest request, PortletWindow window, String title)
	{
		LOG.debug("Setting portlet title for window '" + window.getId() + "' to '"
				+ title + "'.");
		PortletStateAccess.getPortletState(request, window.getId().getStringId())
				.setTitle(title);
	}

	public PortletURLProvider getPortletURLProvider(HttpServletRequest request,
			PortletWindow window)
	{
		PortletState currentState = PortletStateAccess.getPortletState(request, window
				.getId().getStringId());
		PortletState state = null;
		if (currentState != null)
		{
			state = new PortletState(currentState);
		}
		else
		{
			state = new PortletState(window.getId().getStringId());
		}
		String baseUrl = request.getRequestURI();
		return new SakaiPortletURLProvider(baseUrl, state);
	}

	public ResourceURLProvider getResourceURLProvider(HttpServletRequest request,
			PortletWindow window)
	{
		return new SakaiResourceURLProvider("");
	}

	public Map getRequestProperties(HttpServletRequest request, PortletWindow window)
	{
		PortletState currentState = PortletStateAccess.getPortletState(request, window
				.getId().getStringId());
		return currentState.getRequestProperties();
	}

	public void setResponseProperty(HttpServletRequest request, PortletWindow window,
			String key, String value)
	{
		PortletState currentState = PortletStateAccess.getPortletState(request, window
				.getId().getStringId());
		currentState.getResponseProperties().put(key, value);
	}

	public void addResponseProperty(HttpServletRequest request, PortletWindow window,
			String key, String value)
	{
		PortletState currentState = PortletStateAccess.getPortletState(request, window
				.getId().getStringId());
		currentState.getResponseProperties().put(key, value);
	}

	/**
	 * PorltetURLProvider implementation. <p/> Implementation encodes the the
	 * url's state utilizing the encapsulating services portletStateEncoder.
	 * 
	 * @see SakaiPortalCallbackService#getPortletStateEncoder()
	 * @see SakaiPortalCallbackService#setPortletStateEncoder(PortletStateEncoder)
	 * @see PortletState
	 */
	class SakaiPortletURLProvider implements PortletURLProvider
	{

		private String baseUrl;

		private PortletState portletState;

		public SakaiPortletURLProvider(String baseUrl, PortletState portletState)
		{
			this.baseUrl = baseUrl;
			this.portletState = portletState;
		}

		public void setPortletMode(PortletMode portletMode)
		{
			portletState.setPortletMode(portletMode);
		}

		public void setWindowState(WindowState windowState)
		{
			portletState.setWindowState(windowState);
		}

		public void setAction(boolean b)
		{
			portletState.setAction(b);
		}

		public void setSecure()
		{
			portletState.setSecure(true);
		}

		public void clearParameters()
		{
			portletState.clearParameters();
		}

		public void setParameters(Map map)
		{
			portletState.setParameters(map);
		}

		@Override
		public String toString()
		{
			return new StringBuffer(baseUrl).append("?")
					.append(PORTLET_STATE_QUERY_PARAM).append("=").append(
							portletStateEncoder.encode(portletState)).toString();
		}
	}

	/**
	 * Resources URL Provider implementation used by this callback service.
	 */
	public class SakaiResourceURLProvider implements ResourceURLProvider
	{

		private String base;

		private String path;

		private String absolute;

		public SakaiResourceURLProvider(String serverUri)
		{
			this.base = serverUri;
		}

		public void setAbsoluteURL(String string)
		{
			this.absolute = string;

		}

		public void setFullPath(String string)
		{
			this.path = string;
		}

		@Override
		public String toString()
		{
			if (absolute != null)
			{
				return this.absolute;
			}
			return base + path;
		}

	}
}