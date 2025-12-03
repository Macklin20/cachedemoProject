//$Id$
package com.zoho.wms.asyncweb.server.grid.servlet;

// java imports
import java.io.IOException;
import java.util.logging.*;

// wms imports
import com.zoho.wms.asyncweb.server.http.*;
import com.adventnet.wms.servercommon.dc.DC;

import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;

public class GridOperationServlet extends HttpServlet
{
	private static Logger logger = Logger.getLogger(GridOperationServlet.class.getName());

	@SuppressWarnings("unchecked")		//No I18n
	public void service(HttpRequest request, HttpResponse response) throws IOException, AWSException
	{
		try
		{
			response.setRequestState(StateConstants.REQUEST_ACKNOWLEDGED);
			logger.log(Level.INFO,"NS--> Eneterd GridOperationServlet.");

			if(DC.handleGridOperation(request.getHeaders(),request.getParams()))
			{
				logger.log(Level.INFO,"NS--> Status code inside servlet 200");
				response.commitChunkedTransfer(200, "success");	//No I18n
			}
			else
			{
				logger.log(Level.INFO,"NS--> Status code inside servlet 403");
				response.commitChunkedTransfer(403, "forbidden");	//No I18n
			}
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE,"NS--> Exception inside servlet",e);
		}

		response.close();
	}
}
