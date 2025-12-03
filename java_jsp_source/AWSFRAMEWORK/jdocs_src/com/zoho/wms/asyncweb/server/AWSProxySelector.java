package com.zoho.wms.asyncweb.server;

import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class AWSProxySelector extends ProxySelector
{
	private static AsyncLogger logger = new AsyncLogger(AWSProxySelector.class.getName());

	private ProxySelector defSelector;
	private InnerProxy currentProxy;
	private InnerProxy primaryProxy;
	private InnerProxy secondaryProxy;
	private long lastSwitchTime;
	
	public AWSProxySelector(ProxySelector def, String primaryProxy, int proxyPort)
	{
		this(def, primaryProxy, null, proxyPort);
	}

	public AWSProxySelector(ProxySelector def, String primaryProxy, String secondaryProxy, int proxyPort)
	{
		this.defSelector = def;
		this.primaryProxy = new InnerProxy(new InetSocketAddress(primaryProxy, proxyPort));
		if(secondaryProxy!=null)
		{
			this.secondaryProxy = new InnerProxy(new InetSocketAddress(secondaryProxy, proxyPort));
		}
		this.currentProxy = this.primaryProxy;
	}

	public List<Proxy> select(URI uri)
	{
		if(uri == null)
		{
			throw new IllegalArgumentException("URI can't be null");
		}

		if(currentProxy != primaryProxy)
		{
			switchProxy();
		}

		if( (ConfManager.isProxyAccessRestricted() && ConfManager.isProxyEnableDomain(uri.getHost())) || (!ConfManager.isProxyAccessRestricted() && !ConfManager.isProxyExcludeDomain(uri.getHost())) )
		{
			String protocol = uri.getScheme();
			if(protocol.equalsIgnoreCase(AWSConstants.HTTP) || protocol.equalsIgnoreCase(AWSConstants.HTTPS))
			{
				ArrayList<Proxy> l = new ArrayList<Proxy>();
				l.add(currentProxy.toProxy());
				return l;
			}
		}

		if(defSelector != null )
		{
			 return defSelector.select(uri);
		}

		ArrayList<Proxy> l = new ArrayList<Proxy>();
		l.add(Proxy.NO_PROXY);
		return l;
	}

	public void connectFailed(URI uri, SocketAddress addr, IOException ioe)
	{
		if(uri == null | addr == null || ioe == null)
		{
			throw new IllegalArgumentException("Arguements can't be null");
		}
	
		if(currentProxy.getAddress() == addr)
		{
			int failed = currentProxy.failed();
			
			logger.log(Level.INFO,"[PROXY FAILURE][ADDR] "+addr+" [URI] "+uri+" [ATTEMPT] "+failed+" [EXCEPTION] ",AWSProxySelector.class.getName(),AWSLogMethodConstants.CONNECT_FAILED,ioe);

			if(failed >= ConfManager.getProxyRetryAttempt())
			{
				switchProxy();
			}
		}
		else if(!isOtherProxy(addr))
		{
			if(defSelector != null)
			{
				defSelector.connectFailed(uri, addr, ioe);

			}
		}
	}

	public void switchProxy()
	{
		if(currentProxy == primaryProxy)
		{
			if(secondaryProxy != null)
			{
				lastSwitchTime = System.currentTimeMillis();
				currentProxy = secondaryProxy;
				primaryProxy.reset();

				logger.log(Level.INFO,"[PROXY SWITCH][PRIMARY TO SECONDARY][FROM] "+primaryProxy.getAddress()+" [TO] "+secondaryProxy.getAddress(),AWSProxySelector.class.getName(),AWSLogMethodConstants.SWITCH_PROXY);
			}
		}
		else
		{
			if((System.currentTimeMillis() - lastSwitchTime) < ConfManager.getProxySBTime()*60*1000)
			{
				return;
			}

			currentProxy = primaryProxy;
			secondaryProxy.reset();

			logger.log(Level.INFO,"[PROXY SWITCH][SECONDARY TO PRIMARY] "+secondaryProxy.getAddress()+" [TO] "+primaryProxy.getAddress(),AWSProxySelector.class.getName(),AWSLogMethodConstants.SWITCH_PROXY);
		}
	}

	public boolean isOtherProxy(SocketAddress addr)
	{
		return (primaryProxy.getAddress() == addr || (secondaryProxy != null && secondaryProxy.getAddress() == addr));
	}

	class InnerProxy
	{
		Proxy proxy;
		SocketAddress addr;
		int failedCount = 0;

		InnerProxy(InetSocketAddress addr)
		{
			this.addr = addr;
			this.proxy = new Proxy(Proxy.Type.HTTP, addr);
		}

		SocketAddress getAddress()
		{
			return addr;
		}

		Proxy toProxy()
		{
			return proxy;
		}

		int failed()
		{
			return ++failedCount;
		}
		
		void reset()
		{
			failedCount = 0;
		}
	}
}

