
package livebeansserver;

import java.rmi.RemoteException;
import java.util.HashMap;


class ClientChecker implements Runnable
{

    private static ClientChecker _instance;

    public static ClientChecker getInstance()
    {
        if (_instance == null)
        {
            _instance = new ClientChecker();
        }

        return _instance;
    }
    private final int _checkTime = 5;

    private ClientChecker()
    {
    }

    @Override
    public void run()
    {
        try
        {
            LiveBeansServer serverInstance = LiveBeansServer.getInstance();
            HashMap<Integer, Long> clientHeartbeats
                                   = serverInstance.getClientHeartbeats();

            for (HashMap.Entry<Integer, Long> heartbeat
                 : clientHeartbeats.entrySet())
            {
                if ((System.nanoTime() - heartbeat.getValue())
                    / 1000000000 > _checkTime)
                {
                    System.out.println("[SERVER-LOG] Found a disconnected/crashed"
                                       + " client, removing...");

                    serverInstance.unRegisterClient(heartbeat.getKey());
                }
            }
        }
        catch (RemoteException ex)
        {
            System.out.println("[SERVER-ERROR] There was a problem updating the client list:\r\n\r\n" + ex.toString());
        }
    }
}
