package com.spinytech.macore.tools;


import com.google.protobuf.ByteString;
import com.spinytech.macore.MaApplication;
import com.spinytech.macore.RouteProto;
import com.spinytech.macore.router.RouterRequest;
import com.spinytech.macore.router.RouterResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

/**
 * Created by wanglei on 2016/12/27.
 */

public class RouterMessageUtil {

    public static RouteProto.RouteMessage routerMessage2Proto(RouterRequest routerRequest) throws IOException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(bos);
        os.writeObject(routerRequest.getAndClearObject());
        os.close();

        return RouteProto.RouteMessage
                .newBuilder()
                .setId(System.currentTimeMillis())
                .setFromDomain(routerRequest.getFrom())
                .setDomain(routerRequest.getDomain())
                .setConnectType(RouteProto.RouteMessage.ConnectType.SendRouteRequest)
                .setRequest(RouteProto.RouterRequest
                        .newBuilder()
                        .setProvider(routerRequest.getProvider())
                        .setAction(routerRequest.getAction())
                        .putAllDatas(routerRequest.getData())
                        .setObject(ByteString.copyFrom(bos.toByteArray()))

                ).build();


    }


    public static RouteProto.RouteMessage routerMessage2Proto(RouterResponse response) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(byteArrayOutputStream);
        os.writeObject(response.getObject());
        os.close();

        return RouteProto.RouteMessage
                .newBuilder()
                .setId(System.currentTimeMillis())
                .setResult(RouteProto.RouterResult
                        .newBuilder()
                        .setResultCode(response.getCode())
                        .setObject(ByteString.copyFrom(byteArrayOutputStream.toByteArray()))
                ).build();


    }

    public static RouterResponse protoMessage2ReceiveRouterMessage(RouteProto.RouteMessage routeMessage) throws NoSuchFieldException, IllegalAccessException {
        RouterResponse routerResponse = new RouterResponse();
        Class<? extends RouterResponse> aClass = routerResponse.getClass();
        if (routeMessage.hasResult()) {
            RouteProto.RouterResult result = routeMessage.getResult();
            Field mCode = aClass.getDeclaredField("mCode");
            mCode.setAccessible(true);
            mCode.setInt(routerResponse, result.getResultCode());

            Field mMessage = aClass.getDeclaredField("mMessage");
            mMessage.setAccessible(true);
            mMessage.set(routerResponse, result.getMessage());


            Field mData = aClass.getDeclaredField("mData");
            mData.setAccessible(true);
            mData.set(routerResponse, result.getData());

            Field mObject = aClass.getDeclaredField("mObject");
            mObject.setAccessible(true);
            mObject.set(routerResponse, result.getObject());

        }


        return routerResponse;
    }
    public static RouterRequest protoMessage2RequestRouterMessage(RouteProto.RouteMessage routeMessage) throws NoSuchFieldException, IllegalAccessException {
        RouterRequest routerRequest = new RouterRequest.Builder(MaApplication.getMaApplication())
                .provider(routeMessage.getRequest().getProvider())
                .action(routeMessage.getRequest().getAction())
                .build();
        Class<? extends RouterRequest> aClass = routerRequest.getClass();
        if (routeMessage.hasRequest()) {
            Field from = aClass.getDeclaredField("from");
            from.setAccessible(true);
            from.set(routerRequest, routeMessage.getFromDomain());

            Field domain = aClass.getDeclaredField("domain");
            domain.setAccessible(true);
            domain.set(routerRequest, routeMessage.getDomain());

        }

        return routerRequest;
    }

}
