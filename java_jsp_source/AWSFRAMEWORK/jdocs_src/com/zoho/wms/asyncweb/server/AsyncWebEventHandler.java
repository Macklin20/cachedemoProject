//$Id$

package com.zoho.wms.asyncweb.server;

// Java import
import java.util.logging.Level;
import java.io.*;
import java.nio.channels.*;

import com.zoho.wms.asyncweb.server.ssl.SSLStartUpTypes;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.runtime.WmsRuntimeCounters;
import com.zoho.wms.asyncweb.server.AsyncWebClient.ProxyHeader;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;

public class AsyncWebEventHandler
{
	private static AsyncLogger logger = new AsyncLogger(AsyncWebEventHandler.class.getName());	

	public void acceptClient(Selector selector, SelectionKey key, int port) throws IOException, ClosedChannelException 
	{		
		try
		{
			ServerSocketChannel server = (ServerSocketChannel) key.channel();
			SocketChannel channel = server.accept();
			long stime = System.currentTimeMillis();
			WmsRuntimeCounters.updatePortAccess(port,stime);
			String ipaddr = channel.socket().getInetAddress().getHostAddress();
	                if(DOSManager.isIPBlocked(ipaddr))
                        {
		                new AsyncLogger("doslogger").finer("Ignoring request from blocked ip "+ipaddr,AsyncWebEventHandler.class.getName(),AWSLogMethodConstants.ACCEPT_CLIENT);//No I18n
				channel.close();
              	                return;
                        }
			channel.configureBlocking(false);
			channel.socket().setSoTimeout(ConfManager.getNativeSoTimeout());
			if(ConfManager.getSendBufferSize() != -1)
			{
	                	channel.socket().setSendBufferSize(ConfManager.getSendBufferSize());
			}
			if(ConfManager.getReceiveBufferSize() != -1)
			{
	                	channel.socket().setReceiveBufferSize(ConfManager.getReceiveBufferSize());
			}
			if(ConfManager.isTcpNoDelayEnabled())
			{
				channel.socket().setTcpNoDelay(true);
			}

			SelectionKey readKey = channel.register(selector, SelectionKey.OP_READ);
			if(ConfManager.isHttpsPort(port) && ConfManager.getSSLStartupType(port) == SSLStartUpTypes.DEFAULT)
			{
				readKey.attach(new AsyncWebSSLClient(readKey, port, stime));
			}
			else
			{
				readKey.attach(new AsyncWebClient(readKey, port, stime));
			}

		}catch(Error e)
		{
			logger.log(Level.INFO, " Exception ",AsyncWebEventHandler.class.getName(),AWSLogMethodConstants.ACCEPT_CLIENT, e);
		}
	}

	public void acceptClient(SelectionKey key, int port) throws IOException, ClosedChannelException 
	{		
		try
		{
			ServerSocketChannel server = (ServerSocketChannel) key.channel();
			SocketChannel channel = server.accept();
			long stime = System.currentTimeMillis();
			WmsRuntimeCounters.updatePortAccess(port,stime);
			String ipaddr = channel.socket().getInetAddress().getHostAddress();
                        if(DOSManager.isIPBlocked(ipaddr))
                        {
                                new AsyncLogger("doslogger").finer("Ignoring request from blocked ip "+ipaddr,AsyncWebEventHandler.class.getName(),AWSLogMethodConstants.ACCEPT_CLIENT);//No I18n
	                        channel.close();
       	                        return;
                        }
			channel.configureBlocking(false);
			channel.socket().setSoTimeout(ConfManager.getNativeSoTimeout());
			if(ConfManager.getSendBufferSize() != -1)
			{
	                	channel.socket().setSendBufferSize(ConfManager.getSendBufferSize());
			}
			if(ConfManager.getReceiveBufferSize() != -1)
			{
	                	channel.socket().setReceiveBufferSize(ConfManager.getReceiveBufferSize());
			}
			if(ConfManager.isTcpNoDelayEnabled())
			{
				channel.socket().setTcpNoDelay(true);
			}

			SelectorPool sp = SelectorPoolFactory.getSelectorPool(port);
			if(sp != null)
			{
				sp.registerRead(channel,new Integer(port),new Long(stime));
			}
			else
			{
				logger.log(Level.SEVERE,"SelectorPool Not Available For "+port,AsyncWebEventHandler.class.getName(),AWSLogMethodConstants.ACCEPT_CLIENT);//No I18N
			}
		}
		catch(Error e)
		{
			logger.log(Level.INFO, " Exception ",AsyncWebEventHandler.class.getName(),AWSLogMethodConstants.ACCEPT_CLIENT, e);
		}
	}

	public void handle(SelectionKey key) throws IOException, CancelledKeyException
	{
		AsyncWebNetDataProcessor.process(key);
	}
}
