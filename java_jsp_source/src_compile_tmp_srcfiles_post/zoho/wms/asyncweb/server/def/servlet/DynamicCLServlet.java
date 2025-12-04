//$Id$
package com.zoho.wms.asyncweb.server.def.servlet;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.util.logging.Level;

import java.lang.ClassLoader;
import java.lang.Class;
// Wms import
import com.zoho.wms.asyncweb.server.http.*;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.WebEngine;
import com.zoho.wms.asyncweb.server.AbstractWebEngine;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.AWSConstants;

public class DynamicCLServlet extends HttpServlet
{
	public static AsyncLogger logger = new AsyncLogger(DynamicCLServlet.class.getName());

	public void service(HttpRequest req, HttpResponse res) throws IOException, AWSException
	{
		res.commitChunkedTransfer();
		try {
			Class[] params = new Class[]{URL.class};
			String enginename=req.getParameter("engine");
			String jarname=req.getParameter("jarname");
			File f=new File(jarname);
			URL u;
			if(f.exists())
			{
				u= f.toURL();
				URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
				Class sysclass = URLClassLoader.class;
				Method method = sysclass.getDeclaredMethod("addURL", params);
				method.setAccessible(true);
				method.invoke(sysloader, new Object[]{u});
			}
			if(enginename.equals(AWSConstants.DEFAULT))
			{
				WebEngine.getDefaultEngine().loadServlets();
			}
			else
			{
				WebEngine.getEngineByAppName(enginename).loadServlets();
			}
			res.write("Done");

		}
		catch(Exception e)
		{
			logger.log(Level.INFO, " Exception ",DynamicCLServlet.class.getName(),AWSLogMethodConstants.SERVICE, e);
		}
		res.close();
	}
}



