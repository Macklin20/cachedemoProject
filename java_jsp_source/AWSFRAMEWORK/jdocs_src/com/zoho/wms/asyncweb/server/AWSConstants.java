//$Id$
package com.zoho.wms.asyncweb.server;

import com.adventnet.wms.common.WMSConstants;

public class AWSConstants
{
	public static final int AUTH_OPTIONAL = 0;
	public static final int AUTH_MANDATORY = 1;

	public static final String AWS_THREAD_PREFIX = "AWS"+WMSConstants.THREAD_NAME_SEPARATOR;//No I18n
	public static final String THREAD_NAME_SEPARATOR = "-";

	// ThreadPool Naming Constants
	public static final String REQUEST_PROCESSOR = AWS_THREAD_PREFIX+"AsyncRequestProcessor";//No I18n
	public static final String REQUEST_PROCESSOR_WS = AWS_THREAD_PREFIX+"AsyncRequestProcessor-WS";//No I18n
	public static final String NETDATA_PROCESSOR = AWS_THREAD_PREFIX+"AsyncWebNetDataProcessor";//No I18n
	public static final String WEBENGINE_NETDATA_PROCESSOR = AWS_THREAD_PREFIX+"AsyncWebNetDataProcessor-";
	public static final String ASYNCLOG_PROCESSOR = AWS_THREAD_PREFIX+"AsyncLogProcessor";//No I18n
	public static final String WEBENGINE = AWS_THREAD_PREFIX+"AbstractWebEngine-";//No I18n
	public static final String ANOMALY_HANDLER = AWS_THREAD_PREFIX+"AnomalyHandler";//No I18n
	public static final String WSMSGTHREAD = "WSMsgThread";//No I18n
	public static final String TP_FRAME_EXTERNAL = AWS_THREAD_PREFIX+"AsyncFrameExternalProcessor";//No I18n
	public static final String HTTP2_FRAMEPROCESSOR = AWS_THREAD_PREFIX+"Http2FrameProcessor";//No I18n

	// Monitoring Constants
	public static final String ANOMALY_MONITOR = "ANOMALY_MONITOR";//No I18n
	public static final String ANOMALY_MONITOR_DATA_RECORD = "ANOMALY_MONITOR_DATA_RECORD";//No I18n
	public static final String DOS_MONITOR = "DOS_MONITOR";//No I18n
	public static final String SERVER_PORT_MONITOR = "SERVER_PORT_MONITOR";//No I18n
	public static final String DEADLOCK_MONITOR = "DEADLOCK_MONITOR";//No I18n

	public static final int GRID_PORT = 7070;

	public static final String DEFAULT_CHARSET = "ISO-8859-1";//No I18n
	public static final String AWS_ENGINE_HEADER = "x-awsengine";//No I18n

	public static final int WRITE_IN_PROGRESS = 0;
	public static final int WRITE_COMPLETED = 1;
	public static final int WRITE_FAILURE = -1;
	public static final int WRITE_IDLE = 2;
	public static final int WRITE_IN_PROGRESS_CHUNKED = 3;

	//ServerPort Monitor Constants
	public static final byte[] HTTP_200 = "HTTP/1.1 200".getBytes();//No I18n
	public static final byte[] HTTP_404 = "HTTP/1.1 404".getBytes();//No I18n

	//WebEngine constants
	public static final String PUBSUB_APPNAME = "pubsub";//No I18n
	public static final String LIVEHTTP_APPNAME = "livehttp";//No I18N
	public static final String WS_APPNAME = "ws";//No I18N
	public static final String GRID_APPNAME = "grid";//No I18n
	public static final String POP_APPNAME = "pop";//No I18N

	//request Type Constants
	public static final String POST_REQ = "POST";//No I18n
	public static final String GET_REQ = "GET";//No I18n
	public static final String DELETE_REQ = "DELETE";//No I18n
	public static final String OPTIONS_REQ = "OPTIONS";//No I18n
	public static final String HEAD_REQ = "HEAD";//No I18n

	//logs
	public static final String HACKLOG = "hacklog";//No I18n
	public static final String ACCESS_LOGGER = "accesslogger";//No I18n
	public static final String CUSTOM_FIELD_REQUEST_ID = "_c_reqid";//No I18n
	public static final String JAVAAPPLICATION = "javaapplication";//No I18n
	public static final String APPLICATION = "application";//No I18n
	public static final String AWSACCESS = "awsaccess";//No I18n
	public static final int LOG_DISPATCHER = 0;
	public static final int LOG_TMPA = 1;
	public static final int LOG_TMPB = 2;
	public static final int LOG_TMPC = 3;
	public static final int LOG_RECORD_DISPATCHER = 4;
	public static final String REQID = "reqid";//No I18n

	//Exception
	public static final String CLOSED = "Closed";//No I18n
	public static final String READ_MINUS_ONE = "Read -1";//No I18n
	public static final String UNKNOWN_FILE = "Unknown File";//No I18n

	//protocols
	public static final String HTTP = "http";//No I18n
	public static final String HTTPS = "https";//No I18n
	public static final String WS = "ws";//No I18n
	public static final String WSS = "wss";//No I18n
	public static final String HTTP_1_1 = "HTTP/1.1";//No I18n
	public static final String HTTP_1_1_SPACE = "HTTP/1.1 ";//No I18n

	//Header Constants
	public static final String CONTENT_LENGTH = "content-length";//No I18n
	public static final String HDR_CONTENT_LENGTH = "Content-Length";//No I18n
	public static final String CONTENT_ENCODING = "content-encoding";//No I18n
	public static final String HDR_CONTENT_ENCODING = "Content-Encoding";//No I18n
	public static final String CONTENT_TYPE = "content-type";//No I18n
	public static final String HDR_CONTENT_TYPE = "Content-Type";//No I18n
	public static final String CONNECTION = "connection";//No I18n
	public static final String HDR_CONNECTION = "Connection";//No I18n
	public static final String UPGRADE = "upgrade";//No I18n
	public static final String HDR_UPGRADE = "Upgrade";//No I18n
	public static final String WEBSOCKET = "websocket";//No I18n
	public static final String SEC_WEBSOCKET_KEY = "sec-websocket-key";//No I18n
	public static final String HDR_SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";//No I18n
	public static final String SEC_WEBSOCKET_VERSION = "sec-websocket-version";//No I18n
	public static final String HDR_SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";//No I18n
	public static final String SEC_WEBSOCKET_EXTENSIONS = "sec-websocket-extensions";//No I18n
	public static final String HDR_SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";//No I18n
	public static final String HDR_SEC_WEBSOCKET_EXTENSIONS_1 = "Sec-Websocket-Extensions";//No I18n
	public static final String SEC_WEBSOCKET_PROTOCOL = "sec-websocket-protocol";//No I18n
	public static final String HDR_SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";//No I18n
	public static final String KEEPALIVE = "keepalive";//No I18n
	public static final String KEEP_ALIVE = "keep-alive";//No I18n
	public static final String HDR_KEEP_ALIVE = "Keep-Alive";//No I18n
	public static final String ORGIN = "origin";//No I18n
	public static final String ACCESS_CONTROL_REQUEST_HEADERS = "access-control-request-headers";//No I18n
	public static final String USER_AGENT = "user-agent";//No I18n
	public static final String HDR_TRANSFER_ENCODING = "Transfer-Encoding";//No I18n
	public static final String CHUNKED = "chunked";//No I18n
	public static final String HOST = "host";//No I18n
	public static final String REFERER = "referer";//No I18n
	public static final String AUTHORIZATION = "authorization";//No I18n
	public static final String MULTIPART = "multipart";//No I18n
	public static final String COOKIE = "cookie";//No I18n
	public static final String SET_COOKIE = "set_cookie";//No I18n
	public static final String HDR_COOKIE = "Cookie";//No I18n
	public static final String HDR_LOCATION = "Location";//No I18n
	public static final String GZIP = "gzip";//No I18n
	public static final String TEXT_OR_PLAIN = "text/plain";//No I18n
	public static final String HDR_SERVER = "Server";//No I18n
	public static final String HDR_AWSERVER = "AWServer";//No I18n
	public static final String HDR_LAST_MODIFIED = "Last-Modified";//No I18n
	public static final String BYTESIN = "bytesin";//No I18n
	public static final String BYTESOUT = "bytesout";//No I18n
	public static final String EXTERNAL = "external";//No I18n
	public static final String DATE = "date";//No I18n
	public static final String HDR_DATE = "Date";//No I18n
	public static final String TRACKING_ID = "tracking-id";//No I18n
	public static final String ACCEPT_LANGUAGE = "accept-language";//No I18n
	public static final String BOUNDARY = "boundary";//No I18n
	public static final String CHARSET = "charset";//No I18n
	public static final String IF_MODIFIED_SINCE = "if-modified-since";//No I18n
	public static final String HDR_REUSE_CONNECTION = "Reuse-Connection";//No I18n
	public static final String HDR_SET_COOKIE = "Set-Cookie";//No I18n
	public static final String HDR_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";//No I18n
	public static final String APPLICATION_VND_MS_SYNC_WBXML = "application/vnd.ms-sync.wbxml";//No I18n
	public static final String HDR_MICROSOFT_IIS_6_0 = "Microsoft-IIS/6.0";//No I18n
	public static final String HDR_MS_SERVER_ACTIVESYNC = "MS-Server-ActiveSync";//No I18n
	public static final String HDR_ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";//No I18n
	public static final String HDR_ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";//No I18n
	public static final String HDR_ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";//No I18n
	public static final String HDR_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";//No I18n
	public static final String HDR_X_FRAME_OPTIONS = "X-Frame-Options";//No I18n
	public static final String HDR_ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";//No I18n
	public static final String APPLICATION_X_WWW_URLENCODED = "application/x-www-form-urlencoded";//No I18n
	public static final String TEXT_PLAIN_CHARSET_UTF_8 = "text/plain;charset=UTF-8";//No I18n
	public static final String TEXT_OR_HTML = "text/html";//No I18n
	public static final String FOUR_PROTOCOLS = "http://|https://|ws://|wss://";//No I18n
	public static final String SWITCHING_PROTOCOLS = "Switching Protocols";//No I18n
	public static final String SPACE = " ";//No I18N
	public static final String NEWLINE = "\r\n";//No I18N
	public static final String RESPONSE_CODE = "response_code";//No I18n
	public static final String RESPONSE_MESSAGE = "response_message";//No I18n
	public static final String REQUEST_TIME_TAKEN = "request_time_taken";//No I18n
	public static final String TIME_TAKEN = "time_taken";//No I18n
	public static final String SERVICE = "service";//No I18n
	public static final String THREAD_ID = "thread_id";//No I18n
	public static final String THREAD_NAME = "thread_name";//No I18n
	public static final String VERSION = "version";//No I18n

	//TimeLine Constants
	public static final String TL_DOWNLOAD_TIME = "download time";//No I18n
	public static final String TL_DOWNLOAD_FAILURE = "download failure";//No I18n
	public static final String TL_WRITE_TIME = "write time";//No I18n
	public static final String TL_WRITE_FAILURE = "write failure";//No I18n
	public static final String TL_CREATION_TIME = "creation time";//No I18n
	public static final String TL_RESET_TIME = "reset time";//No I18n
	public static final String TL_UPLOAD_TIME = "upload time";//No I18n
	public static final String TL_UPLOAD_FAILURE = "upload failure";//No I18n
	public static final String TL_READ_TIME = "read time";//No I18n
	public static final String TL_READ_FAILURE = "read failure";//No I18n
	public static final String TL_SSL_HANDSHAKE_TIME = "ssl handshake time";//No I18n
	public static final String TL_WS_CONNECT_TIME = "ws connect time";//No I18n
	public static final String TL_SERVLET_TIME = "servlet time";//No I18n

	//accesslog
	public static final String REQUEST_URI = "request_uri";//No I18n
	public static final String PARAM = "param";//No I18n
	public static final String HEADER = "header";//No I18n
	public static final String METHOD = "method";//No I18n
	public static final String REMOTE_IP = "remote_ip";//No I18n
	public static final String SERVER_PORT = "server_port";//No I18n
	public static final String LOCAL_PORT = "local_port";//No I18n
	public static final String RESPONSE_HEADER = "response_header";//No I18n
	public static final String REQUEST_HEADER = "request_header";//No I18n
	public static final String TIMETAKEN = "timetaken";//No I18n
	public static final String ENDAWSACCESS = "endawsaccess";//No I18n

	//stats
	public static final String SSL_CONNECT_TIME = "ssl_connect_time";//No I18n
	public static final String CONNECT_TIME = "connect_time";//No I18n
	public static final String SSL_HANDSHAKE_TIME_STATS = "ssl_handshake_time";//No I18n
	public static final String SSL_RESET_TIME_STATS = "ssl_reset_time";//No I18n
	public static final String NETDATA_TASKQUEUE = "netdata_taskqueue";//No I18n
	public static final String NETDATA_PROCESSING = "netdata_processing";//No I18n
	public static final String NETDATA_READDATA = "netdata_readdata";//No I18n
	public static final String TASKQUEUEDTIME = "taskQueuedTime";//No I18n
	public static final String PROCESSING_TIME = "processingTime";//No I18n
	public static final String READTIME = "readtime";//No I18n
	public static final String WRITETIME = "writetime";//No I18n
	public static final String COUNT = "count";//No I18n
	public static final String RESETTIME = "reset_time";//No I18n
	public static final String AWS_REQ_PROC = "aws_req_proc";//No I18n
	public static final String AWS_NETDATA_PROC = "aws_netdata_proc";//No I18n
	public static final String AWS_WEBENGINE = "aws_webengine";//No I18n
	public static final String AWS_REQUEST_STATS = "aws_request_stats";//No I18n
	public static final String AWS_INFLATE = "aws_inflate";//No I18n
	public static final String AWS_DEFLATE = "aws_deflate";//No I18n
	public static final String NETDATA_WRITEDATA = "netdata_writedata";//No I18n
	public static final String NETDATA_TOTALTIME = "netdata_totaltime";//No I18n
	public static final String READCOUNT = "readCount";//No I18n
	public static final String WRITECOUNT = "writeCount";//No I18n
	public static final String SAMEORIGIN = "SAMEORIGIN";//No I18n
	public static final String RESET_AND_GETSTATS = "reset_and_getstats";//No I18n
	public static final String USER_AGENT_1 = "user_agent";//No I18n
	public static final String REMAINING_QUEUE_CAPACITY = "remaining_queue_capacity";//No I18n
	public static final String LARGEST_POOL = "largest_pool";//No I18n
	public static final String COMPLETED_TASK_COUNT = "completed_task_count";//No I18n
	public static final String TASK_COUNT = "task_count";//No I18n
	public static final String ACTIVE_COUNT = "active_count";//No I18n
	public static final String POOLSIZE = "pool_size";//No I18n
	public static final String TPE_STATS = "tpe_stats";//No I18n
	public static final String HYPHEN_WSMSG = "-WSMSG";//No I18n
	public static final String HEAVY_DATA = "heavy_data";//No I18n
	public static final String HEAVY_READ = "heavy_read";//No I18n
	public static final String HEAVY_WRITE = "heavy_write";//No I18n
	public static final String SSL_HEAVY_READ = "ssl_heavy_read";//No I18n
	public static final String SSL_HEAVY_WRITE = "ssl_heavy_write";//No I18n
	public static final String HACK_ATTEMPT = "hack_attempt";//No I18n
	public static final String INVALID_REQ_TYPE = "invalid_req_type";//No I18n
	public static final String INVALID_REQ_URL = "invalid_req_url";//No I18n
	public static final String INVALID_REQ_HEADERS = "invalid_req_headers";//No I18n
	public static final String AWS_SERVERPORTMONITOR_STATS = "aws_serverportmonitor";//No I18n
	public static final String WEBENGINE_TASKQUEUE = "webengine_taskqueue";//No I18n
	public static final String WEBENGINE_PROCESSING = "webengine_processing";//No I18n
	public static final String WEBENGINE_TOTAL = "webengine_total";//No I18n
	public static final String WEBENGINE_SERVLET = "webengine_servlet";//No I18n
	public static final String WEBENGINE_ONDATA = "webengine_ondata";//No I18n

	//wmsruntime
	public static final String TOTAL = "total";//No I18n
	public static final String TODAY = "today";//No I18n
	public static final String HITRATE = "hitrate";//No I18n
	public static final String MAXHITRATE = "maxhitrate";//No I18n
	public static final String MAXHITRATETIME = "maxhitratetime";//No I18n
	public static final String MAXHITRATE2DAY = "maxhitrate2day";//No I18n
	public static final String HITS_PER_MIN = "hits_per_min";//No I18n
	public static final String H = "h";//No I18n
	public static final String AVERAGE = "average";//No I18n
	public static final String TOTALREAD = "totalread";//No I18n
	public static final String READRATE_PER_MIN = "readrate/min";//No I18n
	public static final String TOTALWRITE = "totalwrite";//No I18n
	public static final String WRITERATE_PER_MIN = "writerate/min";//No I18n
	public static final String EXTERNALHTTPHITRATE = "externalhttphitrate";//No I18n
	public static final String EXTERNALHTTPSHITSRATE = "externalhttpshitrate";//No I18n
	public static final String USED = "used";//No I18n
	public static final String CURRENT = "current";//No I18n
	public static final String NORMAL = "normal";//No I18n
	public static final String HITS = "Hits";//No I18n
	public static final String USED_MEM = "used mem";//No I18n
	public static final String MEMORY = "Memory";//No I18n
	public static final String INTERNALHTTPREADRATE = "internalhttpreadrate";//No I18n
	public static final String INTERNALHTTPWRITERATE = "internalhttpwriterate";//No I18n
	public static final String EXTERNALHTTPREADRATE = "externalhttpreadrate";//No I18n
	public static final String EXTERNALHTTPWRITERATE = "externalhttpwriterate";//No I18n
	public static final String BANDWIDTH = "Bandwidth";//No I18n
	public static final String DOMAIN_HIT_RATE = "domain hit rate";//No I18n
	public static final String SPACE_KB_PER_MIN = " KB/min";//No I18n
	public static final String SPACE_MB_PER_MIN = " MB/min";//No I18n
	public static final String USED_MEMORY = "used memory";//No I18n
        public static final String TOTAL_MEMORY = "total memory";//No I18n
	public static final String EXTERNALHTTPSREADRATE = "externalhttpsreadrate";//No I18n
	public static final String EXTERNALHTTPSWRITERATE = "externalhttpswriterate";//No I18n
	public static final String MAX = "max";//No I18n
	public static final String MAXTIME = "maxtime";//No I18n
	public static final String MAXRATE = "maxrate";//No I18n
	public static final String RATE = "rate";//No I18n
	public static final String MAXRATETIME = "maxratetime";//No I18n
	public static final String SUMMARY = "summary";//No I18n
	public static final String TNAME = "tname";//No I18n
	public static final String PORT = "port";//No I18n
	public static final String DOMAIN_NAME = "domain_name";//No I18n
	public static final String AWS_SUSPECTIPS = "aws/suspectips";//No I18n
	public static final String AWS_LOGLEVEL = "aws/loglevel";//No I18n
	public static final String AWS_MEMINFO = "aws/meminfo";//No I18n
	public static final String AWS_HITS = "hits";//No I18n
	public static final String AWS_BW = "aws/bw";//No I18n
	public static final String AWS_GCPROFILER = "aws/gcprofiler";//No I18n
	public static final String AWS_UPDATECONF = "aws/updateconf";//No I18n
	public static final String CLI = "cli";//No I18n
	public static final String AWS_CPUINFO = "aws/cpuinfo";
	public static final String AWS_DISKUSAGEINFO = "aws/diskusageinfo";
	public static final String LOADAVG = "load-average";
	public static final String LOADPERCENT = "load-percentage";
	public static final String SYSTEMCPULOAD = "system_cpu_load";
	public static final String PROCESSCPULOAD = "process_cpu_load";
	public static final String CPUTIME = "cpu_time";
	public static final String THREADCOUNT = "thread_count";
	public static final String PROCESSORS = "available_processors";
	public static final String TOTAL_SIZE = "total_size";
	public static final String USED_SIZE = "used_size";
	public static final String FREE_SIZE = "free_size";
	public static final String USED_PERCENTAGE = "used_percentage";
	public static final String FREE_PERCENTAGE = "free_percentage";

	
	//others
	public static final String START = "start";//No I18n
	public static final String PATH = "Path";//No I18n
	public static final String SECURE = "Secure";//No I18n
	public static final String SAMESITE = "Samesite";//No I18n
	public static final String HTTPONLY = "HttpOnly";//No I18n
	public static final String AGE = "Max-Age";//No I18n
	public static final String EXPIREDATE = "Expires";//No I18n
	public static final String END = "end";//No I18n
	public static final String NA = "NA";//No I18n
	public static final String NAME = "name";//No I18n
	public static final String RESTRICTED = "*****";//No I18n
	public static final String VALUE = "value";//No I18n
	public static final String GRID = "grid";//No I18n
	public static final String DEFAULT = "default";//No I18n
	public static final String SAS = "sas";//No I18n
	public static final String UN_KNOWN = "UN_KNOWN";//No I18n
	public static final String ZAID = "zaid";//No I18n
	public static final String IAMCOOKIES = "iamcookies";//No I18n
	public static final String TICKET = "ticket";//No I18n
	public static final String DOMAIN = "domain";//No I18n
	public static final String TRUE = "true";//No I18n
	public static final String FALSE = "false";//No I18n
	public static final String OPTIONS = "options";//No I18n
	public static final String ZUID = "zuid";//No I18n
	public static final String WMS_TKP_TOKEN = "wms-tkp-token";//No I18n
	public static final String WMS_TKP_TOKEN_HYPHEN = "wms-tkp-token-";//No I18n
	public static final String PERMESSAGE_DEFLATE = "permessage-deflate";//No I18n
	public static final String CLIENT_MAX_WINDOW_BITS = "client_max_window_bits";//No I18n
	public static final String TYPE = "type";//No I18n
	public static final String X_WSINFO = "x-wsinfo";//No I18n
	public static final String SECURITYFILTER = "securityfilter";//No I18n
	public static final String KEEPALIVETIMEOUT = "keepalivetimeout";//No I18n
	public static final String SIMPLE_DATE_FORMAT = "EEE, dd MMM yyyy hh:mm:ss Z";//No I18n
	public static final String SIMPLE_DATE_FORMAT_1 = "EEE, dd MMM yyyy kk:mm:ss z";//No I18n
	public static final String SIMPLE_DATE_FORMAT_2 = "dd-MMM-yyyy HH:mm:ss";//No I18n
	public static final String VERSION_8_1 = "8.1";//No I18n
	public static final String UTF_8 = "UTF-8";//No I18n
	public static final String MAX_AGE_EQUAL_TO = "max-age=";//No I18n
	public static final String VALUE_0 = "0";//No I18n
	public static final String VALUE_1 = "1";//No I18n
	public static final String POST_COMMA_GET = "POST,GET";//No I18n
	public static final String INVALID_KEY_SPACE = "Invalid Key ";//No I18n
	public static final String ZSEC_API_CONTEXT_PATH = "ZSEC_API_CONTEXT_PATH";//No I18n
	public static final String CACHE  = "cache";//No I18n
	public static final String ASYNCFRAMEPROC = "asyncframeproc";//No I18n
	public static final String ORGID = "orgid";//No I18n
	public static final String NNAME = "nname";//No I18n
	public static final String AUTH = "auth";//No I18n
	public static final String OPR = "opr";//No I18n
	public static final String GET = "get";//No I18n
	public static final String SET = "set";//No I18n
	public static final String EMAIL = "email";//No I18n
	public static final String CLEAR = "clear";//No I18n
	public static final String OBJ = "obj";//No I18n
	public static final String LEVEL = "level";//No I18n
	public static final String MIN = "min";//No I18n
	public static final String OTHERS = "others";//No I18n
	public static final String STATUS_CODE_443 = "443";
	public static final String SAS_TRUE = "sastrue";//No I18n
	public static final String SAS_FALSE = "sasfalse";//No I18n
	public static final String SAS_TRUE_LENGTH = "7";//No I18n
	public static final String SAS_FALSE_LENGTH = "8";//No I18n
	public static final String RO = "RO";//No I18n
	public static final String SERVICE_UNAVAILABLE = "Service Unavailable";//No I18n
	public static final String EMPTY_STRING = "";//No I18n
	public static final String REQUIRED = "required";//No I18n
	public static final String OPTIONAL = "optional";//No I18n
	public static final String UNKNOWN = "unknown";//No I18n
	public static final String TLS_V1_2 = "TLSv1.2";//No I18n
	public static final String LOCALHOST = "localhost";//No I18n
	public static final String SERVER_PORT_MONITOR_HTTPS_REQUEST = "GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n";//No I18n
	public static final String HTTP_LOCALHOST = "http://localhost:";//No I18n
	public static byte[] inflater_constant_byte_array = new byte[] {0,0, -1, -1};
	public static final String ACK = "ack";
	public static final String WRITE = "write";
	public static final String READ = "read";
	public static final String SSL = "ssl";
	public static final String NONSSL = "nonssl";
	public static final String GREATER = "greater";
	public static final String LESSER = "lesser";
	public static final String EQUAL = "equal";
	public static final String LESSER_OR_EQUAL = "lesser_or_equal";

	//reason for AsyncWebClient close
	public static final String CONNECTION_ALREADY_CLOSED = "Connection already closed";
	public static final String IP_BLOCKED = "IP Blocked ";
	public static final String EXCEPTION_DISPATCH_REQUEST_TO_ENGINE = "Exception - Dispatch Request to engine";
	public static final String WS_WRITE_ACK_FAILURE = "WS write ack failure";
	public static final String EXCEPTION_DISPATCH_WS_DATA_TO_ENGINE = "Exception - Dispatch WS data to engine";
	public static final String IOEXCEPTION_IN_FRAMEPROCESSING = "IOException in Frame Processing";
	public static final String DATA_UNAVAILABLE_TO_READ = "Data Unavailable to read";
	public static final String KEY_INVALID = "Key invalid";
	public static final String WRITESTATUS_FAILURE = "WriteStatus failure";
	public static final String WRITEERROR = "Write error";
	public static final String CANCELLED_KEY_EXCEPTION_WRITE = "Cancelled key exception - write";
	public static final String EXCEPTION_WRITE = "Exception - Write";
	public static final String IOEXCEPTION_WRITE = "IOException - Write";
	public static final String WRITEFAILURE = "Write Failure";
	public static final String RESPONSE_WRITTEN = "Response written";
	public static final String CLOSE_SSL_HANDSHAKE= "Close - ssl handshake";
	public static final String WS_WRITE_FAILURE = "WS write failure";
	public static final String EXCEPTION_INTEREST_OPS = "Exception - key ops";
	public static final String INVALID_REQUEST = "Invalid request";
	public static final String EXPIRED_HEARTBEAT = "Expired heartbeat";
	public static final String READ_EXPIRED = "Read expired";
	public static final String READ_SELECTOR_CLOSED = "Read Selector closed";
	public static final String IOEXCEPTION_SELECTOR_KEY_SELECTION = "IOException - Selector key selection";
	public static final String CANCELLED_KEY_EXCEPTION_SELECTOR_KEY_SELECTION = "Cancelled key exception - Selector key selection";
	public static final String FORCE_CLOSE = "Force close";
	public static final String END_REQUEST_DATAFLOW = "End Request - Dataflow";
	public static final String CLOSE_ALL_CLIENTS = "Close all clients - Http2";
	public static final String CLOSE_HTTP2CONNECTION = "Close Http2Connection";
}
