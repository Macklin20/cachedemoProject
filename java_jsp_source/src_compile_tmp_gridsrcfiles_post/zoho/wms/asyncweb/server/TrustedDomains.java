//$Id$
package com.zoho.wms.asyncweb.server;

//Java import
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Hashtable;
import java.util.Enumeration;

import com.zoho.wms.asyncweb.server.http.HttpRequest;

public class TrustedDomains
{
	private static String defaultdomain = "zoho.com";

	public static String getDomain(HttpRequest req)
	{
		try
		{
			String host = req.getHost();
			if(host.split("\\.").length > 2)
			{
				host=host.substring(host.indexOf(".")+1);
				return host;
			}
			else if(host.indexOf(".")!=-1)
			{
				return host;
			}
		}catch(Exception exp)
		{
		}
		return defaultdomain;
	}

	private static String findAndReplace(String content, Hashtable placeHolder)
	{
		for(Enumeration e=placeHolder.keys(); e.hasMoreElements();)
		{
			String ph = (String) e.nextElement();
			String value = (String) placeHolder.get(ph);

			content= content.replaceAll(ph,value);			
		}

		return content;
	}

	
	public static ByteBuffer replaceVariables(String filecontent, HttpRequest req)
	{
		Hashtable ht = new Hashtable();
		ht.put("\\$DOMAIN\\$",getDomain(req));

		filecontent = findAndReplace(filecontent,ht);

		try
		{
			return ByteBuffer.wrap(filecontent.getBytes(AWSConstants.UTF_8));
		}catch(IOException ioe)
		{
			return ByteBuffer.wrap("".getBytes());
		}
		
	}


        public static boolean isAllowedReferer(HttpRequest request)
        {
                if(ConfManager.getAllowedRefererPattern()==null){return true;}
                
                String referer = request.getHeader(AWSConstants.REFERER);
                if(referer==null){return true;}
                
                int st = referer.indexOf("://")+3;
                int end = referer.indexOf("/", st);
                return (referer.substring(st, end)).matches(ConfManager.getAllowedRefererPattern());
        }
}
