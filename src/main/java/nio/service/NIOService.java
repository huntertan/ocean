package nio.service;

import nio.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Created by hanqing.tan on 2015/5/1.
 */
public class NIOService {
    private static final Logger logger = LoggerFactory.getLogger(NIOService.class);
    private static final int TIMEOUT = 30000;
    private static final int PORT = 8084;
    private static final int BLOCK = 4096;
    private static ByteBuffer receiveBuffer = ByteBuffer.allocate(BLOCK);
    private static ByteBuffer sendBuffer = ByteBuffer.allocate(BLOCK);

    public static void main(String[] args) {

        SocketChannel readChannel = null;
        ServerSocketChannel listenChannel = null;

        try {
            //创建一个选择器，用于监听管理事件
            Selector selector = Selector.open();
            //创建服务器socket管道，用来接收和分发socket连接
            listenChannel = ServerSocketChannel.open();
            //设置管道为非阻塞形式
            listenChannel.configureBlocking(false);
            //绑定socket端口
            listenChannel.socket().bind(new InetSocketAddress(PORT));
            //将管道注册到选择器中，注册的事件类型为OP_ACCEPT,现在selector只监听指定端口的OP_ACCEPT事件
            listenChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("running.......");


            while (true) {
                //监听事件,设置每一次监听的超时数
                if (selector.select(TIMEOUT) == 0) {
                    continue;
                }
                //事件来源列表
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    //获取一个事件
                    SelectionKey key = iter.next();

                    //删除当前事件
                    iter.remove();

                    //检查此键的通道是否已准备好接受新的套接字连接
                    if (key.isAcceptable()) {
                        System.out.println("in acceptable key readyOps : " + key.readyOps());
                        //返回此键的通道
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        //接收套接字连接，返回套接字通道
                        readChannel = server.accept();
                        //配置为非阻塞
                        readChannel.configureBlocking(false);
                        //注册到同一个Selector中
                        readChannel.register(selector, SelectionKey.OP_READ);
                    }
                    if (key.isReadable()) {
                        //返回为之创建的socket通道
                        SocketChannel channel = (SocketChannel) key.channel();
                        //清空buffer
                        receiveBuffer.clear();
                        int count = channel.read(receiveBuffer);
                        if (count > 0) {
                            String receiveText = new String(receiveBuffer.array(), 0, count);
                            //在发送的buffer中存入收到的内容
                            sendBuffer.put(receiveBuffer.array());
                            //设置监听的消息包括写消息
                            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                            System.out.println("in readable key readyOps : " + key.readyOps());
                            System.out.println("receive message : " + receiveText);

                            //在网络不阻塞的情况下，socket都是可写的
                            //保证缓存的可读性
                            sendBuffer.clear();
                            sendBuffer.put(CharsetUtil.encode("server get the input: " + receiveText).array());
                            //保证buffer的可读性
                            sendBuffer.flip();
                            SocketChannel socketChannel = (SocketChannel) key.channel();
                            if (socketChannel.isConnected()) {
                                //do something
                                while (sendBuffer.hasRemaining()) {
                                    if (socketChannel.isConnected()) {
                                        socketChannel.write(sendBuffer);
                                    }
                                }
                            }
                            sendBuffer.clear();
                        } else if (count < 0) {//socket已经断开,count == -1
                            //do nothing
                        }
                    }

                }
            }
        } catch (IOException e) {
            logger.error("nio selector open failure.", e);
        } finally {
            if (readChannel != null) {
                try {
                    readChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (listenChannel != null) {
                try {
                    listenChannel.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
