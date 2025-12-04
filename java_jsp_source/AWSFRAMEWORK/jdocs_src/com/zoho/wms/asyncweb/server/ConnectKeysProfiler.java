//$Id$
package com.zoho.wms.asyncweb.server;

// Java import
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TreeMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Enumeration;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;


public class ConnectKeysProfiler
{

	private static AsyncLogger mtlogger = new AsyncLogger("messagetracker");//No I18n
	private static ConcurrentHashMap<String, TreeMap> keysInfo = new ConcurrentHashMap<String, TreeMap> ();
	private static DateFormat df = new SimpleDateFormat("dd MMM yyyy kk:mm:ss SSS");

	private static ConcurrentHashMap<String, AtomicLong> hits = new ConcurrentHashMap<String, AtomicLong> ();
	private static Timer scheduler = new Timer("ConnectKeysProfiler");
	private static long resettime = 0l;

	public static boolean initialize()
	{
		scheduler.scheduleAtFixedRate(new Profiler(), 5*1000L, 5*1000L);
		resettime = System.currentTimeMillis();
		return true;
	}

	static class Profiler extends TimerTask
	{
		public void run()
		{
			long curtime = System.currentTimeMillis();
			for(Enumeration en=hits.keys(); en.hasMoreElements();)
			{
				String port = (String) en.nextElement();
				AtomicLong cnt = hits.get(port);
				Long count = cnt.longValue();
				TreeMap<Long, String> portKeyInfo = keysInfo.get(port);
				if(portKeyInfo==null)
				{
					portKeyInfo = new TreeMap<Long, String>();
					portKeyInfo.put(count, df.format(curtime));	
					keysInfo.put(port, portKeyInfo);
					mtlogger.log(Level.INFO,""+keysInfo,Profiler.class.getName(),AWSLogMethodConstants.RUN);
				}else
				{
					Long leastvalue = portKeyInfo.firstKey();
					if(portKeyInfo.size() < 5 || count >= leastvalue.intValue())
					{
						portKeyInfo.put(count, df.format(curtime));
						if(portKeyInfo.size() > 5)
						{
							portKeyInfo.remove(leastvalue);
						}
						mtlogger.log(Level.INFO,""+keysInfo,Profiler.class.getName(),AWSLogMethodConstants.RUN);
					}

				}
			}
			clearCount();
			if((curtime - resettime) > 1000L*60*30)
			{
				resettime = curtime;
				keysInfo = new ConcurrentHashMap<String, TreeMap> ();
			}

		}
	}


	public static void updateCount(int port, long count)
	{
		AtomicLong cnt = hits.get(""+port);
		if(cnt != null)
		{
			cnt.addAndGet(count);
		}else
		{
			cnt = new AtomicLong(count);	
		}
		hits.put(""+port, cnt);
	}


	public static void clearCount()
	{
		hits = new ConcurrentHashMap<String, AtomicLong> ();
	}
}
