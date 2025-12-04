package com.zoho.wms.asyncweb.server;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class AWSProxyAuthenticator extends Authenticator
{
	private String username;
	private String password;
	
	public AWSProxyAuthenticator(String username, String password)
	{
		this.username = username;
		this.password = password;
	}
	
	@Override
	protected PasswordAuthentication getPasswordAuthentication()
	{
		if(ConfManager.isProxyHost(getRequestingHost()) && ConfManager.isProxyPort(getRequestingPort()))
		{
			return new PasswordAuthentication(username, password.toCharArray());
		}
			
		return null;
	}
} 
