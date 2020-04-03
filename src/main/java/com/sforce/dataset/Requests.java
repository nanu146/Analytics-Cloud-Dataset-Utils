package com.sforce.dataset;

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.util.EntityUtils;

import com.sforce.ws.util.Base64;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import com.sforce.dataset.RequestResponse;
import com.sforce.dataset.ProxyParams;

public class Requests {

	private RequestBuilder setPost() throws ProtocolException {

		RequestBuilder requestbuilder = RequestBuilder.post();
		return requestbuilder;
	}

	private RequestBuilder setGet() throws ProtocolException {
		RequestBuilder requestbuilder = RequestBuilder.get();
		return requestbuilder;
	}

	private RequestBuilder setPut() throws ProtocolException {
		RequestBuilder requestbuilder = RequestBuilder.put();
		return requestbuilder;
	}

	private RequestBuilder setPatch() throws ProtocolException {
		RequestBuilder requestbuilder = RequestBuilder.patch();
		return requestbuilder;
	}

	private RequestBuilder setDelete() throws ProtocolException {
		RequestBuilder requestbuilder = RequestBuilder.delete();
		return requestbuilder;
	}


	private InputStreamReader getStreamReader(InputStream stream) {
		return new InputStreamReader(stream);
	}
	

	private String getStringResponse(InputStreamReader IStreamReader) {

		StringBuilder response = new StringBuilder();

		try (BufferedReader br = new BufferedReader(IStreamReader)) {

			String responseLine = null;
			while ((responseLine = br.readLine()) != null) {
				response.append(responseLine.trim());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return response.toString();
	}

	private HttpHost getProxy() {
		ProxyParams proxySettings = ProxyParams.getInstance();

		HttpHost proxyHost = new HttpHost(proxySettings.proxyIP, proxySettings.proxyPort, "http");

		return proxyHost;
	}

	private HashMap<String, String> handleHeaders(HashMap<String, String> httpHeaders) {

		httpHeaders.put("Content-Type", "application/json; utf-8");
		httpHeaders.put("Accept", "application/json");

		return httpHeaders;
	}

	private RequestBuilder setHeaders(RequestBuilder requestBuilder, HashMap<String, String> httpHeaders) {

		for (String header : httpHeaders.keySet()) {
			requestBuilder = requestBuilder.setHeader(header, httpHeaders.get(header));
		}

		return requestBuilder;
	}

	private CredentialsProvider createProxycreds(HttpHost proxyHost) {
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		
		ProxyParams proxySettings = ProxyParams.getInstance();

		// Setting the credentials
		credsProvider.setCredentials(new AuthScope(proxyHost.getHostName(), proxyHost.getPort()),
				new UsernamePasswordCredentials(proxySettings.proxyUserName, proxySettings.proxyPassword));

		return credsProvider;

	}

	private HttpClientBuilder setProxyCredentials(HttpClientBuilder clientbuilder, CredentialsProvider credsProvider) {
		// Setting the credentials
		clientbuilder = clientbuilder.setDefaultCredentialsProvider(credsProvider);
		return clientbuilder;
	}

	private HttpUriRequest configRequest(String url, String data, RequestConfig config, String method,
			HashMap<String, String> httpHeaders) throws Exception {
		RequestBuilder requestbuilder;

		if (method.equalsIgnoreCase("get")) {
			requestbuilder = setGet();
		} else if (method.equalsIgnoreCase("post")) {
			requestbuilder = setPost();
		} else if (method.equalsIgnoreCase("put")) {
			requestbuilder = setPut();
		} else if (method.equalsIgnoreCase("patch")) {
			requestbuilder = setPatch();
		} else {
			requestbuilder = setDelete();
		}

		requestbuilder = requestbuilder.setUri(url);
		requestbuilder = setHeaders(requestbuilder, httpHeaders);
		
		if(!data.isBlank()) {
			requestbuilder = requestbuilder.setEntity(new StringEntity(data));
		}

		requestbuilder = requestbuilder.setConfig(config);

		return requestbuilder.build();
	}

	public RequestResponse<InputStream> Call(URL url, HashMap<String, String> httpHeaders, String data, String method) throws Exception {

		RequestResponse<InputStream> streamResponse = new RequestResponse<InputStream>(); 
		//Creating the HttpClientBuilder
		HttpClientBuilder clientbuilder = HttpClients.custom();
		
		httpHeaders = handleHeaders(httpHeaders);

		// handling proxy settings
		ProxyParams proxySettings = ProxyParams.getInstance();
		HttpHost proxy = null;

		if (proxySettings.proxyUserName != null) {
			proxy = getProxy();
			CredentialsProvider creds = createProxycreds(proxy);
			clientbuilder = setProxyCredentials(clientbuilder, creds);

		}
		//Create the target host
		HttpHost targetHost = new HttpHost(url.getHost(), url.getPort(), "https");

		//creating request builder
		RequestConfig.Builder reqconfigconbuilder = RequestConfig.custom();

		//setting proxy if exists
		if (proxySettings.proxyUserName != null) {
			reqconfigconbuilder = reqconfigconbuilder.setProxy(proxy);
		}

		RequestConfig config = reqconfigconbuilder.build();

		HttpRequest request = configRequest(url.getFile(),data, config, method, httpHeaders);

		// Building the CloseableHttpClient object
		CloseableHttpClient httpclient = clientbuilder.build();

		// Printing the status line
		HttpResponse response = httpclient.execute(targetHost, request);
		System.out.println(response.getStatusLine());
		
		streamResponse.response = response.getEntity().getContent();
		streamResponse.status = response.getStatusLine().getStatusCode();
		
		return streamResponse;

	}

	public RequestResponse<String> Post(URL url, HashMap<String, String> httpHeaders, String data) throws Exception {

		RequestResponse<InputStream> IStream = Call(url, httpHeaders, data, "POST");

		//GZIPInputStream GStream = getGZipInputStream(IStream.response);

		InputStreamReader IStreamReader = this.getStreamReader(IStream.response);

		String response = getStringResponse(IStreamReader);
		RequestResponse<String> reqResponse = new RequestResponse<String>();

		reqResponse.response = response;
		reqResponse.status = IStream.status;

		return reqResponse;

	}

	public RequestResponse<String> Get(URL connection, HashMap<String, String> httpHeaders, String data) throws Exception {

		RequestResponse<InputStream> IStream = Call(connection, httpHeaders, data, "GET");

		//GZIPInputStream GStream = getGZipInputStream(IStream.response);

		InputStreamReader IStreamReader = this.getStreamReader(IStream.response);

		String response = getStringResponse(IStreamReader);
		RequestResponse<String> reqResponse = new RequestResponse<String>();

		reqResponse.response = response;
		reqResponse.status = IStream.status;

		return reqResponse;

	}

}
