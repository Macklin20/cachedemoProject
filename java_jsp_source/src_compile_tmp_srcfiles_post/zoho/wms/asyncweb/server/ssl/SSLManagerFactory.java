package com.zoho.wms.asyncweb.server.ssl;

import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Properties;

import com.zoho.wms.asyncweb.server.ConfManager;

public class SSLManagerFactory
{
        private static Logger logger = Logger.getLogger(SSLManagerFactory.class.getName());

	private static HashMap<String,SSLManager> sslManagerMap = new HashMap();

        public static SSLManager getSSLManager()
        {
		if(ConfManager.isConscryptContextEnabled())
		{
                	return ((SSLManager)sslManagerMap.get(SSLManagerTypes.CONSCRYPT));
		}
		return ((SSLManager)sslManagerMap.get(SSLManagerTypes.DEFAULT));
        }

	public static boolean initialize() throws Exception
        {
                Properties sslManagerProp = new Properties();
		sslManagerProp.put(SSLManagerTypes.DEFAULT,"com.zoho.wms.asyncweb.server.ssl.DefaultSSLManager");
                sslManagerProp.put(SSLManagerTypes.CONSCRYPT,"com.zoho.wms.asyncweb.server.ssl.ConscryptSSLManager");
		
		if(ConfManager.isConscryptContextEnabled())
                {
			for(Enumeration e=sslManagerProp.propertyNames();e.hasMoreElements();)
                	{
                        	String sslManagerName = (String) e.nextElement();
                        	String sslManagerClass = (String) sslManagerProp.get(sslManagerName);

                        	SSLManager sslManager = (SSLManager) Class.forName(sslManagerClass).newInstance();
                        	sslManager.initialize();
                        	sslManagerMap.put(sslManagerName,sslManager);
			}
                }
		else
		{
			SSLManager sslManager = (SSLManager) Class.forName((String) sslManagerProp.get(SSLManagerTypes.DEFAULT)).newInstance();
                        sslManager.initialize();
                        sslManagerMap.put(SSLManagerTypes.DEFAULT,sslManager);
		}

                logger.info("SSLManagerFactory :: sslManagerMap :: "+sslManagerMap);
		updateCipherAndProtocolMap();
		resetSSLSessionTimeout();
                return true;
        }

	public static void updateCipherAndProtocolMap()throws Exception
        {
                for(Object key : sslManagerMap.keySet())
                {
                        (sslManagerMap.get((String)key)).updateCipherAndProtocolMap();
                }
        }

	public static void resetSSLSessionTimeout()
        {
                for(Object key : sslManagerMap.keySet())
                {
                        (sslManagerMap.get((String)key)).resetSSLSessionTimeout();
                }
        }

}
