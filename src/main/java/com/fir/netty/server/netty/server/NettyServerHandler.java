package com.fir.netty.server.netty.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fir.netty.server.netty.ChannelMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;


/**
 * @author fir
 */
@Slf4j
public class NettyServerHandler extends ChannelHandlerAdapter {


    /**
     * 设置等待用户第一条消息的超时时间，单位为秒
     */
    private static final int FIRST_MESSAGE_TIMEOUT = 5;


    /**
     * 时间格式转换器
     */
    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    /**
     * 用户名称
     */
    private static final AttributeKey<String> USER_KEY = AttributeKey.valueOf("user");


    /**
     * code 长连接通道自定义编号
     */
    private static final AttributeKey<Long> CTX_CODE = AttributeKey.valueOf("ctxCode");


    /**
     * code 长连接通道自定义编号
     */
    private static final AttributeKey<ScheduledFuture<?>> TIMEOUT = AttributeKey.valueOf("timeout");


    /**
     * 当客户端连接时保存连接通道信息
     *
     * @param ctx 通道处理上下文
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Date date = new Date();
        Channel channel = ctx.channel();
        InetSocketAddress clientAddress = (InetSocketAddress) channel.remoteAddress();
        String clientHost = clientAddress.getHostString();
        int clientPort = clientAddress.getPort();

        String datetime = FORMATTER.format(date);
        // 对每一个连接的通道，编辑一个唯一编号，用于区分每个通道在处理时的日志信息，临时使用时间戳，
        // 生产环境建议使用唯一编号生成方式(例如:雪花算法)
        long code = date.getTime();
        ctx.channel().attr(CTX_CODE).set(code);

        log.info("{}客户端;编号:{};建立连接 {}:{}", datetime, code, clientHost, clientPort);


        // 在用户连接时启动一个计时器, 如果超时后，客户端仍然没有进行鉴权验证，则关闭该用户的连接
        final ScheduledFuture<?> timeout = ctx.executor().schedule(() -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            HashMap<String, Object> map = new HashMap<>();
            map.put("code", 400);
            map.put("msg", "连接超时");
            map.put("data", new HashMap<>());
            String json = JSONObject.toJSONString(map);
            ByteBuf byteBuf = writeInfo(json);
            ctx.writeAndFlush(byteBuf);
            // 超时处理：用户未发送第一条消息，关闭连接
            ctx.close();
        }, FIRST_MESSAGE_TIMEOUT, TimeUnit.SECONDS);
        ctx.channel().attr(AttributeKey.valueOf("timeout")).set(timeout);

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Date date = new Date();
        String datetime = FORMATTER.format(date);
        // 连接关闭时触发该方法
        // 在这里您可以执行相应的处理逻辑，例如从用户列表中移除该用户
        // 或者通知其他用户该用户已离线

        String userCode = ctx.channel().attr(USER_KEY).get();
        Long code = ctx.channel().attr(CTX_CODE).get();
        if (userCode != null) {
            // 用户断开连接，您可以根据username执行相应的操作
            // 例如，从用户列表中移除该用户，通知其他用户该用户已离线等

            log.info("{}用户客户端;编号:{};断开连接:{}", datetime, code, userCode);
        } else {
            log.error("未注册客户端断开链接");
        }

    }


    /**
     * 客户端发送消息时，进行鉴权验证
     *
     * @param ctx 通道处理上下文
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel channel = ctx.channel();
        InetSocketAddress clientAddress = (InetSocketAddress) channel.remoteAddress();
        String clientHost = clientAddress.getHostString();
        int clientPort = clientAddress.getPort();
        Long code = ctx.channel().attr(CTX_CODE).get();
        String received = readInfo(msg);
        log.info("客户端;编号:{};连接成功;IP:{}:{};数据:{}", code, clientHost, clientPort, received);

        if(!isJson(received)){
            log.error("客户端;编号:{};数据格式错误", code);
            received = "";
        }

        if (ctx.channel().hasAttr(USER_KEY)) {
            String userCode = ctx.channel().attr(USER_KEY).get();
            log.info("{}用户客户端;编号:{};连接成功 {}:{}", userCode, code, clientHost, clientPort);

            JSONObject jsonObject = JSON.parseObject(received);
            log.info("业务处理:{}", jsonObject);
        } else {
            log.info("客户端;编号:{};鉴权开始 {}:{}", code, clientHost, clientPort);
            // 用户未登录，需要进行身份验证

            JSONObject jsonObject = JSON.parseObject(received);
            // 判断是否有登录权限, 此处在生产环境中, 应对token验证, 目前只判空
            boolean isLogin = jsonObject != null && jsonObject.get("token") != null;

            if (isLogin) {
                String token = (String) jsonObject.get("token");
                // 使用token得到用户id之后 将用户id赋值到通道处理中
                ctx.channel().attr(USER_KEY).set(token);

                HashMap<String, Object> map = new HashMap<>();
                map.put("code", 200);
                map.put("msg", "连接成功");
                map.put("data", new HashMap<>());
                String json = JSONObject.toJSONString(map);
                ByteBuf byteBuf = writeInfo(json);
                ctx.writeAndFlush(byteBuf);

                // 从处理链中移除此验证处理器(此时添加时，无法回调断开连接函数，故暂不使用)
//                ctx.pipeline().remove(this);


                // 保存用户连接信息
                Channel incomingChannel = ctx.channel();
                ChannelMap.addChannel(token, incomingChannel);
                log.info("{}用户客户端;编号:{};连接成功", token, code);


                // 用户连接成功时，取消超时计时器
                ScheduledFuture<?> timeout = ctx.channel().attr(TIMEOUT).get();
                if (timeout != null) {
                    timeout.cancel(false);
                    ctx.channel().attr(AttributeKey.valueOf("timeout")).remove();
                }
            } else {
                HashMap<String, Object> map = new HashMap<>();
                map.put("code", 400);
                map.put("msg", "连接失败");
                map.put("data", new HashMap<>());
                String json = JSONObject.toJSONString(map);
                ByteBuf byteBuf = writeInfo(json);
                ctx.writeAndFlush(byteBuf);
                ctx.close();
                log.info("客户端;编号:{};拒绝连接", code);
            }
        }
    }


    /**
     * 捕获和处理异常回调方法
     *
     * @param ctx 通道处理上下文
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String username = ctx.channel().attr(USER_KEY).get();
        Long code = ctx.channel().attr(CTX_CODE).get();
        cause.printStackTrace();

        HashMap<String, Object> map = new HashMap<>();
        map.put("code", 400);
        map.put("msg", "处理失败");
        map.put("data", new HashMap<>());
        String json = JSONObject.toJSONString(map);
        ByteBuf byteBuf = writeInfo(json);
        ctx.writeAndFlush(byteBuf);

        log.error("{}用户客户端;编号:{};错误:{}", username, code, cause.getMessage());
    }


    /**
     * 读取信息
     *
     * @param msg 消息体
     * @return 字符串
     */
    private String readInfo(Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        String str = "";
        try {
            str = getMessageGbk(buf);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return str;
    }


    /**
     * 写入信息
     *
     * @param str 信息体
     * @return 字节
     */
    public static ByteBuf writeInfo(String str) {
        ByteBuf buf = null;
        try {
            buf = getSendByteBufGbk(str);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return buf;
    }


    /**
     * 从ByteBuf中获取信息 使用UTF-8编码返回
     *
     * @param buf 信息体
     * @return 字符串
     */
    private String getMessageGbk(ByteBuf buf) throws UnsupportedEncodingException {

        byte[] con = new byte[buf.readableBytes()];
        buf.readBytes(con);
        return new String(con, "GBK");
    }


    private ByteBuf getSendByteBufUtf8(String message) {

        byte[] req = message.getBytes(StandardCharsets.UTF_8);
        ByteBuf pingMessage = Unpooled.buffer();
        pingMessage.writeBytes(req);

        return pingMessage;
    }


    public static ByteBuf getSendByteBufGbk(String message) throws UnsupportedEncodingException {

        byte[] req = message.getBytes("GBK");
        ByteBuf pingMessage = Unpooled.buffer();
        pingMessage.writeBytes(req);

        return pingMessage;
    }


    /**
     * 判断数据是否是JSON格式
     *
     * @param str 数据
     * @return true/false
     */
    public static boolean isJson(String str) {
        boolean result = false;
        try {
            JSON.parse(str);
            result = true;
        } catch (Exception ignored) {
        }
        return result;
    }
}