//$Id$
package com.zoho.wms.asyncweb.server.grid.servlet;

// java imports
import java.io.IOException;
import java.util.logging.*;

// ams imports
import com.zoho.wms.asyncweb.server.http.*;
import com.zoho.wms.asyncweb.server.exception.AWSException;

// wms servercommon imoprts
import com.adventnet.wms.servercommon.dc.DC;
import com.adventnet.wms.servercommon.grid.ar.ARManager;
import com.adventnet.wms.servercommon.util.WMSUtil;
import com.adventnet.wms.servercommon.grid.ar.ARConstants;

public class ARServlet extends HttpServlet
{
	private static final Logger LOGGER = Logger.getLogger(ARServlet.class.getName());

	public void service(HttpRequest request, HttpResponse response) throws IOException, AWSException
	{
		String version = request.getHeader("version");
		String status  = request.getParameter("status");
		String operation = request.getParameter("operation");

		LOGGER.info("AR--> version : "+version+" \nstatus : " + status+" \noperation : " + operation); //No I18N"
		try
		{
			if(String.valueOf(ARConstants.AR_COMPLETED).equals(status))
			{
				long stime = System.currentTimeMillis();
				int mode = ARManager.getServerMode();
				if("switch".equals(operation) && mode == -1)
				{
					mode = ARConstants.SERVERMODE_NEW;
				}

				if(DC.notifyARComplete(mode,operation))
				{
					LOGGER.info("AR-->Success [Version : "+version+"]");
					response.write(ARConstants.Status.COMPLETED.getBytes());
				}
				else
				{
					LOGGER.info("AR-->Failed [Version : "+version+"]");
					response.write(ARConstants.Status.FAILED.getBytes());
				}
				LOGGER.info("ARDEBUG--> Take over time " +WMSUtil.HumanReadable.getDuration(System.currentTimeMillis() - stime,true));
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE,"AR-->Exception in AR Servlet for status : "+status+" [Version : "+version+"]",e);
		}
		finally
		{
			response.close();
		}
	}
}
