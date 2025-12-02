//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class WSRequestTimeoutListener extends TimeOutListener
{
	private final static AsyncLogger LOGGER = new AsyncLogger(WSRequestTimeoutListener.class.getName());

        public static final WSRequestTimeoutListener TRACKER = new WSRequestTimeoutListener();

	private WSRequestTimeoutListener()
	{
		super("WSRequestTimeoutListener",ConfManager.getWSRequestTrackerInterval());//No I18N
	}

	public boolean isExpired(Object obj)
	{
		return (System.currentTimeMillis() >= (((WebSocket)obj).getExpireTime()));
	}

	public boolean isInvalidEntry(Object obj, long time)
	{
		return (((WebSocket)obj).isInvalidTimeoutEntry(time));
	}

	public void handleExpired(ArrayList list)
	{
		LOGGER.log(Level.INFO, "WS Request Expired List : {0}",WSRequestTimeoutListener.class.getName(),AWSLogMethodConstants.HANDLE_EXPIRED, new Object[]{list.size()});
		for(Iterator it = list.iterator(); it.hasNext();)
		{
			WebSocket obj = (WebSocket) it.next();
			try
			{
				obj.onTimeout();
			}
			catch(Exception e)
			{
				LOGGER.log(Level.WARNING,"Exception during close ",WSRequestTimeoutListener.class.getName(),AWSLogMethodConstants.HANDLE_EXPIRED,e);//No I18N
			}
			try
			{
				obj.close();
			}
			catch(Exception ex)
			{
			}
		}
	}
}
