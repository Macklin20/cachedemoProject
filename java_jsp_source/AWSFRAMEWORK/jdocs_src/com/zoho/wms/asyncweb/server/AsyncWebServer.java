//$Id$

package com.zoho.wms.asyncweb.server;

// Java import
import java.io.*;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.net.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;

public class AsyncWebServer extends Thread
{
	private static AsyncLogger logger = new AsyncLogger(AsyncWebServer.class.getName());
	private static AsyncLogger mtlogger = new AsyncLogger("messagetracker");//No I18n

	private static AtomicLong counter = new AtomicLong(0);

	private int port;
	private Selector selector;
	private AsyncWebEventHandler handler;
	private ServerSocketChannel serverChannel;
	private boolean isRunnable = true;
	private boolean isInternalPort = false;
	
	public AsyncWebServer(int port, AsyncWebEventHandler handler) throws IOException
	{
		super(AWSConstants.AWS_THREAD_PREFIX+"AsyncWebServer"+AWSConstants.THREAD_NAME_SEPARATOR+port+AWSConstants.THREAD_NAME_SEPARATOR+counter.getAndIncrement());//No I18n
		this.port = port;
		this.isInternalPort = ConfManager.isGridPort(port);
		this.handler = handler;
		this.serverChannel = getServerSocketChannel(port);
		selector = getSelector(serverChannel);
	}

	private static ServerSocketChannel getServerSocketChannel(int port) throws IOException
	{
                ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.socket().setReuseAddress(true);

		for(int i=0;  ;i++)
		{
			try
			{
				serverChannel.socket().bind(new InetSocketAddress(port), ConfManager.getBackLog());
				break;
			}
			catch(Exception ex)
			{

				if(i == ConfManager.getBindRetryAttempt())
				{
					throw ex;
				}
				
				logger.log(Level.INFO,"PORT - "+port+" BIND RETRY ATTEMPT #"+(i+1),AsyncWebServer.class.getName(),AWSLogMethodConstants.GET_SERVER_SOCKET_CHANNEL);

				try
				{
					Thread.sleep(500);
				}
				catch(Exception e)
				{
				}
			}
		}

		serverChannel.configureBlocking(false);
		return serverChannel;
	}

	public static Selector getSelector(ServerSocketChannel serverChannel) throws IOException 
	{
		Selector selector = Selector.open();
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		return selector;
	}

	public void listen() 
	{
		SelectionKey key = null;

		for (;;) 
		{
			if(isRunnable)
			{	
				try
				{
					if(ConfManager.getServerCheckStatus() && !serverChannel.isOpen())
					{
						logger.log(Level.SEVERE,"Port - "+port+" is closed. Setting servercheck as false",AsyncWebServer.class.getName(),AWSLogMethodConstants.LISTEN);//No I18N
						ConfManager.setServerCheckStatus(false);
					}
					selector.select(5000);
					Set<SelectionKey> keys = selector.selectedKeys();
					long skeys = keys.size();
					ConnectKeysProfiler.updateCount(port, skeys);
					Iterator it = keys.iterator();
					while (it.hasNext()) 
					{
						key = (SelectionKey) it.next();
						try
						{
							handleKey(key);
						}
						catch (IOException e) 
						{
							logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,AsyncWebServer.class.getName(),AWSLogMethodConstants.LISTEN, e);
							/*try
							{
								key.channel().close();
							}
							catch(Exception ex)
							{
								logger.log(Level.SEVERE,"Exception while closing socket channel ",e);//NO I18N
							}*/
							try
							{
								key.cancel();
							}
							catch(Exception ex)
							{
								logger.addExceptionLog(Level.WARNING, AWSLogConstants.EXCEPTION_DURING_KEY_CANCEL,AsyncWebServer.class.getName(),AWSLogMethodConstants.LISTEN, ex);
							}
						}
						catch (NullPointerException e) 
						{
							logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,AsyncWebServer.class.getName(),AWSLogMethodConstants.LISTEN, e);
						}
						catch(RuntimeException rex)// Temp fix for master secret error in Https : Need to check root cause
						{
							logger.addExceptionLog(Level.SEVERE,AWSLogConstants.RUNTIME_EXCEPTION,AsyncWebServer.class.getName(),AWSLogMethodConstants.LISTEN, rex);//No I18N
						}
						catch(Exception e)
						{
							logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,AsyncWebServer.class.getName(),AWSLogMethodConstants.LISTEN, e);
						}

						it.remove();
					}
				}
				catch (Exception e) 
				{
					logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION, AsyncWebServer.class.getName(),AWSLogMethodConstants.LISTEN,e);
				}
			}
			else
			{
				break;
			}
		}
	}

	public void run()
	{
		listen();
	}

	private void handleKey(SelectionKey key) throws IOException 
	{
		try
		{
			long hsttime = System.currentTimeMillis(); 
			String ktype = "r";
			if(ConfManager.isSelectorPoolMode(port))
			{
				if (key.isValid() && key.isAcceptable())
				{
					ktype = "mutli-selector-a";
					handler.acceptClient(key, port);
				}
				else
				{
					//handler.handle(key);
					/*try
					{
						key.channel().close();
					}
					catch(Exception ex)
					{
						logger.log(Level.WARNING,"Exception during channel close ",ex);//No I18N
					}*/
					try
					{
						key.cancel();
					}
					catch(Exception ex){}
				}
			}
			else
			{
				if (key.isValid() && key.isAcceptable())
				{
					ktype = "a";
					handler.acceptClient(selector, key, port);
				}
				else
				{
					handler.handle(key);
				}
			}
			long duration = System.currentTimeMillis() - hsttime;
			if(duration > 1000)
			{
				mtlogger.log(Level.INFO,"HTTP["+port+"]::Key handle Stats "+duration+"#"+ktype,AsyncWebServer.class.getName(),AWSLogMethodConstants.HANDLE_KEY);
			}
		}
		catch(CancelledKeyException cke)
		{
			logger.addExceptionLog(Level.SEVERE, AWSLogConstants.CONNECTION_ERROR, AsyncWebServer.class.getName(),AWSLogMethodConstants.HANDLE_KEY,cke);
			/*try
			{
				key.channel().close();
			}
			catch(Exception ex)
			{
				logger.log(Level.WARNING,"Exception during key channel close ",ex);//No I18N
			}*/
			try
			{
				key.cancel();
			}
			catch(Exception ex)
			{
			}
		}
	}

	public void stopThis() throws IOException,InterruptedException
	{
		isRunnable = false;
		interrupt();
		join();
		serverChannel.close();
		selector.close();
	}
	
}
