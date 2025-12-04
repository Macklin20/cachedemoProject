//$Id$

package com.zoho.wms.asyncweb.server.util;

public class StateConstants
{
	public static final int REQUEST_IN_PROCESS = 1;
	public static final int REQUEST_ACKNOWLEDGED = 2;
	public static final int REQUEST_KEEPALIVE = 3;

	public static final int ON_HEADER_COMPLETION = 10;
	public static final int ON_DATA = 11;
	public static final int ON_COMPLETION = 12;
	public static final int ON_OUTPUTBUFFERREFILL = 13;
	public static final int ON_WRITECOMPLETE = 14;
	public static final int ON_WRITEFAILURE = 15;
	public static final int ON_CLOSE = 16;
	public static final int ON_PING = 20;
	public static final int ON_PONG = 21;

}
