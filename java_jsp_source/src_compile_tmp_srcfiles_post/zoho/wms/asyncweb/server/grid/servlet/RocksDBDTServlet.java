package com.zoho.wms.asyncweb.server.grid.servlet;

// java imports
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

// aws imports
import com.adventnet.wms.common.stats.influx.StatsDB;
import com.adventnet.wms.servercommon.dc.DC;
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;

// wms servercommon import
import com.adventnet.wms.servercommon.rocksdb.RocksDBManager;

public class RocksDBDTServlet extends WebSocket
{
	private static final Logger LOGGER = Logger.getLogger(RocksDBDTServlet.class.getName());
	static FutureTask<Boolean> writeTask;
	static RocksDBWriter rocksDBWriter;

	public void onConnect(HttpRequest req, HttpResponse res) throws IOException
	{
		LOGGER.info("NS--> Connected made successfully. remoteip="+req.getRemoteAddr());
	}

	public void onMessage(String data) throws IOException
	{
	}

	public void onMessage(byte[] data) throws IOException
	{
		ByteBuffer dataByteBuffer = ByteBuffer.wrap(data);
		long seqNo = dataByteBuffer.getLong();
		//todo upgrade jdk version
		//		byte[] batchByteArray = new byte[data.length - Long.BYTES];
		byte[] batchByteArray = new byte[data.length - 8];
		dataByteBuffer.get(batchByteArray);


		try
		{
			if(!RocksDBManager.write(batchByteArray))
			{
				LOGGER.log(Level.WARNING, "SEQ-- RocksDBWriter - write failed");
				response.write("-1");
			}
			response.write(Long.toString(seqNo));
		}
		catch(Exception e)
		{
			LOGGER.log(Level.SEVERE,"SNA-- Error inside RocksDBDTServlet. seqNo="+seqNo, e);
		}
	}

	public void onClose() throws IOException
	{
		writeTask.cancel(true);
	}


	public static class RocksDBWriter implements Callable<Boolean>
	{
		private boolean writeFlag = true;
		private static boolean stopWriting = false;
		private BlockingQueue<ByteArrayWrapper> queue = new ArrayBlockingQueue<>(1000);
		private boolean result = true;


		@Override
		public Boolean call() throws Exception
		{
			ByteArrayWrapper byteArrayWrapper;
			while(writeFlag || !queue.isEmpty())
			{
				if(stopWriting)
				{
					LOGGER.log(Level.INFO, "SNA- stop writebreaking write ");
					break;
				}
				try
				{
					byteArrayWrapper = queue.poll();
					if(byteArrayWrapper == null)
					{
						continue;
					}

					long startTime = System.nanoTime();
					if(!RocksDBManager.write(byteArrayWrapper.getData()))
					{
						LOGGER.log(Level.WARNING, "SEQ-- RocksDBWriter - write failed");
						return false;
					}
					StatsDB.addData("rocksdbar", "rocksdbmanager.write", DC.getServertype(), System.nanoTime() - startTime); //No I18N
				}
				catch (Exception e)
				{
					LOGGER.log(Level.WARNING, "SNA-- RocksDBWriter - Exception", e);
					return false;
				}
			}


			return result;
		}

		private void addToQueue(ByteArrayWrapper b)
		{
			try
			{
				queue.put(b);
			}
			catch (InterruptedException e)
			{
				result = false;
				stopWriting();
			}
		}

		void endWriting()
		{
			LOGGER.log(Level.INFO, "SNA- rocksdb writer end writing");
			writeFlag = false;
		}

		static void stopWriting()
		{
			stopWriting = true;
		}

	}

	static class ByteArrayWrapper
	{
		private byte[] data;

		ByteArrayWrapper(byte[] data)
		{
			this.data = data;
		}

		public byte[] getData()
		{
			return data;
		}
	}

	public void onPingMessage(byte[] data) throws IOException
	{

	}

	public void onPongMessage(byte[] data) throws IOException
	{

	}
}