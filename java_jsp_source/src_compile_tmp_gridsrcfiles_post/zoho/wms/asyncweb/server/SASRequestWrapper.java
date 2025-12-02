//$Id$
package com.zoho.wms.asyncweb.server;

import com.adventnet.iam.security.APIRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.servlet.AsyncContext;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class SASRequestWrapper extends APIRequestWrapper
{
        private String uri = null;
        private String requestMethod = AWSConstants.GET_REQ;// NO I18N
        private Map<String, String[]> parameterMap = null;
        private Map<String, String> headerMap = null;
	//Header names case-insensitive as proceed by AWS team. RFC - https://www.rfc-editor.org/rfc/rfc7230#section-3.2
        private Map<String, String> parsedHeaderMap = null;
        private String remoteAddr = null;
        private String contextPath = "";
        private Cookie[] cookies = null;
        private Map<String, Object> attributesMap = null;
        private static final AsyncLogger LOG = new AsyncLogger("securitylogger");//No I18n
        private static String scheme;

        public SASRequestWrapper(String uri, String contextPath, Map<String, String[]> parameterMap, Map<String, String> headerMap, Map<String, String> parsedHeaderMap, String remoteAddr, String requestMethod, String scheme)
        {
                super(uri,contextPath,parameterMap);
                this.uri = uri;
                this.parameterMap = parameterMap;
                setHeaderMap(headerMap);
                //setCookie(this.headerMap.get(AWSConstants.HDR_COOKIE));
                setCookie((headerMap.get(AWSConstants.HDR_COOKIE) != null) ? headerMap.get(AWSConstants.HDR_COOKIE) : parsedHeaderMap.get(AWSConstants.COOKIE));
                setAttribute(AWSConstants.ZSEC_API_CONTEXT_PATH, contextPath);                     // NO I18N
		this.remoteAddr = remoteAddr;
		this.requestMethod = requestMethod;
                this.parsedHeaderMap = parsedHeaderMap;
                this.scheme = scheme;
	}

	private void setHeaderMap(Map<String, String> hdrMap)
	{
		this.headerMap = hdrMap;
	}

        private void setCookie(String cookie)
        {
                if(cookie == null)
                {
                            return;
                }

                String[] rawCookieParams = cookie.split(";");
                cookies = new Cookie[rawCookieParams.length];

		int index = 0;
                for (String rawCookieNameAndValue : rawCookieParams)
                {
			String key = null;
			String value = null;
                        try
                        {
                                String[] rawCookieNameAndValuePair = rawCookieNameAndValue.split("=");

				if(rawCookieNameAndValuePair.length == 2)
				{
                        	        key = rawCookieNameAndValuePair[0].trim();
                        	        value = rawCookieNameAndValuePair[1].trim();
				}
				else
				{
					key = rawCookieNameAndValuePair[0].trim();// Empty Cookie Value case, set Cookie key only.
				}
                        }
                        catch(Exception e)
                        {
				LOG.log(Level.INFO,"[SECURITY VALIDATION] Cookie value improper in SASRequestWrapper : ",SASRequestWrapper.class.getName(),AWSLogMethodConstants.SET_COOKIE,e);//No I18N
                        }
			cookies[index++] = new Cookie(key, value);
                }
        }

        public String getRequestURI()
        {
                return this.uri;
        }

        public long getDateHeader(String arg0)
        {
                return 0L;
        }

        public String getHeader(String key)
        {
                if (parsedHeaderMap != null)
                {
                        return parsedHeaderMap.get(key.toLowerCase());
                }
                return null;
        }
          
	 public Enumeration getHeaderNames() 
        {
                if (this.headerMap != null) 
                {
                        Set<String> paramNames = this.headerMap.keySet();
                        ArrayList<String> list = new ArrayList();
                        list.addAll(paramNames);
                        return Collections.enumeration(list);
                }
                return null;
        }

        public int getIntHeader(String arg0) 
        {
                return 0;
        }

        public String getParameter(String param) 
        {
                if (this.parameterMap != null) 
                {
                        String[] values = (String[]) this.parameterMap.get(param);
                        if (values != null) 
                        {
                                return values[0];
                        }
                }
                return null;
        }

        public Map getParameterMap() 
        {
                return this.parameterMap;
        }

        public Enumeration<String> getParameterNames() 
        {
                if (this.parameterMap != null) 
                {
                        Set<String> paramNames = this.parameterMap.keySet();
                        ArrayList<String> list = new ArrayList();
                        list.addAll(paramNames);
                        return Collections.enumeration(list);
                }
                return null;
        }

        public String[] getParameterValues(String param) 
        {
                if (this.parameterMap != null) 
                {
                        return (String[]) this.parameterMap.get(param);
                }
                return null;
        }

	@Override
        public String getMethod() 
        {
                if(ConfManager.isSupportedPostMethod(this.requestMethod))
		{
			return AWSConstants.POST_REQ;
		}
		else if(ConfManager.isSupportedGetMethod(this.requestMethod))
		{
			return AWSConstants.GET_REQ;
		}
                return this.requestMethod;
        }

	public Enumeration getHeaders(String headerName) 
	{
		if(this.headerMap != null && headerMap.containsKey(headerName))
		{
			List<String> headerValues = Arrays.asList(headerMap.get(headerName).split(","));
			return Collections.enumeration(headerValues);
		}
		return null;
	}

        public void setRemoteAddr(String addr) 
        {
                this.remoteAddr = addr;
        }

        public String getRemoteAddr() 
        {
                if (this.remoteAddr == null) 
                {
                        try 
                        {
                                this.remoteAddr = InetAddress.getAllByName(AWSConstants.LOCALHOST)[0].getHostAddress();  // NO I18N
                        }
                        catch (UnknownHostException e) 
                        {
                                LOG.log(Level.INFO, " Exception ",SASRequestWrapper.class.getName(),AWSLogMethodConstants.GET_REMOTE_ADDR, e);
                        }
                }
                return this.remoteAddr;
        }

        public String getContextPath() 
        {
                return this.contextPath;
        }

        public Object getAttribute(String name) 
        {
                if (this.attributesMap != null) 
                {
                        return this.attributesMap.get(name);
                }
                return null;
        }

	public Enumeration<String> getAttributeNames()  
        {
                if (this.attributesMap != null)         
                {
                        Set<String> attributeNames = this.attributesMap.keySet();
                        ArrayList<String> list = new ArrayList();
                        list.addAll(attributeNames);
                        return Collections.enumeration(list);
                }
                return null;
        }

        public void removeAttribute(String name) 
        {
                if (this.attributesMap != null)         
                {
                        this.attributesMap.remove(name);
                }
        }

        public void setAttribute(String name, Object value) 
        {
                if (this.attributesMap == null)         
                {
                        this.attributesMap = new HashMap();
                }
                this.attributesMap.put(name, value);
        }

        public String getCharacterEncoding() 
        {
		try
		{
			if(this.getHeader(AWSConstants.HDR_CONTENT_TYPE) != null && this.getHeader(AWSConstants.HDR_CONTENT_TYPE).startsWith(AWSConstants.MULTIPART))
			{
				String[] parameters = this.getHeader(AWSConstants.HDR_CONTENT_TYPE).split(";");
				for(int i=1; i<parameters.length; i++)
				{
					if(parameters[i].contains(AWSConstants.CHARSET))
					{
						String[] keyValue = parameters[i].split("=");
						if(keyValue.length ==2)
						{
							return keyValue[1].trim().replace("\"","");
						}
					}
				}
				return AWSConstants.DEFAULT_CHARSET;
			}
		}
		catch(Exception ex)
		{
			LOG.log(Level.SEVERE, "Exception in getCharacter Encoding for content-type : "+this.getHeader(AWSConstants.HDR_CONTENT_TYPE),SASRequestWrapper.class.getName(),AWSLogMethodConstants.GET_CHARACTER_ENCODING, ex);//No I18n
		}
                return null;
        }

        public String getLocalAddr() 
        {
                return null;
        }


        public String getLocalName() 
        {
                return null;
        }

        public int getLocalPort()       
        {
                return 0;
        }

        public Locale getLocale()       
        {
                return null;
        }

        public Enumeration getLocales() 
        {
                return null;
        }

        public String getProtocol() 
        {
                return null;
        }

        public BufferedReader getReader() throws IOException 
        {
                return null;
        }

        public String getRealPath(String arg0) 
        {
                return null;
        }

        public String getRemoteHost()   
        {
                return null;
        }

        public int getRemotePort()      
        {
                return 0;
        }

        public RequestDispatcher getRequestDispatcher(String arg0) 
        {
                return null;
        }        

	public String getScheme()       
        {
                return scheme;
        }

        public String getServerName() 
        {
		if(headerMap.containsKey("Host"))
		{
			return ((String) headerMap.get("Host")).replaceFirst(":.*$", "");//No I18n
		}
		if(parsedHeaderMap.containsKey(AWSConstants.HOST))
		{
			return ((String) parsedHeaderMap.get(AWSConstants.HOST)).replaceFirst(":.*$", "");//No I18n
		}
                return null;
        }

        public int getServerPort() 
        {
                return 0;
        }       

        public boolean isSecure() 
        {
                return false;
        }

        public String getAuthType() 
        {
                return null;
        }

        public Cookie[] getCookies() 
        {
                return cookies;
        }

        public String getPathInfo() 
        {
                return null;
        }

        public String getPathTranslated() 
        {
                return null;
        }
        

        public String getQueryString() 
        {
                return null;
        }


        public String getRemoteUser() 
        {
                return null;
        }

        public StringBuffer getRequestURL() 
        {
                StringBuffer url = new StringBuffer();
                if(parsedHeaderMap.containsKey(AWSConstants.HOST))
                {
                        url.append(scheme+"://");
                        url.append(parsedHeaderMap.get(AWSConstants.HOST));
                        url.append(uri);
                }
                else
                {
                        url.append(uri);
                }
                return url;
        }

        public String getRequestedSessionId() 
        {
                return null;
        }

        public String getServletPath()  
        {
                return uri;
        }

        public HttpSession getSession() 
        {
                return null;
        }

        public HttpSession getSession(boolean arg0) 
        {
                return null;
        }

        public Principal getUserPrincipal() 
        {
                return null;
        }

        public boolean isRequestedSessionIdFromCookie() 
        {
                return false;
        }

        public boolean isRequestedSessionIdFromURL() 
        {
                return false;
        }

        public boolean isRequestedSessionIdFromUrl() 
        {
                return false;
        }

        public boolean isRequestedSessionIdValid() 
        {
                return false;
        }        

	 public boolean isUserInRole(String arg0) 
        {
                return false;
        }

        @Override
        public boolean authenticate(HttpServletResponse hsr) throws IOException, ServletException 
        {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
        }

        @Override
        public void login(String string, String string1) throws ServletException 
        {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
        }

        @Override
        public void logout() throws ServletException 
        {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
        }

        @Override
        public Collection<Part> getParts() throws IOException, IllegalStateException, ServletException 
        {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
        }

        @Override
        public Part getPart(String string) throws IOException, IllegalStateException, ServletException 
        {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
        }

        @Override
        public ServletContext getServletContext() 
        {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
        }

        @Override
        public AsyncContext startAsync() 
        {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
    }

    @Override
    public AsyncContext startAsync(ServletRequest sr, ServletResponse sr1) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
    }

    @Override
    public boolean isAsyncStarted() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
    }

    @Override
    public boolean isAsyncSupported() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.//NO I18N
    }
}
