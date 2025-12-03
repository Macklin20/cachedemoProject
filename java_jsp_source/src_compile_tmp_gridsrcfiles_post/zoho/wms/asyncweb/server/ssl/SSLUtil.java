package com.zoho.wms.asyncweb.server.ssl;

import java.io.FileInputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Iterator;
import java.security.KeyStore;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.KeyManagerFactory;
import java.util.logging.Level;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.ConfManager;

public class SSLUtil
{
	private static AsyncLogger logger = new AsyncLogger(SSLUtil.class.getName());

        public static String[] getSSLProtocolOrder(SSLEngine sslEngine)
        {
		String[] supportedProtocols = sslEngine.getSupportedProtocols();
                ArrayList tlslist = new ArrayList();
                for(int i=0;i<supportedProtocols.length;i++)
                {
                        if(ConfManager.isEnabledTLSVersion(supportedProtocols[i]))
                        {
                                tlslist.add(supportedProtocols[i]);
                        }
                }
                String[] tlsarr = new String[tlslist.size()];
                for(int i=0;i<tlsarr.length;i++)
                {
                        tlsarr[i] = (String)tlslist.get(i);
                }
                return tlsarr;
        }

        public static String[] getSSLCipherSuiteOrder(SSLEngine sslEngine)
        {
		ArrayList supportedCipherSuitesList = new ArrayList(Arrays.asList(sslEngine.getSupportedCipherSuites()));
                ArrayList enabledCipherSuitesList = ConfManager.getEnabledCipherSuitesList();
                Iterator itr = new ArrayList(enabledCipherSuitesList).iterator();
                while(itr.hasNext())
                {
                        String cipherSuite = (String)itr.next();
                                
                        if(cipherSuite!=null && !supportedCipherSuitesList.contains(cipherSuite))
                        {
                                enabledCipherSuitesList.remove(cipherSuite);
                        }
                }
                String[] suitearr = new String[enabledCipherSuitesList.size()];
                for(int i=0;i<suitearr.length;i++)
                {
                        suitearr[i] = (String)enabledCipherSuitesList.get(i);
                }
                return suitearr;
        }

	public static KeyManagerFactory getKeyManagerFactory(Properties sslproperties,String srvname)throws Exception
        {
		String keyStore;
                String keyPass;
                if(ConfManager.getKeyStoreLoader() != null)
                {
                        keyStore = ConfManager.getKeyStoreLoader().getKeyStoreName(srvname);
                        keyPass = ConfManager.getKeyStoreLoader().getKeyStorePass(srvname);
                }
                else
                {
                        keyStore = sslproperties.getProperty("keystore");
                        keyPass = sslproperties.getProperty("keypass");
                }
                String keyStoreType = sslproperties.getProperty("keystoretype","JKS");
                if(keyStore==null || keyPass==null)
                {
                        logger.log(Level.INFO, "keyStore or keyPass is null --> Unable to init SSL for the servlet " + srvname,SSLUtil.class.getName(),AWSLogMethodConstants.GET_KEY_MANAGER_FACTORY);
                        return null;
                }
                KeyStore ks = KeyStore.getInstance(keyStoreType);

                char[] passArray = keyPass.toCharArray();

                ks.load(new FileInputStream(ConfManager.getSSLPropFilePath()+keyStore), passArray);

                KeyManagerFactory kf = KeyManagerFactory.getInstance("SunX509");
                kf.init(ks, passArray);
		return kf;
	}

}


