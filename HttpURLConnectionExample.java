import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import javax.net.ssl.HttpsURLConnection;

public class HttpURLConnectionExample {

	public static void main(String[] args) throws IOException {

		String httpsURL;
		URL myurl;
		HttpsURLConnection con;
		postJson();
		
		// try get
		// to be done, need to confirm the format.
		httpsURL = "https://paf-communications-bluetooth.online/messages/cam";
		myurl = new URL(httpsURL);
		con = (HttpsURLConnection) myurl.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setRequestProperty("Accept", "application/json");
		try {
			   System.out.println("****** Content of the URL ********");
			   BufferedReader br =
				new BufferedReader(
					new InputStreamReader(con.getInputStream()));

			   String input;

			   while ((input = br.readLine()) != null){
			      System.out.println(input);
			   }
			   br.close();

			} catch (IOException e) {
			   e.printStackTrace();
			}

	}

	private static void postJson() throws MalformedURLException, IOException, ProtocolException {
		String httpsURL = "https://paf-communications-bluetooth.online/messages/cam/";
		//String httpsURL = "https://google.com";
		//String httpsURL = "https://localhost:8888/";
		
		String query = "{\r\n" + 
				"        \"geo_networking\": \"bonjour\",\r\n" + 
				"        \"btp_b\": \"mes\",\r\n" + 
				"        \"its\": \"amis\"\r\n" + 
				"       }";
		URL myurl = new URL(httpsURL);
		HttpsURLConnection con = (HttpsURLConnection)myurl.openConnection();
		System.out.println("Connected!");
		con.setRequestMethod("POST");

		con.setRequestProperty("Content-length", String.valueOf(query.length())); 
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setRequestProperty("Accept", "application/json");
		con.setDoOutput(true); 
		con.setDoInput(true); 
		
		System.out.println("Preparing to send data...");
		BufferedWriter output = new BufferedWriter(new OutputStreamWriter(con.getOutputStream()));  
		
		output.write(query);
		output.flush();
		System.out.println("write finish");
		output.close();
		System.out.println("done POST");
		
		int responseCode = con.getResponseCode();
		System.out.println("responseCode");
	}

}