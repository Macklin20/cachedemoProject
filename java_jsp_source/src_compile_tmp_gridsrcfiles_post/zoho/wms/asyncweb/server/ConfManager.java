//$Id$
package com.zoho.wms.asyncweb.server;

//Java import
import java.util.Properties;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.Collections;
import java.util.Arrays;

import java.io.File;
import java.io.FileInputStream;

import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.Authenticator;

// Wms import
import com.zoho.wms.asyncweb.server.http2.Http2StatsUpdater;
import com.zoho.wms.asyncweb.server.stats.MIAdapter;
import com.zoho.wms.asyncweb.server.util.CommonIamUtil;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.ssl.SSLStartUpTypes;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.monitor.DeadlockMonitor;
import com.zoho.wms.asyncweb.server.http.DefaultServlet;
import com.zoho.wms.asyncweb.server.http2.Http2Constants;

// Common import
import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.common.HttpConnection;

// servercommon import
import com.adventnet.wms.servercommon.ServerUtil;
import com.adventnet.wms.servercommon.WmsUIDGenerator;
import com.adventnet.wms.servercommon.dc.DC;

// iam imports
import com.adventnet.iam.security.RuleSetParser;
import com.adventnet.iam.security.SecurityFilterProperties;
import com.zoho.accounts.AccountsConstants;
import com.zoho.resource.RESTProperties;

import java.io.InputStream;
import java.io.OutputStream;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.CertificateException;		
import java.security.cert.X509Certificate;

import com.zoho.wms.asyncweb.server.runtime.WmsRuntimeCounters;
import com.zoho.wms.asyncweb.server.ssl.SSLManagerFactory;
import com.zoho.wms.asyncweb.server.stats.RuntimeStatsListener;

import com.adventnet.wms.servercommon.runtime.RuntimeAdmin;
import com.adventnet.wms.servercommon.runtime.WmsRuntime;

public class ConfManager
{
	private static AsyncLogger logger = new AsyncLogger(ConfManager.class.getName());

	private static String serverHome = null;
	private static String securityContextHome = null;
	private static String dataMonitorHome = System.getProperty("user.home")+File.separator+"monitoring";//No I18N

	static
	{
		serverHome = System.getProperty("server.home");
		securityContextHome = System.getProperty("awssecurity.home");
		dataMonitorHome = System.getProperty("datamonitor.home",dataMonitorHome);
	}
	
	private static String serverConfFile = null;
	private static String proxyConfFile = null;
	private static String blogFile = null;
	private static String domainMap = null;
	private static String portMap = null;
	private static String engineMapFile = null;
	private static String sslConfFile = null;
	private static String iamserviceConfFile = null;
	private static String iampropertyFile = null;
	private static String iamprivateKeyFile = null;
	private static String miXMLFile = null;
	private static String miConfFile = null;
	private static String extension = "";

	private static long serverStartTime = 0;	
	private static boolean serverStartStatus = false;
	private static int webServerPort = 80;
	private static int gridPort = -1;
	private static int reqProcThread = 10;
	private static int maxReqProcThread = 10;
	private static long reqProcKATime = 100L;
	private static int reqProcExecutor = 1;
	private static int reqProcQueueSize = -1;
	private static int reqProcMaxThreadCreationLimit = -1;
	private static boolean enableReqProcRejectedExecutionTask = false;
	private static int wsWriteAckProc = 10;
	private static int maxWSWriteAckProc = 10;
	private static long wsWriteAckProcKATime = 100L;
	private static int wsWriteAckProcExecutor = 1;
	private static int wsWriteAckProcQueueSize = -1;
	private static int wsWriteAckProcMaxThreadCreationLimit = -1;
	private static boolean enableWsWriteAckProcRejectedExecutionTask = false;
	private static int bindretryattempt = 3;
	private static String wnetAddress="localhost:7070";
	private static long soTimeout = 5*60000L;
	private static int nativeSoTimeout = 10*1000;// 10 sec
	private static String sslMode=AWSConstants.DEFAULT;
	private static String iamserverurl=null;
	private static String servicename="ZohoChat";
	private static String localip=AWSConstants.LOCALHOST;
	private static int connectors = 1;
	private static boolean servercheck = true;
	private static String blog = "Unknown";
	private static int httpreadselectorcnt = 0;
	private static int httpsreadselectorcnt = 0;
	private static int backlog = 100;
	private static long requesttrackerinterval = 30000;
	private static long wsrequesttrackerinterval = 30000;
	private static boolean initiamprivatekey = false;
	private static String clientipheader = "lb_ssl_remote_ip";
	private static String lblsslips = null;
	private static CopyOnWriteArrayList<String> lblsslipslist = null;
	private static CopyOnWriteArrayList<String> lblsslipsrangelist = null;
	private static boolean replacelbips = false;
	private static String chunkedstreamtimeoutheader = "x-chunkedtimeout";
	private static Hashtable<String,Integer> sslportmap = null;
	private static int netdataprocessor = 10;
	private static int maxnetdataprocessor = 10;
	private static long netdataKATime = 100L;
	private static int netdataExecutor = 1;
	private static int netdataQueueSize = -1;
	private static int netDataMaxThreadCreationLimit = -1;
	private static boolean enableNetdataRejectedExecutionTask = false;
	private static String streammodeheader = "x-streammode";//No I18N
	private static int sendbufsize = -1;
	private static int receivebufsize = -1;
	private static int writelimit = 1*1024*1024;
	//private static boolean dataflownotification = false; 
	private static long requestmaxidletime = 5*60*1000;
	private static boolean reqheartbeatmonitor = false;
	private static boolean writeheartbeat = true;
	private static long ignorehbperiod = 1000;
	private static int keyactiveseltimeout = 100;
	private static int workaroundpause = 0;
	private static ArrayList validdomain = new ArrayList();
	private static int read_limit = 24*1024;
	private static int req_payload_size = 0;
	private static boolean msgretryenabled = false;
	private static boolean adaptermode = false;
	private static boolean wsprdlistfactoryenabled = false;
	private static ArrayList supportedwsversion = null;
	private static boolean nopforwardtoengine = false;
	private static int writecapacity = 8192;
	private static int max_outbb_size = 1*1024*1024;
	private static int maxdataperwsread = 1*1024*1024;
	private static long wsrequesttimeout = 0l;
	private static boolean dosmonitor = false;
	private static long dosscavengetime = 30*60*1000;
	private static long dostimeout = 5*60*1000;
	private static int dosmonitorthreshold = 1000;
	private static int sslcontextsize = 10;
	private static boolean runtimemonitoring = false;
	private static int runtimeinterval = 1;
	private static boolean isWMSRuntimePropsLoaded = false;
	private static boolean isAWSRuntimePropsLoaded = false;
	private static boolean disableWMSRuntime = false;
	private static boolean proxyprotocol = false;
	private static boolean anomalymailingenabled = false;
	private static boolean anomalydetection = false;
	private static boolean recordanomalydata = false;
	private static int anomalymonitorinterval = 5; 
	private static int anomalydetectionthreshold = 2;
	private static long anomalyhitratethreshold = 50000l; 
	private static long anomalybwratethreshold = 10000000l; //10 mb
	private static long anomalymemthreshold = 10000000000l; //10 gb
	private static int anomalybehaviouranalysisperiod=15;
	private static int iterationperanomaly = 6;
	private static int anomalyscheduleinterval = 24*60;//1 day in minutes
	private static boolean anomalyscheduling = false;
	private static String creatormail_to = "mariaraj@zohocorp.com,koveanthan.pon@zohocorp.com";//no i18n
	private static String creatormail_from = "koveanthan.pon@zohocorp.com";//no i18n
	private static String statsreceivers = "mariaraj@zohocorp.com,koveanthan.pon@zohocorp.com,karthickkumar.r@zohocorp.com";//no i18n
	private static String pmstatsreceivers = "mariaraj@zohocorp.com,koveanthan.pon@zohocorp.com,karthickkumar.r@zohocorp.com";//no i18n
	private static String refererpattern = null;// .*\\.zoho\\.com$
	private static ArrayList supportedpostmethods = new ArrayList();
	private static ArrayList supportedgetmethods = new ArrayList();
	private static ArrayList accessheaders = new ArrayList();
	private static ArrayList accessresponseheaders = new ArrayList();
	private static ArrayList accessrestrictedparams = new ArrayList();
	private static long neteventthreshold = 100;
	private static boolean neteventtrace = false;
	private static boolean headercompletion = false;
	private static boolean websocketcompression = false;
	private static boolean datastats = false;
	private static boolean datamonitor = false;
	private static long datamonitortimewait = 10000;
	private static long datastatstimewait = 3000;
	private static boolean closereaddebug = false;
	private static boolean throwbindexp = true;
	private static boolean printincompleterequest = false;

	private static HashSet<String> webServerPortList = new HashSet<String>();
	private static boolean reuseconnection = true;

	private static Properties mappedDomain;
	private static Properties engineMap;
	private static Properties serverConf;
	private static Properties proxyConf;
	private static Properties sslConf;
	private static String enabledTLSVersions = "TLSv1.2,TLSv1.3";
	private static ArrayList enabledTLSVersionList = null;
	private static ArrayList<String> securityFilesInfo = new ArrayList();
	private static String securityfiles = "security-properties.xml,security-privatekey.xml";//No I18N
	private static String enabledCipherSuites = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256,TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384";//No I18N 
	private static ArrayList enabledCipherSuiteList = null;

	private static boolean enablecipherorder = true;

	private static String iamsecurityservicename = "ZohoChat";
	private static boolean securityfilter = false;
	private static boolean binarywsenabled = false;
	private static boolean readonly = false;
	private static boolean keepalive = false;
	private static boolean sts = true;
	private static long stsmaxlifetime = 15768000;
	private static int maxZeroReadCount = 100;

	private static boolean enablemi = true;
	private static MIAdapter miadapter = null;
	private static ArrayList miexcludedurls = new ArrayList();
	private static ArrayList securityexcludedurls = new ArrayList();

	private static boolean serverportmonitor = false;
	private static int serverportmonitorinterval = 30;
	private static int serverportmonitorthreshold = 30;
	private static int serverportrequestmonitorinterval = 30;
	private static int spmconnecttimeout = 3000;
	private static int spmreadtimeout = 20000;
	private static ServerPortMonitor spm = null;
	private static final String HANDSHAKE_FAILURE_MSG = "Received fatal alert: handshake_failure"; //No I18n

	private static boolean wsservletthreadsafe = true;
	private static boolean wcpservletthreadsafe = true;
	
	private static boolean serverProxy = false;
	
	private static String primaryProxy;
	private static String secondaryProxy;
	private static int proxyPort = -1;
	private static int proxysbtime = 2;
	private static int proxyretryattempt = 2;
	private static ArrayList proxyexcludedomains = new ArrayList();
	private static ArrayList proxyenabledomains = new ArrayList();
	private static boolean restrictproxyaccess = false;
	private static ArrayList restrainedurls = new ArrayList();

	private static boolean conscrypt = false;
        private static int sslsessiontimeout = 600;
	private static boolean sslsessionresumption = false;
	private static boolean conscryptticketresumption = false;

	private static boolean initialized = false;

	private static String dcu = null;
	private static X509DynamicCertUpdater dcuimpl = null;
	private static HashSet<String> needClientAuth = new HashSet<String>();
	private static HashSet<String> wantClientAuth = new HashSet<String>();

	private static int maxheadersize = 2048;

	private static long wspingtimeout = -1;
	private static boolean discardpingres = false;
	
	private static String defaulturl = "index.html";//No I18n
	private static boolean wsoffloader = false;
	private static ArrayList debugips = new ArrayList();
	private static ArrayList printincompleterequestforips = new ArrayList();
	private static boolean isGridEngineActive = false;
	private static ArrayList accesslogexcludedurls = new ArrayList();
	private static boolean enablelogplogger = false;

	// anomaly handler
	private static boolean enableanomalyhandler = false;
	private static String anomalyhandler = null;
	private static AnomalyHandlerImpl anomalyhandlerimpl = null;
	private static int anomalyProcessorCount = 10;
	private static int anomalyMaxProcessorCount = 10;
	private static int anomalyMaxThreadCreationLimit = -1;

	private static DeadlockMonitor dlmonitor = null;
	private static boolean deadlockmonitor = false;
	private static long dlwaitinginterval = 5*60*1000;
	private static String dlalertmailreceivers = "";
	private static boolean webenginews = false;
	private static boolean qosstats = true;
	private static int influxStatsUpdateInterval = 60;// value in Seconds.
	private static ArrayList influxStatsExcludedUrls = new ArrayList();
	private static boolean disableInfluxStatsLog = true;
	private static String engineLoaderName;
	private static WebEngineLoader engineLoader;
	private static String defaultServletURL = "com.zoho.wms.asyncweb.server.http.DefaultServlet";//No I18n
	private static boolean enableTcpNoDelay = false;
	private static long keepalivetrackerinterval = 30000L;
	private static ArrayList miincludedwsurls = new ArrayList();
	private static long keepalivetime = 2*60*1000;
	private static int internalReadLimit = 50*1024;
	private static int internalWriteLimit = 2*read_limit;
	private static boolean useDefaultTrustManager = false;
	private static boolean compressionStatsEnabled = true;
	private static boolean exceptionlogs = true;
	private static boolean debuglogs = false;
	private static boolean accesslogs = true;
	private static String keystoreimpl = null;
	private static KeyStoreLoader keyStoreLoader = null;
	private static String responseCharset = AWSConstants.DEFAULT_CHARSET;
	private static int sslnetbuffersize = -1;
	private static int sslappbuffersize = -1;
	private static int wsdeflatebuffersize = 1024;
	private static int wsinflatebuffersize = 1024;
	private static boolean streamMode = false;
	private static boolean printinvalidwsparam = false;
	private static String runtimestatslistenerimpl;
	private static RuntimeStatsListener runtimestatslistener;

	private static boolean asyncFrameProc = false;

	private static String tlsALPNProtocols = "http/1.1";
	private static String[] tlsALPNProtocolList = null;
	private static boolean isTLSALPNEnabled = true;
	private static boolean http2Enabled = false;
	private static String http2ConfFile = null;
	private static Properties http2Conf = null;
	private static int maxAllowedClientStream = 1000; // i.e max no. of req a client a make using single HTTP2 Connection's lifetime
	private static int maxAllowedConcurrentClientStream = 100; // i.e max no. of concurrent req using single HTTP2 Connection
	private static int initialStreamLevelServerWindowSize = Http2Constants.DEFAULT_WINDOW_SIZE; // 64 kb
	private static int http2ConnectionTimeout = 2 * 60 * 1000; // 2 min
	private static int requestFrameMaxSize = Http2Constants.DEFAULT_FRAME_PAYLOAD_SIZE; // 16 kb
	private static int dynamicTableSize = Http2Constants.DEFAULT_HEADER_TABLE_SIZE;
	private static int streamTrackerInterval = 10 * 1000; // 10 sec
	private static int streamTimeout = 30 * 1000; // 30 sec
	private static int http2FrameProcessorCorePoolSize = 10;
	private static int http2FrameProcessorMaxPoolSize = 50;
	private static int http2FrameProcessorThreadCreationLimit = 100;
	private static boolean http2StatsUpdaterEnabled = true;
	private static boolean http2CounterStatsEnabled = true;
	private static boolean http2SocketTimeTakenStatsEnabled = true;
	private static boolean http2LogsEnabled = false;
	private static boolean http2FrameLogsEnabled = false;
	private static boolean http2HuffmanEncodingEnabled = true;
	private static int http2ReadDataBufferLimit = 1 * 1024 * 1024; // 1 MB
	private static int resetFrameLimitPercent = 20;
	private static int resetFrameLimit = 20;
	private static int continuationFrameLimit = 10;
	private static int streamErrorLimit = 10;

	private static int externalFPCount = 10;//FP - frameProcessor
	private static int externalMaxFPCount = 10;
	private static int externalFPMaxThreadCreationLimit = -1;
	private static long externalFPKATime = 100L;
	private static int externalFPExecutorCount = 1;
	private static int externalFPQueueSize = -1;
	private static boolean externalFPRejectionHandler = false;
	private static boolean illegalreqexception = true;
	private static boolean heavyDataStatsEnabled = true;
	private static boolean hackAttemptStatsEnabled = true;
	private static boolean useSasLogFormat = false;
	private static boolean enableEndAWSAccessLog = false;
	private static boolean enableCookieDebugLogs = false;
	private static boolean wmsserver=false;
	private static boolean writeDataInQueue = true;
	private static boolean cacheEnabled = true;
	private static boolean webenginenetdata = false;
	private static boolean serverheaderneeded = true;

	private static String defaultHttpContentType = "text/html; charset=ISO-8859-1";//No I18N

	public static void setServerHome(String srvhome)
	{
		serverHome = srvhome;
	}

	public static void setSecurityContextHome(String scxtHome)
	{
		securityContextHome = scxtHome;
	}

	public static boolean initialize(boolean mode) throws Exception
	{
		serverHome = (serverHome!=null) ?serverHome :"..";//No I18N
        System.out.println(serverHome);
		securityContextHome = (securityContextHome!=null) ?securityContextHome :serverHome+File.separator+"conf";//No I18N
		serverConfFile = serverHome+File.separator+"conf"+File.separator+"awsadapterconf.properties"+extension;//No I18N
		proxyConfFile = serverHome+File.separator+"conf"+File.separator+"proxyconf.properties";//No I18N
		blogFile = serverHome+File.separator+"blog"+File.separator+"version.txt";
		domainMap = serverHome+File.separator+"conf"+File.separator+"mappeddomain.properties"+extension;
		portMap = serverHome+File.separator+"conf"+File.separator+"portengine.properties";
		engineMapFile = serverHome+File.separator+"conf"+File.separator+"webengine.properties";
		sslConfFile = serverHome+File.separator+"conf"+File.separator+"sslservers.properties";
		miConfFile = serverHome+File.separator+"conf"+File.separator+"miconf.properties";//No I18N
		miXMLFile = serverHome+File.separator+"conf"+File.separator+"instrument-configuration.xml";
		if(!WmsUIDGenerator.initialize(100,2))
		{
			logger.severe("Unable to start the server WmsUIDGenerator initialization failed",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE);//No I18N	
		}
		adaptermode = mode;
		if(adaptermode)
		{
			return initializeAdapterMode();
		}
		else
		{
			logger.log(Level.INFO,"INITIALIZATION POSSIBLE ONLY IN ADAPTER MODE. PLEASE CHECK.",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE);
		}	
		return false;
	}

	public static boolean initializeAdapterMode() throws Exception
	{
		serverConf = getProperties(serverConfFile);
		sslConf = getProperties(sslConfFile);
		engineMap = getProperties(engineMapFile);
		mappedDomain = getProperties(domainMap);
		proxyConf = getProperties(proxyConfFile);
		
		if(serverConf==null) return false;
		Properties blogConf = getProperties(blogFile);


		webServerPort = Integer.parseInt(serverConf.getProperty("webserver.port","-1"));
		if(webServerPort != -1)
		{
			webServerPortList.add(""+webServerPort);
		}
		gridPort = Integer.parseInt(serverConf.getProperty("webserver.gridport",""+gridPort));//No I18N
		backlog = Integer.parseInt(serverConf.getProperty("backlog", ""+backlog));
		sslMode = serverConf.getProperty("sslmode");
		wnetAddress = serverConf.getProperty("wnetaddress");//No I18N
		dosmonitor = Boolean.valueOf(serverConf.getProperty("dosmonitor",String.valueOf(dosmonitor)));
		dosscavengetime = Long.parseLong(serverConf.getProperty("dosscavengetime",""+dosscavengetime));
		dostimeout = Long.parseLong(serverConf.getProperty("dostimeout",""+dostimeout));
		dosmonitorthreshold = Integer.parseInt(serverConf.getProperty("dosmonitorthreshold",""+dosmonitorthreshold));
		serverportmonitor = Boolean.valueOf(serverConf.getProperty("serverportmonitor",String.valueOf(serverportmonitor)));//No I18N
		serverportmonitorinterval = Integer.parseInt(serverConf.getProperty("serverportmonitorinterval",String.valueOf(serverportmonitorinterval)));//No I18N
		serverportmonitorthreshold = Integer.parseInt(serverConf.getProperty("serverportmonitorthreshold",String.valueOf(serverportmonitorthreshold)));//No I18N
		serverportrequestmonitorinterval = Integer.parseInt(serverConf.getProperty("serverportrequestmonitorinterval",String.valueOf(serverportrequestmonitorinterval)));//No I18N
		spmconnecttimeout = Integer.parseInt(serverConf.getProperty("spmconnecttimeout",""+spmconnecttimeout));//No I18N
		spmreadtimeout = Integer.parseInt(serverConf.getProperty("spmreadtimeout",""+spmreadtimeout));//No I18N
		keepalive = Boolean.parseBoolean(serverConf.getProperty(AWSConstants.KEEPALIVE,""+keepalive));//No I18N
		sts = Boolean.parseBoolean(serverConf.getProperty("sts",""+sts));
		stsmaxlifetime = Long.parseLong(serverConf.getProperty("stsmaxlifetime",""+stsmaxlifetime));
		proxyprotocol = Boolean.parseBoolean(serverConf.getProperty("proxyprotocol",""+proxyprotocol));//No I18N
		maxheadersize = Integer.parseInt(serverConf.getProperty("maxheadersize",""+maxheadersize));
		wsoffloader = Boolean.parseBoolean(serverConf.getProperty("wsoffloader", ""+wsoffloader));
		enablelogplogger = Boolean.parseBoolean(serverConf.getProperty("enablelogplogger", ""+enablelogplogger));
		illegalreqexception = Boolean.parseBoolean(serverConf.getProperty("illegalreqexception", ""+illegalreqexception));

		defaulturl = serverConf.getProperty("defaulturl", defaulturl);//No I18N
		maxZeroReadCount = Integer.parseInt(serverConf.getProperty("maxzeroreadcount",""+maxZeroReadCount));//No I18N

		discardpingres = Boolean.parseBoolean(serverConf.getProperty("discardpingres",""+discardpingres));
		printinvalidwsparam = Boolean.parseBoolean(serverConf.getProperty("printinvalidwsparam",""+printinvalidwsparam));

		if(proxyprotocol)
		{
			logger.log(Level.INFO,"PROXY PROTOCOL ENABLED",ConfManager.class.getName(),"initializeAdapterMode");
		}

		try
		{
			if(wnetAddress==null)
			{
				wnetAddress = getIPAddress()+":"+gridPort;
				logger.log(Level.INFO,"setting wnetAddress as "+wnetAddress,ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);//No I18N
			}
		}
		catch(Exception exp)
		{
			logger.log(Level.INFO, " Set WNET Address Error ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, exp);
		}


		try
		{
			blog = blogConf.getProperty("BuildLabel", blog);
		}catch(Exception e)
		{	
			logger.log(Level.FINE,"No blog",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);//No I18N
		}

		soTimeout = Long.parseLong(serverConf.getProperty("sotimeout",""+soTimeout));
		nativeSoTimeout = Integer.parseInt(serverConf.getProperty("nativesotimeout",""+nativeSoTimeout));
		clientipheader = serverConf.getProperty("clientipheader", clientipheader);
		lblsslips = serverConf.getProperty("lblsslips");
		replacelbips = Boolean.parseBoolean(serverConf.getProperty("replacelbips",""+replacelbips));

		try
		{
			loadLBLSSLIPs();
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"Error loading LBLSSIPs : ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE,ex);
		}

		connectors = Integer.parseInt(serverConf.getProperty("connectors", ""+connectors));

		try{
			InetAddress localaddr = InetAddress.getLocalHost();
			localip = localaddr.getHostAddress();
		}catch(Exception ee)
		{
			logger.log(Level.FINE,"Exception in localaddr formation ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE,ee);//No I18N
		}
		keyactiveseltimeout = Integer.parseInt(serverConf.getProperty("keyactiveselecttimeout", ""+keyactiveseltimeout));
		workaroundpause = Integer.parseInt(serverConf.getProperty("selectorpause",""+workaroundpause));
		httpreadselectorcnt = Integer.parseInt(serverConf.getProperty("httpreadselector", ""+httpreadselectorcnt));
		httpsreadselectorcnt = Integer.parseInt(serverConf.getProperty("httpsreadselector", ""+httpsreadselectorcnt));
		reqProcThread = Integer.parseInt(serverConf.getProperty("requestprocessorcount", ""+reqProcThread));
		maxReqProcThread = Integer.parseInt(serverConf.getProperty("maxrequestprocessorcount", ""+maxReqProcThread));
		sendbufsize = Integer.parseInt(serverConf.getProperty("sendbuffersize", ""+sendbufsize));
		receivebufsize = Integer.parseInt(serverConf.getProperty("receivebuffersize", ""+receivebufsize));
		reqProcKATime = Long.parseLong(serverConf.getProperty("requestprocessorkeepalivetime",""+reqProcKATime));
		reqProcExecutor = Integer.parseInt(serverConf.getProperty("requestprocessorexecutor",""+reqProcExecutor));
		reqProcQueueSize = Integer.parseInt(serverConf.getProperty("requestprocessorqueuesize",""+reqProcQueueSize));
		reqProcMaxThreadCreationLimit = Integer.parseInt(serverConf.getProperty("requestprocessormaxthreadcreationlimit",""+reqProcMaxThreadCreationLimit));
		enableReqProcRejectedExecutionTask = Boolean.parseBoolean(serverConf.getProperty("requestprocessorrejectedexecutiontask",""+enableReqProcRejectedExecutionTask));
		wsWriteAckProc = Integer.parseInt(serverConf.getProperty("wswriteackproc",""+wsWriteAckProc));
		maxWSWriteAckProc = Integer.parseInt(serverConf.getProperty("maxwswriteackproc", ""+maxWSWriteAckProc));
		wsWriteAckProcKATime = Long.parseLong(serverConf.getProperty("wswriteackprockeepalivetime",""+wsWriteAckProcKATime));
		wsWriteAckProcExecutor = Integer.parseInt(serverConf.getProperty("wswriteackprocexecutor",""+wsWriteAckProcExecutor));
		wsWriteAckProcQueueSize = Integer.parseInt(serverConf.getProperty("wswriteackprocqueuesize",""+wsWriteAckProcQueueSize));
		wsWriteAckProcMaxThreadCreationLimit = Integer.parseInt(serverConf.getProperty("wswriteackprocmaxthreadcreationlimit",""+wsWriteAckProcMaxThreadCreationLimit));
		enableWsWriteAckProcRejectedExecutionTask = Boolean.parseBoolean(serverConf.getProperty("wswriteackprocrejectedexecutiontask",""+enableWsWriteAckProcRejectedExecutionTask));
		supportedwsversion = CommonUtil.getList(serverConf.getProperty("wsversions","13"));	
		nopforwardtoengine = Boolean.parseBoolean(serverConf.getProperty("nopforwardtoengine",""+nopforwardtoengine));
		defaultHttpContentType = serverConf.getProperty("defaulthttpcontenttype",defaultHttpContentType);
		requesttrackerinterval = Long.parseLong(serverConf.getProperty("requesttrackerinterval", ""+requesttrackerinterval));

		http2Enabled = Boolean.parseBoolean(serverConf.getProperty("http2enabled",""+http2Enabled));
		try
		{
			if(http2Enabled)
			{
				tlsALPNProtocols = "h2,http/1.1";

				http2ConfFile = serverHome+File.separator+"conf"+File.separator+"http2conf.properties";
				http2Conf = getProperties(http2ConfFile);
				if(http2Conf == null)
				{
					http2Conf = new Properties();
				}

				maxAllowedClientStream = Integer.parseInt(http2Conf.getProperty("maxallowedclientstream",""+maxAllowedClientStream));
				maxAllowedConcurrentClientStream = Integer.parseInt(http2Conf.getProperty("maxallowedconcurrentclientstream",""+maxAllowedConcurrentClientStream));
				http2ConnectionTimeout = Integer.parseInt(http2Conf.getProperty("http2connectiontimeout",""+http2ConnectionTimeout));
				requestFrameMaxSize = Integer.parseInt(http2Conf.getProperty("requestframemaxsize",""+requestFrameMaxSize));
				dynamicTableSize = Integer.parseInt(http2Conf.getProperty("dynamictablesize",""+dynamicTableSize));
				initialStreamLevelServerWindowSize = Integer.parseInt(http2Conf.getProperty("initialstreamlevelserverwindowsize",""+ initialStreamLevelServerWindowSize));
				streamTrackerInterval = Integer.parseInt(http2Conf.getProperty("streamtrackerinterval",""+streamTrackerInterval));
				streamTimeout = Integer.parseInt(http2Conf.getProperty("streamtimeout",""+streamTimeout));
				http2FrameProcessorCorePoolSize = Integer.parseInt(http2Conf.getProperty("http2frameprocessorcorepoolsize",""+http2FrameProcessorCorePoolSize));
				http2FrameProcessorMaxPoolSize = Integer.parseInt(http2Conf.getProperty("http2frameprocessormaxpoolsize",""+http2FrameProcessorMaxPoolSize));
				http2FrameProcessorThreadCreationLimit = Integer.parseInt(http2Conf.getProperty("http2frameprocessorthreadcreationlimit",""+http2FrameProcessorThreadCreationLimit));
				http2StatsUpdaterEnabled = Boolean.parseBoolean(http2Conf.getProperty("http2StatsUpdaterEnabled",""+http2StatsUpdaterEnabled));
				if(http2StatsUpdaterEnabled)
				{
					Http2StatsUpdater.initialize();
				}
				else
				{
					Http2StatsUpdater.stopThread();
				}
				http2CounterStatsEnabled = Boolean.parseBoolean(http2Conf.getProperty("http2counterstatsenabled",""+http2CounterStatsEnabled));
				http2SocketTimeTakenStatsEnabled = Boolean.parseBoolean(http2Conf.getProperty("http2sockettimetakenstatsenabled",""+http2SocketTimeTakenStatsEnabled));
				http2LogsEnabled = Boolean.parseBoolean(http2Conf.getProperty("http2logsenabled", ""+http2LogsEnabled));
				http2FrameLogsEnabled = Boolean.parseBoolean(http2Conf.getProperty("http2framelogsenabled", ""+http2FrameLogsEnabled));
				http2HuffmanEncodingEnabled = Boolean.parseBoolean(http2Conf.getProperty("http2huffmanencodingenabled", ""+http2HuffmanEncodingEnabled));
				http2ReadDataBufferLimit = Integer.parseInt(http2Conf.getProperty("http2readdatabufferlimit",""+http2ReadDataBufferLimit));
				resetFrameLimit = Integer.parseInt(http2Conf.getProperty("resetframelimit",""+resetFrameLimit));
				resetFrameLimitPercent = Integer.parseInt(http2Conf.getProperty("resetframelimitpercent",""+resetFrameLimitPercent));
				continuationFrameLimit = Integer.parseInt(http2Conf.getProperty("continuationframelimit",""+continuationFrameLimit));
				streamErrorLimit = Integer.parseInt(http2Conf.getProperty("streamerrorlimit",""+streamErrorLimit));
				// protocol specific changes
				initialStreamLevelServerWindowSize = Math.max(initialStreamLevelServerWindowSize, Http2Constants.DEFAULT_WINDOW_SIZE); // minimum value is 65535

			}
			else
			{
				tlsALPNProtocols = "http/1.1";
			}
			logger.log(Level.INFO ,"ConfManager - Http2 Enabled:"+http2Enabled);
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE ,"ConfManager - Http2 Error", ex);
		}
		tlsALPNProtocolList = (serverConf.getProperty("tlsalpnprotocols",""+tlsALPNProtocols)).split(",");
		isTLSALPNEnabled = Boolean.parseBoolean(serverConf.getProperty("istlsalpnenabled",""+isTLSALPNEnabled));

		asyncFrameProc = Boolean.parseBoolean(serverConf.getProperty(AWSConstants.ASYNCFRAMEPROC,""+asyncFrameProc));
		externalFPCount = Integer.parseInt(serverConf.getProperty("externalfpcount",""+externalFPCount));
		externalMaxFPCount = Integer.parseInt(serverConf.getProperty("externalmaxfpcount",""+externalMaxFPCount));
		externalFPMaxThreadCreationLimit = Integer.parseInt(serverConf.getProperty("externalfpmaxthreadcreationlimit",""+externalFPMaxThreadCreationLimit));
		externalFPKATime = Long.parseLong(serverConf.getProperty("externalfpkatime",""+externalFPKATime));
		externalFPExecutorCount = Integer.parseInt(serverConf.getProperty("externalfpexecutorcount",""+externalFPExecutorCount));
		externalFPQueueSize = Integer.parseInt(serverConf.getProperty("externalfpqueuesize",""+externalFPQueueSize));
		externalFPRejectionHandler = Boolean.parseBoolean(serverConf.getProperty("externalfprejectionhandler",""+externalFPRejectionHandler));

		wsrequesttrackerinterval = Long.parseLong(serverConf.getProperty("wsrequesttrackerinterval",""+wsrequesttrackerinterval));
		keepalivetrackerinterval = Long.parseLong(serverConf.getProperty("keepalivetrackerinterval",""+keepalivetrackerinterval));
		streammodeheader = serverConf.getProperty("streammodeheader",""+streammodeheader);
		try
		{
			sslportmap= loadSSLPortMap();
		}
		catch(Exception ex)
		{
			logger.log(Level.FINE,"No sslportmap",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);//No I18N
		}

		netdataprocessor = Integer.parseInt(serverConf.getProperty("netdataprocessor",""+netdataprocessor));
		maxnetdataprocessor = Integer.parseInt(serverConf.getProperty("maxnetdataprocessor",""+maxnetdataprocessor));
		netdataKATime = Long.parseLong(serverConf.getProperty("netdataprocessorkeepalivetime",""+netdataKATime));
		netdataExecutor = Integer.parseInt(serverConf.getProperty("netdataprocessorexecutor",""+netdataExecutor));
		netdataQueueSize = Integer.parseInt(serverConf.getProperty("netdataprocessorqueuesize",""+netdataQueueSize));
		netDataMaxThreadCreationLimit = Integer.parseInt(serverConf.getProperty("netdatamaxthreadcreationlimit",""+netDataMaxThreadCreationLimit));
		enableNetdataRejectedExecutionTask = Boolean.parseBoolean(serverConf.getProperty("netdataprocessorrejectedexecutiontask",""+enableNetdataRejectedExecutionTask));

		wsprdlistfactoryenabled = Boolean.parseBoolean(serverConf.getProperty("wsprdlistfactoryenabled", ""+wsprdlistfactoryenabled));
		requestmaxidletime = Long.parseLong(serverConf.getProperty("requestmaxidletime", ""+requestmaxidletime));
		writelimit = Integer.parseInt(serverConf.getProperty("writelimit", ""+writelimit));
		//dataflownotification = Boolean.parseBoolean(serverConf.getProperty("dataflownotification", ""+dataflownotification));
		msgretryenabled = Boolean.parseBoolean(serverConf.getProperty("msgretry", ""+msgretryenabled));
		reqheartbeatmonitor = Boolean.parseBoolean(serverConf.getProperty("reqheartbeatmonitor", ""+reqheartbeatmonitor));
		writeheartbeat = Boolean.parseBoolean(serverConf.getProperty("writeheartbeat",""+writeheartbeat));
		ignorehbperiod = Long.parseLong(serverConf.getProperty("ignorehbperiod",""+ignorehbperiod));
		read_limit = Integer.parseInt(serverConf.getProperty("readlimit", ""+read_limit));
		req_payload_size = Integer.parseInt(serverConf.getProperty("reqpayloadsize",""+req_payload_size));
		serverheaderneeded = Boolean.parseBoolean(serverConf.getProperty("serverheaderneeded", ""+serverheaderneeded));
		if(serverConf.getProperty("headerlimit") != null)
		{
			read_limit = Integer.parseInt(serverConf.getProperty("headerlimit", ""+read_limit));
		}

		validdomain = CommonUtil.getList(serverConf.getProperty("serverdomain", ""+validdomain));
		writecapacity = Integer.parseInt(serverConf.getProperty("writecapacity",""+writecapacity));
		max_outbb_size = Integer.parseInt(serverConf.getProperty("maxdataperwrite",""+max_outbb_size));
		maxdataperwsread = Integer.parseInt(serverConf.getProperty("maxdataperwsread",""+maxdataperwsread));
		wsrequesttimeout = (Long.parseLong(serverConf.getProperty("wsrequesttimeout",""+wsrequesttimeout))*60*1000);
		sslcontextsize = Integer.parseInt(serverConf.getProperty("sslcontextsize",""+sslcontextsize));
		runtimemonitoring = Boolean.parseBoolean(serverConf.getProperty("runtime",""+runtimemonitoring));
		runtimeinterval = Integer.parseInt(serverConf.getProperty("runtimeinterval",""+runtimeinterval));
		disableWMSRuntime = Boolean.parseBoolean(serverConf.getProperty("disablewmsruntime",""+disableWMSRuntime));
		refererpattern = serverConf.getProperty("refererpattern");
		supportedpostmethods = CommonUtil.getList(serverConf.getProperty("supportedpostmethods",""));   
		supportedgetmethods = CommonUtil.getList(serverConf.getProperty("supportedgetmethods",""));
		restrainedurls = CommonUtil.getList(serverConf.getProperty("restrainedurls","gridopr"));//No I18N
		accessheaders = CommonUtil.getList(serverConf.getProperty("accessheaders",""));
		accessresponseheaders = CommonUtil.getList(serverConf.getProperty("accessresponseheaders",""));
		accessrestrictedparams = CommonUtil.getList(serverConf.getProperty("accessrestrictedparams","iscsignature"));//No I18N
		reuseconnection = Boolean.parseBoolean(serverConf.getProperty("reuseconnection",""+reuseconnection));
		securityFilesInfo = CommonUtil.getList(serverConf.getProperty("securityfiles",""+securityfiles));
		iamserviceConfFile = getSecurityFile("iamservices.properties");//No I18N
		iampropertyFile = getSecurityFile("security-properties.xml");//No I18N
		iamprivateKeyFile = getSecurityFile("security-privatekey.xml");//No I18N
		initiamprivatekey = Boolean.parseBoolean(serverConf.getProperty("initiamprivatekey",""+initiamprivatekey));
		iamsecurityservicename = serverConf.getProperty("iamsecurityservicename",""+iamsecurityservicename);
		servicename = serverConf.getProperty("servicename",""+servicename);
		if(serverConf.getProperty("iamserverurl") != null && !serverConf.getProperty("iamserverurl").equals(""))
		{
			iamserverurl = serverConf.getProperty("iamserverurl");
			CommonIamUtil.setIamProxy(iamserverurl);	
		}

		if(initiamprivatekey)
		{
			initializeRestProperties();
			initIAMSecurityPrivateKey();	
		}

		enabledTLSVersionList = CommonUtil.getList(serverConf.getProperty("enabledtlsversions",""+enabledTLSVersions));
		enabledCipherSuiteList = CommonUtil.getList(serverConf.getProperty("enabledciphersuites",""+enabledCipherSuites));
		enablecipherorder = Boolean.parseBoolean(serverConf.getProperty("enablecipherorder",""+enablecipherorder));
		binarywsenabled = Boolean.parseBoolean(serverConf.getProperty("binarywsenabled",""+binarywsenabled));
		headercompletion = Boolean.parseBoolean(serverConf.getProperty("headercompletion",""+headercompletion));
		enablemi = Boolean.parseBoolean(serverConf.getProperty("enablemi",""+enablemi));
		miexcludedurls = CommonUtil.getList(serverConf.getProperty("miexcludedurls",""));
		if(!miexcludedurls.contains("grid/login/servercheck.jsp"))
		{
			miexcludedurls.add("grid/login/servercheck.jsp");//No I18n
		}
		securityexcludedurls = CommonUtil.getList(serverConf.getProperty("securityexcludedurls",""));
		anomalydetectionthreshold = Integer.parseInt(serverConf.getProperty("anomalydetectionthreshold",""+anomalydetectionthreshold));
		anomalyhitratethreshold = Long.parseLong(serverConf.getProperty("anomalyhitratethreshold",""+anomalyhitratethreshold));
		anomalybwratethreshold = Long.parseLong(serverConf.getProperty("anomalybwratethreshold",""+anomalybwratethreshold));
		anomalymemthreshold = Long.parseLong(serverConf.getProperty("anomalymemthreshold",""+anomalymemthreshold));
		anomalybehaviouranalysisperiod = Integer.parseInt(serverConf.getProperty("anomalybehaviouranalysisperiod",""+anomalybehaviouranalysisperiod));
		anomalydetection = Boolean.parseBoolean(serverConf.getProperty("anomalydetection",""+anomalydetection));
		anomalymailingenabled = Boolean.parseBoolean(serverConf.getProperty("anomalymailingenabled",""+anomalymailingenabled));
		anomalymonitorinterval = Integer.parseInt(serverConf.getProperty("anomalymonitorinterval",""+anomalymonitorinterval));
		anomalyscheduleinterval = Integer.parseInt(serverConf.getProperty("anomalyscheduleinterval",""+anomalyscheduleinterval));
		anomalyscheduling = Boolean.parseBoolean(serverConf.getProperty("anomalyscheduling",""+anomalyscheduling));
		datastats = Boolean.parseBoolean(serverConf.getProperty("datastats",""+datastats));
		datamonitor = Boolean.parseBoolean(serverConf.getProperty("datamonitor",""+datamonitor));
		datamonitortimewait = Long.parseLong(serverConf.getProperty("datamonitortimewait",""+datamonitortimewait));
		datastatstimewait = Long.parseLong(serverConf.getProperty("datastatstimewait",""+datastatstimewait));
		iterationperanomaly = Integer.parseInt(serverConf.getProperty("iterationperanomaly",""+iterationperanomaly));
		creatormail_from = serverConf.getProperty("creatormail_from",""+creatormail_from);
		creatormail_to = serverConf.getProperty("creatormail_to",""+creatormail_to);
		statsreceivers = serverConf.getProperty("statsreceivers",""+statsreceivers);
		pmstatsreceivers = serverConf.getProperty("pmstatsreceivers",""+pmstatsreceivers);
		recordanomalydata = Boolean.parseBoolean(serverConf.getProperty("recordanomalydata",""+recordanomalydata));
		bindretryattempt = Integer.parseInt(serverConf.getProperty("bindretryattempt",""+bindretryattempt));
		neteventthreshold = Long.parseLong(serverConf.getProperty("neteventthreshold",""+neteventthreshold));
		neteventtrace = Boolean.parseBoolean(serverConf.getProperty("neteventtrace",""+neteventtrace));
		websocketcompression = Boolean.parseBoolean(serverConf.getProperty("websocketcompression",""+websocketcompression));
		securityfilter = Boolean.parseBoolean(serverConf.getProperty(AWSConstants.SECURITYFILTER,""+securityfilter));
		wsservletthreadsafe = Boolean.parseBoolean(serverConf.getProperty("wsservletthreadsafe",""+wsservletthreadsafe));
		wcpservletthreadsafe = Boolean.parseBoolean(serverConf.getProperty("wcpservletthreadsafe",""+wcpservletthreadsafe));
		serverProxy = Boolean.parseBoolean(serverConf.getProperty("serverproxy", ""+serverProxy));
		sslsessiontimeout =Integer.parseInt(serverConf.getProperty("sslsessiontimeout",""+sslsessiontimeout));
		sslsessionresumption = Boolean.parseBoolean(serverConf.getProperty("sslsessionresumption", ""+sslsessionresumption));
		conscryptticketresumption = Boolean.parseBoolean(serverConf.getProperty("conscryptticketresumption", ""+conscryptticketresumption));
		closereaddebug = Boolean.parseBoolean(serverConf.getProperty("closereaddebug",""+closereaddebug));
		throwbindexp = Boolean.parseBoolean(serverConf.getProperty("throwbindexp",""+throwbindexp));
		wspingtimeout = Long.parseLong(serverConf.getProperty("wspingtimeout",""+wspingtimeout));
		debugips = CommonUtil.getList(serverConf.getProperty("debugips"));
		accesslogexcludedurls = CommonUtil.getList(serverConf.getProperty("accesslogexcludedurls",""));
		printincompleterequestforips = CommonUtil.getList(serverConf.getProperty("printincompleterequestforips"));
		printincompleterequest = Boolean.parseBoolean(serverConf.getProperty("printincompleterequest",""+printincompleterequest));
		webenginews = Boolean.parseBoolean(serverConf.getProperty("webenginews",""+webenginews));
		qosstats = Boolean.parseBoolean(serverConf.getProperty("qosstats",""+qosstats));
		influxStatsUpdateInterval = Integer.parseInt(serverConf.getProperty("influxstatsupdateinterval",""+influxStatsUpdateInterval));
		influxStatsExcludedUrls = CommonUtil.getList(serverConf.getProperty("influxstatsexcludedurls",""));   
		disableInfluxStatsLog = Boolean.parseBoolean(serverConf.getProperty("disableinfluxstatslog",""+disableInfluxStatsLog));
		enableTcpNoDelay = Boolean.parseBoolean(serverConf.getProperty("enabletcpnodelay",""+enableTcpNoDelay));
		miincludedwsurls = CommonUtil.getList(serverConf.getProperty("miincludedwsurls",""));
		keepalivetime = Long.parseLong(serverConf.getProperty("keepalivetime",""+keepalivetime));
		internalReadLimit = Integer.parseInt(serverConf.getProperty("internalreadlimit",""+internalReadLimit));
		internalWriteLimit = Integer.parseInt(serverConf.getProperty("internalwritelimit",""+internalWriteLimit));
		useDefaultTrustManager = Boolean.parseBoolean(serverConf.getProperty("usedefaulttrustmanager",""+useDefaultTrustManager));
		compressionStatsEnabled = Boolean.parseBoolean(serverConf.getProperty("compressionstatsenabled",""+compressionStatsEnabled));
		exceptionlogs = Boolean.parseBoolean(serverConf.getProperty("exceptionlogs",""+exceptionlogs));
		debuglogs = Boolean.parseBoolean(serverConf.getProperty("debuglogs",""+debuglogs));
		accesslogs = Boolean.parseBoolean(serverConf.getProperty("accesslogs",""+accesslogs));
		responseCharset = serverConf.getProperty("responsecharset",""+responseCharset);
		sslnetbuffersize = Integer.parseInt(serverConf.getProperty("sslnetbuffersize",""+sslnetbuffersize));
		sslappbuffersize = Integer.parseInt(serverConf.getProperty("sslappbuffersize",""+sslappbuffersize));
		wsdeflatebuffersize = Integer.parseInt(serverConf.getProperty("wsdeflatebuffersize",""+wsdeflatebuffersize));
		wsinflatebuffersize = Integer.parseInt(serverConf.getProperty("wsinflatebuffersize",""+wsinflatebuffersize));
		streamMode = Boolean.parseBoolean(serverConf.getProperty("streammode",""+streamMode));
		heavyDataStatsEnabled = Boolean.parseBoolean(serverConf.getProperty("heavydatastatsenabled",""+heavyDataStatsEnabled));
		hackAttemptStatsEnabled = Boolean.parseBoolean(serverConf.getProperty("hackattemptstatsenabled",""+hackAttemptStatsEnabled));
		useSasLogFormat = Boolean.parseBoolean(serverConf.getProperty("usesaslogformat",""+useSasLogFormat));
		enableEndAWSAccessLog = Boolean.parseBoolean(serverConf.getProperty("enableendawsaccesslog",""+enableEndAWSAccessLog));
		enableCookieDebugLogs = Boolean.parseBoolean(serverConf.getProperty("enablecookiedebuglogs",""+enableCookieDebugLogs));
		writeDataInQueue = Boolean.parseBoolean(serverConf.getProperty("writedatainqueue",""+writeDataInQueue));
		cacheEnabled = Boolean.parseBoolean(serverConf.getProperty("cacheenabled",""+cacheEnabled));
		webenginenetdata = Boolean.parseBoolean(serverConf.getProperty("webenginenetdata",""+webenginenetdata));

		try
		{
			defaultServletURL = serverConf.getProperty("defaultservleturl",""+defaultServletURL);
			DefaultServlet servlet = (DefaultServlet) Class.forName(defaultServletURL).newInstance();
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in default servlet url parsing : ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, ex);
			throw ex;
		}

		dcu = serverConf.getProperty("dcuimpl");

		if(dcu != null && dcuimpl == null)
		{
			try
			{
				dcuimpl = (X509DynamicCertUpdater) Class.forName(dcu).newInstance();
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, "Failed to initialize DCU : ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, ex);
			}
		}

	
		boolean newconscrypt = Boolean.parseBoolean(serverConf.getProperty("conscrypt", ""+conscrypt));  
		if((newconscrypt != isConscryptContextEnabled()) && AsyncWebServerAdapter.isReinitConf())
                {
			logger.log(Level.INFO,"SWITCHING SSL CONTEXT WITH CONSCRYPT SET TO "+newconscrypt,ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
			try
			{
				SSLManagerFactory.initialize();
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, "SSL Manager Init faliure ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, ex);
			}
			//existing context should be cleaned after init 
			conscrypt = newconscrypt;	
                        SSLManagerFactory.getSSLManager().resetContextMap();   
		}
		else
		{
			conscrypt = newconscrypt;	
			if(AsyncWebServerAdapter.isReinitConf())
			{
				try
				{
					SSLManagerFactory.resetSSLSessionTimeout();
				}
				catch(Exception ex)
				{
					logger.log(Level.INFO, "SSL session timeout reset failure ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, ex);
				}
			}
		}

		if(datastats || datamonitor)
		{
			File dataHome = new File(getDataMonitorHome());
			
			if(!dataHome.exists())
			{
				dataHome.mkdir();
			}

			if(datastats)
			{
				if(!DataCountUpdater.isThreadAlive())
				{		
					clearDirectory(dataMonitorHome+File.separator+"aws");//No I18N
					new DataCountUpdater().start();
					logger.log(Level.INFO,"Data count updater enabled....",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
				}
			}

			if(datamonitor)
			{
				if(!DataCountAnalyzer.isThreadAlive())
				{
					new DataCountAnalyzer().start();
					logger.log(Level.INFO,"Data analayzer enabled....",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
				}
			}
		}
		
		if(anomalydetection && !AnomalyMonitor.isThreadAlive())
		{
			new AnomalyMonitor().start();
			logger.log(Level.INFO,"Anomaly monitoring enabled....",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
		}

		enableanomalyhandler = Boolean.parseBoolean(serverConf.getProperty("enableanomalyhandler", ""+enableanomalyhandler));
		anomalyhandler = serverConf.getProperty("anomalyhandler");
		anomalyProcessorCount = Integer.parseInt(serverConf.getProperty("anomalyprocessorcount",""+anomalyProcessorCount));
		anomalyMaxProcessorCount = Integer.parseInt(serverConf.getProperty("anomalymaxprocessorcount",""+anomalyMaxProcessorCount));
		anomalyMaxThreadCreationLimit = Integer.parseInt(serverConf.getProperty("anomalyprocessormaxthreadcreationlimit",""+anomalyMaxThreadCreationLimit));

		if(enableanomalyhandler)
		{

			if(enableanomalyhandler && anomalyhandler != null && anomalyhandlerimpl == null)
			{
				try
				{
					anomalyhandlerimpl = (AnomalyHandlerImpl) Class.forName(anomalyhandler).newInstance();
					AnomalyHandler.init();
				}
				catch(Exception e)
				{
					logger.log(Level.INFO, "Exception during Initialize AnomalyHanding : ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, e);
					enableanomalyhandler = false;
					anomalyhandlerimpl = null;
					logger.log(Level.INFO, "Due to Exception during AnomalyHandler Initialization, enableanomalyhandler set to false.",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
				}
			}
		}

		try
		{
			if(serverportmonitor)
			{
				if(spm == null || !ServerPortMonitor.isThreadAlive())
				{
					spm = new ServerPortMonitor();
					spm.start();
					logger.log(Level.INFO,"Server port monitoring enabled....",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
				}
				else
				{
					spm.refreshConfValues();
				}
			}
			else
			{
				if(spm != null && ServerPortMonitor.isThreadAlive())
				{
					spm.stopThread();
					spm = null;
				}
			}
		}
		catch(Exception spmex)
		{
			logger.log(Level.INFO, "Exception in Initializing ServerPortMonitoring ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, spmex);
		}

		if(enablemi && !MIAdapter.getStatus())
		{
			try
			{
				miadapter = new MIAdapter();
				miadapter.start();
				logger.log(Level.INFO,"MI monitoring thread started...",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
			}
			catch(Exception miexp)
			{
				logger.log(Level.SEVERE, "Exception in initializing MI.",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, miexp);//No I18n
			}
		}

		if(serverProxy && proxyConf!=null)
		{
			initializeProxy();
		}

		deadlockmonitor = Boolean.parseBoolean(serverConf.getProperty("deadlockmonitor",""+deadlockmonitor));
		dlwaitinginterval = Long.parseLong(serverConf.getProperty("dlwaitinginterval", ""+dlwaitinginterval));
		dlalertmailreceivers = serverConf.getProperty("dlalertmailreceivers",""+dlalertmailreceivers);
		keystoreimpl = serverConf.getProperty("keystoreimpl",keystoreimpl);
		try
		{
			if(keystoreimpl!=null && !keystoreimpl.equals(""))
			{
				keyStoreLoader = (KeyStoreLoader)Class.forName(keystoreimpl).newInstance();
			}
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "Exception while loading KeyStoreLoader",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, e);
		}

		try
		{
			if(deadlockmonitor)
			{
				if(dlmonitor == null || !DeadlockMonitor.isThreadAlive())
				{
					dlmonitor = new DeadlockMonitor();
					dlmonitor.start();
					logger.log(Level.INFO, "Deadlock Monitoring enabled...",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
				}
				else
				{
					dlmonitor.refreshConfValues();
				}
			}
			else
			{
				if(dlmonitor != null && DeadlockMonitor.isThreadAlive())
				{
					dlmonitor.stopThread();
					dlmonitor = null;
				}
			}
		}
		catch(Exception dexp)
		{
			logger.log(Level.SEVERE, "Exception in Deadlock thread Init ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, dexp);//No I18n
		}

		if(runtimemonitoring)
		{
			registerAWSRuntimeProps();
		}

                try
                {
			runtimestatslistenerimpl = serverConf.getProperty("runtimestatslistener");
                        if(runtimestatslistener == null && runtimestatslistenerimpl != null)
                        {
                                runtimestatslistener = (RuntimeStatsListener)Class.forName(runtimestatslistenerimpl).newInstance();
                        }
                }
                catch(Exception e)
                {                                                                                                            
                        logger.log(Level.SEVERE, "Exception in loading RuntimeStatsListener",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE ,e);
                }

		try
		{
			engineLoaderName = serverConf.getProperty("engineloader");
			if(engineLoader == null && engineLoaderName != null)
			{
				engineLoader = (WebEngineLoader) Class.forName(engineLoaderName).newInstance();
				logger.log(Level.INFO, "WebEngine Loader loaded...",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);//No I18n
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in loading WebEngineLoader : ",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE, ex);//No I18n
		}

		initialized = true;

		try
                {
                        SSLManagerFactory.updateCipherAndProtocolMap();
                }
                catch(Exception ex)
                {
                        logger.info("[Error Updating SSLManagerFactory]["+ex.getMessage()+"]",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_ADAPTER_MODE);
                        return false;
                }
		SSLManagerFactory.resetSSLSessionTimeout();
		return true;
	}

	/*
	 * To Load Runtime Properties file WMS Teams.
	*/

	static void registerWMSRuntimeProps()
	{
		try
		{
			if(!disableWMSRuntime && !isWMSRuntimePropsLoaded)
			{
				// Whenever added an entry here, add command.conf equivalent mapping in CLIRuntime.addAWSCommandConf()
				RuntimeAdmin.registerRuntime(AWSConstants.CLI, getWmsRuntimeInstance("com.adventnet.wms.servercommon.runtime.cli.CLIRuntime"), 1);//No I18n
				RuntimeAdmin.registerRuntime(AWSConstants.AWS_UPDATECONF, getWmsRuntimeInstance("com.zoho.wms.asyncweb.server.runtime.UpdateConf"));//No I18n
				isWMSRuntimePropsLoaded=true;
				logger.log(Level.INFO, "WMS Runtime Props registered successfully.",ConfManager.class.getName(),AWSLogMethodConstants.REGISTER_WMS_RUNTIME_PROPS);//No I18n
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in register wmsruntime props. ",ConfManager.class.getName(),AWSLogMethodConstants.REGISTER_WMS_RUNTIME_PROPS, ex);//No I18n
		}
	}

	/*
	 * To Load Runtime Properties file NON-WMS Teams. In particular, for AsyncWebStatsManager stats calculation.
	*/

	private static void registerAWSRuntimeProps()
	{
		try
		{
			if(!isAWSRuntimePropsLoaded)
			{
				// Whenever added an entry here, add command.conf equivalent mapping in CLIRuntime.addAWSCommandConf()
				RuntimeAdmin.registerRuntime(AWSConstants.AWS_MEMINFO, getWmsRuntimeInstance("com.zoho.wms.asyncweb.server.runtime.MemInfo"), runtimeinterval);//No I18n
				RuntimeAdmin.registerRuntime(AWSConstants.AWS_HITS, getWmsRuntimeInstance("com.zoho.wms.asyncweb.server.runtime.HttpHits"), runtimeinterval);//No I18n
				RuntimeAdmin.registerRuntime(AWSConstants.AWS_BW, getWmsRuntimeInstance("com.zoho.wms.asyncweb.server.runtime.BandWidthProfiler"), runtimeinterval);//No I18n
				RuntimeAdmin.registerRuntime(AWSConstants.AWS_SUSPECTIPS, getWmsRuntimeInstance("com.zoho.wms.asyncweb.server.runtime.SuspectedIPsRuntime"));//No I18n
				RuntimeAdmin.registerRuntime(AWSConstants.AWS_GCPROFILER, getWmsRuntimeInstance("com.zoho.wms.asyncweb.server.runtime.GCProfiler"), runtimeinterval);//No I18n
				RuntimeAdmin.registerRuntime(AWSConstants.AWS_CPUINFO, getWmsRuntimeInstance("com.zoho.wms.asyncweb.server.runtime.CPUInfo"), runtimeinterval);
				RuntimeAdmin.registerRuntime(AWSConstants.AWS_DISKUSAGEINFO, getWmsRuntimeInstance("com.zoho.wms.asyncweb.server.runtime.DiskUsageInfo"), runtimeinterval);
				isAWSRuntimePropsLoaded=true;
				logger.log(Level.INFO, "AWS Runtime Props registered successfully.",ConfManager.class.getName(),AWSLogMethodConstants.REGISTER_AWS_RUNTIME_PROPS);//No I18n
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in register wmsruntime props. ",ConfManager.class.getName(),AWSLogMethodConstants.REGISTER_AWS_RUNTIME_PROPS, ex);//No I18n
		}
	}

	private static WmsRuntime getWmsRuntimeInstance(String classname) throws Exception
	{
		return (WmsRuntime) Class.forName(classname).newInstance();
	}

	public static void restartMI()
	{
		if(enablemi)
		{
			if(miadapter != null && miadapter.getStatus())
                	{
                        	miadapter.interrupt();
                	}
                	miadapter = new MIAdapter();
                	miadapter.start();
                	logger.log(Level.INFO,"MI Restarted...",ConfManager.class.getName(),AWSLogMethodConstants.RESTART_MI);		
		}
	}

	private static void initializeRestProperties()
        {
                try
                {
                        String restServer = iamserverurl + (iamserverurl.endsWith("/") ? "" : "/") + AccountsConstants.ACCOUNTS_SLASH_RESOURCE_CONTEXT;
                        restServer = System.getProperty("com.zoho.resource." + AccountsConstants.REST_CONTEXT + ".server", restServer); // No I18n
                        RESTProperties.init(AccountsConstants.REST_CONTEXT, restServer);
                        logger.log(Level.INFO, "REST Inited with URL {0} "+ restServer,ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_REST_PROPERTIES);
                }
                catch(Exception ex)
                {
                        logger.log(Level.INFO, "Initializing REST properties failed..",ConfManager.class.getName(),AWSLogMethodConstants.INITIALIZE_REST_PROPERTIES, ex);
                }
        }

	private static void initIAMSecurityPrivateKey()
        {
                try
                {
                        SecurityFilterProperties filterConfig = new SecurityFilterProperties();
                        RuleSetParser.initSecurityRules(filterConfig , new File(iampropertyFile));
                        RuleSetParser.initSecurityRules(filterConfig , new File(iamprivateKeyFile));
                        SecurityFilterProperties.addFilterInstance(iamsecurityservicename, filterConfig);
                }
                catch(Exception ex)
                {
                        logger.log(Level.INFO, "Unable to initialize Security Private Key",ConfManager.class.getName(),AWSLogMethodConstants.INIT_IAM_SECURITY_PRIVATE_KEY, ex);
                }
        }

	public static void loadLBLSSLIPs()
	{
		if (lblsslips!=null && !CommonUtil.isEmpty(lblsslips))
                {
			String[] lbsslipsArr = lblsslips.split(",");
			HashSet<String> lblsslipslst = new HashSet<String>();
			HashSet<String> lblsslipsrangelst = new HashSet<String>();
			for (String ip : lbsslipsArr)
			{
				if(ip!=null && !CommonUtil.isEmpty(ip))
				{
					if (ip.indexOf("-") != -1)
					{
						lblsslipsrangelst.add(ip);
					} else
					{
						lblsslipslst.add(ip);
					}
				}
			}

			lblsslipslist = new CopyOnWriteArrayList<String>(lblsslipslst);
			lblsslipsrangelist = new CopyOnWriteArrayList<String>(lblsslipsrangelst);
                }	
	}
		
	public static Hashtable loadSSLPortMap()
	{
		sslportmap = new Hashtable();
		wantClientAuth = new HashSet();
		needClientAuth = new HashSet();
		Properties sslprops = getSSLConf();
		Enumeration sslen = sslprops.propertyNames();
		while(sslen.hasMoreElements())
		{
			String srvname = (String)sslen.nextElement();
			try
			{
				String val = sslprops.getProperty(srvname);

				String[] valarr = val.split(",");
				String port = valarr[0];
				int startuptype = Integer.parseInt(valarr[2]);
				sslportmap.put(port,new Integer(startuptype));

				int authmode = -1;
				try
				{
					authmode = Integer.parseInt(valarr[3]);
				}
				catch(Exception ex)
				{
					authmode = -1;
				}
				setClientAuthMode(port, authmode);
			}
			catch(Exception ex)
			{	
				logger.log(Level.INFO, "SSL Port Map Load Error ",ConfManager.class.getName(),AWSLogMethodConstants.LOAD_SSL_PORT_MAP, ex);
			}
		}	
		return sslportmap;
	}

	public static Hashtable addSSLPorts(Properties sslprops,boolean cleanupexisting)
	{
		if(sslportmap == null || cleanupexisting)
		{
			sslportmap = new Hashtable();
		}
		Enumeration sslen = sslprops.propertyNames();
		while(sslen.hasMoreElements())
		{
			String srvname = (String)sslen.nextElement();
			try
			{
				String val = sslprops.getProperty(srvname);

				sslConf.setProperty(srvname,val);
				String[] valarr = val.split(",");
				String port = valarr[0];
				int startuptype = Integer.parseInt(valarr[2]);
				sslportmap.put(port,new Integer(startuptype));
			}
			catch(Exception ex)
			{	
				logger.log(Level.INFO, "Add SSL Port Error ",ConfManager.class.getName(),AWSLogMethodConstants.ADD_SSL_PORTS, ex);
			}
		}

		return sslportmap;
	}
	
	public static Properties getProperties(String propsFile)
	{
		try
		{
			logger.log(Level.INFO,"Loading props "+propsFile,ConfManager.class.getName(),AWSLogMethodConstants.GET_PROPERTIES);//No I18N
			Properties props = new Properties();
			props.load(new FileInputStream(propsFile));
			return props;	
		}
		catch(Exception exp)
		{
			logger.log(Level.SEVERE,"Unable to load conf file "+propsFile,ConfManager.class.getName(),AWSLogMethodConstants.GET_PROPERTIES);//No I18N
			return null;
		}
	}

	public static void initializeProxy()
	{
		primaryProxy = proxyConf.getProperty("primary");
		secondaryProxy = proxyConf.getProperty("secondary");
		proxyPort = Integer.parseInt(proxyConf.getProperty(AWSConstants.PORT,""+proxyPort));
		Boolean authNeeded = Boolean.parseBoolean(proxyConf.getProperty("authneeded",AWSConstants.FALSE));
		String username = proxyConf.getProperty("username");
		String password = proxyConf.getProperty("password");
		proxyretryattempt = Integer.parseInt(proxyConf.getProperty("retryattempts",""+proxyretryattempt));
		proxysbtime = Integer.parseInt(proxyConf.getProperty("switchbacktime",""+proxysbtime));
		proxyexcludedomains = CommonUtil.getList(proxyConf.getProperty("excludedomains",""));
		proxyenabledomains = CommonUtil.getList(proxyConf.getProperty("proxyenabledomains",""));
		restrictproxyaccess = Boolean.parseBoolean(proxyConf.getProperty("restrictproxyaccess",""+restrictproxyaccess));
		
		AWSProxySelector proxySel = new AWSProxySelector(ProxySelector.getDefault(), primaryProxy, secondaryProxy, proxyPort);
		ProxySelector.setDefault(proxySel);

		if(authNeeded)
		{
			AWSProxyAuthenticator proxyAuth = new AWSProxyAuthenticator(username, password);
			Authenticator.setDefault(proxyAuth);
		}
	}

	public static boolean isProxyHost(String host)
	{
		try
		{
			return  host.equals(primaryProxy) || host.equals(secondaryProxy);
		}
		catch(Exception ex)
		{
		}
		
		return false;
	}
	
	public static CopyOnWriteArrayList getLBLSSLIPsList()
	{
		return lblsslipslist;
	}
	
	public static CopyOnWriteArrayList getLBLSSLIPsRangeList()
	{
		return lblsslipsrangelist;
	}

	public static boolean isProxyPort(int port)
	{
		return port == proxyPort;
	}
	
	public static int getProxySBTime()
	{
		return proxysbtime;
	}

	public static int getProxyRetryAttempt()
	{
		return proxyretryattempt;
	}

	public static boolean isProxyExcludeDomain(String domain)
	{
		return proxyexcludedomains.contains(domain);
	}

	public static boolean isProxyEnableDomain(String domain)
	{
		return proxyenabledomains.contains(domain);
	}

	public static boolean isProxyAccessRestricted()
	{
		return restrictproxyaccess;
	}

	public static Hashtable getDetails()
	{
		return serverConf;
	}

	public static Properties getMappedDomainDetails()
	{
		return mappedDomain;
	}

	public static void setServerStartTime(long time)
	{
		serverStartTime = time;
	}

	public static void setServerStartStatus(boolean status)
	{
		serverStartStatus = status;
	}

	public static boolean isDOSEnabled()
	{
		return dosmonitor;
	}
		
	public static  void setValidDomains(String domains)
	{
		validdomain = CommonUtil.getList(domains);
		logger.log(Level.INFO,"updated server domains:"+validdomain,ConfManager.class.getName(),AWSLogMethodConstants.SET_VALID_DOMAINS);
	}

	public static void setKeepAlive(boolean status)
	{
		keepalive = status;
	}

	public static boolean isKeepAliveEnabled()
	{
		return keepalive;
	}

	public static long getServerStartTime()
	{
		return serverStartTime;
	}

	public static boolean getServerStartStatus()
	{
		return serverStartStatus;
	}

	public static int getWebServerPort()
	{
		return 	webServerPort;
	}

	public static HashSet getWebServerPortList()
	{
		return webServerPortList;
	}

	public static void addWebServerPort(int webserverport)
	{
		addWebServerPort(webserverport,false);
	}

	public static void addWebServerPort(int webserverport,boolean cleanupexisting)
	{
		if(cleanupexisting)
		{
			webServerPortList.clear();
		}
		webServerPortList.add(""+webserverport);
	}	
	
	public static boolean isWebServerPort(int port)
	{
		Iterator itr = webServerPortList.iterator();

		while(itr.hasNext())
		{
			int webServerPort = Integer.parseInt((String)itr.next());

			if(port == webServerPort || ((port >= webServerPort) && ((port-webServerPort) < connectors)))
			{
				return true;
			}
		}

		return false;
	}

	public static void removeWebServerPort(int port)
	{
		webServerPortList.remove(""+port);
	}

	public static void clearWebServerPortList()
	{
		webServerPortList.clear();
	}

	public static int getConnectorsCount()
	{
		return connectors;
	}

	public static String getLocalIP()
	{
		return localip;
	}

	public static String getIamServerURL()
	{
		return iamserverurl;
	}

	public static String getIAMSecurityServiceName()
	{
		return iamsecurityservicename;
	}

	public static String getServiceName()
	{
		return servicename;
	}

	public static String getWnetAddress()
	{
		return wnetAddress;
	}

	public static String getIPAddress()                                                                                                                                       
        {
                String ip = null;
                try
                {
                        ip = InetAddress.getLocalHost().getHostName();
                }
                catch(Exception ex)
                {
			logger.log(Level.INFO, " IP address get error ",ConfManager.class.getName(),AWSLogMethodConstants.GET_IP_ADDRESS, ex);
                }
                return ip;
        }

	public static long getSoTimeout()
	{
		return soTimeout;
	}

	public static int getNativeSoTimeout()
	{
		return nativeSoTimeout;
	}

	public static boolean isSSLOffloader()
	{
		return sslMode.equals("offloader");
	}

	public static boolean isSSLDefault()
	{
		return sslMode.equals(AWSConstants.DEFAULT);
	}

	public static String getServerHome()
	{
		return serverHome;
	}

	public static String getSecurityContextHome()
	{
		return securityContextHome;
	}

	public static String getDataMonitorHome()
	{
		return dataMonitorHome;
	}

	public static String getSecurityFile(String filename)
	{
		for(String file : securityFilesInfo)
		{
			if(file !=null && file.matches("^[A-Za-z0-9/][A-Za-z0-9\\-/\\.]+") && !file.contains("..") && file.endsWith(filename))
			{
				return securityContextHome+File.separator+file;
			}
		}

		return securityContextHome+File.separator+filename;
	}
			

	public static String getQueueDir()
	{
		return ServerUtil.dataHome+"bqueue";
	}

	public static boolean getServerCheckStatus()
	{
		return servercheck;
	}

	public static String getBlog()
	{
		return blog;
	}

	public static boolean setServerCheckStatus(boolean status)
	{
		System.out.println(" setServerCheckStatus "+status);
		servercheck = status;
		return servercheck;
	}

	public static int getRequestProcessorCount()
	{
		return reqProcThread;
	}

	public static int getMaxRequestProcessorCount()
	{
		return maxReqProcThread;
	}

	public static long getRequestProcessorKeepaliveTime()
	{
		return reqProcKATime;
	}

	public static int getRequestProcessorExecutorCount()
	{
		return reqProcExecutor;
	}

	public static int getRequestProcessorMaxThreadCreationLimit()
	{
		return reqProcMaxThreadCreationLimit;
	}

	public static int getRequestProcessorQueueSize()
	{
		return reqProcQueueSize;
	}

	public static boolean isRequestProcessorRejectionHandlerEnabled()
	{
		return enableReqProcRejectedExecutionTask;
	}

	public static int getWSWriteAckProcessorCount()
	{
		return wsWriteAckProc;
	}

	public static int getMaxWSWriteAckProcessorCount()
	{
		return maxWSWriteAckProc;
	}

	public static long getWSWriteAckProcessorKeepaliveTime()
	{
		return wsWriteAckProcKATime;
	}

	public static int getWSWriteAckProcessorExecutorCount()
	{
		return wsWriteAckProcExecutor;
	}

	public static int getWSWriteAckProcessorQueueSize()
	{
		return wsWriteAckProcQueueSize;
	}

	public static int getWSWriteAckProcessorMaxThreadCreationLimit()
	{
		return wsWriteAckProcMaxThreadCreationLimit;
	}

	public static boolean isWSWriteAckProcessorRejectionHandlerEnabled()
	{
		return enableWsWriteAckProcRejectedExecutionTask;
	}

	public static boolean isHttpSelectorPoolMode()
	{
		return (httpreadselectorcnt > 0);
	}

	public static int getHttpSelectorCount()
	{
		return httpreadselectorcnt;
	}

	public static boolean isHttpsSelectorPoolMode()
	{
		return (httpsreadselectorcnt > 0);
	}

	public static int getHttpsSelectorCount()
	{
		return httpsreadselectorcnt;
	}

	public static int getBackLog()
	{
		return backlog;
	}

	public static boolean isSelectorPoolMode()
	{
		return ((httpreadselectorcnt > 0) || (httpsreadselectorcnt > 0));
	}

	public static boolean isSelectorPoolMode(int port)
	{
		return (getReadSelectorCount(port) > 0);
	}

	public static int getReadSelectorCount(int port)
	{
		if(isHttpsPort(port))
		{
			return httpsreadselectorcnt;
		}
		if(isWebServerPort(port) || isGridPort(port)) //for grid engine
		{	
			return httpreadselectorcnt;
		}

		return 0;
	}

	public static boolean isGridPort(int port)
	{
		return port==gridPort;
	}

	public static boolean isHttpsPort(int port)
	{
		return sslportmap.get(""+port)!=null ;
	}

	public static boolean isSSLPort(int port)
	{
		return sslportmap.get(""+port)!=null && getSSLStartupType(port)==SSLStartUpTypes.DEFAULT ;
	}

	public static int getGridPort()
	{
		return gridPort;
	}

	public static String getDomainMap()
	{
		return domainMap;
	}

	public static String getPortEngineMap()
	{
		return portMap;
	}

	public static String getEngineMap()
	{
		return engineMapFile;
	}

	public static long getRequestTrackerInterval()
	{
		return requesttrackerinterval;
	}

	public static long getWSRequestTrackerInterval()
	{
		return wsrequesttrackerinterval;
	}
	
	public static long getDataMonitorTimeWait()
	{
		return datamonitortimewait;
	}

	public static long getDataStatsTimeWait()
	{
		return datastatstimewait;
	}

	//Request Header name which specifies the Client IP when connected through Offloader
	public static String getClientIPHeader()
	{
		return clientipheader;
	}

	public static String getLBLSSLIps()
	{
		return lblsslips;
	}

	public static boolean isReplaceLBIpsEnabled()
	{
		return replacelbips;
	}

	public static void setLBSSLIps(String iprange)
	{
		lblsslips = iprange;
	}

	public static String getChunkedStreamTimeoutHeader()
	{
		return chunkedstreamtimeoutheader;
	}

	/*public static String getSSLConf()
	{
		return sslConfFile;
	}*/

	public static Properties getSSLConf()
	{
		return sslConf;
	}

	public static void removeSSLConf(String srvname)
	{
		if(sslConf.get(srvname)!=null)
		{
			sslConf.remove(srvname);
		}
	}

	public static void clearSSLConf()
	{
		sslConf.clear();
	}

	public static String getSSLPropFile(String srvname)
	{
		return serverHome+File.separator+"conf"+File.separator+"sslcerts"+File.separator+srvname+".properties";
	}

	public static String getSSLPropFilePath()
	{
		return serverHome+File.separator+"conf"+File.separator+"sslcerts"+File.separator;
	}

	public static Hashtable getAllSSLPortMap()
	{
		return sslportmap;		
	} 

	public static ArrayList getAllPorts()
	{
		ArrayList ports = new ArrayList();
		ports.addAll(Collections.list(sslportmap.keys()));
		ports.addAll(webServerPortList);
		if(isGridEngineActive())
		{
			ports.add(""+gridPort);
		}
		return ports;
	}

	public static int getSSLStartupType(int port)
	{
		return sslportmap.get(""+port).intValue();
	}

	public static Properties getIamServiceProperties()
	{
		Properties iamservices = getProperties(iamserviceConfFile);
		if(iamservices==null)
		{
			iamservices = new Properties();
		}
		return iamservices;
	}

	public static int getNetDataProcessorCount()
	{
		return netdataprocessor;
	}

	public static int getMaxNetDataProcessorCount()
	{
		if(maxnetdataprocessor > netdataprocessor)
		{
			return maxnetdataprocessor;
		}
		return netdataprocessor;
	}

	public static long getNetDataProcessorKeepaliveTime()
	{
		return netdataKATime;
	}

	public static int getNetDataProcessorExecutorCount()
	{
		return netdataExecutor;
	}

	public static int getNetDataProcessorQueueSize()
	{
		return netdataQueueSize;
	}

	public static boolean isNetDataProcessorRejectionHandlerEnabled()
	{
		return enableNetdataRejectedExecutionTask;
	}

	public static int getNetDataMaxThreadCreationLimit()
	{
		return netDataMaxThreadCreationLimit;
	}

	public static String getStreamModeHeader()
	{
		return streammodeheader;
	}

	public static int getSendBufferSize()
	{
		return sendbufsize;
	}

	public static int getReceiveBufferSize()
	{
		return receivebufsize;
	}

	public static int getWriteLimit()
	{
		return writelimit;
	}

	/*public static boolean isDataFlowNotificationEnabled()
	{
		return dataflownotification;
	}*/

	public static boolean isServerHeaderEnabled()
	{
		return serverheaderneeded;
	}

	public static long getRequestMaxIdleTime()
	{
		return requestmaxidletime;
	}

	public static boolean isRequestHeartBeatMonitorEnabled()
	{
		return reqheartbeatmonitor;
	}

	public static boolean isWriteHBEnabled()
	{
		return writeheartbeat;
	}

	public static long getIgnoreHBPeriod()
	{
		return ignorehbperiod;
	}

	public static int getKeyActiveSelectionTimeOut()
	{
		return keyactiveseltimeout;
	}

	public static boolean isValidDomain(String host)
	{
		if(host == null)
		{
			return false;
		}
		if(host.startsWith("http://www."))
		{
			host = host.replaceFirst("http://www.","");
		}
		else if(host.startsWith("https://www."))
		{
			host = host.replaceFirst("https://www.","");
		}

		if(validdomain.contains(host) || validdomain.contains("*"))
		{
			return true;
		}
		Iterator itr = validdomain.iterator();
		while(itr.hasNext())
		{
			boolean breaks = false;
                        String indomain = (String)itr.next();
                        if(indomain.split("\\.").length == host.split("\\.").length)
                        {
                                String[] domain = indomain.split("\\.");
                                String[] hostdomain = host.split("\\.");
                                for(int i=0; i<domain.length; i++)                                                   
                                {
                                        if(!domain[i].equals("*") && !domain[i].equals(hostdomain[i]))                                                                   
                                        {
                                                breaks = true;
                                                break;
                                        }
                                }
                                if(!breaks)
                                {
                                        return true;
                                }
                        }
		}
		return false;
	}
		
	public static boolean isRestrainedURL(String url)
	{
		return restrainedurls.contains(url);
	}

	public static ArrayList getAccessRestrictedParams()
	{
		return accessrestrictedparams;
	}

	public static String getValidDomains()
	{
		return CommonUtil.getString(validdomain);
	}

	public static int getReadLimit()
	{
		return read_limit;
	}

	public static void setReadLimit(int readlimit)
	{
		read_limit = readlimit;
	}

	public static int getReqPayloadSize()
        {
                return req_payload_size;
        }

        public static void setReqPayloadSize(int size)
        {
                req_payload_size = size;
        }

	public static boolean isMessageRetryEnabled()
	{
		return msgretryenabled;
	}

	public static boolean isAdapterMode()
	{
		return adaptermode;
	}

	public static boolean isWSPrdListenerFactoryEnabled()
	{
		return wsprdlistfactoryenabled;
	}

	public static boolean isSupportedWSVersion(String version)
	{
		return supportedwsversion.contains(version);
	}

	public static String getSupportedWSVersions()
	{
		return CommonUtil.getString(supportedwsversion);
	}

	public static boolean isNOPForwardToEngine()
	{
		return nopforwardtoengine;
	}

	public static int getWriteCapacity()
	{
		return writecapacity;
	}

	public static int getMaxDataPerWrite()
	{
		return max_outbb_size;
	}

	public static int getMaxDataPerWSRead()
	{
		return maxdataperwsread;
	}

	public static long getWSRequestTimeout()
	{
		return wsrequesttimeout;
	}

	public static int getSSLContextSize()
	{
		return sslcontextsize;
	}

	public static boolean isRuntimeMonitoringEnabled()
	{
		return runtimemonitoring;
	}

	public static int getWorkAroundPause()
	{
		return workaroundpause;
	}

	public static String getAllowedRefererPattern()
	{
		return refererpattern;
	}

	public static boolean isSupportedRequestType(String type)
	{
		return (supportedpostmethods.contains(type) || supportedgetmethods.contains(type));
	}

	public static boolean isSupportedPostMethod(String type)
	{
		return supportedpostmethods.contains(type);
	}

	public static boolean isSupportedGetMethod(String type)
	{
		return supportedgetmethods.contains(type);
	}

	public static String getSupportedGetMethods()
	{
		return serverConf.getProperty("supportedgetmethods");
	}

	public static String getSupportedPostMethods()
	{
		return serverConf.getProperty("supportedpostmethods");
	}

	public static ArrayList getAccessHeaders()
	{
		return accessheaders;
	}

	public static ArrayList getAccessResponseHeaders()
	{
		return accessresponseheaders;
	}

	public static boolean isReuseEnabled()
	{
		return reuseconnection;
	}

	public static boolean isCipherOrderEnabled()
	{
		return enablecipherorder;
	}

	public static boolean isEnabledTLSVersion(String tlsversion)
	{
		return enabledTLSVersionList.contains(tlsversion);
	}

	public static boolean isEnabledCipherSuite(String suite)
	{
		return enabledCipherSuiteList.contains(suite);
	}

	public static ArrayList getEnabledCipherSuitesList()
	{
		return enabledCipherSuiteList;
	}

	public static boolean isBinaryWSDataEnabled()
	{
		return binarywsenabled;
	}

	public static void setReadOnlyMode(boolean status)
	{
		readonly = status;
	}

	public static void setConfFileExtension(String extName)
	{
		if(extName != extension)
		{
			WmsRuntimeCounters.setDomainHitMapLoaded(false);	
		}
		extension = extName;
	}

	public static String getConfFileExtension()
	{
		return extension;
	}
	
	public static boolean isReadOnlyMode()
	{
		return readonly;
	}

	public static boolean isMIEnabled()
	{
		return enablemi;
	}

	public static void setWMSServer()         
        {
		if(!CommonUtil.isEmpty(DC.getServertype()))
		{
			wmsserver=true;
			logger.log(Level.INFO,"wms server set true",ConfManager.class.getName(),AWSLogMethodConstants.SET_WMS_SERVER);
		}
		else
		{
			logger.log(Level.INFO,"wms server set false",ConfManager.class.getName(),AWSLogMethodConstants.SET_WMS_SERVER);
		}
        }

	public static boolean isWMSServer()
	{
		return wmsserver;
	}

	public static boolean isMIExcludedURL(String url)
	{
		return miexcludedurls.contains(url);
	}

	public static boolean isSecurityExcludedURL(String url)
	{
		return securityexcludedurls.contains(url);
	}

	public static Properties getMIConfProperties()
	{
		return getProperties(miConfFile);
	}

	public static String getMIXML()
	{
		return miXMLFile;
	}
		
	public static boolean isAnomalySchedulingEnabled()
	{
		return anomalyscheduling;
	}

	public static boolean isAnomalyDetectionEnabled()
	{
		return anomalydetection;
	}

	public static long getAnomalyHitRateThreshold()
	{
		return anomalyhitratethreshold;
	}

	public static long getAnomalyBWRateThreshold()
	{
		return anomalybwratethreshold;
	}
	
	public static long getAnomalyMemThreshold()
	{
		return anomalymemthreshold;
	}

	public static boolean isAnomalyMailingEnabled()
	{
		return anomalymailingenabled;
	}

	public static int getAnomalyDetectionThreshold()
	{
		return anomalydetectionthreshold;
	}

	public static int getAnomalyBehaviourAnalysisPeriod()
	{
		return anomalybehaviouranalysisperiod;
	}
	
	public static int getAnomalyMonitorInterval()
	{
		return anomalymonitorinterval;
	}
	
	public static int getAnomalyScheduleInterval()
	{
		return anomalyscheduleinterval;
	}

	public static int getIterationPerAnomaly()
	{
		return iterationperanomaly;
	}

	public static String getCreatorMailToAddr()
	{
		return creatormail_to;
	}
	
	public static String getCreatorMailFromAddr()
	{
		return creatormail_from;
	}

	public static String getStatReceiverEmails()
	{
		return statsreceivers;
	}

	public static String getPMStatReceiverEmails()
        {
                return pmstatsreceivers;                                                             
        }

	public static boolean isAnomalyDataRecordEnabled()
	{
		return recordanomalydata;
	}

	public static int getBindRetryAttempt()
	{
		return bindretryattempt;
	}

	public static long getNetEventThreshold()
	{
		return neteventthreshold;
	}
	
	public static boolean isNetEventTraceEnabled(String ip)
	{
		if(debugips.isEmpty())
		{
			return neteventtrace; 
		}
		return debugips.contains(ip);
	}

	public static boolean isHeaderCompletionNeeded()
	{
		return headercompletion;
	}

	public static boolean isServerPortMonitoringEnabled()
	{
		return serverportmonitor;
	}

	public static int getServerPortMonitorThreshold()
	{
		return serverportmonitorthreshold;
	}

	public static int getServerPortRequestMonitorInterval()
	{
		return serverportrequestmonitorinterval;
	}

	public static int getServerPortMonitorInterval()
	{
		return serverportmonitorinterval;
	}

	public static boolean isValidEngine(String engine)
	{
		return engineMap.get(engine)!=null;
	}

	public static Properties getWebEngineMap()
	{
		return engineMap;
	}

	public static boolean isWebSocketCompressionEnabled()
	{
		return websocketcompression;
	}

	public static void setWebSocketCompression(boolean status)
	{
		websocketcompression = status;
	}

	public static boolean isSecurityFilterEnabled()
	{
		return securityfilter;
	}

	public static void setSecurityFilter(boolean status)
	{
		securityfilter = status;
	}

	public static boolean isWSServletThreadSafe()
	{
		return wsservletthreadsafe;
	}

	public static boolean isWCPServletThreadSafe()
	{
		return wcpservletthreadsafe;
	}

	public static boolean isProxyProtocolEnabled()
	{
		return proxyprotocol;
	}

	public static boolean isConscryptContextEnabled()
        {
                return conscrypt;                                                                                                                                          
        }

	public static boolean isSSLSessionResumptionEnabled()
	{
		return sslsessionresumption;	
	}
	
        public static int getSSLSessionTimeout()
        {
                return sslsessiontimeout;
        }

	public static boolean isConscryptTicketResumptionEnabled()
	{
		return conscryptticketresumption;
	}

	public static boolean isDataStatsEnabled()
	{
		return datastats;
	}

	public static boolean isDataMonitorEnabled()
	{
		return datamonitor;
	}

	public static boolean isRunning(int port)
	{
		if((WmsRuntimeCounters.getPortAccess(port)!=null) && (System.currentTimeMillis()-WmsRuntimeCounters.getPortAccess(port) < (ConfManager.getServerPortMonitorThreshold()*1000)))
		{
			return true;
		}

		if(ConfManager.isSSLPort(port))
		{
			InputStream is = null;
			OutputStream os = null;
			SSLSocket s = null;
			try
			{
				SSLSocketFactory sfac = (SSLSocketFactory)SSLSocketFactory.getDefault();
				X509TrustManager tm = new SSLTrustManager(AWSConstants.DEFAULT);
				SSLContext ctx = SSLContext.getInstance(AWSConstants.TLS_V1_2);
				ctx.init(null, new X509TrustManager[]{tm}, null);
				sfac = ctx.getSocketFactory();
				s = (SSLSocket)sfac.createSocket(AWSConstants.LOCALHOST,port);
				s.setSoTimeout(getSPMReadTimeout());
				is = s.getInputStream();
				os = s.getOutputStream();
				os.write(AWSConstants.SERVER_PORT_MONITOR_HTTPS_REQUEST.getBytes(AWSConstants.UTF_8));
				byte[] b = new byte[12];
				int a = is.read(b);
				if(Arrays.equals(b, AWSConstants.HTTP_200) || Arrays.equals(b, AWSConstants.HTTP_404))
				{
					return true;
				}
				else
				{
					logger.log(Level.SEVERE, AWSLogConstants.SSL_PORT_DOWN,ConfManager.class.getName(),AWSLogMethodConstants.IS_RUNNING, new Object[]{""+port});
					return false;
				}
			}
			catch(SSLHandshakeException she)
			{
				if(she.getMessage().equals(HANDSHAKE_FAILURE_MSG) && (isClientAuthNeeded(""+port) || isClientAuthWanted(""+port)))
				{
					return true;
				}
				logger.log(Level.INFO, AWSLogConstants.SSL_HANDSHAKE_EXCEPTION,ConfManager.class.getName(),AWSLogMethodConstants.IS_RUNNING, she);
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE, AWSLogConstants.PORT_MONITOR_EXCEPTION+port,ConfManager.class.getName(),AWSLogMethodConstants.IS_RUNNING, ex);
			}
			finally
			{
				try{is.close();}catch(Exception ex){}
				try{os.close();}catch(Exception ex){}
				try{s.close();}catch(Exception ex){}
			}
		}
		else
		{
			try
			{
				HttpConnection con = new HttpConnection(AWSConstants.HTTP_LOCALHOST+port, getSPMReadTimeout(), getSPMConnectTimeout());
				con.doGet();
				if(con.getStatusCode()== HttpResponseCode.OK || con.getStatusCode()==HttpResponseCode.BAD_REQUEST)
				{ 
					logger.addDebugLog(Level.FINE, AWSLogConstants.PORT_MONITOR_IS_RUNNING,  ConfManager.class.getName(),AWSLogMethodConstants.IS_RUNNING,new Object[]{""+port});
					return true;
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE, AWSLogConstants.PORT_MONITOR_EXCEPTION+port,ConfManager.class.getName(),AWSLogMethodConstants.IS_RUNNING, ex);
			}
		}

		logger.log(Level.SEVERE, AWSLogConstants.PORT_MONITOR_IS_DOWN,ConfManager.class.getName(),AWSLogMethodConstants.IS_RUNNING, new Object[]{""+port});
		return false;
	}

	static class SSLTrustManager implements X509TrustManager		
	{		
		private KeyStore trustStore;		
		private String domain;		

		public SSLTrustManager(String domain)		
		{		
		}		
		public X509Certificate[] getAcceptedIssuers()		
		{		
			return new X509Certificate[0];		
		}		

		public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException		
		{		
		}		

		public void checkServerTrusted(X509Certificate[] x509Certs, String arg1) throws CertificateException		
		{		
		}		
	}

	public static boolean isCloseReadDebugEnabled(String ip)
	{
		if(debugips.isEmpty())
		{
			return closereaddebug;
		}
		return debugips.contains(ip);
	}

	public static boolean isPrintIncompleteRequestEnabled(String ipaddr)
	{
		if(printincompleterequestforips.isEmpty())
		{
			return printincompleterequest;
		}
		return printincompleterequestforips.contains(ipaddr);
	}

	public static boolean toThrowBindExp()
	{
		return throwbindexp;
	}
	
	public static long getDOSScavengeTime()
	{
		return dosscavengetime;
	}

	public static long getDOSTimeout()
	{
		return dostimeout;
	}

	public static long getDOSMonitorThreshold()
	{
		return dosmonitorthreshold;
	}

	public static boolean isSTSEnabled()
	{
		return sts;
	}	

	public static long getSTSMaxLifeTime()
	{
		return stsmaxlifetime;
	}

	public static void clearDirectory(String dir)
        {
                try
                {
                        File directory = new File(dir);
                        if(!directory.isDirectory())
                        {
                                return;
                        }
                        for(File file: directory.listFiles())
                        {
                                if (!file.isDirectory())
                                {
                                        file.delete();
                                }
                        }
                }
                catch(Exception ex)
                {}
        }
	
	public static boolean isInitialized()
	{
		return initialized;
	}

	public static X509DynamicCertUpdater getDCUImpl()
	{
		return dcuimpl;
	}

	public static boolean isClientAuthNeeded(String port)
	{
		return needClientAuth.contains(port);
	}

	public static boolean isClientAuthWanted(String port)
	{
		return wantClientAuth.contains(port);
	}

	public static int getMaxHeaderSize()
	{
		return maxheadersize;
	}

	public static boolean isWSOffloaderEnabled()
	{
		return wsoffloader;
	}

	public static String getDefaultURL()
	{
		return defaulturl;
	}

	public static long getWSPingTimeOutInterval()
	{
		return wspingtimeout;
	}

	public static boolean discardPingResponse()
	{
		return discardpingres;
	}

	public static String getAdapterConfFile()
	{
		return serverConfFile;
	}

	public static String getSSLServersConfFile()
	{
		return sslConfFile;
	}

	static void setClientAuthMode(String port, int authmode) 
	{
		try
		{
			if(authmode == AWSConstants.AUTH_OPTIONAL)
			{
				wantClientAuth.add(port);
			}
			else if(authmode == AWSConstants.AUTH_MANDATORY)
			{
				needClientAuth.add(port);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.WARNING, "Exception in setclient authmode : port {0}, authmode : {1}",ConfManager.class.getName(),"setClientAuthMode", new Object[]{port, authmode});//No I18n
		}
	}

	public static void updateWebEngineMap() throws Exception
	{
		engineMap = getProperties(engineMapFile);
	}

	public static void setGridEnginePort(int port)
	{
		gridPort = port;
	}

	public static void setGridEngineActive(boolean isActive)
	{
		isGridEngineActive = isActive;
	}

	public static boolean isGridEngineActive()
	{
		return isGridEngineActive;
	}

	public static boolean isAccessLogExcludedURL(String url)
	{
		return accesslogexcludedurls.contains(url);
	}

	public static boolean isAnomalyHandlerEnabled()
	{
		return enableanomalyhandler && (anomalyhandlerimpl != null);
	}

	public static AnomalyHandlerImpl getAnomalyHandlerObject()
	{
		return anomalyhandlerimpl;
	}

	public static int getAnomalyProcessorCount()
	{
		return anomalyProcessorCount;
	}

	public static int getAnomalyMaxProcessorCount()
	{
		return anomalyMaxProcessorCount;
	}

	public static int getAnomalyProcessorMaxThreadCreationLimit()
	{
		return anomalyMaxThreadCreationLimit;
	}

	public static boolean isDeadlockMonitoringEnabled()
	{
		return deadlockmonitor;
	}

	public static long getDLMonitoringWaitingInterval()
	{
		return dlwaitinginterval;
	}

	public static String getDLAlertMailReceivers()
	{
		return dlalertmailreceivers;
	}

	public static boolean isLogpLoggerEnabled()
	{
		return enablelogplogger;
	}

	public static int getMaxZeroReadCount()
	{
		return maxZeroReadCount;
	}

	public static boolean isWebEngineWSMsgDispatcherEnabled()
	{
		return webenginews;
	}

	public static boolean isQOSEnabled()
	{
		return qosstats && DC.getServertype() != null;
	}

	public static int getInfluxStatsUpdateInterval()
	{
		return influxStatsUpdateInterval;
	}

	public static boolean isInfluxStatsExcludedURL(String url)
	{
		if(url == null)
		{
			return false;
		}
		return influxStatsExcludedUrls.contains(url);
	}

	public static boolean isInfluxStatsLogDisabled()
	{
		return disableInfluxStatsLog;
	}

	public static WebEngineLoader getWebEngineLoader()
	{
		return engineLoader;//test
	}

	public static String getDefaultServletURL()
	{
		return defaultServletURL;
	}

	public static boolean isTcpNoDelayEnabled()
	{
		return enableTcpNoDelay;
	}

	public static long getKeepaliveTrackerInterval()
	{
		return keepalivetrackerinterval;
	}
	
	public static boolean isExceptionLogsEnabled()
	{
		return exceptionlogs;
	}

	public static boolean isDebugLogsEnabled()
	{
		return debuglogs;
	}

	public static boolean isMIIncludedWSURL(String url)
	{
		return miincludedwsurls.contains(url);
	}

	public static long getKeepaliveTime()
	{
		return keepalivetime;
	}

	public static int getInternalReadLimit()
	{
		return internalReadLimit;
	}

	public static int getInternalWriteLimit()
	{
		return internalWriteLimit;
	}



	public static String[] getTLSALPNProtocolList()
	{
		return tlsALPNProtocolList;
	}

	public static boolean isTLSALPNEnabled()
	{
		return isTLSALPNEnabled;
	}

	public static boolean isHttp2Enabled()
	{
		return http2Enabled;
	}

	public static int getMaxAllowedClientStream()
	{
		return maxAllowedClientStream;
	}

	public static int getMaxAllowedConcurrentClientStream()
	{
		return maxAllowedConcurrentClientStream;
	}

	public static int getInitialStreamLevelServerWindowSize()
	{
		return initialStreamLevelServerWindowSize;
	}

	public static int getHttp2ConnectionTimeout()
	{
		return http2ConnectionTimeout;
	}

	public static int getRequestFrameMaxSize()
	{
		return requestFrameMaxSize;
	}

	public static int getDynamicTableSize()
	{
		return dynamicTableSize;
	}

	public static int getStreamTrackerInterval()
	{
		return streamTrackerInterval;
	}

	public static int getStreamTimeout()
	{
		return streamTimeout;
	}

	public static int getHttp2FrameProcessorCorePoolSize()
	{
		return http2FrameProcessorCorePoolSize;
	}

	public static int getHttp2FrameProcessorMaxPoolSize()
	{
		return http2FrameProcessorMaxPoolSize;
	}

	public static int getHttp2FrameProcessorThreadCreationLimit()
	{
		return http2FrameProcessorThreadCreationLimit;
	}

	public static boolean isHttp2CounterStatsEnabled()
	{
		return http2CounterStatsEnabled;
	}

	public static boolean isHttp2SocketTimeTakenStatsEnabled()
	{
		return http2SocketTimeTakenStatsEnabled;
	}

	public static boolean isHttp2LogsEnabled()
	{
		return http2LogsEnabled;
	}

	public static boolean isHttp2HuffmanEncodingEnabled()
	{
		return http2HuffmanEncodingEnabled;
	}

	public static int getHttp2ReadDataBufferLimit()
	{
		return http2ReadDataBufferLimit;
	}

	public static boolean isHttp2FrameLogsEnabled()
	{
		return http2FrameLogsEnabled;
	}



	public static boolean isAsyncFrameProcEnabled()
	{
		return asyncFrameProc;
	}

	public static int getExternalFPCount()
	{
		return externalFPCount;
	}

	public static int getMaxExternalFPCount()
	{
		return externalMaxFPCount;
	}

	public static long getExternalFPKATime()
	{
		return externalFPKATime;
	}

	public static int getExternalFPExecutorCount()
	{
		return externalFPExecutorCount;
	}

	public static int getExternalFPQueueSize()
	{
		return externalFPQueueSize;
	}

	public static boolean isExternalFPRejectionHandlerEnabled()
	{
		return externalFPRejectionHandler;
	}

	public static int getExternalFPMaxThreadCreationLimit()
	{
		return externalFPMaxThreadCreationLimit;
	}

	public static boolean isUseDefaultTrustManager()
	{
		return useDefaultTrustManager;
	}

	public static boolean isCompressionStatsEnabled()
	{
		return compressionStatsEnabled;
	}

	public static KeyStoreLoader getKeyStoreLoader()
	{
		return keyStoreLoader;
	}

	public static String getResponseCharset()
	{
		return responseCharset;
	}

	public static int getSSLNetSize()
	{
		return sslnetbuffersize;
	}

	public static int getSSLAppSize()
	{
		return sslappbuffersize;
	}

	public static int getWSDeflateBufferSize()
	{
		return wsdeflatebuffersize;
	}

	public static int getWSInflateBufferSize()
	{
		return wsinflatebuffersize;
	}

	public static boolean isStreamModeEnabled()
	{
		return streamMode;
	}

	public static boolean printInvalidWSParams()
	{
		return printinvalidwsparam;
	}

	public static String getDefaultHttpContentType()
	{
		return defaultHttpContentType;
	}

	public static boolean isAccessLogsEnabled()
	{
		return accesslogs;
	}

	public static boolean isIllegalReqExceptionEnabled()
	{
		return illegalreqexception;
	}

	public static boolean isHeavyDataStatsEnabled()
	{
		return heavyDataStatsEnabled;
	}

	public static boolean isHackAttemptStatsEnabled()
	{
		return hackAttemptStatsEnabled;
	}

	public static int getSPMConnectTimeout()
	{
		return spmconnecttimeout;
	}

	public static int getSPMReadTimeout()
	{
		return spmreadtimeout;
	}

	public static boolean isSasLogFormatEnabled()
	{
		return useSasLogFormat;
	}

	public static RuntimeStatsListener getRuntimeStatsListener()
        {
                return runtimestatslistener;
        }

	public static boolean isEndAWSAccessLogEnabled()
	{
		return enableEndAWSAccessLog;
	}

	public static boolean isCookieDebugLogEnabled()
	{
		return enableCookieDebugLogs;
	}

	public static boolean isWriteDataInQueueEnabled()
	{
		return writeDataInQueue;
	}

	public static boolean isCacheEnabled()
	{
		return cacheEnabled;
	}

	public static boolean isWebEngineNetDataEnabled()
	{
		return webenginenetdata;
	}

	public static int getResetFrameLimit()
	{
		return resetFrameLimit;
	}

	public static int getResetFrameLimitPercent()
	{
		return resetFrameLimitPercent;
	}

	public static int getContinuationFrameLimit()
	{
		return continuationFrameLimit;
	}

	public static int getStreamErrorLimit()
	{
		return streamErrorLimit;
	}
}
