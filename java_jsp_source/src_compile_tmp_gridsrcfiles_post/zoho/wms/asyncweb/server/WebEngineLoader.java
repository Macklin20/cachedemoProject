//$Id$
package com.zoho.wms.asyncweb.server;

import com.zoho.wms.asyncweb.server.http.HttpRequest;

public interface WebEngineLoader
{
        public String getEngineName(HttpRequest request);

        default String getInternalEngineName(HttpRequest request)
        {
                return null;
        }
}
