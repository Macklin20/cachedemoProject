//$Id$
package com.zoho.wms.asyncweb.server;

public interface KeyStoreLoader
{
	public String getKeyStoreName(String srvname);
	public String getKeyStorePass(String srvname);
}
