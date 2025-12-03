//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.Enumeration;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class DOSManager
{
	private static AsyncLogger logger = new AsyncLogger(DOSManager.class.getName());

	private static String serverHome = ConfManager.getServerHome();
	private static ConcurrentHashMap<String,DOSMonitor> dosMap = new ConcurrentHashMap();
	private static String engineMap = serverHome+File.separator+"conf"+File.separator+"webengine.properties";
	private static Properties engineProp = null;
	private static Properties mappedDomainProp = null;
	private static ArrayList<String> engineNameArray = new ArrayList();
	private static ArrayList<String> mappedEngineArray = new ArrayList();
	private static ArrayList<String> blockedlist = new ArrayList();
	private static int threadcount=0;
	private static boolean timescavenger = false;

	public static boolean initialize()
	{
		if(!ConfManager.isDOSEnabled())
		{
			return false;
		}
		try
		{
			engineProp = Util.getProperties(engineMap);
			mappedDomainProp = (Properties)ConfManager.getMappedDomainDetails();
			Enumeration e = engineProp.propertyNames();

			while(e.hasMoreElements())
			{
				String engineName = (String)e.nextElement();

				engineNameArray.add(engineName);
				AbstractWebEngine engine = null;
				if(engineName.equals(AWSConstants.DEFAULT))
				{
					engine = (AbstractWebEngine)WebEngine.getDefaultEngine();
				}
				else
				{
					engine = (AbstractWebEngine)WebEngine.getEngineByAppName(engineName);
				}

				if(engine!=null && engine.isMonitorEnabled())
				{
					DOSMonitor monitor = new DOSMonitor(engineName);
					dosMap.put(engineName,monitor);
				}
			}

			e = mappedDomainProp.propertyNames();
			while(e.hasMoreElements())
			{
				String mappedDomainName = (String)e.nextElement();
				if(!mappedEngineArray.contains(mappedDomainProp.getProperty(mappedDomainName)))
				{
					mappedEngineArray.add(mappedDomainProp.getProperty(mappedDomainName));
				}
			}

			if(!timescavenger)
			{
				new DOSScavenger("time").start();//No I18N
				timescavenger = true;
			}

		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE,"Cannot initialize DOSManager ",DOSManager.class.getName(),AWSLogMethodConstants.INITIALIZE,ex);
			return false;
		}
		return true;
	}


	public static DOSMonitor getDOSByEngineName(String engine)
	{
		return getDOSByEngineName(engine,true);
	}

	public static DOSMonitor getDOSByEngineName(String engine, boolean scavenge)
	{
		DOSMonitor dosmonitor = (DOSMonitor)dosMap.get(engine);
			
		if(scavenge && dosmonitor!=null && (dosmonitor.size() > ConfManager.getDOSMonitorThreshold()))
		{
			new DOSScavenger(engine+"-"+dosmonitor.size()+"-"+getThreadCount(),engine).start();
			
		}
		
		return dosmonitor;
	}

	public static int getThreadCount()
	{
		if(threadcount < 0)
		{
			threadcount = 0;
		}	
		
		return threadcount++;
	}

	public static void blockIP(String ipaddr)
	{
		blockedlist.add(ipaddr);
	}

	public static boolean isManuallyBlocked(String ipaddr)
	{
		return blockedlist.contains(ipaddr);
	}

	public static void unblockIP(String ipadr)
	{
		blockedlist.remove(ipadr);
	}

	public static void enableTimeScavenger()
	{
		timescavenger = false;
	}

	public static ConcurrentHashMap<String,DOSMonitor> getDOSMap()
	{
		return dosMap;
	}

	public static boolean isIPBlocked(String ipaddr)
	{
		int entryCount = 0;
		int blockedCount = 0;
		int i=0;
		if(blockedlist.contains(ipaddr))
		{
			return true;
		}
		if(!ConfManager.isDOSEnabled())
		{
			return false;
		}

		for(;i<engineNameArray.size();i++)
		{
			String engineName = (String)engineNameArray.get(i);
			if(dosMap.get(engineName)==null && mappedEngineArray.contains(engineName))
			{
				return false;//if there is any external engine(engines which has entries in mappeddomain.properties)
				//which has not enabled dosmonitor then request is forwarded 

			}
			if(dosMap.get(engineName)!=null && ((DOSMonitor)dosMap.get(engineName)).hasIPEntry(ipaddr))
			{
				entryCount++;
				if(((DOSMonitor)dosMap.get(engineName)).checkIPBlocked(ipaddr))
				{
					blockedCount++;
				}
			}

		}
		if(blockedCount==entryCount && blockedCount>0 && entryCount>0)
		{
			return true;
		}
		else
		{
			return false;
		}

	}

}

class DOSScavenger extends Thread
{
	public static AsyncLogger logger = new AsyncLogger(DOSScavenger.class.getName());

	String mode;
	String engine;
	private static AtomicLong counter = new AtomicLong(0);

	public DOSScavenger(String mode)
	{
		super(AWSConstants.AWS_THREAD_PREFIX+"AWSDOSScavenger"+AWSConstants.THREAD_NAME_SEPARATOR+mode+AWSConstants.THREAD_NAME_SEPARATOR+counter.getAndIncrement());//No I18N
		this.mode = mode;
	}

	public DOSScavenger(String mode, String engine)
	{
		this(mode);
		this.engine = engine;
	}
		

	public void cleanUpLocks()
	{
		ConcurrentHashMap<String, DOSMonitor> dosMap = DOSManager.getDOSMap();

		if(!dosMap.isEmpty())
		{
			Set<String> engineSet = dosMap.keySet();
			
			for(String engine : engineSet)
			{
				if(engine!=null)
				{
					cleanUpLock(engine);
				}
			}
		}
	}

	public void cleanUpLock(String engine)
	{
		DOSMonitor dosMonitor = DOSManager.getDOSByEngineName(engine,false);
		ConcurrentHashMap ipLockMap = dosMonitor.getIPDOSLockMap();

		if(!ipLockMap.isEmpty())
		{
			Set<String> ipSet = ipLockMap.keySet();
		
			for(String ip : ipSet)
			{

				if(ip!=null)
				{
					DOSMonitor.IPDOSLock ipLock = (DOSMonitor.IPDOSLock) ipLockMap.get(ip);
					if(ipLock != null)
					{		
						if((System.currentTimeMillis() - ipLock.getLastActivityTime()) > ConfManager.getDOSTimeout())
						{
							dosMonitor.clearIPDOSLock(ip);
						}
						else
						{
							ConcurrentHashMap urlLockMap = ipLock.getURLDOSLockMap();
						
							if(!urlLockMap.isEmpty())
							{
								Set<String> urlSet = urlLockMap.keySet();
							
								for(String url : urlSet)
								{			
									if(url!=null)
									{
										DOSMonitor.URLDOSLock urlLock = (DOSMonitor.URLDOSLock) urlLockMap.get(url);
										if(urlLock != null)
										{
											if((System.currentTimeMillis() - urlLock.getLastActivityTime()) > ConfManager.getDOSTimeout())
											{
												ipLock.clearURLDOSLock(url);
											}
										}				
									}
								}
							}
						}
					}
				}
			}
		}
	}	

	public void run()
	{
		try
		{
			if(mode.equals("time"))
			{
				while(true)
				{
					try
					{
						Thread.sleep(ConfManager.getDOSScavengeTime());
					}
					catch(Exception ex)
					{}
					
					cleanUpLocks();	
				  }
			}
			else
			{
				if(engine!=null)
				{
					cleanUpLock(engine);
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE,"DOS "+mode+" Scavenge failure. Exception",DOSManager.class.getName(),AWSLogMethodConstants.RUN, ex);
		}
	
		if(mode.equals("time"))
		{
			DOSManager.enableTimeScavenger();
		}
	}
}		
