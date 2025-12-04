//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.logging.Level;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.util.concurrent.atomic.AtomicLong;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class DataCountUpdater extends Thread
{
	private static AsyncLogger logger = new AsyncLogger("datastatlog");//No I18n

	public static final DataCountUpdater COUNTER = new DataCountUpdater();
	
	SimpleDateFormat sdf = new SimpleDateFormat("dd MM YYYY");
	
	private static boolean status = false;
	private String currentFile;
	private int currentDay;
	private BufferedWriter bw;
	public long readCount = 0;
	public long writeCount = 0;

	private static AtomicLong counter = new AtomicLong(0);

	public DataCountUpdater()
	{
		super(AWSConstants.AWS_THREAD_PREFIX+"datacountupdater"+AWSConstants.THREAD_NAME_SEPARATOR+"AWS"+AWSConstants.THREAD_NAME_SEPARATOR+counter.getAndIncrement());//No I18n
		initialize();
	}
	
	private void initialize()
	{
		currentFile = ConfManager.getDataMonitorHome()+File.separator+"aws"+File.separator+sdf.format(new Date())+".txt";
		
		try
		{
			File file = new File(currentFile);
			if(!file.exists())
			{
				if(!file.getParentFile().exists())
				{
					file.getParentFile().mkdir();
				}
				file.createNewFile();
			}
			else
			{
				file.delete();
				file.createNewFile();
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		
		currentDay = new Date().getDate();

		try
		{
			bw = new BufferedWriter(new FileWriter(currentFile,true));
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	private void reinit()
	{
		try
		{
			if(bw!=null)
			{
				bw.close();
			}
		}
		catch(Exception ex)
		{}

		initialize();
	}
	
	public void updateRead(long bytes) 
	{
		readCount = readCount+bytes;
	}

	public void updateWrite(long bytes)
	{
		writeCount = writeCount+bytes;
	}	

	private long getReadCount()
	{
		long read = readCount;
		readCount = 0;
		return read;
	}
	
	private long getWriteCount()
	{
		long write = writeCount;
		writeCount = 0;
		return write;
	}

	public void run()
	{
		status = true;
		try
		{
			while(ConfManager.isDataStatsEnabled())
			{
				if(currentDay != new Date().getDate())
				{
					reinit();
				}

				try
				{
					Thread.sleep(ConfManager.getDataStatsTimeWait());
				}
				catch(Exception ex)
				{}
				
				long read = COUNTER.getReadCount();
				long write = COUNTER.getWriteCount();
	
				try
				{	
					bw.write(System.currentTimeMillis()/1000+"\t"+read+"\t"+write);
					bw.newLine();
					bw.flush();
				}
				catch(Exception ex)
				{
					logger.log(Level.WARNING, "FAILURE TO WRITE TO DATA MONITORING FILE "+currentFile+". Reinit Initiated.",DataCountUpdater.class.getName(),AWSLogMethodConstants.RUN);
					reinit();
					ex.printStackTrace();
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "DataCountUpdater Failure. Please check.",DataCountUpdater.class.getName(),AWSLogMethodConstants.RUN);
			ex.printStackTrace();
		}
		finally
		{
			try
			{
				if(bw!=null)
				{
					bw.close();
				}
			}
			catch(Exception ex)
			{}
		}
		status = false;
	}

	public static boolean isThreadAlive()
	{
		return status;
	}
}
				
				
				
				
				
		
