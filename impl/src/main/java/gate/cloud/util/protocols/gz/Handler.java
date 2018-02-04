package gate.cloud.util.protocols.gz;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.Permission;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * URLStreamHandler for the gz: protocol.  Understands URLs of the form
 * gz:URL (e.g. gz:file:/home/user/readme.txt.gz) and uncompresses them
 * on the fly when reading.
 */
public class Handler extends URLStreamHandler {

  @Override
  protected URLConnection openConnection(URL u) throws IOException {
    return new GZURLConnection(u);
  }

  static class GZURLConnection extends URLConnection {

    URLConnection delegate;
    
    protected GZURLConnection(URL url) throws IOException {
      super(url);
      // url must start gz:, strip this off to find the real URL
      URL delegateUrl = new URL(url.toExternalForm().substring(3));
      delegate = delegateUrl.openConnection();
    }

    public void connect() throws IOException {
      delegate.connect();
    }
    
    
    public InputStream getInputStream() throws IOException {
      return new GzipCompressorInputStream(delegate.getInputStream());
    }

    public OutputStream getOutputStream() throws IOException {
      return new GzipCompressorOutputStream(delegate.getOutputStream());
    }



    public void setConnectTimeout(int timeout) {
      delegate.setConnectTimeout(timeout);
    }

    public int getConnectTimeout() {
      return delegate.getConnectTimeout();
    }

    public void setReadTimeout(int timeout) {
      delegate.setReadTimeout(timeout);
    }

    public int getReadTimeout() {
      return delegate.getReadTimeout();
    }

    public String getContentType() {
      return delegate.getContentType();
    }

    public long getExpiration() {
      return delegate.getExpiration();
    }

    public long getDate() {
      return delegate.getDate();
    }

    public long getLastModified() {
      return delegate.getLastModified();
    }

    public String getHeaderField(String name) {
      return delegate.getHeaderField(name);
    }

    public Map<String, List<String>> getHeaderFields() {
      return delegate.getHeaderFields();
    }

    public int getHeaderFieldInt(String name, int Default) {
      return delegate.getHeaderFieldInt(name, Default);
    }

    public long getHeaderFieldDate(String name, long Default) {
      return delegate.getHeaderFieldDate(name, Default);
    }

    public String getHeaderFieldKey(int n) {
      return delegate.getHeaderFieldKey(n);
    }

    public String getHeaderField(int n) {
      return delegate.getHeaderField(n);
    }

    public Permission getPermission() throws IOException {
      return delegate.getPermission();
    }

    public void setDoInput(boolean doinput) {
      delegate.setDoInput(doinput);
    }

    public boolean getDoInput() {
      return delegate.getDoInput();
    }

    public void setDoOutput(boolean dooutput) {
      delegate.setDoOutput(dooutput);
    }

    public boolean getDoOutput() {
      return delegate.getDoOutput();
    }

    public void setAllowUserInteraction(boolean allowuserinteraction) {
      delegate.setAllowUserInteraction(allowuserinteraction);
    }

    public boolean getAllowUserInteraction() {
      return delegate.getAllowUserInteraction();
    }

    public void setUseCaches(boolean usecaches) {
      delegate.setUseCaches(usecaches);
    }

    public boolean getUseCaches() {
      return delegate.getUseCaches();
    }

    public void setIfModifiedSince(long ifmodifiedsince) {
      delegate.setIfModifiedSince(ifmodifiedsince);
    }

    public long getIfModifiedSince() {
      return delegate.getIfModifiedSince();
    }

    public void setDefaultUseCaches(boolean defaultusecaches) {
      delegate.setDefaultUseCaches(defaultusecaches);
    }

    public void setRequestProperty(String key, String value) {
      delegate.setRequestProperty(key, value);
    }

    public void addRequestProperty(String key, String value) {
      delegate.addRequestProperty(key, value);
    }

    public String getRequestProperty(String key) {
      return delegate.getRequestProperty(key);
    }

    public Map<String, List<String>> getRequestProperties() {
      return delegate.getRequestProperties();
    }
    
  }
  
}
