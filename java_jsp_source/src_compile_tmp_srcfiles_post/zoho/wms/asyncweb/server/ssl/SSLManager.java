//$Id$
package com.zoho.wms.asyncweb.server.ssl;

import java.io.*;
import java.security.*;
import java.util.Hashtable;
import com.zoho.wms.asyncweb.server.X509DynamicTrustManager;
import javax.net.ssl.*;

import com.zoho.wms.asyncweb.server.ConfManager;

public abstract class SSLManager
{
	public static Hashtable<String,Integer> sslSessionCntxMap = new Hashtable();
        public static Hashtable<String,Integer> sslContextCounterMap = new Hashtable();
	public static int sslsessiontimeout = ConfManager.getSSLSessionTimeout();

	public static Hashtable<String,X509DynamicTrustManager> tmstore = new Hashtable();
	public abstract void initialize() throws Exception;
	public abstract SSLEngine getSSLEngine(int port , String ipaddr, int sockport);
	public abstract void updateCipherAndProtocolMap() throws Exception;
	public abstract void resetSSLSessionTimeout();

	public abstract String[] getSSLProtocol(int port);   
        public abstract String[] getSSLCipherSuite(int port);

	public static void addToTrustStore(String port, String domain, byte[] data, char[] password) throws Exception
        {
                X509DynamicTrustManager tm = tmstore.get(port);

                if(tm==null)
                {
                        throw new SSLException("SSL Context not initialized for the port "+port); //No I18n
                }

                addToTrustStore(port, domain, data, password, "JKS"); //No I18n 
        }

        public static void addToTrustStore(String port, String domain, byte[] data, char[] password, String kstype) throws Exception
        {
                X509DynamicTrustManager tm = tmstore.get(port);

                if(tm==null)
                {
                        throw new SSLException("SSL Context not initialized for the port "+port); //No I18n 
                }

                tm.add(domain, data, password, kstype);
        }

        public static void deleteTrustStore(String port, String domain) throws Exception
        {
                X509DynamicTrustManager tm = tmstore.get(port);

                if(tm==null)
                {
                        throw new SSLException("SSL Context not initialized for the port "+port); //No I18n 
                }

                tm.delete(domain);
        }

        public static void resetContextMap()
        {
                tmstore.clear();
        }

}





