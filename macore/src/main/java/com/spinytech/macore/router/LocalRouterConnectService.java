package com.spinytech.macore.router;

import android.app.Service;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.spinytech.macore.MaApplication;
import com.spinytech.macore.RouteProto;
import com.spinytech.macore.Threads;
import com.spinytech.macore.tools.Logger;
import com.spinytech.macore.tools.ProcessUtil;
import com.spinytech.macore.tools.RouterMessageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by wanglei on 2016/11/29.
 */

public class LocalRouterConnectService extends Service {
    private static final String TAG = "LocalRouterConnectService";
    public static AtomicBoolean isRunningLocalRouteConnectService = new AtomicBoolean(true);
    private static OutputStream outputStream;
    private static HashMap<Long, ReceiveProtoListener> router = new HashMap();

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.i(TAG, "onCreate()");

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Uri data = intent.getData();
        Logger.i(TAG, "onStartCommand()" + data.toString());
        String domain = data.toString();
        Threads.submit(new LocalSocketClientConnectWideRouterConnectService(domain));
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.e("MRCS","onBind");
            return null;
    }

    class LocalSocketClientConnectWideRouterConnectService implements Runnable {
        String domain;
        public LocalSocketClientConnectWideRouterConnectService(String domain) {
            this.domain = domain;
        }

        @Override
        public void run() {
            Logger.i(TAG, "localRouter start connecting Socket...");
            LocalSocket localSocket = new LocalSocket();
            try {
                Thread.sleep(1000);
                localSocket.connect(new LocalSocketAddress(WideRouterConnectService.LOCALADDRESS));
                Logger.i(TAG, "LocalRoute connected ...=" + localSocket.isConnected());
                if (localSocket.isConnected()) {
                    outputStream = localSocket.getOutputStream();

                    RouteProto.RouteMessage routeMessage = RouteProto.RouteMessage.newBuilder().setId(System.currentTimeMillis())
                            .setFromDomain(ProcessUtil.getProcessName(ProcessUtil.getMyProcessId()))
                            .setConnectType(RouteProto.RouteMessage.ConnectType.ConnectWideService).build();

                    Logger.i(TAG, "LocalRoute connected message send ...= " + routeMessage);
                    outputStream.write(routeMessage.toByteArray());
                    outputStream.flush();
                    byte[] bytes = new byte[1024];
                    int length = -1;
                    InputStream inputStream = localSocket.getInputStream();
                    while (true) {
                        length = inputStream.read(bytes);
                        ByteString bytes1 = ByteString.copyFrom(bytes, 0, length);
                        RouteProto.RouteMessage reveiveRouterMessage = RouteProto.RouteMessage.parseFrom(bytes1);
                        Logger.i(TAG, "LocalRoute receive ...=" + reveiveRouterMessage.toString());
                        if (reveiveRouterMessage.getConnectType() == RouteProto.RouteMessage.ConnectType.ReceiveRouteResult) {
                            ReceiveProtoListener listener = router.remove(reveiveRouterMessage.getId());
                            if (listener != null) {
                                listener.onReceiveMessage(routeMessage);
                            }
                        } else if (reveiveRouterMessage.getConnectType() == RouteProto.RouteMessage.ConnectType.SendRouteRequest) {
                            try {

                                RouterResponse routerResponse = LocalRouter.getInstance(MaApplication.getMaApplication()).route(MaApplication.getMaApplication(), RouterMessageUtil.protoMessage2RequestRouterMessage(reveiveRouterMessage));
                                RouteProto.RouteMessage responcemessage = RouterMessageUtil.routerMessage2Proto(routerResponse);

                                responcemessage = responcemessage.toBuilder().setConnectType(RouteProto.RouteMessage.ConnectType.ReceiveRouteResult).setId(routeMessage.getId()).build();
                                outputStream.write(responcemessage.toByteArray());

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        Thread.sleep(1000);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public static boolean checkResponseAsync(RouterRequest routerRequest) {
        try {
            RouteProto.RouteMessage routeMessage = RouterMessageUtil.routerMessage2Proto(routerRequest);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * send message to WideRouterConnectService
     */
    public static RouterResponse sendRouteRequest2WideRouterSync(final RouterRequest routerRequest) {
        final long id= System.nanoTime();
        Logger.i(TAG, "send routeRequest 1");
        Callable<RouterResponse> callable = new Callable<RouterResponse>() {

            @Override
            public RouterResponse call() throws Exception {
                if (outputStream != null) {
                    Logger.i(TAG, "send routeRequest 2");

                    try {
                        Logger.i(TAG, "send routeRequest 3");
                        RouteProto.RouteMessage routeMessage = RouterMessageUtil.routerMessage2Proto(routerRequest);
                        routeMessage.toBuilder().setConnectType(RouteProto.RouteMessage.ConnectType.SendRouteRequest)
                                .setId(id);
                        outputStream.write(routeMessage.toByteArray());
                        outputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                ReceiveProtoListener receiveProtoListener = new ReceiveProtoListener() {

                    @Override
                    public RouterResponse onReceiveMessage(RouteProto.RouteMessage routeMessage) {
                        try {
                            return RouterMessageUtil.protoMessage2ReceiveRouterMessage(routeMessage);

                        } catch (NoSuchFieldException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        return new RouterResponse();
                    }
                };
                router.put(id, receiveProtoListener);
                return new RouterResponse();
            }
        };

        Threads.submit(callable);
        try {
            return callable.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new RouterResponse();
    }

    public interface ReceiveProtoListener {
        RouterResponse onReceiveMessage(RouteProto.RouteMessage routeMessage);
    }

    public static boolean checkConnectedWideService() {
        if (outputStream == null)
            return false;
        else
            return true;
    }

    public static void closeConnect() {
        try {
            outputStream.close();
            outputStream = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
