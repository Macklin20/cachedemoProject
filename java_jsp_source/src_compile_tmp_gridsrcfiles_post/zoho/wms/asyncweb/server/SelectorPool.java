//$Id$
package com.zoho.wms.asyncweb.server;

import java.io.IOException;
import java.nio.channels.*;

public abstract class SelectorPool
{
	public abstract void initRead() throws IOException ;

	public abstract void registerRead(SocketChannel sc , Integer port, Long stime) throws IOException ;

	public abstract String getDebugInfo(boolean details);	

	public abstract void shutdown() throws Exception;
}


