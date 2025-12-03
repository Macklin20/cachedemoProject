//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.logging.Level;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DecimalFormat;
import java.util.Set;
import java.util.Iterator;
import java.util.HashMap;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class DataCountAnalyzer extends Thread
{
	private static AsyncLogger logger = new AsyncLogger("datastatlog");//No I18n
	private SimpleDateFormat sdf = new SimpleDateFormat("dd MM YYYY");
        private String currentReadFile;
	private String currentWriteFile;
        private int currentDay;
        private BufferedWriter readBW;
	private BufferedWriter writeBW;
	private HashMap<String, RandomAccessFile> monitorFiles = new HashMap();
	private static boolean status = false;
	private DecimalFormat df = new DecimalFormat("0.00");
	private static AtomicLong counter = new AtomicLong(0);

	public DataCountAnalyzer()
	{
		super(AWSConstants.AWS_THREAD_PREFIX+"datacountanalyzer"+AWSConstants.THREAD_NAME_SEPARATOR+"AWS"+AWSConstants.THREAD_NAME_SEPARATOR+counter.getAndIncrement());//No I18n
		initialize();
	}

	private void initialize()
	{
		currentReadFile = ConfManager.getDataMonitorHome()+File.separator+"results"+File.separator+sdf.format(new Date())+"-read.txt";
		currentWriteFile = ConfManager.getDataMonitorHome()+File.separator+"results"+File.separator+sdf.format(new Date())+"-write.txt";
		createNewFile(currentReadFile);
		createNewFile(currentWriteFile);
		currentDay = new Date().getDate();

		try
                {       
                        readBW = new BufferedWriter(new FileWriter(currentReadFile,true));
                }
                catch(Exception ex)
                {       
                        ex.printStackTrace();
                }
		
		try
		{
			writeBW = new BufferedWriter(new FileWriter(currentWriteFile, true));
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	} 

	private void createNewFile(String currentFile)
	{
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
	}

	private void procureDataFiles() throws IOException
	{
		File monitorHome = new File(ConfManager.getDataMonitorHome());
		
		if(monitorHome.listFiles().length == 0)
		{
			throw new IOException("No app/network data to analyze.");
		}
		
		String headerLine = "Date\t";	

		for(File file :  monitorHome.listFiles())
		{
			if(file.isDirectory() && !file.getName().equals("results"))
			{
				String appname = file.getName();

				File appFile = new File(file.getAbsolutePath()+File.separator+sdf.format(new Date())+".txt");
				
				if(appFile.exists())
				{
					RandomAccessFile raf = new RandomAccessFile(appFile, "r");
					monitorFiles.put(appname, raf);
					headerLine = headerLine+appname+"\t";
				}
			}
		}
		
		update(headerLine);
		updateNewLine();
	}
	
	private void update(String data)
	{
		updateRead(data);
		updateWrite(data);
	}
	
	private void updateNewLine()
	{
		updateNewLineRead();
		updateNewLineWrite();
	}

	private void updateRead(String data)
	{
		try
		{
			readBW.write(data);
			readBW.flush();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	private void updateNewLineRead()
	{
		try
		{
			readBW.newLine();
			readBW.flush();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	private void updateWrite(String data)
	{
		try
		{
			writeBW.write(data);
			writeBW.flush();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	private void updateNewLineWrite()
	{
		try
		{
			writeBW.newLine();
			writeBW.flush();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

	private void reinit() throws IOException 
	{
		closeReadStreams();
		closeWriteStreams();
		monitorFiles.clear();

		initialize();
		
		try
		{
			Thread.sleep(10000);
		}
		catch(Exception ex)
		{}

		procureDataFiles();
	}

	private void closeWriteStreams()
	{
		try
		{
			if(readBW!=null)
			{
				readBW.close();
			}
		}
		catch(Exception ex)
		{}

		try
		{
			if(writeBW!=null)
			{
				writeBW.close();
			}
		}
		catch(Exception ex)
		{}
	}

	private void closeReadStreams()
	{
		Set<String> applist = monitorFiles.keySet();
				
		if(applist.size() == 0)
		{
			return;
		}

		Iterator itr = applist.iterator();
		
		while(itr.hasNext())
		{	
			String appname = (String) itr.next();
			RandomAccessFile raf = monitorFiles.get(appname);
			
			try
			{
				raf.close();
			}
			catch(Exception ex)
			{}
		}
	}

	public void run()
	{
		status = true;
		try
		{
			try
			{
				Thread.sleep(10000);
			}
			catch(Exception ex)
			{}

			procureDataFiles();
			
			while(ConfManager.isDataMonitorEnabled())
			{
				if(new Date().getDate() != currentDay)
				{
					reinit();
				}
				else
				{
					try
					{
						Thread.sleep(ConfManager.getDataMonitorTimeWait());
					}
					catch(Exception ex)
					{}
				}

				long currentTime = System.currentTimeMillis()/1000;

				String readData = currentTime+"\t";
				String writeData = currentTime+"\t";

				Set<String> applist = monitorFiles.keySet();
				
				if(applist.size() == 0)
				{
					logger.log(Level.SEVERE, "Monitoring files are not present. Quitting Analysis.",DataCountAnalyzer.class.getName(),AWSLogMethodConstants.RUN);
					break;
				}

				Iterator itr = applist.iterator();
				
				while(itr.hasNext())
				{
					String appname = (String)itr.next();
					RandomAccessFile raf = monitorFiles.get(appname);
					double readcount = 0;
					double writecount = 0;
					float incorrect = 0;
					float valid = 0;
				
					while(true)
					{
						String data = null;

						try
						{
							long offset = raf.getFilePointer();
							data = raf.readLine();

							if(data == null) 
							{
								break;
							}

							String[] components = data.trim().split("\\s+");
		
							if(components.length != 3) 
							{
								if(incorrect < 5)
								{
									logger.info("[DATA ANALYZER] Incomplete data for "+appname+" : "+data+". Attempt "+(incorrect+1),DataCountAnalyzer.class.getName(),AWSLogMethodConstants.RUN);	
									raf.seek(offset);
									incorrect++;
									continue;
								}
								else
								{
									logger.info("[DATA ANALYZER] Skipping incomplete data for "+appname+" : "+data+". Attempt "+(incorrect+1),DataCountAnalyzer.class.getName(),AWSLogMethodConstants.RUN);	
									incorrect = 0;
									continue;
								}
							}

							if(Long.parseLong(components[0]) <= currentTime)
							{
								valid++;
								readcount = readcount+Double.parseDouble(components[1]);
								writecount = writecount+Double.parseDouble(components[2]);
							}
							else
							{
								raf.seek(offset);
								break;
							}
						}
						catch(Exception ex)
						{
							logger.log(Level.INFO,"[DATA ANALYZER] Incorrect data for "+appname+" : "+data,DataCountAnalyzer.class.getName(),AWSLogMethodConstants.RUN, ex);	
						}	
					}

					if(appname.equals("sar"))
					{
						readData = readData+df.format(readcount/valid)+"\t";//No I18N
						writeData = writeData+df.format(writecount/valid)+"\t";//No I18N
					}
					else
					{
						readData = readData+readcount+"\t";
						writeData = writeData+writecount+"\t";
					}
				}
				
				updateRead(readData);
				updateWrite(writeData);
				updateNewLine();
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "DataCountAnalyzer failure. Please check.",DataCountAnalyzer.class.getName(),AWSLogMethodConstants.RUN);
			ex.printStackTrace();
		}
		finally
		{
			closeReadStreams();
			closeWriteStreams();
			monitorFiles.clear();
		}
		status = false;
	}

	public static boolean isThreadAlive()
	{
		return status;
	}
}			

