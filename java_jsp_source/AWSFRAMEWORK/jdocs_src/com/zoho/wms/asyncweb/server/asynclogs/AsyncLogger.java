//$Id$
package com.zoho.wms.asyncweb.server.asynclogs;

import java.util.logging.Logger;

import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.Throwable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class AsyncLogger extends Logger
{
	private Logger logger = null;
	private static String class_name = "";

	static
	{
		class_name = AsyncLogger.class.getName();
	}

	public AsyncLogger(String name)
	{
		super(name, null);
		this.logger = Logger.getLogger(name);
	}

	public AsyncLogger(String name, String resourceBundleName)
	{
		super(name, resourceBundleName);
		this.logger = Logger.getLogger(name, resourceBundleName);
	}

	@Override
	public void log(Level level, String msg)
        {
                log( level, msg, class_name, AWSLogMethodConstants.LOG);
        }

	public void log(Level level, String msg, String classname, String methodname)
	{
		if (!isLoggable(level))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, level, msg, classname, methodname);
	}

	@Override
	public void log(Level level, String msg, Object param)
        {
                log( level, msg, class_name, AWSLogMethodConstants.LOG, param);
        }

	public void log(Level level, String msg, String classname, String methodname, Object param)
	{
		if (!isLoggable(level))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, level, msg, classname, methodname, param);
	}

	@Override
	public void log(Level level, String msg, Object[] params)
        {
                log(level, msg, class_name, AWSLogMethodConstants.LOG, params);
        }

	public void log(Level level, String msg, String classname, String methodname, Object[] params)
	{
		if (!isLoggable(level))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, level, msg, classname, methodname, params);
	}
	
	@Override
	public void log(Level level, String msg, Throwable thrown)
        {
                log(level, msg, class_name,AWSLogMethodConstants.LOG, thrown);
        }

	public void log(Level level, String msg, String classname, String methodname, Throwable thrown)
	{
		if (!isLoggable(level))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, level, msg, classname, methodname, thrown);
		printFileDescriptorDetails(thrown);
	}

	@Override
	public void log(LogRecord record)
        {
                log( record, class_name,AWSLogMethodConstants.LOG);
        }

	public void log(LogRecord record, String classname, String methodname)
	{
		if (!isLoggable(record.getLevel()))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, record, classname, methodname);
	}

	@Override
	public void severe(String msg)
        {
                severe( msg, class_name, AWSLogMethodConstants.SEVERE);
        }

	public void severe(String msg, String classname, String methodname)
	{
		if (!isLoggable(Level.SEVERE))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, Level.SEVERE, msg, classname, methodname);
	}
	
	@Override
	public void warning(String msg)
        {
                warning( msg, class_name, AWSLogMethodConstants.WARNING);
        }

	public void warning(String msg, String classname, String methodname)
	{
		if (!isLoggable(Level.WARNING))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, Level.WARNING, msg, classname, methodname);
	}

	@Override
	public void info(String msg)
        {
                info( msg, class_name, AWSLogMethodConstants.INFO);
        }

	public void info(String msg, String classname, String methodname)
	{
		if (!isLoggable(Level.INFO))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, Level.INFO, msg, classname, methodname);
	}

	@Override
	public void config(String msg)
        {
                config( msg, class_name, AWSLogMethodConstants.CONFIG);
        }

	public void config(String msg, String classname, String methodname)
	{
		if (!isLoggable(Level.CONFIG))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, Level.CONFIG, msg, classname, methodname);
	}

	@Override
	public void fine(String msg)
        {
                fine( msg, class_name, AWSLogMethodConstants.FINE);
        }

	public void fine(String msg, String classname, String methodname)
	{
		if (!isLoggable(Level.FINE))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, Level.FINE, msg, classname, methodname);
	}
	
	@Override
	public void finer(String msg)
        {
                finer( msg, class_name, AWSLogMethodConstants.FINER);
        }

	public void finer(String msg, String classname, String methodname)
	{
		if (!isLoggable(Level.FINER))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, Level.FINER, msg, classname, methodname);
	}
	
	@Override
	public void finest(String msg)
        {
                finest( msg, class_name, AWSLogMethodConstants.FINEST);
        }

	public void finest(String msg, String classname, String methodname)
	{
		if (!isLoggable(Level.FINEST))
		{
			return;
		}
		AsyncLogsProcessor.process(logger, Level.FINEST, msg, classname, methodname);
	}

	public void addDebugLog(Level level, String msg)
	{
		addDebugLog(level,msg,class_name, AWSLogMethodConstants.ADD_DEBUG_LOG);
	}

	public void addDebugLog(Level level, String msg, String classname, String methodname)
	{

		if(ConfManager.isDebugLogsEnabled())
		{
			if (!isLoggable(Level.FINEST))
			{
				return;
			}
			AsyncLogsProcessor.process(logger, level, msg, classname, methodname);
		}
	}

	public void addDebugLog(Level level, String msg, Object[] params)
	{
			addDebugLog(level,msg,class_name,AWSLogMethodConstants.ADD_DEBUG_LOG ,params);
	}

	public void addDebugLog(Level level, String msg, String classname, String methodname, Object[] params)
	{
		if(ConfManager.isDebugLogsEnabled())
		{
			if (!isLoggable(Level.FINEST))
			{
				return;
			}
			AsyncLogsProcessor.process(logger, level, msg, classname, methodname, params);
		}
	}

	public void addDebugLog(Level level, String msg, Throwable thrown)
	{
		addDebugLog(level,msg,class_name,AWSLogMethodConstants.ADD_DEBUG_LOG,thrown);
	}

	public void addDebugLog(Level level, String msg, String classname, String methodname, Throwable thrown)
	{
		if(ConfManager.isDebugLogsEnabled())
		{
			if (!isLoggable(Level.FINEST))
			{
				return;
			}
			AsyncLogsProcessor.process(logger, level, msg, classname, methodname, thrown);
		}
		printFileDescriptorDetails(thrown);
	}

	public void addExceptionLog(Level level, String msg)
	{
		addExceptionLog(level,msg,class_name,AWSLogMethodConstants.ADD_EXCEPTION_LOG);
	}

	public void addExceptionLog(Level level, String msg, String classname, String methodname)
	{
		if(ConfManager.isExceptionLogsEnabled())
		{
			if (!isLoggable(Level.FINEST))
			{
				return;
			}
			AsyncLogsProcessor.process(logger, level, msg, classname, methodname);
		}
	}

	public void addExceptionLog(Level level, String msg, Object[] params)
	{
		addExceptionLog(level, msg, class_name, AWSLogMethodConstants.ADD_EXCEPTION_LOG,params);
	}

	public void addExceptionLog(Level level, String msg, String classname, String methodname, Object[] params)
	{
		if(ConfManager.isExceptionLogsEnabled())
		{
			if (!isLoggable(Level.FINEST))
			{
				return;
			}
			AsyncLogsProcessor.process(logger, level, msg, classname, methodname, params);
		}
	}

	public void addExceptionLog(Level level, String msg, Throwable thrown)
	{
		addExceptionLog(level,msg,class_name,AWSLogMethodConstants.ADD_EXCEPTION_LOG,thrown);
	}

	public void addExceptionLog(Level level, String msg, String classname, String methodname, Throwable thrown)
	{
		if(ConfManager.isExceptionLogsEnabled())
		{
			if (!isLoggable(Level.FINEST))
			{
				return;
			}
			AsyncLogsProcessor.process(logger, level, msg, classname, methodname, thrown);
		}
		printFileDescriptorDetails(thrown);
	}

	private void printFileDescriptorDetails(Throwable thrown)
	{
		if(thrown != null && thrown.getMessage() != null && thrown.getMessage().equalsIgnoreCase(AWSLogConstants.TOO_MANY_OPEN_FILES))
		{
			try
			{
				RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
				String jvmName = bean.getName();
				long pid = Long.parseLong(jvmName.split("@")[0]);
				Runtime runtime = Runtime.getRuntime();
				Process process = runtime.exec("ls -l /proc/"+pid+"/fd/ | wc -l");
				log(Level.INFO, AWSLogConstants.FILE_DESCRIPTOR, new Object[]{pid, getProcessValue(process)});
			}
			catch(NullPointerException npe)
			{
			}
			catch(Exception e)
			{
				log(Level.SEVERE, "Unable to runtime debug values ", e);
			}
		}
	}

	private String getProcessValue(Process process) throws Exception
	{
		BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		StringBuffer sb = new StringBuffer();
		String str;
		while ((str = br.readLine()) != null)
		{
			sb.append(str + "\n");
		}
		br.close();
		return sb.toString();
	}
}
