//$Id$
package com.zoho.wms.asyncweb.server.asynclogs;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.lang.Throwable;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import java.io.File;
import java.io.FileInputStream;
import java.io.Serializable;

import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.AWSLogClientThreadLocal;

import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;
import com.adventnet.wms.servercommon.components.executor.WmsTask;

import com.zoho.logs.logclient.LogClientThreadLocal;

public class AsyncLogsProcessor extends Thread
{
	private static Logger asynclogger = Logger.getLogger(AsyncLogsProcessor.class.getName());

	private static String loggerHome = null;
	private	static String propsFile = null;
	private static boolean initialized = false;
	private static int logProcessorCount = -1;
	private static int maxLogProcessorCount = -1;
	private static long katime = 100L;
	private static int tpexecutor = 1;
	private static int qsize = -1;
	private static int maxThreadCreationLimit = -1;
	private static boolean rejectionHandler = false;
	private static boolean isWMSThreadPoolEnabled = false;

	static
	{
		loggerHome = System.getProperty("server.home");
		loggerHome = (loggerHome!=null) ?loggerHome :"..";//No I18N
		configThreadPoolType();
		initialize();
	}

	public static boolean initialize()
	{
		try
		{
			if(initialized)
			{
				asynclogger.log(Level.INFO, "AsyncLog Processor already Initialized.");
				return true;
			}
			loadLogProcessorConfigurations();
			if(logProcessorCount > 0)
			{
				return doInit();
			}
		}
		catch(Exception e)
		{
			asynclogger.log(Level.SEVERE, "Exception while init LogProcessor : ", e);
		}
		return false;
	}

	private static void configThreadPoolType()
	{
		try
		{
			String adapterConfFile = loggerHome+File.separator+"conf"+File.separator+"awsadapterconf.properties";//No I18n
			Properties props = new Properties();
			props.load(new FileInputStream(adapterConfFile));
			if(props != null)
			{
				isWMSThreadPoolEnabled = Boolean.parseBoolean(props.getProperty("enablewmsthreadpool",""+isWMSThreadPoolEnabled));
			}
		}
		catch(Exception e)
		{
			asynclogger.log(Level.SEVERE, "Exception while config ThreadPoolType", e);
		}
	}

	private static void loadLogProcessorConfigurations() throws Exception
	{
		propsFile = loggerHome+File.separator+"conf"+File.separator+"asynclogs.properties";//No I18n
		Properties props = Util.getProperties(propsFile);
		if(props != null)
		{
			logProcessorCount = Integer.parseInt(props.getProperty("logprocessorcount",""+logProcessorCount));
			maxLogProcessorCount = Integer.parseInt(props.getProperty("maxlogprocessorcount",""+maxLogProcessorCount));
			katime = Long.parseLong(props.getProperty("keepalivetime",""+katime));
			tpexecutor = Integer.parseInt(props.getProperty("tpexecutor",""+tpexecutor));
			qsize = Integer.parseInt(props.getProperty("queuesize",""+qsize));
			maxThreadCreationLimit = Integer.parseInt(props.getProperty("maxthreadcreationlimit",""+maxThreadCreationLimit));
			rejectionHandler = Boolean.parseBoolean(props.getProperty("rejectionhandler",""+rejectionHandler));
		}
	}

	private static boolean doInit()
	{
		try
		{
			int maxThreadCount = Util.getMaxThreadCount(maxLogProcessorCount, logProcessorCount);
			int wmsMaxThreadCreationLimit = Util.getMaxThreadCreationLimit(maxThreadCreationLimit, maxThreadCount);
			initialized = WMSTPExecutorFactory.createNewExecutor(AWSConstants.ASYNCLOG_PROCESSOR, logProcessorCount, maxThreadCount, (int)katime, new Dispatcher(), wmsMaxThreadCreationLimit);
		}
		catch(Exception ex)
		{
			asynclogger.log(Level.INFO, "Exception in TPE initialization ", ex);//No I18n
		}
		return initialized;
	}

	public static boolean reinitialize()
	{
		try
		{
			if(!initialized)
			{
				return initialize();
			}

			loadLogProcessorConfigurations();
			int maxThreadCount = Util.getMaxThreadCount(maxLogProcessorCount, logProcessorCount);
			int wmsMaxThreadCreationLimit = Util.getMaxThreadCreationLimit(maxThreadCreationLimit, maxThreadCount);
			initialized = WMSTPExecutorFactory.updateExecutor(AWSConstants.ASYNCLOG_PROCESSOR, logProcessorCount, maxLogProcessorCount, (int)katime, wmsMaxThreadCreationLimit);
			return true;
		}
		catch(Exception e)
		{
			asynclogger.log(Level.SEVERE, "Exception while reinit AsyncLogsProcessor : ", e);
		}
		return false;
	}

	static void process(Logger logger, Level level, String message, String classname, String methodname)
	{
		if(initialized)
		{
			try
			{
				String reqid = LogClientThreadLocal.getRequestID();
				WMSTPExecutorFactory.execute(AWSConstants.ASYNCLOG_PROCESSOR, new Event(logger, level, message, classname, methodname, AWSConstants.LOG_DISPATCHER,reqid));
				return;
			}
			catch(Exception exp)
			{
				asynclogger.log(Level.SEVERE, "Exception in log dispatch ", exp);
			}
		}
		log(logger, level, message, classname, methodname, null, null, null );
	}

	static void process(Logger logger, Level level, String message, String classname, String methodname, Object param)
	{
		if(initialized)
		{
			try
			{
				String reqid = LogClientThreadLocal.getRequestID();
				WMSTPExecutorFactory.execute(AWSConstants.ASYNCLOG_PROCESSOR, new Event(logger, level, message, classname, methodname, param, AWSConstants.LOG_TMPA,reqid));
				return;
			}
			catch(Exception exp)
			{
				asynclogger.log(Level.SEVERE, "Exception in log dispatch ", exp);
			}
		}
		log(logger, level, message,classname, methodname, param, null, null);
	}

	static void process(Logger logger, Level level, String message, String classname, String methodname, Object[] params)
	{
		if(initialized)
		{
			try
			{
				String reqid = LogClientThreadLocal.getRequestID();
				WMSTPExecutorFactory.execute(AWSConstants.ASYNCLOG_PROCESSOR, new Event(logger, level, message, classname, methodname, params, AWSConstants.LOG_TMPB,reqid));
				return;
			}
			catch(Exception exp)
			{
				asynclogger.log(Level.SEVERE, "Exception in log dispatch ", exp);
			}
		}
		log(logger, level, message, classname, methodname, null, params, null);
	}

	static void process(Logger logger, Level level, String message, String classname, String methodname, Throwable thrown)
	{
		if(initialized)
		{
			try
			{
				String reqid = LogClientThreadLocal.getRequestID();
				WMSTPExecutorFactory.execute(AWSConstants.ASYNCLOG_PROCESSOR, new Event(logger, level, message, classname, methodname, thrown, AWSConstants.LOG_TMPC,reqid));
				return;
			}
			catch(Exception exp)
			{
				asynclogger.log(Level.SEVERE, "Exception in log dispatch ", exp);
			}
		}
		log(logger, level, message, classname, methodname, null, null, thrown);
	}

	static void process(Logger logger, LogRecord record, String classname, String methodname)
	{
		if(initialized)
		{
			try
			{
				String reqid = LogClientThreadLocal.getRequestID();
				WMSTPExecutorFactory.execute(AWSConstants.ASYNCLOG_PROCESSOR, new Event(logger, record, classname, methodname, AWSConstants.LOG_RECORD_DISPATCHER,reqid));
				return;
			}
			catch(Exception exp)
			{
				asynclogger.log(Level.SEVERE, "Exception in log dispatch ", exp);
			}
		}
		log(logger, record, classname, methodname);
	}

	private static void log(Logger logger, LogRecord record, String classname, String methodname)
	{
		record.setSourceClassName(classname);
		record.setSourceMethodName(methodname);
		logger.log(record);
	}

	private static void log(Logger logger, Level level, String message, String classname, String methodname, Object obj, Object[] params, Throwable thrown)
	{
		try
		{
			if(!ConfManager.isLogpLoggerEnabled())
			{
				if(obj !=null)
				{
					logger.log(level, message, obj);
				}
				else if(params != null && params.length > 0)
				{
					logger.log(level, message, params);
				}
				else if(thrown != null)
				{
					logger.log(level, message, thrown);
				}
				else
				{
					logger.log(level, message);
				}
			}
			else
			{
				if(obj !=null)
				{
					logger.logp(level, classname, methodname, message, obj);
				}
				else if(params != null && params.length > 0)
				{
					logger.logp(level, classname, methodname, message, params);
				}
				else if(thrown != null)
				{
					logger.logp(level, classname, methodname, message, thrown);
				}
				else
				{
					logger.logp(level, classname, methodname, message);
				}
			}
		}
		catch(Exception exp)
		{
			asynclogger.log(Level.SEVERE, "Exception in asynclogging : loggername : "+logger+", message : "+message, exp);//No I18n
		}
	}

	private static void handleLogDispatcher(Logger logger, Level level, String message ,String classname, String methodname, String reqid)
	{
		try
		{
			AWSLogClientThreadLocal.setLoggingProperties(reqid);
			AWSLogClientThreadLocal.setAccesslogLoggingProperties(reqid);
			log(logger, level, message, classname, methodname, null, null, null);
		}
		catch(Exception ex)
		{
			asynclogger.log(Level.INFO, "[Exception][AsyncLogsProcessor - Run]", ex);
		}
	}

	public static void handleTmpA(Logger logger, Level level, String message,String classname, String methodname, Object param, String reqid)
	{
		try
		{
			AWSLogClientThreadLocal.setLoggingProperties(reqid);
			AWSLogClientThreadLocal.setAccesslogLoggingProperties(reqid);
			log(logger, level, message, classname, methodname, param, null, null);
		}
		catch(Exception ex)
		{
			asynclogger.log(Level.INFO, "[Exception][AsyncLogsProcessor - Run]", ex);
		}
	}

	public static void handleTmpB(Logger logger, Level level, String message, String classname, String methodname, Object[] params, String reqid)
	{
		try
		{
			AWSLogClientThreadLocal.setLoggingProperties(reqid);
			AWSLogClientThreadLocal.setAccesslogLoggingProperties(reqid);
			log(logger, level, message, classname, methodname, null, params, null);
		}
		catch(Exception ex)
		{
			asynclogger.log(Level.INFO, "[Exception][AsyncLogsProcessor - Run]", ex);
		}
	}

	private static void handleTmpC(Logger logger, Level level, String message, String classname, String methodname, Throwable thrown, String reqid)
	{
		try
		{
			AWSLogClientThreadLocal.setLoggingProperties(reqid);
			AWSLogClientThreadLocal.setAccesslogLoggingProperties(reqid);
			log(logger, level, message, classname, methodname, null, null, thrown);
		}
		catch(Exception ex)
		{
			asynclogger.log(Level.INFO, "[Exception][AsyncLogsProcessor - Run]", ex);
		}
	}

	private static void handleLogRecordDispatcher(Logger logger, LogRecord record ,String classname, String methodname, String reqid)
	{
		try
		{
			AWSLogClientThreadLocal.setLoggingProperties(reqid);
			AWSLogClientThreadLocal.setAccesslogLoggingProperties(reqid);
			log(logger, record, classname, methodname);
		}
		catch(Exception ex)
		{
			asynclogger.log(Level.INFO, "[Exception][AsyncLogsProcessor - Run]", ex);
		}
	}

	private static class Dispatcher implements WmsTask
	{
		@Override
		public void handle(Object obj)
		{
			try
			{
				Event event = (Event)obj;
				int type = event.getType();
				switch(type)
				{
					case AWSConstants.LOG_DISPATCHER:
						handleLogDispatcher(event.getLogger(), event.getLevel(), event.getMessage(), event.getClassName(), event.getMethodName(), event.getReqid());
						break;
					case AWSConstants.LOG_TMPA:
						handleTmpA(event.getLogger(), event.getLevel(), event.getMessage(), event.getClassName(), event.getMethodName(), event.getParam(), event.getReqid());
						break;
					case AWSConstants.LOG_TMPB:
						handleTmpB(event.getLogger(), event.getLevel(), event.getMessage(), event.getClassName(), event.getMethodName(), event.getParams(), event.getReqid());
						break;
					case AWSConstants.LOG_TMPC:
						handleTmpC(event.getLogger(), event.getLevel(), event.getMessage(), event.getClassName(), event.getMethodName(), event.getThrown(), event.getReqid());
						break;
					case AWSConstants.LOG_RECORD_DISPATCHER:
						handleLogRecordDispatcher(event.getLogger(), event.getLogRecord(), event.getClassName(), event.getMethodName(), event.getReqid());
						break;
				}
			}
			catch(Exception e)
			{
				asynclogger.log(Level.SEVERE, "Exception in LogDispatcher : ", e);
			}
		}
	}

	private static class Event implements Serializable
	{
		private Logger logger;
		private LogRecord record;
		private Level level;
		private String message;
		private String classname;
		private String methodname;
		private Object param;
		private Object[] params;
		private Throwable thrown;
		private int type;
		private String customfield;

		public Event(Logger logger, LogRecord record, String classname, String methodname,int type, String reqid)
		{
			this.type = type;
			this.logger = logger;
			this.record = record;
			this.classname = classname;
			this.methodname = methodname;
			this.customfield = reqid;
		}

		public Event(Logger logger, Level level, String message, String classname, String methodname, int type, String reqid)
		{
			this.type = type;
			this.logger = logger;
			this.level = level;
			this.message = message;
			this.classname = classname;
			this.methodname = methodname;
			this.customfield = reqid;
		}

		public Event(Logger logger, Level level, String message, String classname, String methodname, Object param, int type, String reqid)
		{
			this.type = type;
			this.logger = logger;
			this.level = level;
			this.message = message;
			this.param = param;
			this.classname = classname;
			this.methodname = methodname;
			this.customfield = reqid;
		}

		public Event(Logger logger, Level level, String message, String classname, String methodname, Object[] params, int type, String reqid)
		{
			this.type = type;
			this.logger = logger;
			this.level = level;
			this.message = message;
			this.params = params;
			this.classname = classname;
			this.methodname = methodname;
			this.customfield = reqid;
		}

		public Event(Logger logger, Level level, String message, String classname, String methodname, Throwable thrown, int type, String reqid)
		{
			this.type = type;
			this.logger = logger;
			this.level = level;
			this.message = message;
			this.thrown = thrown;
			this.classname = classname;
			this.methodname = methodname;
			this.customfield = reqid;
		}

		public Logger getLogger()
		{
			return logger;
		}

		public LogRecord getLogRecord()
		{
			return record;
		}

		public Level getLevel()
		{
			return level;
		}

		public String getMessage()
		{
			return message;
		}

		public Object getParam()
		{
			return param;
		}

		public Object[] getParams()
		{
			return params;
		}

		public Throwable getThrown()
		{
			return thrown;
		}

		public int getType()
		{
			return type;
		}

		public String getClassName()
		{
			return classname;
		}

		public String getMethodName()
		{
			return methodname;
		}

		public String getReqid()
		{
			return customfield;
		}
	}
}
