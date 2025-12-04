package com.zoho.wms.asyncweb.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Key;
import java.util.Collection;
import java.util.Locale;
import java.util.logging.Level;

import com.adventnet.iam.security.APIResponseWrapper;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

public class SASResponseWrapper extends APIResponseWrapper
{
	private static AsyncLogger logger = new AsyncLogger("securitylogger");//No I18n

	private HttpResponse httpResponse;

	public SASResponseWrapper(HttpResponse httpResponse)
	{
		this.httpResponse = httpResponse;
	}

	@Override
	public void addCookie(Cookie cookie)
	{
		com.zoho.wms.asyncweb.server.http.Cookie awsCookie = new com.zoho.wms.asyncweb.server.http.Cookie(cookie.getName(), cookie.getValue());
		awsCookie.setDomain(cookie.getDomain());
		awsCookie.setComment(cookie.getComment());
		awsCookie.setMaxAge(cookie.getMaxAge());
		awsCookie.setPath(cookie.getPath());
		awsCookie.setSecure(cookie.getSecure());
		awsCookie.setVersion(cookie.getVersion());
		awsCookie.setHttpOnly(cookie.isHttpOnly());
		httpResponse.addCookie(awsCookie);
		if(ConfManager.isCookieDebugLogEnabled())
		{
			logger.log(Level.INFO, "[AWS DEBUG] [ADDCOOKIE] name:{0}, url:{1}, secure:{2}, httponly:{3}",SASResponseWrapper.class.getName(),AWSLogMethodConstants.ADD_COOKIE, new Object[]{cookie.getName(), httpResponse.getStatsRequestURL(), cookie.getSecure(), cookie.isHttpOnly()});
		}
	}


	@Override
	public void addDateHeader(String key, long value)
	{
		httpResponse.addHeader(key, ""+value);
	}

	@Override
	public void addHeader(String key, String value)
	{
		addResponseHeader(key, value);
	}

	@Override
	public void addIntHeader(String key, int value)
	{
		httpResponse.addHeader(key, ""+value);
	}

	@Override
	public void setDateHeader(String key, long value)
	{
		httpResponse.addHeader(key, ""+value);
	}

	@Override
	public void setHeader(String key, String value)
	{
		addResponseHeader(key, value);
	}


	@Override
	public void setIntHeader(String key, int value)
	{
		httpResponse.addHeader(key, ""+value);
	}


	@Override
	public void setStatus(int sc)
	{
		setStatus(sc, null);
	}


	@Override
	public void setStatus(int sc, String msg)
	{
		httpResponse.addStatusLine(sc, msg);
	}

	private void addResponseHeader(String key, String value)
	{
		if(key!=null && key.equalsIgnoreCase(AWSConstants.HDR_SET_COOKIE))
		{
			httpResponse.addCookie(new com.zoho.wms.asyncweb.server.http.Cookie(value.substring(0, value.indexOf("=")), value.substring(value.indexOf("=")+1), false, false));
			if(ConfManager.isCookieDebugLogEnabled())
			{
				logger.log(Level.INFO, "[AWS DEBUG] [ADDHEADER] name:{0}, url:{1}, secure:false, httponly:false",SASResponseWrapper.class.getName(),AWSLogMethodConstants.ADD_RESPONSE_HEADER, new Object[]{value.substring(0, value.indexOf("=")), httpResponse.getStatsRequestURL()});
			}
		}
		else
		{
			httpResponse.addHeader(key, value);
		}
	}

}
