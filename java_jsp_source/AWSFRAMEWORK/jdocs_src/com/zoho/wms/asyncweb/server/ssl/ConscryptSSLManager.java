//$Id$
package com.zoho.wms.asyncweb.server.ssl;

// Java import
import java.io.*;
import java.security.*;
import javax.net.ssl.*;
import java.util.Hashtable;
import java.util.Set;
import java.util.Properties;
import java.util.Enumeration;
import java.util.logging.Level;

import org.conscrypt.*;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.X509DynamicTrustManager;
import com.zoho.wms.asyncweb.server.ConfManager;

public class ConscryptSSLManager extends SSLManager
{
        private Hashtable<String,OpenSSLContextImpl[]> sslCntxMap = new Hashtable();
	private Hashtable<String,String[]> sslCipherMap = new Hashtable();
        private Hashtable<String,String[]> sslProtocolMap = new Hashtable();

	private static AsyncLogger logger = new AsyncLogger(ConscryptSSLManager.class.getName());

        public void initialize() throws Exception
        {
                logger.info("Initializing  ConscryptSSL",ConscryptSSLManager.class.getName(),AWSLogMethodConstants.INITIALIZE);
                sslsessiontimeout = ConfManager.getSSLSessionTimeout();
                sslCntxMap.clear();

                Properties sslserverprops = ConfManager.getSSLConf();
                Enumeration en = sslserverprops.propertyNames();

                while(en.hasMoreElements())
                {
                        String srvname = (String)en.nextElement();
                        String val = sslserverprops.getProperty(srvname);

                        String[] valarr = val.split(",");
                        int port = Integer.parseInt(valarr[0]);
                        String domain = valarr[1];
                        int startuptype = Integer.parseInt(valarr[2]);

                        if(startuptype != SSLStartUpTypes.DEFAULT)      
                        {
                                continue;
                        }       

                        Properties sslproperties = Util.getProperties(ConfManager.getSSLPropFile(srvname));

			KeyManagerFactory kf = SSLUtil.getKeyManagerFactory(sslproperties,srvname);
			if(kf!=null)
			{
                        	X509DynamicTrustManager tm = null;
                        	if(!ConfManager.isUseDefaultTrustManager())
                        	{
                                	tm = new X509DynamicTrustManager(""+port);
                                	tmstore.put(""+port,tm);
                        	}
                        	//tm.add(domain, ConfManager.getSSLCertFilePath(keyStore), passArray, keyStoreType);

                        	OpenSSLContextImpl[] contextarr = new OpenSSLContextImpl[ConfManager.getSSLContextSize()];

                        	for(int i=0;i<contextarr.length;i++)
                        	{
                                	OpenSSLContextImpl sslCntx = (OpenSSLContextImpl) Conscrypt.newPreferredSSLContextSpi();
                                	if(!ConfManager.isUseDefaultTrustManager())
                                	{
                                        	sslCntx.engineInit(kf.getKeyManagers(), new TrustManager[]{tm}, null);
                                	}
                                	else
                                	{
                                        	sslCntx.engineInit(kf.getKeyManagers(), null, null);
                                	}
                                	sslCntx.engineGetServerSessionContext().setSessionTimeout(sslsessiontimeout);  
                                	contextarr[i] = sslCntx;
                        	}
                        	sslCntxMap.put(""+port,contextarr);
                        	if(contextarr.length > 1)
                        	{
                                	sslContextCounterMap.put(""+port,0);
                        	}
                        	logger.log(Level.INFO,"ConscryptContext successfully created For [Domain] "+domain+" [SERVER] "+srvname+" [PORT] "+port,ConscryptSSLManager.class.getName(),AWSLogMethodConstants.INITIALIZE);
			}
			else
			{
				throw new Exception("keyStore or keyPass is null --> Unable to init SSL for the servlet " + srvname);
			}
		}
	}

	public SSLEngine getSSLEngine(int port , String ipaddr , int sockport)
        {
            SSLEngine sslEngine=getSSLContext((ipaddr),port).engineCreateSSLEngine(ipaddr,sockport);
            if(ConfManager.isTLSALPNEnabled())//should be enabled after http/2.0
            {
                Conscrypt.setApplicationProtocols(sslEngine,ConfManager.getTLSALPNProtocolList());
            }
                if(ConfManager.isConscryptTicketResumptionEnabled())
                {
                        Conscrypt.setUseSessionTickets(sslEngine,true);
                }
		sslEngine.setEnabledProtocols(SSLUtil.getSSLProtocolOrder(sslEngine));
		sslEngine.setEnabledCipherSuites(SSLUtil.getSSLCipherSuiteOrder(sslEngine));
                return sslEngine;
        }

	private OpenSSLContextImpl getSSLContext(String address, int port)
        {
                OpenSSLContextImpl[] contextarr = (OpenSSLContextImpl[])sslCntxMap.get(""+port);
                if(contextarr.length == 1)
                {
                        return contextarr[0];
                }

                String sessionCntx = address+":"+port;                                                                                                                 
                if(sslSessionCntxMap.get(sessionCntx) != null)
                {
                        Integer cntxID = sslSessionCntxMap.get(sessionCntx);
                        return contextarr[cntxID.intValue()];
                }

                Integer contextcounter = sslContextCounterMap.get(""+port);
                int i = 0;
                int LIMIT = 3;
                while(i++ < LIMIT)
                {
                        try
                        {
                                contextcounter++;
                                contextcounter = contextcounter % contextarr.length;
                                sslSessionCntxMap.put(sessionCntx,contextcounter);
                                sslContextCounterMap.put(""+port,contextcounter);
                                return contextarr[contextcounter];
                        }
                        catch(ArrayIndexOutOfBoundsException ex)
                        {
                                continue;
                        }
                }
                sslSessionCntxMap.put(sessionCntx,0);
                sslContextCounterMap.put(""+port,0);
                return contextarr[0];
        }

	public String[] getSSLProtocol(int port) 
        {
                return sslProtocolMap.get(""+port);
        }
        
        public String[] getSSLCipherSuite(int port) 
        {
                return sslCipherMap.get(""+port);
        }

	public void updateCipherAndProtocolMap() throws Exception
        {
                Set<String> keys = sslCntxMap.keySet();
                for(String key: keys)
                {
                        int port = Integer.parseInt(key);
                        OpenSSLContextImpl[] contextarr = sslCntxMap.get(key);
                        updateSSLProtocolMap(port,contextarr[0]);
                        updateSSLCipherSuiteMap(port,contextarr[0]);
                }
        }

        private void updateSSLProtocolMap(int port, OpenSSLContextImpl sslcntx)
        {
                sslProtocolMap.put(""+port,SSLUtil.getSSLProtocolOrder(sslcntx.engineCreateSSLEngine()));
        }

        private void updateSSLCipherSuiteMap(int port, OpenSSLContextImpl sslcntx)
        {
                sslCipherMap.put(""+port,SSLUtil.getSSLCipherSuiteOrder(sslcntx.engineCreateSSLEngine()));
        }

	public void resetSSLSessionTimeout()
        {
                if(ConfManager.getSSLSessionTimeout() == sslsessiontimeout)
                {
                        return;
                }
                else
                {
                        sslsessiontimeout = ConfManager.getSSLSessionTimeout();
                }
                Enumeration en = sslCntxMap.keys();
                while(en.hasMoreElements())
                {
                        String key = (String)en.nextElement();
                        OpenSSLContextImpl[] contextarr = sslCntxMap.get(key);
                        for(int i=0;i<contextarr.length;i++)
                        {
                                contextarr[i].engineGetServerSessionContext().setSessionTimeout(sslsessiontimeout);
                        }
                }
                logger.log(Level.INFO,"SSL SESSION TIMEOUT SET TO "+sslsessiontimeout,ConscryptSSLManager.class.getName(),AWSLogMethodConstants.RESET_SSL_SESSION_TIMEOUT);
        }

}
