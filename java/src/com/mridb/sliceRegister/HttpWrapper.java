package com.mridb.sliceRegister;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

//import java.lang.String;

//import com.google.api.client.http.javanet.NetHttpResponse.SizeValidatingInputStream;
//import com.google.api.client.http.javanet.NetHttpResponse.* as google;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.common.primitives.Bytes;


public class HttpWrapper {

	private com.google.api.client.http.javanet.NetHttpTransport _transport;
	private com.google.api.client.http.HttpRequestFactory _requestFactory;
	public HttpHeaders requestHeaders;
	public HttpHeaders responseHeaders;
	public LocalDateTime dateRequestSent;
	public LocalDateTime dateResponseReceived;
	private static HostnameVerifier originalHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
	private static SSLSocketFactory originalSslSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
	


	public HttpWrapper() {
		this._transport = new com.google.api.client.http.javanet.NetHttpTransport();
		this._requestFactory = _transport.createRequestFactory();
		this.requestHeaders = new HttpHeaders();
		requestHeaders.setContentType("application/json");
		requestHeaders.setAccept("application/json");
		this.responseHeaders = null;
		
	

	}

	public Long getContentLength(String address) {
		
		GenericUrl url = new GenericUrl(address);
		Instant startTime = Instant.now();
		Instant timeoutTime = startTime.plusSeconds(300);
		
		while(true) {
			try {
				HttpRequest request = this._requestFactory.buildHeadRequest(url);
				request.setHeaders(this.requestHeaders);
				request.execute();
				HttpResponse response = request.execute();
				 responseHeaders = response.getHeaders(); 
				return responseHeaders.getContentLength();
			} catch(Exception ex) {
				if(Instant.now().compareTo(timeoutTime) > 0) {
					return null;
				}
			}
		}
	}

	public String webRead(String address, String content) throws IOException {
		HttpRequest request = generateRequest(address, content);
		this.dateRequestSent = LocalDateTime.now();
		HttpResponse response = request.execute();
		this.responseHeaders = response.getHeaders();
		InputStream responseContent = response.getContent();

		// read the stream; the simple version is not available in 1.8
		//byte[] output = responseContent.readAllBytes();


		long totalRead = 0;
		int chunkSize = 1024 * 1024;
		byte[] output = new byte[] {};
		byte[] buffer = new byte[chunkSize];
		int bytesRead = 0;

		//		Can't use content length header because it seems to pertain to the zipped content, which google seems to obfuscate
		//while(responseContent.available() > 0) {
		while(bytesRead > -1) {
			bytesRead = responseContent.read(buffer, 0, chunkSize);
			if(bytesRead > -1) {
				ByteBuffer sliced = ByteBuffer.wrap(buffer, 0, bytesRead).slice();
				output = Bytes.concat(output, sliced.array());
			}
		}


		this.dateResponseReceived = LocalDateTime.now();

		// todo: handle other encodings
		String encoding = responseHeaders.getContentEncoding();
		//System.out.println(bytes2Hex(output , output.length));


		String str = new String(output, "UTF-8");
		responseContent.close();
		return str;
	}

	public long webSave(String address, String content, String filePath) throws IOException {
		HttpWrapper.enableSslVerification();

		HttpRequest request = generateRequest(address, content);
		this.dateRequestSent = LocalDateTime.now();
		HttpResponse response = request.execute();
		this.responseHeaders = response.getHeaders();
		InputStream responseContent = response.getContent();

		int chunkSize = 1024 * 1024 * 16;
		byte[] buffer = new byte[chunkSize];
		int chunkCounter = 0;

		FileOutputStream fStream = new FileOutputStream(filePath);
		long totalReadBytes = 0;
		int byteReadCount = 0;

		//while(responseContent.available() > 0) {
		while(byteReadCount > -1) {
			byteReadCount = responseContent.read(buffer, 0, chunkSize);
			if(byteReadCount > -1) {
				totalReadBytes += byteReadCount; 
				ByteBuffer sliced = ByteBuffer.wrap(buffer, 0, byteReadCount).slice();
				fStream.write(buffer, 0, byteReadCount);
				//System.out.println("Bytes read: " + byteReadCount);
				chunkCounter++;
			}
		}

		fStream.close();
		responseContent.close();
		return totalReadBytes;
	}
	
	// ignores ssl problems
	public long webSaveUnsafe(String address, String content, String filePath) throws IOException {
		HttpWrapper.disableSslVerification();

		HttpRequest request = generateRequest(address, content);
		this.dateRequestSent = LocalDateTime.now();
		HttpResponse response = request.execute();
		this.responseHeaders = response.getHeaders();
		InputStream responseContent = response.getContent();

		int chunkSize = 1024 * 1024 * 16;
		byte[] buffer = new byte[chunkSize];
		int chunkCounter = 0;

		FileOutputStream fStream = new FileOutputStream(filePath);
		long totalReadBytes = 0;
		int byteReadCount = 0;

		//while(responseContent.available() > 0) {
		while(byteReadCount > -1) {
			byteReadCount = responseContent.read(buffer, 0, chunkSize);
			if(byteReadCount > -1) {
				totalReadBytes += byteReadCount; 
				ByteBuffer sliced = ByteBuffer.wrap(buffer, 0, byteReadCount).slice();
				fStream.write(buffer, 0, byteReadCount);
				//System.out.println("Bytes read: " + byteReadCount);
				chunkCounter++;
			}
		}

		fStream.close();
		responseContent.close();
		return totalReadBytes;
	}
	
	
	private static void enableSslVerification() {
		// Install the normal trust manager
		HttpsURLConnection.setDefaultSSLSocketFactory(HttpWrapper.originalSslSocketFactory);

		// Install the normal host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(HttpWrapper.originalHostnameVerifier);

	}
	
	private static void disableSslVerification() {
	    try
	    {
	        // Create a trust manager that does not validate certificate chains
	        TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
	            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
	                return null;
	            }
	            public void checkClientTrusted(X509Certificate[] certs, String authType) {
	            }
	            public void checkServerTrusted(X509Certificate[] certs, String authType) {
	            }
	        }
	        };

	        // Install the all-trusting trust manager
	        SSLContext sc = SSLContext.getInstance("SSL");
	        sc.init(null, trustAllCerts, new java.security.SecureRandom());
	        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

	        // Create all-trusting host name verifier
	        HostnameVerifier allHostsValid = new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					// TODO Auto-generated method stub
	                return true;
				}
	        };

	        // Install the all-trusting host verifier
	        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	    } catch (NoSuchAlgorithmException e) {
	        e.printStackTrace();
	    } catch (KeyManagementException e) {
	        e.printStackTrace();
	    }
	}


	@SuppressWarnings("unused")
	private String bytes2Hex(byte[] buffer, int length) {
		int bytesPerLine = 16;
		StringBuilder sb = new StringBuilder();
		if(length < buffer.length) {
			length = buffer.length;
		}
		for(int i = 0; i < buffer.length; i++) {
			if((i > 0) & (i % bytesPerLine == 0)) {
				sb.append('\n');
			}
			byte buf = buffer[i];
			int upper = buf >> 4;
		sb.append(hexValue((byte)upper));			
		sb.append(hexValue(buf));			
		sb.append(' ');			
		}
		return sb.toString();		
	}
	private char hexValue(byte input) {
		int val = input % 16;
		if(val == 0) {return '0';}
		if(val == 1) {return '1';}
		if(val == 2) {return '2';}
		if(val == 3) {return '3';}
		if(val == 4) {return '4';}
		if(val == 5) {return '5';}
		if(val == 6) {return '6';}
		if(val == 7) {return '7';}
		if(val == 8) {return '8';}
		if(val == 9) {return '9';}
		if(val == 10) {return 'A';}
		if(val == 11) {return 'B';}
		if(val == 12) {return 'C';}
		if(val == 13) {return 'D';}
		if(val == 14) {return 'E';}
		if(val == 15) {return 'F';}
		return '\0';		
	}

	private HttpRequest generateRequest(String address, String content) throws IOException {

		GenericUrl url = new GenericUrl(address);
		HttpRequest request;
		if(content.length() == 0) { // get request
			request = this._requestFactory.buildGetRequest(url);
		}
		else { // post request
			com.google.api.client.http.ByteArrayContent byteContent = com.google.api.client.http.ByteArrayContent.fromString(requestHeaders.getContentType(), content);			
			request = this._requestFactory.buildPostRequest(url, byteContent);			
		}
		request.setHeaders(this.requestHeaders);
		return request;
	}


	public static void main(String[] args) throws IOException {

		String s3Link = "http://fcp-indi.s3.amazonaws.com/data/Projects/RocklandSample/RawDataBIDSLatest/sub-A00037110/ses-BAS1/func/sub-A00037110_ses-BAS1_task-CHECKERBOARD_acq-645_events.tsv";
		HttpWrapper wrapper = new HttpWrapper();
		Long length = wrapper.getContentLength(s3Link);
		
		String page = wrapper.webRead("https://google.com/", "");
		System.out.println(page);
		//long byteCount = wrapper.webSave("https://google.com/", "", "test.txt");
	}
	
	


}


