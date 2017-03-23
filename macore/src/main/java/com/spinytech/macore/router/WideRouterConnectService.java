package com.spinytech.macore.router;

import android.app.Service;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.google.protobuf.ByteString;
import com.spinytech.macore.IWideRouterAIDL;
import com.spinytech.macore.MaActionResult;
import com.spinytech.macore.MaApplication;
import com.spinytech.macore.RouteProto;
import com.spinytech.macore.Threads;
import com.spinytech.macore.tools.Logger;
import com.spinytech.macore.tools.ProcessUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Created by wanglei on 2016/11/29.
 */

public final class WideRouterConnectService extends Service {
    private static final String TAG = "WideRouterConnectService";
    public static final String LOCALADDRESS = "com.spinytech.macore.wideRouteConnectServer";
    public static HashMap<String, OutputStream> localSockets = new HashMap<>() ;
    public static AtomicBoolean isRunningReadingSocket = new AtomicBoolean(true);

    public WideRouterConnectService() {
        Logger.i(TAG, "WideRouterConnectService()");
        startLocalSocketServer();
    }

    @Override
    public void onCreate() {
        Logger.i(TAG, "onCreate");
        super.onCreate();
        if (!(getApplication() instanceof MaApplication)) {
            throw new RuntimeException("Please check your AndroidManifest.xml and make sure the application is instance of MaApplication.");
        }
        WideRouter.getInstance(MaApplication.getMaApplication()).startAllRegisiterLocalRouter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        String domain = intent.getStringExtra("domain");
        if (WideRouter.getInstance(MaApplication.getMaApplication()).mIsStopping) {
            Logger.e(TAG, "Bind error: The wide router is stopping.");
            return null;
        }
        if (domain != null && domain.length() > 0) {
            boolean hasRegistered = WideRouter.getInstance(MaApplication.getMaApplication()).checkLocalRouterHasRegistered(domain);
            if (!hasRegistered) {
                Logger.e(TAG, "Bind error: The local router of process " + domain + " is not bidirectional." +
                        "\nPlease create a Service extend LocalRouterConnectService then register it in AndroidManifest.xml and the initializeAllProcessRouter method of MaApplication." +
                        "\nFor example:" +
                        "\n<service android:name=\"XXXConnectService\" android:process=\"your process name\"/>" +
                        "\nWideRouter.registerLocalRouter(\"your process name\",XXXConnectService.class);");
                return null;
            }
            WideRouter.getInstance(MaApplication.getMaApplication()).connectLocalRouter(domain);
        } else {
            Logger.e(TAG, "Bind error: Intent do not have \"domain\" extra!");
            return null;
        }

        return null;
    }

    public void startLocalSocketServer() {
        Logger.i(TAG, "start wide socket ");
        new Thread() {
            @Override
            public void run() {
                try {
                    Logger.i(TAG, "start wide socket ");
                    LocalServerSocket localServerSocket = new LocalServerSocket(LOCALADDRESS);
                    while (true) {
                        // block read
                        LocalSocket serverSocket = localServerSocket.accept();
                        int pid = serverSocket.getPeerCredentials().getPid();
                        String processName = ProcessUtil.getProcessName(pid);

                        Logger.i(TAG, "processName = " + processName);
                        InputStream inputStream = serverSocket.getInputStream();
                        OutputStream outputStream = serverSocket.getOutputStream();
                        ReadSocket readSocket = new ReadSocket(inputStream, outputStream);
                        Threads.submit(readSocket);

                        Thread.sleep(1000);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }.start();

    }
    public void show(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            stringBuffer.append(bytes[i]);
        }

        Logger.i(TAG, "LocalRoute receive bytes ..."+ stringBuffer.toString());
    }
    class ReadSocket implements Runnable {
        private InputStream inputStream;
        private OutputStream outputStream;

        public ReadSocket(InputStream inputStream, OutputStream outputStream ) {
            this.inputStream = inputStream;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {

            // client outStream has cloead
            byte[] s = new byte[1024];
            int length = 0;
            while (isRunningReadingSocket.get()) {
                try {
                        Logger.i(TAG, "WideRouteConnectService receive bytes 1");
                        Thread.sleep(1000);
                        length = inputStream.read(s);
                        ByteString bytes1 = ByteString.copyFrom(s, 0, length);
                        show(bytes1.toByteArray());
                        Logger.i(TAG, "WideRouteConnectService receive bytes 9"+  bytes1);
                        RouteProto.RouteMessage routeMessage = RouteProto.RouteMessage.parseFrom(bytes1);

                        Logger.i(TAG, "WideRouteConnectService receive 5 bytes routeRequest" + routeMessage.toString());

                    if (routeMessage.getConnectType() == RouteProto.RouteMessage.ConnectType.ConnectWideService) {
                        localSockets.put(routeMessage.getFromDomain(), outputStream);
                        Logger.i(TAG, "WideRouteConnectService receive 2 routeRequest" + routeMessage.toString());

                    } else if (routeMessage.getConnectType() == RouteProto.RouteMessage.ConnectType.SendRouteRequest) {
                        Logger.i(TAG, "WideRouteConnectService receive3  routeRequest" + routeMessage.toString());
                        OutputStream outputStream = localSockets.get(routeMessage.getDomain());
                        if (outputStream != null) {
                            Logger.i(TAG, "WideRouteConnectService receive3 + 1 routeRequest" + routeMessage.toString());
                            outputStream.write(bytes1.toByteArray());
                            outputStream.flush();
                        }
                    } else if (routeMessage.getConnectType() == RouteProto.RouteMessage.ConnectType.ReceiveRouteResult) {
                        Logger.i(TAG, "WideRouteConnectService receive3  routeRequest" + routeMessage.toString());
                        OutputStream outputStream = localSockets.get(routeMessage.getDomain());
                        if (outputStream != null) {
                            outputStream.write(bytes1.toByteArray());
                            outputStream.flush();
                        }
                    }
                    Logger.i(TAG, "WideRouteConnectService receive bytes 4");
                } catch (IOException e) {
                    Logger.e(TAG, "WideRouteConnectService receive bytes 6" + e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) {
                    Logger.e(TAG, "WideRouteConnectService receive bytes 7" + e.getMessage());
                }
            }
        }
    }

    IWideRouterAIDL.Stub stub = new IWideRouterAIDL.Stub() {

        @Override
        public boolean checkResponseAsync(String domain, String routerRequest) throws RemoteException {
            return
                    WideRouter.getInstance(MaApplication.getMaApplication())
                            .answerLocalAsync(domain, routerRequest);
        }

        @Override
        public String route(String domain, String routerRequest) {
            try {
                return WideRouter.getInstance(MaApplication.getMaApplication())
                        .route(domain, routerRequest)
                        .mResultString;
            } catch (Exception e) {
                e.printStackTrace();
                return new MaActionResult.Builder()
                        .code(MaActionResult.CODE_ERROR)
                        .msg(e.getMessage())
                        .build()
                        .toString();
            }
        }

        @Override
        public boolean stopRouter(String domain) throws RemoteException {
            return WideRouter.getInstance(MaApplication.getMaApplication())
                    .disconnectLocalRouter(domain);
        }

    };
}
