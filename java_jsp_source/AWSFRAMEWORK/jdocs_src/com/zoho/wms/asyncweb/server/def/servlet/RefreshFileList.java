//$Id$

package com.zoho.wms.asyncweb.server.def.servlet;

// Java import
import java.io.*;
import java.util.logging.Level;
import java.util.Properties;
import java.util.Enumeration;

// Wms import
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.AbstractWebEngine;
import com.zoho.wms.asyncweb.server.WebEngine;
import com.zoho.wms.asyncweb.server.http.*;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.AWSConstants;

public class RefreshFileList extends HttpServlet
{
	private static AsyncLogger logger = new AsyncLogger(RefreshFileList.class.getName());

	public void service(HttpRequest req, HttpResponse res) throws IOException, AWSException
	{		
		String enginename = req.getParameter("enginename");
		if(enginename!=null && !enginename.isEmpty())
		{
			if(enginename.equals("all"))
			{
				if(!refreshAllEngineFileList())
				{
					res.sendError(HttpResponseCode.INTERNAL_SERVER_ERROR, HttpResponseCode.INTERNAL_SERVER_ERROR_MSG);
					res.close();
					return;
				}
			}
			else if(ConfManager.isValidEngine(enginename))
			{
				try
				{
					refreshEngineFileList(enginename);
				}
				catch(Exception ex)
				{
					logger.log(Level.INFO, " Exception ",RefreshFileList.class.getName(),AWSLogMethodConstants.SERVICE, ex);
					res.sendError(HttpResponseCode.INTERNAL_SERVER_ERROR, HttpResponseCode.INTERNAL_SERVER_ERROR_MSG);
					res.close();
					return;		
				}
			}
			else
			{
				res.sendError(HttpResponseCode.BAD_REQUEST, HttpResponseCode.BAD_REQUEST_MSG);
				res.close();
				return;
			}
		}
		else
		{
			res.sendError(HttpResponseCode.BAD_REQUEST, HttpResponseCode.BAD_REQUEST_MSG);
			res.close();
			return;
		}

		res.commitChunkedTransfer();
		res.write("success");
		res.close();
	}
	
	public boolean refreshAllEngineFileList()
	{
		Properties webengine = ConfManager.getWebEngineMap();
		int failed = 0;
		Enumeration en = webengine.keys();

		while(en.hasMoreElements())
		{
			try
			{
				String appname = (String) en.nextElement();
				refreshEngineFileList(appname);
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",RefreshFileList.class.getName(),AWSLogMethodConstants.REFRESH_ALL_ENGINE_FILE_LIST, ex);
				failed++;
			}
		}

		if(failed > 0)
		{
			return false;
		}
		
		return true;

	}

	public void refreshEngineFileList(String appname)
	{
		AbstractWebEngine engine = null;
		if(appname.equals(AWSConstants.DEFAULT))
		{
			engine = WebEngine.getDefaultEngine();
		}
		else
		{
			engine = WebEngine.getEngineByAppName(appname);
		}
		engine.refreshStaticFileList();
	}
}

