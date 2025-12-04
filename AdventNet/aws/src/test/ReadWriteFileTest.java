package out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doNothing;
import org.mockito.Mockito;


import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.exception.AWSException;

import com.webengine.servlet.FileReadServlet;
import com.webengine.LRUCache;
import com.webengine.servlet.ReadAndWriteFile;


import java.io.IOException;

public class ReadWriteFileTest
{
	private static HttpRequest request;
	private static HttpResponse response;
	private static FileReadServlet servlet;
	private static LRUCache lrucache;
	private static ReadAndWriteFile readWriteFile;


	@BeforeAll
	public static void setUp()
	{
		request = Mockito.mock(HttpRequest.class);
                response = Mockito.mock(HttpResponse.class);

		Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");

		servlet = spy(new FileReadServlet());	
		readWriteFile = new ReadAndWriteFile(request , response);
		
		try
		{
			doNothing().when(response).write(Mockito.any(byte[].class));
			doNothing().when(response).close();
			doNothing().when(servlet).setReadWriteFile();

		}
		catch(AWSException e)
                {
                        e.printStackTrace();
                }
                catch(IOException e)
                {
                        e.printStackTrace();
                } 
	}

	@Test
	public void setLRUCacheTest()
	{
		readWriteFile.setLRUCache(2);
		lrucache =  readWriteFile.getLRUCache();
		
		assertTrue(lrucache!=null , "Cache not created");
	}

}
