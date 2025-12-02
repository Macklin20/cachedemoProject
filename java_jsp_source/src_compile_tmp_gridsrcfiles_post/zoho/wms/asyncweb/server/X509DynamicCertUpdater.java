//$Id$
package com.zoho.wms.asyncweb.server;

import java.security.cert.X509Certificate;

public abstract class X509DynamicCertUpdater
{
	public abstract boolean updateToTrustStore(String port, String domain);
	public abstract boolean checkValidity(X509Certificate[] chain, String domain);
}

