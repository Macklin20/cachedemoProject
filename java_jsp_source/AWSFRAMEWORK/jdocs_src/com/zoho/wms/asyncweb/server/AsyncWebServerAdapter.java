//$Id$
package com.zoho.wms.asyncweb.server;

import com.adventnet.wms.servercommon.logging.ConsoleLogger;
import com.adventnet.wms.servercommon.runtime.RuntimeAdmin;
import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;
import com.zoho.wms.asyncweb.server.ssl.SSLStartUpTypes;
import com.zoho.wms.asyncweb.server.ssl.SSLManagerFactory;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.exception.AWSStartupException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogsProcessor;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.ArrayList;
import java.io.PrintStream;

public class AsyncWebServerAdapter 
{
	public static AsyncLogger logger = new AsyncLogger(AsyncWebServerAdapter.class.getName());
	
	private static Hashtable portservermap= new Hashtable();
	private static boolean enableconsolelogger = true;
	private static boolean reinitconf = false;
	/**
	 * This method is used to set server home
	 * @param serverhome - directory path of server home
	 */

	public static void setServerHome(String serverhome)
	{
		ConfManager.setServerHome(serverhome);
	}

	/**
	 * This method is used to get server home
	 */

	public static String getServerHome()
	{
		return ConfManager.getServerHome();
	}

	/**
	 * This method is to set security context path
	 * @param contextpath - directory path of security context files
	 */

	public static void setSecurityContextHome(String contextpath)
	{
		ConfManager.setSecurityContextHome(contextpath);
	}

	/**
	 * This method is to get security context path
	 */

	public static String getSecurityContextHome()
	{
		return ConfManager.getSecurityContextHome();
	}

	/**
	 * To set server check status of the server
	 */

	public static void setServerCheckStatus(boolean status)
	{
		ConfManager.setServerCheckStatus(status);
	}

	/**
	 * To get server check status of the server
	 */

	public static boolean getServerCheckStatus()
	{
		return ConfManager.getServerCheckStatus();
	}

	/**
	 * To check if a port initiated by the server is running 
	 */

	public static boolean isRunning(int port)
	{
		return ConfManager.isRunning(port);
	}

	/** 		
	 *This method is used to disable console logger		
	 */		
					
	public static void disableConsoleLogger()		
	{		
        	enableconsolelogger = false;		
	}		

	/**
	 * Invoke this method to check wheather the server is currently in read only state
	 */

	public static boolean isReadOnlyMode()
	{
		return ConfManager.isReadOnlyMode();
	}	

	/**
	 * Invoke this method to set the server state as read only. 
	 * All servercheck requests will be responded with 300 if read only.
	 */

	public static void setReadOnlyMode()
	{
		AWSSecurityFilter.resetInit();
		ConfManager.setReadOnlyMode(true);
	}

	/**
	 * Invoke this method to set the server state as read/write
	 * All servercheck requests will be responsded with 200 henceforth
	 */

	public static void setReadWriteMode()
	{
		AWSSecurityFilter.resetInit();
		ConfManager.setReadOnlyMode(false);
	}
	
	/**
         * This method is used to set keepalive status of the server
	 * @param status - true, if enabled
	 *                 false, if disabled
	 */
 
	public static void setKeepAlive(boolean status)
	{
		ConfManager.setKeepAlive(status);
	}

	/**
	 * This method is used to set valid domains for the server
	 * @param domains - The domains that are needed to be set
	 */
	
	public static void setValidDomains(String domains)
	{
		ConfManager.setValidDomains(domains);	
	}

	/**
	 * Invoke this method to reinitialize confs
	 */

	public static void reinitializeConfs() throws Exception
	{
		reinitconf = true;
		if(!ConfManager.initialize(true))
		{
			throw new AWSStartupException("Unable to restart the server ConfManager initialization failed");//No I18N
		}
		else
		{
			logger.log(Level.INFO,"Adapter conf reinitialized successfully",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.REINITIALIZE_CONFS);
		}

		if(!WebEngine.initialize())
		{
			throw new AWSStartupException("Unable to restart the server->WebEngine initialization failes");//No I18N
		}
		else
		{
			logger.log(Level.INFO,"WebEngine reinitialized successfully",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.REINITIALIZE_CONFS);
		}

		if(!AsyncWebNetDataProcessor.reinit())
		{
			logger.log(Level.INFO, "Netdata Processor reinit falied !!!",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.REINITIALIZE_CONFS);//No I18n
		}

		if(!AsyncRequestProcessor.reinit())
		{
			logger.log(Level.INFO, "Request Processor reinit falied !!!",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.REINITIALIZE_CONFS);//No I18n
		}

		if(ConfManager.isSecurityFilterEnabled())
		{		
			AWSSecurityFilter.resetInit();
			AWSSecurityFilter.initialize();
		}
	}
	
	public static boolean isReinitConf()
	{
		return reinitconf;
	}
	
	public static void setConfFileExtension(String extension)
	{
		ConfManager.setConfFileExtension(extension);
	}

	/**
	 * This method is used to initialize a http, a internal and a set of ssl ports mentioned in awsadapterconf.properties and sslservers.properties
	 */

	public static void initialize() throws Exception
	{
		initialize(null);
	}

	/**
	 * This method is used to initialize a http, a internal and a set of ssl ports
	 * @param portconf - A Hashtable with details of webserver.port,webserver.sslservers (key of hastable)
	 *
	 * Note: 
	 *
	 * 1.Multiple ssl ports can be initialized.
	 * 2.The value of webserver.sslservers should of type Properties whose key and values should be represented in form servername=port,domain,sslstartuptype. Ex: zoho=8080,*.zoho.com,1
	 *   sslstartuptype -- 0 - default, 1 - offloader , 2 - none
	 */

	public static void initialize(Hashtable portconf) throws Exception
	{
		if(enableconsolelogger)
		{
			try
			{
				ConsoleLogger consoleLog = new ConsoleLogger(AWSConstants.DEFAULT);
				System.setOut(new PrintStream(consoleLog,true));
				System.setErr(new PrintStream(consoleLog,true));
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE, ex);
			}
		}
		if(!ConfManager.isInitialized())
		{
			if(!ConfManager.initialize(true))
			{
				logger.log(Level.SEVERE,"Unable to start the server ConfManager initialization failed",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE);//No I18N
				System.exit(1);
			}
		}

		if(portconf != null && portconf.get("webserver.sslservers") != null && portconf.get("webserver.sslservers") instanceof Properties)
		{
			ConfManager.addSSLPorts((Properties)portconf.get("webserver.sslservers"),false);
		}
		
		if(!WebEngine.initialize())
                {
                        logger.log(Level.SEVERE,"Unable to start the server->WebEngine initialization failes",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE);//No I18N
                        System.exit(1);
                }
		
		if(!DOSManager.initialize())
		{
			logger.log(Level.SEVERE,"unable to start DOSManager",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE);//No I18N
		}

		if(ConfManager.isSecurityFilterEnabled())
		{		
			AWSSecurityFilter.initialize();
		}
		ConfManager.setWMSServer();

		SSLManagerFactory.initialize();

                AsyncWebEventHandler evh = new AsyncWebEventHandler();

                int connectors = ConfManager.getConnectorsCount();
		if(portconf != null && portconf.get("webserver.port")!= null)
		{
			try
			{
				int webserverport = Integer.parseInt((String)portconf.get("webserver.port"));
				if(!ConfManager.isWebServerPort(webserverport))
				{
					ConfManager.addWebServerPort(webserverport);
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO,"Given webserver port value is wrong ["+portconf.get("webserver.port")+"] , ",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE,ex);
			}
		}
		
		if(ConfManager.isSelectorPoolMode())
                {
                        SelectorPoolFactory.init(evh);
                }	

		HashSet webserverportlist = ConfManager.getWebServerPortList();
		Iterator wpitr = webserverportlist.iterator();
		
		while(wpitr.hasNext())
		{
			int webserverport = Integer.parseInt((String)wpitr.next());

			if(webserverport != -1)
			{
				for(int i=0; i < connectors; i++, webserverport++)
				{
					try
					{
						AsyncWebServer server = new AsyncWebServer(webserverport, evh);
						server.start();
						portservermap.put(""+webserverport,server);
						logger.log(Level.INFO,"Connector Started Successfully : Listening @ "+webserverport,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE);
					}
					catch(Exception ex)
					{
						logger.log(Level.INFO,"ALREADY BOUND WEBSERVER PORT : "+ webserverport,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE, ex);
						if(ConfManager.toThrowBindExp())
						{
							throw new AWSStartupException("Unable to init webserver port : "+webserverport);//No I18N
						}
					}
				}
			}
		}
		
		if(ConfManager.isSSLDefault() || ConfManager.isSSLOffloader())
		{
			boolean sslstarted = false;
			if(portconf != null && portconf.get("webserver.sslservers") != null && (portconf.get("webserver.sslservers") instanceof Properties))
			{
				try
				{
					startSSLServers(evh,(Properties)portconf.get("webserver.sslservers"));
					sslstarted = true;
				}
				catch(Exception ex)
				{
					logger.log(Level.WARNING,"Given SSL Port "+portconf,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE,ex);
				}
			}
			if(!sslstarted)
			{
				startSSLServers(evh);
			}
		}
		else
		{
			logger.log(Level.INFO,"SSL Connector Disabled",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE);
		}


                ConfManager.setServerStartTime(System.currentTimeMillis());
		ConfManager.setServerStartStatus(true);

                logger.log(Level.INFO,"Server Started Successfully",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.INITIALIZE);

	}

	private static void startSSLServers(AsyncWebEventHandler evh) throws Exception
	{
		startSSLServers(evh,null);
	}

	private static void startSSLServers(AsyncWebEventHandler evh, Properties sslserverprops) throws Exception
	{
		if(sslserverprops == null)
		{
			sslserverprops = ConfManager.getSSLConf();
		}
		Enumeration en = sslserverprops.propertyNames();
		
		while(en.hasMoreElements())
		{
			String srvname = (String)en.nextElement();
			String val = sslserverprops.getProperty(srvname);
			
			String[] valarr = val.split(",");
			int port = Integer.parseInt(valarr[0]);
			String domain = valarr[1];
			int startuptype = Integer.parseInt(valarr[2]);
			int authmode = -1;
			try
			{
				authmode = Integer.parseInt(valarr[3]);
			}
			catch(Exception mex)
			{
				authmode = -1;
			}
			ConfManager.setClientAuthMode(""+port, authmode);
			try
			{
				switch(startuptype)
				{
					
					case SSLStartUpTypes.DEFAULT:
						AsyncWebSSLServer sslsrv = new AsyncWebSSLServer(port,evh);
						sslsrv.start();
						portservermap.put(""+port,sslsrv);
						logger.log(Level.INFO,"SSL Server Started on "+port+" For Domain "+domain,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.STARTSSLSERVERS);
						break;
					case SSLStartUpTypes.OFFLOADER:
						AsyncWebServer srv = new AsyncWebServer(port,evh);
						srv.start();
						portservermap.put(""+port,srv);
						logger.log(Level.INFO,"Non-SSL(Offloader mode) Server Started on "+port+" For Domain "+domain,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.STARTSSLSERVERS);
						break;
					case SSLStartUpTypes.NONE:
						logger.log(Level.INFO,"Startup Type None For Domain "+domain+" , Server Not Started on Port "+port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.STARTSSLSERVERS);
						break;					
					
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO,"ALREADY BOUND SSL PORT :"+port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.STARTSSLSERVERS,ex);
				if(ConfManager.toThrowBindExp())
				{
					throw new AWSStartupException("Unable to init ssl port : "+port);//No I18N
				}
			}
		}
	}

	/** 
	 * This method is used to set Load Balancer ssl ip ranges
	 * ips- list of ssl ips seperated by comma
	 */

	public static void setLBSSLIP(String ips)
	{
		if(ips != null)
		{
			ConfManager.setLBSSLIps(ips);
		}
	}

	/**
	 * This method is used to set read limit
	 * @param readlimit - read buffer limit
	 */

	public static void setReadLimit(int readlimit)
	{
		ConfManager.setReadLimit(readlimit);
	}

	public static int getReadLimit()
	{
		return ConfManager.getReadLimit();
	}

	/**
	 * To restart server with desired configurations
	 * @param portconf - A Hashtable with details of webserver.port,webserver.sslservers (key of hastable)
	 *
	 * Note: 
	 *
	 * 1.Multiple ssl ports can be initialized.
	 * 2.The value of webserver.sslservers should of type Properties whose key and values should be represented in form servername=port,domain,sslstartuptype. Ex: zoho=8080,*.zoho.com,1
	 *   sslstartuptype -- 0 - default, 1 - offloader , 2 - none
	 *
	 */

	public static void restartAWSAdapter(Hashtable portconf) throws Exception
	{
		shutdown();
		if(portconf != null && portconf.get("webserver.sslservers") != null && portconf.get("webserver.sslservers") instanceof Properties)
		{
			ConfManager.addSSLPorts((Properties)portconf.get("webserver.sslservers"),true);
		}
		SSLManagerFactory.initialize();

		AsyncWebEventHandler evh = new AsyncWebEventHandler();

		int connectors = ConfManager.getConnectorsCount();
                int webserverport = ConfManager.getWebServerPort();
		
		if(portconf != null && portconf.get("webserver.port")!= null)
		{
			try
			{
				webserverport = Integer.parseInt((String)portconf.get("webserver.port"));
				if(!ConfManager.isWebServerPort(webserverport))
				{
					ConfManager.addWebServerPort(webserverport,true);
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO,"Given webserver port value is wrong ["+webserverport+"] , ",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.RESTART_AWS_ADAPTER,ex);
			}
		}
		
		if(ConfManager.isSelectorPoolMode())
                {
                        SelectorPoolFactory.init(evh);
                }

		if(webserverport != -1)
		{
			for(int i=0; i < connectors; i++, webserverport++)
			{
				
				try
				{
					AsyncWebServer server = new AsyncWebServer(webserverport, evh);
					server.start();
					portservermap.put(""+webserverport,server);
					logger.log(Level.INFO,"Connector Started Successfully : Listening @ "+webserverport,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.RESTART_AWS_ADAPTER);
				}
				catch(Exception ex)
				{
					logger.log(Level.INFO,"ALREADY BOUND WEBSERVER PORT : "+ webserverport,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.RESTART_AWS_ADAPTER,ex);
				}
			}
		}
		
		if(ConfManager.isSSLDefault() || ConfManager.isSSLOffloader())
		{
			boolean sslstarted = false;
			if(portconf != null && portconf.get("webserver.sslservers") != null && (portconf.get("webserver.sslservers") instanceof Properties))
			{
				try
				{
					startSSLServers(evh,(Properties)portconf.get("webserver.sslservers"));
					sslstarted = true;
				}
				catch(Exception ex)
				{
					logger.log(Level.WARNING,"Given SSL Port "+portconf+" , ",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.RESTART_AWS_ADAPTER,ex);
				}
			}
			if(!sslstarted)
			{
				startSSLServers(evh);
			}
		}
		else
		{
			logger.log(Level.INFO,"SSL Connector Disabled",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.RESTART_AWS_ADAPTER);
		}

	}
		
	
	public static void shutdown() throws Exception
	{
		if(!portservermap.isEmpty())
		{
			SelectorPoolFactory.shutdown();
                	closeSockets();
               	 	portservermap.clear();	
		}

		ConfManager.clearWebServerPortList();
		ConfManager.clearSSLConf();
	}
	
	/**
	 * To start new ports in addition to the already running ones
	 * @param portconf - A Hashtable with details of webserver.port,webserver.sslservers (key of hastable)
	 *
	 * Note: 
	 *
	 * 1.Multiple ssl ports can be initialized.
	 * 2.The value of webserver.sslservers should of type Properties whose key and values should be represented in form servername=port,domain,sslstartuptype. Ex: zoho=8080,*.zoho.com,1
	 *   sslstartuptype -- 0 - default, 1 - offloader , 2 - none
	 */
	 
	public static void startPort(Hashtable portconf) throws Exception
	{
		
		if(portconf != null && portconf.get("webserver.sslservers") != null && portconf.get("webserver.sslservers") instanceof Properties)
		{
			ConfManager.addSSLPorts((Properties)portconf.get("webserver.sslservers"),false);
		}
		SSLManagerFactory.initialize();
		
		AsyncWebEventHandler evh = new AsyncWebEventHandler();

		int connectors = ConfManager.getConnectorsCount();
		int webserverport = -1;
		if(portconf != null && portconf.get("webserver.port")!= null)
		{
			try
			{
				webserverport = Integer.parseInt((String)portconf.get("webserver.port"));
				ConfManager.addWebServerPort(webserverport,false);
				SelectorPoolFactory.initPort(Integer.toString(webserverport),evh);
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO,"Given webserver port value is wrong ["+webserverport+"] , ",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_PORT,ex);
			}
		}
		
		if(webserverport != -1)
		{
			for(int i=0; i < connectors; i++, webserverport++)
			{
				
				try
				{
					AsyncWebServer server = new AsyncWebServer(webserverport, evh);
					server.start();
					ConfManager.addWebServerPort(webserverport,false);
					portservermap.put(""+webserverport,server);
					logger.log(Level.INFO,"Connector Started Successfully : Listening @ "+webserverport,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_PORT);
				}
				catch(Exception ex)
				{
					logger.log(Level.INFO,"ALREADY BOUND WEBSERVER PORT : "+ webserverport,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_PORT,ex);
				}
			}
		}
		
		if(ConfManager.isSSLDefault() || ConfManager.isSSLOffloader())
		{
			if(portconf != null && portconf.get("webserver.sslservers") != null && (portconf.get("webserver.sslservers") instanceof Properties))
			{
				Properties sslserverprops = (Properties) portconf.get("webserver.sslservers");
				Enumeration en = sslserverprops.propertyNames();
				
				while(en.hasMoreElements())
				{
					String srvname = (String)en.nextElement();
					String val = sslserverprops.getProperty(srvname);
					String[] valarr = val.split(",");
					String port = valarr[0];
					SelectorPoolFactory.initPort(port,evh);
				}	
				try
				{
					startSSLServers(evh,(Properties)portconf.get("webserver.sslservers"));
				}
				catch(Exception ex)
				{
					logger.log(Level.WARNING,"Given SSL Port "+portconf,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_PORT,ex);
				}
			}
		}
		else
		{
			logger.log(Level.INFO,"SSL Connector Disabled",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_PORT);
		}

	}

	/**
	 * To stop the current ports
	 *
	 * @param portconf - A Hashtable with details of webserver.port,webserver.sslservers (key of hastable)
	 *
	 * Note: 
	 *
	 * 1.Multiple ssl ports can be stopped
	 * 2.The value of webserver.sslservers should of type Properties whose key and values should be represented in form servername=port,domain,sslstartuptype. Ex: zoho=8080,*.zoho.com,1
	 *   sslstartuptype -- 0 - default, 1 - offloader , 2 - none
	 */
	 
	public static void stopPort(Hashtable portconf) throws Exception
	{
		if(portconf!=null && portconf.get("webserver.port")!=null)
		{
			String port = (String)portconf.get("webserver.port");
			ConfManager.removeWebServerPort(Integer.parseInt(port));
			SelectorPoolFactory.shutdownPort(port);
			closeSocket(port);
		}
		
		if(portconf!=null && portconf.get("webserver.sslservers")!=null && portconf.get("webserver.sslservers") instanceof Properties)
		{
			Properties sslserverprops = (Properties) portconf.get("webserver.sslservers");
			Enumeration en = sslserverprops.propertyNames();
			
			while(en.hasMoreElements())
			{
				String srvname = (String)en.nextElement();
				String val = sslserverprops.getProperty(srvname);
				String[] valarr = val.split(",");
				String port = valarr[0];
				ConfManager.removeSSLConf(srvname);
				closeSocket(port);
				SelectorPoolFactory.shutdownPort(port);
			}
			SSLManagerFactory.initialize();
		}
	}
	
	private static void closeSockets() throws Exception
	{
		for(Enumeration e=portservermap.keys(); e.hasMoreElements();)
		{
			String port = (String) e.nextElement();
			Object server = portservermap.get(port);
			if(server instanceof AsyncWebServer)
			{
				AsyncWebServer aws = (AsyncWebServer)server;
				aws.stopThis();
				logger.log(Level.INFO,"SERVER CLOSED ON PORT :"+port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.CLOSE_SOCKETS);
			}
			else if(server instanceof AsyncWebSSLServer)
			{
				AsyncWebSSLServer aws = (AsyncWebSSLServer)server;
				aws.stopThis();
				logger.log(Level.INFO,"SERVER CLOSED ON SSL PORT :"+port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.CLOSE_SOCKETS);
			}

		}
	}

	private static void closeSocket(String port) throws Exception
	{
		if(portservermap.get(port)!=null)
		{
			Object server = portservermap.get(port);
			if(server instanceof AsyncWebServer)
			{
				AsyncWebServer aws = (AsyncWebServer)server;
				aws.stopThis();
				portservermap.remove(port);
				logger.log(Level.INFO,"SERVER CLOSED ON PORT :"+port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.CLOSE_SOCKETS);
			}
			else if(server instanceof AsyncWebSSLServer)
			{
				AsyncWebSSLServer aws = (AsyncWebSSLServer)server;
				aws.stopThis();
				portservermap.remove(port);
				logger.log(Level.INFO,"SERVER CLOSED ON SSL PORT :"+port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.CLOSE_SOCKETS);
			}
		}

	}
	
	public static boolean startGridPort() throws Exception
	{
		return startGridPort(AWSConstants.GRID_PORT);
	}

	public static boolean startGridPort(int port) throws Exception
	{
		if(port != -1)
		{
			if(!ConfManager.initialize(true))
			{
				throw new AWSStartupException("Unable to restart the server ConfManager initialization failed");//No I18N
			}
			else
			{
				logger.log(Level.INFO,"AWS Adapter conf reinitialized successfully",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_GRID_PORT);
			}

			WebEngine.loadGridEngine(port);

			AsyncWebEventHandler evh = new AsyncWebEventHandler();
			SelectorPoolFactory.initPort(""+port,evh);
	
			try
			{
				AsyncWebServer server = new AsyncWebServer(port, evh);
				server.start();
				portservermap.put(""+port,server);
				ConfManager.setWMSServer();
				logger.log(Level.INFO,"Grid connector Started Successfully : Listening @ "+port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_GRID_PORT);
				return true;
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO,"ALREADY BOUND GRID PORT : "+ port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_GRID_PORT,ex);
				return false;
			}
		}
		else
		{
			logger.log(Level.INFO, "Illegal grid port value : "+port,AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.START_GRID_PORT);
			return false;
		}
	}

	/**
 	 * To Set ThreadPoolExecutor's pool size and keepalive time at runtime based on demand.
 	 * @param threadPoolName To indicate the poolName. Pass one of the below values as ThreadPoolName.
 	 * 			AWSConstants.REQUEST_PROCESSOR - For modifying requestProcessor thread counts
 	 * 			AWSConstants.REQUEST_PROCESSOR_WS - For modifying Websocket Write ACK thread counts
 	 * 			AWSConstants.NETDATA_PROCESSOR - For modifying netdataProcessor thread counts
 	 * 			AWSConstants.ASYNCLOG_PROCESSOR - For modifying AsyncLogProcessor thread counts
 	 * 			AWSConstants.WEBENGINE+engine name - For update webengine pool size
 	 * @param corePoolSize Sets the core number of threads.
 	 * @param maxPoolSize Sets the maximum allowed number of threads.
	 * @param keepaliveTime Sets the time limit for which threads may remain idle before being terminated.
	 * @param maxThreadCreationLimit Sets the threshold limit to create the maxPool threads once the queue reach this limit (only for WMSThreadPool, otherwise zero)
	 * @return update status - true/false
 	 */
	public static boolean updateThreadPoolExecutorConfs(String threadPoolName, int corePoolSize, int maxPoolSize, long keepaliveTime, int maxThreadCreationLimit)
	{
		try
		{
			int maxThreadCount = Util.getMaxThreadCount(corePoolSize, maxPoolSize);
			int newMaxThreadCreationLimit = Util.getMaxThreadCreationLimit(maxThreadCreationLimit, maxThreadCount);
			return WMSTPExecutorFactory.updateExecutor(threadPoolName, corePoolSize, maxThreadCount, (int)keepaliveTime, newMaxThreadCreationLimit);
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "Exception while reinit ThreadPoolExecutors : ",AsyncWebServerAdapter.class.getName(),AWSLogMethodConstants.UPDATE_THREAD_POOL_EXECUTOR_CONFS, e);
		}
		return false;
	}

	/**
         * To clear the cache of default servlet of all engines.
         */

	public static void clearCache()
	{
		clearCache(null);
	}

	public static void clearCache(String url)
	{
		ArrayList<String> list=WebEngine.getAllEngineName();
                for(String engineName : list)
                {
			if(url!=null)
			{
				WebEngine.getEngineByAppName(engineName).getBaseServlet().clearCache(url);
			}
			else
			{
                        	WebEngine.getEngineByAppName(engineName).getBaseServlet().clearAllCache();
			}
                }
	}
}
