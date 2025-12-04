//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.logging.Level;
import java.util.Enumeration;
import java.util.Hashtable;

import java.util.concurrent.ConcurrentHashMap;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class MessageIdTracker
{
	private static ConcurrentHashMap<String, Long> messageIdMap = new ConcurrentHashMap(10000);
	private static ConcurrentHashMap<String, String> successMessageIdList = new ConcurrentHashMap(2500);

	private static AsyncLogger mtlogger = new AsyncLogger("messagetracker");//No I18n

	public static void add(String id)
	{
		if(id==null)return;
		messageIdMap.put(id, new Long(System.currentTimeMillis()));
	}


	public static void remove(String id, String stats)
	{
		if(id==null)return;
		if(messageIdMap.remove(id)!=null)
		{
			String[] statsinfo = stats.split("#");
			long readtime = Long.parseLong(statsinfo[0]);
			long processtime = Long.parseLong(statsinfo[1]);
			long writetime = Long.parseLong(statsinfo[2]);
			if((readtime+processtime+writetime) > 500)
			{
				successMessageIdList.put(id, stats);
			}
		}
		if(successMessageIdList.size() > 1500)
		{
			mtlogger.log(Level.INFO,"Delivered MessageIds with time > 500msec :"+successMessageIdList,MessageIdTracker.class.getName(),AWSLogMethodConstants.REMOVE);
			successMessageIdList = new ConcurrentHashMap(10000);
		}
	}


	public static void printStats()
	{
		if(getStats().size() > 0)
		{
			mtlogger.log(Level.INFO,"Undelivered MessageIds :"+getStats(),MessageIdTracker.class.getName(),AWSLogMethodConstants.PRINT_STATS);
		}
	}


	public static Hashtable getStats()
	{
		long time = System.currentTimeMillis();
		Hashtable expiredMessageIds = new Hashtable();
		for(Enumeration en=messageIdMap.keys(); en.hasMoreElements();)
		{
			String key = (String) en.nextElement();
			long mtime = messageIdMap.get(key);
			if((time - 10*1000) > mtime)
			{
				messageIdMap.remove(key);
				expiredMessageIds.put(key, ""+mtime);
			}
		}
		return expiredMessageIds;
	}

	public static void clearStats()
	{
		if(messageIdMap.size()==0){return;}
		messageIdMap = new ConcurrentHashMap(10000);
		mtlogger.log(Level.INFO,"Undelivered messageIds cleared",MessageIdTracker.class.getName(),AWSLogMethodConstants.CLEAR_STATS);
	}

}
