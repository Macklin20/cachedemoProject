//$Id$
package com.zoho.wms.asyncweb.server;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIServerName;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class X509DynamicTrustManager extends X509ExtendedTrustManager
{
	private static AsyncLogger logger = new AsyncLogger(X509DynamicTrustManager.class.getName());
	
	private String port;
	private ConcurrentHashMap<String, ArrayList<X509ExtendedTrustManager>> dynamicTMs = new ConcurrentHashMap();
	private static X509DynamicCertUpdater dcu = ConfManager.getDCUImpl();

	public X509DynamicTrustManager(String port) throws Exception
	{
		super();
		this.port = port;
	}

	public void add(String domain, String kspath, char[] password, String kstype) throws Exception
	{
		try
		{
			KeyStore ks = KeyStore.getInstance(kstype);
			InputStream in = new FileInputStream(kspath);

			try
			{
				ks.load(in, password);
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE,"Failed to load keystore TM "+kspath,X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.ADD, ex);
				throw ex;
			}
			finally
			{
				try
				{
					in.close();
				}
				catch(Exception ex){}
			}

			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ks);
				
			TrustManager tms[] = tmf.getTrustManagers();		
			for (int i = 0; i < tms.length; i++)
			{
				if (tms[i] instanceof X509ExtendedTrustManager) 
				{
					ArrayList<X509ExtendedTrustManager> list;
					if(dynamicTMs.containsKey(domain))
					{
						list = dynamicTMs.get(domain);
					}
					else
					{
						list = new ArrayList<>();

					}
					list.add((X509ExtendedTrustManager)tms[i]);
					dynamicTMs.put(domain, list);
					return;
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "[Port] "+port+" Add keystore failure in TM "+kspath+" : ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.ADD, ex);
			throw ex;	
		}
	}

	public void add(String domain, byte[] keystore, char[] password, String kstype) throws Exception
	{
		try
		{
			KeyStore ks = KeyStore.getInstance(kstype);
			InputStream in = new ByteArrayInputStream(keystore);
			
			try
			{
				ks.load(in, password);
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE,"[Port] "+port+" Failed to add keystore for domain TM "+domain,X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.ADD,ex);
				throw ex;
			}
			finally
			{
				in.close();
			}

			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ks);
				
			TrustManager tms[] = tmf.getTrustManagers();		
			for (int i = 0; i < tms.length; i++)
			{
				if (tms[i] instanceof X509ExtendedTrustManager) 
				{
					ArrayList<X509ExtendedTrustManager> list;
					if(dynamicTMs.containsKey(domain))
					{
						list = dynamicTMs.get(domain);
					}
					else
					{
						list = new ArrayList<>();

					}
					list.add((X509ExtendedTrustManager)tms[i]);
					dynamicTMs.put(domain, list);
					return;
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "[Port] "+port+" Add keystore failure in TM "+domain+" : ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.ADD, ex);
			throw ex;
		}
	}

	public void delete(String domain)
	{
		dynamicTMs.remove(domain);
	} 

	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
	{
		throw new CertificateException("Unable to fetch SNI name");//No I18N
	}
	
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
	{
		throw new CertificateException("Unable to fetch SNI name");//No I18N
	}

	public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException
	{
		throw new CertificateException("Unable to fetch SNI name");//No I18N
	}
	
	public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException
	{
		throw new CertificateException("Unable to fetch SNI name");//No I18N
	}

	public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException
	{
		String domain;
		try
		{
			verifyDigitalSignature(chain);
			domain = getServerName(engine);

			if(domain == null)
			{
				throw new CertificateException("Subject DN not present");//No I18N
			}
			else
			{
				if(!dcu.checkValidity(chain, domain))
				{
					throw new CertificateException("Invalid Certificate"); // No I18n
				}
				
				ArrayList<X509ExtendedTrustManager> list = dynamicTMs.get(domain);

				if(list == null)
				{
					try
					{
						if(!dcu.updateToTrustStore(port, domain))
						{
							throw new CertificateException("Update to truststore failed");// No I18n
						}
						list = dynamicTMs.get(domain);
					}
					catch(CertificateException cxe)
					{
						throw cxe;
					}
					catch(NullPointerException npe)
					{
						logger.log(Level.SEVERE, "Null Pointer Exception ",X509DynamicTrustManager.class.getName(),"checkClientTrusted", npe);
						throw npe;
					}
					catch(Exception ex)
					{
						logger.log(Level.SEVERE, "Exception ",X509DynamicTrustManager.class.getName(),"checkClientTrusted", ex);
						throw ex;
					}
				}
				try
				{
					if(list == null || list.isEmpty())
					{
						throw new Exception("No TrustManager Found.");//No I18n
					}
					for(X509ExtendedTrustManager tm : list)
					{
						try
						{
							tm.checkClientTrusted(chain, authType, engine);
							return;
						}
						catch(CertificateException ex)
						{
							logger.log(Level.SEVERE, "Check client trusted for domain {0} failed.",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_CLIENT_TRUSTED, new Object[]{domain});//No I18n
						}
					}
					throw new CertificateException("Certificate chain is not present in any TMs for "+domain);//No I18N
				}
				catch(CertificateException cxe)
				{
					throw cxe;
				}
				catch(NullPointerException npe)
				{
					logger.log(Level.SEVERE, "Null Pointer Exception ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_CLIENT_TRUSTED, npe);//No I18n
					throw npe;
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, "Exception ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_CLIENT_TRUSTED, ex);
					throw ex;
				}
			}

		}
		catch(CertificateException cxe)
		{
			logger.log(Level.INFO, "Certificate Exception : ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_CLIENT_TRUSTED, cxe);
			throw cxe;
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception : ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_CLIENT_TRUSTED, ex);
			throw new CertificateException("Unknown issue");//No I18N
		}
	}
	
	public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException
	{
		String domain;
		try
		{
			verifyDigitalSignature(chain);
			domain = getServerName(engine);

			if(domain == null)
                        {
                                throw new CertificateException("Subject DN not present");//No I18N
                        }
			else
                        {
				if(!dcu.checkValidity(chain, domain))
				{
					throw new CertificateException("Invalid Certificate"); // No I18n
				}

				ArrayList<X509ExtendedTrustManager> list = dynamicTMs.get(domain);

				if(list == null)
				{
					try
					{
						if(!dcu.updateToTrustStore(port, domain))
						{
							throw new CertificateException("Update to truststore failed");// No I18n
						}
						list = dynamicTMs.get(domain);
					}
					catch(CertificateException cxe)
					{
						throw cxe;
					}
					catch(NullPointerException npe)
					{
						logger.log(Level.SEVERE, "Null Pointer Exception ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_SERVER_TRUSTED, npe);
						throw npe;
					}
					catch(Exception ex)
					{
						logger.log(Level.SEVERE, "Exception ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_SERVER_TRUSTED, ex);
						throw ex;
					}
				}
				try
				{
					if(list == null || list.isEmpty())
					{
						throw new Exception("No TrustManager Found.");//No I18n
					}
					for(X509ExtendedTrustManager tm : list)
					{
						try
						{
							tm.checkServerTrusted(chain, authType, engine);
							return;
						}
						catch(CertificateException ex)
						{
							logger.log(Level.SEVERE, "Check Server trusted for domain {0} failed.",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_SERVER_TRUSTED, new Object[]{domain});//No I18n
						}
					}
					throw new CertificateException("Certificate chain is not present in any TMs for "+domain);//No I18N
				}
				catch(CertificateException cxe)
				{
					throw cxe;
				}
				catch(NullPointerException npe)
				{
					logger.log(Level.SEVERE, "Null Pointer Exception ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_SERVER_TRUSTED, npe);//No I18n
					throw npe;
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, "Exception ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_SERVER_TRUSTED, ex);
					throw ex;
				}
			}
		}
		catch(CertificateException cxe)
		{
			logger.log(Level.INFO, "Certificate Exception : ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_SERVER_TRUSTED, cxe);
			throw cxe;
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception : ",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.CHECK_SERVER_TRUSTED, ex);
			throw new CertificateException("Unknown issue");//No I18N
		}
	}

	public X509Certificate[] getAcceptedIssuers()
	{
		return new X509Certificate[0];
	}

	public void verifyDigitalSignature(X509Certificate[] chain) throws CertificateException
	{
		Principal authprincipal = null;
		for (int i = (chain.length-1); i >= 0 ; i--)
		{
			X509Certificate cert = chain[i];
			Principal issuer = cert.getIssuerDN();
			Principal subject = cert.getSubjectDN();
			if (authprincipal!= null) {
				if (issuer.equals(authprincipal)) {
					try {
						PublicKey publickey =
							chain[i + 1].getPublicKey();
						chain[i].verify(publickey);
					}
					catch (GeneralSecurityException generalsecurityexception) {
						throw new CertificateException(
								"signature verification failed ");//No I18N
					}
				}
				else {
					throw new CertificateException(
							"subject/issuer verification failed of ");//No I18N
				}
			}
			authprincipal = subject;
		}
	}

	private String getServerName(SSLEngine engine)
	{
		try
		{
			ExtendedSSLSession session=(ExtendedSSLSession)engine.getHandshakeSession();
			List<SNIServerName> serverNames = session.getRequestedServerNames();
			SNIServerName sniServer = serverNames.get(0);
			byte[] bytes = sniServer.getEncoded();
			String serverName = new String(bytes);
			return serverName;
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"Exception",X509DynamicTrustManager.class.getName(),AWSLogMethodConstants.GET_SERVER_NAME,ex);
		}
		return null;
	}
}	
