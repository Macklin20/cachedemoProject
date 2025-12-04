package com.zoho.wms.asyncweb.server.exception;

public class Http2Exception extends Exception
{
    public Http2Exception(String msg)
    {
        super(msg);
    }

    public Http2Exception(String msg, Throwable ex)
    {
        super(msg, ex);
    }
}