package com.zoho.controllers;

import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpServlet;
import com.zoho.wms.asyncweb.server.exception.AWSException;


import java.io.IOException;
import java.util.HashMap;

public class MyCustomServlet extends HttpServlet
{
        private HttpResponse res;
        private HttpRequest req;
        private boolean isConnect;

        public void service(HttpRequest req, HttpResponse res) throws IOException , AWSException
        {
                System.out.println("Received request");

                try
                {
                        this.req = req;
                        this.res = res;
                        writeResponse(res);

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

        public boolean isConnectedClient()
        {
                return isConnect;
        }
}
                                                                                                                                                             

