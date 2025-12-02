//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.logging.Level;
import java.util.*;
import java.io.*;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class SelectorPoolFactory
{
	private static AsyncLogger logger = new AsyncLogger(SelectorPool.class.getName());

	private static HashMap selectorPoolMap = new HashMap();

	public static SelectorPool getSelectorPool(int port)
	{
		if(selectorPoolMap.get(""+port)!=null)
		{
			return ((SelectorPool)(selectorPoolMap.get(port+"")));
		}
		return null;
	}

	public static void init(AsyncWebEventHandler handler) throws IOException
	{
		if(ConfManager.isHttpSelectorPoolMode())
		{
			int connectors = ConfManager.getConnectorsCount();
			HashSet webserverportlist = ConfManager.getWebServerPortList();
			Iterator itr= webserverportlist.iterator();

			while(itr.hasNext())
			{
				int webserverport = Integer.parseInt((String)itr.next());

				for(int i=0; i < connectors; i++, webserverport++)
				{
					SelectorPool httppool = new SelectorPoolImpl(webserverport,handler);
					httppool.initRead();
					selectorPoolMap.put(""+webserverport,httppool);
				}
			}
		}
		if(ConfManager.isHttpsSelectorPoolMode())
		{
			Hashtable httpsPortMap = ConfManager.getAllSSLPortMap();
			Enumeration httpsitr = httpsPortMap.keys();
			//boolean httpspoolinitialized = false;
			while(httpsitr.hasMoreElements())
			{
				int port = Integer.parseInt((String)httpsitr.nextElement());
				//if(!httpspoolinitialized)
				//{
					SelectorPool httpspool = new SelectorPoolImpl(port,handler);
					httpspool.initRead();
				//}
				selectorPoolMap.put(""+port,httpspool);
			}
		}
		logger.log(Level.INFO,"SelectorPoolFactory :: selectorPoolMap :: "+selectorPoolMap,SelectorPoolFactory.class.getName(),AWSLogMethodConstants.INIT);
	}

	public static void shutdown()
	{
		try
		{
			Iterator itr = selectorPoolMap.keySet().iterator();
			while(itr.hasNext())
			{
				String port = (String)itr.next();
				try
				{
					SelectorPool spool = (SelectorPool)selectorPoolMap.get(port);
					spool.shutdown();
				}
				catch(Exception ex)
				{
					logger.log(Level.WARNING,"Unable to shutdown selector for "+port+",",SelectorPoolFactory.class.getName(),AWSLogMethodConstants.SHUTDOWN, ex);
				}
			}
			selectorPoolMap.clear();

		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"Unable to shutdown selectors ",SelectorPoolFactory.class.getName(),AWSLogMethodConstants.SHUTDOWN,ex);
		}
	}
	
	public static void shutdownPort(String port) throws Exception
	{
		if(selectorPoolMap.get(port)!=null)
		{
			SelectorPool spool = (SelectorPool)selectorPoolMap.get(port);
			spool.shutdown();
			selectorPoolMap.remove(port);
			return;
		}

		logger.log(Level.WARNING,"PORT "+port+" NOT IN USE - CHECK PORT AND TRY AGAIN",SelectorPoolFactory.class.getName(),AWSLogMethodConstants.SHUTDOWN_PORT);
	}

	public static void initPort(String port, AsyncWebEventHandler handler) throws IOException
	{
		if(selectorPoolMap.get(port)!=null)
		{
			logger.log(Level.WARNING,"ALREADY IN USE PORT "+port+" - PLEASE CHECK",SelectorPoolFactory.class.getName(),AWSLogMethodConstants.INIT_PORT);
			return;
		}
		
		SelectorPool pool = new SelectorPoolImpl(Integer.parseInt(port),handler);
		pool.initRead();
		selectorPoolMap.put(""+port,pool);
	}
}
