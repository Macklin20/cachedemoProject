//$Id$
package com.zoho.wms.asyncweb.server.http;

import java.io.IOException;

public interface DownloadRefillListener
{
	public void onOutputBufferRefill(HttpRequest req, HttpResponse res) throws IOException;
}
