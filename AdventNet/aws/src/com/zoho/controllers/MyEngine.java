package com.zoho.webengine;

import com.zoho.wms.asyncweb.server.AbstractWebEngine;

public class MyEngine extends AbstractWebEngine
{
        private static boolean isEngineStarted = false;

        public void initializeEngine()
        {
                System.out.println("hii this is my web engine class");

                setWebStatus();
        }

        public static void setWebStatus()
        {
                isEngineStarted = true;
        }

        public boolean getWebStatus()
        {
                return isEngineStarted;
        }
}
