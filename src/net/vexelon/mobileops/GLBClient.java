/*
 * MyGlob Android Application
 * 
 * Copyright (C) 2010 Petar Petrov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package net.vexelon.mobileops;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

import net.vexelon.myglob.configuration.Defs;
import net.vexelon.myglob.utils.TrustAllSocketFactory;
import net.vexelon.myglob.utils.UserAgentHelper;
import net.vexelon.myglob.utils.Utils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.util.Log;

public class GLBClient implements IClient {
	
	private final String HTTP_MYTELENOR = "https://my.telenor.bg";

	private final int DEFAULT_BUFFER_SIZE = 1024;
	private final String RESPONSE_ENCODING = "windows-1251";
	private final int MAX_REQUEST_RETRIES = 2;

	private String username;
	private String password;
	private DefaultHttpClient httpClient = null;
	private CookieStore httpCookieStore = null;
	private HttpContext httpContext = null;
	
	private HashMap<GLBRequestType, String> operationsHash;
	
	private long bytesDownloaded = 0;
	private long invoiceDateTime;

	public GLBClient(String username, String password) {
		this.username = username;
		this.password = password;

		initHttpClient();
	}

	public void close() {
		if ( httpClient != null )
			httpClient.getConnectionManager().shutdown();
	}

	/**
	 * Perform login into the web system using the specified user and password
	 */
	public void login() throws HttpClientException, InvalidCredentialsException, SecureCodeRequiredException {

		try {
			List<NameValuePair> qparams = new ArrayList<NameValuePair>();
			//qparams.add(new BasicNameValuePair("refid", ""));
//			qparams.add(new BasicNameValuePair("continuation",
//					URLDecoder.decode("myglobul.portal%3Faction%3Duserhome%26pkey%3D0%26jkey%3D0",
//							"UTF-8")));
				                    
//			qparams.add(new BasicNameValuePair("image.x", Integer.toString(Utils.getRandomInt(1024)) ));
//			qparams.add(new BasicNameValuePair("image.y", Integer.toString(Utils.getRandomInt(768)) ));
			qparams.add(new BasicNameValuePair("password", password));
			qparams.add(new BasicNameValuePair("username", username));
			qparams.add(new BasicNameValuePair("_eventId","submit"));
			qparams.addAll(findLoginParams());
			
			handleLogin(qparams);
			
		} catch (UnsupportedEncodingException e) {
			throw new HttpClientException("Failed to create url! " + e.getMessage(), e);
		}
	}

	public void logout()
		throws HttpClientException {

		StringBuilder fullUrl = new StringBuilder(100);
//		fullUrl.append(HTTP_MYGLOBUL_SITE).append(GLBRequestType.LOGOUT.getPath()).append("?")
//			.append(GLBRequestType.LOGOUT.getParams());
		fullUrl.append(GLBRequestType.LOGOUT.getPath()).append("?").append(GLBRequestType.LOGOUT.getParams());

		try {
			HttpGet httpGet = new HttpGet(fullUrl.toString());
			HttpResponse resp = httpClient.execute(httpGet, httpContext);
			StatusLine status = resp.getStatusLine();

			if ( status.getStatusCode() != HttpStatus.SC_OK )
				throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());

			resp.getEntity().consumeContent();
		} catch (ClientProtocolException e) {
			throw new HttpClientException("Client protocol error!" + e.getMessage(), e);
		} catch (IOException e) {
			throw new HttpClientException("Client error!" + e.getMessage(), e);
		}
	}
	
	public String getCurrentBalance()
		throws HttpClientException {

		StringBuilder builder = new StringBuilder(100);
		HttpResponse resp;
		long bytesCount = 0;
		try {
			String url = HTTP_MYTELENOR + GLBRequestType.GET_BALANCE.getPath();
			url += '?';
			url += new Date().getTime();
			
			HttpGet httpGet = new HttpGet(url);
//			httpGet.setHeader("X-Requested-With", "XMLHttpRequest");
			resp = httpClient.execute(httpGet, httpContext);
		} catch (Exception e) {
			throw new HttpClientException("Client protocol error!" + e.getMessage(), e);
		}
		
		StatusLine status = resp.getStatusLine();
		
		if (status.getStatusCode() != HttpStatus.SC_OK)
			throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());

		try {
			HttpEntity entity = resp.getEntity();
			// bytes downloaded
			bytesCount = entity.getContentLength() > 0 ? entity.getContentLength() : 0;
			
			Document doc = Jsoup.parse(entity.getContent(), RESPONSE_ENCODING, "");
			Elements elements;
			
			// period bill
			elements = doc.select("#outstanding-amount");
			if (elements.size() > 0) {
				Elements divs = elements.get(0).select("div");
				for (Element el : divs) {
					String elClass = el.className();
					if (elClass.contains("custme-select") || elClass.equalsIgnoreCase("history")) {
						builder.insert(0,  el.html());
					}
				}	
			}
			
			// current bill
			elements = doc.select("#bars-wrapper .p-price");
			if (elements.size() > 0) {
				Element el = elements.get(0);
				builder.insert(0,  el.html());
			}			

			return builder.toString();
			
		} catch (ClientProtocolException e) {
			throw new HttpClientException("Client protocol error!" + e.getMessage(), e);
		} catch (IOException e) {
			throw new HttpClientException("Client error!" + e.getMessage(), e);
		} finally {
			addDownloadedBytesCount(bytesCount);
		}		
	}
	
	@Override
	public byte[] getInvoiceData()
			throws HttpClientException, InvoiceException {
		
		return findInvoiceExportParams();
	}
	
	@Override
	public long getInvoiceDateTime() {
		return invoiceDateTime;
	}
	
	@Override
	public long getDownloadedBytesCount() {
		if (Defs.LOG_ENABLED)
			Log.d(Defs.LOG_TAG, "Get KBs: " + bytesDownloaded / 1024.0f);
		
		return bytesDownloaded;
	}
	
	private void addDownloadedBytesCount(long bytesCount) {
		if (Defs.LOG_ENABLED)
			Log.d(Defs.LOG_TAG, "Added bytes: " + bytesCount);
		
		bytesDownloaded += bytesCount;
	}

	// ---

	/**
	 * Initialize Http Client
	 */
	private void initHttpClient() {

		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		params.setParameter(CoreProtocolPNames.USER_AGENT, UserAgentHelper.getRandomUserAgent());
		//params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, HTTP.UTF_8);
		// Bugfix #1: The target server failed to respond
		params.setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, Boolean.FALSE);

		DefaultHttpClient client = new DefaultHttpClient(params);

		httpCookieStore = new BasicCookieStore();
		client.setCookieStore(httpCookieStore);
		
		httpContext = new BasicHttpContext();
		httpContext.setAttribute(ClientContext.COOKIE_STORE, httpCookieStore);

		// Bugfix #1: Adding retry handler to repeat failed requests
		HttpRequestRetryHandler retryHandler = new HttpRequestRetryHandler() {

			@Override
			public boolean retryRequest(IOException exception, int executionCount,
					HttpContext context) {

				if (executionCount >= MAX_REQUEST_RETRIES) {
					return false;
				}

				if (exception instanceof NoHttpResponseException || exception instanceof ClientProtocolException) {
					return true;
				}

				return false;
			}
		};
		client.setHttpRequestRetryHandler(retryHandler);

		// SSL
		HostnameVerifier verifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

		try {
			SchemeRegistry registry = new SchemeRegistry();
			registry.register(new Scheme("http", new PlainSocketFactory(), 80));
			registry.register(new Scheme("https", new TrustAllSocketFactory(), 443));

			SingleClientConnManager connMgr = new SingleClientConnManager(
					client.getParams(), registry);

			httpClient = new DefaultHttpClient(connMgr, client.getParams());
		}
		catch (InvalidAlgorithmParameterException e) {
//			Log.e(Defs.LOG_TAG, "", e);

			// init without connection manager
			httpClient = new DefaultHttpClient(client.getParams());
		}

		HttpsURLConnection.setDefaultHostnameVerifier(verifier);

	}

	/**
	 * Login logic
	 * @param qparams
	 */
	private void handleLogin(List<NameValuePair> qparams) throws HttpClientException, InvalidCredentialsException, 
	SecureCodeRequiredException, UnsupportedEncodingException {

		StringBuilder fullUrl = new StringBuilder(100);
		fullUrl.append(GLBRequestType.LOGIN.getPath())
//		.append("?service=" + URLDecoder.decode(
//				"https%3A%2F%2Fmy.globul.bg%2Fmg%2Fmyglobul.portal%3Faction%3Duserhome%26pkey%3D0%26jkey%3D0", "UTF-8"));
		.append("?asid=s01&service=https%3A%2F%2Fmy.telenor.bg%2Flogin");
	
		
		HttpPost httpPost = createPostRequest(fullUrl.toString(), qparams);
		HttpResponse resp;
		BufferedReader reader = null;
		long bytesCount = 0;
		
		try {
			resp = httpClient.execute(httpPost, httpContext);
		} catch (Exception e) {
			throw new HttpClientException("Client  protocol error!" + e.getMessage(), e);
		}
		
		StatusLine status = resp.getStatusLine();

		if ( status.getStatusCode() == HttpStatus.SC_OK ) {
			try {
				reader = new BufferedReader(new InputStreamReader(resp.getEntity().getContent()));
				
				boolean loggedIn = false;
				String line = null;
				
				while((line = reader.readLine()) != null) {	
					
					bytesCount += line.length();
					
					if (line.indexOf("action=pdetails") != -1 || line.indexOf("action=chpass") != -1
							|| line.indexOf("action=logoff") != -1) {
						// if the username is not present in the content, then we're obviously not logged in
						loggedIn = true;
						break;
					}
					
					//check if secure code image is sent
					if (line.indexOf("/mg/my/GetImage?refid=") != -1 || line.indexOf("j_captcha_response") != -1) {
						//TODO: retrieve image url
						//<img class="code" alt="Ако се затруднявате с разчитането на кода от картинката, моля кликнете
						//върху нея за да я смените." src="/mg/my/GetImage?refid=b7b8fa558b461f1e2d400ae0f3348f2f">
						throw new SecureCodeRequiredException("");
					}
				}
				
				if (!loggedIn) {
					// TODO: Seems that occasionally the login response would return
					// something totally different than the welcome page.
					// So we cannot count on the markers to be always correct. We just assume
					// that we have logged in and we proceed with the actions further.
					Log.w(Defs.LOG_TAG, "Could not locate login markers!");
//					throw new InvalidCredentialsException();
				}
				
			} catch (IOException e) {
				throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());				
			} finally {
				if (reader != null) try { reader.close(); } catch (IOException e) {};
				// bytes downloaded
				addDownloadedBytesCount(bytesCount);
			}
			
		} else if ( status.getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY || 
				status.getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY || 
				status.getStatusCode() == HttpStatus.SC_SEE_OTHER ||
				status.getStatusCode() == HttpStatus.SC_TEMPORARY_REDIRECT) {
			
			// XXX Kind of a hack (sometimes we get 302 from the web serv),
			//     May not work if Globul changes impl.	
			//	   What we should do is proper redirect and parsing.
			// See http://hc.apache.org/httpclient-legacy/redirects.html
			
//			String redirectLocation;
//	        Header locationHeader = resp.getFirstHeader("location");
//	        if (locationHeader != null) {
//	            redirectLocation = locationHeader.getValue();
//	        } else {
//	            // The response is invalid and did not provide the new location for
//	            // the resource.  Report an error or possibly handle the response
//	            // like a 404 Not Found error.
//	        }
			
			try {
				resp.getEntity().consumeContent();
				
				// bytes downloaded
				// XXX This could be misleading!
				if (resp.getEntity().getContentLength() > 0) {
					addDownloadedBytesCount(resp.getEntity().getContentLength());
				}
				
			} catch (IOException e) {
				throw new HttpClientException("Could not consume MOVED_TEMPORARILY content!", e);
			}			
			
		} else {
			// Unhandled response
			throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());
		}
	}
	
	private byte[] findInvoiceExportParams() throws HttpClientException, InvoiceException {

		BufferedReader reader = null;
		long bytesCount = 0;
		StringBuilder xmlUrl = new StringBuilder(100);
		String invoiceDate = Long.toString(new Date().getTime()); // today
		
		byte[] resultData = null;
		try {
			// Get invoice check page
			StringBuilder fullUrl = new StringBuilder(100);
			fullUrl.append(HTTP_MYTELENOR).append(GLBRequestType.PAGE_INVOICE.getPath());
			
			HttpGet httpGet = new HttpGet(fullUrl.toString());
			HttpResponse resp = httpClient.execute(httpGet, httpContext);
			StatusLine status = resp.getStatusLine();
			
			// Construct invoice id url
			fullUrl.setLength(0);
			fullUrl.append(HTTP_MYTELENOR);
			
			if (status.getStatusCode() == HttpStatus.SC_OK) {
				// bytes downloaded
				bytesCount += resp.getEntity().getContentLength() > 0 ? resp.getEntity().getContentLength() : 0;
				// Find invoice id
				Document doc = Jsoup.parse(resp.getEntity().getContent(), RESPONSE_ENCODING, "");
				Elements links = doc.select("a");
				for (Element el : links) {
					String href = el.attributes().get("href");
					if (href != null && href.contains("invId")) {
						fullUrl.append("/").append(href);
						break;
					}
				}	
			} else {
				throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());
			}
			
			// Fetch invoice download parameters
			httpGet = new HttpGet(fullUrl.toString());
			resp = httpClient.execute(httpGet, httpContext);
			status = resp.getStatusLine();
			if (status.getStatusCode() == HttpStatus.SC_OK) {
				String line = null;
				reader = new BufferedReader(new InputStreamReader(resp.getEntity().getContent()));
				while((line = reader.readLine()) != null) {
					// bytes downloaded
					bytesCount += line.length();
					
					if (line.contains("ei2_open_file") && line.contains("xml") && line.contains("summary")) {
						if (Defs.LOG_ENABLED)
							Log.d(Defs.LOG_TAG, line);
						
						/*
						 * This is a g'damn hack. We don't need fancy stuff ;)
						 */
						xmlUrl.append(HTTP_MYTELENOR).append(GLBRequestType.GET_INVOICE.getPath())
						.append("?file_name=summary")
						.append("&file_type=xml")
						.append("&lower_bound=0")
						.append("&upper_bound=0");

//						?file_name=summary
//						&file_type=xml
//						&lower_bound=0
//						&upper_bound=0
//						&invoiceNumber=1234567890
//						&bill_acc_id=1231231
//						&custnum=001234567
//						&servicetype=1
//						&period=1414792800000
//						&cust_acc_id=1011111
//						&custCode10=1.111111
//						&prgCode=1
		                 
						// extract keyword
						String keys[] = {"file_type", "file_name", "lower_bound", "upper_bound", 
								"invoiceNumber", "bill_acc_id", "custnum", "servicetype", "period", "cust_acc_id", 
								"custCode10", "prgCode"};
						String parts[] = line.split(",");
						if (parts.length > 5) {
							for (int i = 5; i < parts.length - 1; i++) {
								String value = parts[i].replace("'", "").trim();
								
								xmlUrl.append("&").append(keys[i]).append("=")
								.append(value); // strip single quotes
								
								// we need the invoice date
								if (keys[i].equals("period")) {
									invoiceDate = value;
								}
							}
							// the last param is a bit tricky, because we need to remove the <a> data
							int lastidx = parts.length - 1;
							parts = parts[lastidx].split("\\)");
							xmlUrl.append("&").append(keys[lastidx]).append("=").append(parts[0].replace("'", ""));
						} else {
							Log.e(Defs.LOG_TAG, "Got line: " + line);
							throw new IOException("Invalid invoice fingerprint!");
						}
						break;
					}
				}
			} else {
				throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());
			}

			// close current stream reader
			if (reader != null) try { reader.close(); } catch (IOException e) {};
			
			// Fetch Invoice XML
			if (Defs.LOG_ENABLED)
				Log.v(Defs.LOG_TAG, "Fetching invoice XML from: " + xmlUrl.toString());	
			
			if (xmlUrl.length() == 0) {
				throw new InvoiceException("Invoice HTTP url not available!");
			}
			
			httpGet = new HttpGet(xmlUrl.toString());
			
			resp = httpClient.execute(httpGet, httpContext);
			status = resp.getStatusLine();
			if ( status.getStatusCode() == HttpStatus.SC_OK ) {
				resultData = Utils.read(resp.getEntity().getContent());
				// add loaded bytes
				bytesCount += resultData.length;
			} else {
				throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());
			}
			
		} catch (ClientProtocolException e) {
			throw new HttpClientException("Client protocol error!" + e.getMessage(), e);
		} catch (IOException e) {
			throw new HttpClientException("Client error!" + e.getMessage(), e);
		} finally {
			if (reader != null) try { reader.close(); } catch (IOException e) {};
			
			addDownloadedBytesCount(bytesCount);
		}
		// parse invoice datetime
		try {
			invoiceDateTime = Long.parseLong(invoiceDate); //invoiceDate.substring(0, invoiceDate.length() - 3));
		} catch (NumberFormatException e) {
			// default 
			invoiceDateTime = new Date().getTime();
		}		
		
		return resultData;
	}	
	
	private List<NameValuePair> findLoginParams() throws HttpClientException {
		
		List<NameValuePair> result = new ArrayList<NameValuePair>();
		BufferedReader reader = null;
		long bytesCount = 0;
		
		try {  
			// Get invoice check page
			StringBuilder fullUrl = new StringBuilder(100);
			fullUrl.append(GLBRequestType.LOGIN.getPath())
			.append("?")
			.append(GLBRequestType.LOGIN.getParams());
			
			HttpGet httpGet = new HttpGet(fullUrl.toString());
			HttpResponse resp = httpClient.execute(httpGet, httpContext);
			StatusLine status = resp.getStatusLine();
			if (status.getStatusCode() == HttpStatus.SC_OK) {
				
				Document doc = Jsoup.parse(resp.getEntity().getContent(), RESPONSE_ENCODING, "");
				Elements inputs = doc.select("input");
				for (Element el : inputs) {
//					if (Defs.LOG_ENABLED) {
//						Log.v(Defs.LOG_TAG, "ELEMENT: " + el.tagName());
//					}
					Attributes attrs = el.attributes();
//					for (Attribute attr : attrs) {
//						if (Defs.LOG_ENABLED) {
//							Log.v(Defs.LOG_TAG, " " + attr.getKey() + "=" + attr.getValue());
//						}
//					}
					
					String elName = attrs.get("name");
					if (elName.equalsIgnoreCase("lt") || elName.equalsIgnoreCase("execution")) {
						result.add(new BasicNameValuePair(elName, attrs.get("value")));
					}
				}			
			} else {
				throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());
			}

			// close current stream reader
//			if (reader != null) try { reader.close(); } catch (IOException e) {};
			
		} catch (ClientProtocolException e) {
			throw new HttpClientException("Client protocol error!" + e.getMessage(), e);
		} catch (IOException e) {
			throw new HttpClientException("Client error!" + e.getMessage(), e);
		} finally {
			if (reader != null) try { reader.close(); } catch (IOException e) {};
			
			addDownloadedBytesCount(bytesCount);
		}
		
		return result;		
	}
	
	private HttpPost createPostRequest(String url, List<NameValuePair> qparams)
		throws UnsupportedEncodingException {
		HttpPost httpPost = new HttpPost(url);
		httpPost.setEntity(new UrlEncodedFormEntity(qparams, HTTP.UTF_8));
		return httpPost;
	}

	private String doPostRequest(GLBRequestType requestType) throws HttpClientException {

		HttpResponse resp;
		try {
			List<NameValuePair> qparams = requestType.getParamsAsList();
			// Fix Issue #7 - Page does not exist
			qparams.add(new BasicNameValuePair("parameter", this.operationsHash.get(requestType)));
			
//			for (NameValuePair nameValuePair : qparams) {
//				Log.d(Defs.LOG_TAG, "Param: " + nameValuePair.getName() + " = " + nameValuePair.getValue());
//			}
			
			HttpPost httpPost = createPostRequest(HTTP_MYTELENOR + requestType.getPath(), qparams);
			httpPost.setHeader("X-Requested-With", "XMLHttpRequest");
			httpPost.setHeader("X-Prototype-Version", "1.6.0.2");
			
			resp = httpClient.execute(httpPost, httpContext);
		} catch (Exception e) {
			throw new HttpClientException("Client protocol error!" + e.getMessage(), e);
		}
		
		StatusLine status = resp.getStatusLine();
		
		if (status.getStatusCode() != HttpStatus.SC_OK)
			throw new HttpClientException(status.getReasonPhrase(), status.getStatusCode());

		HttpEntity entity = resp.getEntity();
		ByteArrayOutputStream baos = null;
		try {
			baos = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
			entity.writeTo(baos);
		} catch (Exception e) {
			throw new HttpClientException("Failed to load response! " + e.getMessage(), e);
		} finally {
			if (baos != null) try { baos.close(); } catch (IOException e) {};
		}
		
		// bytes downloaded
		addDownloadedBytesCount(baos.size());

		try {
			return new String(baos.toByteArray(), RESPONSE_ENCODING);
		} catch (UnsupportedEncodingException e) {
			return new String(baos.toByteArray()); // XXX check this!
		}
	}
}
