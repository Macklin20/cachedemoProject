package com.zoho.wms.asyncweb.server.grid.servlet;

//java imports
import java.io.IOException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.io.RandomAccessFile;
import java.util.logging.Logger;

//common imports
import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.common.HttpDataWraper;

//servercommon imports
import com.adventnet.wms.servercommon.components.fts.FTS;
import com.adventnet.wms.servercommon.stats.influx.StatsDB;
import com.adventnet.wms.servercommon.components.fts.FTStats;
import com.adventnet.wms.servercommon.components.fts.FTSUtil;
import com.adventnet.wms.servercommon.components.fts.FTStatus;
import com.adventnet.wms.servercommon.components.fts.RemoteFile;
import com.adventnet.wms.servercommon.components.fts.FileRWStatus;
import com.adventnet.wms.servercommon.components.net.util.WCPUtil;
import com.adventnet.wms.servercommon.components.fts.FTSConstants;
import com.adventnet.wms.servercommon.components.fts.FTServletInfo;
import com.adventnet.wms.servercommon.components.fts.FTSThreadLocal;
import com.adventnet.wms.servercommon.components.constants.ComponentConstants;

//aws imports
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpStreamServlet;

public class FTStreamPutServlet extends HttpStreamServlet  //name:FTStreamWriteServlet?
{
	private static final Logger LOGGER = Logger.getLogger(FTStreamPutServlet.class.getName());

	private static Hashtable<String, FTServletInfo> servletInfo = new Hashtable<String, FTServletInfo>();

	public void onHeaderCompletion(HttpRequest req, HttpResponse res) throws Exception
	{
		try
		{
			res.setInputDataSize(req.getContentLength());
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Exception in onHeaderCompletion. streamId="+req.getHeader(FTSConstants.STREAM_ID)+" servletInfo="+servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)), e);
			throw e;
		}

		LOGGER.log(Level.INFO, "FTS PUT--> On Header Completion Successful. servletInfo={0}", servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)));
		addFTServerStat(req);
	}

	public void onData(HttpRequest req, HttpResponse res) throws Exception
	{
		//logger.log(Level.INFO, "FTS--> ON DATA : "+req.getHttpStream().isAvailable());

		try
		{
			if(servletInfo.get(req.getHeader(FTSConstants.STREAM_ID))==null)
			{
				synchronized (WCPUtil.getObjLockForRegister(req.getHeader(FTSConstants.STREAM_ID)))
				{
					if(servletInfo.get(req.getHeader(FTSConstants.STREAM_ID))==null)
					{
						registerServletInfo(req, res);
					}
				}
			}
		}
		finally
		{
			WCPUtil.releaseObjForRegister(req.getHeader(FTSConstants.STREAM_ID));
		}

		try
		{
			synchronized (WCPUtil.getObjLockForRegister(req.getHeader(FTSConstants.STREAM_ID)))
			{
				RandomAccessFile raf = servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)).getRaf();
				String streamId = req.getHeader(FTSConstants.STREAM_ID);

				while(req.getHttpStream().isAvailable())
				{
					byte[] data = req.getHttpStream().read();
					FileRWStatus fileRWStatus = servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)).getFileRWStatus();

					fileRWStatus.setEndPosition(fileRWStatus.getCurrPosition()+data.length);
					LOGGER.log(Level.INFO, "FTS PUT--> ON DATA CHUNK CurrPos={0} endPos={1}", new Object[] {fileRWStatus.getCurrPosition(), fileRWStatus.getEndPosition()});// remove after testing
					FTSUtil.updateFileStreamPercentage(servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)).getFtStatus(), fileRWStatus.getEndPosition(), req.getContentLength());
					writeToFile(raf, data, fileRWStatus.getCurrPosition(), fileRWStatus.getEndPosition());
					fileRWStatus.setCurrPosition(fileRWStatus.getEndPosition());
				}

				if(req.getHttpStream().isFinished())
				{
					FTStatus ftStatus = servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)).getFtStatus();

					LOGGER.log(Level.INFO, "FTS PUT--> Temp File written successfully. streamId={0}", streamId);
					ftStatus.setProgressPercentage(70);
					ftStatus.setStatus("Temp File written successfully");//NO I18N

					//add server stats
					try
					{
						RemoteFile remoteFile = servletInfo.get(streamId).getRemoteFile();
						FTStats.addFTSSeverStat(FTSConstants.STREAM_MODE.getName(FTSConstants.STREAM_MODE.PUT), FTSConstants.STATUS.FTSTREAM_PUT_SERVLET_STREAM_ENDED, FTSConstants.COMMUNICATION_TYPE.SERVER, remoteFile.getSourceDC(), remoteFile.getSourceCluster(), streamId, raf.length(), 0);
					} catch (Exception e) // backward compatibility issue
					{
					}

					closeRaf(raf);
					LOGGER.log(Level.INFO, "FTS PUT--> RAF closed successfully. streamId={0}", streamId); //remove after debug

					Hashtable<String,String> ht = new Hashtable<String,String>(); // 
					ht.put("stream-id", streamId);
					ht.put("lmtime", Long.toString(ftStatus.getLastModifiedTime()));
					res.write(HttpDataWraper.getString(ht));
					LOGGER.log(Level.INFO, "FTS PUT--> Writing response streamId={0}", streamId); //remove after debug

					res.close();
					LOGGER.log(Level.INFO, "FTS PUT--> URL Connection closed. streamId={0}", streamId);
				}
			}
		}
		catch(Exception e)
		{
			LOGGER.log(Level.SEVERE, "Exception in onData. servletInfo="+servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)), e);
			throw e;
		}
		finally
		{
			WCPUtil.releaseObjForRegister(req.getHeader(FTSConstants.STREAM_ID));
		}
	}

	private void registerServletInfo(HttpRequest req, HttpResponse res) throws Exception
	{
		try
		{
			String streamId = req.getHeader(FTSConstants.STREAM_ID);
			RemoteFile remoteFile = new RemoteFile((Hashtable<String, String>) HttpDataWraper.getObject(req.getHeader(FTSConstants.REMOTE_FILE)));

			LOGGER.log(Level.INFO, "FTS PUT--> registerServletInfo. streamId={0} remoteFile={1}", new Object[] {streamId, remoteFile}); //remove after debug

			FTStatus ftStatus = FTS.getFTStatus(streamId);
			ftStatus.setProgressPercentage(40); //null check needed?
			ftStatus.setStatus("FTStreamServlet-onHeaderCompletion");//NO I18N

			String tempFilePath = ftStatus.getTempFilePath();// testing can be remove
			if(CommonUtil.isEmpty(tempFilePath))
			{
				LOGGER.log(Level.INFO, "FTS PUT--> TempFilePath is empty. tempFilePath={0}",tempFilePath);
				tempFilePath = FTSConstants.TEMP_FILE_PATH+streamId+".txt"; //NO I18N
			}

			RandomAccessFile raf= new RandomAccessFile(tempFilePath, "rw");
			FileRWStatus fileRWStatus = new FileRWStatus(tempFilePath, raf, 0, 0);

			FTServletInfo info = new FTServletInfo(streamId, remoteFile, ftStatus, tempFilePath, raf, fileRWStatus);
			servletInfo.put(streamId, info);

			info.setRedirected(true); // why?
			LOGGER.log(Level.INFO, "FTS PUT--> registerServletInfo Successful. streamId={0}", streamId);
			FTStats.addFTSSeverStat(FTSConstants.STREAM_MODE.getName(FTSConstants.STREAM_MODE.PUT), FTSConstants.STATUS.FTSTREAM_PUT_SERVLET_ON_DATA, FTSConstants.COMMUNICATION_TYPE.SERVER, remoteFile.getSourceDC(), remoteFile.getSourceCluster(), streamId, 0, 0);
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Exception in registerServletInfo. servletInfo="+servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)), e);
			StatsDB.recordError(ComponentConstants.FTS.getModuleCode(), ComponentConstants.FTS.EXCEPTION_IN_REGISTER_SERVLET_INFO.getErrorCode(), 1);
			FTStats.addFTSError(FTSConstants.STREAM_MODE.getName(FTSThreadLocal.getStreamMode()), FTSConstants.ERROR_MSG.EXCEPTION_IN_REGISTER_SERVLET_INFO, FTSConstants.COMMUNICATION_TYPE.SERVER, FTSThreadLocal.getStreamId());

			throw e;
		}

		LOGGER.log(Level.INFO, "FTS PUT--> RandomAccessFile creadted SUCCESSFULLY. servletInfo={0}", servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)));
	}

	private void addFTServerStat(HttpRequest req)
	{
		try
		{
			RemoteFile remoteFile = servletInfo.get(req.getHeader(FTSConstants.STREAM_ID)).getRemoteFile();
			FTStats.addFTSSeverStat(FTSConstants.STREAM_MODE.getName(FTSConstants.STREAM_MODE.PUT), FTSConstants.STATUS.FTSTREAM_PUT_SERVLET_ON_HEADER_COMPLETION, FTSConstants.COMMUNICATION_TYPE.SERVER, remoteFile.getSourceDC(), remoteFile.getSourceCluster(), req.getHeader(FTSConstants.STREAM_ID), 0, 0);
		}
		catch (Exception e)
		{
		}
	}

	public void onOutputBufferRefill(HttpRequest req, HttpResponse res)
	{
		LOGGER.log(Level.INFO, "FTS PUT--> onOutputBufferRefill");
	}

	public void onWriteComplete(HttpRequest req, HttpResponse res)
	{
		LOGGER.log(Level.INFO, "FTS PUT--> onWriteComplete");
	}

	public void onWriteFailure(HttpRequest req, HttpResponse res)
	{
		LOGGER.log(Level.WARNING, "FTS PUT--> onWriteFailure");
	}

	public void service(HttpRequest req, HttpResponse res)
	{
		LOGGER.log(Level.INFO, "FTS PUT--> service");
	}

	private void closeRaf(RandomAccessFile file) throws IOException
	{  
		file.close();
	}

	private void writeToFile(RandomAccessFile file, byte[] data, long startPosition, long endPosition) throws IOException
	{
		//set status - to calculate current streamed percentage, endPosition/fileLength*100
		file.seek(startPosition);
		file.write(data);  
	}
}