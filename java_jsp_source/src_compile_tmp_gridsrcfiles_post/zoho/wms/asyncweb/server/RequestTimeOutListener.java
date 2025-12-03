//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;


public class RequestTimeOutListener extends TimeOutListener
{
	private static AsyncLogger logger = new AsyncLogger(RequestTimeOutListener.class.getName());
	public static final RequestTimeOutListener TRACKER = new RequestTimeOutListener();

	private RequestTimeOutListener()
	{
		super("RequestTimeOutListener",ConfManager.getRequestTrackerInterval());
	}

	public boolean isExpired(Object obj)
	{
		return (System.currentTimeMillis() >= (((AsyncWebClient)obj).getExpireTime()));
	}

	public boolean isInvalidEntry(Object obj, long time)
	{
		return (((AsyncWebClient)obj).isInvalidSoTimeoutEntry(time));
	}

	public void handleExpired(ArrayList list)
	{
		int errorCase = 0;
		int reqACK = 0;
		int readexpired = 0;
		for(Iterator it = list.iterator(); it.hasNext();)
		{
			AsyncWebClient client = (AsyncWebClient) it.next();
			try
			{
				switch(client.getRequestState())
				{
					case StateConstants.REQUEST_KEEPALIVE:

					case StateConstants.REQUEST_IN_PROCESS:
						readexpired++;
						client.close(AWSConstants.READ_EXPIRED);
						break;
					case StateConstants.REQUEST_ACKNOWLEDGED:
						reqACK++;
						client.removeFromTimeoutTracker();
						break;
					default:
						errorCase++;
						client.close(AWSConstants.READ_EXPIRED);
						break;
				}
			}
			catch(Exception e)
			{
			}
		}
		logger.log(Level.FINE, AWSLogConstants.AWS_REQUEST_TIMEOUT,RequestTimeOutListener.class.getName(),AWSLogMethodConstants.HANDLE_EXPIRED, new Object[]{list.size(), errorCase, reqACK, readexpired, list.size()-reqACK});
	}
}
