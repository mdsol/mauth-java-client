package com.mdsol.mauth.util;

import com.mdsol.mauth.MAuthVersion;
import com.mdsol.mauth.exceptions.MAuthSigningException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.CryptoException;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.encodings.PKCS1Encoding;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MAuthSignatureHelper {

  private static final Logger logger = LoggerFactory.getLogger(MAuthSignatureHelper.class);

  /**
   * Generate string_to_sign for Mauth V1
   * @param appUUID: app uuid
   * @param httpMethod: Http_Verb
   * @param resourceUrl: resource_url_path (no host, port or query string; first "/" is included)
   * @param requestBody: request body string
   * @param epochTime: current seconds since Epoch
   * @return String
   *   httpMethod + "\n" + resourceUrl + "\n" + requestBody + "\n" + app_uuid + "\n" + epochTime
   *
   */
  public static String generateUnencryptedSignature(UUID appUUID, String httpMethod, String resourceUrl, String requestBody, String epochTime) {
    logger.debug("Generating String to sign for V1");
    return httpMethod + "\n" + resourceUrl + "\n" + requestBody + "\n" + appUUID.toString() + "\n" + epochTime;
  }

  /**
   * Generate string_to_sign for Mauth V2
   * @param appUUID: app uuid
   * @param httpMethod: Http_Verb
   * @param resourceUrl: resource_url_path (no host, port or query string; first "/" is included)
   * @param queryParameters: request parameters string
   * @param requestBody: request body string
   * @param epochTime: current seconds since Epoch
   * @return String
   *   httpMethod + "\n" + resourceUrl + "\n" + requestBody_digest + "\n" + app_uuid + "\n" + epochTime + "\n" + encoded_queryParameters
   *
   * @throws MAuthSigningException
   */
  public static String generateStringToSignV2(UUID appUUID, String httpMethod, String resourceUrl,
      String queryParameters, String requestBody, String epochTime) throws MAuthSigningException{
    logger.debug("Generating String to sign for V2");

    String bodyDigest;
    String encryptedQueryParams;
    try {
      bodyDigest = getHexEncodedDigestedString(requestBody);
      encryptedQueryParams = generateEncryptedQueryParams(queryParameters);
    } catch (IOException e) {
      logger.error("Error generating Unencrypted Signature", e);
      throw new MAuthSigningException(e);
    }
    return httpMethod + "\n" + resourceUrl + "\n" + bodyDigest + "\n" + appUUID.toString() + "\n" + epochTime + "\n" + encryptedQueryParams;
  }

  /**
   * Generate string_to_sign for Mauth
   * @param appUUID: app uuid
   * @param httpMethod: Http_Verb
   * @param resourceUrl: resource_url_path (no host, port or query string; first "/" is included)
   * @param queryParameters: request parameters string
   * @param requestBody: request body string
   * @param epochTime: current seconds since Epoch
   * @param mauthVersion: Mauth version (MSW or MSWV2)
   * @return String
   *
   * @throws MAuthSigningException
   */
  public static String generateStringToSign(UUID appUUID, String httpMethod, String resourceUrl,
      String queryParameters, String requestBody, String epochTime, String mauthVersion) throws MAuthSigningException{
    String strTosign;
    if (mauthVersion.equalsIgnoreCase(MAuthVersion.MWS.getValue())) {
      strTosign = generateUnencryptedSignature(appUUID, httpMethod, resourceUrl, requestBody, epochTime);
    }
    else {
      strTosign = generateStringToSignV2(appUUID, httpMethod, resourceUrl, queryParameters, requestBody, epochTime);
    }
    return strTosign;
  }

  public static String encryptSignature(PrivateKey privateKey, String unencryptedString) throws IOException, CryptoException {
    String hexEncodedString = getHexEncodedDigestedString(unencryptedString);

    PKCS1Encoding encryptEngine = new PKCS1Encoding(new RSAEngine());
    encryptEngine.init(true, PrivateKeyFactory.createKey(privateKey.getEncoded()));
    byte[] encryptedStringBytes = encryptEngine.processBlock(hexEncodedString.getBytes(), 0,hexEncodedString.getBytes().length);

    return new String(Base64.encodeBase64(encryptedStringBytes), "UTF-8");
  }

  public static byte[] decryptSignature(PublicKey publicKey, String encryptedSignature) {
    try {
      // Decode the signature from its base 64 form
      byte[] decodedSignature = Base64.decodeBase64(encryptedSignature);

      // Decrypt the signature with public key from requesting application
      PKCS1Encoding decryptEngine = new PKCS1Encoding(new RSAEngine());
      decryptEngine.init(false, PublicKeyFactory.createKey(publicKey.getEncoded()));
      byte[] decryptedSignature;
      decryptedSignature = decryptEngine.processBlock(decodedSignature, 0, decodedSignature.length);

      return decryptedSignature;
    } catch (InvalidCipherTextException | IOException ex) {
      final String msg = "Couldn't decrypt the signature using given public key.";
      logger.error(msg, ex);
      throw new MAuthSigningException(msg, ex);
    }
  }

  public static String getHexEncodedDigestedString(String unencryptedString) {
    try {
      // Get digest
      MessageDigest md = MessageDigest.getInstance("SHA-512");
      byte[] digestedString = md.digest(unencryptedString.getBytes(StandardCharsets.UTF_8));
      // Convert to hex
      return Hex.encodeHexString(digestedString);
    } catch (NoSuchAlgorithmException ex) {
      final String message = "Invalid alghoritm or security provider.";
      logger.error(message, ex);
      throw new MAuthSigningException(message, ex);
    }
  }

  public static String generateEncryptedQueryParams(String query) throws IOException {

    if (query == null || query.isEmpty())
      return "";

    StringBuilder encryptedQueryParams = new StringBuilder();

    String[] params = query.split("&");
    Arrays.sort(params);
    Map<String, String> map = new HashMap<String, String>();
    for (String param : params)
    {
      String name = param.split("=")[0];
      String value = param.split("=")[1];
      if(encryptedQueryParams.length() > 0){
        encryptedQueryParams.append('&');
      }
      encryptedQueryParams.append(urlEncodeValue(name)).append('=').append(urlEncodeValue(value));
    }

    return encryptedQueryParams.toString();
  }

  /**
   * encode a string value using `UTF-8` encoding scheme
   * @param value
   * @return encoded String
   *
   * See https://stackoverflow.com/questions/10786042/java-url-encoding-of-query-string-parameters
   */
  private static String urlEncodeValue(String value) {
    try {
      return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex.getCause());
    }
  }

}
