//$Id$
package com.zoho.wms.asyncweb.server;

import java.nio.channels.*;
import java.util.logging.Level;
import java.util.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

import com.zoho.wms.asyncweb.server.ssl.SSLStartUpTypes;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;


public class SelectorPoolImpl extends SelectorPool
{
	private static AsyncLogger logger = new AsyncLogger(SelectorPoolImpl.class.getName());
	private static AsyncLogger mtlogger = new AsyncLogger("messagetracker");//No I18N
	private int readscnt = 0;
	private int readSelectorCount = 0;
	public SecondaryReadSelector[] readSelector;
	private int selectorport;
	private AsyncWebEventHandler handler;
	private int activeselectiontimeout = 0;
	private int workaroundpause = ConfManager.getWorkAroundPause();
	private static AtomicLong counter = new AtomicLong(0);

	public SelectorPoolImpl(int port,AsyncWebEventHandler handler)
	{
		this.selectorport = port;
		this.handler = handler;
	}

	public void initRead() throws IOException
	{
		if(WebEngine.getEngineByPort(selectorport) != null)
		{
			readSelectorCount = WebEngine.getEngineByPort(selectorport).getReadSelectorCount(selectorport) != -1 ? WebEngine.getEngineByPort(selectorport).getReadSelectorCount(selectorport) : ConfManager.getReadSelectorCount(selectorport);
			activeselectiontimeout = WebEngine.getEngineByPort(selectorport).getKeyActiveSelectionTimeout() != -1 ? WebEngine.getEngineByPort(selectorport).getKeyActiveSelectionTimeout() : ConfManager.getKeyActiveSelectionTimeOut();

		}
		else
		{
			readSelectorCount = ConfManager.getReadSelectorCount(selectorport);
			activeselectiontimeout = ConfManager.getKeyActiveSelectionTimeOut();
		}
		this.readSelector = new SecondaryReadSelector[readSelectorCount];
		for(int i=0;i<readSelector.length;i++)
		{
			readSelector[i] = new SecondaryReadSelector(Selector.open(),this.selectorport, i);
			readSelector[i].start();
			logger.log(Level.INFO,"Read Selector "+i+" Initialized And Started",SelectorPoolImpl.class.getName(),AWSLogMethodConstants.INIT_READ);//No I18N
		}
		logger.log(Level.INFO,"SelectorPool :: Read Selectors :: "+readSelectorCount +" Initialized",SelectorPoolImpl.class.getName(),AWSLogMethodConstants.INIT_READ);//No I18N
	}

	public void registerRead(SocketChannel sc , Integer port, Long stime) throws IOException
	{
		/*readSelector[readscnt++].register(sc,port,stime);
		readscnt = readscnt % readSelectorCount;*/

		int keysize = Integer.parseInt(readSelector[0].getKeySize());
                for(int i=1 ; i<readSelector.length ; i++)
                {
                        if(Integer.parseInt(readSelector[i].getKeySize()) < keysize)
                        {
                                keysize = Integer.parseInt(readSelector[i].getKeySize()); 
                                readscnt = i;
                        }
                }               
                readSelector[readscnt].register(sc,port,stime);
	}

	public String getDebugInfo(boolean details)
	{
		StringBuilder sbuilder = new StringBuilder();
		for(int i=0;i<readSelectorCount;i++)
		{
			sbuilder.append(readSelector[i].getName()+"\r\n");//No I18N
			if(details)
			{
				sbuilder.append(readSelector[i].getKeys()+"\r\n");//No I18N
			}
			sbuilder.append(readSelector[i].getKeySize()+"\r\n");//No I18N
			Thread.State state = readSelector[i].getThreadState();
			sbuilder.append("State - "+state.name()+"\r\n");//No I18N
			sbuilder.append("Timeout - "+readSelector[i].getTimeout()+"\r\n");//No I18N
			sbuilder.append("\r\n");//No I18N
		}
		return sbuilder.toString();
	}

	public void shutdown() throws Exception
	{
		for(int i=0;i<readSelector.length;i++)
		{
			readSelector[i].shutdown();
		}
	}

	class SecondaryReadSelector extends Thread
	{
		private Selector selector;
		private int port;
		//private int index;
		private boolean isAvailable = true;
		private boolean isInternalPort = false;

		private Vector channelRegVector = new Vector();

		public SecondaryReadSelector(Selector selector,int port, int index)
		{
			super(AWSConstants.AWS_THREAD_PREFIX+"ReadSelector"+AWSConstants.THREAD_NAME_SEPARATOR+port+AWSConstants.THREAD_NAME_SEPARATOR+index+",pause"+AWSConstants.THREAD_NAME_SEPARATOR+workaroundpause+AWSConstants.THREAD_NAME_SEPARATOR+counter.getAndIncrement());//No I18n
			this.selector = selector;
			this.port  = port;
			//this.index = index;
			this.isInternalPort = ConfManager.isGridPort(port);
		}

		public void register(SocketChannel sc ,  Integer port, Long stime)
		{
			Object[] channelDetArr = new Object[]{sc,port,stime};
			channelRegVector.add(channelDetArr);
		}

		private void registerSocketChannels()
		{
			if(channelRegVector == null) 
			{
				return;
			}
			while(channelRegVector.size()>0)
			{
				try
				{
					Object[] channelDetArr = new Object[2];
					channelDetArr = (Object[])(channelRegVector.firstElement());
					channelRegVector.removeElement(channelDetArr);
					SocketChannel channel = (SocketChannel) channelDetArr[0];
					int port = ((Integer) channelDetArr[1]).intValue();
					long stime = ((Long) channelDetArr[2]).longValue();

					SelectionKey key = channel.register(selector,SelectionKey.OP_READ);

					if(ConfManager.isHttpsPort(port) && ConfManager.getSSLStartupType(port) == SSLStartUpTypes.DEFAULT)
					{
						key.attach(new AsyncWebSSLClient(key,port,stime));
					}
					else
					{
						key.attach(new AsyncWebClient(key,port,stime));
					}
				}
				catch(Exception exp)
				{
					logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.REGISTER_SOCKET_CHANNELS, exp);
				}
			}
		}

		public String getKeys()
		{
			return this.selector.keys().toString();
		}

		public String getKeySize()
		{
			return ""+this.selector.keys().size();
		}

		public Thread.State getThreadState()
		{
			return this.getState();
		}

		public int getTimeout()
		{
			return activeselectiontimeout;
		}
		
		private void closeClients()
		{
			Iterator it = selector.keys().iterator();
			
			while (it.hasNext()) 
			{
				SelectionKey key = (SelectionKey) it.next();
				try
				{
					AsyncWebClient client = (AsyncWebClient)key.attachment();
					if(client != null)
					{
						client.close(AWSConstants.READ_SELECTOR_CLOSED);
					}
					else
					{
						key.channel().close();
					}
				}
				catch(Exception ex)
				{
					logger.addExceptionLog(Level.SEVERE, AWSLogConstants.EXCEPTION_WHILE_CLOSING_SOCKET_CHANNEL,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.CLOSE_CLIENTS,  ex);
				}
				try
				{
					key.cancel();
				}
				catch(Exception exp)
				{
					logger.addExceptionLog(Level.SEVERE, AWSLogConstants.EXCEPTION_DURING_KEY_CANCEL,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.CLOSE_CLIENTS,  exp);
				}
			}
		}

		public void run()
		{
			SelectionKey key = null;
			int count = 1;
			for (;;) 
			{
				if(isAvailable)
				{
					try
					{
						registerSocketChannels();
						selector.select(activeselectiontimeout);
						if(count % 250 == 0)
						{
							try
							{
								Thread.sleep(workaroundpause);//WORK AROUND FOR JDK BUG : 6693490 : Need to update jdk 6u17
							}
							catch(Exception ex)
							{
								logger.addExceptionLog(Level.WARNING, AWSLogConstants.INTERRUPTED,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.RUN,  new Object[]{this});
							}
						}

						Iterator it = selector.selectedKeys().iterator();
						
						while (it.hasNext()) 
						{
							key = (SelectionKey) it.next();
							it.remove();
							try
							{
								handleKey(key);
							}
							catch (IOException e) 
							{
								logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.RUN, e);
								try
								{
									AsyncWebClient client = (AsyncWebClient)key.attachment();
									if(client != null)
									{
										client.close(AWSConstants.IOEXCEPTION_SELECTOR_KEY_SELECTION);
									}
									else
									{
										key.channel().close();
									}
								}
								catch(Exception ex)
								{
									logger.addExceptionLog(Level.SEVERE, AWSLogConstants.EXCEPTION_WHILE_CLOSING_SOCKET_CHANNEL, SecondaryReadSelector.class.getName(),AWSLogMethodConstants.RUN,e);
								}
								try
								{
									key.cancel();
								}
								catch(Exception exp)
								{
									logger.addExceptionLog(Level.SEVERE, AWSLogConstants.EXCEPTION_DURING_KEY_CANCEL, SecondaryReadSelector.class.getName(),AWSLogMethodConstants.RUN,e);
								}
							}
							catch (NullPointerException ex) 
							{
								logger.addExceptionLog(Level.SEVERE, AWSLogConstants.SERVER_ERROR,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.RUN ,ex);
							}
							catch(RuntimeException rex)// Temp fix for master secret error in Https : Need to check root cause
							{
								logger.addExceptionLog(Level.SEVERE, AWSLogConstants.RUNTIME_EXCEPTION,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.RUN, rex);
							}
							catch(Exception e)
							{
								logger.addExceptionLog(Level.SEVERE, AWSLogConstants.SELECTORPOOLERROR, SecondaryReadSelector.class.getName(),AWSLogMethodConstants.RUN,e);
							}
						}
						count ++;
					}
					catch (Exception e) 
					{
						logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION, SecondaryReadSelector.class.getName(),AWSLogMethodConstants.RUN,e);
					}
				}
				else
				{
					break;
				}
			}

		}

		public void shutdown() throws Exception
		{
			isAvailable=false;
			interrupt();
			join();
			closeClients();
			selector.close();
		}

		private void handleKey(SelectionKey key) throws IOException 
		{
			try
			{
				long hsttime = System.currentTimeMillis(); 
				String ktype = "multi-selector-a";
				if (key.isValid() && key.isAcceptable())
				{
					logger.addDebugLog(Level.SEVERE, AWSLogConstants.ISACCEPTABLE_FIRED_IN_READ_SELECTOR, SecondaryReadSelector.class.getName(),AWSLogMethodConstants.HANDLE_KEY,new Object[]{key.attachment()});
					/*try
					{
						key.channel().close();
					}
					catch(Exception ex)
					{
						logger.log(Level.WARNING,"Exception during channel close ",ex);//No I18N
					}
					key.cancel();*/
				}
				else
				{
					if(key.attachment() == null)
					{
						logger.addDebugLog(Level.SEVERE, AWSLogConstants.PROBLEN_WITH_KEY_INITIALIZATION,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.HANDLE_KEY, new Object[]{System.currentTimeMillis()-hsttime});

						try
						{
							key.channel().close();
						}
						catch(Exception ex)
						{
							logger.addExceptionLog(Level.WARNING, AWSLogConstants.EXCEPTION_DURING_CHANNEL_CLOSE,SecondaryReadSelector.class.getName(),AWSLogMethodConstants.HANDLE_KEY,ex);
						}
						try
						{
							key.cancel();
						}
						catch(Exception ex)
						{
							logger.addExceptionLog(Level.WARNING, AWSLogConstants.EXCEPTION_DURING_KEY_CANCEL, SecondaryReadSelector.class.getName(),AWSLogMethodConstants.HANDLE_KEY,ex);
						}
						return;
					}	
					
					handler.handle(key);
				}
				//long duration = System.currentTimeMillis() - hsttime;
				if((System.currentTimeMillis() - hsttime) > 1000)
				{
					mtlogger.addDebugLog(Level.INFO, AWSLogConstants.KEY_HANDLE_STATS, SecondaryReadSelector.class.getName(),AWSLogMethodConstants.HANDLE_KEY,new Object[]{port, System.currentTimeMillis()-hsttime, ktype});
				}
			}
			catch(CancelledKeyException cke)
			{
				try
				{
					//key.channel().close();
					AsyncWebClient client = (AsyncWebClient)key.attachment();
					if(client != null)
					{
						logger.addExceptionLog(Level.INFO, AWSLogConstants.CONNECTION_ERROR + "reqUrl:"+client.getRequestURL()+", ipaddr:"+client.getIPAddress(), SecondaryReadSelector.class.getName(),AWSLogMethodConstants.HANDLE_KEY,cke);
						client.close(AWSConstants.CANCELLED_KEY_EXCEPTION_SELECTOR_KEY_SELECTION);
					}
					else
					{
						key.channel().close();
					}
				}
				catch(Exception ex)
				{
					//logger.addExceptionLog(Level.WARNING, AWSLogConstants.EXCEPTION_DURING_CHANNEL_CLOSE, ex);
				}
				key.cancel();
			}
		}
	}
}
