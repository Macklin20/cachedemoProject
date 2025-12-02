//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.io.File;

import java.util.concurrent.atomic.AtomicInteger;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class DOSMonitor
{
	private ConcurrentHashMap<String,IPDOSLock> ipLockMap = new ConcurrentHashMap<String,IPDOSLock>();
	private short blockperiod = 15;
	private Hashtable<String,Short> urlThresholdMap = null; 
	private short suspectthreshold = 300; 
	private short suspectperiod = 2;
	private short maxiphitthreshold = 1500;
	private short monitorsec = 1;
	private static AsyncLogger logger = new AsyncLogger("doslogger");//No I18n
	private boolean block = false;
	private boolean disabledosmonitor = false;
	private short thresholdValue = 1500;

	public DOSMonitor(String engineName)
	{
		String serverHome = ConfManager.getServerHome();
		String confPath = serverHome+File.separator+"webengine"+File.separator+engineName+File.separator+"conf"+File.separator+"conf.properties";
		String dosPath = serverHome+File.separator+"webengine"+File.separator+engineName+File.separator+"conf"+File.separator+"dos.properties";
		try
		{
			Properties engineConf = Util.getProperties(confPath);
			blockperiod = Short.parseShort(engineConf.getProperty("blockperiod",String.valueOf(blockperiod)));
			suspectthreshold = Short.parseShort(engineConf.getProperty("suspectthreshold",String.valueOf(suspectthreshold)));
			maxiphitthreshold = Short.parseShort(engineConf.getProperty("maxiphitthreshold",String.valueOf(maxiphitthreshold)));
			monitorsec =  Short.parseShort(engineConf.getProperty("monitorsec",String.valueOf(monitorsec)));
			suspectperiod = Short.parseShort(engineConf.getProperty("suspectperiod",String.valueOf(suspectperiod)));
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"Cannot retrieve conf values for engine "+engineName+" starting with default values",DOSMonitor.class.getName(),AWSLogMethodConstants.DOS_MONITOR);//No I18N	
		}
		AbstractWebEngine engine = null;
		if(engineName.equals(AWSConstants.DEFAULT))
		{
			engine = (AbstractWebEngine)WebEngine.getDefaultEngine();
		}
		else
		{
			engine = (AbstractWebEngine)WebEngine.getEngineByAppName(engineName);
		}	

		block = engine.isBlockEnabled();

		logger.log(Level.INFO,"DOS Monitor for "+engineName+". Suspect threshold - "+suspectthreshold+" , Suspect period - "+suspectperiod+" , Max IP hit threshold - "+maxiphitthreshold+" , Monitor sec - "+monitorsec+" , Block Enabled - "+block,DOSMonitor.class.getName(),AWSLogMethodConstants.DOS_MONITOR);

		Properties dosConf = Util.getProperties(dosPath);	
		urlThresholdMap = new Hashtable();	
		if(dosConf!=null)
		{
			Enumeration e = dosConf.propertyNames();
			while(e.hasMoreElements())
			{
				String servletName = (String)e.nextElement();
				try	
				{
					thresholdValue = Short.parseShort(dosConf.getProperty(servletName));	
					logger.log(Level.FINE,engineName+" : "+servletName+" - dos thresholdvalue - "+thresholdValue,DOSMonitor.class.getName(),AWSLogMethodConstants.DOS_MONITOR);
				}	
				catch(Exception ex)
				{	
					logger.log(Level.FINE,engineName+" : "+servletName+" thresholdvalue not mentioned properly. Starting with default value 1500",DOSMonitor.class.getName(),AWSLogMethodConstants.DOS_MONITOR);
					thresholdValue = 1500;
				}
				urlThresholdMap.put(servletName,thresholdValue);
			}

		}

	}

	public int size()
	{
		return ipLockMap.size();
	}

	public void enableBlocking()
	{
		this.block = true;
	}

	public void disableBlocking()
	{
		this.block = false;
	}

	public void enableMonitoring()
	{
		this.disabledosmonitor = false;
	}

	public void disableMonitoring()
	{
		this.disabledosmonitor = true;
	}

	public void createIPDOSLock(String ip)
	{
		IPDOSLock lock = new IPDOSLock(ip);
		lock.isBlocked();
		ipLockMap.put(ip,lock);
	}

	public boolean hasIPEntry(String ip)
	{
		if(ipLockMap!=null && ipLockMap.get(ip)!=null)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public boolean checkIPBlocked(String ip)
	{
		if(ipLockMap!=null && ipLockMap.get(ip)!=null)
		{
			return ((ipLockMap.get(ip)).checkIPBlocked());
		}
		else
                {
                        return false;
                }
	}

	public boolean isIPBlocked(String ip)
	{
		try
		{
			if(disabledosmonitor)
			{
				return false;
			}
			if(ipLockMap.get(ip) == null)
			{
				createIPDOSLock(ip);
				return false;
			}
			return ipLockMap.get(ip).isBlocked();
		}
		catch(Exception ex)
		{
			return false;
		}
	}

	public boolean isSuspectedIP(String ip)
	{
		try
		{
			if(disabledosmonitor)
			{
				return false;
			}
			if(ipLockMap.get(ip) == null)
			{
				createIPDOSLock(ip);
				return false;
			}
			return ipLockMap.get(ip).isSuspectedIP();
		}
		catch(Exception ex)
		{
			return false;
		}
	}

	public void doURLHit(String ip, String url)
	{
		try
		{
			if(urlThresholdMap.get(url) == null)
			{
				return;
			}
			if(ipLockMap.get(ip) == null)
			{
				createIPDOSLock(ip);
			}
			ipLockMap.get(ip).urlHit(url);
		}
		catch(Exception ex)
		{
		}
	}

	public ConcurrentHashMap getIPDOSLockMap()
	{
		return ipLockMap;
	}

	public void clearIPDOSLocks()
	{
		ipLockMap.clear();
	}

	public void clearIPDOSLock(String ip)
	{
		ipLockMap.remove(ip);
	}

	class IPDOSLock
	{
		AtomicInteger counter = new AtomicInteger(0);
		long lastupdate = System.currentTimeMillis();
		long lastactive = System.currentTimeMillis();
		boolean blocked = false;
		boolean suspected = false;
		long blockedtime = -1;
		private ConcurrentHashMap<String,URLDOSLock> urlLockMap = new ConcurrentHashMap<String,URLDOSLock>();
		String ip = null;

		public IPDOSLock(String ip)
		{
			this.ip = ip;
		}

		public boolean checkIPBlocked()
		{
			lastactive = System.currentTimeMillis();

			if(counter.get() < 0)
			{
				counter.set(0);
			}
			if(blockedtime == -1)
			{
				return false;
			}

			if((System.currentTimeMillis() - blockedtime) > (blockperiod*60*1000))
			{
				return false;
			}

			return blocked;
		}

		public boolean isSuspectedIP()
		{
			lastactive = System.currentTimeMillis();

			try
			{
				if((System.currentTimeMillis() - lastupdate) > (suspectperiod*1000))
				{
					lastupdate = System.currentTimeMillis();
					counter.set(1);
					suspected=false;
					this.urlLockMap.clear();
				}

				logger.log(Level.FINE,"DOSCounter for IP "+ip+" :"+counter.get(),IPDOSLock.class.getName(),"isSuspectedIP");
				
				if(counter.get() >= suspectthreshold)
				{
					if(!suspected)
					{
						logger.log(Level.INFO,"Suspection on IP "+ip +" , Hit for "+suspectperiod+ " seconds , Counter Reached "+counter.get()+" . URL Monitoring Enabled",IPDOSLock.class.getName(),AWSLogMethodConstants.IS_SUSPECTED_IP);
						if(ConfManager.isAnomalyHandlerEnabled()) 
						{	
							sendAlert(ip, ""+suspectperiod, ""+counter.get());
						}
						suspected=true;
					}
					if(counter.get() >= maxiphitthreshold)
					{
						block();
						logger.log(Level.INFO,"Max hit threshold reached for IP "+ip+" , Hit "+counter.get()+" for "+suspectperiod+" seconds . Restriction enabled. Block Period "+blockperiod,IPDOSLock.class.getName(),AWSLogMethodConstants.IS_SUSPECTED_IP);
						if(ConfManager.isAnomalyHandlerEnabled()) 
						{
							sendAlert(ip, ""+suspectperiod, ""+counter.get(), ""+blockperiod);
						}
					}
					return true;
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",IPDOSLock.class.getName(),AWSLogMethodConstants.IS_SUSPECTED_IP, ex);
			}
			return false;
		}

		private void sendAlert(String ip, String suspectperiod, String counter)
		{
			Hashtable<String, String> anomalyinfo = new Hashtable<>();
			anomalyinfo.put("anomaly_type", AWSConstants.DOS_MONITOR);
			anomalyinfo.put(AWSConstants.TYPE, "suspect");
			anomalyinfo.put("suspectedip", ip);
			anomalyinfo.put("suspectperiod", suspectperiod);
			anomalyinfo.put("hits", counter);
			AnomalyHandler.handleAnomaly(AWSConstants.DOS_MONITOR, anomalyinfo);
		}

		private void sendAlert(String ip, String suspectperiod, String counter, String blockperiod)
		{
			Hashtable<String, String> anomalyinfo = new Hashtable<>();
			anomalyinfo.put("anomaly_type", AWSConstants.DOS_MONITOR);
			anomalyinfo.put(AWSConstants.TYPE, "block");
			anomalyinfo.put("blockedip", ip);
			anomalyinfo.put("blockperiod", blockperiod);
			anomalyinfo.put("suspectperiod", suspectperiod);
			anomalyinfo.put("hits", counter);
			AnomalyHandler.handleAnomaly(AWSConstants.DOS_MONITOR, anomalyinfo);
		}

		public void urlHit(String url)
		{
			lastactive = System.currentTimeMillis();

			if(blocked || (!block && (blockedtime != -1) && ((System.currentTimeMillis() - blockedtime) < (blockperiod*60*1000))))
			{
				return;
			}
			if(urlThresholdMap.get(url) == null)
			{
				return;
			}
			URLDOSLock urlLock = urlLockMap.get(url);
			if(urlLock == null)
			{
				urlLock = new URLDOSLock(urlThresholdMap.get(url).shortValue());
				urlLockMap.put(url,urlLock);
			}
			if(urlLock.isSuspectedURL())
			{
				logger.log(Level.FINE,"URL Hit Exceed Limit("+urlThresholdMap.get(url)+") for "+url+" . Restriction Enabled for IP "+ip+" . Block Period - "+blockperiod,IPDOSLock.class.getName(),AWSLogMethodConstants.URLHIT);
				block();
			}
		}

		public void block()
		{
			lastactive = System.currentTimeMillis();

			if(block)
			{
				this.blocked = true;
			}
			blockedtime = System.currentTimeMillis();
			//urlLockMap.clear();
		}

		public boolean isBlocked()
		{
			lastactive = System.currentTimeMillis();

			try
			{
				if(counter.get() < 0)
				{
					counter.set(0);
				}
				if(blockedtime == -1)
				{
					counter.incrementAndGet();
					return false;	
				}
				if((System.currentTimeMillis() - blockedtime) > (blockperiod*60*1000))
				{
					logger.log(Level.FINE,"Lock period over for IP "+ip+" , Disabling URL Monitoring",IPDOSLock.class.getName(),AWSLogMethodConstants.ISBLOCKED);
					this.blocked = false;
					this.blockedtime = -1;
					this.urlLockMap.clear();
					this.suspected = false;
					this.counter.set(0);
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",IPDOSLock.class.getName(),AWSLogMethodConstants.ISBLOCKED, ex);
			}
			return blocked;
		}

		public ConcurrentHashMap getURLDOSLockMap()
		{
			return urlLockMap;
		}

		public void clearURLDOSLocks()
		{
			urlLockMap.clear();
		}

		public void clearURLDOSLock(String url)
		{
			urlLockMap.remove(url);
		}

		public long getLastActivityTime()
		{
			return lastactive;
		}
	}

	class URLDOSLock
	{
		AtomicInteger counter = new AtomicInteger();
		long lastupdate = System.currentTimeMillis();
		long lastactive = System.currentTimeMillis();
		short threshold = -1;
		boolean blocked = false;

		public URLDOSLock(short threshold)
		{
			this.threshold = threshold;
		}

		public boolean isSuspectedURL()
		{
			lastactive = System.currentTimeMillis();

			try
			{
				if(counter.get() < 0)
				{
					counter.set(0);
				}
				if(blocked)
				{
					return true;
				}
				if(counter.get() >= threshold && ((System.currentTimeMillis() - lastupdate) <= (monitorsec * 1000)))
				{
					lastupdate = System.currentTimeMillis();
					counter.set(0);
					blocked = true;
					return true;
				}
				if((System.currentTimeMillis() - lastupdate) > (monitorsec * 1000))
				{	
					lastupdate = System.currentTimeMillis();
					counter.set(0);
				}
				counter.incrementAndGet();
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",URLDOSLock.class.getName(),AWSLogMethodConstants.IS_SUSPECTED_URL, ex);
			}
			return false;
		}
		
		public long getLastActivityTime()
		{
			return lastactive;
		}
	}
}
