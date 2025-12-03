//$Id$
package com.zoho.wms.asyncweb.server.grid.servlet;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.zoho.wms.asyncweb.server.http.*;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.adventnet.wms.servercommon.components.logging.WmsLogger;

public class WmsLoggerServlet extends HttpServlet
{

	private static Logger logger = Logger.getLogger(WmsLoggerServlet.class.getName());
	@Override
	public void service(HttpRequest request, HttpResponse response) throws Exception 
	{
		try
		{
			response.setRequestState(StateConstants.REQUEST_ACKNOWLEDGED);
			logger.info("WMSLOG--> Eneterd WmsloggerServlet.");
			
			Map<String,Object> result = WmsLogger.handleAdminOperation(request.getHeaders(),request.getParams());
			int status = (Integer) result.get("status");
			
			if(status==200)
			{
				response.write(((String) result.get("result")).getBytes());	//No I18n
			}
			else
			{
				response.sendError(status, (String) result.get("result"));
			}
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE,"WMSLOG--> Exception inside wms logger servlet",e);
		}
		finally
		{
			response.close();
		}
	}

}
