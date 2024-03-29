/*
 *  ArchiveInputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ArchiveInputHandler.java 20268 2017-09-30 22:17:03Z ian_roberts $ 
 */
package gate.cloud.io.arc;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_SOURCE_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_DEFAULT_ENCODING;
import static gate.cloud.io.IOConstants.PARAM_MIME_TYPE;
import static gate.cloud.io.IOConstants.PARAM_REPOSITIONING_INFO;

import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.DocumentData;
import gate.cloud.io.InputHandler;
import gate.cloud.util.ByteArrayURLStreamHandler;
import gate.util.GateException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.httpclient.ChunkedInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.util.ArchiveUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An input handler that reads from a web archive (ARC or WARC) file.
 * It assumes that the document IDs use the record URL as the id text 
 * (see {@link DocumentID#getIdText()}), and that the document ID attributes 
 * (see {@link DocumentID#getAttributes()}) include:
 * <ul>
 * <li>{@link #RECORD_OFFSET_ATTR}: the byte offset (inside the archive file) for
 * the required record.</li>
 * <li>{@link #RECORD_LENGTH_ATTR}: the length (in bytes) for the required 
 * record.</li>
 * <li>{@link #RECORD_POSITION_ATTR}: a numeric value that
 * is used as a sequence number. If the IDs are generated by an 
 * {@link ARCDocumentEnumerator}, the this attribute will contain the actual
 * record position inside the archive file.</li>
 * <li>{@link #URL_ATTR} (optional): the full URL where the archive file is 
 * available. This can be used to override the source file configured on the 
 * actual input handler. For example, when a batch uses records from multiple 
 * files as input, the file cannot be configured on the input handler, 
 * so each document ID must include one instead.  
 * </ul>
 */
public abstract class ArchiveInputHandler implements InputHandler {
  
  private static final Logger logger = LoggerFactory.getLogger(ArchiveInputHandler.class);
  
  private static final String ARC_HEADER_PREFIX = "arc_header_";

  private static final String HTTP_HEADER_PREFIX = "http_header_";
  
  private static final String HTTP_CONTENT_TYPE_HEADER_NAME = "Content-Type";

  private static final String HTTP_TRANSFER_ENCODING_HEADER_NAME = "Transfer-Encoding";
  
  /**
   * Name for an attribute used when generating {@link DocumentID} values for
   * the {@link ArchiveInputHandler}. This attribute stores (a String 
   * representation) of the Long byte offset for the input record.
   * 
   * Value: {@value}
   */
  static final String RECORD_OFFSET_ATTR = "recordOffset";

  /**
   * Name for an attribute used when generating {@link DocumentID} values for
   * the {@link ArchiveInputHandler}. This attribute stores (a String 
   * representation) of the Long byte length for the input record. 
   */  
  static final String RECORD_LENGTH_ATTR = "recordLength";

  /**
   * Name for an attribute used when generating {@link DocumentID} values for
   * the {@link ArchiveInputHandler}. This attribute stores (a String 
   * representation) of the integer position inside the archive for the input 
   * record.
   */  
  static final String RECORD_POSITION_ATTR = "recordPosition";
  
  /**
   * Name for an attribute used when generating {@link DocumentID} values for
   * the {@link ArchiveInputHandler}. This attribute stores the URL where the input
   * file can be located. This can be used to override the file 
   * configured on the actual input handler. When a batch uses records from
   * multiple files as input, the file cannot be configured on the input
   * handler, so each document ID must include one. 
   */  
  static final String URL_ATTR = "url";

  /**
   * The archive file we are loading documents from.
   */
  protected File srcFile;
  

  /**
   * The directory containing the batch specification file, or
   * <code>null</code> if the batch specification did not come from a
   * file.
   */
  protected File batchDir;
  
  /**
   * The encoding to assume for records that don't specify
   * one in their Content-Type header.  Windows-1252 is used if
   * not specified in the batch file.
   */
  protected String defaultEncoding;
  
  /**
   * Optional mime type override.  If specified, all entries to
   * be processed will be parsed by GATE using this mime type.
   * By default, we respect the mime type given in the ARC
   * metadata for each entry.
   */
  protected String mimeType;
  
  /**
   * Should we collect repositioning info when parsing?
   */
  protected boolean repositioningInfo = false;
  
  /**
   * Regular expression pattern matching the "charset" from an HTTP
   * Content-type header.
   */
  protected static final Pattern CHARSET_PATTERN = Pattern.compile("charset=(\\S*)");

  public void config(Map<String, String> configData) throws IOException,
          GateException {
    // ARC file
    String arcFileStr = configData.get(PARAM_SOURCE_FILE_LOCATION);
    if(arcFileStr != null) {
      String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
      if(batchFileStr != null) {
        batchDir = new File(batchFileStr).getParentFile();
      }
      srcFile = new File(arcFileStr);
      if(!srcFile.isAbsolute()) {
        srcFile = new File(batchDir, arcFileStr);
      }
      if(!srcFile.exists()) {
        throw new IllegalArgumentException("File \"" + srcFile
                + "\", provided as value for required parameter \""
                + PARAM_SOURCE_FILE_LOCATION + "\", does not exist!");
      }
      if(!srcFile.isFile()) {
        throw new IllegalArgumentException("File \"" + srcFile
                + "\", provided as value for required parameter \""
                + PARAM_SOURCE_FILE_LOCATION + "\", is not a file!");
      }      
    } else {
      srcFile = null;
    }
    
    defaultEncoding = configData.get(PARAM_DEFAULT_ENCODING);
    if(defaultEncoding == null) {
      defaultEncoding = "Windows-1252";
    }
    
    mimeType = configData.get(PARAM_MIME_TYPE);
    
    if("true".equals(configData.get(PARAM_REPOSITIONING_INFO))) {
      repositioningInfo = true;
    }
  }

  public void init() throws IOException, GateException { }

  public DocumentData getInputDocument(DocumentID id) throws IOException, GateException {
    if(id.getAttributes() == null) {
      throw new IllegalArgumentException(
      "Document IDs within a web archive must include \"" + RECORD_OFFSET_ATTR +
      "\" and \"" + RECORD_LENGTH_ATTR + "\" attributes; \"" + 
      id.toString() + "\" does not.");
    }
    String arcUrlStr = id.getAttributes().get(URL_ATTR);
    if(srcFile == null && (arcUrlStr == null || arcUrlStr.length() == 0)) {
      throw new IllegalArgumentException(
        "No source file was configured for this input handler, so the " + 
        "document IDs must provide a \"" + URL_ATTR +
        "\" attribute; \"" + id.toString() + "\" does not."); 
    }
    
    String offsetStr = id.getAttributes().get(RECORD_OFFSET_ATTR);
    String lengthStr = id.getAttributes().get(RECORD_LENGTH_ATTR);
    if(offsetStr == null || offsetStr.length() == 0 ||
       lengthStr == null || lengthStr.length() == 0) {
      throw new IllegalArgumentException(
        "Document IDs within a web archive must include \"" + RECORD_OFFSET_ATTR +
        "\" and \"" + RECORD_LENGTH_ATTR + "\" attributes; \"" + 
        id.toString() + "\" does not."); 
    }
    
    long offset = -1;
    try{
      offset = Long.parseLong(offsetStr);
    }catch (NumberFormatException nfe) {
      throw new IllegalArgumentException(
        "Could not parse offset \"" + offsetStr + "\" as a long value."); 
    }
    long length = -1;
    try{
      length = Long.parseLong(lengthStr);
    }catch (NumberFormatException nfe) {
      throw new IllegalArgumentException(
        "Could not parse length \"" + lengthStr + "\" as a long value."); 
    }
    // read the arc record
    ArchiveRecord record = null;
    ArchiveReader reader = null;
    URL arcUrl = null;
    try{
      if(arcUrlStr != null) {
        arcUrl = new URL(arcUrlStr);
        URLConnection conn = arcUrl.openConnection();
        conn.setRequestProperty("Range", "bytes=" + Long.toString(offset) + 
            "-" + Long.toString(offset + length - 1));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = conn.getInputStream();
        if(arcUrlStr.endsWith(".gz")) {
          // compressed input
          is = new GZIPInputStream(is);
        }
        IOUtils.copy(is, baos);
        is.close();
        record = archiveRecordFromByteArray(baos.toByteArray(), arcUrlStr);
      } else {
        // no custom URL, so we can use the default arc file
        reader = createReader();
        record = getRecord(reader, offset);         
      }
      ArchiveRecordHeader header = record.getHeader();

      // extract the content
      long recordContentBegin = header.getContentBegin();
      record.skip(recordContentBegin);
      long recordBodySize = record.available();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      record.dump(baos);
      byte[] content = baos.toByteArray();
      
      String encoding = null;
      Header[] httpHeaders = httpHeaders(record);
      boolean isChunked = false;
      Pattern charsetPattern = Pattern.compile("charset=(['\"]?)([a-zA-Z0-9_-]+)\\1");
      for(Header aHeader : httpHeaders) {
        if(aHeader.getName().equalsIgnoreCase(HTTP_CONTENT_TYPE_HEADER_NAME)){
          Matcher m = charsetPattern.matcher(aHeader.getValue());
          if(m.find()) {
            encoding = m.group(2);
          }
        } else if(aHeader.getName().equalsIgnoreCase(HTTP_TRANSFER_ENCODING_HEADER_NAME)) {
          if("chunked".equalsIgnoreCase(aHeader.getValue())) {
            isChunked = true;
          }
        }
      }
      if(encoding == null) encoding = defaultEncoding;

      if(isChunked) {
        // de-chunk the stream
        ChunkedInputStream chunkIn = new ChunkedInputStream(new ByteArrayInputStream(content));
        baos = new ByteArrayOutputStream();
        IOUtils.copy(chunkIn, baos);
        chunkIn.close();
        content = baos.toByteArray();
      }
      ByteArrayURLStreamHandler.Header[] handlerHeaders = null;
      if(httpHeaders != null) {
        handlerHeaders = new ByteArrayURLStreamHandler.Header[httpHeaders.length];
        for(int i = 0; i < httpHeaders.length; i++) {
          handlerHeaders[i] = new ByteArrayURLStreamHandler.Header(httpHeaders[i].getName(), httpHeaders[i].getValue());
        }
      }
      URL docUrl = new URL(null, header.getUrl(), new ByteArrayURLStreamHandler(content, handlerHeaders));
      
      FeatureMap docParams = Factory.newFeatureMap();
      docParams.put(Document.DOCUMENT_URL_PARAMETER_NAME, docUrl);
      if(encoding != null && encoding.length() > 0) {
        docParams.put(Document.DOCUMENT_ENCODING_PARAMETER_NAME, encoding);
      }
      docParams.put(Document.DOCUMENT_MARKUP_AWARE_PARAMETER_NAME, Boolean.TRUE);
      if(mimeType != null) {
        docParams.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, mimeType);
      }
      
      if(repositioningInfo) {
        docParams.put(Document.DOCUMENT_REPOSITIONING_PARAMETER_NAME, Boolean.TRUE);
      }
      
      FeatureMap docFeatures = Factory.newFeatureMap();
      Object redirect = header.getHeaderValue("location");
      if(redirect != null) {
        docFeatures.put("redirect_to", redirect.toString());
      }
      if(arcUrl != null) {
        docFeatures.put("archive_name", arcUrl.getFile());
      } else if(srcFile != null){
        docFeatures.put("archive_name", srcFile.getName());  
      }
      
  //      docFeatures.put("archive_position", idMatcher.group(1));
      String posStr = id.getAttributes().get(RECORD_POSITION_ATTR);
      if(posStr != null) {
        try {
          Long.parseLong(posStr);
          docFeatures.put("archive_position", posStr);    
        } catch(NumberFormatException e) {
          // log and ignore
          logger.warn("Invalid record position value (not an integer number): {}",
          posStr);
        }
      }
      
      Date documentDate = getDate(header, record);
      if(documentDate != null) {
        docFeatures.put("retrievedAt", documentDate);
      }
      docFeatures.put("original_size", Long.toString(recordBodySize));
      
      // store file size
      long fileSize = content.length;
      
      /*
       * Store all ARC headers as document features in case they turn out to be
       * useful later.
       */
      Iterator<?> headerKeyIter = header.getHeaderFieldKeys().iterator();
      String headerKey, headerValueString;
      Object headerValue;
      while(headerKeyIter.hasNext()) {
        headerKey = headerKeyIter.next().toString();
        headerValue = header.getHeaderValue(headerKey);
        // Shouldn't happen ... but just in case
        if(headerValue != null) {
          headerValueString = headerValue.toString();
        } else {
          headerValueString = "_null_";
        }
        docFeatures.put(ARC_HEADER_PREFIX + headerKey, headerValueString);
      }
      // Do the same for the HTTP headers
      Header[] httpHeader = httpHeaders(record);
  
      if(httpHeader != null) {
        for(Header h : httpHeader) {
          headerKey = h.getName();
          headerValueString = h.getValue();
          docFeatures.put(HTTP_HEADER_PREFIX + headerKey, headerValueString);
        }
      }
      
      DocumentData docData = new DocumentData(
              (Document)Factory.createResource("gate.corpora.DocumentImpl",
                docParams, docFeatures, id.toString()), id);
      docData.fileSize = fileSize;
      return docData;
    } finally {
      try {
        if(reader != null) reader.close();
      } catch(IOException e) {
        // ignore
        logger.error("Error while closing ARC reader.", e);
      }
      if(record != null) record.close();
    }
  }
  
  protected abstract ArchiveReader createReader() throws IOException;
  
  protected abstract ArchiveRecord getRecord(ArchiveReader reader, long offset) throws IOException;
  
  protected abstract ArchiveRecord archiveRecordFromByteArray(byte[] data, String url) throws IOException;
  
  protected abstract Header[] httpHeaders(ArchiveRecord record);
  
  /**
   * Use the HTTP Date header if present, otherwise the archival date.
   * 
   * @return null if no date is found.
   */
  private Date getDate(ArchiveRecordHeader header, ArchiveRecord record) {
    /*
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html Date header must
     * be in RFC 1123 format
     */
    String dateString = null;
    Header[] httpHeader = httpHeaders(record);
    if(httpHeader != null) {
      for(Header h : httpHeader) {
        if(h.getName().equalsIgnoreCase("Date")) {
          dateString = h.getValue();
          break;
        }
      }
    }
    if(dateString != null) {
      try {
        return DateUtil.parseDate(dateString);
      } catch(DateParseException e) {

      }
    }
    // failed to get HTTP date; try the arc header
    dateString = header.getDate();
    try {
      return ArchiveUtils.parse14DigitDate(dateString);
    } catch(ParseException e) {

    }
    return null;
  }
  
  /**
   * No operation.
   */
  public void close() throws IOException, GateException { }

}
