//$Id$
package com.zoho.wms.asyncweb.server.http;

import java.io.IOException;
import java.util.HashMap;

import com.zoho.wms.asyncweb.server.AWSConstants;

public class Cookie
{
	private String name;
	private String value;
	private int age = -1;
	private String domain;
	private String path;
	private boolean secure = true;
	private boolean httponly = true;
	private int version = -1;
	private String comment;
	private String expiryDate;
	private String samesite;
	public static final String SAMESITE_NONE="None";//No I18N
	public static final String SAMESITE_LAX="Lax";//No I18N
	public static final String SAMESITE_STRICT="Strict";//No I18N


	public Cookie(String name, String value)
	{
		this(name, value, true, true);
	}

	public Cookie(String name, String value, boolean secure, boolean httponly)
	{
		if(!isValid(name))
		{
			throw new IllegalArgumentException("Cookie Name Invalid");//No I18N
		}
		this.name = name;
		this.value = value;
		this.secure = secure;
		this.httponly = httponly;
	}

	public void setMaxAge(int age)
	{
		this.age = age;
	}

	public void setDomain(String domain)
	{
		this.domain = domain;
	}

	public void setPath(String path)
	{
		this.path = path;
	}

	public void setSecure(boolean secure)
	{
		this.secure = secure;
	}

	public void setHttpOnly(boolean httponly)
	{
		this.httponly = httponly;
	}

	public void setVersion(int version)
	{
		this.version = version;
	}

	public void setComment(String comment)
	{
		this.comment = comment;
	}

	public void setExpires(String expiryDate)
	{
		this.expiryDate = expiryDate;
	}

	public boolean isValid(String cname)
	{
		for(int i=0;i<cname.length();i++)
		{
			char c = cname.charAt(i);
			if(c <= ' ' || c == ';' || c == ',' || c >= '\177' || c == '=')// No I18N
			{
				return false;
			}
		}
		if(cname.equals("Path") || cname.equals("Domain") || cname.equals("Version") || cname.equals("Secure") || cname.equals("Comment") || cname.equals("Expires") || cname.equals("Max-Age") || cname.equals("Discard"))//No I18N
		{
			return false;
		}
		return true;
	}
	
	public void setSameSite(String samesite) throws IOException
	{
		if(!samesite.matches(SAMESITE_NONE+"|"+SAMESITE_LAX+"|"+SAMESITE_STRICT))//No I18N
		{
			throw new IOException("Unsupported SameSite cookie attribute value : "+samesite);//No I18N
		}
		this.samesite = samesite;
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(name);
		sb.append("=");//No I18N
		sb.append(value);
		sb.append(";");//No I18N
		if(domain != null)
		{
			sb.append("Domain=");//No I18N
			sb.append(domain);
			sb.append(";");//NO I18N
		}
		if(age != -1)
		{
			sb.append("Max-Age=");//No I18N
			sb.append(age);
			sb.append(";");//NO I18N
		}
		if(path!= null)
		{
			sb.append("Path=");//NO I18N
			sb.append(path);
			sb.append(";");//No I18N
		}
		if(expiryDate != null)
		{
			sb.append("Expires=");//No I18n
			sb.append(expiryDate);
			sb.append(";");//No I18N
		}
		if(secure)
		{
			sb.append("Secure;");//No I18N
		}
		if(httponly)
		{
			sb.append("HttpOnly;");//No I18N
		}
		if(comment != null)
		{
			sb.append("Comment=");//No I18N
			sb.append(comment);
			sb.append(";");//NO I18N
		}
		if(version != -1)
		{
			sb.append("Version=");//NO I18N
			sb.append(version);
			sb.append(";");//NO I18N
		}
		if(samesite!=null)
		{
			sb.append("SameSite=");//No I18N
			sb.append(samesite);
			sb.append(";");//No I18N
		}
		return sb.toString();
	}
	
	public String getName()
	{
		return name;
	}

	public HashMap<String, String> getAccessCookieValue()
	{
		HashMap<String, String> cookie = new HashMap<>();
		cookie.put(AWSConstants.NAME, name);
		cookie.put(AWSConstants.SECURE, secure?AWSConstants.TRUE:AWSConstants.FALSE);
		cookie.put(AWSConstants.HTTPONLY, httponly?AWSConstants.TRUE:AWSConstants.FALSE);
		if(path != null)
		{
			cookie.put(AWSConstants.PATH, path);
		}
		if(samesite != null)
		{
			cookie.put(AWSConstants.SAMESITE, samesite);
		}
		if(domain != null)
		{
			cookie.put(AWSConstants.DOMAIN, domain);
		}
		if(age != -1)
		{
			cookie.put(AWSConstants.AGE, ""+age);
		}
		if(expiryDate != null)
		{
			cookie.put(AWSConstants.EXPIREDATE, expiryDate);
		}
		if(version != -1)
		{
			cookie.put(AWSConstants.VERSION, ""+version);
		}
		return cookie;
	}
}
