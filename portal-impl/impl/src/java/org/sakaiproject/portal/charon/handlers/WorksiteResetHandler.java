/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007 The Sakai Foundation.
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

package org.sakaiproject.portal.charon.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.portal.api.PortalHandlerException;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.util.Web;

/**
 * @author ieb
 */
public class WorksiteResetHandler extends BasePortalHandler
{
	public WorksiteResetHandler()
	{
		urlFragment = "worksite-reset";
	}

	@Override
	public int doGet(String[] parts, HttpServletRequest req, HttpServletResponse res,
			Session session) throws PortalHandlerException
	{
		if ((parts.length > 2) && (parts[1].equals("worksite-reset")))
		{
			try
			{
				String worksiteUrl = req.getContextPath() + "/worksite"
						+ Web.makePath(parts, 2, parts.length);
				// Make sure to add the parameters such as panel=Main
				String queryString = req.getQueryString();
				if (queryString != null)
				{
					worksiteUrl = worksiteUrl + "?" + queryString;
				}
				portalService.setResetState("true");
				res.sendRedirect(worksiteUrl);
				return RESET_DONE;
			}
			catch (Exception ex)
			{
				throw new PortalHandlerException(ex);
			}
		}
		else
		{
			return NEXT;
		}
	}

}