package com.sforce.dataset;

import com.sforce.soap.partner.CallOptions_element;
import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.GetUserInfoResult;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.fault.LoginFault;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

import com.sforce.dataset.ProxyParams;
import com.sforce.dataset.util.HttpClientTransport;
import com.sforce.dataset.util.SessionRenewerImpl;

public class PartnerConnectManager{
	private PartnerConnection connection;
	private static PartnerConnectManager connectManager;
	private String userName;
	private String password;
	private ProxyParams proxySettings;
	private String token;
	private String sessionId;
	private String endPoint;
	private static ConnectorConfig connConfig;
	
	PartnerConnectManager(String userName, String password,String token, String endPoint, String sessionId, ProxyParams proxySettings) {
		this.userName = userName;
		this.password = password;
		this.proxySettings = proxySettings;
		this.token = token;
		this.sessionId = sessionId;
		this.endPoint = endPoint;
	}
	
	public static PartnerConnectManager initialize(String userName, String password,String token, String endPoint, String sessionId, ProxyParams proxySettings) {

			connectManager = new PartnerConnectManager(userName, password, token, endPoint, sessionId, proxySettings);
			return connectManager;
		
	}
	
	public static PartnerConnectManager getInstance() {
		if(connectManager == null){
			throw new AssertionError("initialize method not invoked first");
		}
		
		return connectManager;
	}
	
	public static ConnectorConfig getConnectorConfig(){
		return connConfig;
	}
	
	private static void setConnectorConfig(ConnectorConfig config) {
		connConfig = config;
	}
	
	
	/**
	 * Login.
	 *
	 * @param retryCount the retry count
	 * @param username the username
	 * @param password the password
	 * @param token the token
	 * @param endpoint the endpoint
	 * @param sessionId the session id
	 * @param debug the debug
	 * @return the partner connection
	 * @throws ConnectionException the connection exception
	 * @throws MalformedURLException the malformed url exception
	 */
	public PartnerConnection login(int retryCount, boolean debug) throws ConnectionException, MalformedURLException  {
		
		
		try {
			ConnectorConfig config = getConfig();
			
			setConnectorConfig(config);

			
			PartnerConnection connection = Connector.newConnection(config);
			

	
			//Set the clientId
			CallOptions_element co = new CallOptions_element();
		    co.setClient(DatasetUtilConstants.clientId);
		    connection.__setCallOptions(co);
		    
		    
			GetUserInfoResult userInfo = connection.getUserInfo();
			
			// if the execution has come this far without failing
			// it means that connection is successfully established
			System.out.println("\nLogging in ...");
			System.out.println("Service Endpoint: " + config.getServiceEndpoint());
			if(debug)
				System.out.println("SessionId: " + config.getSessionId()+"\n");
			
			// return the connection
			return connection;
			
		}catch (ConnectionException e) {	
			System.out.println(e.getClass().getCanonicalName());
			e.printStackTrace();
			boolean retryError = true;	
			if(e instanceof LoginFault || sessionId != null)
				retryError = false;
			if(retryCount<3 && retryError)
			{
				retryCount++;
				try {
					Thread.sleep(1000*retryCount);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				return this.login(retryCount,debug);
			}
			throw new ConnectionException(e.toString());
		}
	}
	
    /**
     * Gets the connector config.
	 *
	 * @return the connector config
	 */
	protected ConnectorConfig getConfig() throws MalformedURLException{
	        ConnectorConfig cc = new ConnectorConfig();
	        cc.setTransport(HttpClientTransport.class);
	        
	        String pswd = this.password;
			String endpnt = this.endPoint;
			
			if(sessionId==null)
			{
				if (this.userName == null || this.userName.isEmpty()) {
					throw new IllegalArgumentException("username is required");
				}
		
				if (this.password == null || this.password.isEmpty()) {
					throw new IllegalArgumentException("password is required");
				}

				if (token != null)
					pswd = this.password + this.token;
			}

			if (this.endPoint == null || this.endPoint.isEmpty()) {
				endpnt = DatasetUtilConstants.defaultEndpoint;
			}
			
			if(sessionId != null && !sessionId.isEmpty())
			{
				while(endpnt.toLowerCase().contains("login.salesforce.com") || endpnt.toLowerCase().contains("test.salesforce.com") || endpnt.toLowerCase().contains("test") || endpnt.toLowerCase().contains("prod") || endpnt.toLowerCase().contains("sandbox"))
				{
					throw new IllegalArgumentException("ERROR: endpoint must be the actual serviceURL and not the login url");
				}
			}
			
			URL uri = new URL(endpnt);
			String protocol = uri.getProtocol();
			String host = uri.getHost();
			if(protocol == null || !protocol.equalsIgnoreCase("https"))
			{
				if(host == null || !(host.toLowerCase().endsWith("internal.salesforce.com") || host.toLowerCase().endsWith("localhost")))
				{
					System.out.println("\nERROR: Invalid endpoint. UNSUPPORTED_CLIENT: HTTPS Required in endpoint");
					System.exit(-1);
				}
			}
			
			if(uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().equals("/"))
			{
				uri = new URL(uri.getProtocol(), uri.getHost(), uri.getPort(), DatasetUtilConstants.defaultSoapEndPointPath); 
			}
			endpnt = uri.toString();
	        
	        // proxy properties
	        try {
	        	com.sforce.dataset.Config conf = DatasetUtilConstants.getSystemConfig();
	            if (this.proxySettings.proxyIP != null && this.proxySettings.proxyIP.length() > 0 && this.proxySettings.proxyPort > 0) {
	                cc.setProxy(this.proxySettings.proxyIP, this.proxySettings.proxyPort);
	
	                if (this.proxySettings.proxyUserName != null && this.proxySettings.proxyUserName.length() > 0) {
	                    cc.setProxyUsername(this.proxySettings.proxyUserName);
	
	                    if (this.proxySettings.proxyPassword != null && this.proxySettings.proxyPassword.length() > 0) {
	                        cc.setProxyPassword(this.proxySettings.proxyPassword);
	                    } else {
	                        cc.setProxyPassword("");
	                    }
	                }
	                /*
	                if (conf.proxyNtlmDomain != null && conf.proxyNtlmDomain.length() > 0) {
	                    cc.setNtlmDomain(conf.proxyNtlmDomain);
	                }
	                */
	            }
	            
				if(sessionId!=null)
				{
					cc.setServiceEndpoint(endpnt);
					cc.setSessionId(sessionId);
				}else
				{
					cc.setUsername(this.userName);
					cc.setPassword(pswd);
					cc.setAuthEndpoint(endpnt);
					cc.setSessionRenewer(new SessionRenewerImpl(this.userName, password, null, endpnt));
				}
	
	            // Time out after 5 seconds for connection
	            cc.setConnectionTimeout(conf.connectionTimeoutSecs * 1000);
	
	            // Time out after 1 minute 10 sec for login response
	            cc.setReadTimeout((conf.timeoutSecs * 1000));
	
	            // use compression or turn it off
	            cc.setCompression(!conf.noCompression);
	
	            if (conf.debugMessages) {
	                cc.setTraceMessage(true);
	                cc.setPrettyPrintXml(true);
	                    try {
	                        cc.setTraceFile(DatasetUtilConstants.getDebugFile().getAbsolutePath());
	                    } catch (Throwable e) {
	                    	e.printStackTrace();
	                    }
	            }
	        
	        } catch (Throwable e) {
	            e.printStackTrace();
	        }
	
	//        String server = getSession().getServer();
	//        if (server != null) {
	//            cc.setAuthEndpoint(server + DEFAULT_AUTH_ENDPOINT_URL.getPath());
	//            cc.setServiceEndpoint(server + DEFAULT_AUTH_ENDPOINT_URL.getPath());
	//            cc.setRestEndpoint(server + REST_ENDPOINT);
	//        }
	
	        return cc;
	    }
	
}
