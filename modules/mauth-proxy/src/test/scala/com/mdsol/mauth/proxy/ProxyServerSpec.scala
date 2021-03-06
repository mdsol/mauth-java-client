package com.mdsol.mauth.proxy

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.mdsol.mauth.MAuthRequest
import com.typesafe.config.ConfigFactory
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPatch, HttpPost}
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.DefaultProxyRoutePlanner
import org.apache.http.{HttpHost, HttpStatus}
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProxyServerSpec extends AnyFlatSpec with Matchers with MockFactory with BeforeAndAfterAll with BeforeAndAfterEach {
  private val BASE_URL = "http://localhost"
  private val MY_RESOURCE = "/my/resource"
  private val QUERY_PARAMETERS = "k1=v1&k2=v2"
  private val MY_RESOURCE_WITH_PARAMS = MY_RESOURCE + "?" + QUERY_PARAMETERS
  private val MY_REQUEST_PAYLOAD = "{k1:v1, k2:v2}"
  var service = new WireMockServer(wireMockConfig.dynamicPort)
  val proxyServer = new ProxyServer(new ProxyConfig(ConfigFactory.load.resolve))

  override protected def beforeAll(): Unit =
    service.start()

  override def beforeEach(): Unit =
    proxyServer.serve()

  override def afterEach(): Unit =
    proxyServer.stop()

  override protected def afterAll(): Unit =
    service.stop()

  it should "correctly modify request to add MAuth headers" in {
    service.stubFor(
      get(urlEqualTo(MY_RESOURCE))
        .withHeader(MAuthRequest.X_MWS_AUTHENTICATION_HEADER_NAME, containing("MWS "))
        .withHeader(MAuthRequest.X_MWS_TIME_HEADER_NAME, matching(".*"))
        .withHeader(MAuthRequest.MCC_AUTHENTICATION_HEADER_NAME, containing("MWSV2 "))
        .withHeader(MAuthRequest.MCC_TIME_HEADER_NAME, matching(".*"))
        .willReturn(ok("success"))
    )
    val response = execute(proxyServer.getPort, new HttpGet(BASE_URL + ":" + service.port + MY_RESOURCE))
    response.getStatusLine.getStatusCode shouldBe HttpStatus.SC_OK
  }

  it should "override existing MAuth headers" in {
    val WRONG_MAUTH_HEADER = "This is a wrong Mauth Header"
    service.stubFor(
      get(urlEqualTo(MY_RESOURCE))
        .withHeader(MAuthRequest.X_MWS_AUTHENTICATION_HEADER_NAME, notMatching(WRONG_MAUTH_HEADER))
        .withHeader(MAuthRequest.X_MWS_TIME_HEADER_NAME, notMatching(WRONG_MAUTH_HEADER))
        .withHeader(MAuthRequest.MCC_AUTHENTICATION_HEADER_NAME, notMatching(WRONG_MAUTH_HEADER))
        .withHeader(MAuthRequest.MCC_TIME_HEADER_NAME, notMatching(WRONG_MAUTH_HEADER))
        .willReturn(ok("success"))
    )
    val request = new HttpGet(BASE_URL + ":" + service.port + MY_RESOURCE)
    request.addHeader(MAuthRequest.X_MWS_AUTHENTICATION_HEADER_NAME, WRONG_MAUTH_HEADER)
    request.addHeader(MAuthRequest.X_MWS_TIME_HEADER_NAME, WRONG_MAUTH_HEADER)
    request.addHeader(MAuthRequest.MCC_AUTHENTICATION_HEADER_NAME, WRONG_MAUTH_HEADER)
    request.addHeader(MAuthRequest.MCC_TIME_HEADER_NAME, WRONG_MAUTH_HEADER)
    val response = execute(proxyServer.getPort, request)
    response.getStatusLine.getStatusCode shouldBe HttpStatus.SC_OK
  }

  private def execute(proxyServerPort: Int, request: HttpGet) = {
    val proxy = new HttpHost("localhost", proxyServerPort)
    val routePlanner = new DefaultProxyRoutePlanner(proxy)
    val httpClient = HttpClients.custom.setRoutePlanner(routePlanner).build
    httpClient.execute(request)
  }

  it should "correctly modify request with parameters to add MAuth headers" in {
    service.stubFor(
      get(urlEqualTo(MY_RESOURCE_WITH_PARAMS))
        .withHeader(MAuthRequest.X_MWS_AUTHENTICATION_HEADER_NAME, containing("MWS "))
        .withHeader(MAuthRequest.X_MWS_TIME_HEADER_NAME, matching(".*"))
        .withHeader(MAuthRequest.MCC_AUTHENTICATION_HEADER_NAME, containing("MWSV2 "))
        .withHeader(MAuthRequest.MCC_TIME_HEADER_NAME, matching(".*"))
        .willReturn(ok("success"))
    )
    val response = execute(proxyServer.getPort, new HttpGet(BASE_URL + ":" + service.port + MY_RESOURCE_WITH_PARAMS))
    response.getStatusLine.getStatusCode shouldBe HttpStatus.SC_OK
  }

  it should "POST - correctly modify request with request payload" in {
    service.stubFor(
      post(urlEqualTo(MY_RESOURCE_WITH_PARAMS))
        .withRequestBody(containing(""))
        .withHeader(MAuthRequest.X_MWS_AUTHENTICATION_HEADER_NAME, containing("MWS "))
        .withHeader(MAuthRequest.X_MWS_TIME_HEADER_NAME, matching(".*"))
        .withHeader(MAuthRequest.MCC_AUTHENTICATION_HEADER_NAME, containing("MWSV2 "))
        .withHeader(MAuthRequest.MCC_TIME_HEADER_NAME, matching(".*"))
        .willReturn(created)
    )

    val response = execute(proxyServer.getPort, "post")
    response.getStatusLine.getStatusCode shouldBe HttpStatus.SC_CREATED
  }

  it should "PATCH - correctly modify request with request payload" in {
    service.stubFor(
      patch(urlEqualTo(MY_RESOURCE_WITH_PARAMS))
        .withRequestBody(containing(""))
        .withHeader(MAuthRequest.X_MWS_AUTHENTICATION_HEADER_NAME, containing("MWS "))
        .withHeader(MAuthRequest.X_MWS_TIME_HEADER_NAME, matching(".*"))
        .withHeader(MAuthRequest.MCC_AUTHENTICATION_HEADER_NAME, containing("MWSV2 "))
        .withHeader(MAuthRequest.MCC_TIME_HEADER_NAME, matching(".*"))
        .willReturn(ok("success"))
    )

    val response = execute(proxyServer.getPort, "patch")
    response.getStatusLine.getStatusCode shouldBe HttpStatus.SC_OK
  }

  it should "POST - Fail if proxy responds with 404" in {
    service.stubFor(
      post(urlEqualTo("/my/failure_resource?k=v"))
        .withHeader(MAuthRequest.X_MWS_AUTHENTICATION_HEADER_NAME, containing("MWS "))
        .withHeader(MAuthRequest.X_MWS_TIME_HEADER_NAME, matching(".*"))
        .withHeader(MAuthRequest.MCC_AUTHENTICATION_HEADER_NAME, containing("MWSV2 "))
        .withHeader(MAuthRequest.MCC_TIME_HEADER_NAME, matching(".*"))
        .willReturn(notFound)
    )

    val proxy = new HttpHost("localhost", proxyServer.getPort)
    val routePlanner = new DefaultProxyRoutePlanner(proxy)
    val httpClient = HttpClients.custom.setRoutePlanner(routePlanner).build
    val request = new HttpPost(BASE_URL + ":" + service.port + "/my/failure_resource?k=v")

    val response = httpClient.execute(request)
    response.getStatusLine.getStatusCode shouldBe HttpStatus.SC_NOT_FOUND
  }

  it should "PATCH - Fail if proxy responds with 404" in {
    service.stubFor(
      patch(urlEqualTo("/my/failure_resource?k=v"))
        .withHeader(MAuthRequest.X_MWS_AUTHENTICATION_HEADER_NAME, containing("MWS "))
        .withHeader(MAuthRequest.X_MWS_TIME_HEADER_NAME, matching(".*"))
        .withHeader(MAuthRequest.MCC_AUTHENTICATION_HEADER_NAME, containing("MWSV2 "))
        .withHeader(MAuthRequest.MCC_TIME_HEADER_NAME, matching(".*"))
        .willReturn(notFound)
    )

    val proxy = new HttpHost("localhost", proxyServer.getPort)
    val routePlanner = new DefaultProxyRoutePlanner(proxy)
    val httpClient = HttpClients.custom.setRoutePlanner(routePlanner).build
    val request = new HttpPatch(BASE_URL + ":" + service.port + "/my/failure_resource?k=v")

    val response = httpClient.execute(request)
    response.getStatusLine.getStatusCode shouldBe HttpStatus.SC_NOT_FOUND
  }

  private def execute(proxyServerPort: Int, verb: String): CloseableHttpResponse = {
    val proxy = new HttpHost("localhost", proxyServerPort)
    val routePlanner = new DefaultProxyRoutePlanner(proxy)
    val httpClient = HttpClients.custom.setRoutePlanner(routePlanner).build
    if (verb.equals("post")) {
      val request = new HttpPost(BASE_URL + ":" + service.port + MY_RESOURCE_WITH_PARAMS)
      request.setEntity(new ByteArrayEntity(MY_REQUEST_PAYLOAD.getBytes("UTF-8")))
      httpClient.execute(request)
    } else {
      val request = new HttpPatch(BASE_URL + ":" + service.port + MY_RESOURCE_WITH_PARAMS)
      request.setEntity(new ByteArrayEntity(MY_REQUEST_PAYLOAD.getBytes("UTF-8")))
      httpClient.execute(request)
    }
  }
}
