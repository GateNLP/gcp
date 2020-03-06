/*
 *  WARCDocumentEnumerator.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: WARCDocumentEnumerator.java 20268 2017-09-30 22:17:03Z ian_roberts $ 
 */
package gate.cloud.io.arc;

import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.Header;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.HeaderedArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;
import org.archive.io.warc.WARCRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enumerator to build a list of document IDs from a WARC file.
 */
public class WARCDocumentEnumerator extends ArchiveDocumentEnumerator {
  
  private static Logger logger = LoggerFactory.getLogger(WARCDocumentEnumerator.class);

  // some non-Heritrix-produced WARC files have slightly different spacing in the mime type
  protected static final Pattern HTTP_RESPONSE_MIMETYPE_PATTERN = Pattern.compile("(?i)application/http;\\s*msgtype=response");

  @Override
  protected ArchiveReader createReader() throws IOException {
    return WARCReaderFactory.get(srcFile);
  }

  @Override
  protected ArchiveRecord nextRecord(Iterator<ArchiveRecord> it) {
    WARCRecord record = (WARCRecord)it.next();
    if(!HTTP_RESPONSE_MIMETYPE_PATTERN.matcher(record.getHeader().getMimetype()).matches()) {
      logger.debug("WARC record mimetype was {}, ignored", record.getHeader().getMimetype());
      return null;
    }
    try {
      return new HeaderedArchiveRecord(record, true);
    } catch(IOException e) {
      logger.warn("Couldn't parse WARC record HTTP headers", e);
      return null;
    }
  }

  @Override
  protected long findContentBegin(ArchiveRecord record) {
    return record.getHeader().getContentBegin() + ((HeaderedArchiveRecord)record).getContentHeadersLength();
  }

  @Override
  protected String statusCode(ArchiveRecord record) {
    int statusCodeNum = ((HeaderedArchiveRecord)record).getStatusCode();
    if(statusCodeNum < 0) {
      return null;
    } else {
      return String.valueOf(statusCodeNum);
    }
  }

  @Override
  protected String mimeType(ArchiveRecord record) {
    Header[] headers = ((HeaderedArchiveRecord)record).getContentHeaders();
    for(Header h : headers) {
      if("Content-Type".equals(h.getName())) {
        return h.getValue();
      }
    }
    
    // no content type found
    return "_not_found_";
  }

}
