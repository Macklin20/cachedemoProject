import com.zoho.wms.asyncweb.server.AsyncWebServerAdapter;


public class MainServer
{
        private static boolean isServerStarted = false;

        public static void main(String[] args)
        {
                try
                {
                        System.setProperty("server.home" ,"/Users/macklin-ts506/cachedemo/AdventNet/aws");
                        System.out.println("Entering server block");
                        AsyncWebServerAdapter.disableConsoleLogger();
                  
                        AsyncWebServerAdapter.initialize();

                        System.out.println("Server connected");

                        setServerStatus();
                }
                catch(Exception e)
                {
                        System.out.println("Exception block in main server");
                        e.printStackTrace();
                }
        }


        public static void setServerStatus()
        {
                isServerStarted = true;
        }

        public boolean checkInit()
        {
                return isServerStarted;
        }
}
