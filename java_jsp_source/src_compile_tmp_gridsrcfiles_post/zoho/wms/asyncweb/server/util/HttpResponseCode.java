//$Id$

package com.zoho.wms.asyncweb.server.util;

public class HttpResponseCode
{
	public static final int WS_UPGRADE = 101;

	public static final int OK = 200;

	public static final int NOT_MODIFIED = 304;

	public static final int BAD_REQUEST = 400;
	public static final int UNAUTHORIZED = 401;
	public static final int FORBIDDEN = 403;
	public static final int NOT_FOUND = 404;
	public static final int METHOD_NOT_ALLOWED = 405;
	public static final int LENGTH_REQUIRED = 411;
	public static final int TOO_MANY_REQUESTS = 429;
	public static final int REDIRECT = 302;

	public static final int INTERNAL_SERVER_ERROR = 500;
	public static final int MULTIPLE_CHOICES = 300;
	public static final int SERVICE_UNAVAILABLE = 503;

	
	public static final String INTERNAL_SERVER_ERROR_MSG = "Internal Server Error";//No I18n
	public static final String BAD_REQUEST_MSG = "Bad Request";
	public static final String NOT_FOUND_MSG = "Not Found";//No I18n
	public static final String UNAUTHORIZED_MSG = "Unauthorized";
	public static final String METHOD_NOT_ALLOWED_MSG = "Method Not Allowed";
	public static final String LENGTH_REQUIRED_MSG = "Length Required";
	public static final String NOT_MODIFIED_MSG = "Not Modified";//No I18n
	public static final String  FORBIDDEN_MSG = "Forbidden";//No I18n
	public static final String OK_MSG = "OK";//No I18n
	public static final String TOO_MANY_REQUESTS_MSG = "Too Many Requests";//No I18N
	public static final String FOUND = "FOUND";
}
