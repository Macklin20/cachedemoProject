package com.webengine.servlet;

import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpServlet;
import com.zoho.wms.asyncweb.server.exception.AWSException;


import java.io.IOException;
import java.util.HashMap;

public class MyCustomServlet extends HttpServlet
{
        private boolean isResponded = false;
        private boolean isConnect = false;
        private int port;
        private HashMap headermap;
        private HttpRequest req;
        private HttpResponse res;

        public void service(HttpRequest req, HttpResponse res) throws IOException , AWSException
        {
                System.out.println("Received request");

                try
                {
                        this.req = req;
                        this.res = res;

                        res.setRequestState(2);
                        res.commitChunkedTransfer();

                        port = req.getLocalPort();
                        headermap = req.getHeaders();
                        writeResponse(res);

                        System.out.println(req.getRequestURL());

                        isConnect = true;

                        res.close();
                }
                catch(IOException e)
                {
                        e.printStackTrace();
                }
                catch(AWSException e)
                {
                        e.printStackTrace();
                }
                catch(NullPointerException e)
                {
                        throw new NullPointerException("request or response is null");
                }
        }


	public void writeResponse(HttpResponse res)
        {
                try
		{
			String str = """
		
				<a href="http://wms.localzoho.com:9999/fileservlet?filename=file1">file1.txt</a><br>
				<a href="http://wms.localzoho.com:9999/fileservlet?filename=file2">file2.txt</a><br>
				<a href="http://wms.localzoho.com:9999/fileservlet?filename=file3">file3.txt</a><br>
				
				""";

			res.write(str.getBytes());
                        System.out.println("Responded to Client");

                        isResponded = true;
                }
                catch(IOException e)
                {
                        e.printStackTrace();
                }
                catch(AWSException e)
                {
                        e.printStackTrace();
                }
                catch(NullPointerException e)
                {
                        throw new NullPointerException("response is null");
                }
        }

        public boolean isResponded()
        {
                return isResponded;
        }

        public int getPort()
        {
                return port;
        }

        public HashMap getRequestHeaders()
        {
                return headermap;
        }
                             
        public boolean isConnectedClient()
        {
                return isConnect;
        }
}
                                                                                                                                                             

