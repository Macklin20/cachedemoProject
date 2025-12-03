package com.zoho.wms.asyncweb.server.http2;

public class Http2Constants
{
	// COMMON
	public static final String HTTP2_VERSION = "HTTP/2";
	public static final byte CONNECTION_STREAM_IDENTIFIER = (byte)0x0;
	public static final byte[] CLIENT_PREFACE_HEX_STRING = Http2Util.convertHexToByteArray("505249202a20485454502f322e300d0a0d0a534d0d0a0d0a");//No I18N
	public static final String HTTP2_REQ_TYPE = "PRI";
	public static final String HEADER_SEPARATOR = ": ";
	public static final String COOKIE_SEPARATOR = "; ";
	public static final String HEADER_VALUE_SEPARATOR = ", ";

	// SIZE
	public static final int HEADER_SIZE = 9;
	public static final int DEFAULT_FRAME_PAYLOAD_SIZE = 16384; // 16 kb   (i.e 2^14 octets)
	public static final int MAXIMUM_FRAME_PAYLOAD_SIZE = 16777215; // 16 mb   (i.e 2^24 - 1 octets)
	public static final int DEFAULT_WINDOW_SIZE= 65535; // 64 kb   (i.e 2^16 - 1 octets)
	public static final int MAXIMUM_WINDOW_SIZE= 2147483647; // 2 gb   (i.e 2^31 - 1 octets)
	public static final int DEFAULT_HEADER_TABLE_SIZE = 4096; // 4 kb (i.e 2^12 octets)
//	public static final long MAXIMUM_HEADER_TABLE_SIZE = 4294967295;

	// Frame Types
	public static final byte DATA_FRAME = (byte)0x0;
	public static final byte HEADER_FRAME = (byte)0x1;
	public static final byte PRIORITY_FRAME = (byte)0x2;
	public static final byte RESET_STREAM_FRAME = (byte)0x3;
	public static final byte SETTINGS_FRAME = (byte)0x4;
	public static final byte PUSH_PROMISE_FRAME = (byte)0x5;
	public static final byte PING_FRAME = (byte)0x6;
	public static final byte GOAWAY_FRAME = (byte)0x7;
	public static final byte WINDOW_UPDATE_FRAME = (byte)0x8;
	public static final byte CONTINUATION_FRAME = (byte)0x9;

	// Error Types
	public static final byte NO_ERROR = (byte)0x0;
	public static final byte PROTOCOL_ERROR = (byte)0x1;
	public static final byte INTERNAL_ERROR = (byte)0x2;
	public static final byte FLOW_CONTROL_ERROR = (byte)0x3;
	public static final byte SETTINGS_TIMEOUT = (byte)0x4;
	public static final byte STREAM_CLOSED = (byte)0x5;
	public static final byte FRAME_SIZE_ERROR = (byte)0x6;
	public static final byte REFUSED_STREAM = (byte)0x7;
	public static final byte CANCEL = (byte)0x8;
	public static final byte COMPRESSION_ERROR = (byte)0x9;
	public static final byte CONNECT_ERROR = (byte)0xa;
	public static final byte ENHANCE_YOUR_CALM = (byte)0xb;
	public static final byte INADEQUATE_SECURITY = (byte)0xc;
	public static final byte HTTP_1_1_REQUIRED = (byte)0xd;

	// Flag Types
	public static final byte EMPTY_FLAG = (byte)0x0;
	public static final byte END_STREAM_FLAG = (byte)0x1;
	public static final byte ACK_FLAG = (byte)0x1; //FOR SETTINGS and PING FRAMES
	public static final byte END_HEADERS_FLAG = (byte)0x4;
	public static final byte PADDED_FLAG = (byte)0x8;
	public static final byte PRIORITY_FLAG = (byte)0x20;

	// SETTINGS Parameters
	public static final int SETTINGS_HEADER_TABLE_SIZE = (byte)0x1;
	public static final int SETTINGS_ENABLE_PUSH = (byte)0x2;
	public static final int SETTINGS_MAX_CONCURRENT_STREAMS = (byte)0x3;
	public static final int SETTINGS_INITIAL_WINDOW_SIZE = (byte)0x4;
	public static final int SETTINGS_MAX_FRAME_SIZE = (byte)0x5;
	public static final int SETTINGS_MAX_HEADER_LIST_SIZE = (byte)0x6;

	// Header Indexing Types
	public static final int HEADER_INDEXED = 0;
	public static final int HEADER_INCREMENTAL_INDEXED = 1;
	public static final int HEADER_WITHOUT_INDEXED = 2;
	public static final int HEADER_NEVER_INDEXED = 3;

	// Pseudo_Header Fields
	public static final String METHOD_PSEUDO_HEADER = ":method";
	public static final String SCHEME_PSEUDO_HEADER = ":scheme";
	public static final String AUTHORITY_PSEUDO_HEADER = ":authority";
	public static final String PATH_PSEUDO_HEADER = ":path";
	public static final String STATUS_PSEUDO_HEADER = ":status";

	// Stream States
	public static final int IDLE = 0;
	public static final int RESERVED_LOCAL = 1;
	public static final int RESERVED_REMOTE = 2;
	public static final int OPEN = 3;
	public static final int HALF_CLOSED_LOCAL = 4;
	public static final int HALF_CLOSED_REMOTE = 5;
	public static final int CLOSED = 6;
	public static final int TIMED_OUT = 7;

	// Stats Constants
	public static final String STATS_H2CONN = "h2_conn";
	public static final String STATS_H2STREAM = "h2_stream";
	public static final String STATS_H2FRAME = "h2_frame";
	public static final String STATS_H2EXP = "h2_exp";
	public static final String STATS_H2_COMPRESSION = "h2_comp";
	public static final String STATS_HTTP2_COUNTER = "http2_counter";
	public static final String STATS_CLIENT_CONN = "client_conn";
	public static final String STATS_HTTP_REQUEST = "http_request";
	public static final String STATS_HTTP_BANDWIDTH = "http_bandwidth";
	public static final String STATS_HTTP_CONNECTION_REUSE = "http_connection_reuse";
	public static final String STATS_H2_CONN_READDATA_LIST_COUNT = "h2_conn_readdata_list_count";
	public static final String STATS_H2_ACTIVE_STREAM_COUNT = "h2_active_stream_count";


	// Error / Exception Constants
	public static final String EXP_H2CONN = "exp_new_h2conn";
	public static final String EXP_H2CONN_ADDHTTP2DATA = "exp_h2conn_addHttp2Data";
	public static final String EXP_H2CONN_PROCESSFRAME = "exp_h2conn_processFrame";
	public static final String EXP_H2CONN_DISPATCHNEWREQUEST = "exp_h2conn_dispatchNewRequest";
	public static final String EXP_H2CONN_WRITEDATA = "exp_h2conn_writeData";
	public static final String EXP_H2CONN_CLOSECONNECTION = "exp_h2conn_closeConnection";
	public static final String EXP_H2STREAM_PROCESSHEADERFRAME = "exp_h2stream_processHeaderFrame";
	public static final String EXP_H2STREAM_PROCESSCONTINUATIONFRAME = "exp_h2stream_processContinuationFrame";
	public static final String EXP_H2STREAM_PROCESSDATAFRAME = "exp_h2stream_processDataFrame";
	public static final String EXP_H2STREAM_GETINDEXEDENCODEDHEADER = "exp_h2stream_getIndexedEncodedHeader";
	public static final String EXP_H2STREAM_PROCESSCOMPLETEDHEADER = "exp_h2stream_processCompletedHeader";
	public static final String EXP_H2STREAM_GETRELATIVEPATH = "exp_h2stream_getRelativePath";
	public static final String EXP_H2STREAM_UPDATEPARAMMAP = "exp_h2stream_updateParamMap";
	public static final String EXP_H2STREAM_GETPARAMSTRING = "exp_h2stream_getParamString";
	public static final String EXP_H2STREAM_GETSERVLETURI = "exp_h2stream_getServletURI";
	public static final String EXP_H2RES_PUSHRESPONSEDATA = "exp_h2res_pushResponseData";
	public static final String EXP_H2RES_SENDFILE = "exp_h2res_sendFile";
	public static final String EXP_H2RES_COMMITRESPONSEHEADER = "exp_h2res_commitResponseHeader";
	public static final String EXP_H2RES_WRITEMOBILECONTENT = "exp_h2res_writeMobileContent";
	public static final String EXP_H2RES_SENDOPTIONSRESPONSE = "exp_h2res_sendOptionsResponse";
	public static final String EXP_H2RES_CLOSE = "exp_h2res_close";
	public static final String EXP_H2STATSUPDATER_UPDATEHTTP2STATS = "exp_h2statsupdater_updateHttp2Stats";
	public static final String EXP_H2STATSUPDATER_RESETSOCKETTIMETAKENSTATS = "exp_h2statsupdater_resetSocketTimeTakenStats";


	public static final String HTTP2_CONN_ID = "http2ConnID";
	public static final String HTTP2_STREAM_ID = "http2StreamID";
}
