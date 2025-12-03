//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.http.WebSocket;

public class WSPingTimeOutListener extends TimeOutListener
{
	private static AsyncLogger logger = new AsyncLogger(WSPingTimeOutListener.class.getName());

        public static final WSPingTimeOutListener TRACKER = new WSPingTimeOutListener();

	private WSPingTimeOutListener()
	{
		super("WSPingTimeOutListener",10000);//No I18n
	}

	public boolean isExpired(Object obj)
	{
		return (System.currentTimeMillis() >= (((WebSocket)obj).getWSPingExpireTime()));
	}

	public boolean isInvalidEntry(Object obj, long time)
	{
		return (((WebSocket)obj).isValidWSPingTimeoutEntry(time));
	}

	public void handleExpired(ArrayList list)
	{
		logger.log(Level.INFO,"Expired PingTimeOut List "+list.size(),WSPingTimeOutListener.class.getName(),AWSLogMethodConstants.HANDLE_EXPIRED);
		for(Iterator it = list.iterator(); it.hasNext();)
		{
			WebSocket ws = (WebSocket) it.next();
			try
			{
				ws.onPingTimeOut();
			}
			catch(Exception e)
			{
			}
		}
	}
}
