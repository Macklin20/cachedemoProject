//$Id$
package com.zoho.wms.asyncweb.server.http;

// Java import

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.net.*;
import java.util.logging.Level;
import java.text.SimpleDateFormat;

// Wms import
import com.adventnet.iam.security.SecurityRequestWrapper;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.http2.Http2Constants;
import com.zoho.wms.asyncweb.server.util.CommonIamUtil;
import com.zoho.wms.asyncweb.server.ssl.SSLStartUpTypes;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.WebEngine;
import com.zoho.wms.asyncweb.server.WmsSessionIdGenerator;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.AWSConstants;

import com.adventnet.wms.common.HttpDataWraper;
import com.adventnet.wms.servercommon.util.WmsSecurityUtil;
import com.adventnet.wms.servercommon.WMSAuthTypes;
import com.adventnet.wms.common.exception.WMSException;

import com.adventnet.iam.security.UploadedFileItem;

public class HttpRequest
{
	private static AsyncLogger logger = new AsyncLogger(HttpRequest.class.getName());

	private HashMap headerValues=new HashMap();	
	private HashMap originalHeaderValues=new HashMap();	
	private HashMap paramValues=new HashMap();
	private String reqUrl;
	private String rawUrl;
	private String statsRequestUrl;
	private String ipaddr;
	private int localport;
	private String clientip;
	private String version;
	private byte[] bodyContent ;
	private String reqType;
	private HttpStream stream;
	private int notificationtype;
	private String scheme;
	private String enginename;
	private Locale prefLocale;
	private Hashtable localeMap = new Hashtable();
	private Hashtable<String, Part> multipart = new Hashtable();
	private int connectiontype;
	private String wsKey;
	private String wsVersion;
	private String wsExtension;
	private String wsSupportedExtension;
	private String wsSupportedProtocol;
	private boolean websocket=false;
	private boolean isCompressionEnabled=false;
	private HashMap compressionDetails;
	private Multipart mimepart;
	private long zuid = -1;
	private String miurl = null;
	private HashMap<String, String[]> filteredParameterMap = null;
	private ArrayList<UploadedFileItem> validatedMultipartData = null;
	private String filteredStreamContent = null;
	private SecurityRequestWrapper securityRequestWrapper = null;
	private ReentrantLock syncLock  = new ReentrantLock(true);

	/**
	 * To initialize a http request
	 * @param reqType - type of http request such as GET,POST,etc.
	 * @param reqUrl - request url of the http request
	 * @param version - http version. Ex HTTP/1.0
	 * @param ipaddr - ip address to which the connection is made
	 * @param headerMap - list of headers in the request
	 * @param paramMap - list of request parameters
	 * @param bodyContent - request content
	 * @param notificationtype - state of the request Ex: StateConstants.ON_COMPLETE
	 * @param stream - httpstream of the request to get digest data
	 */

	 public HttpRequest(String reqType,String reqUrl, String rawUrl, String version ,String ipaddr,HashMap headerMap,HashMap originalHeaderMap,HashMap paramMap,byte[] bodyContent,int notificationtype,HttpStream stream, int port, String scheme, String engineName) throws IOException 
	{	
		this.reqType = reqType;
		this.reqUrl = reqUrl;
		this.rawUrl = rawUrl;
		this.version = version;
		this.ipaddr=ipaddr;
		this.originalHeaderValues = originalHeaderMap;
		this.headerValues = headerMap;
		this.paramValues = paramMap;
		this.enginename = engineName;
		setBody(bodyContent);
		this.notificationtype = notificationtype;
		this.stream = stream;
		parseLocale();
		handleWebSocket();
		this.clientip = getRemoteAddr();
		this.scheme = scheme;
		setLocalPort(port);
	}
	
	/**
	 * To set the ip address of the client
	 * @param ip - ip address 
	 */

	public void setClientIP(String ip)
	{
		this.clientip = ip;
	}
	
	/**
	 * Connected local port of the request.
	 */

	public void setLocalPort(int port)
	{
		this.localport = port;
		if(ConfManager.isHttpsPort(port))
		{
			this.connectiontype = ConfManager.getSSLStartupType(port);
		}
		else
		{
			this.connectiontype = SSLStartUpTypes.NONE;
		}
	}

	/**
	 * To set engine associated with the request
	 * @param enginename - name of the request
	 */
	
	public void setEngineName(String enginename)
	{
		this.enginename = enginename;
	}

	/**
	 * To set scheme of the request
	 * @param scheme - scheme Ex:http,https
	 */

	public void setScheme(String scheme)
	{
		this.scheme = scheme;
	}

	/**
	 * To set header values of the request
	 * @param headerValues - list of header name and values
	 */
	
	public void setHeaders(HashMap headerValues)
	{
		this.headerValues = headerValues;
	}

	/**
	 * To set parameter values of the request
	 * @param paramValues - list of header name and values
	 */
	
	public void setParams(HashMap paramValues)
	{
		this.paramValues = paramValues;
	}

	/**
	 * To set body content of the request
	 * @param bodyContent - data bytes to be sent 
	 */
	
	public void setBody(byte[] bodyContent)
	{
		this.bodyContent = bodyContent;
		String boundary = null;
		String charset = null;
		try
		{
			String contentType = getHeader(AWSConstants.CONTENT_TYPE);
			
			if((contentType!=null) && contentType.toLowerCase().startsWith(AWSConstants.MULTIPART) && bodyContent!=null)
			{
				boundary = getHeaderParameter(contentType,AWSConstants.BOUNDARY);  
				charset = getHeaderParameter(contentType,AWSConstants.CHARSET);

				if(boundary == null)
				{
					throw new AWSException("Invalid MIME header "+contentType);//No I18n
				}

				if(charset == null)
				{
					charset = "ISO-8859-1";
				}

				mimepart = new Multipart(contentType, boundary, charset);
				String parts[] = (new String(bodyContent,charset)).trim().split("--"+boundary);
				
				mimepart.setPreambleContent(parts[0].replace("\r\n", AWSConstants.EMPTY_STRING));//No I18n
				for(int i = 1; i < (parts.length-1); i++)
				{
					Part part = new Part(parts[i],charset);
					if (part.getName() != null)
					{
						multipart.put(part.getName(),part);
					}
					mimepart.addPart(part);
				}

				if(!(parts[parts.length-1].equals("--")))
				{
					if(parts[parts.length-1].startsWith("--"))
					{
						mimepart.setEpilogueContent(parts[parts.length-1].replace("--\r\n", AWSConstants.EMPTY_STRING));//No I18n
					}
					else
					{
						throw new AWSException("Invalid MIME content closure "+parts[parts.length-1]);//new line after closure //No I18N
					}
				}
			}
		}
		catch(ArrayIndexOutOfBoundsException aex)
		{
			logger.log(Level.INFO,"Exception in MULTIPART , Body Null :: reqUrl:"+reqUrl+", ip:"+ipaddr+", boundary:"+boundary+", charset:"+charset+", params:"+paramValues,HttpRequest.class.getName(),AWSLogMethodConstants.SET_BODY ,new Exception("Body Null"));
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception in MULTIPART :: reqUrl:"+reqUrl+", ip:"+ipaddr+", boundary:"+boundary+", charset:"+charset+", params:"+paramValues,HttpRequest.class.getName(),AWSLogMethodConstants.SET_BODY, ex);
		}
	}

	/** 
	 * To get all parts of a MIME/multipart data
	 * @return - hashtable of parts
	 */

	public Hashtable getParts()
	{
		return multipart;
	}

	/** 
	 * To get part of a MIME/multipart data
	 * @param name - name of the part
	 * @return - part
	 */

	public Part getPart(String name)
	{
		return multipart.get(name);
	}

	/**
	 * To get all parts of a MIME/multipart data
	 * @return - multipart instance
	 */

	public Multipart getMultipart()
	{
		return mimepart;
	}

	/**
	 * To get header parameter value
	 * @param headerValue - value of header
	 * @return - parameter
	 */

	private String getHeaderParameter(String headerValue, String parameter)
	{
		String[] parameters = headerValue.split(";");
		for(int i=1; i<parameters.length; i++)
		{
			if(parameters[i].contains(parameter))
			{
				String[] keyValue = parameters[i].split("=");
				
				if(keyValue.length ==2)
				{	
					return keyValue[1].trim().replace("\"", AWSConstants.EMPTY_STRING);
				}
				else
				{
					logger.log(Level.INFO,"UNDEFINED HEADER PARAM : "+parameters[i],HttpRequest.class.getName(),AWSLogMethodConstants.GET_HEADER_PARAMETER);
				}
			}
		}
		return null;
	}

	/**
	 * To process accept-language header
	 */
	
	private void parseLocale()
	{
		try
		{
			String accLang = (String)headerValues.get(AWSConstants.ACCEPT_LANGUAGE);
			String lg[] = accLang.split(",");
			for (int i = 0;i < lg.length;i++)
			{
				String[] larr = lg[i].trim().split(";");
				Locale localeObj = null;
				String[] lpr = larr[0].split("-");
				switch(lpr.length)
				{
					case 2: localeObj = new Locale(lpr[0], lpr[1]); break;
					case 3: localeObj = new Locale(lpr[0], lpr[1], lpr[2]); break;
					default: localeObj = new Locale(lpr[0]); break;
				}

				if(lg[i].contains("q="))
				{
					localeMap.put(localeObj, AWSConstants.EMPTY_STRING);
				}
				else
				{
					prefLocale = localeObj;
				}
			}
		}
		catch(Exception ex)
		{
		}
	}

	/**
	 * To get the locale associated with the request
	 * @return - Locale in accept-language else default locale 
	 */
	
	public Locale getLocale()
	{
		if(this.prefLocale == null)
		{
			return Locale.getDefault();
		}
		return this.prefLocale;
	}

	/** 
	 * To get the list of locale associated with the request
	 * @return - a enumeration of locale available in accept-language header or default locale
	 */

	public Enumeration getLocales()
	{
		if(this.localeMap.size() == 0)
		{
			this.localeMap.put(Locale.getDefault(), AWSConstants.EMPTY_STRING);
		}
		return this.localeMap.keys();
	}
	
	/** 
	 * To get the httpstream associated with the request
	 * @return - httpstream of the request
	 */

	public HttpStream getHttpStream()
	{
		return this.stream;
	}

	/**
	 * To get the request body 
	 * @return - request body as byte array
	 */

	public byte[] getBody()
	{
		return bodyContent;
	}
	
	/**
	 * To get the request type 
	 * @return - request type as string
	 */

	public String getRequestType()
	{
		return reqType;
	}

	/**
	 * To get the local port
	 * @return - port as int
	 */

	public int getLocalPort()
	{
		return localport;
	}

	/**
	 * To get the connection type
	 * @return - type as int
	 */

	public int getConnectionType()
	{
		return connectiontype;
	}

	/**
	 * To check whether this request is on SSL default mode(ie local decryption)
	 * @return boolean
	 */

	public boolean isSSLDefault()
	{
		return (connectiontype == SSLStartUpTypes.DEFAULT);
	}

	/**
	 * To check whether this request is on SSL offloader mode(ie L7 decryption)
	 * @return boolean
	 */

	public boolean isSSLOffloader()
	{
		return (connectiontype == SSLStartUpTypes.OFFLOADER);
	}

	/**
	 * To check whether this request is on non-ssl mode 
	 * @return boolean
	 */

	public boolean isPlain()
	{
		return (connectiontype == SSLStartUpTypes.NONE);
	}
	
	/**
	 * To get the request body
	 * @return - request body as string
	 * 
	 */
	
	public String getBodyAsString()
	{
		if(reqType.equals(AWSConstants.POST_REQ))
		{
			return new String(bodyContent);
		}
		return null;
	}

	/**
	 * To get the request url 
	 * @return - request url as string
	 */
	
	public String getRequestURL() 
	{
		return reqUrl;
	}

	/**
	 * To get the raw request url 
	 * @return - raw request url as string
	 */
	
	public String getRawRequestURL() 
	{
		return rawUrl;
	}

	public String getStatsRequestUrl()
	{
		return statsRequestUrl;
	}

	public void setStatsRequestUrl(String url)
	{
		this.statsRequestUrl = url;
	}
	
	/**
	 * To get the request status
	 * @return - Integer representing the state of the request
	 *
	 * Note: List of Possible values
	 * 	 1. StateConstants.REQUEST_IN_PROCESS = 1
	 * 	 2. StateConstants.REQUEST_ACKNOWLEDGED = 2
	 * 	 3. StateConstants.ON_HEADER_COMPLETION = 10
	 * 	 4. StateConstants.ON_DATA = 11
	 * 	 5. StateConstants.ON_COMPLETION = 12
	 * 	 6. StateConstants.ON_OUTPUTBUFFERREFILL = 13
	 * 	 7. StateConstants.ON_WRITECOMPLETE = 14
	 * 	 8. StateConstants.ON_WRITEFAILURE = 15
	 */
	
	public int getState()
	{
		return notificationtype;
	}

	public void setState(int notificationtype)
	{
		this.notificationtype = notificationtype;
	}
	
	/**
	 * To get the remote ip address associated with the client
	 * @return - ip address 
	 */

	public String getRemoteAddr()
	{
		if(clientip != null)
		{
			return clientip;
		}
		if(ipaddr.indexOf(":") != -1)
		{
			if(ipaddr.split(":").length == 2)
			{
				return ipaddr.split(":")[0];
			}
			else if(ipaddr.split(":").length == 8 || ipaddr.split(":").length == 9)
			{
				try
				{
					return isLocalIPV6Address(ipaddr) ?InetAddress.getLocalHost().getHostAddress() :getIPV6HostAddress(ipaddr);
				}
				catch(Exception ex)
				{
					logger.log(Level.INFO, " Exception ",HttpRequest.class.getName(),AWSLogMethodConstants.GET_REMOTE_ADDR, ex);
				}
			}
		}
		return ipaddr;
	}

	private String getIPV6HostAddress(String ipv6)
	{
		return ipv6.substring(0,ipv6.lastIndexOf(":"));
	}

	private boolean isLocalIPV6Address(String ipv6)
	{
		String[] split = ipv6.split(":");

		if((split[0].equals(AWSConstants.VALUE_0) && split[1].equals(AWSConstants.VALUE_0) && split[2].equals(AWSConstants.VALUE_0) && split[3].equals(AWSConstants.VALUE_0) && split[4].equals(AWSConstants.VALUE_0) && split[5].equals(AWSConstants.VALUE_0) && split[6].equals(AWSConstants.VALUE_0) && split[7].equals(AWSConstants.VALUE_1)))
		{
			return true;
		}

		return false;
	}

	/**
	 * To get wnet address associated with the request
	 * @return - wnet address 
	 */

	public String getWnetAddress()
	{
		return ipaddr;
	}

	/**
	 * To get the value of tracking-id header
	 * @return - tracking-id header value
	 */
	
	public String getTrackingId() throws Exception
	{
		return getHeader(AWSConstants.TRACKING_ID);
	}

	/**
	 * To get the domain name in host header
	 * @return - domain name 
	 */
	
	public String getHost()
	{
		try
		{
			String host= getHeader(AWSConstants.HOST);
			return host.replaceFirst(":.*$", AWSConstants.EMPTY_STRING);
		}catch(Exception exp)
		{
			return AWSConstants.DEFAULT;//No I18N
		}
	}

	/**
	 * To get the domain name in origin header
	 * @return - origin domain name
	 */

	public String getOrigin()
	{
		try
		{
			String origin = getHeader(AWSConstants.ORGIN).replaceAll(AWSConstants.FOUR_PROTOCOLS, AWSConstants.EMPTY_STRING).replaceAll("/.*", AWSConstants.EMPTY_STRING);//No I18n
			return origin.replaceFirst(":.*$", AWSConstants.EMPTY_STRING).trim();
		}
		catch(Exception exp)
		{
			return null;
		}
	}

	/**
	 * To get the domain name in referer header
	 * @return - referer domain name
	 */

	public String getReferer()
	{
		try
		{
			String referer = getHeader(AWSConstants.REFERER).replaceAll(AWSConstants.FOUR_PROTOCOLS, AWSConstants.EMPTY_STRING).replaceAll("/.*", AWSConstants.EMPTY_STRING);//No I18n
			return referer.replaceFirst(":.*$", AWSConstants.EMPTY_STRING).trim();
		}
		catch(Exception exp)
		{
			return null;
		}
	}

	/**
	 * To get the complete host header
	 * @return - host header value
	 */
	
	public String getRawHost() throws Exception
	{
		try
		{
			String host= getHeader(AWSConstants.HOST);
			return host;
		}
		catch(Exception exp)
		{
			throw exp;
		}
	}

	/**
	 * To get the port to which the request is connected
	 * @return - port
	 */
	
	public String getPort()
	{
		try
		{
			String host= getHeader(AWSConstants.HOST);
			return (Integer.parseInt(host.replaceFirst(".*:", AWSConstants.EMPTY_STRING))+AWSConstants.EMPTY_STRING);//No I18N
		}
		catch(Exception exp)
		{
		}
	
		if(getScheme().equals(AWSConstants.HTTPS) || getScheme().equals(AWSConstants.WSS))
		{
			return AWSConstants.STATUS_CODE_443;
		}
		
		return "80";
	}

	/**
	 * To get the user-agent associated with the request
	 * @return - user-agent
	 */
	
	public String getUserAgent()
	{
		try
		{
			return (hasHeader(AWSConstants.USER_AGENT)) ? getHeader(AWSConstants.USER_AGENT) : AWSConstants.UNKNOWN;
		}
		catch(Exception e)
		{	
		}
		return AWSConstants.UNKNOWN;
	}
		
	/**
         * To get value associated with the originalheader
         * @param key - name of the header
         * @return - value of the header
         */

        public String getOriginalHeader(String key)
        {
                return (String)originalHeaderValues.get(key);                                                        
        }

	/**
	 * To get value associated with the header
	 * @param key - name of the header
	 * @return - value of the header
	 */
	
	public String getHeader(String key) 
	{
             	return (String)headerValues.get(key);
	}

	/**
	 * To check if the header is present
	 * @param key - name of the header
	 * @return - true if present
	 */

	public boolean hasHeader(String key)
	{
		return headerValues.containsKey(key);
	}

	/**
         * To get headers associated with the request
         * @return - entire list of headers in the request
         */
        
        public HashMap getOriginalHeaders()
        {
                return originalHeaderValues;
        }

	/**
	 * To get headers associated with the request
	 * @return - entire list of headers in the request
	 */
	
	public HashMap getHeaders()
	{
		return headerValues;
	}

	/**
	 * To add header to the request
	 * @param key - name of the header
	 * @param value - value of the header
	 * @return - status of the operation true - success false - failure
	 */
	
	public boolean addHeader(String key, String value)
	{
		if(headerValues.get(key)==null)
		{
			headerValues.put(key,value);
			return true;
		}
		return false;
	}

	public boolean addOriginalHeader(String key, String value)
	{
		if(!originalHeaderValues.containsKey(key))
		{
			originalHeaderValues.put(key, value);
			return true;
		}
		return false;
	}

	/**
	 * To remove header from the request
	 * @param key - name of the header
	 * @return - value of the header
	 */
	
	public String removeHeader(String key)
	{
		return (String)headerValues.remove(key);
	}

	/**
	 * To get value of the request parameter
	 * @param key - request parameter name
	 * @return - value of the request parameter
	 */
	
	public String getParameter(String key)
	{
		return (String)paramValues.get(key);
	}

	/**
	 * To check if the parameter is present
	 * @param key - request parameter name
	 * @return - true if present
	 */

	public boolean hasParameter(String key)
	{
		return paramValues.containsKey(key);
	}

	/**
	 * To get request parameters associated with the request
	 * @return - list of request parameters 
	 */

	public HashMap getParams()
	{
		return paramValues;
	}

	/**
	 * To determine if the connection will be kept alive based on connection header
	 * @return - true - kept alive
	 * 	     false - will be closed
	 */
	
	public boolean closeAfterResponse() 
	{
		//return getHeader(AWSConstants.CONNECTION).charAt(0) == 'c';
		return (this.version.equals(AWSConstants.HTTP_1_1))? getHeader(AWSConstants.CONNECTION).charAt(0) == 'c' : true;
	}

	/**
	 * To get the value of content-length header
	 * @return - content-length value
	 */
	
	public int getContentLength() 
	{
		String lengthStr = (String) headerValues.get(AWSConstants.CONTENT_LENGTH);
		return (lengthStr != null) ? Integer.parseInt(lengthStr) : 0;
	}

	/**
	 * To get the value of if-modified-since header
	 * @return - if-modified-since value
	 */
	
	public long getModifiedSince() 
	{
		String tmp = (String) headerValues.get(AWSConstants.IF_MODIFIED_SINCE);
		try
		{
			return (tmp == null) ? 0: new SimpleDateFormat(AWSConstants.SIMPLE_DATE_FORMAT).parse(tmp).getTime();
		}catch(Exception e)
		{
			return 0;
		}
	}

	/**
	 * To get authdetails associated with the request
	 * @return - authtype - WMSAuthTypes.DEFAULT = 0
	 * 	 	       	WMSAuthTypes.AUTH_TOKEN = 1
	 *                      WMSAuthTypes.PORTAL_USER = 2  
	 *                      WMSAuthTypes.SS_TICKET = 3
	 *                      WMSAuthTypes.ANNON_AUTH = 4  
	 *                      WMSAuthTypes.PEX_AUTH = 5
	 *                      WMSAuthTypes.PORTAL_SST = 6  
	 *
	 * Note: The key-value in hashtable are String type
	 */
	
	public Hashtable getAuthDetails()
	{
		try
		{
			String authstr = getParameter("xa");
			if(authstr==null)
			{
				Hashtable authdetails = new Hashtable();
				authdetails.put("authtype", ""+WMSAuthTypes.DEFAULT);	
				return authdetails;
			}
			return (Hashtable)HttpDataWraper.getObject(WmsSecurityUtil.decrypt(authstr));
		}catch(Exception e)
		{
			logger.log(Level.INFO, " Exception ",HttpRequest.class.getName(),AWSLogMethodConstants.GET_AUTH_DETAILS, e);
		}
		return null;
	}
	
	/** 
	 * To get oauth token from the request
	 * @return - ticket
	 */

	public String getOAuthToken()
	{
		String auth = getHeader(AWSConstants.AUTHORIZATION);//No I18N
		
		if(auth!=null && auth.startsWith(CommonIamUtil.OAUTH_PREFIX))
		{
			return auth.split(CommonIamUtil.OAUTH_PREFIX)[1];
		}
		
		return null;
	}
		

	/** 
	 * To get the ticket associated with the request from cookie header or ticket parameter
	 * @return - ticket
	 */

	public String getTicket()
	{
		String ticket = getParameter(AWSConstants.TICKET);

		if(ticket!=null && !ticket.isEmpty())
		{
			return ticket;
		}

		String cookie = getHeader(AWSConstants.COOKIE);
		if(cookie!=null)
		{

			try
			{
				StringTokenizer st = new StringTokenizer(cookie,";");
			
				while(st.hasMoreTokens())
				{
					String t = st.nextToken().trim();
					if(t.startsWith(CommonIamUtil.getIamTicketName()+"=") && t.split("=").length==2)
					{
						return t.split("=")[1].trim();
					}
				}
			}
			catch(Exception e)
			{
				logger.log(Level.INFO, " Exception ",HttpRequest.class.getName(),AWSLogMethodConstants.GET_TICKET, e);
			}			
		}
		return null;
	}

	/**
	 * To get token pair details for authcheck
	 @return - Hashtable with entries
	 * 	     1.domain=domainname
	 * 	     2.ticket=ticketvalue
	 * 	     3.iamcookies = iamcookies
	 *
	 */

	public Hashtable getTokenPairDetails()
	{
		String cookie = getHeader(AWSConstants.COOKIE);
		Hashtable details = new Hashtable();
		Properties cookieprops = new Properties();
		try
		{
			cookieprops.load(new StringReader(cookie.replace(";","\n")));
			String zaid = getParameter(AWSConstants.ZAID);
			String tkpticketcookie = WebEngine.getEngineByAppName(enginename).getTKPTicketCookie();
			String tkpdomaincookie = WebEngine.getEngineByAppName(enginename).getTKPDomainCookie();
			if((zaid != null && cookieprops.get(tkpticketcookie+"-"+zaid) != null) || cookieprops.get(tkpticketcookie) != null)
			{
				List<String> iamCookieNames = CommonIamUtil.getIAMCookieNames();
				Map<String, String> iamCookies = new Hashtable<String, String>();
				for(String cookieName : iamCookieNames){
					if(cookieprops.get(cookieName)!=null){
						iamCookies.put(cookieName, cookieprops.getProperty(cookieName));
					}
				}
				details.put(AWSConstants.IAMCOOKIES, iamCookies);
				
				String val = (String)cookieprops.get(tkpticketcookie+"-"+zaid);
				if(val == null)
				{
					val = (String)cookieprops.get(tkpticketcookie);
				}
				if(val.split("=").length==1)
				{
					details.put(AWSConstants.TICKET,val.trim());
				}

				if(cookieprops.get(tkpdomaincookie)!=null)
				{
					String keyval = (String)cookieprops.get(tkpdomaincookie);
					if(keyval.split("=").length==1)
					{
						details.put(AWSConstants.DOMAIN,keyval.trim());
					}
				}
				return details;
			}
			else if(getHeader(tkpticketcookie+"-"+zaid)!=null || getHeader(tkpticketcookie)!=null)
			{
				String val = getHeader(tkpticketcookie+"-"+zaid);

				if(val == null)
				{
					val = getHeader(tkpticketcookie);
				}

				if(val.split("=").length==1)
				{
					details.put(AWSConstants.TICKET,val.trim());
				}

				if(getHeader(tkpdomaincookie)!=null)
				{
					String keyval = getHeader(tkpdomaincookie);
					if(keyval.split("=").length==1)
					{
						details.put(AWSConstants.DOMAIN,keyval.trim());
					}
				}

				details.put(AWSConstants.HEADER,AWSConstants.TRUE);

				return details;
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.WARNING,"Unable to get token pair details: "+getParameter(AWSConstants.ZUID),HttpRequest.class.getName(),AWSLogMethodConstants.GET_TOKEN_PAIR_DETAILS,ex); //NO I18N
		}
		return new Hashtable();
	}

	public String getWMSTokenFromCookie()
	{
		try
		{
			String zaid = getParameter(AWSConstants.ZAID);
			String wmstoken = getCookie(AWSConstants.WMS_TKP_TOKEN_HYPHEN+zaid);
			if(wmstoken == null)
			{
				return getCookie(AWSConstants.WMS_TKP_TOKEN);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.WARNING,"Unable to get wms token pair details: "+getParameter(AWSConstants.ZUID),HttpRequest.class.getName(),AWSLogMethodConstants.GET_WMS_TOKEN_FROM_COOKIE,ex); //NO I18N
		}
		return null;
	}

	/**
	 * To get the cookie value associated with the reqeust
	 * @param cookiename - name of the cookie
	 * @return - cookie value
	 */
	
	public String getCookie(String cookiename)
	{
		String cookie = getHeader(AWSConstants.COOKIE);
		if(cookie!=null)
		{
			try
			{
				StringTokenizer st = new StringTokenizer(cookie,";");//No I18N
			
				while(st.hasMoreTokens())
				{
					String t = st.nextToken().trim();
					if(t.startsWith(cookiename+"=") && t.split("=").length==2)//No I18N
					{
						return t.split("=")[1].trim();//No I18N
					}
				}
			}
			catch(Exception e)
			{
				logger.log(Level.INFO, " Exception ",HttpRequest.class.getName(),AWSLogMethodConstants.GET_COOKIE, e);
			}			
		}
		return null;
	}

	
        /**
	 * To get the value of lbsslremoteip Header
	 * @return - ip value. 
	 */

	public String getClientIPFromSSLLBLHeader()
        {
                return getHeader(ConfManager.getClientIPHeader());
        }

	/** 
	 * To get the name of the engine processing this request
	 * @return - engine name
	 */

	public String getEngineName()
	{
		return this.enginename;
	}

	/**
	 * To get the scheme associated with the request
	 * @return - scheme Ex: http,https 
	 */

	public String getScheme()
	{
		return this.scheme;
	}

	/**
	 * To check if request is chunk streamed 
	 * @return - true, if chunk streamed
	 *           false , if isn't
	 */

	public boolean isChunkedStream()
	{
		try
		{
			String  chunkheader = getHeader(ConfManager.getChunkedStreamTimeoutHeader());
			long timeout = Long.parseLong(chunkheader);

			return (timeout > 0);
		}
		catch(Exception ex)
		{
			return false;
		}
	}

	/**
	 * To get the value of chunked timeout header
	 * @return - timeout value
	 */
	
	public long getChunkedStreamTimeout()
	{
		try
		{
			String  chunkheader = getHeader(ConfManager.getChunkedStreamTimeoutHeader());
			long timeout = Long.parseLong(chunkheader);

			return timeout;
		}
		catch(Exception ex)
		{
			return ConfManager.getSoTimeout();
		}
	}

	public boolean isHttp2()
	{
		return (this.version == Http2Constants.HTTP2_VERSION);
	}

	public String getVersion()
	{
		return version;
	}

	public void handleWebSocket()
	{
		try
		{
			if(isHttp2())
			{
				return;
			}
			String upgrade = getHeader(AWSConstants.UPGRADE);
			if(upgrade==null) { return; }
			if(!upgrade.toLowerCase().equals(AWSConstants.WEBSOCKET))
			{
				return;
			}
			websocket = true;

			wsKey = getHeader(AWSConstants.SEC_WEBSOCKET_KEY);

			wsVersion = getHeader(AWSConstants.SEC_WEBSOCKET_VERSION);

			wsExtension = getHeader(AWSConstants.SEC_WEBSOCKET_EXTENSIONS);

			wsSupportedProtocol = getHeader(AWSConstants.SEC_WEBSOCKET_PROTOCOL);

			if(wsExtension!=null)
			{
				boolean isPerMessageDeflate = false;
				// Temporary - should be done according to spec to support multiple extension
				// as of now we support only permessage-deflate and client_max_window_bits - no value for client_max_... the default is 15
				// due to limits in current jdk
				String wsExt[] = wsExtension.split(";");

				if(wsExt.length>0)
				{
					logger.log(Level.FINE,"GS --> websocket extension "+wsExtension,HttpRequest.class.getName(),AWSLogMethodConstants.HANDLE_WEBSOCKET);
					if(wsExt.length==1)
					{
						isPerMessageDeflate = wsExt[0].trim().equals(AWSConstants.PERMESSAGE_DEFLATE);
					}
					else if(wsExt.length==2)
					{
						isPerMessageDeflate = wsExt[0].trim().equals(AWSConstants.PERMESSAGE_DEFLATE) && wsExt[1].trim().equals(AWSConstants.CLIENT_MAX_WINDOW_BITS);

					}
					else
					{
						logger.log(Level.INFO,"GS --> websocket extension not supported wsExt="+wsExtension,HttpRequest.class.getName(),AWSLogMethodConstants.HANDLE_WEBSOCKET);
					}
				}

				if(isPerMessageDeflate && ConfManager.isWebSocketCompressionEnabled())
				{
					wsSupportedExtension = AWSConstants.PERMESSAGE_DEFLATE;
					isCompressionEnabled = true;
					compressionDetails = new HashMap();
					compressionDetails.put(AWSConstants.TYPE,AWSConstants.PERMESSAGE_DEFLATE);
					compressionDetails.put(AWSConstants.CLIENT_MAX_WINDOW_BITS, AWSConstants.EMPTY_STRING); // no value -default 15
					//compressionDetails.put("server_max_window_bits","15"); // to controll memory foot print this can be reduced to 11/12 jdk doesn't support this so going by default
					//compressionDetails.put("server_no_context_takeover",""); // no need to support - will be inefficient
					//compressionDetails.put("client_no_context_takeover",""); // no need to support - will be inefficeint


				}

				logger.log(Level.FINE,"GS --> websocket compression supported : permessage-deflate "+isPerMessageDeflate,HttpRequest.class.getName(),AWSLogMethodConstants.HANDLE_WEBSOCKET);
			}

		}
		catch(Exception e)
		{
			logger.log(Level.INFO, " Exception ",HttpRequest.class.getName(),AWSLogMethodConstants.HANDLE_WEBSOCKET, e);
		}

	}

	/**
	 * To check if request support WebSocket
	 * @return - true, if WebSocket
	 * 	     false, if not
	 */
	
	public boolean isWebSocket()
	{
		return websocket;
	}

	/**
	 * To get the value of sec-websocket-key header
	 * @return - sec-websocket-key value
	 */

	public String getWebSocketKey()
	{
		return wsKey;
	}

	/**
	 * To get the value of sec-websocket-version header
	 * @return - sec-websocket-version value
	 */
	
	public String getWebSocketVersion()
	{
		return wsVersion;
	}

	/**
	 * To get the value of sec-websocket-extensions
	 * @return - sec-websocket-extensions value
	 */

	public String getWebSocketExtension()
	{
		return wsExtension;
	}

	/** 
	 * To get the value of websocket supported extension
	 * @return - websocket supported extension
	 */

	public String getWebSocketSupportedExtension()
	{
		return wsSupportedExtension;
	}

	/**
	 * To get the value of websocket supported sub-protocols
	 * @return - websocket supported sub-protocols
	 */

	public String getWebSocketProtocol()
	{
		return wsSupportedProtocol;
	}

	/** 
	 * To check if compression is enabled for websocket
	 * @return - true, compression enabled
	 * 	     false, compression disabled
	 */

	public boolean isCompressionEnabled()
	{
		return isCompressionEnabled;
	}

	/** 
	 * To get compression related details for websocket
	 * @return - compression details
	 */

	public HashMap getCompressionDetails()
	{
		return compressionDetails;
	}



	/**
	 * To get the type value of x-wsinfo header 
	 * @return - wstype value
	 */
	
	public int getWSType()
	{
		try
		{
			return Integer.parseInt(getHeader(AWSConstants.X_WSINFO).split("-")[1]);//No I18N
		}
		catch(Exception ex)
		{
			return -1;
		}
	}

	/**
	 * To get the prd value in x-wsinfo header
	 * @return - wsprd value
	 */
	
	public String getWSPrd()
	{
		try
		{
			return getHeader(AWSConstants.X_WSINFO).split("-")[0];//No I18N
		}
		catch(Exception ex)
		{
			return null;
		}
	}

	public void setZUID(long zuid)
	{
		this.zuid = zuid;
	}

	public long getZUID()
	{
		return zuid;
	}

	public void setFilteredParameterMap(HashMap<String, String[]> filteredParameterMap)
	{
		this.filteredParameterMap = filteredParameterMap;
	}

	/**
	 * To get parameter map validated in securityfilter
	 * @return - param map
	 */

	public HashMap<String, String[]> getFilteredParameterMap()
	{
		return this.filteredParameterMap;
	}

	public void setValidatedMultipartData(ArrayList<UploadedFileItem> validatedMultipartData)
	{
		this.validatedMultipartData = validatedMultipartData;
	}

	/**
	 * To get multipart data validated in securityfilter
	 * @return - UploadedFileItem : in which you'll get File object from which you can read validated multipart
	 */

	public ArrayList<UploadedFileItem> getValidatedMultipartData()
	{
		return this.validatedMultipartData;
	}

	public void setFilteredStreamContent(String data)
	{
		this.filteredStreamContent = data;
	}

	/**
	 * To get stream/body content validated in securityfilter
	 * @return - validated body/stream content
	 */

	public String getFilteredStreamContent()
	{
		return this.filteredStreamContent;
	}

	public void setSecurityRequestWrapper(SecurityRequestWrapper securityRequestWrapper)
	{
		this.securityRequestWrapper = securityRequestWrapper;
	}

	/**
	 * To check IAM User role match
	 * @param role
	 * @return - is IAM user role matches
	 */
	public boolean isUserInRole(String role)
	{
		try
		{
			if(securityRequestWrapper != null)
			{
				return securityRequestWrapper.isUserInRole(role);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception in isUserInRole : ",HttpRequest.class.getName(),AWSLogMethodConstants.IS_USER_IN_ROLE, ex);//No I18n
		}
		return false;
	}

	/**
	 * If ZohoSecurity filter enable and remote user is set in SecurityRequestWrapper, you can get remoteuser id. 
	 * @return - remote userid from ZOHO Security's SecurityRequestWrapper
	 */

	public String getRemoteUser()
	{
		try
		{
			if(securityRequestWrapper != null)
			{
				return securityRequestWrapper.getRemoteUser();
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception in getRemoteUser from sec wrapper : ",HttpRequest.class.getName(),AWSLogMethodConstants.GET_REMOTE_USER, ex);//No I18n
		}
		return null;
	}

	public SecurityRequestWrapper getSecurityRequestWrapper()
	{
		return securityRequestWrapper;
	}

	/**
	 * To get the string representation of the request
	 * @return - request string
	 */
	
	public String toString()
	{
		return ("["+reqUrl+"],["+reqType+"],["+ipaddr+"],["+Util.getAccessHeaders(headerValues)+"],["+Util.getAccessParams(paramValues)+"]");
	}
	
	public ReentrantLock getSyncLock()
	{       
		return syncLock;
	}
}
