//$Id$
package com.zoho.wms.asyncweb.server.util;

// Java import
import java.util.logging.Level;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Properties;

// iam import
import com.adventnet.iam.UserAPI;
import com.adventnet.iam.AuthAPI;
import com.adventnet.iam.GroupAPI;
import com.adventnet.iam.Ticket;
import com.adventnet.iam.IAMProxy;
import com.adventnet.iam.User;
import com.adventnet.iam.IAMUtil;
import com.adventnet.iam.OAuthAPI;
import com.adventnet.iam.OAuthToken;
import com.adventnet.iam.OAuthTokenManager.OAuthScopeOperation;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.accounts.AccountsUtil;

// Wms import
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.stats.MIAdapter;
import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.common.exception.WMSException;


public class CommonIamUtil
{
	public static AsyncLogger logger = new AsyncLogger(CommonIamUtil.class.getName());
        private static final Properties iamservices = ConfManager.getIamServiceProperties();

	static
	{
		setIamProxy(ConfManager.getIamServerURL());
	}

	private static final AsyncLogger AUTHLOGGER = new AsyncLogger("authchecklog");//No I18n
	private static long authCheckCount = 0;
	private static long authDelayCount = 0;
	private static final String DEFAULT_IAM_COOKIE = "IAMAGENTTICKET";
	public static final String OAUTH_PREFIX = "Zoho-oauthtoken ";//No I18N
	private static ZUIDCache zuidmapping = new ZUIDCache(5000);

	public static Hashtable<String, String> validateISC(String ticket, String iscscope, String ip, String useragent, String prd)
	{
                String iamservicename = iamservices.getProperty(prd, ConfManager.getIAMSecurityServiceName());
		Hashtable<String, String> userinfo = new Hashtable();
		long starttime=System.currentTimeMillis();
		userinfo.put(AWSConstants.AUTH,AWSConstants.FALSE);
		if(ticket==null)
		{
			return userinfo;
		}

                String zuid=null;
		try
		{
			authCheckCount++;
			AuthAPI aapi = getAuthApi();
			Ticket userticket = aapi.validateISC(ticket, -1l, iamservicename, iscscope, false, ip);
			zuid = ""+userticket.getZUID();
			boolean auth = true;
			userinfo.put(AWSConstants.AUTH,""+auth);
			userinfo.put(AWSConstants.NNAME, getUserDisplayName(userticket));
			userinfo.put(AWSConstants.ZUID, zuid); 

                        AUTHLOGGER.log(Level.INFO,"AUTHTOKENCHECK "+zuid+" ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+" ***** ip : "+ip+" iamservice: "+iamservicename,CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_ISC);//No I18N
		}catch(Exception exp)
		{
			AUTHLOGGER.log(Level.INFO,"AUTHTOKENERROREXP  ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+"  ip : "+ip+" iamservice: "+iamservicename+" : ",CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_ISC, exp);//No I18N
		}finally
		{
			if((System.currentTimeMillis()-starttime)>1000)
			{
				authDelayCount++;
				AUTHLOGGER.log(Level.INFO,"AUTHTOKENSTAT-DELAY "+zuid+" "+(System.currentTimeMillis()-starttime)+" ["+authDelayCount+"/"+authCheckCount+"]",CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_ISC);//No I18N
			}
		}

		return userinfo;	

	}


	public static Hashtable<String, String> validateTokenPairTicket(String clientToken, String mdmToken, String servicedomain, String ip, String useragent, String prd, String zaid) 
	{
		String iamservicename = iamservices.getProperty(prd, ConfManager.getIAMSecurityServiceName());
		Hashtable<String, String> userinfo = new Hashtable<String, String>();
		long starttime = System.currentTimeMillis();
		userinfo.put(AWSConstants.AUTH, AWSConstants.FALSE);
		if(clientToken == null)
		{
			return userinfo;
		}

		String zuid = null;
		String zoid = null;
		try
		{
			authCheckCount++;
			AuthAPI aapi = getAuthApi();
			Ticket user = aapi.validateClientTokenAndGetTicket(clientToken, iamservicename, servicedomain, ip, mdmToken, zaid);
			zuid = ""+user.getZUID();
			zoid = ""+user.getZOID();
			boolean auth = true;
			userinfo.put(AWSConstants.AUTH, auth+"");
			userinfo.put(AWSConstants.NNAME, getUserDisplayName(user));
			userinfo.put(AWSConstants.ZUID, zuid); 
			userinfo.put(AWSConstants.ORGID, zoid);
			if(user.getZaid() != null){
				userinfo.put(AWSConstants.ZAID, user.getZaid());
			}
			if(!CommonUtil.isEmpty(user.getPrimaryEmail()))
			{
				userinfo.put(AWSConstants.EMAIL, user.getPrimaryEmail());
			}
			AUTHLOGGER.log(Level.INFO,"TokenPairTicketCHECK "+zuid+" ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+" ***** ip : "+ip+" iamservice: "+iamservicename+" domain "+ servicedomain + " zaid "+zaid,CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_TOKEN_PAIR_TICKET);
		}
		catch(Exception exp)
		{
			AUTHLOGGER.log(Level.INFO,"TokenPairTicketERROREXP "+prd+" "+servicedomain+" ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+"  ip : "+ip+" iamservice: "+iamservicename+" : "+useragent+" zaid "+zaid,CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_TOKEN_PAIR_TICKET, exp);
		}
		finally
		{
			if((System.currentTimeMillis()-starttime)>1000)
			{
				authDelayCount++;
				AUTHLOGGER.log(Level.INFO,"TokenPairTicketSTAT-DELAY "+zuid+" "+(System.currentTimeMillis()-starttime)+" ["+authDelayCount+"/"+authCheckCount+"]",CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_TOKEN_PAIR_TICKET);
			}
		}
		return userinfo;	
	}

	public static Hashtable validateUser(String ticket, String serverdomain, String ip, String useragent, String prd)
	{
		return validateUser(ticket, serverdomain, ip, useragent, prd, false, null, null);
	}

	public static Hashtable validateUser(String ticket, String serverdomain, String ip, String useragent, String prd, String mdmToken)
	{
		return validateUser(ticket, serverdomain, ip, useragent, prd, false, null, mdmToken);
	}

	public static Hashtable validateUser( String ticket, String serverdomain, String ip, String useragent, String prd, boolean isClientUser, String zaid, String mdmToken)
	{
                String iamservicename = iamservices.getProperty(prd, ConfManager.getIAMSecurityServiceName());
		Hashtable userinfo = new Hashtable();
		long starttime=System.currentTimeMillis();
		userinfo.put(AWSConstants.AUTH,AWSConstants.FALSE);
		if(ticket==null)
		{
			return userinfo;
		}
		String zuid = null;
		try
		{
			authCheckCount++;
			AuthAPI aapi = getAuthApi();
			Ticket userticket = aapi.validate(ticket, ConfManager.getIAMSecurityServiceName(), serverdomain, ip, useragent, isClientUser, zaid, mdmToken);
			zuid = ""+userticket.getZUID();
			boolean auth = true;
			userinfo.put(AWSConstants.AUTH,""+auth);
			userinfo.put(AWSConstants.NNAME, getUserDisplayName(userticket));
			userinfo.put(AWSConstants.ZUID, zuid); 
			if(auth)
			{
				AUTHLOGGER.log(Level.INFO,"AUTHCHECK "+zuid+" ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+"  ip : "+ip,CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_USER);//No I18N
			}else
			{
				AUTHLOGGER.log(Level.INFO,"AUTHCHECKFAILED  ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+"  ip : "+ip,CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_USER);//No I18N
			}
		}catch(Exception exp)
		{
			AUTHLOGGER.log(Level.INFO,"AUTHERROREXP "+zuid+" ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+"  ip : "+ip+" : ",CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_USER,exp);//No I18N
		}finally
		{
			if((System.currentTimeMillis()-starttime)>1000)
			{
				authDelayCount++;
				AUTHLOGGER.log(Level.INFO,"AUTHSTAT-DELAY "+zuid+" "+(System.currentTimeMillis()-starttime)+" ["+authDelayCount+"/"+authCheckCount+"]",CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_USER);//No I18N
			}
		}

		return userinfo;	
	}

	public static Hashtable<String, String> validateOAuth(String oauthtoken, String mdmToken, String prd, String sid, String ipaddr, String useragent)	
	{

		String iamservicename = iamservices.getProperty(prd, ConfManager.getIAMSecurityServiceName());
		Hashtable<String, String> userinfo = new Hashtable();
		long starttime=System.currentTimeMillis();
		userinfo.put(AWSConstants.AUTH,AWSConstants.FALSE);
		if(oauthtoken == null)
		{
			return userinfo;
		}
		
		String zuid = null;
		String zoid = null;
		try
		{
			authCheckCount++;
			OAuthToken token = AccountsUtil.validateToken(null, oauthtoken, mdmToken, false, ipaddr);
			User user = token.getUser();
			zuid = ""+user.getZUID();
			zoid = ""+user.getZOID();
			boolean auth = true;
			userinfo.put(AWSConstants.AUTH,""+auth);
			userinfo.put(AWSConstants.ZUID,zuid);
			userinfo.put(AWSConstants.ORGID,zoid);
			userinfo.put(AWSConstants.NNAME,user.getDisplayName());
			if(!CommonUtil.isEmpty(user.getPrimaryEmail()))
			{
				userinfo.put(AWSConstants.EMAIL,user.getPrimaryEmail());
			}
			AUTHLOGGER.log(Level.INFO,"OAUTHTOKENCHECK "+zuid+" "+sid+" ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+" ***** ip : "+ipaddr+"iamservice: "+iamservicename,CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_OAUTH);
		}
		catch(Exception ex)
		{
			AUTHLOGGER.log(Level.INFO,"OAUTHTOKENERROREXP "+sid+" ("+authCheckCount+") "+(System.currentTimeMillis()-starttime)+" :ip : "+ipaddr+" iamservice: "+iamservicename+" : "+useragent+" ",CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_OAUTH,ex);
		}
		finally
		{
			if((System.currentTimeMillis()-starttime)>1000)
			{
				authDelayCount++;
				AUTHLOGGER.log(Level.INFO,"OAUTHTOKENSTAT-DELAY "+zuid+" "+(System.currentTimeMillis()-starttime)+" ["+authDelayCount+"/"+authCheckCount+"]",CommonIamUtil.class.getName(),AWSLogMethodConstants.VALIDATE_OAUTH);
			}			
		}
		return userinfo;
	}

	public static boolean isMemberOfGroup(String zuid,String groupId)
	{
		try
		{
			if(zuid == null || groupId == null)
			{
				return false;
			}
			long userid = Long.parseLong(zuid);
			long grpid = Long.parseLong(groupId);
			GroupAPI gapi = getGroupAPI();
			return gapi.isGroupMember(grpid,userid);
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ",CommonIamUtil.class.getName(),AWSLogMethodConstants.IS_MEMBER_OF_GROUP, ex);
		}
		return false;
	}

	public static String getUserDisplayName(User cuser)
        {
                if(!CommonUtil.isEmpty(cuser.getDisplayName()))
                {
                        return cuser.getDisplayName();
                }
                if(!CommonUtil.isEmpty(cuser.getFullName()))
                {
                        return cuser.getFullName();
                }
                if(!CommonUtil.isEmpty(cuser.getPrimaryEmail()))
                {
                        return cuser.getPrimaryEmail();
                }
                return "";
        }

	private static AuthAPI getAuthApi() 
	{
		IAMProxy proxy = IAMProxy.getInstance();
		AuthAPI authapi = proxy.getAuthAPI();
		return authapi;
	}

	public static GroupAPI getGroupAPI() 
	{
		IAMProxy proxy = IAMProxy.getInstance();
		GroupAPI uapi = proxy.getGroupAPI();
		return uapi;
	}

	public static UserAPI getUserAPI() 
	{
		IAMProxy proxy = IAMProxy.getInstance();
		UserAPI uapi = proxy.getUserAPI();
		return uapi;
	}

	public static boolean isMemberOfOrg(long zuid,long zoid)
	{
		try
		{
			if(zoid == -1)
			{
				return false;
			}
			if(getUserAPI().getZOID(zuid) == zoid)
			{
				return true;
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ",CommonIamUtil.class.getName(),AWSLogMethodConstants.IS_MEMBER_OF_ORG, ex);
		}
		return false;
	}

	public static boolean setIamProxy(String url)
	{
		if(url!=null)
		{
			IAMProxy.setIAMInternalServerURL(url);
		}
		return true;
	}

	public static String getIamTicketName()
	{
		return DEFAULT_IAM_COOKIE;// Since this(IAMProxy.getIAMCookieName()) api is deprecated since ZOHOACCOUNTS_M4011, we are returning default value
	}

        public static String getZUID(String uname) throws WMSException
        {
                try
                {
                        Long zuid = new Long(uname);
                        return uname;
                }catch(NumberFormatException nexp)
                {
                        try
                        {
                                if(zuidmapping.get(uname)!=null)
                                {
                                        return ""+zuidmapping.get(uname);
                                }
                                UserAPI userAPI = getUserAPI();
                                User userinfo = userAPI.getUser(uname);
                                fillCache(uname,""+userinfo.getZUID());
                                return ""+userinfo.getZUID();
                        }catch(Exception exp)
                        {
                                throw new WMSException("Unable to getZUID for uname : ["+uname+"]");
                        }
                }catch(Exception e)
                {
                        throw new WMSException("Unable to getZUID for uname : ["+uname+"]");
                }

        }

        private static void fillCache(String uname, String zuid)
        {
                zuidmapping.put(""+uname,""+zuid);
        }

        static class ZUIDCache extends LinkedHashMap
        {
                private int maxsize;
                public ZUIDCache(int capacity)
                {
                        super(capacity,0.75f,true);
                        maxsize = capacity;
                }
                protected boolean removeEldestEntry(Map.Entry eldest)
                {
                        return size() > maxsize;
                }
        }

        public static String escapeHTML(String str)
        {
                return IAMUtil.htmlEscape(str);
        }

	
	public static List<String> getIAMCookieNames()
	{
		try
		{
			return IAMUtil.getIAMCookies();
		}
		catch(Exception e)
		{
			return new ArrayList();
		}
	}

	public static String getLoginZUIDIfExists()
        {       
                try
                {       
                        return ""+(IAMUtil.getCurrentUser().getZUID());
                }catch(Exception exp)
                {
                }
                return null;
        }

	public static String getStatelessUserdata()
	{
		try
		{
			return IAMUtil.getStatelessUserdata();
		}
		catch(Exception ex)
		{
		}
		return null;
	}
}
