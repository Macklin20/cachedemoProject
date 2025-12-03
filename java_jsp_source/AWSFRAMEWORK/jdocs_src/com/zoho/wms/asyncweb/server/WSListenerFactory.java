//$Id:$
package com.zoho.wms.asyncweb.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;

import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.adventnet.wms.servercommon.LockManager;

public class WSListenerFactory
{
	private static AsyncLogger logger = new AsyncLogger(WSListenerFactory.class.getName());
	private static ConcurrentHashMap<String,ArrayList> listenerMap = new ConcurrentHashMap<String,ArrayList>();
	private static Hashtable<String,Integer> keyIndexMap = new Hashtable<String,Integer>();
	private static LockManager listenerlock = new LockManager();
	private static ConcurrentHashMap<String,ArrayList<String>> prdIpMap = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String, Integer> prdIndexMap = new ConcurrentHashMap<>();

	public static void add(String ukey, int type,  WebSocket wsservlet)
	{
		String key = ukey+"_"+type;//No I18N
		try
		{
			synchronized(listenerlock.lock(key))
			{
				if(listenerMap.get(key) == null)
				{
					ArrayList wslist = new ArrayList();
					listenerMap.put(key,wslist);
					keyIndexMap.put(key,0);
				}
				listenerMap.get(key).add(wsservlet);
			}
			listenerlock.release(key);
		}
		catch(Exception ex)
		{
			listenerlock.release(key);
		}
	}

	public static void addIpMap(String prd, String ip)
	{
		try
		{
			synchronized(listenerlock.lock(prd))
			{
				if(!prdIpMap.containsKey(prd))
				{
					ArrayList<String> list = new ArrayList<>();
					prdIpMap.put(prd, list);
					prdIndexMap.put(prd, 0);
				}
				if(!prdIpMap.get(prd).contains(ip))
				{
					prdIpMap.get(prd).add(ip);
				}
			}

		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception in add ip map : "+prd+", ip : "+ip,WSListenerFactory.class.getName(),AWSLogMethodConstants.ADD_IP_MAP, ex);
		}
		finally
		{
			listenerlock.release(prd);
		}
	}

	public static WebSocket getListenerWS(String ukey,int type)
	{
		WebSocket ws = null;
		String key = ukey+"_"+type;//No I18N
		try
		{
			synchronized(listenerlock.lock(key))
			{
				if(listenerMap.get(key) == null)
				{		
					listenerlock.release(key);
					return null;
				}
				try
				{
					int size = listenerMap.get(key).size();
					if(size == 0)
					{
						listenerlock.release(key);
						return null;
					}
					int idx = ((keyIndexMap.get(key)).intValue());
					++idx;
					if(idx >= size)
					{
						idx = 0;
					}
					keyIndexMap.put(key,idx);
					ws = (WebSocket)(listenerMap.get(key).get(idx));
				}
				catch(Exception ex)
				{
					keyIndexMap.put(key,0);
					ws = (WebSocket)(listenerMap.get(key).get(0));
				}
			}
			listenerlock.release(key);
		}
		catch(Exception ex)
		{
			listenerlock.release(key);
		}
		return ws;
	}

	public static String getServer(String prd)
	{
		try
		{
			synchronized(listenerlock.lock(prd))
			{
				if(!prdIpMap.containsKey(prd) || prdIpMap.get(prd).isEmpty())
				{
					return null;
				}
				try
				{
					int index = prdIndexMap.get(prd);
					++index;
					if(index >= prdIpMap.get(prd).size())
					{
						index = 0;
					}
					prdIndexMap.put(prd, index);
					return prdIpMap.get(prd).get(index);
				}
				catch(Exception e)
				{
					prdIndexMap.put(prd, 0);
					return prdIpMap.get(prd).get(0);
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception in get server : "+prd,WSListenerFactory.class.getName(),AWSLogMethodConstants.GET_SERVER, ex);
		}
		finally
		{
			listenerlock.release(prd);
		}
		return null;
	}

	public static String getNextWnet(String prd, String wnet,int type)
	{
		String key = prd+"_"+wnet+"_"+type;
		try
		{
			synchronized(listenerlock.lock(key))
			{
				try
				{
					Iterator keysetitr = listenerMap.keySet().iterator();
					while(keysetitr.hasNext())
					{
						String nkey = (String)keysetitr.next();
						if(listenerMap.get(nkey) == null || listenerMap.get(nkey).size() == 0 || nkey.equals(key))
						{
							continue;
						}

						String[] ndata = nkey.split("_");
						String nprd = ndata[0];
						String nwnet = ndata[1];
						String ntype = ndata[2];

						if(prd.equals(nprd) && type == Integer.parseInt(ntype))
						{
							listenerlock.release(key);
							return nwnet;
						}
					}
				}
				catch(Exception ex)
				{
					logger.log(Level.INFO, " Exception ",WSListenerFactory.class.getName(),AWSLogMethodConstants.GET_NEXT_WNET, ex);
				}
			}
			listenerlock.release(key);
		}
		catch(Exception ex)
		{
			listenerlock.release(key);
		}
		return wnet;
	}

	public static void remove(String ukey, int type , WebSocket wsservlet)
	{
		String key = ukey+"_"+type;//No I18N
		try
		{
			synchronized(listenerlock.lock(key))
			{
				if(listenerMap.get(key) == null)
				{
					listenerlock.release(key);
					return;
				}
				listenerMap.get(key).remove(wsservlet);
			}
			listenerlock.release(key);
		}
		catch(Exception ex)
		{
			listenerlock.release(key);
		}
	}

	public static void removePrd(String prd, String ip)
	{
		try
		{
			synchronized(listenerlock.lock(prd))
			{
				if(!prdIpMap.containsKey(prd))
				{
					return;
				}
				prdIpMap.get(prd).remove(ip);
				if(prdIpMap.get(prd).isEmpty())
				{
					prdIpMap.remove(prd);
					prdIndexMap.remove(prd);
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception in remove : "+prd+", ip : "+ip,WSListenerFactory.class.getName(),AWSLogMethodConstants.REMOVE_PRD, ex);
		}
		finally
		{
			listenerlock.release(prd);
		}
	}
}
