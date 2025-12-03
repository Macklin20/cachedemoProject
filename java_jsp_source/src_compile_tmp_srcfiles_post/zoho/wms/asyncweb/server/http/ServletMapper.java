//$Id$
package com.zoho.wms.asyncweb.server.http;

//Java import
/*import java.util.Properties;
import java.util.logging.Level;
import java.util.HashMap;
import java.util.Enumeration;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class ServletMapper
{
	private static AsyncLogger logger = new AsyncLogger(ServletMapper.class.getName());
	
	private static HashMap<String, AbstractServlet> servlets = new HashMap();
	private static HashMap<String, AbstractServlet> securedServlets = new HashMap();

	private static AbstractServlet defaultServlet;

	public static boolean initialize(Properties servletMapping, Properties securedServletMapping)
	{
		loadServlets(servlets, servletMapping);
		loadServlets(securedServlets, securedServletMapping);
		defaultServlet = new DefaultServlet();		
		return true;	
	}

	private static void loadServlets(HashMap instanceMap, Properties classMap)
	{
		for(Enumeration e=classMap.propertyNames();e.hasMoreElements();)
		{
			String servletName = (String) e.nextElement();
			String servletClass = (String) classMap.get(servletName);

			try
			{
				instanceMap.put(servletName, (AbstractServlet) Class.forName(servletClass).newInstance());
			}
			catch(Exception exp)
			{
				logger.log(Level.INFO,"Unable to load servlet - "+servletName+" class - "+servletClass+" - ",ServletMapper.class.getName(),AWSLogMethodConstants.LOAD_SERVLETS,exp);//No I18N
			}
		}
	}

	public static AbstractServlet getServlet(String reqUrl) throws Exception
	{
		AbstractServlet servlet = servlets.get(reqUrl);
		return (servlet!=null)?servlet:defaultServlet;		
	}

	public static AbstractServlet getSecuredServlet(String reqUrl) throws Exception
	{
		AbstractServlet servlet = securedServlets.get(reqUrl);
		return (servlet!=null)?servlet:defaultServlet;		
	}
}*/
