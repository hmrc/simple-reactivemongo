/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mongo

import java.net.InetSocketAddress
import java.util.concurrent.{ExecutorService, Executors}
import java.util.concurrent.atomic.AtomicLong

import shaded.netty.bootstrap.{ClientBootstrap, ServerBootstrap}
import shaded.netty.buffer.{ChannelBuffer, ChannelBuffers}
import shaded.netty.channel.Channels._
import shaded.netty.channel._
import shaded.netty.channel.socket.ClientSocketChannelFactory
import shaded.netty.channel.socket.nio.{NioClientSocketChannelFactory, NioServerSocketChannelFactory}

case class SleepyProxyContext(
  sb: ServerBootstrap,
  cf: ClientSocketChannelFactory,
  executor: ExecutorService,
  sleepTime: AtomicLong) {

  def setSleepTime(sleepTime: Int) {
    this.sleepTime.getAndSet(sleepTime)
  }

  def shutDown() {
    sb.shutdown()
    cf.shutdown()
    executor.shutdownNow
  }
}

object SleepyProxy {
  private val sleepTime: AtomicLong = new AtomicLong(0)

  def start(localPort: Int, remotePort: Int, remoteHost: String): SleepyProxyContext = {
    val executor: ExecutorService      = Executors.newCachedThreadPool
    val sb: ServerBootstrap            = new ServerBootstrap(new NioServerSocketChannelFactory(executor, executor))
    val cf: ClientSocketChannelFactory = new NioClientSocketChannelFactory(executor, executor)

    val pipelineFactory: ChannelPipelineFactory = new ChannelPipelineFactory() {
      @throws(classOf[Exception])
      def getPipeline: ChannelPipeline = {
        val p: ChannelPipeline = pipeline
        p.addLast("handler", new SleepyProxyInboundHandler(cf, remoteHost, remotePort, sleepTime))
        p
      }
    }
    sb.setPipelineFactory(pipelineFactory)
    val thread: Thread = new Thread(new Runnable() {
      def run {
        sb.bind(new InetSocketAddress(localPort))
      }
    })
    thread.start()

    new SleepyProxyContext(sb, cf, executor, sleepTime)
  }
}

class SleepyProxyInboundHandler(
  cf: ClientSocketChannelFactory,
  remoteHost: String,
  remotePort: Int,
  sleepTime: AtomicLong)
    extends SimpleChannelUpstreamHandler {
  @volatile
  private var outboundChannel: Channel = null

  @throws(classOf[Exception])
  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    val inboundChannel: Channel = e.getChannel
    inboundChannel.setReadable(false)

    val cb: ClientBootstrap = new ClientBootstrap(cf)
    cb.getPipeline.addLast("handler", new OutboundHandler(e.getChannel))

    val f: ChannelFuture = cb.connect(new InetSocketAddress(remoteHost, remotePort))
    outboundChannel = f.getChannel
    f.addListener(new ChannelFutureListener() {
      @throws(classOf[Exception])
      def operationComplete(future: ChannelFuture) {
        if (future.isSuccess) {
          inboundChannel.setReadable(true)
        } else {
          inboundChannel.close
        }
      }
    })
  }

  @throws(classOf[Exception])
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val msg: ChannelBuffer = e.getMessage.asInstanceOf[ChannelBuffer]
    try {
      Thread.sleep(this.sleepTime.get)
    } catch {
      case e1: InterruptedException => {
        e1.printStackTrace
      }
    }
    outboundChannel.write(msg)
  }

  @throws(classOf[Exception])
  override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
    if (outboundChannel != null) {
      closeOnFlush(outboundChannel)
    }
  }

  @throws(classOf[Exception])
  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    e.getCause.printStackTrace
    closeOnFlush(e.getChannel)
  }

  private class OutboundHandler(inboundChannel: Channel) extends SimpleChannelUpstreamHandler {

    @throws(classOf[Exception])
    override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
      val msg: ChannelBuffer = e.getMessage.asInstanceOf[ChannelBuffer]
      inboundChannel.write(msg)
    }

    @throws(classOf[Exception])
    override def channelClosed(ctx: ChannelHandlerContext, e: ChannelStateEvent) {
      closeOnFlush(inboundChannel)
    }

    @throws(classOf[Exception])
    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      e.getCause.printStackTrace
      closeOnFlush(e.getChannel)
    }
  }

  /**
    * Closes the specified channel after all queued write requests are flushed.
    */
  private def closeOnFlush(ch: Channel) {
    if (ch.isConnected) {
      ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    }
  }
}
