//$Id$

package com.zoho.wms.asyncweb.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;
import java.util.Properties;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.util.CommonIamUtil;
import com.zoho.wms.asyncweb.server.SASRequestWrapper;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;
//IAM Import
import com.adventnet.iam.security.APIRequestWrapper;
import com.adventnet.iam.security.APIResponseWrapper;
import com.adventnet.iam.security.SecurityRequestWrapper;
import com.adventnet.iam.security.SecurityResponseWrapper;
import com.adventnet.iam.security.UploadedFileItem;
import com.adventnet.iam.security.IAMSecurityException;
import com.adventnet.iam.security.SecurityUtil;
import com.adventnet.iam.IAMUtil;
import com.adventnet.iam.security.SecurityFilterProperties;
import com.adventnet.iam.security.Authenticator;
import com.adventnet.iam.security.ActionRule;

import com.adventnet.wms.common.HttpDataWraper;

public class AWSSecurityFilter
{
	private static AsyncLogger logger = new AsyncLogger("securitylogger");//No I18n
	private static boolean initialized = false;
	private static long inittime;
	
	public static void initialize()
	{
		try
		{
			if(initialized)
			{
				logger.log(Level.INFO,"Security filter already initialized",AWSSecurityFilter.class.getName(),AWSLogMethodConstants.INITIALIZE);
				return;
			}
			
			for(String engineName: WebEngine.getAllEngineName())
			{
				if(SecurityFilterProperties.FILTER_INSTANCES != null)
				{
					SecurityFilterProperties.FILTER_INSTANCES.remove(engineName);
				}

				AbstractWebEngine engine = WebEngine.getEngineByAppName(engineName);

				if(engine!=null && engine.isSecurityFilterEnabled())
				{
					initSecurityFilter(engineName);
				}
			}

			inittime = System.currentTimeMillis();
			initialized = true;
		}
		catch(Exception e)
		{
			logger.log(Level.INFO,"Unable to initialize SECURITY_CONTEXT : ",AWSSecurityFilter.class.getName(),AWSLogMethodConstants.INITIALIZE,e);
		}
	}

	public static void initSecurityFilter(String enginename)
	{
		SecurityFilterProperties filterConfig = SecurityFilterProperties.FILTER_INSTANCES.get(enginename);

		if(filterConfig!=null)
		{
			logger.log(Level.INFO,"Security context already initialized ::::: "+enginename,AWSSecurityFilter.class.getName(),AWSLogMethodConstants.INIT_SECURITY_FILTER);
			return;
		}
		
		String[] security_files = new String[]
				{
				getSecurityFile("security-"+enginename+".xml"), // No I18N
				getSecurityFile("security-properties.xml"), // No I18N
				getSecurityFile("security-common.xml"), // No I18N
				getSecurityFile("security-regex.xml")//No I18N
				};
		initSecurityFilter(security_files, enginename);
	}

	private static void initSecurityFilter(String[] securityFiles, String contextName)
	{
		try
		{
			SecurityUtil.initSecurityConfiguration(securityFiles, contextName);
			logger.log(Level.INFO,"Security context initialized ::::: "+contextName,AWSSecurityFilter.class.getName(),AWSLogMethodConstants.INIT_SECURITY_FILTER);
		}
		catch (Exception e)
		{
			logger.log(Level.INFO,"Unable to initSecurityFilter context for engine "+contextName+" : ",AWSSecurityFilter.class.getName(),AWSLogMethodConstants.INIT_SECURITY_FILTER,e);
		}
	}

	private static String getSecurityFile(String filename)
	{
		return ConfManager.getSecurityFile(filename);
	}

	public static boolean verifyRequest(HttpRequest request, String securitycontextpath) throws Exception
	{
		return verifyRequest(request, null, securitycontextpath);
	}

	public static boolean verifyRequest(HttpRequest request, HttpResponse response, String securitycontextpath) throws Exception
	{
		Map<String,String[]> parameterMap = null;
		String requestUrl = null;
		try
		{
			parameterMap = new HashMap<String,String[]>();
			requestUrl = request.getRequestURL();
		
			if(ConfManager.isSecurityExcludedURL(requestUrl))
			{
				return true;
			}	

			if(!requestUrl.startsWith("/"))
			{
				requestUrl = "/"+requestUrl;
			}

			Map<String,String> reqParams = request.getParams();
			for (Object key : reqParams.keySet())
			{
				String[] value = new String[] { "" + reqParams.get(key) };
				parameterMap.put("" + key, value);
			}
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "Exception --> [request:"+request+"] --> ",AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, e);
			throw new AWSException("Invalid Parameter");
		}

		try
		{
		//	SASRequestWrapper securityrequest = new SASRequestWrapper(requestUrl, securitycontextpath, parameterMap, request.getOriginalHeaders(), request.getRemoteAddr(), request.getRequestType());
			SASRequestWrapper securityrequest;
			if(!request.getRequestType().equalsIgnoreCase(AWSConstants.GET_REQ) && !request.getRequestType().equalsIgnoreCase(AWSConstants.POST_REQ))
			{
				if(ConfManager.isSupportedPostMethod(request.getRequestType()))
				{
					securityrequest = new SASRequestWrapper(requestUrl, securitycontextpath, parameterMap, request.getOriginalHeaders(), request.getHeaders(), request.getRemoteAddr(), AWSConstants.POST_REQ, request.getScheme());
				}
				else if(ConfManager.isSupportedGetMethod(request.getRequestType()))
				{
					securityrequest = new SASRequestWrapper(requestUrl, securitycontextpath, parameterMap, request.getOriginalHeaders(), request.getHeaders(), request.getRemoteAddr(), AWSConstants.GET_REQ, request.getScheme());
				}
				else
				{
					logger.log(Level.INFO,"AWS Security Filter --> Unsupported Request Method :: {0}",AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, new Object[]{request.getRequestType()});//No I18n
					throw new AWSException("Unsupported Request Method :: "+request.getRequestType());//No I18n
				}
			}
			else
			{
				securityrequest = new SASRequestWrapper(requestUrl, securitycontextpath, parameterMap, request.getOriginalHeaders(), request.getHeaders(), request.getRemoteAddr(), request.getRequestType(), request.getScheme());
			}

			try
			{
				try
				{
					if(request.getRequestType().equals(AWSConstants.POST_REQ) && !isStreamMode(request) && request.getHeader(AWSConstants.CONTENT_LENGTH) != null && request.getBody() != null && (Integer.parseInt(request.getHeader(AWSConstants.CONTENT_LENGTH)) == request.getBody().length) && (request.getHeader(AWSConstants.CONTENT_TYPE) != null && !request.getHeader(AWSConstants.CONTENT_TYPE).startsWith(AWSConstants.APPLICATION_X_WWW_URLENCODED)))
					{
						securityrequest.setInputStream(request.getBody());
					}
				}
				catch(Exception streamexp)
				{
					logger.addExceptionLog(Level.SEVERE, "Exception in setting input stream : "+request,AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, streamexp);//No I18n
				}

				if(response == null)
				{
					SecurityUtil.validateRequest(securityrequest);
				}
				else
				{
					SecurityUtil.validateRequest(securityrequest, new SASResponseWrapper(response));
				}
				SecurityRequestWrapper srequest = (SecurityRequestWrapper) securityrequest.getAttribute(SecurityRequestWrapper.class.getName());
				request.setSecurityRequestWrapper(srequest);
				try
				{
					HashMap<String, String[]> filteredParameterMap = (HashMap) srequest.getParameterMap();
					if(filteredParameterMap != null && !filteredParameterMap.isEmpty())
					{
						request.setFilteredParameterMap(filteredParameterMap);
					}
					if(srequest.getAttribute(SecurityUtil.MULTIPART_FORM_REQUEST) != null)
					{
						ArrayList<UploadedFileItem> filesList = (ArrayList<UploadedFileItem>) srequest.getAttribute(SecurityUtil.MULTIPART_FORM_REQUEST); 
						request.setValidatedMultipartData(filesList);
 
					}
					request.setFilteredStreamContent(srequest.getFilteredStreamContent());
				}
				catch(Exception filteredParamExp)
				{
					logger.addExceptionLog(Level.SEVERE, "Exception in get filtered param map : "+request.getRequestURL()+", filtered param map : "+srequest.getParameterMap(),AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, filteredParamExp);
				}
				if(AWSConstants.REQUIRED.equalsIgnoreCase(srequest.getURLActionRule().getAuthentication()))
				{
					boolean valid = authenticateUser(srequest,securitycontextpath);
					if (valid)
					{
						logger.addDebugLog(Level.FINE, AWSLogConstants.AWS_SECURITY_FILTER_SUCCESS, AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST,new Object[]{request});
					}        
					else
					{
						logger.log(Level.INFO,"AWS Security Filter --> USERAUTHENTICATION.FAILURE : {0}",AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, new Object[]{request});                                     // No I18N
						return false;
					}
				}
				else if(AWSConstants.OPTIONAL.equalsIgnoreCase(srequest.getURLActionRule().getAuthentication()))
				{
					boolean valid = authenticateUser(srequest,securitycontextpath);
					if(!valid)
					{
						logger.log(Level.INFO,"AWS Security Filter --> USERAUTHENTICATION OPTIONAL : {0} : {1}",AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, new Object[]{valid, request});// No I18N
					}
					return true;
				}
			}
			finally
			{
				try
				{
					if(CommonIamUtil.getLoginZUIDIfExists() != null)
					{
						request.setZUID(Long.parseLong(CommonIamUtil.getLoginZUIDIfExists()));
					}
				}
				catch(Exception iamexp)
				{
				}
			}
		}
		catch (NullPointerException npe)
		{
			logger.addExceptionLog(Level.INFO, "AWS Security Filter --> NPE securitycontextpath: " + securitycontextpath + " inittime: " + inittime+" request : "+request,AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, npe);
			throw npe;
		}
		catch(IAMSecurityException exp)
		{
			if (IAMSecurityException.XSS_DETECTED.equals(exp.getErrorCode()))
			{
				logger.addExceptionLog(Level.INFO, "AWS Security Filter --> Error in verifyRequest. XSS Detected in requestUrl: "+requestUrl+" securitycontextpath: " + securitycontextpath +" request : "+request,AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, exp);
				throw exp;
			}
			else if (IAMSecurityException.JSON_PARSE_ERROR.equals(exp.getErrorCode()))
			{
				logger.addExceptionLog(Level.INFO, "AWS Security Filter --> JSON parse error - parammap: " + parameterMap+" requestUrl: "+requestUrl+" securitycontextpath: " + securitycontextpath +" request : "+request, AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST,exp);
				throw exp;
			}
			else
			{
				if (IAMSecurityException.REMOTE_IP_LOCKED.equals(exp.getErrorCode()))
				{
					logger.addExceptionLog(Level.INFO, "AWS Security Filter --> Error in verifyRequest. REMOTE_IP_LOCKED. RemoteIP: "+request.getRemoteAddr()+" requestUrl: "+requestUrl+" securitycontextpath: " + securitycontextpath +" request : "+request,AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, exp);
				}
				else
				{
					logger.addExceptionLog(Level.INFO, "AWS Security Filter --> Error in verifyRequest. RemoteIP: "+request.getRemoteAddr()+" requestUrl: "+requestUrl+" securitycontextpath: " + securitycontextpath +" request : "+request,AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST, exp);
				}
				throw exp;
			}
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.INFO, "AWS Security Filter --> Error in verifyRequest. RemoteIP: "+request.getRemoteAddr()+" requestUrl: "+requestUrl+" securitycontextpath: " + securitycontextpath +" request : "+request, AWSSecurityFilter.class.getName(),AWSLogMethodConstants.VERIFY_REQUEST,ex);
			throw ex;
		}

		return true;
	}

	private static boolean authenticateUser(SecurityRequestWrapper srequest,  String securitycontextpath) throws Exception 
	{
		Authenticator authenticator = SecurityFilterProperties.getInstance(securitycontextpath).getAuthenticationProvider();
                return authenticator.authenticate(srequest, new SecurityResponseWrapper(new APIResponseWrapper()));	
	}
	
	public static void cleanUp()
	{
		IAMUtil.cleanupThreadCredentials();
		SecurityUtil.cleanUpThreadLocals();
	}

	static void resetInit()
	{
		initialized = false;
	}

	private static boolean isStreamMode(HttpRequest request)
	{
		return Util.isStreamMode(request.getHeader(ConfManager.getStreamModeHeader()));
	}

}
