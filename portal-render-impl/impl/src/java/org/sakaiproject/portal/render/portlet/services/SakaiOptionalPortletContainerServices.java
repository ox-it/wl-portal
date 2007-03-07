package org.sakaiproject.portal.render.portlet.services;

import org.apache.pluto.OptionalContainerServices;
import org.apache.pluto.OptionalContainerServices;
import org.apache.pluto.spi.optional.PortletPreferencesService;
import org.apache.pluto.spi.optional.PortletEnvironmentService;
import org.apache.pluto.spi.optional.PortletInvokerService;
import org.apache.pluto.spi.optional.PortletRegistryService;
import org.apache.pluto.spi.optional.PortletInfoService;
import org.apache.pluto.spi.optional.PortalAdministrationService;
// TODO: Uncomment for Pluto 1.1.1
// import org.apache.pluto.spi.optional.PortletServletService;
// import org.apache.pluto.spi.optional.PortletServletListener;
// import org.apache.pluto.spi.optional.UserInfoService;

import org.apache.pluto.PortletContainerException;

import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.apache.pluto.spi.optional.P3PAttributes;

import org.apache.pluto.PortletWindow;
import org.apache.pluto.internal.InternalPortletPreference;

import org.sakaiproject.authz.cover.SecurityService;

import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.cover.SiteService;

import java.net.URLEncoder;
import java.net.URLDecoder;

import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.ToolSession;
import org.sakaiproject.tool.api.Tool;

import org.sakaiproject.thread_local.cover.ThreadLocalManager;

// Reuse the Pluto preference implementation - nothing wrong with it!
import org.apache.pluto.internal.impl.PortletPreferenceImpl;

public class SakaiOptionalPortletContainerServices implements OptionalContainerServices {

    private static Log M_log = LogFactory.getLog(SakaiOptionalPortletContainerServices.class);

    protected final static String CURRENT_PLACEMENT = "sakai:ToolComponent:current.placement";

    // OptionalContainerServices Impl ------------------------------------------
    
    // TODO: Uncomment for Pluto 1.1.1
    // private UserInfoService userInfoService = new SakaiUserInfoService();
    // private PortletServletService portletServletService = new SakaiPortletServletService();

    private PortletPreferencesService prefService = new SakaiPortletPreferencesService();

    private boolean prefLog = true;
    public PortletPreferencesService getPortletPreferencesService() {
	if ( prefLog) M_log.info("Sakai Optional Portal Services returning "+prefService);
        prefLog = false;  // Only log once
        return prefService;
    }

    public PortletRegistryService getPortletRegistryService() {
        return null;
    }

    public PortletEnvironmentService getPortletEnvironmentService() {
        return null;
    }
    
    public PortletInvokerService getPortletInvokerService() {
        return null;
    }

    public PortletInfoService getPortletInfoService() {
        return null;
    }

    public PortalAdministrationService getPortalAdministrationService() {
        return null;
    }

    // TODO: Uncomment for Pluto 1.1.1 - Make sure to test
/*
    public UserInfoService getUserInfoService() {
        return userInfoService;
    }

    public PortletServletService getPortletServletService() {
        return portletServletService;
    }

    // Our implementations of these local services

    // At some level this could return a clever proxy which did lazy loading
    public class SakaiUserInfoService implements UserInfoService {

        public Map getUserInfo(PortletRequest request) throws PortletContainerException {

            Map retval = null;

            User user = UserDirectoryService.getCurrentUser();
            if ( user != null ) {
                System.out.println("Found Current User="+user.getEid());
                retval = new HashMap<String,String> ();
                retval.put(P3PAttributes.USER_HOME_INFO_ONLINE_EMAIL,user.getEmail());
                retval.put(P3PAttributes.USER_BUSINESS_INFO_ONLINE_EMAIL,user.getEmail());
                retval.put(P3PAttributes.USER_NAME_GIVEN,user.getFirstName());
                retval.put(P3PAttributes.USER_NAME_FAMILY,user.getLastName());
                retval.put(P3PAttributes.USER_NAME_NICKNAME,user.getDisplayName());
            }

            System.out.println("Returning=" +retval);
            if ( retval == null ) retval = new HashMap();
            return retval;
        }
    }

    public class SakaiPortletServletService  implements PortletServletService {
    
        private List portletServletListeners = null;
    
        public List getPortletServletListeners () {

	   if ( portletServletListeners == null ) {
        	portletServletListeners = new ArrayList(1);
	    	portletServletListeners.add(new SakaiPortletServletListener());
	    }

	// System.out.println("SakaiPortletServletService called returninng="+portletServletListeners.get(0));
            return portletServletListeners ;
        }
    }

    public class SakaiPortletServletListener implements  PortletServletListener {
    
        public void preRender(PortletRequest request, PortletResponse response)
	{
		System.out.println("preRender Called =============");
		preRequest(request,response);
		preRequest(request,response);
	}
    
        public void preAction(PortletRequest request, PortletResponse response)
	{
		System.out.println("preAction Called =============");
		preRequest(request,response);
		preRequest(request,response);
	}

	private void preRequest(PortletRequest request, PortletResponse response)
        {

		String placementId = (String) request.getAttribute("org.sakaiproject.portal.api.PortalService_placementid");
		// System.out.println("place from getAttribute = "+placementId);
		if ( placementId == null ) {
			M_log.info("No Placement found");
			return;  // We have nothing to work with
		}

        	Session session = SessionManager.getCurrentSession();
		// System.out.println("Session = "+session);
		if ( session == null ) {
			M_log.info("No Session found placementId="+placementId);
			return;   // We have nothing to work with
		}

                // System.out.println("UserId="+session.getUserId()+" UserEID="+session.getUserEid());

		// Check to see if there is already a placement in place
            	Placement ppp = (Placement) ThreadLocalManager.get(CURRENT_PLACEMENT);
		// System.out.println("ThreadLocal CURRENT_PLACEMENT="+ppp);
		if ( ppp != null ) {
			// System.out.println("ThreadLocal CURRENT_PLACEMENT ID="+ppp.getId());
			if ( placementId.equals(ppp.getId()) ) return;  // Placement in place
		}

		// find the tool from some site (ToolConfiguration extends Placement)
		ToolConfiguration siteTool = SiteService.findTool(placementId);
		// System.out.println("siteTool="+siteTool);
		if ( siteTool == null ) {
			M_log.info("No ToolConfiguration found, placementId="+placementId+" session="+session);
			return;
		}

		// Actually store the placement in Thread Local
		ThreadLocalManager.set(CURRENT_PLACEMENT, siteTool);


		// *** Testing Printout to see how well we have the APIs Configured ****
		// ToolSession ts = SessionManager.getCurrentToolSession();
		// System.out.println("*** TEST *** \nTool Session = "+ts);
		// if ( ts != null ) System.out.println("ToolSession.getId="+ts.getId());

		// Placement placement = ToolManager.getCurrentPlacement();
		// System.out.println("Placement = "+placement);

        	// if ( placement != null ) {
			// String placementContext = placement.getContext();
			// System.out.println("Context = "+placementContext);
        	// }
	 }

    }
*/
    // TODO: End Uncomment for Pluto 1.1.1


    public class SakaiPortletPreferencesService implements PortletPreferencesService {
	
	public SakaiPortletPreferencesService() {
		// Do nothing.
	}

	/**
	 * Returns the stored portlet preferences array. The preferences managed by
	 * this service should be protected from being directly accessed, so this
	 * method returns a cloned copy of the stored preferences.
	 * 
	 * @param portletWindow  the portlet window.
	 * @param request  the portlet request from which the remote user is retrieved.
	 * @return a copy of the stored portlet preferences array.
	 * @throws PortletContainerException
	 */
	public InternalPortletPreference[] getStoredPreferences(
			PortletWindow portletWindow,
			PortletRequest request)
	throws PortletContainerException {

            boolean readOnly = true;

	    // Get the Placement Id
	    String key = portletWindow.getId().getStringId();

            // find the tool from some site
            ToolConfiguration siteTool = SiteService.findTool(key);
            // System.out.println("siteTool="+siteTool);

	    ArrayList<InternalPortletPreference> prefArray = new ArrayList<InternalPortletPreference> ();
       	    if ( siteTool != null ) {
                String siteId = siteTool.getSiteId();
                // System.out.println("siteId="+siteId);
    
                String siteReference = SiteService.siteReference(siteId);
                // System.out.println("Reference="+siteReference);

	        // If you don't have site.upd - Mark all references as read only
                readOnly = ! SecurityService.unlock("site.upd",siteReference);

		Properties props = siteTool.getPlacementConfig();
		// System.out.println("props = "+props);
     		for (Enumeration e = props.propertyNames() ; e.hasMoreElements() ;) {
		    String propertyName = (String) e.nextElement();
		    // System.out.println("Property name = "+propertyName);
		    if ( propertyName != null && propertyName.startsWith("javax.portlet:") && propertyName.length() > 14) {
		    	String propertyValue = props.getProperty(propertyName);
		    	String [] propertyList = deSerializeStringArray(propertyValue);
		    	String internalName = propertyName.substring(14);
		    	// System.out.println("internalName="+internalName+" propertyList="+propertyList);
		    	InternalPortletPreference newPref = 
				new PortletPreferenceImpl(internalName,propertyList,readOnly);
		    	// System.out.println("newPref = "+newPref);
			prefArray.add(newPref);
		    }
     		}
	    }

            InternalPortletPreference[] preferences = new InternalPortletPreference[prefArray.size()];

            preferences = (InternalPortletPreference[]) prefArray.toArray(preferences);

            if (M_log.isDebugEnabled()) {
            	M_log.debug("Got " + preferences.length + " stored preferences.");
            }
            return preferences;
	}
	
	/**
	 * Stores the portlet preferences to the in-memory storage. This method
	 * should be invoked after the portlet preferences are validated by the
	 * preference validator (if defined).
	 * <p>
	 * The preferences managed by this service should be protected from being
	 * directly accessed, so this method clones the passed-in preferences array
	 * and saves it.
	 * </p>
	 * 
	 * @see javax.portlet.PortletPreferences#store()
	 *
	 * @param portletWindow  the portlet window
	 * @param request  the portlet request from which the remote user is retrieved.
	 * @param preferences  the portlet preferences to store.
	 * @throws PortletContainerException
	 */
    	public void store(PortletWindow portletWindow,
                      PortletRequest request,
                      InternalPortletPreference[] preferences)
    	throws PortletContainerException {

	    String key = portletWindow.getId().getStringId();

            // find the tool from some site
            ToolConfiguration siteTool = SiteService.findTool(key);
            // System.out.println("siteTool="+siteTool);
	    if ( siteTool == null ) return;

	    Properties props = siteTool.getPlacementConfig();
	    if ( props == null ) return;

            String siteId = siteTool.getSiteId();
            // System.out.println("siteId="+siteId);

            String siteReference = SiteService.siteReference(siteId);
            // System.out.println("Reference="+siteReference);

	    // If you don't have site.upd - silently return not storing
  	    // In an ideal world perhaps we should throw java.io.IOException
	    // As per PortletPreferences API on the store() method
            if ( ! SecurityService.unlock("site.upd",siteReference) ) {
		// System.out.println("You do not have site.upd - silently returning and not storing");
		return;
	    }

	    // System.out.println("props before cleanup= "+props);

	    boolean changed = false;

            // Remove properties from the placement which did not come back to be stored
	    if ( props != null ) {
     		for (Enumeration e = props.propertyNames() ; e.hasMoreElements() ;) {
		    String propertyName = (String) e.nextElement();
		    // System.out.println("Property name = "+propertyName);
		    if ( propertyName != null && propertyName.startsWith("javax.portlet:") && propertyName.length() > 14) {
		    	String internalName = propertyName.substring(14);
		    	// System.out.println("making sure we still have a prop named internalName="+internalName);
			boolean found = false;
    	                for (int i = 0; i < preferences.length; i++) {
		            if ( preferences[i] != null ) {
                                String propName = preferences[i].getName();
		                // System.out.println("Store["+i+"]  ="+propName);
				if ( internalName.equals(propName) ) {
                                    found = true;
                                    break;
                                }
                            }
                        }
			if ( ! found ) {
                           // System.out.println("Removing "+propertyName);
                           props.remove(propertyName);
                           changed = true;
                        }
                   }
               }
            }

	    // System.out.println("props after cleanup= "+props);

	    // Add / up date property values
    	    for (int i = 0; i < preferences.length; i++) {
		// System.out.println("Store["+i+"]  ="+preferences[i]);
		if ( preferences[i] != null && props != null ) {
		    String propKey = "javax.portlet:"+preferences[i].getName();
		    String storeString = serializeStringArray(preferences[i].getValues());
		    String oldString = props.getProperty(propKey);

                    // System.out.println("propKey = "+propKey);
                    // System.out.println("storeString = "+storeString);
                    // System.out.println("oldString = "+oldString);
                    if ( (oldString == null && storeString != null ) || ( ! storeString.equals(oldString) ) ) {
		        // System.out.println("Setting "+propKey+" value="+storeString);
		        props.setProperty(propKey,storeString);
		        changed = true;
	    	    }
                }
	    }

            // System.out.println("props after update= "+props);
	    // System.out.println("changed="+changed);

	    if ( changed && siteTool != null ) {
		siteTool.save();
		// System.out.println("Saved");
	    }

            if (M_log.isDebugEnabled()) {
                M_log.debug("Portlet preferences stored for: " + key);
            }
    	}
    
    	private String serializeStringArray(String [] input)
    	{
	    if ( input == null || input.length < 1 ) return null;

	    String retval = "";
	    for ( int i=0; i< input.length; i++ )
	    {
	        if ( i > 0 ) retval = retval + "!";
	        retval += URLEncoder.encode(input[i]);
	    }
	    return retval;
    	}

    	private String [] deSerializeStringArray(String input)
    	{
	    // System.out.println("Input="+input);
	    String [] retval = input.split("!");
	    // System.out.println("Found "+retval.length+" items.");
	    for ( int i = 0; i< retval.length; i++ ) {
	    	retval[i] = URLDecoder.decode(retval[i]);
	    }
	    return retval;
    	}

    }  // End of SakaiPortletPreferencesService

}