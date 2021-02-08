package com.example.pafprojectintelligentcar;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;


public class HttpsWithCertificate {

    private Context activityContext;
    private int certificateRawRessourceId;
    private String clientCertPassword;
    private static SSLContext sslContext;
    private static boolean run = false;

    public HttpsWithCertificate(Context activityContext, int certificateRawRessourceId, String clientCertPassword) {
        this.activityContext = activityContext;
        this.certificateRawRessourceId = certificateRawRessourceId;
        this.clientCertPassword = clientCertPassword;
        try {
            if(!run) {
                setupSSLClientCertificate();
                run = true;
            }
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public void setupSSLClientCertificate() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException, KeyManagementException {

        final InputStream in = activityContext.getResources().openRawResource(R.raw.client_certificate);
        KeyStore keyStore = KeyStore.getInstance("PKCS12"); //Cert file has to be in PKCS12 format (.p12)
        keyStore.load(in, clientCertPassword.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("X509");
        kmf.init(keyStore, clientCertPassword.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, null, null);
    }

    public String httpGet(String url) {
        String result = "";
        HttpURLConnection urlConnection = null;
        try {
            URL requestedUrl = new URL(url);
            urlConnection = (HttpURLConnection) requestedUrl.openConnection();
            if(urlConnection instanceof HttpsURLConnection) {
                ((HttpsURLConnection)urlConnection)
                        .setSSLSocketFactory(sslContext.getSocketFactory());
            }

            urlConnection.setRequestMethod("GET");

            result = readFully(urlConnection.getInputStream());

        } catch(Exception ex) {
            ex.printStackTrace();
            result = null;
        } finally {
            if(urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        return result;
    }



    public static String readFully(InputStream inputStream) throws IOException {
        if(inputStream == null) {
            return "";
        }

        BufferedInputStream bufferedInputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = null;

        try {
            bufferedInputStream = new BufferedInputStream(inputStream);
            byteArrayOutputStream = new ByteArrayOutputStream();

            final byte[] buffer = new byte[1024];
            int available = 0;

            while ((available = bufferedInputStream.read(buffer)) >= 0) {
                byteArrayOutputStream.write(buffer, 0, available);
            }

            return byteArrayOutputStream.toString();

        } finally {
            if(bufferedInputStream != null) {
                bufferedInputStream.close();
            }
        }
    }

    public void httpPost(String url, String data) {
        HttpURLConnection urlConnection = null;
        try {
            URL requestedUrl = new URL(url);
            urlConnection = (HttpURLConnection) requestedUrl.openConnection();
            if(urlConnection instanceof HttpsURLConnection) {
                ((HttpsURLConnection)urlConnection)
                        .setSSLSocketFactory(sslContext.getSocketFactory());
            }

            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-length", String.valueOf(data.length()));
            urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setDoOutput(true);

            writeFully(urlConnection.getOutputStream(), data);
            System.out.println(urlConnection.getResponseCode() + " " + urlConnection.getResponseMessage());

        } catch(Exception ex) {
            ex.printStackTrace();
        } finally {
            if(urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private static void writeFully(OutputStream outputStream, String data) {
        if(outputStream == null) {
            return ;
        }

        PrintWriter pw = null;
        try {
            pw = new PrintWriter(outputStream);
            System.out.println(data);
            pw.write(data);
        }  finally {
            pw.flush();
            pw.close();
        }
    }

}
