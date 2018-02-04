/*
 *  ARCDocumentNamingStrategy.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ARCDocumentNamingStrategy.java 17362 2014-02-20 12:12:57Z ian_roberts $ 
 */
package gate.cloud.io.arc;

import static gate.cloud.io.IOConstants.PARAM_PATTERN;
import java.io.IOException;
import java.util.Formatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import gate.cloud.batch.DocumentID;
import gate.cloud.io.file.SimpleNamingStrategy;
import gate.util.GateException;

/**
 * <p>A naming strategy to convert document IDs suitable for use with
 * an {@link ArchiveInputHandler} to file paths suitable for saving the
 * results of their processing.  It assumes that the document IDs
 * use the record URL as the id text (see {@link DocumentID#getIdText()}), and
 * that the document ID attributes (see {@link DocumentID#getAttributes()}) 
 * include a numeric value for {@link ArchiveInputHandler#RECORD_POSITION_ATTR} that
 * is used as a sequence number.
 * It arranges the output files under a single document root in numbered 
 * directories constructed by padding the document sequence number to the left 
 * with zeros and creating intermediate directories according to a configurable
 * pattern.  The default pattern is '3/3', which pads the numbers to a minimum 
 * of 6 digits and then splits them up into groups of three.  The ID text
 * is cleaned up to remove any URL protocol like http:// and any query string
 * or fragment.  Any sequences of non-ASCII characters are removed and any
 * remaining slashes or colons are replaced with underscores.</p>
 * 
 * <p>For example with the default pattern, the document
 * ID with <code>recordPosition="1"</code> and URL 'http://example.org/file.html?param=value' maps to the file
 * 000/001_example.org_file.html (with any additional configured file
 * extension appended).  If the numeric part has more digits than the
 * pattern allows then additional digits are used in the first place, so
 * the ID 1234567 maps to 1234/567 rather than 123/4567.</p>
 * @author ian
 *
 */
public class ARCDocumentNamingStrategy extends SimpleNamingStrategy {
  
  private static final Logger logger = Logger.getLogger(ARCDocumentEnumerator.class); 
  
  protected String pattern;
  
  protected long[] divisors;
  
  /**
   * A pattern matching any sequence of non-ascii characters.
   */
  protected static final Pattern NON_ASCII_PATTERN = Pattern.compile(
          "[^\\p{ASCII}]+");
  
  /**
   * Pattern matching characters that are not allowed in a file name.
   */
  protected static final Pattern NON_FILENAME_PATTERN = Pattern.compile(
          "[/:\\\\]");
  
  /**
   * Pattern to strip the protocol, query string and fragment from a URL.
   */
  protected static final Pattern STRIP_PROTOCOL_QUERY_FRAGMENT =
          Pattern.compile("^(?:.*?://)?(.*?)(?:\\?.*)?(?:#.*)?$");

  public void config(boolean isOutput, Map<String, String> configData)
          throws IOException, GateException {
    super.config(isOutput, configData);
    String patternStr = configData.get(PARAM_PATTERN);
    if(patternStr == null || patternStr.length() == 0) {
      patternStr = "3/3";
    }
    // work out how many groups there are
    Matcher m = Pattern.compile("\\G(\\d+)(/?)").matcher(patternStr);
    StringBuilder patternBuilder = new StringBuilder();
    int matchCount = 0;
    while(m.find()) {
      matchCount++;
    }
    if(!m.hitEnd()) {
      throw new GateException(
              "Illegal pattern format for ARCDocumentNamingStrategy");
    }
    // build up the format pattern and fill in the initial divisors array
    // with the logs of the values that should eventually end up there
    divisors = new long[matchCount];
    matchCount = 0;
    m.reset();
    while(m.find()) {
      divisors[matchCount] = Long.parseLong(m.group(1));
      patternBuilder.append("%0");
      patternBuilder.append(divisors[matchCount]);
      patternBuilder.append("d");
      patternBuilder.append(m.group(2));
      matchCount++;
    }
    // multiply up using the logs to get the real divisors
    for(int i = divisors.length - 1; i >= 0; i--) {
      long log = divisors[i];
      divisors[i] = 1;
      for(long j = 0; j < log; j++) {
        divisors[i] = divisors[i] * 10;
      }
    }
    
    pattern = patternBuilder.toString();
  }

  @Override
  protected String relativePathFor(DocumentID id) {
    StringBuffer pathBuilder = new StringBuffer();

    // we use the record archive position as the id number, and we then 
    // distribute the output files into a directory structure.
    String posStr = id.getAttributes().get(ArchiveInputHandler.RECORD_POSITION_ATTR);
    // default value is 0, which means that all input records with no explicit
    // position will end up in files called: 000/.../000/URL_as_file_name
    long idNum = 0;
    if(posStr != null && posStr.length() > 0) {
      try {
        idNum = Long.parseLong(posStr);
      } catch (NumberFormatException e) {
        logger.warn("Invalid record position value (not an integer number): " +
            posStr);
      }
    }
    
    // build up the arguments for the format string by dividing the
    // id number by the relevant divisors
    Object[] formatArgs = new Object[divisors.length];
    for(int i = divisors.length - 1; i >= 0; i--) {
      formatArgs[i] = idNum % divisors[i];
      idNum = idNum / divisors[i];
    }
    
    // write the numeric bit of the relative path
    Formatter formatter =  new Formatter(pathBuilder);
    formatter.format(pattern, formatArgs);
    formatter.close();
    
    // the rest of the output file path is constructed from the record URL
    String remaining = id.getIdText();
    if(remaining != null && remaining.length() > 0) {
      // strip the protocol, query and fragment
      Matcher stripQueryMatcher = STRIP_PROTOCOL_QUERY_FRAGMENT.matcher(remaining);
      if(stripQueryMatcher.find()) {
        // this matcher should never fail as every string can match the (.*) part,
        // but be conservative anyway
        remaining = stripQueryMatcher.group(1);
      }
      // append an underscore and the cleaned-up remaining part of the name
      pathBuilder.append("_");
      Matcher nonAsciiMatcher = NON_ASCII_PATTERN.matcher(remaining);
      Matcher cleanupMatcher = NON_FILENAME_PATTERN.matcher(
              nonAsciiMatcher.replaceAll("~"));
      while(cleanupMatcher.find()) {
        cleanupMatcher.appendReplacement(pathBuilder, "_");
      }
      cleanupMatcher.appendTail(pathBuilder);
    }
    
    return pathBuilder.toString();
  }

  @Override
  public String toString() {
    return super.toString() + "\n\t\tPattern:        " + pattern;
  }
}
