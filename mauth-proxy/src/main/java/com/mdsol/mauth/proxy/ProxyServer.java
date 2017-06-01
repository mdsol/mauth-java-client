package com.mdsol.mauth.proxy;

import com.mdsol.mauth.apache.HttpClientRequestSigner;
import com.typesafe.config.ConfigFactory;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.extras.SelfSignedMitmManager;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;

public class ProxyServer {
  private final ProxyConfig proxyConfig;

  private final HttpClientRequestSigner httpClientRequestSigner;
  private HttpProxyServer httpProxyServer;

  ProxyServer(ProxyConfig proxyConfig) throws IOException, URISyntaxException {
    this.proxyConfig = proxyConfig;

    httpClientRequestSigner = new HttpClientRequestSigner(
        proxyConfig.getAppUuid(),
        proxyConfig.getPrivateKey()
    );
  }

  void serve() {
    final HttpProxyServerBootstrap bootstrap = DefaultHttpProxyServer.bootstrap().withPort(proxyConfig.getProxyPort());
    bootstrap.withManInTheMiddle(new SelfSignedMitmManager());

    httpProxyServer = bootstrap.withFiltersSource(new HttpFiltersSourceAdapter() {
      @Override
      public int getMaximumRequestBufferSizeInBytes() {
        return proxyConfig.getBufferSizeInByes();
      }

      public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext clientCtx) {
        return new MAuthForwardRequestFilter(originalRequest, httpClientRequestSigner);
      }
    })
        .start();
  }

  void stop() {
    if (httpProxyServer != null) {
      httpProxyServer.stop();
    }
  }

  int getPort() {
    return httpProxyServer.getListenAddress().getPort();
  }

  public static void main(String[] args) {
    try {
      new ProxyServer(new ProxyConfig(ConfigFactory.load().resolve())).serve();
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
    }
  }
}