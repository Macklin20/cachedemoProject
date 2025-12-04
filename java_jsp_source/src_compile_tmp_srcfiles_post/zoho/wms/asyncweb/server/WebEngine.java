//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.ArrayList;
import java.io.IOException;

import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

import com.adventnet.wms.common.constants.AWSEngines;

public class WebEngine
{
	private static ConcurrentHashMap<String, AbstractWebEngine> engineMap = new ConcurrentHashMap();
	private static ConcurrentHashMap domainMap = new ConcurrentHashMap();
	private static ConcurrentHashMap portEngineMap = new ConcurrentHashMap();
	private static ConcurrentHashMap enginePorts = new ConcurrentHashMap();
	private static AbstractWebEngine defaultengine;

	private static AsyncLogger logger = new AsyncLogger(WebEngine.class.getName());
	
	public static boolean initialize()
	{
		try
		{
			Properties prop = Util.getProperties(ConfManager.getEngineMap());
			loadDefaultEngine((String)prop.remove(AWSConstants.DEFAULT));
			loadEngineMap(prop);
			loadDomainMap();	
			loadPortEngineMap();
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ",WebEngine.class.getName(),AWSLogMethodConstants.INITIALIZE, ex);
			return false;
		}
		return true;
	}

	public static void loadGridEngine() throws Exception
	{
		loadGridEngine(AWSConstants.GRID_PORT);
	}

	public static void loadGridEngine(int port) throws Exception
	{
		if(ConfManager.isGridEngineActive())
		{
			logger.log(Level.SEVERE, "AWS GRID ENGINE IS ALREADY RUNNING IN PORT : {0}",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_GRID_ENGINE, new Object[]{ConfManager.getGridPort()});//No I18n
			return;
		}
		Properties prop = Util.getProperties(ConfManager.getEngineMap());
		String value = ((String)prop.remove(AWSConstants.GRID_APPNAME));
		String appname = AWSConstants.GRID_APPNAME;
		int threadcount = 10;
		String classname = value.split(",")[0];

		try
		{
			threadcount = Integer.parseInt(value.split(",")[1]);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE,"THREAD COUNT NOT PRESENT FOR GRID ENGINE "+appname+" PROCEEDING WITH DEFAULT "+threadcount,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_GRID_ENGINE);//No I18N
		}

		int maxthreadcount = threadcount;

		try
		{
			maxthreadcount = Integer.parseInt(value.split(",")[2]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE,"MAX THREAD COUNT NOT PRESENT FOR GRID ENGINE "+appname+" PROCEEDING WITH DEFAULT "+threadcount,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_GRID_ENGINE);//No I18N
		}

		long katime = 100L;

		try
		{
			katime = Long.parseLong(value.split(",")[3]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"KEEPALIVE TIME NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH DEFAULT.",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_GRID_ENGINE);//NO I18N
		}

		int tpexecutor = 1;

		try
		{
			tpexecutor = Integer.parseInt(value.split(",")[4]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"TPEXECUTOR COUNT NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH DEFAULT",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_GRID_ENGINE);//NO I18N
		}

		int qsize = -1;

		try
		{
			qsize = Integer.parseInt(value.split(",")[5]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"BLOCKING QUEUE SIZE NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH UNBOUNDED BLOCKING QUEUE",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_GRID_ENGINE);//NO I18N
		}

		boolean rejectionHandler = false;

		try
		{
			rejectionHandler = Boolean.parseBoolean(value.split(",")[6]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"REJECTION HANDLER STATUS NOT PRESENT FOR ENGINE :: "+appname,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_GRID_ENGINE);//NO I18N
		}

		AbstractWebEngine gridEngine = getAbstractWebEngine(appname, classname);
		gridEngine.doInit(appname,threadcount,maxthreadcount,katime,tpexecutor,qsize,rejectionHandler);
		gridEngine.initializeEngine();
		portEngineMap.put(""+port,gridEngine);
		ConfManager.setGridEnginePort(port);
		ConfManager.setGridEngineActive(true);
		ConfManager.registerWMSRuntimeProps();
	}

	private static AbstractWebEngine getAbstractWebEngine(String appname, String classname) throws Exception
	{
		if(engineMap.containsKey(appname))
		{
			return (AbstractWebEngine)engineMap.get(appname);
		}
		Class classfile = Class.forName(classname);
		AbstractWebEngine absengine = (AbstractWebEngine)classfile.newInstance();
		engineMap.put(appname, absengine);
		return absengine;
	}

	private static void loadDefaultEngine(String value) throws Exception
	{
		String appname = AWSConstants.DEFAULT;
		int threadcount = 10;

		String classname = value.split(",")[0];
		try
		{
			threadcount = Integer.parseInt(value.split(",")[1]);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE,"THREAD COUNT NOT PRESENT FOR ENGINE "+appname+" PROCEEDING WITH DEFAULT "+threadcount,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DEFAULT_ENGINE);//No I18N
		}
		int maxthreadcount = threadcount;

		try
		{
			maxthreadcount = Integer.parseInt(value.split(",")[2]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE,"MAX THREAD COUNT NOT PRESENT FOR ENGINE "+appname+" PROCEEDING WITH DEFAULT "+threadcount,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DEFAULT_ENGINE);//No I18N
		}

		long katime = 100L;

		try
		{
			katime = Long.parseLong(value.split(",")[3]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"KEEPALIVE TIME NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH DEFAULT.",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DEFAULT_ENGINE);//NO I18N
		}

		int tpexecutor = 1;

		try
		{
			tpexecutor = Integer.parseInt(value.split(",")[4]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"TPEXECUTOR COUNT NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH DEFAULT",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DEFAULT_ENGINE);//NO I18N
		}

		int qsize = -1;

		try
		{
			qsize = Integer.parseInt(value.split(",")[5]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"BLOCKING QUEUE SIZE NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH UNBOUNDED BLOCKING QUEUE",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DEFAULT_ENGINE);//NO I18N
		}

		boolean rejectionHandler = false;

		try
		{
			rejectionHandler = Boolean.parseBoolean(value.split(",")[6]);	
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"REJECTION HANDLER STATUS NOT PRESENT FOR ENGINE :: "+appname,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DEFAULT_ENGINE);//NO I18N
		}

		defaultengine = getAbstractWebEngine(appname, classname);
		defaultengine.doInit(appname,threadcount,maxthreadcount,katime,tpexecutor,qsize,rejectionHandler);
		defaultengine.initializeEngine();
	}

	private static void loadEngineMap(Properties prop) throws Exception
	{
		Enumeration propenum = prop.propertyNames();
		while(propenum.hasMoreElements())
		{
			String appname = (String)propenum.nextElement();
			String value = prop.getProperty(appname);
			int threadcount = 10;

			String classname = value.split(",")[0];
			try
			{
				threadcount = Integer.parseInt(value.split(",")[1]);
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE,"THREAD COUNT NOT PRESENT FOR ENGINE "+appname+" PROCEEDING WITH DEFAULT "+threadcount,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_ENGINE_MAP);//NO I18N
			}
			int maxthreadcount = threadcount;

			try
			{
				maxthreadcount = Integer.parseInt(value.split(",")[2]);	
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE,"MAX THREAD COUNT NOT PRESENT FOR ENGINE "+appname+" PROCEEDING WITH DEFAULT "+threadcount,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_ENGINE_MAP);//NO I18N
			}

			long katime = 100L;

			try
			{
				katime = Long.parseLong(value.split(",")[3]);	
			}
			catch(Exception ex)
			{
				logger.log(Level.FINE,"KEEPALIVE TIME NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH DEFAULT.",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_ENGINE_MAP);//NO I18N
			}

			int tpexecutor = 1;

			try
			{
				tpexecutor = Integer.parseInt(value.split(",")[4]);	
			}
			catch(Exception ex)
			{
				logger.log(Level.FINE,"TPEXECUTOR COUNT NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH DEFAULT",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_ENGINE_MAP);//NO I18N
			}

			int qsize = -1;

			try
			{
				qsize = Integer.parseInt(value.split(",")[5]);	
			}
			catch(Exception ex)
			{
				logger.log(Level.FINE,"BLOCKING QUEUE SIZE NOT PRESENT FOR ENGINE :: "+appname+", PROCEEDING WITH UNBOUNDED BLOCKING QUEUE",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_ENGINE_MAP);//NO I18N
			}

			boolean rejectionHandler = false;

			try
			{
				rejectionHandler = Boolean.parseBoolean(value.split(",")[6]);	
			}
			catch(Exception ex)
			{
				logger.log(Level.FINE,"REJECTION HANDLER STATUS NOT PRESENT FOR ENGINE :: "+appname,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_ENGINE_MAP);//NO I18N
			}

			AbstractWebEngine engine = getAbstractWebEngine(appname, classname);
			engine.doInit(appname,threadcount,maxthreadcount,katime,tpexecutor,qsize,rejectionHandler);
			engine.initializeEngine();
		}	
	}	

	private static void loadDomainMap() throws Exception
	{
		if(domainMap.size() > 0)
		{
			domainMap = new ConcurrentHashMap();
		}

		try
		{
			Properties prop = Util.getProperties(ConfManager.getDomainMap());
			if(prop == null)
			{
				logger.log(Level.INFO, "Mapped domain : File {0} Not Found.",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DOMAIN_MAP, new Object[]{ConfManager.getDomainMap()});//No I18n
				return;
			}
			Enumeration propenum = prop.propertyNames();
			while(propenum.hasMoreElements())
			{
				String domainname = (String)propenum.nextElement();
				String appname = prop.getProperty(domainname);
				AbstractWebEngine engine = (AbstractWebEngine) engineMap.get(appname);
				domainMap.put(domainname,engine);
			}
			logger.log(Level.INFO, "Mapped domain : {0}",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DOMAIN_MAP, new Object[]{domainMap});//No I18n
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE,"unable to load domain map ",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_DOMAIN_MAP, ex);//No I18n
		}
	}

	private static void loadPortEngineMap() throws Exception
	{
		try
		{
			if(portEngineMap.size() > 0)
			{
				portEngineMap = new ConcurrentHashMap();
				if(engineMap.containsKey(AWSConstants.GRID_APPNAME) && ConfManager.getGridPort() != -1)
				{
					AbstractWebEngine gridEngine = (AbstractWebEngine) engineMap.get(AWSConstants.GRID_APPNAME);
					portEngineMap.put(""+ConfManager.getGridPort(), gridEngine);
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in reloading portengine map : "+portEngineMap+", enginemap : "+engineMap,WebEngine.class.getName(),AWSLogMethodConstants.LOAD_PORT_ENGINE_MAP, ex);//No I18n
		}
		try
		{
			Properties prop = Util.getProperties(ConfManager.getPortEngineMap());
			if(prop != null)
			{
				Enumeration propenum = prop.propertyNames();
				while(propenum.hasMoreElements())
				{
					int port = Integer.parseInt((String)propenum.nextElement());
					String appname = prop.getProperty(""+port);
					enginePorts.put(appname,port);
					if(appname.equals(AWSConstants.GRID_APPNAME))
					{
						ConfManager.setGridEnginePort(port);
						ConfManager.setGridEngineActive(true);
						ConfManager.registerWMSRuntimeProps();
					}
					AbstractWebEngine engine = (AbstractWebEngine) engineMap.get(appname);
					portEngineMap.put(""+port,engine);
				}
				logger.log(Level.INFO,"[PORT ENGINE MAP]["+portEngineMap+"]",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_PORT_ENGINE_MAP);
			}
			else
			{
				logger.log(Level.INFO,"[PORT ENGINE MAP NOT PRESENT]"+ConfManager.getPortEngineMap(),WebEngine.class.getName(),AWSLogMethodConstants.LOAD_PORT_ENGINE_MAP);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.WARNING,"[PORT ENGINE MAP LOADING ISSUE]["+ConfManager.getPortEngineMap()+"]",WebEngine.class.getName(),AWSLogMethodConstants.LOAD_PORT_ENGINE_MAP,ex);
		}
	}

	public static int getEnginePortByAppName(String appname)
	{
		if(enginePorts.containsKey(appname))
		{
			return (int)enginePorts.get(appname);
		}
		return -1;
	}

	public static AbstractWebEngine getEngineByAppName(String appname)
	{
		if(appname.equals(AWSConstants.DEFAULT))
		{
			return getDefaultEngine();
		}
		return ((AbstractWebEngine) engineMap.get(appname));
	}

	public static AbstractWebEngine getEngineByDomainName(String domainname)
	{
		try
		{
			return ((AbstractWebEngine) domainMap.get(domainname));
		}
		catch(Exception ex)
		{
			logger.log(Level.WARNING,"Engine Not Available for Domain :: "+domainname,WebEngine.class.getName(),AWSLogMethodConstants.GET_ENGINE_BY_DOMAINNAME);//NO I18N
		}
		return null;
	}

	public static AbstractWebEngine getEngineByPort(int port)
	{
		return ((AbstractWebEngine) portEngineMap.get(""+port));
	}

	public static AbstractWebEngine getDefaultEngine()
	{
		return defaultengine;
	}

	public static AbstractWebEngine getEngine(String domainName, int port)
	{
		try
		{
			AbstractWebEngine engine = null;
			if(domainName != null)
			{
				engine = getEngineByDomainName(domainName);
			}
			if( engine == null && ((engine = getEngineByPort(port)) == null))
			{
				engine = getDefaultEngine();
			}
			return engine;
		}
		catch(Exception ex)
		{
		}
		return getDefaultEngine();
	}

	public static ArrayList<String> getAllEngineName()
	{
		ArrayList<String> list = new ArrayList<String>();
		try
		{
			for(String engineName : engineMap.keySet())
			{
				list.add(engineName);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in get all webengine name : ",WebEngine.class.getName(),AWSLogMethodConstants.GET_ALL_ENGINE_NAME, ex);//No I18n
		}
		return list;
	}

}
